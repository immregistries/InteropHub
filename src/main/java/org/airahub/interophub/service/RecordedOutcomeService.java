package org.airahub.interophub.service;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsRecordedOutcome;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.RecordedOutcomeType;
import org.airahub.interophub.model.TopicNoteStatus;

public class RecordedOutcomeService {

    private static final String EDITOR_CHANGED_CODE = "NOTE_EDITOR_CHANGED";
    private static final String SOURCE_MISSING_CODE = "RECORDED_OUTCOME_SOURCE_MISSING";

    private final MeetingAuthorizationService authorizationService;
    private final MeetingImmutabilityGuard immutabilityGuard;
    private final TopicNoteDocumentSupport documentSupport;
    private final TopicNoteEventPublisher eventPublisher;

    public RecordedOutcomeService() {
        this.authorizationService = new MeetingAuthorizationService();
        this.immutabilityGuard = new MeetingImmutabilityGuard();
        this.documentSupport = new TopicNoteDocumentSupport();
        this.eventPublisher = new TopicNoteEventPublisher();
    }

    public OutcomesState getOutcomesForNote(Long noteId) {
        if (noteId == null) {
            throw new IllegalArgumentException("noteId is required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            EsTopicNote note = session.get(EsTopicNote.class, noteId);
            if (note == null) {
                throw new IllegalArgumentException("Note not found: " + noteId);
            }
            return toOutcomesState(session, note);
        }
    }

    public OutcomeMutationResult createOutcome(Long noteId, String sourceNodeId, RecordedOutcomeType outcomeType,
            String shortTitle, String outcomeText, Long expectedEditorVersion, Long actingUserId) {
        if (noteId == null || sourceNodeId == null || sourceNodeId.isBlank() || outcomeType == null
                || actingUserId == null || expectedEditorVersion == null) {
            throw new IllegalArgumentException(
                    "noteId, sourceNodeId, outcomeType, expectedEditorVersion, and actingUserId are required.");
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
                ensureOutcomeMutationAllowed(note, meeting, actingUserId, expectedEditorVersion);

                TopicNoteDocumentSupport.ListItemAnchor anchor = findAnchorOrThrow(note, sourceNodeId);
                if (session.createQuery(
                        "from EsRecordedOutcome o where o.esTopicNoteId = :noteId and o.sourceNodeId = :sourceNodeId",
                        EsRecordedOutcome.class)
                        .setParameter("noteId", noteId)
                        .setParameter("sourceNodeId", sourceNodeId.trim())
                        .setMaxResults(1)
                        .uniqueResultOptional()
                        .isPresent()) {
                    throw new IllegalStateException("A recorded outcome already exists for this bullet.");
                }

                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                EsRecordedOutcome outcome = new EsRecordedOutcome();
                outcome.setEsTopicNoteId(noteId);
                outcome.setSourceNodeId(anchor.sourceNodeId());
                outcome.setOutcomeType(outcomeType);
                outcome.setShortTitle(trimToNull(shortTitle));
                outcome.setOutcomeText(requireOutcomeText(outcomeText));
                outcome.setDisplayOrder(anchor.displayOrder());
                outcome.setCreatedAt(now);
                outcome.setCreatedByUserId(actingUserId);
                outcome.setUpdatedAt(now);
                outcome.setUpdatedByUserId(actingUserId);
                session.persist(outcome);

                tx.commit();
                eventPublisher.publishOutcomesChanged(note, actingUserId,
                        meeting == null ? null : meeting.getStatus());
                return new OutcomeMutationResult(note.getEsTopicNoteId(), note.getRevisionNo(),
                        editorVersion(note), outcome.getEsRecordedOutcomeId());
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public OutcomeMutationResult updateOutcome(Long outcomeId, RecordedOutcomeType outcomeType,
            String shortTitle, String outcomeText, Long expectedEditorVersion, Long actingUserId) {
        if (outcomeId == null || outcomeType == null || actingUserId == null || expectedEditorVersion == null) {
            throw new IllegalArgumentException(
                    "outcomeId, outcomeType, expectedEditorVersion, and actingUserId are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsRecordedOutcome outcome = session.get(EsRecordedOutcome.class, outcomeId);
                if (outcome == null) {
                    throw new IllegalArgumentException("Outcome not found: " + outcomeId);
                }
                EsTopicNote note = session.find(EsTopicNote.class, outcome.getEsTopicNoteId(),
                        LockModeType.PESSIMISTIC_WRITE);
                EsMeeting meeting = note != null && note.getEsMeetingId() != null
                        ? session.find(EsMeeting.class, note.getEsMeetingId(), LockModeType.PESSIMISTIC_READ)
                        : null;
                ensureOutcomeMutationAllowed(note, meeting, actingUserId, expectedEditorVersion);

                // Guard against stale anchors after note edits.
                findAnchorOrThrow(note, outcome.getSourceNodeId());

                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                outcome.setOutcomeType(outcomeType);
                outcome.setShortTitle(trimToNull(shortTitle));
                outcome.setOutcomeText(requireOutcomeText(outcomeText));
                outcome.setUpdatedAt(now);
                outcome.setUpdatedByUserId(actingUserId);
                session.merge(outcome);

                tx.commit();
                eventPublisher.publishOutcomesChanged(note, actingUserId,
                        meeting == null ? null : meeting.getStatus());
                return new OutcomeMutationResult(note.getEsTopicNoteId(), note.getRevisionNo(),
                        editorVersion(note), outcome.getEsRecordedOutcomeId());
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public OutcomeMutationResult deleteOutcome(Long outcomeId, Long expectedEditorVersion, Long actingUserId) {
        if (outcomeId == null || actingUserId == null || expectedEditorVersion == null) {
            throw new IllegalArgumentException("outcomeId, expectedEditorVersion, and actingUserId are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsRecordedOutcome outcome = session.get(EsRecordedOutcome.class, outcomeId);
                if (outcome == null) {
                    throw new IllegalArgumentException("Outcome not found: " + outcomeId);
                }
                EsTopicNote note = session.find(EsTopicNote.class, outcome.getEsTopicNoteId(),
                        LockModeType.PESSIMISTIC_WRITE);
                EsMeeting meeting = note != null && note.getEsMeetingId() != null
                        ? session.find(EsMeeting.class, note.getEsMeetingId(), LockModeType.PESSIMISTIC_READ)
                        : null;
                ensureOutcomeMutationAllowed(note, meeting, actingUserId, expectedEditorVersion);
                session.remove(outcome);
                tx.commit();
                eventPublisher.publishOutcomesChanged(note, actingUserId,
                        meeting == null ? null : meeting.getStatus());
                return new OutcomeMutationResult(note.getEsTopicNoteId(), note.getRevisionNo(),
                        editorVersion(note), outcomeId);
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    void validateAndSyncOutcomesForSavedDocument(org.hibernate.Session session, EsTopicNote note,
            TopicNoteDocumentSupport.NormalizedTopicNoteDocument normalizedDocument, Long actingUserId,
            LocalDateTime updatedAtUtc) {
        if (session == null || note == null || note.getEsTopicNoteId() == null || normalizedDocument == null) {
            return;
        }
        List<EsRecordedOutcome> outcomes = session.createQuery(
                "from EsRecordedOutcome o where o.esTopicNoteId = :noteId order by o.displayOrder asc, o.esRecordedOutcomeId asc",
                EsRecordedOutcome.class)
                .setParameter("noteId", note.getEsTopicNoteId())
                .getResultList();
        if (outcomes.isEmpty()) {
            return;
        }

        List<TopicNoteDocumentSupport.ListItemAnchor> anchors = documentSupport
                .listListItemAnchors(normalizedDocument.json());
        Map<String, TopicNoteDocumentSupport.ListItemAnchor> byNodeId = new LinkedHashMap<>();
        for (TopicNoteDocumentSupport.ListItemAnchor anchor : anchors) {
            byNodeId.put(anchor.sourceNodeId(), anchor);
        }

        for (EsRecordedOutcome outcome : outcomes) {
            TopicNoteDocumentSupport.ListItemAnchor anchor = byNodeId.get(outcome.getSourceNodeId());
            if (anchor == null) {
                throw new TopicNoteConflictException(
                        SOURCE_MISSING_CODE,
                        "A recorded outcome is anchored to a bullet that no longer exists.",
                        note.getActiveEditorUserId(),
                        editorVersion(note));
            }
            Integer desiredOrder = anchor.displayOrder();
            if (!desiredOrder.equals(outcome.getDisplayOrder())) {
                outcome.setDisplayOrder(desiredOrder);
                outcome.setUpdatedAt(updatedAtUtc == null ? LocalDateTime.now(ZoneOffset.UTC) : updatedAtUtc);
                if (actingUserId != null) {
                    outcome.setUpdatedByUserId(actingUserId);
                }
                session.merge(outcome);
            }
        }
    }

    private OutcomesState toOutcomesState(org.hibernate.Session session, EsTopicNote note) {
        List<EsRecordedOutcome> outcomes = session.createQuery(
                "from EsRecordedOutcome o where o.esTopicNoteId = :noteId order by o.displayOrder asc, o.esRecordedOutcomeId asc",
                EsRecordedOutcome.class)
                .setParameter("noteId", note.getEsTopicNoteId())
                .getResultList();

        Map<String, TopicNoteDocumentSupport.ListItemAnchor> anchorsByNodeId = new LinkedHashMap<>();
        for (TopicNoteDocumentSupport.ListItemAnchor anchor : documentSupport
                .listListItemAnchors(note.getDocumentJson())) {
            anchorsByNodeId.put(anchor.sourceNodeId(), anchor);
        }

        List<OutcomeView> views = new ArrayList<>();
        for (EsRecordedOutcome outcome : outcomes) {
            TopicNoteDocumentSupport.ListItemAnchor anchor = anchorsByNodeId.get(outcome.getSourceNodeId());
            String sourceText = anchor == null ? "" : anchor.sourceText();
            Integer displayOrder = anchor == null ? outcome.getDisplayOrder() : anchor.displayOrder();
            views.add(new OutcomeView(
                    outcome.getEsRecordedOutcomeId(),
                    outcome.getSourceNodeId(),
                    outcome.getOutcomeType(),
                    outcome.getShortTitle(),
                    outcome.getOutcomeText(),
                    sourceText,
                    displayOrder));
        }

        return new OutcomesState(
                note.getEsTopicNoteId(),
                note.getRevisionNo(),
                editorVersion(note),
                views);
    }

    private void ensureOutcomeMutationAllowed(EsTopicNote note, EsMeeting meeting, Long actingUserId,
            Long expectedEditorVersion) {
        if (note == null) {
            throw new IllegalArgumentException("Topic note was not found.");
        }
        immutabilityGuard.ensureNoteAndMeetingMutable(note, meeting);
        if (note.getStatus() == TopicNoteStatus.FINALIZED) {
            throw new IllegalStateException("Cannot edit outcomes on a finalized note.");
        }
        if (!authorizationService.canEditTopicNote(actingUserId, note)) {
            throw new IllegalStateException("User is not authorized to edit outcomes.");
        }
        long currentEditorVersion = editorVersion(note);
        if (!actingUserId.equals(note.getActiveEditorUserId())
                || expectedEditorVersion.longValue() != currentEditorVersion) {
            throw new TopicNoteConflictException(
                    EDITOR_CHANGED_CODE,
                    "Another editor is currently responsible for these notes.",
                    note.getActiveEditorUserId(),
                    currentEditorVersion);
        }
    }

    private TopicNoteDocumentSupport.ListItemAnchor findAnchorOrThrow(EsTopicNote note, String sourceNodeId) {
        TopicNoteDocumentSupport.ListItemAnchor anchor = documentSupport.findListItemAnchor(note.getDocumentJson(),
                sourceNodeId);
        if (anchor == null) {
            throw new TopicNoteConflictException(
                    SOURCE_MISSING_CODE,
                    "Selected bullet no longer exists in the saved note.",
                    note.getActiveEditorUserId(),
                    editorVersion(note));
        }
        return anchor;
    }

    private long editorVersion(EsTopicNote note) {
        return note == null || note.getActiveEditorVersion() == null ? 0L : note.getActiveEditorVersion();
    }

    private String requireOutcomeText(String outcomeText) {
        String normalized = trimToNull(outcomeText);
        if (normalized == null) {
            throw new IllegalArgumentException("outcomeText is required.");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record OutcomeView(Long outcomeId, String sourceNodeId, RecordedOutcomeType outcomeType,
            String shortTitle, String outcomeText, String sourceText, Integer displayOrder) {
    }

    public record OutcomesState(Long noteId, Long revision, Long editorVersion, List<OutcomeView> outcomes) {
    }

    public record OutcomeMutationResult(Long noteId, Long revision, Long editorVersion, Long outcomeId) {
    }
}