# Azure Blob Storage — Artifact Management

## Connection info

| Item | Value |
|---|---|
| **Blob endpoint** | `https://testsabbiastorage.blob.core.windows.net` |
| **Container (prod)** | `artifacts` |
| **Container (dev)** | `artifacts-dev` |

CORS is already configured for browser-direct uploads (methods: GET, HEAD, PUT, OPTIONS).

## Authentication

The app authenticates via a container-level SAS token set as an environment variable. Contact DevOps for the current token value.

## Developer setup

### Maven dependency

```xml
<dependency>
    <groupId>com.azure</groupId>
    <artifactId>azure-storage-blob</artifactId>
    <version>12.28.0</version>
</dependency>
```

### Environment variables

```
HUB_ARTIFACTS_BLOB_ENDPOINT=https://testsabbiastorage.blob.core.windows.net
HUB_ARTIFACTS_CONTAINER=artifacts
HUB_ARTIFACTS_SAS_TOKEN=<ask-devops-for-token>
```

### Usage example

```java
import com.azure.storage.blob.*;
import com.azure.storage.blob.models.*;
import java.io.*;

public class ArtifactService {

    private final BlobContainerClient container;

    public ArtifactService() {
        String endpoint = System.getenv("HUB_ARTIFACTS_BLOB_ENDPOINT");
        String containerName = System.getenv("HUB_ARTIFACTS_CONTAINER");
        String sasToken = System.getenv("HUB_ARTIFACTS_SAS_TOKEN");

        BlobServiceClient service = new BlobServiceClientBuilder()
            .endpoint(endpoint)
            .sasToken(sasToken)
            .buildClient();

        this.container = service.getBlobContainerClient(containerName);
    }

    public void upload(String blobName, byte[] data) {
        BlobClient blob = container.getBlobClient(blobName);
        try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
            blob.upload(stream, data.length, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] download(String blobName) {
        BlobClient blob = container.getBlobClient(blobName);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            blob.download(out);
            return out.toByteArray();
        }
    }

    public String generateSasUrl(String blobName, int expiryMinutes) {
        BlobClient blob = container.getBlobClient(blobName);
        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(
            OffsetDateTime.now().plusMinutes(expiryMinutes),
            BlobContainerSasPermission.parse("r")
        );
        return blob.getBlobUrl() + "?" + blob.generateSas(values);
    }
}
```

### Generate per-user short-lived SAS URLs for browser uploads

```java
BlobClient blob = container.getBlobClient("user-upload.pdf");
BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(
    OffsetDateTime.now().plusHours(1),
    BlobContainerSasPermission.parse("cw")  // create + write
);
String sasUrl = blob.getBlobUrl() + "?" + blob.generateSas(values);
```

The frontend can then PUT directly to this URL from the browser (CORS is already configured).
