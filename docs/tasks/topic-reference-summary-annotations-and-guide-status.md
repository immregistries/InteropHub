# Topic Reference: Executive Summary, Change Markers, Annotations Panel, and Guide Status

**Status:** Plan, decisions approved 2026-11-17 by the product owner (Section 2). Not started.
**Repositories:** `content-publishing-templates` (workstreams A, C, D, E) and `InteropHub` (workstream B).
**Releases:** InteropHub's next production release carries B. Templates v0.21.0 carries A; v0.22.0 carries C, D, and E.

## 0. Progress and how to resume (as of 2026-11-17)

- **Workstream A (templates v0.21.0): done and reviewed, not committed.** Changes are in the working tree of `C:\dev\writing\content-publishing-templates`. The agent's report is `topic-reference-guide-status-report-templates.md`, next to this file. A review confirmed: release records agree, 62 affected tests pass, and the fixture manifest is in `<head>`, schema 1, with no paths and coded warnings.
- **Workstream B (InteropHub): done and reviewed 2026-11-18, not committed.** The report is `topic-reference-guide-status-report-interophub.md`, next to this file. The contract is unchanged; the guide tests pass (10/12/3). It upgrades aira-web to 0.1.17 (pushed upstream), which ships with this release.
- **Decided at review:** v0.21.0's upgrade impact is `config`, not `rebuild`, because public guides must add a workflow step and secret.
- **2026-11-18: fixes 1–4 below are done** (`validate.ps1` passes, 104 tests). Also done: the `not_found` error now says to register the guide. The end-to-end test passed against local InteropHub with the real tools: three real guides built, two manifest-only reports, one hosted upload, an unregistered guide refused with `not_found`, a guide without a manifest refused, and the status page, detail page, and filters correct. Test tokens revoked. The test rows stay in the local DB until the next refresh. **Remaining:** commit both repos, then tag v0.21.0 with the real date (fix 5).
- **Fixes to make to A before release:**
  1. `publish_guide_to_hub.py` resolves the document key in this order: `--document`, then `comments.document`, then `guide.json` `id`. The public workflow stops passing `--document` and `--hub`, so `guide.json` is the only place the key is set.
  2. The public `publish-guide.yml` checks for `INTEROPHUB_TOKEN` *before* the Pages deploy. The manifest send stays after the deploy.
  3. `new_topic_reference.py` writes `"visibility"` explicitly into `guide.json`. Inferring it from hosting stays as the fallback.
  4. The v0.21.0 CHANGELOG adoption notes say: register the guide in InteropHub if it is not already. These guides likely are not registered (local or no comments): `content-publishing-process`, `emerging-standards-fhir-rationale`, `ivc-practices`, `signal-iis-community-information`.
  5. Set the real v0.21.0 date in `process-releases.json` and `CHANGELOG.md` at tagging. It is currently 2026-11-17.
- **Next steps:**
  1. Apply fixes 1–4 and run `validate.ps1`.
  2. Review B's report.
  3. Run an end-to-end test: build a guide with v0.21.0 and publish it, by upload and with `--manifest-only`, to local InteropHub. Check the `es_guide_build` rows and the status page (No status reported, current, behind, unreadable).
  4. Commit both repos (on instruction).
  5. Tag v0.21.0, deploy InteropHub, publish the first guide on v0.21.0, and move the remaining guides.
- **To resume in Claude Code:** open `C:\dev\writing\content-publishing-templates` and run `claude --resume`, choosing the session about "Executive Summary, change markers, annotations panel, guide status". Or start fresh and say: "Read `C:\dev\immregistries\InteropHub\docs\tasks\topic-reference-summary-annotations-and-guide-status.md` section 0 and continue."

## 1. Summary

Four improvements to how Topic Reference guides are written, read, and managed:

1. **Executive Summary and Scope.** Every guide opens with an Executive Summary, the primary infographic, and a Scope with In scope, Out of scope, and Not yet decided. They sit above Chapter 1 and have no menu entry.
2. **Change markers.** Authors can mark what changed in this version, either as FYI or as a request for feedback. The markers are written in the source and expire on their own when the next version is released.
3. **Annotations panel.** Comments, review flags, and changes move out of the right rail into a left panel. The panel is closed by default and has one tab for each kind. The text shows only the kind you have selected.
4. **Guide status in InteropHub.** Each build records its process version, document version, and warnings in the guide. InteropHub reads that record and shows every guide's status on one admin page, including which guides need upgrading and how much work each upgrade is.

## 2. Decisions

| # | Decision |
|---|---|
| D1 | The Executive Summary, infographic, and Scope form a front section above Chapter 1. It is not in the section bar and does not count as a chapter. |
| D2 | The Executive Summary may repeat content from the chapters. It is the only section allowed to. |
| D3 | Executive Summary length: about one printed page. The build warns above 550 words and fails above 650. It also warns below 150, because a summary that short duplicates the InteropHub topic overview, which is capped. |
| D4 | Scope has **In scope** and **Out of scope** (both required) and **Not yet decided** (optional). |
| D5 | `00-status-and-scope.md` is retired. Its parts move into the front section (Section C3). There is no compatibility mode. |
| D6 | There are three change levels. Level 0 is no marker. Level 1 is a `changed` marker (FYI). Level 2 is a `changed … feedback` marker (feedback wanted). |
| D7 | Change markers live in the source and belong to one version. They appear in every output and stop showing once the version moves on. InteropHub review flags stay as they are: workflow state that carries across versions. The two are deliberately separate. |
| D8 | Annotations move to a left panel with **Comments**, **Review**, and **Changes** tabs, closed by default. The active tab controls which markers appear in the text. The right rail shows only navigation. |
| D9 | Each guide carries a machine-readable build manifest. Publishing a guide always saves its manifest in InteropHub: hosted guides with the upload, externally hosted guides by sending it on its own. A publish that does not save the manifest has failed. |
| D11 | InteropHub never contacts outside sources for status. It lists every registered guide and shows what that guide last reported. A guide that has reported nothing is shown as "No status reported". What InteropHub knows about process releases comes only from the manifests it receives. A new process release stays provisional, and InteropHub doesn't know it exists, until at least one guide is published with it. This is intended: publishing the first guide on a release is how that release goes live. |
| D10 | Each process release declares its upgrade impact: `rebuild`, `config`, or `content`. |

## 3. Release sequence

1. **Templates v0.21.0 (workstream A):** version file, release registry, and build manifest. Its upgrade impact is `rebuild`, so every guide can adopt it by rebuilding and republishing.
2. **InteropHub release (workstream B):** stores manifests and adds the status page. It also accepts uploads without a manifest, so the two releases can ship in either order.
3. **Move every guide to v0.21.0.** The status page lists every registered guide from the start. A guide shows "No status reported" until it is republished with v0.21.0, so the page itself is the to-do list.
4. **Templates v0.22.0 (workstreams C, D, and E):** front section, change markers, and annotations panel. Its upgrade impact is `content`, because each guide needs an Executive Summary and Scope written.
5. **Migrate the guides to v0.22.0,** tracking progress on the status page.

E is prototyped first within v0.22.0 (Section E6) so the product owner can try it before C and D are finished.

---

## Workstream A: Process version and build manifest (templates v0.21.0)

### A1. The process knows its own version

- Add a `VERSION` file at the repository root containing the release label, for example `v0.21.0`.
- Add `process-releases.json` at the root, newest release first:

  ```json
  [
    {"version": "v0.21.0", "date": "2026-11-24", "upgradeImpact": "rebuild",
     "summary": "Guides carry a build manifest for InteropHub's status page."}
  ]
  ```

  `upgradeImpact` is one of three values:
  - `rebuild`: move the submodule, rebuild, and republish
  - `config`: also edit `guide.json` or a workflow
  - `content`: authors must write or restructure content
- `build_guide.py` copies the whole registry into each manifest (A2). That is how InteropHub learns about releases without fetching anything.
- Each CHANGELOG release gets an **Upgrade impact:** line that matches its registry entry.
- `validate.ps1` fails when these disagree: `VERSION`, the top registry entry, and the top CHANGELOG heading must match, and the impact values must be valid.
- Release steps in the README gain "update `VERSION` and `process-releases.json`."

### A2. Manifest contents (schema 1)

`build_guide.py` embeds `<script type="application/json" id="guide-build-manifest">` in the guide's `<head>`:

```json
{
  "schema": 1,
  "generator": "content-publishing-templates",
  "processVersion": "v0.21.0",
  "processCommit": "ecee590",
  "processReleases": [
    {"version": "v0.21.0", "date": "2026-11-24", "upgradeImpact": "rebuild", "summary": "…"},
    {"version": "v0.20.0", "date": "2026-11-10", "upgradeImpact": "content", "summary": "…"}
  ],
  "document": {
    "id": "contextual-conditions", "title": "…", "documentType": "Topic Reference",
    "status": "Draft", "version": "v0.5.0", "keptIn": "git",
    "visibility": "public", "hosting": "interophub", "interophubTopic": 7
  },
  "build": {
    "builtAt": "2026-11-24T18:03:11Z", "sourceCommit": "a1b2c3d", "sourceDirty": false,
    "python": "3.12.7", "pandoc": "3.6.3"
  },
  "structure": {
    "chapters": 6, "appendices": 2, "hasInfographic": true,
    "hasExecutiveSummary": true, "executiveSummaryWords": 512,
    "hasScope": true, "changeMarkers": 4, "feedbackMarkers": 1
  },
  "checks": {
    "confirmOpen": 0, "openIdeas": 3,
    "warnings": [
      {"code": "CHAPTER_COUNT", "message": "8 chapters; the suggested size is 6 to 7.", "file": "Project.qmd"}
    ]
  }
}
```

Rules:

- **Readers can see the manifest,** so it holds only what any reader of the guide may see. Files are named by repository-relative path. It never contains machine paths, the workspace `location`, or tokens.
- **Warnings have stable codes** so the status page can filter on them. The first set is `CHAPTER_COUNT`, `APPENDIX_ORDER`, `CONFIRM_OPEN`, `SUMMARY_MISSING`, `SUMMARY_LONG`, `SUMMARY_SHORT`, `SCOPE_MISSING`, `SCOPE_OUT_UNLINKED`, `STALE_CHANGE_MARKER`, `RETIRED_STATUS_AND_SCOPE`, and `INFOGRAPHIC_WARNING`. Codes are added in later releases and never renamed.
- **`structure` fields that a release doesn't know yet are left out,** not reported as false. In v0.21.0 the summary, scope, and marker fields are absent.
- **`sourceCommit` and `sourceDirty`** are omitted for guides kept in a workspace, which have no Git repository.
- **Checks run inside the build:** `check_content.py` is refactored so `build_guide.py` can call its checks directly and put the findings in `checks`. Running it from the command line works as before.

### A3. Saving the manifest is part of publishing

- **Hosted guides:** `publish_guide_to_hub.py` uploads the guide, which carries the manifest. It fails if the built guide has no manifest.
- **Externally hosted guides:** `publish_guide_to_hub.py --manifest-only` reads the manifest from the built guide and POSTs it to `/api/guide/{document}/builds` (B3).
  - The public profile's `publish-guide.yml` runs it after deploying to GitHub Pages, using an `INTEROPHUB_TOKEN` repository secret.
  - Without the secret, the workflow fails. It is not skipped.
- **Guides built locally and shared by hand** report with `--manifest-only` when they are released.
- The guidance (`Process.md`, `TopicReference.md`, the project README's publishing section) lists saving the status to InteropHub as a publishing step: a guide has not been published until its manifest is saved.
- `tests/mock_hub.py` implements the builds endpoint and checks uploaded guides for the manifest.

### A4. Tests

- `test_build_guide.py`:
  - the manifest is present, parses, and matches schema 1
  - it contains no absolute paths
  - warnings have codes
- Validation fails if `VERSION`, the registry, and the CHANGELOG disagree.
- `test_publish_guide_to_hub.py` covers `--manifest-only`.

---

## Workstream B: Guide status in InteropHub (next production release)

### B1. Schema (`db/unapplied_updates.sql`)

```sql
-- Build records for Topic Reference guides. One row per hosted upload, plus
-- one per manifest-only report from an externally hosted guide. The manifest
-- is kept whole; the columns are copies of its fields used for listing and filtering.
CREATE TABLE es_guide_build (
  guide_build_id BIGINT NOT NULL AUTO_INCREMENT,
  guide_document_id BIGINT NOT NULL,
  guide_hosted_version_id BIGINT NULL,
  source VARCHAR(16) NOT NULL COMMENT 'UPLOAD or MANIFEST',
  label VARCHAR(64) NULL,
  manifest_schema INT NULL,
  process_version VARCHAR(32) NULL,
  document_version VARCHAR(64) NULL,
  document_status VARCHAR(40) NULL,
  built_at DATETIME(6) NULL,
  source_commit VARCHAR(64) NULL,
  warning_count INT NULL,
  manifest_json JSON NULL,
  manifest_error VARCHAR(300) NULL,
  received_by_user_id BIGINT NOT NULL,
  received_at DATETIME(6) NOT NULL,
  PRIMARY KEY (guide_build_id),
  KEY ix_es_guide_build_document (guide_document_id, received_at),
  CONSTRAINT fk_es_guide_build_document FOREIGN KEY (guide_document_id) REFERENCES es_guide_document (guide_document_id),
  CONSTRAINT fk_es_guide_build_version FOREIGN KEY (guide_hosted_version_id) REFERENCES es_guide_hosted_version (guide_hosted_version_id),
  CONSTRAINT fk_es_guide_build_user FOREIGN KEY (received_by_user_id) REFERENCES auth_user (user_id),
  CONSTRAINT chk_es_guide_build_source CHECK (source IN ('UPLOAD', 'MANIFEST'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

Follow `docs/database-release-practice.md` and rehearse the migration on a freshly restored database.

### B2. Reading the manifest on upload

- `GuideHostingService.upload` looks for `<script type="application/json" id="guide-build-manifest">`, parses it, and saves an `es_guide_build` row linked to the new hosted version.
- **The manifest never blocks an upload:**
  - no manifest (a guide built before v0.21.0): the row has a null process version and is shown as "before v0.21.0"
  - malformed manifest, or an unknown schema: `manifest_error` is set and the upload still succeeds
- Size cap: the manifest is ignored if it exceeds 64 KB.

### B3. Manifest-only endpoint

- `POST /api/guide/{document}/builds` takes `application/json`, the manifest, with an optional `label`.
- It uses the same bearer authentication, scope, and topic-edit permission as the upload, and works for `EXTERNAL` and `HUB` documents alike.
- It returns 201 with the stored row.
- `GET /api/guide/{document}/builds` returns the build history to topic editors.

### B4. Knowing the latest process release

InteropHub makes no outside requests. It works from the manifests it has received:

- **The latest process release** is the highest `processVersion` reported by any guide's latest build. The first guide moved to a new release brings that release with it.
- **The release list** is the `processReleases` array from that same manifest. It holds every release up to the latest, with dates and upgrade impacts.
- From that list the page works out, for each guide:
  - how many releases it is behind
  - the most demanding upgrade impact between its version and the latest

  A guide that reported a version missing from the list shows its version without a behind count.
- Until any guide reports a manifest, the page shows only the registered guides, all with "No status reported".

### B5. The status page

Extend `/admin/es/guides` (`AdminGuideServlet`) with a **Status** view. It is admin-only at first.

It is the birds-eye view of every Topic Reference: what is current, what is behind, and what needs attention.

**One row per registered, active guide document,** whether or not it has ever reported. A guide with no build record shows **No status reported** in place of the build columns. A guide whose latest record failed to parse shows **Status unreadable** with the parse error.

**Columns:**
- Guide and topic, linked
- Hosting
- Process version, with a "N behind" badge: green when current, amber when behind with only `rebuild` or `config` impact, red when a `content` upgrade is pending
- Upgrade impact
- Document version and status
- Last build and last publish, as dates
- Warnings: a count that opens the list
- Open comments and open review flags, from the existing tables
- Feedback markers, from `structure.feedbackMarkers`

**Summary line above the table:**
- number of registered guides
- how many are current
- how many are behind, split by upgrade impact
- how many have no status reported
- how many have warnings

**Filters:** no status reported, needs upgrade (by impact), has warnings, by warning code, not published in 60 days.

**Detail view per guide:** build history with the newest first, the warnings of the latest build, and the raw manifest.

**Sort order:** no status reported first, then most behind, then by warning count.

Volume is about 100 guides with a few builds a week each, so one query with a latest-build-per-document subquery is enough. No caching table is needed.

### B6. Tests

- `GuideHostingTest`: upload with a manifest, without one, with a malformed one, and with one over the size cap.
- A new `GuideBuildServiceTest`:
  - the manifest-only endpoint's permissions
  - the latest release and release list come from the highest reported manifest
  - releases-behind and impact calculation
  - a version missing from the release list shows no behind count
  - registered guides with no builds appear as "No status reported"
- A servlet test that the Status view renders and its filters apply.

---

## Workstream C: Executive Summary and Scope (templates v0.22.0)

### C1. Source files

| File | Section ID | Contents |
|---|---|---|
| `content/00-executive-summary.md` | `executive-summary` | `# Executive Summary`, the status notice (C3), then prose. A short list is allowed. |
| `content/00-scope.md` | `scope` | `# Scope`, one sentence of purpose, then `## In scope`, `## Out of scope`, and optionally `## Not yet decided`, each a bullet list. |

- `Project.qmd` includes both files first, in that order.
- The tools recognize the two IDs, not their position in the file list.
- **Out of scope:** each item should say where the topic is handled, with a link to the related topic or document, or "(not covered by any current topic)".
- **Not yet decided:** each item should link to its open question or working-decision row when one exists.

### C2. Rules and checks (`check_content.py`, `build_guide.py`)

| Check | Result |
|---|---|
| Either file missing | Error (`SUMMARY_MISSING` or `SCOPE_MISSING`) |
| `00-status-and-scope.md` still present | Error (`RETIRED_STATUS_AND_SCOPE`), with migration steps |
| Executive Summary over 550 words | Warning (`SUMMARY_LONG`) |
| Executive Summary over 650 words | Error |
| Executive Summary under 150 words | Warning (`SUMMARY_SHORT`) |
| Scope missing In scope or Out of scope, or either list empty | Error |
| Scope heading other than the three allowed | Error |
| Out-of-scope item with no link and no "(not covered by any current topic)" | Warning (`SCOPE_OUT_UNLINKED`) |

**How words are counted:** the prose and list text of the Executive Summary, excluding the heading, the status notice, and HTML comments. 550 words fills about one Letter page in the Word template (11 pt body, 1-inch side margins), with room for a heading and paragraph spacing.

The chapter count and appendix-order checks ignore the front section.

### C3. What happens to `00-status-and-scope.md`

| Old part | New place |
|---|---|
| Status notice (`{{SCOPE_STATUS}}`) | A `::: {.status-notice}` block at the top of `00-executive-summary.md`. The guide shows it as a banner under the title; Word as a shaded paragraph. |
| In brief | Becomes the start of the Executive Summary, then expanded |
| Purpose | The opening sentence of `00-scope.md` |
| What this project covers | `## In scope` |
| What this project does not cover | `## Out of scope` |

### C4. Guide layout

- Order under the title block: status banner, Executive Summary, infographic, Scope.
- **Scope layout:**
  - In scope and Out of scope are cards side by side, with check and x icons from the existing icon set.
  - Not yet decided is a full-width card beneath them, with the help icon.
  - On narrow screens the cards stack.
- **Navigation:**
  - The section bar starts at Chapter 1.
  - The document title links to the top.
  - While the reader is in the front section, the right rail lists "Executive Summary" and "Scope."
- Comments, review flags, and change markers work in the front section as anywhere else.
- The infographic stays where `infographic_figure` puts it, now after the Executive Summary rather than before the first section.

### C5. Word and the reading copy

- **Word:**
  - The order is the same.
  - `insert_word_infographic.py` places the infographic before the Scope heading instead of before the first chapter.
  - Scope is a two-column table, In scope beside Out of scope, with Not yet decided as a full-width row beneath when present. A Quarto Lua filter shipped in `templates/word/` builds the table, and it gets the `AIRA2` table style like the other tables.
- **Reading copy:** `CoreDocument.md` keeps headings and lists, which read well on GitHub.

### C6. Skeleton, guidance, and fixture

- `new_topic_reference.py`:
  - writes the two new files with CONFIRM prompts
  - drops `status-and-scope` from `navLabels` in the skeleton `guide.json`
  - places the `SCOPE_STATUS` fragment in the status notice
- **Conventions:**
  - The Executive Summary is for a reader who reads nothing else. It may repeat the chapters, it is refreshed every release, and it differs from the InteropHub topic overview.
  - The Scope rules from C1.
- `TopicReference.md` and `Process.md` gain the new files and the refresh step.
- **Test fixture:**
  - Replace `tests/fixture-project/content/00-summary.md` with the two new files.
  - Regenerate `CoreDocument.md` and review the difference.
  - Add tests for each check in C2.

---

## Workstream D: Change markers (templates v0.22.0)

### D1. Syntax

The markers follow the `<!-- row-anchors -->` directive pattern:

```markdown
<!-- changed v0.5.0: Consent now follows the state rule; former rule 3 removed. -->
<!-- changed v0.5.0 feedback: New exception for school-based clinics. -->
<!-- changed v0.5.0 rows=OQ-13,OQ-14: Reworded after the October call. -->
```

- **Before a block** (paragraph, list, table, figure, diagram), the marker covers that block.
- **Before a heading,** it covers the section down to the next heading at the same or a higher level.
- **Before a table with row anchors,** a marker with `rows=` covers those rows. It sits after the `<!-- row-anchors -->` line.
- **The note is required,** at most 300 characters, plain text. It says what changed, not just that something did.
- **The version** must match the release label pattern the infographic already uses.

### D2. Which markers show

- **The current version** is the `--version` passed to the build. For guides kept in a workspace it is the `guide.json` `version`. When the build label is not a release (for example `main`), the current version is the newest release tag plus one, so markers written for the coming release show.
- Markers for the current version (or a later one) are shown.
- Older markers are hidden, listed by `check_content.py` as `STALE_CHANGE_MARKER`, and counted in the manifest. Authors delete them when starting the next version.
- **Errors:** a malformed marker, a marker followed by no block, and `rows=` naming a row that does not exist.

### D3. Rendering

| Output | Level 1 (`changed`) | Level 2 (`changed … feedback`) |
|---|---|---|
| Guide | Neutral badge "Changed in v0.5.0" with the note on hover or focus, and a thin left bar in a neutral accent | Purple "Changed · feedback wanted" badge and purple bar, matching review flags |
| Word | One-line italic note above the block: *Changed in v0.5.0: …* | The same, prefixed **Feedback wanted.** |
| Reading copy | `> **Changed in v0.5.0:** …` | `> **Changed in v0.5.0, feedback wanted:** …` |

### D4. "What changed" list

- When the current version has any markers, the front section gets **What changed in v0.5.0**, between the Executive Summary and the infographic.
- It lists every marker in document order: section, note, and level, with feedback-wanted items first. Each entry links to its spot.
- The list is generated and has no source file.
- Authors may copy it into the CHANGELOG or release notes.

### D5. Tests

- Parsing and placement: block, heading, and rows.
- Showing and hiding by version, including a `main` build.
- Stale marker reporting.
- The same markers render in the guide, the reading copy, and Word.
- The list order.
- The manifest counts.

---

## Workstream E: Annotations panel (templates v0.22.0)

### E1. Layout

- **Count strip:** the sticky section bar gains a strip at its right end: `Comments 12 · Review 3 · Changes 5`, each with its icon. A count of zero is shown dimmed. Clicking a count opens the panel on that tab.
- **Left panel:**
  - It slides in from the left edge and is about 340 px wide on wide screens, where the reading column shifts rather than being covered.
  - Below 900 px it becomes a bottom sheet.
  - Esc closes it, and focus returns to the strip.
- **Right rail:** navigation only. The comments panel and the "Review flags (n)" list leave it.

### E2. Tabs

| Tab | Lists | Filters |
|---|---|---|
| Comments | Comment threads by section, then "Not placed" | Open / All, Mine |
| Review | InteropHub review flags and Level 2 source markers together, by section. Each item is labeled "Review flag" (with who and when) or "Changed · feedback wanted" (with version). | Open / All for flags |
| Changes | Level 1 and Level 2 source markers by section | none |

- Clicking an item scrolls to it and highlights it briefly.
- Author controls stay with each item: disposition, close, edit or clear a flag, and re-attach an orphan.
- In local-comments mode, the Review tab shows only source markers. Load and download controls move into the Comments tab.

### E3. One layer at a time

- **The active tab decides what the text shows:**
  - Comments: comment markers and counts
  - Review: purple bars and badges
  - Changes: change badges and bars
- **Panel closed:** the text shows only faint ticks in the left margin wherever any annotation exists. The comment and flag buttons still appear on hover.

### E4. Links and memory

- `?lens=comments|review|changes` opens the guide with the panel on that tab. This lets a release email link straight to the changes.
- The last tab and open state are remembered per document in local storage, in a try/catch block. A link overrides the remembered state.

### E5. Accessibility

- The strip buttons and tabs are real buttons with `aria-controls` and `aria-selected`.
- The panel is a labeled region, and the lists are keyboard navigable.
- Markers do not rely on color alone: badges carry text.

### E6. Prototype, then finish

1. Build E against `tests/fixture-project` with `tests/mock_hub.py`. The fixture gets sample comments, flags, and both marker levels, with D's parsing stubbed if it is not ready.
2. Capture screenshots with `tests/browser_check.py --screenshots` (wide, narrow, each tab, panel closed). Serve the build locally so the product owner can try it.
3. Adjust from that review, then finish with D's real markers.

### E7. Tests

`browser_check.py` covers:
- opening each tab from the strip
- the lens controlling which markers show
- `?lens=`
- Esc and focus return
- the bottom sheet at narrow width
- author controls inside the panel
- a reader's view without author controls
- local-comments mode

---

## 4. Migrating a guide to v0.22.0

1. Move `process/` to v0.22.0.
2. Split `content/00-status-and-scope.md` into the two new files (C3), then write the Executive Summary up to about one page.
3. Add Not yet decided items and links for out-of-scope items.
4. Update `Project.qmd` and remove `status-and-scope` from `navLabels`.
5. Optionally, add change markers for this version.
6. Build, fix errors, review warnings, publish, and confirm the guide shows as current on the status page.

## 5. Open items

- Whether topic editors (not only admins) should see a status view of their own guides. This can follow later; the API in B3 already gives editors the data.
- A later batch tool to run `rebuild`-impact upgrades across many projects, using the status page as its list.
