package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_communication_bundle")
public class EsCommunicationBundle {

    public enum Status {
        DRAFT,
        PUBLISHED,
        RETIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bundle_id")
    private Long bundleId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "purpose_id", nullable = false)
    private Long purposeId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "single_instance_guard")
    private Integer singleInstanceGuard;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_scope", nullable = false, length = 16)
    private EsCommunicationBundlePurpose.Audience audience;

    @Column(name = "communication_month")
    private Integer communicationMonth;

    @Column(name = "communication_year")
    private Integer communicationYear;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = Status.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getBundleId() { return bundleId; }
    public void setBundleId(Long value) { bundleId = value; }
    public Long getEsTopicId() { return esTopicId; }
    public void setEsTopicId(Long value) { esTopicId = value; }
    public Long getPurposeId() { return purposeId; }
    public void setPurposeId(Long value) { purposeId = value; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long value) { templateId = value; }
    public Integer getSingleInstanceGuard() { return singleInstanceGuard; }
    public void setSingleInstanceGuard(Integer value) { singleInstanceGuard = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public EsCommunicationBundlePurpose.Audience getAudience() { return audience; }
    public void setAudience(EsCommunicationBundlePurpose.Audience value) { audience = value; }
    public Integer getCommunicationMonth() { return communicationMonth; }
    public void setCommunicationMonth(Integer value) { communicationMonth = value; }
    public Integer getCommunicationYear() { return communicationYear; }
    public void setCommunicationYear(Integer value) { communicationYear = value; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long value) { createdByUserId = value; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
    public void setUpdatedByUserId(Long value) { updatedByUserId = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
}
