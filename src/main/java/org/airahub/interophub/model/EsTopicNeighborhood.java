package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_topic_neighborhood")
public class EsTopicNeighborhood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_neighborhood_id")
    private Long esTopicNeighborhoodId;

    @Column(name = "es_topic_id", nullable = false)
    private Long esTopicId;

    @Column(name = "es_neighborhood_id", nullable = false)
    private Long esNeighborhoodId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getEsTopicNeighborhoodId() {
        return esTopicNeighborhoodId;
    }

    public void setEsTopicNeighborhoodId(Long esTopicNeighborhoodId) {
        this.esTopicNeighborhoodId = esTopicNeighborhoodId;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public void setEsTopicId(Long esTopicId) {
        this.esTopicId = esTopicId;
    }

    public Long getEsNeighborhoodId() {
        return esNeighborhoodId;
    }

    public void setEsNeighborhoodId(Long esNeighborhoodId) {
        this.esNeighborhoodId = esNeighborhoodId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
