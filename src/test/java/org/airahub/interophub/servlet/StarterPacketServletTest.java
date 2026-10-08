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
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.*;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.StarterPacketService;
import org.junit.jupiter.api.Test;

class StarterPacketServletTest {
    @Test
    void unauthorizedGetAndPostAreDeniedWithoutMutation() throws IOException {
        var f = new Fixture();
        f.allowed = false;
        f.servlet().doGet(f.request(), f.response());
        assertEquals(403, f.status);
        assertFalse(f.rendered);
        f.servlet().doPost(f.request(), f.response());
        assertEquals(403, f.status);
        assertTrue(f.operations.isEmpty());
    }

    @Test
    void invalidPathsAreValidationErrors() throws IOException {
        for (String path : List.of("/0", "/-1", "/10/12", "/", "/999999999999999999999")) {
            var f = new Fixture();
            f.path = path;
            f.servlet().doGet(f.request(), f.response());
            assertEquals(400, f.status, path);
        }
    }

    @Test
    void everyMutationRequiresCsrfIncludingMultipart() throws IOException {
        for (String action : List.of("create", "copy", "settings", "text", "list", "select", "remove",
                "moveUp", "moveDown", "context", "publish", "retire", "link", "upload")) {
            var f = new Fixture();
            f.parameters.put("action", action);
            if ("upload".equals(action)) {
                f.contentType = "multipart/form-data; boundary=example";
                f.parts.add(f.part(3));
            }
            f.servlet().doPost(f.request(), f.response());
            assertEquals(403, f.status, action);
            assertTrue(f.operations.isEmpty(), action);
            assertEquals("upload".equals(action), f.partDeleted);
        }
    }

    @Test
    void createAndCopyRedirectToTheNewDraft() throws IOException {
        var f = new Fixture();
        f.post("create");
        assertEquals(List.of("create:null"), f.operations);
        assertEquals("/hub/es/starter-packets/10?bundleId=99", f.redirect);
        f = new Fixture();
        f.packet.bundle().setStatus(EsCommunicationBundle.Status.RETIRED);
        f.post("copy");
        assertEquals(List.of("create:12"), f.operations);
        assertEquals("/hub/es/starter-packets/10?bundleId=99", f.redirect);
    }

    @Test
    void textAndListShareKindAwareServiceAndKeepSubmittedTextOnRejection() throws IOException {
        for (String action : List.of("text", "list")) {
            var f = new Fixture();
            f.parameters.put("content", "<Rejected text>");
            f.failure = new IllegalArgumentException("Content rejected.");
            f.post(action);
            assertEquals(400, f.status);
            assertEquals("Content rejected.", f.error);
            assertEquals("<Rejected text>", f.submitted.get("content"));
            assertEquals("1", f.submitted.get("componentId"));
            assertEquals(List.of("value:1:<Rejected text>"), f.operations);
        }
    }

    @Test
    void publishedOrRetiredPacketsRejectDraftEditsIncludingResourceCreation() throws IOException {
        for (var status : List.of(EsCommunicationBundle.Status.PUBLISHED, EsCommunicationBundle.Status.RETIRED)) {
            for (String action : List.of("settings", "text", "list", "select", "remove", "moveUp",
                    "moveDown", "context", "publish", "upload", "link")) {
                var f = new Fixture();
                f.packet.bundle().setStatus(status);
                f.post(action);
                assertEquals(400, f.status, status + ":" + action);
                assertTrue(f.operations.isEmpty(), action);
            }
        }
    }

    @Test
    void publicationRequiresExactExplicitConfirmationAndReportsStorageFailure() throws IOException {
        var f = new Fixture();
        f.post("publish");
        assertEquals(400, f.status);
        assertTrue(f.operations.isEmpty());
        f = new Fixture();
        f.parameters.put("preserveConfirmed", "true");
        f.publishFailure = new IOException("Preservation unavailable");
        f.post("publish");
        assertEquals(503, f.status);
        assertEquals(List.of("publish:true"), f.operations);
        assertNull(f.redirect);
        assertTrue(f.responseError.contains("draft remains recoverable"));
        f = new Fixture();
        f.parameters.put("preserveConfirmed", "true");
        f.post("publish");
        assertEquals("/hub/es/starter-packets/10?bundleId=12", f.redirect);
    }

    @Test
    void onlyPublishedPacketsCanBeRetiredAndDraftCannotBeCopied() throws IOException {
        var f = new Fixture();
        f.post("retire");
        assertEquals(400, f.status);
        assertTrue(f.operations.isEmpty());
        f = new Fixture();
        f.post("copy");
        assertEquals(400, f.status);
        assertTrue(f.operations.isEmpty());
        f = new Fixture();
        f.packet.bundle().setStatus(EsCommunicationBundle.Status.PUBLISHED);
        f.post("retire");
        assertEquals(List.of("retire"), f.operations);
    }

    @Test
    void crossTopicBundleIdsCannotBeMutated() throws IOException {
        var f = new Fixture();
        f.packet.bundle().setEsTopicId(20L);
        f.post("text");
        assertEquals(403, f.status);
        assertTrue(f.operations.isEmpty());
    }

    @Test
    void settingsAndResourceActionsDispatchTheirExactIdentifiers() throws IOException {
        Map<String, String> expected = Map.of(
                "settings", "settings:10:2026", "select", "select:1:41", "remove", "remove:51",
                "moveUp", "move:51:true", "moveDown", "move:51:false", "context", "context:51:Context",
                "link", "link:https://example.org:Resource");
        for (var entry : expected.entrySet()) {
            var f = new Fixture();
            f.post(entry.getKey());
            assertEquals(List.of(entry.getValue()), f.operations);
            assertEquals("/hub/es/starter-packets/10?bundleId=12", f.redirect);
        }
        var f = new Fixture();
        f.parameters.put("communicationMonth", "October");
        f.post("settings");
        assertEquals(400, f.status);
        assertTrue(f.operations.isEmpty());
    }

    @Test
    void uploadsEnforceFileShapeAndSizeAndAlwaysDeleteParts() throws IOException {
        var f = new Fixture();
        f.contentType = "multipart/form-data; boundary=example";
        f.parts.add(f.part(3));
        f.post("upload");
        assertEquals(List.of("upload:example.png:Resource"), f.operations);
        assertTrue(f.partDeleted);
        assertTrue(f.inputClosed);
        for (long size : List.of(0L, 25L * 1024 * 1024 + 1)) {
            f = new Fixture();
            f.contentType = "multipart/form-data";
            f.parts.add(f.part(size));
            f.post("upload");
            assertEquals(400, f.status);
            assertTrue(f.operations.isEmpty());
            assertTrue(f.partDeleted);
        }
        f = new Fixture();
        f.contentType = "multipart/form-data";
        f.parts.add(f.part(3));
        f.parts.add(f.part(3));
        f.post("upload");
        assertEquals(400, f.status);
        assertTrue(f.operations.isEmpty());
        assertTrue(f.partDeleted);
    }

    @Test
    void operationalFailuresAreNotReportedAsValidationErrors() throws IOException {
        var f = new Fixture();
        f.failure = new IllegalStateException("Database unavailable");
        f.post("text");
        assertEquals(503, f.status);
        assertNull(f.redirect);
    }

    @Test
    void unknownActionsAreExplicitlyRejected() throws IOException {
        var f = new Fixture();
        f.post("unknown");
        assertEquals(400, f.status);
        assertTrue(f.operations.isEmpty());
        assertEquals("Unknown Starter Packet operation.", f.error);
    }

    static class Fixture {
        User user = new User();
        boolean allowed = true;
        boolean rendered;
        boolean partDeleted;
        boolean inputClosed;
        int status = 200;
        String path = "/10";
        String contentType;
        String redirect;
        String error;
        String responseError;
        RuntimeException failure;
        IOException publishFailure;
        Map<String, String> submitted = Map.of();
        Map<String, String> parameters = new HashMap<>();
        Map<String, Object> attributes = new HashMap<>();
        List<String> operations = new ArrayList<>();
        List<Part> parts = new ArrayList<>();
        OrientationResources packet = StarterPacketRendererTest.packet(12L, EsCommunicationBundle.Status.DRAFT);
        HttpSession session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
                new Class<?>[] {HttpSession.class}, (proxy, method, args) -> {
                    if ("getAttribute".equals(method.getName())) { return attributes.get(args[0]); }
                    if ("setAttribute".equals(method.getName())) { attributes.put((String) args[0], args[1]); }
                    return null;
                });

        Fixture() {
            parameters.putAll(Map.of("bundleId", "12", "componentId", "1", "resourceId", "41", "placementId", "51",
                    "communicationMonth", "10", "communicationYear", "2026", "contextNote", "Context",
                    "content", "Content", "title", "Resource", "externalUrl", "https://example.org"));
        }

        void post(String action) throws IOException {
            parameters.put("action", action);
            var request = request();
            parameters.put("csrfToken", CsrfTokenSupport.getOrCreateToken(request));
            servlet().doPost(request, response());
        }

        AuthFlowService auth() {
            return new AuthFlowService() {
                @Override public Optional<User> findAuthenticatedUser(HttpServletRequest request) {
                    return Optional.ofNullable(user);
                }
            };
        }

        StarterPacketService service() {
            return new StarterPacketService() {
                @Override public EsTopic requireAccess(User viewer, Long topicId) {
                    if (!allowed || viewer == null) { throw new SecurityException("Access denied."); }
                    var topic = new EsTopic();
                    topic.setEsTopicId(topicId);
                    topic.setTopicName("Example Topic");
                    return topic;
                }
                @Override public OrientationResources get(User viewer, Long bundleId) {
                    requireAccess(viewer, packet.bundle().getEsTopicId());
                    assertEquals(12L, bundleId);
                    return packet;
                }
                @Override public EsCommunicationBundle create(User viewer, Long topicId, Long sourceBundleId) {
                    record("create:" + sourceBundleId);
                    var bundle = new EsCommunicationBundle();
                    bundle.setBundleId(99L);
                    return bundle;
                }
                @Override public void saveValue(User viewer, Long topicId, Long bundleId, Long componentId, String content) {
                    record("value:" + componentId + ":" + content);
                }
                @Override public void saveSettings(User viewer, Long topicId, Long bundleId, Integer month, Integer year) {
                    record("settings:" + month + ":" + year);
                }
                @Override public void selectResource(User viewer, Long topicId, Long bundleId, Long componentId, Long resourceId) {
                    record("select:" + componentId + ":" + resourceId);
                }
                @Override public void removeResource(User viewer, Long topicId, Long bundleId, Long placementId) {
                    record("remove:" + placementId);
                }
                @Override public void moveResource(User viewer, Long topicId, Long bundleId, Long placementId, boolean up) {
                    record("move:" + placementId + ":" + up);
                }
                @Override public void saveContext(User viewer, Long topicId, Long bundleId, Long placementId, String note) {
                    record("context:" + placementId + ":" + note);
                }
                @Override public void publish(User viewer, Long topicId, Long bundleId, boolean confirmed) throws IOException {
                    record("publish:" + confirmed);
                    if (publishFailure != null) { throw publishFailure; }
                }
                @Override public void retire(User viewer, Long topicId, Long bundleId) { record("retire"); }
                @Override public EsTopicResource addLink(User viewer, Long topicId, String url, String title) {
                    record("link:" + url + ":" + title);
                    return new EsTopicResource();
                }
                @Override public EsTopicResource upload(User viewer, Long topicId, InputStream input, String filename,
                        String type, String title) {
                    record("upload:" + filename + ":" + title);
                    return new EsTopicResource();
                }
            };
        }

        void record(String operation) {
            operations.add(operation);
            if (failure != null) { throw failure; }
        }

        StarterPacketServlet servlet() {
            return new StarterPacketServlet(auth(), service(), new EsTopicSpaceDao()) {
                @Override void render(HttpServletRequest request, HttpServletResponse response, EsTopic topic, User viewer,
                        String message, Map<String, String> values) {
                    rendered = true;
                    error = message;
                    submitted = values;
                }
            };
        }

        Part part(long size) {
            return (Part) Proxy.newProxyInstance(Part.class.getClassLoader(), new Class<?>[] {Part.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getName" -> "resourceFile";
                        case "getSubmittedFileName" -> "example.png";
                        case "getContentType" -> "image/png";
                        case "getSize" -> size;
                        case "getInputStream" -> new ByteArrayInputStream(new byte[] {1, 2, 3}) {
                            @Override public void close() throws IOException {
                                inputClosed = true;
                                super.close();
                            }
                        };
                        case "delete" -> { partDeleted = true; yield null; }
                        default -> null;
                    });
        }

        HttpServletRequest request() {
            return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                    new Class<?>[] {HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getPathInfo" -> path;
                        case "getContextPath" -> "/hub";
                        case "getParameter" -> parameters.get(args[0]);
                        case "getSession" -> session;
                        case "getContentType" -> contentType;
                        case "getParts" -> parts;
                        default -> null;
                    });
        }

        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                    new Class<?>[] {HttpServletResponse.class}, (proxy, method, args) -> {
                        if ("setStatus".equals(method.getName())) { status = (Integer) args[0]; }
                        if ("sendError".equals(method.getName())) {
                            status = (Integer) args[0];
                            responseError = args.length > 1 ? (String) args[1] : null;
                        }
                        if ("sendRedirect".equals(method.getName())) { redirect = (String) args[0]; }
                        if ("isCommitted".equals(method.getName())) { return false; }
                        return null;
                    });
        }
    }
}
