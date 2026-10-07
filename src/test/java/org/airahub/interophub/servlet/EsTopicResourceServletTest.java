package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.*;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.CommunicationBundleService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.junit.jupiter.api.Test;

class EsTopicResourceServletTest {
    @Test
    void anonymousUsersAndNonStewardFollowersCannotOpenEditorOrMutate() throws IOException {
        Fixture f = new Fixture();
        f.user = null;
        f.servlet().doGet(f.request(), f.response());
        assertEquals(403, f.status);
        f.user = new User();
        f.canEdit = false;
        f.servlet().doPost(f.request(), f.response());
        assertEquals(403, f.status);
        assertFalse(f.mutated);
    }

    @Test
    void hiddenTopicsAndInvalidPathsReturnNotFound() throws IOException {
        Fixture f = new Fixture();
        f.canView = false;
        f.servlet().doGet(f.request(), f.response());
        assertEquals(404, f.status);
        f.canView = true;
        f.path = "/10/11";
        f.servlet().doGet(f.request(), f.response());
        assertEquals(404, f.status);
    }

    @Test
    void missingCsrfTokenIsRejectedBeforeMutation() throws IOException {
        Fixture f = new Fixture();
        f.parameters.put("action", "createDraft");
        f.servlet().doPost(f.request(), f.response());
        assertEquals(403, f.status);
        assertFalse(f.mutated);
    }

    @Test
    void validStewardPostCreatesDraftAndRedirectsWithFlash() throws IOException {
        Fixture f = new Fixture();
        var request = f.request();
        f.parameters.put("csrfToken", CsrfTokenSupport.getOrCreateToken(request));
        f.parameters.put("action", "createDraft");
        f.servlet().doPost(request, f.response());
        assertTrue(f.mutated);
        assertEquals("/hub/es/topic-resources/10", f.redirect);
        assertTrue(f.sessionAttributes.values().contains("Orientation draft created."));
    }

    private static class Fixture {
        User user = new User();
        boolean canEdit = true;
        boolean canView = true;
        boolean mutated;
        int status = 200;
        String path = "/10";
        String redirect;
        Map<String, String> parameters = new HashMap<>();
        Map<String, Object> sessionAttributes = new HashMap<>();
        HttpSession session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
                new Class<?>[] {HttpSession.class}, (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName())) { return sessionAttributes.get(args[0]); }
                    if ("setAttribute".equals(method.getName())) { sessionAttributes.put((String) args[0], args[1]); }
                    return null;
                });

        EsTopicResourceServlet servlet() {
            return new EsTopicResourceServlet(new AuthFlowService() {
                @Override public Optional<User> findAuthenticatedUser(HttpServletRequest request) {
                    return Optional.ofNullable(user);
                }
            }, new EsTopicDao() {
                @Override public Optional<EsTopic> findById(Long id) {
                    EsTopic topic = new EsTopic();
                    topic.setEsTopicId(10L);
                    return Optional.of(topic);
                }
            }, new TopicSpaceAccessService() {
                @Override public boolean canViewTopic(User viewer, EsTopic topic) { return canView; }
                @Override public boolean canEditTopic(User viewer, EsTopic topic) { return canEdit; }
            }, new CommunicationBundleService() {
                @Override public org.airahub.interophub.model.EsCommunicationBundle createTopicOrientationDraft(
                        User viewer, Long topicId) {
                    assertEquals(10L, topicId);
                    mutated = true;
                    return new org.airahub.interophub.model.EsCommunicationBundle();
                }
            });
        }

        HttpServletRequest request() {
            return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getPathInfo" -> path;
                        case "getContextPath" -> "/hub";
                        case "getParameter" -> parameters.get(args[0]);
                        case "getSession" -> session;
                        default -> null;
                    });
        }

        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class}, (proxy, method, args) -> {
                        if ("sendError".equals(method.getName())) { status = (Integer) args[0]; }
                        if ("sendRedirect".equals(method.getName())) { redirect = (String) args[0]; }
                        return null;
                    });
        }
    }
}
