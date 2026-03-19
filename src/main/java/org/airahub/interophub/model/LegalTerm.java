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
@Table(name = "legal_term")
public class LegalTerm {
    public enum ScopeType {
        REGISTRATION,
        WORKSPACE,
        BOTH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "term_id")
    private Long termId;

    @Column(name = "term_code", nullable = false, length = 80)
    private String termCode;

    @Column(name = "version_num", nullable = false)
    private Integer versionNum;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "short_text", nullable = false, length = 500)
    private String shortText;

    @Column(name = "full_text", columnDefinition = "TEXT")
    private String fullText;

    @Column(name = "full_text_url", length = 500)
    private String fullTextUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16)
    private ScopeType scopeType;

    @Column(name = "is_required", nullable = false)
    private Boolean required;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (scopeType == null) {
            scopeType = ScopeType.REGISTRATION;
        }
        if (required == null) {
            required = Boolean.TRUE;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
        if (active == null) {
            active = Boolean.TRUE;
        }
        if (effectiveAt == null) {
            effectiveAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public Long getTermId() {
        return termId;
    }

    public void setTermId(Long termId) {
        this.termId = termId;
    }

    public String getTermCode() {
        return termCode;
    }

    public void setTermCode(String termCode) {
        this.termCode = termCode;
    }

    public Integer getVersionNum() {
        return versionNum;
    }

    public void setVersionNum(Integer versionNum) {
        this.versionNum = versionNum;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortText() {
        return shortText;
    }

    public void setShortText(String shortText) {
        this.shortText = shortText;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }

    public String getFullTextUrl() {
        return fullTextUrl;
    }

    public void setFullTextUrl(String fullTextUrl) {
        this.fullTextUrl = fullTextUrl;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(ScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(LocalDateTime effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public LocalDateTime getRetiredAt() {
        return retiredAt;
    }

    public void setRetiredAt(LocalDateTime retiredAt) {
        this.retiredAt = retiredAt;
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
}
