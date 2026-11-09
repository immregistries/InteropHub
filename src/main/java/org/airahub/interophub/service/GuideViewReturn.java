package org.airahub.interophub.service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The only path a signed-out guide reader may be sent back to after the magic
 * link. Anything else is ignored so the link cannot become an open redirect.
 */
public final class GuideViewReturn {
    private static final String PREFIX = "/guide/view/";
    private static final int MAX_LENGTH = 500;

    private GuideViewReturn() {
    }

    /**
     * A relative path under {@code /guide/view/}, query kept. Absolute URLs,
     * {@code //host}, other paths, and encoded forms of those are empty.
     */
    public static Optional<String> accept(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH || unsafe(value)) {
            return Optional.empty();
        }
        String decoded = value;
        for (int pass = 0; pass < 3 && decoded.indexOf('%') >= 0; pass++) {
            String next = percentDecode(decoded);
            if (next == null || unsafe(next)) {
                return Optional.empty();
            }
            decoded = next;
        }
        if (unsafe(decoded) || !guidePath(decoded)) {
            return Optional.empty();
        }
        return Optional.of(decoded);
    }

    private static boolean guidePath(String value) {
        int query = value.indexOf('?');
        String path = query >= 0 ? value.substring(0, query) : value;
        return path.startsWith(PREFIX);
    }

    private static boolean unsafe(String value) {
        if (value.startsWith("//") || value.contains("\\") || value.contains("://") || value.contains("..")
                || value.indexOf('#') >= 0 || value.contains("//")) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= ' ' || c == 0x7f) {
                return true;
            }
        }
        try {
            URI uri = new URI(value);
            return uri.isAbsolute() || uri.getScheme() != null || uri.getHost() != null || uri.getRawAuthority() != null;
        } catch (Exception ex) {
            return true;
        }
    }

    private static String percentDecode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
