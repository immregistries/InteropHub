package org.airahub.interophub.service;

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

    private final EsTopicDao topicDao;
    private final MeetingAuthorizationService authorizationService;
    private final MeetingImmutabilityGuard immutabilityGuard;
    private final TopicNoteDocumentSupport documentSupport;

    public TopicNoteService() {
        this.topicDao = new EsTopicDao();
        this.authorizationService = new MeetingAuthorizationService();
        this.immutabilityGuard = new MeetingImmutabilityGuard();
        this.documentSupport = new TopicNoteDocumentSupport();
    }

    TopicNoteService(MeetingAuthorizationService authorizationService) {
        this.topicDao = new EsTopicDao();
        this.authorizationService = authorizationService;
        this.immutabilityGuard = new MeetingImmutabilityGuard();
        this.documentSupport = new TopicNoteDocumentSupport();
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

    public EsTopicNote takeOverEditing(Long noteId, Long actingUserId) {
        if (noteId == null || actingUserId == null) {
            throw new IllegalArgumentException("noteId and actingUserId are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return takeOverEditing(session, noteId, actingUserId);
        }
    }

    EsTopicNote takeOverEditing(org.hibernate.Session session, Long noteId, Long actingUserId) {
        org.hibernate.Transaction tx = session.beginTransaction();
        try {
            EsTopicNote note = session.get(EsTopicNote.class, noteId);
            if (note == null) {
                throw new IllegalArgumentException("Note not found: " + noteId);
            }
            EsMeeting meeting = note.getEsMeetingId() != null ? session.get(EsMeeting.class, note.getEsMeetingId())
                    : null;
            immutabilityGuard.ensureNoteAndMeetingMutable(note, meeting);
            if (!authorizationService.canEditTopicNote(actingUserId, note)) {
                throw new IllegalStateException("User is not authorized to edit the note.");
            }

            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            saveRevisionSnapshot(session, note, actingUserId, now);

            Long previousEditor = note.getActiveEditorUserId();
            note.setActiveEditorUserId(actingUserId);
            note.setActiveEditorStartedAt(now);
            note.setActiveEditorVersion(
                    (note.getActiveEditorVersion() == null ? 0L : note.getActiveEditorVersion()) + 1L);
            session.merge(note);

            EsTopicNoteEditorHistory history = new EsTopicNoteEditorHistory();
            history.setEsTopicNoteId(noteId);
            history.setPreviousEditorUserId(previousEditor);
            history.setNewEditorUserId(actingUserId);
            history.setChangedAt(now);
            history.setChangedByUserId(actingUserId);
            session.persist(history);

            tx.commit();
            return note;
        } catch (Exception ex) {
            tx.rollback();
            throw ex;
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
                if (!editorUserId.equals(note.getActiveEditorUserId())) {
                    throw new IllegalStateException("This user is not the active editor.");
                }
                long currentVersion = note.getActiveEditorVersion() == null ? 0L : note.getActiveEditorVersion();
                if (!editorVersion.equals(currentVersion)) {
                    throw new IllegalStateException("Stale editor version.");
                }
                if (!expectedRevision.equals(note.getRevisionNo())) {
                    throw new IllegalStateException("Stale note revision.");
                }

                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                note.setDocumentJson(documentJson);
                note.setDocumentText(documentSupport.extractPlainText(documentJson));
                note.setRevisionNo(note.getRevisionNo() + 1L);
                session.merge(note);
                saveRevisionSnapshot(session, note, editorUserId, now);

                tx.commit();
                return note;
            } catch (Exception ex) {
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
}