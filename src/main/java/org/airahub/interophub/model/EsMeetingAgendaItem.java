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
@Table(name = "es_meeting_agenda_item")
public class EsMeetingAgendaItem {

    public enum AgendaItemStatus {
        DRAFT,
        PROPOSED,
        ACCEPTED,
        NEEDS_REVISION,
        POSTPONED,
        COVERED,
        NOT_COVERED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_meeting_agenda_item_id")
    private Long esMeetingAgendaItemId;

    @Column(name = "es_meeting_id", nullable = false)
    private Long esMeetingId;

    @Column(name = "es_topic_id")
    private Long esTopicId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "agenda_markdown", columnDefinition = "TEXT")
    private String agendaMarkdown;

    @Column(name = "time_minutes")
    private Integer timeMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AgendaItemStatus status;

    @Column(name = "proposed_by_user_id")
    private Long proposedByUserId;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "postponed_to_meeting_id")
    private Long postponedToMeetingId;

    @Column(name = "status_note", columnDefinition = "TEXT")
    private String statusNote;

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
        if (status == null) {
            status = AgendaItemStatus.DRAFT;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsMeetingAgendaItemId() {
        return esMeetingAgendaItemId;
    }

    public void setEsMeetingAgendaItemId(Long esMeetingAgendaItemId) {
        this.esMeetingAgendaItemId = esMeetingAgendaItemId;
    }

    public Long getEsMeetingId() {
        return esMeetingId;
    }

    public void setEsMeetingId(Long esMeetingId) {
        this.esMeetingId = esMeetingId;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public void setEsTopicId(Long esTopicId) {
        this.esTopicId = esTopicId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAgendaMarkdown() {
        return agendaMarkdown;
    }

    public void setAgendaMarkdown(String agendaMarkdown) {
        this.agendaMarkdown = agendaMarkdown;
    }

    public Integer getTimeMinutes() {
        return timeMinutes;
    }

    public void setTimeMinutes(Integer timeMinutes) {
        this.timeMinutes = timeMinutes;
    }

    public AgendaItemStatus getStatus() {
        return status;
    }

    public void setStatus(AgendaItemStatus status) {
        this.status = status;
    }

    public Long getProposedByUserId() {
        return proposedByUserId;
    }

    public void setProposedByUserId(Long proposedByUserId) {
        this.proposedByUserId = proposedByUserId;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Long getPostponedToMeetingId() {
        return postponedToMeetingId;
    }

    public void setPostponedToMeetingId(Long postponedToMeetingId) {
        this.postponedToMeetingId = postponedToMeetingId;
    }

    public String getStatusNote() {
        return statusNote;
    }

    public void setStatusNote(String statusNote) {
        this.statusNote = statusNote;
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
