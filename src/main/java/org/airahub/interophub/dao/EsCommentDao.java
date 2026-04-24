package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsComment;

public class EsCommentDao extends GenericDao<EsComment, Long> {

    public EsCommentDao() {
        super(EsComment.class);
    }

    public List<EsComment> findByCampaignId(Long campaignId) {
        if (campaignId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsComment c where c.esCampaignId = :cid order by c.createdAt asc",
                    EsComment.class)
                    .setParameter("cid", campaignId)
                    .getResultList();
        }
    }

    public List<EsComment> findByCampaignIdAndType(Long campaignId, EsComment.CommentType commentType) {
        if (campaignId == null || commentType == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsComment c where c.esCampaignId = :cid and c.commentType = :type"
                            + " order by c.createdAt asc",
                    EsComment.class)
                    .setParameter("cid", campaignId)
                    .setParameter("type", commentType)
                    .getResultList();
        }
    }

    public List<EsComment> findByCampaignIdAndTopicId(Long campaignId, Long topicId) {
        if (campaignId == null || topicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsComment c where c.esCampaignId = :cid and c.esTopicId = :tid"
                            + " order by c.createdAt asc",
                    EsComment.class)
                    .setParameter("cid", campaignId)
                    .setParameter("tid", topicId)
                    .getResultList();
        }
    }

    public List<EsComment> findByTopicId(Long topicId) {
        if (topicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsComment c where c.esTopicId = :tid and c.commentType = :type"
                            + " order by c.createdAt asc",
                    EsComment.class)
                    .setParameter("tid", topicId)
                    .setParameter("type", EsComment.CommentType.TOPIC)
                    .getResultList();
        }
    }

    public List<TopicCommentCountRow> findTopicCommentCountsByCampaignId(Long campaignId) {
        if (campaignId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsCommentDao$TopicCommentCountRow(c.esTopicId, count(c.esCommentId))"
                            + " from EsComment c"
                            + " where c.esCampaignId = :cid"
                            + " and c.commentType = :topicType"
                            + " and c.esTopicId is not null"
                            + " group by c.esTopicId",
                    TopicCommentCountRow.class)
                    .setParameter("cid", campaignId)
                    .setParameter("topicType", EsComment.CommentType.TOPIC)
                    .getResultList();
        }
    }

    public List<EsComment> findByUserAndCampaign(Long campaignId, Long userId) {
        if (campaignId == null || userId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsComment c where c.esCampaignId = :cid and c.userId = :uid"
                            + " and c.commentType = :type order by c.createdAt asc",
                    EsComment.class)
                    .setParameter("cid", campaignId)
                    .setParameter("uid", userId)
                    .setParameter("type", EsComment.CommentType.TOPIC)
                    .getResultList();
        }
    }

    public int deleteByCampaignId(Long campaignId) {
        if (campaignId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int deleted = session.createMutationQuery(
                    "delete from EsComment c where c.esCampaignId = :cid")
                    .setParameter("cid", campaignId)
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

    public static final class TopicCommentCountRow {
        private final Long esTopicId;
        private final Long commentCount;

        public TopicCommentCountRow(Long esTopicId, Long commentCount) {
            this.esTopicId = esTopicId;
            this.commentCount = commentCount == null ? 0L : commentCount;
        }

        public Long getEsTopicId() {
            return esTopicId;
        }

        public Long getCommentCount() {
            return commentCount;
        }
    }
}
