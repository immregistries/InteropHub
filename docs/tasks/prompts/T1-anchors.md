# T1: Anchors in Local Mode (content-publishing-templates)

You are working in `C:\dev\writing\content-publishing-templates`.

## Read first

- `AGENTS.md` in this repository, and follow it.
- The design, from the InteropHub repository: `C:\dev\immregistries\InteropHub\docs\topic-reference-comments-design.md`, Sections 5 (Anchors) and 11 (Changes in content-publishing-templates). Read Sections 4, 6, and 9 only for context; they are later steps.
- `guidance/TopicReference.md`, `guidance/Conventions.md`, `tools/build_guide.py`, `templates/guide/guide.js`, `tools/check_content.py`, `tools/build_core_markdown.py`, `tests/fixture-project/`, `validate.ps1`.

**Public repository rule:** the design document is internal. Do not copy its text, project names, InteropHub URLs, or people's names into this repository. Write guidance in your own generic words.

## Task

Add anchors to the web guide while comments stay in today's browser-only (local) mode.

1. **Syntax.** A line containing only `<!-- anchor: name -->` in a content Markdown file, between blocks. Names match `^[a-z0-9]+(-[a-z0-9]+)*$`.
2. **Areas.** An anchor starts an area that runs to the next anchor or heading. Every heading is also an anchor, using its existing heading ID. Content before the first heading or anchor belongs to a document-start area; pick a stable name for it and document it.
3. **`build_guide.py`.** Turn each anchor line into a marked, targetable element (`id` equal to the name) in the guide. Give every commentable block (paragraph, list, quote, table) its area's anchor name and its 0-based position within that area, as data attributes. Use `data-anchor` and `data-block-index`; these names are part of the contract with the later InteropHub backend, so do not change them.
4. **Snippet.** Define one function in `guide.js` that produces a block's snippet: its text content, whitespace collapsed to single spaces, trimmed, first 120 characters. Store it with each local comment. This is also part of the later contract.
5. **Local comments.** Store `anchor`, `blockIndex`, and `snippet` on each new local comment. Place comments using the design's Section 5.3 rules: anchor exists and a block in its area has a matching snippet, show it there (prefer the stored block index when several match); anchor exists but nothing matches, show it at the start of the area; anchor missing, list it in an "Orphaned comments" group at the end of the document. Comments saved before this change (no anchor) must still load and show as they do today, or as orphans if they cannot be placed; do not lose them.
6. **Deep links.** `#<anchor>` scrolls to the area; `#comment-<id>` scrolls to and highlights that comment, opening its rail entry if needed. Both work on first load and on `hashchange`.
7. **`check_content.py`.** Report bad anchor syntax, duplicate anchor names, and anchors that collide with a heading ID, across the whole guide (all content files that build into one guide).
8. **Other builds.** Confirm anchor lines are dropped from the Word build and the reading copy (`build_core_markdown.py`). Regenerate the fixture reading copy and review the diff.
9. **`import_guide_comments.py`.** Keep it working with comment files that now carry anchor fields, and with older files that do not.
10. **Fixture.** Add a few anchors to `tests/fixture-project/`, including one area with several blocks, and extend `validate.ps1` to check the build output has the anchor elements and data attributes, and that `check_content.py` catches a duplicate and a malformed anchor.
11. **Guidance.** In `guidance/Conventions.md`, anchor naming and placement: one line, sparing, at natural review points, stable names, agents maintain them. In `guidance/TopicReference.md`, a short note on what anchors do for comments and deep links.
12. **Release.** CHANGELOG entry for the next minor version (the latest tag is `v0.4.0`). Do not create the tag; the product owner does that.

## Out of scope

- Any InteropHub backend, sign-in, the `comments` setting in `guide.json`, replies, states, dispositions, review flags (T2, T3).
- `aira.css` and the logo.
- Third-party Python dependencies.

## Done when

- `validate.ps1` passes.
- The fixture builds, a local comment made on a paragraph survives renaming the paragraph's text lightly (placed by anchor and index), falls back to the area start when the text changes completely, and shows as orphaned when its anchor is removed.
- `#anchor` and `#comment-<id>` deep links work on the built fixture served from a local web server.
- Word build and reading copy contain no anchor text.

## Report back

List the files changed, the exact markup an anchor and a commentable block produce (one HTML example), the name chosen for the document-start area, the local comment JSON shape now stored, and anything T2 should know. Do not commit, tag, or push unless the product owner says to.
