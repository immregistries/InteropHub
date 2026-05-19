package org.airahub.interophub.service;

import java.util.EnumSet;
import java.util.Set;
import org.airahub.interophub.model.CommunicationRecipientPreview;
import org.airahub.interophub.model.CommunicationRenderedEmail;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.RecipientGroup;

/**
 * Handles CANCELLED communications.
 * Default recipients: all 4 groups.
 * Expected meeting status: CANCELLED.
 */
public class CancelledMeetingCommunicationHandler implements MeetingCommunicationHandler {

    @Override
    public Set<RecipientGroup> defaultRecipientGroups() {
        return EnumSet.allOf(RecipientGroup.class);
    }

    @Override
    public EsMeeting.MeetingStatus expectedMeetingStatus() {
        return EsMeeting.MeetingStatus.CANCELLED;
    }

    @Override
    public String defaultSubject(EsMeeting meeting) {
        return "Cancelled: " + meeting.getMeetingName();
    }

    @Override
    public String emailReason() {
        return EmailReason.MEETING_COMMUNICATION_CANCELLED;
    }

    @Override
    public CommunicationRenderedEmail renderEmail(
            EsMeetingCommunication communication,
            EsMeeting meeting,
            CommunicationRecipientPreview recipient,
            String resolvedSubject,
            String baseUrl) {
        String body = buildBody(communication, meeting, recipient, baseUrl);
        return new CommunicationRenderedEmail(recipient, resolvedSubject, body);
    }

    private String buildBody(
            EsMeetingCommunication communication,
            EsMeeting meeting,
            CommunicationRecipientPreview recipient,
            String baseUrl) {
        StringBuilder sb = new StringBuilder();
        String greeting = recipient.getDisplayName() != null && !recipient.getDisplayName().isBlank()
                ? "Hi " + recipient.getDisplayName() + ","
                : "Hi,";
        sb.append(greeting).append("\n\n");
        sb.append("We are writing to let you know that the following meeting has been cancelled:\n\n");
        sb.append("  Meeting: ").append(meeting.getMeetingName()).append("\n");
        if (meeting.getScheduledStart() != null) {
            sb.append("  Originally scheduled: ").append(meeting.getScheduledStart().toLocalDate()).append("\n");
        }
        if (meeting.getCancellationReason() != null && !meeting.getCancellationReason().isBlank()) {
            sb.append("\nReason: ").append(meeting.getCancellationReason().trim()).append("\n");
        }
        if (communication.getNoteToInclude() != null && !communication.getNoteToInclude().isBlank()) {
            sb.append("\n").append(communication.getNoteToInclude().trim()).append("\n");
        }
        sb.append("\nWe apologize for any inconvenience. For more information: ")
                .append(baseUrl).append("/es/agenda?meetingId=")
                .append(meeting.getEsMeetingId()).append("\n");
        return sb.toString();
    }
}
