# Artifact Storage: Managed Identity Migration

## Status (2026-10-14): Phase 1 complete, Phase 2 implemented

- **Phase 1 done.** Assignment `a2482ee2-46aa-46ad-9447-420c5e93ba5c`:
  `Storage Blob Data Contributor` → VM identity
  `7eb7d453-8f61-4d39-90cf-f1e9b8b54dda`, scope `artifacts` container, created
  by `csorenson@immregistries.org`. Live-verified from `testSabbia`: IMDS token
  (`oid`/`tid`/audience correct), PUT `__mitest` → `201`, DELETE → `202`,
  container left empty. (Early `403`s were a missing `x-ms-date` header in the
  verification curl, not the grant.)
  **2026-10-19:** a hand-written `curl` PUT from `testSabbia` was refused with
  `403` and *"Authentication scheme Bearer is not supported in this version"*.
  The role assignment, the principal, the container scope, and the account
  were all correct, and the same request with an `x-ms-version` header
  returned `201`. The service falls back to a version predating OAuth
  support when that header is absent, so the refusal was the request, not
  the grant. **The application was never affected**: the Azure SDK always
  sends the header, so its writes worked throughout. Only hand-written
  requests need it — `docs/tasks/migrate_ivc_archive_to_blob.sh` and
  `docs/tasks/diagnose_blob_write_access.sh` both send it now, and the
  diagnostic shows the versioned and unversioned requests side by side so
  the two are not confused. The account accepts writes with an account
  key, and an object in the container is anonymously readable (`GET` →
  `200`), so the Blob read model is sound. **Anonymous read of a stored object is
  still unproven in production**: the container was left empty, and the
  `WELCOME_BLOB_DEMO` object the v0.8 migration points at returns `404`. Before
  migrating file content, prove that an uploaded object is anonymously
  readable (`PUT` with the managed identity, then `GET` the same URL without
  an `Authorization` header and expect `200`). The redirect read model
  depends on it.
- **Phase 2 implemented** against the then-current tree (see below for how the
  tree had evolved): `com.azure:azure-identity:1.18.7`,
  `ManagedIdentityCredential` via `ManagedIdentityCredentialBuilder`, cached
  localhost + identity gate with injectable probe, `ArtifactStorageHealthListener`
  boot log line, updated `writeProblem` messaging, tests green (253 pass).
- **Remaining:** Phase 3 cutover (deploy WAR, remove `HUB_ARTIFACTS_SAS_TOKEN`
  from Tomcat env, boot-log + upload verification) and Phase 5 follow-ups
  (`allowSharedKeyAccess: false`, key rotation).

Note: between the original plan and implementation, the application tree moved
on (task 1a/1b: `StoredFileService` with `LOCAL`/`BLOB` backends, injectable
`developmentMode`, `/files/` redirect reads). The SAS-presence write gate this
document originally described no longer existed; Phase 2 therefore kept the new
gate shape and only swapped the credential plus the identity-availability
check. Code details in the Phase 2 section below describe the older tree and
are historical; the implementation commit is authoritative.

Related documents:

- `azure-blob-storage-handoff-reaction.md` — original design; its SAS guidance
  is superseded by this document (see *Phase 4*).
- `artifact-storage-deployment-handoff.md` — the SAS-based deployment runbook;
  Phase 4 replaces its credential sections.
- `azure-blob-storage-handoff.md` — connection info and validated Azure state.

---

## Why

The write SAS worked, but it was a bearer secret in `setenv.sh` with a manual
rotation cycle, and it required `allowSharedKeyAccess: true` to stay enabled.
More importantly it became a load-bearing signal in the application:

```java
// ArtifactStorageConfig.java
public static boolean isWriteEnabled() {
    return SAS_TOKEN != null;
}
```

That line infers the environment from a string's presence. The design in the
reaction document requires three distinct cases:

```
Development mode   -> Blob writes always disabled
Production + SAS   -> writes enabled for authorized users
Production, no SAS -> writes disabled, configuration warning logged
```

Only the last two were implemented. A SAS present on a developer machine
therefore enabled writes to the **production** `artifacts` container, replacing
the live object behind a stable public URL. There is no `artifacts-dev`
container in the design, and a developer's local database is a copy of
production, so the slot row and object key are identical. The failure was
silent.

Under managed identity there is no string to be present or absent. A developer
machine cannot reach IMDS, so writes are off for a real reason, and production
is off only if the identity or its role is genuinely missing — which is a
truthful signal rather than an ambiguous one.

---

## Verified environment

Confirmed read-only against the subscription on 2026-08-12.

| Item | Value |
|---|---|
| Subscription | `39da6898-f77b-4368-9db9-3b708050c734` |
| Tenant | `1c7b3a07-6af4-4e84-80e1-439eebbf7b23` |
| Production VM | `testSabbia` (RG `testSabbia`), Ubuntu 22.04, `Standard_B2s` |
| Production origin | `informatics.immregistries.org` → `20.51.109.147` → `testSabbia-ip` |
| VM managed identity | `SystemAssigned`, principal `7eb7d453-8f61-4d39-90cf-f1e9b8b54dda` |
| Current role assignments | **none** |
| Storage account | `testsabbiastorage` (RG `testSabbia`), `StorageV2` |
| Account public access | `allowBlobPublicAccess: true` |
| Account shared key | `allowSharedKeyAccess: true` |
| Account min TLS | `TLS1_2` |
| Account networking | `publicNetworkAccess: Enabled`, `defaultAction: Allow`, no IP/VNet rules, no private endpoints |
| Container `artifacts` | ACL `blob`, currently empty |
| Container `artifacts-dev` | ACL `null` (private), unused |

The VM name is misleading and worth recording: `AIRA-File-Server` (HubRG) and
`Bergamo` (ProductionRG) are not production. Bergamo is the VM with port 8080
open to `*`, but its address does not match the production origin.
`testSabbia` matches.

Three facts make this migration low-risk:

1. The VM already has a system-assigned identity, so no `az vm identity assign`
   is needed.
2. `allowSharedKeyAccess: false` does **not** affect anonymous blob reads. It
   blocks service SASes only. Phase 5 can therefore harden the account without
   disturbing the read model.
3. The `privatelink.blob.core.windows.net` private DNS zone is linked only to
   HubRG, not to `testSabbia-vnet`, so there is no name-resolution surprise.

---

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Credential | System-assigned managed identity | No secret to store, rotate, or leak. Already present on the VM. No client ID needed in code. |
| Who runs the grant | An account with a role that includes `Microsoft.Authorization/roleAssignments/write` | The current `sorech02@gmail.com` login holds only **Contributor** at subscription scope, which excludes authorization writes. See *Prerequisite*. |
| Role | Built-in `Storage Blob Data Contributor` | Simple, and the extra delete permission is acceptable for a low-traffic MVP. |
| Role scope | The `artifacts` container | `artifacts-dev` and any future container stay unreachable. |
| Write gate | `!isLocalhostMode() && identityAvailable()` | Environment no longer inferred from configuration. |
| `allowSharedKeyAccess: false` | Deferred | Safe to do, but not required for this change. Tracked in Phase 5. |
| RBAC ownership | Chris | Owns role assignments on `testsabbiastorage` once the grant can actually be made. |

---

## Prerequisite — authorization to run Phase 1

The Phase 1 grant requires `Microsoft.Authorization/roleAssignments/write` at
the target scope. The account this migration is being executed with,
`sorech02@gmail.com` (object `af3f48c1-3208-4485-a039-76f9e7c93008`), holds
exactly one role assignment at subscription scope:

```
/subscriptions/39da6898-f77b-4368-9db9-3b708050c734
  -> Contributor  (b24988ac-6180-42a0-ab88-20f7382dd24c)
```

`Contributor` deliberately excludes `Microsoft.Authorization/*` write actions,
so the command fails with `AuthorizationFailed`:

```
The client 'live.com#sorech02@gmail.com' with object id
'af3f48c1-3208-4485-a039-76f9e7c93008' does not have authorization to perform
action 'Microsoft.Authorization/roleAssignments/write' over scope
'.../containers/artifacts'
```

This is an operator-permission gap, **not** a defect in the approach. The VM
identity exists, the container scope is valid, and the role is available in
the tenant. Any one of the following unblocks it:

1. Run the Phase 1 command from an account holding **Owner**, **User Access
   Administrator**, or **Role Based Access Control Administrator** at that
   scope.
2. Elevate `sorech02@gmail.com` to **Role Based Access Control Administrator**
   at subscription or resource-group scope. This is the least-privilege fix:
   it grants `roleAssignments/write` without the broader reach of Owner.

Both `Owner` and `User Access Administrator` were confirmed present in this
tenant's built-in role definitions.

### Getting the grant command right

`az role assignment create` has a failure mode that misreports itself. If
`--assignee-principal-type` is omitted, the CLI queries Microsoft Graph to
determine whether the object ID is a user, group, or service principal. That
Graph call fails, and the CLI then reports:

```
ERROR: Subscription '39da6898-f77d-4368-9db9-3b708050c734' not found.
```

This is misleading. The subscription exists and is `Enabled`; `az account show`
and `az rest` against the subscription both succeed. The real fault is the
missing Graph lookup, and the message sends you auditing the wrong thing.

Working form — note the three requirements:

```bash
az role assignment create \
  --role "<exact role name>" \
  --assignee-object-id <guid> \
  --assignee-principal-type <User|ServicePrincipal> \
  --scope "/subscriptions/39da6898-f77d-4368-9db9-3b708050c734/resourceGroups/testSabbia"
```

1. `--assignee-principal-type` is **mandatory**. Omit it and you get the bogus
   subscription error above.
2. `--scope` must be the **full path** beginning `/subscriptions/<id>/`. The
   relative form `--scope /resourceGroups/testSabbia` is rejected.
3. Do **not** add `--subscription`. Supplying it alongside a full `--scope`
   pushes the command down a resolution path that fails the same way.

Verified against this subscription: with all three correct, the command reaches
ARM and returns the true `AuthorizationFailed` for `roleAssignments/write`,
which is the expected result for a Contributor account.

Related read-only commands that are **not** affected and remain useful for
verifying the result: `az role assignment list --all` and
`az role assignment list --assignee <guid>`. Both work. Prefer those over
`--scope`, which is unreliable in azure-cli 2.90.0 for this subscription.

Role definition GUIDs, verified against this tenant:

| Role | GUID |
|---|---|
| Owner | `8e3af657-a8ff-443c-a75c-2fe8c4bcb635` |
| Role Based Access Control Administrator | `f58310d9-a9f6-439a-9e8d-f62e7b41a168` |
| User Access Administrator | `18d7d88d-d35e-4fb5-a5c3-7773c20a72d9` |
| Storage Blob Data Contributor | `ba92f5b4-2d11-453d-a403-e96b0029c9fe` |

Do not work around this by granting the storage account key to the VM, or by
widening `allowSharedKeyAccess`. Both reintroduce the shared secret this
migration exists to remove.

**Accepted consequence of the built-in role:** it includes delete, whereas the
reaction document specifies that no delete permission is needed. After this
change that becomes a code convention, not an enforced boundary. A custom role
limited to `Microsoft.Storage/storageAccounts/blobServices/containers/blobs/write`
and `/add` would enforce it; that is a deliberate deferral, not an oversight.

---

## Phase 1 — Azure grant (go/no-go gate)

The only Azure change required.

```bash
az role assignment create \
  --role "Storage Blob Data Contributor" \
  --assignee 7eb7d453-8f61-4d39-90cf-f1e9b8b54dda \
  --assignee-principal-type ServicePrincipal \
  --scope /subscriptions/39da6898-f77b-4368-9db9-3b708050c734/resourceGroups/testSabbia/providers/Microsoft.Storage/storageAccounts/testsabbiastorage/blobServices/default/containers/artifacts
```

The identity currently holds no roles, so this grant adds nothing beyond the
intended write access on `artifacts`.

Verify over SSH on the VM that IMDS answers and the grant permits a write:

```bash
TOKEN=$(curl -s -H "Metadata: true" \
  "http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https://storage.azure.com/" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

printf x > /tmp/mi.txt
curl -s -o /dev/null -w "%{http_code}\n" -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "x-ms-blob-type: BlockBlob" \
  -H "Content-Length: 2" \
  --data-binary @/tmp/mi.txt \
  "https://testsabbiastorage.blob.core.windows.net/artifacts/__mitest"
```

Expect `201`. RBAC propagation can take a couple of minutes; a `403` before
propagation completes is expected, a persistent `403` is not.

Clean up the test object:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  "https://testsabbiastorage.blob.core.windows.net/artifacts/__mitest"
```

Expect `202`.

**If IMDS is unreachable or the grant fails, stop here.** Nothing has been
deployed, and the SAS path continues to work.

---

## Phase 2 — Application changes

### 2.1 Dependency

`pom.xml`, alongside the existing `com.azure:azure-storage-blob:12.28.0`:

```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-identity</artifactId>
    <version>1.18.5</version>
</dependency>
```

Transitively pulls msal4j. Build with `mvn clean package` and confirm the WAR
still assembles; Java release is 17.

### 2.2 `ArtifactStorageConfig`

Remove `HUB_ARTIFACTS_SAS_TOKEN`, `getSasToken()`, `normalizeSasToken()`, and
the `static` block that logs the missing-SAS warning. Keep `getBlobEndpoint()`,
`getContainer()`, and `getReadUrl()` unchanged — the read path is unaffected by
this migration.

### 2.3 `ArtifactBlobStorageService`

Authenticate with a credential rather than a token. A system-assigned identity
needs no client ID:

```java
BlobServiceClient service = new BlobServiceClientBuilder()
        .endpoint(ArtifactStorageConfig.getBlobEndpoint())
        .credential(new ManagedIdentityCredential())
        .buildClient();

BlobClient blobClient = service.getBlobContainerClient(ArtifactStorageConfig.getContainer())
        .getBlobClient(objectKey);
```

### 2.4 Write gate

```java
public boolean isWriteEnabled() {
    return !new PublicUrlService().isLocalhostMode() && identityAvailable();
}
```

`isWriteEnabled()` must not infer the environment from configuration. Keep the
POST-handler guard in `AdminEsArtifactTestServlet` as a second layer — it is
defense in depth, not duplication.

### 2.5 Startup log

The removed SAS warning was the only production misconfiguration signal in the
Tomcat log. Replace it with an explicit boot-time statement in a
`ServletContextListener` — the project already has four
(`MeetingCommunicationSchedulerListener`, `MeetingLifecycleSchedulerListener`,
`DandelionSyncSchedulerListener`, `DailyDigestSchedulerListener`). Register the
new one in `web.xml` alongside them, matching existing convention, and log one
line: `artifact write path: enabled` or `disabled (reason)`. That makes the
state greppable in the log and preserves the deploy-time check the SAS warning
provided.

**Implemented.** The listener reports four distinct outcomes, so an operator is
told which one applies: `enabled (managed identity available)`,
`disabled (development mode)`, `disabled (managed identity unavailable)`, and
`disabled (environment check failed)`. The last one matters: a failed
environment check used to be reported as development mode, which pointed an
operator at the wrong cause during exactly the incident the line exists to
explain. `ArtifactBlobStorageService.environment()` now returns
`PRODUCTION`, `DEVELOPMENT`, or `UNKNOWN`, and a failed check still fails
closed — writes stay disabled — but it is no longer mislabelled.

### 2.6 `AdminEsArtifactTestServlet`

The disabled message currently asserts "This is expected on a local machine …
In production this means the environment variable is missing." The page cannot
know which environment it is in, so it guesses from the same wrong signal.
Branch on `isLocalhostMode()` and state the actual reason.

### Implementation cautions

- `PublicUrlService.isLocalhostMode()` reads `hub_settings` from the database.
  Do not call it per request; cache the result in a static initializer, matching
  how `ArtifactStorageConfig` already caches its environment reads. Note the
  fallback: with no `hub_settings` row the method returns `true`, so a
  production instance missing `external_base_url` would report localhost and
  disable writes. That fails safe, but confirm the production value is set.
  **Implemented:** the determination is cached in `PublicUrlService` for the
  life of the process, and a failed read is never cached, so a transient
  database error cannot pin the instance into development mode. The cached
  value does not follow a later `hub_settings` edit; changing
  `external_base_url` requires a Tomcat restart, as the other deployment
  settings do. Production `external_base_url` is
  `https://informatics.immregistries.org/hub`.
- `ManagedIdentityCredential` resolves lazily. On a misconfigured production
  instance the first upload throws rather than being caught by the gate. Phase
  2.5's boot log is what surfaces that; make sure the failure message in the
  servlet mentions the identity and role rather than a SAS.

---

## Phase 3 — Cutover

1. Deploy the WAR.
2. Remove `HUB_ARTIFACTS_SAS_TOKEN` from the Tomcat environment. Remove it —
   do not leave it present but unused.
3. Confirm the boot log reports `artifact write path: enabled`.
4. Open `/welcome` **signed out** and confirm the image still renders.
   Anonymous read is untouched, and this is the check that would catch an
   accidental account-level change.
5. Upload one image through `/admin/es/artifact-test`; replace it; confirm the
   object key and direct Blob URL are unchanged.
6. Confirm writes are disabled locally: the admin page shows the disabled
   notice and `/welcome` still renders the same image.

Keep the existing SAS valid through step 5 so a rollback is a configuration
change rather than a redeploy.

---

## Phase 4 — Documentation updates

| Document | Change |
|---|---|
| `artifact-storage-deployment-handoff.md` | Remove the SAS regeneration section, the `setenv.sh` block, the `HUB_ARTIFACTS_SAS_TOKEN` env-var row, and the "uploads disabled because no SAS is configured" copy. Add the RBAC grant, the VM principal ID, and the boot-log check to the pre-deployment checklist. |
| `azure-blob-storage-handoff.md` | Drop `HUB_ARTIFACTS_SAS_TOKEN` from the env-var table. State that writes use the VM's managed identity with no application secret. |
| `azure-blob-storage-handoff-reaction.md` | Add a short status note recording the move from SAS to managed identity, so the "SAS must remain a server-side production secret" guidance is not read as still binding. |

The reaction document framed the original engagement; leaving its credential
guidance unamended would leave two contradictory models in `docs/`.

---

## Phase 5 — Follow-ups (deliberately not in this change)

- `az storage account update -n testsabbiastorage --resource-group testSabbia --allow-shared-key-access false`
  — safe, because anonymous reads are unaffected. This is what finally
  invalidates the two SAS tokens that were pasted into a chat session during
  validation and remain valid until 2027-01-01.
- Rotate both storage account keys. Both were retrieved during validation.
- Decide on Azure Blob soft delete as an infrastructure-only safeguard.
- Revisit a custom write-only role if delete on `artifacts` ever becomes a real
  concern.

---

## Risks

**Rollback after cutover is a redeploy, not a config flip.** If managed identity
fails at step 3 or 5, the previous WAR plus the previous SAS in the Tomcat
environment is the way back. Sequence the work so the upload path is proven
before the SAS is removed, and keep the SAS valid until step 5 passes.

**RBAC propagation is not instant.** A `403` immediately after the grant is
expected and self-resolving. Do not read it as a failure.

**A misconfigured production instance now fails at first upload**, not at
startup, unless Phase 2.5 is implemented. That step is not optional polish;
it replaces the misconfiguration signal the SAS warning provided.

**A system-assigned identity does not survive VM replacement.** If `testSabbia`
is ever rebuilt or replaced, the principal ID changes and the Phase 1 grant
must be recreated against the new principal before uploads work again. A
user-assigned identity would avoid this, at the cost of a client ID in code;
for now the simple option is the right trade, but record the rebuild step in
whatever runbook covers VM replacement.

**Deleting `artifacts-dev` is not part of this work.** It remains in the
account, private and unused. Removing it is independent cleanup.

---

*Prepared 2026-08-12. Azure state verified read-only; no changes applied by this
document.*
