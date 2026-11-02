package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

class TopicReferenceAuthorPagesTest {
    @Test
    void panelLinksCommentsFlagsAndHidesManagementFromReaders() {
        var writer = new StringWriter();
        TopicReferenceCommentsPanel.write(new PrintWriter(writer), "/hub", 7L, new TopicReferenceCommentsPanel.PanelView(
                "contextual-conditions", false,
                List.of(new TopicReferenceCommentsPanel.CommentItem(12L, "Should this say which authorities",
                        "Guide Reader", "Nov 2, 2026", "Persuasive", "2026-11-02T00:00:00.000Z")),
                List.of(new TopicReferenceCommentsPanel.FlagItem("consent-requirements", "Please review",
                        "Nov 2, 2026", "2026-11-02T00:00:00.000Z"))));
        String html = writer.toString();
        assertTrue(html.contains("Topic Reference comments"));
        assertTrue(html.contains("/hub/guide/open?doc=contextual-conditions&amp;comment=12"));
        assertTrue(html.contains("/hub/guide/open?doc=contextual-conditions&amp;anchor=consent-requirements"));
        assertTrue(html.contains("/hub/guide/open?doc=contextual-conditions\">Open the guide"));
        assertTrue(html.contains("Guide Reader"));
        assertTrue(html.contains("Persuasive"));
        assertFalse(html.contains("/es/topic-guide-comments/"));
        assertFalse(html.contains("confirm("));

        writer = new StringWriter();
        TopicReferenceCommentsPanel.write(new PrintWriter(writer), "/hub", 7L, new TopicReferenceCommentsPanel.PanelView(
                "contextual-conditions", true, List.of(), List.of()));
        assertTrue(writer.toString().contains("/hub/es/topic-guide-comments/7"));
    }

    @Test
    void managementLinkSitsNextToGuideRegistration() {
        var counts = new TopicManageNavRenderer.TopicManageCounts(0, 0, 0, 0, 0, 0);
        var writer = new StringWriter();
        TopicManageNavRenderer.render(new PrintWriter(writer), "/hub", 7L, null, false, null, false, counts, false, false);
        String html = writer.toString();
        int registration = html.indexOf("/es/topic-guide/7");
        int comments = html.indexOf("/es/topic-guide-comments/7");
        assertTrue(registration >= 0);
        assertTrue(comments > registration);
        assertTrue(html.contains("Topic Reference comments"));
    }

    @Test
    void commentPageDefaultsToEverythingExceptClosed() {
        assertEquals("OPEN,DISPOSED,WITHDRAWN", GuideCommentsPageServlet.stateQuery(null));
        assertEquals("OPEN,DISPOSED,WITHDRAWN", GuideCommentsPageServlet.stateQuery("current"));
        assertEquals("all", GuideCommentsPageServlet.stateQuery("all"));
        assertEquals("CLOSED", GuideCommentsPageServlet.stateQuery("CLOSED"));
        assertEquals("OPEN,DISPOSED,WITHDRAWN", GuideCommentsPageServlet.stateQuery("nope"));
    }
}
