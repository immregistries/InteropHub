package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.airahub.interophub.config.ArtifactStorageConfig;
import org.airahub.interophub.dao.StoredFileDao;
import org.airahub.interophub.model.StoredFile;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.junit.jupiter.api.*;

class StoredFileServiceTest {
    private Path root;
    private ArtifactStorageConfig config;
    private StoredFileService service;
    private StoredFile saved;

    @BeforeEach
    void setup() throws IOException {
        root = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "artifact-test-");
        config = new ArtifactStorageConfig(root.toString(), null, null, null);
        service = new StoredFileService(config, () -> true, new StoredFileDao() {
            @Override
            public StoredFile save(StoredFile file) {
                saved = new StoredFile(file);
                saved.setStoredFileId(1L);
                saved.setRevision(file.getRevision() + 1);
                return saved;
            }
        });
    }

    @AfterEach
    void cleanup() throws IOException {
        try (var files = Files.walk(root)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }

    @Test
    void meetingPdfDownloadPolicyIsPersistedWithoutChangingOtherPdfConsumers() throws IOException {
        StoredFile meetingFile = service.upload(null, input("%PDF-1.7\nproof"),
                "slides.pdf", "application/pdf", 7L, true, file -> new StoredFile(file));
        assertTrue(meetingFile.isDownloadOnly());
        assertFalse(StoredFileService.inline(meetingFile));
        StoredFile normalFile = service.upload(null, input("%PDF-1.7\nproof"),
                "preview.pdf", "application/pdf", 7L);
        assertFalse(normalFile.isDownloadOnly());
        assertTrue(StoredFileService.inline(normalFile));
        StoredFile replacement = service.upload(meetingFile, input("%PDF-1.7\nnew"),
                "slides.pdf", "application/pdf", 7L);
        assertTrue(replacement.isDownloadOnly());
        assertFalse(StoredFileService.inline(replacement));
    }

    @Test
    void localUploadAndReplacementKeepPublicIdentityButPublishNewBytes() throws IOException {
        StoredFile first = upload("old.txt", "old");
        String oldKey = first.getStorageKey();
        StoredFile second = service.upload(first, input("new"), "new.txt", "text/plain", 7L);
        assertEquals(first.getPublicId(), second.getPublicId());
        assertNotEquals(oldKey, second.getStorageKey());
        assertFalse(Files.exists(root.resolve(oldKey)));
        assertEquals("new", Files.readString(root.resolve(second.getStorageKey())));
        assertEquals(StoredFile.Backend.LOCAL, second.getBackend());
        assertEquals("/hub/files/" + second.getPublicId(), StoredFileService.readUrl("/hub", second));
        assertEquals(2, second.getRevision());
    }

    @Test
    void invalidReplacementDoesNotDamageCurrentFileOrMetadata() throws IOException {
        StoredFile first = upload("good.txt", "good");
        assertThrows(IllegalArgumentException.class, () ->
                service.upload(first, input("fake"), "fake.png", "image/png", 7L));
        assertEquals("good", Files.readString(root.resolve(first.getStorageKey())));
        assertEquals(first.getStorageKey(), saved.getStorageKey());
        assertEquals(1, fileCount());
    }

    @Test
    void databaseFailureRetainsPriorContentAndLogsCandidateForReconciliation() throws IOException {
        StoredFile first = upload("good.txt", "good");
        assertThrows(IllegalStateException.class, () -> service.upload(first, input("new"),
                "new.txt", "text/plain", 7L, file -> {
                    throw new IllegalStateException("simulated ambiguous commit failure");
                }));
        assertEquals("good", Files.readString(root.resolve(first.getStorageKey())));
        assertEquals("good.txt", saved.getOriginalFilename());
        assertEquals(2, fileCount());
    }

    @Test
    void streamedLimitAcceptsExactCapAndRejectsOneByteMoreWithoutPartialFile() throws IOException {
        try (InputStream exact = repeated(StoredFileValidation.MAX_BYTES)) {
            StoredFile file = service.upload(null, exact, "limit.txt", "text/plain", 7L);
            assertEquals(26_214_400, file.getSizeBytes());
        }
        try (InputStream oversized = repeated(StoredFileValidation.MAX_BYTES + 1)) {
            assertThrows(IllegalArgumentException.class, () ->
                    service.upload(null, oversized, "too-large.txt", "text/plain", 7L));
        }
        assertEquals(1, fileCount());
    }

    @Test
    void allowedImagesPdfOfficeAndTextAreDetected() throws IOException {
        for (String format : List.of("png", "jpeg", "gif")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertTrue(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes));
            StoredFile image = service.upload(null, new ByteArrayInputStream(bytes.toByteArray()),
                    "image." + format, "image/" + format, 7L);
            assertTrue(image.isImage());
        }
        assertEquals("application/pdf", service.upload(null, input("%PDF-1.7\n%%EOF"),
                "sample.pdf", "application/pdf", 7L).getContentType());
        for (String extension : List.of("docx", "pptx")) {
            StoredFile office = service.upload(null, new ByteArrayInputStream(officeZip(extension, false)),
                    "sample." + extension, "application/octet-stream", 7L);
            assertTrue(office.getContentType().contains("openxmlformats"));
            assertFalse(StoredFileService.inline(office));
        }
        for (String extension : List.of("doc", "ppt")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (extension.equals("ppt")) {
                try (HSLFSlideShow slides = new HSLFSlideShow()) {
                    slides.createSlide();
                    slides.write(bytes);
                }
            } else {
                try (POIFSFileSystem fs = new POIFSFileSystem()) {
                    byte[] header = new byte[32];
                    header[0] = (byte) 0xec;
                    header[1] = (byte) 0xa5;
                    fs.createDocument(new ByteArrayInputStream(header), "WordDocument");
                    fs.writeFilesystem(bytes);
                }
            }
            assertNotNull(service.upload(null, new ByteArrayInputStream(bytes.toByteArray()),
                    "sample." + extension, null, 7L));
        }
    }

    @Test
    void rejectsSpoofedActiveMediaAndMacroPackages() throws IOException {
        for (String extension : List.of("svg", "html", "mp4", "mp3", "zip", "pptm", "exe")) {
            assertThrows(IllegalArgumentException.class, () ->
                    service.upload(null, input("content"), "bad." + extension, null, 7L));
        }
        assertThrows(IllegalArgumentException.class, () ->
                service.upload(null, input("text"), "text.txt", "text/html", 7L));
        assertThrows(IllegalArgumentException.class, () ->
                service.upload(null, new ByteArrayInputStream(officeZip("pptx", true)),
                        "macro.pptx", null, 7L));
        assertThrows(IllegalArgumentException.class, () ->
                service.upload(null, new ByteArrayInputStream(officeZip("pptx", false)),
                        "wrong.docx", null, 7L));
        assertThrows(IllegalArgumentException.class, () ->
                service.upload(null, new ByteArrayInputStream(new byte[] {0, 1, 2}), "bad.txt", null, 7L));
        assertThrows(IllegalArgumentException.class, () ->
                service.upload(null, input("content"), "bad\r\n.txt", null, 7L));
        assertEquals(0, fileCount());
    }

    @Test
    void missingRelativeAndNonexistentRootsHaveExplicitProblems() {
        assertNotNull(new LocalFileStorageService(new ArtifactStorageConfig(null, null, null, null)).writeProblem());
        assertNotNull(new LocalFileStorageService(new ArtifactStorageConfig("relative", null, null, null))
                .writeProblem());
        assertNotNull(new LocalFileStorageService(
                new ArtifactStorageConfig(root.resolve("missing").toString(), null, null, null)).writeProblem());
        assertFalse(Files.exists(root.resolve("missing")));
        service.local().setDeploymentPath(root.getParent().toString());
        assertNotNull(service.writeProblem(null));
    }

    @Test
    void pathsMustBeOpaqueAndKnownRootFilesMustNotBeSymlinks() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> service.local().open("../outside"));
        assertThrows(IllegalArgumentException.class, () -> service.local().delete(root.toString()));
        Path target = root.resolve("target.txt");
        Files.writeString(target, "outside");
        String key = UUID.randomUUID().toString();
        try {
            Files.createSymbolicLink(root.resolve(key), target);
        } catch (IOException | UnsupportedOperationException ex) {
            Assumptions.abort("Host does not permit creation of symbolic links.");
        }
        assertThrows(IOException.class, () -> service.local().open(key));
        assertThrows(IOException.class, () -> service.local().delete(key));
    }

    @Test
    void blobWritesNeverRunInDevelopmentEvenWithCredentialAndNeverFallbackToLocal() {
        AtomicBoolean development = new AtomicBoolean(true);
        ArtifactStorageConfig sasConfig = new ArtifactStorageConfig(root.toString(), null, null, "test-not-a-secret");
        ArtifactBlobStorageService blob = new ArtifactBlobStorageService(sasConfig, development::get);
        assertFalse(blob.isWriteEnabled());
        assertThrows(IllegalStateException.class, () -> blob.store(UUID.randomUUID().toString(),
                input("content"), "text/plain", "file.txt"));
        development.set(false);
        assertTrue(blob.isWriteEnabled());
        StoredFile file = new StoredFile();
        file.setBackend(StoredFile.Backend.BLOB);
        file.setBlobEndpoint(sasConfig.blobEndpoint());
        file.setBlobContainer(sasConfig.container());
        assertNotNull(service.writeProblem(file));
        assertNull(service.writeProblem(null));
    }

    @Test
    void blobLocationsCannotRedirectToArbitraryHostsOrContainCredentials() {
        String key = UUID.randomUUID().toString();
        assertEquals("https://test.blob.core.windows.net/artifacts/" + key,
                ArtifactStorageConfig.blobReadUrl("https://test.blob.core.windows.net", "artifacts", key));
        for (String endpoint : List.of("http://test.blob.core.windows.net", "https://evil.example",
                "https://test.blob.core.windows.net/?sig=secret", "https://user@test.blob.core.windows.net")) {
            assertThrows(IllegalStateException.class, () ->
                    ArtifactStorageConfig.blobReadUrl(endpoint, "artifacts", key));
        }
    }

    @Test
    void filenamesStripClientPathsAndUseSafeInternationalDownloadHeaders() {
        assertEquals("slides.pptx", StoredFileValidation.filename("C:\\fakepath\\slides.pptx"));
        String disposition = StoredFileService.contentDisposition("café \"slides\".pptx", false);
        assertTrue(disposition.startsWith("attachment;"));
        assertTrue(disposition.contains("filename*=UTF-8''caf%C3%A9"));
        assertFalse(disposition.contains("\r"));
    }

    private StoredFile upload(String name, String text) throws IOException {
        return service.upload(null, input(text), name, "text/plain", 7L);
    }

    private long fileCount() throws IOException {
        try (var files = Files.list(root)) { return files.count(); }
    }

    private static InputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static InputStream repeated(long count) {
        return new InputStream() {
            long remaining = count;
            @Override public int read() { return remaining-- > 0 ? 'a' : -1; }
            @Override public int read(byte[] buffer, int offset, int length) {
                if (remaining == 0) { return -1; }
                int read = (int) Math.min(remaining, length);
                Arrays.fill(buffer, offset, offset + read, (byte) 'a');
                remaining -= read;
                return read;
            }
        };
    }

    private static byte[] officeZip(String extension, boolean macro) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        String main = extension.equals("docx") ? "word/document.xml" : "ppt/presentation.xml";
        String type = extension.equals("docx")
                ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
                : "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Override PartName=\"/" + main + "\" ContentType=\"" + type + "\"/></Types>");
            entries.put("_rels/.rels", "<Relationships/>");
            entries.put(main, "<document/>");
            if (macro) { entries.put("ppt/vbaProject.bin", "macro"); }
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
