package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsInterest;

public class EsInterestDao extends GenericDao<EsInterest, Long> {

    public EsInterestDao() {
        super(EsInterest.class);
    }

    /**
     * Returns true if a duplicate interest exists for this campaign+topic
     * combination.
     * Priority order: user_id first (if non-null), then email_normalized (if
     * non-blank).
     * Blank-email anonymous voters are not checked here by design.
     */
    public boolean existsByUserOrEmail(Long campaignId, Long topicId, Long userId, String emailNormalized) {
        if (campaignId == null || topicId == null) {
            return false;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (userId != null) {
                Long count = session.createQuery(
                        "select count(i.esInterestId) from EsInterest i"
                                + " where i.esCampaignId = :cid and i.esTopicId = :tid and i.userId = :uid",
                        Long.class)
                        .setParameter("cid", campaignId)
                        .setParameter("tid", topicId)
                        .setParameter("uid", userId)
                        .uniqueResult();
                if (count != null && count > 0) {
                    return true;
                }
            }
            if (emailNormalized != null && !emailNormalized.isBlank()) {
                Long count = session.createQuery(
                        "select count(i.esInterestId) from EsInterest i"
                                + " where i.esCampaignId = :cid and i.esTopicId = :tid"
                                + " and i.emailNormalized = :email",
                        Long.class)
                        .setParameter("cid", campaignId)
                        .setParameter("tid", topicId)
                        .setParameter("email", emailNormalized)
                        .uniqueResult();
                if (count != null && count > 0) {
                    return true;
                }
            }
            return false;
        }
    }

    public List<EsInterest> findByCampaignAndTopic(Long campaignId, Long topicId) {
        if (campaignId == null || topicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInterest i where i.esCampaignId = :cid and i.esTopicId = :tid"
                            + " order by i.createdAt asc",
                    EsInterest.class)
                    .setParameter("cid", campaignId)
                    .setParameter("tid", topicId)
                    .getResultList();
        }
    }

    public Optional<EsInterest> findByUserAndCampaignAndTopic(Long userId, Long campaignId, Long topicId) {
        if (userId == null || campaignId == null || topicId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInterest i where i.userId = :uid and i.esCampaignId = :cid"
                            + " and i.esTopicId = :tid",
                    EsInterest.class)
                    .setParameter("uid", userId)
                    .setParameter("cid", campaignId)
                    .setParameter("tid", topicId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public long countByCampaignAndTopic(Long campaignId, Long topicId) {
        if (campaignId == null || topicId == null) {
            return 0L;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(i.esInterestId) from EsInterest i"
                            + " where i.esCampaignId = :cid and i.esTopicId = :tid",
                    Long.class)
                    .setParameter("cid", campaignId)
                    .setParameter("tid", topicId)
                    .uniqueResult();
            return count == null ? 0L : count;
        }
    }

    public EsInterest saveOrUpdate(EsInterest interest) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsInterest merged = (EsInterest) session.merge(interest);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }
}
