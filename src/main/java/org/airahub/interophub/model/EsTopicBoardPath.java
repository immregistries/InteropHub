package org.airahub.interophub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "es_topic_board_path")
public class EsTopicBoardPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "es_topic_board_path_id")
    private Long esTopicBoardPathId;

    @Column(name = "es_topic_board_definition_id", nullable = false)
    private Long esTopicBoardDefinitionId;

    @Column(name = "es_topic_path_definition_id", nullable = false)
    private Long esTopicPathDefinitionId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public Long getEsTopicBoardPathId() {
        return esTopicBoardPathId;
    }

    public void setEsTopicBoardPathId(Long esTopicBoardPathId) {
        this.esTopicBoardPathId = esTopicBoardPathId;
    }

    public Long getEsTopicBoardDefinitionId() {
        return esTopicBoardDefinitionId;
    }

    public void setEsTopicBoardDefinitionId(Long esTopicBoardDefinitionId) {
        this.esTopicBoardDefinitionId = esTopicBoardDefinitionId;
    }

    public Long getEsTopicPathDefinitionId() {
        return esTopicPathDefinitionId;
    }

    public void setEsTopicPathDefinitionId(Long esTopicPathDefinitionId) {
        this.esTopicPathDefinitionId = esTopicPathDefinitionId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
