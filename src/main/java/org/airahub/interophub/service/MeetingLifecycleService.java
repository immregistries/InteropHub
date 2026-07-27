package org.airahub.interophub.service;

import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.model.EsLiveVote;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingRoleAssignment;
import org.airahub.interophub.model.EsMeetingStatusHistory;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.LiveVoteResult;
import org.airahub.interophub.model.LiveVoteStatus;
import org.airahub.interophub.model.LiveVoteResponseType;
import org.airahub.interophub.model.MeetingCloseMethod;
import org.airahub.interophub.model.MeetingRoleType;
import org.airahub.interophub.model.MeetingTransitionMethod;
import org.airahub.interophub.model.TopicNoteFinalizationMethod;
import org.airahub.interophub.model.TopicNoteStatus;

public class MeetingLifecycleService {

    private final EsMeetingDao meetingDao;
    private final MeetingAuthorizationService authorizationService;
    private final MeetingImmutabilityGuard immutabilityGuard;
    private final TopicNoteEventPublisher noteEventPublisher;
    private final Clock clock;

    public MeetingLifecycleService() {
        this(Clock.systemUTC(), new MeetingAuthorizationService());
    }

    MeetingLifecycleService(Clock clock, MeetingAuthorizationService authorizationService) {
        this.meetingDao = new EsMeetingDao();
        this.authorizationService = authorizationService;
        this.immutabilityGuard = new MeetingImmutabilityGuard();
        this.noteEventPublisher = new TopicNoteEventPublisher();
        this.clock = clock;
    }

    public EsMeeting startMeeting(Long meetingId, Long actingUserId) {
        return startMeeting(meetingId, actingUserId, clock.instant());
    }

    public EsMeeting startMeeting(Long meetingId, Long actingUserId, Instant nowInstant) {
        if (meetingId == null || actingUserId == null) {
            throw new IllegalArgumentException("meetingId and actingUserId are required.");
        }
        Instant effectiveNow = nowInstant != null ? nowInstant : clock.instant();
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsMeeting meeting = session.find(EsMeeting.class, meetingId, LockModeType.PESSIMISTIC_WRITE);
                if (meeting == null) {
                    throw new IllegalArgumentException("Meeting not found: " + meetingId);
                }
                immutabilityGuard.ensureMeetingMutable(meeting);
                if (!authorizationService.canControlMeeting(actingUserId, meeting)) {
                    throw new IllegalStateException("User is not authorized to start the meeting.");
                }

                if (meeting.getStatus() == EsMeeting.MeetingStatus.IN_SESSION) {
                    throw new IllegalStateException("The meeting is already in session.");
                }
                if (meeting.getStatus() != EsMeeting.MeetingStatus.FINALIZED) {
                    throw new IllegalStateException("Meeting is not finalized.");
                }
                if (!MeetingWindowRules.isMeetingStartWindowOpen(meeting, effectiveNow)) {
                    throw new IllegalStateException("Start window is not open yet.");
                }

                LocalDateTime nowUtc = LocalDateTime.ofInstant(effectiveNow, ZoneOffset.UTC);
                meeting.setStatus(EsMeeting.MeetingStatus.IN_SESSION);
                meeting.setStartedAt(nowUtc);
                meeting.setStartedByUserId(actingUserId);
                meeting.setCurrentChairUserId(meeting.getDesignatedChairUserId());
                meeting.setCurrentScribeUserId(meeting.getDesignatedScribeUserId());
                session.merge(meeting);

                if (meeting.getDesignatedChairUserId() != null
                        && !hasOpenRoleAssignment(session, meetingId, MeetingRoleType.CHAIR)) {
                    persistRoleAssignment(session, meetingId, MeetingRoleType.CHAIR,
                            meeting.getDesignatedChairUserId(), actingUserId, nowUtc);
                }
                if (meeting.getDesignatedScribeUserId() != null
                        && !hasOpenRoleAssignment(session, meetingId, MeetingRoleType.SCRIBE)) {
                    persistRoleAssignment(session, meetingId, MeetingRoleType.SCRIBE,
                            meeting.getDesignatedScribeUserId(), actingUserId, nowUtc);
                }

                insertStatusHistory(session, meetingId, EsMeeting.MeetingStatus.FINALIZED,
                        EsMeeting.MeetingStatus.IN_SESSION, actingUserId, MeetingTransitionMethod.USER, nowUtc);

                tx.commit();
                return meeting;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public EsMeeting completeMeeting(Long meetingId, Long actingUserId) {
        return completeMeeting(meetingId, actingUserId, clock.instant());
    }

    public EsMeeting completeMeeting(Long meetingId, Long actingUserId, Instant nowInstant) {
        if (meetingId == null || actingUserId == null) {
            throw new IllegalArgumentException("meetingId and actingUserId are required.");
        }
        Instant effectiveNow = nowInstant != null ? nowInstant : clock.instant();
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsMeeting meeting = session.find(EsMeeting.class, meetingId, LockModeType.PESSIMISTIC_WRITE);
                if (meeting == null) {
                    throw new IllegalArgumentException("Meeting not found: " + meetingId);
                }
                immutabilityGuard.ensureMeetingMutable(meeting);
                if (!authorizationService.canControlMeeting(actingUserId, meeting)) {
                    throw new IllegalStateException("User is not authorized to complete the meeting.");
                }
                if (meeting.getStatus() == EsMeeting.MeetingStatus.COMPLETED
                        || meeting.getStatus() == EsMeeting.MeetingStatus.CLOSED) {
                    throw new IllegalStateException("The meeting has already ended.");
                }
                if (meeting.getStatus() != EsMeeting.MeetingStatus.IN_SESSION) {
                    throw new IllegalStateException("Meeting is not in session.");
                }

                LocalDateTime nowUtc = LocalDateTime.ofInstant(effectiveNow, ZoneOffset.UTC);
                LocalDateTime deadline = nowUtc.plusDays(7);
                meeting.setStatus(EsMeeting.MeetingStatus.COMPLETED);
                meeting.setCompletedAt(nowUtc);
                meeting.setCompletedByUserId(actingUserId);
                meeting.setCloseDueAt(deadline);
                session.merge(meeting);

                endCurrentAgendaActivity(session, meetingId, nowUtc, actingUserId);
                clearCurrentAgendaPointer(session, meeting);
                setOpenMeetingNoteDeadlines(session, meetingId, deadline);
                endOpenRoleAssignments(session, meetingId, nowUtc, MeetingRoleType.CHAIR);
                endOpenRoleAssignments(session, meetingId, nowUtc, MeetingRoleType.SCRIBE);
                insertStatusHistory(session, meetingId, EsMeeting.MeetingStatus.IN_SESSION,
                        EsMeeting.MeetingStatus.COMPLETED, actingUserId, MeetingTransitionMethod.USER, nowUtc);

                tx.commit();
                return meeting;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public EsMeeting closeMeeting(Long meetingId, Long actingUserId) {
        return closeMeetingInternal(meetingId, actingUserId, MeetingCloseMethod.MANUAL, MeetingTransitionMethod.USER);
    }

    public int automaticallyCloseDueMeetings() {
        List<EsMeeting> dueMeetings = meetingDao.findDueForClosure(LocalDateTime.now(ZoneOffset.UTC));
        int closed = 0;
        for (EsMeeting meeting : dueMeetings) {
            try {
                closeMeetingInternal(meeting.getEsMeetingId(), null, MeetingCloseMethod.AUTOMATIC,
                        MeetingTransitionMethod.AUTOMATIC);
                closed++;
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(MeetingLifecycleService.class.getName())
                        .warning("Failed to automatically close meeting id=" + meeting.getEsMeetingId()
                                + ": " + ex.getMessage());
            }
        }
        return closed;
    }

    private EsMeeting closeMeetingInternal(Long meetingId, Long actingUserId, MeetingCloseMethod closeMethod,
            MeetingTransitionMethod transitionMethod) {
        if (meetingId == null) {
            throw new IllegalArgumentException("meetingId is required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsMeeting meeting = session.get(EsMeeting.class, meetingId);
                if (meeting == null) {
                    throw new IllegalArgumentException("Meeting not found: " + meetingId);
                }
                if (meeting.getStatus() == EsMeeting.MeetingStatus.CLOSED) {
                    return meeting;
                }
                if (actingUserId != null && !authorizationService.canControlMeeting(actingUserId, meeting)) {
                    throw new IllegalStateException("User is not authorized to close the meeting.");
                }

                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                if (meeting.getCompletedAt() == null) {
                    meeting.setCompletedAt(now);
                    meeting.setCompletedByUserId(actingUserId);
                }
                if (meeting.getCloseDueAt() == null) {
                    meeting.setCloseDueAt(meeting.getCompletedAt().plusDays(7));
                }

                List<EsTopicNote> finalizedNotes = finalizeOpenMeetingNotes(session, meetingId,
                        meeting.getCloseDueAt(), actingUserId, closeMethod);
                closeOpenVotes(session, meetingId, actingUserId);
                endCurrentAgendaActivity(session, meetingId, now, actingUserId);
                clearRolePointersAndAssignments(session, meetingId, now);
                clearCurrentAgendaPointer(session, meeting);

                EsMeeting.MeetingStatus fromStatus = meeting.getStatus();
                meeting.setStatus(EsMeeting.MeetingStatus.CLOSED);
                meeting.setClosedAt(now);
                meeting.setClosedByUserId(actingUserId);
                meeting.setCloseMethod(closeMethod == MeetingCloseMethod.AUTOMATIC
                        ? EsMeeting.MeetingCloseMethod.AUTOMATIC
                        : EsMeeting.MeetingCloseMethod.MANUAL);
                session.merge(meeting);

                insertStatusHistory(session, meetingId, fromStatus, EsMeeting.MeetingStatus.CLOSED,
                        actingUserId, transitionMethod, now);
                tx.commit();
                for (EsTopicNote finalizedNote : finalizedNotes) {
                    noteEventPublisher.publishNoteStateChanged(finalizedNote, EsMeeting.MeetingStatus.CLOSED);
                }
                return meeting;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    private List<EsTopicNote> finalizeOpenMeetingNotes(org.hibernate.Session session, Long meetingId,
            LocalDateTime deadline,
            Long actingUserId, MeetingCloseMethod closeMethod) {
        TopicNoteDocumentSupport documentSupport = new TopicNoteDocumentSupport();
        List<EsTopicNote> finalized = new java.util.ArrayList<>();
        List<EsTopicNote> notes = session.createQuery(
                "from EsTopicNote n where n.esMeetingId = :meetingId and n.status = :status",
                EsTopicNote.class)
                .setParameter("meetingId", meetingId)
                .setParameter("status", TopicNoteStatus.OPEN)
                .getResultList();
        for (EsTopicNote note : notes) {
            boolean provisional = note.getRevisionNo() != null && note.getRevisionNo() <= 0L;
            boolean emptyDocument = documentSupport.isEmptyDocument(note.getDocumentJson());
            if (provisional && emptyDocument && !noteHasOutcomesOrVotes(session, note.getEsTopicNoteId())) {
                session.remove(note);
                continue;
            }

            note.setStatus(TopicNoteStatus.FINALIZED);
            note.setFinalizedAt(LocalDateTime.now(ZoneOffset.UTC));
            note.setFinalizedByUserId(actingUserId);
            note.setFinalizationMethod(closeMethod == MeetingCloseMethod.AUTOMATIC
                    ? TopicNoteFinalizationMethod.AUTOMATIC
                    : TopicNoteFinalizationMethod.MANUAL);
            note.setFinalizeAt(deadline);
            note.setActiveEditorUserId(null);
            note.setActiveEditorStartedAt(null);
            session.merge(note);
            finalized.add(note);
        }
        return finalized;
    }

    private boolean noteHasOutcomesOrVotes(org.hibernate.Session session, Long noteId) {
        if (noteId == null) {
            return false;
        }

        List<Long> outcomeIds = session.createQuery(
                "select o.esRecordedOutcomeId from EsRecordedOutcome o where o.esTopicNoteId = :noteId",
                Long.class)
                .setParameter("noteId", noteId)
                .setMaxResults(1)
                .getResultList();
        if (!outcomeIds.isEmpty()) {
            return true;
        }

        List<Long> voteIds = session.createQuery(
                "select v.esLiveVoteId from EsLiveVote v where v.esRecordedOutcomeId in ("
                        + "select o.esRecordedOutcomeId from EsRecordedOutcome o where o.esTopicNoteId = :noteId)",
                Long.class)
                .setParameter("noteId", noteId)
                .setMaxResults(1)
                .getResultList();
        return !voteIds.isEmpty();
    }

    private void closeOpenVotes(org.hibernate.Session session, Long meetingId, Long actingUserId) {
        List<EsLiveVote> openVotes = session.createQuery(
                "from EsLiveVote v where v.status = :status and v.esRecordedOutcomeId in ("
                        + "select o.esRecordedOutcomeId from EsRecordedOutcome o where o.esTopicNoteId in ("
                        + "select n.esTopicNoteId from EsTopicNote n where n.esMeetingId = :meetingId))",
                EsLiveVote.class)
                .setParameter("status", LiveVoteStatus.OPEN)
                .setParameter("meetingId", meetingId)
                .getResultList();
        for (EsLiveVote vote : openVotes) {
            MapCounts counts = aggregateElectronicResponses(session, vote.getEsLiveVoteId());
            vote.setElectronicForCount(counts.forCount);
            vote.setElectronicAgainstCount(counts.againstCount);
            vote.setElectronicAbstainCount(counts.abstainCount);
            vote.setFinalForCount(vote.getFinalForCount() != null ? vote.getFinalForCount()
                    : vote.getElectronicForCount() + vote.getManualForCount());
            vote.setFinalAgainstCount(vote.getFinalAgainstCount() != null ? vote.getFinalAgainstCount()
                    : vote.getElectronicAgainstCount() + vote.getManualAgainstCount());
            vote.setFinalAbstainCount(vote.getFinalAbstainCount() != null ? vote.getFinalAbstainCount()
                    : vote.getElectronicAbstainCount() + vote.getManualAbstainCount());
            if (vote.getResult() == null) {
                vote.setResult(LiveVoteResult.NO_RESULT);
            }
            vote.setStatus(LiveVoteStatus.CLOSED);
            vote.setClosedAt(LocalDateTime.now(ZoneOffset.UTC));
            vote.setClosedByUserId(actingUserId);
            session.merge(vote);
            session.createMutationQuery("delete from EsLiveVoteResponse r where r.esLiveVoteId = :voteId")
                    .setParameter("voteId", vote.getEsLiveVoteId())
                    .executeUpdate();
        }
    }

    private MapCounts aggregateElectronicResponses(org.hibernate.Session session, Long voteId) {
        List<Object[]> rows = session.createQuery(
                "select r.response, count(r.esLiveVoteResponseId) from EsLiveVoteResponse r"
                        + " where r.esLiveVoteId = :voteId group by r.response",
                Object[].class)
                .setParameter("voteId", voteId)
                .getResultList();
        MapCounts counts = new MapCounts();
        for (Object[] row : rows) {
            LiveVoteResponseType response = (LiveVoteResponseType) row[0];
            long count = (Long) row[1];
            if (response == LiveVoteResponseType.FOR) {
                counts.forCount = (int) count;
            } else if (response == LiveVoteResponseType.AGAINST) {
                counts.againstCount = (int) count;
            } else if (response == LiveVoteResponseType.ABSTAIN) {
                counts.abstainCount = (int) count;
            }
        }
        return counts;
    }

    private void clearRolePointersAndAssignments(org.hibernate.Session session, Long meetingId, LocalDateTime endedAt) {
        session.createMutationQuery(
                "update EsMeetingRoleAssignment r set r.endedAt = :endedAt"
                        + " where r.esMeetingId = :meetingId and r.endedAt is null")
                .setParameter("endedAt", endedAt)
                .setParameter("meetingId", meetingId)
                .executeUpdate();
        EsMeeting meeting = session.get(EsMeeting.class, meetingId);
        if (meeting != null) {
            meeting.setCurrentChairUserId(null);
            meeting.setCurrentScribeUserId(null);
            session.merge(meeting);
        }
    }

    private void clearCurrentAgendaPointer(org.hibernate.Session session, EsMeeting meeting) {
        if (meeting == null) {
            return;
        }
        meeting.setCurrentAgendaItemId(null);
        session.merge(meeting);
    }

    private void setOpenMeetingNoteDeadlines(org.hibernate.Session session, Long meetingId, LocalDateTime deadline) {
        session.createMutationQuery(
                "update EsTopicNote n set n.finalizeAt = :deadline"
                        + " where n.esMeetingId = :meetingId and n.status = :status")
                .setParameter("deadline", deadline)
                .setParameter("meetingId", meetingId)
                .setParameter("status", TopicNoteStatus.OPEN)
                .executeUpdate();
    }

    private void endCurrentAgendaActivity(org.hibernate.Session session, Long meetingId, LocalDateTime endedAt,
            Long endedByUserId) {
        session.createMutationQuery(
                "update EsMeetingAgendaActivity a set a.endedAt = :endedAt, a.endedByUserId = :endedByUserId"
                        + " where a.esMeetingId = :meetingId and a.endedAt is null")
                .setParameter("endedAt", endedAt)
                .setParameter("endedByUserId", endedByUserId)
                .setParameter("meetingId", meetingId)
                .executeUpdate();
    }

    private void persistRoleAssignment(org.hibernate.Session session, Long meetingId, MeetingRoleType roleType,
            Long userId, Long actingUserId, LocalDateTime now) {
        EsMeetingRoleAssignment assignment = new EsMeetingRoleAssignment();
        assignment.setEsMeetingId(meetingId);
        assignment.setRoleType(roleType);
        assignment.setUserId(userId);
        assignment.setStartedAt(now);
        assignment.setAssignedByUserId(actingUserId);
        session.persist(assignment);
    }

    private boolean hasOpenRoleAssignment(org.hibernate.Session session, Long meetingId, MeetingRoleType roleType) {
        Long count = session.createQuery(
                "select count(r.esMeetingRoleAssignmentId) from EsMeetingRoleAssignment r"
                        + " where r.esMeetingId = :meetingId and r.roleType = :roleType and r.endedAt is null",
                Long.class)
                .setParameter("meetingId", meetingId)
                .setParameter("roleType", roleType)
                .getSingleResult();
        return count != null && count > 0;
    }

    private void endOpenRoleAssignments(org.hibernate.Session session, Long meetingId, LocalDateTime endedAt,
            MeetingRoleType roleType) {
        session.createMutationQuery(
                "update EsMeetingRoleAssignment r set r.endedAt = :endedAt"
                        + " where r.esMeetingId = :meetingId and r.roleType = :roleType and r.endedAt is null")
                .setParameter("endedAt", endedAt)
                .setParameter("meetingId", meetingId)
                .setParameter("roleType", roleType)
                .executeUpdate();
    }

    private void insertStatusHistory(org.hibernate.Session session, Long meetingId, EsMeeting.MeetingStatus fromStatus,
            EsMeeting.MeetingStatus toStatus, Long actingUserId, MeetingTransitionMethod method,
            LocalDateTime changedAt) {
        EsMeetingStatusHistory history = new EsMeetingStatusHistory();
        history.setEsMeetingId(meetingId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedAt(changedAt);
        history.setChangedByUserId(actingUserId);
        history.setTransitionMethod(method);
        session.persist(history);
    }

    private static final class MapCounts {
        private int forCount;
        private int againstCount;
        private int abstainCount;
    }
}