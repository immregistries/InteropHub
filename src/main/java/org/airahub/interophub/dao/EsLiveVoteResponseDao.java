package org.airahub.interophub.dao;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsLiveVoteResponse;
import org.airahub.interophub.model.LiveVoteResponseType;

public class EsLiveVoteResponseDao extends GenericDao<EsLiveVoteResponse, Long> {

    public EsLiveVoteResponseDao() {
        super(EsLiveVoteResponse.class);
    }

    public Map<LiveVoteResponseType, Long> countByVoteId(Long esLiveVoteId) {
        Map<LiveVoteResponseType, Long> counts = new EnumMap<>(LiveVoteResponseType.class);
        for (LiveVoteResponseType type : LiveVoteResponseType.values()) {
            counts.put(type, 0L);
        }
        if (esLiveVoteId == null) {
            return counts;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select r.response, count(r.esLiveVoteResponseId)"
                            + " from EsLiveVoteResponse r where r.esLiveVoteId = :voteId group by r.response",
                    Object[].class)
                    .setParameter("voteId", esLiveVoteId)
                    .getResultList();
            for (Object[] row : rows) {
                counts.put((LiveVoteResponseType) row[0], (Long) row[1]);
            }
        }
        return counts;
    }

    public Optional<EsLiveVoteResponse> findByVoteIdAndUserId(Long esLiveVoteId, Long userId) {
        if (esLiveVoteId == null || userId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsLiveVoteResponse r where r.esLiveVoteId = :voteId and r.userId = :userId",
                    EsLiveVoteResponse.class)
                    .setParameter("voteId", esLiveVoteId)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public int deleteByVoteId(Long esLiveVoteId) {
        if (esLiveVoteId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int deleted = session.createMutationQuery(
                    "delete from EsLiveVoteResponse r where r.esLiveVoteId = :voteId")
                    .setParameter("voteId", esLiveVoteId)
                    .executeUpdate();
            tx.commit();
            return deleted;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }
}