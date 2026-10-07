package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_communication_bundle_template")
public class EsCommunicationBundleTemplate {

    public enum Status {
        DRAFT,
        ACTIVE,
        RETIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "purpose_id", nullable = false)
    private Long purposeId;

    @Column(name = "version_no", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = Status.DRAFT;
        }
    }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long value) { templateId = value; }
    public Long getPurposeId() { return purposeId; }
    public void setPurposeId(Long value) { purposeId = value; }
    public int getVersion() { return version; }
    public void setVersion(int value) { version = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long value) { createdByUserId = value; }
}
