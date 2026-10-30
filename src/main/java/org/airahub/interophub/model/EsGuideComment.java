package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_guide_comment")
public class EsGuideComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_comment_id")
    private Long guideCommentId;

    @Column(name = "guide_document_id", nullable = false)
    private Long guideDocumentId;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(name = "anchor_id", length = 100)
    private String anchorId;

    @Column(name = "block_index")
    private Integer blockIndex;

    @Column(name = "block_snippet", length = 120)
    private String blockSnippet;

    @Column(name = "comment_text", columnDefinition = "TEXT")
    private String commentText;

    @Column(name = "state", nullable = false, length = 16)
    private String state;

    @Column(name = "disposition_code", length = 32)
    private String dispositionCode;

    @Column(name = "change_note", length = 500)
    private String changeNote;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_note", length = 200)
    private String sourceNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by_user_id")
    private Long deletedByUserId;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "disposed_at")
    private LocalDateTime disposedAt;

    @Column(name = "disposed_by_user_id")
    private Long disposedByUserId;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    public Long getGuideCommentId() {
        return guideCommentId;
    }

    public void setGuideCommentId(Long guideCommentId) {
        this.guideCommentId = guideCommentId;
    }

    public Long getGuideDocumentId() {
        return guideDocumentId;
    }

    public void setGuideDocumentId(Long guideDocumentId) {
        this.guideDocumentId = guideDocumentId;
    }

    public Long getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(Long parentCommentId) {
        this.parentCommentId = parentCommentId;
    }

    public String getAnchorId() {
        return anchorId;
    }

    public void setAnchorId(String anchorId) {
        this.anchorId = anchorId;
    }

    public Integer getBlockIndex() {
        return blockIndex;
    }

    public void setBlockIndex(Integer blockIndex) {
        this.blockIndex = blockIndex;
    }

    public String getBlockSnippet() {
        return blockSnippet;
    }

    public void setBlockSnippet(String blockSnippet) {
        this.blockSnippet = blockSnippet;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDispositionCode() {
        return dispositionCode;
    }

    public void setDispositionCode(String dispositionCode) {
        this.dispositionCode = dispositionCode;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public void setChangeNote(String changeNote) {
        this.changeNote = changeNote;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSourceNote() {
        return sourceNote;
    }

    public void setSourceNote(String sourceNote) {
        this.sourceNote = sourceNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(LocalDateTime editedAt) {
        this.editedAt = editedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getDeletedByUserId() {
        return deletedByUserId;
    }

    public void setDeletedByUserId(Long deletedByUserId) {
        this.deletedByUserId = deletedByUserId;
    }

    public LocalDateTime getWithdrawnAt() {
        return withdrawnAt;
    }

    public void setWithdrawnAt(LocalDateTime withdrawnAt) {
        this.withdrawnAt = withdrawnAt;
    }

    public LocalDateTime getDisposedAt() {
        return disposedAt;
    }

    public void setDisposedAt(LocalDateTime disposedAt) {
        this.disposedAt = disposedAt;
    }

    public Long getDisposedByUserId() {
        return disposedByUserId;
    }

    public void setDisposedByUserId(Long disposedByUserId) {
        this.disposedByUserId = disposedByUserId;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public Long getClosedByUserId() {
        return closedByUserId;
    }

    public void setClosedByUserId(Long closedByUserId) {
        this.closedByUserId = closedByUserId;
    }
}
