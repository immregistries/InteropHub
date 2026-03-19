package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "legal_term_acceptance")
public class LegalTermAcceptance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "acceptance_id")
    private Long acceptanceId;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "accepted_value", nullable = false)
    private Boolean acceptedValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        if (acceptedAt == null) {
            acceptedAt = LocalDateTime.now();
        }
        if (acceptedValue == null) {
            acceptedValue = Boolean.TRUE;
        }
    }

    public Long getAcceptanceId() {
        return acceptanceId;
    }

    public void setAcceptanceId(Long acceptanceId) {
        this.acceptanceId = acceptanceId;
    }

    public Long getTermId() {
        return termId;
    }

    public void setTermId(Long termId) {
        this.termId = termId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Boolean getAcceptedValue() {
        return acceptedValue;
    }

    public void setAcceptedValue(Boolean acceptedValue) {
        this.acceptedValue = acceptedValue;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
