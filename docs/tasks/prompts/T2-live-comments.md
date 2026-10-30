# T2: Live Comments in the Guide (content-publishing-templates)

You are working in `C:\dev\writing\content-publishing-templates`, on `main` at `v0.5.1`.

## Read first

- `AGENTS.md` in this repository, and follow it.
- **Your specification:** `C:\dev\immregistries\InteropHub\docs\tasks\topic-reference-comments-api-contract.md` ("the contract"). You build the guide side of Sections 1, 2, 3.4 to 3.6, 4 (as a client), 5.1 to 5.5, the `text` field of 5.6, and 5.7. Where this prompt and the contract disagree, the contract wins; tell G.
- The design, for background: `C:\dev\immregistries\InteropHub\docs\topic-reference-comments-design.md`, Sections 4.2 to 4.4, 6.1, 6.2, 6.4, 9.1, 9.2, and 11.
- `templates/guide/guide.js`, `templates/guide/guide.css`, `tools/build_guide.py`, `guidance/TopicReference.md`, `tests/fixture-project/`, `validate.ps1`.

**Public repository rule.** The contract and design are internal documents. Do not copy their text, any InteropHub host name or URL, project names, or people's names into this repository. The mode name `interophub` and the field names are fine. Write guidance in your own generic words.

## Task

1. **`comments` setting.** `guide.json` may have:
   ```json
   "comments": { "mode": "interophub", "hub": "https://<host>/hub", "document": "<documentKey>" }
   ```
   `mode` is `interophub` or `local`; without the setting, the guide behaves exactly as `v0.5.1`. `build_guide.py` validates it (`hub` is an absolute `https` URL, or `http://localhost` / `http://127.0.0.1` with any port; `document` matches the contract's `documentKey` rule; trailing slash removed from `hub`) and passes it to the page in `guide-data`.
2. **One storage interface, two backends.** Put the comment code behind one interface: load, add, reply, edit, withdraw, and a change subscription for refreshes. The **local** backend is today's code and behaviour, unchanged. The **interophub** backend calls the API. Placement, deep links, and orphan handling (`v0.5.0`) work the same for both.
3. **Sign-in** (contract 3.4 to 3.6):
   - PKCE round trip exactly as in 3.4, including what goes in `sessionStorage`, the `state` check, removing the fragment, and restoring the reader's place, including `#anchor` and `#comment-<id>`.
   - Token in `localStorage` under a key that includes the hub and the document key. Treat a token past `expiresAt` as absent.
   - Any `401`: forget the token and show "Sign in to comment". **Never redirect on your own**, except for the `interophub=signin` marker (3.6).
   - Rail when signed out: "Sign in to comment". When signed in: "Signed in as <name>, <organization>", with **Sign out** and **Not you?** (3.5). Do Not you? last; if time is short, report it as not done.
4. **Reading and posting** (contract 5.1 to 5.5, 5.7, and `text` in 5.6):
   - On load, with a token: `GET /api/guide/{documentKey}`. If `canView` is false, say the reader's account cannot see comments on this document, and show none. If `isActive` is false, show comments but no way to add them.
   - List threads and place them by anchor, block index, and snippet. Show author name and organization, time, "edited" when `editedAt` is set, and replies under their comment.
   - Add a comment on a block (anchor, block index, snippet from the existing `blockSnippet`), and add a reply under a top-level comment. No replies to replies.
   - Edit and withdraw only when the thread's `permissions` allow it. Withdrawn items show "Withdrawn by the commenter." in place of the text.
   - The **first-comment notice**: the first time a user posts on this hub, before sending, tell them other signed-in users will see the comment with their name and organization. Remember the acknowledgement per user and hub.
   - Show the API's `message` for `4xx` errors near the form. Keep the reader's draft text when a post fails.
   - Not signed in: no comments at all, only the document and "Sign in to comment".
   - In interophub mode, hide the local-only controls: name box, Download, Load, Clear all.
5. **Refreshing** (design 9.2): use `GET …/comments?since=<serverTime>` and merge threads by `id`, including tombstones (remove deleted ones) and threads that have become `CLOSED` or `WITHDRAWN` (closed threads disappear; author controls and "Show closed" are T3). Back-off: 30 s after load, after posting, or after a check finds something new; then 1 min, 10 min, 30 min (maximum) while nothing changes. Pause while the tab is hidden; on becoming visible, check at once and restart at 30 s.
6. **Mock server.** `tests/mock_hub.py`, standard library only, generic (no real host names), following the contract for everything T2 uses:
   - a `/home` that skips sign-in and redirects straight back with `#code=…&state=…` for a test user chosen by a query parameter or setting, and that checks PKCE and `response_mode=fragment`;
   - `POST` and `DELETE /api/guide/token`, with the checks and single error message of 3.4 step 4;
   - CORS per Section 4, from a configurable list of allowed origins;
   - 5.1, 5.3 (including `since` and tombstones), 5.4, 5.5, `text` in 5.6, 5.7, with the edit window and permissions;
   - a way for tests to revoke a token, delete a comment, close a thread, and change `canView`, to drive the client.
   In-memory state is fine. Document how to run it in `tests/README.md` (create it if needed).
7. **Tests.** Extend `validate.ps1`:
   - Python tests (standard library) that run the mock and check it against the contract: PKCE success and each failure, CORS allowed and refused, `since` with a tombstone, edit window, reply-to-reply refused.
   - Build the fixture in local mode (unchanged output apart from the new setting handling) and in interophub mode pointing at the mock.
   - If Chrome or Edge is installed, a headless browser check of the round trip and one post against the mock; skip with a message if not, like the PowerPoint check.
8. **Guidance.** In `guidance/TopicReference.md`: the two comment modes, what the `comments` setting does, and that in interophub mode comments live in the hub, not the repository. Keep it short; T4 writes the full setup guidance.
9. **Release.** CHANGELOG entry for `v0.6.0`. Do not tag; G releases after review.

## Out of scope

- Author controls: state, disposition, change note, close, delete, re-attach, "Show closed", review flags (T3). Render nothing for them yet.
- Registration and setup tooling, `import_guide_comments.py` changes, the template project (T4).
- Any change to InteropHub.
- `aira.css` and the logo. Third-party Python dependencies.

## Done when

- `validate.ps1` passes.
- Against the mock, served from a local web server: sign in, comment, reply, edit, withdraw, and see a second user's comment appear without reloading. A revoked token returns the page to "Sign in to comment" without redirecting.
- A guide with no `comments` setting, and one with `"mode": "local"`, behave exactly as `v0.5.1`.

## Report back

Files changed; the storage interface (its methods, in a few lines); how you ran the browser checks and what you saw; anything in the contract that was unclear, missing, or awkward from the client side; anything not done. Do not commit, tag, or push.
