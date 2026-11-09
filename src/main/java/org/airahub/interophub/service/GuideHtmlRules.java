package org.airahub.interophub.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/** Checks for a hosted guide upload. General file uploads do not use this class. */
public final class GuideHtmlRules {
    public static final long MAX_BYTES = 20L * 1024 * 1024;
    public static final String EXTERNAL = "EXTERNAL";
    public static final String HUB = "HUB";
    private static final Pattern LABEL = Pattern.compile(
            "^(?:latest|main|v[0-9]+(?:\\.[0-9]+)*(?:-[A-Za-z0-9][A-Za-z0-9.]*)?)$");

    private GuideHtmlRules() {
    }

    /** Absent means EXTERNAL. Any other value must be EXTERNAL or HUB. */
    public static String hosting(String raw) {
        if (raw == null) {
            return EXTERNAL;
        }
        String value = raw.trim();
        if (EXTERNAL.equals(value) || HUB.equals(value)) {
            return value;
        }
        throw GuideApiException.invalidField("hosting", "Hosting must be EXTERNAL or HUB.");
    }

    public static String label(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > 64 || !LABEL.matcher(value).matches()) {
            throw GuideApiException.invalidField("label",
                    "Label must be latest, main, or a version such as v1.2.3.");
        }
        return value;
    }

    /**
     * {@code text/html} (an optional charset parameter is allowed), at most 20 MiB,
     * and the bytes start like an HTML document after an optional BOM and whitespace.
     */
    public static void html(byte[] bytes, String contentType) {
        String media = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!"text/html".equals(media)) {
            throw GuideApiException.invalidField("type", "The guide must be sent as text/html.");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw GuideApiException.invalidField("size",
                    "The guide must be a non-empty HTML file of at most 20 MB.");
        }
        if (!startsLikeHtml(bytes)) {
            throw GuideApiException.invalidField("type", "The file must start like an HTML document.");
        }
    }

    static boolean startsLikeHtml(byte[] bytes) {
        int start = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            start = 3;
        }
        while (start < bytes.length) {
            byte value = bytes[start];
            if (value == ' ' || value == '\t' || value == '\n' || value == '\r') {
                start++;
                continue;
            }
            break;
        }
        int length = Math.min(bytes.length - start, 15);
        if (length <= 0) {
            return false;
        }
        String head = new String(bytes, start, length, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        return head.startsWith("<!doctype") || head.startsWith("<html");
    }
}
