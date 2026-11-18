# AIRA CSS Changes Revision 17

This note records the AIRA Web changes made for release `0.1.17` in response to InteropHub's proposal "Preformatted code / JSON block (`aira-code-block`)" in `docs/aira-web/aira-css-proposed-changes.md`, found on the guide status detail view (`GuideStatusRenderer`), which shows a guide's raw build manifest as pretty-printed JSON.

## Completed

- Added `aira-code-block` for `<pre>` elements (new source file `14-code-block.css`, concatenated after `14-tables.css`).
  - Monospace text (`--aira-font-mono`, `0.8125rem`, line height `1.5`, `tab-size: 2`).
  - The same surface, border, and radius as `aira-table-panel` (`--aira-surface`, `--aira-border`, `--aira-radius-panel`), with `--aira-panel-padding`.
  - `max-height: 40rem` and `overflow: auto`, so long or wide payloads scroll inside the block.
  - Resets the browser's default `<pre>` margin. An inner `<code>` element inherits the block's font and color.
- Added the `aira-code-block--wrap` modifier: `white-space: pre-wrap; overflow-wrap: anywhere`.
- The block fills its container and never widens it. It can sit directly inside `aira-section-card__body`, `aira-panel`, either column of `aira-right-rail-layout`, or a flex row such as `aira-cluster` without making the page scroll horizontally.
- Print: the height limit is removed and lines wrap, so the whole payload prints.
- Added a "Code block" example (default and wrapped) to the demo component reference page.
- Documented the component under "Preformatted Code Blocks" in `docs/components-guide.md` and listed it in the README.
- Updated the root and all module parent versions to `0.1.17`. The published artifacts are `aira-web-components:0.1.17` and `aira-web-theme:0.1.17`.

## Verification

Measured in headless Chrome against the built `aira.css` at 1400, 900, and narrow viewport widths, with a 1,900px-wide unbroken JSON line and an 80-line payload. The test placed blocks directly in a section card body, in the main column and right rail of `aira-right-rail-layout`, and inside an `aira-cluster`. In every case the document's scroll width equaled its client width, so the page had no horizontal scrolling. Each block's width stayed within its parent. The default block scrolled horizontally inside itself, the wrapped block had no horizontal overflow, and the tall payload was capped at 40rem with vertical scrolling.

## Design Decisions and Deviations

- **`contain: inline-size`.** A long unwrapped line would otherwise set the block's min-content width and widen a grid or flex ancestor that lacks `min-width: 0`. `aira-right-rail-layout` already sets that on its children, but section cards and clusters inside other grids do not. Inline-size containment makes the block's width come only from its container, and `width: 100%` keeps it full width inside flex rows.
- **Same surface as `aira-table-panel`.** The proposal asked for that panel's surface and border, so the block uses `--aira-surface` (white in the light tokens), not `--aira-surface-muted`. Inside a white section card it is set apart by its border and monospace text.
- **Light and dark themes.** `aira.css` currently defines only the light token set and has no dark theme (no `prefers-color-scheme` or `data-theme` rules). The block uses only theme tokens for its colors (`--aira-surface`, `--aira-border`, `--aira-text`) and sets `color` explicitly, so a page or future theme that redefines those tokens for dark mode gets a correct block with no further changes.
- **Keyboard access is in the markup.** The class can't make an element focusable. The guide asks applications to add `tabindex="0"` and an accessible name to a scrolling block, so keyboard users can scroll it. The existing global `:focus-visible` ring applies.
- No syntax highlighting, copy button, or JavaScript. Applications HTML-escape the content.

## InteropHub Migration Notes

- Upgrade `aira-web-components` and `aira-web-theme` to `0.1.17`, then refresh copied stylesheet and component-guide references.
- In `GuideStatusRenderer.renderDetail`, remove the inline `style="white-space: pre-wrap; overflow-wrap: anywhere; max-height: 40rem; overflow: auto;"` from `<pre id="guide-build-manifest-raw">` and use the classes, for example:

  ```html
  <pre id="guide-build-manifest-raw" class="aira-code-block aira-code-block--wrap" tabindex="0" aria-label="Raw build manifest">…</pre>
  ```

  Keep `aira-code-block--wrap` to match today's wrapped behavior, or drop it to scroll long lines sideways instead.
- Mark the proposal as available in this project.
