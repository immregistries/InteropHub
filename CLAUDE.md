# CLAUDE.md

## Shared AIRA Web CSS

InteropHub's page shell and general-purpose CSS (`aira-*` classes) come from the `aira-web` project, consumed as a Maven dependency (`org.immregistries:aira-web-components`). It is a separate, evolving project — InteropHub is currently its only consumer.

**Before adding a new general-purpose CSS class or component to any local stylesheet in this project** (a table, card, button variant, layout primitive, badge, etc.), read `docs/aira-web/README.md` first. It explains the current version, how to check what the shared CSS already provides, and the process for proposing a reusable gap upstream instead of forking it locally.

## Database release process

InteropHub's MySQL schema and seed data changes accumulate in `db/unapplied_updates.sql` until they're released to production. Nathan's local dev database is wiped and rebuilt from a fresh production backup roughly daily (or on demand), then `unapplied_updates.sql` is reapplied on top — so anything changed only through the running app's admin UI, and not also captured in that file, is silently lost on the next refresh.

**Before editing `db/unapplied_updates.sql`, `db/schema.sql`, or any `db/vX.Y_*.sql` file**, read `docs/database-release-practice.md` first. It explains which files are hand-edited vs. generated, how to read back local-only admin/UI changes and fold them into `unapplied_updates.sql` before they're lost, and the release process that freezes `unapplied_updates.sql` into a permanent `vX.Y_*.sql` record.
