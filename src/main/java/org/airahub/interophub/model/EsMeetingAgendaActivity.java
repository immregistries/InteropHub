package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_meeting_agenda_activity")
public class EsMeetingAgendaActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_meeting_agenda_activity_id")
    private Long esMeetingAgendaActivityId;

    @Column(name = "es_meeting_id", nullable = false)
    private Long esMeetingId;

    @Column(name = "es_meeting_agenda_item_id", nullable = false)
    private Long esMeetingAgendaItemId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "started_by_user_id", nullable = false)
    private Long startedByUserId;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "ended_by_user_id")
    private Long endedByUserId;

    public Long getEsMeetingAgendaActivityId() {
        return esMeetingAgendaActivityId;
    }

    public void setEsMeetingAgendaActivityId(Long esMeetingAgendaActivityId) {
        this.esMeetingAgendaActivityId = esMeetingAgendaActivityId;
    }

    public Long getEsMeetingId() {
        return esMeetingId;
    }

    public void setEsMeetingId(Long esMeetingId) {
        this.esMeetingId = esMeetingId;
    }

    public Long getEsMeetingAgendaItemId() {
        return esMeetingAgendaItemId;
    }

    public void setEsMeetingAgendaItemId(Long esMeetingAgendaItemId) {
        this.esMeetingAgendaItemId = esMeetingAgendaItemId;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public void setEsTopicId(Long esTopicId) {
        this.esTopicId = esTopicId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public Long getStartedByUserId() {
        return startedByUserId;
    }

    public void setStartedByUserId(Long startedByUserId) {
        this.startedByUserId = startedByUserId;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public Long getEndedByUserId() {
        return endedByUserId;
    }

    public void setEndedByUserId(Long endedByUserId) {
        this.endedByUserId = endedByUserId;
    }
}