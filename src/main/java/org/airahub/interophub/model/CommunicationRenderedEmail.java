package org.airahub.interophub.model;

/**
 * A single rendered email for one recipient — subject + plain-text body.
 * In-memory value object.
 */
public class CommunicationRenderedEmail {

    private final CommunicationRecipientPreview recipient;
    private final String subject;
    private final String bodyText;

    public CommunicationRenderedEmail(
            CommunicationRecipientPreview recipient,
            String subject,
            String bodyText) {
        this.recipient = recipient;
        this.subject = subject;
        this.bodyText = bodyText;
    }

    public CommunicationRecipientPreview getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBodyText() {
        return bodyText;
    }
}
