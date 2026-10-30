package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "es_guide_document_url")
public class EsGuideDocumentUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_document_url_id")
    private Long guideDocumentUrlId;

    @Column(name = "guide_document_id", nullable = false)
    private Long guideDocumentId;

    @Column(name = "url", nullable = false, length = 255)
    private String url;

    @Column(name = "label", nullable = false, length = 40)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "allow_id")
    private Long allowId;

    @Column(name = "owns_allowlist", nullable = false)
    private Boolean ownsAllowlist;

    public Long getGuideDocumentUrlId() {
        return guideDocumentUrlId;
    }

    public void setGuideDocumentUrlId(Long guideDocumentUrlId) {
        this.guideDocumentUrlId = guideDocumentUrlId;
    }

    public Long getGuideDocumentId() {
        return guideDocumentId;
    }

    public void setGuideDocumentId(Long guideDocumentId) {
        this.guideDocumentId = guideDocumentId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getAllowId() {
        return allowId;
    }

    public void setAllowId(Long allowId) {
        this.allowId = allowId;
    }

    public Boolean getOwnsAllowlist() {
        return ownsAllowlist;
    }

    public void setOwnsAllowlist(Boolean ownsAllowlist) {
        this.ownsAllowlist = ownsAllowlist;
    }
}
