# Migrating Legacy Servlets from `main.css` to the AIRA Page Framework

## Purpose

This guide describes how to migrate an existing servlet that renders a legacy page using `main.css` into the shared AIRA page framework using:

- `AiraPage`
- `InteropAiraPageFactory`
- `aira.css`
- The shared page header and footer generators
- A narrowly scoped local stylesheet only when the page has application-specific needs

The goal is not to redesign the page. The goal is to replace legacy shell, layout, and component styling with the shared AIRA implementation while preserving page behavior.

---

## Migration principles

1. **Leave `main.css` unchanged.**  
   It remains available for pages that have not yet been migrated.

2. **Migrate one servlet at a time.**  
   A migrated servlet should no longer load or depend on `main.css`.

3. **Use `AiraPage` to render the document shell.**  
   Do not manually reproduce the document start, shared header, account area, search, environment badge, footer, or closing tags.

4. **Use AIRA classes for general-purpose presentation.**  
   Layouts, panels, buttons, forms, tables, alerts, badges, spacing, and typography should come from `aira.css`.

5. **Keep feature-specific classes when they represent real application behavior.**  
   A prefix such as `mw-`, `es-`, or another application prefix is not automatically legacy. Keep classes that identify a specialized editor, interactive widget, JavaScript hook, or domain-specific display.

6. **Do not recreate AIRA components in local CSS.**  
   Local CSS should not define alternative page backgrounds, fonts, generic panels, buttons, form controls, tables, headers, or footers.

7. **Promote reusable gaps back into `aira.css`.**  
   When a migrated page exposes a general component that other applications would reasonably use, add that component to the shared stylesheet rather than duplicating it locally.

---

# 1. Understand the new rendering model

A migrated servlet builds an `AiraPage` inside `doGet`.

The shared page is initialized through:

```java
InteropAiraPageFactory.base(...)
```

The factory supplies the common application shell inputs, including:

- Application name
- Context path
- AIRA logo
- Account menu
- Global search
- Optional environment indicator

The servlet then applies page-specific options, such as:

- Browser page title
- Application subtitle
- Context title
- Context navigation
- Container width
- Page-specific stylesheet
- Other supported page options

For context navigation, use the centralized helper in the page factory instead of building per-servlet navigation lists.

The rendering sequence is:

```java
page.writeStart(out);
renderPageContent(request, response, out);
page.writeEnd(out);
```

Conceptually:

```java
@Override
protected void doGet(
    HttpServletRequest request,
    HttpServletResponse response
) throws ServletException, IOException {

    response.setContentType("text/html;charset=UTF-8");

    try (PrintWriter out = response.getWriter()) {
        AiraPage page = InteropAiraPageFactory.base(
            request,
            "Application Name"
        );

        // Apply page-specific configuration here.
        // Use the actual methods supported by AiraPage.
        //
        // Examples:
        // page.setTitle("Page Title");
        // page.setAppSubtitle("Administration");
        // page.addStylesheet("/css/example-page.css");
        // page.setContextTitle("Context Name");
        // page.addContextNavigation(...);

        page.writeStart(out);
        renderPageContent(request, response, out);
        page.writeEnd(out);
    }
}
```

Use the actual factory and `AiraPage` method signatures in the project. The important pattern is:

1. Build and configure the page.
2. Call `writeStart`.
3. Render only the page-specific content.
4. Call `writeEnd`.

## Shared Topics and Meetings header context

To keep the application header context consistent across migrated servlets, use the shared helper on `InteropAiraPageFactory`:

```java
InteropAiraPageFactory.topicsMeetingsContext(
  contextLabel,
  spaceCode,
  topicsActive,
  meetingsActive
)
```

Parameters:

- `contextLabel`: text shown in the context bar (for example, the topic space name)
- `spaceCode`: current topic-space code; used to build the meetings link
- `topicsActive`: whether Topics is the active context item
- `meetingsActive`: whether Meetings is the active context item

The shared helper standardizes context navigation to exactly:

- Topics: `/es/topics`
- Meetings: `/es/topics?space={spaceCode}&view=meetings` (or `/es/topics?view=meetings` when space is unavailable)

Do not hardcode alternate items such as Workspace, Tables, Components, or one-off per-page context menus for these servlet migrations.

### Example: Topic detail page

```java
AiraPage page = InteropAiraPageFactory.base(request, topicName + " - InteropHub")
  .applicationSubtitle("Topic display")
  .mainClass("aira-main")
  .context(InteropAiraPageFactory.topicsMeetingsContext(
    topicSpace.getSpaceName(),
    topicSpace.getSpaceCode(),
    true,
    false))
  .build();
```

### Example: Meeting workspace page

```java
AiraPage page = InteropAiraPageFactory.base(request, meetingName + " - Meeting Workspace")
  .applicationSubtitle("Meeting Workspace")
  .mainClass("aira-main interophub-meeting-workspace-main")
  .context(InteropAiraPageFactory.topicsMeetingsContext(
    meetingSpaceName,
    meetingSpaceCode,
    false,
    true))
  .build();
```

When migrating other servlets, default to this shared helper unless there is a documented product requirement for a different context model.

---

# 2. What `page.writeStart()` and `page.writeEnd()` own

After migration, the servlet should not manually render elements already owned by the shared page framework.

## `page.writeStart(out)` should own

- `<!DOCTYPE html>`
- Opening `<html>`
- `<head>`
- Character encoding
- Viewport metadata
- Browser title
- `aira.css`
- Approved page-specific CSS
- Opening `<body>`
- `.aira-app`
- Skip link
- Shared global header
- Logo and application identity
- Search
- Account actions
- Environment badge
- Optional context header and navigation
- Opening `<main class="aira-main">`

## The servlet content renderer should own

- Page header content
- Page-specific navigation below the shared context navigation
- Forms
- Tables
- Panels
- Domain-specific controls
- Empty states
- Feature-specific scripts or configuration blocks

## `page.writeEnd(out)` should own

- Closing main structure
- Shared footer
- Privacy and terms links
- Closing `.aira-app`
- Closing `<body>`
- Closing `<html>`

Do not write duplicate `<main>`, `<body>`, footer, or closing document tags unless the current `AiraPage` contract explicitly requires them.

---

# 3. Initial servlet migration procedure

## Step 1: Identify the current shell

Locate the code that renders:

- `<!DOCTYPE html>`
- `<html>`, `<head>`, and `<body>`
- Stylesheet links
- Application header
- Logo
- Account links
- Navigation
- Footer
- Closing tags

These should normally be removed and replaced by `AiraPage`.

## Step 2: Create the shared page

Start from:

```java
AiraPage page = InteropAiraPageFactory.base(...);
```

Configure the page using the shared conventions, including `InteropAiraPageFactory.topicsMeetingsContext(...)` for Topics/Meetings context navigation.

## Step 3: Add only required local CSS

A migrated page should load:

```text
aira.css
```

and, only when necessary:

```text
a narrowly scoped page or feature stylesheet
```

Examples:

```text
meeting-workspace.css
topic-admin.css
billing-allocation.css
```

Do not continue loading `main.css`.

## Step 4: Move existing body rendering into a content method

Create or retain a method such as:

```java
private void renderPageContent(
    HttpServletRequest request,
    HttpServletResponse response,
    PrintWriter out
) {
    // Page-specific HTML only
}
```

This makes the shell boundary clear and reduces the chance that the servlet will render duplicate document elements.

## Step 5: Replace legacy classes

Replace general-purpose legacy classes with AIRA components. Some replacements are direct. Others require small markup changes.

## Step 6: Retain legitimate feature classes

Keep classes that:

- Are JavaScript selectors
- Represent component state
- Style a third-party editor
- Identify domain-specific content
- Have no equivalent AIRA component

## Step 7: Remove the servlet's dependency on `main.css`

Search the servlet, included renderers, templates, and JavaScript-generated HTML for legacy classes. Do not remove `main.css` from the entire application until all dependent pages are migrated.

## Step 8: Test the migrated page

Verify visual layout, interaction, narrow-screen behavior, keyboard focus, empty states, errors, disabled controls, and generated header/footer content.

---

# 4. Standard AIRA page structure

A typical migrated page body should resemble:

```html
<div class="aira-container--standard">
  <div class="aira-page-header">
    <div>
      <h1 class="aira-page-title">Page title</h1>
      <p class="aira-page-intro">
        Optional description of this page.
      </p>
    </div>

    <div class="aira-action-group">
      <a class="aira-button aira-button--primary" href="...">
        Add item
      </a>
    </div>
  </div>

  <div class="aira-stack">
    <section class="aira-panel">
      ...
    </section>
  </div>
</div>
```

Choose the appropriate container:

```text
aira-container--narrow
aira-container--standard
aira-container--wide
```

Do not use a legacy `.container` merely because the original page did.

---

# 5. Common direct class transitions

The following mappings are typical. Confirm the intended use before applying them mechanically.

| Legacy concept or class | AIRA replacement |
|---|---|
| `.container` used only for width | `.aira-container--narrow`, `.aira-container--standard`, or `.aira-container--wide` |
| `.panel` | `.aira-panel` |
| `.section-title` | `.aira-section-title` |
| Page heading | `.aira-page-title` |
| Page description or tagline | `.aira-page-intro` |
| Secondary metadata | `.aira-meta` |
| Muted text | `.aira-muted` or `.aira-meta` |
| Error text | `.aira-danger-text` or `.aira-field-error` |
| Generic button | `.aira-button` plus a variant |
| Primary button | `.aira-button .aira-button--primary` |
| Secondary button or button-like link | `.aira-button .aira-button--secondary` |
| Low-emphasis action | `.aira-button .aira-button--tertiary` |
| Destructive action | `.aira-button .aira-button--danger` |
| Link-style action | `.aira-button .aira-button--link` |
| Small action | Add `.aira-button--small` |
| Form | `.aira-form` |
| Form field wrapper | `.aira-field` |
| Form row | `.aira-field-row` |
| Label | `.aira-label` or label inside `.aira-field` |
| Text input | `.aira-input` |
| Select | `.aira-select` |
| Text area | `.aira-textarea` |
| Field hint | `.aira-field-help` |
| Field error | `.aira-field-error` |
| Form actions | `.aira-action-group` or `.aira-cluster` |
| Generic table | `.aira-table` |
| Horizontally scrollable table | Wrap in `.aira-table-wrap` |
| Action column | `.aira-table__cell--actions` |
| Primary table value | `.aira-table__cell--primary` |
| Secondary table value | `.aira-table__cell--secondary` |
| Date or nonwrapping cell | `.aira-table__cell--date` or `.aira-table__cell--nowrap` |
| Alert or message box | `.aira-alert` plus a semantic variant |
| Small status label | `.aira-badge` plus a semantic variant |
| Horizontal group | `.aira-cluster` |
| Group with opposite alignment | `.aira-cluster .aira-cluster--between` |
| Vertical group | `.aira-stack` |
| Compact vertical group | `.aira-stack .aira-stack--compact` |
| Responsive card or section grid | `.aira-grid` |
| Sidebar layout | `.aira-sidebar-layout` |
| Sticky sidebar | `.aira-sidebar .aira-sidebar--sticky` |
| Sidebar heading | `.aira-sidebar-title` |
| Sidebar navigation | `.aira-sidebar-nav` |
| Sidebar link | `.aira-sidebar-link` |
| Footer | Shared page footer generator; do not recreate locally |

---

# 6. Transitions that require markup changes

Some legacy classes combine width, layout, background, border, and padding. These should not be replaced with a single class.

## Legacy container used as a visual panel

### Before

```html
<div class="container">
  <h1>Bill Codes</h1>
  ...
</div>
```

### After

```html
<div class="aira-container--standard">
  <div class="aira-page-header">
    <div>
      <h1 class="aira-page-title">Bill Codes</h1>
    </div>
  </div>

  <section class="aira-panel">
    ...
  </section>
</div>
```

The AIRA container controls page width. The panel controls surface, border, radius, and padding.

---

## Legacy form

### Before

```html
<form class="login-form">
  <label for="name">Name</label>
  <input id="name" name="name">

  <span class="field-hint">Enter the displayed name.</span>

  <div class="form-actions">
    <button type="submit">Save</button>
    <a class="button-link" href="...">Cancel</a>
  </div>
</form>
```

### After

```html
<form class="aira-form">
  <div class="aira-field">
    <label for="name">Name</label>
    <input class="aira-input" id="name" name="name">
    <div class="aira-field-help">
      Enter the displayed name.
    </div>
  </div>

  <div class="aira-action-group">
    <button class="aira-button aira-button--primary" type="submit">
      Save
    </button>
    <a class="aira-button aira-button--secondary" href="...">
      Cancel
    </a>
  </div>
</form>
```

---

## Legacy table

### Before

```html
<table class="admin-table">
  ...
</table>
```

### After

```html
<div class="aira-table-wrap">
  <table class="aira-table">
    ...
  </table>
</div>
```

For an action column:

```html
<th class="aira-table__cell--actions">Actions</th>
<td class="aira-table__cell--actions">
  <div class="aira-cluster">
    ...
  </div>
</td>
```

Do not keep local table styling merely to reproduce alternating rows, hover state, borders, padding, or header formatting. Those belong to the shared table component.

---

## Legacy admin layout

### Before

```html
<div class="admin-shell">
  <aside class="admin-rail">
    ...
  </aside>
  <div class="admin-main">
    ...
  </div>
</div>
```

### After

```html
<div class="aira-container--wide">
  <div class="aira-sidebar-layout">
    <aside class="aira-sidebar aira-sidebar--sticky">
      <section class="aira-panel">
        <h2 class="aira-sidebar-title">Billing</h2>
        <nav class="aira-sidebar-nav">
          <a class="aira-sidebar-link" href="...">Bill Codes</a>
          <a class="aira-sidebar-link" href="...">Allocations</a>
        </nav>
      </section>
    </aside>

    <div class="aira-stack">
      ...
    </div>
  </div>
</div>
```

Use `aria-current="page"` on the active sidebar link.

---

# 7. How to evaluate prefixed classes

Do not blindly remove every class with an application prefix.

For each prefixed class, ask:

## A. Is it only recreating a general visual component?

Replace it with AIRA.

Examples:

```text
mw-outcome-table
mw-outcome-table-wrap
custom-panel
custom-button
custom-form-field
```

When the element is simply a table, panel, button, or form control, use the AIRA component.

## B. Is it a JavaScript or server-rendering hook?

Keep it unless the JavaScript is also updated.

Examples:

```html
<div class="mw-note-editor" data-note-editor>
```

The class may be used for local styling while `data-note-editor` is used by JavaScript. Do not remove either without checking the scripts.

## C. Is it a specialized feature component?

Keep it local.

Examples:

```text
mw-note-editor
mw-note-readonly
mw-note-outcome-marker
mw-note-save-state
```

These describe meeting-note behavior rather than general AIRA presentation.

## D. Is the class used only to add spacing, color, border, or typography?

Prefer an AIRA utility or component class. Remove the local class when it no longer serves behavioral or semantic purposes.

---

# 8. Local CSS rules after migration

A page-specific stylesheet is appropriate when the page has specialized behavior. It should be loaded after `aira.css`.

## Good local CSS

```css
.mw-note-root {
  min-width: 0;
}

.mw-note-editor,
.mw-note-readonly {
  min-height: 8.5rem;
}

.mw-note-editor .ProseMirror,
.mw-note-readonly .ProseMirror {
  min-height: 100%;
  white-space: pre-wrap;
}

.mw-note-outcome-marker {
  border-left: 3px solid var(--aira-green-accessible);
  padding-left: var(--aira-space-2);
  background: var(--aira-surface-selected);
}
```

This CSS is specialized and uses AIRA tokens.

## Avoid in local CSS

```css
:root {
  --text: ...;
  --panel: ...;
}

body {
  font-family: ...;
  background: ...;
}

h1 {
  ...
}

p {
  ...
}

.panel {
  ...
}

button {
  ...
}

table {
  ...
}
```

A migrated local stylesheet should not introduce a second theme or reset.

## Scope local CSS

Prefer either:

- Feature-specific class names, or
- Rules scoped beneath a page root

Example:

```css
.interophub-billing-page .billing-allocation-grid {
  ...
}
```

Avoid generic selectors that could affect other pages.

## Use AIRA tokens

Prefer:

```css
color: var(--aira-text-secondary);
border-color: var(--aira-border-subtle);
background: var(--aira-surface-muted);
gap: var(--aira-space-3);
border-radius: var(--aira-radius-panel);
```

Do not add fallback colors that silently replace the AIRA theme:

```css
/* Avoid */
color: var(--aira-text-secondary, #475569);
```

If `aira.css` is required, a missing token should be visible during development rather than hidden by a competing fallback.

---

# 9. Page header guidance

Use the shared AIRA shell header generated by `AiraPage`.

Inside the main content, render a page header using:

```html
<div class="aira-page-header">
  <div>
    <h1 class="aira-page-title">Page title</h1>
    <p class="aira-page-intro">
      Optional page explanation.
    </p>
  </div>

  <div class="aira-action-group">
    ...
  </div>
</div>
```

Do not confuse:

- The **global application header**, generated by `AiraPage`
- The **context header/navigation**, configured through `AiraPage`
- The **page header**, rendered as page content

Use each layer only when needed.

---

# 10. Footer guidance

Use the shared footer generated by:

```java
page.writeEnd(out);
```

Remove local equivalents such as:

```text
legal-footer
custom footer HTML
manually rendered privacy links
manually rendered terms links
```

Do not add extra closing document tags after `page.writeEnd(out)`.

---

# 11. Alerts, statuses, and validation

Use shared semantic components consistently.

## Informational message

```html
<div class="aira-alert aira-alert--info">
  <p>Information about the current state.</p>
</div>
```

## Success message

```html
<div class="aira-alert aira-alert--success">
  <p>The changes were saved.</p>
</div>
```

## Warning

```html
<div class="aira-alert aira-alert--warning">
  <p>This action may affect existing assignments.</p>
</div>
```

## Error

```html
<div class="aira-alert aira-alert--error">
  <p>The record could not be saved.</p>
</div>
```

For a field error:

```html
<div class="aira-field">
  <label for="code">Code</label>
  <input
      class="aira-input"
      id="code"
      name="code"
      aria-invalid="true"
      aria-describedby="code-error">
  <div class="aira-field-error" id="code-error">
    A code is required.
  </div>
</div>
```

Do not rely only on color to communicate state.

---

# 12. Accessibility requirements during migration

A visual migration must not remove or weaken accessibility.

Verify:

- The page has one primary `<h1>`.
- Heading order remains logical.
- The generated skip link targets the correct main content.
- Navigation elements have useful `aria-label` values.
- The active navigation item uses `aria-current="page"`.
- Every form control has a label.
- Help and error text are connected with `aria-describedby`.
- Invalid controls use `aria-invalid="true"`.
- Buttons remain real `<button>` elements when they perform actions.
- Links remain `<a>` elements when they navigate.
- Disabled controls use the appropriate `disabled` attribute.
- Dialogs retain correct native or ARIA semantics.
- Keyboard focus remains visible.
- Tables retain meaningful headers.
- JavaScript-generated HTML uses the same migrated classes as initial server-rendered HTML.

---

# 13. JavaScript-generated markup

A servlet may render only the initial structure while JavaScript later inserts:

- Tables
- Rows
- Buttons
- Dialogs
- Messages
- Badges
- Empty states

Search the related JavaScript for legacy class names. A page is not fully migrated until both server-rendered and client-generated markup use the new classes.

For example, changing:

```html
<table class="admin-table">
```

in the servlet is incomplete if JavaScript still generates:

```javascript
'<table class="admin-table">'
```

Update the JavaScript to generate:

```javascript
'<div class="aira-table-wrap">' +
  '<table class="aira-table">' +
  ...
```

Also check CSS selectors and automated tests that reference legacy classes.

---

# 14. Handling missing AIRA components

The canonical `aira.css` is maintained in the separate AIRA Web project. A servlet migration in this project must not directly change or fork the shared stylesheet.

During migration, a page may need something that the current version of `aira.css` does not yet provide.

Use this decision process:

1. Is the need specific to this page or domain?
   - Keep it in the page stylesheet.

2. Would multiple AIRA applications reasonably use it?
   - Record it as a proposed shared improvement.

3. Is it already available through a combination of AIRA primitives?
   - Use the existing primitives instead of proposing another component.

4. Is the proposed component merely a differently colored panel, button, table, or field?
   - Do not propose it. Use the existing semantic variants and tokens.

Examples of likely shared additions:

- A general native dialog component
- A reusable rich-text editor surface
- Additional semantic text utilities
- A structured multi-line sidebar link

## Proposed-change log

Record reusable gaps in:

```text
docs/aira-web/aira-css-proposed-changes.md
```

Use Markdown rather than an `aira-proposed.css` file. A CSS file would create an unofficial second version of the shared stylesheet and could gradually become another local compatibility layer.

The proposal log is a review queue. It allows agents to document improvements discovered during migrations without changing the canonical stylesheet or inventing a permanent local substitute.

## Agent rules

- Agents may add proposals to `docs/aira-web/aira-css-proposed-changes.md`.
- Agents must not modify the project's imported copy of `aira.css` as part of an ordinary servlet migration.
- Agents must identify the servlet, page, or migration that exposed the need.
- Agents must explain why the need is reusable rather than page-specific.
- Agents should show the current local workaround when one exists.
- Agents should propose the public class interface and example markup.
- Agents may include suggested CSS for review, but it remains a proposal rather than active project CSS.
- Agents should identify compatibility and migration effects.
- Agents must not create a new general-purpose local component merely because the shared proposal has not yet been accepted.
- A narrowly scoped temporary workaround is allowed when required to complete the page, but it must be identified as temporary in both the local CSS and the proposal.
- Rejected proposals should remain briefly documented so that later agents do not repeatedly suggest the same change.

## Proposal template

Add proposals using this structure:

````markdown
## Proposal: Native dialog component

**Status:** Proposed  
**Found during:** `MeetingWorkspaceServlet` migration  
**Date:** YYYY-MM-DD

### Problem

Describe the general UI or styling gap. Explain what the current AIRA
components cannot express.

### Current local workaround

```css
.mw-outcome-dialog {
  /* Existing temporary implementation */
}
```

State whether the workaround is already active, is being introduced
temporarily, or is only illustrative.

### Proposed shared interface

```html
<dialog class="aira-dialog">
  <div class="aira-dialog__surface">
    ...
  </div>
</dialog>
```

### Proposed `aira.css` changes

```css
.aira-dialog {
  ...
}
```

### Why this belongs in `aira.css`

Explain why this is a reusable AIRA Web component rather than a page-specific
preference.

### Compatibility and migration impact

- Describe effects on existing pages.
- Identify local rules that could be removed after adoption.
- Identify any required markup changes.

### Resolution

Pending review in the AIRA Web project.
````

## Status values

Use one of these values:

```text
Proposed
Accepted
Implemented upstream
Available in this project
Rejected
Deferred
```

Recommended progression:

```text
Proposed
  -> Accepted
  -> Implemented upstream
  -> Available in this project
```

## Upstream and downstream workflow

1. A migration exposes a reusable gap.
2. The agent records a proposal in `docs/aira-web/aira-css-proposed-changes.md`.
3. The proposal is reviewed outside the servlet migration.
4. An accepted change is implemented in the separate AIRA Web project.
5. The updated AIRA Web artifact or version is brought into this project through the normal dependency or resource-update process.
6. The proposal status is changed to `Available in this project`.
7. Temporary local CSS is removed and affected pages are updated to use the shared component.
8. The proposal records the relevant AIRA Web version, release, or commit when available.

Do not mark a proposal complete merely because equivalent CSS was added locally. The improvement is complete only after it has been implemented upstream and the updated shared resource is available in this project.

Changes to `aira.css` should be reviewed independently from the servlet migration so that page-specific preferences do not accidentally become global defaults.

---

# 15. Migration verification checklist

## Shared shell

- [ ] Servlet creates an `AiraPage`.
- [ ] Page starts from `InteropAiraPageFactory.base(...)`.
- [ ] Page title and page-specific options are configured.
- [ ] `page.writeStart(out)` is called before page content.
- [ ] `page.writeEnd(out)` is called after page content.
- [ ] Old document-start and document-end markup is removed.
- [ ] Old header and footer HTML is removed.
- [ ] `main.css` is no longer loaded by the servlet.
- [ ] Only required local CSS is added.

## HTML and components

- [ ] Page uses an appropriate AIRA container.
- [ ] Page heading uses `.aira-page-title`.
- [ ] Page introduction uses `.aira-page-intro` when present.
- [ ] Generic panels use `.aira-panel`.
- [ ] Buttons use `.aira-button` and an appropriate variant.
- [ ] Forms use AIRA field and control classes.
- [ ] Tables use `.aira-table`.
- [ ] Scrollable tables use `.aira-table-wrap`.
- [ ] Alerts and statuses use shared semantic components.
- [ ] Sidebar navigation uses the AIRA sidebar classes.
- [ ] Inline style attributes have been removed where practical.

## Local styles

- [ ] Remaining local classes represent real page-specific needs.
- [ ] Local stylesheet uses feature-specific or page-scoped selectors.
- [ ] Local stylesheet uses AIRA tokens.
- [ ] Local stylesheet does not style `body`, generic headings, paragraphs, lists, buttons, inputs, or tables.
- [ ] Local stylesheet does not redefine AIRA components.
- [ ] Unused legacy rules have not been copied into the new stylesheet.
- [ ] Reusable gaps have been recorded in `docs/aira-web/aira-css-proposed-changes.md`.
- [ ] Temporary shared-component workarounds are clearly marked and linked to a proposal.
- [ ] The imported `aira.css` was not modified as part of the servlet migration.

## Behavior

- [ ] Related JavaScript-generated markup has been migrated.
- [ ] JavaScript selectors still match the intended elements.
- [ ] Forms submit correctly.
- [ ] Validation messages appear correctly.
- [ ] Disabled and loading states work.
- [ ] Empty states work.
- [ ] Dialogs work.
- [ ] Page works without JavaScript where expected.

## Responsive and accessibility

- [ ] Wide desktop layout is correct.
- [ ] Narrow desktop or tablet layout is correct.
- [ ] Mobile-width layout remains usable.
- [ ] No unintended horizontal page scrolling occurs.
- [ ] Keyboard navigation works.
- [ ] Focus indicators are visible.
- [ ] Heading structure is correct.
- [ ] Form labels and errors are associated correctly.
- [ ] Active navigation uses `aria-current`.
- [ ] Color is not the only indicator of state.

---

# 16. Recommended Copilot task pattern

When asking Copilot to migrate a servlet, give it a narrow and explicit task.

Example:

```text
Migrate [ServletName] from the legacy main.css page structure to the shared
AIRA page framework.

Use [MigratedServletName] as the rendering example.

Requirements:

1. Build an AiraPage in doGet using InteropAiraPageFactory.base(...).
2. Configure the page title, application subtitle, context navigation, and any
   required local stylesheet using the existing AiraPage API.
3. Render using:
     page.writeStart(out);
     renderPageContent(...);
     page.writeEnd(out);
4. Remove manually rendered document shell, global header, account area,
   navigation shell, footer, and closing document tags now supplied by AiraPage.
5. Remove this servlet's dependency on main.css.
6. Replace legacy general-purpose styles with existing aira.css components:
   containers, page headers, panels, stacks, clusters, buttons, forms, tables,
   alerts, badges, and sidebar navigation.
7. Do not blindly remove application-specific classes. Retain classes needed
   for JavaScript hooks, specialized behavior, or domain-specific presentation.
8. Move any necessary page-specific rules into [page-name].css. Scope those
   rules to the page or feature and use AIRA tokens.
9. Update JavaScript-generated markup and selectors that still use legacy
   classes.
10. Do not modify main.css or the imported copy of aira.css.
11. When an actual reusable gap is identified, add or update a proposal in
    docs/aira-web/aira-css-proposed-changes.md. Do not implement an unofficial
    shared component locally unless a narrowly scoped temporary workaround is
    required to complete the page.
12. For each proposal, identify the page that exposed the need, the current
    workaround, the proposed shared interface, why it belongs upstream, and
    the compatibility or migration impact.
13. Preserve all current page behavior and servlet actions.
14. Check for duplicate main/body/header/footer markup after the conversion.
15. Summarize the files changed, legacy classes intentionally retained,
    temporary workarounds, and proposed upstream AIRA CSS improvements.
```

---

# 17. Definition of done

A servlet transition is complete when:

- It renders through `AiraPage`.
- It uses the shared header and footer generators.
- It no longer loads `main.css`.
- Its general presentation uses `aira.css`.
- Its remaining local stylesheet contains only narrowly scoped, feature-specific rules.
- Related JavaScript and generated HTML no longer depend on removed legacy classes.
- Existing behavior and accessibility are preserved.
- Any reusable styling gap has been documented in `docs/aira-web/aira-css-proposed-changes.md`.
- The imported `aira.css` has not been locally forked or modified during the migration.
- Any temporary workaround is linked to an upstream proposal and can be removed when the updated AIRA Web resource is brought into the project.
