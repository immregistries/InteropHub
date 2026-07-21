package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "es_topic_board_stage")
public class EsTopicBoardStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_board_stage_id")
    private Long esTopicBoardStageId;

    @Column(name = "es_topic_board_definition_id", nullable = false)
    private Long esTopicBoardDefinitionId;

    @Column(name = "es_topic_stage_definition_id", nullable = false)
    private Long esTopicStageDefinitionId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public Long getEsTopicBoardStageId() {
        return esTopicBoardStageId;
    }

    public void setEsTopicBoardStageId(Long esTopicBoardStageId) {
        this.esTopicBoardStageId = esTopicBoardStageId;
    }

    public Long getEsTopicBoardDefinitionId() {
        return esTopicBoardDefinitionId;
    }

    public void setEsTopicBoardDefinitionId(Long esTopicBoardDefinitionId) {
        this.esTopicBoardDefinitionId = esTopicBoardDefinitionId;
    }

    public Long getEsTopicStageDefinitionId() {
        return esTopicStageDefinitionId;
    }

    public void setEsTopicStageDefinitionId(Long esTopicStageDefinitionId) {
        this.esTopicStageDefinitionId = esTopicStageDefinitionId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
