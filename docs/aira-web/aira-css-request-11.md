# AIRA Web CSS Request — Target Version 0.1.11

**Requested by:** InteropHub
**Date:** 2026-09-14
**Found during:** Design analysis for `docs/interophub-meeting-cadence-design.md` — a shared "action queue" of meeting-lifecycle steps (publish agenda, finalize agenda, close meeting, publish notes) that authorized users need to complete on a fixed cadence. The design calls for a visible signal in the global header when a signed-in user has at least one unresolved action, so the request isn't missed on a page that isn't the dashboard.

This is a standalone request covering the one blocking gap found. Unlike prior requests (`aira-css-request-7.md`/`8.md`/`9.md`), which were pure CSS/markup gaps, this one also needs a small `aira-web-components` Java API change, because InteropHub has no way to inject markup into the global header itself — it is rendered entirely by `AiraPage.writeGlobalHeader()` from config objects InteropHub supplies (`AiraSearchConfig`, `AiraActionItem` list, `AiraAccountConfig`, `AiraEnvironmentConfig`). There is no raw-HTML injection point.

## Summary of the pre-implementation analysis

Comparing the need against the current `aira-web-components` 0.1.10 API and `aira.css`:

| Design need | Existing coverage |
|---|---|
| A clickable header entry pointing at the action queue | `Builder.addGlobalAction(label, href, variant)` → `AiraActionItem` — already exists, renders as a plain `<a class="aira-button aira-button--{variant}">` |
| A due/overdue-vs-normal visual distinction | `aira-button` variants (`primary, secondary, tertiary, success, danger, ghost, link`) already cover this — `primary` for due, `danger` for overdue, no new variant needed |
| **A count indicator on that entry** | **Missing.** `AiraActionItem` carries only `label`/`href`/`variant` — no way to render a number/dot without baking it into the label text (e.g. `"3 actions needed"`), which reads fine but isn't a real badge and doesn't visually match the pill-badge language `aira.css` already uses elsewhere (`.aira-badge`, `--aira-radius-pill`) |
| Hiding the entry entirely when there's nothing to show | Already possible today — InteropHub just omits the `addGlobalAction` call. No gap. |

**The one gap is a badge/count affordance on a global header action item.** This is a general pattern — any AIRA application with a due-count, pending-approval-count, or unread-count in its header would want the same thing (a header button that also conveys "how many"), not something meeting/agenda-specific to InteropHub.

## Current local workaround

None. Baking the count into the button's label text (`"3 actions needed"`) is possible without any upstream change and InteropHub may ship that as an interim v1 while this request is pending, but it is a lesser fallback, not a substitute for the badge itself — it can't be dropped in in place once this ships, since the label text and the counting logic are coupled together.

## Requested change: optional badge count on `AiraActionItem` / global header action

### Problem

`AiraActionItem` (`aira-web-components`, `org.immregistries.aira.web.AiraActionItem`) is a 3-field record (`label`, `href`, `variant`) with no slot for a count, and `AiraPage.writeGlobalHeader()` renders each one as a single `<a class="aira-button aira-button--{variant}">{label}</a>` with no badge markup. There is no shared class anywhere in `aira.css` that turns a number into a small pill sitting on/beside a button or icon in the header — `.aira-environment-badge` is the closest visual precedent but is plain unstyled-count text ("Local"), not a numeric pill.

### Proposed shared interface

**Java (`aira-web-components`):**

```java
// AiraActionItem — add an optional 4th component, defaulting to "no badge"
public record AiraActionItem(String label, String href, String variant, Integer badgeCount) {
  public AiraActionItem(String label, String href, String variant) {
    this(label, href, variant, null);
  }
  // existing validation on label/href/variant unchanged;
  // badgeCount, if present, must be > 0 (0/negative should just mean "no badge", not render a zero)
}
```

```java
// AiraPage.Builder — new overload alongside the existing one
public Builder addGlobalAction(String label, String href, String variant, int badgeCount) {
  this.globalActions.add(new AiraActionItem(label, href, variant, badgeCount));
  return this;
}
// existing addGlobalAction(label, href, variant) unchanged, delegates with badgeCount = null
```

**Rendered markup**, in `AiraPage.writeGlobalHeader()`:

```html
<nav class="aira-global-actions" aria-label="Global actions">
  <a class="aira-button aira-button--primary aira-button--has-badge" href="/welcome">
    Action needed
    <span class="aira-badge-count" aria-hidden="true">3</span>
  </a>
</nav>
```

The count is also readable to assistive tech without a separate visually-hidden node — put it in the visible label text too (e.g. `aria-label="Action needed, 3 pending"` on the `<a>`, with `aria-hidden="true"` on the visual `.aira-badge-count` span so it isn't announced twice). Exact ARIA wiring is open to the AIRA Web project's judgment.

### Proposed `aira.css` changes

```css
.aira-button--has-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--aira-space-2);
}

.aira-badge-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.25rem;
  height: 1.25rem;
  padding: 0 0.25rem;
  border-radius: var(--aira-radius-pill);
  background: var(--aira-surface);
  color: inherit;
  font-size: 0.6875rem;
  font-weight: 700;
  line-height: 1;
}
```

`.aira-badge-count` deliberately uses `background: var(--aira-surface)` / `color: inherit` rather than a fixed color, so it reads as a light "count chip" against whatever button variant it's placed on (`primary`, `danger`, etc.) instead of introducing a second competing accent color. If the AIRA Web project prefers a fixed high-contrast chip instead (e.g. always white-on-color), that's a fine alternative — InteropHub has no strong preference on the exact visual treatment, only on the underlying capability (a count can be attached to a header action item at all).

### Why this belongs in `aira-web`

A numeric badge on a header action/button is a generic UI affordance, not specific to InteropHub's meeting cadence — any AIRA application with a pending-count, unread-count, or approval-count would want the same primitive. It's additive to an existing component (`AiraActionItem`/`addGlobalAction`) rather than a new one, in the same spirit as the `aira-table-panel` accent-border modifiers (0.1.8) being an additive extension of an existing component rather than a new component.

### Compatibility and migration impact

- Additive on both sides. The existing 3-arg `AiraActionItem` constructor and 3-arg `addGlobalAction(label, href, variant)` builder method are unchanged and continue to render with no badge, exactly as today.
- No existing InteropHub page currently uses `addGlobalAction` for anything the badge would attach to, so there is nothing to migrate.
- Once available, InteropHub calls the new 4-arg overload only when the signed-in user has ≥1 pending action, and omits the header action entirely otherwise (unchanged behavior from today).

### Resolution

Implemented upstream in `aira-web-components`/`aira-web-theme` `0.1.11` (see [`aira-css-changes-revision-11.md`](aira-css-changes-revision-11.md)). InteropHub now consumes `0.1.11` (`pom.xml`). Shipped with the light "count chip" styling and single-`aria-label` approach this request proposed, unchanged.
