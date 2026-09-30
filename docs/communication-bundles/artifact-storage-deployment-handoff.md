# Artifact Storage Deployment Handoff

Everything needed to deploy and verify **Communication Bundles step 1** — the
Azure Blob storage proof described in
`InteropHub_Communication_Bundles_Implementation_Plan.md` and
`azure-blob-storage-handoff-reaction.md`.

This step adds no user-facing feature. It uploads one image through an admin
page and shows it on `/welcome`, to prove that server-side writes and anonymous
direct browser reads both work in production before bundle features depend on
them.

Audience: Nathan (local setup), Chris (production Tomcat + Azure checks), and
whoever holds Azure access for the storage account.

---

## What was added

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
