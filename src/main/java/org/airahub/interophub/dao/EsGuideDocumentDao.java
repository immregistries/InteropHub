package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.model.EsGuideDocument;
import org.hibernate.Session;

public class EsGuideDocumentDao extends GenericDao<EsGuideDocument, Long> {
    public EsGuideDocumentDao() {
        super(EsGuideDocument.class);
    }

    public Optional<EsGuideDocument> findByKey(Session session, String documentKey) {
        return session.createQuery(
                "from EsGuideDocument d where d.documentKey = :key", EsGuideDocument.class)
                .setParameter("key", documentKey)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    public List<EsGuideDocument> findByTopicId(Session session, Long topicId) {
        return session.createQuery(
                "from EsGuideDocument d where d.esTopicId = :topicId order by d.documentKey asc",
                EsGuideDocument.class)
                .setParameter("topicId", topicId)
                .getResultList();
    }

    public List<EsGuideDocument> findAllOrdered(Session session) {
        return session.createQuery(
                "from EsGuideDocument d order by d.documentKey asc", EsGuideDocument.class)
                .getResultList();
    }
}
