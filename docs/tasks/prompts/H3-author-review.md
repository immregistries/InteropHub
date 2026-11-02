# H3: Author Review (InteropHub)

You are working in the InteropHub repository. Other agents may work in this repository at the same time, so work only in your own worktree and branch.

## Setup

Create a worktree under `C:\dev\immregistries\InteropHub.worktrees\` on a branch named `feature/h3-author-review`, from the current `main`. `main` has E1, H1, and H2, and is what production runs (0.10.0). Do not touch the main working copy. Local commits on your branch are fine; do not push or merge.

Local Tomcat and the local MySQL database are shared. **Ask the product owner before you deploy.** Additive schema changes to the local database are fine.

## Read first

- `CLAUDE.md`; `docs/database-release-practice.md` before touching `db/`; `docs/aira-web/README.md` before adding CSS.
- `docs/tasks/topic-reference-comments-api-contract.md`: **your specification.** You build the author fields of 5.6 (`dispositionCode`, `changeNote`, `state`, `anchor`, `blockIndex`, `snippet`), 5.8, and 6, and fill in every author field of the 5.2 thread object that H2 returns as `null`. Where this prompt and the contract disagree, the contract wins; tell G.
- `docs/topic-reference-comments-design.md`, Sections 6.3, 7, 8, and 10 (topic page panel and comment management page).
- `docs/tasks/topic-reference-comments-workstreams.md`, Section 8 (Decisions Log).
- `docs/tasks/topic-reference-comments-test-plan.md`, Section 5 (Gate 3).
- What H2 built, now on `main`: `GuideCommentService` and `HibernateGuideCommentStore`, `GuideRegistrationService`, `GuideApiServlet`, `GuideOpenServlet`, `GuideResponses`, `EsGuideReviewFlag` and `EsGuideReviewFlagDao`, `es_guide_disposition`, `es_guide_comment_audit`, and the author columns already on `es_guide_comment`. H1's `GuideScopes`, `GuideCors`, `GuideApiErrors`, and `ApiTime`. **Extend these; do not write a second service, store, error writer, CORS helper, or time formatter.**

Follow existing patterns: DAO per table, services hold rules, servlets render. Tests in `src/test/java`. No new libraries.

## Task

1. **Author actions in `PATCH /api/guide/comments/{id}`** (contract 5.6). Replace H2's `400 invalid_field` refusal of the author fields with the rules in the 5.6 table. Authorship is `canEditTopic` on the document's topic. Top-level comments only; on a reply these fields stay `400 invalid_field`. A change note is required when a disposition is set unless one is already stored. `null` disposition returns the comment to `OPEN` and clears the change note. Closing closes the thread to new replies; reopening returns to `DISPOSED` or `OPEN`. Re-attach is allowed in any state except deleted. All fields in one request are checked before anything is saved. `expectedUpdatedAt` conflicts as in H2. One audit row per changed field. Each change bumps the thread's `updated_at`, so the guide's `since` poll picks it up.
2. **`DELETE /api/guide/comments/{id}`** (contract 5.8): authors only, soft delete with `deleted_at` and `deleted_by_user_id`, a top-level delete also deletes its replies, `204`, and the thread returns as a tombstone in `since` as H2 already does for other removals.
3. **Review flags** (contract 6): the three endpoints in 6.2, with at most one active flag per anchor per document. A repeated `POST` on an anchor with an active flag replaces the note and returns `200`. `DELETE` clears the flag and is safe to repeat. The comment list poll already returns active flags (H2); make sure set, replace, and clear show up on the next poll.
4. **Comment management page** for authors (design 10), linked from the topic's manage page next to the guide registration link: all comments for a document, filterable by state (default: everything except `CLOSED`), with disposition and change note editing, close and reopen, and delete. Each comment links into the guide through `/guide/open?doc=…&comment=…`. Use the same service as the API. Readers who are not authors do not see the page or the link. Avoid browser `confirm()` dialogs; use an in-page confirmation for delete, as the guide does.
5. **Topic page panel** (design 10): a "Topic Reference comments" panel on the public topic page (`/es/topic/{id}`) beside the meeting notes, shown only when the topic has an active registered guide and the viewer passes `canViewTopic`. It shows recent open and disposed comments (a short excerpt, commenter display name, date, disposition if any) and active review flags, each linked through `/guide/open` to its comment or anchor, plus a link to open the guide and, for authors, to the comment management page. Keep it small: a handful of items, newest first.
6. **Registration page fix.** On the guide registration page (`GuideRegistrationPageServlet`), "Check without saving" clears the form after a passed check, so the user has to type everything again before saving. Keep the submitted values in the form after a check, whether it passes or fails.
7. **Database.** H2 already created the author columns, `es_guide_disposition`, and `es_guide_review_flag`, so H3 should need no schema change. If you find you need one, put it in a clearly commented block in `db/unapplied_updates.sql` (which is now empty after the v0.10 freeze) and tell G why.
8. **Dev setup.** If the Gate 3 checks need more local data (for example comments in each state, or a flag), extend `db/scripts/topic_reference_dev_setup.sql`, keeping it idempotent and local only. Note that after the next local refresh the pilot (`contextual-conditions`) comes from production with its production URLs; the dev script adds `http://localhost:8001/` to it.
9. **Tests**: every rule in the 5.6 table (each state change, change note required, replace and clear a disposition, refused on `CLOSED` and `WITHDRAWN`, re-attach in each state, replies refuse author fields, author cannot edit another's text), a mixed request where one field is refused and nothing is saved, delete cascading to replies and appearing as a tombstone in `since`, flag create, repeat, clear, clear again, and one active flag per anchor, readers refused with `403 forbidden` on every author action (Gate 3 check 3.8), and the audit rows.

## Out of scope

- `/mcp` and its tools (H4), and the topic tools (E2).
- Automatic closing of comments.
- Anything in `content-publishing-templates`. The guide's author controls are already built (templates `v0.7.0`) against the contract; if the contract turns out to be wrong in a way that affects the guide, stop and tell G rather than working around it.

## Done when (test plan Gate 3, Section 5)

- `mvn test` passes.
- The curl checks for 3.1 to 3.5, 3.7, and 3.8 pass against your deployed branch with the dev setup applied.
- The pilot guide, built locally with templates `v0.9.0` and `"comments": {"mode": "interophub", "hub": "http://localhost:8080/hub", "document": "contextual-conditions"}` and served at `http://localhost:8001/`, shows an author's disposition, change note, close, delete, re-attach, and flag to a second signed-in reader. G or the product owner can build and serve it for you.
- The topic page panel and comment management page work for an author, show nothing extra to a reader who is not an author, and their links land on the right comment or anchor (check 3.9).

## Report back

Files changed; any SQL added and why; the curl commands and results for 3.1 to 3.5, 3.7, and 3.8; screenshots or a description of the panel and the management page; anything in the contract that was unclear or that you had to interpret. Commit on your branch. Do not push or merge.
