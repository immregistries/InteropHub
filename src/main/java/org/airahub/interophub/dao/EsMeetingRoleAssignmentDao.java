package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeetingRoleAssignment;
import org.airahub.interophub.model.MeetingRoleType;

public class EsMeetingRoleAssignmentDao extends GenericDao<EsMeetingRoleAssignment, Long> {

    public EsMeetingRoleAssignmentDao() {
        super(EsMeetingRoleAssignment.class);
    }

    public Optional<EsMeetingRoleAssignment> findCurrentByMeetingIdAndRoleType(Long esMeetingId,
            MeetingRoleType roleType) {
        if (esMeetingId == null || roleType == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingRoleAssignment r where r.esMeetingId = :meetingId"
                            + " and r.roleType = :roleType and r.endedAt is null"
                            + " order by r.startedAt desc, r.esMeetingRoleAssignmentId desc",
                    EsMeetingRoleAssignment.class)
                    .setParameter("meetingId", esMeetingId)
                    .setParameter("roleType", roleType)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsMeetingRoleAssignment> findOpenAssignmentsByMeetingId(Long esMeetingId) {
        if (esMeetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingRoleAssignment r where r.esMeetingId = :meetingId"
                            + " and r.endedAt is null order by r.startedAt asc, r.esMeetingRoleAssignmentId asc",
                    EsMeetingRoleAssignment.class)
                    .setParameter("meetingId", esMeetingId)
                    .getResultList();
        }
    }

    public int endOpenAssignmentsByMeetingIdAndRoleType(Long esMeetingId, MeetingRoleType roleType,
            LocalDateTime endedAt, Long endedByUserId) {
        if (esMeetingId == null || roleType == null || endedAt == null || endedByUserId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update EsMeetingRoleAssignment r set r.endedAt = :endedAt"
                            + " where r.esMeetingId = :meetingId and r.roleType = :roleType and r.endedAt is null")
                    .setParameter("endedAt", endedAt)
                    .setParameter("meetingId", esMeetingId)
                    .setParameter("roleType", roleType)
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