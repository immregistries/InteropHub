package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.airahub.interophub.service.GuideBuildService;
import org.airahub.interophub.service.GuideBuildService.GuideStatus;
import org.airahub.interophub.service.GuideBuildService.Impact;
import org.airahub.interophub.service.GuideBuildService.State;
import org.airahub.interophub.service.GuideBuildService.StatusReport;
import org.airahub.interophub.service.GuideBuildService.Warning;
import org.airahub.interophub.service.GuideRows.BuildRow;
import org.junit.jupiter.api.Test;

class GuideStatusRendererTest {

    @Test
    void statusViewRendersEveryStateAndTheSummary() {
        String html = render(report(), null, null);
        assertTrue(html.contains("Guide status"));
        assertTrue(html.contains("aria-current=\"page\">Status</a>"));
        assertTrue(html.contains("<strong>5</strong> registered guides"));
        assertTrue(html.contains("<strong>1</strong> current"));
        assertTrue(html.contains("<strong>2</strong> behind (1 rebuild, 0 config, 1 content)"));
        assertTrue(html.contains("<strong>1</strong> no status reported"));
        assertTrue(html.contains("<strong>1</strong> with warnings"));
        assertTrue(html.contains("<strong>1</strong> status unreadable"));
        assertTrue(html.contains("Latest process release: <strong>v0.22.0</strong>"));
        assertTrue(html.contains("No status reported</span>"));
        assertTrue(html.contains("Status unreadable</span> <span class=\"aira-muted\">The build manifest is not valid JSON."));
        assertTrue(html.contains("aira-badge--success\">Current"));
        assertTrue(html.contains("aira-badge--danger\">1 behind"));
        assertTrue(html.contains("aira-badge--warning\">2 behind"));
        assertTrue(html.contains("<details><summary>2</summary>"));
        assertTrue(html.contains("<code>CHAPTER_COUNT</code>"));
        assertTrue(html.contains("href=\"/hub/es/topic/8\""));
        assertTrue(html.contains("href=\"/hub/guide/view/hub-current/latest/\""));
        assertTrue(html.contains("href=\"/hub/admin/es/guides?view=status&amp;guide=hub-current\""));
        assertTrue(html.contains("Process releases known to InteropHub"));
        assertEquals(5, count(html, "<tr data-guide="));
    }

    @Test
    void filtersApply() {
        StatusReport report = report();
        assertEquals(List.of("never"), keys(GuideStatusRenderer.filtered(report, "no-status", null)));
        assertEquals(List.of("content-behind", "rebuild-behind"),
                keys(GuideStatusRenderer.filtered(report, "needs-upgrade", null)));
        assertEquals(List.of("content-behind"), keys(GuideStatusRenderer.filtered(report, "needs-content", null)));
        assertEquals(List.of("rebuild-behind"), keys(GuideStatusRenderer.filtered(report, "needs-rebuild", null)));
        assertEquals(List.of(), keys(GuideStatusRenderer.filtered(report, "needs-config", null)));
        assertEquals(List.of("hub-current"), keys(GuideStatusRenderer.filtered(report, "has-warnings", null)));
        assertEquals(List.of("hub-current"), keys(GuideStatusRenderer.filtered(report, null, "CHAPTER_COUNT")));
        assertEquals(List.of(), keys(GuideStatusRenderer.filtered(report, null, "SUMMARY_LONG")));
        assertEquals(List.of("never", "rebuild-behind"), keys(GuideStatusRenderer.filtered(report, "stale", null)));

        String html = render(report, "needs-content", null);
        assertEquals(1, count(html, "<tr data-guide="));
        assertTrue(html.contains("Guides (1 of 5)"));
        assertTrue(html.contains("<option value=\"needs-content\" selected>"));

        String none = render(report, "needs-config", null);
        assertTrue(none.contains("No guides match."));
    }

    @Test
    void detailShowsHistoryWarningsAndTheEscapedRawManifest() {
        StatusReport report = report();
        GuideStatus guide = report.guides.stream().filter(g -> g.documentKey.equals("hub-current")).findFirst().get();
        BuildRow latest = guide.latestBuild;
        BuildRow older = new BuildRow();
        older.id = 1L;
        older.source = "UPLOAD";
        older.label = "latest";
        older.manifestError = "Unknown build manifest schema: 2.";
        older.receivedAt = LocalDateTime.of(2026, 11, 1, 9, 0);
        GuideBuildService.Detail detail = new GuideBuildService.Detail("hub-current", "Hub <Current>", true, guide,
                report, List.of(latest, older));
        StringWriter writer = new StringWriter();
        GuideStatusRenderer.renderDetail(new PrintWriter(writer), "/hub", detail);
        String html = writer.toString();
        assertTrue(html.contains("Hub &lt;Current&gt;"));
        assertTrue(html.contains("Warnings in the latest build"));
        assertTrue(html.contains("<code>CHAPTER_COUNT</code> 8 chapters"));
        assertTrue(html.contains("Build history"));
        assertTrue(html.indexOf("2026-11-30 10:00") < html.indexOf("2026-11-01 09:00"));
        assertTrue(html.contains("Unreadable</span> Unknown build manifest schema: 2."));
        assertTrue(html.contains(
                "id=\"guide-build-manifest-raw\" class=\"aira-code-block aira-code-block--wrap\" tabindex=\"0\""));
        assertFalse(html.contains("max-height: 40rem"));
        assertTrue(html.contains("script&gt;&lt;b&gt;x"));
        assertFalse(html.contains("</script><b>"));
    }

    private static String render(StatusReport report, String filter, String code) {
        StringWriter writer = new StringWriter();
        GuideStatusRenderer.render(new PrintWriter(writer), "/hub", report, filter, code);
        return writer.toString();
    }

    private static StatusReport report() {
        StatusReport report = new StatusReport();
        report.latestVersion = "v0.22.0";
        report.releaseSourceKey = "hub-current";
        report.releases = new ArrayList<>(List.of(
                new GuideBuildService.Release("v0.22.0", "2026-12-01", Impact.CONTENT, "Front section"),
                new GuideBuildService.Release("v0.21.0", "2026-11-24", Impact.REBUILD, "Manifest")));
        report.warningCodes = List.of("CHAPTER_COUNT");

        GuideStatus never = guide("never", State.NO_STATUS);
        never.stale = true;

        GuideStatus unreadable = guide("unreadable", State.UNREADABLE);
        unreadable.error = "The build manifest is not valid JSON.";

        GuideStatus content = guide("content-behind", State.BEHIND);
        content.processVersion = "v0.21.0";
        content.behind = 1;
        content.impact = Impact.CONTENT;

        GuideStatus rebuild = guide("rebuild-behind", State.NO_MANIFEST);
        rebuild.behind = 2;
        rebuild.impact = Impact.REBUILD;
        rebuild.stale = true;

        GuideStatus current = guide("hub-current", State.CURRENT);
        current.hosting = "HUB";
        current.guideUrl = "/guide/view/hub-current/latest/";
        current.processVersion = "v0.22.0";
        current.behind = 0;
        current.warningCount = 2;
        current.warnings = List.of(new Warning("CHAPTER_COUNT", "8 chapters", "Project.qmd"),
                new Warning("CHAPTER_COUNT", "Still 8", null));
        current.warningCodes.add("CHAPTER_COUNT");
        current.feedbackMarkers = 1;
        BuildRow build = new BuildRow();
        build.id = 2L;
        build.source = "UPLOAD";
        build.label = "latest";
        build.processVersion = "v0.22.0";
        build.receivedAt = LocalDateTime.of(2026, 11, 30, 10, 0);
        build.manifestJson = "{\"schema\":1,\"processVersion\":\"v0.22.0\",\"document\":{\"title\":\"</script><b>x\"}}";
        current.latestBuild = build;

        report.guides.addAll(List.of(never, unreadable, content, rebuild, current));
        return report;
    }

    private static GuideStatus guide(String key, State state) {
        GuideStatus guide = new GuideStatus();
        guide.documentKey = key;
        guide.title = key;
        guide.hosting = "EXTERNAL";
        guide.topicId = 8L;
        guide.topicName = "Topic";
        guide.state = state;
        return guide;
    }

    private static List<String> keys(List<GuideStatus> guides) {
        return guides.stream().map(guide -> guide.documentKey).toList();
    }

    private static int count(String text, String part) {
        int count = 0;
        int index = text.indexOf(part);
        while (index >= 0) {
            count++;
            index = text.indexOf(part, index + part.length());
        }
        return count;
    }
}
