# Task: Backload Historical IVC Meetings into InteropHub

## Outcome

Create a basic public InteropHub record for historical IVC meetings that
predate the current online series. The production handoff must consist of:

1. Reproducible data changes in `db/unapplied_updates.sql`.
2. Presentation files staged under
   `C:\dev\immregistries\InteropHub-artifacts` at the exact storage keys
   referenced by `hub_stored_file`.
3. A migration report containing mappings, decisions, validation evidence, and
   public links for Nathan and the IVC website project.

Do not connect to or change production.

## Definition of done and end point

This task is **complete when Nathan has inspected and approved the result on
his local machine**. The agent's job ends at the handoff: a locally verified
database state, the staged files, and the report.

The end point this work must support, which is outside this task's scope, is
a production release that Nathan performs with only three steps:

1. Copy the backload's files from `C:\dev\immregistries\InteropHub-artifacts`
   to production's artifact directory.
2. Run `db/unapplied_updates.sql` against production.
3. Deploy the new InteropHub WAR.

So the local result must be **fully reproducible from those three inputs**:

- All database state comes from `db/unapplied_updates.sql` run against a
  fresh production copy. Do not leave any manual database edits, UI-only
  changes, or one-off scripts that production would need.
- All file bytes come from the staging folder, under the exact keys the SQL
  references. Nothing is read from Dropbox or the IVC repo at runtime.
- This task needs no code or configuration changes. If one turns out to be
  necessary, stop and ask, because it would have to ship in the WAR and change
  the release.
- The local reset (`interophub-dev-db-restore`) is the rehearsal for
  production step 2. If the result passes Nathan's local review after a clean
  reset, production should match.

The staging folder already holds unrelated test uploads from the
Communication Bundles 1a/1b work, and their database rows disappear on reset.
The report must list the exact backload file keys so Nathan can copy only
those files, or knowingly copy the whole folder.

## Relationship to the IVC website task

This task is the InteropHub-side execution of
`C:\dev\nuva\IVC-Website\docs\tasks\interop-hub-historical-meeting-backload.md`.
That document states the IVC project's goals and source-material rules. This
document adds what InteropHub actually supports and the decisions Nathan made
after comparing the two (2026-10-08). **Where they differ, this document
wins.** The known differences are:

| Topic | IVC task says | Decision here |
| --- | --- | --- |
| Ordinary meeting title | `IVC Monthly Meeting` | Match the existing series: `Immunization Vocabularies Collaboration (IVC) Monthly Meeting` |
| Public summaries | Add to meeting notes when supported | Skip. List supportable summaries in the report for a possible later pass |
| File-store "naming and directory conventions" | Unspecified | Flat, opaque UUID keys. See [Presentation staging](#presentation-selection-and-staging) |

## Read first

Follow the current instructions in:

- `CLAUDE.md`
- `docs/database-release-practice.md`
- `docs/InteropHub_Content_Security_and_Storage_Principles.md`
- `docs/communication-bundles/artifact-storage-deployment-handoff.md`
  (especially "Task 1b: implemented meeting agenda attachments")
- `docs/interophub-meeting-notes-data-model.md` (meeting/agenda lifecycle)
- More-specific comments in the affected code and SQL blocks

`db/unapplied_updates.sql` is the hand-edited pending-release source.
`db/schema.sql` is generated and must never be hand-edited. Preserve unrelated
working-tree and pending-SQL changes.

## Skills to use

| Need | Skill |
| --- | --- |
| Ad hoc local MySQL queries | `interophub-dev-database` |
| Rebuild local DB from latest cached prod backup + `unapplied_updates.sql` | `interophub-dev-db-restore` |
| Sign in to local Tomcat (`localhost:8080/hub`) for UI/download checks | `interophub-dev-signin` |

## Systems and paths

| Purpose | Location |
| --- | --- |
| InteropHub repository | `C:\dev\immregistries\interophub` |
| Artifact staging root (= local `INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY`) | `C:\dev\immregistries\InteropHub-artifacts` |
| Local database | see `interophub-dev-database` skill |
| On-demand reset | `T:\scripts\python\restore_interophub_db_from_latest_local.py` (via `interophub-dev-db-restore`) |
| Source collection | `C:\Users\NathanBunker\AIRA Dropbox\Nathan Bunker\Emerging Standards\IVC` |
| Reviewed inventory | `C:\dev\nuva\IVC-Website\docs\content-inventory` |

The reset script rebuilds the local database from the latest cached production
backup, applies local configuration and `db/unapplied_updates.sql`, and
regenerates the schema snapshot. Local UI/database changes not captured in the
SQL will be erased. **The reset does not touch the artifact staging root**, so
staged files survive a reset, and files from abandoned attempts stay there as
orphans until someone removes them.

Confirm Tomcat's `INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY` points at the staging
root before checking downloads (see the storage handoff doc). Changing it
requires a Tomcat service restart.

## Verified InteropHub model (snapshot 2026-10-08, re-verify before use)

These facts come from the local schema, code, and production-derived data on
2026-10-08. Use them as a starting point, but re-query them, and **resolve
rows by stable fields in SQL rather than hard-coding the IDs shown here.**

**Target series**

- Topic Space `Emerging Standards`: `es_topic_space.space_code = 'emerging-standards'` (id 1, PUBLIC).
- Topic `Immunization Vocabularies Collaboration (IVC)`: `es_topic` id 31, in space 1.
- Meeting series: `es_topic_meeting` id 1, `meeting_name = 'Immunization Vocabularies Collaboration (IVC) Monthly Meeting'`, `es_topic_id` = 31, ACTIVE.
- Existing occurrences: `es_meeting` rows 6, 7, and 8 (2026-06-10, 07-08, 09-09, CLOSED), then 22 onward (2026-10-14, PROPOSED/DRAFT). The earliest existing date is 2026-06-10, which fits the 13 May 2026 scope end. Stop if any IVC meeting exists on or before 2026-05-13.

**Meeting row pattern** (`es_meeting`, mirroring 6–8)

- `es_topic_meeting_id` = the series, `es_topic_space_id` = Emerging Standards.
- `meeting_name`: the series name for ordinary meetings, or a distinct title for Spanish/Bordeaux meetings.
- `scheduled_start` / `timezone_id`: start times do not matter for past meetings. Use the meeting date at `10:00` with `timezone_id = 'America/New_York'` for **every** historical meeting, including Bordeaux, exactly as rows 6–8 store it. Leave `scheduled_end` NULL like those rows. Do not try to reconstruct actual times.
- Closed lifecycle: `status = 'CLOSED'`, `completed_at`/`closed_at` set, `close_method = 'MANUAL'`, and `created_by_user_id` / `closed_by_user_id` set to Nathan's user, resolved by email (`nbunker@immregistries.org`, currently user 2). Rows 6 and 7 also have one `es_meeting_status_history` row (`FINALIZED → CLOSED`). Decide from the code which lifecycle fields and history rows a historical closed meeting needs, and document the choice. Do not fabricate start, attendance, RSVP, role, or participant-count rows.
- `meeting_description`: copy whatever comparable rows carry (the series default). Do not put summaries there.

**Agenda item pattern** (`es_meeting_agenda_item`)

- New meetings created in the UI get two default items (`EsAgendaServlet.createDefaultAgendaItems`): `Welcome and Introductions` (order 10, markdown `Welcome\nIntroductions`, 5 min) and `Wrap Up` (order 20, markdown `Next steps\nNext topics`, 5 min). Closed meetings keep them; meeting 8 renamed Welcome to `Welcome and Updates`.
- Substantive items sit between them (existing orders: 15, 17, 18, 19). Closed-meeting items are `status = 'ACCEPTED'` with `accepted_at` set.
- Every historical meeting gets the Welcome item (the attachment fallback) and Wrap Up, to match the native UI structure. For Bordeaux, an evidenced equivalent title such as an opening session may replace them.
- `agenda_markdown` and `time_minutes` are optional. Include them only when the evidence is reliable.
- `es_topic_id` is optional. Cross-space links are permitted: `isTopicAllowedForMeetingHost` allows a public host space to reference topics in any public space. No current agenda item does this yet, so verify the agenda and topic pages render cross-space links.

**File and attachment pattern**

- `hub_stored_file` (created in `unapplied_updates.sql`, task 1a/1b blocks): `public_id` and `storage_key` must both be lowercase UUIDs (`ArtifactStorageConfig.validateKey`). `storage_backend = 'LOCAL'`, Blob columns NULL. Set `original_filename`, `content_type`, `size_bytes`, `uploaded_by_user_id`, `uploaded_at`, and `created_at`. Use `download_only = b'1'`, which is what meeting uploads set.
- Local bytes live at `<root>\<storage_key>`: flat, with no subfolders and no extension (`LocalFileStorageService.path`).
- `es_meeting_agenda_attachment`: one row per file, linking `es_meeting_agenda_item_id` and `stored_file_id` (unique, so a file attaches to exactly one item). Set `attached_by_user_id` and `attached_at`, and leave `removed_*` NULL.
- Meeting attachments accept only raster images, PDF, and PPT/PPTX (`MeetingAttachmentService.requireAllowedFilename`), at most 26,214,400 bytes (`StoredFileValidation.MAX_BYTES`). PPTX also gets an Office package and macro check. Use the content type mapping in `StoredFileValidation`.
- Public download URL: `/hub/files/<public_id>` (`StoredFileService.readUrl`). Anyone with the URL can read the file.

**Topic spaces for associations**

- `Building Bridges` Topic Space: `space_code = 'building-bridges'` (id 2, PUBLIC, 42 topics on 2026-10-08), with country and international-organization topics.
- Do not confuse it with the **Emerging Standards topic** named `Building Bridges` (id 120). Current IVC agendas link that topic for the country-interview project itself.
- Current IVC agendas also link existing Emerging Standards topics such as `Unified Nomenclature of Vaccines (NUVA)` (117), `NDC Transition to 12-Digit Format` (103), and `CDS Contextual Conditions` (7). Historical items may link to existing Emerging Standards topics under the same strict evidence rule as Building Bridges topics.

## Scope and organization

Import:

- Recurring IVC meetings from June 2023 through 13 May 2026.
- The Spanish-language IVC meetings on 12 March, 9 April, and 23 July 2025.
- The IVC vaccine-code training in Bordeaux on 8 May 2025.
- The International Summit on Vaccine Coding & Standards in Bordeaux on
  9 May 2025.

Create one meeting per historical occurrence/event. Put all records in the
existing IVC meeting series under **Emerging Standards**. Do not create
separate series, groups, or categories. Clear titles and chronological order
provide the distinction.

Ordinary recurring meetings use the series name
`Immunization Vocabularies Collaboration (IVC) Monthly Meeting` exactly, with
no date in the title. Spanish-language and Bordeaux meetings use the distinct
titles below or ones supported by their inventories. Do not rename the IVC topic
or series in this task.

Resolve the target from current production-derived data using stable fields.
Verify it rather than assuming a numeric ID. Detect and stop on possible
duplicates.

Use these inventories for dates, versions, privacy, and delivery evidence:

- `C:\dev\nuva\IVC-Website\docs\content-inventory\events\meeting-archive-boundary.md`
- `C:\dev\nuva\IVC-Website\docs\content-inventory\events\bordeaux-2025-event.md`
- `C:\dev\nuva\IVC-Website\docs\content-inventory\events\bordeaux-2025-artifacts.md`
- `C:\dev\nuva\IVC-Website\docs\content-inventory\events\ivc-en-espanol-2025.md`
- `C:\dev\nuva\IVC-Website\docs\content-inventory\local-source-review.md`
- `C:\dev\nuva\IVC-Website\docs\content-inventory\inventory.md`
- `C:\dev\nuva\IVC-Website\docs\content-inventory\collection-backlog.md`

Legacy indexes may help reconcile public dates and titles:

- `https://ivci.org/doku/doku.php?id=ivci:meetings`
- `https://ivci.org/doku/doku.php?id=ivci:presentations`

Reconcile multiple sources. A planned agenda, filename, modified timestamp, or
DokuWiki entry is not conclusive by itself. Do not invent missing information.

## Minimal historical record

For every meeting:

1. Create the meeting with the best-supported date and the title rule above.
2. Include the default `Welcome and Introductions` and `Wrap Up` items.
3. Add agenda items, with at least meaningful titles, for subjects reliably
   evidenced as having occurred. Read source agenda documents as evidence and
   recreate them in the native agenda model. Times, descriptions, and speakers
   are optional. Include them only when they are reliable and useful.
4. Attach a selected presentation to its matching item when the match is clear.
5. Attach it to `Welcome` when it applies generally, was the one deck for the
   meeting, or cannot reliably be matched. Do not guess to avoid `Welcome`.
6. Attach presentations only.

Do not import notes, summaries, outcomes, attendance, RSVPs, contacts,
invitations, polls, chat, recordings, transcripts, photographs, agendas as
files, reports, logistics, speaker guidance, or internal planning material. Do
not create topic notes or placeholder notes. Where a public summary could be
supported, record that in the report only.

### Bordeaux rules

- Treat the 8 May training and 9 May summit as separate meetings. If the
  evidence or model suggests a materially better structure, propose it to
  Nathan before implementing.
- Create summit items only for sessions evidenced as delivered in the review.
- Do not represent planned WHO or PAHO/Latin America slots as delivered without
  new evidence.
- The EU strategy session was delivered virtually without slides. It may have
  a titled item, but it has no presentation attachment.
- The STCHealth footer does not exclude its deck. The organizer confirmed it is
  not sensitive for this use.
- IVC already has permission to continue hosting the presentations.

### IVC en español rules

Create three occurrences in the existing IVC series under Emerging Standards.
Do not create a separate Spanish series or group. Recommended titles are:

- 12 March 2025: `IVC en español — Introducción a los códigos de vacunas`
- 9 April 2025: `IVC en español — IVC y NUVA`
- 23 July 2025: `IVC en español — Resumen de la Cumbre de Burdeos`

Use the detailed review for supported agenda items. Retain Spanish titles when
the sources support them. Attach meeting-wide decks to `Welcome`, and attach the
March WHODrug PDF to its matching agenda item.

Use the copy under
`Emerging Standards\IVC\IVC en espanol` as the canonical source. The separate
`C:\Users\NathanBunker\AIRA Dropbox\Nathan Bunker\IVC en espanol` folder is a
duplicate and must not produce additional records or attachments.

Select only:

- March: `IVC en español 2025-03_meeting review version.pptx`
- March: `2025_03_12_USA_AIRA_SPA_IVC en español_WHODrug Global.pdf`
- April: `IVC en español 2025-04.pptx`
- July: `International Vaccine Codes 2025.07.23 Esp.pptx`

Do not select March conflicted copies or its duplicate PDF export. Do not
select `International Vaccine Codes 2025.07.23 Esp extra.pptx`. It is a large
source/compilation deck rather than the concise presentation to preserve.

The July deck's title slide says 11 June 2025, while the filename and collection
context support 23 July. Preserve the file unchanged and document the
discrepancy. Stop for direction if database records or other authoritative
evidence conflict with 23 July or indicate a separate June occurrence.

These decks contain dated historical descriptions, roles, plans, and claims.
Including them as meeting artifacts does not make them current IVC guidance.
Apply the standard private-material exclusions, including recordings, chat,
attendance, registrations, contacts, invitations, images, and internal reports.

## Presentation selection and staging

- Prefer the supported original/final `.pptx`, and otherwise use its PDF.
- Never import both PPTX and PDF copies of the same presentation.
- Do not import routine drafts or duplicates. Document selected and excluded
  versions.
- Preserve the original filename in `hub_stored_file.original_filename`.
- **Assign each file a fixed `public_id` UUID and a separate fixed
  `storage_key` UUID, and write both as literals in the SQL.** Do not use
  `UUID()` in SQL. Every restore must point at the same staged bytes, or reruns
  will orphan files and break URLs. Generate the UUIDs once, record them in the
  manifest, and never reuse an existing key.
- Copy the selected bytes to `C:\dev\immregistries\InteropHub-artifacts\<storage_key>`,
  using the bare UUID with no extension or subfolder. Do not alter or delete
  source files.
- Record SHA-256 and byte size for the source and staged copies. `size_bytes`
  must equal the staged size.
- Validate every selected file with the same checks the upload path applies:
  run `StoredFileValidation.validate` (for example, from a small test harness)
  and the meeting attachment type allowlist. If a deck exceeds 25 MiB or fails
  validation, report it and stop for that file. Do not convert, repair,
  compress, or drop it without direction.

## Optional topic associations

Meetings remain under IVC in **Emerging Standards**. Agenda items may reference
existing topics from the public **Building Bridges** Topic Space, or existing
Emerging Standards topics already in this domain.

For each substantive agenda item:

1. Inspect current candidate topics and, when useful, their history.
2. Associate only when the historical subject clearly matches an existing
   topic's meaning and scope.
3. Do not create topics in this migration.
4. Do not force broad, speculative, keyword-only, or speaker-country matches.
5. Leave ambiguous items unassociated.
6. Report each association with a short rationale, and list intentionally
   unassociated items.

The goal is accurate topic history, not maximum associations. Associated
historical items will appear in those topics' meeting history, so check how
they render there.

## SQL requirements

Append a clearly labeled IVC historical-meeting block to the end of
`db/unapplied_updates.sql`, following current project conventions.

- Preserve unrelated pending SQL and worktree changes. The file already
  contains apply-once DDL (for example, `CREATE TABLE hub_stored_file`), so the
  whole file only ever runs once per restore. Write the backload block so it is
  still correct if run against a database that already has it: guard on
  natural identity, such as series + date + title for meetings and
  `public_id` for files.
- Resolve the space, topic, series, user, and association topics by stable
  fields (`space_code`, `topic_name` within space, series name, user email)
  into variables. Fail clearly if any lookup is missing or ambiguous.
- Use a transaction when current conventions permit. Fail on duplicate natural
  identities or unexpected counts.
- Populate required lifecycle, order, visibility, audit, storage, MIME, size,
  and association fields from current code and comparable rows, not from
  assumptions in this task.
- Do not edit released `db/vX.Y_*.sql`, hand-edit `db/schema.sql`, or execute
  production SQL.

The UI or direct SQL may be used to prototype locally, but encode/export that
state into `db/unapplied_updates.sql` before resetting.

## Required execution and proof

1. Reconcile all meetings, agenda titles, and presentation candidates. Produce
   a migration manifest that lists, for each meeting, the source evidence,
   proposed title/date, agenda order, topic associations, selected
   decks with canonical paths, exclusions, and open questions.
2. **Review ambiguous dates, identity collisions, proposed topic links, and the
   Bordeaux structure with Nathan before encoding them in SQL.**
3. Ensure existing local-only work will not be lost by a reset.
4. Implement SQL and artifact staging.
5. Apply and inspect the result in local MySQL.
6. Build/deploy if needed and check the running local application, not only
   SQL. Sign in with `interophub-dev-signin` for any signed-in views, and check
   anonymous access too.
7. Correct mistakes, rebuilding the disposable database as needed.
8. Run the reset (`interophub-dev-db-restore`) to test the real
   fresh-production-baseline pipeline.
9. Repeat database and application checks after confirmed script success.
10. Run the reset a second time, or an equally strong repeatability check, to
    prove consistent results without duplicates.
11. Review `git diff`. Do not absorb or discard unrelated work.
12. Hand off to Nathan for local inspection with the report and a short list
    of local URLs to review. Fix anything he flags in the SQL or staged files,
    then reset and re-verify. The task is done when he approves the local
    result.

Verify:

- Exact meeting dates, titles, and counts, with no duplicates, and each
  meeting showing on its own date in the UI (10:00 ET).
- One Welcome and one Wrap Up per meeting, plus item titles and order.
- Attachment mappings, with no duplicate format variants.
- Stored-file backend, key, filename, MIME, size, and `download_only`.
- One staged file per key, and equal SHA-256 for the source, staged, and
  downloaded copies.
- Anonymous agenda display and downloads, including attachment disposition and
  filename.
- Stable meeting navigation and direct URLs, including chronological placement
  before meeting 6.
- Topic-page history for associated topics, including cross-space rendering.
- The same results after a fresh reset.

## Migration report

Create `docs/tasks/ivc-historical-meetings-migration-report.md` containing:

1. Counts of meetings, items, decks, associations, skipped files, and gaps.
2. Meeting map: source date/title, final title, meeting ID, public/local URL,
   and stable IVC series/topic relationship.
3. Agenda map: order, title, ID, and associated topic ID/name/space/public URL
   or `none`, with a rationale for each match.
4. Presentation manifest: absolute source, excluded versions, `public_id`,
   `storage_key`, staged path, stored-file/attachment/item IDs, filename,
   MIME, size, SHA-256, and public `/hub/files/...` URL.
5. Exclusions and gaps, without reproducing private content. Include a short
   list of meetings where a public summary appears supportable, for a possible
   later pass.
6. SQL section, execution method, transaction/repeatability behavior, and
   production preconditions (the task 1a/1b storage blocks must be applied
   first).
7. Reset result, validation queries/counts, app checks, hash comparisons, and
   second-run result.
8. Nathan's production checklist for the three-step release (copy files, run
   `unapplied_updates.sql`, deploy WAR):
   - back up the database and artifact root together;
   - give the exact list of backload file keys to copy into production's
     `INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY`, copied before the SQL runs;
   - confirm that the backload needs nothing beyond those three steps;
   - list post-deployment checks (a few meeting pages and downloads with
     expected SHA-256 values); and
   - add a recovery note.

   Do not perform these steps.
9. Website handoff: the stable public IVC meeting landing URL plus direct
   Bordeaux training/summit URLs and caveats. The website should normally link
   to the stable landing page, may feature direct Bordeaux/recent links, and
   should not duplicate the archive.
10. Changed files/commits and unrelated dirty work left untouched.

## Stop and ask

Request direction if:

- the target is ambiguous;
- a meeting may already exist;
- evidence supports different dates or boundaries;
- a substantive version choice is unresolved;
- a file fails validation or size limits;
- cross-space association fails to render or is rejected;
- unrelated SQL would need restructuring; or
- a reset would erase uncaptured work.

Missing detail is not permission to invent it. Import supported basics and
report the gap.

## Acceptance criteria

- Every supported meeting has a basic record under the existing IVC series.
  Ordinary meetings use the series title, and the Spanish and Bordeaux meetings
  use their distinct titles.
- Every meeting has evidenced agenda titles in the native agenda UI, with
  Welcome and Wrap Up.
- Every recoverable approved presentation appears once, using PPTX when
  supported and otherwise PDF. Only presentations are attached, and no agenda
  documents or summaries are added.
- Clear topic matches are linked without forcing ambiguous ones.
- Fixed UUID keys and staged bytes match.
- A fresh reset recreates the state from `db/unapplied_updates.sql`, and the
  local application displays and downloads it correctly without rerun
  duplicates.
- The report provides the complete production and website handoff.
