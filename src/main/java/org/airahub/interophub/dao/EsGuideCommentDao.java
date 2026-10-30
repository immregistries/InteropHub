package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.model.EsGuideComment;
import org.hibernate.Session;

public class EsGuideCommentDao extends GenericDao<EsGuideComment, Long> {
    public EsGuideCommentDao() {
        super(EsGuideComment.class);
    }

    public Optional<EsGuideComment> findById(Session session, Long id) {
        return Optional.ofNullable(session.get(EsGuideComment.class, id));
    }

    public List<EsGuideComment> findByDocumentId(Session session, Long documentId) {
        return session.createQuery(
                "from EsGuideComment c where c.guideDocumentId = :id order by c.createdAt asc, c.guideCommentId asc",
                EsGuideComment.class)
                .setParameter("id", documentId)
                .getResultList();
    }
}
