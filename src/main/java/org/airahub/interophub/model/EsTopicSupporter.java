package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Join row recording that a {@link Supporter} supports a Topic. Mirrors the
 * shape of {@link EsTopicNeighborhood}: the relationship either exists or it
 * doesn't, with no status/type field. Removing a row never affects the
 * referenced {@link Supporter} record.
 */
@Entity
@Table(name = "es_topic_supporter")
public class EsTopicSupporter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_supporter_id")
    private Long esTopicSupporterId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "supporter_id", nullable = false)
    private Long supporterId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getEsTopicSupporterId() {
        return esTopicSupporterId;
    }

    public void setEsTopicSupporterId(Long esTopicSupporterId) {
        this.esTopicSupporterId = esTopicSupporterId;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public void setEsTopicId(Long esTopicId) {
        this.esTopicId = esTopicId;
    }

    public Long getSupporterId() {
        return supporterId;
    }

    public void setSupporterId(Long supporterId) {
        this.supporterId = supporterId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
