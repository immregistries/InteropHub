# InteropHub Communication Bundles Implementation Plan

## Goal and current state

Extend the operating InteropHub application with curated rich content around existing Topics. Communication Bundles combine purpose-specific narrative and selected Topic Resources so people can understand a Topic or start a new project with a Starter Packet.

Topics already exist, and the Topic page at `hub/es/topic/` is working. Step 1 provided the Blob image demonstration; production Blob verification remains blocked by Azure provisioning permissions. Task 1a provides shared local/Blob file metadata, local uploads, stable anonymous file URLs, and image/document demonstration slots. Task 1b now supplies meeting agenda attachments, the first real feature consumer. Local image display, PowerPoint/PDF downloads, live attachment addition/removal, and persistence across WAR redeployment have been verified. Phase 2 provides the domain and service foundation. Phase 3 adds Topic Resource authoring and Orientation resource presentation on the existing Topic page. Phase 4 now completes Orientation narrative authoring, audience/date settings, publication/retirement, audit history, and stable-URL file replacement. Production operational checks remain separate.

**Phase 2 status:** The initial Topic Resource, Purpose/Template, Bundle, component-value, and resource-placement persistence/service foundation is implemented and locally verified. Its database changes were applied locally from `db/unapplied_updates.sql`, and the WAR was deployed to local Tomcat on October 7, 2026. Hibernate schema validation, real-database persistence, focused regression tests, passwordless sign-in, Topic display, and existing local file delivery passed. Topic Space administrators are the interim Topic stewards, and bundle audiences are further constrained by the Topic's existing Topic Space visibility. Phase 2 does not add authoring pages, publication operations, or template administration. Production application of the SQL and deployment remain separate.

Read this plan alongside the [Conceptual Model](InteropHub_Communication_Bundles_Conceptual_Model.md), [Blob Handoff Reaction](azure-blob-storage-handoff-reaction.md), and [Deployment Handoff](artifact-storage-deployment-handoff.md). The October 2026 revision adds task 1a (reusable local-folder and Blob storage) and task 1b (meeting agenda attachments), with local storage deployed first. Both are implemented and deployed to local Tomcat; production deployment verification is tracked in the handoff.

The upload/serve mechanism is application infrastructure, not a Communication Bundle feature. Task 1b makes meeting agenda attachments its first real feature consumer, without needing a Topic Resource or bundle. Promotional PNG workflows remain a future consumer, not part of this work.

## Initial use cases

### Topic Orientation

Provide the canonical, living introduction to a Topic, with at most one Orientation bundle per Topic. Its resources may include an infographic, one-pager, presentation, and supporting materials.

The existing Topic page controls the display layout. It will pull selected content from named bundle components using stable semantic keys. A template-driven display is not needed for this early implementation, although the content still has a defined structure. That structure can initially be seeded or supplied in code.

### Starter Packet

Provide a dated snapshot of project context, available resources, suggested starting points, and next actions to help people start a new project. A Starter Packet does not imply that our team's work is complete or that responsibility is being transferred. A Topic can have multiple Starter Packets, each representing a Month and Year.

This is the second implementation target. Its display has not yet been designed and will use the template-driven authoring and generic rendering framework. It also requires explicit resource preservation so replacing a shared current file does not silently change an earlier Starter Packet.

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

**Result:** Early Orientation work uses the same underlying model that Starter Packet will extend.

### 3. Enrich the existing Topic page

**Status: implemented, tested, and deployed locally.**

Give stewards a simple way to upload or select resources for named Orientation roles and edit basic resource metadata. Connect those selections to the existing `hub/es/topic/` page. Start with images, PDFs, downloadable Office files, and external links; comprehensive document previews can follow later.

Topic Space administrators reach `hub/es/topic-resources/{topicId}` through **Orientation Resources** in the Topic management navigation. They can create the initial Orientation draft, upload a Topic Resource or add an HTTP(S) link, and edit its title, description, attribution, and external URL. Uploads use the shared 25 MiB validation/storage service and register file metadata and Topic ownership in one transaction. No additional SQL is needed beyond Phase 2.

The resource editor uses the Orientation's retained template version to show Infographic, One-pager, Primary presentation, and Additional resources roles. Selecting a resource for a single role replaces only that placement; supporting resources are appended in order, with duplicate selections rejected. Removing a selection retains the resource in the Topic library. These role mutations lock and recheck the draft bundle within their transaction. The same resource can fill multiple roles without another upload.

**Topic-page presentation:** Integrate resources into the existing Overview area, not a separate "Orientation resources" card. The image selected for the Infographic role replaces the right-hand "Coming soon" placeholder and scales within that column; clicking it opens the full image in a new tab. Other selected resources appear as named `aira-chip` links beside Confluence in the left-hand Overview column, with small type icons (IMG, PDF, DOC, PPT, LINK), tooltips, and accessible labels. Viewable files and external links open in a new tab; downloadable files retain their download behavior. Resource descriptions, filenames, attribution, and role headings remain in the editor rather than expanding the Topic page. Supporting images do not become additional full-size previews.

**Interim visibility:** By explicit agreement, Phase 3 showed draft resources on the Topic page only to stewards, with a clear draft-preview label. Phase 4 now shows a published Orientation only to viewers allowed by both the Topic and its selected audience; draft and retired content remain hidden from non-stewards. The editor rejects non-stewards and requires CSRF tokens for mutations. File URLs retain the shared storage layer's anonymous-read policy; the editor warns that draft visibility does not make uploaded bytes confidential.

**Local verification (October 7, 2026):** All 51 focused tests passed, including the opt-in real-database test, resource rendering/escaping, editor authorization/CSRF, navigation, and existing storage/meeting regressions. The real-database test now verifies atomic file/resource registration, metadata changes, single-role replacement, ordered collection selection, invalid/duplicate selection rejection, draft isolation, and removal without deleting library resources. Browser checks on a temporary Topic verified image/PDF/PowerPoint uploads, an external link, all four role selections, metadata updates, image rendering and document delivery, single-role replacement, and selection removal. Anonymous requests confirmed draft resources were absent from the Topic page and the editor returned HTTP 403. Temporary test content was removed. The deployed and built WARs matched by SHA-256.

**Presentation correction verified (October 7, 2026):** All 52 focused tests passed and the WAR was rebuilt and deployed locally with matching SHA-256 hashes. Browser verification on Topic 1 confirmed the uploaded infographic loads inside Overview, fits its column (437 px), and links to the original image in a new tab; the standalone resource section is absent. Renderer tests cover supporting images as icons, PDF viewing, presentation downloads, external links, escaping, and missing-infographic fallback. Anonymous HTTP checks still exclude the draft content. Existing user resources were not modified.

**Chip refinement verified (October 7, 2026):** All 53 focused tests passed and the local deployment matched the built WAR. Topic 1 now shows "CDS Hooks versus ImmDS" as an `aira-chip` alongside Confluence, with its PPT icon and download behavior retained, and no resource links beneath the infographic. The management link reads "Orientation Resources". Anonymous draft isolation remains unchanged.

**Result:** Documents and images appear on demonstration Topic pages early, without waiting for template administration or a generic renderer.

### 4. Complete the Orientation publishing workflow — complete

Add draft editing, preview, publication, retirement, Month/Year dating, and audit records. Constrain bundle visibility by the enclosing Topic and keep editing permission separate from viewing permission. Verify resource replacement behavior and avoid exposing unpublished bundle content through Topic pages.

**Implemented:** Stewards can edit draft narrative, assign named Topic Resources, and set an allowed audience and optional Month/Year; Month/Year and all required template components are validated before publication. Published Orientations render with narrative and dates only to viewers allowed by both Topic visibility and bundle audience. As living bundles, published Orientations also allow immediate, audited updates to narrative, audience/date, and resource roles without retirement or republication. These edits retain bundle identity and the original publication timestamp; published Month/Year cannot be cleared. Retirement is for withdrawing an Orientation, not routine maintenance. Bundle edits, resource placements, resource metadata updates, and file replacement are audited. Replacing a current file retains its Topic Resource, stored-file identity, and public URL while updating the current content. Published snapshots and retired bundles do not gain live-edit permissions.

**Verification (October 8, 2026):** The full Maven suite passed (193 tests, 0 failures, 0 errors); the opt-in local-database persistence test was skipped because it requires the Phase 4 SQL migration to be applied locally. Focused tests cover lifecycle validation, audience constraints, retirement/recreation, draft-only controls, and servlet behavior. The persistence test now checks stable public identity and prior-file cleanup during local replacement, and removes audit rows during cleanup.

**Result:** Stewards can populate and maintain real Topic Orientations while the remaining framework is developed.

**Presentation refinement (October 8, 2026):** The communication date appears below the infographic. "What this Topic is", "Why it matters", and "How to get involved" remain available authoring prompts but are all optional; blank narratives are omitted from the Topic page and do not block publication. Month/Year remains required for publication. The pending SQL seeds optional narratives and includes a targeted update for existing Orientation templates.

**Living maintenance refinement (October 8, 2026):** Each save on a published Orientation updates the live page immediately and records an audit event. Update Month/Year explicitly for a meaningful refresh, not a minor correction. The editor explains this behavior, retains retirement, and does not offer republication. Persistence guards check the purpose's `LIVING` mode under the bundle mutation lock; required components remain enforced for published content. This uses existing purpose mode and audit tables and needs no additional schema migration.

**Living maintenance verification:** All 61 focused tests passed, including the opt-in local-database test. Live narrative, settings, and resource-role changes persist with actor audit entries while retaining identity, publication timestamp, and published visibility. Tests also verify that minor narrative edits do not automatically advance Month/Year, published dates cannot be cleared, unauthorized edits are rejected, and snapshot/retired bundles stay protected. The updated WAR was deployed to local Tomcat with matching SHA-256 hashes.

### 5. Build template administration and generic bundle authoring — implemented, verification pending

Provide administrator tools to define, preview, version, and activate templates for supported product-defined Purposes. Existing bundles retain their original template version. Activating a template and publishing a bundle are separate operations.

Build a template-driven editor and generic renderer supporting narrative, structured items, individual resources, and ordered resource collections. Distinguish required from recommended content and omit empty optional components from the display. Use Orientation experience to inform this work.

**Administration and navigation:** Global administrators enter through **Admin → Content → Communication Bundle Templates** (`/admin/content/bundle-templates`). Topic Space stewardship alone does not grant access. Administrators can copy a released template into a new numbered draft, edit component labels/prompts, kinds, requirement flags, cardinalities and display order, preview the template, and activate it. Purposes remain product-defined; this is not an arbitrary-purpose/page-builder interface. Semantic keys are independent of display wording. Orientation's named resource roles must retain their keys and kinds so the specialized Topic layout keeps working.

**Version lifecycle:** Draft component edits and activation are transactional. Released or already-used templates cannot be changed. Activation retires the previous active version and changes the Purpose's active-template pointer for new bundles only; existing bundles keep their original version and component definitions. Template activation does not publish content. Explicit bundle migration is not part of this phase.

**Topic authoring:** Actual content remains under the Topic and is authored by its stewards, not in Admin. Orientation now exercises the shared component value editor and generic preview alongside its specialized infographic/Topic presentation. Text fields use plain text. Structured lists are ordered text items authored one per line and stored as a JSON string array; richer structured schemas remain a future extension. Resource collections support explicit up/down ordering, and placements support context notes. Required versus recommended content is indicated during authoring; empty optional components do not render. Existing audience, draft/publication, living-edit, audit, and file-access boundaries remain in force.

**Verification status:** Focused unit and opt-in local-database tests were added for template validation, admin authorization, Content navigation, copy/activation/version isolation, structured content, generic rendering and resource reordering. Static validation covers editor diagnostics, servlet XML and duplicate-route checks, and diff whitespace checks. Maven execution, local redeployment and browser verification of the new Admin route remain pending at the user's request. Phase 6 still owns the Starter Packet Purpose/template and end-to-end packet lifecycle.

**Result:** The framework can support Starter Packets without a bespoke page for each packet.

### 6. Implement Starter Packet

**Status: Implemented in source; executable verification, database application and deployment pending.**

Define the initial Starter Packet template around project context, available resources, suggested starting points, and next actions, rather than completed-work transfer. Complete its authoring, preview, publication, and display workflow. Support multiple dated Starter Packets under each Topic, with retirement when a packet is no longer applicable. Add intentional preservation of resource versions and bind historically stable Starter Packet resources to those versions before publication. Each preserved file has its own identity and backend locator; replacing the current file must not overwrite preserved bytes, regardless of backend.

**Confirmed initial scope (October 8, 2026):**

- **Access:** Global administrators and people with an active `CHAMPION` or `SUPPORT` subscription on this Topic can view, create, edit drafts, publish, and retire Starter Packets. These are individual subscription roles, not the separate supporter organizations. Require normal Topic visibility as well. Enforce the same access on direct bundle URLs and every mutation; merely hiding a link is insufficient. Ordinary followers and Topic Space participants do not gain packet access from membership alone.
- **Management navigation:** Add **Starter Packet** under **Manage This Topic**, leading to the Topic's packet management/list page. Keep template administration under Admin → Content.
- **Initial fields:** Required packet title and Month/Year; optional separate teaser summary and teaser image, full explanation, ordered starting-points list, ordered next-actions list, and ordered supporting resources. Teaser content is distinct from the full explanation. Use the versioned component framework for content rather than adding a bespoke packet field system.
- **Topic presentation:** Add a **Starter Packet** section at the bottom of the main `/es/topic/{id}` content, immediately before the Topic ID/details strip. Show a teaser for every published, non-retired packet, newest Month/Year first, with a deterministic tie-breaker. Each teaser contains its title/date, available summary/image, and a link to the full bundle. Hide the section from unauthorized viewers and when there are no published, non-retired packets. Drafts remain in the authorized management workflow.
- **Full bundle page:** Provide a general bundle page that renders the full packet using its original template version, not just a Starter Packet-specific Topic fragment. Include a **Back to Topic** link and the Topic Space context header. Apply the packet's access policy to this page, including direct navigation.
- **Lifecycle:** Multiple dated packets can coexist. Published packets are locked against editing; revisions copy into a new draft and publication is a separate action. Authors retire an old packet explicitly when it is no longer applicable; publishing another does not automatically retire it. A copied draft does not modify its source packet or its preserved resources.
- **Preservation:** Before publication, require explicit author confirmation to preserve every selected uploaded file, including the teaser image, as independently identified stored bytes. Freeze selected resource metadata/context and external-link URLs for the published packet as well, so the snapshot does not silently follow later Topic Resource edits. Remote content behind external URLs cannot be frozen. Publication must not succeed with incomplete preservation; failures must leave a recoverable draft and surface an actionable error. Routine current-resource replacement still does not create automatic history.
- **Storage boundary:** Restricted bundle access governs InteropHub pages, not file-level confidentiality. Stable file URLs remain readable under the existing storage policy; this restriction does not make packet images/documents suitable for confidential information.
- **Validation:** Cover Topic-specific role authorization and direct URLs, management discovery, teaser placement/omission and ordering, all component rendering, original-template retention, snapshot edit rejection, copying/retirement, and preservation across current-file replacements and failure paths. Rebuild, deployment, and executable verification remain deferred until requested, consistent with the current no-rebuild instruction.

**Implemented surfaces:**

- Topic **Manage This Topic → Starter Packet** leads to `/es/starter-packets/{topicId}`. Champion/support access is independent of the existing Orientation steward controls. Management lists every status, edits drafts, previews the full page, and supports explicit copying, preservation/publication and retirement.
- The Topic page places published packet teasers immediately before its Topic ID/details strip. Restricted pages use `no-store`. `/es/bundle/{bundleId}` renders a read-only packet with the original template and preserved resources, a Topic Space header, and Back to Topic navigation.
- A blank draft uses the active Starter Packet template. A copied draft keeps the source's released template version, text/list values, ordering/context and preserved resource selections. Reselecting a current resource in a draft opts that selection into a new preservation at publication; it does not alter the source snapshot.
- `StarterPacketService` enforces Topic-specific authorization and the draft-only edit boundary. Generic/Orientation authoring cannot bypass these rules. Publication locks the draft and source rows, validates required content, copies selected uploaded bytes, binds immutable resource metadata versions, and publishes in one database transaction. Failed publication rolls back to a recoverable draft and reports any newly written objects requiring operator reconciliation.
- `StoredFileService.preserve` supports independent LOCAL and configured BLOB copies. Preserved stored-file identities cannot be replaced or registered as current editable Topic Resources. Ordinary file replacement still creates no automatic history.
- The pending SQL adds the Starter Packet Purpose/template, resource-version table and placement version foreign key. **Apply it through the established database refresh/release process before deploying a build containing the new Hibernate mapping.** The generated schema is not hand-edited.

**Verification status:** Unit/renderer/servlet tests and an opt-in local-database lifecycle/preservation test are written. Coverage includes role isolation, CSRF, direct URLs, teaser omission, version-aware image/resource resolution, locked snapshots, template contracts, publication confirmation, recoverable preservation failure, copied original versions, retirement, deterministic ordering, and stable PDF/image bytes after current-file replacements. Editor diagnostics, XML/duplicate-route checks and whitespace checks are complete; the existing unrelated unused-field warning remains. Tests have not been executed, and no rebuild, database refresh or deployment was performed.

**Result:** An authorized Topic champion/support contact or global administrator can publish a coherent Starter Packet that helps people begin a project and whose preserved files remain stable when current Topic Resources change.

### 7. Pilot and refine both workflows

Exercise Orientation and Starter Packet with real content and stewards. Refine Topic-page presentation, bundle discovery, authoring guidance, and incomplete or failed operations. Verify that newer template versions leave older bundles valid and that resource reuse works across both purposes.

**Result:** Both initial use cases work from upload through audience presentation.

## Design boundaries and document alignment

Retain the Blob handoff's production-only server-side Blob writes, direct Blob reads, opaque keys, and no automatic retention of every replacement. Task 1a supersedes Blob-only storage and development-wide read-only assumptions: local writes are permitted in isolated development, and local reads pass through InteropHub. Stable application URLs serve local files or redirect to Blob. The handoff's fixed-slot assumptions are replaced by reusable Topic Resources over a shared file layer. Its exclusion of preserved versions is superseded by the conceptual model's explicit preservation requirement for Starter Packets.

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

**First bundle workflow (2-4):** A Topic steward uploads/selects resources into a Topic Orientation, previews the existing Topic page, and publishes the enriched page for its audience. Reach this milestone before completing template administration and Starter Packet.
