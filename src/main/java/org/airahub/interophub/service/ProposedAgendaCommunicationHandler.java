package org.airahub.interophub.service;

import java.util.EnumSet;
import java.util.Set;
import org.airahub.interophub.model.CommunicationRecipientPreview;
import org.airahub.interophub.model.CommunicationRenderedEmail;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.RecipientGroup;

/**
 * Handles PROPOSED_AGENDA communications.
 * Default recipients: Topic Champions + Agenda Presenters.
 * Expected meeting status: PROPOSED.
 */
public class ProposedAgendaCommunicationHandler implements MeetingCommunicationHandler {

    @Override
    public Set<RecipientGroup> defaultRecipientGroups() {
        return EnumSet.of(RecipientGroup.TOPIC_CHAMPION, RecipientGroup.AGENDA_PRESENTER);
    }

    @Override
    public EsMeeting.MeetingStatus expectedMeetingStatus() {
        return EsMeeting.MeetingStatus.PROPOSED;
    }

    @Override
    public String defaultSubject(EsMeeting meeting) {
        return "Proposed Agenda: " + meeting.getMeetingName();
    }

    @Override
    public String emailReason() {
        return EmailReason.MEETING_COMMUNICATION_PROPOSED_AGENDA;
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
            case AGENDA_PRESENTER:
                sb.append("You have been listed as a presenter on the proposed agenda for:\n\n");
                if (!recipient.getAgendaItemTitles().isEmpty()) {
                    sb.append("  Your agenda item(s):\n");
                    for (String title : recipient.getAgendaItemTitles()) {
                        sb.append("    - ").append(title).append("\n");
                    }
                    sb.append("\n");
                }
                break;
            case TOPIC_CHAMPION:
                sb.append("As a topic champion/support lead, the proposed agenda has been published for:\n\n");
                if (!recipient.getTopicNames().isEmpty()) {
                    sb.append("  Your topic(s):\n");
                    for (String topic : recipient.getTopicNames()) {
                        sb.append("    - ").append(topic).append("\n");
                    }
                    sb.append("\n");
                }
                break;
            default:
                sb.append("The proposed agenda has been published for:\n\n");
                break;
        }

        sb.append("  Meeting: ").append(meeting.getMeetingName()).append("\n");
        if (meeting.getScheduledStart() != null) {
            sb.append("  Date: ").append(meeting.getScheduledStart().toLocalDate()).append("\n");
        }

        if (communication.getNoteToInclude() != null && !communication.getNoteToInclude().isBlank()) {
            sb.append("\n").append(communication.getNoteToInclude().trim()).append("\n");
        }

        sb.append("\nSee the proposed agenda: ")
                .append(baseUrl).append("/es/agenda?meetingId=")
                .append(meeting.getEsMeetingId()).append("\n");
        return sb.toString();
    }
}
