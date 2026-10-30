package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_guide_review_flag")
public class EsGuideReviewFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_flag_id")
    private Long reviewFlagId;

    @Column(name = "guide_document_id", nullable = false)
    private Long guideDocumentId;

    @Column(name = "anchor_id", nullable = false, length = 100)
    private String anchorId;

    @Column(name = "note", nullable = false, length = 500)
    private String note;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "cleared_at")
    private LocalDateTime clearedAt;

    @Column(name = "cleared_by_user_id")
    private Long clearedByUserId;

    public Long getReviewFlagId() {
        return reviewFlagId;
    }

    public void setReviewFlagId(Long reviewFlagId) {
        this.reviewFlagId = reviewFlagId;
    }

    public Long getGuideDocumentId() {
        return guideDocumentId;
    }

    public void setGuideDocumentId(Long guideDocumentId) {
        this.guideDocumentId = guideDocumentId;
    }

    public String getAnchorId() {
        return anchorId;
    }

    public void setAnchorId(String anchorId) {
        this.anchorId = anchorId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
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

    public LocalDateTime getClearedAt() {
        return clearedAt;
    }

    public void setClearedAt(LocalDateTime clearedAt) {
        this.clearedAt = clearedAt;
    }

    public Long getClearedByUserId() {
        return clearedByUserId;
    }

    public void setClearedByUserId(Long clearedByUserId) {
        this.clearedByUserId = clearedByUserId;
    }
}
