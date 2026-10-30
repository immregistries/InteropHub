# H2: Guide Registration and the Comments API (InteropHub)

You are working in the InteropHub repository. Other agents work in this repository at the same time, so work only in your own worktree and branch.

## Setup

Create a worktree under `C:\dev\immregistries\InteropHub.worktrees\` on a branch named `feature/h2-comments`, from the current `main` (which now has E1 and H1). Do not touch the main working copy. Local commits on your branch are fine; do not push or merge.

Local Tomcat and the local MySQL database are shared. **Ask the product owner before you deploy.** Additive schema changes to the local database are fine.

## Read first

- `CLAUDE.md`; `docs/database-release-practice.md` before touching `db/`; `docs/aira-web/README.md` before adding CSS.
- `docs/tasks/topic-reference-comments-api-contract.md`: **your specification.** You build Sections 3.6 (`/guide/open`), 5.1, 5.2, 5.3, 5.4, 5.5, the `text` field of 5.6, 5.7, 7, and the H2 rows of 9 and 10. Where this prompt and the contract disagree, the contract wins; tell G.
- `docs/topic-reference-comments-design.md`, Sections 6.1, 6.2, 6.4, 8, 9.1, 9.4, and 10 (registration page and admin only).
- `docs/tasks/topic-reference-comments-workstreams.md`, Section 8 (Decisions Log).
- `docs/tasks/topic-reference-comments-test-plan.md`, Section 4 (Gate 2).
- What H1 built, now on `main`: `GuideBearerTokenFilter`, `GuideRequestAuth`, `GuideScopes` (`requireScope`), `GuideCors`, `GuideApiErrors`, `GuideAllowlistOrigins`, `ApiTime`, `GuideTokenServlet`, `db/scripts/topic_reference_dev_setup.sql`. **Reuse them; do not write a second filter, CORS helper, error writer, or time formatter.**
- What E1 built, now on `main`: `TopicSpaceAccessService.canEditTopic` (the one topic rule: global admin, space admin, or active champion or support subscriber), `TopicEditService`, `es_topic_change`.

Follow existing patterns: DAO per table, services hold rules, servlets render. Tests in `src/test/java`. No new libraries.

## Task

1. **Database**, one clearly commented block at the end of `db/unapplied_updates.sql`:
   - `es_guide_document`, `es_guide_document_url` (with `sort_order` and the `allow_id` it created), `es_guide_comment` (with `updated_at`, `deleted_at`, `deleted_by_user_id`, `withdrawn_at`, and the author columns H3 will use: `disposition_code`, `change_note`, `disposed_at`, `disposed_by_user_id`, `closed_at`, `closed_by_user_id`), `es_guide_comment_audit`, `es_guide_disposition` seeded with the seven codes and labels in contract 5.1, and the approved guide URL prefix table. Design Section 8 plus contract Section 9.
   - Seed the approved prefix `https://immregistries.github.io/`.
   - Create `es_guide_review_flag` too, so H3 adds no tables, but build no flag endpoints.
2. **Registration** (contract 7): `POST /api/guides` and `PATCH /api/guide/{documentKey}`, both with `dryRun`, scope `topics:write`, and `canEditTopic` on the topic. URL rules in 7.3, including the localhost dev rule through `PublicUrlService.isLocalhostMode()`. Allowlist upkeep: add an `app_redirect_allowlist` row for each URL, and disable only rows the registration created when a URL is removed or the document becomes inactive. Record each change in `es_topic_change` (source `API` or `WEB`, with `app_user_token_id` for API calls); say in your report how you represented a guide change there.
3. **Registration page**, reachable from the topic's manage page for those who pass `canEditTopic`: the same fields and rules as the API, through the same service. It warns that a private topic should not be linked to a publicly published guide, and the reverse (design 6.2).
4. **Admin page** for global admins: approved URL prefixes (add, disable), and all guide registrations across topics.
5. **`/guide/open`** (contract 3.6).
6. **Comments API**: 5.1, 5.3, 5.4, 5.5, the commenter's `text` edit in 5.6, and 5.7, all behind H1's filter with scope `comments` and H1's CORS helper. Visibility through `canViewTopic`; authorship (`isAuthor`, author `permissions`) through `canEditTopic`. Return the full thread object of 5.2 with every field, author fields `null` until H3. `since` per 5.3, including tombstones and thread-level `updated_at` bumped by reply changes. 14-day edit window. Audit rows for every change.
   - Timestamps: format with `ApiTime`. **Parse** incoming `since` and `expectedUpdatedAt` leniently: accept any ISO 8601 UTC instant (`Instant.parse`), not only the millisecond form, and answer `400 invalid_field` naming the parameter if it does not parse. Extend `ApiTime` for this rather than adding a second helper.
   - Author-only `PATCH` fields (`dispositionCode`, `changeNote`, `state`, `anchor`, `blockIndex`, `snippet`), `DELETE`, and review flag endpoints are **H3**. For now refuse those fields with `400 invalid_field`.
7. **Dev setup**: extend `db/scripts/topic_reference_dev_setup.sql` (still idempotent, still local only) with the Outsider user, a private test Topic Space with one topic, a guide registration for it, and a registration of the pilot (`documentKey` `contextual-conditions`, topic 7, URL `http://localhost:8001/`) linked to the allowlist row H1's script creates. Test plan Section 1.3.
8. **Tests**: visibility (public, private, outsider), the comment rules in 5.4 and 5.6 (reply to a reply, reply to a closed or withdrawn thread, edit window, editing another's text), `since` with tombstones and reply-only changes, registration URL rules (approved, not approved, admin override, dev rule, duplicate), allowlist upkeep, dry run saving nothing, scope refusal (test plan check 1.31: a guide token on `POST /api/guides`).

## Out of scope

- Author actions in the API, review flag endpoints, the comment management page, the topic page panel (H3).
- `/mcp` (H4).
- Anything in `content-publishing-templates`.

## Done when (test plan Gate 2, Section 4.1, checks 2.1 to 2.10, plus 1.31 and 2.32)

- `mvn test` passes.
- With the dev setup applied, the curl checks in 4.1 pass.
- The pilot guide built with `"comments": {"mode": "interophub", "hub": "http://localhost:8080/hub", "document": "contextual-conditions"}` and served at `http://localhost:8001/` signs in, posts, and replies against your deployed branch. (It uses templates `v0.6.0`; G or the product owner can build it for you.)

## Report back

Files changed; the SQL added; how a guide change is recorded in `es_topic_change`; the curl commands and results for 2.1 to 2.10, 1.31, and 2.32; anything in the contract that was unclear or that you had to interpret. Commit on your branch. Do not push or merge.
