# Guide status in InteropHub: implementation report (Workstream B)

**Plan:** [`topic-reference-summary-annotations-and-guide-status.md`](topic-reference-summary-annotations-and-guide-status.md), Workstream B (decisions D9, D11; section 3; manifest from A2).
**Date:** 2026-11-17
**State:** Implemented in the working tree. Nothing is committed or pushed. No outside service was contacted.

## Summary

- `es_guide_build` is added to `db/unapplied_updates.sql`. It was applied locally and then rehearsed on a freshly restored database.
- Every hosted upload now records a build row, and the manifest never blocks the upload.
- `POST` and `GET /api/guide/{documentKey}/builds` work as the shared contract describes. The contract is unchanged.
- `/admin/es/guides?view=status` lists every registered active guide with the B5 columns, summary line, filters, and sort order. `&guide={key}` opens a per-guide detail view.
- The full suite passes: 419 tests run, 0 failures, 0 errors, 3 skipped. The 3 skipped tests were already skipped before this change.

## Files changed and why

| File | Change |
|---|---|
| `db/unapplied_updates.sql` | Appends the `es_guide_build` table from plan B1, word for word. The only addition is a pointer to the plan in the header comment. |
| `src/main/resources/hibernate.cfg.xml` | Maps `EsGuideBuild`. |
| `model/EsGuideBuild.java` (new) | Entity for `es_guide_build`. `manifest_json` is mapped as `columnDefinition = "JSON"`, the same way as `EsTopicChange.changesJson`. |
| `dao/EsGuideBuildDao.java` (new) | History newest first. Latest build per document comes from one query with a correlated subquery: newest `received_at`, ties to the higher id. |
| `service/GuideBuildManifest.java` (new) | Finds `<script type="application/json" id="guide-build-manifest">` in uploaded HTML and parses and validates schema 1. `uploadRow()` builds the upload's row and never throws. |
| `service/GuideBuildService.java` (new) | The manifest-only report (POST), build history (GET), the B4 release calculation, and the B5 status model: states, behind count, impact, sort, summary counts. All of the logic is pure and runs against a `Store` interface. |
| `service/HibernateGuideBuildStore.java` (new) | Database side. The status page uses a fixed number of grouped queries no matter how many guides there are: documents, topics, latest builds, open comments, open flags, last hosted upload, guide URLs. |
| `service/GuideRows.java` | Adds `BuildRow`. |
| `service/GuideHostingService.java` | `upload` builds the row from the HTML and passes it to `Store.save`. It also adds `processVersion` to the upload's `es_topic_change` JSON when there is one. The upload response is unchanged. |
| `service/HibernateGuideHostingStore.java` | `save` persists the build row in the same transaction as the hosted version, linked through `guide_hosted_version_id`. `toDocument` and `toTopic` are now package-visible for reuse. |
| `servlet/GuideApiServlet.java` | Adds the two `/builds` routes and a constructor that takes a `GuideBuildService`. |
| `servlet/GuideStatusRenderer.java` (new) | HTML for the Status view and the detail view, plus the filter logic. It is kept out of the servlet so tests can render it without a database or a signed-in admin. |
| `servlet/AdminGuideServlet.java` | `?view=status` and `?view=status&guide=key`. Both pages get a Registrations / Status switch (`aira-segmented-control`). The Registrations view is still the default. |
| `test/.../GuideHostingTest.java` | Five new tests (see Tests). The in-memory store now records build rows. |
| `test/.../GuideBuildServiceTest.java` (new) | 12 tests. |
| `test/.../servlet/GuideStatusRendererTest.java` (new) | 3 tests. |
| `docs/aira-web/aira-css-proposed-changes.md` | Proposed `aira-code-block` for the raw-manifest `<pre>`, then marked it available when aira-web 0.1.17 shipped it, and added the 0.1.17 revision-history row. |
| `pom.xml`, `docs/aira-web/README.md`, `docs/aira-web/reference/aira.css`, `docs/aira-web/components-guide.md`, `docs/aira-web/adoption-guide.md`, `docs/aira-web/aira-css-changes-revision-17.md` | Upgraded to aira-web 0.1.17 and refreshed the mirrors. |

`docs/tasks/topic-reference-guide-status-report-templates.md` also appears in `git status`. It belongs to the templates agent; I did not touch it.

## Final SQL

This block is appended to `db/unapplied_updates.sql`:

```sql
-- Build records for Topic Reference guides. One row per hosted upload, plus
-- one per manifest-only report from an externally hosted guide. The manifest
-- is kept whole; the columns are copies of its fields used for listing and filtering.
-- See docs/tasks/topic-reference-summary-annotations-and-guide-status.md, Workstream B.
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

**Rehearsal:** I ran `restore_interophub_db_from_latest_local.py`, which restored the backup on disk (dated Nov 17 00:30) and reapplied the whole `unapplied_updates.sql`. It exited 0. `SHOW CREATE TABLE es_guide_build` matches, with all three foreign keys and the CHECK. The app started cleanly against it: `hibernate.hbm2ddl.auto=validate` accepted the new entity.

## Decisions the plan did not specify

1. **Upload with an unknown schema.** `manifest_error` is set and `manifest_schema` holds the reported number. `manifest_json` keeps the parsed object, so the raw view can show what arrived. For malformed JSON, a manifest over 64 KB, or a script element of the wrong `type`, `manifest_json` is null.
2. **What counts as an unreadable schema-1 manifest.** All of these set `manifest_error` on upload and return `invalid_request` on POST:
   - `schema` missing or not an integer
   - `processVersion` missing, or longer than its column
   - `document.version`, `document.status`, or `build.sourceCommit` longer than their columns
   - `build.builtAt` that is not an ISO-8601 instant
   - `processReleases` or `checks.warnings` that are not lists
   - a release entry with no `version`

   Fields the plan lists that InteropHub doesn't use are not checked.
3. **`built_at` time zone.** `builtAt` (UTC) is converted to the JVM zone, the same convention as every other guide timestamp (`ApiTime`). Admin pages show server-local time.
4. **POST `label`.** Free text up to 64 characters, trimmed; blank means null. It is not held to the hosted-version label rule, because an external guide's label can be anything.
5. **POST and GET scopes.** POST needs `topics:write`, like the upload. GET accepts `topics:read`, which `topics:write` also satisfies, plus topic-edit permission. GET works on inactive documents so their history stays readable. POST on an inactive document returns `document_inactive`, checked after `forbidden`, in the same order as the upload.
6. **GET response shape.** `{"documentKey", "builds": [...]}`. Each build has `buildId, source, label, manifestSchema, processVersion, documentVersion, documentStatus, builtAt, sourceCommit, warningCount, manifestError, receivedBy, receivedAt, manifest`. Null fields are written as JSON `null`.
7. **POST does not write an `es_topic_change` row.** Only the upload does, as it did before. Status reports are frequent and machine-generated. Add one if you want them in the topic change log.
8. **Hosted uploads with no manifest** (plan B2, "shown as before v0.21.0"):
   - State is `NO_MANIFEST`. The version cell reads "Before {oldest release InteropHub knows}", which is "Before v0.21.0" once v0.21.0 is reported. Before any manifest has been received it reads "No manifest".
   - The guide counts as behind every known release, with their most demanding impact, and is included in "needs upgrade".
   - A guide with no build row at all, which includes every hosted guide uploaded before this release, shows **No status reported**, as D11 requires.
9. **Which manifest sets the latest release.** Among latest builds that parsed, the one with the highest `processVersion` wins. Versions are compared numerically: `vMAJOR.MINOR[.PATCH][-suffix]`, a pre-release sorts below its release, and a missing patch equals `.0`. Unreadable builds are ignored, whatever version they claim. The release list is that manifest's `processReleases`, sorted newest first. A release with an unknown `upgradeImpact` adds to the behind count but not to the impact.
10. **States, sort, and badges.**
    - States: `NO_STATUS`, `UNREADABLE`, `NO_MANIFEST`, `UNKNOWN_VERSION` (the version is not in the list: no behind count, an info badge "Not in release list", not counted as needing upgrade), `BEHIND`, `CURRENT`.
    - Sort: No status reported, then Status unreadable, then most behind, then most warnings, then title.
    - Badges: green "Current"; amber "N behind" for rebuild or config; red for content. Amber is also used when the impact is unknown.
11. **Last build and last publish.**
    - "Last build" is the latest build's `build.builtAt`.
    - "Last publish" is the later of the newest hosted upload and the latest build's `received_at`.
    - "Not published in 60 days" also matches guides that were never published. Those show a "Never" badge.
12. **Open comments** use the same rule as the topic page's open count: live, top-level, state `OPEN`. **Open review flags** are uncleared flags.
13. **Filters.** "Show" offers: all, no status reported, needs upgrade (any impact, or rebuild, config, or content), has warnings, and not published in 60 days. It combines with a "Warning code" select whose options are the codes found in the latest builds. Both are plain GET parameters (`filter`, `code`), so a filtered view can be bookmarked.
14. **Detail view.**
    - Shows: state, warnings of the latest build, build history (newest first, including unreadable rows with their error), and the latest build's raw manifest, pretty-printed and HTML-escaped.
    - A key that isn't registered returns 404 with a message.
    - An inactive registration still shows its history; it just has no status row.
15. **Extra on the status page:** a "Process releases known to InteropHub" table that names the guide it came from, so it's visible where the release list came from (D11).
16. **Guide links.** An external guide links to its first registered URL. A hub guide links to its hosted viewer: the `latest` label if it exists, otherwise the label uploaded most recently.

## Deviations from the plan or contract

- **Contract:** none.
- **Plan B2:** B2 says a manifest over 64 KB "is ignored". Following the agent brief, it is recorded with `manifest_error` ("The build manifest is larger than 64 KB."), the manifest is not stored, and the upload succeeds.
- **CSS:** `aira.css` had no code-block style, so I proposed `aira-code-block` upstream instead of adding a local class. aira-web 0.1.17 shipped it, and the raw-manifest `<pre>` now uses `aira-code-block aira-code-block--wrap` with `tabindex="0"` and an `aria-label`. There is no inline style or local CSS. Everything else uses existing `aira-*` classes: `aira-badge--success/warning/danger/subtle/info/outline`, `aira-segmented-control`, `aira-toolbar`, `aira-table--compact`, `aira-section-card`.

## Tests

Commands:

```
mvn -o test -Dtest=GuideHostingTest,GuideBuildServiceTest,GuideStatusRendererTest
mvn -o test
```

Results:

- Targeted run: GuideHostingTest 10/10, GuideBuildServiceTest 12/12, GuideStatusRendererTest 3/3.
- Full suite: `Tests run: 419, Failures: 0, Errors: 0, Skipped: 3`, BUILD SUCCESS.
- The 3 skipped tests are the `*PersistenceTest` classes, which were already skipped.
- After the last renderer polish, the guide test classes were rerun and all passed.

Coverage against plan B6:

- **GuideHostingTest**
  - Upload with a manifest: every column copied, linked to the hosted version, `\u003c` escaping survives, upload response unchanged.
  - Upload without a manifest.
  - Malformed JSON, unknown schema, schema 1 with no `processVersion`, and a wrong `type` attribute: each records an error and the upload succeeds.
  - Over the size cap.
  - Element found among other scripts with either quote style.
- **GuideBuildServiceTest**
  - Manifest-only report for EXTERNAL and HUB documents.
  - Permissions: `not_found`, `forbidden`, `document_inactive`, and forbidden before the body is checked.
  - `invalid_request` for a missing manifest, a non-object manifest, an unknown schema, a manifest over 64 KB, and a label that's too long.
  - Through the servlet: wrong scope gives `insufficient_scope`, bad JSON gives `invalid_request`, success gives 201, GET by a non-editor gives 403, unknown guide gives 404.
  - History newest first.
  - Latest release and release list come from the highest manifest, with unreadable builds ignored.
  - Behind count and most demanding impact.
  - Version missing from the list: no behind count.
  - Registered guides with no builds show No status reported, and inactive guides are excluded.
  - Uploads without a manifest are behind every release.
  - Sort order, summary counts, and the stale flag.
  - Version comparison.
- **GuideStatusRendererTest**
  - Status view renders every state, the summary line, badges, links, and the warnings list.
  - Each filter and the warning-code filter apply, including "N of M" and "No guides match."
  - Detail view shows history order, warnings, an unreadable row, and the escaped raw manifest.

## Local check (running instance)

I deployed to local Tomcat, signed in with the dev magic link as the seeded admin, and created a 30-day personal token with `topics:write`. The test data went through the real API. I wrote the schema-1 manifests by hand to match A2, with two releases: v0.22.0 `content` and v0.21.0 `rebuild`.

| Guide | How | Result |
|---|---|---|
| `contextual-conditions` (EXTERNAL) | `POST /builds` with v0.22.0, 2 warnings (`CHAPTER_COUNT`, `SUMMARY_LONG` with a `<…>` in its message), `feedbackMarkers: 1` | 201. **Current** (green), Warnings ▸ 2, Feedback markers 1. It became the source of the release list. |
| `immunization-cds` (EXTERNAL) | `POST /builds` with v0.21.0 | 201. **v0.21.0 · 1 behind** (red, `content`). |
| `guide-hosted-test` (HUB) | Hosted upload with the manifest embedded, `<` escaped as `\u003c` | 201. Build row linked to the hosted version. **1 behind**, `content`. |
| `guide-private-test` (switched to HUB through the API for the test) | Hosted upload with a truncated manifest | 201. **Status unreadable** · "The build manifest is not valid JSON." |
| The other five registrations | none | **No status reported**, Last publish "Never". |

The summary line read: *9 registered guides · 1 current · 2 behind (0 rebuild, 0 config, 2 content) · 5 no status reported · 1 with warnings · 1 status unreadable*, followed by *Latest process release: v0.22.0, as reported by contextual-conditions.*

- **Filter check:** "Needs upgrade: content" showed "Guides (2 of 9)", the two v0.21.0 guides.
- **Detail check:** `?view=status&guide=contextual-conditions` showed the warnings with the `<over 550>` text escaped, a one-row history, and the pretty-printed manifest.
- **Error check:** through the API:
  - `{"label":"x"}` → 400 `invalid_request` "A manifest is required."
  - schema 2 → 400 `invalid_request`
  - `{bad` → 400 `invalid_request`
  - unknown guide → 404 `not_found`

  `GET /builds` returned the history.

**Screenshots** (session scratchpad, which may be cleared):
- `C:\Users\NathanBunker\AppData\Local\Temp\claude\f002f47733d4b7e6\013ad5df-d1f6-45e8-a98e-24cb0f50a225\scratchpad\screenshots\01-status-summary-and-filters.jpg`: summary line and filters. This shot was taken before the "Never" fix.
- `C:\Users\NathanBunker\AppData\Local\Temp\claude\f002f47733d4b7e6\013ad5df-d1f6-45e8-a98e-24cb0f50a225\scratchpad\screenshots\02-status-table-all-states.jpg`: the full table with every state. In this shot, `aira-resource-repository-reference` held the unreadable test; see the cleanup note below.
- `C:\Users\NathanBunker\AppData\Local\Temp\claude\f002f47733d4b7e6\013ad5df-d1f6-45e8-a98e-24cb0f50a225\scratchpad\screenshots\03-detail-contextual-conditions.jpg`: detail view.

**Cleanup and side effects:**

- I first sent the malformed upload to the real hosted guide `aira-resource-repository-reference` as label `main`, which replaced what `main` served locally. I then deleted that hosted-version row, its build row, its `es_topic_change` row, and its `hub_stored_file` row, and redid the test on `guide-private-test`. The 184-byte stored file stays in the local artifacts folder; nothing refers to it.
- The test token was revoked.
- The rehearsal restore afterwards wiped all of this test data, including the `guide-private-test` HUB switch. None of it is in `unapplied_updates.sql`.

## How to verify by hand

1. Deploy: `mvn clean package -Plocal-tomcat-deploy "-Dtomcat.webapps.dir=…\Tomcat 10.1\webapps"`. Then sign in as an admin (`interophub-dev-signin` skill).
2. Open `/hub/admin/es/guides` and choose **Status**. With no builds, every active registration shows **No status reported**, and the page says no guide has reported a process release yet.
3. Create a personal token with `topics:write` on `/hub/account`.
4. Report a build:

   ```
   curl -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
     --data '{"label":"v0.6.0","manifest":{"schema":1,"processVersion":"v0.22.0",
       "processReleases":[{"version":"v0.22.0","date":"2026-12-08","upgradeImpact":"content","summary":"…"},
                          {"version":"v0.21.0","date":"2026-11-24","upgradeImpact":"rebuild","summary":"…"}],
       "document":{"version":"v0.6.0","status":"Draft"},"build":{"builtAt":"2026-11-17T15:03:11Z"},
       "structure":{"feedbackMarkers":1},
       "checks":{"warnings":[{"code":"CHAPTER_COUNT","message":"8 chapters","file":"Project.qmd"}]}}}' \
     http://localhost:8080/hub/api/guide/contextual-conditions/builds
   ```

   Expect 201 and `{"buildId":…,"documentKey":"contextual-conditions","processVersion":"v0.22.0","receivedAt":"…Z"}`.
5. Send the same request to another guide with `processVersion` `v0.21.0` and only the v0.21.0 release. It should show **1 behind** in red with impact `content`.
6. Upload a hosted guide (`POST /api/guide/{key}/versions/{label}`, `text/html`):
   - with a `<script type="application/json" id="guide-build-manifest">` element, it gets a linked build row
   - with broken JSON inside the element, it still gets 201 and shows **Status unreadable**
   - with no element, it shows "Before v0.21.0" and counts as behind
7. Try each filter, the warning-code filter, and a guide's detail link.
8. `GET /api/guide/{key}/builds` with the token returns the history, newest first.
9. Query: `SELECT guide_build_id, source, process_version, manifest_error FROM es_guide_build;`
10. Remove the test data with a restore (`interophub-dev-db-restore`), and revoke the token.
