# InteropHub Communication Bundles Implementation Plan

## Goal and current state

Extend the operating InteropHub application with curated rich content around existing Topics. Communication Bundles combine purpose-specific narrative and selected Topic Resources so people can understand a Topic or receive a project handoff.

Topics already exist, and the Topic page at `hub/es/topic/` is working. There is currently no ability to upload or view files and no Communication Bundle support. The first practical goal is to enrich those existing Topic pages with documents and images. These pages are the front door to the work and will be the primary window for testing the new functionality.

Read this plan alongside the Communication Bundles Conceptual Model and the Azure Blob Storage Handoff Reaction. This file establishes implementation priorities rather than prescribing a detailed schema or UI design. Inspect the existing application before deciding how to map the concepts onto it.

## Initial use cases

### Topic Orientation

Provide the canonical, living introduction to a Topic, with at most one Orientation bundle per Topic. Its resources may include an infographic, one-pager, presentation, and supporting materials.

The existing Topic page controls the display layout. It will pull selected content from named bundle components using stable semantic keys. A template-driven display is not needed for this early implementation, although the content still has a defined structure. That structure can initially be seeded or supplied in code.

### Project Handoff

Provide a dated snapshot that transfers project context, accumulated work, and next actions. A Topic can have multiple handoffs, each representing a Month and Year.

This is the second implementation target. Its display has not yet been designed and will use the template-driven authoring and generic rendering framework. It also requires explicit resource preservation so replacing a shared current file does not silently change an earlier handoff.

## Implementation order

### 1. Prove Blob storage in a demonstration Topic

Build a small steward-only interface to upload, display or download, and replace documents and images. Verify production uploads through Tomcat, direct browser reads from Blob Storage, and fresh content after replacement. Development remains read-only, with Blob writes rejected by the server.

**Result:** The application can reliably store and retrieve files before bundle features depend on it.

### 2. Establish Topic Resources and the minimum bundle structure

Map the conceptual model onto the existing database and application. Introduce the minimum structures needed for reusable Topic Resources, the Topic Orientation Purpose and template definition, a bundle instance, and resource placements. Establish Topic stewardship and audience rules.

Resources belong to Topics independently of any bundle. Keep technical resource type separate from the semantic role a resource fills in a bundle. Allow one resource to be selected in several bundles without duplicate uploads.

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

Define the initial Handoff template and complete its authoring, preview, publication, and display workflow. Support multiple dated handoffs under each Topic. Add intentional preservation of resource versions and bind historically stable handoff resources to those versions before publication.

**Result:** A steward can publish a coherent handoff whose preserved files remain stable when current Topic Resources change.

### 7. Pilot and refine both workflows

Exercise Orientation and Handoff with real content and stewards. Refine Topic-page presentation, bundle discovery, authoring guidance, and incomplete or failed operations. Verify that newer template versions leave older bundles valid and that resource reuse works across both purposes.

**Result:** Both initial use cases work from upload through audience presentation.

## Design boundaries and document alignment

Retain the Blob handoff's infrastructure approach: production-only server-side writes, direct reads, opaque object keys, and no automatic retention of every replacement. Its fixed-file-slot assumptions must be adapted to reusable Topic Resources. Its exclusion of application-level preserved versions is superseded by the conceptual model's explicit preservation requirement for handoffs. Stable current resource URLs and separate preserved versions must coexist.

Bundle visibility governs presentation within InteropHub. Under the proposed Blob approach, anyone possessing a direct file URL can read it; bundle audience settings do not create file-level access controls.

Keep the feature focused on curated Topic communication. General file storage, folder hierarchies, Office collaboration, arbitrary user-defined page builders, and automatic file version history are outside scope. Implementation Bundles, video uploads, and browser-direct uploads are later possibilities rather than initial requirements.

## First usable milestone

A Topic steward can upload documents and images, select them into a Topic Orientation, preview the existing Topic page, and publish the enriched page for its intended audience. Reach this milestone before completing template administration and Project Handoff.
