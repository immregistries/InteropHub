# AIRA Web CSS Request — Target Version 0.1.14

**Requested by:** InteropHub
**Date:** 2026-10-30
**Found during:** The Initiatives checklist, roster and Small Group pages (`/es/initiatives/{code}/checklist`, `/people`, `/small-groups/{id}`). These are working lists that will hold dozens of rows: checklist items, initiative members, Small Group participants. They need to read like a spreadsheet, and their add/edit dialogs contain type-ahead searches.

This is a standalone request, following the per-version pattern of `aira-css-request-13.md`. Two of the three changes are already queued as proposals in `aira-css-proposed-changes.md`, with local workarounds in InteropHub. The third came out of Nathan's review of the Small Group page.

---

## Requested change 1: Compact table modifier (`aira-table--compact`)

### Problem

`.aira-table th` / `td` use `0.5rem 0.75rem` padding, and `.aira-input` / `.aira-select` have a minimum height of `--aira-control-height` (2.25rem). A row holding a line of text, a checkbox and a select can't get near 40px. A 31-item checklist was about 200px per row before InteropHub worked around it locally.

### Current local workaround (to be removed once this ships)

`src/main/webapp/css/initiative-checklist.css`, scoped to `.initiative-checklist`:

```css
.initiative-checklist .aira-table > :is(thead, tbody) > tr > :is(th, td) {
  padding: 0.15rem 0.45rem;
  vertical-align: middle;
}
.initiative-checklist .aira-select,
.initiative-checklist .aira-input {
  min-height: 1.5rem;
  padding-top: 0.05rem;
  padding-bottom: 0.05rem;
}
```

### Proposed shared interface

```html
<table class="aira-table aira-table--compact">...</table>
```

- Cells get tighter padding (around `0.25rem 0.5rem`; pick values that fit the spacing scale) and `vertical-align: middle`.
- `.aira-input`, `.aira-select`, `.aira-button` and `.aira-badge` inside the table become compact. Use `--aira-control-height-small` or a new compact token (around 1.5–1.75rem) and adjust font size if needed.
- Checkboxes and small icon buttons should line up with the text baseline.
- Keep a usable touch target where the shared CSS already promises one; say in the revision notes if compact mode trades that off.

### Why shared

Any roster, queue or checklist that should read as a sheet hits the same row height. Density belongs to the table, not to InteropHub's checklist.

### Compatibility

A new modifier, so it has no effect until a page uses it.

---

## Requested change 2: Let a search popover draw outside a modal dialog

### Problem

`.aira-search-popover` is absolutely positioned under its field. A native modal `<dialog>` with `.aira-dialog` clips its content (`overflow: auto`), so the suggestion list is cut off at the dialog's edge instead of drawing over the fields below. The popover can't be moved outside the dialog either, because the dialog is in the top layer.

### Current local workaround (to be removed once this ships)

- `src/main/webapp/js/initiative-org-typeahead.js` sets `dialog.style.overflow = 'visible'` while the menu is open and clears it afterward.
- `src/main/webapp/css/initiative-org.css` gives the field `position: relative` and raises it to `--aira-z-overlay` while it is open.

### Proposed shared interface

A documented, CSS-only way for a search field inside an `aira-dialog` to show its popover past the dialog edge, for example:

```css
.aira-dialog:has(.aira-search-popover:not([hidden])) {
  overflow: visible;
}
```

along with a positioning context for the field. Choose the selector and markup contract. If `:has()` isn't acceptable for the supported browsers, propose the smallest alternative, such as a modifier class the application toggles, and document it.

Add a note and example to the Dialogs (or Search Suggestions) section of the components guide showing a type-ahead field inside a dialog form.

### Why shared

Any management dialog with a type-ahead hits the same clipping. Both the dialog and the search popover are shared components. InteropHub's next round adds a people search inside several dialogs, so this pattern is about to spread.

### Compatibility

Dialogs without a search popover keep their current scrolling. Dialogs with long content and an open popover should still be usable; note any trade-off.

---

## Requested change 3: Row headers in a table body should look like data cells

### Problem

`.aira-table th` gives every `th` the column-header treatment: muted background, `2px` strong bottom border, `vertical-align: bottom`. The components guide's own table example marks the first cell of each body row as `<th scope="row" class="aira-table__cell--primary">`, which is correct for accessibility. So pages that follow the guide get a grey "header column" down the left side. Nathan's review: the first column looks like a header and should be styled like the other columns.

InteropHub has six renderers using body row headers (`AccountServlet`, `EsTopicBoardServlet`, `EsTopicsServlet`, `InitiativePageRenderer`, `ChecklistPageRenderer`, `SmallGroupPageRenderer`).

### Proposed `aira.css` change

Style `tbody th` like `td`, keeping the semantics:

```css
.aira-table tbody th {
  background: transparent;
  border-bottom: 1px solid var(--aira-border-subtle);
  vertical-align: top;
  font-weight: inherit;
}
```

`.aira-table__cell--primary` keeps its bold weight. Make sure:
- the existing last-row and `aria-selected` row rules still apply;
- `aira-table--compact`, `aira-table--brand-header` and `aira-table--sticky-header` still behave (only `thead` cells should get the header look);
- `aira-matrix-table`, which uses row headers intentionally as a grid, is not changed.

### Compatibility

This changes how existing pages look: body row headers lose the grey background. That's the intended result. Spot-check the demo, and say in the revision notes which shared components are affected.

---

## Delivery

- Bump `aira-web-components` / `aira-web-theme` to `0.1.14` and `mvn install` locally.
- Add demo examples: a compact table with inline controls, a dialog with a working search popover, and a table with body row headers.
- Update the Tables and Dialogs sections of `components-guide.md`.
- Write `aira-css-changes-revision-14.md`. It should state what shipped, any deviations, and which InteropHub workarounds can be deleted. Copy it to `C:\dev\immregistries\interophub\docs\aira-web\`.
