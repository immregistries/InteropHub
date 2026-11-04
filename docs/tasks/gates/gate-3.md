# Gate 3 Report

**Date:** 2026-11-03
**Run by:** Stream G, with H3's API checks
**Test plan:** `docs/tasks/topic-reference-comments-test-plan.md`, Section 5
**Result:** Blocked on production. API and browser checks pass locally after H3.2. The production review round (started 2026-11-04 on 0.10.1) stopped at the first author action: production's Apache front end refuses `PATCH` and `DELETE` (finding H3.3).

## Summary

| Part | Result | Evidence |
|---|---|---|
| H3 author review | Pass | `feature/h3-author-review` (`18e1668`, CSRF `f64ff7f`, H3.2 `ba1270a`); `mvn test` 373 tests, 0 failures; reviewed by G against contract 5.6, 5.8, 6 |
| T3 guide author controls (templates `v0.7.0`, pilot on `v0.9.0`) | Pass | Browser checks by G against the H3 branch |
| P2 process guidance | Pass | Check 3.10 below |
| Real review round on the pilot | Blocked | Production 0.10.1 (AART #3962). Every `PATCH` and `DELETE` is refused by Apache before it reaches Tomcat; finding H3.3 |

## API checks (run by H3 with curl against local Tomcat)

| # | Result | Note |
|---|---|---|
| 3.1 | Pass | All seven dispositions set with change notes; the reader's list shows every code |
| 3.2 | Pass | No change note: `400` on `changeNote`; replacing keeps the note; clearing returns `OPEN` with no disposition or note |
| 3.3 | Pass | Closed comment omitted by default, listed with `state=CLOSED`; reply `409 invalid_state` |
| 3.4 | Pass (API) | Reopen returns `DISPOSED` with a disposition, `OPEN` without |
| 3.5 | Pass | Reply then thread deleted (`204`); reader's `since` returns both tombstones; rows kept with `deleted_at` |
| 3.7 | Pass (API) | Two flags `201`; repeat `200` same id with new note; clear twice `204`; reader sees the remaining flag |
| 3.8 | Pass | Reader's dispose, close, re-attach, delete, flag, clear-flag all `403 forbidden`; nothing changed |

## Browser checks (run by G, 2026-11-03)

Pilot built with templates `v0.9.0` and `comments.hub` = `http://localhost:8080/hub`, served at `http://localhost:8001/`, against the H3 branch on local Tomcat. Signed in through the guide as Guide Author, then (via "Not you?") as Guide Reader.

| # | Result | Note |
|---|---|---|
| 3.1 | Pass | Each disposition shows its label, change note, and "Disposed by … on …" in the guide |
| 3.3 | Pass | Close from the guide hides the comment; "Show closed" brings it back as closed with its disposition |
| 3.4 | Pass after H3.2 | First run: no "Reopen" on a closed comment (`canClose` false on `CLOSED`). Fixed in `ba1270a`; H3 confirmed in the guide that "Show closed" offers Reopen and reopening restores Reply, Dispose, and Close |
| 3.6 | Pass (re-attach) | A comment on an anchor not in the text is listed under "Orphaned comments"; Re-attach lists headings and explicit anchors; after saving it moved to "In brief". The rename-rebuild-publish part runs in the real review round |
| 3.7 | Pass | "Flag for review" on Purpose: area highlighted, button becomes "Edit review flag", listed in the rail's "Flagged for review" with Clear; the reader sees the same highlight and list |
| 3.8 | Pass | Reader sees no Dispose, Close, Re-attach, Delete, or flag controls; only Edit and Withdraw on their own comments |
| 3.9 | Pass (by H3, spot-checked) | Topic page panel and management page; reader gets the panel without "Manage comments" |
| 3.10 | Pass | P2 reviewed by G against design 3 and 12, small fixes; `content-publishing-process` `d4b2609`, tagged `v0.3.0` |

## Production review round (started 2026-11-04)

Production 0.10.1 deployed (AART #3962). G signed in to the pilot at `/latest/` (`v0.3.0`) through the guide as the product owner's account, with author controls shown. All three existing threads (#1 `in-brief`, #2 `purpose`, #3 `longitudinal-record-boundary`) are test comments, not review feedback, so the round uses them as test data and changes no text (product owner, 2026-11-04). The `v0.3.0` reorganization orphaned none of them: the three anchors it removed (`authoritative-source`, `feedback-adjudication`, `related-representations`) had no comments.

| # | Result | Note |
|---|---|---|
| 3.2 (no note) | Pass (client) | The guide refuses to save without a change note ("Please fill out this field") |
| 3.1 | Blocked | "Save disposition" on #3 sends `PATCH /api/guide/comments/3`; Apache answers `403` with its own HTML page and no CORS headers; the browser reports a network failure; nothing saved; the guide shows no error |
| 3.2 to 3.9 | Not run | Every remaining author action is a `PATCH` or `DELETE` except adding a flag |

Reproduced with curl, no token needed (so it is not the app): `PATCH`, `DELETE`, and `PUT` to any path under `/hub/` (for example `/hub/api/guide/comments/3`, `/hub/`) get Apache's `403 Forbidden` page; `GET`, `POST`, and the `OPTIONS` preflight reach Tomcat (`401` / `204` with CORS headers).

## Findings

- **H3.3 (production configuration, blocks Gate 3):** production's Apache refuses `PATCH` and `DELETE` under `/hub/`. Broken on production since 0.10.0: editing a comment (2.5), dispose, change, clear, close, reopen, re-attach (5.6), deleting comments (5.8), clearing review flags (6.2), and the guide's "Sign out" token revoke (`DELETE /api/guide/token`). Gate 2's production part did not run an edit, so it was missed there. Fix: allow `PATCH` and `DELETE` (at least under `/hub/api/`) in the Apache configuration; no code change. Then rerun the round.
- **T4 follow-up:** when a write fails at the network level, the guide shows no message; the form stays open and nothing tells the user it was not saved. Show an error.

- **H3.2 (bug, fixed in `ba1270a`):** `canClose` should be true for an author on `OPEN`, `DISPOSED`, and `CLOSED` (false only on `WITHDRAWN`), so the guide can reopen. Contract 5.2 is clarified: `canClose` covers both closing and reopening.
- **Not a defect:** the T3 follow-up "re-attach lists only explicit anchors" is already fixed in templates `v0.9.0`.

## Approval

Pending.
