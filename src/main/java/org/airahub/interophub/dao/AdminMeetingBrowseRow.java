package org.airahub.interophub.dao;

public class AdminMeetingBrowseRow {

    private final Long esTopicMeetingId;
    private final String meetingName;
    private final long requestedCount;
    private final long approvedCount;

    public AdminMeetingBrowseRow(Long esTopicMeetingId, String meetingName, long requestedCount, long approvedCount) {
        this.esTopicMeetingId = esTopicMeetingId;
        this.meetingName = meetingName;
        this.requestedCount = requestedCount;
        this.approvedCount = approvedCount;
    }

    public Long getEsTopicMeetingId() {
        return esTopicMeetingId;
    }

    public String getMeetingName() {
        return meetingName;
    }

    public long getRequestedCount() {
        return requestedCount;
    }

    public long getApprovedCount() {
        return approvedCount;
    }
}
