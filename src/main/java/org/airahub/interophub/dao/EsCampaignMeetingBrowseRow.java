package org.airahub.interophub.dao;

public class EsCampaignMeetingBrowseRow {
    private final Long esTopicId;
    private final String topicCode;
    private final String topicName;
    private final String stage;
    private final Integer displayOrder;
    private final Long esTopicMeetingId;
    private final String meetingName;
    private final String meetingDescription;
    private final Boolean joinRequiresApproval;

    public EsCampaignMeetingBrowseRow(Long esTopicId, String topicCode, String topicName, String stage,
            Integer displayOrder,
            Long esTopicMeetingId, String meetingName, String meetingDescription, Boolean joinRequiresApproval) {
        this.esTopicId = esTopicId;
        this.topicCode = topicCode;
        this.topicName = topicName;
        this.stage = stage;
        this.displayOrder = displayOrder;
        this.esTopicMeetingId = esTopicMeetingId;
        this.meetingName = meetingName;
        this.meetingDescription = meetingDescription;
        this.joinRequiresApproval = joinRequiresApproval;
    }

    public Long getEsTopicId() {
        return esTopicId;
    }

    public String getTopicCode() {
        return topicCode;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getStage() {
        return stage;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public Long getEsTopicMeetingId() {
        return esTopicMeetingId;
    }

    public String getMeetingName() {
        return meetingName;
    }

    public String getMeetingDescription() {
        return meetingDescription;
    }

    public Boolean getJoinRequiresApproval() {
        return joinRequiresApproval;
    }
}