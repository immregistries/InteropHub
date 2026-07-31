# CLAUDE.md

## Shared AIRA Web CSS

InteropHub's page shell and general-purpose CSS (`aira-*` classes) come from the `aira-web` project, consumed as a Maven dependency (`org.immregistries:aira-web-components`). It is a separate, evolving project — InteropHub is currently its only consumer.

**Before adding a new general-purpose CSS class or component to any local stylesheet in this project** (a table, card, button variant, layout primitive, badge, etc.), read `docs/aira-web/README.md` first. It explains the current version, how to check what the shared CSS already provides, and the process for proposing a reusable gap upstream instead of forking it locally.
