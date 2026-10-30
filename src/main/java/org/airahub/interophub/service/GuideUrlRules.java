package org.airahub.interophub.service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Published-guide URL checks from contract 7.3. */
public final class GuideUrlRules {
    static final int MAX_URL_LENGTH = 255;
    static final int MAX_LABEL_LENGTH = 40;
    private static final Set<String> RESERVED_KEYS = Set.of("comments", "review-flags", "token");

    private GuideUrlRules() {
    }

    public static boolean reservedDocumentKey(String documentKey) {
        return documentKey != null && RESERVED_KEYS.contains(documentKey);
    }

    public static String documentKey(String raw) {
        String key = raw == null ? "" : raw.trim();
        if (key.isEmpty() || key.length() > 100 || !key.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
            throw GuideApiException.invalidField("documentKey",
                    "Document key must be lowercase words separated by hyphens, at most 100 characters.");
        }
        if (RESERVED_KEYS.contains(key)) {
            throw GuideApiException.invalidField("documentKey", "That document key is reserved.");
        }
        return key;
    }

    public static String title(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 200) {
            throw GuideApiException.invalidField("title", "Title must be 1 to 200 characters.");
        }
        return value;
    }

    /** Blank becomes null. A present value must be an absolute http(s) URL. */
    public static String repositoryUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > 500) {
            throw GuideApiException.invalidField("repositoryUrl", "Repository URL is too long.");
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (Exception ex) {
            throw GuideApiException.invalidField("repositoryUrl", "Repository URL must be an absolute http(s) URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (uri.getHost() == null || (!"http".equals(scheme) && !"https".equals(scheme))) {
            throw GuideApiException.invalidField("repositoryUrl", "Repository URL must be an absolute http(s) URL.");
        }
        return value;
    }

    public static String label(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > MAX_LABEL_LENGTH) {
            throw GuideApiException.invalidField("urls", "Each URL label must be 1 to 40 characters.");
        }
        return value;
    }

    /**
     * Normalizes a guide URL. {@code devApproved} is true when the localhost dev rule
     * treats this URL as an approved prefix.
     */
    public static Normalized normalize(String raw, boolean localhostMode) {
        if (raw == null || raw.isBlank()) {
            throw GuideApiException.invalidField("urls", "A guide URL is required.");
        }
        String trimmed = raw.trim();
        if (trimmed.indexOf('?') >= 0 || trimmed.indexOf('#') >= 0) {
            throw GuideApiException.invalidField("urls", "A guide URL cannot include a query or fragment.");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (Exception ex) {
            throw GuideApiException.invalidField("urls", "A guide URL must be absolute.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || uri.getUserInfo() != null) {
            throw GuideApiException.invalidField("urls", "A guide URL must be absolute.");
        }
        boolean localHost = "localhost".equals(host) || "127.0.0.1".equals(host);
        boolean devApproved = localhostMode && "http".equals(scheme) && localHost;
        if (!"https".equals(scheme) && !devApproved) {
            throw GuideApiException.invalidField("urls",
                    "A guide URL must be https. http is only allowed for localhost in local development.");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.isEmpty()) {
            path = "/";
        } else if (!path.endsWith("/")) {
            path = path + "/";
        }
        int port = uri.getPort();
        boolean defaultPort = port == -1 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String rebuilt = scheme + "://" + host + (defaultPort ? "" : ":" + port) + path;
        if (rebuilt.length() > MAX_URL_LENGTH) {
            throw GuideApiException.invalidField("urls", "A guide URL must be at most 255 characters.");
        }
        return new Normalized(rebuilt, devApproved);
    }

    public static String prefix(String raw) {
        Normalized normalized = normalize(raw, false);
        if (normalized.devApproved()) {
            throw GuideApiException.invalidField("urlPrefix", "An approved prefix must be an https URL.");
        }
        return normalized.url();
    }

    public static boolean approved(String normalizedUrl, boolean devApproved, boolean globalAdmin,
            Iterable<String> prefixes) {
        if (globalAdmin || devApproved) {
            return true;
        }
        if (prefixes == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix != null && !prefix.isBlank() && normalizedUrl.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public record Normalized(String url, boolean devApproved) {
    }
}
