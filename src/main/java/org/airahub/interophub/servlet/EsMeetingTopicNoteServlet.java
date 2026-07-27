package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.logging.Logger;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsTopicNoteDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.RecordedOutcomeType;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.RecordedOutcomeService;
import org.airahub.interophub.service.TopicNoteConflictException;
import org.airahub.interophub.service.TopicNoteService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class EsMeetingTopicNoteServlet extends HttpServlet {

    private static final String ACTION_START_EDITING = "startEditing";
    private static final String ACTION_TAKE_OVER = "takeOver";
    private static final String ACTION_EDITOR_STATE = "editorState";
    private static final String ACTION_CONTENT = "content";
    private static final String ACTION_OUTCOMES = "outcomes";
    private static final String ACTION_CREATE_OUTCOME = "createOutcome";
    private static final String ACTION_UPDATE_OUTCOME = "updateOutcome";
    private static final String ACTION_REMOVE_OUTCOME = "removeOutcome";
    private static final Logger LOGGER = Logger.getLogger(EsMeetingTopicNoteServlet.class.getName());

    private final AuthFlowService authFlowService;
    private final TopicSpaceAccessService topicSpaceAccessService;
    private final TopicNoteService topicNoteService;
    private final RecordedOutcomeService recordedOutcomeService;
    private final EsTopicNoteDao topicNoteDao;
    private final EsMeetingDao meetingDao;
    private final UserDao userDao;

    public EsMeetingTopicNoteServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
        this.topicNoteService = new TopicNoteService();
        this.recordedOutcomeService = new RecordedOutcomeService();
        this.topicNoteDao = new EsTopicNoteDao();
        this.meetingDao = new EsMeetingDao();
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeError(response, "AUTH_REQUIRED", "Authentication is required.");
            return;
        }

        String action = trimToNull(request.getParameter("action"));
        if (!ACTION_EDITOR_STATE.equals(action) && !ACTION_CONTENT.equals(action) && !ACTION_OUTCOMES.equals(action)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "BAD_REQUEST", "Unsupported action.");
            return;
        }

        try {
            Long noteId = parseRequiredLong(request.getParameter("noteId"), "noteId is required.");
            EsTopicNote note = topicNoteDao.findById(noteId).orElse(null);
            if (note == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                writeError(response, "NOT_FOUND", "Topic note was not found.");
                return;
            }
            if (!canViewNote(user.get(), note)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                writeError(response, "FORBIDDEN", "You do not have access to this note.");
                return;
            }

            if (ACTION_CONTENT.equals(action)) {
                TopicNoteService.TopicNoteContentState contentState = topicNoteService.getContentState(noteId);
                Long sinceRevision = parseLong(request.getParameter("sinceRevision"));
                if (sinceRevision != null && contentState.metadata().revision() != null
                        && sinceRevision.longValue() >= contentState.metadata().revision().longValue()) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return;
                }
                writeContentState(response, contentState);
                return;
            }

            if (ACTION_OUTCOMES.equals(action)) {
                writeOutcomes(response, recordedOutcomeService.getOutcomesForNote(noteId));
                return;
            }

            TopicNoteService.TopicNoteEditorState state = topicNoteService.getEditorState(noteId);
            writeEditorState(response, state, true, null, null);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "BAD_REQUEST", ex.getMessage());
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(response, "SERVER_ERROR", "Unexpected editor-state error.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeError(response, "AUTH_REQUIRED", "Authentication is required.");
            return;
        }
        if (!CsrfTokenSupport.isValid(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeError(response, "CSRF_FAILED", "CSRF validation failed.");
            return;
        }

        String action = trimToNull(request.getParameter("action"));
        if (ACTION_START_EDITING.equals(action)) {
            handleStartEditing(request, response, user.get());
            return;
        }
        if (ACTION_TAKE_OVER.equals(action)) {
            handleTakeOver(request, response, user.get());
            return;
        }
        if (ACTION_CREATE_OUTCOME.equals(action)) {
            handleCreateOutcome(request, response, user.get());
            return;
        }
        if (ACTION_UPDATE_OUTCOME.equals(action)) {
            handleUpdateOutcome(request, response, user.get());
            return;
        }
        if (ACTION_REMOVE_OUTCOME.equals(action)) {
            handleRemoveOutcome(request, response, user.get());
            return;
        }
        handleSave(request, response, user.get());
    }

    private void handleCreateOutcome(HttpServletRequest request, HttpServletResponse response, User user)
            throws IOException {
        try {
            JSONObject body = parseJsonBody(request);
            Long noteId = parseRequiredLongObject(body.opt("noteId"), "noteId is required.");
            EsTopicNote note = topicNoteDao.findById(noteId).orElse(null);
            if (note == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                writeError(response, "NOT_FOUND", "Topic note was not found.");
                return;
            }
            if (!canViewNote(user, note)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                writeError(response, "FORBIDDEN", "You do not have access to this note.");
                return;
            }

            RecordedOutcomeType outcomeType = parseOutcomeType(body.optString("outcomeType", null));
            String sourceNodeId = trimToNull(body.optString("sourceNodeId", null));
            String shortTitle = trimToNull(body.optString("shortTitle", null));
            String outcomeText = trimToNull(body.optString("outcomeText", null));
            Long expectedEditorVersion = parseRequiredLongObject(body.opt("expectedEditorVersion"),
                    "expectedEditorVersion is required.");

            RecordedOutcomeService.OutcomeMutationResult result = recordedOutcomeService.createOutcome(
                    noteId,
                    sourceNodeId,
                    outcomeType,
                    shortTitle,
                    outcomeText,
                    expectedEditorVersion,
                    user.getUserId());
            writeOutcomeMutationSuccess(response, result);
        } catch (TopicNoteConflictException ex) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            writeConflict(response, ex);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "BAD_REQUEST", ex.getMessage());
        } catch (IllegalStateException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeError(response, "FORBIDDEN", ex.getMessage());
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(response, "SERVER_ERROR", "Unexpected create-outcome error.");
        }
    }

    private void handleUpdateOutcome(HttpServletRequest request, HttpServletResponse response, User user)
            throws IOException {
        try {
            JSONObject body = parseJsonBody(request);
            Long outcomeId = parseRequiredLongObject(body.opt("outcomeId"), "outcomeId is required.");
            RecordedOutcomeType outcomeType = parseOutcomeType(body.optString("outcomeType", null));
            String shortTitle = trimToNull(body.optString("shortTitle", null));
            String outcomeText = trimToNull(body.optString("outcomeText", null));
            Long expectedEditorVersion = parseRequiredLongObject(body.opt("expectedEditorVersion"),
                    "expectedEditorVersion is required.");

            RecordedOutcomeService.OutcomeMutationResult result = recordedOutcomeService.updateOutcome(
                    outcomeId,
                    outcomeType,
                    shortTitle,
                    outcomeText,
                    expectedEditorVersion,
                    user.getUserId());
            writeOutcomeMutationSuccess(response, result);
        } catch (TopicNoteConflictException ex) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            writeConflict(response, ex);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "BAD_REQUEST", ex.getMessage());
        } catch (IllegalStateException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeError(response, "FORBIDDEN", ex.getMessage());
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(response, "SERVER_ERROR", "Unexpected update-outcome error.");
        }
    }

    private void handleRemoveOutcome(HttpServletRequest request, HttpServletResponse response, User user)
            throws IOException {
        try {
            JSONObject body = parseJsonBody(request);
            Long outcomeId = parseRequiredLongObject(body.opt("outcomeId"), "outcomeId is required.");
            Long expectedEditorVersion = parseRequiredLongObject(body.opt("expectedEditorVersion"),
                    "expectedEditorVersion is required.");

            RecordedOutcomeService.OutcomeMutationResult result = recordedOutcomeService.deleteOutcome(
                    outcomeId,
                    expectedEditorVersion,
                    user.getUserId());
            writeOutcomeMutationSuccess(response, result);
        } catch (TopicNoteConflictException ex) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            writeConflict(response, ex);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "BAD_REQUEST", ex.getMessage());
        } catch (IllegalStateException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeError(response, "FORBIDDEN", ex.getMessage());
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(response, "SERVER_ERROR", "Unexpected remove-outcome error.");
        }
    }

    private void handleStartEditing(HttpServletRequest request, HttpServletResponse response, User user)
            throws IOException {
        try {
            Long meetingId = parseRequiredLong(request.getParameter("meetingId"), "meetingId is required.");
            Long agendaItemId = parseRequiredLong(request.getParameter("agendaItemId"), "agendaItemId is required.");
            EsMeeting meeting = meetingDao.findById(meetingId).orElse(null);
            if (meeting == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                writeError(response, "NOT_FOUND", "Meeting was not found.");
                return;
            }
            if (!topicSpaceAccessService.canViewMeeting(user, meeting)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                writeError(response, "FORBIDDEN", "You do not have access to this note.");
                return;
            }

            TopicNoteService.TopicNoteEditorState state = topicNoteService
                    .startMeetingTopicNoteEditing(meetingId, agendaItemId, user.getUserId());
            writeEditorState(response, state, true, state.created(), state.tookOver());
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "BAD_REQUEST", ex.getMessage());
        } catch (IllegalStateException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "INVALID_STATE", ex.getMessage());
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(response, "SERVER_ERROR", "Unexpected note start-editing error.");
        }
    }

    private void handleTakeOver(HttpServletRequest request, HttpServletResponse response, User user)
            throws IOException {
        try {
            Long noteId = parseRequiredLong(request.getParameter("noteId"), "noteId is required.");
            EsTopicNote note = topicNoteDao.findById(noteId).orElse(null);
            if (note == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                writeError(response, "NOT_FOUND", "Topic note was not found.");
                return;
            }
            if (!canViewNote(user, note)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                writeError(response, "FORBIDDEN", "You do not have access to this note.");
                return;
            }

            TopicNoteService.TopicNoteEditorState state = topicNoteService.takeOverEditing(noteId, user.getUserId());
            writeEditorState(response, state, true, false, state.tookOver());
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "BAD_REQUEST", ex.getMessage());
        } catch (IllegalStateException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeError(response, "FORBIDDEN", ex.getMessage());
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(response, "SERVER_ERROR", "Unexpected note take-over error.");
        }
    }

    private void handleSave(HttpServletRequest request, HttpServletResponse response, User user) throws IOException {
        try {
            SaveRequest saveRequest = parseSaveRequest(request);
            TopicNoteService.SavedMeetingTopicNote saved = topicNoteService.saveMeetingTopicNote(
                    saveRequest.meetingId(),
                    saveRequest.agendaItemId(),
                    user.getUserId(),
                    saveRequest.expectedRevision(),
                    saveRequest.expectedEditorVersion(),
                    saveRequest.documentJson(),
                    parseSaveMode(request));
            if (saved.emptyIgnored()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writeError(response, "EMPTY_DOCUMENT", "Enter some notes before saving.");
                return;
            }
            writeSaveSuccess(response, saved);
        } catch (TopicNoteConflictException ex) {
            LOGGER.info("Topic note save conflict: code=" + ex.getErrorCode());
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            writeConflict(response, ex);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "BAD_REQUEST", ex.getMessage());
        } catch (IllegalStateException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeError(response, "FORBIDDEN", ex.getMessage());
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(response, "SERVER_ERROR", "Unexpected note save error.");
        }
    }

    private SaveRequest parseSaveRequest(HttpServletRequest request) throws IOException {
        Long meetingId = parseRequiredLong(request.getParameter("meetingId"),
                "meetingId and agendaItemId are required.");
        Long agendaItemId = parseRequiredLong(request.getParameter("agendaItemId"),
                "meetingId and agendaItemId are required.");

        JSONObject jsonObject = parseJsonBody(request);
        if (!jsonObject.has("expectedRevision")) {
            throw new IllegalArgumentException("expectedRevision is required.");
        }
        if (!jsonObject.has("expectedEditorVersion")) {
            throw new IllegalArgumentException("expectedEditorVersion is required.");
        }
        if (!jsonObject.has("document")) {
            throw new IllegalArgumentException("document is required.");
        }

        long expectedRevision = jsonObject.optLong("expectedRevision", Long.MIN_VALUE);
        long expectedEditorVersion = jsonObject.optLong("expectedEditorVersion", Long.MIN_VALUE);
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must be zero or greater.");
        }
        if (expectedEditorVersion < 0) {
            throw new IllegalArgumentException("expectedEditorVersion must be zero or greater.");
        }
        Object document = jsonObject.get("document");
        if (!(document instanceof JSONObject)) {
            throw new IllegalArgumentException("document must be a JSON object.");
        }
        return new SaveRequest(meetingId, agendaItemId, expectedRevision, expectedEditorVersion, document.toString());
    }

    private JSONObject parseJsonBody(HttpServletRequest request) throws IOException {
        String body = request.getReader().lines().reduce("", (left, right) -> left + right);
        Object parsed = new JSONTokener(body == null ? "" : body).nextValue();
        if (!(parsed instanceof JSONObject jsonObject)) {
            throw new IllegalArgumentException("Request body must be a JSON object.");
        }
        return jsonObject;
    }

    private boolean canViewNote(User user, EsTopicNote note) {
        if (note == null) {
            return false;
        }
        if (note.getEsMeetingId() != null) {
            EsMeeting meeting = meetingDao.findById(note.getEsMeetingId()).orElse(null);
            return topicSpaceAccessService.canViewMeeting(user, meeting);
        }
        return user != null && user.getUserId() != null;
    }

    private void writeSaveSuccess(HttpServletResponse response, TopicNoteService.SavedMeetingTopicNote saved)
            throws IOException {
        JSONObject json = new JSONObject();
        json.put("ok", true);
        json.put("noteId", saved.noteId());
        json.put("revision", saved.revision());
        json.put("editorVersion", saved.editorVersion());
        json.put("savedAt", saved.savedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        json.put("status", saved.status().name());
        json.put("created", saved.created());
        json.put("activeEditor", activeEditorJson(saved.activeEditorUserId()));
        writeJson(response, json);
    }

    private void writeEditorState(HttpServletResponse response, TopicNoteService.TopicNoteEditorState state,
            boolean ok, Boolean created, Boolean tookOver) throws IOException {
        JSONObject json = new JSONObject();
        json.put("ok", ok);
        json.put("noteId", state.noteId());
        json.put("meetingId", state.meetingId());
        json.put("agendaItemId", state.agendaItemId());
        json.put("revision", state.revision());
        json.put("editorVersion", state.editorVersion());
        json.put("status", state.noteStatus() == null ? JSONObject.NULL : state.noteStatus().name());
        json.put("meetingStatus", state.meetingStatus() == null ? JSONObject.NULL : state.meetingStatus().name());
        json.put("savedAt", state.savedAt() == null ? JSONObject.NULL
                : state.savedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        json.put("activeEditor", activeEditorJson(state.activeEditorUserId()));
        if (created != null) {
            json.put("created", created.booleanValue());
        }
        if (tookOver != null) {
            json.put("tookOver", tookOver.booleanValue());
        }
        writeJson(response, json);
    }

    private void writeContentState(HttpServletResponse response, TopicNoteService.TopicNoteContentState contentState)
            throws IOException {
        TopicNoteService.TopicNoteEditorState metadata = contentState.metadata();
        JSONObject json = new JSONObject();
        json.put("ok", true);
        json.put("noteId", metadata.noteId());
        json.put("revision", metadata.revision());
        json.put("editorVersion", metadata.editorVersion());
        json.put("status", metadata.noteStatus() == null ? JSONObject.NULL : metadata.noteStatus().name());
        json.put("meetingStatus", metadata.meetingStatus() == null ? JSONObject.NULL : metadata.meetingStatus().name());
        json.put("savedAt", metadata.savedAt() == null ? JSONObject.NULL
                : metadata.savedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        json.put("activeEditor", activeEditorJson(metadata.activeEditorUserId()));
        json.put("document",
                contentState.documentJson() == null ? JSONObject.NULL : new JSONObject(contentState.documentJson()));
        writeJson(response, json);
    }

    private Object activeEditorJson(Long activeEditorUserId) {
        if (activeEditorUserId == null) {
            return JSONObject.NULL;
        }
        User editor = userDao.findById(activeEditorUserId).orElse(null);
        JSONObject editorJson = new JSONObject();
        editorJson.put("userId", activeEditorUserId);
        editorJson.put("displayName", userDisplayName(editor, activeEditorUserId));
        return editorJson;
    }

    private void writeConflict(HttpServletResponse response, TopicNoteConflictException ex) throws IOException {
        JSONObject json = new JSONObject();
        json.put("ok", false);
        json.put("errorCode", ex.getErrorCode());
        json.put("message", ex.getMessage());
        if (ex.getEditorVersion() != null) {
            json.put("editorVersion", ex.getEditorVersion());
        }
        if (ex.getActiveEditorUserId() != null) {
            json.put("activeEditor", activeEditorJson(ex.getActiveEditorUserId()));
        }
        writeJson(response, json);
    }

    private void writeOutcomes(HttpServletResponse response, RecordedOutcomeService.OutcomesState outcomesState)
            throws IOException {
        JSONObject json = new JSONObject();
        json.put("ok", true);
        json.put("noteId", outcomesState.noteId());
        json.put("revision", outcomesState.revision());
        json.put("editorVersion", outcomesState.editorVersion());
        JSONArray outcomes = new JSONArray();
        for (RecordedOutcomeService.OutcomeView outcome : outcomesState.outcomes()) {
            JSONObject item = new JSONObject();
            item.put("outcomeId", outcome.outcomeId());
            item.put("sourceNodeId", outcome.sourceNodeId());
            item.put("outcomeType", outcome.outcomeType() == null ? JSONObject.NULL : outcome.outcomeType().name());
            item.put("shortTitle", outcome.shortTitle() == null ? JSONObject.NULL : outcome.shortTitle());
            item.put("outcomeText", outcome.outcomeText());
            item.put("sourceText", outcome.sourceText() == null ? "" : outcome.sourceText());
            item.put("displayOrder", outcome.displayOrder());
            outcomes.put(item);
        }
        json.put("outcomes", outcomes);
        writeJson(response, json);
    }

    private void writeOutcomeMutationSuccess(HttpServletResponse response,
            RecordedOutcomeService.OutcomeMutationResult result) throws IOException {
        JSONObject json = new JSONObject();
        json.put("ok", true);
        json.put("noteId", result.noteId());
        json.put("revision", result.revision());
        json.put("editorVersion", result.editorVersion());
        json.put("outcomeId", result.outcomeId());
        writeJson(response, json);
    }

    private void writeError(HttpServletResponse response, String errorCode, String message) throws IOException {
        JSONObject json = new JSONObject();
        json.put("ok", false);
        json.put("errorCode", errorCode == null ? "ERROR" : errorCode);
        json.put("error", message == null ? "Unexpected error." : message);
        json.put("message", message == null ? "Unexpected error." : message);
        writeJson(response, json);
    }

    private void writeJson(HttpServletResponse response, JSONObject json) throws IOException {
        try (PrintWriter out = response.getWriter()) {
            out.print(json.toString());
        }
    }

    private Long parseRequiredLong(String raw, String missingMessage) {
        Long parsed = parseLong(raw);
        if (parsed == null) {
            throw new IllegalArgumentException(missingMessage);
        }
        return parsed;
    }

    private Long parseLong(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseRequiredLongObject(Object value, String message) {
        Long parsed = parseLongObject(value);
        if (parsed == null) {
            throw new IllegalArgumentException(message);
        }
        return parsed;
    }

    private Long parseLongObject(Object value) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return parseLong(text);
        }
        return null;
    }

    private RecordedOutcomeType parseOutcomeType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("outcomeType is required.");
        }
        try {
            return RecordedOutcomeType.valueOf(normalized.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported outcomeType value.");
        }
    }

    private String userDisplayName(User user, Long fallbackId) {
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

    private TopicNoteService.SaveMode parseSaveMode(HttpServletRequest request) {
        String raw = trimToNull(request.getHeader("X-InteropHub-Save-Mode"));
        if (raw == null) {
            return TopicNoteService.SaveMode.AUTOSAVE;
        }
        if ("manual".equalsIgnoreCase(raw)) {
            return TopicNoteService.SaveMode.MANUAL;
        }
        return TopicNoteService.SaveMode.AUTOSAVE;
    }

    private record SaveRequest(Long meetingId, Long agendaItemId, Long expectedRevision, Long expectedEditorVersion,
            String documentJson) {
    }
}
