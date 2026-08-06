# AIRA Web CSS Request — Target Version 0.1.9

**Requested by:** InteropHub
**Date:** 2026-08-06
**Found during:** Implementing the InteropHub global header search (`docs/InteropHub_Global_Search_Design.md`) — a search-as-you-type suggestion panel anchored beneath the existing header search field (`.aira-global-search` / `.aira-search-form` / `.aira-search-input`), plus a shareable full results page.

This is a standalone request covering the one blocking gap found, per the per-version request-file pattern established with `aira-css-request-7.md` / `aira-css-request-8.md`. No InteropHub search code has been written yet — per the task instructions, implementation is deliberately paused until this request is resolved and consumed.

## Summary of the pre-implementation analysis

The design calls for two surfaces:

1. A **search-as-you-type suggestion panel** that opens beneath the header search input after two characters, grouped into Topics / Upcoming meetings / Previous meetings, each entry showing title + metadata + an optional match-explanation line, fully keyboard-navigable (arrow keys, Enter, Escape) with ARIA combobox/listbox semantics, plus a trailing "View all results" action and inline loading/empty messaging.
2. A **full results page** grouped into the same three sections with in-place "Show N more" expansion.

Comparing both against the current `aira.css` (0.1.8) and `components-guide.md`:

| Design need | Existing `aira.css` coverage |
|---|---|
| Header search field itself | `.aira-global-search`, `.aira-search-form`, `.aira-search-input` — already in use via `AiraSearchConfig` |
| Full-results section grouping (Topics / Upcoming / Previous) | `.aira-section-card` per group |
| Individual result rows on the full results page | `.aira-choice-row` (title/meta/description/actions, `[aria-selected]` support) |
| Stage / status labels | `.aira-badge` + semantic variants |
| Topic Space attribution | `.aira-meta-chip` or `.aira-tag` |
| "Show N more" in-place expansion | A local `<button class="aira-button aira-button--link">` toggling pre-rendered rows — no shared CSS gap, same pattern as any progressive-disclosure control |

Everything needed for the **full results page** already exists as a composition of shared primitives. No gap there.

**The one gap is the suggestion panel itself** — a floating, anchored, grouped, keyboard-navigable combobox/listbox popover. `aira.css` has never shipped this pattern; it does not exist for any current InteropHub feature, so there is no precedent to reuse.

---

## Requested change: Search suggestion / combobox popover component

### Problem

`aira.css` provides no floating/anchored overlay panel other than the native `<dialog>` (`aira-dialog`, 0.1.7), which is a centered modal — wrong pattern for a panel that must appear *attached to* an input while the rest of the page stays interactive. Specifically missing:

- **Anchoring:** `.aira-global-search` has no `position: relative`, so there is no positioning context for a panel to sit directly beneath the input.
- **Elevated floating surface:** `--aira-shadow-elevated` and `--aira-z-overlay` are already defined as tokens but are not consumed by any current component — there is no `.aira-*` class that turns them into an actual floating popover surface (border, radius, max-height, internal scroll).
- **Grouped listbox structure:** nothing expresses a labeled group heading followed by a set of selectable option rows inside a popover (`role="group"` / `role="option"` friendly markup hooks). `.aira-choice-list` + `.aira-choice-row` come closest but assume a full-width page section, generous panel padding, and a leading control column (checkbox/handle) — too spacious for a dense, scrollable, 9-row dropdown.
- **Compact option row with a match-explanation line:** suggestion rows need a title line, one or two secondary metadata lines, and an optional third "why this matched" line (e.g. "Agenda match: Certificate Management"), at a materially denser padding/line-height than `.aira-choice-row__title`/`__meta`/`__description`.
- **Footer action row:** the "View all results for '{query}'" row is part of the same keyboard-navigable roving selection as the option rows above it, but reads as an action, not a data row — no shared row style expresses that distinction.
- **Inline status row:** "No matching topics or meetings" / a loading indicator need to render inside the popover without the padding, border, and shadow of `.aira-empty-state`, which is designed as a standalone page-level block.
- **Matched-term highlighting:** nothing styles `<mark>`/a highlight utility. Unstyled `<mark>` falls back to the browser default (yellow-on-black), which does not match the AIRA palette.

### Current local workaround

None. Per the task instructions, InteropHub is not implementing a temporary local substitute for this — the search-as-you-type panel is paused entirely until this component is available upstream, rather than building a page-specific popover that would need to be torn out later.

### Proposed shared interface

```html
<div class="aira-global-search aira-search--has-suggestions">
  <form class="aira-search-form" role="search">
    <input
      class="aira-search-input"
      type="text"
      name="q"
      role="combobox"
      aria-expanded="true"
      aria-controls="global-search-listbox"
      aria-activedescendant="global-search-option-3"
      autocomplete="off">
  </form>

  <div class="aira-search-popover" id="global-search-popover">
    <div class="aira-search-listbox" id="global-search-listbox" role="listbox">
      <div class="aira-search-group" role="group" aria-label="Topics">
        <p class="aira-search-group__label">Topics</p>
        <a class="aira-search-option" id="global-search-option-1" role="option" href="/topics/certificate-management">
          <span class="aira-search-option__title">Certificate Management</span>
          <span class="aira-search-option__meta">Emerging Standards · Gathering Information</span>
        </a>
      </div>

      <div class="aira-search-group" role="group" aria-label="Upcoming meetings">
        <p class="aira-search-group__label">Upcoming meetings</p>
        <a class="aira-search-option" id="global-search-option-3" role="option" aria-selected="true" href="/meetings/focus-group-2026-08-14">
          <span class="aira-search-option__title">Immunization Focus Group</span>
          <span class="aira-search-option__meta">August 14, 2026 · 9:25 AM MDT · Emerging Standards</span>
          <span class="aira-search-option__match">Agenda match: Certificate Management</span>
        </a>
      </div>
    </div>

    <a class="aira-search-option aira-search-option--action" role="option" href="/search?q=certificate+management">
      View all results for &ldquo;certificate management&rdquo;
    </a>

    <!-- or, when there are no matches: -->
    <p class="aira-search-status">No matching topics or meetings</p>
  </div>
</div>
```

### Proposed `aira.css` changes

```css
.aira-global-search {
  position: relative;
}

.aira-search-popover {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: var(--aira-space-1);
  background: var(--aira-surface);
  border: 1px solid var(--aira-border);
  border-radius: var(--aira-radius-panel);
  box-shadow: var(--aira-shadow-elevated);
  z-index: var(--aira-z-overlay);
  max-height: 24rem;
  overflow-y: auto;
}

.aira-search-listbox {
  display: grid;
}

.aira-search-group + .aira-search-group {
  border-top: 1px solid var(--aira-border-subtle);
}

.aira-search-group__label {
  margin: 0;
  padding: var(--aira-space-2) var(--aira-space-3) 0;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--aira-text-secondary);
}

.aira-search-option {
  display: grid;
  gap: 0.125rem;
  padding: var(--aira-space-2) var(--aira-space-3);
  color: inherit;
  text-decoration: none;
}

.aira-search-option:hover,
.aira-search-option[aria-selected="true"] {
  background: var(--aira-surface-selected);
}

.aira-search-option__title {
  font-size: 0.9375rem;
  font-weight: 600;
}

.aira-search-option__meta {
  font-size: 0.8125rem;
  color: var(--aira-text-secondary);
}

.aira-search-option__match {
  font-size: 0.8125rem;
  color: var(--aira-text-secondary);
  font-style: italic;
}

.aira-search-option--action {
  border-top: 1px solid var(--aira-border-subtle);
  font-weight: 600;
  color: var(--aira-link);
}

.aira-search-status {
  margin: 0;
  padding: var(--aira-space-3);
  text-align: center;
  color: var(--aira-text-secondary);
}

mark,
.aira-text-highlight {
  background: var(--aira-warning-surface);
  color: inherit;
  border-radius: 2px;
  padding: 0 0.125em;
}
```

Class names, exact tokens, and the `role="option"` vs. `role="listbox"` markup contract are open to revision by the AIRA Web project — this is a starting proposal, not a required final shape.

### Why this belongs in `aira.css`

Any AIRA application with more than a handful of records will eventually want type-to-navigate search (a combobox anchored to a text input, opening a scrollable, grouped, keyboard-navigable popover of results). This is a general interaction pattern — not a meetings-and-topics-specific one — in the same way `aira-dialog` (0.1.7) generalized "a small blocking overlay" beyond InteropHub's legal-terms use case. Building it as InteropHub-local CSS would fork exactly the kind of general popover/listbox primitive that `aira-web` is meant to own, and it reuses two tokens (`--aira-shadow-elevated`, `--aira-z-overlay`) that already exist in the theme but currently have no consumer.

### Compatibility and migration impact

- Additive. `.aira-global-search { position: relative; }` has no visible effect on the current plain search form and does not change existing layout.
- No existing InteropHub page uses a popover pattern today, so there is nothing to migrate away from.
- Once available, InteropHub's suggestion panel markup adopts `aira-search-popover` / `aira-search-listbox` / `aira-search-group` / `aira-search-option` / `aira-search-status` directly; InteropHub's local CSS and JavaScript own only the debounce, fetch, ARIA state management (`aria-expanded`, `aria-activedescendant`), and keyboard handling.

### Resolution

Pending review in the AIRA Web project, target version 0.1.9.
