package org.airahub.interophub.model;

import java.util.List;

/**
 * The full preview result for a single EsMeetingCommunication: resolved
 * recipients, per-group summary, and up to 5 sample rendered emails (one per
 * distinct primary group). In-memory value object.
 */
public class CommunicationPreview {

    private final EsMeetingCommunication communication;
    private final EsMeeting meeting;
    private final List<CommunicationRecipientPreview> allRecipients;
    private final List<CommunicationRecipientGroupSummary> groupSummaries;
    /** Up to 5 sample rendered emails, one per distinct primaryGroup. */
    private final List<CommunicationRenderedEmail> sampleEmails;
    private final CommunicationEligibilityResult eligibility;

    public CommunicationPreview(
            EsMeetingCommunication communication,
            EsMeeting meeting,
            List<CommunicationRecipientPreview> allRecipients,
            List<CommunicationRecipientGroupSummary> groupSummaries,
            List<CommunicationRenderedEmail> sampleEmails,
            CommunicationEligibilityResult eligibility) {
        this.communication = communication;
        this.meeting = meeting;
        this.allRecipients = allRecipients;
        this.groupSummaries = groupSummaries;
        this.sampleEmails = sampleEmails;
        this.eligibility = eligibility;
    }

    public EsMeetingCommunication getCommunication() {
        return communication;
    }

    public EsMeeting getMeeting() {
        return meeting;
    }

    public List<CommunicationRecipientPreview> getAllRecipients() {
        return allRecipients;
    }

    public List<CommunicationRecipientGroupSummary> getGroupSummaries() {
        return groupSummaries;
    }

    public List<CommunicationRenderedEmail> getSampleEmails() {
        return sampleEmails;
    }

    public CommunicationEligibilityResult getEligibility() {
        return eligibility;
    }

    public int getTotalRecipientCount() {
        return allRecipients.size();
    }
}
