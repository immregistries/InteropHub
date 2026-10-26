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
@Table(name = "es_checklist_template")
public class EsChecklistTemplate {

    public enum Kind {
        INITIATIVE,
        SMALL_GROUP
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_checklist_template_id")
    private Long esChecklistTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_kind", nullable = false, length = 32)
    private Kind templateKind;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "source_markdown", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String sourceMarkdown;

    @Column(name = "is_current", nullable = false)
    private Boolean current;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
        if (current == null) {
            current = Boolean.FALSE;
        }
    }

    public Long getEsChecklistTemplateId() {
        return esChecklistTemplateId;
    }

    public void setEsChecklistTemplateId(Long esChecklistTemplateId) {
        this.esChecklistTemplateId = esChecklistTemplateId;
    }

    public Kind getTemplateKind() {
        return templateKind;
    }

    public void setTemplateKind(Kind templateKind) {
        this.templateKind = templateKind;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceMarkdown() {
        return sourceMarkdown;
    }

    public void setSourceMarkdown(String sourceMarkdown) {
        this.sourceMarkdown = sourceMarkdown;
    }

    public Boolean getCurrent() {
        return current;
    }

    public void setCurrent(Boolean current) {
        this.current = current;
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
