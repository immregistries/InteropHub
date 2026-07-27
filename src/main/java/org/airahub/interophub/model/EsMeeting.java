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
@Table(name = "es_meeting")
public class EsMeeting {

    public enum MeetingStatus {
        DRAFT,
        PROPOSED,
        FINALIZED,
        IN_SESSION,
        COMPLETED,
        CLOSED,
        CANCELLED
    }

    public enum MeetingCloseMethod {
        MANUAL,
        AUTOMATIC
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_meeting_id")
    private Long esMeetingId;

    @Column(name = "es_topic_meeting_id", nullable = false)
    private Long esTopicMeetingId;

    @Column(name = "es_topic_space_id")
    private Long esTopicSpaceId;

    @Column(name = "meeting_key", length = 80)
    private String meetingKey;

    @Column(name = "meeting_name", nullable = false, length = 160)
    private String meetingName;

    @Column(name = "meeting_description", columnDefinition = "TEXT")
    private String meetingDescription;

    @Column(name = "scheduled_start", nullable = false)
    private LocalDateTime scheduledStart;

    @Column(name = "scheduled_end")
    private LocalDateTime scheduledEnd;

    @Column(name = "timezone_id", length = 64)
    private String timezoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MeetingStatus status;

    @Column(name = "designated_chair_user_id")
    private Long designatedChairUserId;

    @Column(name = "designated_scribe_user_id")
    private Long designatedScribeUserId;

    @Column(name = "current_chair_user_id")
    private Long currentChairUserId;

    @Column(name = "current_scribe_user_id")
    private Long currentScribeUserId;

    @Column(name = "current_agenda_item_id")
    private Long currentAgendaItemId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "started_by_user_id")
    private Long startedByUserId;

    @Column(name = "completed_by_user_id")
    private Long completedByUserId;

    @Column(name = "close_due_at")
    private LocalDateTime closeDueAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_method", length = 16)
    private MeetingCloseMethod closeMethod;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "online_meeting_url", length = 2048)
    private String onlineMeetingUrl;

    @Column(name = "online_meeting_details", columnDefinition = "TEXT")
    private String onlineMeetingDetails;

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
            status = MeetingStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsMeetingId() {
        return esMeetingId;
    }

    public void setEsMeetingId(Long esMeetingId) {
        this.esMeetingId = esMeetingId;
    }

    public Long getEsTopicMeetingId() {
        return esTopicMeetingId;
    }

    public void setEsTopicMeetingId(Long esTopicMeetingId) {
        this.esTopicMeetingId = esTopicMeetingId;
    }

    public Long getEsTopicSpaceId() {
        return esTopicSpaceId;
    }

    public void setEsTopicSpaceId(Long esTopicSpaceId) {
        this.esTopicSpaceId = esTopicSpaceId;
    }

    public String getMeetingKey() {
        return meetingKey;
    }

    public void setMeetingKey(String meetingKey) {
        this.meetingKey = meetingKey;
    }

    public String getMeetingName() {
        return meetingName;
    }

    public void setMeetingName(String meetingName) {
        this.meetingName = meetingName;
    }

    public String getMeetingDescription() {
        return meetingDescription;
    }

    public void setMeetingDescription(String meetingDescription) {
        this.meetingDescription = meetingDescription;
    }

    public LocalDateTime getScheduledStart() {
        return scheduledStart;
    }

    public void setScheduledStart(LocalDateTime scheduledStart) {
        this.scheduledStart = scheduledStart;
    }

    public LocalDateTime getScheduledEnd() {
        return scheduledEnd;
    }

    public void setScheduledEnd(LocalDateTime scheduledEnd) {
        this.scheduledEnd = scheduledEnd;
    }

    public String getTimezoneId() {
        return timezoneId;
    }

    public void setTimezoneId(String timezoneId) {
        this.timezoneId = timezoneId;
    }

    public MeetingStatus getStatus() {
        return status;
    }

    public void setStatus(MeetingStatus status) {
        this.status = status;
    }

    public Long getDesignatedChairUserId() {
        return designatedChairUserId;
    }

    public void setDesignatedChairUserId(Long designatedChairUserId) {
        this.designatedChairUserId = designatedChairUserId;
    }

    public Long getDesignatedScribeUserId() {
        return designatedScribeUserId;
    }

    public void setDesignatedScribeUserId(Long designatedScribeUserId) {
        this.designatedScribeUserId = designatedScribeUserId;
    }

    public Long getCurrentChairUserId() {
        return currentChairUserId;
    }

    public void setCurrentChairUserId(Long currentChairUserId) {
        this.currentChairUserId = currentChairUserId;
    }

    public Long getCurrentScribeUserId() {
        return currentScribeUserId;
    }

    public void setCurrentScribeUserId(Long currentScribeUserId) {
        this.currentScribeUserId = currentScribeUserId;
    }

    public Long getCurrentAgendaItemId() {
        return currentAgendaItemId;
    }

    public void setCurrentAgendaItemId(Long currentAgendaItemId) {
        this.currentAgendaItemId = currentAgendaItemId;
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

    public Long getCompletedByUserId() {
        return completedByUserId;
    }

    public void setCompletedByUserId(Long completedByUserId) {
        this.completedByUserId = completedByUserId;
    }

    public LocalDateTime getCloseDueAt() {
        return closeDueAt;
    }

    public void setCloseDueAt(LocalDateTime closeDueAt) {
        this.closeDueAt = closeDueAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public Long getClosedByUserId() {
        return closedByUserId;
    }

    public void setClosedByUserId(Long closedByUserId) {
        this.closedByUserId = closedByUserId;
    }

    public MeetingCloseMethod getCloseMethod() {
        return closeMethod;
    }

    public void setCloseMethod(MeetingCloseMethod closeMethod) {
        this.closeMethod = closeMethod;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public LocalDateTime getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(LocalDateTime finalizedAt) {
        this.finalizedAt = finalizedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public String getOnlineMeetingUrl() {
        return onlineMeetingUrl;
    }

    public void setOnlineMeetingUrl(String onlineMeetingUrl) {
        this.onlineMeetingUrl = onlineMeetingUrl;
    }

    public String getOnlineMeetingDetails() {
        return onlineMeetingDetails;
    }

    public void setOnlineMeetingDetails(String onlineMeetingDetails) {
        this.onlineMeetingDetails = onlineMeetingDetails;
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
