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
}
