# H5: Private Hosting of Topic Reference Guides (InteropHub)

You are working in the InteropHub repository. Other agents may work in this repository at the same time, so work only in your own worktree and branch.

## Setup

Create a worktree under `C:\dev\immregistries\InteropHub.worktrees\` on a branch named `feature/h5-private-hosting`, from the current `main`. `main` has E1, H1, H2, and H3, and is what production runs (0.10.1). Do not touch the main working copy. Local commits on your branch are fine; do not push or merge.

Local Tomcat and the local MySQL database are shared. **Ask the product owner before you deploy.** Additive schema changes to the local database are fine.

## Read first

- `CLAUDE.md`; `docs/database-release-practice.md` before touching `db/`; `docs/aira-web/README.md` before adding CSS.
- **Your specification:** `docs/tasks/topic-reference-private-hosting.md`, Sections 2, 3, 5, and 6. Where this prompt and that document disagree, the document wins; tell G.
- `docs/tasks/topic-reference-comments-api-contract.md`: Sections 1 to 4 (conventions, errors, tokens and scopes, CORS) and 7 (guide registration and URL rules). New endpoints follow the same conventions, error shape, and scopes.
- `docs/tasks/topic-reference-comments-workstreams.md`, Section 8 (Decisions Log), especially the 2026-11-09 entries.
- What is already on `main` and must be **extended, not duplicated**:
  - Storage: `StoredFileService`, `StoredFileValidation`, `ArtifactBlobStorageService` (`open` reads anonymously from a recorded location), `LocalFileStorageService`, `StoredFileDao`, `hub_stored_file`, and `StoredFileServlet` (the anonymous `/files/*`, which hosted guides must **not** go through).
  - Guides: `GuideRegistrationService` and `HibernateGuideRegistrationStore` (URL rules and allowlist upkeep), `GuideApiServlet`, `GuideRegistrationPageServlet`, `GuideCommentsPageServlet`, `GuideOpenServlet`, `AdminGuideServlet`, `GuideResponses`, `GuideScopes`, `GuideCors`, `GuideApiErrors`, `ApiTime`.
  - Access and audit: `TopicSpaceAccessService` (`canViewTopic`, `canEditTopic`), `EsTopicChange`, and how existing pages send a signed-out user to sign in and back.

Follow existing patterns: DAO per table, services hold rules, servlets render. Tests in `src/test/java`. No new libraries.

## Task

1. **Schema.** In a clearly commented block in `db/unapplied_updates.sql`: `es_guide_document.hosting` (`EXTERNAL` or `HUB`, default `EXTERNAL`, so existing documents are unchanged), and a new table for hosted versions (guide document, label, stored file, uploaded by, uploaded at; a label resolves to its newest upload; older uploads are kept). Update `db/schema.sql` only the way `docs/database-release-practice.md` says to.
2. **Storing a version.** A service method that takes the HTML, validates it on its own (`text/html`; the content starts like an HTML document after optional BOM and whitespace; 20 MB maximum; label is `latest`, `main`, or `v` followed by digits and dots with an optional `-suffix`), and stores it through `StoredFileService` so it lands in the configured backend (Blob on production, local in dev). **General uploads must still refuse HTML**: do not widen `StoredFileValidation`'s general allowlist. The stored object is written as an attachment, not inline.
3. **Serving.** `GET /guide/view/{documentKey}/{label}/` (redirect the URL without the trailing slash to it; any further path after the label is a 404):
   - signed out: send to sign-in and back to the same guide URL, as other signed-in pages do;
   - signed in without `canViewTopic` on the document's topic, or document inactive or not `HUB`, or unknown label: the same refusal a private topic gives (do not reveal whether the document exists);
   - allowed: stream the stored HTML from the backend with `Content-Type: text/html; charset=utf-8`, `Cache-Control: private, no-store`, `X-Robots-Tag: noindex`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`. **Never redirect to the Blob URL.** Do not add a CSP sandbox; the guide's script must run (risk accepted, design Section 5).
4. **Switching a document to `HUB`.** On the registration page and in `PATCH /api/guide/{documentKey}` (a new `hosting` field), setting `HUB` registers `<public base>/guide/view/{documentKey}/latest/` and `.../main/` as the document's guide URLs through the existing registration rules and allowlist upkeep, replacing its external URLs. Switching back to `EXTERNAL` removes them the same way. The hub's own view URLs count as approved without an approved-prefix setting. The `POST /api/guides` create accepts `hosting` too. Add `hosting` to the 5.1 document object.
5. **Upload API.** `POST /api/guide/{documentKey}/versions/{label}`, body is the HTML with `Content-Type: text/html`, scope `topics:write`, caller must satisfy `canEditTopic`, document must be `HUB`. `201` for a new label, `200` for a replacement, returning the version object (`label`, `sizeBytes`, `uploadedBy` display name, `uploadedAt` in UTC via `ApiTime`, `viewUrl`). `GET /api/guide/{documentKey}/versions` lists the newest upload per label plus history count, for `comments` or `topics:read`. Errors in the contract's shape: `400 invalid_field` (label, type, size), `403 forbidden`, `404 not_found`, `409 invalid_state` (document not `HUB`). `POST` only; no `PUT` (production Apache refuses it). Record each upload in `es_topic_change`.
6. **Upload form.** On the guide's management page (or the registration page, whichever authors already use; say which), for `canEditTopic` users only: a file input and label, CSRF token as H3 added, the list of versions with uploader and time, and a link to each view URL. Readers do not see it.
7. **Never expose storage identities.** No page, API response, or log line visible to users contains the stored file's `public_id` or Blob URL for a hosted guide.
8. **Topic page panel and `/guide/open`.** Confirm both land on the hosted guide for a `HUB` document (they should, via the registered URLs) and that `/guide/open?doc=…&comment=…` keeps the `#comment-…` fragment.
9. **Contract.** Add the new endpoints, the `hosting` field, and the view URL to `docs/tasks/topic-reference-comments-api-contract.md` in a new subsection of Section 7, and send G the diff in your report. G approves contract changes.
10. **Dev setup.** Extend `db/scripts/topic_reference_dev_setup.sql` (idempotent, local only) so Gate 3H can be run: a `HUB` document on a topic in a PRIVATE space, a user who can view it, an author, and a signed-in user who cannot view it. Do not add guide HTML to SQL; the gate uploads it.
11. **Tests:** label and type validation; HTML still refused by general uploads; upload by author `201` then `200` with history kept; upload refused for reader (`403`), wrong scope, non-`HUB` document (`409`); serving: signed out redirects, no access refused, inactive document refused, unknown label refused, allowed request gets the headers and the body; switching `HUB` and back adds and removes the view URLs and allowlist rows; no `public_id` in the version object.

## Out of scope

- `/mcp` (H4) and the topic tools (E2).
- A separate host name for hosted guides, or a private Blob container.
- Anything in `content-publishing-templates` (T5 is building the upload script against your contract text) or in content projects.
- Production configuration. Tell G if anything needs Apache or Azure changes; nothing should.

## Done when (Gate 3H, local part)

- `mvn test` passes.
- On your deployed branch with the dev setup applied: curl uploads a built guide to `latest` and `main`, replaces `latest`, and lists the history; the three users in the dev setup get redirect, refusal, and the guide respectively; the view response has the required headers.
- In a browser, the hosted guide's "Sign in to comment" completes and a comment posted there appears for a second signed-in viewer without reloading. G or the product owner can build a guide for you (templates `v0.13.0`, `"comments": {"mode": "interophub", "hub": "http://localhost:8080/hub", "document": "<key>"}`).

## Report back

Files changed; the SQL added and why; the contract diff; curl commands and results; which page has the upload form; anything in the design you had to interpret. Commit on your branch. Do not push or merge.
