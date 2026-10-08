package org.airahub.interophub.service;

import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import java.io.IOException;
import java.io.InputStream;
import org.airahub.interophub.config.ArtifactStorageConfig;
import java.util.function.BooleanSupplier;

/**
 * Server-side writes to the Azure Blob artifacts container
 * (docs/communication-bundles/artifact-storage-deployment-handoff.md).
 *
 * <p>
 * Stable application URLs redirect browsers to the recorded Blob location.
 */
public class ArtifactBlobStorageService {
    private final ArtifactStorageConfig config;
    private final BooleanSupplier developmentMode;

    public ArtifactBlobStorageService() {
        this(ArtifactStorageConfig.current(), () -> new PublicUrlService().isLocalhostMode());
    }

    public ArtifactBlobStorageService(ArtifactStorageConfig config, BooleanSupplier developmentMode) {
        this.config = config;
        this.developmentMode = developmentMode;
    }

    /**
     * Direct reads must revalidate; application URLs remain stable on replacement.
     */
    private static final String CACHE_CONTROL = "no-cache";

    public boolean isWriteEnabled() {
        return config.hasBlobCredential() && !developmentMode.getAsBoolean();
    }

    /** Anonymous reads use only the validated location recorded on the source file. */
    public InputStream open(String endpoint, String container, String objectKey) throws IOException {
        ArtifactStorageConfig.validateBlobLocation(endpoint, container);
        ArtifactStorageConfig.validateKey(objectKey);
        BlobClient client = new BlobClientBuilder()
                .endpoint(endpoint)
                .containerName(container)
                .blobName(objectKey)
                .buildClient();
        try {
            return client.openInputStream();
        } catch (BlobStorageException ex) {
            throw new IOException("Blob read failed (HTTP " + ex.getStatusCode()
                    + ", " + ex.getErrorCode() + "). Check the recorded public Blob location.");
        }
    }

    /**
     * Streams {@code content} to {@code objectKey}, overwriting whatever is
     * there. Headers are set as part of the upload so the container SAS needs
     * only create/write permission.
     */
    public void store(String objectKey, InputStream content, String contentType, String downloadFilename) {
        store(config.blobEndpoint(), config.container(), objectKey, content, contentType, downloadFilename,
                contentType.startsWith("image/") || contentType.equals("application/pdf"));
    }

    public void store(String endpoint, String container, String objectKey, InputStream content,
            String contentType, String downloadFilename, boolean inline) {
        if (!isWriteEnabled()) {
            throw new IllegalStateException("Blob writes require production mode and a configured write SAS.");
        }
        ArtifactStorageConfig.validateBlobLocation(endpoint, container);
        ArtifactStorageConfig.validateKey(objectKey);
        if (!endpoint.equals(config.blobEndpoint()) || !container.equals(config.container())) {
            throw new IllegalStateException("No write credential is configured for this file's Blob location.");
        }

        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(contentType)
                .setCacheControl(CACHE_CONTROL);
        if (downloadFilename != null && !downloadFilename.isBlank()) {
            headers.setContentDisposition(StoredFileService.contentDisposition(downloadFilename, inline));
        }

        BlobClient blobClient = new BlobClientBuilder()
                .endpoint(endpoint)
                .sasToken(config.sasToken())
                .containerName(container)
                .blobName(objectKey)
                .buildClient();

        try {
            blobClient.uploadWithResponse(
                    new BlobParallelUploadOptions(content).setHeaders(headers), null, Context.NONE);
        } catch (BlobStorageException ex) {
            // Azure exception bodies can contain request URLs; never propagate a SAS into logs.
            throw new IllegalStateException("Blob upload failed (HTTP " + ex.getStatusCode()
                    + ", " + ex.getErrorCode() + "). Check the production credential and Azure configuration.");
        }
    }

}
