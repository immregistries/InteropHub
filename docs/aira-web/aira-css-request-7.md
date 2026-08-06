# AIRA Web CSS Request — Target Version 0.1.7

**Requested by:** InteropHub
**Date:** 2026-08-04
**Found during:** Migrating `HomeServlet` (`/home`), `SendWelcomeEmailServlet` (`/send-welcome-email`), and `MagicLinkServlet` (`/magic-link`) — the sign-in, register, and magic-link confirmation pages — to the shared AIRA page framework. See `docs/aira-web/Migrating Servlets to AIRA CSS.md` section 14 for the decision process this request follows.

This is a standalone request covering only the item below. It does not carry forward history from `aira-css-proposed-changes.md` — 0.1.5 and 0.1.6 are already implemented and delivered, and are tracked there.

---

## Requested change: Native dialog component (`aira-dialog`)

### Problem

The register page (`SendWelcomeEmailServlet`) shows a "More details" modal for each legal term the user must accept, rendered by `LegalTermsUiRenderer`. Before this migration it was built with a `display:none` div toggled by inline `style="position:fixed; inset:0; background:rgba(0,0,0,0.55); ..."` attributes and a hand-rolled surface (`background:#fff; max-width:760px; margin:6vh auto; ...`). `aira.css` has no dialog/modal component, so there is no shared way to express an accessible overlay with a backdrop, a bounded surface, and focus handling.

### Current local workaround

`LegalTermsUiRenderer` (`org.airahub.interophub.servlet.LegalTermsUiRenderer`) now renders a native HTML `<dialog>` element, opened/closed via `showModal()`/`close()`, which gives correct focus trapping, `Esc`-to-close, and top-layer stacking for free. Its surface and `::backdrop` are styled with a narrowly-scoped, explicitly temporary local class in `css/register.css`:

```css
/* TEMPORARY WORKAROUND, see this request */
.interophub-legal-term-dialog {
  width: min(90vw, 40rem);
  max-width: 40rem;
  padding: var(--aira-panel-padding);
  border: 1px solid var(--aira-border);
  border-radius: var(--aira-radius-panel);
  background: var(--aira-surface);
  color: var(--aira-text);
}

.interophub-legal-term-dialog::backdrop {
  background: rgba(0, 0, 0, 0.55);
}

.interophub-legal-term-dialog__text {
  white-space: pre-wrap;
}
```

This workaround is active now and uses AIRA tokens (`--aira-panel-padding`, `--aira-border`, `--aira-radius-panel`, `--aira-surface`, `--aira-text`) rather than hard-coded values, but it duplicates panel-surface styling that `aira.css` already defines for `.aira-panel`.

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

Built on the native `<dialog>` element (via `showModal()`/`close()`) so applications get correct focus management and `Esc`-to-close without any shared JavaScript. `aira.css` would only need to style `dialog[open].aira-dialog` and `.aira-dialog::backdrop`, plus optional `__title`/`__body`/`__actions` layout helpers.

### Proposed `aira.css` changes

```css
.aira-dialog {
  width: min(90vw, 40rem);
  max-width: 40rem;
  padding: var(--aira-panel-padding);
  border: 1px solid var(--aira-border);
  border-radius: var(--aira-radius-panel);
  background: var(--aira-surface);
  color: var(--aira-text);
}

.aira-dialog::backdrop {
  background: rgba(0, 0, 0, 0.55);
}

.aira-dialog__title {
  margin: 0 0 var(--aira-space-2);
}

.aira-dialog__body {
  white-space: pre-wrap;
}
```

### Why this belongs in `aira.css`

Any AIRA Web application with a confirmation prompt, "more details" popup, or a small blocking form (not just InteropHub's legal-terms acceptance) will need the same accessible overlay primitive. Hand-rolling `position:fixed` overlays per application is exactly the kind of general-purpose visual component this project exists to centralize, and the native `<dialog>` element removes the need for any shared JavaScript.

### Compatibility and migration impact

- No existing pages use a dialog today, so adding `.aira-dialog` is additive and has no migration impact elsewhere.
- Once available, `LegalTermsUiRenderer` should switch its `<dialog>` elements from `interophub-legal-term-dialog` to `aira-dialog`, and `css/register.css`'s temporary block can be deleted entirely.

### Resolution

Pending review in the AIRA Web project, target version 0.1.7.
