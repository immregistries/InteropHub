package org.airahub.interophub.service;

/**
 * Composes email subjects and bodies for every outgoing email type.
 * Add a subject + body method pair here whenever a new email type is
 * introduced.
 *
 * The composition is intentionally kept here (high up, separate from the SMTP
 * transport) so that the content of every email is easy to find and change.
 * Callers (servlets) compose the message, then pass subject + bodyText to
 * EmailService.send(), and can log the already-composed text directly.
 */
public final class EmailTemplates {

    // -------------------------------------------------------------------------
    // MAGIC_LINK — main registration / sign-in flow
    // -------------------------------------------------------------------------

    public static String magicLinkSubject() {
        return "Welcome to InteropHub";
    }

    public static String magicLinkBody(String loginLink) {
        return "Welcome to InteropHub!\n\n"
                + "Use this sign-in link to continue:\n"
                + loginLink + "\n";
    }

    // -------------------------------------------------------------------------
    // MEETING_MAGIC_LINK — meeting attendance check-in flow
    // -------------------------------------------------------------------------

    public static String meetingMagicLinkSubject() {
        return "Welcome to InteropHub";
    }

    public static String meetingMagicLinkBody(String loginLink) {
        return "Welcome to InteropHub!\n\n"
                + "Use this sign-in link to continue:\n"
                + loginLink + "\n";
    }

    private EmailTemplates() {
    }
}
