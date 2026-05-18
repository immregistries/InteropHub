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

    private EmailReason() {
    }
}
