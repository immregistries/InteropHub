# InteropHub Content Security and Storage Principles

## Purpose

This document defines the intended security level, information boundaries, and storage responsibilities for content presented through InteropHub.

InteropHub is a communication and publication platform. It presents the current state of a topic to an intended audience. It is not a confidential document repository, general file-storage system, document collaboration system, or system of record.

The design should remain proportional to that purpose. Its most important security responsibility is controlling who may post or replace information presented by InteropHub. It does not promise strong confidentiality for uploaded content.

## The Bulletin Board Model

InteropHub operates like a community bulletin board.

Each topic has a small, predefined set of presentation slots, such as:

- One current one-page explanation
- One current infographic
- Other specifically supported artifact types
- A link to a separate location containing broader project files

Each slot contains zero or one current artifact. Uploading a new artifact replaces what InteropHub previously presented in that slot. The stable location continues to represent the current content.

InteropHub does not support:

- Draft or intermediate documents
- Multiple files of the same artifact type
- General file collections
- Movement of artifacts between topics
- Movement of topics between Public and Private Topic Spaces
- Application-visible version history or rollback
- Permanent archival storage
- Recovery from an administrator uploading the wrong file

Working files, earlier versions, supporting collections, and authoritative source documents must be maintained in an appropriate external system such as Dropbox, Confluence, Microsoft 365, or another designated repository. InteropHub may link to that external location.

## Information Appropriate for InteropHub

Content placed in InteropHub must be safe enough that accidental disclosure would not cause material harm.

Appropriate content includes:

- Public community information
- Current topic explanations
- Infographics and presentations
- Concept diagrams
- Working strategy proposals that are not ready for public distribution but are not confidential
- Information whose unintended disclosure might cause temporary confusion but would not create substantive legal, financial, privacy, contractual, personnel, or operational harm

InteropHub must not be used for:

- Protected health information or identifiable patient information
- Sensitive personally identifiable information
- Passwords, access tokens, keys, or other credentials
- Financial account information
- Personnel or human-resources records
- Sensitive legal or contractual documents
- Security-sensitive operational information
- Any information requiring dependable confidentiality or controlled redistribution

When content requires stronger protection, it must remain in an approved secure system. InteropHub may link to that system when appropriate.

## Public Topic Spaces

Public Topic Spaces are intentionally open. Their pages and artifacts are available without login or authentication and may be indexed, linked, copied, downloaded, cached, or redistributed.

Public artifacts are comparable to notices posted on a public community bulletin board. The system should make them easy to view and download directly from Blob Storage.

The important control is that only authorized users may upload or replace a public artifact. Unauthorized users must not be able to publish material that could appear to represent the current position or work of AIRA or another participating group.

## Private Topic Spaces

Private Topic Spaces are excluded from normal public presentation and are intended for a smaller selected audience. In this system, **private does not mean confidential**.

The privacy mechanism for uploaded artifacts is an unlisted, high-entropy URL that is not reasonably guessable. InteropHub provides that URL only through pages available to the intended audience. Blob Storage does not separately authorize each person who follows the URL.

Accordingly:

- Anyone who possesses the artifact URL can access the artifact.
- A recipient can copy or forward the URL.
- The URL may remain in browser history, logs, messages, or other systems.
- InteropHub cannot guarantee that the artifact will never be discovered or redistributed.
- InteropHub does not revoke access separately for each recipient.
- Accessing the same stable URL returns the current content assigned to that artifact slot.

This provides reasonable privacy during normal operation by preventing casual discovery and public presentation. It is a communication curtain, not a security wall.

The model is appropriate only because content in a Private Topic Space must still be safe for accidental disclosure. For example, an internal strategy idea might cause confusion if presented publicly as an adopted AIRA position, but its unintended discovery would not itself create material harm.

## Meaning of “Private”

InteropHub currently uses the product terms **Public Topic Space** and **Private Topic Space**. These terms may remain, but the meaning of private must be explicit:

> Private means excluded from public presentation and normally available only to the selected audience. It does not mean confidential, secure against determined discovery, or appropriate for sensitive information.

Authentication controls access to the Private Topic Space interface. The unlisted URL provides the only privacy boundary for the artifact after the URL has been issued.

## Security Priorities

InteropHub security should prioritize integrity and authorized presentation over high-assurance confidentiality.

### Required controls

- Authenticate users before allowing uploads or replacements.
- Authorize upload and replacement operations according to the applicable Topic Space and administrative role.
- Generate artifact locations and object keys within the application; users must not choose arbitrary Blob keys.
- Use high-entropy, non-guessable identifiers for Private Topic Space artifact URLs.
- Limit each topic to its predefined artifact slots.
- Validate file type, content type, filename, and file size.
- Prevent executable or active content from creating a cross-site scripting or malware-distribution risk.
- Protect Blob upload credentials from unauthorized users.
- Limit upload authorization to the intended object and operation when practical.
- Record who uploaded or replaced an artifact and when.
- Make the Topic Space context and status clear so material is not mistaken for a broader formal position.
- Prevent public pages from displaying or linking to Private Topic Space artifacts through ordinary application behavior.

### Not required

- Per-user authorization for every download
- A newly signed download URL for every request
- Guaranteed revocation after a recipient has obtained a URL
- Prevention of copying or forwarding
- Digital rights management
- Per-download audit records
- Confidentiality suitable for sensitive or regulated information
- Application-level version history or rollback
- Permanent preservation of uploaded files

## Upload and Download Model

### Downloads and display

Artifacts should be read directly from Azure Blob Storage rather than routed through Tomcat. This is especially important for large files and video because it avoids making the application server a data-transfer intermediary.

- Public artifacts use stable, directly readable Blob URLs.
- Private artifacts use stable, directly readable, non-guessable Blob URLs.
- InteropHub pages disclose Private artifact URLs only to users who can access the applicable Private Topic Space.
- Individual download requests are not separately checked or signed.

Blob containers and access settings should prevent anonymous enumeration when possible. Preventing listing is useful even though possession of an artifact URL is sufficient for reading it.

### Uploads and replacements

Uploads present a different security risk from downloads. Unauthorized uploads could deface the system, misrepresent organizational positions, distribute harmful files, consume storage, or replace valid information.

Therefore:

1. The user must authenticate to InteropHub.
2. InteropHub must verify that the user may manage the applicable topic and artifact slot.
3. InteropHub generates or requests narrowly scoped upload authorization for the predetermined Blob object.
4. The browser may upload directly to Blob Storage using a temporary SAS limited to the intended upload.
5. InteropHub confirms the completed upload and updates the slot metadata.

The upload SAS is an implementation mechanism for transferring the file. It does not define who is authorized to manage topic content; InteropHub makes that decision before issuing it.

## Stable Artifact Locations and Replacement

Each topic artifact slot should have a stable logical location. Replacing an artifact updates the content at that location instead of creating a new user-facing artifact history.

Consequences of this design include:

- Existing links continue to work and return the current content.
- InteropHub presents only the current artifact.
- The previous artifact is not recoverable through the InteropHub interface.
- If an administrator uploads an incorrect file, that administrator must obtain and upload the correct file again.
- Azure soft delete or infrastructure-level versioning may be enabled for operational recovery, but InteropHub does not expose or guarantee it.

Caching must be configured so that clients revalidate stable URLs and do not continue presenting obsolete content indefinitely. Blob ETags and appropriate `Cache-Control` headers should be used.

## Storage Responsibility

InteropHub is responsible for reasonable uptime and continuity of its current presentation. It is not the authoritative or permanent home of uploaded documents.

Artifact owners remain responsible for retaining authoritative copies elsewhere. InteropHub makes no promise to preserve an overwritten or incorrectly replaced artifact.

The absence of version management is intentional. It keeps InteropHub focused on communicating the current state of each topic instead of becoming another document repository.

## Uploader Acknowledgment

Before uploading or replacing an artifact, the interface should communicate substantially the following:

> InteropHub is not a confidential or permanent document repository. Upload only current materials that are appropriate for the selected Topic Space and whose accidental disclosure would not cause material harm. Private Topic Space artifacts are protected from normal public discovery by an unlisted URL, but anyone who obtains that URL may access or share the file. Keep the authoritative copy and any prior versions in the appropriate document-management system.

The user should affirm this statement as part of the upload process. The acknowledgment should be clear but not burdensome; it exists to establish the system boundary, not to create the appearance of high-assurance security.

## Summary Principle

> InteropHub controls the bulletin board: who may post, what current artifact occupies each predefined slot, and whether the topic is normally presented publicly or to a selected audience. It does not guarantee confidentiality, preserve document history, or replace an authoritative file repository.
