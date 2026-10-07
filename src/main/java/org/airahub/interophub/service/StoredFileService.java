package org.airahub.interophub.service;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import java.util.function.*;
import java.util.logging.*;
import org.airahub.interophub.config.ArtifactStorageConfig;
import org.airahub.interophub.dao.StoredFileDao;
import org.airahub.interophub.model.StoredFile;
import org.airahub.interophub.model.StoredFile.Backend;

public class StoredFileService {
    private static final Logger LOGGER = Logger.getLogger(StoredFileService.class.getName());
    private final ArtifactStorageConfig config;
    private final LocalFileStorageService local;
    private final ArtifactBlobStorageService blob;
    private final StoredFileDao files;

    public StoredFileService() {
        this(ArtifactStorageConfig.current(), () -> new PublicUrlService().isLocalhostMode(), new StoredFileDao());
    }

    public StoredFileService(ArtifactStorageConfig config, BooleanSupplier developmentMode, StoredFileDao files) {
        this.config = config;
        this.local = new LocalFileStorageService(config);
        this.blob = new ArtifactBlobStorageService(config, developmentMode);
        this.files = files;
    }

    public LocalFileStorageService local() { return local; }

    public String writeProblem(StoredFile existing) {
        if (existing == null || existing.getBackend() == Backend.LOCAL) {
            return local.writeProblem();
        }
        if (existing.getBackend() != Backend.BLOB) {
            return "File storage backend is invalid.";
        }
        if (!blob.isWriteEnabled()) {
            return "Blob replacement requires production mode and HUB_ARTIFACTS_SAS_TOKEN. "
                    + "Local uploads remain independent.";
        }
        if (!config.blobEndpoint().equals(existing.getBlobEndpoint())
                || !config.container().equals(existing.getBlobContainer())) {
            return "No write credential is configured for this file's recorded Blob location.";
        }
        return null;
    }

    public StoredFile upload(StoredFile existing, InputStream content, String filename,
            String declaredType, Long uploader) throws IOException {
        return upload(existing, content, filename, declaredType, uploader, files::save);
    }

    /** Registration can atomically persist feature ownership alongside shared file metadata. */
    public StoredFile upload(StoredFile existing, InputStream content, String filename, String declaredType,
            Long uploader, Function<StoredFile, StoredFile> register) throws IOException {
        if (uploader == null) {
            throw new IllegalArgumentException("An authenticated uploader is required.");
        }
        String problem = writeProblem(existing);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        String name = StoredFileValidation.filename(filename);
        StoredFile candidate = existing == null ? new StoredFile() : new StoredFile(existing);
        if (existing == null) {
            candidate.setPublicId(UUID.randomUUID().toString());
            candidate.setBackend(Backend.LOCAL);
            candidate.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        candidate.setStorageKey(UUID.randomUUID().toString());
        Path staged = null;
        boolean published = false;
        boolean registered = false;
        try {
            if (candidate.getBackend() == Backend.LOCAL) {
                staged = local.stage(content);
            } else {
                staged = Files.createTempFile("interophub-blob-upload-", ".tmp");
                try (OutputStream output = Files.newOutputStream(staged)) {
                    LocalFileStorageService.copyBounded(content, output);
                }
            }
            candidate.setContentType(StoredFileValidation.validate(staged, name, declaredType));
            candidate.setOriginalFilename(name);
            candidate.setSizeBytes(Files.size(staged));
            candidate.setUploadedByUserId(uploader);
            candidate.setUploadedAt(LocalDateTime.now(ZoneOffset.UTC));
            if (candidate.getBackend() == Backend.LOCAL) {
                local.publish(staged, candidate.getStorageKey());
            } else {
                try (InputStream input = Files.newInputStream(staged)) {
                    blob.store(candidate.getBlobEndpoint(), candidate.getBlobContainer(), candidate.getStorageKey(),
                            input, candidate.getContentType(), name, inline(candidate));
                }
            }
            published = true;
            StoredFile result = register.apply(candidate);
            registered = true;
            if (existing != null && existing.getBackend() == Backend.LOCAL) {
                cleanupLocal(existing.getStorageKey());
            } else if (existing != null) {
                LOGGER.info("Blob replacement completed; previous unreferenced object requires operator cleanup: "
                        + existing.getStorageKey());
            }
            return result;
        } finally {
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Upload staging file requires cleanup: " + staged, ex);
                }
            }
            if (published && !registered) {
                // Commit failures may be ambiguous. Never delete possibly committed content.
                LOGGER.severe("File registration failed; reconcile unreferenced upload before cleanup: "
                        + candidate.getBackend() + " " + candidate.getStorageKey());
            }
        }
    }

    private void cleanupLocal(String key) {
        try {
            local.delete(key);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Replacement succeeded but previous local object needs cleanup: " + key, ex);
        }
    }

    public static boolean inline(StoredFile file) {
        return file.isImage() || "application/pdf".equals(file.getContentType());
    }

    public static String readUrl(String contextPath, StoredFile file) {
        ArtifactStorageConfig.validateKey(file.getPublicId());
        return (contextPath == null ? "" : contextPath) + "/files/" + file.getPublicId();
    }

    public static String contentDisposition(String filename, boolean inline) {
        String ascii = filename.replaceAll("[^\\x20-\\x7E]|[\"\\\\]", "_");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return (inline ? "inline" : "attachment") + "; filename=\"" + ascii
                + "\"; filename*=UTF-8''" + encoded;
    }
}
