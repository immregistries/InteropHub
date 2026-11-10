package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.config.ArtifactStorageConfig;
import org.airahub.interophub.dao.StoredFileDao;
import org.airahub.interophub.model.StoredFile;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.HostedVersion;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.airahub.interophub.servlet.GuideApiServlet;
import org.airahub.interophub.servlet.GuideViewServlet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class GuideHostingTest {
    private static final byte[] FIRST = "<!DOCTYPE html><html><body>first</body></html>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECOND = "<!DOCTYPE html><html><body>second</body></html>".getBytes(StandardCharsets.UTF_8);

    @Test
    void labelAndHtmlRules() {
        assertEquals("latest", GuideHtmlRules.label("latest"));
        assertEquals("main", GuideHtmlRules.label(" main "));
        assertEquals("v0.13.0", GuideHtmlRules.label("v0.13.0"));
        assertEquals("v1.2.3-rc1", GuideHtmlRules.label("v1.2.3-rc1"));
        assertField("label", () -> GuideHtmlRules.label("Latest"));
        assertField("label", () -> GuideHtmlRules.label("v"));
        assertField("label", () -> GuideHtmlRules.label("v1."));
        assertField("label", () -> GuideHtmlRules.label("v1.2.3-"));

        GuideHtmlRules.html(FIRST, "text/html");
        GuideHtmlRules.html(FIRST, "text/html; charset=utf-8");
        byte[] bom = new byte[FIRST.length + 3];
        bom[0] = (byte) 0xEF;
        bom[1] = (byte) 0xBB;
        bom[2] = (byte) 0xBF;
        System.arraycopy(FIRST, 0, bom, 3, FIRST.length);
        GuideHtmlRules.html(bom, "text/html");
        GuideHtmlRules.html("\n <html></html>".getBytes(StandardCharsets.UTF_8), "text/html");
        assertField("type", () -> GuideHtmlRules.html(FIRST, "application/octet-stream"));
        assertField("type", () -> GuideHtmlRules.html("<p>no</p>".getBytes(StandardCharsets.UTF_8), "text/html"));
        assertField("size", () -> GuideHtmlRules.html(new byte[0], "text/html"));
        assertField("size", () -> GuideHtmlRules.html(new byte[(int) GuideHtmlRules.MAX_BYTES + 1], "text/html"));
    }

    @Test
    void generalUploadStillRefusesHtmlWhilePreparedStorageAcceptsIt() throws Exception {
        Path file = Files.createTempFile("guide", ".html");
        Files.write(file, FIRST);
        assertThrows(IllegalArgumentException.class,
                () -> StoredFileValidation.validate(file, "guide.html", "text/html"));
        Files.deleteIfExists(file);

        Path root = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "guide-html-");
        StoredFile[] saved = new StoredFile[1];
        StoredFileService storage = new StoredFileService(new ArtifactStorageConfig(root.toString(), null, null),
                () -> true, new StoredFileDao() {
                    @Override
                    public StoredFile save(StoredFile entity) {
                        saved[0] = new StoredFile(entity);
                        saved[0].setStoredFileId(4L);
                        return saved[0];
                    }
                });
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> storage.upload(null, new ByteArrayInputStream(FIRST), "guide.html", "text/html", 7L));
            long id = new GuideHtmlFiles(storage, new StoredFileDao() {
                @Override
                public StoredFile save(StoredFile entity) {
                    saved[0] = new StoredFile(entity);
                    saved[0].setStoredFileId(4L);
                    return saved[0];
                }
            }).store(FIRST, 7L);
            assertEquals(4L, id);
            assertTrue(saved[0].isDownloadOnly());
            assertEquals("text/html", saved[0].getContentType());
            assertFalse(StoredFileService.inline(saved[0]));
            assertArrayEquals(FIRST, new GuideHtmlFiles(storage, new StoredFileDao() {
                @Override
                public java.util.Optional<StoredFile> findById(Long storedFileId) {
                    return java.util.Optional.of(saved[0]);
                }
            }).read(4L));
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void uploadKeepsHistoryAndHidesStorageIdentity() {
        Memory memory = hosted();
        User author = user(5);
        GuideHostingService.UploadResult created = memory.service.upload(author, 9L, "API", "guide-hosted-test",
                "latest", FIRST, "text/html");
        assertEquals(201, created.status());
        assertEquals(Set.of("label", "sizeBytes", "uploadedBy", "uploadedAt", "viewUrl"), created.version().keySet());
        assertEquals("Guide Author", created.version().getString("uploadedBy"));
        assertEquals("http://localhost:8080/hub/guide/view/guide-hosted-test/latest/",
                created.version().getString("viewUrl"));
        assertFalse(created.version().toString().contains("public"));

        GuideHostingService.UploadResult replaced = memory.service.upload(author, 9L, "API", "guide-hosted-test",
                "latest", SECOND, "text/html; charset=utf-8");
        assertEquals(200, replaced.status());
        memory.service.upload(author, 9L, "API", "guide-hosted-test", "v1.2.0", FIRST, "text/html");

        JSONArray versions = memory.service.list(author, "guide-hosted-test").getJSONArray("versions");
        assertEquals("latest", versions.getJSONObject(0).getString("label"));
        assertEquals(SECOND.length, versions.getJSONObject(0).getInt("sizeBytes"));
        assertEquals(2, versions.getJSONObject(0).getInt("historyCount"));
        assertEquals(1, versions.getJSONObject(0).getJSONArray("earlier").length());
        assertEquals(FIRST.length, versions.getJSONObject(0).getJSONArray("earlier").getJSONObject(0).getInt("sizeBytes"));
        assertEquals("v1.2.0", versions.getJSONObject(1).getString("label"));
        assertFalse(versions.toString().contains("public_id"));
        assertFalse(versions.toString().contains("blob"));
        assertTrue(memory.changes.get(0).contains("\"replaced\":false"));
        assertTrue(memory.changes.get(1).contains("\"replaced\":true"));
        assertFalse(String.join("\n", memory.changes).contains("public"));
        assertArrayEquals(SECOND, memory.service.open(author, "guide-hosted-test", "latest"));
    }

    @Test
    void uploadIsRefusedForAReaderTheWrongScopeAndADocumentThatIsNotHosted() throws Exception {
        Memory memory = hosted();
        User reader = user(2);
        memory.view(2);
        GuideApiException forbidden = assertThrows(GuideApiException.class,
                () -> memory.service.upload(reader, 3L, "API", "guide-hosted-test", "latest", FIRST, "text/html"));
        assertEquals("forbidden", forbidden.getError());
        assertEquals(403, forbidden.getStatus());

        memory.documents.get("guide-hosted-test").hosting = "EXTERNAL";
        memory.edit(5);
        GuideApiException state = assertThrows(GuideApiException.class,
                () -> memory.service.upload(user(5), 3L, "API", "guide-hosted-test", "latest", FIRST, "text/html"));
        assertEquals("invalid_state", state.getError());

        memory.documents.get("guide-hosted-test").hosting = "HUB";
        memory.documents.get("guide-hosted-test").active = false;
        GuideApiException inactive = assertThrows(GuideApiException.class,
                () -> memory.service.upload(user(5), 3L, "API", "guide-hosted-test", "latest", FIRST, "text/html"));
        assertEquals("document_inactive", inactive.getError());
        assertEquals(0, memory.stores);

        Captured captured = new Captured();
        GuideApiServlet servlet = new GuideApiServlet(null, null, null, memory.service);
        servlet.service(request("/api/guide/guide-hosted-test/versions/latest", "POST", Set.of(GuideScopes.COMMENTS),
                user(5), FIRST), response(captured));
        assertEquals(403, captured.status);
        assertTrue(captured.body().contains("insufficient_scope"));
        assertEquals(0, memory.stores);
    }

    @Test
    void servingRedirectsSignsInAndRefusesWithoutRevealingTheGuide() throws Exception {
        Memory memory = hosted();
        User author = user(5);
        memory.service.upload(author, 1L, "API", "guide-hosted-test", "latest", FIRST, "text/html");
        FakeAuth auth = new FakeAuth();
        GuideViewServlet servlet = new GuideViewServlet(memory.service, auth);

        Captured slash = exchange(servlet, "/guide-hosted-test/latest", auth);
        assertEquals(302, slash.status);
        assertEquals("/hub/guide/view/guide-hosted-test/latest/", slash.headers.get("Location"));

        Captured signedOut = exchange(servlet, "/guide-hosted-test/latest/", auth);
        assertEquals(302, signedOut.status);
        assertEquals("/hub/home", signedOut.headers.get("Location"));
        assertEquals("/guide/view/guide-hosted-test/latest/", auth.guideReturn);

        auth.user = Optional.of(user(2));
        Captured refused = exchange(servlet, "/guide-hosted-test/latest/", auth);
        assertEquals(404, refused.status);
        assertTrue(refused.body().contains("could not be found"));
        assertFalse(refused.body().contains("guide-hosted-test"));

        memory.view(2);
        memory.documents.get("guide-hosted-test").active = false;
        Captured inactive = exchange(servlet, "/guide-hosted-test/latest/", auth);
        assertEquals(404, inactive.status);
        assertFalse(inactive.body().contains("guide-hosted-test"));

        memory.documents.get("guide-hosted-test").active = true;
        memory.documents.get("guide-hosted-test").hosting = "EXTERNAL";
        assertNull(memory.service.open(user(2), "guide-hosted-test", "latest"));
        memory.documents.get("guide-hosted-test").hosting = "HUB";
        assertNull(memory.service.open(user(2), "guide-hosted-test", "v9.9.9"));
        Captured extra = exchange(servlet, "/guide-hosted-test/latest/extra", auth);
        assertEquals(404, extra.status);

        Captured allowed = exchange(servlet, "/guide-hosted-test/latest/", auth);
        assertEquals(200, allowed.status);
        assertEquals("text/html; charset=utf-8", allowed.headers.get("Content-Type"));
        assertEquals("private, no-store", allowed.headers.get("Cache-Control"));
        assertEquals("noindex", allowed.headers.get("X-Robots-Tag"));
        assertEquals("nosniff", allowed.headers.get("X-Content-Type-Options"));
        assertEquals("same-origin", allowed.headers.get("Referrer-Policy"));
        assertArrayEquals(FIRST, allowed.bytes.toByteArray());
        assertFalse(allowed.headers.containsKey("Content-Security-Policy"));
        assertFalse(allowed.body().contains("public_id"));
    }

    private static void assertField(String field, org.junit.jupiter.api.function.Executable action) {
        GuideApiException ex = assertThrows(GuideApiException.class, action);
        assertEquals("invalid_field", ex.getError());
        assertEquals(field, ex.getField());
    }

    private static Memory hosted() {
        Memory memory = new Memory();
        TopicRow topic = new TopicRow();
        topic.id = 8;
        topic.code = "GUIDE-PRIVATE-TEST";
        topic.name = "Guide Private Test";
        memory.topics.put(8L, topic);
        DocumentRow document = new DocumentRow();
        document.id = 3L;
        document.documentKey = "guide-hosted-test";
        document.topicId = 8;
        document.active = true;
        document.hosting = "HUB";
        memory.documents.put(document.documentKey, document);
        memory.view(5);
        memory.edit(5);
        return memory;
    }

    private static User user(long id) {
        User user = new User();
        user.setUserId(id);
        user.setDisplayName("Guide Author");
        user.setOrganization("Example Health Department");
        return user;
    }

    private static Captured exchange(GuideViewServlet servlet, String path, FakeAuth auth) throws Exception {
        Captured captured = new Captured();
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "GET";
                    case "getPathInfo" -> path;
                    case "getContextPath" -> "/hub";
                    case "getQueryString" -> null;
                    default -> defaultValue(method.getReturnType());
                });
        servlet.service(request, response(captured));
        return captured;
    }

    private static HttpServletRequest request(String uri, String method, Set<String> scopes, User caller, byte[] body) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getContentType" -> "text/html";
                    case "getInputStream" -> new ServletInputStream() {
                        private final ByteArrayInputStream input = new ByteArrayInputStream(body);
                        public int read() { return input.read(); }
                        public boolean isFinished() { return input.available() == 0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) { }
                    };
                    case "getAttribute" -> {
                        String name = (String) args[0];
                        if (GuideRequestAuth.USER.equals(name)) {
                            yield caller;
                        }
                        if (GuideRequestAuth.SCOPES.equals(name)) {
                            yield scopes;
                        }
                        yield null;
                    }
                    default -> defaultValue(m.getReturnType());
                });
    }

    private static HttpServletResponse response(Captured captured) {
        return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "setStatus" -> {
                        captured.status = (Integer) args[0];
                        yield null;
                    }
                    case "sendRedirect" -> {
                        captured.status = 302;
                        captured.headers.put("Location", (String) args[0]);
                        yield null;
                    }
                    case "sendError" -> {
                        captured.status = (Integer) args[0];
                        yield null;
                    }
                    case "setHeader" -> {
                        captured.headers.put((String) args[0], (String) args[1]);
                        yield null;
                    }
                    case "setContentType" -> {
                        captured.headers.put("Content-Type", (String) args[0]);
                        yield null;
                    }
                    case "setCharacterEncoding", "setContentLength", "setContentLengthLong" -> null;
                    case "getOutputStream" -> captured.output;
                    case "getWriter" -> {
                        if (captured.writer == null) {
                            captured.writer = new PrintWriter(new OutputStreamWriter(captured.bytes, StandardCharsets.UTF_8), true);
                        }
                        yield captured.writer;
                    }
                    default -> defaultValue(method.getReturnType());
                });
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

    private static final class Captured {
        int status;
        final Map<String, String> headers = new HashMap<>();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ServletOutputStream output = new ServletOutputStream() {
            public boolean isReady() { return true; }
            public void setWriteListener(WriteListener listener) { }
            public void write(int value) { bytes.write(value); }
        };
        PrintWriter writer;

        String body() {
            if (writer != null) {
                writer.flush();
            }
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class FakeAuth extends AuthFlowService {
        Optional<User> user = Optional.empty();
        String guideReturn;

        @Override
        public Optional<User> findAuthenticatedUser(HttpServletRequest request) {
            return user;
        }

        @Override
        public void rememberGuideViewReturn(HttpServletRequest request, String candidate) {
            guideReturn = candidate;
        }
    }

    private static final class Memory implements GuideHostingService.Store, GuideHostingService.Files {
        final GuideHostingService service = new GuideHostingService(this, this);
        final Map<String, DocumentRow> documents = new HashMap<>();
        final Map<Long, TopicRow> topics = new HashMap<>();
        final List<HostedVersion> versions = new ArrayList<>();
        final Map<Long, byte[]> bytes = new HashMap<>();
        final Set<String> viewers = new HashSet<>();
        final Set<String> editors = new HashSet<>();
        final List<String> changes = new ArrayList<>();
        int stores;
        long nextFile = 1;
        long nextVersion = 1;

        void view(long userId) {
            viewers.add(userId + ":8");
        }

        void edit(long userId) {
            editors.add(userId + ":8");
        }

        @Override
        public long store(byte[] html, long userId) {
            stores++;
            long id = nextFile++;
            bytes.put(id, html);
            return id;
        }

        @Override
        public byte[] read(long storedFileId) {
            return bytes.get(storedFileId);
        }

        @Override
        public DocumentRow documentByKey(String key) {
            return documents.get(key);
        }

        @Override
        public TopicRow topicById(long id) {
            return topics.get(id);
        }

        @Override
        public boolean canView(User user, TopicRow topic) {
            return user != null && viewers.contains(user.getUserId() + ":" + topic.id);
        }

        @Override
        public boolean canEdit(User user, TopicRow topic) {
            return user != null && editors.contains(user.getUserId() + ":" + topic.id);
        }

        @Override
        public String displayName(long userId) {
            return "Guide Author";
        }

        @Override
        public String publicBaseUrl() {
            return "http://localhost:8080/hub";
        }

        @Override
        public List<HostedVersion> versions(long documentId) {
            return versions.stream().filter(version -> version.documentId == documentId).toList();
        }

        @Override
        public void save(HostedVersion version, String changesJson, long topicId, long userId, Long tokenId,
                String source) {
            version.id = nextVersion++;
            versions.add(version);
            changes.add(changesJson);
        }
    }
}
