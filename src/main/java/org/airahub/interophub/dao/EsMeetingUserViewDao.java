package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeetingUserView;

public class EsMeetingUserViewDao extends GenericDao<EsMeetingUserView, Long> {

    public EsMeetingUserViewDao() {
        super(EsMeetingUserView.class);
    }

    public Optional<EsMeetingUserView> findByUserIdAndMeetingId(Long userId, Long esMeetingId) {
        if (userId == null || esMeetingId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingUserView v where v.userId = :userId and v.esMeetingId = :meetingId",
                    EsMeetingUserView.class)
                    .setParameter("userId", userId)
                    .setParameter("meetingId", esMeetingId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public EsMeetingUserView saveOrUpdate(EsMeetingUserView meetingUserView) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsMeetingUserView merged = (EsMeetingUserView) session.merge(meetingUserView);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public List<RecentlyViewedMeetingRow> findRecentMeetingsByUserId(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsMeetingUserViewDao$RecentlyViewedMeetingRow(" +
                            " m.esMeetingId, m.meetingName, m.esTopicSpaceId, m.scheduledStart, v.lastViewedAt)" +
                            " from EsMeetingUserView v, EsMeeting m" +
                            " where v.userId = :userId" +
                            " and m.esMeetingId = v.esMeetingId" +
                            " order by v.lastViewedAt desc",
                    RecentlyViewedMeetingRow.class)
                    .setParameter("userId", userId)
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    public record RecentlyViewedMeetingRow(Long esMeetingId, String meetingName, Long esTopicSpaceId,
            LocalDateTime scheduledStart, LocalDateTime lastViewedAt) {
    }
}
