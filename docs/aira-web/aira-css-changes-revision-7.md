# AIRA CSS Changes Revision 7

This note records the AIRA Web changes made for release `0.1.7` in response to `docs/aira-css-request-7.md`.

## Completed

- Added native dialog styling in `aira-web-theme/src/main/theme-css/15-dialog.css`.
  - `.aira-dialog` provides the bounded dialog surface using shared panel tokens.
  - `.aira-dialog::backdrop` provides the shared modal backdrop.
  - `.aira-dialog__title`, `.aira-dialog__body`, and `.aira-dialog__actions` provide simple layout hooks.
- Updated the theme build so generated `/aira/css/aira.css` includes the dialog stylesheet.
- Added a working dialog example to the demo component reference page.
- Updated `docs/components-guide.md` and `README.md` to document dialog support and clarify that applications still own JavaScript behavior.
- Updated Maven versions for release `0.1.7`.
  - Root project version changed to `0.1.7`.
  - Module parent versions changed to `0.1.7` in `aira-web-theme`, `aira-web-components`, and `aira-web-demo`.

## InteropHub Migration Notes

- Upgrade AIRA Web dependencies to `0.1.7`.
- Replace local legal-term dialog classes with `aira-dialog`, `aira-dialog__title`, `aira-dialog__body`, and `aira-dialog__actions`.
- Keep page-owned `showModal()` and `close()` JavaScript in InteropHub.
- Remove the temporary `interophub-legal-term-dialog` CSS block from `css/register.css` after the shared version is consumed.
