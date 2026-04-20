package org.airahub.interophub.dao;

public class EsCampaignTopicBrowseRow {
    private final Long esTopicId;
    private final String topicName;
    private final String description;
    private final String topicType;
    private final String policyStatus;
    private final String neighborhood;
    private final String stage;
    private final Integer displayOrder;

    public EsCampaignTopicBrowseRow(Long esTopicId, String topicName, String description,
            String topicType, String policyStatus, String neighborhood, String stage, Integer displayOrder) {
        this.esTopicId = esTopicId;
        this.topicName = topicName;
        this.description = description;
        this.topicType = topicType;
        this.policyStatus = policyStatus;
        this.neighborhood = neighborhood;
        this.stage = stage;
        this.displayOrder = displayOrder;
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

    public String getStage() {
        return stage;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}