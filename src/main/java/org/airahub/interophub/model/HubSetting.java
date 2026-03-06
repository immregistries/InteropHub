package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "hub_settings")
public class HubSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "setting_id")
    private Long settingId;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "external_base_url", nullable = false, length = 300)
    private String externalBaseUrl;

    @Column(name = "smtp_host", nullable = false, length = 255)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private Integer smtpPort;

    @Column(name = "smtp_username", nullable = false, length = 255)
    private String smtpUsername;

    @Column(name = "smtp_password", nullable = false, length = 255)
    private String smtpPassword;

    @Column(name = "smtp_auth", nullable = false)
    private Boolean smtpAuth;

    @Column(name = "smtp_starttls", nullable = false)
    private Boolean smtpStarttls;

    @Column(name = "smtp_ssl", nullable = false)
    private Boolean smtpSsl;

    @Column(name = "smtp_from_email", nullable = false, length = 254)
    private String smtpFromEmail;

    @Column(name = "smtp_from_name", nullable = false, length = 160)
    private String smtpFromName;

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
        updatedAt = now;
        if (active == null) {
            active = Boolean.TRUE;
        }
        if (smtpAuth == null) {
            smtpAuth = Boolean.TRUE;
        }
        if (smtpStarttls == null) {
            smtpStarttls = Boolean.TRUE;
        }
        if (smtpSsl == null) {
            smtpSsl = Boolean.FALSE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getSettingId() {
        return settingId;
    }

    public void setSettingId(Long settingId) {
        this.settingId = settingId;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getExternalBaseUrl() {
        return externalBaseUrl;
    }

    public void setExternalBaseUrl(String externalBaseUrl) {
        this.externalBaseUrl = externalBaseUrl;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public Integer getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(Integer smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public String getSmtpPassword() {
        return smtpPassword;
    }

    public void setSmtpPassword(String smtpPassword) {
        this.smtpPassword = smtpPassword;
    }

    public Boolean getSmtpAuth() {
        return smtpAuth;
    }

    public void setSmtpAuth(Boolean smtpAuth) {
        this.smtpAuth = smtpAuth;
    }

    public Boolean getSmtpStarttls() {
        return smtpStarttls;
    }

    public void setSmtpStarttls(Boolean smtpStarttls) {
        this.smtpStarttls = smtpStarttls;
    }

    public Boolean getSmtpSsl() {
        return smtpSsl;
    }

    public void setSmtpSsl(Boolean smtpSsl) {
        this.smtpSsl = smtpSsl;
    }

    public String getSmtpFromEmail() {
        return smtpFromEmail;
    }

    public void setSmtpFromEmail(String smtpFromEmail) {
        this.smtpFromEmail = smtpFromEmail;
    }

    public String getSmtpFromName() {
        return smtpFromName;
    }

    public void setSmtpFromName(String smtpFromName) {
        this.smtpFromName = smtpFromName;
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
