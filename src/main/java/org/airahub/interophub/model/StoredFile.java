package org.airahub.interophub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Shared content metadata; feature ownership is held by the referencing feature. */
@Entity
@Table(name = "hub_stored_file")
public class StoredFile {
    public enum Backend { LOCAL, BLOB }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stored_file_id")
    private Long storedFileId;
    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_backend", nullable = false, length = 10)
    private Backend backend;
    @Column(name = "storage_key", nullable = false, length = 64)
    private String storageKey;
    @Column(name = "blob_endpoint", length = 500)
    private String blobEndpoint;
    @Column(name = "blob_container", length = 63)
    private String blobContainer;
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Version
    @Column(name = "revision", nullable = false)
    private long revision;
    @Column(name = "download_only", nullable = false)
    private boolean downloadOnly;

    public StoredFile() { }

    public StoredFile(StoredFile source) {
        storedFileId = source.storedFileId;
        publicId = source.publicId;
        backend = source.backend;
        storageKey = source.storageKey;
        blobEndpoint = source.blobEndpoint;
        blobContainer = source.blobContainer;
        originalFilename = source.originalFilename;
        contentType = source.contentType;
        sizeBytes = source.sizeBytes;
        uploadedByUserId = source.uploadedByUserId;
        uploadedAt = source.uploadedAt;
        createdAt = source.createdAt;
        revision = source.revision;
        downloadOnly = source.downloadOnly;
    }

    public Long getStoredFileId() { return storedFileId; }
    public void setStoredFileId(Long value) { storedFileId = value; }
    public String getPublicId() { return publicId; }
    public void setPublicId(String value) { publicId = value; }
    public Backend getBackend() { return backend; }
    public void setBackend(Backend value) { backend = value; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String value) { storageKey = value; }
    public String getBlobEndpoint() { return blobEndpoint; }
    public void setBlobEndpoint(String value) { blobEndpoint = value; }
    public String getBlobContainer() { return blobContainer; }
    public void setBlobContainer(String value) { blobContainer = value; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { originalFilename = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long value) { sizeBytes = value; }
    public Long getUploadedByUserId() { return uploadedByUserId; }
    public void setUploadedByUserId(Long value) { uploadedByUserId = value; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime value) { uploadedAt = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public long getRevision() { return revision; }
    public void setRevision(long value) { revision = value; }
    public boolean isDownloadOnly() { return downloadOnly; }
    public void setDownloadOnly(boolean value) { downloadOnly = value; }

    public boolean isImage() {
        return java.util.Set.of("image/png", "image/jpeg", "image/webp", "image/gif").contains(contentType);
    }
}
