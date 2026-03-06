package org.airahub.interophub.dao;

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
}
