package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsRecordedOutcome;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.RecordedOutcomeType;
import org.airahub.interophub.model.TopicNoteStatus;

public class RecordedOutcomeService {

    private final MeetingAuthorizationService authorizationService;
    private final MeetingImmutabilityGuard immutabilityGuard;

    public RecordedOutcomeService() {
        this.authorizationService = new MeetingAuthorizationService();
        this.immutabilityGuard = new MeetingImmutabilityGuard();
    }

    public EsRecordedOutcome createOutcome(Long noteId, String sourceNodeId, RecordedOutcomeType outcomeType,
            String shortTitle, String outcomeText, Integer displayOrder, Long actingUserId) {
        if (noteId == null || sourceNodeId == null || sourceNodeId.isBlank() || outcomeType == null
                || outcomeText == null || displayOrder == null || actingUserId == null) {
            throw new IllegalArgumentException("All recorded outcome fields are required.");
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
                if (note.getStatus() == TopicNoteStatus.FINALIZED) {
                    throw new IllegalStateException("Cannot edit outcomes on a finalized note.");
                }
                if (!authorizationService.canEditTopicNote(actingUserId, note)) {
                    throw new IllegalStateException("User is not authorized to edit outcomes.");
                }
                if (note.getDocumentJson() != null && !note.getDocumentJson().contains(sourceNodeId.trim())) {
                    throw new IllegalArgumentException("Source node id was not found in the note document.");
                }

                EsRecordedOutcome outcome = new EsRecordedOutcome();
                outcome.setEsTopicNoteId(noteId);
                outcome.setSourceNodeId(sourceNodeId.trim());
                outcome.setOutcomeType(outcomeType);
                outcome.setShortTitle(shortTitle);
                outcome.setOutcomeText(outcomeText);
                outcome.setDisplayOrder(displayOrder);
                outcome.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
                outcome.setCreatedByUserId(actingUserId);
                outcome.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                outcome.setUpdatedByUserId(actingUserId);
                session.persist(outcome);

                tx.commit();
                return outcome;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public EsRecordedOutcome updateOutcome(Long outcomeId, String shortTitle, String outcomeText, Integer displayOrder,
            Long actingUserId) {
        if (outcomeId == null || outcomeText == null || displayOrder == null || actingUserId == null) {
            throw new IllegalArgumentException("outcomeId, outcomeText, displayOrder, and actingUserId are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsRecordedOutcome outcome = session.get(EsRecordedOutcome.class, outcomeId);
                if (outcome == null) {
                    throw new IllegalArgumentException("Outcome not found: " + outcomeId);
                }
                EsTopicNote note = session.get(EsTopicNote.class, outcome.getEsTopicNoteId());
                EsMeeting meeting = note != null && note.getEsMeetingId() != null
                        ? session.get(EsMeeting.class, note.getEsMeetingId())
                        : null;
                immutabilityGuard.ensureNoteAndMeetingMutable(note, meeting);
                if (note == null || note.getStatus() == TopicNoteStatus.FINALIZED) {
                    throw new IllegalStateException("Cannot edit outcomes after note finalization.");
                }
                if (!authorizationService.canEditTopicNote(actingUserId, note)) {
                    throw new IllegalStateException("User is not authorized to edit outcomes.");
                }

                outcome.setShortTitle(shortTitle);
                outcome.setOutcomeText(outcomeText);
                outcome.setDisplayOrder(displayOrder);
                outcome.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                outcome.setUpdatedByUserId(actingUserId);
                session.merge(outcome);

                tx.commit();
                return outcome;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public int deleteOutcome(Long outcomeId, Long actingUserId) {
        if (outcomeId == null || actingUserId == null) {
            throw new IllegalArgumentException("outcomeId and actingUserId are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsRecordedOutcome outcome = session.get(EsRecordedOutcome.class, outcomeId);
                if (outcome == null) {
                    return 0;
                }
                EsTopicNote note = session.get(EsTopicNote.class, outcome.getEsTopicNoteId());
                EsMeeting meeting = note != null && note.getEsMeetingId() != null
                        ? session.get(EsMeeting.class, note.getEsMeetingId())
                        : null;
                immutabilityGuard.ensureNoteAndMeetingMutable(note, meeting);
                if (note == null || note.getStatus() == TopicNoteStatus.FINALIZED) {
                    throw new IllegalStateException("Cannot delete outcomes after note finalization.");
                }
                if (!authorizationService.canEditTopicNote(actingUserId, note)) {
                    throw new IllegalStateException("User is not authorized to delete outcomes.");
                }

                session.remove(outcome);
                tx.commit();
                return 1;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }
}