package org.airahub.interophub.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;
import org.json.JSONObject;

public final class TopicNoteEventBroker {

    public interface TopicNoteEventListener {
        void onEvent(String eventType, JSONObject payload) throws Exception;
    }

    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    private static final Logger LOGGER = Logger.getLogger(TopicNoteEventBroker.class.getName());
    private static final TopicNoteEventBroker INSTANCE = new TopicNoteEventBroker();

    private final Map<Long, Set<TopicNoteEventListener>> listenersByNoteId = new ConcurrentHashMap<>();

    private TopicNoteEventBroker() {
    }

    public static TopicNoteEventBroker getInstance() {
        return INSTANCE;
    }

    public Subscription subscribe(Long noteId, TopicNoteEventListener listener) {
        if (noteId == null || listener == null) {
            return () -> {
            };
        }
        listenersByNoteId.computeIfAbsent(noteId, ignored -> new CopyOnWriteArraySet<>()).add(listener);
        return () -> unsubscribe(noteId, listener);
    }

    public void publish(Long noteId, String eventType, JSONObject payload) {
        if (noteId == null || eventType == null || eventType.isBlank() || payload == null) {
            return;
        }
        Set<TopicNoteEventListener> listeners = listenersByNoteId.get(noteId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        for (TopicNoteEventListener listener : listeners) {
            try {
                listener.onEvent(eventType, new JSONObject(payload.toString()));
            } catch (Exception ex) {
                LOGGER.fine("Removing note stream subscriber after event write failure for noteId=" + noteId);
                unsubscribe(noteId, listener);
            }
        }
    }

    private void unsubscribe(Long noteId, TopicNoteEventListener listener) {
        Set<TopicNoteEventListener> listeners = listenersByNoteId.get(noteId);
        if (listeners == null) {
            return;
        }
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            listenersByNoteId.remove(noteId);
        }
    }
}
