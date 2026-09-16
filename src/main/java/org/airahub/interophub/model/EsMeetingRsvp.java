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
 * One registered user's RSVP for one meeting occurrence
 * (docs/meeting-attendance-console-design.md, Phase 4). Separate from
 * {@link EsMeetingAttendance} - RSVP is intent, recorded before the meeting;
 * attendance is what actually happened. A participant who RSVP'd still needs
 * to sign attendance separately when they attend.
 */
@Entity
@Table(name = "es_meeting_rsvp")
public class EsMeetingRsvp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_meeting_rsvp_id")
    private Long esMeetingRsvpId;

    @Column(name = "es_meeting_id", nullable = false)
    private Long esMeetingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "response", nullable = false, length = 16)
    private MeetingRsvpResponse response;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

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

    public Long getEsMeetingRsvpId() {
        return esMeetingRsvpId;
    }

    public void setEsMeetingRsvpId(Long esMeetingRsvpId) {
        this.esMeetingRsvpId = esMeetingRsvpId;
    }

    public Long getEsMeetingId() {
        return esMeetingId;
    }

    public void setEsMeetingId(Long esMeetingId) {
        this.esMeetingId = esMeetingId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public MeetingRsvpResponse getResponse() {
        return response;
    }

    public void setResponse(MeetingRsvpResponse response) {
        this.response = response;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
