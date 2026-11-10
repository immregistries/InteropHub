package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.service.GuideRows.CommentRow;
import org.airahub.interophub.service.GuideRows.FlagRow;
import org.airahub.interophub.service.TopicReferenceDigest.OpenThread;
import org.airahub.interophub.service.TopicReferenceSectionData.Attention;
import org.airahub.interophub.service.TopicReferenceSectionData.FlagLine;
import org.junit.jupiter.api.Test;

class TopicReferenceDigestTest {
    @Test
    void countsSkipDeletedAndWithdrawnAndCapTheLists() {
        List<CommentRow> comments = List.of(
                comment(1L, null, "OPEN", "open-one", 1L, LocalDateTime.of(2026, 11, 9, 15, 0)),
                comment(2L, null, "OPEN", "open-two", 3L, LocalDateTime.of(2026, 11, 8, 15, 0)),
                comment(3L, null, "OPEN", "open-three", 1L, LocalDateTime.of(2026, 11, 7, 15, 0)),
                comment(4L, null, "OPEN", "open-four", 1L, LocalDateTime.of(2026, 11, 6, 15, 0)),
                comment(5L, null, "OPEN", "open-five", 1L, LocalDateTime.of(2026, 11, 5, 15, 0)),
                comment(6L, null, "OPEN", "open-six", 1L, LocalDateTime.of(2026, 11, 4, 15, 0)),
                comment(7L, 1L, "OPEN", "a reply", 2L, LocalDateTime.of(2026, 11, 9, 16, 0)),
                comment(8L, null, "DISPOSED", "disposed-one", 1L, LocalDateTime.of(2026, 11, 3, 15, 0)),
                closed(9L, LocalDateTime.of(2026, 11, 2, 15, 0)),
                withdrawn(10L, "WITHDRAWN-SHOULD-NOT-COUNT"),
                deleted(11L, "DELETED-SHOULD-NOT-COUNT"),
                withdrawnOpen(12L, "OPEN-WITHDRAWN-SHOULD-NOT-COUNT"),
                disposedButClosed(13L));
        List<FlagRow> flags = List.of(
                flag(1L, "a-one", "flag-one", LocalDateTime.of(2026, 11, 9, 12, 0), false),
                flag(2L, "a-two", "flag-two", LocalDateTime.of(2026, 11, 8, 12, 0), false),
                flag(3L, "a-three", "flag-three", LocalDateTime.of(2026, 11, 7, 12, 0), false),
                flag(4L, "a-four", "flag-four", LocalDateTime.of(2026, 11, 6, 12, 0), false),
                flag(5L, "a-cleared", "flag-cleared", LocalDateTime.of(2026, 11, 9, 18, 0), true));

        TopicReferenceDigest digest = TopicReferenceDigest.of(comments, flags, Map.of(1L, "Ada", 2L, "Bea", 3L, "Cam"));

        assertEquals(6, digest.open());
        assertEquals(1, digest.disposed());
        assertEquals(1, digest.closed());
        assertEquals(4, digest.flagCount());
        assertEquals(LocalDateTime.of(2026, 11, 9, 16, 0), digest.lastActivity());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), digest.openThreads().stream().map(OpenThread::id).toList());
        assertEquals(1, digest.openThreads().get(0).replies());
        assertEquals("Ada", digest.openThreads().get(0).author());
        assertEquals(3, digest.flagNotes().size());
        assertEquals("a-one", digest.firstAnchor());
        assertFalse(digest.flagNotes().stream().anyMatch(note -> "flag-four".equals(note.note())));
        assertFalse(digest.flagNotes().stream().anyMatch(note -> "flag-cleared".equals(note.note())));

        var guide = TopicReferenceSectionData.present("/hub", 186L, "guide-hosted", "Hosted guide", "HUB", false,
                List.of(), List.of(), digest);
        assertEquals(6, guide.open().value());
        assertEquals(1, guide.disposed().value());
        assertEquals(1, guide.closed().value());
        assertEquals(4, guide.flags().value());
        assertEquals(5, guide.needsAttention().size());
        assertEquals(6, guide.openTotal());
        assertEquals("open-one", guide.needsAttention().get(0).excerpt());
        assertEquals("Ada · Nov 9, 2026 · 1 reply", guide.needsAttention().get(0).meta());
        assertTrue(guide.needsAttention().stream().map(Attention::excerpt).noneMatch(text -> text.contains("open-six")));
        assertEquals(3, guide.flagged().size());
        assertEquals(4, guide.flagTotal());
        assertEquals("flag-one", guide.flagged().get(0).note());
        assertEquals("a-one · Nov 9, 2026", guide.flagged().get(0).meta());
        assertTrue(guide.flagged().stream().map(FlagLine::note).noneMatch(text -> text.contains("flag-four")));
        assertEquals("Last activity Nov 9, 2026", guide.activity());
        assertEquals("/hub/es/topic-guide-comments/186?document=guide-hosted&show=OPEN", guide.open().href());
        assertTrue(guide.needsAttention().stream().map(Attention::excerpt)
                .noneMatch(text -> text.contains("SHOULD-NOT-COUNT")));
    }

    @Test
    void excerptsStopNearOneHundredCharacters() {
        String text = "a".repeat(140);
        assertEquals(100, TopicReferenceSectionData.excerpt(text).length());
        assertTrue(TopicReferenceSectionData.excerpt(text).endsWith("..."));
        assertEquals("short note", TopicReferenceSectionData.excerpt("  short   note  "));
    }

    private static CommentRow comment(Long id, Long parentId, String state, String text, long userId, LocalDateTime at) {
        CommentRow row = new CommentRow();
        row.id = id;
        row.parentId = parentId;
        row.state = state;
        row.text = text;
        row.userId = userId;
        row.updatedAt = at;
        row.createdAt = at;
        return row;
    }

    private static CommentRow closed(long id, LocalDateTime at) {
        CommentRow row = comment(id, null, "CLOSED", "closed-one", 1L, at);
        row.closedAt = at;
        return row;
    }

    private static CommentRow withdrawn(long id, String text) {
        CommentRow row = comment(id, null, "WITHDRAWN", text, 1L, LocalDateTime.of(2026, 11, 9, 15, 0));
        row.withdrawnAt = row.updatedAt;
        return row;
    }

    private static CommentRow withdrawnOpen(long id, String text) {
        CommentRow row = comment(id, null, "OPEN", text, 1L, LocalDateTime.of(2026, 11, 9, 15, 0));
        row.withdrawnAt = row.updatedAt;
        return row;
    }

    private static CommentRow deleted(long id, String text) {
        CommentRow row = comment(id, null, "OPEN", text, 1L, LocalDateTime.of(2026, 11, 9, 17, 0));
        row.deletedAt = row.updatedAt;
        return row;
    }

    private static CommentRow disposedButClosed(long id) {
        CommentRow row = comment(id, null, "DISPOSED", "DISPOSED-CLOSED-SHOULD-NOT-COUNT", 1L,
                LocalDateTime.of(2026, 1, 1, 0, 0));
        row.closedAt = row.updatedAt;
        return row;
    }

    private static FlagRow flag(long id, String anchor, String note, LocalDateTime at, boolean cleared) {
        FlagRow row = new FlagRow();
        row.id = id;
        row.anchor = anchor;
        row.note = note;
        row.createdAt = at;
        row.updatedAt = at;
        if (cleared) {
            row.clearedAt = at;
        }
        return row;
    }
}
