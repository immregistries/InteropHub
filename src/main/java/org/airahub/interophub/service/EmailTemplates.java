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

    // -------------------------------------------------------------------------
    // PRESENTER_INVITATION — notify a newly-added presenter
    // -------------------------------------------------------------------------

    public static String presenterInvitationSubject(String itemTitle) {
        return "You've been added as a presenter: " + (itemTitle != null ? itemTitle : "Agenda Item");
    }

    public static String presenterInvitationBody(
            String recipientName, String itemTitle, String topicName,
            String meetingName, String meetingDateDisplay, String roleLabel,
            String agendaLink) {
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(recipientName != null ? recipientName : "there").append(",\n\n");
        body.append("You've been added as a presenter for the following agenda item:\n\n");
        body.append("  ").append(itemTitle != null ? itemTitle : "Agenda Item").append("\n");
        if (topicName != null && !topicName.isBlank()) {
            body.append("  Topic: ").append(topicName).append("\n");
        }
        if (meetingName != null && !meetingName.isBlank()) {
            body.append("  Meeting: ").append(meetingName).append("\n");
        }
        if (meetingDateDisplay != null && !meetingDateDisplay.isBlank()) {
            body.append("  Date: ").append(meetingDateDisplay).append("\n");
        }
        if (roleLabel != null && !roleLabel.isBlank()) {
            body.append("  Your role: ").append(roleLabel).append("\n");
        }
        body.append("\nPlease visit the agenda to confirm or decline your participation:\n");
        body.append("  ").append(agendaLink).append("\n\n");
        body.append("Thank you,\nInteropHub\n");
        return body.toString();
    }

    private EmailTemplates() {
    }
}
