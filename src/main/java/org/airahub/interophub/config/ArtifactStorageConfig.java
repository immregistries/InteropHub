package org.airahub.interophub.config;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Azure Blob Storage settings for Topic artifacts, read from the environment
 * (docs/communication-bundles/artifact-storage-deployment-handoff.md).
 *
 * <p>
 * Endpoint and container are public values and carry defaults. The write SAS
 * has no default and is never logged or sent to the browser. Writes are enabled
 * only where a SAS is configured, which in practice means production only -
 * reads work everywhere because the container allows anonymous read by exact
 * Blob URL.
 */
public final class ArtifactStorageConfig {

    private static final Logger LOGGER = Logger.getLogger(ArtifactStorageConfig.class.getName());

    private static final String DEFAULT_BLOB_ENDPOINT = "https://testsabbiastorage.blob.core.windows.net";
    private static final String DEFAULT_CONTAINER = "artifacts";

    private static final String BLOB_ENDPOINT = resolve("HUB_ARTIFACTS_BLOB_ENDPOINT", DEFAULT_BLOB_ENDPOINT);
    private static final String CONTAINER = resolve("HUB_ARTIFACTS_CONTAINER", DEFAULT_CONTAINER);
    private static final String SAS_TOKEN = normalizeSasToken(System.getenv("HUB_ARTIFACTS_SAS_TOKEN"));

    static {
        if (SAS_TOKEN == null) {
            LOGGER.log(Level.WARNING, "HUB_ARTIFACTS_SAS_TOKEN is not set - artifact uploads are disabled. "
                    + "This is expected in local development and is a configuration error in production.");
        }
    }

    private ArtifactStorageConfig() {
    }

    public static boolean isWriteEnabled() {
        return SAS_TOKEN != null;
    }

    public static String getBlobEndpoint() {
        return BLOB_ENDPOINT;
    }

    public static String getContainer() {
        return CONTAINER;
    }

    /** Server-side Blob writes only. Never log this or send it to the browser. */
    public static String getSasToken() {
        return SAS_TOKEN;
    }

    /** The stable, anonymously readable URL for an object key. */
    public static String getReadUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return BLOB_ENDPOINT + "/" + CONTAINER + "/" + objectKey;
    }

    private static String resolve(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        value = value.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizeSasToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String token = value.trim();
        return token.startsWith("?") ? token.substring(1) : token;
    }
}
