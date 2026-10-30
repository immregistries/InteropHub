# AIRA CSS Changes Revision 14

This note records the AIRA Web changes made for release `0.1.14` in response to InteropHub's working-list and dialog request (`aira-css-request-14.md`).

## Completed

- Added the opt-in `aira-table--compact` modifier for sheet-like tables and queues.
  - Header and body cells use `--aira-space-1` / `--aira-space-2` padding and vertically center their contents.
  - Inline `aira-input`, `aira-select`, and `aira-button` controls use `--aira-control-height-small`, tighter vertical padding, and the small control font size.
  - Badges retain a compact `--aira-space-6` height with spacing-scale padding, and checkbox/radio labels align their controls with adjacent cell content.
- Added a CSS-only dialog popover contract.
  - Add `aira-dialog--popover-visible` to a bounded `aira-dialog` and wrap the anchored field and `aira-search-popover` in `aira-dialog-search`.
  - The modifier allows overflow to draw beyond the dialog; the wrapper supplies a relative positioning context and `--aira-z-overlay` stacking.
  - Applications continue to own combobox behavior and the popover's `hidden` state, but do not toggle dialog overflow.
- Changed `aira-table tbody th` to use data-cell presentation while retaining row-header semantics. Body row headers now have a transparent background, subtle one-pixel bottom border, top alignment, inherited table font size, and inherited weight. `aira-table__cell--primary` still supplies bold emphasis.
- Updated the last-row border rule to cover both body `th` and `td` cells.
- Narrowed the branded-header selectors to `thead th`, so `aira-table--brand-header` continues to brand column headers without recoloring body row headers. Sticky headers remain limited to `thead th`.
- Kept `aira-matrix-table` unchanged; its independent row-header selectors retain the intentional sticky grid-header treatment.
- Added demo examples for a compact participant table with inline controls, a table with semantic body row headers, and a working type-ahead inside a dialog whose popover can draw outside the dialog.
- Updated the Tables and Dialogs sections of `docs/components-guide.md` with markup contracts, accessibility guidance, and tradeoffs.
- Updated the root and all module parent versions to `0.1.14`; the published artifacts are `aira-web-components:0.1.14` and `aira-web-theme:0.1.14`.

## Design Decisions and Deviations

- Compact table cell padding is exactly the established spacing-scale pair `var(--aira-space-1)` (`0.25rem`) and `var(--aira-space-2)` (`0.5rem`), rather than the request's approximate literal values.
- Compact inputs, selects, and buttons use the existing `--aira-control-height-small` (`1.875rem`) rather than introducing a new 1.5–1.75rem token. This is slightly taller than the suggested range, but keeps shared control sizing consistent. Compact mode intentionally trades away the default full touch-target height and is documented for dense desktop working lists.
- The dialog contract uses an explicit, always-present modifier instead of `:has()`. This keeps behavior deterministic in embedded browser environments and avoids making layout depend on live relational-selector support. It also avoids any application JavaScript that mutates dialog overflow.
- `aira-dialog--popover-visible` is for bounded dialogs. Because it removes clipping/scroll containment, a long dialog should keep the default scrolling behavior or use a different interaction.
- The row-header change applies only to `tbody th` within `aira-table`. It does not select `aira-matrix-table`, and the branded and sticky variants now explicitly target only `thead th`.

## InteropHub Migration Notes

- Upgrade `aira-web-components` and `aira-web-theme` to `0.1.14`, then refresh the copied stylesheet and component-guide references.
- Add `aira-table--compact` to the checklist, roster, or participant tables that need sheet-like density.
- For a bounded dialog type-ahead, add `aira-dialog--popover-visible` to the dialog and `aira-dialog-search` to the element containing the input and `aira-search-popover`.
- Existing semantic `<th scope="row">` markup should remain. It will automatically receive data-cell styling after the stylesheet upgrade; keep `aira-table__cell--primary` where bold row identity is desired.

After adopting those shared classes, delete exactly these InteropHub workarounds:

1. From `src/main/webapp/css/initiative-checklist.css`, delete the `.initiative-checklist .aira-table > :is(thead, tbody) > tr > :is(th, td)` rule that supplies local cell padding and vertical alignment.
2. From `src/main/webapp/css/initiative-checklist.css`, delete the `.initiative-checklist .aira-select, .initiative-checklist .aira-input` rule that supplies local minimum height and vertical padding.
3. From `src/main/webapp/js/initiative-org-typeahead.js`, delete the code that sets `dialog.style.overflow = 'visible'` while the suggestion menu is open and clears that inline overflow afterward.
4. From `src/main/webapp/css/initiative-org.css`, delete the type-ahead field rules that locally add `position: relative` and raise the open field to `--aira-z-overlay`.

There is no InteropHub workaround to delete for body row headers; the shared change fixes the presentation of the existing semantic markup directly.
