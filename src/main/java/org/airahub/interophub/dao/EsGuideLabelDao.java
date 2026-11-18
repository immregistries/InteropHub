package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.model.EsGuideLabel;
import org.hibernate.Session;

public class EsGuideLabelDao extends GenericDao<EsGuideLabel, Long> {
    public EsGuideLabelDao() {
        super(EsGuideLabel.class);
    }

    public List<EsGuideLabel> findByDocumentId(Session session, Long documentId) {
        return session.createQuery(
                "from EsGuideLabel l where l.guideDocumentId = :id order by l.label asc",
                EsGuideLabel.class)
                .setParameter("id", documentId)
                .getResultList();
    }

    /** The CURRENT label of every guide that has one. */
    public List<EsGuideLabel> findCurrent(Session session) {
        return session.createQuery("from EsGuideLabel l where l.displayState = 'CURRENT'", EsGuideLabel.class)
                .getResultList();
    }
}
