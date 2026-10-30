# Gate 2 Report

**Date:** 2026-10-30
**Run by:** Stream G, with H2's API checks
**Test plan:** `docs/tasks/topic-reference-comments-test-plan.md`, Section 4
**Result:** Local part passes, approved by the product owner 2026-10-30. Production part (4.3, P3) not yet run.

## Summary

| Part | Result | Evidence |
|---|---|---|
| T2 live comments (templates `v0.6.0`) | Pass | Browser checks against the mock by G |
| T3 author review (templates `v0.7.0`) | Pass | Browser checks against the mock by G (counts toward Gate 3) |
| H2 registration and comments API | Pass | `feature/h2-comments` (`93e6cf7`), merged into `main` as `aecf429`; `mvn test` 361 run, 0 failures, 3 skipped |
| End to end on local InteropHub | Pass | Pilot built on `v0.7.0` in InteropHub mode, served at `http://localhost:8001/`, against `feature/h2-comments` on Tomcat |
| Production (P3) | Not run | Needs an InteropHub release and the pilot published in InteropHub mode |

## 4.1 API checks (run by H2 with curl)

| # | Result | Note |
|---|---|---|
| 2.1 | Pass | Reader `canView` true; Outsider on the private guide `200` with `canView` false and `topic` null; unknown key `404` |
| 2.2 | Pass | Post, reply, reply to a reply refused |
| 2.3 | Pass | Each limit refused with `invalid_field` naming the field |
| 2.4 | Pass | `since` returns new, edited, reply-changed, withdrawn, and tombstoned threads |
| 2.5 | Pass | Own edit; another's edit `403 forbidden`; after 15 days `403 edit_window_closed` |
| 2.6 | Pass | Withdraw comment and reply; reply to withdrawn thread `409 invalid_state` |
| 2.7 | Pass | Stale `expectedUpdatedAt` gives `409 conflict` with `current` |
| 2.8 | Pass | Unapproved URL refused for an author, allowed for an admin; duplicate `409`; dry run saves nothing |
| 2.9 | Pass | Allowlist row follows URL removal and deactivation; inactive guide refuses comments |
| 2.10 | Pass | Audit rows written |
| 1.31 | Pass | Guide token on `POST /api/guides`: `403 insufficient_scope`, `requiredScope` `topics:write` |
| 2.32 | Pass | `/guide/open` redirects with `?interophub=signin` and the anchor or comment; unknown document `404` |

## 4.2 Browser checks (run by G against local InteropHub)

| # | Result | Note |
|---|---|---|
| 2.20, 2.26 | Pass | Signed out: document, "Sign in to comment", no comments, no redirect |
| 2.21 | Pass | With an InteropHub session: immediate. Without (after "Not you?"): email, dev magic link, back to `#minimum-needed-assertion` signed in as Guide Reader. First-comment notice is an in-page checkbox. |
| 2.22 | Pass | Reader's comment (id 8) saved and placed in its anchor area; Author's reply, posted from a second account, appeared in the Reader's open page within a second, nested under it |
| 2.23, 2.24 | Pass by review | Back-off timing and hidden-tab pause read in `guide.js` and exercised against the mock; not timed live |
| 2.25 | Pass | Reader on the private guide: "Your account cannot see comments on this document.", no comments, no comment buttons. Cosmetic: "Show closed" still shows there. |
| 2.27 | Pass | Revoking the guide sign-in on the account page returns the guide to "Sign in to comment" without a redirect |
| 2.28 | Pass | `/guide/open?doc=…&comment=8` lands on the comment, highlighted, without signing in again |
| 2.29 | Pass | Local mode and no `comments` setting covered by `validate.ps1` |
| 2.30 | Pass | Mock contract tests in the templates repository |
| 2.31 | Pass | "Signed in as", Sign out, "Not you?" (which also covered `prompt=login` end to end) |
| 2.32 | Pass | See 4.1 |

Times shown in the guide matched local time for comments stored in UTC (`ApiTime`).

## 4.3 Production

To do with P3: release InteropHub with the H1 and H2 database changes, register the pilot on production, publish the pilot with templates `v0.7.0` and the `comments` setting, then repeat 2.20 to 2.22 and 2.28 with two real accounts.

## Follow-ups

- "Show closed" appears for a reader who cannot view comments (T4, cosmetic).

## Approval

Product owner: local part approved 2026-10-30. Production part pending P3.
