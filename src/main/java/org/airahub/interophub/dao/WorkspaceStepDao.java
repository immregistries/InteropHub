package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.WorkspaceStep;

public class WorkspaceStepDao extends GenericDao<WorkspaceStep, Long> {
    public WorkspaceStepDao() {
        super(WorkspaceStep.class);
    }

    public List<WorkspaceStep> findByWorkspaceOrdered(Long workspaceId) {
        if (workspaceId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from WorkspaceStep ws where ws.workspaceId = :workspaceId order by ws.sortOrder asc, ws.stepId asc",
                    WorkspaceStep.class)
                    .setParameter("workspaceId", workspaceId)
                    .getResultList();
        }
    }

    public WorkspaceStep saveOrUpdate(WorkspaceStep workspaceStep) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            WorkspaceStep merged = (WorkspaceStep) session.merge(workspaceStep);
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
