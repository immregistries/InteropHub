package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.model.EsGuideReviewFlag;
import org.hibernate.Session;

public class EsGuideReviewFlagDao extends GenericDao<EsGuideReviewFlag, Long> {
    public EsGuideReviewFlagDao() {
        super(EsGuideReviewFlag.class);
    }

    public List<EsGuideReviewFlag> findActiveByDocumentId(Session session, Long documentId) {
        return session.createQuery(
                "from EsGuideReviewFlag f where f.guideDocumentId = :id and f.clearedAt is null order by f.createdAt asc",
                EsGuideReviewFlag.class)
                .setParameter("id", documentId)
                .getResultList();
    }
}
