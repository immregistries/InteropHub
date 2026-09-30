package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * TEMPORARY - one demo artifact slot, used only to prove Azure Blob storage
 * works end to end before Communication Bundles depend on it
 * (docs/communication-bundles/artifact-storage-deployment-handoff.md). Replaced
 * by real Topic Resources in step 2; drop the table and this class then.
 *
 * <p>
 * {@code objectKey} is the opaque Blob name. It is minted once per slot and
 * stays stable when the image is replaced, so the public URL never changes.
 */
@Entity
@Table(name = "es_artifact_demo")
public class EsArtifactDemo {

    /** The single slot used by the admin test page and the /welcome banner. */
    public static final String SLOT_WELCOME_DEMO = "WELCOME_DEMO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_artifact_demo_id")
    private Long esArtifactDemoId;

    @Column(name = "slot_key", nullable = false, length = 40)
    private String slotKey;

    @Column(name = "object_key", nullable = false, length = 64)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsArtifactDemoId() {
        return esArtifactDemoId;
    }

    public void setEsArtifactDemoId(Long esArtifactDemoId) {
        this.esArtifactDemoId = esArtifactDemoId;
    }

    public String getSlotKey() {
        return slotKey;
    }

    public void setSlotKey(String slotKey) {
        this.slotKey = slotKey;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Long getUploadedByUserId() {
        return uploadedByUserId;
    }

    public void setUploadedByUserId(Long uploadedByUserId) {
        this.uploadedByUserId = uploadedByUserId;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
