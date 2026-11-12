# AIRA Web CSS Request — Target Version 0.1.15

**Requested by:** InteropHub
**Date:** 2026-11-12
**Found during:** The "Send info / question" dialog on the topic page (`EsTopicDetailServlet`). A contractor reported that after clicking Send there was no visible confirmation. InteropHub now shows an `aira-alert--success` result inside the `aira-dialog`, with Close / Send another. Once the dialog closes, a brief notice stays on the page and hides itself after 45 seconds. That page notice has no shared component.

Both changes are queued as proposals in `aira-css-proposed-changes.md`.

---

## Requested change 1: Page notice that hides itself (`aira-toast`)

### Problem

`aira-alert` is an in-flow block. After a dialog closes, or after an inline action with no room for an alert, an application needs a short floating notice: "Your message was sent." There is nothing shared for this.

### Current local workaround (to be removed once this ships)

`src/main/webapp/css/hub-toast.css`:

```css
.hub-toast {
  position: fixed;
  right: var(--aira-space-4);
  bottom: var(--aira-space-4);
  z-index: var(--aira-z-overlay);
  display: flex;
  align-items: flex-start;
  gap: var(--aira-space-3);
  max-width: min(28rem, calc(100vw - 2 * var(--aira-space-4)));
  box-shadow: var(--aira-shadow-dialog);
}
.hub-toast[hidden] { display: none; }
.hub-toast > p { flex: 1 1 auto; align-self: center; }
.hub-toast > .aira-button { flex: 0 0 auto; }
```

Markup:

```html
<div class="hub-toast aira-alert aira-alert--success" hidden>
  <p>✓ Your message was sent to this topic's contacts.</p>
  <button type="button" class="aira-button aira-button--ghost aira-button--small" aria-label="Dismiss notice">&times;</button>
</div>
```

### Proposed shared interface

```html
<div class="aira-toast-region" role="status" aria-live="polite">
  <div class="aira-toast aira-toast--success">
    <p class="aira-toast__message">Your message was sent.</p>
    <button type="button" class="aira-toast__dismiss" aria-label="Dismiss notice">&times;</button>
  </div>
</div>
```

- `aira-toast-region`: a fixed stack in the bottom-right corner (bottom-center or full-width on narrow screens), above page content and the sticky header, and below modal dialogs. A modal `<dialog>` is in the top layer anyway. The region is empty and takes no space when it has no toasts.
- `aira-toast`: an elevated surface reusing the `aira-alert` variant colors (`--info`, `--success`, `--warning`, `--error`), with a message and an optional dismiss button.
- `[hidden]` must hide it.
- Optional enter/leave motion, turned off under `prefers-reduced-motion`.
- Document in the components guide: the application owns the timer and the show/hide JavaScript (same contract as `aira-dialog`). Recommend pausing on hover/focus, a dismiss button, and a duration of 30–60 seconds for confirmations. A toast should repeat a confirmation, never carry the only copy of an error.

Choose the exact class names and positioning. Note any deviation in the revision notes.

### Why shared

Every AIRA application that saves from a dialog or an inline control needs the same confirmation.

### Compatibility

Additive.

---

## Requested change 2: `hidden` attribute wins over component `display`

### Problem

`aira-stack`, `aira-cluster`, `aira-form-actions` and others set `display`, which beats the browser's `[hidden] { display: none }`. `<div class="aira-form-actions" hidden>` stays visible. Only `aira-search-popover[hidden]` handles this today.

### Current local workaround

Plain `<div hidden>` wrappers around the `aira-*` blocks in the topic page dialog.

### Proposed shared interface

A base rule such as:

```css
[hidden]:not([hidden="until-found"]) { display: none !important; }
```

or per-component `[hidden]` rules if you'd rather not use a global `!important`. Document the choice.

### Why shared

Toggling `hidden` is the documented show/hide contract for AIRA popovers, and applications reasonably expect it to work on every component.

### Compatibility

Pages that currently put `hidden` on an `aira-*` element and rely on it still showing would change. That would be a bug in those pages; none are known in InteropHub.
