package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicUserView;

public class EsTopicUserViewDao extends GenericDao<EsTopicUserView, Long> {

    public EsTopicUserViewDao() {
        super(EsTopicUserView.class);
    }

    public Optional<EsTopicUserView> findByUserIdAndTopicId(Long userId, Long esTopicId) {
        if (userId == null || esTopicId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicUserView v where v.userId = :userId and v.esTopicId = :topicId",
                    EsTopicUserView.class)
                    .setParameter("userId", userId)
                    .setParameter("topicId", esTopicId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public EsTopicUserView saveOrUpdate(EsTopicUserView topicUserView) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicUserView merged = (EsTopicUserView) session.merge(topicUserView);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public List<RecentlyViewedTopicRow> findRecentActiveTopicsByUserId(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsTopicUserViewDao$RecentlyViewedTopicRow(" +
                            " t.esTopicId, t.topicName, t.esTopicSpaceId, v.lastViewedAt, t.topicEmoji)" +
                            " from EsTopicUserView v, EsTopic t" +
                            " where v.userId = :userId" +
                            " and t.esTopicId = v.esTopicId" +
                            " and t.status = :status" +
                            " order by v.lastViewedAt desc",
                    RecentlyViewedTopicRow.class)
                    .setParameter("userId", userId)
                    .setParameter("status", EsTopic.EsTopicStatus.ACTIVE)
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    public record RecentlyViewedTopicRow(Long esTopicId, String topicName, Long esTopicSpaceId,
            LocalDateTime lastViewedAt, String topicEmoji) {
    }
}
