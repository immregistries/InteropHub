# Artifact Storage Deployment Handoff

Everything needed to deploy and verify **Communication Bundles tasks 1a and 1b** -
shared local/Blob document and image storage and meeting agenda attachments described in
`InteropHub_Communication_Bundles_Implementation_Plan.md` and
`azure-blob-storage-handoff-reaction.md`.

Task 1a adds no bundle or meeting feature. Its admin demonstration uploads
a Welcome image and a separate document, proving server-side writes and anonymous
stable file URLs before those features depend on storage. Task 1b adds selected-item
meeting attachments without introducing bundles or Topic Resources.

Audience: Nathan (local setup), Chris (production Tomcat + Azure checks), and
whoever holds Azure access for the storage account.

## October 2026 status: task 1a implemented

**Task 1a now supplies shared local/Blob file metadata, local uploads, and anonymous stable InteropHub file URLs.** Production Blob verification remains blocked by provisioning permissions. The original Azure instructions below are historical step 1 instructions, not the current local deployment procedure.

The demo is a consumer of shared file storage. `hub_stored_file` holds backend-aware records; `es_artifact_demo` now associates slots with those records. The migration explicitly maps old objects to `BLOB` and preserves the old image as `WELCOME_BLOB_DEMO`. New slots are `WELCOME_DEMO` (local Welcome image) and `DOCUMENT_DEMO` (local document proof). Welcome prefers the local image and otherwise uses the preserved Blob image. Step 2 removes temporary demo structures only after retaining shared records and references.

Local setup uses `INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY=C:\dev\immregistries\InteropHub-artifacts`. The directory must already exist. Restart the Tomcat service after setting/changing the environment variable; replacing the WAR alone cannot refresh the service's inherited environment.

### Local-first deployment checklist

- [ ] Provision one durable folder outside the WAR, exploded deployment, web root, and temporary directories; set the absolute path in `INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY` in Tomcat's environment.
- [ ] Give the Tomcat service account read/write rights, verify the root and temporary-upload area cannot escape through symlinks, and do not expose directory listing or a separate static web-root mapping.
- [ ] Include the folder and database in coordinated backup/restore procedures, monitor free disk space, and verify files survive restart and WAR redeployment. Before adding nodes, arrange a shared durable root or transition to Blob.
- [ ] Apply task 1a's schema update from `db/unapplied_updates.sql` according to the [database release practice](../database-release-practice.md). It is apply-once and requires the original step 1 table. Before production application, check the migration's historical Blob endpoint/container literals against the location actually used by the old demo. Do not reapply to an already-migrated database.
- [ ] Deploy the task 1a WAR with new uploads explicitly local. No Azure credentials or account changes are required for local upload/read verification. Adding a SAS later must not silently change new-upload routing.
- [ ] Verify the admin demo and signed-in/signed-out Welcome page use stable InteropHub file URLs. Exercise PDF and downloadable TXT/DOC/DOCX/PPT/PPTX through the separate document form. Existing Blob replacements remain production-only and require a matching write credential.
- [ ] Verify the 25 MiB per-file cap, with bounded multipart overhead at Tomcat and any reverse proxy. A file of 26,214,401 bytes is rejected; audio/video and disallowed active formats are rejected. Local files stream with correct metadata, `nosniff`, and cache revalidation.
- [ ] Replace a file and confirm the stable URL presents fresh content without exposing a partial upload. Simulate disk/permission failure and confirm an explicit failure and retention of the previous good content.
- [ ] Check blank local configuration disables only local support; an invalid configured root reports an actionable error. Missing Blob SAS must not disable working local uploads. A recorded backend outage must not trigger silent fallback to a different object.
- [ ] For development, configure a separate isolated local root and database/file copy; never mount the production folder writable. Verify Blob writes are rejected even with a SAS accidentally configured. Missing copied files are reported, not silently fetched from production.

Anyone with an opaque file URL can read it on either backend. This does not make private/draft content confidential, revoke previously disclosed links, or remove the requirement to retain authoritative documents elsewhere.

### Configuration semantics

| Setting/state | Task 1a behavior |
| --- | --- |
| `INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY` absent/blank | Local reads/writes disabled; Blob capability is independent |
| Local directory explicitly invalid/unwritable | Explicit local configuration error; no alternate-path/provider fallback |
| Valid local directory, no SAS | Local uploads enabled for authorized users; existing configured Blob reads still work |
| Production with Blob SAS | Blob writes available for recorded Blob files; task 1a new uploads remain local |
| Development with isolated local root | Local uploads allowed; all Blob writes rejected independently of SAS |
| Neither backend writable | Uploads disabled with a clear explanation; available reads remain independent |

Do not treat a valid directory as proof of operational readiness: backup/restore and persistence checks are deployment prerequisites. Selective or full migration, provider-selection UI, and future upload policy are deferred. A migration must verify bytes before changing the recorded locator and retain the stable public ID; direct Blob links already distributed outside InteropHub require separate handling.

### Validation, replacement, and recovery

- Uploads validate filenames, extension, declared MIME, content/container structure, and streamed size. Decoded PNG/JPEG/GIF images are capped at 40 million pixels. WebP uses RIFF/frame-header validation because the JDK has no WebP decoder; this is not full image decoding. Office ZIP expansion is bounded to 100 MiB, checks CRC and the expected package manifest, and rejects macro payloads; legacy Office macro markers are also rejected.
- Validation is not antivirus scanning. PDF validation checks its header; accepted Office/PDF content can still be unsafe for recipients. Do not upload confidential content or execute/convert embedded content on the server.
- Each successful replacement keeps the public URL but creates a new opaque physical key. Publication is atomic; optimistic metadata versions and the demo transaction prevent conflicting replacements from overwriting each other. GET/HEAD uses the new physical key as its ETag.
- After successful local replacement, obsolete local bytes are removed. Cleanup failure is logged for operator action. Blob replacement leaves the old object for operator cleanup because the existing SAS does not authorize deletion.
- A database registration failure can have an ambiguous commit outcome. Candidate bytes are deliberately retained and their key logged for reconciliation; never blindly delete files absent from a stale database snapshot. Inspect committed `hub_stored_file` references before cleanup. Stale upload staging files also require operator reconciliation after a process crash.
- Back up the database and root together. A routine production-database refresh alone does not preserve local test records or copy their files; it can leave unreferenced test bytes. Production recovery and coordinated backup/restore drills remain required before production readiness is claimed.

### Verification record

Task 1a's full Maven test/package run passed 154 tests (including 20 storage tests). New POI and Log4j dependencies were scanned without known CVEs after pinning patched Log4j API 2.25.5. The local schema migration was applied after backing up the original empty demo table.

Local Tomcat10 verification on October 7, 2026:

- Nathan uploaded a PNG through the admin form and confirmed it works. Its recorded backend is `LOCAL`; it renders on signed-in Welcome, and anonymous Welcome includes the same stable image URL.
- An actual legacy PowerPoint was uploaded through the document form. Anonymous download matched the original SHA-256, used the correct attachment filename/type/length, and conditional GET returned 304.
- Anonymous image HEAD returned 200 with inline disposition, size/type, `nosniff`, and cache revalidation headers. Unregistered file IDs returned 404; unauthenticated admin upload redirected to sign-in without writing.
- Both registered files remained readable after a hub-only WAR redeployment; the PowerPoint bytes remained unchanged. No Tomcat restart was required after the initial environment-setting restart.
- Blank/invalid configuration, size boundaries, rejected formats, failed registration, replacement identity, and development Blob-write rejection have automated coverage.

Production Blob access, production coordinated backup/restore, proxy request limits, disk monitoring, permission-failure drills, and a post-upload service-restart persistence check remain deployment checks, not claimed results. Automated tests and a local WAR redeployment do not establish production operational readiness.

### Task 1b: implemented meeting agenda attachments

After task 1a's Welcome-image proof, [task 1b](InteropHub_Communication_Bundles_Implementation_Plan.md) delivers the first real document workflow, before Communication Bundle structures. The code and local migration are implemented and deployed to Tomcat10.

The proof sequence is:

1. An authorized Meeting Controls user selects an agenda item on `/es/meeting-workspace` and uploads a PPTX. The workspace's existing selected-item heading identifies the upload target.
2. On `/es/agenda`, the corresponding Agenda cell shows planned text/existing link, attached images, PDF/PowerPoint downloads, then notes/outcomes. Verify downloaded bytes and original filename.
3. Add multiple attachments, including images uploaded after documents; images still appear first, with upload order retained within each group. Confirm an already-open agenda updates through its existing live refresh.
4. Remove an attachment from the workspace; the agenda link disappears without deleting the shared file or overwriting earlier slides. Verify upload/removal does not discard unsaved notes.
5. Verify unauthorized and cross-meeting writes fail, changes work after the session ends but not after `CLOSED`, and copy/postpone leaves new items without attachments.
6. Check Confluence export contains absolute InteropHub attachment links (including image links), before notes/outcomes, without copying files into Confluence.

Task 1b accepts raster images, PDF, and PPT/PPTX at the shared 25 MiB per-file cap, one file per upload and zero or more attachments per item. It does not need Topic Resources, bundles, or Azure rights. Shared storage may later serve Blob-backed attachments through the same URLs; verify Blob download disposition when infrastructure becomes available. Detached files remain stored pending a separately designed retention/cleanup policy, so disk monitoring remains important.

#### Task 1b deployment

- Apply only the new block marked `Meeting agenda attachments, Communication Bundles task 1b.` in [unapplied_updates.sql](../../db/unapplied_updates.sql), following the database release practice. It adds `hub_stored_file.download_only` and `es_meeting_agenda_attachment`. This block is already applied locally; do not reapply either migration blindly. Do not hand-edit generated schema snapshots.
- Deploy the matching WAR after applying the schema. No new environment variable or Azure permission is needed. Continue using `INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY`; a WAR update alone needs no service restart.
- The workspace servlet has a 26,214,400-byte per-file cap and a 27,262,976-byte request cap. Match reverse-proxy limits to allow multipart overhead while preserving the file cap. Reject multiple submitted file parts rather than silently selecting one.
- Users need both meeting-view access and existing Meeting Controls permission. Accepted presenters anywhere in the meeting intentionally qualify. Closed/cancelled meetings and cancelled/postponed items reject changes.
- Upload/removal uses a panel-only asynchronous request, preserving the selected item and active notes editor. The agenda's existing polling now updates attachments in every open meeting state, including completed meetings, and stops after closed/cancelled state.
- Agenda attachments appear below the Meeting Controls buttons and above Roles. The compact panel does not repeat the workspace's selected-item title or the anonymous-file-access warning. Removing that warning from the panel does not change the anonymous-read or retained-file policy documented above.
- Each file has a compact inline remove icon after its link, with a filename-specific accessible label and a "Remove from item" tooltip.
- Registration commits shared metadata and the item association together, with lifecycle/ownership rechecked under locks. Detachment records actor/time, retains file metadata/bytes, and does not revoke disclosed URLs. Corrections upload new files; copy/postpone does not copy associations.
- `download_only` is persisted before publication. Meeting PDFs download; normal PDF consumers retain existing inline behavior. Images remain inline, and PPT/PPTX remain downloads. Future Blob migration must preserve this policy and object headers.

#### Task 1b local verification

The final Maven package passed **167 tests**, with no failures/errors/skips; the dependency-free frontend suite passed **3 tests**. No dependencies changed for task 1b. Shared-storage tests cover exactly 26,214,400 bytes and rejection at 26,214,401 bytes.

On October 7, 2026, meeting 65 (`ImmDS + HALO`), selected item 204, demonstrated:

- PPTX, PDF, then PNG uploaded through Meeting Controls. Anonymous downloads matched fixture SHA-256 values and original filenames; PDF/PPTX used attachment disposition and PNG was inline. The generated PPTX proves package validation/byte transfer, not desktop PowerPoint rendering.
- An already-open completed-meeting agenda received additions and removal via polling. Images appeared before documents, followed by existing notes/outcomes. Confluence exported absolute image/document links in that order, without embedding attachment images.
- Upload/removal kept the same active editor DOM and selected item. The persisted note-document hash was unchanged; no real notes were edited for this test.
- TXT and invalid PNG content, missing CSRF, anonymous upload, cross-meeting item IDs, cross-item removal, multiple submitted file parts, a file of 26,214,401 bytes, and a closed-meeting upload were rejected. Removal recorded its actor/time and left the detached PDF anonymously readable. Audit timestamps follow the existing DAO/JDBC convention and were checked against database UTC.
- Registered URLs and bytes survived a hub-only redeployment. Nathan's task 1a Welcome image was not replaced.

No private meeting is present in the local dataset; private-access denial has service-test coverage, not a local browser proof. Copy/postpone non-transfer was checked against the existing item-creation paths and separate attachment association model, without changing the real meeting lifecycle. Production backup/restore, proxy limits, restart persistence, disk monitoring, private-meeting deployment checks, and Blob-backed download/migration verification remain open. Task 1b.6 stays unchecked for these deployment checks, not missing implementation.

The existing topic-note streaming endpoint logged a non-blocking `ServletOutputStream.isReady()` error during the workspace proof. That endpoint was not changed for task 1b; attachment operations passed and the stored note content remained unchanged. Treat live-note streaming as a separate follow-up, not a verified part of this milestone.

---

## Historical Blob-only step 1 instructions

The sections below preserve the original Azure handoff. Their fixed-key replacement,
image-only UI, and temporary-table descriptions are superseded by task 1a above.

### What was added in step 1

| Piece | Location |
|---|---|
| Azure SDK dependency | `com.azure:azure-storage-blob:12.28.0` in `pom.xml` |
| Configuration | `org.airahub.interophub.config.ArtifactStorageConfig` |
| Blob writes | `org.airahub.interophub.service.ArtifactBlobStorageService` |
| Admin test page | `AdminEsArtifactTestServlet` → `/admin/es/artifact-test` |
| Welcome page display | `WelcomeServlet` (anonymous and signed-in branches) |
| Temporary table | `es_artifact_demo` in `db/unapplied_updates.sql` |

`es_artifact_demo` exists only to remember the current object key. Step 2
replaces it with real Topic Resources; drop the table and the
`EsArtifactDemo` / `EsArtifactDemoDao` classes then.

---

## Environment variables

| Variable | Required | Default | Notes |
|---|---|---|---|
| `HUB_ARTIFACTS_BLOB_ENDPOINT` | No | `https://testsabbiastorage.blob.core.windows.net` | Public value |
| `HUB_ARTIFACTS_CONTAINER` | No | `artifacts` | Public value |
| `HUB_ARTIFACTS_SAS_TOKEN` | **Production only** | none | **Secret.** Container-scoped write SAS |

The rule the application follows:

```
SAS configured    -> uploads enabled
SAS not configured -> uploads disabled, warning logged at startup
```

A missing SAS is never treated as "development mode." In production it is a
configuration error and the admin page will say so explicitly.

### Local development (Nathan)

Nothing to configure. The defaults are correct, and without a SAS the upload
form is hidden while the uploaded image still renders — local pages read the
production Blob URL directly, which is the intended development behavior.

### Production Tomcat (Chris)

Set all three in Tomcat's environment, normally `bin/setenv.sh`:

```sh
export HUB_ARTIFACTS_BLOB_ENDPOINT="https://testsabbiastorage.blob.core.windows.net"
export HUB_ARTIFACTS_CONTAINER="artifacts"
export HUB_ARTIFACTS_SAS_TOKEN="sv=...&sr=c&sp=cw&..."
```

Then restart Tomcat. Rules for the SAS:

- Never commit it, never paste it into a chat or ticket, never log it.
- It is never sent to the browser — the application uploads through Tomcat.
- If it is ever exposed, regenerate it (below); the old one stays valid until
  its expiry, so exposure is not self-correcting.

---

## Regenerating the write SAS

**This must be done before deploying.** The two tokens created during the
August 2026 validation were pasted into a chat session and are considered
exposed. Do not reuse them.

Run with Azure CLI, signed in with access to the storage account:

```bash
key=$(az storage account keys list -n testsabbiastorage \
  --query "[?permissions=='FULL'].value" -o tsv | head -1)

az storage container generate-sas \
  --name artifacts \
  --account-name testsabbiastorage \
  --permissions cw \
  --https-only \
  --expiry "2027-01-01T00:00:00Z" \
  --account-key "$key" \
  -o tsv
```

`cw` is create + write. The application deliberately needs nothing more: it
sets `Content-Type`, `Cache-Control`, and `Content-Disposition` as part of the
upload call rather than as a separate header-update operation, so no read,
list, or delete permission is required.

Record the expiry date you chose and set a rotation reminder at least a month
before it. When the SAS expires, uploads stop working; reads are unaffected
because they are anonymous.

---

## Pre-deployment checks (Azure)

The infrastructure state below was validated on 2026-08-12 and has not been
re-checked since. Confirm each item before or during deployment — if anonymous
Blob read has since been disabled by policy, the whole no-proxy design fails
and we need to revisit the approach rather than patch the code.

- [ ] Account `testsabbiastorage` still has `allowBlobPublicAccess: true`
- [ ] Container `artifacts` still has ACL `blob`
- [ ] Anonymous GET of an exact Blob URL returns `200`
- [ ] Anonymous container listing still fails (`404`)
- [ ] Account still has `allowSharedKeyAccess: true` (required for the SAS)
- [ ] A freshly generated `cw` SAS can upload

Quick checks:

```bash
az storage account show -n testsabbiastorage \
  --query "{publicAccess:allowBlobPublicAccess, sharedKey:allowSharedKeyAccess, tls:minimumTlsVersion}"

az storage container show-permission -n artifacts --account-name testsabbiastorage --auth-mode login
```

---

## Deployment steps

1. Apply `db/unapplied_updates.sql` to production (creates `es_artifact_demo`).
   Back up the production database first, per
   `docs/database-release-practice.md`.
2. Set the three environment variables in Tomcat and restart.
3. Deploy the WAR.
4. Confirm the Tomcat log has **no** `HUB_ARTIFACTS_SAS_TOKEN is not set`
   warning. If it does, the environment variable is not reaching Tomcat.

`hibernate.hbm2ddl.auto` is `validate`, so if step 1 is skipped the application
will refuse to start rather than silently create the table. That is intentional.

---

## Post-deployment test script

1. Sign in as an admin and open **Topic Spaces → Artifact Storage Test**
   (`/admin/es/artifact-test`).
2. Upload a PNG or JPEG under 10 MB. → *Proves: authenticated upload through
   Tomcat with the write SAS.*
3. The image appears on the page, with its object key and direct Blob URL. →
   *Proves: the anonymously readable URL is correct.*
4. Open `/welcome` while signed in. The image appears. → *Proves: read works
   for authenticated pages.*
5. Open `/welcome` in a private window, signed out. The image still appears. →
   *Proves: anonymous read by exact Blob URL.*
6. Upload a **different** image on the admin page. The URL is unchanged, and
   both pages show the new image after a refresh. → *Proves: stable object keys
   plus `Cache-Control: no-cache` revalidation.*
7. Try an oversized file and a non-image file. Both are rejected with a clear
   message. → *Proves: server-side validation.*
8. On a local machine, `/welcome` shows the same image and the admin page shows
   the "uploads disabled" notice. → *Proves: development is read-only.*

If step 2 fails, the likely causes in order: SAS not reaching Tomcat, SAS
expired or wrong permissions, or account public-access settings changed. The
server log carries the Azure error detail.

---

## Open questions for the Azure contact

Not blocking, but worth answering while we have something live to test against:

1. **Soft delete** — is it enabled on the `artifacts` container? Purely an
   infrastructure safeguard; the application does not expose recovery, and
   replacing an image overwrites it. Nice to have during testing.
2. **Rotation** — who owns SAS rotation, and should the expiry be shorter than
   the current ~year, given it is a single long-lived credential in a Tomcat
   environment file?
3. **Policy risk** — is anything (Azure Policy, subscription governance)
   likely to disable anonymous blob access on this account in future? The
   design depends on it, and losing it silently would break every artifact URL
   at once.
4. **Lifecycle** — should there be any retention or lifecycle rule on this
   container, or is it intended to accumulate indefinitely?

---

## Known limits of this step

- Images only (PNG, JPEG, WebP, GIF), 10 MB, one slot. Documents and PDFs come
  with real Topic Resources in step 2.
- No delete. Replace-in-place only.
- Privacy model is unchanged from the reaction document: anyone holding a Blob
  URL can read it. Nothing whose disclosure would cause material harm belongs
  in this container.
