package org.airahub.interophub.servlet;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsTopicNoteDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicNoteEventBroker;
import org.airahub.interophub.service.TopicNoteService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.json.JSONObject;

public class EsMeetingTopicNoteStreamServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(EsMeetingTopicNoteStreamServlet.class.getName());
    private static final ScheduledExecutorService KEEPALIVE = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "interophub-note-sse-keepalive");
        thread.setDaemon(true);
        return thread;
    });
    private static final Set<StreamConnection> CONNECTIONS = ConcurrentHashMap.newKeySet();

    static {
        KEEPALIVE.scheduleAtFixedRate(() -> {
            for (StreamConnection connection : CONNECTIONS) {
                connection.sendKeepalive();
            }
        }, 20, 20, TimeUnit.SECONDS);
    }

    private final AuthFlowService authFlowService;
    private final TopicSpaceAccessService topicSpaceAccessService;
    private final EsTopicNoteDao topicNoteDao;
    private final EsMeetingDao meetingDao;
    private final TopicNoteService topicNoteService;
    private final TopicNoteEventBroker broker;

    public EsMeetingTopicNoteStreamServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
        this.topicNoteDao = new EsTopicNoteDao();
        this.meetingDao = new EsMeetingDao();
        this.topicNoteService = new TopicNoteService();
        this.broker = TopicNoteEventBroker.getInstance();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication is required.");
            return;
        }

        Long noteId = parseLong(request.getParameter("noteId"));
        if (noteId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "noteId is required.");
            return;
        }

        EsTopicNote note = topicNoteDao.findById(noteId).orElse(null);
        if (note == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Topic note was not found.");
            return;
        }
        if (!canViewNote(user.get(), note)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have access to this note.");
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        AsyncContext async = request.startAsync();
        async.setTimeout(0L);

        StreamConnection connection = new StreamConnection(noteId, async);
        CONNECTIONS.add(connection);

        TopicNoteEventBroker.Subscription subscription = broker.subscribe(noteId, (eventType, payload) -> {
            connection.sendEvent(eventType, payload);
        });
        connection.setSubscription(subscription);

        async.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                connection.close();
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                connection.close();
            }

            @Override
            public void onError(AsyncEvent event) {
                connection.close();
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                // no-op
            }
        });

        LOGGER.info("SSE note stream opened for noteId=" + noteId);
        connection.sendComment("connected");
        connection.sendEvent("note-state", initialStatePayload(noteId));
    }

    private JSONObject initialStatePayload(Long noteId) {
        TopicNoteService.TopicNoteEditorState state = topicNoteService.getEditorState(noteId);
        JSONObject payload = new JSONObject();
        payload.put("noteId", state.noteId());
        payload.put("revision", state.revision());
        payload.put("editorVersion", state.editorVersion());
        payload.put("noteStatus", state.noteStatus() == null ? JSONObject.NULL : state.noteStatus().name());
        payload.put("meetingStatus", state.meetingStatus() == null ? JSONObject.NULL : state.meetingStatus().name());
        payload.put("savedAt", state.savedAt() == null ? JSONObject.NULL
                : state.savedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        payload.put("activeEditor", activeEditorJson(state.activeEditorUserId()));
        return payload;
    }

    private Object activeEditorJson(Long userId) {
        if (userId == null) {
            return JSONObject.NULL;
        }
        User user = new org.airahub.interophub.dao.UserDao().findById(userId).orElse(null);
        JSONObject json = new JSONObject();
        json.put("userId", userId);
        if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
            json.put("displayName", user.getFullName());
        } else if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            json.put("displayName", user.getEmail());
        } else {
            json.put("displayName", "User #" + userId);
        }
        return json;
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

    private static final class StreamConnection {
        private final Long noteId;
        private final AsyncContext asyncContext;
        private volatile TopicNoteEventBroker.Subscription subscription;
        private volatile boolean closed;

        private StreamConnection(Long noteId, AsyncContext asyncContext) {
            this.noteId = noteId;
            this.asyncContext = asyncContext;
            HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
            try {
                ServletOutputStream output = response.getOutputStream();
                output.setWriteListener(new WriteListener() {
                    @Override
                    public void onWritePossible() {
                        // no-op
                    }

                    @Override
                    public void onError(Throwable t) {
                        close();
                    }
                });
            } catch (IOException ex) {
                close();
            }
        }

        private void setSubscription(TopicNoteEventBroker.Subscription subscription) {
            this.subscription = subscription;
        }

        private synchronized void sendKeepalive() {
            sendComment("keepalive");
        }

        private synchronized void sendComment(String comment) {
            if (closed) {
                return;
            }
            try {
                HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
                response.getOutputStream().write((":" + comment + "\n\n").getBytes(StandardCharsets.UTF_8));
                response.getOutputStream().flush();
            } catch (IOException ex) {
                close();
            }
        }

        private synchronized void sendEvent(String eventType, JSONObject payload) {
            if (closed) {
                return;
            }
            try {
                HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
                String body = "event: " + eventType + "\n"
                        + "data: " + payload.toString() + "\n\n";
                response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                response.getOutputStream().flush();
            } catch (IOException ex) {
                close();
            }
        }

        private synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            CONNECTIONS.remove(this);
            if (subscription != null) {
                try {
                    subscription.close();
                } catch (Exception ignored) {
                    // no-op
                }
            }
            try {
                asyncContext.complete();
            } catch (Exception ignored) {
                // no-op
            }
            LOGGER.info("SSE note stream closed for noteId=" + noteId);
        }
    }
}
