# Topic Reference Private Hosting

**Status:** Approved 2026-11-09 by the product owner (decisions in Section 7)
**Amends:** `docs/topic-reference-comments-design.md` (Sections 6 and 15 row 7) and `docs/tasks/topic-reference-comments-workstreams.md` (steps H5 and T5, Gate 3H)

## 1. Problem

Some Topic References must not be publicly distributed. They are not high-security documents; they need only the protection InteropHub already gives a private topic space: you must sign in, and you must be able to view the topic. Unguessable links are enough for the stored file itself.

Today a guide is published to GitHub Pages. Pages from a private repository are still public on the web, so a private repository does not keep the guide private. The design treats "private topic with a public guide" as a setup mistake (design 15, row 7) and offers no supported way to do it.

## 2. Three hosting modes

| Mode | Where the guide lives | Comments | Who can read it |
|---|---|---|---|
| **Local** | Built file on the author's machine, shared by hand | Local (browser, exported as JSON) | Whoever has the file |
| **Public** | GitHub Pages (today's setup) | InteropHub | Anyone |
| **Hosted** (new) | Uploaded to InteropHub, served by InteropHub | InteropHub | Signed-in users who can view the topic |

Local and Public already work. Hosted uses the same guide build, comment API, and sign-in, so H1 to H3 and T1 to T3 carry over unchanged.

The guide build produces one self-contained `index.html` per version (about 400 KB for the pilot; images are embedded so the guide works even when the repository is private). The publish workflow copies that one file to `vX.Y.Z/`, `latest/`, and `main/`. So a hosted version is one stored file per label.

## 3. InteropHub (step H5)

1. **Hosting on the guide document.** `es_guide_document` gains `hosting` (`EXTERNAL` or `HUB`). A new table records uploaded versions: guide document, label (`latest`, `main`, or a release tag such as `v0.3.0`), stored file, uploader, time. Uploading to an existing label replaces what the label serves; earlier uploads stay listed as history.
2. **Storage: the existing Blob container.** Hosted guides use the existing `hub_stored_file` and `StoredFileService` storage (Blob on production, local backend in dev, where Blob writes are off). Object keys are random UUIDs, so the Blob URL is unguessable; that is the accepted level of protection for the stored object. Because the HTML is not an image or PDF, Blob stores it with `Content-Disposition: attachment`, so a leaked Blob URL downloads the file rather than showing a working guide.
   - **Never redirect to Blob.** The guide must be served from InteropHub's own URL: its comment sign-in depends on that origin, and the Blob URL must not be handed to readers. InteropHub streams the file (`ArtifactBlobStorageService.open` already reads anonymously from the recorded location).
   - **HTML is allowed only on this path.** General uploads (`StoredFileValidation`) keep refusing HTML. The guide upload validates its own type (`text/html`, starts like an HTML document, 20 MB maximum) and stores through the same service.
   - The stored file's `public_id` is never shown in pages or API responses, so `/files/*` is not a way in.
3. **Serving.** `GET /hub/guide/view/{documentKey}/{label}/` (a URL without the trailing slash redirects to it) serves the file only after the existing topic visibility check (`TopicSpaceAccessService`):
   - not signed in: redirect to sign-in, then back to the guide;
   - signed in without access to the topic: refused, with the same page a private topic gives;
   - allowed: the HTML with `Cache-Control: private, no-store`, `X-Robots-Tag: noindex`, `X-Content-Type-Options: nosniff`, and `Referrer-Policy: same-origin`.
   For a topic in a public space this means "any signed-in user", which is still not public distribution.
4. **Comments work unchanged.** When a document is set to `HUB`, InteropHub registers its own view URLs (`…/guide/view/{documentKey}/latest/` and `…/main/`) as the document's guide URLs, with their allowlist rows, through the existing registration rules. Sign-in, CORS, and `/guide/open` then behave exactly as for a GitHub Pages guide; the guide needs no special case.
5. **Uploading.**
   - API: `POST /api/guide/{documentKey}/versions/{label}` with the HTML as the body (`Content-Type: text/html`), scope `topics:write`, caller must satisfy `canEditTopic`. `POST`, not `PUT`, because production Apache refused `PUT` (H3.3). `201` on a new label, `200` on a replacement, with the version object (label, size, uploader, time, view URL). `GET /api/guide/{documentKey}/versions` lists them.
   - Page: an upload form on the guide's management page (CSRF token, same rule), for authors who do not run scripts.
   - Labels: `latest`, `main`, or `v` followed by digits and dots (with an optional `-suffix`).
   - Every upload is recorded in `es_topic_change`.
6. **Admin and topic page.** The registration page shows the hosting mode and the uploaded versions. The topic page panel's "Open the guide" link already goes through `/guide/open`, so it works for hosted guides once the view URLs are registered. The "private topic with a public guide" warning stays for `EXTERNAL` documents and suggests `HUB`.

## 4. Templates (step T5)

1. `guide.json` gains `hosting`: `local`, `github-pages` (default, today's behavior), or `interophub`. With `interophub`, `comments.mode` must be `interophub` (`check_content.py` validates it).
2. `tools/publish_guide_to_hub.py` (standard library only) uploads a built guide under one or more labels, reading a personal access token from `INTEROPHUB_TOKEN`. It never prints the token.
3. A workflow template for private repositories: build, then upload `vX.Y.Z` and `latest` on a tag and `main` on a push, using a repository secret. Private GitHub repositories still run Actions; only Pages is public.
4. Repository links stay in the guide. For a private repository they work only for people with GitHub access, which is fine.
5. Guidance in `guidance/TopicReference.md` on choosing a mode. T4's setup step asks the hosting question.
6. Built and tested against a mock upload endpoint added to `tests/mock_hub.py`.

Public repository rules still apply: no project names, no private URLs.

## 5. Accepted risk: guide script runs on the hub's origin

A hosted guide is HTML with its own script, served from InteropHub's origin, so its script runs with the reader's InteropHub session. Only topic authors (`canEditTopic`) can upload, and every upload is logged with the uploader. **Accepted 2026-11-09.** If hosted guides spread beyond trusted authors, the upgrade is a separate host name for hosted guides (DNS, certificate, and Apache work); the guide itself would not change.

## 6. Gate 3H: private hosting

Passes when, on local InteropHub:

1. An author uploads a guide with `publish_guide_to_hub.py` and with the upload form; uploading again to `latest` replaces what `latest` serves and the earlier upload is still listed.
2. A signed-out browser opening the guide URL is sent to sign-in and comes back to the guide.
3. A signed-in user who cannot view the topic (private space) is refused; one who can reads the guide and comments, and a second user sees the comment without reloading.
4. A reader without `canEditTopic` gets `403` from the upload API and does not see the upload form.
5. The served page carries the headers in 3.3; no page or API response contains the stored file's `public_id` or Blob URL; a general upload of an HTML file is still refused.

Then on production with the first private Topic Reference: `aira-resource-repository-reference` (`C:\dev\writing\opportunities\aira-resource-repository-reference`, repository `immregistries-internal/aira-resource-repository-reference`, InteropHub topic 180 "Community Knowledge Repository and Governance", in the PRIVATE space "AIRA Opportunity Nursery"). That project has no publish workflow yet and no `comments` setting; it goes from nothing to Hosted (step P5).

## 7. Decisions (2026-11-09, product owner)

1. Three hosting modes and the Hosted design: approved.
2. Same-origin script risk (Section 5): accepted.
3. H5 runs before H4.
4. First private Topic Reference: `aira-resource-repository-reference`.
5. Storage: the existing Blob container with unguessable keys; no private container. Files are streamed through InteropHub, never redirected.
6. H5 goes to Cursor, T5 to Codex.
