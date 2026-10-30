package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.model.EsGuideDisposition;
import org.hibernate.Session;

public class EsGuideDispositionDao extends GenericDao<EsGuideDisposition, String> {
    public EsGuideDispositionDao() {
        super(EsGuideDisposition.class);
    }

    public List<EsGuideDisposition> findAllOrdered(Session session) {
        return session.createQuery(
                "from EsGuideDisposition d order by d.displayOrder asc", EsGuideDisposition.class)
                .getResultList();
    }
}
