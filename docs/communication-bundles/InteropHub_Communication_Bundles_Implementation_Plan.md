# InteropHub Communication Bundles Implementation Plan

## Goal and current state

Extend the operating InteropHub application with curated rich content around existing Topics. Communication Bundles combine purpose-specific narrative and selected Topic Resources so people can understand a Topic or receive a project handoff.

Topics already exist, and the Topic page at `hub/es/topic/` is working. Step 1 provided the Blob image demonstration; production Blob verification remains blocked by Azure provisioning permissions. Task 1a provides shared local/Blob file metadata, local uploads, stable anonymous file URLs, and image/document demonstration slots. Task 1b now supplies meeting agenda attachments, the first real feature consumer. Local image display, PowerPoint/PDF downloads, live attachment addition/removal, and persistence across WAR redeployment have been verified. Phase 2 now has its initial domain and service foundation; the Topic Resource authoring UI and published Orientation workflow remain future work. Production operational checks remain separate.

**Phase 2 status:** The initial Topic Resource, Purpose/Template, Bundle, component-value, and resource-placement persistence/service foundation is implemented and locally verified. Its database changes were applied locally from `db/unapplied_updates.sql`, and the WAR was deployed to local Tomcat on October 7, 2026. Hibernate schema validation, real-database persistence, focused regression tests, passwordless sign-in, Topic display, and existing local file delivery passed. Topic Space administrators are the interim Topic stewards, and bundle audiences are further constrained by the Topic's existing Topic Space visibility. Phase 2 does not add authoring pages, publication operations, or template administration. Production application of the SQL and deployment remain separate.

Read this plan alongside the [Conceptual Model](InteropHub_Communication_Bundles_Conceptual_Model.md), [Blob Handoff Reaction](azure-blob-storage-handoff-reaction.md), and [Deployment Handoff](artifact-storage-deployment-handoff.md). The October 2026 revision adds task 1a (reusable local-folder and Blob storage) and task 1b (meeting agenda attachments), with local storage deployed first. Both are implemented and deployed to local Tomcat; production deployment verification is tracked in the handoff.

The upload/serve mechanism is application infrastructure, not a Communication Bundle feature. Task 1b makes meeting agenda attachments its first real feature consumer, without needing a Topic Resource or bundle. Promotional PNG workflows remain a future consumer, not part of this work.

## Initial use cases

### Topic Orientation

Provide the canonical, living introduction to a Topic, with at most one Orientation bundle per Topic. Its resources may include an infographic, one-pager, presentation, and supporting materials.

The existing Topic page controls the display layout. It will pull selected content from named bundle components using stable semantic keys. A template-driven display is not needed for this early implementation, although the content still has a defined structure. That structure can initially be seeded or supplied in code.

### Project Handoff

Provide a dated snapshot that transfers project context, accumulated work, and next actions. A Topic can have multiple handoffs, each representing a Month and Year.

This is the second implementation target. Its display has not yet been designed and will use the template-driven authoring and generic rendering framework. It also requires explicit resource preservation so replacing a shared current file does not silently change an earlier handoff.

## Implementation order

### 1. Prove Blob storage in a demonstration Topic

**Status: code written; production Blob verification blocked on Azure permissions.** The original proof was an admin-only image slot at `/admin/es/artifact-test`, displayed on `/welcome`, rather than a Topic steward document interface. Task 1a supersedes its 10 MiB cap with the shared 25 MiB cap and independently rejects development Blob writes.

Keep the Blob implementation and verify production server-side uploads, direct anonymous browser reads, and replacement freshness when infrastructure access is available. This verification is no longer a prerequisite for steps 2-4; task 1a's local production proof is.

**Result:** Existing Blob proof remains available without holding up local-backed delivery.

### 1a. Add reusable document/image storage with local and Blob backends

**Status: implemented and locally verified; production operational checks remain open.** Deploy local storage first, preserve Blob support, and permit records on both backends simultaneously. No automatic migration, dual writes, or provider failover.

#### Agreed behavior and boundaries

- `INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY` is an environment variable containing an absolute, durable server-folder path. Tomcat receives read/write rights. The folder is outside the WAR, exploded deployment, web root, source tree, and temporary directories. The local development setting is `C:\dev\immregistries\InteropHub-artifacts`. Restart Tomcat after changing its environment.
- An absent/blank directory disables the local backend only; it does not disable configured Blob reads or authorized Blob writes. If neither backend is writable, uploads are disabled. An explicitly configured but invalid/unwritable root is an actionable configuration error, not a reason to write somewhere else. Do not default to a temporary folder or create an arbitrary directory silently.
- Initial production deployment uses one Tomcat server and coordinated file/database backups. Adding application nodes requires the same shared durable root or a deliberate transition to Blob, not separate node-local disks.
- New uploads go to local storage for this milestone. Availability of Blob credentials must not change that choice. Replacements use the file's recorded backend. Future upload-routing policy and selective/all-file migration are separate decisions.
- Accept PNG, JPEG, WebP, GIF, PDF, TXT, DOC, DOCX, PPT, and PPTX, up to **25 MiB (26,214,400 bytes) per file**. Reject audio/video uploads on both backends in this milestone; local storage is not intended for audio/video later either. Audio/video external links may be considered in bundle design, without implying upload support.
- Inline presentation is limited to validated raster images and PDFs. TXT and Office files are attachments. Reject HTML, SVG, executable files, macro-enabled Office formats, and arbitrary archives. DOCX/PPTX container validation must not inadvertently allow general ZIP uploads. Accepted Office files are not guaranteed malware-free; do not execute, convert, or extract embedded content on the server.
- Both backends keep the existing anonymous-read-by-opaque-URL model. Bundle/Topic audiences govern disclosure of links, not authorization of each download. Anyone with a file URL can read it; no sensitive content belongs here. Draft/retired presentation rules do not revoke a previously disclosed URL.
- Development may upload to explicitly configured, isolated local storage only, never production storage or Blob. A production database copy alone does not copy local files. Use an isolated file/database copy for full local rendering; missing bytes must be reported rather than fetched from production implicitly. Before mutating copied records, ensure their local locators resolve only inside that isolated root.

#### Implementation checklist

- [x] **1a.1 Shared boundary:** Generalize configuration and the storage service around stored files, retaining the existing Blob writer as one backend. Keep Topic stewardship, bundle role, meeting ownership, and promotion placement outside the storage layer. Authorized feature endpoints invoke the shared service; do not introduce an unrestricted upload API.
- [x] **1a.2 Durable metadata:** Introduce a shared stored-file record/reference with a high-entropy public ID, explicit `LOCAL` or `BLOB` backend, opaque storage key, original filename, validated content type, byte size, uploader, and timestamps. Separate public identity from physical location. Record enough Blob location information to resolve existing records independently of future upload defaults; never store a SAS or absolute local path in a record. Topic Resources reference these files; external links remain links, not pretend stored files.
- [x] **1a.3 Compatibility:** Wire the admin demo and both Welcome-page branches to shared records and URL resolution. Existing demo keys represent Blob objects, not local files; migrate them explicitly as `BLOB` without assuming their bytes are present locally. Preserve the existing demo content/reference during migration. In step 2 retire only demo-specific entities/table, not the shared file records or storage service. Capture schema work through the documented database release process.
- [x] **1a.4 Local writes:** Resolve application-generated keys beneath the validated root; forbid path traversal, user-chosen paths, and symlink escapes. Stream to a temporary file on the same filesystem and publish atomically. A failed/oversized upload must not expose partial content or discard the previous good file. Coordinate file publication and metadata updates, including database failure and concurrent replacements, so served bytes/headers remain consistent; clean up only known failed-operation files.
- [x] **1a.5 Stable reads:** Add a context-path-aware, storage-neutral InteropHub file URL using the opaque public ID. Resolve the record, then stream local bytes or issue a temporary redirect to the recorded direct Blob URL. Do not proxy Blob bytes, expose physical paths, provide directory listing, or redirect to an arbitrary user-supplied URL. Local reads support GET/HEAD, correct length/type/disposition, `nosniff`, and revalidation after replacement. Redirects must revalidate too, so migrations do not leave a cached permanent backend choice. Missing metadata/files and unavailable backends produce explicit non-success responses and appropriate server diagnostics.
- [x] **1a.6 Upload controls:** Reuse application authentication/authorization and request-protection patterns for all writes. Validate extension, detected content, declared MIME type, filename, and streamed size rather than trusting the browser MIME type. Align servlet multipart and upstream request limits with the 25 MiB file cap, allowing bounded multipart overhead; reject a file one byte above the cap. Enforce the independent development Blob-write prohibition in both UI and server/service paths, even if a SAS is accidentally configured.
- [ ] **1a.7 Operations and proof:** Update the deployment instructions, exercise the existing demo with local images and downloadable documents, and verify anonymous reads, replacement freshness, disabled/invalid configuration, rejected formats, unauthorized writes, and restart/redeployment persistence. Verify isolated development writes cannot affect production. Confirm backup/restore of the matching files and database and monitor free disk space. Blob checks remain pending until Azure permissions are obtained; do not report an unverified backend as production-ready.

**Completion:** Without Azure credentials, an authorized admin can upload and replace an allowed image or document, retrieve it through a stable InteropHub URL while signed out, and retain it across restart/redeployment. The model explicitly records backend location and can represent local and Blob files at the same time. Local support and configuration errors are independently observable.

**Verification status:** Task 1a code, schema migration, 154 passing tests, and local PNG/PowerPoint upload/read proof are complete. Both files survived hub-only WAR redeployment. Checklist 1a.7 remains open for production backup/restore, operational failure drills, and remaining deployment checks; production Blob access is still unverified. See the [deployment verification record](artifact-storage-deployment-handoff.md#verification-record). Task 1b is not implemented.

**Future transition contract:** A migration copies and verifies the selected current file or preserved version, then updates its locator/backend while retaining its public ID. Keep the source until cutover is confirmed and rollback needs are resolved. Migration is not a new content version and must not alter preserved bytes. Stable InteropHub URLs continue to work; already distributed direct Blob URLs cannot be redirected by InteropHub and need a retention/redirect strategy before removing their objects. Task 1a establishes this contract, not a migration tool.

### 1b. Upload meeting agenda attachments and present them to participants

**Status: implemented and locally demonstrated on October 7, 2026.** Meeting 65, item 204 proved PowerPoint/PDF downloads, image-first ordering, live addition/removal, Confluence links, and unchanged selected item/active note editor. The local schema update is applied. See the [deployment handoff](artifact-storage-deployment-handoff.md) for verification details and remaining production checks.

**Priority: immediately after task 1a's local production proof, before step 2.** First demonstrate an image on the Welcome page through task 1a, then upload a PowerPoint from `/es/meeting-workspace` and download it from the corresponding item on `/es/agenda`. Production Blob verification and Communication Bundle structures are not dependencies.

This is deliberately smaller than Communication Bundles: an agenda item owns zero or more attachment associations to task 1a's shared stored files. It does not need a Topic, Topic Resource, bundle, template, or separate publication lifecycle. Do not put uploaded files into the existing single `link_url` field; preserve that independent external-link capability.

#### Agreed requirements

- **1b-REQ-01 - Upload target and multiplicity:** Add an upload option below the buttons under **Meeting Controls** for the currently selected agenda item. Use the workspace's existing selected-item heading rather than repeating its title in the compact attachment panel. No selected item means no upload target. Upload one file per request, repeatable for zero or more attachments; this milestone introduces no separate per-item count cap. Accept PNG/JPEG/WebP/GIF, PDF, and PPT/PPTX only, with task 1a's 25 MiB per-file limit and content validation. TXT and DOC/DOCX remain shared-storage capabilities, not meeting upload options.
- **1b-REQ-02 - Authorization and lifecycle:** Reuse `MeetingAuthorizationService.canControlMeeting` plus existing meeting-view access and CSRF protection for upload/removal. This intentionally includes accepted presenters anywhere in the meeting, not just the selected item's presenter. Verify on every write that the item belongs to the supplied meeting and the attachment belongs to that item; do not trust hidden fields. Allow changes before, during, and after the session, but not once the meeting is `CLOSED`; follow existing restrictions for cancelled meetings/items. Closed records remain readable. Do not equate being able to view the workspace with being able to modify attachments.
- **1b-REQ-03 - Participant presentation:** Preserve the existing three-column agenda table. In each item's **Agenda** cell, show planned agenda text and its existing external link, then inline attached images, then download links for PDFs and PowerPoints, then existing Notes and Outcomes. Within image and document groups retain upload order, with a deterministic tie-breaker. Use escaped original filenames as initial labels and image alternative text; constrain images to the cell width without distorting their aspect ratio. No slide conversion, PDF embedding, or additional table columns.
- **1b-REQ-04 - Immediate visibility and live refresh:** Successful attachments are immediately visible wherever the item is already visible; no extra publish action. Apply the same meeting access and item-status filtering to initial HTML, live-state JSON, and export. Extend the existing agenda polling so uploaded or removed attachments appear without a full-page reload and remain before Notes/Outcomes. Private meeting links must not leak through unauthorized page/API access; individual file URLs still use task 1a's anonymous-read model.
- **1b-REQ-05 - Corrections and ownership:** Show existing attachments and a remove-from-item action in the workspace. No replace-in-place, rename/reorder editor, automatic history, or attachment-library selector in this milestone. A corrected presentation is a new stored file and attachment, never an overwrite of old slides. Removal detaches the item association and records who did it and when; it does not revoke known URLs or physically delete the shared file. Retain detached stored-file records/bytes for now; garbage collection and retention policy require separate future design, with disk monitoring in the meantime. Copying or postponing an item into another meeting creates no attachment associations on the new item; original associations stay with the original item and obey its normal visibility rules.
- **1b-REQ-06 - Export and continuity:** Include attachments in the existing Confluence export as absolute stable InteropHub file links, images first and documents second, between the planned agenda/link and notes/outcomes. Images are links in the export, not copied or uploaded to Confluence. Use the existing trusted application-base URL mechanism. Backend migration must not change these references. An exported URL remains readable by its holder even if the meeting is private or its attachment is subsequently detached.

#### Implementation checklist

- [x] **1b.1 Attachment association [1b-REQ-01,05]:** Add an agenda-item attachment model/DAO connecting `EsMeetingAgendaItem` to task 1a's shared file records, with deterministic upload ordering and upload/removal provenance. Use the repository database release process for eventual SQL; do not hand-edit generated schema snapshots. Review `EsAgendaServlet` item-copy paths and meeting postponement paths so attachments do not transfer automatically. Load attachment lists in batches for meeting rendering rather than one query per attachment.
- [x] **1b.2 Workspace writes [1b-REQ-01,02,05]:** Extend `src/main/java/org/airahub/interophub/servlet/EsMeetingWorkspaceServlet.java` and its multipart registration in `src/main/webapp/WEB-INF/web.xml` with selected-item upload/removal, explicit success/error feedback, and an attachment list. Reuse shared storage rather than create another filesystem/Blob writer. Commit an attachment association only after a successful file upload; handle association-save failure explicitly without reporting success or damaging existing attachments. Reuse request protection, recheck access/lifecycle at submission time, and keep unauthorized or disabled actions unavailable in the UI and rejected by the server.
- [x] **1b.3 Workspace UX continuity [1b-REQ-01,02]:** Preserve selected-item navigation and unsaved/autosaving notes during upload/removal; do not let a form submit silently discard note edits or attach a file to the current meeting item instead of the item the user selected. Integrate with `frontend/meeting-workspace-notes.js` if necessary, using its existing edit/navigation protection. Reuse AIRA components; consult the shared CSS adoption guidance before adding any reusable styling.
- [x] **1b.4 Agenda and downloads [1b-REQ-03,04]:** Update `src/main/java/org/airahub/interophub/servlet/EsAgendaServlet.java` in both editor and read-only render branches. Keep attachment links outside the agenda text's click-to-edit wrapper. Add an attachment block before notes, with empty items producing no empty heading/placeholder. Use task 1a's URLs and metadata: raster images inline, PDF/PPT/PPTX with attachment download disposition and original filenames. Ensure the shared delivery policy supports document downloads on local and Blob backends; do not rely only on the HTML `download` attribute across a Blob redirect. Meeting PDFs are download links even though other task 1a consumers may present PDFs inline.
- [x] **1b.5 Live refresh and export [1b-REQ-04,06]:** Extend `EsAgendaServlet.renderLiveStateJson` and `src/main/webapp/js/agenda-live.js` with attachment content using the same grouping/order as initial rendering. Update `src/main/java/org/airahub/interophub/servlet/EsAgendaConfluenceServlet.java` to include absolute file links in the existing export table. Preserve all current notes, outcomes, current-item highlighting, and access/status filtering; do not introduce a second polling mechanism or expose server paths.
- [ ] **1b.6 Demonstration and verification [1b-REQ-01..06]:** After task 1a's Welcome-image proof, upload a PPTX to a selected item and download the identical bytes/original filename from its agenda row. Exercise zero/multiple attachments, interleaved image/document uploads, exact file cap and rejected types, live addition/removal, and preservation of notes. Verify forged cross-meeting/item IDs and unauthorized writes are rejected, `CLOSED` blocks writes, and copying/postponing does not transfer attachments. Verify Confluence links/grouping, private-meeting page/API access, persistence after redeployment, and no changed URLs after a backend locator move. Use existing servlet/service tests (including `src/test/java/org/airahub/interophub/servlet/EsMeetingWorkspaceServletTest.java`) and focused manual/browser checks; identify any Blob-specific behavior still awaiting infrastructure verification.

**Completion:** An authorized meeting controller uploads an image, PDF, and PowerPoint to a selected agenda item without losing notes. Participants see that item's planned agenda, images, document downloads, notes, and outcomes in that order; live refresh and Confluence links agree. Multiple attachments work without a bundle or Topic Resource. Closed meetings reject changes, and corrections do not mutate earlier uploaded bytes.

**Why this milestone first:** It proves actual document transfer, item ownership, participant display, and reusable storage before the much larger bundle/template/publishing work. Keep it limited to attachment associations and existing meeting workflows; do not bring Communication Bundle lifecycle or file versioning into the meeting proof.

### 2. Establish Topic Resources and the minimum bundle structure

**Status: persistence/service foundation implemented, SQL applied locally, and local deployment verified.**

Map the conceptual model onto the existing database and application. Introduce the minimum structures needed for reusable Topic Resources, the Topic Orientation Purpose and template definition, a bundle instance, and resource placements. Establish Topic stewardship and audience rules.

Resources belong to Topics independently of any bundle and reference task 1a's shared stored files or an external link. Keep technical resource type separate from the semantic role a resource fills in a bundle. Allow one resource to be selected in several bundles without duplicate uploads. Do not make the shared file layer depend on Topic ownership.

For this initial implementation, existing Topic Space administrators serve as Topic stewards; this avoids a second permission system while retaining the current Topic Space visibility boundary. The seeded Topic Orientation template is versioned and uses stable component keys. Bundle draft access is steward-only; published audience scopes are constrained by the Topic's visibility. Revisit explicit per-Topic steward assignments only if the existing Topic Space administrator boundary proves too broad.

**Local verification (October 7, 2026):** The build/deploy task in `.vscode/tasks.json` ran 37 focused tests with no failures or skips. `CommunicationBundlePersistenceTest` explicitly opts into the local database with `-Dinterophub.localBundleIntegration=true`; it validates the Hibernate mappings and seeded seven-component template, creates a temporary Topic and Orientation, persists/reloads narrative and resource placement, checks draft access and singleton behavior, then removes its test content. It is skipped in ordinary builds. The deployed WAR matched the built WAR by SHA-256. Browser verification completed passwordless sign-in, Welcome-image rendering, and existing Topic display; anonymous local file delivery also returned HTTP 200 after redeployment.

**Result:** Early Orientation work uses the same underlying model that Project Handoff will extend.

### 3. Enrich the existing Topic page

Give stewards a simple way to upload or select resources for named Orientation roles and edit basic resource metadata. Connect those selections to the existing `hub/es/topic/` page. Start with images, PDFs, downloadable Office files, and external links; comprehensive document previews can follow later.

**Result:** Documents and images appear on demonstration Topic pages early, without waiting for template administration or a generic renderer.

### 4. Complete the Orientation publishing workflow

Add draft editing, preview, publication, retirement, Month/Year dating, and audit records. Constrain bundle visibility by the enclosing Topic and keep editing permission separate from viewing permission. Verify resource replacement behavior and avoid exposing unpublished bundle content through Topic pages.

**Result:** Stewards can populate and maintain real Topic Orientations while the remaining framework is developed.

### 5. Build template administration and generic bundle authoring

Provide administrator tools to define, preview, version, and activate templates for supported product-defined Purposes. Existing bundles retain their original template version. Activating a template and publishing a bundle are separate operations.

Build a template-driven editor and generic renderer supporting narrative, structured items, individual resources, and ordered resource collections. Distinguish required from recommended content and omit empty optional components from the display. Use Orientation experience to inform this work.

**Result:** The framework can support Project Handoff without a bespoke page for each handoff.

### 6. Implement Project Handoff

Define the initial Handoff template and complete its authoring, preview, publication, and display workflow. Support multiple dated handoffs under each Topic. Add intentional preservation of resource versions and bind historically stable handoff resources to those versions before publication. Each preserved file has its own identity and backend locator; replacing the current file must not overwrite preserved bytes, regardless of backend.

**Result:** A steward can publish a coherent handoff whose preserved files remain stable when current Topic Resources change.

### 7. Pilot and refine both workflows

Exercise Orientation and Handoff with real content and stewards. Refine Topic-page presentation, bundle discovery, authoring guidance, and incomplete or failed operations. Verify that newer template versions leave older bundles valid and that resource reuse works across both purposes.

**Result:** Both initial use cases work from upload through audience presentation.

## Design boundaries and document alignment

Retain the Blob handoff's production-only server-side Blob writes, direct Blob reads, opaque keys, and no automatic retention of every replacement. Task 1a supersedes Blob-only storage and development-wide read-only assumptions: local writes are permitted in isolated development, and local reads pass through InteropHub. Stable application URLs serve local files or redirect to Blob. The handoff's fixed-slot assumptions are replaced by reusable Topic Resources over a shared file layer. Its exclusion of preserved versions is superseded by the conceptual model's explicit preservation requirement for handoffs.

Bundle visibility governs presentation within InteropHub, not file-level access controls, on either backend.

Keep bundle features focused on curated Topic communication, while allowing other application features to reuse the underlying file mechanism. General-purpose user file storage, folder hierarchies, Office collaboration, arbitrary page builders, and automatic version history remain outside scope. Task 1b adds only the specified meeting attachment workflow. Promotional workflows, migration tooling, future backend-selection policy, Implementation Bundles, media uploads, and browser-direct uploads remain deferred.

The existing [agenda live-notes and Confluence design](../es-agenda-live-notes-and-confluence-export.md) describes planned agenda followed directly by Notes/Outcomes. Task 1b inserts attachments between them without changing the table or note/outcome semantics; implementation should update that meeting-specific documentation when the feature is built.

The older [Content Security and Storage Principles](../InteropHub_Content_Security_and_Storage_Principles.md) still describes fixed slots, no preserved versions, Blob-only reads, and browser-direct uploads. For this work, follow the updated bundle documents on those points; retain its content-safety and low-assurance confidentiality principles. Updating that document is outside this Markdown-only Communication Bundles revision.

## Requirement-to-task mapping for the storage revision

| Requirement | Planned evidence |
| --- | --- |
| Local deployment without Azure rights; missing directory disables local support | 1a.1, 1a.4, 1a.7 |
| Reusable upload/serve mechanism beyond bundles | 1a.1, 1a.2, step 2 |
| Explicit per-file backend; mixed storage and later migration | 1a.2, 1a.3, 1a.5; future transition contract |
| Stable URLs and compatible anonymous reads | 1a.5, 1a.7 |
| Documents/images only, agreed allowlist and exact size cap | 1a.4, 1a.6, 1a.7 |
| Isolated development uploads; no production/Blob mutation | 1a.6, 1a.7 |
| Durable files, controlled replacement, operational recovery | 1a.4, 1a.7 |
| Intentional snapshot preservation independent of backend | 1a.2, step 6 |
| Selected-item uploads and zero/multiple attachments (1b-REQ-01) | 1b.1, 1b.2, 1b.3, 1b.6 |
| Meeting permissions, request protection, and CLOSED write boundary (1b-REQ-02) | 1b.2, 1b.3, 1b.6 |
| Images before document downloads before notes/outcomes (1b-REQ-03) | 1b.4, 1b.6 |
| Immediate presentation and existing live refresh (1b-REQ-04) | 1b.4, 1b.5, 1b.6 |
| Add/detach without overwrite or automatic copy (1b-REQ-05) | 1b.1, 1b.2, 1b.6 |
| Confluence links and storage-neutral continuity (1b-REQ-06) | 1b.5, 1b.6 |

## First usable milestones

**Storage proof (1a):** An admin uploads a local image that appears on the Welcome page while signed in and signed out, with durable storage and stable URLs.

**First real file workflow (1b):** A meeting controller uploads a PowerPoint to a selected agenda item, and participants download it from that item's agenda cell. Images, PDF downloads, live refresh, and Confluence links complete this milestone before bundle work begins.

**First bundle workflow (2-4):** A Topic steward uploads/selects resources into a Topic Orientation, previews the existing Topic page, and publishes the enriched page for its audience. Reach this milestone before completing template administration and Project Handoff.
