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

    /** Agenda presenter invitation. */
    public static final String PRESENTER_INVITATION = "PRESENTER_INVITATION";

    /** Topic comment notification sent to a topic's support contact. */
    public static final String TOPIC_COMMENT_SUPPORT_NOTIFY = "TOPIC_COMMENT_SUPPORT_NOTIFY";

    /** Topic comment notification sent to a topic's champion contact. */
    public static final String TOPIC_COMMENT_CHAMPION_NOTIFY = "TOPIC_COMMENT_CHAMPION_NOTIFY";

    /**
     * Topic comment notification sent to administrators when no support/champion
     * contact exists.
     */
    public static final String TOPIC_COMMENT_ADMIN_NOTIFY = "TOPIC_COMMENT_ADMIN_NOTIFY";

    /**
     * InteropHub Daily Digest — recurring digest of topic-contact-worthy activity.
     */
    public static final String DAILY_DIGEST = "DAILY_DIGEST";

    private EmailReason() {
    }
}
