package org.airahub.interophub.service;

import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.EsTopicNoteEditorHistory;
import org.airahub.interophub.model.EsTopicNoteRevision;
import org.airahub.interophub.model.TopicNoteFinalizationMethod;
import org.airahub.interophub.model.TopicNoteStatus;

public class TopicNoteService {

    private static final String REVISION_CONFLICT_CODE = "NOTE_REVISION_CONFLICT";
    private static final String EDITOR_CHANGED_CODE = "NOTE_EDITOR_CHANGED";
    private static final String REVISION_CONFLICT_MESSAGE = "These notes changed after you opened them. Reload the saved notes before saving.";

    private final EsTopicDao topicDao;
    private final MeetingAuthorizationService authorizationService;
    private final MeetingImmutabilityGuard immutabilityGuard;
    private final TopicNoteDocumentSupport documentSupport;
    private final TopicNoteEventPublisher eventPublisher;
    private final RecordedOutcomeService recordedOutcomeService;

    public TopicNoteService() {
        this.topicDao = new EsTopicDao();
        this.authorizationService = new MeetingAuthorizationService();
        this.immutabilityGuard = new MeetingImmutabilityGuard();
        this.documentSupport = new TopicNoteDocumentSupport();
        this.eventPublisher = new TopicNoteEventPublisher();
        this.recordedOutcomeService = new RecordedOutcomeService();
    }

    TopicNoteService(MeetingAuthorizationService authorizationService) {
        this.topicDao = new EsTopicDao();
        this.authorizationService = authorizationService;
        this.immutabilityGuard = new MeetingImmutabilityGuard();
        this.documentSupport = new TopicNoteDocumentSupport();
        this.eventPublisher = new TopicNoteEventPublisher();
        this.recordedOutcomeService = new RecordedOutcomeService();
    }

    public SavedMeetingTopicNote saveMeetingTopicNote(Long meetingId, Long agendaItemId, Long actingUserId,
            Long expectedRevision, Long expectedEditorVersion, String documentJson) {
        return saveMeetingTopicNote(meetingId, agendaItemId, actingUserId, expectedRevision,
                expectedEditorVersion, documentJson, SaveMode.AUTOSAVE);
    }

    public SavedMeetingTopicNote saveMeetingTopicNote(Long meetingId, Long agendaItemId, Long actingUserId,
            Long expectedRevision, Long expectedEditorVersion, String documentJson, SaveMode saveMode) {
        if (meetingId == null || agendaItemId == null || actingUserId == null
                || expectedRevision == null || expectedEditorVersion == null) {
            throw new IllegalArgumentException(
                    "meetingId, agendaItemId, actingUserId, expectedRevision, and expectedEditorVersion are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return saveMeetingTopicNote(session, meetingId, agendaItemId, actingUserId, expectedRevision,
                    expectedEditorVersion, documentJson, saveMode == null ? SaveMode.AUTOSAVE : saveMode);
        }
    }

    public SavedMeetingTopicNote saveMeetingTopicNote(Long meetingId, Long agendaItemId, Long actingUserId,
            Long expectedRevision, String documentJson) {
        long legacyEditorVersion = expectedRevision != null && expectedRevision.longValue() == 0L ? 0L : -1L;
        return saveMeetingTopicNote(meetingId, agendaItemId, actingUserId, expectedRevision,
                legacyEditorVersion, documentJson);
    }

    SavedMeetingTopicNote saveMeetingTopicNote(org.hibernate.Session session, Long meetingId, Long agendaItemId,
            Long actingUserId, Long expectedRevision, Long expectedEditorVersion, String documentJson,
            SaveMode saveMode) {
        org.hibernate.Transaction tx = session.beginTransaction();
        try {
            EsMeeting meeting = session.find(EsMeeting.class, meetingId, LockModeType.PESSIMISTIC_WRITE);
            if (meeting == null) {
                throw new IllegalArgumentException("Meeting not found: " + meetingId);
            }
            immutabilityGuard.ensureMeetingMutable(meeting);

            EsMeetingAgendaItem agendaItem = session.find(EsMeetingAgendaItem.class, agendaItemId,
                    LockModeType.PESSIMISTIC_WRITE);
            if (agendaItem == null) {
                throw new IllegalArgumentException("Agenda item not found: " + agendaItemId);
            }
            if (!meetingId.equals(agendaItem.getEsMeetingId())) {
                throw new IllegalArgumentException("Agenda item does not belong to the meeting.");
            }

            EsTopicMeeting topicMeeting = session.get(EsTopicMeeting.class, meeting.getEsTopicMeetingId());
            if (topicMeeting == null) {
                throw new IllegalStateException("Meeting series could not be resolved.");
            }
            Long effectiveTopicId = agendaItem.getEsTopicId() != null ? agendaItem.getEsTopicId()
                    : topicMeeting.getEsTopicId();
            if (effectiveTopicId == null) {
                throw new IllegalStateException("Effective topic could not be resolved for the agenda item.");
            }

            EsTopicNote note = session.createQuery(
                    "from EsTopicNote n where n.esMeetingAgendaItemId = :agendaItemId",
                    EsTopicNote.class)
                    .setParameter("agendaItemId", agendaItemId)
                    .setMaxResults(1)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .uniqueResultOptional()
                    .orElse(null);
            if (note == null) {
                throw new IllegalStateException("Start taking notes before saving this topic.");
            }
            if (!meetingId.equals(note.getEsMeetingId()) || !agendaItemId.equals(note.getEsMeetingAgendaItemId())) {
                throw new IllegalArgumentException("Note does not belong to the requested meeting and agenda item.");
            }
            immutabilityGuard.ensureNoteAndMeetingMutable(note, meeting);

            if (!authorizationService.canEditTopicNote(actingUserId, note)) {
                throw new IllegalStateException("User is not authorized to edit the note.");
            }

            long currentEditorVersion = note.getActiveEditorVersion() == null ? 0L : note.getActiveEditorVersion();
            if (!actingUserId.equals(note.getActiveEditorUserId())
                    || expectedEditorVersion.longValue() != currentEditorVersion) {
                throw new TopicNoteConflictException(
                        EDITOR_CHANGED_CODE,
                        "Another editor is currently responsible for these notes.",
                        note.getActiveEditorUserId(),
                        currentEditorVersion);
            }

            if (!expectedRevision.equals(note.getRevisionNo())) {
                throw new TopicNoteConflictException(REVISION_CONFLICT_CODE, REVISION_CONFLICT_MESSAGE,
                        note.getActiveEditorUserId(), currentEditorVersion);
            }

            TopicNoteDocumentSupport.NormalizedTopicNoteDocument normalized = documentSupport
                    .normalizeDocument(documentJson);
            if (normalized.isMeaningfulTextEmpty()) {
                throw new IllegalArgumentException("Empty notes cannot be saved.");
            }

            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

            recordedOutcomeService.validateAndSyncOutcomesForSavedDocument(session, note, normalized,
                    actingUserId, now);
            note.setEsTopicId(effectiveTopicId);
            note.setNoteTitle(resolveNoteTitle(agendaItem, topicMeeting));
            note.setDocumentJson(normalized.json());
            note.setDocumentText(normalized.plainText());
            note.setRevisionNo(note.getRevisionNo() + 1L);
            note.setUpdatedAt(now);
            if (note.getFinalizeAt() == null && meeting.getCloseDueAt() != null) {
                note.setFinalizeAt(meeting.getCloseDueAt());
            }
            session.merge(note);
            if (shouldCreateSnapshot(session, note, actingUserId, now, saveMode)) {
                saveRevisionSnapshot(session, note, actingUserId, now);
            }

            tx.commit();
            eventPublisher.publishNoteUpdated(session, note, actingUserId, meeting.getStatus());
            return new SavedMeetingTopicNote(note.getEsTopicNoteId(), note.getRevisionNo(), now,
                    note.getStatus(), false, false, currentEditorVersion, note.getActiveEditorUserId());
        } catch (RuntimeException ex) {
            tx.rollback();
            throw ex;
        }
    }

    SavedMeetingTopicNote saveMeetingTopicNote(org.hibernate.Session session, Long meetingId, Long agendaItemId,
            Long actingUserId, Long expectedRevision, Long expectedEditorVersion, String documentJson) {
        return saveMeetingTopicNote(session, meetingId, agendaItemId, actingUserId, expectedRevision,
                expectedEditorVersion, documentJson, SaveMode.AUTOSAVE);
    }

    public TopicNoteEditorState startMeetingTopicNoteEditing(Long meetingId, Long agendaItemId, Long actingUserId) {
        if (meetingId == null || agendaItemId == null || actingUserId == null) {
            throw new IllegalArgumentException("meetingId, agendaItemId, and actingUserId are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return startMeetingTopicNoteEditing(session, meetingId, agendaItemId, actingUserId);
        }
    }

    TopicNoteEditorState startMeetingTopicNoteEditing(org.hibernate.Session session, Long meetingId,
            Long agendaItemId, Long actingUserId) {
        org.hibernate.Transaction tx = session.beginTransaction();
        try {
            EsMeeting meeting = session.find(EsMeeting.class, meetingId, LockModeType.PESSIMISTIC_WRITE);
            if (meeting == null) {
                throw new IllegalArgumentException("Meeting not found: " + meetingId);
            }
            immutabilityGuard.ensureMeetingMutable(meeting);

            EsMeetingAgendaItem agendaItem = session.find(EsMeetingAgendaItem.class, agendaItemId,
                    LockModeType.PESSIMISTIC_WRITE);
            if (agendaItem == null) {
                throw new IllegalArgumentException("Agenda item not found: " + agendaItemId);
            }
            if (!meetingId.equals(agendaItem.getEsMeetingId())) {
                throw new IllegalArgumentException("Agenda item does not belong to the meeting.");
            }

            EsTopicMeeting topicMeeting = session.get(EsTopicMeeting.class, meeting.getEsTopicMeetingId());
            if (topicMeeting == null) {
                throw new IllegalStateException("Meeting series could not be resolved.");
            }
            Long effectiveTopicId = agendaItem.getEsTopicId() != null ? agendaItem.getEsTopicId()
                    : topicMeeting.getEsTopicId();
            if (effectiveTopicId == null) {
                throw new IllegalStateException("Effective topic could not be resolved for the agenda item.");
            }

            EsTopicNote existing = session.createQuery(
                    "from EsTopicNote n where n.esMeetingAgendaItemId = :agendaItemId",
                    EsTopicNote.class)
                    .setParameter("agendaItemId", agendaItemId)
                    .setMaxResults(1)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .uniqueResultOptional()
                    .orElse(null);

            EsTopicNote authorizationCandidate = existing != null
                    ? existing
                    : buildShellNote(agendaItem, meeting, effectiveTopicId, actingUserId);
            if (!authorizationService.canEditTopicNote(actingUserId, authorizationCandidate)) {
                throw new IllegalStateException("User is not authorized to edit the note.");
            }

            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            if (existing == null) {
                EsTopicNote created = new EsTopicNote();
                created.setEsTopicId(effectiveTopicId);
                created.setEsMeetingId(meetingId);
                created.setEsMeetingAgendaItemId(agendaItemId);
                created.setNoteTitle(resolveNoteTitle(agendaItem, topicMeeting));
                created.setDocumentJson(documentSupport.buildEmptyBulletDocument());
                created.setDocumentText("");
                created.setRevisionNo(0L);
                created.setStatus(TopicNoteStatus.OPEN);
                created.setActiveEditorUserId(actingUserId);
                created.setActiveEditorStartedAt(now);
                created.setActiveEditorVersion(1L);
                created.setCreatedAt(now);
                created.setCreatedByUserId(actingUserId);
                created.setUpdatedAt(now);
                created.setFinalizeAt(meeting.getCloseDueAt());
                session.persist(created);
                session.flush();
                insertEditorHistory(session, created.getEsTopicNoteId(), null, actingUserId, actingUserId, now);
                tx.commit();
                eventPublisher.publishNoteState(session, created, meeting.getStatus());
                return toEditorState(created, meeting, true, false);
            }

            immutabilityGuard.ensureNoteAndMeetingMutable(existing, meeting);
            if (existing.getStatus() == TopicNoteStatus.FINALIZED) {
                throw new IllegalStateException("Topic note is finalized and immutable.");
            }

            Long previousEditor = existing.getActiveEditorUserId();
            boolean alreadyEditor = actingUserId.equals(previousEditor);
            if (!alreadyEditor) {
                long nextVersion = (existing.getActiveEditorVersion() == null ? 0L : existing.getActiveEditorVersion())
                        + 1L;
                existing.setActiveEditorUserId(actingUserId);
                existing.setActiveEditorStartedAt(now);
                existing.setActiveEditorVersion(nextVersion);
                existing.setUpdatedAt(now);
                session.merge(existing);
                insertEditorHistory(session, existing.getEsTopicNoteId(), previousEditor, actingUserId, actingUserId,
                        now);
            }

            tx.commit();
            if (!alreadyEditor) {
                eventPublisher.publishEditorChanged(session, existing, actingUserId, now, meeting.getStatus());
            }
            return toEditorState(existing, meeting, false, !alreadyEditor);
        } catch (RuntimeException ex) {
            tx.rollback();
            throw ex;
        }
    }

    public TopicNoteEditorState takeOverEditing(Long noteId, Long actingUserId) {
        if (noteId == null || actingUserId == null) {
            throw new IllegalArgumentException("noteId and actingUserId are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return takeOverEditing(session, noteId, actingUserId);
        }
    }

    TopicNoteEditorState takeOverEditing(org.hibernate.Session session, Long noteId, Long actingUserId) {
        org.hibernate.Transaction tx = session.beginTransaction();
        try {
            EsTopicNote note = session.find(EsTopicNote.class, noteId, LockModeType.PESSIMISTIC_WRITE);
            if (note == null) {
                throw new IllegalArgumentException("Note not found: " + noteId);
            }
            EsMeeting meeting = note.getEsMeetingId() != null
                    ? session.find(EsMeeting.class, note.getEsMeetingId(), LockModeType.PESSIMISTIC_READ)
                    : null;
            immutabilityGuard.ensureNoteAndMeetingMutable(note, meeting);
            if (!authorizationService.canEditTopicNote(actingUserId, note)) {
                throw new IllegalStateException("User is not authorized to edit the note.");
            }

            Long previousEditor = note.getActiveEditorUserId();
            if (!actingUserId.equals(previousEditor)) {
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                long nextVersion = (note.getActiveEditorVersion() == null ? 0L : note.getActiveEditorVersion()) + 1L;
                note.setActiveEditorUserId(actingUserId);
                note.setActiveEditorStartedAt(now);
                note.setActiveEditorVersion(nextVersion);
                note.setUpdatedAt(now);
                session.merge(note);
                insertEditorHistory(session, note.getEsTopicNoteId(), previousEditor, actingUserId, actingUserId, now);
            }

            tx.commit();
            if (!actingUserId.equals(previousEditor)) {
                eventPublisher.publishEditorChanged(session, note, actingUserId,
                        note.getActiveEditorStartedAt(), meeting != null ? meeting.getStatus() : null);
            }
            return toEditorState(note, meeting, false, !actingUserId.equals(previousEditor));
        } catch (RuntimeException ex) {
            tx.rollback();
            throw ex;
        }
    }

    public TopicNoteEditorState getEditorState(Long noteId) {
        if (noteId == null) {
            throw new IllegalArgumentException("noteId is required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            EsTopicNote note = session.get(EsTopicNote.class, noteId);
            if (note == null) {
                throw new IllegalArgumentException("Note not found: " + noteId);
            }
            EsMeeting meeting = note.getEsMeetingId() != null ? session.get(EsMeeting.class, note.getEsMeetingId())
                    : null;
            return toEditorState(note, meeting, false, false);
        }
    }

    public TopicNoteContentState getContentState(Long noteId) {
        if (noteId == null) {
            throw new IllegalArgumentException("noteId is required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            EsTopicNote note = session.get(EsTopicNote.class, noteId);
            if (note == null) {
                throw new IllegalArgumentException("Note not found: " + noteId);
            }
            EsMeeting meeting = note.getEsMeetingId() != null ? session.get(EsMeeting.class, note.getEsMeetingId())
                    : null;
            TopicNoteEditorState metadata = toEditorState(note, meeting, false, false);
            return new TopicNoteContentState(metadata, note.getDocumentJson());
        }
    }

    public EsTopicNote createAdHocTopicNote(Long topicId, String title, Long creatorUserId) {
        if (topicId == null || creatorUserId == null) {
            throw new IllegalArgumentException("topicId and creatorUserId are required.");
        }
        if (!authorizationService.canCreateAdHocTopicNote(creatorUserId, topicId)) {
            throw new IllegalStateException("User is not authorized to create a topic note.");
        }
        EsTopic topic = topicDao.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        EsTopicNote note = new EsTopicNote();
        note.setEsTopicId(topicId);
        note.setNoteTitle(title != null && !title.isBlank() ? title.trim() : topic.getTopicName());
        note.setDocumentJson(documentSupport.buildInitialDocument(note.getNoteTitle()));
        note.setDocumentText(documentSupport.extractPlainText(note.getDocumentJson()));
        note.setRevisionNo(1L);
        note.setStatus(TopicNoteStatus.OPEN);
        note.setActiveEditorUserId(creatorUserId);
        note.setActiveEditorStartedAt(now);
        note.setActiveEditorVersion(1L);
        note.setCreatedAt(now);
        note.setCreatedByUserId(creatorUserId);
        note.setUpdatedAt(now);
        note.setFinalizeAt(now.plusDays(7));

        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                session.persist(note);
                saveRevisionSnapshot(session, note, creatorUserId, now);
                tx.commit();
                return note;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public EsTopicNote createMeetingNote(Long meetingId, Long agendaItemId, Long creatorUserId) {
        if (meetingId == null || agendaItemId == null || creatorUserId == null) {
            throw new IllegalArgumentException("meetingId, agendaItemId, and creatorUserId are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsMeeting meeting = session.get(EsMeeting.class, meetingId);
                if (meeting == null) {
                    throw new IllegalArgumentException("Meeting not found: " + meetingId);
                }
                immutabilityGuard.ensureMeetingMutable(meeting);
                EsMeetingAgendaItem agendaItem = session.get(EsMeetingAgendaItem.class, agendaItemId);
                if (agendaItem == null || !meetingId.equals(agendaItem.getEsMeetingId())) {
                    throw new IllegalArgumentException("Agenda item does not belong to the meeting.");
                }
                EsTopicMeeting topicMeeting = session.get(EsTopicMeeting.class, meeting.getEsTopicMeetingId());
                if (topicMeeting == null) {
                    throw new IllegalStateException("Meeting series could not be resolved.");
                }
                Long effectiveTopicId = agendaItem.getEsTopicId() != null ? agendaItem.getEsTopicId()
                        : topicMeeting.getEsTopicId();
                if (effectiveTopicId == null) {
                    throw new IllegalStateException("Effective topic could not be resolved for the agenda item.");
                }
                if (!authorizationService.canEditTopicNote(creatorUserId,
                        buildShellNote(agendaItem, meeting, effectiveTopicId, creatorUserId))) {
                    throw new IllegalStateException("User is not authorized to create a meeting note.");
                }

                EsTopicNote existing = session.createQuery(
                        "from EsTopicNote n where n.esMeetingAgendaItemId = :agendaItemId",
                        EsTopicNote.class)
                        .setParameter("agendaItemId", agendaItemId)
                        .setMaxResults(1)
                        .uniqueResultOptional()
                        .orElse(null);
                if (existing != null) {
                    return existing;
                }

                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                EsTopicNote note = new EsTopicNote();
                note.setEsTopicId(effectiveTopicId);
                note.setEsMeetingId(meetingId);
                note.setEsMeetingAgendaItemId(agendaItemId);
                note.setNoteTitle(agendaItem.getTitle());
                note.setDocumentJson(documentSupport.buildInitialDocument(agendaItem.getTitle()));
                note.setDocumentText(documentSupport.extractPlainText(note.getDocumentJson()));
                note.setRevisionNo(1L);
                note.setStatus(TopicNoteStatus.OPEN);
                note.setActiveEditorUserId(creatorUserId);
                note.setActiveEditorStartedAt(now);
                note.setActiveEditorVersion(1L);
                note.setCreatedAt(now);
                note.setCreatedByUserId(creatorUserId);
                note.setUpdatedAt(now);
                session.persist(note);
                saveRevisionSnapshot(session, note, creatorUserId, now);
                tx.commit();
                return note;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public EsTopicNote saveDocument(Long noteId, Long editorUserId, Long editorVersion, Long expectedRevision,
            String documentJson) {
        if (noteId == null || editorUserId == null || editorVersion == null || expectedRevision == null) {
            throw new IllegalArgumentException(
                    "noteId, editorUserId, editorVersion, and expectedRevision are required.");
        }
        if (documentSupport.isEmptyDocument(documentJson)) {
            throw new IllegalArgumentException("Empty Tiptap documents are not saved.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsTopicNote note = session.find(EsTopicNote.class, noteId, LockModeType.PESSIMISTIC_WRITE);
                if (note == null) {
                    throw new IllegalArgumentException("Note not found: " + noteId);
                }
                EsMeeting meeting = note.getEsMeetingId() != null
                        ? session.find(EsMeeting.class, note.getEsMeetingId(), LockModeType.PESSIMISTIC_READ)
                        : null;
                immutabilityGuard.ensureNoteAndMeetingMutable(note, meeting);
                if (!authorizationService.canEditTopicNote(editorUserId, note)) {
                    throw new IllegalStateException("User is not authorized to edit the note.");
                }
                long currentVersion = note.getActiveEditorVersion() == null ? 0L : note.getActiveEditorVersion();
                if (!editorUserId.equals(note.getActiveEditorUserId()) || !editorVersion.equals(currentVersion)) {
                    throw new TopicNoteConflictException(EDITOR_CHANGED_CODE,
                            "Another editor is currently responsible for these notes.",
                            note.getActiveEditorUserId(), currentVersion);
                }
                if (!expectedRevision.equals(note.getRevisionNo())) {
                    throw new TopicNoteConflictException(REVISION_CONFLICT_CODE, REVISION_CONFLICT_MESSAGE,
                            note.getActiveEditorUserId(), currentVersion);
                }

                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                note.setDocumentJson(documentJson);
                note.setDocumentText(documentSupport.extractPlainText(documentJson));
                note.setRevisionNo(note.getRevisionNo() + 1L);
                session.merge(note);
                if (shouldCreateSnapshot(session, note, editorUserId, now, SaveMode.AUTOSAVE)) {
                    saveRevisionSnapshot(session, note, editorUserId, now);
                }

                tx.commit();
                eventPublisher.publishNoteUpdated(session, note, editorUserId, meeting != null ? meeting.getStatus() : null);
                return note;
            } catch (RuntimeException ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public EsTopicNote finalizeNote(Long noteId, Long actingUserId) {
        return finalizeNote(noteId, actingUserId, TopicNoteFinalizationMethod.MANUAL);
    }

    public EsTopicNote finalizeNote(Long noteId, Long actingUserId, TopicNoteFinalizationMethod method) {
        if (noteId == null) {
            throw new IllegalArgumentException("noteId is required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsTopicNote note = session.get(EsTopicNote.class, noteId);
                if (note == null) {
                    throw new IllegalArgumentException("Note not found: " + noteId);
                }
                EsMeeting meeting = note.getEsMeetingId() != null ? session.get(EsMeeting.class, note.getEsMeetingId())
                        : null;
                immutabilityGuard.ensureNoteAndMeetingMutable(note, meeting);
                if (meeting != null && meeting.getStatus() == EsMeeting.MeetingStatus.CLOSED) {
                    throw new IllegalStateException("Meeting is closed and note editing is blocked.");
                }
                if (actingUserId != null && !authorizationService.canEditTopicNote(actingUserId, note)) {
                    throw new IllegalStateException("User is not authorized to finalize the note.");
                }

                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                saveRevisionSnapshot(session, note, actingUserId, now);

                note.setStatus(TopicNoteStatus.FINALIZED);
                note.setFinalizedAt(now);
                note.setFinalizedByUserId(actingUserId);
                note.setFinalizationMethod(method);
                note.setActiveEditorUserId(null);
                note.setActiveEditorStartedAt(null);
                session.merge(note);

                tx.commit();
                eventPublisher.publishNoteStateChanged(session, note, meeting != null ? meeting.getStatus() : null);
                return note;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public int automaticallyFinalizeDueAdHocNotes() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<EsTopicNote> due = session.createQuery(
                    "from EsTopicNote n where n.status = :status and n.esMeetingId is null"
                            + " and n.finalizeAt is not null and n.finalizeAt <= :now"
                            + " order by n.finalizeAt asc, n.esTopicNoteId asc",
                    EsTopicNote.class)
                    .setParameter("status", TopicNoteStatus.OPEN)
                    .setParameter("now", LocalDateTime.now(ZoneOffset.UTC))
                    .getResultList();
            int finalized = 0;
            for (EsTopicNote note : due) {
                try {
                    finalizeNote(note.getEsTopicNoteId(), null, TopicNoteFinalizationMethod.AUTOMATIC);
                    finalized++;
                } catch (Exception ex) {
                    java.util.logging.Logger.getLogger(TopicNoteService.class.getName())
                            .warning("Failed to automatically finalize note id=" + note.getEsTopicNoteId()
                                    + ": " + ex.getMessage());
                }
            }
            return finalized;
        }
    }

    private EsTopicNote buildShellNote(EsMeetingAgendaItem agendaItem, EsMeeting meeting, Long effectiveTopicId,
            Long creatorUserId) {
        EsTopicNote note = new EsTopicNote();
        note.setEsTopicId(effectiveTopicId);
        note.setEsMeetingId(meeting != null ? meeting.getEsMeetingId() : null);
        note.setEsMeetingAgendaItemId(agendaItem.getEsMeetingAgendaItemId());
        note.setCreatedByUserId(creatorUserId);
        return note;
    }

    private String resolveNoteTitle(EsMeetingAgendaItem agendaItem, EsTopicMeeting topicMeeting) {
        if (agendaItem != null && agendaItem.getTitle() != null && !agendaItem.getTitle().isBlank()) {
            return agendaItem.getTitle().trim();
        }
        if (agendaItem != null && agendaItem.getEsTopicId() != null) {
            EsTopic topic = topicDao.findById(agendaItem.getEsTopicId()).orElse(null);
            if (topic != null && topic.getTopicName() != null && !topic.getTopicName().isBlank()) {
                return topic.getTopicName().trim();
            }
        }
        if (topicMeeting != null && topicMeeting.getMeetingName() != null && !topicMeeting.getMeetingName().isBlank()) {
            return topicMeeting.getMeetingName().trim();
        }
        return "Meeting notes";
    }

    private void insertEditorHistory(org.hibernate.Session session, Long noteId, Long previousEditorUserId,
            Long newEditorUserId, Long changedByUserId, LocalDateTime changedAt) {
        if (session == null || noteId == null || newEditorUserId == null || changedByUserId == null
                || changedAt == null) {
            return;
        }
        EsTopicNoteEditorHistory history = new EsTopicNoteEditorHistory();
        history.setEsTopicNoteId(noteId);
        history.setPreviousEditorUserId(previousEditorUserId);
        history.setNewEditorUserId(newEditorUserId);
        history.setChangedAt(changedAt);
        history.setChangedByUserId(changedByUserId);
        session.persist(history);
    }

    private TopicNoteEditorState toEditorState(EsTopicNote note, EsMeeting meeting, boolean created, boolean tookOver) {
        return new TopicNoteEditorState(
                note.getEsTopicNoteId(),
                note.getEsMeetingId(),
                note.getEsMeetingAgendaItemId(),
                note.getRevisionNo(),
                note.getActiveEditorVersion() == null ? 0L : note.getActiveEditorVersion(),
                note.getActiveEditorUserId(),
                note.getStatus(),
                meeting != null ? meeting.getStatus() : null,
                note.getUpdatedAt(),
                created,
                tookOver);
    }

    private void saveRevisionSnapshot(org.hibernate.Session session, EsTopicNote note, Long savedByUserId,
            LocalDateTime savedAt) {
        if (note == null || savedByUserId == null) {
            return;
        }
        EsTopicNoteRevision revision = new EsTopicNoteRevision();
        revision.setEsTopicNoteId(note.getEsTopicNoteId());
        revision.setRevisionNo(note.getRevisionNo());
        revision.setDocumentJson(note.getDocumentJson());
        revision.setDocumentText(note.getDocumentText());
        revision.setSavedAt(savedAt);
        revision.setSavedByUserId(savedByUserId);
        session.persist(revision);
    }

    private boolean shouldCreateSnapshot(org.hibernate.Session session, EsTopicNote note, Long actingUserId,
            LocalDateTime savedAt, SaveMode saveMode) {
        if (note == null || note.getEsTopicNoteId() == null || actingUserId == null || savedAt == null) {
            return false;
        }

        EsTopicNoteRevision latest = session.createQuery(
                "from EsTopicNoteRevision r where r.esTopicNoteId = :noteId"
                        + " order by r.revisionNo desc, r.esTopicNoteRevisionId desc",
                EsTopicNoteRevision.class)
                .setParameter("noteId", note.getEsTopicNoteId())
                .setMaxResults(1)
                .uniqueResultOptional()
                .orElse(null);

        if (latest == null) {
            return true;
        }
        if (latest.getDocumentJson() != null && latest.getDocumentJson().equals(note.getDocumentJson())) {
            return false;
        }
        if (saveMode == SaveMode.MANUAL) {
            return true;
        }
        Duration elapsed = Duration.between(latest.getSavedAt(), savedAt);
        return elapsed.compareTo(Duration.ofMinutes(5)) >= 0;
    }

    public record SavedMeetingTopicNote(Long noteId, Long revision, LocalDateTime savedAt, TopicNoteStatus status,
            boolean created, boolean emptyIgnored, Long editorVersion, Long activeEditorUserId) {
        static SavedMeetingTopicNote empty() {
            return new SavedMeetingTopicNote(null, 0L, null, null, false, true, 0L, null);
        }
    }

    public record TopicNoteEditorState(Long noteId, Long meetingId, Long agendaItemId, Long revision,
            Long editorVersion, Long activeEditorUserId, TopicNoteStatus noteStatus,
            EsMeeting.MeetingStatus meetingStatus, LocalDateTime savedAt, boolean created, boolean tookOver) {
    }

    public record TopicNoteContentState(TopicNoteEditorState metadata, String documentJson) {
    }

    public enum SaveMode {
        AUTOSAVE,
        MANUAL
    }
}
