# Azure Blob Storage Handoff Reaction

## Overall Reaction

The Azure Blob Storage service described in the handoff provides most of the infrastructure needed for the InteropHub MVP. The Blob endpoint, production `artifacts` container, Java SDK dependency, environment-variable configuration, and server-side SAS authentication are all appropriate.

The InteropHub design has been simplified since the original Blob Storage plan. InteropHub will present a small number of current artifacts in fixed topic slots. It will not provide confidential document storage, individualized download authorization, document history, or permanent file preservation.

Under the revised model:

- InteropHub will use only the production `artifacts` container.
- The `artifacts-dev` container and development SAS are not needed.
- Browsers will read artifacts directly from Blob Storage.
- Public artifacts will be openly presented.
- Private Topic Space artifacts will use stable, high-entropy URLs that are not reasonably guessable.
- Anyone possessing an artifact URL will be able to read it.
- Uploads and replacements will pass through Tomcat and use the production SAS held by the server.
- Uploads will be disabled in development mode.
- Browser-direct upload and video upload are deferred.

The handoff is therefore close to what is needed, but its read-access configuration, development setup, and sample code should be revised.

## What Is Good

### Production Blob service

The following production resources are appropriate:

```text
Blob endpoint: https://testsabbiastorage.blob.core.windows.net
Container: artifacts
```

InteropHub can use this single container for both Public and Private Topic Space artifacts because the distinction is based on how the URL is disclosed, not on Azure authorization.

### Environment-based configuration

These environment variables provide a simple production configuration model:

```text
HUB_ARTIFACTS_BLOB_ENDPOINT
HUB_ARTIFACTS_CONTAINER
HUB_ARTIFACTS_SAS_TOKEN
```

The SAS must remain a server-side production secret. It must not be committed to source control, stored in MySQL, written to application logs, or sent to the browser.

### Azure Java SDK

Using `azure-storage-blob` is appropriate. The simplified design does not require `azure-identity`, managed identity, Azure CLI authentication, or user-delegation keys.

### SAS authentication for server-side writes

A container-scoped SAS is a reasonable credential for this low-risk application when held only by production Tomcat. InteropHub will authenticate and authorize the user before using that SAS to upload or replace an artifact.

The SAS should have only the permissions required to create and overwrite Blob content and update its HTTP headers or metadata. The initial implementation does not require container listing or deletion permission.

### Existing CORS configuration

The existing CORS configuration does no harm. However, it is not required for the MVP because uploads will pass through Tomcat and normal Blob display and download will use direct browser requests.

## Required Infrastructure Change

### Allow anonymous read by exact Blob URL without container listing

The original handoff appears to assume that the SAS will also be used for reads. The revised design requires content in the `artifacts` container to be readable when the exact Blob URL is known, without requiring a SAS on each read.

The `artifacts` container should therefore allow anonymous **Blob-level** read access while anonymous container listing remains disabled.

The intended behavior is:

- A browser with the exact Blob URL can read the object.
- A browser cannot request a listing of the container.
- Public artifact URLs may be openly published.
- Private Topic Space artifact URLs are disclosed only through authenticated InteropHub pages.
- Private object keys contain stable, high-entropy random identifiers and cannot reasonably be guessed.

This is an intentional low-assurance privacy model. Private Topic Space artifacts must not contain information whose accidental disclosure would cause material harm.

If Azure policy prohibits anonymous Blob-level access, that should be identified now because it would prevent this simplified model. The alternatives would be expiring read SAS URLs or proxying downloads through Tomcat, neither of which is desired.

## Development Environment

Development does not require a writable Blob environment.

The local application receives a current copy of the production database. It can use the production object keys and the anonymously readable `artifacts` container to render pages with the same images and documents used in production.

In development mode:

- No SAS token is provided.
- Upload, replacement, and removal operations are disabled.
- Blob write controls should be hidden or visibly disabled.
- Server-side upload endpoints must reject write requests even if called directly.
- The application continues to construct and display production Blob URLs for read-only presentation testing.

The application already knows whether it is running in development mode. The intended rule should be:

```text
Development mode -> Blob writes always disabled
Production mode with SAS -> Blob writes enabled for authorized users
Production mode without SAS -> Blob writes disabled and configuration warning logged
```

A missing SAS must not by itself determine that the application is in development mode. Otherwise, a production configuration error could silently appear to be an intentional development configuration.

### `artifacts-dev` is no longer required

InteropHub does not need:

- The `artifacts-dev` container
- A development SAS
- Daily development-token generation or delivery
- Local Blob uploads
- Synchronization of artifacts between development and production

The existing `artifacts-dev` container may remain in Azure if it is useful for another purpose, but InteropHub will not depend on or use it for the MVP.

Upload integration will be verified directly in production during the initial implementation. Current traffic is low, deployment can be corrected quickly, and uploads are infrequent and low consequence. A separate test environment can be introduced later if traffic or operational risk justifies it.

## Changes Needed in the Handoff Examples

### Remove the development SAS setup

The developer setup should no longer instruct developers to obtain or configure:

```text
HUB_ARTIFACTS_SAS_TOKEN
```

Local development needs only the public Blob endpoint and production container information required to construct read URLs:

```text
HUB_ARTIFACTS_BLOB_ENDPOINT=https://testsabbiastorage.blob.core.windows.net
HUB_ARTIFACTS_CONTAINER=artifacts
```

### Remove server-side download proxying

The example `download()` method reads the complete Blob through Java into a `ByteArrayOutputStream`. That is not needed and should not become part of the InteropHub design.

InteropHub will construct the stable Blob URL and place that URL in the applicable page. The browser will retrieve the artifact directly from Blob Storage.

### Remove per-request read SAS generation

The `generateSasUrl()` example is no longer required. Read URLs will be stable and will not be individually signed.

In addition, an existing SAS credential generally cannot be used to generate another SAS. Generating a new SAS normally requires a shared-key signing credential or a user-delegation key obtained through an Azure identity. The example should not imply that a `BlobClient` authenticated only with `HUB_ARTIFACTS_SAS_TOKEN` can mint narrower SAS tokens.

### Remove browser-direct upload SAS generation from the MVP

The browser-direct upload example should be removed or clearly marked as deferred. The initial production flow will be:

1. The browser uploads the file to an authenticated InteropHub endpoint.
2. InteropHub verifies that the user may manage the topic and artifact slot.
3. Tomcat streams the upload to the stable Blob object using the server-side SAS.
4. InteropHub records the filename, content type, size, uploader, and upload time.

The SAS must never be returned to the browser.

### Stream uploads instead of buffering them

The sample `upload(String blobName, byte[] data)` method requires the complete file to be held in memory. The implementation should instead accept an `InputStream` and known content length and stream the request to Azure Blob Storage.

The MVP will initially support ordinary documents and images rather than video. File-size limits should still be enforced by InteropHub and Tomcat. Direct browser upload can be reconsidered later if large video uploads become a requirement.

### Set Blob HTTP headers

Uploads and replacements should set at least:

- `Content-Type`
- `Content-Disposition` when a download filename is needed
- `Cache-Control`

Because Blob URLs remain stable when content is replaced, caching should require revalidation so that users do not continue seeing obsolete content. Blob ETags and a policy such as `Cache-Control: no-cache` can support this behavior.

## Stable Object Model

Each fixed artifact slot should receive one stable, opaque Blob object key when first created. For example:

```text
artifacts/3f841cec-0fd7-49c8-b2d7-8b0a45e28931
```

The key should:

- Be generated by InteropHub, not supplied by the user.
- Contain a high-entropy random value.
- Remain stable when the artifact is replaced.
- Avoid exposing topic names, topic IDs, filenames, or whether the topic is public or private.

The original filename should be retained as metadata rather than used as the storage key. Replacing an artifact overwrites the Blob at the same key. InteropHub does not provide application-level versions or rollback.

## Upload Authorization Boundary

The security-sensitive operation is writing, not reading.

Before uploading or replacing an artifact in production, InteropHub must:

1. Confirm that the application is running in production mode and that the SAS is configured.
2. Authenticate the user.
3. Confirm that the user may manage the applicable Topic Space and topic.
4. Confirm that the requested slot is one of the supported fixed artifact types.
5. Generate or retrieve the object key for that slot.
6. Validate filename, file type, content type, and file size.
7. Stream the file to that exact Blob key.
8. Record the uploader and upload time.

The container SAS gives Tomcat technical permission to write. It does not replace InteropHub's user authorization checks.

## Questions for the Azure Specialist

1. Can the `artifacts` container be configured to allow anonymous read access to an exact Blob URL while preventing anonymous container listing?
2. Is account-level anonymous Blob access currently allowed, or is it disabled by an Azure policy that would need to be changed?
3. What permissions are included in the production SAS? For the MVP, create/write and any permission required to update Blob headers should be sufficient; list and delete are not needed.
4. What is the production SAS expiration period and rotation process?
5. Does the production deployment already expose the endpoint, container name, and SAS environment variables to Tomcat?
6. Is Azure Blob soft delete already enabled? It is optional and would be an infrastructure safeguard only; InteropHub will not expose version recovery.

## Requested Handoff Revision

The handoff should be revised to describe this final MVP model:

- InteropHub uses only the production `artifacts` container.
- `artifacts` is readable by exact Blob URL without permitting container listing.
- Public and Private Topic Space artifacts use stable direct Blob URLs.
- Private artifact privacy depends only on a high-entropy unlisted URL.
- Production Tomcat holds a container-scoped write SAS in an environment variable.
- Authenticated uploads and replacements pass through Tomcat and are streamed to Blob Storage.
- Development uses production Blob URLs for read-only page testing.
- Development mode always disables Blob writes and has no SAS.
- The `artifacts-dev` container and development SAS are not required.
- The application does not proxy reads.
- The application does not generate read SAS URLs.
- The application does not send the production SAS to the browser.
- Browser-direct upload and video upload are deferred.

With these adjustments, the existing Azure service should support a simple InteropHub implementation without introducing a separate development storage environment or a high-assurance access-control system that the application does not need.
