package org.airahub.interophub.service;

import java.net.URI;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CORS for {@code /api/guide/*}. Allowed origins are the scheme, host, and port of enabled
 * {@code topic-reference} allowlist rows. Credentials are never allowed.
 */
public final class GuideCors {
    public enum Permission {
        ABSENT,
        ALLOWED,
        REFUSED
    }

    private GuideCors() {
    }

    public static String originOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.isBlank()) {
                return null;
            }
            scheme = scheme.toLowerCase();
            host = host.toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            int port = uri.getPort();
            boolean defaultPort = port == -1 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
            return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
        } catch (Exception ex) {
            return null;
        }
    }

    public static Permission permission(String originHeader, Set<String> allowedOrigins) {
        if (originHeader == null || originHeader.isBlank()) {
            return Permission.ABSENT;
        }
        String normalized = originOf(originHeader);
        if (normalized != null && allowedOrigins != null && allowedOrigins.contains(normalized)) {
            return Permission.ALLOWED;
        }
        return Permission.REFUSED;
    }

    public static void applyAllowed(HttpServletResponse response, String originHeader) {
        response.setHeader("Access-Control-Allow-Origin", originHeader.trim());
        response.setHeader("Vary", "Origin");
        response.setHeader("Access-Control-Expose-Headers", "WWW-Authenticate");
    }

    public static void applyPreflight(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE");
        response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
        response.setHeader("Access-Control-Max-Age", "600");
    }
}
