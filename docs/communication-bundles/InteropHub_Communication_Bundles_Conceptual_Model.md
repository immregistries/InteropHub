INTEROPHUB

# Communication Bundles

Conceptual model for curated, purpose-specific communication around interoperability topics

**Core idea A Topic is the durable center of work. A Communication Bundle is a curated, purpose-specific presentation of structured narrative and selected Topic resources for a particular audience or task.**



## Purpose of this document

This document defines Communication Bundles conceptually before database or application design. It is intended to provide a stable set of terms, boundaries, lifecycle rules, and design principles that can be used to evaluate the current InteropHub implementation and determine what needs to change.

## Initial proven uses

- Topic Orientation — the canonical, living introduction to a Topic.

- Project Handoff — a dated snapshot used to transfer accumulated work and context to a new team.

- Implementation Bundle — an anticipated deeper technical orientation for implementers; useful to design for, but not required for the first implementation.

August 2026

**Storage clarification, October 2026:** Communication Bundles motivated a reusable InteropHub document/image upload-and-serve mechanism, but meeting agenda attachments will be its first real feature consumer. Local server-folder storage will be deployed first while Azure permissions are resolved; Blob support remains available for later use. See [implementation tasks 1a and 1b](InteropHub_Communication_Bundles_Implementation_Plan.md) for the storage proof and meeting attachment workflow. Neither is implemented by this clarification; promotional workflows remain deferred.

## Executive Summary

InteropHub is intentionally not a general-purpose file server, document collaboration system, or free-form content management platform. Its role is to organize interoperability work so that people can understand a Topic, find relevant activity, use curated resources, and participate effectively. Communication Bundles extend that organizing model to rich media and other resources without losing the platform’s opinionated structure.

A Communication Bundle combines predefined narrative fields and named resource roles for a specific communication purpose. The bundle does not provide an empty page on which a steward can place arbitrary content. Instead, a versioned template asks for the information normally needed for that purpose and identifies the kinds of resources that can fill each named role. Empty recommended sections simply do not render.

Resources are associated with the Topic and may be reused across bundles. Resource type is fixed and bounded by the application, while resource role is defined by the bundle template. This allows one PowerPoint, image, audio recording, or document to be used in different ways without duplicating the underlying resource merely because it appears in more than one communication experience.

The model distinguishes living bundles from snapshot bundles. Living bundles are maintained as the current representation of a purpose, such as Topic Orientation. Snapshot bundles, such as Project Handoffs, represent what was communicated for a particular month and year and are not expected to remain current. File versioning is explicit rather than automatic: InteropHub does not retain every replaced upload. A preserved resource version is created only when a steward intentionally chooses to preserve one, including when a snapshot bundle needs a stable historical resource.

## 1. Why Communication Bundles Exist

A folder containing several good documents can still be difficult to use. The problem is not storage capacity; it is communication. A recipient needs to know why the collection exists, which materials are primary, which are supplemental, where to start, what each resource is best used for, and what action or understanding should result.

The Risk-Based Immunization Forecasting handoff illustrated this clearly. The handoff page did more than list files: it explained the transition, offered different starting routes for different levels of depth, assigned semantic roles to resources, described how each should be used, and closed with a statement of what responsibility was being transferred. That pattern is reusable even though the purpose of the handoff differs from a general Topic introduction.

**Design principle The files are ingredients. The Communication Bundle is the communication product.**



Communication Bundles therefore provide a second organizing layer beneath a Topic. A Topic answers “What is this work?” A Communication Bundle answers “What does this audience need from this Topic for this purpose?”

## 2. Conceptual Model

The conceptual hierarchy is:

| Concept | Meaning |
| --- | --- |
| Topic | The durable center of interoperability work. It owns the long-running subject, community context, resources, meetings, outcomes, and related activity. |
| Resource | A durable content object associated with a Topic, such as an image, presentation, document, audio file, video, or external link. |
| Communication Bundle Purpose | A product-defined communication use case, such as Topic Orientation or Project Handoff. |
| Template Version | A versioned definition of the fields, prompts, named resource roles, cardinalities, ordering behavior, and defaults used for new bundles of a Purpose. |
| Communication Bundle | An instance of a Purpose for one Topic, containing the actual narrative and selected resources. |
| Bundle Placement | The relationship between a bundle slot and a Resource, including role-specific guidance and display order. |
| Steward | A person with responsibility to curate a Topic and manage its Resources and Communication Bundles. |

Resources belong conceptually to the Topic, not to a single bundle. A bundle selects and organizes Topic resources. A Resource can exist without appearing in any bundle, and the same Resource can appear in multiple bundles, potentially in different roles.

### Shared stored files are infrastructure, not Topic Resources

A Topic Resource is a domain object; a stored file is the underlying content and technical metadata it references. Keep those concepts separate. Meeting agenda attachments in task 1b reference shared files without requiring a dummy Topic, Resource, or bundle. Later promotional images can do the same. Each feature supplies its own ownership, upload authorization, and presentation rules.

An agenda item may have zero or more attachment associations. This is not a Communication Bundle or a Resource collection slot: it is a separate meeting-domain relationship. Task 1b supplies selected-item upload/removal under Meeting Controls and shows images, document download links, then notes/outcomes in the agenda. Uploads are immediately presented under existing meeting/item visibility rules, with no bundle-style publishing workflow. Corrections use new files; removal detaches an association without overwriting bytes or revoking known URLs. Copy/postpone does not automatically transfer attachment associations.

Each stored file has a stable, high-entropy public identity, an explicit backend (`LOCAL` or `BLOB`), and an opaque physical storage locator, separate from its original filename and descriptive metadata. Local roots are deployment configuration, not database paths. Blob location metadata must resolve the actual stored object rather than infer it from whichever backend is currently preferred for new uploads. External-link Resources do not require a stored-file record.

The stable InteropHub file URL resolves the recorded backend: it streams local bytes or redirects to a direct Blob URL. Both are anonymously readable by anyone possessing the URL. Audience restrictions control where links are disclosed; they do not secure individual downloads. A backend move retains the public identity and does not require rewriting bundle placements or future meeting links.

For task 1a, uploads are limited to the agreed document/image allowlist and 25 MiB per file. No audio/video uploads are enabled on either backend; local storage is not an audio/video backend. Conceptual audio/video Resource types below describe future possibilities or external links, not current upload capabilities.

## 3. Purposes and Opinionated Templates

A Communication Bundle Purpose is not merely a label. It defines the expected shape of a particular communication task. InteropHub should provide a bounded set of product-defined Purposes rather than allowing users to create arbitrary templates.

Each Purpose defines at least:

- A stable internal semantic key and a separate display name.

- Whether a Topic may have one bundle of this Purpose or multiple bundles.

- Whether bundles of this Purpose are living or snapshot communications.

- A current active Template Version for creating new bundles.

- Default visibility guidance.

- Authoring help that explains what this communication is intended to accomplish.

Templates are themselves versioned and have lifecycle/status. A new bundle should normally use the currently active template. Existing bundles remain associated with the template version under which they were created unless a steward explicitly migrates them. Adding a field to a later template must not make older bundles appear incomplete.

### Opinionated does not mean bureaucratic

The template should guide authors, not block useful communication. Only a small number of fields should be required. The opening explanation of what the bundle is for is a reasonable required element. Most other slots should be recommended. The authoring experience can show recommended elements as incomplete, while the display simply omits empty sections.

## 4. Template Slots and Stable Semantic Keys

A template should contain named components broader than file/resource slots. A component can represent narrative, structured data, or one or more resources. Every component has a stable semantic key that is distinct from its display wording.

| Semantic key | Component kind | Example display label | Typical cardinality |
| --- | --- | --- | --- |
| introduction | Text | What this bundle is for | 1 |
| current_state | Text | Current state | 0..1 |
| quick_start_routes | Structured repeating item | Suggested ways to start | 0..\* |
| primary_infographic | Resource | Infographic | 0..1 |
| primary_handoff_document | Resource | Primary handoff document | 0..1 |
| technical_sources | Resource collection | Technical foundation | 0..\* |
| next_actions | Structured list/text | Next actions | 0..1 |

Stable keys allow the generic renderer, specialized Topic pages, and future application logic to locate specific content without depending on display labels. A specialized Topic page can pull primary_infographic or primary_presentation into a bespoke layout while the same bundle remains fully renderable in the generic Communication Bundle view.

**Rendering rule Every bundle must be completely understandable in the generic renderer. Specialized views may promote named components, but the underlying data model must not depend on bespoke front-end code.**



## 5. Resources: Type, Role, Metadata, and Placement

Resource Type and Resource Role are deliberately different concepts.

Resource Type is application-defined and bounded. It describes what the asset technically is. Examples include image, presentation, document, audio, video, PDF, and external link. The application can use Resource Type to determine preview, playback, thumbnail generation, allowed upload formats, and technical validation.

Resource Role is semantic and is defined by the Template slot. It describes why the resource appears in this bundle. Examples include primary infographic, primary handoff document, technical source, supplemental visual, implementation guide, or audio orientation.

### Resource metadata vs. bundle placement

The Resource stores canonical information about the content itself: title, description, Resource Type, attribution where useful, file/link information, and other durable metadata. Bundle Placement stores context-specific meaning: which role the resource fills, why the audience should use it here, and its display order within a repeating slot.

**Rule Resource metadata answers “What is this?” Bundle placement answers “Why should I look at it here?”**



For 0..\* resource slots, ordering is explicit. The steward curates the sequence rather than relying on upload date, filename, or search relevance.

## 6. Living and Snapshot Bundles

Different communication purposes have different expectations about time. This behavior belongs to the Purpose definition.

| Purpose | Instances per Topic | Mode | Expected behavior |
| --- | --- | --- | --- |
| Topic Orientation | 0..1 | Living | Maintained as the current canonical introduction to the Topic. |
| Project Handoff | 0..\* | Snapshot | Represents what was handed off for a specific month/year; later handoffs create new bundles. |
| Implementation Bundle | 0..1 | Living | Maintained as the current deeper orientation for implementers. |

A published snapshot is historically meaningful but does not need to be technically immutable. Corrections may be made and audited. A substantive later communication should be represented as a new snapshot bundle rather than silently rewriting what an earlier handoff meant.

## 7. Bundle Dating and Publication Time

The domain date for a Communication Bundle is Month + Year, not a precise calendar day. The useful statement is “Project Handoff — August 2026” or “Topic Orientation — August 2026,” not “August 10, 2026.” Bundle preparation may span weeks, and day-level precision suggests meaning that is not actually present.

The system should separately maintain exact timestamps for creation, update, publication, and audit history. Those timestamps are system provenance. The Month/Year value is communication meaning.

For a living bundle, the Month/Year should change when a steward performs a meaningful refresh of the communication, not when someone makes a trivial editorial correction. For a snapshot bundle, the Month/Year remains the period the communication represents.

## 8. Explicit Resource Versioning

InteropHub should not implicitly preserve every uploaded file. Automatic file version history would turn routine replacement into permanent storage behavior and create complexity that is not needed for this application.

The Resource model should therefore distinguish two intentional actions:

1. Replace the current file. The resource continues to represent the same conceptual item, but the previous file is not retained as a recoverable version.

1. Create a preserved version. The steward intentionally keeps the existing file as a named or dated Resource Version and makes a newer version current.

Snapshot bundles create a special requirement: a snapshot must not silently change later because a shared Topic resource was replaced. Before publication, any resource that must remain historically stable should therefore be bound to an explicitly preserved Resource Version. The application may prompt the steward to preserve a version, but it should never silently retain one. This keeps preservation intentional.

**Important distinction Audit history is not file versioning. History records who changed metadata or bundle content and when. A Resource Version is a deliberately preserved file state that can be referenced later.**

Current and deliberately preserved files need independent stored-file identities and backend locators. A preserved version may be local while the current file is in Blob, or vice versa. Replacing the current file must never overwrite the object referenced by a preserved version. Moving storage is a verified location change, not a new content version; it must preserve bytes and existing references. Actual migration tooling is deferred.



## 9. Bundle Lifecycle

A small lifecycle is sufficient:

Draft: Being assembled by Topic stewards. Not visible to the intended audience.

Published: Available according to its visibility scope and treated as the current communication for its stated Purpose/date.

Retired: Preserved in InteropHub but no longer presented as active/current.

Published bundles normally are not deleted. Retirement preserves history without requiring the user to treat the material as current. Administrative deletion can remain an exceptional system-management capability rather than part of the normal community workflow.

## 10. Visibility, Stewardship, and Scope

Bundle visibility is audience management, not sensitive-document security. Effective visibility is constrained by the enclosing Topic. A bundle cannot expose content to an audience that cannot see the Topic itself.

Initial audience scopes:

Public: Anyone who can view the Topic may view the bundle without authentication.

Participants: Authenticated people who are allowed to participate in or access the Topic/Topic Space may view the bundle.

Stewards: Only Topic stewards, designated support personnel, and administrators may view the bundle.

Editing permission is separate from visibility. Communication Bundles and Topic Resources are curated by Topic stewards. Other participants consume the resulting communication; they do not receive bundle-authoring permissions merely because they can view the Topic.

Steward should be modeled as a Topic-level responsibility. A steward can create and manage the Topic’s Resources and any supported type of Communication Bundle. InteropHub administrators retain broader administrative authority.

### Content boundary

InteropHub is not the repository for contracts, personnel information, sensitive operational information, protected health information, or documents requiring strong distribution controls. A handoff bundle communicates the state and context of the Topic; it does not contain the contract that funds the Topic. Limited visibility means “not everyone needs this communication,” not “this system is protecting sensitive material.”

## 11. Initial Communication Bundle Purposes

The framework should be general enough to support additional purposes, but the product should initially define only purposes that correspond to demonstrated needs. This keeps InteropHub opinionated and avoids turning it into a configurable page builder.

### 11.1 Topic Orientation

Purpose: Provide the canonical, current introduction to a Topic for someone who wants to understand what it is, why it matters, and how to engage.

- Instances per Topic: 0..1.

- Mode: Living.

- Likely visibility: Public for public Topics; otherwise inherited/constrained by Topic visibility.

- Likely components: opening summary, why it matters, primary infographic (0..1), one-pager (0..1), primary presentation (0..1), video/audio introduction (0..1 each), additional resources (0..\*), and how to get involved.

- Bespoke presentation: the main Topic page may promote selected named components into fixed locations while still using the same underlying Communication Bundle.

### 11.2 Project Handoff

Purpose: Transfer accumulated context and work from one project phase or team to another without requiring the recipient to reconstruct the history or reopen substantially settled decisions.

- Instances per Topic: 0..\*.

- Mode: Snapshot.

- Bundle date: Month + Year.

- Likely visibility: Participants or Stewards, depending on the Topic and handoff audience.

- Likely components: what this handoff is for, current state, suggested ways to start, primary handoff document, primary presentation, technical foundation, visual explanations, audio/video orientation, working documents, what is being handed off, continuing coordination, and next actions.

- Historical behavior: a later substantive handoff becomes a new dated bundle rather than an update to the old handoff.

### 11.3 Implementation Bundle (anticipated)

Purpose: Give implementers a deeper technical orientation than the general Topic introduction, collecting the current specifications, implementation guidance, examples, test resources, reference applications, and supporting explanations needed to begin implementation.

- Instances per Topic: likely 0..1.

- Mode: likely Living.

- Likely audience: public or authenticated participants, depending on the Topic.

- Status: anticipated design target, not required for the initial release unless a concrete implementation use case is ready.

## 12. Explicit Non-Goals

Communication Bundles should not become a path toward general file-server or CMS functionality. The following are outside the intended scope unless a future concrete InteropHub use case justifies them independently:

- Personal or shared folder hierarchies.

- Arbitrary file dumping without Topic context.

  This restricts Communication Bundle authoring, not the shared storage mechanism. Other supported application features may own files in their own domain context.

- Dropbox/Drive-style synchronization or backup.

- General document collaboration or in-browser Office editing.

- Automatic retention of every file replacement.

- Arbitrary user-defined page templates or drag-and-drop page building.

- Complex document-level ACLs intended to protect sensitive information.

- Contract, HR, confidential business, clinical, or other sensitive-document repositories.

## 13. Conceptual Rules

1. Topics remain the durable centers of InteropHub. Bundles are purpose-specific views and communications around a Topic.

2. Every Communication Bundle has exactly one Topic and one Purpose/Template Version.

3. Resources are reusable Topic assets; bundle inclusion does not transfer ownership of the Resource to the bundle.

4. A Resource may appear in zero, one, or many bundles.

5. Resource Type is globally defined by the application. Resource Role is defined by the bundle template.

6. Template components use stable semantic keys independent of display wording.

7. Empty recommended components do not render.

8. Repeating components have explicit author-controlled order.

9. Every bundle can be rendered generically. Specialized views may elevate known semantic components.

10. Bundle dating uses Month + Year for communication meaning; exact timestamps remain system history.

11. Published snapshot bundles should not silently change because a shared Resource file changes.

12. File versions are preserved only through explicit steward action; routine file replacement does not create implicit history.

13. Template versions are explicit and controlled; older bundles remain valid against the template version they used.

14. Visibility is audience management, not protection for sensitive content.

15. Only Topic stewards manage bundles and Topic resources in the normal workflow.

16. Published content is retired rather than deleted as the normal lifecycle action.

## 14. Questions for Evaluating the Current System

The following questions can be used to compare the current database and application against the conceptual model before deciding what schema or UI changes are needed:

- Can a Resource exist as a Topic-level object independent of any bundle?

- Can one Resource be reused in several bundles without re-uploading the file?

- Does the current Resource model clearly separate technical Resource Type from semantic use/role?

- Can bundle placement store role-specific guidance and display order without modifying canonical Resource metadata?

- Is there a first-class concept representing Communication Bundle Purpose?

- Can each Purpose specify singleton vs. multiple instances per Topic?

- Can each Purpose specify living vs. snapshot behavior?

- Can Purpose templates be versioned, activated/deactivated, and retained for old bundles?

- Can template components have stable semantic keys and different component kinds (text, structured list, resource, resource collection)?

- Can required and recommended components be distinguished?

- Can empty components be omitted from display?

- Can the same bundle be rendered through both a generic renderer and a specialized Topic-page representation?

- Does the model support a Month/Year communication date separately from exact system timestamps?

- Can a published snapshot bind to intentionally preserved Resource Versions without turning every upload into automatic version history?

- Can routine Resource replacement occur without retaining the previous file?

- Is bundle visibility separate from edit permission and constrained by Topic visibility?

- Is Topic stewardship sufficient to authorize resource and bundle management?

- Can bundles move through Draft, Published, and Retired states without normal deletion?

- Does the current system preserve audit history without confusing audit history with recoverable content versions?

- Are there any current features that would unintentionally turn Communication Bundles into a general file server or free-form CMS?

## Conclusion

Communication Bundles give InteropHub a single model for rich topic communication without turning the platform into a file repository. Topic Orientation, Project Handoff, and potentially Implementation Orientation are different communication purposes built on the same underlying concepts: opinionated templates, stable semantic slots, reusable Topic resources, explicit roles, audience-aware visibility, and a small lifecycle.

The model is intentionally constrained. InteropHub decides which communication purposes it supports and what each purpose normally contains. Stewards fill in that structure and select the best resources. The result is a communication experience that can be rendered generically or presented through a purpose-specific interface while remaining coherent under the covers.

The next design step is to map these conceptual objects and rules onto the current InteropHub database and application, identifying which existing structures can be retained, which can be generalized, and the minimum new entities and relationships required.
