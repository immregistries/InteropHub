package org.airahub.interophub.dao;

public class EsCampaignTopicBrowseRow {
    private final Long esTopicId;
    private final String topicName;
    private final String description;
    private final String topicType;
    private final String policyStatus;
    private String neighborhood;
    private final String stage;
    private final Integer displayOrder;
    private final String confluenceUrl;
    private final String topicSummary;
    private final String topicEmoji;
    private final String path;
    private final Long esTopicStageDefinitionId;
    private final Long esTopicPathDefinitionId;

    public EsCampaignTopicBrowseRow(Long esTopicId, String topicName, String description,
            String topicType, String policyStatus, String neighborhood, String stage, Integer displayOrder,
            String confluenceUrl, String topicSummary, String topicEmoji, String path,
            Long esTopicStageDefinitionId, Long esTopicPathDefinitionId) {
        this.esTopicId = esTopicId;
        this.topicName = topicName;
        this.description = description;
        this.topicType = topicType;
        this.policyStatus = policyStatus;
        this.neighborhood = neighborhood;
        this.stage = stage;
        this.displayOrder = displayOrder;
        this.confluenceUrl = confluenceUrl;
        this.topicSummary = topicSummary;
        this.topicEmoji = topicEmoji;
        this.path = path;
        this.esTopicStageDefinitionId = esTopicStageDefinitionId;
        this.esTopicPathDefinitionId = esTopicPathDefinitionId;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getDescription() {
        return description;
    }

    public String getTopicType() {
        return topicType;
    }

    public String getPolicyStatus() {
        return policyStatus;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public String getStage() {
        return stage;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public String getConfluenceUrl() {
        return confluenceUrl;
    }

    public String getTopicSummary() {
        return topicSummary;
    }

    public String getTopicEmoji() {
        return topicEmoji;
    }

    public String getPath() {
        return path;
    }

    public Long getEsTopicStageDefinitionId() {
        return esTopicStageDefinitionId;
    }

    public Long getEsTopicPathDefinitionId() {
        return esTopicPathDefinitionId;
    }
}