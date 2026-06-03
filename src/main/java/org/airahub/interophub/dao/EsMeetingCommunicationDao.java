package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.EsMeetingCommunication.CommunicationStatus;

public class EsMeetingCommunicationDao extends GenericDao<EsMeetingCommunication, Long> {

    public EsMeetingCommunicationDao() {
        super(EsMeetingCommunication.class);
    }

    public EsMeetingCommunication saveOrUpdate(EsMeetingCommunication communication) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsMeetingCommunication merged = (EsMeetingCommunication) session.merge(communication);
                tx.commit();
                return merged;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public List<EsMeetingCommunication> findByMeetingId(Long esMeetingId) {
        if (esMeetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingCommunication c where c.esMeetingId = :mid"
                            + " order by c.createdAt desc",
                    EsMeetingCommunication.class)
                    .setParameter("mid", esMeetingId)
                    .getResultList();
        }
    }

    /**
     * Returns the next communication scheduled to send for a meeting.
     *
     * "Next" is the earliest row in SCHEDULED status with a non-null
     * scheduledSendAt.
     */
    public Optional<EsMeetingCommunication> findNextScheduledByMeetingId(Long esMeetingId) {
        if (esMeetingId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingCommunication c"
                            + " where c.esMeetingId = :mid"
                            + " and c.status = :scheduled"
                            + " and c.scheduledSendAt is not null"
                            + " order by c.scheduledSendAt asc",
                    EsMeetingCommunication.class)
                    .setParameter("mid", esMeetingId)
                    .setParameter("scheduled", CommunicationStatus.SCHEDULED)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    /**
     * Returns all SCHEDULED communications whose scheduledSendAt <= asOf and
     * whose status is still SCHEDULED. Used by the background scheduler to find
     * work to do.
     */
    public List<EsMeetingCommunication> findDueToSend(LocalDateTime asOf) {
        if (asOf == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingCommunication c"
                            + " where c.status = :scheduled"
                            + " and c.scheduledSendAt <= :asOf",
                    EsMeetingCommunication.class)
                    .setParameter("scheduled", CommunicationStatus.SCHEDULED)
                    .setParameter("asOf", asOf)
                    .getResultList();
        }
    }

    public List<EsMeetingCommunication> findAllRecent(int limit) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingCommunication c order by c.createdAt desc",
                    EsMeetingCommunication.class)
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    /**
     * Cancels all DRAFT or SCHEDULED communications for a meeting, recording who
     * cancelled them and when.
     *
     * @return number of rows updated
     */
    public int cancelPendingForMeeting(Long esMeetingId, Long cancelledByUserId, LocalDateTime cancelledAt) {
        if (esMeetingId == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                int updated = session.createNativeMutationQuery(
                        "UPDATE es_meeting_communication"
                                + " SET status = 'CANCELLED',"
                                + "     cancelled_at = :cancelledAt,"
                                + "     cancelled_by_user_id = :cancelledBy,"
                                + "     cancellation_reason = 'Meeting was cancelled',"
                                + "     updated_at = :cancelledAt"
                                + " WHERE es_meeting_id = :mid"
                                + "   AND status IN ('DRAFT','SCHEDULED')")
                        .setParameter("mid", esMeetingId)
                        .setParameter("cancelledAt", cancelledAt)
                        .setParameter("cancelledBy", cancelledByUserId)
                        .executeUpdate();
                tx.commit();
                return updated;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    /**
     * Atomic compare-and-swap: transitions status from SCHEDULED to SENDING.
     * Returns the number of rows affected (1 = claimed, 0 = already taken or not
     * in SCHEDULED state).
     */
    public int claimForSending(Long esMeetingCommunicationId) {
        if (esMeetingCommunicationId == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                int updated = session.createNativeMutationQuery(
                        "UPDATE es_meeting_communication"
                                + " SET status = 'SENDING', sent_started_at = :now, updated_at = :now"
                                + " WHERE es_meeting_communication_id = :id"
                                + "   AND status = 'SCHEDULED'")
                        .setParameter("id", esMeetingCommunicationId)
                        .setParameter("now", LocalDateTime.now(ZoneOffset.UTC))
                        .executeUpdate();
                tx.commit();
                return updated;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    /**
     * Marks a communication as SENT after all emails have been dispatched.
     */
    public void markSent(Long esMeetingCommunicationId, LocalDateTime completedAt) {
        if (esMeetingCommunicationId == null) {
            return;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                session.createNativeMutationQuery(
                        "UPDATE es_meeting_communication"
                                + " SET status = 'SENT', sent_completed_at = :completedAt, updated_at = :completedAt"
                                + " WHERE es_meeting_communication_id = :id")
                        .setParameter("id", esMeetingCommunicationId)
                        .setParameter("completedAt", completedAt)
                        .executeUpdate();
                tx.commit();
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    /**
     * Marks a communication as FAILED and records the error message.
     */
    public void markFailed(Long esMeetingCommunicationId, String lastError) {
        if (esMeetingCommunicationId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                session.createNativeMutationQuery(
                        "UPDATE es_meeting_communication"
                                + " SET status = 'FAILED', last_error = :err, updated_at = :now"
                                + " WHERE es_meeting_communication_id = :id")
                        .setParameter("id", esMeetingCommunicationId)
                        .setParameter("err", lastError)
                        .setParameter("now", now)
                        .executeUpdate();
                tx.commit();
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }
}
