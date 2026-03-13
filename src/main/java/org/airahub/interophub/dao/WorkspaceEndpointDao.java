package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.WorkspaceEndpoint;
import org.hibernate.Transaction;

public class WorkspaceEndpointDao extends GenericDao<WorkspaceEndpoint, Long> {
    public WorkspaceEndpointDao() {
        super(WorkspaceEndpoint.class);
    }

    public List<WorkspaceEndpoint> findBySystemId(Long systemId) {
        if (systemId == null)
            return List.of();
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from WorkspaceEndpoint we where we.systemId = :systemId order by we.createdAt asc",
                    WorkspaceEndpoint.class)
                    .setParameter("systemId", systemId)
                    .getResultList();
        }
    }

    public WorkspaceEndpoint saveOrUpdate(WorkspaceEndpoint endpoint) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            endpoint.setUpdatedAt(LocalDateTime.now());
            WorkspaceEndpoint merged = (WorkspaceEndpoint) session.merge(endpoint);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null)
                tx.rollback();
            throw ex;
        }
    }
}
