# Annotations Panel Prototype (Workstream E): Report

**Date:** 2026-11-18
**Plan:** `topic-reference-summary-annotations-and-guide-status.md`, decisions D6–D8 and Workstream E (E1–E7)
**Repository:** `C:\dev\writing\content-publishing-templates`. All changes are in the working tree: nothing committed, tagged, or released. `VERSION`, `process-releases.json`, `CHANGELOG.md`, and `aira.css` are untouched.
**Status:** The prototype works against the mock and against local InteropHub. `validate.ps1` passes.

## Addendum (2026-11-18): resizable panel

The product owner asked for a resizable panel. It is built, and the hosted guide was re-uploaded to the same label (`v0.3.0-panelprototype`, replaced).

- **Use:**
  - Drag the panel's right edge, which turns blue on hover. The range is 280 to 640 px, capped so the reading column keeps at least 640 px.
  - Double-click the edge to go back to 340 px.
  - The edge is focusable (`role="separator"` with `aria-valuenow`): Left and Right resize in 16 px steps (64 px with Shift), and Home resets it.
  - The width is remembered per document with the tab and open state. A `?lens=` link keeps the remembered width.
  - There's no handle on the bottom sheet below 900 px.
- **Right rail:** it now drops under the text whenever the window minus the panel leaves less than 940 px. Before, it dropped at a fixed 1280 px breakpoint, which assumed a 340 px panel. The script sets this as a `guide-rail-below` class.
- **Files:** `tools/build_guide.py` (the handle element), `guide.js` (width state, dragging, keys, memory, rail fit), `guide.css` (the handle, and the rail rule now uses the class), `templates/guide/README.md`, and `tests/browser_check.py`.
- **Browser check now covers:**
  - dragging wider and narrower, with the column following;
  - both limits, and the rail moving under the text and back;
  - arrow keys and Home;
  - memory across a reload, and double-click reset;
  - no handle on the bottom sheet.
- **Bug found and fixed:** my first version restarted the panel's slide-in animation at the end of every drag, so the panel jumped after each resize.
- **Harness fix:** Chrome now picks its own debug port and the check reads it from the profile. With fixed ports, a second run could attach to the first run's browser while it was still closing.
- **Validation:** `validate.ps1` exit 0, 109 tests OK, browser check passed. Two back-to-back quick runs passed. New screenshot: `wide-10-resized.png`.
- **Hub:**
  - A new temporary token (45) was created for the upload and revoked; the API returns 401 for it.
  - `immunization-cds` is still clean.
  - Comments 87–93 on `guide-hosted-test` were not added by this work: 87–88 are the product owner's, and 89–93 are "[check]" comments posted as the test reader, probably by another session.

## 1. Try it

### On local InteropHub (recommended)

Open **http://localhost:8080/hub/guide/view/guide-hosted-test/v0.3.0-panelprototype/**

This is the real Immunization CDS guide content, built with this repository's tools, hosted on InteropHub's own origin, with 7 sample change markers. Every marker note starts with "Sample:"; they are invented, not real changes. The guide also has 4 comment threads, 2 replies, 1 disposition, and 2 review flags, all added through the guide by two users.

- **Author view:** click **Comments** in the strip, then **Sign in to comment**. Sign in as `nbunker@immregistries.org`. The dev magic link is printed on the page; open it and press **OK**. You return to the guide signed in as an author, with the flag buttons, Dispose, Close, Edit, and Clear controls.
- **Reader view:** in the Comments tab choose **Not you?** and sign in as `reader.guide-test@example.invalid`, a non-admin member of the private test topic space. This user wrote 3 of the threads, so **Mine** has something to show.
- **Direct links:** add `?lens=comments`, `?lens=review`, or `?lens=changes` to the address.

### Against the mock (no InteropHub needed)

After `validate.ps1` has run, which makes `output/fixture-remote`:

```powershell
python tests/sample_change_markers.py output/fixture-remote/output/guide/index.html
python tests/mock_hub.py --port 8766 --allow-origin http://127.0.0.1:8765 --seed-demo
python -m http.server 8765 --bind 127.0.0.1 --directory output/fixture-remote
```

Open http://127.0.0.1:8765/output/guide/. The mock signs you in as "Test Reader". To be the author, add `&user=Test%20Author` to the mock's `/home` address when the browser lands on it.

## 2. Files changed and why

| File | Change |
|---|---|
| `tools/build_guide.py` | Page markup only. Adds the count strip to the sticky section bar and the panel (header, tablist, three tabpanels, comment filters, and the local-mode name, load, download, and clear controls). Takes the comments panel out of the right rail, which is renamed "On this page". No change to parsing or the manifest. |
| `templates/guide/guide.js` | Section tracking is unchanged. New shared annotations module: open and close state, tabs, lens body classes, `?lens=`, memory, Esc and focus return, lists grouped by section, show-in-text with highlight, count pills in the text, margin ticks, and change-marker reading and drawing. Both comment modes now feed the panel: threads render in the panel rather than inline, and new-comment forms still open under the block. Remote mode adds the Mine filter (`mine`, with a user-ID fallback for older hubs), the Open/All filter for flags (`includeCleared=true`), flag cards with Edit and Clear, and a signed-out state where the comment buttons lead to sign-in. Snippet text now ignores everything the guide inserts into a block. |
| `templates/guide/guide.css` | The existing flag styles are scoped to the Review lens; the "Hide comments" and rail-index rules are gone. New: the strip, the panel (pushes the column at 900 px and wider, bottom sheet below), tabs, filters, cards, change badges, popovers, and bars, comment count pills, ticks, the highlight, and print and reduced-motion rules. |
| `templates/guide/README.md` | Documents the panel and the **change-marker contract** (section 3). |
| `tests/mock_hub.py` | `mine` on threads and replies; `includeCleared` on review flags; `seed_demo()` and `--seed-demo` (comments, replies, a disposition, a closed thread, a comment that can't be placed, an active flag, and a cleared flag). |
| `tests/sample_change_markers.py` (new) | Stand-in for D: adds or replaces the marker element in a built guide, with fixture defaults or `--markers file.json`. |
| `tests/browser_check.py` | Rewritten for E7 (section 5), keeping every earlier comment check. Adds `--local-guide`, `--screenshots`, and `--quick`. |
| `tests/test_annotations_data.py` (new) | Unit tests: mock `mine` and cleared flags; the helper is idempotent, leaves other scripts alone, and escapes `</`. |
| `tests/README.md` | How to run the panel by hand and what the browser check covers. |
| `validate.ps1` | Passes the local-mode fixture guide to the browser check (`--local-guide`). |

## 3. The change-marker contract (for Workstream D)

`build_guide.py` emits, in the body before `guide-data`:

```html
<script type="application/json" id="guide-change-markers">
[{"anchor": "consent-requirements", "version": "v0.5.0", "level": "changed", "note": "…"},
 {"anchor": "consent-requirements", "blockIndex": 2, "version": "v0.5.0", "level": "feedback", "note": "…"},
 {"anchor": "oq-13", "version": "v0.5.0", "level": "changed", "note": "…"}]
</script>
```

| Field | Required | Meaning |
|---|---|---|
| `anchor` | yes | The existing review-area anchor: a section ID, a `<!-- anchor: -->` name, or a row anchor |
| `blockIndex` | no | **Added by me** (decision 4.3). Narrows the marker to one block of the area, matching `data-block-index`. Without it, the marker covers the whole area. |
| `version` | yes | Release label |
| `level` | yes | `changed` or `feedback` |
| `note` | yes | Plain text, at most 300 characters |

- A section anchor covers the whole section, with its badge under the heading. A row anchor covers its row, with the badge in the first cell. Any other anchor covers its area's blocks, or the one `blockIndex` names, with the badge before the first block.
- The build emits only the markers to show; `guide.js` does not compare versions. Markers are listed in document order, and `rows=` becomes one marker per row. With no markers, the element is omitted. `</` is escaped as `<\/`.
- An unknown anchor is listed under "Not placed" and is not drawn (D should make it a build error). Malformed entries are skipped with a console warning.

Full text: `templates/guide/README.md`, "Change markers".

## 4. Decisions the plan didn't cover, and deviations

1. **Threads live in the panel, not inline.** E3 says the Comments lens shows "comment markers and counts", so the text shows a yellow count pill per commented block (in the first cell for table rows). The full threads, replies, and author controls are in the panel. The new-comment form still opens under its block. After posting, the panel opens on the new thread.
2. **Bidirectional links.** Each panel item starts with a "↳ …" line naming its place in the text: click it to scroll there and highlight the place. A count pill in the text opens the panel at that thread.
3. **`blockIndex` added to the marker contract** (section 3). Without it, D could only mark a whole area, and most paragraphs have no anchor of their own.
4. **Mine** means threads the reader started *or replied to*. In local mode it matches the "Your name" field.
5. **Counts:** Comments counts open threads, unaffected by filters. Review counts open flags plus feedback markers. Changes counts all markers. A signed-out reader sees "–" for Comments, and Review counts only the source markers.
6. **Signed out:** comment buttons still appear on hover (E3), and clicking one opens the Comments tab with focus on **Sign in**. Before, the buttons were hidden while signed out.
7. **The section-bar purple dots** for flagged sections show only in the Review lens, to keep "panel closed = ticks only".
8. **ARIA:** the strip buttons use `aria-controls` with **`aria-expanded`**, not `aria-selected`, because `aria-selected` isn't valid on a plain button. The tabs use `role="tab"` with `aria-selected` and arrow keys. Deviates from E5's wording, not its intent.
9. **The label `panel-prototype` was rejected.** Both InteropHub (`GuideHtmlRules`) and the publish tool accept only `latest`, `main`, or `v<n>[-suffix]`, so I uploaded as **`v0.3.0-panelprototype`**. Side finding: the tool's suffix pattern allows hyphens (`[A-Za-z0-9.-]`) but InteropHub's doesn't (`[A-Za-z0-9.]`), so the tool accepts some labels the hub then refuses.
10. **Second user:** the existing non-admin test user `reader.guide-test@example.invalid` (user 111, member of the private test space).
11. **Panel width** is 340 px. Between 900 and 1280 px with the panel open, the right rail drops below the text so the reading column keeps its width.
12. **Filter reloads keep open forms.** Switching Open/All re-fetches from the hub. That render now keeps any form opened meanwhile, as polls do. The browser check found this race.

## 5. Validation

**`validate.ps1`** (run as `powershell -File validate.ps1`): **exit 0**, "Repository validation passed."

- Unit tests: `Ran 109 tests … OK` (104 before, plus 5 new).
- Browser check, with the full 65-second refresh test: *"Annotations panel (tabs, lenses, ?lens=, memory, Esc, bottom sheet, filters, author and reader views, local mode) and guide comments (refresh-safe drafts, blocked-request drafts, review flags, rows) check passed."*
- Failures along the way, all fixed:
  - One full run timed out on the browser check's first page load: a fresh headless profile loading a 3 MB page within a 12-second limit. The first load now gets 30 seconds, like later loads; the rerun passed.
  - During development, the browser check caught:
    - the closed panel still showing, because its `display: flex` overrode `hidden`;
    - a stale flag card after editing from the panel;
    - the filter-reload race in 4.12.
  - My first version of the marker helper deleted part of `guide.js` when run twice. Its pattern is now strict, with a unit test.

**What `tests/browser_check.py` covers (E7):**
- Closed by default, with ticks and no markers; the rail holds no comments or flags.
- Each tab opens from the strip with focus on its tab.
- Each lens shows only its markers: count pills; flags plus feedback markers; all change markers.
- Section, single-block, and row markers.
- Arrow keys between tabs; Esc closes the panel and returns focus to the strip.
- Memory across reload; `?lens=` overrides it.
- Show-in-text highlight.
- From the panel: post, dispose, reply, close, reopen with All, re-attach a comment that is not placed, edit a flag, the Open/All filter for flags (cleared flags without controls), and Mine for the author.
- Refresh-safe forms, row comments, blocked-request drafts, and clearing a flag from its marker (earlier checks, kept).
- Reader: no author or flag controls, and Mine uses the hub's `mine` field.
- Bottom sheet at 420 px, with Esc.
- Signed out: dashes, and a comment button that leads to sign-in.
- Local mode: Review lists only the 2 source markers, without a flag filter; the load and download controls are in the Comments tab; posting a comment; Mine by name.

**Local InteropHub:**
- Built from `immunization-cds` with a temporary `guide.json` in the scratchpad (hosting `interophub`, document `guide-hosted-test`) and uploaded with `publish_guide_to_hub.py`: `v0.3.0-panelprototype: created, 3251976 bytes`.
- Through the guide UI in headless Chrome:
  - The reader added 3 comments and 1 reply.
  - The admin added 1 reply, 1 comment, 2 flags (a block area and the WD-9 row), and 1 disposition.
- Confirmed in the database: comments 81–86 and the 2 flags on document 11. The reader got no flag controls.
- `immunization-cds` `git status` is clean before and after. No other registered guide was touched.
- Personal token 37 ("panel-prototype publish (temporary)") is revoked. The API now returns 401 for it, and the local copy is deleted.

## 6. Screenshots

`C:\dev\writing\content-publishing-templates\output\annotations-prototype\` (git-ignored):

- **Mock, wide (1400 px):**
  - `wide-1-closed-author.png`: panel closed, ticks only
  - `wide-2-comments-author.png`
  - `wide-3-review-author.png`
  - `wide-4-changes.png`
  - `wide-5-comments-reader.png`, `wide-6-review-reader.png`: reader view
  - `wide-7-signed-out.png`
  - `wide-8-local-review.png`, `wide-9-local-comments.png`: local mode
- **Mock, narrow (420 px):** `narrow-0-closed.png`, `narrow-1-comments.png`, `narrow-2-review.png`, `narrow-3-changes.png`
- **Local InteropHub, real content:** `hub-author-comments.png`, `hub-author-review.png`, `hub-changes.png`, `hub-reader-comments.png`, `hub-reader-closed.png`

## 7. What to judge first

1. **Threads in the panel instead of inline** (4.1). This is the biggest change in reading feel. Try reading a section with Comments open and decide whether the count pills plus panel beat inline threads.
2. **Panel closed by default, with ticks only.** Are the ticks enough of a cue that a document has been commented on, or should the strip counts be more prominent?
3. **The Review tab mixing flags and feedback markers.** Are the two kinds distinct enough? Flags say "Review flag" with who and when; markers say "Changed · feedback wanted" with the version.
4. **Change badges in the text.** Is the neutral "Changed in vX" badge too loud for an FYI, given it sits above every changed block?
5. **The bottom sheet on a phone.** It covers 70% of the screen.

## 8. Known gaps

- **Not done here (C and D):**
  - The "What changed" list (D4)
  - Markers in Word and the reading copy
  - Version filtering and the "newest tag + 1" rule. The hub sample shows `v0.3.0-1-gce10f34` on badges because the build wasn't a release.
  - Manifest marker counts
  - Front-section navigation
- **No CHANGELOG entry**, as instructed. v0.22.0 needs one: the built output and markup change, and the right rail's comment panel is gone.
- **Left in the local database:** the hosted version `v0.3.0-panelprototype` and the test comments and flags on `guide-hosted-test`. Also two guide sign-in tokens, one for the admin and one for the test reader, created by the headless sessions; they can be revoked on /hub/account under Access tokens. Nothing else was created.
- **Polls rebuild the panel list** every 30 seconds or more. Scroll position and open forms survive. A thread with an open form keeps its old content until the form closes, as before.
- **No screen-reader pass yet.** Structure and keyboard paths are tested; reading order and announcements are not.
- **Touch:** comment and flag buttons are always visible below 64rem, as before. The count pills on phones sit above the block.
- **Local-mode "Mine"** relies on the name the reader typed; loaded files from people with the same name count as theirs.
- **Changes tab order:** markers are listed by section in document order. D4's "feedback first" ordering is for the generated list, not the tab.

## Version display and comment modes

**Date:** 2026-11-18
**Built to:** `topic-reference-comments-api-contract.md` as amended 2026-11-18 (2, 5.2–5.4, 6, 7.5), and section 4 of `topic-reference-version-display-report.md`.
**State:** In the `content-publishing-templates` working tree, uncommitted. `VERSION`, `CHANGELOG.md`, and `process-releases.json` are untouched.

### Files changed

| File | Change |
|---|---|
| `templates/guide/guide.js` | **Sends `pageUrl`** (`location.href`) on every new comment, reply, and flag, and on every comments read and poll. An address longer than 2,000 characters is not sent (the page is then treated as having no label). **Keeps `labelPolicy`** from each read and applies it: Closed, Release, or unknown (below). **Authors:** threads and flags with `inScope: false` get "From another version (label)" and a dashed card. They are listed but left out of the strip and tab counts. Cards in scope show "Written on label". **`403 comments_closed`** on a write keeps the form, its text, and the saved draft, and says "Comments are closed on this version. Your text is still here and saved in this browser." The page then switches to its closed state. **A changed mode** reported by a poll triggers a full reload (decision 4 below). |
| `templates/guide/guide.css` | The Comments-tab mode line; Closed hides the comment buttons, flag buttons, count pills, and forms (except a refused one); the "From another version" marking and "Written on" text; author tool rows now wrap in the panel (they overflowed with five or more tools). |
| `tools/build_guide.py` | One element: the mode line (`#guide-label-note`, `role="status"`) at the top of the Comments tab. |
| `tools/publish_guide_to_hub.py` | **Label pattern** now matches InteropHub's `GuideHtmlRules`: after the suffix's first character, only letters, digits, and dots (`[0-9]` rather than `\d`). The error message gives examples. **`--display`** and **`--comment-mode`** call `PATCH /api/guide/{key}/labels/{label}` for the **first** `--label`, after a successful upload or `--manifest-only` (so the label is known). The tool prints the result and any demoted Current label. If the publish step fails, no settings are sent. `--dry-run` describes the settings it would send. |
| `tests/mock_hub.py` | Follows the amended contract: known labels (uploads, build reports, settings, registered URLs); `pageUrl` resolution (hosted path, longest registered prefix, query and fragment ignored, scheme and host without case); publication time; scope; Open, Release, and Closed filtering for readers and authors, including `since` listings; `labelPolicy`, `inScope`, and `madeOnLabel`; `400 invalid_field` for a long `pageUrl`; `403 comments_closed` on comments, replies, and flags; `GET /labels`, and `PATCH /labels/{label}` with demotion, its validation errors, and a scope check. Its upload label pattern now matches InteropHub's. `seed_demo(state, site)` adds three versions (`main` Open, `v0.2.0` Current and Release, `v0.1.0` Retired and Closed). `/test/label` changes a mode directly. |
| `tests/test_annotations_data.py` | +6 tests: the mock's Open, Release (with `since`), and Closed behavior; writes recording labels; no `pageUrl` and an unknown one; `GET` and `PATCH /labels`. |
| `tests/test_publish_guide_to_hub.py` | +4 tests: the label pattern (valid and invalid examples); publishing a release and making it Current in Release mode, then demoting it with the next release; `--manifest-only` with settings; errors (no label, a failed upload sends no settings, a token without `topics:write`, dry run, a bad choice). `setUp` now clears uploads and labels so tests don't depend on order. |
| `tests/browser_check.py` | A new comment-modes section against the mock, with copies of the guide served as Release and Closed versions (section "Test results"). |
| `templates/guide/README.md`, `tests/README.md` | A "Versions and comment modes" section; the mock's labels and `--site`. |

### Decisions

1. **Comments-tab line.**
   - Release: "This tab shows comments for this release and earlier." Authors also see "Comments from other versions are marked."
   - Closed: "Comments are closed on this version."
   - Open, and `labelPolicy` null or absent: no line.
2. **Closed state:**
   - The Comments count shows "–" (not 0, which would suggest nobody commented), with the title "Comments are closed on this version".
   - The Open/All/Mine filters are hidden.
   - The Review tab keeps the source's feedback-wanted change markers: they belong to the document, not to comments. Its count is just those.
3. **Out-of-scope items with no `madeOnLabel`** (untagged, created after publication) read "From another version", with no label in brackets.
4. **Mode changes while a page is open.** Since-listings never send tombstones for threads a new mode hides, and never resend threads it reveals. So when a poll's `labelPolicy.commentMode` differs from the previous one, the guide re-reads the whole list. Without this, a reader would keep seeing the old set until a reload.
5. **A refused write switches the page to Closed at once.** A form in the text stays, with its message and draft. A reply form in the panel stays until the next refresh, when the list empties; its text is still saved in the browser.
6. **Cleared flags** (Review tab, All) come from `GET /review-flags?includeCleared=true`, which takes no label (see the next section). In Release mode the guide applies the scope rule itself: readers don't get cleared flags from other versions, and authors see them marked. In Closed mode cleared flags are not fetched.
7. **`--display` and `--comment-mode` apply to the first `--label` only.** The common call is `--label v0.4.0 --label latest --display current --comment-mode release`, and only one label can be Current. An explicit per-label form seemed unnecessary.
8. **Display state is not shown in the guide** (no "Retired" or "Authors only" banner). The task asked only for comment modes, and the hosted viewer already shows readers the retired page. See "Known gaps".

### Test results

- **`validate.ps1`:** exit 0, "Repository validation passed." `Ran 119 tests … OK` (109 before, plus 10).
- **Browser check:** passed, including the full 65-second refresh test. Run with `--quick` it passed again after each change. New checks against the mock:
  - **Release, as a reader:**
    - the line is shown, without the authors' sentence;
    - another version's comments are absent, and the count matches the list;
    - a comment posted there is stored with `madeOnLabel: v0.2.0`.
  - **Release, as an author:**
    - two comments are marked "From another version (main)", and their cards are dashed;
    - "Written on v0.2.0" appears on the in-scope cards;
    - the comment count excludes the other versions;
    - a flag from `main` is marked, and the Review count excludes it.
  - **Closed, author and reader:**
    - the line is shown, the count is "–", and the filters are hidden;
    - there are no threads, flag markers, comment or flag buttons, or flag cards;
    - the Review count is just the 2 source markers.
  - **Mode switched under an open page:** Release → Open reveals the hidden thread at the next refresh, and back to Release restores the line.
  - **Refused write:**
    - the author types, the mock closes `v0.2.0`, and the author saves;
    - the form stays with its text and message, the draft is in local storage, the buttons are hidden, the count is "–", and the flags are gone.
- **Local InteropHub** (`guide-hosted-test`, the real Immunization CDS build):
  - Published with the updated tool to three new labels of mine:
    - `v0.2.0-modesopen`
    - `v0.2.0-modesrelease` with `--comment-mode release`
    - `v0.2.0-modesclosed` with `--comment-mode closed`
  - The tool printed "display unlisted, comments release." and "…comments closed." The pattern refused `v0.2.0-modes-bad` locally.
  - Then 14 checks through the guide UI in headless Chrome, as `reader.guide-test@example.invalid` and the admin (an author): **14 passed, 0 failed**, on the first build and again after republishing the final build.
    - **Open label:** no line.
    - **Release, reader:** the line; a comment written on the Open label after publication is hidden; earlier comments are shown; the count matches the list (11).
    - **Release, author:** that comment is marked "From another version (v0.2.0-modesopen)"; "Written on v0.2.0-modesrelease" on the new one; the count is 12 with 1 marked and excluded.
    - **Closed, both people:** the line, no comments, flags, or buttons, and "–".
    - **Refused write:** a real `PATCH` to Closed while the reader was writing gave `403 comments_closed`; the text and draft were kept; the label was set back to Release afterwards.
  - **Database:** comment 94 has `made_on_label` `v0.2.0-modesopen` and 95 has `v0.2.0-modesrelease`; the refused comment was not stored.
  - **Left unchanged:** a before/after comparison of `es_guide_label` shows the existing `main`, `v0.1.0`, `v0.2.0`, and `v0.3.0` settings unchanged, and no other uploads touched. `immunization-cds` `git status` is clean.
  - **Tokens:** two temporary personal tokens (49 and 52), created on /hub/account, revoked (the API returns 401 for them), and deleted locally.
- **Failures found and fixed along the way:**
  - The mock's first version reported real scope on Open labels instead of `inScope: true`; the browser check caught it, and a unit test now covers it.
  - The refused-write path first left the old flag list and counts showing until the next refresh.
  - The author's tool row overflowed the panel.
  - One test of my own wrongly expected a re-set Release mode to hide a thread (see the next section).

### Contract points that didn't fit cleanly

1. **`GET /review-flags?includeCleared=true` takes no `label` or `pageUrl`**, so cleared flags arrive unfiltered and without `inScope`. The guide applies 5.3.1's scope rule itself, using `madeOnLabel`, `createdAt`, and `labelPolicy.publishedAt` (decision 6). Passing `pageUrl` to that endpoint too would be simpler and keep the rule in one place.
2. **Since-listings and mode changes:** the contract says a hidden thread is never sent, not even as a tombstone. That is right for privacy, but the guide can only learn of a mode change from `labelPolicy`, so it re-reads everything when the mode changes (decision 4). Worth stating in 5.3.1 as client guidance.
3. **Re-setting Release moves the publication time** of a label with no upload or build (5.3.1, third fallback). Switching such a label Release → Open → Release brings every earlier comment into scope. This follows the contract, and found my test's wrong expectation, but authors may not expect it. Hosted and build-reported labels aren't affected.
4. **The `inScope: false` count rule** isn't in the contract. "Keep them out of the reader-facing counts" was applied to the strip and tab counts. The in-text count pills still include them for authors, because they match what the panel lists.
5. **Label pattern:** the publish tool now matches InteropHub's `GuideHtmlRules`. The contract's 7.4 describes hosted labels in words; it could quote the pattern, so the two can't drift again.

### Screenshots

In `C:\dev\writing\content-publishing-templates\output\annotations-prototype\`:

| File | Shows |
|---|---|
| `modes-1-release-reader.png` | Mock, Release version, reader |
| `modes-2-release-author.png` | Mock, Release version, author: "From another version (main)" and "Written on v0.2.0" |
| `modes-3-closed-author.png` | Mock, Closed version, author |
| `modes-4-refused-closed.png` | Mock, a save refused because the version closed; text kept |
| `hub-modes-1-release-reader.png` | Local InteropHub, `v0.2.0-modesrelease`, reader |
| `hub-modes-2-release-author.png` | Local InteropHub, the same label, author: "From another version (v0.2.0-modesopen)" |
| `hub-modes-3-closed-author.png` | Local InteropHub, `v0.2.0-modesclosed`, author |
| `hub-modes-4-refused.png` | Local InteropHub, the refused save |

### Known gaps

- **No display-state banner.** Authors viewing a Retired or Authors only label get no notice in the guide; `labelPolicy.display` is available for one.
- **Tab label wraps.** On the 340 px panel a two-digit count wraps the "Comments" tab label onto two lines. It is readable, but a tighter tab style would help.
- **Local test data left in the database:**
  - the uploads `v0.2.0-modesopen`, `v0.2.0-modesrelease`, and `v0.2.0-modesclosed`, with their two settings rows (Release and Closed);
  - comments 94–97, the `[modes]` comments from both runs.
- **Guidance not updated:** `guidance/TopicReference.md` and `Process.md` don't mention `--display` and `--comment-mode` yet. The tool's own help and docstring do.
- **No CHANGELOG entry**, as instructed. v0.22.0 needs one, covering `pageUrl`, the comment modes, and the new publish options.
