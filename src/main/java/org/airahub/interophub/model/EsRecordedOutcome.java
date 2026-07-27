package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "es_recorded_outcome")
public class EsRecordedOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_recorded_outcome_id")
    private Long esRecordedOutcomeId;

    @Column(name = "es_topic_note_id", nullable = false)
    private Long esTopicNoteId;

    @Column(name = "source_node_id", nullable = false, length = 128)
    private String sourceNodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_type", nullable = false, length = 32)
    private RecordedOutcomeType outcomeType;

    @Column(name = "short_title", length = 200)
    private String shortTitle;

    @Column(name = "outcome_text", nullable = false, columnDefinition = "TEXT")
    private String outcomeText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsRecordedOutcomeId() {
        return esRecordedOutcomeId;
    }

    public void setEsRecordedOutcomeId(Long esRecordedOutcomeId) {
        this.esRecordedOutcomeId = esRecordedOutcomeId;
    }

    public Long getEsTopicNoteId() {
        return esTopicNoteId;
    }

    public void setEsTopicNoteId(Long esTopicNoteId) {
        this.esTopicNoteId = esTopicNoteId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public RecordedOutcomeType getOutcomeType() {
        return outcomeType;
    }

    public void setOutcomeType(RecordedOutcomeType outcomeType) {
        this.outcomeType = outcomeType;
    }

    public String getShortTitle() {
        return shortTitle;
    }

    public void setShortTitle(String shortTitle) {
        this.shortTitle = shortTitle;
    }

    public String getOutcomeText() {
        return outcomeText;
    }

    public void setOutcomeText(String outcomeText) {
        this.outcomeText = outcomeText;
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

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(Long updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }
}