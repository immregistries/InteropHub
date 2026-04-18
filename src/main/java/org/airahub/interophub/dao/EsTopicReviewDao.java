package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicReview;

public class EsTopicReviewDao extends GenericDao<EsTopicReview, Long> {

    public EsTopicReviewDao() {
        super(EsTopicReview.class);
    }

    public List<EsTopicReview> findByCampaignIdAndUserId(Long campaignId, Long userId) {
        if (campaignId == null || userId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicReview r where r.esCampaignId = :campaignId and r.userId = :userId"
                            + " order by r.updatedAt desc, r.esTopicReviewId desc",
                    EsTopicReview.class)
                    .setParameter("campaignId", campaignId)
                    .setParameter("userId", userId)
                    .getResultList();
        }
    }

    public Optional<EsTopicReview> findByCampaignIdAndTopicIdAndUserId(Long campaignId, Long topicId, Long userId) {
        if (campaignId == null || topicId == null || userId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicReview r where r.esCampaignId = :campaignId"
                            + " and r.esTopicId = :topicId and r.userId = :userId",
                    EsTopicReview.class)
                    .setParameter("campaignId", campaignId)
                    .setParameter("topicId", topicId)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public long countReviewedTopicsByCampaignIdAndUserId(Long campaignId, Long userId) {
        if (campaignId == null || userId == null) {
            return 0L;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(r.esTopicReviewId) from EsTopicReview r"
                            + " where r.esCampaignId = :campaignId and r.userId = :userId",
                    Long.class)
                    .setParameter("campaignId", campaignId)
                    .setParameter("userId", userId)
                    .uniqueResult();
            return count == null ? 0L : count;
        }
    }

    public long countDistinctRespondersByCampaignId(Long campaignId) {
        if (campaignId == null) {
            return 0L;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(distinct r.userId) from EsTopicReview r where r.esCampaignId = :campaignId",
                    Long.class)
                    .setParameter("campaignId", campaignId)
                    .uniqueResult();
            return count == null ? 0L : count;
        }
    }

    public List<ResponderRow> findRespondersByCampaignId(Long campaignId) {
        if (campaignId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsTopicReviewDao$ResponderRow("
                            + " u.userId, u.displayName, u.email, u.emailNormalized, count(r.esTopicReviewId))"
                            + " from EsTopicReview r, User u"
                            + " where r.esCampaignId = :campaignId and r.userId = u.userId"
                            + " group by u.userId, u.displayName, u.email, u.emailNormalized"
                            + " order by lower(coalesce(u.displayName, u.emailNormalized, u.email)) asc",
                    ResponderRow.class)
                    .setParameter("campaignId", campaignId)
                    .getResultList();
        }
    }

    public List<TopicSummaryRow> findTopicSummaryByCampaignIdAcrossActiveTopics(Long campaignId) {
        if (campaignId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsTopicReviewDao$TopicSummaryRow("
                            + " t.esTopicId, t.topicName,"
                            + " avg(r.communityValueScore),"
                            + " count(r.esTopicReviewId),"
                            + " sum(case when r.communityValueScore >= 3 then 1 else 0 end),"
                            + " sum(case when r.communityValueScore >= 4 then 1 else 0 end))"
                            + " from EsTopic t"
                            + " left join EsTopicReview r"
                            + "   on r.esTopicId = t.esTopicId and r.esCampaignId = :campaignId"
                            + " where t.status = :activeStatus"
                            + " group by t.esTopicId, t.topicName"
                            + " order by case when avg(r.communityValueScore) is null then 1 else 0 end asc,"
                            + " avg(r.communityValueScore) desc,"
                            + " count(r.esTopicReviewId) desc,"
                            + " sum(case when r.communityValueScore >= 4 then 1 else 0 end) desc,"
                            + " sum(case when r.communityValueScore >= 3 then 1 else 0 end) desc,"
                            + " lower(t.topicName) asc",
                    TopicSummaryRow.class)
                    .setParameter("campaignId", campaignId)
                    .setParameter("activeStatus", EsTopic.EsTopicStatus.ACTIVE)
                    .getResultList();
        }
    }

    public EsTopicReview saveOrUpdate(EsTopicReview review) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicReview merged = (EsTopicReview) session.merge(review);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public static final class ResponderRow {
        private final Long userId;
        private final String displayName;
        private final String email;
        private final String emailNormalized;
        private final Long reviewCount;

        public ResponderRow(Long userId, String displayName, String email, String emailNormalized, Long reviewCount) {
            this.userId = userId;
            this.displayName = displayName;
            this.email = email;
            this.emailNormalized = emailNormalized;
            this.reviewCount = reviewCount == null ? 0L : reviewCount;
        }

        public Long getUserId() {
            return userId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getEmail() {
            return email;
        }

        public String getEmailNormalized() {
            return emailNormalized;
        }

        public Long getReviewCount() {
            return reviewCount;
        }
    }

    public static final class TopicSummaryRow {
        private final Long esTopicId;
        private final String topicName;
        private final Double averageScore;
        private final Long reviewCount;
        private final Long countScore3Plus;
        private final Long countScore4Plus;

        public TopicSummaryRow(Long esTopicId, String topicName, Double averageScore, Long reviewCount,
                Long countScore3Plus, Long countScore4Plus) {
            this.esTopicId = esTopicId;
            this.topicName = topicName;
            this.averageScore = averageScore;
            this.reviewCount = reviewCount == null ? 0L : reviewCount;
            this.countScore3Plus = countScore3Plus == null ? 0L : countScore3Plus;
            this.countScore4Plus = countScore4Plus == null ? 0L : countScore4Plus;
        }

        public Long getEsTopicId() {
            return esTopicId;
        }

        public String getTopicName() {
            return topicName;
        }

        public Double getAverageScore() {
            return averageScore;
        }

        public Long getReviewCount() {
            return reviewCount;
        }

        public Long getCountScore3Plus() {
            return countScore3Plus;
        }

        public Long getCountScore4Plus() {
            return countScore4Plus;
        }
    }
}
