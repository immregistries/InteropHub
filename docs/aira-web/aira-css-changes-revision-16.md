# AIRA CSS Changes Revision 16

This note records the AIRA Web changes made for release `0.1.16`. InteropHub found the problem while adopting 0.1.15: after the upgrade, a browser kept its cached 0.1.14 `aira.css`. That copy lacked the new global `[hidden]` rule, so a dialog showed its compose form and its "sent" confirmation at the same time. A hard refresh fixed it, but ordinary users won't know to do that.

## Cause

`AiraPage` linked the shared stylesheet at the fixed path `/aira/css/aira.css`, and the theme jar is served with `ETag` / `Last-Modified` but no `Cache-Control`. Browsers then apply heuristic freshness, typically about 10% of the file's age, and can reuse an old `aira.css` for a day or more after a deploy, without asking the server. This applied to every past release. 0.1.15 is the first where the stale copy breaks behavior rather than only appearance.

## Completed

- Added `AiraWebVersion.get()`, which returns the AIRA Web release version from `org/immregistries/aira/web/aira-web-version.properties`. The components module filters that resource with Maven so it holds `${project.version}`. When the value is unavailable (unfiltered sources in an IDE), it returns `dev`.
- `AiraPage.writeStart` now appends a `v` query parameter to stylesheet links:
  - the shared stylesheet: `{contextPath}/aira/css/aira.css?v={AIRA Web version}`
  - each `addLocalStylesheet(...)` path: `?v={applicationVersion}`, or `&v=` when the path already has a query string
  - the value is URL-encoded, then HTML-attribute-escaped as before
  - absolute `http://` / `https://` stylesheet URLs are unchanged
- Updated `AiraPageTest` for the versioned URLs, and added tests for the shared-version stamp (it must not be `dev` in a Maven build), local-stylesheet versioning, existing query strings, and absolute URLs. 21 tests pass.
- Documented the behavior under "Stylesheet versioning" in `docs/adoption-guide.md`.
- Updated the root and all module parent versions to `0.1.16`.

## Design Decisions

- A query-string version, not a versioned path or a `Cache-Control` header. The theme is served by the servlet container's default handling of `META-INF/resources`, so AIRA Web can't set headers without asking each application to add a filter. A new URL per release works in every container with no application changes.
- Local stylesheets use the application version the page already requires, so applications get the same protection for their own CSS without a new API. Applications should bump `applicationVersion` when they deploy changed CSS.
- No CSS changes in this release.

## InteropHub Migration Notes

- Upgrade `aira-web-components` to `0.1.16`, then refresh the copied stylesheet (unchanged except for the version banner, if any) and `adoption-guide.md`.
- No markup changes are needed. Every page built with `AiraPage` gets versioned stylesheet links automatically.
- InteropHub's local stylesheets (`agenda.css`, `topic-reference.css`, …) are now versioned by InteropHub's application version, so a release that changes them should bump that version, which the project's versioning convention already does.
