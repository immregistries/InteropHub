package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_meeting_participant_count")
public class EsMeetingParticipantCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_meeting_participant_count_id")
    private Long esMeetingParticipantCountId;

    @Column(name = "es_meeting_agenda_activity_id", nullable = false)
    private Long esMeetingAgendaActivityId;

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "recorded_by_user_id", nullable = false)
    private Long recordedByUserId;

    public Long getEsMeetingParticipantCountId() {
        return esMeetingParticipantCountId;
    }

    public void setEsMeetingParticipantCountId(Long esMeetingParticipantCountId) {
        this.esMeetingParticipantCountId = esMeetingParticipantCountId;
    }

    public Long getEsMeetingAgendaActivityId() {
        return esMeetingAgendaActivityId;
    }

    public void setEsMeetingAgendaActivityId(Long esMeetingAgendaActivityId) {
        this.esMeetingAgendaActivityId = esMeetingAgendaActivityId;
    }

    public Integer getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(Integer participantCount) {
        this.participantCount = participantCount;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Long getRecordedByUserId() {
        return recordedByUserId;
    }

    public void setRecordedByUserId(Long recordedByUserId) {
        this.recordedByUserId = recordedByUserId;
    }
}