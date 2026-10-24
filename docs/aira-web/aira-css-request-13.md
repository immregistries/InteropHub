# AIRA Web CSS Request — Target Version 0.1.13

**Requested by:** InteropHub
**Date:** 2026-10-24
**Found during:** Reviewing the first InteropHub Initiatives pages (`/es/initiatives/{code}/settings`, `/organizations`, `/people`). These are management pages where staff add and update dozens of organizations and people. As built, every form uses `aira-form` inside a wide `aira-panel`, and the result was hard to use: every button and field spans the full panel width (about 1100px). Nathan's feedback: "It's so hard to read with large wide fields and wide buttons. Just lots of horizontal lines."

This is a standalone request, following the per-version pattern of `aira-css-request-7.md` through `aira-css-request-11.md`.

## Summary of the analysis

Most of what the redesigned pages need is already in `aira.css` 0.1.12:

| Page need | Existing coverage |
|---|---|
| Compact list of organizations or people, with an "Add" button in the header | `aira-table-panel` + `__actions`, `aira-table`, `aira-table__cell--actions` |
| Small row actions | `aira-button--small`, `aira-button--tertiary` / `--ghost` |
| "Add" and "Change status" in a modal | `aira-dialog`, with the form composed inside it as `EsMeetingWorkspaceServlet` already does (`form.aira-stack.aira-stack--compact` instead of `aira-dialog__body`) |
| Two or three short fields side by side | `aira-field-row` |
| Button groups | `aira-form-actions` |

Three gaps remain. None of them is specific to Initiatives: any AIRA application with an admin or management form will run into them.

---

## Requested change 1: Buttons placed directly in `aira-form` keep their natural width

### Problem

`.aira-form` is `display: grid`. Grid items stretch to the full width of the column by default, so a button that is a direct child of the form becomes a full-width bar:

```html
<form class="aira-form">
  <div class="aira-field">...</div>
  <button class="aira-button aira-button--secondary">Update status</button>  <!-- full width -->
</form>
```

It looks normal only when it is wrapped in `aira-form-actions`. That is easy to forget. The first Initiatives pages forgot it on almost every form, and nothing in the guide warns about it. A full-width button is almost never what a design wants, so the safe default belongs in the shared CSS rather than in each page.

### Proposed `aira.css` change

```css
.aira-form > .aira-button,
.aira-form > a.aira-button {
  justify-self: start;
}
```

### Compatibility

- InteropHub has about 60 other `aira-form` uses. Any page that put a button directly in the form will now show a natural-width button. That is the intended result, but the aira-web demo pages and a sample of InteropHub pages should be checked after the change.
- Nothing changes for buttons inside `aira-form-actions`.
- **Open question for the aira-web agent:** `aira-stack` is also a grid and stretches direct-child buttons in the same way. Decide whether the same rule belongs on `.aira-stack > .aira-button`, or whether stretching there is relied on (for example, in narrow sidebars). If you leave it out, note why in the revision notes.

---

## Requested change 2: Inline form variant for one-line actions and filter bars

### Problem

Management pages repeatedly need a short form on one line: a filter bar (Status / Organization / Type / Filter), or a quick action like "status + note + Save". `aira-form` always stacks fields vertically, and `.aira-input` / `.aira-select` are `width: 100%`. So a three-control action takes three full-width rows plus a label above each one.

InteropHub currently builds filter bars by hand from `aira-cluster` inside `aira-form`, and those still inherit the 100% control widths.

### Proposed shared interface

```html
<form class="aira-form aira-form--inline" method="get">
  <div class="aira-field">
    <label for="f-status">Status</label>
    <select class="aira-select" id="f-status" name="status">...</select>
  </div>
  <div class="aira-field aira-field--grow">
    <label for="f-note">Note</label>
    <input class="aira-input" id="f-note" name="note" type="text">
  </div>
  <button class="aira-button aira-button--secondary" type="submit">Save</button>
</form>
```

### Proposed `aira.css` changes

```css
.aira-form--inline {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--aira-space-2) var(--aira-space-3);
}

.aira-form--inline > .aira-field {
  flex: 0 1 auto;
}

.aira-form--inline .aira-input,
.aira-form--inline .aira-select {
  width: auto;
  min-width: 10rem;
  max-width: 100%;
}

.aira-form--inline > .aira-field--grow {
  flex: 1 1 16rem;
}

.aira-form--inline > .aira-field--grow .aira-input,
.aira-form--inline > .aira-field--grow .aira-select {
  width: 100%;
}
```

Buttons line up with the bottom of the controls because of `align-items: flex-end`. On narrow screens the items wrap. The exact values are suggestions; adjust them to fit the existing spacing tokens.

---

## Requested change 3: A readable maximum width for stacked forms

### Problem

A stacked `aira-form` inside an `aira-panel` on a wide page makes every text field 1000px or more wide, even for a name, a code or a date. Long, thin fields are hard to scan, and the form reads as a series of horizontal lines. Pages need an easy way to say "this is an ordinary data-entry form, keep it a readable width" without writing local CSS.

### Proposed shared interface and `aira.css` change

```css
:root {
  --aira-form-max-width: 44rem;
}

.aira-form--narrow {
  max-width: var(--aira-form-max-width);
}
```

```html
<form class="aira-form aira-form--narrow">...</form>
```

Short fields can still sit side by side with `aira-field-row`. A modifier, rather than a change to `.aira-form` itself, means no existing page changes width unexpectedly.

---

## Requested documentation change: a "Forms" section in `components-guide.md`

The guide documents buttons, tables and dialogs, but has no Forms section. Please add one that covers:

- `aira-form`, `aira-field`, `aira-field-row`, `aira-form-actions`, plus the new `--inline` and `--narrow` variants;
- the guidance that buttons go in `aira-form-actions` (or, after change 1, directly in the form) and never stretch;
- **a form inside a dialog:** compose the form directly inside `<dialog class="aira-dialog">` (as a compact `aira-stack`) and don't use `aira-dialog__body`, because its `white-space: pre-wrap` is meant for text content and shows the markup's indentation inside a form. A short example would prevent the next page from making that mistake;
- **the management-list pattern:** an `aira-table-panel` with an "Add" button in `__actions`, rows linking to a detail page, and add/edit in an `aira-dialog`. This is the layout InteropHub is moving the Initiatives roster to, and it suits any admin list.

---

## Delivery

- Bump `aira-web-components` / `aira-web-theme` to `0.1.13` and `mvn install` locally.
- Update the aira-web demo to show the inline form, the narrow form and a form dialog.
- Write `aira-css-changes-revision-13.md` (same style as revision 12), stating what shipped, any choices that differ from this request, and whether InteropHub markup needs to change.
