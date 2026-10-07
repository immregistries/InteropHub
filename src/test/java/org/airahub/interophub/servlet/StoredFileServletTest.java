package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import org.airahub.interophub.config.ArtifactStorageConfig;
import org.airahub.interophub.dao.StoredFileDao;
import org.airahub.interophub.model.StoredFile;
import org.airahub.interophub.service.LocalFileStorageService;
import org.junit.jupiter.api.*;

class StoredFileServletTest {
    private Path root;
    private StoredFile file;
    private StoredFileServlet servlet;
    private boolean lookupCalled;

    @BeforeEach
    void setup() throws IOException {
        root = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "artifact-servlet-test-");
        file = new StoredFile();
        file.setPublicId(UUID.randomUUID().toString());
        file.setStorageKey(UUID.randomUUID().toString());
        file.setBackend(StoredFile.Backend.LOCAL);
        file.setContentType("text/plain");
        file.setOriginalFilename("sample.txt");
        file.setUploadedAt(LocalDateTime.of(2026, 10, 7, 12, 0));
        file.setSizeBytes(5);
        Files.writeString(root.resolve(file.getStorageKey()), "hello");
        servlet = new StoredFileServlet(new StoredFileDao() {
            @Override public Optional<StoredFile> findByPublicId(String id) {
                lookupCalled = true;
                return id.equals(file.getPublicId()) ? Optional.of(new StoredFile(file)) : Optional.empty();
            }
        }, new LocalFileStorageService(new ArtifactStorageConfig(root.toString(), null, null, null)));
    }

    @AfterEach
    void cleanup() throws IOException {
        try (var files = Files.walk(root)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
        }
    }

    @Test
    void anonymousGetStreamsBytesWithDownloadAndRevalidationHeaders() throws IOException {
        Capture response = new Capture();
        servlet.doGet(request("/" + file.getPublicId(), null), response.response);
        assertEquals(200, response.status);
        assertEquals("hello", response.body.toString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("5", response.headers.get("Content-Length"));
        assertEquals("no-cache", response.headers.get("Cache-Control"));
        assertEquals("nosniff", response.headers.get("X-Content-Type-Options"));
        assertTrue(response.headers.get("Content-Disposition").startsWith("attachment;"));
    }

    @Test
    void headSendsMetadataWithoutBytesAndConditionalGetReturns304() throws IOException {
        Capture head = new Capture();
        servlet.doHead(request("/" + file.getPublicId(), null), head.response);
        assertEquals(200, head.status);
        assertEquals(0, head.body.size());
        Capture cached = new Capture();
        servlet.doGet(request("/" + file.getPublicId(), head.headers.get("ETag")), cached.response);
        assertEquals(304, cached.status);
        assertEquals(0, cached.body.size());
    }

    @Test
    void meetingPdfHasAttachmentDispositionWhileImagesStayInline() throws IOException {
        file.setDownloadOnly(true);
        file.setContentType("application/pdf");
        file.setOriginalFilename("slides.pdf");
        Capture pdf = new Capture();
        servlet.doGet(request("/" + file.getPublicId(), null), pdf.response);
        assertTrue(pdf.headers.get("Content-Disposition").startsWith("attachment;"));
        file.setContentType("image/png");
        file.setOriginalFilename("image.png");
        Capture image = new Capture();
        servlet.doGet(request("/" + file.getPublicId(), null), image.response);
        assertTrue(image.headers.get("Content-Disposition").startsWith("inline;"));
    }

    @Test
    void malformedConditionalDateDoesNotReportStorageOutage() throws IOException {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(), new Class<?>[] { HttpServletRequest.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPathInfo" -> "/" + file.getPublicId();
                    case "getDateHeader" -> throw new IllegalArgumentException("Invalid HTTP date");
                    default -> defaultValue(method.getReturnType());
                });
        Capture response = new Capture();
        servlet.doGet(request, response.response);
        assertEquals(200, response.status);
        assertEquals("hello", response.body.toString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void replacementChangesEtagEvenWithinSameSecond() throws IOException {
        String oldEtag = "\"" + file.getStorageKey() + "\"";
        Files.delete(root.resolve(file.getStorageKey()));
        file.setStorageKey(UUID.randomUUID().toString());
        Files.writeString(root.resolve(file.getStorageKey()), "fresh");
        Capture response = new Capture();
        servlet.doGet(request("/" + file.getPublicId(), oldEtag), response.response);
        assertEquals(200, response.status);
        assertEquals("fresh", response.body.toString(java.nio.charset.StandardCharsets.UTF_8));
        assertNotEquals(oldEtag, response.headers.get("ETag"));
    }

    @Test
    void blobRedirectUsesRecordedLocationWithoutProxyOrWriteCredential() throws IOException {
        file.setBackend(StoredFile.Backend.BLOB);
        file.setBlobEndpoint("https://another.blob.core.windows.net");
        file.setBlobContainer("old-artifacts");
        Capture response = new Capture();
        servlet.doGet(request("/" + file.getPublicId(), null), response.response);
        assertEquals(302, response.status);
        assertEquals(file.getBlobEndpoint() + "/old-artifacts/" + file.getStorageKey(),
                response.headers.get("Location"));
        assertEquals("no-cache", response.headers.get("Cache-Control"));
        assertEquals(0, response.body.size());
    }

    @Test
    void missingContentReturns404AndNeverFallsBackToBlob() throws IOException {
        Files.delete(root.resolve(file.getStorageKey()));
        Capture response = new Capture();
        servlet.doGet(request("/" + file.getPublicId(), null), response.response);
        assertEquals(404, response.status);
        assertFalse(response.headers.containsKey("Location"));
    }

    @Test
    void wrongFileSizeReturns503NotSuccess() throws IOException {
        file.setSizeBytes(99);
        Capture response = new Capture();
        servlet.doGet(request("/" + file.getPublicId(), null), response.response);
        assertEquals(503, response.status);
        assertEquals(0, response.body.size());
    }

    @Test
    void invalidOrUnknownIdsCannotListFiles() throws IOException {
        for (String path : List.of("/", "/../sample.txt", "/random", "/" + file.getPublicId() + "/extra")) {
            lookupCalled = false;
            Capture response = new Capture();
            servlet.doGet(request(path, null), response.response);
            assertEquals(404, response.status);
            assertFalse(lookupCalled);
        }
        Capture response = new Capture();
        servlet.doGet(request("/" + UUID.randomUUID(), null), response.response);
        assertEquals(404, response.status);
    }

    @Test
    void weakAndMultipleEntityTagsMatch() {
        assertTrue(StoredFileServlet.matchesEtag("W/\"one\", \"two\"", "\"one\""));
        assertTrue(StoredFileServlet.matchesEtag("*", "\"one\""));
        assertFalse(StoredFileServlet.matchesEtag("\"other\"", "\"one\""));
    }

    private static HttpServletRequest request(String path, String etag) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "getPathInfo" -> path;
                    case "getHeader" -> args[0].equals("If-None-Match") ? etag : null;
                    case "getDateHeader" -> -1L;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) { return false; }
        if (type == int.class) { return 0; }
        if (type == long.class) { return 0L; }
        return null;
    }

    private static class Capture {
        int status = 200;
        final Map<String, String> headers = new HashMap<>();
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(), new Class<?>[] { HttpServletResponse.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setHeader", "setDateHeader" -> headers.put((String) args[0], String.valueOf(args[1]));
                        case "setContentLengthLong" -> headers.put("Content-Length", String.valueOf(args[0]));
                        case "setContentType" -> headers.put("Content-Type", (String) args[0]);
                        case "setStatus", "sendError" -> status = (Integer) args[0];
                        case "reset" -> { status = 200; headers.clear(); body.reset(); }
                        case "getOutputStream" -> {
                            return new ServletOutputStream() {
                                @Override public boolean isReady() { return true; }
                                @Override public void setWriteListener(WriteListener listener) { }
                                @Override public void write(int value) { body.write(value); }
                            };
                        }
                        default -> { }
                    }
                    return defaultValue(method.getReturnType());
                });
    }
}
