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
@Table(name = "es_checklist")
public class EsChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_checklist_id")
    private Long esChecklistId;

    @Column(name = "es_initiative_id", nullable = false)
    private Long esInitiativeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_kind", nullable = false, length = 32)
    private EsChecklistTemplate.Kind templateKind;

    @Column(name = "applied_es_checklist_template_id", nullable = false)
    private Long appliedEsChecklistTemplateId;

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
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsChecklistId() {
        return esChecklistId;
    }

    public void setEsChecklistId(Long esChecklistId) {
        this.esChecklistId = esChecklistId;
    }

    public Long getEsInitiativeId() {
        return esInitiativeId;
    }

    public void setEsInitiativeId(Long esInitiativeId) {
        this.esInitiativeId = esInitiativeId;
    }

    public EsChecklistTemplate.Kind getTemplateKind() {
        return templateKind;
    }

    public void setTemplateKind(EsChecklistTemplate.Kind templateKind) {
        this.templateKind = templateKind;
    }

    public Long getAppliedEsChecklistTemplateId() {
        return appliedEsChecklistTemplateId;
    }

    public void setAppliedEsChecklistTemplateId(Long appliedEsChecklistTemplateId) {
        this.appliedEsChecklistTemplateId = appliedEsChecklistTemplateId;
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
