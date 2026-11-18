package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * The display state and comment mode an author chose for one label of a guide.
 * A label with no row behaves as UNLISTED / OPEN.
 */
@Entity
@Table(name = "es_guide_label")
public class EsGuideLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_label_id")
    private Long guideLabelId;

    @Column(name = "guide_document_id", nullable = false)
    private Long guideDocumentId;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    @Column(name = "display_state", nullable = false, length = 16)
    private String displayState;

    @Column(name = "comment_mode", nullable = false, length = 16)
    private String commentMode;

    @Column(name = "comment_mode_set_at")
    private LocalDateTime commentModeSetAt;

    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getGuideLabelId() {
        return guideLabelId;
    }

    public void setGuideLabelId(Long guideLabelId) {
        this.guideLabelId = guideLabelId;
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

    public String getDisplayState() {
        return displayState;
    }

    public void setDisplayState(String displayState) {
        this.displayState = displayState;
    }

    public String getCommentMode() {
        return commentMode;
    }

    public void setCommentMode(String commentMode) {
        this.commentMode = commentMode;
    }

    public LocalDateTime getCommentModeSetAt() {
        return commentModeSetAt;
    }

    public void setCommentModeSetAt(LocalDateTime commentModeSetAt) {
        this.commentModeSetAt = commentModeSetAt;
    }

    public Long getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(Long updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
