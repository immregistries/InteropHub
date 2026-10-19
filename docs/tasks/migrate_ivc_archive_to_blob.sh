#!/usr/bin/env bash
#
# Move the IVC historical-meeting attachments from the local artifact
# directory to Azure Blob, then report the SQL needed to flip the
# hub_stored_file rows.
#
# The 57 files were staged on the server as bare storage_key UUIDs, with no
# extension. docs/tasks/ivc-historical-meetings-files.tsv is the oracle: its
# storage_key column is the server filename, and its sha256 column is the
# expected content hash.
#
# Every upload is verified against that hash before the row is flipped, and
# the file is only removed from local storage after the Blob object has been
# read back and re-hashed. Nothing is deleted that has not been proven
# readable from Blob.
#
# Usage:
#   ./migrate_ivc_archive_to_blob.sh --manifest <tsv> --local-dir <dir> [--apply-sql]
#
#   --manifest <tsv>     the archive manifest (required)
#   --local-dir <dir>    INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY (required)
#   --apply-sql          run the row UPDATE through the mysql client, and
#                        then remove the migrated local files
#   --endpoint <url>     Blob endpoint   (default: the production endpoint)
#   --container <name>   Blob container  (default: artifacts)
#   --dry-run            report what would be done, upload nothing
#
# Local files are kept until the row UPDATE has been applied, so an
# interrupted run never leaves a row pointing at a file that is gone.
#
# Authentication is the host's system-assigned managed identity via IMDS, the
# same credential the application uses. There is no SAS and no account key.

set -euo pipefail

MANIFEST=""
LOCAL_DIR=""
APPLY_SQL=0
DRY_RUN=0
ENDPOINT="https://testsabbiastorage.blob.core.windows.net"
CONTAINER="artifacts"

while [ $# -gt 0 ]; do
    case "$1" in
        --manifest) MANIFEST="$2"; shift 2 ;;
        --local-dir) LOCAL_DIR="$2"; shift 2 ;;
        --apply-sql) APPLY_SQL=1; shift ;;
        --endpoint) ENDPOINT="$2"; shift 2 ;;
        --container) CONTAINER="$2"; shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
        *) echo "Unknown option: $1" >&2; exit 2 ;;
    esac
done

if [ -z "$MANIFEST" ] || [ -z "$LOCAL_DIR" ]; then
    echo "Both --manifest and --local-dir are required." >&2
    exit 2
fi
if [ ! -f "$MANIFEST" ]; then
    echo "Manifest not found: $MANIFEST" >&2
    exit 2
fi
if [ ! -d "$LOCAL_DIR" ]; then
    echo "Local artifact directory not found: $LOCAL_DIR" >&2
    exit 2
fi

# Column positions in the manifest: filename(5) content_type(6) size(7)
# public_id(10) storage_key(11) sha256(12).
COL_FILENAME=5
COL_CONTENT_TYPE=6
COL_SIZE=7
COL_PUBLIC_ID=10
COL_STORAGE_KEY=11
COL_SHA256=12

TOKEN=""

# Every request to the Blob service must state the service version. Without
# it the service falls back to a version that predates OAuth support and
# answers a managed-identity request with "Authentication scheme Bearer is not
# supported in this version" (HTTP 403) even when the grant is correct.
BLOB_VERSION="2021-08-06"

get_token() {
    if [ -z "$TOKEN" ]; then
        TOKEN=$(curl -s -H "Metadata: true" \
            "http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https://storage.azure.com/" \
            | python3 -c "
import sys, json
try:
    print(json.load(sys.stdin)['access_token'])
except Exception:
    sys.exit(1)
" 2>/dev/null) || {
            echo "Could not mint a managed-identity token from IMDS." >&2
            echo "Check that the host has a system-assigned identity and that IMDS is reachable:" >&2
            echo "  curl -s -H 'Metadata: true' 'http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https://storage.azure.com/'" >&2
            exit 1
        }
        if [ -z "$TOKEN" ]; then
            echo "IMDS returned an empty access token." >&2
            exit 1
        fi
    fi
    printf '%s' "$TOKEN"
}

# Proves the identity can write to the container before any file is uploaded,
# and reports the service's own error text when it cannot. A bare status code
# is not enough to act on: 403 covers both an authorization failure and a
# token the service will not accept.
verify_write_access() {
    local probe_url="$ENDPOINT/$CONTAINER/__migrationprobe"
    local body
    body=$(mktemp)

    local code
    code=$(curl -s -o "$body" -w "%{http_code}" -X PUT \
        -H "Authorization: Bearer $(get_token)" \
        -H "x-ms-blob-type: BlockBlob" \
        -H "x-ms-version: $BLOB_VERSION" \
        -H "x-ms-blob-content-type: text/plain" \
        -H "x-ms-blob-cache-control: no-cache" \
        --data-binary "probe" \
        "$probe_url")

    if [ "$code" != "201" ]; then
        echo "The managed identity cannot write to $ENDPOINT/$CONTAINER (HTTP $code)." >&2
        sed -n '1,10p' "$body" >&2
        echo >&2
        echo "The role assignment must be Storage Blob Data Contributor for this" >&2
        echo "identity on this container, and the identity must be the one the" >&2
        echo "host actually uses. A stale principal (a replaced VM) produces the" >&2
        echo "same 403." >&2
        rm -f "$body"
        exit 1
    fi

    # Confirm the object is anonymously readable too. A Blob-hosted file is
    # served by redirecting the browser to the object, so an object that is
    # not publicly readable is not readable through InteropHub.
    local anon
    anon=$(curl -s -o /dev/null -w "%{http_code}" "$probe_url")
    curl -s -o /dev/null -X DELETE \
        -H "Authorization: Bearer $(get_token)" \
        -H "x-ms-version: $BLOB_VERSION" \
        "$probe_url" >/dev/null 2>&1 || true
    rm -f "$body"

    if [ "$anon" != "200" ]; then
        echo "Warning: the container accepted the write, but an anonymous read of the" >&2
        echo "object returned $anon. Files served from Blob are reached by redirecting" >&2
        echo "the browser to the object, so they will not be readable until the" >&2
        echo "container allows anonymous read (public access: blob)." >&2
    fi
    echo "Write access confirmed; anonymous read returns $anon."
}

# Reproduces StoredFileService.contentDisposition() exactly: an ASCII
# fallback name plus a UTF-8 encoded form. The application sets this header
# on the Blob object at upload time, and a Blob-hosted file is served by
# redirecting to the object, so the header must be set here or the browser
# sees a bare UUID instead of the original filename.
content_disposition() {
    python3 - "$1" <<'PY'
import sys, urllib.parse
filename = sys.argv[1]
ascii_name = "".join(
    c if (0x20 <= ord(c) <= 0x7E and c not in '"\\') else "_"
    for c in filename
)
encoded = urllib.parse.quote(filename, safe="").replace("+", "%20")
print('attachment; filename="%s"; filename*=UTF-8\'\'%s' % (ascii_name, encoded))
PY
}

sha256_of() {
    sha256sum "$1" | cut -d' ' -f1
}

uploaded=0
verified=0
skipped=0
failed=0
rows=()

total=$(awk -F'\t' 'NR>1' "$MANIFEST" | wc -l)
echo "Migrating $total archive files from $LOCAL_DIR to $ENDPOINT/$CONTAINER"
echo

if [ "$DRY_RUN" -eq 1 ]; then
    echo "Dry run: checking the local files only; nothing will be uploaded."
else
    verify_write_access
    echo
fi

while IFS=$'\t' read -r date kind order source filename content_type size_bytes staged_size size_exception public_id storage_key sha256 staged_sha256; do
    [ "$date" = "date" ] && continue
    [ -z "$storage_key" ] && continue

    path="$LOCAL_DIR/$storage_key"

    if [ ! -f "$path" ]; then
        echo "MISSING  $filename (no file at $path)"
        failed=$((failed + 1))
        continue
    fi

    actual_size=$(stat -c%s "$path")
    if [ "$actual_size" != "$staged_size" ]; then
        echo "SIZE      $filename (server has $actual_size bytes, manifest says $staged_size)"
        failed=$((failed + 1))
        continue
    fi

    actual_sha=$(sha256_of "$path")
    if [ "$actual_sha" != "$sha256" ]; then
        echo "HASH      $filename (server file does not match the manifest; not uploading)"
        failed=$((failed + 1))
        continue
    fi

    disposition=$(content_disposition "$filename")

    if [ "$DRY_RUN" -eq 1 ]; then
        echo "DRY       $filename -> $storage_key ($staged_size bytes)"
        skipped=$((skipped + 1))
        continue
    fi

    token=$(get_token)
    url="$ENDPOINT/$CONTAINER/$storage_key"
    code=$(curl -s -o /tmp/blob_upload.out -w "%{http_code}" -X PUT \
        -H "Authorization: Bearer $token" \
        -H "x-ms-blob-type: BlockBlob" \
        -H "x-ms-version: $BLOB_VERSION" \
        -H "x-ms-blob-content-type: $content_type" \
        -H "x-ms-blob-content-disposition: $disposition" \
        -H "x-ms-blob-cache-control: no-cache" \
        --data-binary "@$path" \
        "$url")

    if [ "$code" != "201" ]; then
        echo "UPLOAD    $filename (HTTP $code)"
        sed -n '1,5p' /tmp/blob_upload.out >&2 || true
        failed=$((failed + 1))
        continue
    fi
    uploaded=$((uploaded + 1))

    # Read the object back and confirm it is the bytes we intended, and that
    # it is anonymously readable. A Blob-hosted file is served by redirecting
    # the browser to the object URL, so an object that is not anonymously
    # readable is not readable through InteropHub.
    readback=$(mktemp)
    anon_code=$(curl -s -o "$readback" -w "%{http_code}" \
        -H "x-ms-version: $BLOB_VERSION" "$url")
    if [ "$anon_code" != "200" ]; then
        echo "READBACK  $filename (anonymous GET returned $anon_code; object not publicly readable)"
        rm -f "$readback"
        failed=$((failed + 1))
        continue
    fi
    readback_sha=$(sha256_of "$readback")
    rm -f "$readback"
    if [ "$readback_sha" != "$sha256" ]; then
        echo "READBACK  $filename (downloaded object does not match the manifest hash)"
        failed=$((failed + 1))
        continue
    fi
    verified=$((verified + 1))

    # The local copy is kept until the row update has been applied and
    # checked. Deleting it before that would leave a file that no row
    # points at, with the row still claiming LOCAL.
    rows+=("$public_id|$storage_key")
    printf "OK        %-60s %s\n" "$filename" "$storage_key"
done < <(tail -n +2 "$MANIFEST")

echo
echo "uploaded=$uploaded verified=$verified skipped=$skipped failed=$failed"

if [ "$DRY_RUN" -eq 1 ]; then
    echo "Dry run: nothing was uploaded, verified, or deleted."
    exit 0
fi

if [ "$failed" -ne 0 ]; then
    echo
    echo "Some files did not migrate. No rows were flipped. Fix the failures and re-run;" >&2
    echo "already-migrated files are simply re-uploaded, which is harmless." >&2
    exit 1
fi

# Row updates. public_id is the stable URL identifier and is left alone;
# storage_key, storage_backend and the Blob location are what change.
sql_file=$(mktemp)
{
    echo "START TRANSACTION;"
    for row in "${rows[@]}"; do
        public_id="${row%%|*}"
        storage_key="${row##*|}"
        printf "UPDATE hub_stored_file SET storage_backend='BLOB', storage_key='%s', blob_endpoint='%s', blob_container='%s' WHERE public_id='%s' AND storage_backend='LOCAL';\n" \
            "$storage_key" "$ENDPOINT" "$CONTAINER" "$public_id"
    done
    echo "COMMIT;"
} > "$sql_file"

echo
echo "SQL written to $sql_file"
if [ "$APPLY_SQL" -eq 1 ]; then
    echo "Applying with the mysql client..."
    mysql --default-character-set=utf8mb4 -u "${INTEROPHUB_DB_USER:-root}" -p"${INTEROPHUB_DB_PASSWORD:-}" interophub < "$sql_file"
    echo "Applied."
else
    echo "Re-run with --apply-sql to apply it, or review and run it yourself."
    echo
    echo "The local files have been kept. Remove them only after the rows are"
    echo "flipped and a download has been checked through InteropHub."
    exit 0
fi

# The rows now point at Blob, so the local copies are redundant. This runs
# only after the update succeeded, and it only removes the exact files this
# run migrated.
if [ "${KEEP_LOCAL:-0}" != "1" ]; then
    echo
    echo "Removing the migrated local files..."
    removed=0
    for row in "${rows[@]}"; do
        storage_key="${row##*|}"
        if [ -f "$LOCAL_DIR/$storage_key" ]; then
            rm -f "$LOCAL_DIR/$storage_key" && removed=$((removed + 1))
        fi
    done
    echo "removed $removed local files from $LOCAL_DIR"
fi
