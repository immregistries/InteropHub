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
@Table(name = "es_topic_meeting_cochair")
public class EsTopicMeetingCochair {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_meeting_cochair_id")
    private Long esTopicMeetingCochairId;

    @Column(name = "es_topic_meeting_id", nullable = false)
    private Long esTopicMeetingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TopicMeetingCochairStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "inactive_at")
    private LocalDateTime inactiveAt;

    @Column(name = "inactive_by_user_id")
    private Long inactiveByUserId;

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
            status = TopicMeetingCochairStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsTopicMeetingCochairId() {
        return esTopicMeetingCochairId;
    }

    public void setEsTopicMeetingCochairId(Long esTopicMeetingCochairId) {
        this.esTopicMeetingCochairId = esTopicMeetingCochairId;
    }

    public Long getEsTopicMeetingId() {
        return esTopicMeetingId;
    }

    public void setEsTopicMeetingId(Long esTopicMeetingId) {
        this.esTopicMeetingId = esTopicMeetingId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public TopicMeetingCochairStatus getStatus() {
        return status;
    }

    public void setStatus(TopicMeetingCochairStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public LocalDateTime getInactiveAt() {
        return inactiveAt;
    }

    public void setInactiveAt(LocalDateTime inactiveAt) {
        this.inactiveAt = inactiveAt;
    }

    public Long getInactiveByUserId() {
        return inactiveByUserId;
    }

    public void setInactiveByUserId(Long inactiveByUserId) {
        this.inactiveByUserId = inactiveByUserId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}