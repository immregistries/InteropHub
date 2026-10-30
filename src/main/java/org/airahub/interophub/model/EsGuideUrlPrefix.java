package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_guide_url_prefix")
public class EsGuideUrlPrefix {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guide_url_prefix_id")
    private Long guideUrlPrefixId;

    @Column(name = "url_prefix", nullable = false, length = 255)
    private String urlPrefix;

    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    @Column(name = "disabled_by_user_id")
    private Long disabledByUserId;

    public Long getGuideUrlPrefixId() {
        return guideUrlPrefixId;
    }

    public void setGuideUrlPrefixId(Long guideUrlPrefixId) {
        this.guideUrlPrefixId = guideUrlPrefixId;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

    public LocalDateTime getDisabledAt() {
        return disabledAt;
    }

    public void setDisabledAt(LocalDateTime disabledAt) {
        this.disabledAt = disabledAt;
    }

    public Long getDisabledByUserId() {
        return disabledByUserId;
    }

    public void setDisabledByUserId(Long disabledByUserId) {
        this.disabledByUserId = disabledByUserId;
    }
}
