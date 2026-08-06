package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SearchTextUtilTest {

    @Test
    void escapeHtmlEscapesUnsafeCharacters() {
        assertEquals("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;",
                SearchTextUtil.escapeHtml("<script>alert(\"x\")</script>"));
    }

    @Test
    void highlightIsCaseInsensitiveAndPreservesOriginalCasing() {
        String result = SearchTextUtil.highlightFull("Certificate Management", "certificate");
        assertEquals("<mark>Certificate</mark> Management", result);
    }

    @Test
    void highlightEscapesTextBeforeWrappingMatches() {
        String result = SearchTextUtil.highlightFull("<b>Certificate</b>", "certificate");
        assertTrue(result.contains("&lt;b&gt;"));
        assertTrue(result.contains("<mark>Certificate</mark>"));
    }

    @Test
    void excerptAndHighlightWindowsAroundTheMatchWithEllipses() {
        String longText = "word ".repeat(50) + "certificate" + " word".repeat(50);
        String excerpt = SearchTextUtil.excerptAndHighlight(longText, "certificate", 40);

        assertTrue(excerpt.length() < longText.length());
        assertTrue(excerpt.contains("<mark>certificate</mark>"));
        assertTrue(excerpt.startsWith("…"));
        assertTrue(excerpt.endsWith("…"));
    }

    @Test
    void excerptAndHighlightFallsBackToStartOfTextWhenQueryNotADirectSubstring() {
        String excerpt = SearchTextUtil.excerptAndHighlight("Some unrelated summary text.", "certificate", 160);
        assertEquals("Some unrelated summary text.", excerpt);
        assertFalse(excerpt.contains("<mark>"));
    }

    @Test
    void normalizeLowercasesAndTrims() {
        assertEquals("certificate management", SearchTextUtil.normalize("  Certificate Management  "));
        assertEquals("", SearchTextUtil.normalize(null));
    }
}
