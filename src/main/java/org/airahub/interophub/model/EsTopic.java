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
@Table(name = "es_topic")
public class EsTopic {

    public enum EsTopicStatus {
        ACTIVE,
        RETIRED,
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_id")
    private Long esTopicId;

    @Column(name = "topic_code", nullable = false, unique = true, length = 80)
    private String topicCode;

    @Column(name = "topic_name", nullable = false, length = 140)
    private String topicName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "neighborhood", length = 120)
    private String neighborhood;

    @Column(name = "priority_iis", nullable = false)
    private Integer priorityIis;

    @Column(name = "priority_ehr", nullable = false)
    private Integer priorityEhr;

    @Column(name = "priority_cdc", nullable = false)
    private Integer priorityCdc;

    @Column(name = "stage", length = 80)
    private String stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EsTopicStatus status;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

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
            status = EsTopicStatus.ACTIVE;
        }
        if (priorityIis == null) {
            priorityIis = 0;
        }
        if (priorityEhr == null) {
            priorityEhr = 0;
        }
        if (priorityCdc == null) {
            priorityCdc = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public void setEsTopicId(Long esTopicId) {
        this.esTopicId = esTopicId;
    }

    public String getTopicCode() {
        return topicCode;
    }

    public void setTopicCode(String topicCode) {
        this.topicCode = topicCode;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public Integer getPriorityIis() {
        return priorityIis;
    }

    public void setPriorityIis(Integer priorityIis) {
        this.priorityIis = priorityIis;
    }

    public Integer getPriorityEhr() {
        return priorityEhr;
    }

    public void setPriorityEhr(Integer priorityEhr) {
        this.priorityEhr = priorityEhr;
    }

    public Integer getPriorityCdc() {
        return priorityCdc;
    }

    public void setPriorityCdc(Integer priorityCdc) {
        this.priorityCdc = priorityCdc;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public EsTopicStatus getStatus() {
        return status;
    }

    public void setStatus(EsTopicStatus status) {
        this.status = status;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
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
