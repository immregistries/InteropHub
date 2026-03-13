package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.WorkspaceSystem;
import org.hibernate.Transaction;

public class WorkspaceSystemDao extends GenericDao<WorkspaceSystem, Long> {
    public WorkspaceSystemDao() {
        super(WorkspaceSystem.class);
    }

    public List<WorkspaceSystem> findByWorkspaceIdAndContactUserId(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null)
            return List.of();
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select ws from WorkspaceSystem ws "
                            + "where ws.workspaceId = :workspaceId "
                            + "and exists (select 1 from WorkspaceSystemContact wsc "
                            + "            where wsc.systemId = ws.systemId and wsc.userId = :userId) "
                            + "order by ws.systemName asc",
                    WorkspaceSystem.class)
                    .setParameter("workspaceId", workspaceId)
                    .setParameter("userId", userId)
                    .getResultList();
        }
    }

    public WorkspaceSystem saveOrUpdate(WorkspaceSystem system) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            system.setUpdatedAt(LocalDateTime.now());
            WorkspaceSystem merged = (WorkspaceSystem) session.merge(system);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null)
                tx.rollback();
            throw ex;
        }
    }
}
