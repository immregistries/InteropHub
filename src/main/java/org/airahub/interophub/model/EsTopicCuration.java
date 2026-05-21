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
@Table(name = "es_topic_curation")
public class EsTopicCuration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_curation_id")
    private Long esTopicCurationId;

    @Column(name = "curator_topic_id", nullable = false)
    private Long curatorTopicId;

    @Column(name = "curated_topic_id", nullable = false)
    private Long curatedTopicId;

    /** Champion's preferred display name for the curated topic within this list. */
    @Column(name = "topic_alias", length = 140)
    private String topicAlias;

    /** Grouping label within the curated list, e.g. "Core", "Adjacent". */
    @Column(name = "category_label", length = 80)
    private String categoryLabel;

    /** Free-text editorial note explaining why the topic is in the list. */
    @Column(name = "editorial_note", columnDefinition = "TEXT")
    private String editorialNote;

    /**
     * Free-text status for this topic within the curated list context. Not
     * DB-enforced.
     */
    @Column(name = "curation_status", length = 80)
    private String curationStatus;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    /** Number of days within which this topic must appear on an agenda; null = not specified. */
    @Column(name = "agenda_cadence_days")
    private Integer agendaCadenceDays;

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
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsTopicCurationId() {
        return esTopicCurationId;
    }

    public void setEsTopicCurationId(Long esTopicCurationId) {
        this.esTopicCurationId = esTopicCurationId;
    }

    public Long getCuratorTopicId() {
        return curatorTopicId;
    }

    public void setCuratorTopicId(Long curatorTopicId) {
        this.curatorTopicId = curatorTopicId;
    }

    public Long getCuratedTopicId() {
        return curatedTopicId;
    }

    public void setCuratedTopicId(Long curatedTopicId) {
        this.curatedTopicId = curatedTopicId;
    }

    public String getTopicAlias() {
        return topicAlias;
    }

    public void setTopicAlias(String topicAlias) {
        this.topicAlias = topicAlias;
    }

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public void setCategoryLabel(String categoryLabel) {
        this.categoryLabel = categoryLabel;
    }

    public String getEditorialNote() {
        return editorialNote;
    }

    public void setEditorialNote(String editorialNote) {
        this.editorialNote = editorialNote;
    }

    public String getCurationStatus() {
        return curationStatus;
    }

    public void setCurationStatus(String curationStatus) {
        this.curationStatus = curationStatus;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Integer getAgendaCadenceDays() {
        return agendaCadenceDays;
    }

    public void setAgendaCadenceDays(Integer agendaCadenceDays) {
        this.agendaCadenceDays = agendaCadenceDays;
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
