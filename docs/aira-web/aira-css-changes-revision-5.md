# AIRA CSS Changes Revision 5

This note records the AIRA Web changes made for release `0.1.5` in response to `aira-css-proposed-changes.md`.

## Completed

- Added the shared matrix/board table component to `aira-web-theme/src/main/theme-css/14-tables.css`.
  - New wrapper: `aira-matrix-table-wrap`.
  - New table shell: `aira-matrix-table`.
  - New structural classes: `aira-matrix-table__corner`, `aira-matrix-table__col-header`, `aira-matrix-table__row-header`, `aira-matrix-table__header-inner`, `aira-matrix-table__label`, and `aira-matrix-table__cell`.
  - New disabled cell modifier: `aira-matrix-table__cell--disabled`.
  - The corner, column headers, and row headers use sticky positioning for two-axis board scrolling.
  - The styles use the existing AIRA token set, including surface, border, spacing, radius, shadow, text, link, and sticky z-index tokens.
- Added the shared compact entity card component to `aira-web-theme/src/main/theme-css/14-tables.css`.
  - New card shell: `aira-entity-card`.
  - New optional slots: `aira-entity-card__handle`, `aira-entity-card__title`, and `aira-entity-card__action`.
  - The card uses explicit grid placement so the title/action layout works when the leading handle or trailing action is omitted.
- Updated the demo Tables page in `aira-web-demo/src/main/java/org/immregistries/aira/web/demo/DemoServlet.java`.
  - Added a "Topic board matrix" example showing stage columns, path rows, multiple entity cards in one cell, optional handles/actions, a header action, and a disabled cell.
- Updated `docs/components-guide.md`.
  - Added "Matrix Tables and Entity Cards" guidance with example HTML for the new shared classes.
- Updated Maven versions for release `0.1.5`.
  - Root project version changed to `0.1.5`.
  - Module parent versions changed to `0.1.5` in `aira-web-theme`, `aira-web-components`, and `aira-web-demo`.

## Deliberately Not Done

- Did not add drag-and-drop styles or JavaScript behavior to AIRA Web.
  - InteropHub should keep local hooks for drop zones, drag handles, trash/remove targets, and drag-state styling.
- Did not add dialog, search-result, or add-topic UI styles for the board workflow.
  - Those remain application-specific per the component guide's "Deliberately Not Included" section.
- Did not modify InteropHub servlet or JavaScript files in this repository.
  - This repository only contains the shared AIRA Web theme/components/demo. The InteropHub agent should apply the markup migration when consuming `0.1.5`.

## InteropHub Migration Notes

- Use `aira-matrix-table-wrap` instead of `aira-table-wrap` for board/matrix layouts.
- Use `aira-matrix-table` instead of `aira-table` for stage-by-path board tables.
- Replace structural board classes with shared equivalents where practical:
  - `tb-board` -> `aira-matrix-table`
  - `tb-corner` -> `aira-matrix-table__corner`
  - `tb-stage` -> `aira-matrix-table__col-header`
  - `tb-path` -> `aira-matrix-table__row-header`
  - `tb-cards` -> `aira-stack aira-stack--compact`
  - `tb-card` -> `aira-entity-card`
  - `tb-topic-link` -> `aira-entity-card__title`
- Keep local classes as additive JavaScript hooks where needed, especially `tb-drop-zone`, `tb-drop-target`, `tb-drop-target-trash`, `tb-drag-handle`, `tb-remove-btn`, and any trash-row/path markers.
