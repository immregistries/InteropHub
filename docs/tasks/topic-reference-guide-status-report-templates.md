# Report: Workstream A, templates v0.21.0 (build manifest and release registry)

**Plan:** [topic-reference-summary-annotations-and-guide-status.md](topic-reference-summary-annotations-and-guide-status.md), Workstream A, decisions D9, D10, D11, and Section 3.
**Repository:** `content-publishing-templates`, branch `main`. All changes are uncommitted in the working tree; nothing was committed, tagged, pushed, or published.
**Date:** 2026-11-17

Workstreams C, D, and E were not started.

## Read this first: three things to review

1. **v0.21.0's upgrade impact is `config`, not `rebuild` (deviation from Section 3).** A hosted guide adopts v0.21.0 by rebuilding and republishing: the upload carries the manifest. A public guide does not. Its manifest is saved by a new step in its own `.github/workflows/publish-guide.yml`, which lives in the project, not in `process/`, and that step needs a new `INTEROPHUB_TOKEN` repository secret. Moving the submodule and rebuilding therefore leaves a public guide at "No status reported". That is the definition of `config` (the same reason v0.15.1 is `config`). On the status page, `rebuild` and `config` are both amber, so the difference is only in accuracy. To follow the plan as written instead, change `"upgradeImpact"` for v0.21.0 in `process-releases.json` and the `**Upgrade impact:**` line in `CHANGELOG.md`. `validate.ps1` checks that the two agree.
2. **A public guide's InteropHub document key.** The public workflow sends the manifest to `--document "<project id>"`, the `id` in `guide.json`, which is also the repository name. The same key is already used for hosted guides (`comments.document`). **InteropHub must register each externally hosted guide under that key**, or the endpoint returns `not_found` and the workflow fails. Any project can override it by editing `--document` in its workflow.
3. **The public workflow fails after the Pages deploy, not before.** As A3 specifies, the manifest step runs after the deploy, so a missing secret or a hub error leaves the new guide live on GitHub Pages and the workflow red. Checking for the secret before deploying would avoid that half-published state, at the cost of blocking the deploy. I followed the plan. Say if you want the check moved earlier.

There are no changes to the shared contract.

## Files changed and why

New files:

| File | Why |
|---|---|
| `VERSION` | `v0.21.0` (A1). |
| `process-releases.json` | The release registry, newest first, with every release back to v0.1.0 (A1, D10). |
| `tools/process_release.py` | Reads `VERSION`, the registry, and the process commit for `build_guide.py`. Run as a command, it checks that `VERSION`, the registry, and `CHANGELOG.md` agree and fails if they don't (A1). |
| `tests/test_process_release.py` | Tests that validation fails when the records disagree or an impact is invalid (A4). |

Changed files:

| File | Why |
|---|---|
| `tools/build_guide.py` | Builds the schema 1 manifest and embeds it in `<head>` (A2). New functions: `build_manifest`, `manifest_script`, `section_warnings` (coded warnings), `source_state`, `pandoc_version`, `visibility`, `check_warnings`, `document_id`. `chapter_warnings` is kept and now wraps `section_warnings`. |
| `tools/check_content.py` | The checks moved into `run_checks(root, include, report_terms) -> ContentReport`, and `main()` prints from that report. The command-line output on the fixture is byte-for-byte the same as before (diffed). |
| `tools/publish_guide_to_hub.py` | Refuses a guide with no manifest, or an unreadable one, in every mode, including `--dry-run`. Adds `--manifest-only`, `--hub`, and `--document`. Maps `invalid_request` and `document_inactive` in `ERRORS`. The upload path is otherwise unchanged. |
| `tools/new_topic_reference.py` | Adds a `HUB_URL` value for public projects, used by the workflow. It comes from the hub address constant the file already had. |
| `tools/workspace_process.py` | Copies `VERSION` and `process-releases.json` into a workspace's `process/`. It skips paths a ref doesn't have, so installing a release before v0.21.0 still works (checked with `--ref v0.20.0`). |
| `templates/topic-reference/profiles/public/_github/workflows/publish-guide.yml` | New last step, "Save build manifest in InteropHub". It runs `--manifest-only` after the Pages deploy and fails with `exit 1` and an `::error::` message when `INTEROPHUB_TOKEN` is empty. |
| `templates/topic-reference/profiles/{public,private,private-workspace}/fragments/README_PUBLISHING.txt` | The project README's publishing section: a guide is not published until its manifest is saved. The workspace profile is unchanged, because a Workspace Topic Reference is not published. |
| `guidance/TopicReference.md` | New section "Saving the build manifest", plus updated start-up steps 5 and 6 (public: add the secret; private local: `--manifest-only` when releasing). |
| `guidance/Process.md` | Section 11, "Render and release": publishing a guide includes saving its manifest. |
| `README.md` | New release steps (CHANGELOG impact line, `VERSION`, registry, validate) and the `publish_guide_to_hub.py` commands. |
| `templates/guide/README.md` | Describes the manifest element. |
| `CHANGELOG.md` | The v0.21.0 entry, and an `**Upgrade impact:**` line on every earlier entry. |
| `tests/mock_hub.py` | `POST /api/guide/{doc}/builds` per the contract. Each upload also records its manifest as InteropHub does (B2): never refused; a missing, oversized, malformed, or unknown-schema manifest is recorded as an error. |
| `tests/test_build_guide.py` | `ManifestTests` (A4): the manifest is in `<head>`, parses, and matches schema 1; it holds no absolute paths, temp directory, or workspace `location`; warnings carry known codes; CONFIRM text stays out; `<` is escaped; a workspace guide has no `sourceCommit` or `sourceDirty`; visibility rules. |
| `tests/test_publish_guide_to_hub.py` | Covers `--manifest-only` (A4): success with and without a label, a guide hosted elsewhere using `--hub` and `--document`, missing manifest, bad schema or JSON, and the errors `forbidden`, `not_found`, `invalid_token`, and `document_inactive`, plus a missing token and the dry run. Uploads record an `UPLOAD` build with the manifest. Also tests the mock endpoint's `invalid_request` cases and its 201 body. |
| `tests/test_new_topic_reference.py` | The generated public workflow runs `--manifest-only` after the deploy, with the hub and document filled in, fails without the secret, and has no `continue-on-error`. |
| `tests/README.md` | Documents the mock's builds endpoint. |
| `validate.ps1` | Compiles the new tools, runs `process_release.py` (fails on disagreement), and checks the fixture guide's manifest: present in `<head>`, schema 1, `processVersion` equal to `VERSION`, and every warning coded. |
| `.github/workflows/validate.yml` | Compiles the new tools and adds a "Check release records" step. |

## Decisions the plan did not specify

**Release records**

- The check is a tool, `tools/process_release.py`, which `validate.ps1` and CI both call, rather than PowerShell logic. It requires all of the following:
  - `VERSION` equals the top registry entry and the top CHANGELOG heading.
  - The registry and the CHANGELOG list the same releases in the same order.
  - Each entry has exactly `version`, `date`, `upgradeImpact`, and `summary`.
  - Versions are strictly descending and dates never increase going down the list.
  - Every impact is `rebuild`, `config`, or `content`.
  - Any CHANGELOG `**Upgrade impact:**` line matches the registry, and the newest entry must have one.
- I added the impact line to every old CHANGELOG entry, because the impacts were already decided for the registry.
- **The v0.21.0 date is 2026-11-17, today.** Update it at tagging if the release goes out later. The plan's example used 2026-11-24.

**Manifest contents**

- **Fields left out instead of sent as null:**
  - `document.documentType` when `guide.json` has none
  - `document.interophubTopic` when there is no topic (a Workspace)
  - `processCommit` when it cannot be known
  - `build.pandoc` when the version cannot be read
  - `checks.openIdeas` when the project has no ideas register
- **`document.visibility`:** `guide.json` has no visibility field. The value is the `visibility` key if present. Otherwise `github-pages` hosting gives `public`, `interophub` hosting gives `private`, and `local` hosting leaves the field out.
- **`document.version`** is the build's version label. In the public workflow's `main` builds that is `main (abc1234)`. For a Workspace it is `workspace-YYYY-MM-DD`.
- **`processCommit`** comes from Git only when `process/` is its own repository top level, as a submodule is. Otherwise it is read from the `Commit:` line of `PROCESS-VERSION.txt` in a workspace copy. It is never the commit of an enclosing repository.
- **`sourceCommit` and `sourceDirty`** are left out when the guide is kept in a workspace (`keptIn` is `workspace`, which includes Workspace Topic References) or when the project is not inside Git. `sourceDirty` is `git status --porcelain -- .` run in the project root. It counts untracked files that aren't ignored, and only inside the project.
- **`builtAt`** is UTC, to the second, with a `Z` suffix.
- **Escaping and size:** the JSON is compact, and every `<` becomes `\u003c`. The build fails if the manifest exceeds 64 KB.

**Warnings**

- **`CONFIRM_OPEN`:** one warning per file, giving the count and line numbers, never the note text. CONFIRM notes are hidden HTML comments for authors and do not appear in the guide. Readers can see the manifest, so including their text would expose them.
- **`INFOGRAPHIC_WARNING`:** one per infographic warning, with `file` set to `content/infographic.yml`.
- **`CHAPTER_COUNT` and `APPENDIX_ORDER`:** `file` is the assembly path relative to the project root, such as `Project.qmd`.
- **Limits:** at most 100 warnings, and each message at most 300 characters. `confirmOpen` is always the full count.
- **Failures are not recorded.** Content-check failures, such as broken links, have no code in schema 1 and are not in the manifest. The build still doesn't fail on them; `check_content.py` and `validate.ps1` do, as before.

**Publishing tool**

- `--manifest-only` works for any `hosting`, including `interophub`. It takes at most one `--label`, checked against the same label pattern as uploads (`main`, `latest`, `vX.Y.Z…`). With no label, the body has no `label` key.
- Hub and document resolution: `--hub` and `--document` are used first, then `comments.hub` and `comments.document` when `comments.mode` is `interophub`. If neither is available, the tool fails with a clear message.
- `--label` is no longer required by argparse. The upload path checks for at least one label itself. The error for a non-hosted guide now suggests `--manifest-only`.

**Mock hub (tests only)**

- `external-document` is registered alongside `document`.
- Endpoint check order: author check (`forbidden`), then unknown document (`not_found`), then inactive (`document_inactive`), then the body (`invalid_request`).
- An upload's manifest error is recorded as `missing`, `too_large`, `unknown_schema`, or `malformed`. These names are the mock's own, not part of the contract.

## Deviations from the plan or contract

- **Contract:** none. The client sends `POST {hub}/api/guide/{documentKey}/builds` with `Authorization: Bearer`, `Content-Type: application/json`, and the body `{"label"?, "manifest"}`. It reads the 201 body's `buildId`, `documentKey`, `processVersion`, and `receivedAt`, and maps the four error codes.
- **Plan:**
  - v0.21.0 impact is `config` instead of `rebuild` (see the top of this report).
  - The public workflow's `--hub` and `--document` values are filled in when the project is generated. The plan did not say where an externally hosted guide's hub and document key come from.
- **Not done:** `GET /api/guide/{document}/builds` (B3) is not in the mock, because it is not part of the shared contract and no client uses it.

## Backfilled release impacts

| Release | Impact | Reason, from the CHANGELOG's adoption notes |
|---|---|---|
| v0.21.0 | config | Public projects copy a workflow step and add a secret. |
| v0.20.0 | content | Private projects replace `INTERNAL-USE.md` and rewrite status notices, `CONTRIBUTING.md`, and `AGENTS.md` (also `license` in `guide.json`). |
| v0.19.0 | rebuild | "Existing projects need no change." |
| v0.18.0 | rebuild | "Adopt by moving the submodule and rebuilding and republishing." |
| v0.17.0 | rebuild | Same wording. |
| v0.16.0 | content | "Projects adopt this by adding the directive to those tables." |
| v0.15.1 | config | Copy the new pandoc step into `publish-guide.yml`. |
| v0.15.0 | rebuild | `hosting` defaults to `github-pages`, so existing projects are unchanged. Hosting is opt-in. |
| v0.14.0 | rebuild | Workspaces are new; generated Standing projects are unchanged except optional `build.ps1` and `validate.ps1` tweaks. |
| v0.13.0 | content | Every Topic Reference needs `content/infographic.yml` written, and `check_content.py` fails without it. |
| v0.12.1 | rebuild | "Rebuild guides to pick up the fix." |
| v0.12.0 | rebuild | Public output unchanged; only the generator's arguments changed. |
| v0.11.0 | rebuild | Removing the section is optional ("may remove"). |
| v0.10.0 | rebuild | Conventions plus a warning only. |
| v0.9.0 | config | "Existing Topic References should remove `shift-heading-level-by: -1` from `_quarto.yml`." |
| v0.8.0 | rebuild | New slide patterns, opt-in. |
| v0.7.0 | rebuild | Guide behavior only. |
| v0.6.0 | rebuild | The `comments` setting is optional. |
| v0.5.1 | rebuild | **Judgment call.** A rebuild may expose a duplicate heading ID that needs a content edit, but only in some projects. |
| v0.5.0 | rebuild | **Judgment call.** Same as v0.5.1. Old local comments may need re-placing, but no edit is required. |
| v0.4.0 | rebuild | Additions only. |
| v0.3.0 | rebuild | Additions only. |
| v0.2.1 | rebuild | Fix. |
| v0.2.0 | rebuild | The web guide is new; `guide.json` is needed only to use it. |
| v0.1.0 | rebuild | First release; a value is required. |

Dates are the tag dates from `git log`.

## Validation and test output

`powershell -NoProfile -ExecutionPolicy Bypass -File validate.ps1` passed, with exit code 0. The relevant lines of output:

```text
Release records agree on v0.21.0.
Ran 104 tests in 78.596s
OK
...
Repository validation passed.
```

`validate.ps1` ran everything, including the headless browser comments check, the infographic export, and the PowerPoint builds. Chrome and python-pptx are installed on this machine.

To confirm the negative case, I set `VERSION` to `v0.22.0` and ran `validate.ps1` again. It failed with exit code 1, then I restored the file:

```text
2 release record problem(s):
  VERSION is v0.22.0 but the newest entry in process-releases.json is v0.21.0
  VERSION is v0.22.0 but the newest CHANGELOG.md heading is v0.21.0
VERSION, process-releases.json, and CHANGELOG.md disagree.
```

Other runs:

- `python -m unittest test_publish_guide_to_hub` (11 tests), `test_build_guide` (26), and `test_process_release` (7): all OK.
- `python tools/check_content.py --root tests/fixture-project`: output identical to the pre-refactor version (diffed).
- `python tools/check_content.py --include "guidance/*.md" --include "templates/*/*.md"` (the repository's own link check in CI): passed.

No failures remained. Two problems came up during development and were fixed: escape characters lost by a scripted edit of `check_content.py`, and an unclosed `HTTPError` that raised a ResourceWarning in a new test. Separately, one of my test commands built the guide to a mistaken relative path, which created a stray folder at `C:\dev\writing\Users`; I removed it.

## Sample manifest (fixture build)

This is from `build_guide.py --root tests/fixture-project --assembly Example.qmd --version test`, run in `validate.ps1`. It is 4,367 bytes as embedded; `processReleases` is shortened here. `sourceDirty` is false because only the fixture folder is checked, and it is unchanged.

```json
{
  "schema": 1,
  "generator": "content-publishing-templates",
  "processVersion": "v0.21.0",
  "processCommit": "ecee590",
  "processReleases": [
    {"version": "v0.21.0", "date": "2026-11-17", "upgradeImpact": "config",
     "summary": "Guides carry a build manifest, and publishing saves it in InteropHub for the guide status page."},
    {"version": "v0.20.0", "date": "2026-11-13", "upgradeImpact": "content",
     "summary": "Appendices and chapter-count warnings, an ideas register, a Collecting ideas status, and a topic-based restricted-use notice for private projects."},
    "... 23 more entries, through v0.1.0"
  ],
  "document": {
    "id": "example-core-document", "title": "Example Core Document", "status": "Draft",
    "version": "test", "keptIn": "git", "visibility": "public", "hosting": "github-pages",
    "interophubTopic": 7
  },
  "build": {
    "builtAt": "2026-11-17T17:23:54Z", "sourceCommit": "ecee590", "sourceDirty": false,
    "python": "3.14.3", "pandoc": "3.10"
  },
  "structure": {"chapters": 2, "appendices": 0, "hasInfographic": false},
  "checks": {
    "confirmOpen": 1,
    "warnings": [
      {"code": "CONFIRM_OPEN", "message": "1 CONFIRM note awaiting resolution, at line 13.",
       "file": "content/01-detail.md"}
    ]
  }
}
```

`processCommit` and `sourceCommit` are both `ecee590` because the fixture lives inside the templates repository, and the build ran on uncommitted changes on top of that commit. A real project shows its own commit for `sourceCommit` and its submodule's commit for `processCommit`.

## How to verify by hand

From `C:\dev\writing\content-publishing-templates`:

1. **Release records:** run `python tools/process_release.py`. It should print "Release records agree on v0.21.0." Change the v0.21.0 impact in `process-releases.json` to `huge`, or edit `VERSION`, and run it again. It should fail. Revert the change.
2. **Manifest in a guide:**
   - Run `python tools/build_guide.py --root tests/fixture-project --assembly Example.qmd --output ../../output/fixture-guide.html --version v1.0.0`.
   - Open `output/fixture-guide.html` in a text editor and find `id="guide-build-manifest"`. It should be inside `<head>`, contain no `C:\` path, and contain no CONFIRM note text.
3. **Publishing against the mock hub:**
   1. Start the mock with `python tests/mock_hub.py --port 8766`.
   2. The mock has no route that issues a personal access token. Insert one into `server.state.tokens` as `tests/test_publish_guide_to_hub.py` does, or run that test file, which does exactly this.
   3. With the token, run `publish_guide_to_hub.py output/fixture-guide.html --manifest-only --hub http://127.0.0.1:8766 --document external-document --label v1.0.0` (no `--config` change is needed). It should print "Build manifest saved: build 1 ...".
   4. Run the same command on a guide with the `<script id="guide-build-manifest">` element removed. It should fail with "The guide has no build manifest."
4. **Generated public workflow:**
   - Run `python tools/new_topic_reference.py ../../output/pub --no-git --visibility public --title "T" --topic 7 --topic-name "T"`.
   - Read `.github/workflows/publish-guide.yml`. The last step should check `INTEROPHUB_TOKEN`, run `exit 1` without it, and run `--manifest-only --label "${TARGETS%% *}" --hub "https://informatics.immregistries.org/hub" --document "t"`.
5. **Against InteropHub, once workstream B is deployed:**
   1. Build a hosted guide and upload it with `publish_guide_to_hub.py`. Its `es_guide_build` row should have `source = UPLOAD` and `process_version = v0.21.0`.
   2. For an externally hosted guide registered under its `guide.json` `id`, run `--manifest-only`. The 201 body should print, and a `MANIFEST` row should appear.
   3. Before any manifest is received, the status page should show "No status reported". After the first manifest, it should list v0.21.0 as the latest release.
