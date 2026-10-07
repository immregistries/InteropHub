# Task: Backload Historical IVC Meetings into InteropHub

## Outcome

Create a basic public InteropHub record for historical International Vaccine
Codes (IVC) meetings that predate the current online series. The production
handoff must consist of:

1. Reproducible data changes in `db/unapplied_updates.sql`.
2. Presentation files staged under
   `C:\dev\immregistries\InteropHub-artifacts` at the exact relative paths
   referenced by `hub_stored_file`.
3. A migration report containing mappings, decisions, validation evidence, and
   public links for Nathan and the IVC website project.

Nathan will copy the files to the production server and execute the SQL. Do not
connect to or change production.

## Read first

Follow the current instructions in:

- `CLAUDE.md`
- `docs/database-release-practice.md`
- `docs/InteropHub_Content_Security_and_Storage_Principles.md`
- `docs/communication-bundles/artifact-storage-deployment-handoff.md`
- More-specific comments in the affected code and SQL blocks

`db/unapplied_updates.sql` is the hand-edited pending-release source.
`db/schema.sql` is generated and must never be hand-edited. Preserve unrelated
working-tree and pending-SQL changes.

## Systems and paths

| Purpose | Location |
| --- | --- |
| InteropHub repository | `C:\dev\immregistries\InteropHub` |
| Artifact staging root | `C:\dev\immregistries\InteropHub-artifacts` |
| Local database | `mysql -uroot -pgoldenroot interophub` |
| On-demand reset | `T:\scripts\python\restore_interophub_db_from_latest_local.py` |
| Source collection | `C:\Users\NathanBunker\AIRA Dropbox\Nathan Bunker\Emerging Standards\IVC` |
| Reviewed inventory | `C:\dev\nuva\ivc-website\docs\content-inventory` |

The reset script rebuilds the local database from the latest cached production
backup, applies local configuration and `db/unapplied_updates.sql`, and
regenerates the schema snapshot. Local UI/database changes not captured in the
SQL will be erased.

## Scope and organization

Import:

- Recurring IVC meetings from June 2023 through 13 May 2026.
- The IVC vaccine-code training in Bordeaux on 8 May 2025.
- The International Summit on Vaccine Coding & Standards in Bordeaux on
  9 May 2025.

Create one meeting per historical occurrence/event. Put all records in the
existing IVC meeting topic/series under **Emerging Standards**. Do not create
separate series, groups, or categories; clear titles and chronological order
provide the distinction. Preserve historical names where useful.

Resolve the target from current production-derived data using stable fields
such as name/slug. Verify it rather than assuming a numeric ID. Detect and stop
on possible duplicates.

Use these inventories for dates, versions, privacy, and delivery evidence:

- `C:\dev\nuva\ivc-website\docs\content-inventory\events\meeting-archive-boundary.md`
- `C:\dev\nuva\ivc-website\docs\content-inventory\events\bordeaux-2025-event.md`
- `C:\dev\nuva\ivc-website\docs\content-inventory\events\bordeaux-2025-artifacts.md`
- `C:\dev\nuva\ivc-website\docs\content-inventory\local-source-review.md`

Legacy indexes may help reconcile public dates and titles:

- `https://ivci.org/doku/doku.php?id=ivci:meetings`
- `https://ivci.org/doku/doku.php?id=ivci:presentations`

Reconcile multiple sources. A planned agenda, filename, modified timestamp, or
DokuWiki entry is not conclusive by itself. Do not invent missing information.

## Minimal historical record

For every meeting:

1. Create the meeting with the best-supported date and a clear title.
2. Retain the `Welcome` agenda item the UI normally creates.
3. Add agenda items, with at least meaningful titles, for subjects reliably
   evidenced as having occurred. Times, descriptions, and speakers are optional
   and should be included only when reliable and useful.
4. Attach a selected presentation to its matching item when the match is clear.
5. Attach it to `Welcome` when it applies generally, was the one deck for the
   meeting, or cannot reliably be matched. Do not guess to avoid `Welcome`.
6. Attach presentations only.

Do not import notes, outcomes, attendance, RSVPs, contacts, invitations, polls,
chat, recordings, transcripts, photographs, agendas as files, reports,
logistics, speaker guidance, or internal planning material. Do not create
placeholder notes.

### Bordeaux rules

- Treat the 8 May training and 9 May summit as separate meetings.
- Create summit items only for sessions evidenced as delivered in the review.
- Do not represent planned WHO or PAHO/Latin America slots as delivered without
  new evidence.
- The EU strategy session was delivered virtually without slides. It may have
  a titled item, but it has no presentation attachment.
- The STCHealth footer does not exclude its deck; the organizer confirmed it is
  not sensitive for this use.
- IVC already has permission to continue hosting the presentations.

## Presentation selection and staging

- Prefer the supported original/final `.pptx`; otherwise use its PDF.
- Never import both PPTX and PDF copies of the same presentation.
- Do not import routine drafts or duplicates. Document selected and excluded
  versions.
- Preserve the original filename in metadata/download behavior. Follow current
  storage-service conventions for physical keys.
- Do not overwrite an object merely because filenames match.
- Record SHA-256 and byte size for source and staged copies.
- Validate with the upload workflow's current type, package, and size checks.
  Do not silently bypass failures.

Copy selected bytes to `C:\dev\immregistries\InteropHub-artifacts`. Relative
paths must exactly match the `LOCAL` locators inserted in `hub_stored_file`.
Do not alter or delete source files. If a deck exceeds limits or fails
validation, report it and stop for that file; do not convert, repair, compress,
or omit it without documenting the issue and seeking direction when needed.

## Optional Building Bridges associations

Meetings remain under IVC in **Emerging Standards**, but agenda items may
reference existing topics from the peer public **Building Bridges** Topic
Space. The model permits a public meeting to include topics from another public
Topic Space.

For each substantive agenda item:

1. Inspect current Building Bridges topics and, when useful, their history.
2. Associate only when the historical subject clearly matches an existing
   topic's meaning and scope.
3. Do not create topics in this migration.
4. Do not force broad, speculative, keyword-only, or speaker-country matches.
5. Leave ambiguous items unassociated.
6. Report each association with a short rationale and list intentionally
   unassociated items.

The goal is accurate topic history, not maximum associations.

## SQL requirements

Add a clearly labeled IVC historical-meeting block to
`db/unapplied_updates.sql`, following current project conventions.

- Preserve unrelated pending SQL and worktree changes.
- Use verified stable lookups where numeric IDs are not guaranteed.
- Make the migration safe for the refresh workflow and preferably rerunnable
  without duplicates. If constraints prevent full idempotency, document that
  and fail clearly on violated preconditions.
- Use a transaction when current conventions permit. Fail on missing or
  ambiguous parents, duplicate natural identities, or unexpected counts.
- Populate required lifecycle, order, visibility, audit, storage, MIME, size,
  checksum, and association fields from current code/schema and comparable
  rows—not assumptions in this task.
- Do not edit released `db/vX.Y_*.sql`, hand-edit `db/schema.sql`, or execute
  production SQL.

The UI or direct SQL may be used to prototype locally, but encode/export that
state into `db/unapplied_updates.sql` before refreshing.

## Required execution and proof

1. Reconcile all meetings, agenda titles, and presentation candidates.
2. Ensure existing local-only work will not be lost by a refresh.
3. Implement SQL and artifact staging.
4. Apply and inspect the result in local MySQL.
5. Build/deploy if needed and check the running local application, not only SQL.
6. Correct mistakes, rebuilding the disposable database as needed.
7. Run `T:\scripts\python\restore_interophub_db_from_latest_local.py` to test
   the real fresh-production-baseline pipeline.
8. Repeat database and application checks after confirmed script success.
9. Run the restore a second time, or an equally strong repeatability check, to
   prove consistent results without duplicates.
10. Review `git diff`; do not absorb or discard unrelated work.

Verify exact meeting dates/titles/counts; one expected `Welcome` per meeting;
item titles/order; attachment mappings; absence of duplicate variants; stored
file backend, locator, filename, MIME, size, checksum, and download behavior;
one staged file per new locator; source/staged/downloaded SHA-256 equality;
public agenda/download access; stable navigation and direct URLs; correct
Building Bridges history; and reproduction after a fresh restore.

## Migration report

Create `docs/tasks/ivc-historical-meetings-migration-report.md` containing:

1. Counts of meetings, items, decks, associations, skipped files, and gaps.
2. Meeting map: source date/title, final title, meeting ID, public/local URL,
   and stable IVC series/topic relationship.
3. Agenda map: order, title, ID, associated topic ID/name/space/public URL or
   `none`, with rationale for Building Bridges matches.
4. Presentation manifest: absolute source, excluded versions, absolute and
   relative destination, stored-file/attachment/item IDs, filename, MIME, size,
   and SHA-256.
5. Exclusions and gaps, without reproducing private content.
6. SQL section, execution method, transaction/repeatability behavior, and
   production preconditions.
7. Restore result, validation queries/counts, app checks, hash comparisons, and
   second-run result.
8. Nathan's production checklist: backup, artifact copy, SQL order,
   post-deployment checks, and recovery note. Do not perform these steps.
9. Website handoff: stable public IVC meeting landing URL plus direct Bordeaux
   training/summit URLs and caveats. The website should normally link to the
   stable landing page, may feature direct Bordeaux/recent links, and should not
   duplicate the archive.
10. Changed files/commits and unrelated dirty work left untouched.

## Stop and ask

Request direction if the target is ambiguous, a meeting may already exist,
evidence supports different dates/boundaries, a substantive version choice is
unresolved, a file fails validation/limits, cross-space association is not
supported, unrelated SQL would need restructuring, artifact path conventions
are unclear, or refresh would erase uncaptured work.

Missing detail is not permission to invent it. Import supported basics and
report the gap.

## Acceptance criteria

Every supported meeting has a basic record and evidenced agenda titles; every
recoverable approved presentation appears once using PPTX when supported and
otherwise PDF; only presentations are attached; clear Building Bridges matches
are linked without forcing ambiguous ones; file locators and staged bytes match;
a fresh restore recreates the state from `db/unapplied_updates.sql`; the local
application displays/downloads it correctly without rerun duplicates; and the
report provides the complete production and website handoff.
