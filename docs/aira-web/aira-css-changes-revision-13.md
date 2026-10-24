# AIRA CSS Changes Revision 13

This note records the AIRA Web changes made for release `0.1.13` in response to InteropHub's management-form request (`aira-css-request-13.md`).

## Completed

- Direct-child buttons now keep their natural width in both stacked form primitives:
  - `.aira-form > .aira-button` and `.aira-form > a.aira-button` use `justify-self: start`.
  - `.aira-stack > .aira-button` and `.aira-stack > a.aira-button` use the same rule.
  - Buttons inside `aira-form-actions` are unchanged.
- Added `aira-form--inline` for filters and short one-line actions.
  - The form uses a wrapping flex layout, aligns controls and buttons along their bottom edges, and uses existing `--aira-space-2` / `--aira-space-3` gap tokens.
  - Direct-child fields size naturally. Inputs and selects have a `10rem` minimum width and remain bounded by their available width.
  - `aira-field--grow` lets one direct-child field consume remaining space with a `16rem` flex basis, and its input or select fills that field.
- Added `aira-form--narrow` and the `--aira-form-max-width: 44rem` token for readable stacked data-entry forms inside wide containers.
- Added a Forms section to `docs/components-guide.md` covering `aira-form`, `aira-field`, `aira-field-row`, `aira-form-actions`, `aira-form--inline`, `aira-field--grow`, and `aira-form--narrow`. It also documents form composition inside `aira-dialog` and the management-list pattern using `aira-table-panel` plus add/edit dialogs.
- Updated the component-reference demo with an inline filter form, the narrow management form, and a form composed directly inside an `aira-dialog`.
- Updated the root and all module parent versions to `0.1.13`; the published artifacts are `aira-web-components:0.1.13` and `aira-web-theme:0.1.13`.

## Design Decisions and Deviations

- The open `.aira-stack` question was resolved in favor of natural-width direct-child buttons. `aira-stack` is a general vertical composition primitive, and full-width buttons caused by Grid's default stretching are as surprising there as they are in `aira-form`. Existing AIRA Web uses include a direct button link in a compact card/rail stack; natural width is the more appropriate default there. Intentional full-width actions can still be created by an application-specific rule or by overriding `justify-self: stretch` on that instance.
- The requested inline-form spacing values already use the established spacing scale, so `var(--aira-space-2)` and `var(--aira-space-3)` shipped unchanged. The proposed `10rem`, `16rem`, and `44rem` sizing values also shipped unchanged because they describe control/form widths rather than spacing increments and fit the existing responsive sizing conventions.
- The existing legacy `.aira-inline-form` utility remains available and unchanged. The new `aira-form--inline` modifier is the documented form interface and adds field/control sizing behavior that the older utility does not provide.

## InteropHub Migration Notes

- Upgrade the `aira-web-components` and `aira-web-theme` dependencies to `0.1.13`, then refresh the copied stylesheet and component-guide references.
- Existing InteropHub markup does not have to change for compatibility. Direct buttons in `aira-form` or `aira-stack` will automatically stop stretching after the stylesheet upgrade.
- To adopt the new layouts on the Initiatives management pages:
  - add `aira-form--narrow` to ordinary stacked add/edit/settings forms;
  - use `aira-form aira-form--inline` for filter bars and quick actions, with `aira-field--grow` on the field that should take remaining space;
  - compose dialog forms directly as `form.aira-stack.aira-stack--compact` inside `dialog.aira-dialog`, without `aira-dialog__body` around the form fields;
  - use `aira-table-panel` with the Add control in `aira-table-panel__actions`, link primary row content to the detail page, and place add/edit workflows in dialogs.
