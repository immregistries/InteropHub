package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One upload of a hosted guide. A label serves its newest row; older rows stay. */
@Entity
@Table(name = "es_guide_hosted_version")
public class EsGuideHostedVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_hosted_version_id")
    private Long guideHostedVersionId;

    @Column(name = "guide_document_id", nullable = false)
    private Long guideDocumentId;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    @Column(name = "stored_file_id", nullable = false)
    private Long storedFileId;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private Long uploadedByUserId;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public Long getGuideHostedVersionId() {
        return guideHostedVersionId;
    }

    public void setGuideHostedVersionId(Long guideHostedVersionId) {
        this.guideHostedVersionId = guideHostedVersionId;
    }

    public Long getGuideDocumentId() {
        return guideDocumentId;
    }

    public void setGuideDocumentId(Long guideDocumentId) {
        this.guideDocumentId = guideDocumentId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Long getStoredFileId() {
        return storedFileId;
    }

    public void setStoredFileId(Long storedFileId) {
        this.storedFileId = storedFileId;
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
}
