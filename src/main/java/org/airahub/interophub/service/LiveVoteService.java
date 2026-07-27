package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsLiveVoteDao;
import org.airahub.interophub.dao.EsLiveVoteResponseDao;
import org.airahub.interophub.dao.EsMeetingParticipantCountDao;
import org.airahub.interophub.model.EsLiveVote;
import org.airahub.interophub.model.EsLiveVoteResponse;
import org.airahub.interophub.model.EsMeetingAgendaActivity;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingParticipantCount;
import org.airahub.interophub.model.EsRecordedOutcome;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.LiveVoteResponseType;
import org.airahub.interophub.model.LiveVoteResult;
import org.airahub.interophub.model.LiveVoteStatus;
import org.airahub.interophub.model.RecordedOutcomeType;
import org.airahub.interophub.model.TopicNoteStatus;

public class LiveVoteService {

    private final EsLiveVoteDao voteDao;
    private final EsLiveVoteResponseDao responseDao;
    private final EsMeetingParticipantCountDao participantCountDao;
    private final MeetingAuthorizationService authorizationService;
    private final MeetingImmutabilityGuard immutabilityGuard;

    public LiveVoteService() {
        this.voteDao = new EsLiveVoteDao();
        this.responseDao = new EsLiveVoteResponseDao();
        this.participantCountDao = new EsMeetingParticipantCountDao();
        this.authorizationService = new MeetingAuthorizationService();
        this.immutabilityGuard = new MeetingImmutabilityGuard();
    }

    public EsLiveVote prepareVote(Long formalMotionOutcomeId, Long actingUserId) {
        if (formalMotionOutcomeId == null || actingUserId == null) {
            throw new IllegalArgumentException("formalMotionOutcomeId and actingUserId are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsRecordedOutcome outcome = session.get(EsRecordedOutcome.class, formalMotionOutcomeId);
                if (outcome == null) {
                    throw new IllegalArgumentException("Recorded outcome not found: " + formalMotionOutcomeId);
                }
                if (outcome.getOutcomeType() != RecordedOutcomeType.FORMAL_MOTION) {
                    throw new IllegalStateException("Only FORMAL_MOTION outcomes may have a live vote.");
                }
                EsTopicNote note = session.get(EsTopicNote.class, outcome.getEsTopicNoteId());
                EsMeeting meeting = note != null && note.getEsMeetingId() != null
                        ? session.get(EsMeeting.class, note.getEsMeetingId())
                        : null;
                immutabilityGuard.ensureVoteMutable(null, meeting, note);
                if (meeting == null || note == null) {
                    throw new IllegalStateException("Vote must be attached to an open meeting note.");
                }
                if (note.getStatus() == TopicNoteStatus.FINALIZED
                        || meeting.getStatus() == EsMeeting.MeetingStatus.CLOSED) {
                    throw new IllegalStateException("Vote cannot be prepared on a finalized note or closed meeting.");
                }
                if (!authorizationService.canControlMeeting(actingUserId, meeting)) {
                    throw new IllegalStateException("User is not authorized to prepare a live vote.");
                }

                EsLiveVote existing = session.createQuery(
                        "from EsLiveVote v where v.esRecordedOutcomeId = :outcomeId",
                        EsLiveVote.class)
                        .setParameter("outcomeId", formalMotionOutcomeId)
                        .setMaxResults(1)
                        .uniqueResultOptional()
                        .orElse(null);
                if (existing != null) {
                    return existing;
                }

                EsLiveVote vote = new EsLiveVote();
                vote.setEsRecordedOutcomeId(formalMotionOutcomeId);
                vote.setStatus(LiveVoteStatus.PREPARED);
                vote.setMotionText(outcome.getOutcomeText());
                vote.setPresidingChairUserId(
                        firstNonNull(meeting.getCurrentChairUserId(), meeting.getDesignatedChairUserId()));
                if (vote.getPresidingChairUserId() == null) {
                    throw new IllegalStateException("A presiding chair is required for a formal motion vote.");
                }
                vote.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
                vote.setCreatedByUserId(actingUserId);
                vote.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                vote.setUpdatedByUserId(actingUserId);

                EsMeetingAgendaActivity currentActivity = session.createQuery(
                        "from EsMeetingAgendaActivity a where a.esMeetingId = :meetingId and a.endedAt is null"
                                + " order by a.startedAt desc, a.esMeetingAgendaActivityId desc",
                        EsMeetingAgendaActivity.class)
                        .setParameter("meetingId", meeting.getEsMeetingId())
                        .setMaxResults(1)
                        .uniqueResultOptional()
                        .orElse(null);
                if (currentActivity != null) {
                    EsMeetingParticipantCount latestCount = participantCountDao.findLatestByAgendaActivityId(
                            currentActivity.getEsMeetingAgendaActivityId()).orElse(null);
                    if (latestCount != null) {
                        vote.setParticipantCountObservationId(latestCount.getEsMeetingParticipantCountId());
                        vote.setCallParticipantCount(latestCount.getParticipantCount());
                    }
                }

                session.persist(vote);
                tx.commit();
                return vote;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public EsLiveVote openVote(Long voteId, Long actingUserId) {
        if (voteId == null || actingUserId == null) {
            throw new IllegalArgumentException("voteId and actingUserId are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsLiveVote vote = session.get(EsLiveVote.class, voteId);
                if (vote == null) {
                    throw new IllegalArgumentException("Live vote not found: " + voteId);
                }
                if (vote.getStatus() == LiveVoteStatus.OPEN) {
                    return vote;
                }
                if (vote.getStatus() != LiveVoteStatus.PREPARED) {
                    throw new IllegalStateException("Only PREPARED votes can be opened.");
                }
                EsRecordedOutcome outcome = session.get(EsRecordedOutcome.class, vote.getEsRecordedOutcomeId());
                EsTopicNote note = outcome != null ? session.get(EsTopicNote.class, outcome.getEsTopicNoteId()) : null;
                EsMeeting meeting = note != null && note.getEsMeetingId() != null
                        ? session.get(EsMeeting.class, note.getEsMeetingId())
                        : null;
                immutabilityGuard.ensureVoteMutable(vote, meeting, note);
                if (!authorizationService.canControlMeeting(actingUserId, meeting)) {
                    throw new IllegalStateException("User is not authorized to open the vote.");
                }

                vote.setStatus(LiveVoteStatus.OPEN);
                vote.setOpenedAt(LocalDateTime.now(ZoneOffset.UTC));
                vote.setOpenedByUserId(actingUserId);
                vote.setPresidingChairUserId(
                        firstNonNull(meeting.getCurrentChairUserId(), meeting.getDesignatedChairUserId()));
                vote.setUpdatedByUserId(actingUserId);
                session.merge(vote);
                tx.commit();
                return vote;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public EsLiveVote castOrChangeElectronicVote(Long voteId, Long userId, LiveVoteResponseType response) {
        if (voteId == null || userId == null || response == null) {
            throw new IllegalArgumentException("voteId, userId, and response are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsLiveVote vote = session.get(EsLiveVote.class, voteId);
                if (vote == null) {
                    throw new IllegalArgumentException("Live vote not found: " + voteId);
                }
                if (vote.getStatus() != LiveVoteStatus.OPEN) {
                    throw new IllegalStateException("Electronic votes may only be changed while the vote is open.");
                }
                EsRecordedOutcome outcome = session.get(EsRecordedOutcome.class, vote.getEsRecordedOutcomeId());
                EsTopicNote note = outcome != null ? session.get(EsTopicNote.class, outcome.getEsTopicNoteId()) : null;
                EsMeeting meeting = note != null && note.getEsMeetingId() != null
                        ? session.get(EsMeeting.class, note.getEsMeetingId())
                        : null;
                immutabilityGuard.ensureVoteMutable(vote, meeting, note);
                if (vote.getPresidingChairUserId() != null && vote.getPresidingChairUserId().equals(userId)) {
                    throw new IllegalStateException("The presiding chair may not submit an electronic vote.");
                }
                if (!authorizationService.canControlMeeting(userId, meeting)
                        && !authorizationService.canEditTopicNote(userId, note)) {
                    throw new IllegalStateException("User is not authorized to cast an electronic vote.");
                }

                EsLiveVoteResponse existing = responseDao.findByVoteIdAndUserId(voteId, userId).orElse(null);
                if (existing == null) {
                    existing = new EsLiveVoteResponse();
                    existing.setEsLiveVoteId(voteId);
                    existing.setUserId(userId);
                }
                existing.setResponse(response);
                session.merge(existing);
                tx.commit();
                return vote;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public EsLiveVote updateManualCounts(Long voteId, int forCount, int againstCount, int abstainCount,
            Long actingUserId) {
        if (voteId == null || actingUserId == null) {
            throw new IllegalArgumentException("voteId and actingUserId are required.");
        }
        if (forCount < 0 || againstCount < 0 || abstainCount < 0) {
            throw new IllegalArgumentException("Manual counts must be nonnegative.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsLiveVote vote = session.get(EsLiveVote.class, voteId);
                if (vote == null) {
                    throw new IllegalArgumentException("Live vote not found: " + voteId);
                }
                if (vote.getStatus() != LiveVoteStatus.OPEN) {
                    throw new IllegalStateException("Manual counts may only be updated while the vote is open.");
                }
                EsRecordedOutcome outcome = session.get(EsRecordedOutcome.class, vote.getEsRecordedOutcomeId());
                EsTopicNote note = outcome != null ? session.get(EsTopicNote.class, outcome.getEsTopicNoteId()) : null;
                EsMeeting meeting = note != null && note.getEsMeetingId() != null
                        ? session.get(EsMeeting.class, note.getEsMeetingId())
                        : null;
                immutabilityGuard.ensureVoteMutable(vote, meeting, note);
                if (!authorizationService.canControlMeeting(actingUserId, meeting)) {
                    throw new IllegalStateException("User is not authorized to update manual counts.");
                }

                vote.setManualForCount(forCount);
                vote.setManualAgainstCount(againstCount);
                vote.setManualAbstainCount(abstainCount);
                vote.setUpdatedByUserId(actingUserId);
                session.merge(vote);
                tx.commit();
                return vote;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public EsLiveVote closeVote(Long voteId, Integer finalForCount, Integer finalAgainstCount,
            Integer finalAbstainCount, LiveVoteResult result, Long actingUserId) {
        if (voteId == null || actingUserId == null) {
            throw new IllegalArgumentException("voteId and actingUserId are required.");
        }
        if ((finalForCount != null && finalForCount < 0)
                || (finalAgainstCount != null && finalAgainstCount < 0)
                || (finalAbstainCount != null && finalAbstainCount < 0)) {
            throw new IllegalArgumentException("Final counts must be nonnegative.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsLiveVote vote = session.get(EsLiveVote.class, voteId);
                if (vote == null) {
                    throw new IllegalArgumentException("Live vote not found: " + voteId);
                }
                if (vote.getStatus() != LiveVoteStatus.OPEN) {
                    throw new IllegalStateException("Only open votes can be closed.");
                }
                EsRecordedOutcome outcome = session.get(EsRecordedOutcome.class, vote.getEsRecordedOutcomeId());
                EsTopicNote note = outcome != null ? session.get(EsTopicNote.class, outcome.getEsTopicNoteId()) : null;
                EsMeeting meeting = note != null && note.getEsMeetingId() != null
                        ? session.get(EsMeeting.class, note.getEsMeetingId())
                        : null;
                immutabilityGuard.ensureVoteMutable(vote, meeting, note);
                if (!authorizationService.canControlMeeting(actingUserId, meeting)) {
                    throw new IllegalStateException("User is not authorized to close the vote.");
                }

                Map<LiveVoteResponseType, Long> electronicCounts = responseDao.countByVoteId(voteId);
                vote.setElectronicForCount(electronicCounts.getOrDefault(LiveVoteResponseType.FOR, 0L).intValue());
                vote.setElectronicAgainstCount(
                        electronicCounts.getOrDefault(LiveVoteResponseType.AGAINST, 0L).intValue());
                vote.setElectronicAbstainCount(
                        electronicCounts.getOrDefault(LiveVoteResponseType.ABSTAIN, 0L).intValue());
                responseDao.deleteByVoteId(voteId);

                vote.setFinalForCount(finalForCount != null ? finalForCount
                        : vote.getElectronicForCount() + vote.getManualForCount());
                vote.setFinalAgainstCount(finalAgainstCount != null ? finalAgainstCount
                        : vote.getElectronicAgainstCount() + vote.getManualAgainstCount());
                vote.setFinalAbstainCount(finalAbstainCount != null ? finalAbstainCount
                        : vote.getElectronicAbstainCount() + vote.getManualAbstainCount());
                vote.setResult(result != null ? result : LiveVoteResult.NO_RESULT);
                vote.setStatus(LiveVoteStatus.CLOSED);
                vote.setClosedAt(LocalDateTime.now(ZoneOffset.UTC));
                vote.setClosedByUserId(actingUserId);
                vote.setUpdatedByUserId(actingUserId);
                session.merge(vote);
                tx.commit();
                return vote;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public int closeVotesForMeeting(Long meetingId, Long actingUserId) {
        if (meetingId == null) {
            return 0;
        }
        List<EsLiveVote> openVotes = voteDao.findOpenByMeetingId(meetingId);
        int closed = 0;
        for (EsLiveVote vote : openVotes) {
            try {
                closeVote(vote.getEsLiveVoteId(), null, null, null, vote.getResult(), actingUserId);
                closed++;
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(LiveVoteService.class.getName())
                        .warning("Failed to close vote id=" + vote.getEsLiveVoteId() + ": " + ex.getMessage());
            }
        }
        return closed;
    }

    private Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }
}