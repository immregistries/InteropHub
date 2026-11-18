package org.airahub.interophub.service;

/** A contract Section 2 error. {@link #getMessage()} is the human-readable message. */
public final class GuideApiException extends RuntimeException {
    private final int status;
    private final String error;
    private final String field;
    private final String currentJson;

    private GuideApiException(int status, String error, String field, String message, String currentJson) {
        super(message);
        this.status = status;
        this.error = error;
        this.field = field;
        this.currentJson = currentJson;
    }

    public static GuideApiException invalidRequest(String message) {
        return new GuideApiException(400, "invalid_request", null, message, null);
    }

    public static GuideApiException invalidField(String field, String message) {
        return new GuideApiException(400, "invalid_field", field, message, null);
    }

    public static GuideApiException forbidden(String message) {
        return new GuideApiException(403, "forbidden", null, message, null);
    }

    /**
     * {@code 403 forbidden} for a topic action by someone who is not an editor of the topic.
     * {@code topicName} is named only when the caller may view the topic; pass null otherwise.
     */
    public static GuideApiException notEditor(String topicName) {
        return notEditor(null, topicName);
    }

    /** As {@link #notEditor(String)}, after a leading sentence such as "Only an author can delete a comment." */
    public static GuideApiException notEditor(String lead, String topicName) {
        StringBuilder message = new StringBuilder();
        if (lead != null && !lead.isBlank()) {
            message.append(lead.trim()).append(' ');
        }
        if (topicName != null && !topicName.isBlank()) {
            message.append("Your account must be an editor of the topic \"").append(topicName.trim())
                    .append("\" to do this.");
        } else {
            message.append("Your account must be an editor of this guide's topic to do this.");
        }
        message.append(" Topic editors are the topic's champions and support subscribers, and the admins of its"
                + " Topic Space.");
        return new GuideApiException(403, "forbidden", null, message.toString(), null);
    }

    public static GuideApiException editWindowClosed() {
        return new GuideApiException(403, "edit_window_closed", null,
                "The 14-day edit window has closed.", null);
    }

    /** The label the guide page belongs to is Closed: no comments, replies, or flags there. */
    public static GuideApiException commentsClosed() {
        return new GuideApiException(403, "comments_closed", null,
                "Comments are closed on this version of the guide.", null);
    }

    public static GuideApiException notFound(String message) {
        return new GuideApiException(404, "not_found", null, message, null);
    }

    public static GuideApiException conflict(String message, String currentJson) {
        return new GuideApiException(409, "conflict", null, message, currentJson);
    }

    public static GuideApiException invalidState(String message) {
        return new GuideApiException(409, "invalid_state", null, message, null);
    }

    public static GuideApiException documentInactive() {
        return new GuideApiException(409, "document_inactive", null,
                "This guide registration is inactive.", null);
    }

    public static GuideApiException duplicate(String field, String message) {
        return new GuideApiException(409, "duplicate", field, message, null);
    }

    public static GuideApiException urlNotApproved() {
        return new GuideApiException(400, "url_not_approved", "urls",
                "The guide URL is outside the approved prefixes.", null);
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getField() {
        return field;
    }

    public String getCurrentJson() {
        return currentJson;
    }
}
