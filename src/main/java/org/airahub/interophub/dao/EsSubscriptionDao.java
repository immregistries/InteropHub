package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDateTime;
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
     * Returns active (SUBSCRIBED or CHAMPION) subscriptions for a specific topic.
     */
    public List<EsSubscription> findActiveByTopicId(Long esTopicId) {
        if (esTopicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSubscription s where s.esTopicId = :topicId"
                            + " and s.status in (:statuses)"
                            + " order by s.createdAt asc",
                    EsSubscription.class)
                    .setParameter("topicId", esTopicId)
                    .setParameterList("statuses", List.of(
                            EsSubscription.SubscriptionStatus.SUBSCRIBED,
                            EsSubscription.SubscriptionStatus.CHAMPION))
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

    /**
     * Sets user_id on all es_subscription rows that match the given email and
     * currently have user_id IS NULL. Safe to call repeatedly (idempotent).
     *
     * @return the number of rows updated
     */
    public int updateUserIdWhereNullByEmailNormalized(String emailNormalized, Long userId) {
        if (emailNormalized == null || userId == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                int updated = session.createMutationQuery(
                        "update EsSubscription s set s.userId = :uid"
                                + " where s.emailNormalized = :email and s.userId is null")
                        .setParameter("uid", userId)
                        .setParameter("email", emailNormalized)
                        .executeUpdate();
                tx.commit();
                return updated;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
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
                            + " and s.status in (:statuses)"
                            + " and s.esTopicId in :topicIds",
                    Long.class)
                    .setParameter("email", emailNormalized)
                    .setParameter("type", EsSubscription.SubscriptionType.TOPIC)
                    .setParameterList("statuses", List.of(
                            EsSubscription.SubscriptionStatus.SUBSCRIBED,
                            EsSubscription.SubscriptionStatus.CHAMPION))
                    .setParameterList("topicIds", topicIds)
                    .getResultList();
            return Set.copyOf(result);
        }
    }

    public Set<Long> findActiveTopicIdsByUserOrEmailAndTopicIds(Long userId, String emailNormalized,
            List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Set.of();
        }
        boolean hasUser = userId != null;
        boolean hasEmail = emailNormalized != null && !emailNormalized.isBlank();
        if (!hasUser && !hasEmail) {
            return Set.of();
        }

        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder();
            hql.append("select distinct s.esTopicId from EsSubscription s");
            hql.append(" where s.subscriptionType = :type");
            hql.append(" and s.status in (:statuses)");
            hql.append(" and s.esTopicId in :topicIds");
            hql.append(" and (");
            if (hasUser) {
                hql.append("s.userId = :userId");
            }
            if (hasEmail) {
                if (hasUser) {
                    hql.append(" or ");
                }
                hql.append("s.emailNormalized = :email");
            }
            hql.append(")");

            var query = session.createQuery(hql.toString(), Long.class)
                    .setParameter("type", EsSubscription.SubscriptionType.TOPIC)
                    .setParameterList("statuses", List.of(
                            EsSubscription.SubscriptionStatus.SUBSCRIBED,
                            EsSubscription.SubscriptionStatus.CHAMPION))
                    .setParameterList("topicIds", topicIds);
            if (hasUser) {
                query.setParameter("userId", userId);
            }
            if (hasEmail) {
                query.setParameter("email", emailNormalized.trim());
            }
            return Set.copyOf(query.getResultList());
        }
    }

    public Optional<EsSubscription> findByUserOrEmailAndTopic(Long userId, String emailNormalized, Long esTopicId) {
        if (esTopicId == null) {
            return Optional.empty();
        }
        boolean hasUser = userId != null;
        boolean hasEmail = emailNormalized != null && !emailNormalized.isBlank();
        if (!hasUser && !hasEmail) {
            return Optional.empty();
        }

        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder();
            hql.append("from EsSubscription s");
            hql.append(" where s.subscriptionType = :type");
            hql.append(" and s.esTopicId = :topicId");
            hql.append(" and (");
            if (hasUser) {
                hql.append("s.userId = :userId");
            }
            if (hasEmail) {
                if (hasUser) {
                    hql.append(" or ");
                }
                hql.append("s.emailNormalized = :email");
            }
            hql.append(")");
            hql.append(" order by s.updatedAt desc, s.createdAt desc");

            var query = session.createQuery(hql.toString(), EsSubscription.class)
                    .setParameter("type", EsSubscription.SubscriptionType.TOPIC)
                    .setParameter("topicId", esTopicId)
                    .setMaxResults(1);
            if (hasUser) {
                query.setParameter("userId", userId);
            }
            if (hasEmail) {
                query.setParameter("email", emailNormalized.trim());
            }
            return query.uniqueResultOptional();
        }
    }

    public List<CampaignTopicSubscriptionCountRow> findTopicSubscriptionCountsBySourceCampaignId(
            Long sourceCampaignId, int limit) {
        if (sourceCampaignId == null) {
            return List.of();
        }
        int maxRows = limit > 0 ? limit : 25;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsSubscriptionDao$CampaignTopicSubscriptionCountRow("
                            + " t.topicName, count(s.esSubscriptionId))"
                            + " from EsSubscription s, EsTopic t"
                            + " where s.sourceCampaignId = :campaignId"
                            + " and s.esTopicId = t.esTopicId"
                            + " and s.subscriptionType = :subscriptionType"
                            + " and s.status = :status"
                            + " group by t.esTopicId, t.topicName"
                            + " order by count(s.esSubscriptionId) desc, lower(t.topicName) asc",
                    CampaignTopicSubscriptionCountRow.class)
                    .setParameter("campaignId", sourceCampaignId)
                    .setParameter("subscriptionType", EsSubscription.SubscriptionType.TOPIC)
                    .setParameter("status", EsSubscription.SubscriptionStatus.SUBSCRIBED)
                    .setMaxResults(maxRows)
                    .getResultList();
        }
    }

    public int deleteBySourceCampaignId(Long sourceCampaignId) {
        if (sourceCampaignId == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                int deleted = session.createMutationQuery(
                        "delete from EsSubscription s where s.sourceCampaignId = :campaignId")
                        .setParameter("campaignId", sourceCampaignId)
                        .executeUpdate();
                tx.commit();
                return deleted;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public EsSubscription saveOrUpdate(EsSubscription subscription) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsSubscription merged = (EsSubscription) session.merge(subscription);
                tx.commit();
                return merged;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    /**
     * Returns all subscriptions for a given user filtered by subscription type.
     */
    public List<EsSubscription> findByUserIdAndType(Long userId, EsSubscription.SubscriptionType subscriptionType) {
        if (userId == null || subscriptionType == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSubscription s where s.userId = :uid"
                            + " and s.subscriptionType = :type order by s.createdAt asc",
                    EsSubscription.class)
                    .setParameter("uid", userId)
                    .setParameter("type", subscriptionType)
                    .getResultList();
        }
    }

    /**
     * Returns all CHAMPION subscriptions for a specific topic.
     */
    public List<EsSubscription> findChampionsByTopicId(Long esTopicId) {
        if (esTopicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSubscription s where s.esTopicId = :topicId"
                            + " and s.status = :status order by s.createdAt asc",
                    EsSubscription.class)
                    .setParameter("topicId", esTopicId)
                    .setParameter("status", EsSubscription.SubscriptionStatus.CHAMPION)
                    .getResultList();
        }
    }

    /**
     * Sets the status on a subscription row. When setting UNSUBSCRIBED, pass the
     * unsubscribedAt timestamp; otherwise pass null.
     *
     * @return number of rows updated (0 or 1)
     */
    public int setTopicSubscriptionStatus(Long esSubscriptionId, EsSubscription.SubscriptionStatus newStatus,
            LocalDateTime unsubscribedAt) {
        if (esSubscriptionId == null || newStatus == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                int updated = session.createNativeMutationQuery(
                        "UPDATE es_subscription SET status = :status, unsubscribed_at = :unsubAt"
                                + " WHERE es_subscription_id = :id")
                        .setParameter("status", newStatus.name())
                        .setParameter("unsubAt", unsubscribedAt)
                        .setParameter("id", esSubscriptionId)
                        .executeUpdate();
                tx.commit();
                return updated;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    /**
     * Promotes a SUBSCRIBED topic subscription to CHAMPION status.
     * Throws IllegalArgumentException if the subscription is not of type TOPIC.
     *
     * @return number of rows updated (1 on success, 0 if not found or not currently
     *         SUBSCRIBED)
     */
    public int promoteToChampion(Long esSubscriptionId) {
        if (esSubscriptionId == null) {
            return 0;
        }
        Optional<EsSubscription> sub = findById(esSubscriptionId);
        if (sub.isEmpty()) {
            return 0;
        }
        if (sub.get().getSubscriptionType() != EsSubscription.SubscriptionType.TOPIC) {
            throw new IllegalArgumentException(
                    "CHAMPION status is only valid for TOPIC subscriptions (id=" + esSubscriptionId + ")");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                int updated = session.createNativeMutationQuery(
                        "UPDATE es_subscription SET status = 'CHAMPION'"
                                + " WHERE es_subscription_id = :id"
                                + " AND subscription_type = 'TOPIC'"
                                + " AND status = 'SUBSCRIBED'")
                        .setParameter("id", esSubscriptionId)
                        .executeUpdate();
                tx.commit();
                return updated;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    /**
     * Demotes a CHAMPION subscription back to SUBSCRIBED status.
     *
     * @return number of rows updated (1 on success, 0 if not found or not currently
     *         CHAMPION)
     */
    public int demoteFromChampion(Long esSubscriptionId) {
        if (esSubscriptionId == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                int updated = session.createNativeMutationQuery(
                        "UPDATE es_subscription SET status = 'SUBSCRIBED'"
                                + " WHERE es_subscription_id = :id"
                                + " AND status = 'CHAMPION'")
                        .setParameter("id", esSubscriptionId)
                        .executeUpdate();
                tx.commit();
                return updated;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    /**
     * Sets a topic subscription to UNSUBSCRIBED and records the timestamp.
     *
     * @return number of rows updated (1 on success, 0 if not found)
     */
    public int unsubscribeFromTopic(Long esSubscriptionId) {
        return setTopicSubscriptionStatus(
                esSubscriptionId,
                EsSubscription.SubscriptionStatus.UNSUBSCRIBED,
                LocalDateTime.now());
    }

    /**
     * Returns active CHAMPION subscriptions for any of the given topic IDs.
     * Used when building the champion recipient group for a meeting communication.
     */
    public List<EsSubscription> findActiveChampionsByTopicIds(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSubscription s where s.esTopicId in :ids"
                            + " and s.subscriptionType = :type"
                            + " and s.status = :champion",
                    EsSubscription.class)
                    .setParameterList("ids", topicIds)
                    .setParameter("type", EsSubscription.SubscriptionType.TOPIC)
                    .setParameter("champion", EsSubscription.SubscriptionStatus.CHAMPION)
                    .getResultList();
        }
    }

    /**
     * Returns active SUBSCRIBED subscriptions for any of the given topic IDs.
     * Used when building the topic-subscriber recipient group for a meeting
     * communication.
     */
    public List<EsSubscription> findActiveSubscribersByTopicIds(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSubscription s where s.esTopicId in :ids"
                            + " and s.subscriptionType = :type"
                            + " and s.status = :subscribed",
                    EsSubscription.class)
                    .setParameterList("ids", topicIds)
                    .setParameter("type", EsSubscription.SubscriptionType.TOPIC)
                    .setParameter("subscribed", EsSubscription.SubscriptionStatus.SUBSCRIBED)
                    .getResultList();
        }
    }

    /**
     * Returns all active (SUBSCRIBED or CHAMPION) subscriptions for a given email,
     * joined with topic name for TOPIC subscriptions.
     */
    public List<ActiveSubscriptionRow> findAllActiveByEmailNormalized(String emailNormalized) {
        if (emailNormalized == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createNativeQuery(
                    "SELECT s.es_subscription_id, s.subscription_type, s.status, s.es_topic_id, t.topic_name"
                            + " FROM es_subscription s"
                            + " LEFT JOIN es_topic t ON s.es_topic_id = t.es_topic_id"
                            + " WHERE s.email_normalized = :email"
                            + " AND s.status IN ('SUBSCRIBED', 'CHAMPION')"
                            + " ORDER BY s.subscription_type ASC, COALESCE(t.topic_name, '') ASC",
                    Object[].class)
                    .setParameter("email", emailNormalized)
                    .getResultList();
            List<ActiveSubscriptionRow> result = new java.util.ArrayList<>();
            for (Object[] row : rows) {
                result.add(new ActiveSubscriptionRow(
                        ((Number) row[0]).longValue(),
                        EsSubscription.SubscriptionType.valueOf((String) row[1]),
                        EsSubscription.SubscriptionStatus.valueOf((String) row[2]),
                        row[3] != null ? ((Number) row[3]).longValue() : null,
                        (String) row[4]));
            }
            return result;
        }
    }

    /**
     * Sets all SUBSCRIBED or CHAMPION subscriptions for an email to UNSUBSCRIBED.
     *
     * @return number of rows updated
     */
    public int unsubscribeAllByEmailNormalized(String emailNormalized) {
        if (emailNormalized == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                int updated = session.createNativeMutationQuery(
                        "UPDATE es_subscription SET status = 'UNSUBSCRIBED', unsubscribed_at = NOW()"
                                + " WHERE email_normalized = :email"
                                + " AND status IN ('SUBSCRIBED', 'CHAMPION')")
                        .setParameter("email", emailNormalized)
                        .executeUpdate();
                tx.commit();
                return updated;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    /**
     * Deletes a single subscription row by its primary key.
     *
     * @return number of rows deleted (0 or 1)
     */
    public int removeById(Long esSubscriptionId) {
        if (esSubscriptionId == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                int deleted = session.createNativeMutationQuery(
                        "DELETE FROM es_subscription WHERE es_subscription_id = :id")
                        .setParameter("id", esSubscriptionId)
                        .executeUpdate();
                tx.commit();
                return deleted;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    /**
     * Returns true if the given email has a GENERAL_ES subscription with
     * UNSUBSCRIBED status. Used as an email guard across all send paths.
     */
    public boolean hasGeneralUnsubscribed(String emailNormalized) {
        if (emailNormalized == null) {
            return false;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(s.esSubscriptionId) from EsSubscription s"
                            + " where s.emailNormalized = :email"
                            + " and s.subscriptionType = :type"
                            + " and s.status = :status",
                    Long.class)
                    .setParameter("email", emailNormalized)
                    .setParameter("type", EsSubscription.SubscriptionType.GENERAL_ES)
                    .setParameter("status", EsSubscription.SubscriptionStatus.UNSUBSCRIBED)
                    .getSingleResult();
            return count != null && count > 0;
        }
    }

    public static final class ActiveSubscriptionRow {
        private final Long esSubscriptionId;
        private final EsSubscription.SubscriptionType subscriptionType;
        private final EsSubscription.SubscriptionStatus status;
        private final Long esTopicId;
        private final String topicName;

        public ActiveSubscriptionRow(Long esSubscriptionId,
                EsSubscription.SubscriptionType subscriptionType,
                EsSubscription.SubscriptionStatus status,
                Long esTopicId,
                String topicName) {
            this.esSubscriptionId = esSubscriptionId;
            this.subscriptionType = subscriptionType;
            this.status = status;
            this.esTopicId = esTopicId;
            this.topicName = topicName;
        }

        public Long getEsSubscriptionId() {
            return esSubscriptionId;
        }

        public EsSubscription.SubscriptionType getSubscriptionType() {
            return subscriptionType;
        }

        public EsSubscription.SubscriptionStatus getStatus() {
            return status;
        }

        public Long getEsTopicId() {
            return esTopicId;
        }

        public String getTopicName() {
            return topicName;
        }
    }

    public static final class CampaignTopicSubscriptionCountRow {
        private final String topicName;
        private final Long subscriptionCount;

        public CampaignTopicSubscriptionCountRow(String topicName, Long subscriptionCount) {
            this.topicName = topicName;
            this.subscriptionCount = subscriptionCount == null ? 0L : subscriptionCount;
        }

        public String getTopicName() {
            return topicName;
        }

        public Long getSubscriptionCount() {
            return subscriptionCount;
        }
    }
}
