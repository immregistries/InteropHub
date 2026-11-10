package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Topic page totals. A row counts only when it is not deleted and not withdrawn.
     * That is the same rule as {@code TopicReferenceDigest.of}.
     */
    private static final String LIVE = "c.deletedAt is null and c.withdrawnAt is null and c.state <> 'WITHDRAWN'";

    public SectionCounts sectionCounts(Session session, Long documentId, LocalDateTime since) {
        SectionCounts counts = session.createQuery(
                "select new org.airahub.interophub.dao.EsGuideCommentDao$SectionCounts("
                        + "sum(case when c.parentCommentId is null and " + LIVE + " and c.state = 'OPEN' then 1 else 0 end),"
                        + "sum(case when c.parentCommentId is null and " + LIVE + " and c.state = 'DISPOSED' and c.closedAt is null then 1 else 0 end),"
                        + "sum(case when c.parentCommentId is null and " + LIVE + " and c.state = 'CLOSED' then 1 else 0 end),"
                        + "sum(case when c.parentCommentId is null and " + LIVE + " and c.updatedAt >= :since then 1 else 0 end),"
                        + "sum(case when c.parentCommentId is not null and " + LIVE + " and c.updatedAt >= :since then 1 else 0 end))"
                        + " from EsGuideComment c where c.guideDocumentId = :id",
                SectionCounts.class)
                .setParameter("id", documentId)
                .setParameter("since", since)
                .uniqueResult();
        return counts == null ? new SectionCounts(0L, 0L, 0L, 0L, 0L) : counts;
    }

    public LocalDateTime lastActivity(Session session, Long documentId) {
        return session.createQuery(
                "select max(c.updatedAt) from EsGuideComment c where c.guideDocumentId = :id and " + LIVE,
                LocalDateTime.class)
                .setParameter("id", documentId)
                .uniqueResult();
    }

    public long recentPeople(Session session, Long documentId, LocalDateTime since) {
        Long count = session.createQuery(
                "select count(distinct c.userId) from EsGuideComment c where c.guideDocumentId = :id and "
                        + LIVE + " and c.updatedAt >= :since",
                Long.class)
                .setParameter("id", documentId)
                .setParameter("since", since)
                .uniqueResult();
        return count == null ? 0L : count;
    }

    public List<EsGuideComment> openThreads(Session session, Long documentId, int limit) {
        return session.createQuery(
                "from EsGuideComment c where c.guideDocumentId = :id and c.parentCommentId is null and "
                        + LIVE + " and c.state = 'OPEN' order by c.updatedAt desc, c.guideCommentId desc",
                EsGuideComment.class)
                .setParameter("id", documentId)
                .setMaxResults(limit)
                .getResultList();
    }

    public Map<Long, Long> replyCounts(Session session, Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = session.createQuery(
                "select c.parentCommentId, count(c) from EsGuideComment c where c.parentCommentId in :ids and "
                        + LIVE + " group by c.parentCommentId",
                Object[].class)
                .setParameter("ids", parentIds)
                .getResultList();
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] instanceof Long parentId && row[1] instanceof Number count) {
                counts.put(parentId, count.longValue());
            }
        }
        return counts;
    }

    /** Aggregate totals for one guide. Null sums become zero. */
    public static final class SectionCounts {
        public final int open;
        public final int disposed;
        public final int closed;
        public final int recentComments;
        public final int recentReplies;

        public SectionCounts(Long open, Long disposed, Long closed, Long recentComments, Long recentReplies) {
            this.open = number(open);
            this.disposed = number(disposed);
            this.closed = number(closed);
            this.recentComments = number(recentComments);
            this.recentReplies = number(recentReplies);
        }

        private static int number(Long value) {
            return value == null ? 0 : value.intValue();
        }
    }
}
