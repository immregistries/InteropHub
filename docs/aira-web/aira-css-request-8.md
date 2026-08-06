# AIRA Web CSS Request — Target Version 0.1.8

**Requested by:** InteropHub
**Date:** 2026-08-06
**Found during:** Pre-migration analysis of `EsAgendaServlet` (`/es/agenda`) and its linked attendance sign-in page, `EsMeetingAttendanceServlet` (`/attend/{topicCode}[/{meetingKey}]`), toward the shared AIRA page framework — matching the shell already used by the Topics and Meetings pages. See `docs/aira-web/Migrating Servlets to AIRA CSS.md` section 14 for the decision process this request follows.

This is a standalone request covering only the item below, per the per-version request-file pattern established with `aira-css-request-7.md`. No servlet code has been changed yet — this is the "propose gaps before migrating" step for the agenda/attendance migration, deliberately done before any `AiraPage` conversion work.

## Summary of the pre-migration analysis

Both pages are currently fully hand-rolled (own `<!DOCTYPE>`/`<head>`/`<link rel="stylesheet" href=".../css/main.css">`, no `AiraPage`), with `EsAgendaServlet` carrying an entire ~330-line inline `<style>` block plus scattered per-element inline `style=` attributes, and `EsMeetingAttendanceServlet` relying on `.attend-*` rules already centralized in `main.css`.

Comparing every legacy pattern in both pages against the current `aira.css` (0.1.7) and `components-guide.md`, nearly everything needed for the migration already exists:

| Legacy pattern | Existing `aira.css` coverage |
|---|---|
| Panels (`.agenda-meta`, `.agenda-description`, `.agenda-attendance`) | `.aira-panel`, `.aira-section-card` |
| Status badges (Draft/Proposed/Finalized/In Session/Completed/Cancelled) | `.aira-badge` + `--outline`/`--subtle`/`--info`/`--success`/`--warning`/`--danger` — components-guide.md already documents a baseline status-to-variant mapping that fits this lifecycle |
| Agenda items table, prior-meeting tables, attendee table | `.aira-table`, `.aira-table-wrap`, `.aira-table-panel` |
| Flash/duration/cancellation messages | `.aira-alert` + semantic variants |
| Meeting metadata rows (Meeting/Date/Time/Status) | `.aira-meta-chip` |
| "Next Meeting" / "All Meetings" footer links | `.aira-meeting-row` |
| Role picker toggle buttons, quick-pick chips | `.aira-segmented-control`, `.aira-chip`/`.aira-tag` |
| Presenter-edit / add-presenter popovers (currently a hand-rolled `position:fixed` panel) | `.aira-dialog` (native `<dialog>`, shipped in 0.1.7) — a direct, better replacement |
| Forms, fields, checkboxes, fieldsets (attendance registration form, topic-interest checklist) | `.aira-form`, `.aira-field`, `.aira-check`, `.aira-fieldset` |
| Print behavior (`.no-print`) | `.aira-no-print`/`.aira-print-only` + an existing `@media print` block — same concept, just a class rename during migration |
| Local-environment corner ribbon (`.env-ribbon`) | Already superseded by `AiraPage`'s built-in header environment badge (`AiraEnvironmentConfig`) — not a gap, just removed on migration |

Click-to-edit show/hide toggling and the topic/presenter autocomplete widgets are app-owned JavaScript behavior with no CSS dependency — consistent with `components-guide.md`'s statement that this release intentionally excludes shared JavaScript, drag-and-drop, and dialog-open behavior beyond the native `<dialog>` contract itself. These will be preserved as local script, same as the existing `aira-dialog` open/close pattern already documented for other pages.

**The one gap found:**

---

## Requested change: Semantic accent-border modifiers for `aira-table-panel`

### Problem

Two sections on the agenda page need a colored, semantically-flagged callout that *contains a data table*, not just text:

- **Open Items** (`.open-items-section`) — a left-border-orange callout listing postponed/not-covered/needs-revision agenda items carried forward from prior meetings, each row with a "re-add to agenda" action.
- **Curated Topic Cadence** (`.curated-cadence-section`) — a left-border-red callout listing topics overdue (or due soon) for discussion per a configured cadence, each row with a quick-add action.

`.aira-alert` provides exactly this left-border accent-color language, but it's a text/paragraph container, not a table host. `.aira-table-panel` provides exactly the header/title/description/table structure needed, but has no semantic accent-border option — it's neutral. Neither component alone covers "a table that needs to visually read as a warning/urgency callout at a glance."

### Current local workaround

Bespoke inline CSS per section in `EsAgendaServlet`'s `renderAgendaStyles()`:

```css
.open-items-section {
  border-left: 4px solid #f59e0b; /* hardcoded amber */
  padding-left: 0.75rem;
}
.curated-cadence-section {
  border-left: 4px solid #dc2626; /* hardcoded red */
  padding-left: 0.75rem;
}
```
with an ordinary `<table class="prev-items-table">`/similar nested inside — no shared table-panel structure at all today.

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
    <table class="aira-table">
      ...
    </table>
  </div>
</section>
```

`--danger` would follow the same pattern for the Curated Topic Cadence section. `--info`/`--success` are proposed alongside for symmetry with `.aira-alert`'s existing variant set, even though this request's immediate need is only `--warning`/`--danger` — so the modifier reads as one consistent semantic family rather than two one-off classes.

### Proposed `aira.css` changes

```css
.aira-table-panel--info,
.aira-table-panel--success,
.aira-table-panel--warning,
.aira-table-panel--danger {
  border-left: 4px solid;
  padding-left: var(--aira-space-3);
}

.aira-table-panel--info { border-color: var(--aira-focus); }
.aira-table-panel--success { border-color: var(--aira-success); }
.aira-table-panel--warning { border-color: var(--aira-yellow); }
.aira-table-panel--danger { border-color: var(--aira-danger); }
```
(Token names to be confirmed against whatever `.aira-alert--info/--success/--warning/--error` already use internally — the intent is to reuse those same tokens, not introduce new colors.)

### Why this belongs in `aira.css`

"A flagged/urgent list of items needing attention" is a common cross-application pattern (overdue items, flagged records, items needing review), not specific to meeting agendas. It's a direct, minimal extension of an existing component — an accent-border modifier — not a new component and not merely a recolored duplicate of something that already exists standalone (the gap is specifically the *combination* of the alert accent-border language with the table-panel structure, which no existing single component provides).

### Compatibility and migration impact

- Additive; existing unmodified `.aira-table-panel` usage is unaffected.
- Once available, `EsAgendaServlet`'s Open Items and Curated Topic Cadence sections adopt `aira-table-panel--warning`/`--danger` and the bespoke inline border-left CSS is deleted.

### Resolution

Pending review in the AIRA Web project, target version 0.1.8.
