package org.airahub.interophub.config;

import java.net.URI;
import java.nio.file.Path;
import java.util.logging.Logger;

/** Deployment settings, independent of the backend recorded on individual files. */
public final class ArtifactStorageConfig {
    private static final Logger LOGGER = Logger.getLogger(ArtifactStorageConfig.class.getName());
    private static final ArtifactStorageConfig INSTANCE = new ArtifactStorageConfig(
            System.getenv("INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY"),
            System.getenv("HUB_ARTIFACTS_BLOB_ENDPOINT"),
            System.getenv("HUB_ARTIFACTS_CONTAINER"),
            System.getenv("HUB_ARTIFACTS_SAS_TOKEN"));

    private final String localDirectory;
    private final String blobEndpoint;
    private final String container;
    private final String sasToken;

    public ArtifactStorageConfig(String localDirectory, String blobEndpoint, String container, String sasToken) {
        this.localDirectory = trim(localDirectory);
        this.blobEndpoint = trim(blobEndpoint) == null
                ? "https://testsabbiastorage.blob.core.windows.net" : trim(blobEndpoint).replaceAll("/+$", "");
        this.container = trim(container) == null ? "artifacts" : trim(container);
        String token = trim(sasToken);
        this.sasToken = token != null && token.startsWith("?") ? token.substring(1) : token;
        if (this.localDirectory == null) {
            LOGGER.info("INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY is not set; local file storage is disabled.");
        }
    }

    public static ArtifactStorageConfig current() { return INSTANCE; }
    public boolean hasLocalDirectory() { return localDirectory != null; }

    public Path localRoot() {
        if (localDirectory == null) {
            throw new IllegalStateException("Local storage is disabled. Set INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY.");
        }
        Path root = Path.of(localDirectory);
        if (!root.isAbsolute()) {
            throw new IllegalStateException("INTEROPHUB_ARTIFACTS_LOCAL_DIRECTORY must be an absolute directory.");
        }
        return root.normalize();
    }

    public String blobEndpoint() { return blobEndpoint; }
    public String container() { return container; }
    /** Server-only credential; never log or return to a browser. */
    public String sasToken() { return sasToken; }
    public boolean hasBlobCredential() { return sasToken != null && !sasToken.isBlank(); }

    public static String blobReadUrl(String endpoint, String container, String key) {
        validateBlobLocation(endpoint, container);
        validateKey(key);
        return endpoint.replaceAll("/+$", "") + "/" + container + "/" + key;
    }

    public static void validateBlobLocation(String endpoint, String container) {
        URI uri;
        try {
            uri = URI.create(endpoint == null ? "" : endpoint);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid Blob endpoint.", ex);
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                || !uri.getHost().endsWith(".blob.core.windows.net")
                || uri.getUserInfo() != null || uri.getPort() != -1
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                || container == null || !container.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")) {
            throw new IllegalStateException("Expected an HTTPS Azure Blob endpoint and valid container name.");
        }
    }

    public static void validateKey(String key) {
        if (key == null || !key.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
            throw new IllegalArgumentException("Invalid opaque file identifier.");
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
