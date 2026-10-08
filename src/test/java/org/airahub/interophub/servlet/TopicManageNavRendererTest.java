package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class TopicManageNavRendererTest {
    @Test
    void starterPacketLinkHasItsOwnChampionSupportAccessFlag() {
        var counts = new TopicManageNavRenderer.TopicManageCounts(0, 0, 0, 0, 0, 0);
        var writer = new StringWriter();
        TopicManageNavRenderer.render(new PrintWriter(writer), "/hub", 42L, null, false, null, false, counts, false, true);
        assertTrue(writer.toString().contains("/es/starter-packets/42"));
        assertTrue(writer.toString().contains(">Starter Packet</span>"));
        assertFalse(writer.toString().contains("/es/topic-resources/42"));
        writer = new StringWriter();
        TopicManageNavRenderer.render(new PrintWriter(writer), "/hub", 42L, null, false, null, false, counts, true, false);
        assertFalse(writer.toString().contains("/es/starter-packets/42"));
    }

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

    @Test
    void resourceEditorLinkIsStewardOnlyNotChampionManagement() {
        var counts = new TopicManageNavRenderer.TopicManageCounts(0, 0, 0, 0, 0, 0);
        assertTrue(render(counts, true).contains("/es/topic-resources/42"));
        assertTrue(render(counts, true).contains(">Orientation Resources</span>"));
        assertFalse(render(counts, true).contains("Manage Orientation"));
        assertFalse(render(counts, false).contains("/es/topic-resources/42"));
        StringWriter writer = new StringWriter();
        TopicManageNavRenderer.render(new PrintWriter(writer), "/hub", 42L, null, false, null, false, counts, true);
        assertTrue(writer.toString().contains("/es/topic-resources/42"));
    }

    private String render(TopicManageNavRenderer.TopicManageCounts counts, boolean isAdmin) {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        TopicManageNavRenderer.render(out, "/hub", 42L, null, isAdmin, null, false, counts, isAdmin);
        out.flush();
        return writer.toString();
    }
}
