package org.airahub.interophub.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared HTML-escaping, excerpting, and match-highlighting helpers for
 * {@link SearchService} and the servlets that render its results. Matching
 * is case-insensitive substring matching only; see
 * {@code docs/InteropHub_Global_Search_Design.md} ("Support for advanced
 * fuzzy matching is optional for the initial implementation").
 */
final class SearchTextUtil {

    private SearchTextUtil() {
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Escapes {@code rawText} in full (no truncation) and highlights matches; for short fields like titles. */
    static String highlightFull(String rawText, String rawQuery) {
        if (rawText == null) {
            return "";
        }
        String escaped = escapeHtml(rawText);
        return highlight(escaped, rawQuery);
    }

    /**
     * Escapes {@code rawText}, trims it to a short window around the first
     * case-insensitive occurrence of {@code rawQuery} (or the start of the
     * text if the query isn't a direct substring, e.g. a keyword-list match),
     * and wraps matched occurrences in {@code <mark>}.
     */
    static String excerptAndHighlight(String rawText, String rawQuery, int maxLength) {
        if (rawText == null) {
            return "";
        }
        String text = rawText.strip();
        if (text.isEmpty()) {
            return "";
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerQuery = normalize(rawQuery);
        int matchIndex = lowerQuery.isEmpty() ? -1 : lowerText.indexOf(lowerQuery);

        String windowed;
        if (matchIndex < 0) {
            windowed = text.length() <= maxLength ? text : text.substring(0, maxLength).strip() + "…";
        } else {
            int start = Math.max(0, matchIndex - (maxLength / 3));
            int end = Math.min(text.length(), matchIndex + lowerQuery.length() + (maxLength * 2 / 3));
            StringBuilder windowBuilder = new StringBuilder();
            if (start > 0) {
                windowBuilder.append("…");
            }
            windowBuilder.append(text, start, end);
            if (end < text.length()) {
                windowBuilder.append("…");
            }
            windowed = windowBuilder.toString();
        }

        String escaped = escapeHtml(windowed);
        return lowerQuery.isEmpty() ? escaped : highlight(escaped, rawQuery);
    }

    /** Wraps case-insensitive occurrences of {@code rawQuery} in {@code <mark>} within already-escaped text. */
    static String highlight(String escapedText, String rawQuery) {
        String escapedQuery = escapeHtml(rawQuery == null ? "" : rawQuery.strip());
        if (escapedQuery.isEmpty() || escapedText == null || escapedText.isEmpty()) {
            return escapedText == null ? "" : escapedText;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(escapedQuery), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(escapedText);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(escapedText, last, matcher.start());
            result.append("<mark>").append(matcher.group()).append("</mark>");
            last = matcher.end();
        }
        result.append(escapedText, last, escapedText.length());
        return result.toString();
    }
}
