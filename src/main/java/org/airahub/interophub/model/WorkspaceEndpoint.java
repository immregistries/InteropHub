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
@Table(name = "workspace_endpoint")
public class WorkspaceEndpoint {
    public enum EndpointType {
        FHIR_BASE,
        SMART_CONFIG,
        WEBHOOK,
        OTHER
    }

    public enum AuthType {
        AIRA_TOKEN,
        BEARER_PAT,
        NONE,
        OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "endpoint_id")
    private Long endpointId;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "endpoint_type", nullable = false, length = 20)
    private EndpointType endpointType;

    @Column(name = "url", length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    private AuthType authType;

    @Column(name = "auth_instructions", columnDefinition = "TEXT")
    private String authInstructions;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (endpointType == null) {
            endpointType = EndpointType.FHIR_BASE;
        }
        if (authType == null) {
            authType = AuthType.BEARER_PAT;
        }
        if (active == null) {
            active = Boolean.TRUE;
        }
    }

    public Long getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(Long endpointId) {
        this.endpointId = endpointId;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public EndpointType getEndpointType() {
        return endpointType;
    }

    public void setEndpointType(EndpointType endpointType) {
        this.endpointType = endpointType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public String getAuthInstructions() {
        return authInstructions;
    }

    public void setAuthInstructions(String authInstructions) {
        this.authInstructions = authInstructions;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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
