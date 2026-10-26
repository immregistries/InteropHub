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
@Table(name = "es_small_group_participant")
public class EsSmallGroupParticipant {

    public enum Status {
        ACTIVE("Active"),
        REMOVED("Removed");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static String labelOf(String code) {
            return CodedLabel.labelOf(Status.class, code, value -> value.label);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_small_group_participant_id")
    private Long esSmallGroupParticipantId;

    @Column(name = "es_small_group_id", nullable = false)
    private Long esSmallGroupId;

    @Column(name = "es_initiative_member_id", nullable = false)
    private Long esInitiativeMemberId;

    @Column(name = "participant_role", length = 200)
    private String participantRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_status", nullable = false, length = 32)
    private Status participantStatus;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

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
        if (participantStatus == null) {
            participantStatus = Status.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsSmallGroupParticipantId() {
        return esSmallGroupParticipantId;
    }

    public void setEsSmallGroupParticipantId(Long esSmallGroupParticipantId) {
        this.esSmallGroupParticipantId = esSmallGroupParticipantId;
    }

    public Long getEsSmallGroupId() {
        return esSmallGroupId;
    }

    public void setEsSmallGroupId(Long esSmallGroupId) {
        this.esSmallGroupId = esSmallGroupId;
    }

    public Long getEsInitiativeMemberId() {
        return esInitiativeMemberId;
    }

    public void setEsInitiativeMemberId(Long esInitiativeMemberId) {
        this.esInitiativeMemberId = esInitiativeMemberId;
    }

    public String getParticipantRole() {
        return participantRole;
    }

    public void setParticipantRole(String participantRole) {
        this.participantRole = participantRole;
    }

    public Status getParticipantStatus() {
        return participantStatus;
    }

    public void setParticipantStatus(Status participantStatus) {
        this.participantStatus = participantStatus;
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
