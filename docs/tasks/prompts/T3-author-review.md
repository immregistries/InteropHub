# T3: Author Review in the Guide (content-publishing-templates)

You are working in `C:\dev\writing\content-publishing-templates`, on `main` at `v0.6.0`.

## Read first

- `AGENTS.md` in this repository, and follow it.
- **Your specification:** `C:\dev\immregistries\InteropHub\docs\tasks\topic-reference-comments-api-contract.md` ("the contract"). You build the guide side of 5.2 (state, disposition, change note, closed, deleted, author permissions), the author fields of 5.6, 5.8, and Section 6 (review flags). Where this prompt and the contract disagree, the contract wins; tell G.
- The design, for background: `C:\dev\immregistries\InteropHub\docs\topic-reference-comments-design.md`, Sections 6.3, 7, and 11.
- What `v0.6.0` already does: `templates/guide/guide.js`, `templates/guide/guide.css`, `tests/mock_hub.py`, `tests/test_mock_hub.py`, `tests/browser_check.py`, `validate.ps1`.

**Public repository rule.** The contract and design are internal. Do not copy their text, host names, project names, or people's names here. Write guidance in your own generic words.

## Task

1. **Show review state to everyone who can view comments.**
   - On each top-level comment: its state, and once disposed, the disposition label (from `dispositions` in `GET /api/guide/{documentKey}`, never hard-coded) and the change note, with who disposed it and when.
   - Withdrawn comments as in `v0.6.0`.
   - **"Show closed"** in the rail: off by default. When on, the guide loads closed threads (`state=all`) and shows them, marked closed, with their replies. Closed threads take no new replies.
2. **Author controls**, shown only when the thread's `permissions` allow them (the server checks again):
   - **Dispose:** choose a disposition, write a change note (required), save. Change it later; clear it (back to open).
   - **Close** and **Reopen.**
   - **Delete** a comment or a reply, with an in-page confirmation that says it is for inappropriate content.
   - **Re-attach:** for an orphaned comment, choose a new anchor in the page (a list of the page's anchors and headings, or "click a paragraph" mode, whichever is simpler and clear), then save `anchor`, `blockIndex`, and `snippet`.
   - Send `expectedUpdatedAt` on every author `PATCH`. On `409 conflict`, show the current version from `current` and say someone else changed it.
3. **Review flags** (contract 6): highlight flagged anchor areas for every viewer, with the flag note, and list them in the rail as "Flagged for review", each linking to its area. Authors (`isAuthor`) can flag an area with a note (from the area's start or the rail), edit the note by flagging again, and clear a flag. Refresh flags from the `reviewFlags` in each `since` poll.
4. **Mock server.** Extend `tests/mock_hub.py` to follow the contract for all of the above: author permissions (a test user who is an author), dispositions list, the 5.6 state rules (dispose needs a change note; `null` clears; close, reopen, and their `409 invalid_state` cases), `expectedUpdatedAt` and `409 conflict` with `current`, 5.8 delete with tombstones, `state=all`, and Section 6 (one active flag per anchor; flagging again replaces the note; clearing is safe to repeat). Add contract tests for each to `tests/test_mock_hub.py`.
5. **Clean up from T2** (G's review of `v0.6.0`):
   - Replace every `window.confirm` (first-comment notice, Withdraw, Clear all) with in-page prompts. Native dialogs block the page and stop browser automation.
   - The InteropHub part of `guide.js` packs several statements on one line. Reformat it to match the rest of the file: one statement per line, the same indentation and brace style. No behaviour change in this step; keep it in its own commit-sized chunk of the diff if you can.
   - `tests/browser_check.py` posts its comment with `urllib`. Make it post through the guide's own form, and add one author action (dispose with a change note) through the page.
   - Close `HTTPError` responses in `tests/test_mock_hub.py`, so `ResourceWarning`s stop appearing on stderr (Windows PowerShell 5.1 then reports a failing exit code).
6. **Guidance.** A short section in `guidance/TopicReference.md`: what readers see (states, dispositions, change notes, flags, "Show closed") and what authors can do from the guide.
7. **Release.** CHANGELOG entry for `v0.7.0`. Do not tag; G releases after review.

## Out of scope

- Setup and registration tooling, `import_guide_comments.py`, the template project (T4).
- Any change to InteropHub.
- `aira.css` and the logo. Third-party Python dependencies.

## Done when

- `validate.ps1` passes and prints no `ResourceWarning`.
- Against the mock, from a local web server, in two browser profiles (an author and a reader): the author disposes with a change note, changes and clears a disposition, closes and reopens, deletes a reply, re-attaches an orphan, and flags and clears an area; the reader sees each change on the next refresh and has none of the author controls.
- Local mode and a guide without a `comments` setting behave exactly as `v0.6.0`.

## Report back

Files changed; how re-attach works for the author; how you ran the two-profile check and what you saw; anything in the contract that was unclear or awkward; anything not done. Do not commit, tag, or push.
