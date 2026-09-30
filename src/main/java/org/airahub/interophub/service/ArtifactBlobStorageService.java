package org.airahub.interophub.service;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import java.io.InputStream;
import org.airahub.interophub.config.ArtifactStorageConfig;

/**
 * Server-side writes to the Azure Blob artifacts container
 * (docs/communication-bundles/artifact-storage-deployment-handoff.md).
 *
 * <p>
 * Reads are never proxied through here - callers put
 * {@link ArtifactStorageConfig#getReadUrl(String)} in the page and the browser
 * fetches the Blob directly.
 */
public class ArtifactBlobStorageService {

    /**
     * Object keys are stable across replacements, so cached copies must revalidate.
     */
    private static final String CACHE_CONTROL = "no-cache";

    public boolean isWriteEnabled() {
        return ArtifactStorageConfig.isWriteEnabled();
    }

    /**
     * Streams {@code content} to {@code objectKey}, overwriting whatever is
     * there. Headers are set as part of the upload so the container SAS needs
     * only create/write permission.
     */
    public void store(String objectKey, InputStream content, String contentType, String downloadFilename) {
        if (!isWriteEnabled()) {
            throw new IllegalStateException("Artifact uploads are disabled: no write SAS is configured.");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Object key is required.");
        }

        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(contentType)
                .setCacheControl(CACHE_CONTROL);
        if (downloadFilename != null && !downloadFilename.isBlank()) {
            headers.setContentDisposition("inline; filename=\"" + sanitizeFilename(downloadFilename) + "\"");
        }

        BlobClient blobClient = new BlobClientBuilder()
                .endpoint(ArtifactStorageConfig.getBlobEndpoint())
                .sasToken(ArtifactStorageConfig.getSasToken())
                .containerName(ArtifactStorageConfig.getContainer())
                .blobName(objectKey)
                .buildClient();

        blobClient.uploadWithResponse(
                new BlobParallelUploadOptions(content).setHeaders(headers), null, Context.NONE);
    }

    /**
     * Keeps quotes and control characters out of the Content-Disposition header.
     */
    private static String sanitizeFilename(String filename) {
        StringBuilder sb = new StringBuilder();
        for (char c : filename.toCharArray()) {
            if (c >= 32 && c < 127 && c != '"' && c != '\\') {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? "download" : sb.toString();
    }
}
