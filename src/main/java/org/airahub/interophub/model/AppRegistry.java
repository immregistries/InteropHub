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
@Table(name = "app_registry")
public class AppRegistry {
    public enum ManagedBy {
        AIRA,
        THIRD_PARTY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "app_id")
    private Long appId;

    @Column(name = "app_code", nullable = false, unique = true, length = 60)
    private String appCode;

    @Column(name = "app_name", nullable = false, length = 120)
    private String appName;

    @Column(name = "default_redirect_url", length = 255)
    private String defaultRedirectUrl;

    @Column(name = "app_description")
    private String appDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "managed_by", nullable = false, length = 16)
    private ManagedBy managedBy;

    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "kill_switch", nullable = false)
    private Boolean killSwitch;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (managedBy == null) {
            managedBy = ManagedBy.AIRA;
        }
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
        if (killSwitch == null) {
            killSwitch = Boolean.FALSE;
        }
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getDefaultRedirectUrl() {
        return defaultRedirectUrl;
    }

    public void setDefaultRedirectUrl(String defaultRedirectUrl) {
        this.defaultRedirectUrl = defaultRedirectUrl;
    }

    public String getAppDescription() {
        return appDescription;
    }

    public void setAppDescription(String appDescription) {
        this.appDescription = appDescription;
    }

    public ManagedBy getManagedBy() {
        return managedBy;
    }

    public void setManagedBy(ManagedBy managedBy) {
        this.managedBy = managedBy;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getKillSwitch() {
        return killSwitch;
    }

    public void setKillSwitch(Boolean killSwitch) {
        this.killSwitch = killSwitch;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
