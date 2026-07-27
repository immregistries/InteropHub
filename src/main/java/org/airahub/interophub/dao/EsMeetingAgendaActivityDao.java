package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeetingAgendaActivity;

public class EsMeetingAgendaActivityDao extends GenericDao<EsMeetingAgendaActivity, Long> {

    public EsMeetingAgendaActivityDao() {
        super(EsMeetingAgendaActivity.class);
    }

    public Optional<EsMeetingAgendaActivity> findCurrentByMeetingId(Long esMeetingId) {
        if (esMeetingId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingAgendaActivity a where a.esMeetingId = :meetingId"
                            + " and a.endedAt is null order by a.startedAt desc, a.esMeetingAgendaActivityId desc",
                    EsMeetingAgendaActivity.class)
                    .setParameter("meetingId", esMeetingId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsMeetingAgendaActivity> findByMeetingIdOrdered(Long esMeetingId) {
        if (esMeetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingAgendaActivity a where a.esMeetingId = :meetingId"
                            + " order by a.startedAt asc, a.esMeetingAgendaActivityId asc",
                    EsMeetingAgendaActivity.class)
                    .setParameter("meetingId", esMeetingId)
                    .getResultList();
        }
    }

    public List<EsMeetingAgendaActivity> findByAgendaItemId(Long esMeetingAgendaItemId) {
        if (esMeetingAgendaItemId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingAgendaActivity a where a.esMeetingAgendaItemId = :agendaItemId"
                            + " order by a.startedAt asc, a.esMeetingAgendaActivityId asc",
                    EsMeetingAgendaActivity.class)
                    .setParameter("agendaItemId", esMeetingAgendaItemId)
                    .getResultList();
        }
    }

    public int endOpenByMeetingId(Long esMeetingId, LocalDateTime endedAt, Long endedByUserId) {
        if (esMeetingId == null || endedAt == null || endedByUserId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update EsMeetingAgendaActivity a set a.endedAt = :endedAt, a.endedByUserId = :endedByUserId"
                            + " where a.esMeetingId = :meetingId and a.endedAt is null")
                    .setParameter("endedAt", endedAt)
                    .setParameter("endedByUserId", endedByUserId)
                    .setParameter("meetingId", esMeetingId)
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