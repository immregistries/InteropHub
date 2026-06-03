package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_topic_meeting_poll")
public class EsTopicMeetingPoll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_meeting_poll_id")
    private Long esTopicMeetingPollId;

    @Column(name = "es_topic_meeting_id", nullable = false)
    private Long esTopicMeetingId;

    @Column(name = "poll_name", nullable = false, length = 160)
    private String pollName;

    @Column(name = "poll_description", columnDefinition = "TEXT")
    private String pollDescription;

    @Column(name = "default_timezone", nullable = false, length = 80)
    private String defaultTimezone;

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

    public Long getEsTopicMeetingPollId() {
        return esTopicMeetingPollId;
    }

    public void setEsTopicMeetingPollId(Long esTopicMeetingPollId) {
        this.esTopicMeetingPollId = esTopicMeetingPollId;
    }

    public Long getEsTopicMeetingId() {
        return esTopicMeetingId;
    }

    public void setEsTopicMeetingId(Long esTopicMeetingId) {
        this.esTopicMeetingId = esTopicMeetingId;
    }

    public String getPollName() {
        return pollName;
    }

    public void setPollName(String pollName) {
        this.pollName = pollName;
    }

    public String getPollDescription() {
        return pollDescription;
    }

    public void setPollDescription(String pollDescription) {
        this.pollDescription = pollDescription;
    }

    public String getDefaultTimezone() {
        return defaultTimezone;
    }

    public void setDefaultTimezone(String defaultTimezone) {
        this.defaultTimezone = defaultTimezone;
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
