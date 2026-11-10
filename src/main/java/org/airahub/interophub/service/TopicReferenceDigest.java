package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.airahub.interophub.service.GuideRows.CommentRow;
import org.airahub.interophub.service.GuideRows.FlagRow;

/**
 * Bounded comment and flag totals for one guide on the topic page.
 * Deleted and withdrawn comments are left out. {@code EsGuideCommentDao} counts the same rows.
 */
public record TopicReferenceDigest(
        int open,
        int disposed,
        int closed,
        LocalDateTime lastActivity,
        int recentComments,
        int recentReplies,
        int recentPeople,
        List<OpenThread> openThreads,
        List<FlagNote> flagNotes,
        int flagCount) {

    public static final int LIST_LIMIT = 5;
    public static final int FLAG_LIMIT = 3;

    public TopicReferenceDigest {
        openThreads = List.copyOf(openThreads == null ? List.of() : openThreads);
        flagNotes = List.copyOf(flagNotes == null ? List.of() : flagNotes);
    }

    public record OpenThread(long id, String text, String author, LocalDateTime activityAt, int replies) {
    }

    public record FlagNote(String anchor, String note, LocalDateTime at) {
    }

    public static TopicReferenceDigest empty() {
        return new TopicReferenceDigest(0, 0, 0, null, 0, 0, 0, List.of(), List.of(), 0);
    }

    /** The anchor the Flags figure opens, or null when there is nothing flagged. */
    public String firstAnchor() {
        if (flagNotes.isEmpty() || flagNotes.get(0).anchor() == null || flagNotes.get(0).anchor().isBlank()) {
            return null;
        }
        return flagNotes.get(0).anchor();
    }

    /**
     * Counts from already-loaded rows. The topic page uses the matching aggregate queries
     * instead of calling this for a live document.
     */
    public static TopicReferenceDigest of(List<CommentRow> comments, List<FlagRow> flags,
            Map<Long, String> authorNames, LocalDateTime since) {
        List<CommentRow> rows = comments == null ? List.of() : comments;
        int open = 0;
        int disposed = 0;
        int closed = 0;
        int recentComments = 0;
        int recentReplies = 0;
        LocalDateTime lastActivity = null;
        Set<Long> people = new HashSet<>();
        Map<Long, Integer> replies = new HashMap<>();
        List<CommentRow> threads = new ArrayList<>();
        for (CommentRow row : rows) {
            if (!live(row)) {
                continue;
            }
            if (row.updatedAt != null && (lastActivity == null || row.updatedAt.isAfter(lastActivity))) {
                lastActivity = row.updatedAt;
            }
            boolean recent = since != null && row.updatedAt != null && !row.updatedAt.isBefore(since);
            if (recent) {
                people.add(row.userId);
                if (row.parentId == null) {
                    recentComments++;
                } else {
                    recentReplies++;
                }
            }
            if (row.parentId != null) {
                replies.merge(row.parentId, 1, Integer::sum);
                continue;
            }
            if ("OPEN".equals(row.state)) {
                open++;
                threads.add(row);
            } else if ("DISPOSED".equals(row.state) && row.closedAt == null) {
                disposed++;
            } else if ("CLOSED".equals(row.state)) {
                closed++;
            }
        }
        threads.sort(Comparator
                .comparing((CommentRow row) -> row.updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(row -> row.id == null ? 0L : row.id, Comparator.reverseOrder()));
        if (threads.size() > LIST_LIMIT) {
            threads = new ArrayList<>(threads.subList(0, LIST_LIMIT));
        }
        List<OpenThread> openThreads = new ArrayList<>();
        for (CommentRow row : threads) {
            openThreads.add(new OpenThread(row.id == null ? 0L : row.id, row.text, author(authorNames, row.userId),
                    row.updatedAt, replies.getOrDefault(row.id, 0)));
        }
        List<FlagRow> active = new ArrayList<>();
        if (flags != null) {
            for (FlagRow flag : flags) {
                if (flag != null && !flag.cleared()) {
                    active.add(flag);
                }
            }
        }
        active.sort(Comparator
                .comparing((FlagRow flag) -> flag.createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(flag -> flag.id == null ? 0L : flag.id, Comparator.reverseOrder()));
        int flagCount = active.size();
        if (active.size() > FLAG_LIMIT) {
            active = new ArrayList<>(active.subList(0, FLAG_LIMIT));
        }
        List<FlagNote> notes = new ArrayList<>();
        for (FlagRow flag : active) {
            notes.add(new FlagNote(flag.anchor, flag.note, flag.createdAt));
        }
        return new TopicReferenceDigest(open, disposed, closed, lastActivity, recentComments, recentReplies,
                people.size(), openThreads, notes, flagCount);
    }

    private static boolean live(CommentRow row) {
        return row != null && !row.deleted() && !row.withdrawn();
    }

    private static String author(Map<Long, String> authorNames, long userId) {
        String name = authorNames == null ? null : authorNames.get(userId);
        return name == null || name.isBlank() ? "Someone" : name;
    }
}
