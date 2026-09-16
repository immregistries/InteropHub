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

/**
 * Personal read/snooze state for one user's view of one derived
 * {@link MeetingActionType} on one meeting. The action itself (whether it
 * applies, its due date, and its completion) is never stored here - it is
 * always derived fresh from authoritative meeting/agenda/communication state.
 * This row exists only to remember what a specific user has already seen or
 * chosen to snooze.
 */
@Entity
@Table(name = "es_meeting_action_state")
public class EsMeetingActionState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_meeting_action_state_id")
    private Long esMeetingActionStateId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "es_meeting_id", nullable = false)
    private Long esMeetingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private MeetingActionType actionType;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "snoozed_until")
    private LocalDateTime snoozedUntil;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsMeetingActionStateId() {
        return esMeetingActionStateId;
    }

    public void setEsMeetingActionStateId(Long esMeetingActionStateId) {
        this.esMeetingActionStateId = esMeetingActionStateId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getEsMeetingId() {
        return esMeetingId;
    }

    public void setEsMeetingId(Long esMeetingId) {
        this.esMeetingId = esMeetingId;
    }

    public MeetingActionType getActionType() {
        return actionType;
    }

    public void setActionType(MeetingActionType actionType) {
        this.actionType = actionType;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public LocalDateTime getSnoozedUntil() {
        return snoozedUntil;
    }

    public void setSnoozedUntil(LocalDateTime snoozedUntil) {
        this.snoozedUntil = snoozedUntil;
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
