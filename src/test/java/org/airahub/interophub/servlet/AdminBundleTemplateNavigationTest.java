package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class AdminBundleTemplateNavigationTest {
    @Test
    void templatesAreDiscoverableInContentAndNotTopicSpaces() {
        var writer = new StringWriter();
        AdminSectionNavRenderer.render(new PrintWriter(writer), "/hub", AdminSection.CONTENT,
                "/hub/admin/content/bundle-templates");
        String html = writer.toString();
        assertTrue(html.contains("Communication Bundle Templates"));
        assertTrue(html.contains("href=\"/hub/admin/content/bundle-templates\" aria-current=\"page\""));
        writer = new StringWriter();
        AdminSectionNavRenderer.render(new PrintWriter(writer), "/hub", AdminSection.TOPIC_SPACES, "");
        assertFalse(writer.toString().contains("Communication Bundle Templates"));
    }
}
