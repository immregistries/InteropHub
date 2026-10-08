package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.airahub.interophub.config.ArtifactStorageConfig;
import org.airahub.interophub.dao.StoredFileDao;
import org.airahub.interophub.model.StoredFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoredFilePreservationTest {
    private Path root;
    private StoredFileService service;
    private boolean preservedSource;
    private final List<LogRecord> records = new ArrayList<>();
    private final Logger logger = Logger.getLogger(StoredFileService.class.getName());
    private final Handler handler = new Handler() {
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    @BeforeEach
    void setup() throws IOException {
        Path target = Files.createDirectories(Path.of("target").toAbsolutePath());
        root = Files.createTempDirectory(target, "preservation-test-");
        service = new StoredFileService(new ArtifactStorageConfig(root.toString(), null, null, null),
                () -> true, new StoredFileDao() {
                    @Override
                    public boolean isPreserved(Long storedFileId) {
                        return storedFileId != null && preservedSource;
                    }
                });
        logger.addHandler(handler);
    }

    @AfterEach
    void cleanup() throws IOException {
        logger.removeHandler(handler);
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @Test
    void preservesExactBytesAndRegistersIndependentMetadataWithoutRevalidatingContent() throws IOException {
        byte[] bytes = {0, 1, (byte) 255, 13, 10};
        StoredFile source = source(bytes);
        AtomicBoolean registered = new AtomicBoolean();

        StoredFile preserved = service.preserve(source, 9L, candidate -> {
            registered.set(true);
            assertNull(candidate.getStoredFileId());
            assertEquals(0, candidate.getRevision());
            assertNotSame(source, candidate);
            assertNotEquals(source.getPublicId(), candidate.getPublicId());
            assertNotEquals(source.getStorageKey(), candidate.getStorageKey());
            assertArrayEquals(bytes, read(candidate));
            candidate.setStoredFileId(22L);
            return candidate;
        });

        assertTrue(registered.get());
        assertEquals(22L, preserved.getStoredFileId());
        assertEquals(StoredFile.Backend.LOCAL, preserved.getBackend());
        assertEquals(source.getOriginalFilename(), preserved.getOriginalFilename());
        assertEquals(source.getContentType(), preserved.getContentType());
        assertEquals(bytes.length, preserved.getSizeBytes());
        assertTrue(preserved.isDownloadOnly());
        assertEquals(9L, preserved.getUploadedByUserId());
        assertEquals(preserved.getCreatedAt(), preserved.getUploadedAt());
        assertNotEquals(source.getCreatedAt(), preserved.getCreatedAt());
        assertSourceUnchanged(source, bytes);
        assertEquals(2, fileCount());
        assertTrue(records.stream().anyMatch(record -> record.getLevel() == java.util.logging.Level.INFO
                && record.getMessage().contains(preserved.getPublicId())
                && record.getMessage().contains(preserved.getStorageKey())));
    }

    @Test
    void replacingOriginalStillDeletesOnlyOriginalBytesNotPreservedBytes() throws IOException {
        byte[] bytes = "original".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StoredFile source = source(bytes);
        StoredFile preserved = service.preserve(source, 9L, candidate -> candidate);

        StoredFile replacement = service.upload(source, new ByteArrayInputStream("replacement".getBytes(
                java.nio.charset.StandardCharsets.UTF_8)), "new.txt", "text/plain", 9L, candidate -> candidate);

        assertFalse(Files.exists(root.resolve(source.getStorageKey())));
        assertArrayEquals(bytes, read(preserved));
        assertEquals(source.getPublicId(), replacement.getPublicId());
        assertEquals("replacement", Files.readString(root.resolve(replacement.getStorageKey())));
        assertEquals(2, fileCount());
    }

    @Test
    void preservedIdentityCannotBeReplacedButCanBeCopiedAgain() throws IOException {
        byte[] bytes = {1, 2, 3};
        StoredFile source = source(bytes);
        preservedSource = true;
        AtomicBoolean registered = new AtomicBoolean();
        try (var input = new ByteArrayInputStream(new byte[] {4, 5, 6})) {
            assertThrows(IllegalStateException.class, () -> service.upload(source, input,
                    "replacement.txt", "text/plain", 9L, candidate -> {
                        registered.set(true);
                        return candidate;
                    }));
            assertEquals(3, input.available());
        }
        assertFalse(registered.get());
        assertSourceUnchanged(source, bytes);
        assertEquals(1, fileCount());

        StoredFile copy = service.preserve(source, 9L, candidate -> candidate);
        assertNotEquals(source.getPublicId(), copy.getPublicId());
        assertNotEquals(source.getStorageKey(), copy.getStorageKey());
        assertArrayEquals(bytes, read(copy));
        assertSourceUnchanged(source, bytes);
        assertEquals(2, fileCount());
    }

    @Test
    void newIdentityIsNotPreservedWithoutDatabaseAccess() {
        assertFalse(new StoredFileDao().isPreserved(null));
    }

    @Test
    void missingSourceNeverRegistersOrPublishes() throws IOException {
        StoredFile source = source(new byte[] {1});
        Files.delete(root.resolve(source.getStorageKey()));
        AtomicBoolean registered = new AtomicBoolean();
        assertThrows(IOException.class, () -> service.preserve(source, 9L, candidate -> {
            registered.set(true);
            return candidate;
        }));
        assertFalse(registered.get());
        assertEquals(0, fileCount());
    }

    @Test
    void sizeMismatchCleansStageAndLeavesSourceUntouched() throws IOException {
        byte[] bytes = {1, 2};
        StoredFile source = source(bytes);
        source.setSizeBytes(3);
        AtomicBoolean registered = new AtomicBoolean();
        assertThrows(IOException.class, () -> service.preserve(source, 9L, candidate -> {
            registered.set(true);
            return candidate;
        }));
        assertFalse(registered.get());
        assertArrayEquals(bytes, read(source));
        assertEquals(3, source.getSizeBytes());
        assertEquals(1, fileCount());
    }

    @Test
    void registrationFailureRetainsPublishedBytesAndLogsReconciliationDestination() throws IOException {
        byte[] bytes = {1, 2, 3};
        StoredFile source = source(bytes);
        AtomicReference<StoredFile> attempted = new AtomicReference<>();
        IllegalStateException failure = new IllegalStateException("ambiguous transaction outcome");
        assertSame(failure, assertThrows(IllegalStateException.class, () ->
                service.preserve(source, 9L, candidate -> {
                    attempted.set(candidate);
                    throw failure;
                })));

        assertArrayEquals(bytes, read(attempted.get()));
        assertSourceUnchanged(source, bytes);
        assertEquals(2, fileCount());
        assertTrue(records.stream().anyMatch(record -> record.getLevel() == java.util.logging.Level.SEVERE
                && record.getMessage().contains("Reconcile committed references")
                && record.getMessage().contains(attempted.get().getStorageKey())));
    }

    @Test
    void recordedOversizeIsRejectedBeforeRegistration() throws IOException {
        StoredFile source = source(new byte[] {1});
        source.setSizeBytes(StoredFileValidation.MAX_BYTES + 1);
        assertThrows(IOException.class, () -> service.preserve(source, 9L, candidate -> {
            fail("Must not register oversized source");
            return candidate;
        }));
        assertEquals(1, fileCount());
    }

    @Test
    void actualOversizeIsBoundedEvenWhenRecordedSizeIsSmallAndCleansStage() throws IOException {
        StoredFile source = source(new byte[] {1});
        try (var channel = Files.newByteChannel(root.resolve(source.getStorageKey()),
                StandardOpenOption.WRITE)) {
            channel.position(StoredFileValidation.MAX_BYTES);
            channel.write(ByteBuffer.wrap(new byte[] {1}));
        }
        assertThrows(IllegalArgumentException.class, () -> service.preserve(source, 9L, candidate -> {
            fail("Must not register oversized bytes");
            return candidate;
        }));
        assertEquals(StoredFileValidation.MAX_BYTES + 1, Files.size(root.resolve(source.getStorageKey())));
        assertEquals(1, fileCount());
    }

    @Test
    void blobPreservationRequiresProductionAndMatchingWriteLocationWithoutLocalFallback() throws IOException {
        StoredFile source = source(new byte[] {1});
        source.setBackend(StoredFile.Backend.BLOB);
        source.setBlobEndpoint("https://testsabbiastorage.blob.core.windows.net");
        source.setBlobContainer("artifacts");
        ArtifactStorageConfig config = new ArtifactStorageConfig(root.toString(),
                source.getBlobEndpoint(), source.getBlobContainer(), "unused-test-token");
        StoredFileService development = new StoredFileService(config, () -> true, null);
        assertThrows(IllegalStateException.class, () -> development.preserve(source, 9L, candidate -> {
            fail("Development must not perform Blob preservation");
            return candidate;
        }));
        source.setBlobContainer("other-artifacts");
        StoredFileService production = new StoredFileService(config, () -> false, null);
        assertThrows(IllegalStateException.class, () -> production.preserve(source, 9L, candidate -> {
            fail("Mismatched Blob location must not be registered");
            return candidate;
        }));
        assertEquals(1, fileCount());
    }

    @Test
    void preservationRequiresExplicitSourceUserAndRegistration() throws IOException {
        StoredFile source = source(new byte[] {1});
        assertThrows(IllegalArgumentException.class, () -> service.preserve(null, 9L, candidate -> candidate));
        assertThrows(IllegalArgumentException.class, () -> service.preserve(source, null, candidate -> candidate));
        assertThrows(IllegalArgumentException.class, () -> service.preserve(source, 9L, null));
        assertEquals(1, fileCount());
    }

    @Test
    void nullRegistrationResultIsAmbiguousAndDoesNotDeleteBytes() throws IOException {
        StoredFile source = source(new byte[] {1});
        assertThrows(IllegalStateException.class, () -> service.preserve(source, 9L, candidate -> null));
        assertEquals(2, fileCount());
        assertArrayEquals(new byte[] {1}, read(source));
    }

    private StoredFile source(byte[] bytes) throws IOException {
        StoredFile file = new StoredFile();
        file.setStoredFileId(11L);
        file.setPublicId(UUID.randomUUID().toString());
        file.setStorageKey(UUID.randomUUID().toString());
        file.setBackend(StoredFile.Backend.LOCAL);
        file.setOriginalFilename("original.txt");
        file.setContentType("text/plain");
        file.setSizeBytes(bytes.length);
        file.setUploadedByUserId(7L);
        file.setCreatedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        file.setUploadedAt(file.getCreatedAt());
        file.setRevision(4);
        file.setDownloadOnly(true);
        Files.write(root.resolve(file.getStorageKey()), bytes);
        return file;
    }

    private void assertSourceUnchanged(StoredFile source, byte[] bytes) {
        assertEquals(11L, source.getStoredFileId());
        assertEquals(4, source.getRevision());
        assertEquals(7L, source.getUploadedByUserId());
        assertEquals(LocalDateTime.of(2020, 1, 1, 0, 0), source.getCreatedAt());
        assertEquals(source.getCreatedAt(), source.getUploadedAt());
        assertArrayEquals(bytes, read(source));
    }

    private byte[] read(StoredFile file) {
        try {
            return Files.readAllBytes(root.resolve(file.getStorageKey()));
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private long fileCount() throws IOException {
        try (var files = Files.list(root)) {
            return files.count();
        }
    }
}
