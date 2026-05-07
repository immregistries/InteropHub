package org.airahub.interophub.dao;

import java.time.LocalDateTime;

public class EmailProspectBrowseRow {

    private final String emailNormalized;
    private final LocalDateTime firstContactAt;
    private final LocalDateTime lastContactAt;
    private final long campaignRegistrationCount;
    private final long commentCount;
    private final long subscriptionCount;
    private final long meetingMemberCount;

    public EmailProspectBrowseRow(
            String emailNormalized,
            LocalDateTime firstContactAt,
            LocalDateTime lastContactAt,
            long campaignRegistrationCount,
            long commentCount,
            long subscriptionCount,
            long meetingMemberCount) {
        this.emailNormalized = emailNormalized;
        this.firstContactAt = firstContactAt;
        this.lastContactAt = lastContactAt;
        this.campaignRegistrationCount = campaignRegistrationCount;
        this.commentCount = commentCount;
        this.subscriptionCount = subscriptionCount;
        this.meetingMemberCount = meetingMemberCount;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public LocalDateTime getFirstContactAt() {
        return firstContactAt;
    }

    public LocalDateTime getLastContactAt() {
        return lastContactAt;
    }

    public long getCampaignRegistrationCount() {
        return campaignRegistrationCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public long getSubscriptionCount() {
        return subscriptionCount;
    }

    public long getMeetingMemberCount() {
        return meetingMemberCount;
    }
}
