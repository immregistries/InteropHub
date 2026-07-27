package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.airahub.interophub.model.EsLiveVote;
import org.airahub.interophub.model.EsLiveVoteResponse;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.EsRecordedOutcome;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.EsTopicNoteEditorHistory;
import org.airahub.interophub.model.EsTopicNoteRevision;
import org.airahub.interophub.model.LiveVoteResponseType;
import org.airahub.interophub.model.LiveVoteResult;
import org.airahub.interophub.model.LiveVoteStatus;
import org.airahub.interophub.model.MeetingCloseMethod;
import org.airahub.interophub.model.TopicNoteFinalizationMethod;
import org.airahub.interophub.model.TopicNoteStatus;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

class MeetingFoundationTest {

    @Test
    void entityMappingsExposeExpectedTablesAndColumns() throws Exception {
        assertEquals("es_meeting", tableName(EsMeeting.class));
        assertEquals("close_method", columnName(EsMeeting.class, "closeMethod"));
        assertEquals("es_topic_note", tableName(org.airahub.interophub.model.EsTopicNote.class));
        assertEquals("finalization_method",
                columnName(org.airahub.interophub.model.EsTopicNote.class, "finalizationMethod"));
        assertEquals("es_live_vote", tableName(EsLiveVote.class));
        assertEquals("result", columnName(EsLiveVote.class, "result"));
        assertEquals("es_live_vote_response", tableName(EsLiveVoteResponse.class));
        assertEquals("response", columnName(EsLiveVoteResponse.class, "response"));
        assertTrue(EnumSet.allOf(EsMeeting.MeetingStatus.class).contains(EsMeeting.MeetingStatus.IN_SESSION));
        assertTrue(EnumSet.allOf(EsMeeting.MeetingStatus.class).contains(EsMeeting.MeetingStatus.CLOSED));
        assertEquals(EnumSet.of(EsMeetingCommunication.ExpectedMeetingStatus.CLOSED,
                EsMeetingCommunication.ExpectedMeetingStatus.IN_SESSION),
                EnumSet.of(EsMeetingCommunication.ExpectedMeetingStatus.CLOSED,
                        EsMeetingCommunication.ExpectedMeetingStatus.IN_SESSION));
    }

    @Test
    void immutabilityGuardBlocksClosedAndFinalizedStates() {
        MeetingImmutabilityGuard guard = new MeetingImmutabilityGuard();

        EsMeeting meeting = new EsMeeting();
        meeting.setStatus(EsMeeting.MeetingStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> guard.ensureMeetingMutable(meeting));

        EsTopicNote note = new EsTopicNote();
        note.setStatus(TopicNoteStatus.FINALIZED);
        assertThrows(IllegalStateException.class, () -> guard.ensureNoteMutable(note));

        EsLiveVote vote = new EsLiveVote();
        vote.setStatus(LiveVoteStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> guard.ensureVoteMutable(vote, null, null));
    }

    @Test
    void documentSupportBuildsAndExtractsText() {
        TopicNoteDocumentSupport support = new TopicNoteDocumentSupport();

        String documentJson = support.buildInitialDocument("Agenda Item");

        assertFalse(support.isEmptyDocument(documentJson));
        assertEquals("- Agenda Item", support.extractPlainText(documentJson));
        assertEquals("", support.extractPlainText(support.buildInitialDocument(null)));
    }

    @Test
    void documentSupportFindsAnchorsWithDisplayOrder() {
        TopicNoteDocumentSupport support = new TopicNoteDocumentSupport();
        String documentJson = tiptapTwoItemDoc(
                "First",
                "11111111-1111-4111-8111-111111111111",
                "Second",
                "22222222-2222-4222-8222-222222222222");

        List<TopicNoteDocumentSupport.ListItemAnchor> anchors = support.listListItemAnchors(documentJson);

        assertEquals(2, anchors.size());
        assertEquals("11111111-1111-4111-8111-111111111111", anchors.get(0).sourceNodeId());
        assertEquals(1, anchors.get(0).displayOrder());
        assertEquals("Second", anchors.get(1).sourceText());
    }

    @Test
    void takeOverEditingReplacesEditorAndRecordsHistory() {
        FakeSessionSupport support = new FakeSessionSupport();

        EsMeeting meeting = new EsMeeting();
        meeting.setEsMeetingId(22L);
        meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
        support.putEntity(EsMeeting.class, 22L, meeting);

        EsTopicNote note = new EsTopicNote();
        note.setEsTopicNoteId(11L);
        note.setEsMeetingId(22L);
        note.setStatus(TopicNoteStatus.OPEN);
        note.setRevisionNo(4L);
        note.setDocumentJson("{\"type\":\"doc\"}");
        note.setDocumentText("Original text");
        note.setActiveEditorUserId(7L);
        note.setActiveEditorVersion(2L);
        support.putEntity(EsTopicNote.class, 11L, note);

        TopicNoteService service = new TopicNoteService(new MeetingAuthorizationService() {
            @Override
            public boolean canEditTopicNote(Long userId, EsTopicNote candidate) {
                return true;
            }
        });

        TopicNoteService.TopicNoteEditorState updated = service.takeOverEditing(support.session(), 11L, 99L);

        assertEquals(11L, updated.noteId());
        assertTrue(updated.tookOver());
        assertEquals(99L, note.getActiveEditorUserId());
        assertEquals(3L, note.getActiveEditorVersion());
        assertEquals(0L, support.persisted.stream().filter(EsTopicNoteRevision.class::isInstance).count());
        assertEquals(1L, support.persisted.stream().filter(EsTopicNoteEditorHistory.class::isInstance).count());

        EsTopicNoteEditorHistory history = support.persisted.stream()
                .filter(EsTopicNoteEditorHistory.class::isInstance)
                .map(EsTopicNoteEditorHistory.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(7L, history.getPreviousEditorUserId());
        assertEquals(99L, history.getNewEditorUserId());
    }

    @Test
    void takeOverBySameEditorIsIdempotent() {
        FakeSessionSupport support = new FakeSessionSupport();

        EsMeeting meeting = new EsMeeting();
        meeting.setEsMeetingId(22L);
        meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
        support.putEntity(EsMeeting.class, 22L, meeting);

        EsTopicNote note = new EsTopicNote();
        note.setEsTopicNoteId(11L);
        note.setEsMeetingId(22L);
        note.setStatus(TopicNoteStatus.OPEN);
        note.setActiveEditorUserId(99L);
        note.setActiveEditorVersion(4L);
        support.putEntity(EsTopicNote.class, 11L, note);

        TopicNoteService service = new TopicNoteService(new MeetingAuthorizationService() {
            @Override
            public boolean canEditTopicNote(Long userId, EsTopicNote candidate) {
                return true;
            }
        });

        TopicNoteService.TopicNoteEditorState state = service.takeOverEditing(support.session(), 11L, 99L);

        assertEquals(99L, state.activeEditorUserId());
        assertEquals(4L, state.editorVersion());
        assertFalse(state.tookOver());
        assertEquals(0L, support.persisted.stream().filter(EsTopicNoteEditorHistory.class::isInstance).count());
    }

    @Test
    void startEditingCreatesProvisionalNoteWithoutRevisionSnapshot() {
        FakeSessionSupport support = new FakeSessionSupport();

        EsMeeting meeting = new EsMeeting();
        meeting.setEsMeetingId(71L);
        meeting.setEsTopicMeetingId(91L);
        meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
        support.putEntity(EsMeeting.class, 71L, meeting);

        EsMeetingAgendaItem item = new EsMeetingAgendaItem();
        item.setEsMeetingAgendaItemId(81L);
        item.setEsMeetingId(71L);
        item.setTitle("Agenda Topic");
        support.putEntity(EsMeetingAgendaItem.class, 81L, item);

        EsTopicMeeting topicMeeting = new EsTopicMeeting();
        topicMeeting.setEsTopicMeetingId(91L);
        topicMeeting.setEsTopicId(501L);
        topicMeeting.setMeetingName("Series");
        support.putEntity(EsTopicMeeting.class, 91L, topicMeeting);

        support.whenQueryContains("from EsTopicNote n where n.esMeetingAgendaItemId = :agendaItemId", Optional.empty());

        TopicNoteService service = new TopicNoteService(new MeetingAuthorizationService() {
            @Override
            public boolean canEditTopicNote(Long userId, EsTopicNote candidate) {
                return true;
            }
        });

        TopicNoteService.TopicNoteEditorState state = service.startMeetingTopicNoteEditing(support.session(), 71L, 81L,
                99L);

        assertTrue(state.created());
        assertEquals(0L, state.revision());
        assertEquals(1L, state.editorVersion());
        assertEquals(99L, state.activeEditorUserId());
        assertEquals(1L, support.persisted.stream().filter(EsTopicNote.class::isInstance).count());
        assertEquals(1L, support.persisted.stream().filter(EsTopicNoteEditorHistory.class::isInstance).count());
        assertEquals(0L, support.persisted.stream().filter(EsTopicNoteRevision.class::isInstance).count());
    }

    @Test
    void saveRejectsEditorAndRevisionConflictsWithDistinctCodes() {
        FakeSessionSupport support = new FakeSessionSupport();

        EsMeeting meeting = new EsMeeting();
        meeting.setEsMeetingId(22L);
        meeting.setEsTopicMeetingId(33L);
        meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
        support.putEntity(EsMeeting.class, 22L, meeting);

        EsMeetingAgendaItem item = new EsMeetingAgendaItem();
        item.setEsMeetingAgendaItemId(44L);
        item.setEsMeetingId(22L);
        item.setEsTopicId(55L);
        item.setTitle("Agenda item");
        support.putEntity(EsMeetingAgendaItem.class, 44L, item);

        EsTopicMeeting topicMeeting = new EsTopicMeeting();
        topicMeeting.setEsTopicMeetingId(33L);
        topicMeeting.setEsTopicId(55L);
        topicMeeting.setMeetingName("Series");
        support.putEntity(EsTopicMeeting.class, 33L, topicMeeting);

        EsTopicNote note = new EsTopicNote();
        note.setEsTopicNoteId(99L);
        note.setEsMeetingId(22L);
        note.setEsMeetingAgendaItemId(44L);
        note.setStatus(TopicNoteStatus.OPEN);
        note.setRevisionNo(3L);
        note.setActiveEditorUserId(100L);
        note.setActiveEditorVersion(7L);
        note.setDocumentJson(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"attrs\":{\"nodeId\":\"11111111-1111-4111-8111-111111111111\"},\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Saved\"}]}]}]}]}");
        support.whenQueryContains("from EsTopicNote n where n.esMeetingAgendaItemId = :agendaItemId",
                Optional.of(note));

        TopicNoteService service = new TopicNoteService(new MeetingAuthorizationService() {
            @Override
            public boolean canEditTopicNote(Long userId, EsTopicNote candidate) {
                return true;
            }
        });

        TopicNoteConflictException editorConflict = assertThrows(TopicNoteConflictException.class,
                () -> service.saveMeetingTopicNote(support.session(), 22L, 44L, 99L, 3L, 7L,
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"attrs\":{\"nodeId\":\"22222222-2222-4222-8222-222222222222\"},\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Try save\"}]}]}]}]}"));
        assertEquals("NOTE_EDITOR_CHANGED", editorConflict.getErrorCode());

        note.setActiveEditorUserId(99L);

        TopicNoteConflictException revisionConflict = assertThrows(TopicNoteConflictException.class,
                () -> service.saveMeetingTopicNote(support.session(), 22L, 44L, 99L, 2L, 7L,
                        "{\"type\":\"doc\",\"content\":[{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"attrs\":{\"nodeId\":\"33333333-3333-4333-8333-333333333333\"},\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Try save\"}]}]}]}]}"));
        assertEquals("NOTE_REVISION_CONFLICT", revisionConflict.getErrorCode());
    }

    @Test
    void manualSaveCreatesSnapshotEvenWhenAutosaveWindowIsRecent() {
        FakeSessionSupport support = new FakeSessionSupport();

        EsMeeting meeting = new EsMeeting();
        meeting.setEsMeetingId(22L);
        meeting.setEsTopicMeetingId(33L);
        meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
        support.putEntity(EsMeeting.class, 22L, meeting);

        EsMeetingAgendaItem item = new EsMeetingAgendaItem();
        item.setEsMeetingAgendaItemId(44L);
        item.setEsMeetingId(22L);
        item.setEsTopicId(55L);
        item.setTitle("Agenda item");
        support.putEntity(EsMeetingAgendaItem.class, 44L, item);

        EsTopicMeeting topicMeeting = new EsTopicMeeting();
        topicMeeting.setEsTopicMeetingId(33L);
        topicMeeting.setEsTopicId(55L);
        topicMeeting.setMeetingName("Series");
        support.putEntity(EsTopicMeeting.class, 33L, topicMeeting);

        EsTopicNote note = new EsTopicNote();
        note.setEsTopicNoteId(99L);
        note.setEsMeetingId(22L);
        note.setEsMeetingAgendaItemId(44L);
        note.setStatus(TopicNoteStatus.OPEN);
        note.setRevisionNo(3L);
        note.setActiveEditorUserId(99L);
        note.setActiveEditorVersion(7L);
        note.setDocumentJson(tiptapDoc("Saved", "11111111-1111-4111-8111-111111111111"));
        support.whenQueryContains("from EsTopicNote n where n.esMeetingAgendaItemId = :agendaItemId",
                Optional.of(note));

        EsTopicNoteRevision latestRevision = new EsTopicNoteRevision();
        latestRevision.setEsTopicNoteId(99L);
        latestRevision.setRevisionNo(3L);
        latestRevision.setDocumentJson(tiptapDoc("Older", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"));
        latestRevision.setSavedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        support.whenQueryContains("from EsTopicNoteRevision r where r.esTopicNoteId = :noteId",
                Optional.of(latestRevision));

        TopicNoteService service = new TopicNoteService(new MeetingAuthorizationService() {
            @Override
            public boolean canEditTopicNote(Long userId, EsTopicNote candidate) {
                return true;
            }
        });

        TopicNoteService.SavedMeetingTopicNote saved = service.saveMeetingTopicNote(
                support.session(),
                22L,
                44L,
                99L,
                3L,
                7L,
                tiptapDoc("Manual save", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                TopicNoteService.SaveMode.MANUAL);

        assertEquals(4L, saved.revision());
        assertEquals(1L, support.persisted.stream().filter(EsTopicNoteRevision.class::isInstance).count());
    }

    @Test
    void autosaveSkipsSnapshotWhenLatestSnapshotIsRecent() {
        FakeSessionSupport support = new FakeSessionSupport();

        EsMeeting meeting = new EsMeeting();
        meeting.setEsMeetingId(22L);
        meeting.setEsTopicMeetingId(33L);
        meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
        support.putEntity(EsMeeting.class, 22L, meeting);

        EsMeetingAgendaItem item = new EsMeetingAgendaItem();
        item.setEsMeetingAgendaItemId(44L);
        item.setEsMeetingId(22L);
        item.setEsTopicId(55L);
        item.setTitle("Agenda item");
        support.putEntity(EsMeetingAgendaItem.class, 44L, item);

        EsTopicMeeting topicMeeting = new EsTopicMeeting();
        topicMeeting.setEsTopicMeetingId(33L);
        topicMeeting.setEsTopicId(55L);
        topicMeeting.setMeetingName("Series");
        support.putEntity(EsTopicMeeting.class, 33L, topicMeeting);

        EsTopicNote note = new EsTopicNote();
        note.setEsTopicNoteId(99L);
        note.setEsMeetingId(22L);
        note.setEsMeetingAgendaItemId(44L);
        note.setStatus(TopicNoteStatus.OPEN);
        note.setRevisionNo(3L);
        note.setActiveEditorUserId(99L);
        note.setActiveEditorVersion(7L);
        note.setDocumentJson(tiptapDoc("Saved", "11111111-1111-4111-8111-111111111111"));
        support.whenQueryContains("from EsTopicNote n where n.esMeetingAgendaItemId = :agendaItemId",
                Optional.of(note));

        EsTopicNoteRevision latestRevision = new EsTopicNoteRevision();
        latestRevision.setEsTopicNoteId(99L);
        latestRevision.setRevisionNo(3L);
        latestRevision.setDocumentJson(tiptapDoc("Older", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"));
        latestRevision.setSavedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        support.whenQueryContains("from EsTopicNoteRevision r where r.esTopicNoteId = :noteId",
                Optional.of(latestRevision));

        TopicNoteService service = new TopicNoteService(new MeetingAuthorizationService() {
            @Override
            public boolean canEditTopicNote(Long userId, EsTopicNote candidate) {
                return true;
            }
        });

        TopicNoteService.SavedMeetingTopicNote saved = service.saveMeetingTopicNote(
                support.session(),
                22L,
                44L,
                99L,
                3L,
                7L,
                tiptapDoc("Autosave", "cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
                TopicNoteService.SaveMode.AUTOSAVE);

        assertEquals(4L, saved.revision());
        assertEquals(0L, support.persisted.stream().filter(EsTopicNoteRevision.class::isInstance).count());
    }

    @Test
    void saveRejectsWhenRecordedOutcomeAnchorIsMissing() {
        FakeSessionSupport support = new FakeSessionSupport();

        EsMeeting meeting = new EsMeeting();
        meeting.setEsMeetingId(22L);
        meeting.setEsTopicMeetingId(33L);
        meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
        support.putEntity(EsMeeting.class, 22L, meeting);

        EsMeetingAgendaItem item = new EsMeetingAgendaItem();
        item.setEsMeetingAgendaItemId(44L);
        item.setEsMeetingId(22L);
        item.setEsTopicId(55L);
        item.setTitle("Agenda item");
        support.putEntity(EsMeetingAgendaItem.class, 44L, item);

        EsTopicMeeting topicMeeting = new EsTopicMeeting();
        topicMeeting.setEsTopicMeetingId(33L);
        topicMeeting.setEsTopicId(55L);
        topicMeeting.setMeetingName("Series");
        support.putEntity(EsTopicMeeting.class, 33L, topicMeeting);

        EsTopicNote note = new EsTopicNote();
        note.setEsTopicNoteId(99L);
        note.setEsMeetingId(22L);
        note.setEsMeetingAgendaItemId(44L);
        note.setStatus(TopicNoteStatus.OPEN);
        note.setRevisionNo(3L);
        note.setActiveEditorUserId(99L);
        note.setActiveEditorVersion(7L);
        note.setDocumentJson(tiptapDoc("Saved", "11111111-1111-4111-8111-111111111111"));
        support.whenQueryContains("from EsTopicNote n where n.esMeetingAgendaItemId = :agendaItemId",
                Optional.of(note));

        EsRecordedOutcome outcome = new EsRecordedOutcome();
        outcome.setEsRecordedOutcomeId(200L);
        outcome.setEsTopicNoteId(99L);
        outcome.setSourceNodeId("11111111-1111-4111-8111-111111111111");
        outcome.setDisplayOrder(1);
        support.whenQueryContains("from EsRecordedOutcome o where o.esTopicNoteId = :noteId",
                List.of(outcome));

        TopicNoteService service = new TopicNoteService(new MeetingAuthorizationService() {
            @Override
            public boolean canEditTopicNote(Long userId, EsTopicNote candidate) {
                return true;
            }
        });

        TopicNoteConflictException conflict = assertThrows(TopicNoteConflictException.class,
                () -> service.saveMeetingTopicNote(
                        support.session(),
                        22L,
                        44L,
                        99L,
                        3L,
                        7L,
                        tiptapDoc("Moved", "22222222-2222-4222-8222-222222222222"),
                        TopicNoteService.SaveMode.MANUAL));

        assertEquals("RECORDED_OUTCOME_SOURCE_MISSING", conflict.getErrorCode());
    }

    @Test
    void finalizeOpenMeetingNotesFinalizesEveryOpenNote() throws Exception {
        FakeSessionSupport support = new FakeSessionSupport();
        EsTopicNote noteOne = openNote(1L, 55L, 101L);
        EsTopicNote noteTwo = openNote(2L, 55L, 102L);
        support.whenQueryContains("from EsTopicNote n where n.esMeetingId = :meetingId and n.status = :status",
                List.of(noteOne, noteTwo));

        MeetingLifecycleService service = new MeetingLifecycleService();
        LocalDateTime deadline = LocalDateTime.of(2026, 1, 1, 12, 0);

        invokePrivate(service, "finalizeOpenMeetingNotes",
                new Class<?>[] { Session.class, Long.class, LocalDateTime.class, Long.class, MeetingCloseMethod.class },
                support.session(), 55L, deadline, 900L, MeetingCloseMethod.AUTOMATIC);

        assertEquals(TopicNoteStatus.FINALIZED, noteOne.getStatus());
        assertEquals(TopicNoteStatus.FINALIZED, noteTwo.getStatus());
        assertEquals(TopicNoteFinalizationMethod.AUTOMATIC, noteOne.getFinalizationMethod());
        assertEquals(deadline, noteOne.getFinalizeAt());
        assertEquals(deadline, noteTwo.getFinalizeAt());
        assertEquals(2, support.merged.size());
    }

    @Test
    void closeOpenVotesAggregatesElectronicResponsesAndClosesVotes() throws Exception {
        FakeSessionSupport support = new FakeSessionSupport();

        EsLiveVote vote = new EsLiveVote();
        vote.setEsLiveVoteId(88L);
        vote.setEsRecordedOutcomeId(144L);
        vote.setStatus(LiveVoteStatus.OPEN);
        vote.setElectronicForCount(0);
        vote.setElectronicAgainstCount(0);
        vote.setElectronicAbstainCount(0);
        vote.setManualForCount(0);
        vote.setManualAgainstCount(0);
        vote.setManualAbstainCount(0);
        support.whenQueryContains("from EsLiveVote v where v.status = :status and v.esRecordedOutcomeId in (",
                List.of(vote));
        support.whenQueryContains("select r.response, count(r.esLiveVoteResponseId) from EsLiveVoteResponse r",
                List.of(
                        new Object[] { LiveVoteResponseType.FOR, 3L },
                        new Object[] { LiveVoteResponseType.AGAINST, 2L },
                        new Object[] { LiveVoteResponseType.ABSTAIN, 1L }));

        MeetingLifecycleService service = new MeetingLifecycleService();

        invokePrivate(service, "closeOpenVotes",
                new Class<?>[] { Session.class, Long.class, Long.class },
                support.session(), 55L, 900L);

        assertEquals(LiveVoteStatus.CLOSED, vote.getStatus());
        assertEquals(Integer.valueOf(3), vote.getElectronicForCount());
        assertEquals(Integer.valueOf(2), vote.getElectronicAgainstCount());
        assertEquals(Integer.valueOf(1), vote.getElectronicAbstainCount());
        assertEquals(Integer.valueOf(3), vote.getFinalForCount());
        assertEquals(Integer.valueOf(2), vote.getFinalAgainstCount());
        assertEquals(Integer.valueOf(1), vote.getFinalAbstainCount());
        assertEquals(LiveVoteResult.NO_RESULT, vote.getResult());
        assertNotNull(vote.getClosedAt());
        assertTrue(support.executedMutations.stream().anyMatch(sql -> sql.contains("delete from EsLiveVoteResponse")));
    }

    private static EsTopicNote openNote(Long id, Long meetingId, Long editorUserId) {
        EsTopicNote note = new EsTopicNote();
        note.setEsTopicNoteId(id);
        note.setEsMeetingId(meetingId);
        note.setStatus(TopicNoteStatus.OPEN);
        note.setRevisionNo(1L);
        note.setDocumentJson("{\"type\":\"doc\"}");
        note.setDocumentText("Text " + id);
        note.setActiveEditorUserId(editorUserId);
        note.setActiveEditorVersion(1L);
        return note;
    }

    private static String tiptapDoc(String text, String nodeId) {
        return "{\"type\":\"doc\",\"content\":[{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"attrs\":{\"nodeId\":\""
                + nodeId
                + "\"},\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                + text
                + "\"}]}]}]}]}";
    }

    private static String tiptapTwoItemDoc(String firstText, String firstNodeId, String secondText,
            String secondNodeId) {
        return "{\"type\":\"doc\",\"content\":[{\"type\":\"bulletList\",\"content\":["
                + "{\"type\":\"listItem\",\"attrs\":{\"nodeId\":\"" + firstNodeId
                + "\"},\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                + firstText + "\"}]}]},"
                + "{\"type\":\"listItem\",\"attrs\":{\"nodeId\":\"" + secondNodeId
                + "\"},\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\""
                + secondText + "\"}]}]}]}]}";
    }

    private static String tableName(Class<?> type) {
        Table table = type.getAnnotation(Table.class);
        return table == null ? null : table.name();
    }

    private static String columnName(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);
        return column == null ? null : column.name();
    }

    private static <T> T invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        T value = (T) method.invoke(target, args);
        return value;
    }

    private static final class FakeSessionSupport implements InvocationHandler {
        private final Map<Class<?>, Map<Object, Object>> entities = new HashMap<>();
        private final List<Object> merged = new ArrayList<>();
        private final List<Object> persisted = new ArrayList<>();
        private final List<String> executedMutations = new ArrayList<>();
        private final List<QueryResult> queryResults = new ArrayList<>();
        private final Transaction transaction = transactionProxy();
        private long nextNoteId = 1000L;

        Session session() {
            return (Session) Proxy.newProxyInstance(
                    Session.class.getClassLoader(),
                    new Class<?>[] { Session.class },
                    this);
        }

        void putEntity(Class<?> type, Object id, Object entity) {
            entities.computeIfAbsent(type, ignored -> new HashMap<>()).put(id, entity);
        }

        void whenQueryContains(String snippet, Object payload) {
            queryResults.add(new QueryResult(snippet, payload));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("beginTransaction".equals(name)) {
                return transaction;
            }
            if ("get".equals(name)) {
                Class<?> type = (Class<?>) args[0];
                Object id = args[1];
                return entities.getOrDefault(type, Map.of()).get(id);
            }
            if ("find".equals(name)) {
                Class<?> type = (Class<?>) args[0];
                Object id = args[1];
                return entities.getOrDefault(type, Map.of()).get(id);
            }
            if ("createQuery".equals(name)) {
                String hql = (String) args[0];
                return queryProxy(hql, resolvePayload(hql));
            }
            if ("createMutationQuery".equals(name)) {
                String hql = (String) args[0];
                return mutationProxy(hql);
            }
            if ("merge".equals(name)) {
                merged.add(args[0]);
                return args[0];
            }
            if ("persist".equals(name)) {
                if (args != null && args.length > 0 && args[0] instanceof EsTopicNote note) {
                    if (note.getEsTopicNoteId() == null) {
                        note.setEsTopicNoteId(nextNoteId++);
                    }
                    putEntity(EsTopicNote.class, note.getEsTopicNoteId(), note);
                }
                persisted.add(args[0]);
                return null;
            }
            if ("flush".equals(name)) {
                return null;
            }
            if ("close".equals(name)) {
                return null;
            }
            return defaultValue(method.getReturnType());
        }

        private Object resolvePayload(String hql) {
            for (QueryResult result : queryResults) {
                if (hql.contains(result.snippet)) {
                    return result.payload;
                }
            }
            return List.of();
        }

        private Object queryProxy(String hql, Object payload) {
            InvocationHandler handler = new QueryHandler(hql, payload, executedMutations);
            return Proxy.newProxyInstance(
                    Query.class.getClassLoader(),
                    new Class<?>[] { Query.class, MutationQuery.class },
                    handler);
        }

        private Object mutationProxy(String hql) {
            executedMutations.add(hql);
            InvocationHandler handler = new MutationQueryHandler(hql, executedMutations);
            return Proxy.newProxyInstance(
                    MutationQuery.class.getClassLoader(),
                    new Class<?>[] { MutationQuery.class, Query.class },
                    handler);
        }

        private Transaction transactionProxy() {
            return (Transaction) Proxy.newProxyInstance(
                    Transaction.class.getClassLoader(),
                    new Class<?>[] { Transaction.class },
                    (proxy, method, args) -> defaultValue(method.getReturnType()));
        }
    }

    private static final class QueryResult {
        private final String snippet;
        private final Object payload;

        private QueryResult(String snippet, Object payload) {
            this.snippet = snippet;
            this.payload = payload;
        }
    }

    private static final class QueryHandler implements InvocationHandler {
        private final String hql;
        private final Object payload;

        private QueryHandler(String hql, Object payload, List<String> executedMutations) {
            this.hql = hql;
            this.payload = payload;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("setParameter".equals(name) || "setMaxResults".equals(name) || "setLockMode".equals(name)) {
                return proxy;
            }
            if ("getResultList".equals(name)) {
                if (payload instanceof List<?> list) {
                    return list;
                }
                if (payload instanceof Optional<?> optional) {
                    return optional.map(List::of).orElseGet(List::of);
                }
                return List.of();
            }
            if ("uniqueResultOptional".equals(name)) {
                if (payload instanceof Optional<?> optional) {
                    return optional;
                }
                if (payload instanceof List<?> list) {
                    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
                }
                return Optional.ofNullable(payload);
            }
            if ("toString".equals(name)) {
                return hql;
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static final class MutationQueryHandler implements InvocationHandler {
        private final String hql;
        private final List<String> executedMutations;

        private MutationQueryHandler(String hql, List<String> executedMutations) {
            this.hql = hql;
            this.executedMutations = executedMutations;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("setParameter".equals(name)) {
                return proxy;
            }
            if ("executeUpdate".equals(name)) {
                executedMutations.add(hql);
                return 1;
            }
            if ("toString".equals(name)) {
                return hql;
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}