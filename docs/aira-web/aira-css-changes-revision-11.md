# AIRA CSS Changes Revision 11

This note records the AIRA Web changes made for release `0.1.11` in response to InteropHub's request for a badge/count affordance on global header action items (`docs/aira-web/aira-css-request-11.md`), needed for the meeting-cadence "action queue" indicator in InteropHub's global header.

## Completed

- Added an optional `badgeCount` component to `AiraActionItem` in `aira-web-components/src/main/java/org/immregistries/aira/web/AiraActionItem.java`.
  - `AiraActionItem` is now `record AiraActionItem(String label, String href, String variant, Integer badgeCount)`.
  - A `badgeCount` of `null` or `<= 0` is normalized to `null` in the canonical constructor, so callers can pass a raw count without checking it first — `0` or a negative number simply means "no badge," it never renders a zero.
  - The existing 3-arg constructor (`label`, `href`, `variant`) is unchanged in behavior and delegates to the 4-arg canonical constructor with `badgeCount = null`.
- Added `Builder.addGlobalAction(String label, String href, String variant, int badgeCount)` in `aira-web-components/src/main/java/org/immregistries/aira/web/AiraPage.java`.
  - The existing 3-arg `addGlobalAction(label, href, variant)` overload is unchanged.
- Updated `AiraPage.writeGlobalHeader()` to render the badge when `badgeCount != null`.
  - The action's `<a class="aira-button aira-button--{variant}">` gains an additional `aira-button--has-badge` class.
  - A `<span class="aira-badge-count" aria-hidden="true">{count}</span>` is rendered inside the anchor, after the label text.
  - The anchor also gains `aria-label="{label}, {count} pending"` so the count is announced once by assistive tech (via the visible-but-labelled anchor) rather than twice (once from the label, once from an unhidden badge span).
- Added `.aira-button--has-badge` to `aira-web-theme/src/main/theme-css/08-buttons-actions.css` — `display: inline-flex; align-items: center; gap: var(--aira-space-2);`.
- Added `.aira-badge-count` to `aira-web-theme/src/main/theme-css/10-badges-tags.css` — a small pill (`border-radius: var(--aira-radius-pill)`) using `background: var(--aira-surface)` and `color: inherit` so it reads as a light count chip against whatever button variant it sits on, rather than introducing a second accent color.
- Added unit tests in `aira-web-components/src/test/java/org/immregistries/aira/web/AiraPageTest.java`:
  - `globalActionWithBadgeCountRendersBadgeMarkup` — verifies the `aira-button--has-badge` class, the `aria-label`, and the `aira-badge-count` span all render for a positive count.
  - `globalActionWithZeroOrNegativeBadgeCountOmitsBadge` — verifies a `0` count renders no badge markup at all.
- Updated `docs/components-guide.md`.
  - The `AiraActionItem` core-types bullet now mentions the optional badge count.
  - Added a "Global Action Badge Counts" section documenting the new `addGlobalAction` overload, the rendered markup, and the ARIA approach.
- Updated Maven versions for release `0.1.11`.
  - Root project version changed to `0.1.11`.
  - Module parent versions changed to `0.1.11` in `aira-web-theme`, `aira-web-components`, and `aira-web-demo`.

## Not Changed

- The request file left the exact visual treatment and ARIA wiring open to this project's judgment. This implementation kept the request's proposed light "count chip" styling (`background: var(--aira-surface)` / `color: inherit`) rather than a fixed high-contrast chip, and used a single `aria-label` on the anchor rather than a visually-hidden duplicate text node, per the request's own suggestion.
- No new button variant was added. `primary`/`danger` (etc.) continue to carry due/overdue meaning; the badge is purely a count on top of an existing variant.
- `.aira-environment-badge` was not touched — it remains plain text, unrelated to the new numeric `.aira-badge-count` pill.
- No demo markup changes were made in `aira-web-demo`; the new overload is additive and not required for the existing demo pages to keep working.

## InteropHub Migration Notes

- Upgrade AIRA Web dependencies to `0.1.11`.
- Call the new `addGlobalAction(label, href, variant, badgeCount)` overload only when the signed-in user has one or more pending actions; omit the header action entirely otherwise, exactly as today's 3-arg overload usage already does.
- No servlet changes are required beyond supplying the pending-action count where the header action is added; the existing 3-arg `addGlobalAction` calls continue to work unchanged.
