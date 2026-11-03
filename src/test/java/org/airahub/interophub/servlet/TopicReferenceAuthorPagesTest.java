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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void postWithoutAValidCsrfTokenIsRefused() throws IOException, ServletException {
        assertRefused(new GuideCommentsPageServlet(), null, null);
        assertRefused(new GuideRegistrationPageServlet(), null, null);
        assertRefused(new GuideCommentsPageServlet(), "expected-token", "other-token");
        assertRefused(new GuideRegistrationPageServlet(), "expected-token", null);
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
