package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class TopicManageNavRendererTest {

    @Test
    void supportersLinkOnlyRenderedForAdmins() {
        TopicManageNavRenderer.TopicManageCounts counts = new TopicManageNavRenderer.TopicManageCounts(
                0, 0, 0, 0, 0, 0);

        String adminHtml = render(counts, true);
        String championHtml = render(counts, false);

        assertTrue(adminHtml.contains("/es/topic-manage/42/supporters"));
        assertFalse(championHtml.contains("/es/topic-manage/42/supporters"));
    }

    @Test
    void supportersCountShownWhenGreaterThanZero() {
        TopicManageNavRenderer.TopicManageCounts counts = new TopicManageNavRenderer.TopicManageCounts(
                0, 0, 0, 0, 0, 3);

        String html = render(counts, true);

        assertTrue(html.contains("Supporters"));
        assertTrue(html.contains(">3<"));
    }

    private String render(TopicManageNavRenderer.TopicManageCounts counts, boolean isAdmin) {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        TopicManageNavRenderer.render(out, "/hub", 42L, null, isAdmin, null, false, counts);
        out.flush();
        return writer.toString();
    }
}
