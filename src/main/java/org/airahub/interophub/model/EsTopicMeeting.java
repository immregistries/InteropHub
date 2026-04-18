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
@Table(name = "es_topic_meeting")
public class EsTopicMeeting {

    public enum MeetingStatus {
        ACTIVE,
        DISABLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_meeting_id")
    private Long esTopicMeetingId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "meeting_name", nullable = false, length = 160)
    private String meetingName;

    @Column(name = "meeting_description", columnDefinition = "TEXT")
    private String meetingDescription;

    @Column(name = "join_requires_approval", nullable = false)
    private Boolean joinRequiresApproval;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MeetingStatus status;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    @Column(name = "disabled_by_user_id")
    private Long disabledByUserId;

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
            status = MeetingStatus.ACTIVE;
        }
        if (joinRequiresApproval == null) {
            joinRequiresApproval = Boolean.FALSE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsTopicMeetingId() {
        return esTopicMeetingId;
    }

    public void setEsTopicMeetingId(Long esTopicMeetingId) {
        this.esTopicMeetingId = esTopicMeetingId;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public void setEsTopicId(Long esTopicId) {
        this.esTopicId = esTopicId;
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

    public Boolean getJoinRequiresApproval() {
        return joinRequiresApproval;
    }

    public void setJoinRequiresApproval(Boolean joinRequiresApproval) {
        this.joinRequiresApproval = joinRequiresApproval;
    }

    public MeetingStatus getStatus() {
        return status;
    }

    public void setStatus(MeetingStatus status) {
        this.status = status;
    }

    public LocalDateTime getDisabledAt() {
        return disabledAt;
    }

    public void setDisabledAt(LocalDateTime disabledAt) {
        this.disabledAt = disabledAt;
    }

    public Long getDisabledByUserId() {
        return disabledByUserId;
    }

    public void setDisabledByUserId(Long disabledByUserId) {
        this.disabledByUserId = disabledByUserId;
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
