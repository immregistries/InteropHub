package org.airahub.interophub.service;

import java.util.EnumSet;
import java.util.Set;
import org.airahub.interophub.model.CommunicationRecipientPreview;
import org.airahub.interophub.model.CommunicationRenderedEmail;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.RecipientGroup;

/**
 * Handles CALL_FOR_TOPICS communications.
 * Default recipients: General Meeting Members + Topic Champions.
 * Expected meeting status: DRAFT.
 */
public class CallForTopicsCommunicationHandler implements MeetingCommunicationHandler {

    @Override
    public Set<RecipientGroup> defaultRecipientGroups() {
        return EnumSet.of(RecipientGroup.GENERAL_MEETING_MEMBER, RecipientGroup.TOPIC_CHAMPION);
    }

    @Override
    public EsMeeting.MeetingStatus expectedMeetingStatus() {
        return EsMeeting.MeetingStatus.DRAFT;
    }

    @Override
    public String defaultSubject(EsMeeting meeting) {
        return "Call for Topics: " + meeting.getMeetingName();
    }

    @Override
    public String emailReason() {
        return EmailReason.MEETING_COMMUNICATION_CALL_FOR_TOPICS;
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

        switch (recipient.getPrimaryGroup()) {
            case TOPIC_CHAMPION:
                sb.append("As a topic champion, you are invited to submit a topic for the upcoming meeting:\n\n");
                break;
            case GENERAL_MEETING_MEMBER:
            default:
                sb.append("You are invited to submit a topic for the upcoming meeting:\n\n");
                break;
        }

        sb.append("  Meeting: ").append(meeting.getMeetingName()).append("\n");
        if (meeting.getScheduledStart() != null) {
            sb.append("  Date: ").append(meeting.getScheduledStart().toLocalDate()).append("\n");
        }

        if (communication.getNoteToInclude() != null && !communication.getNoteToInclude().isBlank()) {
            sb.append("\n").append(communication.getNoteToInclude().trim()).append("\n");
        }

        sb.append("\nSee meeting details and submit a topic: ")
                .append(baseUrl).append("/es/agenda?meetingId=")
                .append(meeting.getEsMeetingId()).append("\n");
        return sb.toString();
    }
}
