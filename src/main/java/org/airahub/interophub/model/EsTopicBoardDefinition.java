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
@Table(name = "es_topic_board_definition")
public class EsTopicBoardDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_board_definition_id")
    private Long esTopicBoardDefinitionId;

    @Column(name = "board_code", nullable = false, unique = true, length = 80)
    private String boardCode;

    @Column(name = "board_name", nullable = false, length = 140)
    private String boardName;

    @Column(name = "board_description", columnDefinition = "TEXT")
    private String boardDescription;

    @Column(name = "es_topic_space_id", nullable = false)
    private Long esTopicSpaceId;

    @Column(name = "curator_topic_id")
    private Long curatorTopicId;

    @Column(name = "show_unassigned_stage", nullable = false)
    private Boolean showUnassignedStage;

    @Column(name = "show_unassigned_path", nullable = false)
    private Boolean showUnassignedPath;

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
        if (showUnassignedStage == null) {
            showUnassignedStage = Boolean.FALSE;
        }
        if (showUnassignedPath == null) {
            showUnassignedPath = Boolean.FALSE;
        }
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getEsTopicBoardDefinitionId() {
        return esTopicBoardDefinitionId;
    }

    public void setEsTopicBoardDefinitionId(Long esTopicBoardDefinitionId) {
        this.esTopicBoardDefinitionId = esTopicBoardDefinitionId;
    }

    public String getBoardCode() {
        return boardCode;
    }

    public void setBoardCode(String boardCode) {
        this.boardCode = boardCode;
    }

    public String getBoardName() {
        return boardName;
    }

    public void setBoardName(String boardName) {
        this.boardName = boardName;
    }

    public String getBoardDescription() {
        return boardDescription;
    }

    public void setBoardDescription(String boardDescription) {
        this.boardDescription = boardDescription;
    }

    public Long getEsTopicSpaceId() {
        return esTopicSpaceId;
    }

    public void setEsTopicSpaceId(Long esTopicSpaceId) {
        this.esTopicSpaceId = esTopicSpaceId;
    }

    public Long getCuratorTopicId() {
        return curatorTopicId;
    }

    public void setCuratorTopicId(Long curatorTopicId) {
        this.curatorTopicId = curatorTopicId;
    }

    public Boolean getShowUnassignedStage() {
        return showUnassignedStage;
    }

    public void setShowUnassignedStage(Boolean showUnassignedStage) {
        this.showUnassignedStage = showUnassignedStage;
    }

    public Boolean getShowUnassignedPath() {
        return showUnassignedPath;
    }

    public void setShowUnassignedPath(Boolean showUnassignedPath) {
        this.showUnassignedPath = showUnassignedPath;
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
