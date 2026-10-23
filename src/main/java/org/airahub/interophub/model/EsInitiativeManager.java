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
@Table(name = "es_initiative_manager")
public class EsInitiativeManager {

    public enum ManagerRole {
        ADMIN("Admin"),
        EDITOR("Editor"),
        VIEWER("Viewer");

        private final String label;

        ManagerRole(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static String labelOf(String code) {
            return CodedLabel.labelOf(ManagerRole.class, code, value -> value.label);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_initiative_manager_id")
    private Long esInitiativeManagerId;

    @Column(name = "es_initiative_id", nullable = false)
    private Long esInitiativeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "manager_role", nullable = false, length = 32)
    private ManagerRole managerRole;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getEsInitiativeManagerId() {
        return esInitiativeManagerId;
    }

    public void setEsInitiativeManagerId(Long esInitiativeManagerId) {
        this.esInitiativeManagerId = esInitiativeManagerId;
    }

    public Long getEsInitiativeId() {
        return esInitiativeId;
    }

    public void setEsInitiativeId(Long esInitiativeId) {
        this.esInitiativeId = esInitiativeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public ManagerRole getManagerRole() {
        return managerRole;
    }

    public void setManagerRole(ManagerRole managerRole) {
        this.managerRole = managerRole;
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
