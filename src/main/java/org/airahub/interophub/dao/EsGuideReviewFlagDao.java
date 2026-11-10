package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.model.EsGuideReviewFlag;
import org.hibernate.Session;

public class EsGuideReviewFlagDao extends GenericDao<EsGuideReviewFlag, Long> {
    public EsGuideReviewFlagDao() {
        super(EsGuideReviewFlag.class);
    }

    public Optional<EsGuideReviewFlag> findById(Session session, Long id) {
        return Optional.ofNullable(session.get(EsGuideReviewFlag.class, id));
    }

    public List<EsGuideReviewFlag> findByDocumentId(Session session, Long documentId, boolean includeCleared) {
        String query = includeCleared
                ? "from EsGuideReviewFlag f where f.guideDocumentId = :id order by f.createdAt asc, f.reviewFlagId asc"
                : "from EsGuideReviewFlag f where f.guideDocumentId = :id and f.clearedAt is null"
                        + " order by f.createdAt asc, f.reviewFlagId asc";
        return session.createQuery(query, EsGuideReviewFlag.class)
                .setParameter("id", documentId)
                .getResultList();
    }

    public Optional<EsGuideReviewFlag> findActiveByAnchor(Session session, Long documentId, String anchor) {
        List<EsGuideReviewFlag> rows = session.createQuery(
                "from EsGuideReviewFlag f where f.guideDocumentId = :id and f.anchorId = :anchor and f.clearedAt is null",
                EsGuideReviewFlag.class)
                .setParameter("id", documentId)
                .setParameter("anchor", anchor)
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public long countActive(Session session, Long documentId) {
        Long count = session.createQuery(
                "select count(f) from EsGuideReviewFlag f where f.guideDocumentId = :id and f.clearedAt is null",
                Long.class)
                .setParameter("id", documentId)
                .uniqueResult();
        return count == null ? 0L : count;
    }

    /** Newest active flags first, capped by the caller. */
    public List<EsGuideReviewFlag> newestActive(Session session, Long documentId, int limit) {
        return session.createQuery(
                "from EsGuideReviewFlag f where f.guideDocumentId = :id and f.clearedAt is null"
                        + " order by f.createdAt desc, f.reviewFlagId desc",
                EsGuideReviewFlag.class)
                .setParameter("id", documentId)
                .setMaxResults(limit)
                .getResultList();
    }
}
