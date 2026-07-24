package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_meeting_status_history")
public class EsMeetingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_meeting_status_history_id")
    private Long esMeetingStatusHistoryId;

    @Column(name = "es_meeting_id", nullable = false)
    private Long esMeetingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    private EsMeeting.MeetingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    private EsMeeting.MeetingStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transition_method", nullable = false, length = 16)
    private MeetingTransitionMethod transitionMethod;

    public Long getEsMeetingStatusHistoryId() {
        return esMeetingStatusHistoryId;
    }

    public void setEsMeetingStatusHistoryId(Long esMeetingStatusHistoryId) {
        this.esMeetingStatusHistoryId = esMeetingStatusHistoryId;
    }

    public Long getEsMeetingId() {
        return esMeetingId;
    }

    public void setEsMeetingId(Long esMeetingId) {
        this.esMeetingId = esMeetingId;
    }

    public EsMeeting.MeetingStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(EsMeeting.MeetingStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public EsMeeting.MeetingStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(EsMeeting.MeetingStatus toStatus) {
        this.toStatus = toStatus;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public void setChangedByUserId(Long changedByUserId) {
        this.changedByUserId = changedByUserId;
    }

    public MeetingTransitionMethod getTransitionMethod() {
        return transitionMethod;
    }

    public void setTransitionMethod(MeetingTransitionMethod transitionMethod) {
        this.transitionMethod = transitionMethod;
    }
}