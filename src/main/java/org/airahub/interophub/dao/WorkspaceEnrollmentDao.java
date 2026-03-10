package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.WorkspaceEnrollment;

public class WorkspaceEnrollmentDao extends GenericDao<WorkspaceEnrollment, Long> {
    public WorkspaceEnrollmentDao() {
        super(WorkspaceEnrollment.class);
    }

    public Optional<WorkspaceEnrollment> findByWorkspaceAndUser(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from WorkspaceEnrollment we where we.workspaceId = :workspaceId and we.userId = :userId",
                    WorkspaceEnrollment.class)
                    .setParameter("workspaceId", workspaceId)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public WorkspaceEnrollment saveOrUpdate(WorkspaceEnrollment enrollment) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            WorkspaceEnrollment merged = (WorkspaceEnrollment) session.merge(enrollment);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public List<WorkspaceEnrollment> findByWorkspaceId(Long workspaceId) {
        if (workspaceId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from WorkspaceEnrollment we where we.workspaceId = :workspaceId order by we.createdAt asc, we.enrollmentId asc",
                    WorkspaceEnrollment.class)
                    .setParameter("workspaceId", workspaceId)
                    .getResultList();
        }
    }

    public long countPendingForWorkspaceIds(List<Long> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            return 0L;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(we.enrollmentId) from WorkspaceEnrollment we where we.workspaceId in (:workspaceIds) and we.state = :state",
                    Long.class)
                    .setParameter("workspaceIds", workspaceIds)
                    .setParameter("state", WorkspaceEnrollment.EnrollmentState.PENDING)
                    .uniqueResult();
            return count == null ? 0L : count;
        }
    }
}
