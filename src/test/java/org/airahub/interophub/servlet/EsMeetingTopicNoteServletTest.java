package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.airahub.interophub.dao.EsTopicNoteDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.RecordedOutcomeType;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.RecordedOutcomeService;
import org.airahub.interophub.service.TopicNoteConflictException;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class EsMeetingTopicNoteServletTest {

    @Test
    void createOutcomeReturnsOkEnvelopeOnSuccess() throws Exception {
        EsMeetingTopicNoteServlet servlet = new EsMeetingTopicNoteServlet();

        User user = new User();
        user.setUserId(77L);

        StubRecordedOutcomeService outcomes = new StubRecordedOutcomeService();
        outcomes.createResult = new RecordedOutcomeService.OutcomeMutationResult(12L, 8L, 4L, 99L);

        StubTopicNoteDao notes = new StubTopicNoteDao();
        EsTopicNote note = new EsTopicNote();
        note.setEsTopicNoteId(12L);
        notes.note = note;

        inject(servlet, "authFlowService", new StubAuthFlowService(Optional.of(user)));
        inject(servlet, "recordedOutcomeService", outcomes);
        inject(servlet, "topicNoteDao", notes);
        inject(servlet, "userDao", new StubUserDao());

        RequestContext request = requestForAction(
                "createOutcome",
                "{\"noteId\":12,\"sourceNodeId\":\"node-1\",\"outcomeType\":\"DIRECTION\",\"shortTitle\":\"Title\",\"outcomeText\":\"Outcome\",\"expectedEditorVersion\":4}");
        ResponseCapture response = new ResponseCapture();

        servlet.doPost(request.request, response.response);

        JSONObject json = new JSONObject(response.body());
        assertEquals(200, response.status);
        assertTrue(json.optBoolean("ok"));
        assertEquals(12L, json.getLong("noteId"));
        assertEquals(8L, json.getLong("revision"));
        assertEquals(4L, json.getLong("editorVersion"));
        assertEquals(99L, json.getLong("outcomeId"));

        assertEquals(12L, outcomes.lastCreateNoteId);
        assertEquals("node-1", outcomes.lastCreateSourceNodeId);
        assertEquals(RecordedOutcomeType.DIRECTION, outcomes.lastCreateOutcomeType);
        assertEquals(4L, outcomes.lastExpectedEditorVersion);
        assertEquals(77L, outcomes.lastActingUserId);
    }

    @Test
    void createOutcomeReturnsConflictEnvelope() throws Exception {
        EsMeetingTopicNoteServlet servlet = new EsMeetingTopicNoteServlet();

        User user = new User();
        user.setUserId(11L);

        StubRecordedOutcomeService outcomes = new StubRecordedOutcomeService();
        outcomes.createFailure = new TopicNoteConflictException(
                "NOTE_EDITOR_CHANGED",
                "Another editor is currently responsible for these notes.",
                88L,
                15L);

        StubTopicNoteDao notes = new StubTopicNoteDao();
        EsTopicNote note = new EsTopicNote();
        note.setEsTopicNoteId(12L);
        notes.note = note;

        inject(servlet, "authFlowService", new StubAuthFlowService(Optional.of(user)));
        inject(servlet, "recordedOutcomeService", outcomes);
        inject(servlet, "topicNoteDao", notes);
        inject(servlet, "userDao", new StubUserDao());

        RequestContext request = requestForAction(
                "createOutcome",
                "{\"noteId\":12,\"sourceNodeId\":\"node-1\",\"outcomeType\":\"ACTION\",\"shortTitle\":\"Title\",\"outcomeText\":\"Outcome\",\"expectedEditorVersion\":4}");
        ResponseCapture response = new ResponseCapture();

        servlet.doPost(request.request, response.response);

        JSONObject json = new JSONObject(response.body());
        assertEquals(409, response.status);
        assertFalse(json.optBoolean("ok", true));
        assertEquals("NOTE_EDITOR_CHANGED", json.getString("errorCode"));
        assertEquals(15L, json.getLong("editorVersion"));
        assertNotNull(json.optJSONObject("activeEditor"));
    }

    @Test
    void createOutcomeRejectsMissingExpectedEditorVersion() throws Exception {
        EsMeetingTopicNoteServlet servlet = new EsMeetingTopicNoteServlet();

        User user = new User();
        user.setUserId(51L);

        StubTopicNoteDao notes = new StubTopicNoteDao();
        EsTopicNote note = new EsTopicNote();
        note.setEsTopicNoteId(12L);
        notes.note = note;

        inject(servlet, "authFlowService", new StubAuthFlowService(Optional.of(user)));
        inject(servlet, "recordedOutcomeService", new StubRecordedOutcomeService());
        inject(servlet, "topicNoteDao", notes);

        RequestContext request = requestForAction(
                "createOutcome",
                "{\"noteId\":12,\"sourceNodeId\":\"node-1\",\"outcomeType\":\"DIRECTION\",\"shortTitle\":\"Title\",\"outcomeText\":\"Outcome\"}");
        ResponseCapture response = new ResponseCapture();

        servlet.doPost(request.request, response.response);

        JSONObject json = new JSONObject(response.body());
        assertEquals(400, response.status);
        assertFalse(json.optBoolean("ok", true));
        assertEquals("BAD_REQUEST", json.getString("errorCode"));
        assertTrue(json.getString("message").contains("expectedEditorVersion is required"));
    }

    @Test
    void createOutcomeReturnsForbiddenWhenUserCannotViewMeeting() throws Exception {
        EsMeetingTopicNoteServlet servlet = new EsMeetingTopicNoteServlet();

        User user = new User();
        user.setUserId(51L);

        StubTopicNoteDao notes = new StubTopicNoteDao();
        EsTopicNote note = new EsTopicNote();
        note.setEsTopicNoteId(12L);
        note.setEsMeetingId(900L);
        notes.note = note;

        inject(servlet, "authFlowService", new StubAuthFlowService(Optional.of(user)));
        inject(servlet, "recordedOutcomeService", new StubRecordedOutcomeService());
        inject(servlet, "topicNoteDao", notes);
        inject(servlet, "topicSpaceAccessService", new StubTopicSpaceAccessService(false));
        inject(servlet, "meetingDao", new StubMeetingDao());

        RequestContext request = requestForAction(
                "createOutcome",
                "{\"noteId\":12,\"sourceNodeId\":\"node-1\",\"outcomeType\":\"DIRECTION\",\"shortTitle\":\"Title\",\"outcomeText\":\"Outcome\",\"expectedEditorVersion\":4}");
        ResponseCapture response = new ResponseCapture();

        servlet.doPost(request.request, response.response);

        JSONObject json = new JSONObject(response.body());
        assertEquals(403, response.status);
        assertFalse(json.optBoolean("ok", true));
        assertEquals("FORBIDDEN", json.getString("errorCode"));
    }

    private static RequestContext requestForAction(String action, String body) {
        Map<String, String> params = new HashMap<>();
        params.put("action", action);
        Map<String, String> headers = new HashMap<>();
        headers.put("X-CSRF-Token", "csrf-ok");

        SimpleSessionState sessionState = new SimpleSessionState();
        sessionState.attributes.put(csrfSessionKey(), "csrf-ok");

        HttpSession session = proxySession(sessionState);
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[] { HttpServletRequest.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setCharacterEncoding".equals(name)) {
                        return null;
                    }
                    if ("getParameter".equals(name)) {
                        return params.get((String) args[0]);
                    }
                    if ("getHeader".equals(name)) {
                        return headers.get((String) args[0]);
                    }
                    if ("getReader".equals(name)) {
                        return new BufferedReader(new StringReader(body == null ? "" : body));
                    }
                    if ("getSession".equals(name)) {
                        return session;
                    }
                    if ("getMethod".equals(name)) {
                        return "POST";
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType.equals(boolean.class)) {
                        return false;
                    }
                    if (returnType.equals(int.class)) {
                        return 0;
                    }
                    if (returnType.equals(long.class)) {
                        return 0L;
                    }
                    return null;
                });

        return new RequestContext(request, sessionState);
    }

    private static HttpSession proxySession(SimpleSessionState state) {
        return (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class[] { HttpSession.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getAttribute".equals(name)) {
                        return state.attributes.get((String) args[0]);
                    }
                    if ("setAttribute".equals(name)) {
                        state.attributes.put((String) args[0], args[1]);
                        return null;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType.equals(boolean.class)) {
                        return false;
                    }
                    if (returnType.equals(int.class)) {
                        return 0;
                    }
                    if (returnType.equals(long.class)) {
                        return 0L;
                    }
                    return null;
                });
    }

    private static String csrfSessionKey() {
        return CsrfTokenSupport.class.getName() + ".token";
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class SimpleSessionState {
        private final Map<String, Object> attributes = new HashMap<>();
    }

    private static final class RequestContext {
        private final HttpServletRequest request;
        @SuppressWarnings("unused")
        private final SimpleSessionState sessionState;

        private RequestContext(HttpServletRequest request, SimpleSessionState sessionState) {
            this.request = request;
            this.sessionState = sessionState;
        }
    }

    private static final class ResponseCapture {
        private int status = 200;
        private final StringWriter writer = new StringWriter();

        private final HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class[] { HttpServletResponse.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setStatus".equals(name)) {
                        status = (Integer) args[0];
                        return null;
                    }
                    if ("sendError".equals(name)) {
                        status = (Integer) args[0];
                        return null;
                    }
                    if ("setCharacterEncoding".equals(name) || "setContentType".equals(name)
                            || "setHeader".equals(name)) {
                        return null;
                    }
                    if ("getWriter".equals(name)) {
                        return new PrintWriter(writer);
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType.equals(boolean.class)) {
                        return false;
                    }
                    if (returnType.equals(int.class)) {
                        return 0;
                    }
                    if (returnType.equals(long.class)) {
                        return 0L;
                    }
                    return null;
                });

        private String body() {
            return writer.toString();
        }
    }

    private static final class StubAuthFlowService extends AuthFlowService {
        private final Optional<User> user;

        private StubAuthFlowService(Optional<User> user) {
            this.user = user;
        }

        @Override
        public Optional<User> findAuthenticatedUser(HttpServletRequest request) {
            return user;
        }
    }

    private static final class StubTopicNoteDao extends EsTopicNoteDao {
        private EsTopicNote note;

        @Override
        public Optional<EsTopicNote> findById(Long id) {
            if (note != null && note.getEsTopicNoteId() != null && note.getEsTopicNoteId().equals(id)) {
                return Optional.of(note);
            }
            return Optional.empty();
        }
    }

    private static final class StubUserDao extends UserDao {
        @Override
        public Optional<User> findById(Long id) {
            return Optional.empty();
        }
    }

    private static final class StubMeetingDao extends org.airahub.interophub.dao.EsMeetingDao {
        @Override
        public Optional<EsMeeting> findById(Long id) {
            EsMeeting meeting = new EsMeeting();
            meeting.setEsMeetingId(id);
            return Optional.of(meeting);
        }
    }

    private static final class StubTopicSpaceAccessService extends TopicSpaceAccessService {
        private final boolean canView;

        private StubTopicSpaceAccessService(boolean canView) {
            this.canView = canView;
        }

        @Override
        public boolean canViewMeeting(User user, EsMeeting meeting) {
            return canView;
        }
    }

    private static final class StubRecordedOutcomeService extends RecordedOutcomeService {
        private RecordedOutcomeService.OutcomeMutationResult createResult;
        private RuntimeException createFailure;

        private Long lastCreateNoteId;
        private String lastCreateSourceNodeId;
        private RecordedOutcomeType lastCreateOutcomeType;
        private Long lastExpectedEditorVersion;
        private Long lastActingUserId;

        @Override
        public RecordedOutcomeService.OutcomeMutationResult createOutcome(Long noteId, String sourceNodeId,
                RecordedOutcomeType outcomeType, String shortTitle, String outcomeText,
                Long expectedEditorVersion, Long actingUserId) {
            this.lastCreateNoteId = noteId;
            this.lastCreateSourceNodeId = sourceNodeId;
            this.lastCreateOutcomeType = outcomeType;
            this.lastExpectedEditorVersion = expectedEditorVersion;
            this.lastActingUserId = actingUserId;
            if (createFailure != null) {
                throw createFailure;
            }
            return createResult;
        }
    }
}
