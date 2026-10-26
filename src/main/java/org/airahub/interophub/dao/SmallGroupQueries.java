package org.airahub.interophub.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsTopicMeeting;

/**
 * Read-only lookups Small Groups need from topics, series, and bundles.
 * This does not create or change those records.
 */
public final class SmallGroupQueries {

    private SmallGroupQueries() {
    }

    public static List<EsTopicMeeting> seriesForTopic(Long topicId) {
        if (topicId == null) {
            return List.of();
        }
        return seriesForTopics(List.of(topicId));
    }

    public static List<EsTopicMeeting> seriesForTopics(Collection<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeeting m where m.esTopicId in :topicIds order by m.meetingName",
                    EsTopicMeeting.class)
                    .setParameter("topicIds", topicIds)
                    .getResultList();
        }
    }

    public static boolean topicHasOrientationBundle(Long topicId) {
        if (topicId == null) {
            return false;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(b.bundleId) from EsCommunicationBundle b, EsCommunicationBundlePurpose p"
                            + " where b.purposeId = p.purposeId and p.purposeKey = :key"
                            + " and b.esTopicId = :topicId and b.status <> :retired",
                    Long.class)
                    .setParameter("key", "TOPIC_ORIENTATION")
                    .setParameter("topicId", topicId)
                    .setParameter("retired", EsCommunicationBundle.Status.RETIRED)
                    .uniqueResult();
            return count != null && count > 0;
        }
    }

    /** A published Starter Packet whose publish (or create) time falls in the cycle. */
    public static boolean starterPacketDuring(Long topicId, LocalDate cycleStart, LocalDate closeout) {
        if (topicId == null || cycleStart == null || closeout == null) {
            return false;
        }
        LocalDateTime start = cycleStart.atStartOfDay();
        LocalDateTime end = closeout.plusDays(1).atStartOfDay();
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(b.bundleId) from EsCommunicationBundle b, EsCommunicationBundlePurpose p"
                            + " where b.purposeId = p.purposeId and p.purposeKey = :key"
                            + " and b.esTopicId = :topicId and b.status = :published"
                            + " and ((b.publishedAt >= :start and b.publishedAt < :end)"
                            + " or (b.publishedAt is null and b.createdAt >= :start and b.createdAt < :end))",
                    Long.class)
                    .setParameter("key", "STARTER_PACKET")
                    .setParameter("topicId", topicId)
                    .setParameter("published", EsCommunicationBundle.Status.PUBLISHED)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .uniqueResult();
            return count != null && count > 0;
        }
    }
}
