package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_guide_comment_audit")
public class EsGuideCommentAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_comment_audit_id")
    private Long guideCommentAuditId;

    @Column(name = "guide_comment_id", nullable = false)
    private Long guideCommentId;

    @Column(name = "changed_by_user_id", nullable = false)
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "field_name", nullable = false, length = 32)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "app_user_token_id")
    private Long appUserTokenId;

    public Long getGuideCommentAuditId() {
        return guideCommentAuditId;
    }

    public void setGuideCommentAuditId(Long guideCommentAuditId) {
        this.guideCommentAuditId = guideCommentAuditId;
    }

    public Long getGuideCommentId() {
        return guideCommentId;
    }

    public void setGuideCommentId(Long guideCommentId) {
        this.guideCommentId = guideCommentId;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public void setChangedByUserId(Long changedByUserId) {
        this.changedByUserId = changedByUserId;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public Long getAppUserTokenId() {
        return appUserTokenId;
    }

    public void setAppUserTokenId(Long appUserTokenId) {
        this.appUserTokenId = appUserTokenId;
    }
}
