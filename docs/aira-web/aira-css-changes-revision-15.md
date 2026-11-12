# AIRA CSS Changes Revision 15

This note records the AIRA Web changes made for release `0.1.15` in response to [InteropHub request 15](aira-css-request-15.md).

## Completed

- Added the `aira-toast-region` fixed notification stack and `aira-toast` component.
  - Added `aira-toast--info`, `aira-toast--success`, `aira-toast--warning`, and `aira-toast--error` semantic variants using the same surfaces and accent colors as alerts.
  - Added `aira-toast__message` and an optional, keyboard-focusable `aira-toast__dismiss` control with a full shared touch target.
  - Used `--aira-shadow-elevated` and `--aira-z-overlay` for a nonmodal elevated surface above ordinary and sticky page content.
  - Added a bottom-center, nearly full-width layout at the existing `36rem` narrow-screen breakpoint.
  - Excluded toast regions from print.
- Added a global `[hidden]:not([hidden="until-found" i])` rule so the native `hidden` state wins over AIRA flex, grid, and other component display declarations.
- Added a working demo that shows, dismisses, and automatically hides a success toast after 45 seconds while pausing the timer for hover and focus.
- Updated the component guide with toast markup, semantic variants, live-region and timing guidance, application behavior boundaries, and the global hidden-state contract.
- Updated the root and all module parent versions to `0.1.15`; the published artifacts are `aira-web-components:0.1.15` and `aira-web-theme:0.1.15`.

## Design Decisions and Deviations

- Toasts use `--aira-shadow-elevated` rather than `--aira-shadow-dialog`. A toast is nonmodal and should not carry the stronger depth of a blocking dialog.
- The live region is a persistent `aira-toast-region`; applications insert or reveal toast children after an action so assistive technology can announce the change reliably.
- The component provides no shared JavaScript. Applications own timers, dismissal, and DOM updates, consistent with the existing `aira-dialog` behavior boundary.
- The global hidden rule uses `!important` deliberately because an explicit native hidden state must override component display declarations. It excludes the case-insensitive `hidden="until-found"` keyword so browser find-in-page disclosure behavior continues to work.
- Enter and leave animation remains optional and is not included in this release. This avoids delaying removal or announcement and requires no additional reduced-motion contract.

## InteropHub Migration Notes

- Upgrade `aira-web-components` and `aira-web-theme` to `0.1.15`, then refresh copied stylesheet and component-guide references.
- Replace `hub-toast aira-alert aira-alert--success` with `aira-toast aira-toast--success`, place it in a persistent `aira-toast-region`, and use `aira-toast__message` plus `aira-toast__dismiss` for its contents.
- Delete `src/main/webapp/css/hub-toast.css` after adopting the shared classes.
- Remove plain `hidden` wrapper elements that exist only to hide an `aira-stack`, `aira-cluster`, `aira-form-actions`, or another AIRA component. Put `hidden` directly on the component whose layout should be removed.
- Keep application JavaScript responsible for the 45-second timer, pause-on-hover/focus behavior, dismissal, and showing the toast after the dialog closes.
