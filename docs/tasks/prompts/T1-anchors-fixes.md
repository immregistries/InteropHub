# T1 Follow-up: Review Fixes (content-publishing-templates)

Continue in `C:\dev\writing\content-publishing-templates`, on top of your uncommitted T1 changes. Same rules as `T1-anchors.md`. Stream G reviewed the diff; the approach is accepted. Make these changes, then report back.

1. **Reserved names.** `check_content.py` refuses an explicit anchor named `document-start`, or starting with `comment-` or `guide-`. These would collide with the document-start area, `#comment-<id>` deep links, or the guide's own element IDs. Say so in the Conventions guidance.
2. **Authoritative ID check in the build.** `check_content.py` re-derives heading IDs with its own slug rules, which can drift from pandoc's. Keep it, but also make `build_guide.py` fail with a clear message if the final HTML contains a duplicate `id` (anchor against heading, anchor against anchor, or anchor against a guide element). Add a validate case for an anchor that collides with a heading ID that only pandoc would generate (for example a heading with punctuation).
3. **Fenced code.** Anchor lines inside fenced code blocks must stay literal text: not converted by `build_guide.py`, not stripped by `build_core_markdown.py`, not counted or checked by `check_content.py`. A content file that shows the anchor syntax as an example must work. Add it to the fixture.
4. **Changelog: what readers will notice.** Commentable blocks are now paragraphs, whole lists, quotes, and tables; individual list items and code blocks are no longer separately commentable. Say this in the v0.5.0 entry, and that earlier local comments on a list item or code block may show at the start of their area or as orphaned.
5. **Prove legacy loading (gate check 1.6).** Using the v0.4.0 guide, make three local comments in the fixture: on the third paragraph of a section, on the second item of a list, and on a code block. Export them, load them in the v0.5.0 build, and report where each lands. None may be lost. If the paragraph comment does not land on its paragraph, fix `legacyBlock` so it does.
6. Tidy: in the rail index, `link.href = placed ? "#comment-" + comment.id : "#comment-" + comment.id;` has the same value on both branches.

Run `validate.ps1`, regenerate and review the fixture reading copy if it changed, and report the files changed and the result of item 5. Do not commit, tag, or push.
