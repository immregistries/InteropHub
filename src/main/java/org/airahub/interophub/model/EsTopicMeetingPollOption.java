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
@Table(name = "es_topic_meeting_poll_option")
public class EsTopicMeetingPollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_meeting_poll_option_id")
    private Long esTopicMeetingPollOptionId;

    @Column(name = "es_topic_meeting_poll_id", nullable = false)
    private Long esTopicMeetingPollId;

    @Column(name = "starts_at_utc", nullable = false)
    private LocalDateTime startsAtUtc;

    @Column(name = "ends_at_utc")
    private LocalDateTime endsAtUtc;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

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
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsTopicMeetingPollOptionId() {
        return esTopicMeetingPollOptionId;
    }

    public void setEsTopicMeetingPollOptionId(Long esTopicMeetingPollOptionId) {
        this.esTopicMeetingPollOptionId = esTopicMeetingPollOptionId;
    }

    public Long getEsTopicMeetingPollId() {
        return esTopicMeetingPollId;
    }

    public void setEsTopicMeetingPollId(Long esTopicMeetingPollId) {
        this.esTopicMeetingPollId = esTopicMeetingPollId;
    }

    public LocalDateTime getStartsAtUtc() {
        return startsAtUtc;
    }

    public void setStartsAtUtc(LocalDateTime startsAtUtc) {
        this.startsAtUtc = startsAtUtc;
    }

    public LocalDateTime getEndsAtUtc() {
        return endsAtUtc;
    }

    public void setEndsAtUtc(LocalDateTime endsAtUtc) {
        this.endsAtUtc = endsAtUtc;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
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
