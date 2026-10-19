#!/usr/bin/env bash
#
# Diagnose whether the host's managed identity can write to the Blob
# container. Run on the VM that hosts InteropHub.
#
# Every request to the Blob service must carry an x-ms-version header.
# Without it the service falls back to a version that predates OAuth
# support and answers a managed-identity request with HTTP 403 and
# "Authentication scheme Bearer is not supported in this version" — even
# when the role assignment is correct. This script sends the header, and
# also shows what happens without it, so that cause is not mistaken for a
# permissions problem.
#
# The application is unaffected: the Azure SDK always sends the version
# header. This only affects hand-written requests such as this one.

set -uo pipefail

ACCOUNT="https://testsabbiastorage.blob.core.windows.net"
CONTAINER="artifacts"
BLOB_VERSION="2021-08-06"

echo "=== 1. IMDS: is the identity endpoint reachable? ==="
curl -s -H "Metadata: true" \
  "http://169.254.169.254/metadata/identity/info?api-version=2019-08-01" | head -c 600
echo; echo

echo "=== 2. IMDS token: mint one for the storage audience ==="
TOKEN=$(curl -s -H "Metadata: true" \
  "http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https://storage.azure.com/" \
  | python3 -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))")
if [ -z "$TOKEN" ]; then
  echo "NO TOKEN MINTED. The raw IMDS response was:"
  curl -s -H "Metadata: true" \
    "http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https://storage.azure.com/" | head -c 800
  echo; exit 1
fi
echo "token length: ${#TOKEN}"
echo

echo "=== 3. Decode the token: which principal (oid) and audience (aud)? ==="
python3 - "$TOKEN" <<'PY'
import sys, json, base64
t = sys.argv[1]
p = t.split('.')[1]
p += '=' * (-len(p) % 4)
c = json.loads(base64.urlsafe_b64decode(p))
for k in ('oid','tid','aud','iss','exp','xms_mirid'):
    if k in c: print(f"  {k} = {c[k]}")
print("  EXPECTED oid = 7eb7d453-8f61-4d39-90cf-f1e9b8b54dda")
PY
echo

echo "=== 4. MI WRITE with the version header (the request that matters) ==="
code=$(curl -s -o /tmp/mi_write.out -w "%{http_code}" -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "x-ms-blob-type: BlockBlob" \
  -H "x-ms-version: $BLOB_VERSION" \
  --data-binary "probe" \
  "$ACCOUNT/$CONTAINER/__mitest2")
echo "  MI-PUT = $code"
if [ "$code" != "201" ]; then
  head -c 900 /tmp/mi_write.out; echo
  echo "  A 403 here means the identity's write is genuinely refused." >&2
else
  echo "  Write accepted."
fi
echo

echo "=== 5. The same request WITHOUT the version header (for comparison) ==="
code=$(curl -s -o /tmp/mi_write_noversion.out -w "%{http_code}" -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "x-ms-blob-type: BlockBlob" \
  --data-binary "probe" \
  "$ACCOUNT/$CONTAINER/__mitest3")
echo "  MI-PUT (no x-ms-version) = $code"
grep -o 'AuthenticationErrorDetail>[^<]*' /tmp/mi_write_noversion.out 2>/dev/null | head -2
echo "  A 403 here with 'Bearer is not supported in this version' is the"
echo "  service version, not the permission."
echo

echo "=== 6. Anonymous read of the object just written ==="
code=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "x-ms-version: $BLOB_VERSION" "$ACCOUNT/$CONTAINER/__mitest2")
echo "  ANON-GET = $code  (200 means the object is publicly readable)"
echo

echo "=== 7. Clean up the probe objects ==="
for n in __mitest2 __mitest3; do
  curl -s -o /dev/null -X DELETE \
    -H "Authorization: Bearer $TOKEN" \
    -H "x-ms-version: $BLOB_VERSION" \
    "$ACCOUNT/$CONTAINER/$n" >/dev/null 2>&1 && echo "  deleted $n"
done
echo "  (a __mitest object left from an earlier run may still exist)"
