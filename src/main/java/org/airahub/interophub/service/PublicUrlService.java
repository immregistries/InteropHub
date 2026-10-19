package org.airahub.interophub.service;

import java.net.URI;
import org.airahub.interophub.dao.HubSettingDao;
import org.airahub.interophub.model.HubSetting;

public class PublicUrlService {

    private static final String DEFAULT_EXTERNAL_BASE_URL = "http://localhost:8080/hub";

    /**
     * Cached environment determination.
     *
     * <p>The value is a deployment property rather than request state: it
     * changes only when an administrator edits {@code hub_settings}, and a
     * restart is already required for the other deployment settings. Reading it
     * per request added a database query to every page render and to every
     * upload check.
     *
     * <p>A failed lookup is never cached, so a transient database error cannot
     * permanently pin the application into development mode.
     */
    private static volatile Boolean cachedLocalhostMode;

    private final HubSettingDao hubSettingDao;

    public PublicUrlService() {
        this(new HubSettingDao());
    }

    PublicUrlService(HubSettingDao hubSettingDao) {
        this.hubSettingDao = hubSettingDao;
    }

    public String resolveExternalUrl(String internalPath) {
        String normalizedPath = normalizeInternalPath(internalPath);
        String baseUrl = resolveExternalBaseUrl();
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + normalizedPath;
        }
        return baseUrl + normalizedPath;
    }

    public String normalizeInternalPath(String internalPath) {
        return normalizeInternalPath(internalPath, false);
    }

    public String normalizeInternalPath(String internalPath, boolean allowExternalUrl) {
        if (internalPath == null) {
            throw new IllegalArgumentException("Target path is required.");
        }

        String value = internalPath.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Target path is required.");
        }
        if (value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("Target path is invalid.");
        }

        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Target path is invalid.", ex);
        }

        if (uri.isAbsolute()) {
            if (allowExternalUrl) {
                return value;
            }
            throw new IllegalArgumentException("Target path must be an internal application path.");
        }
        if (!value.startsWith("/") || value.startsWith("//")) {
            throw new IllegalArgumentException("Target path must begin with a single '/'.");
        }
        if (uri.getPath() == null || uri.getPath().isBlank()) {
            throw new IllegalArgumentException("Target path is invalid.");
        }
        return value;
    }

    public String resolveExternalBaseUrl() {
        return hubSettingDao.findActive()
                .or(() -> hubSettingDao.findFirst())
                .map(HubSetting::getExternalBaseUrl)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_EXTERNAL_BASE_URL);
    }

    public boolean isLocalhostMode() {
        Boolean cached = cachedLocalhostMode;
        if (cached != null) {
            return cached;
        }
        boolean resolved = resolveLocalhostMode();
        cachedLocalhostMode = resolved;
        return resolved;
    }

    private boolean resolveLocalhostMode() {
        String url = resolveExternalBaseUrl();
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Discards the cached environment determination. Only tests should call
     * this; the cached value is otherwise stable for the life of the process.
     */
    static void clearLocalhostModeCache() {
        cachedLocalhostMode = null;
    }
}