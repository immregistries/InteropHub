package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsSubscription;

public class EsSubscriptionDao extends GenericDao<EsSubscription, Long> {

    public EsSubscriptionDao() {
        super(EsSubscription.class);
    }

    /**
     * Returns all subscriptions (any status) for a given email and subscription
     * type.
     * Used to find a GENERAL_ES subscription for dedup before insert.
     */
    public List<EsSubscription> findByEmailNormalizedAndType(
            String emailNormalized, EsSubscription.SubscriptionType subscriptionType) {
        if (emailNormalized == null || subscriptionType == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSubscription s where s.emailNormalized = :email"
                            + " and s.subscriptionType = :type order by s.createdAt asc",
                    EsSubscription.class)
                    .setParameter("email", emailNormalized)
                    .setParameter("type", subscriptionType)
                    .getResultList();
        }
    }

    /**
     * Returns a topic-specific subscription (any status) for a given email.
     * Used for dedup before insert.
     */
    public Optional<EsSubscription> findByEmailNormalizedAndTopic(String emailNormalized, Long esTopicId) {
        if (emailNormalized == null || esTopicId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSubscription s where s.emailNormalized = :email"
                            + " and s.esTopicId = :topicId"
                            + " and s.subscriptionType = :type",
                    EsSubscription.class)
                    .setParameter("email", emailNormalized)
                    .setParameter("topicId", esTopicId)
                    .setParameter("type", EsSubscription.SubscriptionType.TOPIC)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    /**
     * Returns a GENERAL_ES subscription (any status) for a given email.
     * Used for dedup before insert.
     */
    public Optional<EsSubscription> findGeneralByEmailNormalized(String emailNormalized) {
        if (emailNormalized == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSubscription s where s.emailNormalized = :email"
                            + " and s.subscriptionType = :type and s.esTopicId is null",
                    EsSubscription.class)
                    .setParameter("email", emailNormalized)
                    .setParameter("type", EsSubscription.SubscriptionType.GENERAL_ES)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsSubscription> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSubscription s where s.userId = :uid order by s.createdAt asc",
                    EsSubscription.class)
                    .setParameter("uid", userId)
                    .getResultList();
        }
    }

    /**
     * Returns active (SUBSCRIBED) subscriptions for a specific topic.
     */
    public List<EsSubscription> findActiveByTopicId(Long esTopicId) {
        if (esTopicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSubscription s where s.esTopicId = :topicId and s.status = :status"
                            + " order by s.createdAt asc",
                    EsSubscription.class)
                    .setParameter("topicId", esTopicId)
                    .setParameter("status", EsSubscription.SubscriptionStatus.SUBSCRIBED)
                    .getResultList();
        }
    }

    public Optional<EsSubscription> findByUnsubscribeTokenHash(byte[] tokenHash) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSubscription s where s.unsubscribeTokenHash = :hash",
                    EsSubscription.class)
                    .setParameter("hash", tokenHash)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Set<Long> findActiveTopicIdsByEmailAndTopicIds(String emailNormalized, List<Long> topicIds) {
        if (emailNormalized == null || emailNormalized.isBlank() || topicIds == null || topicIds.isEmpty()) {
            return Set.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Long> result = session.createQuery(
                    "select s.esTopicId from EsSubscription s"
                            + " where s.emailNormalized = :email"
                            + " and s.subscriptionType = :type"
                            + " and s.status = :status"
                            + " and s.esTopicId in :topicIds",
                    Long.class)
                    .setParameter("email", emailNormalized)
                    .setParameter("type", EsSubscription.SubscriptionType.TOPIC)
                    .setParameter("status", EsSubscription.SubscriptionStatus.SUBSCRIBED)
                    .setParameterList("topicIds", topicIds)
                    .getResultList();
            return Set.copyOf(result);
        }
    }

    public EsSubscription saveOrUpdate(EsSubscription subscription) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsSubscription merged = (EsSubscription) session.merge(subscription);
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
