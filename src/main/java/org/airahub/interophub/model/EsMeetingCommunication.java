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
@Table(name = "es_meeting_communication")
public class EsMeetingCommunication {

    public enum CommunicationType {
        CALL_FOR_TOPICS,
        PROPOSED_AGENDA,
        FINAL_AGENDA,
        REMINDER,
        CANCELLED
    }

    public enum CommunicationStatus {
        DRAFT,
        SCHEDULED,
        SENDING,
        SENT,
        CANCELLED,
        FAILED
    }

    public enum ExpectedMeetingStatus {
        DRAFT,
        PROPOSED,
        FINALIZED,
        COMPLETED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_meeting_communication_id")
    private Long esMeetingCommunicationId;

    @Column(name = "es_meeting_id", nullable = false)
    private Long esMeetingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "communication_type", nullable = false, length = 20)
    private CommunicationType communicationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private CommunicationStatus status;

    @Column(name = "scheduled_send_at")
    private LocalDateTime scheduledSendAt;

    @Column(name = "timezone_id", length = 64)
    private String timezoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "expected_meeting_status", length = 12)
    private ExpectedMeetingStatus expectedMeetingStatus;

    @Column(name = "include_general_members", nullable = false)
    private boolean includeGeneralMembers = true;

    @Column(name = "include_topic_subscribers", nullable = false)
    private boolean includeTopicSubscribers = true;

    @Column(name = "include_topic_champions", nullable = false)
    private boolean includeTopicChampions = true;

    @Column(name = "include_presenters", nullable = false)
    private boolean includePresenters = true;

    @Column(name = "subject_override", length = 500)
    private String subjectOverride;

    @Column(name = "note_to_include", columnDefinition = "TEXT")
    private String noteToInclude;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    // Reserved for future two-admin approval workflow
    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "sent_started_at")
    private LocalDateTime sentStartedAt;

    @Column(name = "sent_completed_at")
    private LocalDateTime sentCompletedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by_user_id")
    private Long cancelledByUserId;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

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
            status = CommunicationStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsMeetingCommunicationId() {
        return esMeetingCommunicationId;
    }

    public void setEsMeetingCommunicationId(Long esMeetingCommunicationId) {
        this.esMeetingCommunicationId = esMeetingCommunicationId;
    }

    public Long getEsMeetingId() {
        return esMeetingId;
    }

    public void setEsMeetingId(Long esMeetingId) {
        this.esMeetingId = esMeetingId;
    }

    public CommunicationType getCommunicationType() {
        return communicationType;
    }

    public void setCommunicationType(CommunicationType communicationType) {
        this.communicationType = communicationType;
    }

    public CommunicationStatus getStatus() {
        return status;
    }

    public void setStatus(CommunicationStatus status) {
        this.status = status;
    }

    public LocalDateTime getScheduledSendAt() {
        return scheduledSendAt;
    }

    public void setScheduledSendAt(LocalDateTime scheduledSendAt) {
        this.scheduledSendAt = scheduledSendAt;
    }

    public String getTimezoneId() {
        return timezoneId;
    }

    public void setTimezoneId(String timezoneId) {
        this.timezoneId = timezoneId;
    }

    public ExpectedMeetingStatus getExpectedMeetingStatus() {
        return expectedMeetingStatus;
    }

    public void setExpectedMeetingStatus(ExpectedMeetingStatus expectedMeetingStatus) {
        this.expectedMeetingStatus = expectedMeetingStatus;
    }

    public boolean isIncludeGeneralMembers() {
        return includeGeneralMembers;
    }

    public void setIncludeGeneralMembers(boolean includeGeneralMembers) {
        this.includeGeneralMembers = includeGeneralMembers;
    }

    public boolean isIncludeTopicSubscribers() {
        return includeTopicSubscribers;
    }

    public void setIncludeTopicSubscribers(boolean includeTopicSubscribers) {
        this.includeTopicSubscribers = includeTopicSubscribers;
    }

    public boolean isIncludeTopicChampions() {
        return includeTopicChampions;
    }

    public void setIncludeTopicChampions(boolean includeTopicChampions) {
        this.includeTopicChampions = includeTopicChampions;
    }

    public boolean isIncludePresenters() {
        return includePresenters;
    }

    public void setIncludePresenters(boolean includePresenters) {
        this.includePresenters = includePresenters;
    }

    public String getSubjectOverride() {
        return subjectOverride;
    }

    public void setSubjectOverride(String subjectOverride) {
        this.subjectOverride = subjectOverride;
    }

    public String getNoteToInclude() {
        return noteToInclude;
    }

    public void setNoteToInclude(String noteToInclude) {
        this.noteToInclude = noteToInclude;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public Long getApprovedByUserId() {
        return approvedByUserId;
    }

    public void setApprovedByUserId(Long approvedByUserId) {
        this.approvedByUserId = approvedByUserId;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getSentStartedAt() {
        return sentStartedAt;
    }

    public void setSentStartedAt(LocalDateTime sentStartedAt) {
        this.sentStartedAt = sentStartedAt;
    }

    public LocalDateTime getSentCompletedAt() {
        return sentCompletedAt;
    }

    public void setSentCompletedAt(LocalDateTime sentCompletedAt) {
        this.sentCompletedAt = sentCompletedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Long getCancelledByUserId() {
        return cancelledByUserId;
    }

    public void setCancelledByUserId(Long cancelledByUserId) {
        this.cancelledByUserId = cancelledByUserId;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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
