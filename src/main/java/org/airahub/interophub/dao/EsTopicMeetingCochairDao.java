package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicMeetingCochair;
import org.airahub.interophub.model.TopicMeetingCochairStatus;

public class EsTopicMeetingCochairDao extends GenericDao<EsTopicMeetingCochair, Long> {

    public EsTopicMeetingCochairDao() {
        super(EsTopicMeetingCochair.class);
    }

    public List<EsTopicMeetingCochair> findActiveByTopicMeetingId(Long esTopicMeetingId) {
        if (esTopicMeetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeetingCochair c where c.esTopicMeetingId = :meetingId"
                            + " and c.status = :status order by c.createdAt asc, c.esTopicMeetingCochairId asc",
                    EsTopicMeetingCochair.class)
                    .setParameter("meetingId", esTopicMeetingId)
                    .setParameter("status", TopicMeetingCochairStatus.ACTIVE)
                    .getResultList();
        }
    }

    public Optional<EsTopicMeetingCochair> findByTopicMeetingIdAndUserId(Long esTopicMeetingId, Long userId) {
        if (esTopicMeetingId == null || userId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeetingCochair c where c.esTopicMeetingId = :meetingId"
                            + " and c.userId = :userId order by c.createdAt desc",
                    EsTopicMeetingCochair.class)
                    .setParameter("meetingId", esTopicMeetingId)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }
}