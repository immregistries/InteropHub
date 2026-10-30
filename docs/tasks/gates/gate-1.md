# Gate 1 Report

**Date:** 2026-10-30
**Run by:** Stream G
**Test plan:** `docs/tasks/topic-reference-comments-test-plan.md`, Section 3
**Result:** Pass, approved by the product owner 2026-10-30

## Summary

| Part | Result | Evidence |
|---|---|---|
| T1 anchors (templates `v0.5.0`, `v0.5.1`) | Pass | G re-ran `validate.ps1`; reviewed the diff for internal names. |
| P1 pilot anchors | Pass | `contextual-conditions` commit `97b36c9`: 27 anchors, templates `v0.5.1`, explicit `{#related-representations}` ID. |
| H1 sign-in foundation | Pass | Branch `feature/h1-sign-in` (`0d2aa3b`), merged into `main` as `07efc1c`. |
| E1 topic edit service | Pass | Branch `feature/e1-topic-edit-service` (`9dbfdb3`), merged into `main` as `baab8ec`. |
| Merged `main` | Pass | `mvn test`: 352 run, 0 failures, 3 skipped. `unapplied_updates.sql`: E1 block, then H1 block. |

## Checks

### 3.1 T1

| # | Result | Note |
|---|---|---|
| 1.1 | Pass | `validate.ps1` |
| 1.2 | Pass | `data-anchor`, `data-block-index` on every commentable block |
| 1.3 to 1.5 | Pass | Light edit keeps the comment; full rewrite moves it to the area start; removed anchor orphans it |
| 1.6 | Pass | Legacy paragraph comments stay attached; list-item and code-block comments become orphans; none lost. Documented in the changelog. |
| 1.7 | Pass | Anchor and comment deep links on load and on hash change |
| 1.8 | Pass | Duplicate, malformed, reserved, and heading-colliding anchors refused; the build also refuses duplicate IDs in the final HTML |
| 1.9 | Pass | No anchors in Word or the reading copy; no extra blank lines after `v0.5.1` |
| 1.10 | Pass | Tags `v0.5.0`, `v0.5.1` |
| 1.11 | Pass | Pilot builds; deep links checked in the browser |

### 3.2 H1

| # | Result | Note |
|---|---|---|
| 1.20, 1.21 | Pass | Round trip with and without an InteropHub session, including the magic link |
| 1.22, 1.23, 1.24 | Pass | Origin `:8002` refused; token call from a disallowed origin gets `403` and the code survives |
| 1.25, 1.26, 1.27, 1.28 | Pass | Each PKCE failure gives the single `400`; `plain` and missing challenge refused; codes do not cross apps; existing external login unchanged |
| 1.29, 1.30, 1.32, 1.33 | Pass | Token storage and apps as in contract 3.1; identical `401` body for every failure; cookie not accepted; sign-out |
| 1.31 | Moved to Gate 2 | No `topics:write` route exists until H2 |
| 1.34 | Pass | Personal access token shown once, revoked, then refused |
| 1.35 | Moved to Gate 2 (2.32) | `/guide/open` is in H2 |
| 1.36 | Partly | The `app_registry` rows are created idempotently by `unapplied_updates.sql`, so a refresh restores them; not yet observed through an actual refresh |
| 1.37 | Pass | `prompt=login` ends the session and returns as the other user |

### 3.3 E1

| # | Result | Note |
|---|---|---|
| 1.40 | Pass | Edit, admin create, and import save paths work. The runtime check found and fixed an import regression (`9dbfdb3`). Validation messages reworded to one shared form, approved by the product owner. |
| 1.41 | Pass | Champion edits topic 7; reader refused |
| 1.42 | Pass | Space admin sees Add topic and creates; champion refused with `403` |
| 1.43 | Pass | Permission audit reviewed by G; Supporters view stays global-admin only (product owner decision) |
| 1.44 | Pass | `es_topic_change` rows for web and import |

## Carried forward

- 1.31 and 2.32 run at Gate 2.
- 1.36: confirm on the next real database refresh that the apps, columns, and dev setup data all come back.
- Local Tomcat runs E1's build; redeploy from `main` after this merge.

## Approval

Product owner: approved 2026-10-30.
