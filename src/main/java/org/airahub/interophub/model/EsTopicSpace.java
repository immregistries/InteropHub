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
@Table(name = "es_topic_space")
public class EsTopicSpace {

    public enum Visibility {
        PUBLIC,
        PRIVATE
    }

    /** Fallback shown wherever a Topic Space hasn't set its own "what does Stage mean here" text. */
    public static final String DEFAULT_STAGE_CONCEPT_DESCRIPTION =
            "A Stage marks how far along a topic currently is, from first being raised through active work"
                    + " to being ready for broader use.";

    /** Fallback shown wherever a Topic Space hasn't set its own "what does Advancement Path mean here" text. */
    public static final String DEFAULT_PATH_CONCEPT_DESCRIPTION =
            "An Advancement Path describes what a topic currently needs in order to keep moving forward,"
                    + " such as funding, a decision, technical direction, or outside participation.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_space_id")
    private Long esTopicSpaceId;

    @Column(name = "space_code", nullable = false, unique = true, length = 80)
    private String spaceCode;

    @Column(name = "space_name", nullable = false, length = 140)
    private String spaceName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "stage_concept_description", columnDefinition = "TEXT")
    private String stageConceptDescription;

    @Column(name = "path_concept_description", columnDefinition = "TEXT")
    private String pathConceptDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private Visibility visibility;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "primary_es_topic_board_definition_id")
    private Long primaryEsTopicBoardDefinitionId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

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
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsTopicSpaceId() {
        return esTopicSpaceId;
    }

    public void setEsTopicSpaceId(Long esTopicSpaceId) {
        this.esTopicSpaceId = esTopicSpaceId;
    }

    public String getSpaceCode() {
        return spaceCode;
    }

    public void setSpaceCode(String spaceCode) {
        this.spaceCode = spaceCode;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStageConceptDescription() {
        return stageConceptDescription;
    }

    public void setStageConceptDescription(String stageConceptDescription) {
        this.stageConceptDescription = stageConceptDescription;
    }

    public String getPathConceptDescription() {
        return pathConceptDescription;
    }

    public void setPathConceptDescription(String pathConceptDescription) {
        this.pathConceptDescription = pathConceptDescription;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Long getPrimaryEsTopicBoardDefinitionId() {
        return primaryEsTopicBoardDefinitionId;
    }

    public void setPrimaryEsTopicBoardDefinitionId(Long primaryEsTopicBoardDefinitionId) {
        this.primaryEsTopicBoardDefinitionId = primaryEsTopicBoardDefinitionId;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
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