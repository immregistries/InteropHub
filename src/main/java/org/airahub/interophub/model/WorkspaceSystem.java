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
@Table(name = "workspace_system")
public class WorkspaceSystem {
    public enum ManagedBy {
        AIRA,
        THIRD_PARTY
    }

    public enum Capability {
        CLIENT,
        SERVER,
        BOTH
    }

    public enum Availability {
        UP,
        DOWN,
        INTERMITTENT,
        UNKNOWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "system_id")
    private Long systemId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "system_name", nullable = false, length = 160)
    private String systemName;

    @Enumerated(EnumType.STRING)
    @Column(name = "managed_by", nullable = false, length = 16)
    private ManagedBy managedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false, length = 16)
    private Capability capability;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false, length = 16)
    private Availability availability;

    @Column(name = "availability_note", length = 400)
    private String availabilityNote;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "how_to_use", columnDefinition = "TEXT")
    private String howToUse;

    @Column(name = "limitations", columnDefinition = "TEXT")
    private String limitations;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (managedBy == null) {
            managedBy = ManagedBy.THIRD_PARTY;
        }
        if (availability == null) {
            availability = Availability.UNKNOWN;
        }
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public ManagedBy getManagedBy() {
        return managedBy;
    }

    public void setManagedBy(ManagedBy managedBy) {
        this.managedBy = managedBy;
    }

    public Capability getCapability() {
        return capability;
    }

    public void setCapability(Capability capability) {
        this.capability = capability;
    }

    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public String getAvailabilityNote() {
        return availabilityNote;
    }

    public void setAvailabilityNote(String availabilityNote) {
        this.availabilityNote = availabilityNote;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHowToUse() {
        return howToUse;
    }

    public void setHowToUse(String howToUse) {
        this.howToUse = howToUse;
    }

    public String getLimitations() {
        return limitations;
    }

    public void setLimitations(String limitations) {
        this.limitations = limitations;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
