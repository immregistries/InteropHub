# T5: Hosted Publishing (content-publishing-templates)

You are working in `C:\dev\writing\content-publishing-templates`, on `main` at `v0.13.0` (plus one CI commit).

## Read first

- `AGENTS.md` in this repository, and follow it.
- **Your specification:** `C:\dev\immregistries\InteropHub\docs\tasks\topic-reference-private-hosting.md`, Sections 2, 4, and 6. The API you call is in Section 3.5 there and summarized below; InteropHub (H5) is building it at the same time, so build against the mock. Where this prompt and that document disagree, the document wins; tell G.
- The comment API contract, for conventions and the error shape: `C:\dev\immregistries\InteropHub\docs\tasks\topic-reference-comments-api-contract.md`, Sections 1 to 3 and 7.
- What exists now: `tools/build_guide.py` (`comments_config` validates `guide.json` `comments`), `tools/check_content.py`, `tools/new_topic_reference.py`, `templates/topic-reference/profiles/public/` (has `_github/workflows/publish-guide.yml` for GitHub Pages) and `templates/topic-reference/profiles/private/` (local only today: its `products/web-guide/brief.yml` says the guide is not published because Pages from a private repository can be public), `tests/mock_hub.py`, `tests/test_mock_hub.py`, `guidance/TopicReference.md`.

**Public repository rule.** The design and contract are internal. Do not copy their text, host names, project names, or people's names here. Write guidance in your own generic words.

## The upload API (as H5 builds it)

- `POST {hub}/api/guide/{documentKey}/versions/{label}` with the built HTML as the body, `Content-Type: text/html`, `Authorization: Bearer <personal access token>` (scope `topics:write`; the user must be an author of the topic). `201` for a new label, `200` for a replacement. Response: `{"label", "sizeBytes", "uploadedBy", "uploadedAt", "viewUrl"}`.
- `GET {hub}/api/guide/{documentKey}/versions` lists them.
- Labels: `latest`, `main`, or `v` followed by digits and dots with an optional `-suffix`. 20 MB maximum.
- Errors use the contract's error shape: `400 invalid_field`, `401`, `403 forbidden`, `404 not_found`, `409 invalid_state` (document not set up for hosting).
- The guide is then read at `{hub}/guide/view/{documentKey}/{label}/`, only by signed-in users who can view the topic.

## Task

0. **Fix first: the background refresh discards open forms** (found on production, Gate 3 finding H3.4). When the guide's periodic refresh re-renders comment threads, any form open on a thread is removed with its text: a dispose form with a typed change note was wiped after 20 seconds with no change on the server, and a reply draft about 50 seconds in. Edit forms are likely affected too. Keep open forms and their text (and focus and cursor position) across refreshes: for example, skip re-rendering a thread with an open form and apply its update when the form closes, or preserve and restore the form. If a thread changed on the server while its form was open, keep the user's text; the existing `409 conflict` handling covers the save. Add a check for this to `tests/browser_check.py` against the mock (open a dispose form and a reply form, type, let at least two refreshes pass, then confirm the text is still there and saves). Ship this in its own release, `v0.13.1`, before the rest of T5, so the pilot can take it alone; G releases it after review.
1. **`hosting` in `guide.json`:** `local`, `github-pages` (the default when absent, today's behavior), or `interophub`. Validate it where `comments` is validated: with `interophub`, `comments.mode` must be `interophub` and `comments.hub` and `comments.document` are required. `check_content.py` reports the same errors. A guide built for `interophub` hosting needs no change in `guide.js`; confirm that and say so.
2. **`tools/publish_guide_to_hub.py`** (standard library only): uploads a built guide file under one or more labels, for example `--label v0.4.0 --label latest`, taking the hub and document key from `guide.json` (`comments.hub`, `comments.document`) and the token from the `INTEROPHUB_TOKEN` environment variable. It refuses to run when `hosting` is not `interophub`, refuses an `http` hub other than localhost, never prints or logs the token, prints each label's result and view URL, explains each error code in one plain sentence, and exits non-zero on any failure. A `--dry-run` that checks everything locally and sends nothing.
3. **Private profile workflow:** `templates/topic-reference/profiles/private/_github/workflows/publish-guide.yml` that builds the guide and uploads `vX.Y.Z` and `latest` on a tag, and `main` on a push to `main`, using a repository secret named `INTEROPHUB_TOKEN`. It does nothing (and says why) when `hosting` is not `interophub`, so a private project that stays local is not broken. Mirror the public workflow's version and ref logic.
4. **Private profile text.** Update the private profile's web-guide brief and fragments so a private project can be either local only or hosted in InteropHub, and says how to tell which. Do not change the public profile's behavior.
5. **`new_topic_reference.py`:** for the private profile, an option to choose hosted or local (default local), which writes `hosting` and `comments` into `guide.json` accordingly and adds the workflow. If T4 is also touching this file later, keep the change small.
6. **Mock.** Extend `tests/mock_hub.py` with the two versions endpoints (author and reader users as for T3; `409 invalid_state` for a document not set up for hosting; replacement returns `200`) and add tests to `tests/test_mock_hub.py`. Add tests for `publish_guide_to_hub.py` against the mock: success, replacement, reader `403`, missing token, wrong `hosting`, `--dry-run` sends nothing, and the token never appears in output.
7. **Guidance.** A short section in `guidance/TopicReference.md`: the three ways a guide can be hosted (local, public pages with hub comments, hosted in the hub for private topics), how to choose, and how to publish a hosted guide (personal access token from the hub's account page, the secret, the script).
8. **Release.** CHANGELOG entry for `v0.15.0` (task 0 already shipped in `v0.14.0`). Do not tag; G releases after review. Report back after task 0 so G can release it before you continue.

## Out of scope

- Any change to InteropHub.
- The rest of T4 (registration step, `import_guide_comments.py`, template `AGENTS.md`).
- Changing any content project; P5 moves the first private project after this is released.
- `aira.css` and the logo. Third-party Python dependencies.

## Done when

- Task 0: an open dispose, reply, or edit form keeps its text across refreshes, in the browser check and by hand against the mock.
- `validate.ps1` passes.
- The fixture project builds unchanged with no `hosting` setting, and with `"hosting": "github-pages"`.
- `publish_guide_to_hub.py` uploads to the mock and replaces a label; its output never contains the token.
- A new private project from `new_topic_reference.py` with the hosted option validates and has the workflow; one with the local option has none.

## Report back

Files changed; the `guide.json` rules as built; example script output (success and one error); how the workflow decides what to upload; anything in the API description that was unclear. Do not commit, tag, or push.
