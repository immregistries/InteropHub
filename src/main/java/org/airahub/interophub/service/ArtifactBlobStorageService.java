package org.airahub.interophub.service;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.util.Context;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import org.airahub.interophub.config.ArtifactStorageConfig;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server-side writes to the Azure Blob artifacts container
 * (docs/communication-bundles/artifact-storage-deployment-handoff.md).
 *
 * <p>
 * Writes authenticate with the host's system-assigned managed identity, so
 * there is no application secret to configure or rotate. Stable application
 * URLs redirect browsers to the recorded Blob location.
 */
public class ArtifactBlobStorageService {
    private static final Logger LOGGER = Logger.getLogger(ArtifactBlobStorageService.class.getName());

    /** OAuth scope used only to prove the managed identity can mint Storage tokens. */
    private static final String STORAGE_SCOPE = "https://storage.azure.com/.default";
    private static final Duration IDENTITY_PROBE_TIMEOUT = Duration.ofSeconds(10);

    private static final ManagedIdentityCredential CREDENTIAL =
            new ManagedIdentityCredentialBuilder().build();

    /**
     * Cached once proven: a host identity does not appear at runtime. Failures
     * are retried on the next call so a transient IMDS outage does not
     * permanently disable writes.
     */
    private static volatile Boolean identityAvailable;

    private final ArtifactStorageConfig config;
    private final BooleanSupplier developmentMode;
    private final BooleanSupplier identityProbe;

    public ArtifactBlobStorageService() {
        this(ArtifactStorageConfig.current(), () -> new PublicUrlService().isLocalhostMode());
    }

    public ArtifactBlobStorageService(ArtifactStorageConfig config, BooleanSupplier developmentMode) {
        this(config, developmentMode, ArtifactBlobStorageService::probeManagedIdentity);
    }

    ArtifactBlobStorageService(ArtifactStorageConfig config, BooleanSupplier developmentMode,
            BooleanSupplier identityProbe) {
        this.config = config;
        this.developmentMode = developmentMode;
        this.identityProbe = identityProbe;
    }

    /**
     * Direct reads must revalidate; application URLs remain stable on replacement.
     */
    private static final String CACHE_CONTROL = "no-cache";

    /**
     * The host environment, as determined for the write path.
     *
     * <p>{@link #UNKNOWN} means the environment could not be determined. It is
     * reported separately from {@link #DEVELOPMENT} so an operator reading the
     * startup log is told the difference between "this is a development host"
     * and "the check itself failed".
     */
    public enum Environment {
        PRODUCTION, DEVELOPMENT, UNKNOWN
    }

    public boolean isWriteEnabled() {
        return !isDevelopmentMode() && identityProbe.getAsBoolean();
    }

    /**
     * Fail closed: if the environment cannot be determined, the caller is
     * treated as development and writes stay off.
     */
    public boolean isDevelopmentMode() {
        return environment() != Environment.PRODUCTION;
    }

    /**
     * Determines the host environment, reporting a failed check as
     * {@link Environment#UNKNOWN} rather than misreporting it as development.
     */
    public Environment environment() {
        try {
            return developmentMode.getAsBoolean() ? Environment.DEVELOPMENT : Environment.PRODUCTION;
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Development-mode check failed; Blob writes disabled.", ex);
            return Environment.UNKNOWN;
        }
    }

    /**
     * Proves the host managed identity can mint Storage tokens. Skipped
     * automatically on development machines because {@link #isWriteEnabled()}
     * short-circuits first; only production hosts ever reach IMDS.
     */
    static boolean probeManagedIdentity() {
        if (Boolean.TRUE.equals(identityAvailable)) {
            return true;
        }
        boolean available = fetchStorageToken();
        if (available) {
            identityAvailable = Boolean.TRUE;
        }
        return available;
    }

    private static boolean fetchStorageToken() {
        try {
            AccessToken token = CREDENTIAL
                    .getToken(new TokenRequestContext().addScopes(STORAGE_SCOPE))
                    .block(IDENTITY_PROBE_TIMEOUT);
            return token != null;
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Managed identity token unavailable; Blob writes disabled.", ex);
            return false;
        }
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
     * there. Headers are set as part of the upload so the managed identity
     * needs only create/write permission (Storage Blob Data Contributor).
     */
    public void store(String objectKey, InputStream content, String contentType, String downloadFilename) {
        store(config.blobEndpoint(), config.container(), objectKey, content, contentType, downloadFilename,
                contentType.startsWith("image/") || contentType.equals("application/pdf"));
    }

    public void store(String endpoint, String container, String objectKey, InputStream content,
            String contentType, String downloadFilename, boolean inline) {
        if (!isWriteEnabled()) {
            throw new IllegalStateException(
                    "Blob writes require production mode and an available managed identity.");
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
                .credential(CREDENTIAL)
                .containerName(container)
                .blobName(objectKey)
                .buildClient();

        try {
            blobClient.uploadWithResponse(
                    new BlobParallelUploadOptions(content).setHeaders(headers), null, Context.NONE);
        } catch (BlobStorageException ex) {
            // Azure exception bodies can contain request URLs; never propagate credentials into logs.
            throw new IllegalStateException("Blob upload failed (HTTP " + ex.getStatusCode()
                    + ", " + ex.getErrorCode()
                    + "). Check the managed identity role assignment and Azure configuration.");
        }
    }

}
