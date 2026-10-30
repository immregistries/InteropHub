package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.*;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicResource;
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

    @Test
    void narrativeSavePostsEveryComponentAndARejectionReopensEditMode() throws IOException {
        Fixture f = new Fixture();
        f.parameters.put("bundleId", "30");
        f.parameters.put("content-30", "Hello");
        f.parameters.put("content-31", "World");
        f.post("narrative");
        assertEquals(Map.of(30L, "Hello", 31L, "World"), f.narrative);
        assertEquals("/hub/es/topic-resources/10", f.redirect);

        f = new Fixture();
        f.failure = new IllegalArgumentException("Narrative rejected.");
        f.parameters.put("bundleId", "30");
        f.parameters.put("content-30", "Draft");
        f.post("narrative");
        assertEquals(400, f.status);
        assertEquals("narrative", f.rendered.openDialog());
        assertEquals("Draft", f.rendered.posted("content-30"));
    }

    @Test
    void linkAndUploadIntoARoleRedirectToThatRoleAndARejectionReopensTheDialog() throws IOException {
        Fixture f = new Fixture();
        f.parameters.put("title", "Guide");
        f.parameters.put("externalUrl", "https://example.org/guide");
        f.parameters.put("componentId", "21");
        f.post("link");
        assertEquals("link:21", f.lastOperation);
        assertEquals("/hub/es/topic-resources/10#role-21", f.redirect);
        assertTrue(f.sessionAttributes.values().stream().anyMatch(value -> "Guide added to Additional resources.".equals(value)));

        f = new Fixture();
        f.parameters.put("title", "Guide");
        f.parameters.put("externalUrl", "https://example.org/guide");
        f.post("link");
        assertEquals("link:null", f.lastOperation);
        assertEquals("/hub/es/topic-resources/10", f.redirect);
        assertTrue(f.sessionAttributes.values().contains("External link added."));

        f = new Fixture();
        f.contentType = "multipart/form-data; boundary=example";
        f.parts.add(f.filePart());
        f.parameters.put("title", "Poster");
        f.parameters.put("componentId", "20");
        f.post("upload");
        assertEquals("upload:20", f.lastOperation);
        assertEquals("/hub/es/topic-resources/10#role-20", f.redirect);
        assertTrue(f.sessionAttributes.values().contains("Poster added to Infographic."));

        f = new Fixture();
        f.failure = new IllegalArgumentException("External URL is invalid.");
        f.parameters.put("title", "Rejected title");
        f.parameters.put("externalUrl", "notaurl");
        f.parameters.put("componentId", "21");
        f.post("link");
        assertEquals(400, f.status);
        assertEquals("add-resource-21", f.rendered.openDialog());
        assertEquals("Rejected title", f.rendered.posted("title"));
        assertEquals("notaurl", f.rendered.posted("externalUrl"));

        f = new Fixture();
        f.contentType = "multipart/form-data; boundary=example";
        f.parameters.put("title", "Poster");
        f.parameters.put("componentId", "21");
        f.post("upload");
        assertEquals(400, f.status);
        assertEquals("add-resource-21", f.rendered.openDialog());
        assertEquals("Poster", f.rendered.posted("title"));
        assertTrue(f.rendered.error().contains("Choose the file again."));
    }

    private static class Fixture {
        User user = new User();
        boolean canEdit = true;
        boolean canView = true;
        boolean mutated;
        int status = 200;
        String path = "/10";
        String redirect;
        String contentType;
        String lastOperation;
        RuntimeException failure;
        FormInput rendered;
        Map<Long, String> narrative;
        Map<String, String> parameters = new HashMap<>();
        List<Part> parts = new ArrayList<>();
        Map<String, Object> sessionAttributes = new HashMap<>();
        HttpSession session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
                new Class<?>[] {HttpSession.class}, (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName())) { return sessionAttributes.get(args[0]); }
                    if ("setAttribute".equals(method.getName())) { sessionAttributes.put((String) args[0], args[1]); }
                    return null;
                });

        void post(String action) throws IOException {
            parameters.put("action", action);
            var http = request();
            parameters.put("csrfToken", CsrfTokenSupport.getOrCreateToken(http));
            servlet().doPost(http, response());
        }

        Part filePart() {
            return (Part) Proxy.newProxyInstance(Part.class.getClassLoader(), new Class<?>[] {Part.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getName" -> "resourceFile";
                        case "getSubmittedFileName" -> "poster.png";
                        case "getContentType" -> "image/png";
                        case "getSize" -> 3L;
                        case "getInputStream" -> new ByteArrayInputStream(new byte[] {1, 2, 3});
                        case "delete" -> null;
                        default -> null;
                    });
        }

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
                @Override public void saveNarrativeValues(User viewer, Long topicId, Long bundleId, Map<Long, String> values) {
                    lastOperation = "narrative";
                    narrative = values;
                    if (failure != null) {
                        throw failure;
                    }
                }
                @Override public CommunicationBundleService.LibraryAddition registerLinkIntoRole(User viewer, Long topicId,
                        String url, String title, String description, String attribution, Long componentId) {
                    lastOperation = "link:" + componentId;
                    if (failure != null) {
                        throw failure;
                    }
                    EsTopicResource resource = new EsTopicResource();
                    resource.setTitle(title);
                    return new CommunicationBundleService.LibraryAddition(resource,
                            componentId == null ? null : "Additional resources");
                }
                @Override public CommunicationBundleService.LibraryAddition uploadResourceIntoRole(User viewer, Long topicId,
                        InputStream input, String filename, String type, String title, String description,
                        String attribution, Long componentId) {
                    lastOperation = "upload:" + componentId;
                    if (failure != null) {
                        throw failure;
                    }
                    EsTopicResource resource = new EsTopicResource();
                    resource.setTitle(title);
                    return new CommunicationBundleService.LibraryAddition(resource,
                            componentId == null ? null : "Infographic");
                }
            }) {
                @Override void render(HttpServletRequest request, HttpServletResponse response, EsTopic topic,
                        User viewer, FormInput input) {
                    rendered = input;
                }
            };
        }

        HttpServletRequest request() {
            return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getPathInfo" -> path;
                        case "getContextPath" -> "/hub";
                        case "getParameter" -> parameters.get(args[0]);
                        case "getParameterMap" -> {
                            Map<String, String[]> values = new HashMap<>();
                            parameters.forEach((key, value) -> values.put(key, new String[] {value}));
                            yield values;
                        }
                        case "getSession" -> session;
                        case "getContentType" -> contentType;
                        case "getParts" -> parts;
                        case "setCharacterEncoding" -> null;
                        default -> null;
                    });
        }

        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class}, (proxy, method, args) -> {
                        if ("setStatus".equals(method.getName())) { status = (Integer) args[0]; }
                        if ("sendError".equals(method.getName())) { status = (Integer) args[0]; }
                        if ("sendRedirect".equals(method.getName())) { redirect = (String) args[0]; }
                        return null;
                    });
        }
    }
}
