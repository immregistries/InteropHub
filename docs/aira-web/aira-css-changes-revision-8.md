# AIRA CSS Changes Revision 8

This note records the AIRA Web changes made for release `0.1.8` in response to `docs/aira-css-request-8.md`.

## Completed

- Added semantic accent-border modifiers for `aira-table-panel` in `aira-web-theme/src/main/theme-css/14-tables.css`.
  - `aira-table-panel--info`
  - `aira-table-panel--success`
  - `aira-table-panel--warning`
  - `aira-table-panel--danger`
- Reused the same semantic border tokens used by alerts.
  - Info uses `var(--aira-focus)`.
  - Success uses `var(--aira-success)`.
  - Warning uses `var(--aira-yellow)`.
  - Danger uses `var(--aira-danger)`.
- Added warning and danger table-panel examples to the demo Tables page.
- Updated `docs/components-guide.md` with copyable accent table-panel markup.
- Updated `README.md` to include semantic table-panel variants in the published component list.
- Updated Maven versions for release `0.1.8`.
  - Root project version changed to `0.1.8`.
  - Module parent versions changed to `0.1.8` in `aira-web-theme`, `aira-web-components`, and `aira-web-demo`.

## Compatibility

- This is additive. Existing `.aira-table-panel` usage is unaffected.
- Accent modifiers only change the left border width and color; the remaining panel border stays neutral.

## InteropHub Migration Notes

- Upgrade AIRA Web dependencies to `0.1.8`.
- Use `aira-table-panel aira-table-panel--warning` for agenda Open Items.
- Use `aira-table-panel aira-table-panel--danger` for Curated Topic Cadence.
- Remove the local `.open-items-section` and `.curated-cadence-section` border-left CSS once those sections use the shared table-panel structure.
