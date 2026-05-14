package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeeting;

public class EsMeetingDao extends GenericDao<EsMeeting, Long> {

    public EsMeetingDao() {
        super(EsMeeting.class);
    }

    public Optional<EsMeeting> findByMeetingKey(String meetingKey) {
        if (meetingKey == null || meetingKey.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeeting m where m.meetingKey = :key",
                    EsMeeting.class)
                    .setParameter("key", meetingKey.trim())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    /**
     * Returns upcoming meetings with status PROPOSED or FINALIZED,
     * scheduled in the future, ordered by scheduledStart ascending.
     */
    public List<EsMeeting> findUpcoming(int limit) {
        int maxRows = limit > 0 ? limit : 25;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeeting m where m.status in (:statuses)"
                            + " and m.scheduledStart >= :now"
                            + " order by m.scheduledStart asc",
                    EsMeeting.class)
                    .setParameterList("statuses", List.of(
                            EsMeeting.MeetingStatus.PROPOSED,
                            EsMeeting.MeetingStatus.FINALIZED))
                    .setParameter("now", LocalDateTime.now())
                    .setMaxResults(maxRows)
                    .getResultList();
        }
    }

    public List<EsMeeting> findByStatus(EsMeeting.MeetingStatus status) {
        if (status == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeeting m where m.status = :status order by m.scheduledStart asc",
                    EsMeeting.class)
                    .setParameter("status", status)
                    .getResultList();
        }
    }

    /**
     * Returns meetings whose scheduledStart falls within [start, end] inclusive,
     * ordered by scheduledStart ascending.
     */
    public List<EsMeeting> findBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeeting m where m.scheduledStart >= :start"
                            + " and m.scheduledStart <= :end"
                            + " order by m.scheduledStart asc",
                    EsMeeting.class)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .getResultList();
        }
    }

    /**
     * Cancels a meeting, recording the reason and timestamp.
     *
     * @return number of rows updated (0 or 1)
     */
    public int cancelMeeting(Long esMeetingId, String cancellationReason) {
        if (esMeetingId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update EsMeeting m set m.status = :status"
                            + ", m.cancelledAt = :now"
                            + ", m.cancellationReason = :reason"
                            + " where m.esMeetingId = :id"
                            + " and m.status != :cancelled")
                    .setParameter("status", EsMeeting.MeetingStatus.CANCELLED)
                    .setParameter("now", LocalDateTime.now())
                    .setParameter("reason", cancellationReason)
                    .setParameter("id", esMeetingId)
                    .setParameter("cancelled", EsMeeting.MeetingStatus.CANCELLED)
                    .executeUpdate();
            tx.commit();
            return updated;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    /**
     * Finalizes a meeting (transitions to FINALIZED status).
     *
     * @return number of rows updated (0 or 1)
     */
    public int finalizeMeeting(Long esMeetingId) {
        if (esMeetingId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update EsMeeting m set m.status = :status"
                            + ", m.finalizedAt = :now"
                            + " where m.esMeetingId = :id"
                            + " and m.status = :proposed")
                    .setParameter("status", EsMeeting.MeetingStatus.FINALIZED)
                    .setParameter("now", LocalDateTime.now())
                    .setParameter("id", esMeetingId)
                    .setParameter("proposed", EsMeeting.MeetingStatus.PROPOSED)
                    .executeUpdate();
            tx.commit();
            return updated;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    /**
     * Marks a meeting as completed.
     *
     * @return number of rows updated (0 or 1)
     */
    public int completeMeeting(Long esMeetingId) {
        if (esMeetingId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update EsMeeting m set m.status = :status"
                            + ", m.completedAt = :now"
                            + " where m.esMeetingId = :id"
                            + " and m.status = :finalized")
                    .setParameter("status", EsMeeting.MeetingStatus.COMPLETED)
                    .setParameter("now", LocalDateTime.now())
                    .setParameter("id", esMeetingId)
                    .setParameter("finalized", EsMeeting.MeetingStatus.FINALIZED)
                    .executeUpdate();
            tx.commit();
            return updated;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }
}
