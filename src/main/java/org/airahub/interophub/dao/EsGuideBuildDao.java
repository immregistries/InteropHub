package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.model.EsGuideBuild;
import org.hibernate.Session;

public class EsGuideBuildDao extends GenericDao<EsGuideBuild, Long> {
    public EsGuideBuildDao() {
        super(EsGuideBuild.class);
    }

    /** Newest received first. */
    public List<EsGuideBuild> findByDocumentId(Session session, Long documentId) {
        return session.createQuery(
                "from EsGuideBuild b where b.guideDocumentId = :id"
                        + " order by b.receivedAt desc, b.guideBuildId desc",
                EsGuideBuild.class)
                .setParameter("id", documentId)
                .getResultList();
    }

    /**
     * The newest build of every document that has one, in one query. Ties on
     * {@code receivedAt} go to the higher id.
     */
    public List<EsGuideBuild> findLatestPerDocument(Session session) {
        return session.createQuery(
                "from EsGuideBuild b where b.guideBuildId = ("
                        + " select max(b2.guideBuildId) from EsGuideBuild b2"
                        + " where b2.guideDocumentId = b.guideDocumentId"
                        + " and b2.receivedAt = (select max(b3.receivedAt) from EsGuideBuild b3"
                        + " where b3.guideDocumentId = b.guideDocumentId))",
                EsGuideBuild.class)
                .getResultList();
    }
}
