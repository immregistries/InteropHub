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
@Table(name = "es_topic_meeting_poll_response")
public class EsTopicMeetingPollResponse {

    public enum PollResponseValue {
        YES,
        MAYBE,
        NO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_meeting_poll_response_id")
    private Long esTopicMeetingPollResponseId;

    @Column(name = "es_topic_meeting_poll_option_id", nullable = false)
    private Long esTopicMeetingPollOptionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "response", nullable = false, length = 8)
    private PollResponseValue response;

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

    public Long getEsTopicMeetingPollResponseId() {
        return esTopicMeetingPollResponseId;
    }

    public void setEsTopicMeetingPollResponseId(Long esTopicMeetingPollResponseId) {
        this.esTopicMeetingPollResponseId = esTopicMeetingPollResponseId;
    }

    public Long getEsTopicMeetingPollOptionId() {
        return esTopicMeetingPollOptionId;
    }

    public void setEsTopicMeetingPollOptionId(Long esTopicMeetingPollOptionId) {
        this.esTopicMeetingPollOptionId = esTopicMeetingPollOptionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public PollResponseValue getResponse() {
        return response;
    }

    public void setResponse(PollResponseValue response) {
        this.response = response;
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
