# AIRA Web CSS Proposed Changes

Start at [`README.md`](README.md) for how this file fits into the InteropHub ↔ aira-web workflow. This log records reusable AIRA Web styling gaps discovered while adopting `aira.css` in InteropHub. See `docs/aira-web/Migrating Servlets to AIRA CSS.md` section 14 for the detailed decision process.

Entries below the revision history are the **active queue** — proposals not yet implemented upstream. Once a proposal is resolved, its status changes to `Available in this project` with a resolution note (entries are not deleted, so the full reasoning stays visible here); the revision-history table below is the index across releases.

## Revision history

| Version | Date | Revision notes | Proposals addressed |
|---|---|---|---|
| 0.1.5 | 2026-07-31 | [`aira-css-changes-revision-5.md`](aira-css-changes-revision-5.md) | Matrix/board grid table component (`aira-matrix-table`, `aira-entity-card`) |
| 0.1.6 | 2026-07-31 | [`aira-css-changes-revision-6.md`](aira-css-changes-revision-6.md) | Stronger `aira-matrix-table` grid lines; reliable left alignment for `aira-matrix-table__header-inner` |

---

## Proposal: Stronger internal grid lines on `aira-matrix-table`

**Status:** Available in this project
**Found during:** Visual review of `EsTopicBoardServlet` (`/es/board/{code}`) and `EsTopicsServlet` (`/es/topics` board preview) after adopting `aira-matrix-table` from `0.1.5`.
**Date:** 2026-07-31

### Problem

`.aira-matrix-table th` / `.aira-matrix-table td` draw internal cell borders using `--aira-border-subtle` (`#e4e7e8`), which is too low-contrast against `--aira-surface` (white) and `--aira-surface-muted` to read as a visible line. In a data table this is fine because rows are already separated by zebra striping and hover state, but a matrix/board table has no such cues — without a visible border, users scanning the grid can't tell which column or row a cell belongs to, especially once the table is scrolled so the sticky corner/headers no longer line up with the viewport edge.

### Current local workaround

None. This is a shared-component rule; InteropHub does not want to override it locally.

Current rule in `aira.css`:

```css
.aira-matrix-table th,
.aira-matrix-table td {
  border-right: 1px solid var(--aira-border-subtle);
  border-bottom: 1px solid var(--aira-border-subtle);
  vertical-align: top;
}
```

### Proposed shared interface

No markup or class changes. This is a token substitution within the existing rule.

### Proposed `aira.css` changes

```css
.aira-matrix-table th,
.aira-matrix-table td {
  border-right: 1px solid var(--aira-border);
  border-bottom: 1px solid var(--aira-border);
  vertical-align: top;
}
```

`--aira-border` (`#cccccb`) is already the token used for the `aira-matrix-table-wrap` outer border and most other component borders, so this keeps the grid restrained (not a heavy black spreadsheet grid) while making rows and columns trackable. It does not affect `.aira-table`, which has its own separate row-divider rule and is unaffected by this change.

### Why this belongs in `aira.css`

This is a correctness fix to a shared component's default styling, not an InteropHub-specific preference — `--aira-border-subtle` is effectively invisible for this component's purpose regardless of which application uses it.

### Compatibility and migration impact

- Only affects `.aira-matrix-table`. No markup changes needed in InteropHub once the new version is consumed.
- No other component uses `.aira-matrix-table`'s border rule, so this has no effect outside the matrix table.

### Resolution

Implemented upstream in `aira-web-components`/`aira-web-theme` `0.1.6` (see `docs/aira-web/aira-css-changes-revision-6.md`). InteropHub now consumes `0.1.6` (`pom.xml`); no markup or servlet changes were needed.

---

## Proposal: Reliable left alignment for `aira-matrix-table__header-inner` without a trailing control

**Status:** Available in this project
**Found during:** Visual review of `EsTopicBoardServlet` (`/es/board/{code}`) and `EsTopicsServlet` (`/es/topics` board preview) after adopting `aira-matrix-table` from `0.1.5`.
**Date:** 2026-07-31

### Problem

`.aira-matrix-table__header-inner` is a flex container that places a label (`.aira-matrix-table__label`) at the start and, optionally, a trailing control (e.g. a header "+ Add" button, or a row's trash-toggle icon) at the end, using `justify-content: space-between`. This works correctly when both a label and a trailing control are present. When only the label is present — every plain row header (`aira-matrix-table__row-header`) with no trailing control — `space-between` with a single flex item does not reliably render at the start; the label appears centered in the header cell instead of left-aligned. The `.aira-matrix-table__corner`/`__col-header`/`__row-header` rule already sets `text-align: left`, but that has no effect on how the flex child itself is positioned, so it doesn't fix this.

### Current local workaround

None. This is a shared-component rule; InteropHub does not want to override it locally.

Current rule in `aira.css`:

```css
.aira-matrix-table__header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--aira-space-2);
  min-width: 0;
}
```

### Proposed shared interface

No markup or class changes required. `aira-matrix-table__header-inner` continues to accept a label plus an optional single trailing control as direct children, in the same order as today (label first, trailing control last).

### Proposed `aira.css` changes

```css
.aira-matrix-table__header-inner {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: var(--aira-space-2);
  min-width: 0;
}

.aira-matrix-table__header-inner > :last-child:not(:only-child) {
  margin-inline-start: auto;
}
```

`justify-content: flex-start` guarantees the label always renders at the start, whether it is the only child or not. The `:last-child:not(:only-child)` rule reproduces the old "trailing control pinned to the end" behavior only when a second child actually exists, by pushing it away from the label with `margin-inline-start: auto` instead of relying on `space-between`'s single-item edge case.

### Why this belongs in `aira.css`

This is a correctness fix to a shared component's layout, not an InteropHub-specific preference. The bug is in the flex layout itself and would reproduce for any consumer that renders a header cell with no trailing control.

### Compatibility and migration impact

- Only affects `.aira-matrix-table__header-inner`. No markup changes needed in InteropHub once the new version is consumed — existing header markup (label, or label + one trailing control, in that order) continues to work unchanged.
- Headers that already have a trailing control (column headers with the "+ Add" button, the trash row) should render identically to today, since the `:last-child:not(:only-child)` rule reproduces the same visual push-to-end result.

### Resolution

Implemented upstream in `aira-web-components`/`aira-web-theme` `0.1.6` (see `docs/aira-web/aira-css-changes-revision-6.md`). InteropHub now consumes `0.1.6` (`pom.xml`); no markup or servlet changes were needed.
