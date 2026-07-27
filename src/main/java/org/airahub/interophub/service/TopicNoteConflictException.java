package org.airahub.interophub.service;

public class TopicNoteConflictException extends RuntimeException {

    private final String errorCode;
    private final Long activeEditorUserId;
    private final Long editorVersion;

    public TopicNoteConflictException(String message) {
        this("NOTE_CONFLICT", message, null, null);
    }

    public TopicNoteConflictException(String errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public TopicNoteConflictException(String errorCode, String message, Long activeEditorUserId,
            Long editorVersion) {
        super(message);
        this.errorCode = errorCode;
        this.activeEditorUserId = activeEditorUserId;
        this.editorVersion = editorVersion;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Long getActiveEditorUserId() {
        return activeEditorUserId;
    }

    public Long getEditorVersion() {
        return editorVersion;
    }
}