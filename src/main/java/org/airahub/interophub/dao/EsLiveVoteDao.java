package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsLiveVote;
import org.airahub.interophub.model.LiveVoteStatus;

public class EsLiveVoteDao extends GenericDao<EsLiveVote, Long> {

    public EsLiveVoteDao() {
        super(EsLiveVote.class);
    }

    public Optional<EsLiveVote> findOpenByOutcomeId(Long esRecordedOutcomeId) {
        if (esRecordedOutcomeId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsLiveVote v where v.esRecordedOutcomeId = :outcomeId and v.status = :status",
                    EsLiveVote.class)
                    .setParameter("outcomeId", esRecordedOutcomeId)
                    .setParameter("status", LiveVoteStatus.OPEN)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Optional<EsLiveVote> findByOutcomeId(Long esRecordedOutcomeId) {
        if (esRecordedOutcomeId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsLiveVote v where v.esRecordedOutcomeId = :outcomeId order by v.createdAt desc",
                    EsLiveVote.class)
                    .setParameter("outcomeId", esRecordedOutcomeId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsLiveVote> findByStatus(LiveVoteStatus status) {
        if (status == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsLiveVote v where v.status = :status order by v.createdAt desc",
                    EsLiveVote.class)
                    .setParameter("status", status)
                    .getResultList();
        }
    }

    public List<EsLiveVote> findByMeetingId(Long meetingId) {
        if (meetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsLiveVote v where v.esRecordedOutcomeId in ("
                            + "select o.esRecordedOutcomeId from EsRecordedOutcome o where o.esTopicNoteId in ("
                            + "select n.esTopicNoteId from EsTopicNote n where n.esMeetingId = :meetingId))"
                            + " order by v.createdAt asc, v.esLiveVoteId asc",
                    EsLiveVote.class)
                    .setParameter("meetingId", meetingId)
                    .getResultList();
        }
    }

    public List<EsLiveVote> findOpenByMeetingId(Long meetingId) {
        if (meetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsLiveVote v where v.status = :status and v.esRecordedOutcomeId in ("
                            + "select o.esRecordedOutcomeId from EsRecordedOutcome o where o.esTopicNoteId in ("
                            + "select n.esTopicNoteId from EsTopicNote n where n.esMeetingId = :meetingId))"
                            + " order by v.createdAt asc, v.esLiveVoteId asc",
                    EsLiveVote.class)
                    .setParameter("status", LiveVoteStatus.OPEN)
                    .setParameter("meetingId", meetingId)
                    .getResultList();
        }
    }
}