# AIRA CSS Changes Revision 12

This note records a bug fix for release `0.1.12`, found by InteropHub in production use of the `.aira-badge-count` global-action badge shipped in `0.1.11` (`docs/aira-css-changes-revision-11.md`, requested in `docs/aira-web/aira-css-request-11.md`).

## Bug

`.aira-badge-count` used `color: inherit`, tying the badge's text color to whatever button variant it was placed inside. `.aira-button--primary` and `.aira-button--danger` both set `color: var(--aira-white)`. Since the badge's own background is the fixed light `var(--aira-surface)`, the count rendered white-on-white on those two variants — completely invisible. It only happened to be legible on variants with dark label text (`secondary`, `tertiary`), which was never exercised when 0.1.11 shipped, since the request's own markup sample used a generic `aira-button--primary` without anyone actually rendering it.

## Completed

- Changed `.aira-badge-count` in `aira-web-theme/src/main/theme-css/10-badges-tags.css` from `color: inherit` to `color: var(--aira-text)`.
  - The badge now carries its own fixed dark-text-on-light-chip contrast (`var(--aira-text)` on `var(--aira-surface)`) instead of inheriting the surrounding button's text color, so it no longer depends on which variant it's placed inside.
  - Verified contrast against all four variants likely to carry a count:
    - `primary` (background `var(--aira-navy)`, white label text) and `danger` (background `var(--aira-danger)`, white label text) — badge chip (`var(--aira-surface)` white, ~11.6:1 contrast against `var(--aira-text)` navy) now stands out clearly against the dark button background; this is the case that was previously broken.
    - `success` (background `var(--aira-green-accessible)`, white label text) — same fix applies, same result.
    - `secondary` (background `var(--aira-surface)`, navy label text) and `tertiary` (background `var(--aira-surface-muted)`, navy label text) — badge text remains legible (same navy-on-light contrast as the button's own label), unchanged from before since these variants were never the broken case.
- Updated Maven versions for release `0.1.12`.
  - Root project version changed to `0.1.12`.
  - Module parent versions changed to `0.1.12` in `aira-web-theme`, `aira-web-components`, and `aira-web-demo`.

## Not Changed

- No markup or Java API changes. `AiraActionItem`, `Builder.addGlobalAction`, and `AiraPage.writeGlobalHeader()` are unchanged from `0.1.11` — this is a CSS-only fix.
- No new tests were added to `AiraPageTest`; the existing badge markup tests (`globalActionWithBadgeCountRendersBadgeMarkup`, `globalActionWithZeroOrNegativeBadgeCountOmitsBadge`) assert generated HTML, not computed CSS color, and continue to pass unchanged. Contrast was verified by inspecting the shipped token values (`--aira-text`, `--aira-surface`, `--aira-primary-action`, `--aira-danger`, `--aira-green-accessible`) rather than by a new automated check.
- On `secondary`/`tertiary`, the badge chip's background (`var(--aira-surface)` white) is close to or identical to the button's own background, so the pill doesn't stand out as a distinct shape on those two variants (only the digits read clearly, same as before). This wasn't reported as a problem and isn't addressed here; if it becomes one, a subtle border on `.aira-badge-count` would be the next step.

## InteropHub Migration Notes

- Upgrade AIRA Web dependencies to `0.1.12`.
- No markup changes required. InteropHub's interim workaround (using `secondary` instead of `danger` for the header's action-needed badge, to avoid the white-on-white bug) can be reverted now — swap the `addGlobalAction(...)` variant string back to `danger` (or whichever variant is semantically correct) once the dependency bump lands.
