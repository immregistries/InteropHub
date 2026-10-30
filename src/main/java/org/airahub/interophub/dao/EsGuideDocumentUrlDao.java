package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.model.EsGuideDocumentUrl;
import org.hibernate.Session;

public class EsGuideDocumentUrlDao extends GenericDao<EsGuideDocumentUrl, Long> {
    public EsGuideDocumentUrlDao() {
        super(EsGuideDocumentUrl.class);
    }

    public List<EsGuideDocumentUrl> findByDocumentId(Session session, Long documentId) {
        return session.createQuery(
                "from EsGuideDocumentUrl u where u.guideDocumentId = :id order by u.sortOrder asc, u.guideDocumentUrlId asc",
                EsGuideDocumentUrl.class)
                .setParameter("id", documentId)
                .getResultList();
    }

    public Optional<EsGuideDocumentUrl> findByUrl(Session session, String url) {
        return session.createQuery(
                "from EsGuideDocumentUrl u where u.url = :url", EsGuideDocumentUrl.class)
                .setParameter("url", url)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    public void deleteByDocumentId(Session session, Long documentId) {
        session.createMutationQuery("delete from EsGuideDocumentUrl u where u.guideDocumentId = :id")
                .setParameter("id", documentId)
                .executeUpdate();
    }
}
