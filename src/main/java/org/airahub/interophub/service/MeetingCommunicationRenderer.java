package org.airahub.interophub.service;

import java.util.HashMap;
import java.util.Map;
import org.airahub.interophub.model.CommunicationRecipientPreview;
import org.airahub.interophub.model.CommunicationRenderedEmail;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;

/**
 * Delegates email rendering to the appropriate MeetingCommunicationHandler for
 * a given communication type, and resolves the subject (override or default).
 */
public class MeetingCommunicationRenderer {

    private final Map<EsMeetingCommunication.CommunicationType, MeetingCommunicationHandler> handlers;

    public MeetingCommunicationRenderer() {
        handlers = new HashMap<>();
        handlers.put(EsMeetingCommunication.CommunicationType.CALL_FOR_TOPICS,
                new CallForTopicsCommunicationHandler());
        handlers.put(EsMeetingCommunication.CommunicationType.PROPOSED_AGENDA,
                new ProposedAgendaCommunicationHandler());
        handlers.put(EsMeetingCommunication.CommunicationType.FINAL_AGENDA,
                new FinalAgendaCommunicationHandler());
        handlers.put(EsMeetingCommunication.CommunicationType.REMINDER,
                new ReminderCommunicationHandler());
        handlers.put(EsMeetingCommunication.CommunicationType.CANCELLED,
                new CancelledMeetingCommunicationHandler());
    }

    public MeetingCommunicationHandler handlerFor(EsMeetingCommunication.CommunicationType type) {
        MeetingCommunicationHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for communication type: " + type);
        }
        return handler;
    }

    /**
     * Resolves the effective subject: uses subjectOverride if non-blank, otherwise
     * falls back to the handler's default subject.
     */
    public String resolveSubject(EsMeetingCommunication communication, EsMeeting meeting) {
        if (communication.getSubjectOverride() != null && !communication.getSubjectOverride().isBlank()) {
            return communication.getSubjectOverride().trim();
        }
        return handlerFor(communication.getCommunicationType()).defaultSubject(meeting);
    }

    /**
     * Renders a single email for one recipient.
     */
    public CommunicationRenderedEmail render(
            EsMeetingCommunication communication,
            EsMeeting meeting,
            CommunicationRecipientPreview recipient) {
        String subject = resolveSubject(communication, meeting);
        return handlerFor(communication.getCommunicationType())
                .renderEmail(communication, meeting, recipient, subject);
    }
}
