package org.airahub.interophub.service;

import java.net.URI;
import org.airahub.interophub.dao.HubSettingDao;
import org.airahub.interophub.model.HubSetting;

public class PublicUrlService {

    private static final String DEFAULT_EXTERNAL_BASE_URL = "http://localhost:8080/hub";

    private final HubSettingDao hubSettingDao;

    public PublicUrlService() {
        this.hubSettingDao = new HubSettingDao();
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
        if (internalPath == null) {
            throw new IllegalArgumentException("Target path is required.");
        }

        String value = internalPath.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Target path is required.");
        }
        if (!value.startsWith("/") || value.startsWith("//")) {
            throw new IllegalArgumentException("Target path must begin with a single '/'.");
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
            throw new IllegalArgumentException("Target path must be an internal application path.");
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
}