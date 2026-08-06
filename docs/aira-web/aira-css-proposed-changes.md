# AIRA Web CSS Proposed Changes

Start at [`README.md`](README.md) for how this file fits into the InteropHub ↔ aira-web workflow. This log records reusable AIRA Web styling gaps discovered while adopting `aira.css` in InteropHub. See `docs/aira-web/Migrating Servlets to AIRA CSS.md` section 14 for the detailed decision process.

Entries below the revision history are the **active queue** — proposals not yet implemented upstream. Once a proposal is resolved, its status changes to `Available in this project` with a resolution note (entries are not deleted, so the full reasoning stays visible here); the revision-history table below is the index across releases.

## Revision history

| Version | Date | Revision notes | Proposals addressed |
|---|---|---|---|
| 0.1.5 | 2026-07-31 | [`aira-css-changes-revision-5.md`](aira-css-changes-revision-5.md) | Matrix/board grid table component (`aira-matrix-table`, `aira-entity-card`) |
| 0.1.6 | 2026-07-31 | [`aira-css-changes-revision-6.md`](aira-css-changes-revision-6.md) | Stronger `aira-matrix-table` grid lines; reliable left alignment for `aira-matrix-table__header-inner` |
| 0.1.7 | 2026-08-04 | [`aira-css-changes-revision-7.md`](aira-css-changes-revision-7.md) | Native dialog component (`aira-dialog`) — requested in [`aira-css-request-7.md`](aira-css-request-7.md) |
| 0.1.8 | 2026-08-06 | [`aira-css-changes-revision-8.md`](aira-css-changes-revision-8.md) | Semantic accent-border modifiers for `aira-table-panel` — requested in [`aira-css-request-8.md`](aira-css-request-8.md) |

---

## Proposal: Semantic accent-border modifiers for `aira-table-panel`

**Status:** Available in this project
**Found during:** Pre-migration analysis of `EsAgendaServlet` (`/es/agenda`) — the "Open Items" and "Curated Topic Cadence" sections, each a colored-accent callout wrapping a data table.
**Date:** 2026-08-06

### Problem

`.aira-alert` provides a left-border accent-color callout but only hosts text; `.aira-table-panel` provides the header/title/description/table structure but had no semantic accent-border option. Neither alone covered "a table that needs to visually read as a warning/urgency callout at a glance."

### Current local workaround

None remaining. The agenda page's `.open-items-section`/`.curated-cadence-section` bespoke inline `border-left` CSS is being removed as part of the `/es/agenda` migration now that the shared modifiers are available.

### Proposed shared interface

```html
<section class="aira-table-panel aira-table-panel--warning">
  <div class="aira-table-panel__header">
    <div>
      <h2 class="aira-table-panel__title">Open Items</h2>
      <p class="aira-table-panel__description">Carried forward from previous meetings.</p>
    </div>
  </div>
  <div class="aira-table-wrap">
    <table class="aira-table">...</table>
  </div>
</section>
```

### Why this belongs in `aira.css`

"A flagged/urgent list of items needing attention" is a common cross-application pattern, not specific to meeting agendas. It's a minimal, additive modifier on an existing component rather than a new one.

### Compatibility and migration impact

- Additive; existing unmodified `.aira-table-panel` usage is unaffected.
- `EsAgendaServlet`'s Open Items and Curated Topic Cadence sections adopt `aira-table-panel--warning`/`--danger` during migration; the bespoke inline border-left CSS is deleted.

### Resolution

Implemented upstream in `aira-web-components`/`aira-web-theme` `0.1.8` (see [`aira-css-changes-revision-8.md`](aira-css-changes-revision-8.md), delivered in response to the standalone request [`aira-css-request-8.md`](aira-css-request-8.md)). InteropHub now consumes `0.1.8` (`pom.xml`).

---

## Proposal: Month calendar grid (`aira-calendar`)

**Status:** Proposed
**Found during:** `EsMeetingsServlet` (`/es/meetings`) — the Topic-Space meeting calendar page.
**Date:** 2026-08-06

### Problem

The Meetings page needs a conventional seven-column month calendar: a weekday header row, day cells that hold zero or more small event cards, a current-day highlight, and an overflow affordance (`+N more`) when a day has more events than fit. `aira.css` has table, matrix-table, and card primitives, but nothing for a month/day grid, so this was built as a page-scoped local stylesheet (`css/meetings-calendar.css`).

### Current local workaround

`css/meetings-calendar.css`, loaded only by `EsMeetingsServlet`, implements the grid with `.es-meetings-calendar` (CSS Grid, 7 columns), `.es-meetings-calendar__day` (cell), `.es-meetings-calendar__card` (event card, with `--following`/`--past`/`--cancelled` modifiers), and a native `<details>` for the "+N more" overflow — all using AIRA tokens (`--aira-border`, `--aira-surface-muted`, `--aira-green-accessible`, `--aira-radius-panel`, etc.), not hard-coded values.

### Proposed shared interface

```html
<div class="aira-calendar">
  <div class="aira-calendar__weekday">Sun</div>
  ...
  <div class="aira-calendar__day aira-calendar__day--today">
    <span class="aira-calendar__date">14</span>
    <a class="aira-calendar__event aira-calendar__event--following" href="...">
      <span class="aira-calendar__event-time">2:00 PM</span>
      <span class="aira-calendar__event-title">Weekly Sync</span>
    </a>
    <details class="aira-calendar__more">
      <summary>+3 more</summary>
      ...
    </details>
  </div>
</div>
```

### Why this belongs in `aira.css`

A month calendar is a general scheduling UI pattern, not specific to meetings-within-a-topic-space — any AIRA application tracking dated events (campaigns, deadlines, office hours) would want the same grid, current-day highlight, and overflow handling. This project's implementation is generic enough to lift directly.

### Compatibility and migration impact

- Additive; no existing shared component changes.
- Once available, `EsMeetingsServlet` should switch its `es-meetings-calendar*` classes to `aira-calendar*`, and `css/meetings-calendar.css` can be deleted.

### Resolution

Pending review in the AIRA Web project.

---

## Proposal: Native dialog component

**Status:** Available in this project
**Found during:** `HomeServlet` / `SendWelcomeEmailServlet` / `MagicLinkServlet` migration (the sign-in, register, and magic-link confirmation pages) to the shared AIRA page framework.
**Date:** 2026-08-04

### Problem

The register page (`SendWelcomeEmailServlet`) shows a "More details" modal for each legal term the user must accept, rendered by `LegalTermsUiRenderer`. Before this migration it was built with a `display:none` div toggled by inline `style="position:fixed; inset:0; background:rgba(0,0,0,0.55); ..."` attributes and a hand-rolled surface. `aira.css` had no dialog/modal component, so there was no shared way to express an accessible overlay with a backdrop, a bounded surface, and focus handling.

### Current local workaround

None remaining. `LegalTermsUiRenderer` briefly used a temporary local class (`interophub-legal-term-dialog` in `css/register.css`) while this request was pending; that file was deleted once `0.1.7` was consumed.

### Proposed shared interface

```html
<dialog class="aira-dialog">
  <h3 class="aira-dialog__title">Term title</h3>
  <div class="aira-dialog__body">
    ...
  </div>
  <div class="aira-dialog__actions aira-form-actions aira-form-actions--end">
    <button type="button" class="aira-button aira-button--secondary">Close</button>
  </div>
</dialog>
```

### Why this belongs in `aira.css`

Any AIRA Web application with a confirmation prompt, "more details" popup, or a small blocking form needs the same accessible overlay primitive. The native `<dialog>` element removes the need for any shared JavaScript; applications own their own `showModal()`/`close()` triggers.

### Compatibility and migration impact

- Additive; no existing pages used a dialog before this.
- `LegalTermsUiRenderer` now renders `aira-dialog` / `aira-dialog__title` / `aira-dialog__body` / `aira-dialog__actions` directly.

### Resolution

Implemented upstream in `aira-web-components`/`aira-web-theme` `0.1.7` (see [`aira-css-changes-revision-7.md`](aira-css-changes-revision-7.md), delivered in response to the standalone request [`aira-css-request-7.md`](aira-css-request-7.md)). InteropHub now consumes `0.1.7` (`pom.xml`). `LegalTermsUiRenderer` was updated to the shared classes and the temporary `css/register.css` workaround was deleted.

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
