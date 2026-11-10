package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.ApiTime;
import org.airahub.interophub.service.GuideDisplayDates;
import org.airahub.interophub.service.TopicReferenceDigest;
import org.airahub.interophub.service.TopicReferenceDigest.FlagNote;
import org.airahub.interophub.service.TopicReferenceDigest.OpenThread;
import org.airahub.interophub.service.TopicReferenceSectionData;
import org.airahub.interophub.service.TopicReferenceSectionData.ExternalUrl;
import org.airahub.interophub.service.TopicReferenceSectionData.Guide;
import org.airahub.interophub.service.TopicReferenceSectionData.Link;
import org.airahub.interophub.service.TopicReferenceSectionData.VersionChoice;
import org.airahub.interophub.service.TopicReferenceSectionData.View;
import org.junit.jupiter.api.Test;

class TopicReferenceAuthorPagesTest {
    @Test
    void sectionIsVisibleToEditorsAndOmittedOtherwise() {
        assertFalse(TopicReferenceSectionData.visibleTo(null, topic(7L), true));
        User reader = user(2L);
        assertFalse(TopicReferenceSectionData.visibleTo(reader, topic(7L), false));
        assertFalse(TopicReferenceSectionData.visibleTo(reader, new EsTopic(), true));
        assertTrue(TopicReferenceSectionData.visibleTo(reader, topic(7L), true));

        var called = new AtomicBoolean();
        var writer = new StringWriter();
        TopicReferenceSection.render(new PrintWriter(writer), "/hub", null, topic(7L), null,
                (viewer, loadedTopic, space, context) -> {
                    called.set(true);
                    return null;
                });
        assertEquals("", writer.toString());
        assertFalse(called.get());

        writer = new StringWriter();
        TopicReferenceSection.render(new PrintWriter(writer), "/hub", reader, topic(7L), null,
                (viewer, loadedTopic, space, context) -> null);
        assertEquals("", writer.toString());

        writer = new StringWriter();
        TopicReferenceSection.render(new PrintWriter(writer), "/hub", reader, topic(7L), null,
                (viewer, loadedTopic, space, context) -> TopicReferenceSectionData.view(7L, false, "/hub", List.of(), 0));
        assertEquals("", writer.toString());

        writer = new StringWriter();
        TopicReferenceSection.render(new PrintWriter(writer), "/hub", reader, topic(7L), null,
                (viewer, loadedTopic, space, context) -> {
                    throw new IllegalStateException("boom");
                });
        assertEquals("", writer.toString());
    }

    @Test
    void hostedAndExternalLinksStopAtTheLimit() {
        LocalDateTime day = LocalDateTime.of(2026, 11, 1, 0, 0);
        List<Link> hosted = TopicReferenceSectionData.hubLinks("/hub", 186L, "guide-hosted", List.of(
                new VersionChoice("v1", day.plusDays(1)),
                new VersionChoice("latest", day),
                new VersionChoice("v2", day.plusDays(2)),
                new VersionChoice("main", day.plusDays(3)),
                new VersionChoice("v3", day.plusDays(4)),
                new VersionChoice("v4", day.plusDays(5)),
                new VersionChoice("v5", day.plusDays(6))));
        assertEquals(List.of("latest", "main", "v5", "v4", "v3", "+2 earlier"),
                hosted.stream().map(Link::label).toList());
        assertEquals("/hub/guide/open?doc=guide-hosted", hosted.get(0).href());
        assertTrue(hosted.get(0).primary());
        assertEquals("/hub/guide/view/guide-hosted/main/", hosted.get(1).href());
        assertEquals("/hub/guide/view/guide-hosted/v5/", hosted.get(2).href());
        assertFalse(hosted.get(1).primary());
        assertEquals("/hub/es/topic-guide/186", hosted.get(5).href());

        List<Link> external = TopicReferenceSectionData.externalLinks("/hub", 7L, "contextual-conditions", List.of(
                new ExternalUrl("Current", "http://example.test/1"),
                new ExternalUrl("Draft", "http://example.test/2"),
                new ExternalUrl("Three", "http://example.test/3"),
                new ExternalUrl("Four", "http://example.test/4"),
                new ExternalUrl("Five", "http://example.test/5"),
                new ExternalUrl("Six", "http://example.test/6")));
        assertEquals(List.of("Current", "Draft", "Three", "Four", "Five", "+1 more"),
                external.stream().map(Link::label).toList());
        assertEquals("/hub/guide/open?doc=contextual-conditions", external.get(0).href());
        assertTrue(external.get(0).primary());
        assertEquals("http://example.test/2", external.get(1).href());
        assertEquals("/hub/es/topic-guide/7", external.get(5).href());
        assertFalse(external.stream().anyMatch(link -> "http://example.test/6".equals(link.href())));
    }

    @Test
    void sectionRendersBoundedGuidesAndKeepsManagementLinksOnEditors() {
        Guide hosted = TopicReferenceSectionData.present("/hub", 186L, "guide-hosted", "Hosted guide", "HUB", true,
                List.of(), List.of(
                        new VersionChoice("latest", LocalDateTime.of(2026, 11, 9, 12, 0)),
                        new VersionChoice("v1", LocalDateTime.of(2026, 11, 1, 12, 0))),
                new TopicReferenceDigest(0, 0, 0, LocalDateTime.of(2026, 11, 9, 12, 0), 0, 0, 0, List.of(), List.of(), 0));
        View editor = TopicReferenceSectionData.view(186L, true, "/hub", List.of(hosted), 2);
        String html = html(editor);
        assertTrue(html.contains("id=\"topic-reference-title\">Topic Reference"));
        assertTrue(html.contains("Editors only"));
        assertTrue(html.contains("Hosted on InteropHub"));
        assertTrue(html.contains("Manage comments"));
        assertTrue(html.contains("Guide settings"));
        assertTrue(html.contains("2 more guides"));
        assertTrue(html.contains("/hub/guide/open?doc=guide-hosted"));
        assertTrue(html.contains("/hub/guide/view/guide-hosted/v1/"));
        assertTrue(html.contains("Last activity Nov 9, 2026"));
        assertFalse(html.contains("in the last 30 days"));
        assertFalse(html.contains("Host it on InteropHub instead"));
        assertFalse(html.contains("<aside"));
        assertFalse(html.contains("Topic Reference comments"));
        assertFalse(html.contains("public_id"));

        Guide external = TopicReferenceSectionData.present("/hub", 7L, "contextual-conditions", "Pilot guide",
                "EXTERNAL", true, List.of(new ExternalUrl("Current", "http://example.test/guide")), List.of(),
                TopicReferenceDigest.empty());
        String readerHtml = html(TopicReferenceSectionData.view(7L, false, "/hub", List.of(external), 0));
        assertTrue(readerHtml.contains("Published externally"));
        assertTrue(readerHtml.contains("Host it on InteropHub instead"));
        assertTrue(readerHtml.contains("No comments are waiting for a disposition."));
        assertFalse(readerHtml.contains("Manage comments"));
        assertFalse(readerHtml.contains("Guide settings"));
    }

    @Test
    void listsStopAtFiveOpenCommentsAndThreeFlags() {
        List<OpenThread> threads = List.of(
                new OpenThread(1L, "open-one", "Ada", LocalDateTime.of(2026, 11, 9, 12, 0), 1),
                new OpenThread(2L, "open-two", "Ada", LocalDateTime.of(2026, 11, 8, 12, 0), 0),
                new OpenThread(3L, "open-three", "Ada", LocalDateTime.of(2026, 11, 7, 12, 0), 0),
                new OpenThread(4L, "open-four", "Ada", LocalDateTime.of(2026, 11, 6, 12, 0), 0),
                new OpenThread(5L, "open-five", "Ada", LocalDateTime.of(2026, 11, 5, 12, 0), 0),
                new OpenThread(6L, "open-six", "Ada", LocalDateTime.of(2026, 11, 4, 12, 0), 0));
        List<FlagNote> flags = List.of(
                new FlagNote("a-one", "flag-one", LocalDateTime.of(2026, 11, 9, 12, 0)),
                new FlagNote("a-two", "flag-two", LocalDateTime.of(2026, 11, 8, 12, 0)),
                new FlagNote("a-three", "flag-three", LocalDateTime.of(2026, 11, 7, 12, 0)),
                new FlagNote("a-four", "flag-four", LocalDateTime.of(2026, 11, 6, 12, 0)));
        TopicReferenceDigest digest = new TopicReferenceDigest(6, 1, 2, LocalDateTime.of(2026, 11, 9, 12, 0),
                8, 1, 3, threads, flags, 4);
        String html = html(TopicReferenceSectionData.view(186L, true, "/hub", List.of(
                TopicReferenceSectionData.present("/hub", 186L, "guide-hosted", "Hosted guide", "HUB", false,
                        List.of(), List.of(), digest)), 0));
        assertTrue(html.contains("topic-reference-stat__value\">6</span>"));
        assertTrue(html.contains("topic-reference-stat__value\">1</span>"));
        assertTrue(html.contains("topic-reference-stat__value\">2</span>"));
        assertTrue(html.contains("topic-reference-stat__value\">4</span>"));
        assertTrue(html.contains("View all 6 open comments"));
        assertTrue(html.contains("/hub/es/topic-guide-comments/186?document=guide-hosted&amp;show=OPEN"));
        assertTrue(html.contains("+1 more"));
        assertTrue(html.contains("/hub/guide/open?doc=guide-hosted&amp;comment=1"));
        assertTrue(html.contains("/hub/guide/open?doc=guide-hosted&amp;anchor=a-one"));
        assertTrue(html.contains("open-five"));
        assertFalse(html.contains("open-six"));
        assertFalse(html.contains("flag-four"));
        assertEquals(8, count(html, "class=\"aira-recent-topic\" href"));
    }

    @Test
    void topicPagePutsTheSectionInTheMainColumn() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/org/airahub/interophub/servlet/EsTopicDetailServlet.java"));
        assertFalse(source.contains("TopicReferenceCommentsPanel"));
        int section = source.indexOf("TopicReferenceSection.render");
        int aside = source.indexOf("<aside class=\\\"aira-right-rail\\\"");
        assertTrue(section >= 0);
        assertTrue(aside > section);
    }

    @Test
    void hostedUploadTimesUseTheTopicPageDate() {
        String iso = "2026-11-09T18:00:00.000Z";
        assertEquals(ApiTime.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")),
                GuideDisplayDates.day(iso));
        assertFalse(GuideDisplayDates.day(iso).contains("T"));
        assertEquals("", GuideDisplayDates.day("  "));
        assertEquals(ApiTime.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a")),
                GuideDisplayDates.dayTime(iso));
        assertFalse(GuideDisplayDates.dayTime(iso).contains("T"));
        assertEquals("", GuideDisplayDates.dayTime(null));
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

    @Test
    void postWithoutAValidCsrfTokenIsRefused() throws IOException, ServletException {
        assertRefused(new GuideCommentsPageServlet(), null, null);
        assertRefused(new GuideRegistrationPageServlet(), null, null);
        assertRefused(new GuideCommentsPageServlet(), "expected-token", "other-token");
        assertRefused(new GuideRegistrationPageServlet(), "expected-token", null);
    }

    private static int count(String html, String token) {
        int found = 0;
        int from = 0;
        while (true) {
            int at = html.indexOf(token, from);
            if (at < 0) {
                return found;
            }
            found++;
            from = at + token.length();
        }
    }

    private static String html(View view) {
        var writer = new StringWriter();
        TopicReferenceSection.write(new PrintWriter(writer), view);
        return writer.toString();
    }

    private static EsTopic topic(long id) {
        EsTopic topic = new EsTopic();
        topic.setEsTopicId(id);
        return topic;
    }

    private static User user(long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }

    private static void assertRefused(jakarta.servlet.http.HttpServlet servlet, String sessionToken, String postedToken)
            throws IOException, ServletException {
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (sessionToken != null) {
            sessionAttributes.put(CsrfTokenSupport.class.getName() + ".token", sessionToken);
        }
        HttpSession session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
                new Class<?>[] {HttpSession.class}, (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName())) {
                        return sessionAttributes.get(args[0]);
                    }
                    return defaultValue(method.getReturnType());
                });
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class}, (proxy, method, args) -> {
                    if ("getSession".equals(method.getName())) {
                        return sessionToken == null ? null : session;
                    }
                    if ("getParameter".equals(method.getName())) {
                        return "csrfToken".equals(args[0]) ? postedToken : null;
                    }
                    if ("getHeader".equals(method.getName())) {
                        return null;
                    }
                    if ("getMethod".equals(method.getName())) {
                        return "POST";
                    }
                    return defaultValue(method.getReturnType());
                });
        int[] status = {0};
        boolean[] redirected = {false};
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class}, (proxy, method, args) -> {
                    if ("sendError".equals(method.getName()) || "setStatus".equals(method.getName())) {
                        status[0] = (Integer) args[0];
                    }
                    if ("sendRedirect".equals(method.getName())) {
                        redirected[0] = true;
                    }
                    return defaultValue(method.getReturnType());
                });

        servlet.service(request, response);

        assertEquals(403, status[0]);
        assertFalse(redirected[0]);
    }

    private static Object defaultValue(Class<?> type) {
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(int.class)) {
            return 0;
        }
        if (type.equals(long.class)) {
            return 0L;
        }
        return null;
    }
}
