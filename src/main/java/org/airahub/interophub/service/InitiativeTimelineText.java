package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;

/** One line of the shared initiative timeline. */
public final class InitiativeTimelineText {

    public static final int PREVIEW_COUNT = 5;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private InitiativeTimelineText() {
    }

    public static String line(LocalDateTime at, String author, EsInitiativeTimelineEntry.EntryType type, String from,
            String to, String note, boolean edited) {
        String who = author == null || author.isBlank() ? "Someone" : author.trim();
        String when = at == null ? "" : DAY.format(at);
        return when + " · " + who + " · " + body(type, from, to, note, edited);
    }

    /** Index of the first entry to show before "Show all". Entries are oldest first. */
    public static int firstVisible(int size, boolean showAll) {
        if (showAll || size <= PREVIEW_COUNT) {
            return 0;
        }
        return size - PREVIEW_COUNT;
    }

    private static String body(EsInitiativeTimelineEntry.EntryType type, String from, String to, String note,
            boolean edited) {
        if (type == EsInitiativeTimelineEntry.EntryType.STATUS_CHANGE) {
            String start = from == null || from.isBlank() ? "New" : from.trim();
            String end = to == null ? "" : to.trim();
            String text = start + " → " + end;
            if (note != null && !note.isBlank()) {
                text = text + ": \"" + note.trim() + "\"";
            }
            return text;
        }
        if (type == EsInitiativeTimelineEntry.EntryType.FIELD_CHANGE) {
            String field = note == null || note.isBlank() ? "Updated" : note.trim();
            return field + ": " + dash(from) + " → " + dash(to);
        }
        String text = note == null ? "" : note.trim();
        if (edited) {
            text = text + " (edited)";
        }
        return text;
    }

    private static String dash(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        String trimmed = value.trim();
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "…" : trimmed;
    }
}
