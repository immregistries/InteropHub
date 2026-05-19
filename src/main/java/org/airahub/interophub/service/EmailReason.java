package org.airahub.interophub.service;

/**
 * Constants for the email_reason column in email_send_log.
 * This is the canonical list of all possible values stored in the database.
 * Add a new constant here whenever a new category of outgoing email is
 * introduced.
 */
public final class EmailReason {

    /**
     * Magic link / sign-in email sent from the main registration and login flow.
     */
    public static final String MAGIC_LINK = "MAGIC_LINK";

    /**
     * Magic link / sign-in email sent from the meeting attendance check-in flow.
     */
    public static final String MEETING_MAGIC_LINK = "MEETING_MAGIC_LINK";

    /** Meeting communication: call-for-topics blast. */
    public static final String MEETING_COMMUNICATION_CALL_FOR_TOPICS = "MEETING_COMMUNICATION_CALL_FOR_TOPICS";

    /** Meeting communication: proposed-agenda notification. */
    public static final String MEETING_COMMUNICATION_PROPOSED_AGENDA = "MEETING_COMMUNICATION_PROPOSED_AGENDA";

    /** Meeting communication: final-agenda notification. */
    public static final String MEETING_COMMUNICATION_FINAL_AGENDA = "MEETING_COMMUNICATION_FINAL_AGENDA";

    /** Meeting communication: reminder sent before the meeting. */
    public static final String MEETING_COMMUNICATION_REMINDER = "MEETING_COMMUNICATION_REMINDER";

    /** Meeting communication: cancellation notice. */
    public static final String MEETING_COMMUNICATION_CANCELLED = "MEETING_COMMUNICATION_CANCELLED";

    private EmailReason() {
    }
}
