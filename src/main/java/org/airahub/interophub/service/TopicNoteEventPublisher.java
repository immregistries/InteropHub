package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.User;
import org.hibernate.Session;
import org.json.JSONObject;

public class TopicNoteEventPublisher {

    private final TopicNoteEventBroker broker;

    public TopicNoteEventPublisher() {
        this.broker = TopicNoteEventBroker.getInstance();
    }

    public void publishNoteState(Session session, EsTopicNote note, EsMeeting.MeetingStatus meetingStatus) {
        if (note == null || note.getEsTopicNoteId() == null) {
            return;
        }
        JSONObject payload = basePayload(session, note, meetingStatus);
        broker.publish(note.getEsTopicNoteId(), "note-state", payload);
    }

    public void publishNoteUpdated(Session session, EsTopicNote note, Long savedByUserId,
            EsMeeting.MeetingStatus meetingStatus) {
        if (note == null || note.getEsTopicNoteId() == null) {
            return;
        }
        JSONObject payload = basePayload(session, note, meetingStatus);
        payload.put("savedBy", userJson(session, savedByUserId));
        broker.publish(note.getEsTopicNoteId(), "note-updated", payload);
    }

    public void publishEditorChanged(Session session, EsTopicNote note, Long changedByUserId,
            LocalDateTime changedAt, EsMeeting.MeetingStatus meetingStatus) {
        if (note == null || note.getEsTopicNoteId() == null) {
            return;
        }
        JSONObject payload = basePayload(session, note, meetingStatus);
        payload.put("activeEditor", userJson(session, note.getActiveEditorUserId()));
        payload.put("changedBy", userJson(session, changedByUserId));
        if (changedAt != null) {
            payload.put("changedAt", changedAt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        broker.publish(note.getEsTopicNoteId(), "editor-changed", payload);
    }

    public void publishNoteStateChanged(Session session, EsTopicNote note, EsMeeting.MeetingStatus meetingStatus) {
        if (note == null || note.getEsTopicNoteId() == null) {
            return;
        }
        JSONObject payload = basePayload(session, note, meetingStatus);
        broker.publish(note.getEsTopicNoteId(), "note-state-changed", payload);
    }

    public void publishOutcomesChanged(Session session, EsTopicNote note, Long changedByUserId,
            EsMeeting.MeetingStatus meetingStatus) {
        if (note == null || note.getEsTopicNoteId() == null) {
            return;
        }
        JSONObject payload = basePayload(session, note, meetingStatus);
        payload.put("changedBy", userJson(session, changedByUserId));
        broker.publish(note.getEsTopicNoteId(), "outcomes-changed", payload);
    }

    private JSONObject basePayload(Session session, EsTopicNote note, EsMeeting.MeetingStatus meetingStatus) {
        JSONObject payload = new JSONObject();
        payload.put("noteId", note.getEsTopicNoteId());
        payload.put("revision", note.getRevisionNo());
        payload.put("editorVersion", note.getActiveEditorVersion() == null ? 0L : note.getActiveEditorVersion());
        payload.put("noteStatus", note.getStatus() == null ? JSONObject.NULL : note.getStatus().name());
        payload.put("meetingStatus", meetingStatus == null ? JSONObject.NULL : meetingStatus.name());
        payload.put("savedAt", note.getUpdatedAt() == null
                ? JSONObject.NULL
                : note.getUpdatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        payload.put("activeEditor", userJson(session, note.getActiveEditorUserId()));
        return payload;
    }

    private Object userJson(Session session, Long userId) {
        if (userId == null) {
            return JSONObject.NULL;
        }
        User user = session.find(User.class, userId);
        JSONObject json = new JSONObject();
        json.put("userId", userId);
        json.put("displayName", displayName(user, userId));
        return json;
    }

    private String displayName(User user, Long fallbackId) {
        if (user != null) {
            String fullName = trimToNull(user.getFullName());
            if (fullName != null) {
                return fullName;
            }
            String email = trimToNull(user.getEmail());
            if (email != null) {
                return email;
            }
        }
        return "User #" + fallbackId;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
