package org.airahub.interophub.service;

import java.util.Set;
import org.airahub.interophub.model.CommunicationRecipientPreview;
import org.airahub.interophub.model.CommunicationRenderedEmail;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.RecipientGroup;

/**
 * Strategy interface for each communication type.
 * Each implementation encapsulates the default recipient groups, the expected
 * meeting status, the default subject line, and the body renderer for that
 * communication type.
 */
public interface MeetingCommunicationHandler {

    /**
     * Returns the default set of recipient groups that should be pre-selected
     * when creating a new communication of this type.
     */
    Set<RecipientGroup> defaultRecipientGroups();

    /**
     * Returns the expected meeting status for this communication type.
     * Used by MeetingCommunicationEligibilityService to warn when the meeting
     * is not in the expected state.
     */
    EsMeeting.MeetingStatus expectedMeetingStatus();

    /**
     * Returns the email subject to use. If communication.subjectOverride is
     * non-blank, it takes precedence and this method is never called.
     */
    String defaultSubject(EsMeeting meeting);

    /**
     * Renders the plain-text email body for a single recipient.
     */
    CommunicationRenderedEmail renderEmail(
            EsMeetingCommunication communication,
            EsMeeting meeting,
            CommunicationRecipientPreview recipient,
            String resolvedSubject);

    /**
     * Returns the EmailReason constant to store in email_send_log.
     */
    String emailReason();
}
