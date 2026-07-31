# AIRA CSS Changes Revision 6

This note records the AIRA Web changes made for release `0.1.6` in response to the version 6 contents of `aira-css-proposed-changes.md`.

## Completed

- Updated the shared matrix table grid-line styling in `aira-web-theme/src/main/theme-css/14-tables.css`.
  - `.aira-matrix-table th` and `.aira-matrix-table td` now use `var(--aira-border)` for internal right and bottom borders.
  - This keeps the matrix grid restrained while making row and column boundaries easier to track.
- Updated the shared matrix header layout in `aira-web-theme/src/main/theme-css/14-tables.css`.
  - `.aira-matrix-table__header-inner` now uses `justify-content: flex-start`.
  - Added `.aira-matrix-table__header-inner > :last-child:not(:only-child)` with `margin-inline-start: auto` so optional trailing controls remain pinned to the far edge.
  - Header labels now stay left-aligned when they are the only child.
- Updated `docs/components-guide.md`.
  - Clarified that matrix header labels stay left-aligned whether or not a trailing control is present.
- Updated Maven versions for release `0.1.6`.
  - Root project version changed to `0.1.6`.
  - Module parent versions changed to `0.1.6` in `aira-web-theme`, `aira-web-components`, and `aira-web-demo`.

## Not Changed

- No new classes or markup contract changes were added for this release.
- No demo markup changes were required.
  - The existing Tables demo matrix example already includes headers with and without trailing controls, so it exercises the fixed alignment and stronger grid-line styling.
- No InteropHub servlet, JavaScript, or local CSS migration is required beyond consuming AIRA Web `0.1.6`.

## InteropHub Migration Notes

- Upgrade AIRA Web dependencies to `0.1.6`.
- Existing `aira-matrix-table` markup from the `0.1.5` migration should continue to work unchanged.
- Remove any local InteropHub workaround for matrix internal borders or matrix header label alignment if one was added while waiting for this release.
