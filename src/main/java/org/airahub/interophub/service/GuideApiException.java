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

    public static GuideApiException editWindowClosed() {
        return new GuideApiException(403, "edit_window_closed", null,
                "The 14-day edit window has closed.", null);
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
