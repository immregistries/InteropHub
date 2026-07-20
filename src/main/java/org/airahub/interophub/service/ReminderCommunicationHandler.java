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
 * Handles REMINDER communications.
 * Default recipients: all 4 groups.
 * Expected meeting status: FINALIZED.
 */
public class ReminderCommunicationHandler implements MeetingCommunicationHandler {

    @Override
    public Set<RecipientGroup> defaultRecipientGroups() {
        return EnumSet.allOf(RecipientGroup.class);
    }

    @Override
    public EsMeeting.MeetingStatus expectedMeetingStatus() {
        return EsMeeting.MeetingStatus.FINALIZED;
    }

    @Override
    public String defaultSubject(EsMeeting meeting) {
        return "Reminder: " + meeting.getMeetingName();
    }

    @Override
    public String emailReason() {
        return EmailReason.MEETING_COMMUNICATION_REMINDER;
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
                sb.append("This is a reminder that you are a presenter at the upcoming meeting:\n\n");
                if (!recipient.getAgendaItemTitles().isEmpty()) {
                    sb.append("  Your agenda item(s):\n");
                    for (String title : recipient.getAgendaItemTitles()) {
                        sb.append("    - ").append(title).append("\n");
                    }
                    sb.append("\n");
                }
                break;
            case TOPIC_CHAMPION:
                sb.append("This is a reminder about the upcoming meeting for topics you champion/support:\n\n");
                if (!recipient.getTopicNames().isEmpty()) {
                    sb.append("  Your topic(s):\n");
                    appendTopicLines(sb, recipient, baseUrl);
                    sb.append("\n");
                }
                break;
            case TOPIC_SUBSCRIBER:
                sb.append("This is a reminder about the upcoming meeting for topics you follow:\n\n");
                if (!recipient.getTopicNames().isEmpty()) {
                    sb.append("  Topics you follow:\n");
                    appendTopicLines(sb, recipient, baseUrl);
                    sb.append("\n");
                }
                break;
            case GENERAL_MEETING_MEMBER:
            default:
                sb.append("This is a reminder about the upcoming meeting:\n\n");
                break;
        }

        sb.append("  Meeting: ").append(meeting.getMeetingName()).append("\n");
        if (meeting.getScheduledStart() != null) {
            sb.append("  Date/Time: ").append(meeting.getScheduledStart()).append("\n");
        }
        if (meeting.getOnlineMeetingUrl() != null && !meeting.getOnlineMeetingUrl().isBlank()) {
            sb.append("  Join URL: ").append(meeting.getOnlineMeetingUrl()).append("\n");
        }
        if (meeting.getOnlineMeetingDetails() != null && !meeting.getOnlineMeetingDetails().isBlank()) {
            sb.append("\n").append(meeting.getOnlineMeetingDetails().trim()).append("\n");
        }

        if (communication.getNoteToInclude() != null && !communication.getNoteToInclude().isBlank()) {
            sb.append("\n").append(communication.getNoteToInclude().trim()).append("\n");
        }

        sb.append("\nSee the full agenda: ")
                .append(baseUrl).append("/es/agenda?meetingId=")
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
