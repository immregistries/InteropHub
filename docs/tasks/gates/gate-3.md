# Gate 3 Report

**Date:** 2026-11-03
**Run by:** Stream G, with H3's API checks
**Test plan:** `docs/tasks/topic-reference-comments-test-plan.md`, Section 5
**Result:** In progress. API and browser checks pass after H3.2. The real review round on the pilot is still to run.

## Summary

| Part | Result | Evidence |
|---|---|---|
| H3 author review | Pass | `feature/h3-author-review` (`18e1668`, CSRF `f64ff7f`, H3.2 `ba1270a`); `mvn test` 373 tests, 0 failures; reviewed by G against contract 5.6, 5.8, 6 |
| T3 guide author controls (templates `v0.7.0`, pilot on `v0.9.0`) | Pass | Browser checks by G against the H3 branch |
| P2 process guidance | Pass | Check 3.10 below |
| Real review round on the pilot | Not run | After H3 merges, on production or a local copy, with the product owner as Author |

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

## Findings

- **H3.2 (bug, fixed in `ba1270a`):** `canClose` should be true for an author on `OPEN`, `DISPOSED`, and `CLOSED` (false only on `WITHDRAWN`), so the guide can reopen. Contract 5.2 is clarified: `canClose` covers both closing and reopening.
- **Not a defect:** the T3 follow-up "re-attach lists only explicit anchors" is already fixed in templates `v0.9.0`.

## Approval

Pending.
