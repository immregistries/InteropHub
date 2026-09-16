package org.airahub.interophub.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.airahub.interophub.model.CommunicationRecipientPreview;
import org.airahub.interophub.model.CommunicationRenderedEmail;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.RecipientGroup;

/**
 * Handles NOTES_AVAILABLE communications.
 * Default recipients: all 4 groups (same deduplicated audience as FINAL_AGENDA).
 * Expected meeting status: COMPLETED.
 */
public class NotesAvailableCommunicationHandler implements MeetingCommunicationHandler {

    @Override
    public Set<RecipientGroup> defaultRecipientGroups() {
        return EnumSet.allOf(RecipientGroup.class);
    }

    @Override
    public EsMeeting.MeetingStatus expectedMeetingStatus() {
        return EsMeeting.MeetingStatus.COMPLETED;
    }

    @Override
    public String defaultSubject(EsMeeting meeting) {
        return "Notes Available for Review: " + meeting.getMeetingName();
    }

    @Override
    public String emailReason() {
        return EmailReason.MEETING_COMMUNICATION_NOTES_AVAILABLE;
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
                sb.append("The meeting was held, and notes are now available for review, including your agenda item(s), for:\n\n");
                if (!recipient.getAgendaItemTitles().isEmpty()) {
                    sb.append("  Your agenda item(s):\n");
                    for (String title : recipient.getAgendaItemTitles()) {
                        sb.append("    - ").append(title).append("\n");
                    }
                    sb.append("\n");
                }
                break;
            case TOPIC_CHAMPION:
                sb.append("As a topic champion/support lead, the meeting was held and notes are now available for review for:\n\n");
                if (!recipient.getTopicNames().isEmpty()) {
                    sb.append("  Your topic(s):\n");
                    appendTopicLines(sb, recipient, baseUrl);
                    sb.append("\n");
                }
                break;
            case TOPIC_SUBSCRIBER:
                sb.append("The meeting was held and notes are now available for review, for a meeting on topics you follow:\n\n");
                if (!recipient.getTopicNames().isEmpty()) {
                    sb.append("  Topics you follow:\n");
                    appendTopicLines(sb, recipient, baseUrl);
                    sb.append("\n");
                }
                break;
            case GENERAL_MEETING_MEMBER:
            default:
                sb.append("The meeting was held and notes are now available for review, for:\n\n");
                break;
        }

        sb.append("  Meeting: ").append(meeting.getMeetingName()).append("\n");
        if (meeting.getScheduledStart() != null) {
            sb.append("  Date: ").append(meeting.getScheduledStart().toLocalDate()).append("\n");
        }

        if (communication.getNoteToInclude() != null && !communication.getNoteToInclude().isBlank()) {
            sb.append("\n").append(communication.getNoteToInclude().trim()).append("\n");
        }

        sb.append("\nPlease review the notes and let us know of any corrections or fixes within the next 3 days.")
                .append(" Note that the editing window for these notes is limited, so corrections received after it")
                .append(" closes cannot be applied directly to this meeting's record.\n");
        sb.append("\nSee the meeting notes: ")
                .append(baseUrl).append("/es/meeting-workspace?meetingId=")
                .append(meeting.getEsMeetingId()).append("\n");
        return sb.toString();
    }

    private void appendTopicLines(StringBuilder sb, CommunicationRecipientPreview recipient, String baseUrl) {
        List<String> topicNames = recipient.getTopicNames();
        List<Long> topicIds = recipient.getTopicIds();
        for (int i = 0; i < topicNames.size(); i++) {
            sb.append("    - ").append(topicNames.get(i));
            if (i < topicIds.size() && topicIds.get(i) != null && baseUrl != null && !baseUrl.isBlank()) {
                sb.append(" (").append(baseUrl).append("/es/topic/").append(topicIds.get(i)).append(")");
            }
            sb.append("\n");
        }
    }
}
