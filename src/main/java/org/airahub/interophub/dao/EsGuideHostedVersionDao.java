package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.model.EsGuideHostedVersion;
import org.hibernate.Session;

public class EsGuideHostedVersionDao extends GenericDao<EsGuideHostedVersion, Long> {
    public EsGuideHostedVersionDao() {
        super(EsGuideHostedVersion.class);
    }

    /** Newest upload first. The first row for a label is the one that label serves. */
    public List<EsGuideHostedVersion> findByDocumentId(Session session, Long documentId) {
        return session.createQuery(
                "from EsGuideHostedVersion v where v.guideDocumentId = :id"
                        + " order by v.uploadedAt desc, v.guideHostedVersionId desc",
                EsGuideHostedVersion.class)
                .setParameter("id", documentId)
                .getResultList();
    }
}
