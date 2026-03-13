package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.WorkspaceSystemContact;
import org.hibernate.Transaction;

public class WorkspaceSystemContactDao extends GenericDao<WorkspaceSystemContact, Long> {
    public WorkspaceSystemContactDao() {
        super(WorkspaceSystemContact.class);
    }

    public List<WorkspaceSystemContact> findBySystemId(Long systemId) {
        if (systemId == null)
            return List.of();
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from WorkspaceSystemContact wsc where wsc.systemId = :systemId order by wsc.createdAt asc",
                    WorkspaceSystemContact.class)
                    .setParameter("systemId", systemId)
                    .getResultList();
        }
    }

    public Optional<WorkspaceSystemContact> findBySystemAndUser(Long systemId, Long userId) {
        if (systemId == null || userId == null)
            return Optional.empty();
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from WorkspaceSystemContact wsc where wsc.systemId = :systemId and wsc.userId = :userId",
                    WorkspaceSystemContact.class)
                    .setParameter("systemId", systemId)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public WorkspaceSystemContact saveOrUpdate(WorkspaceSystemContact contact) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            WorkspaceSystemContact merged = (WorkspaceSystemContact) session.merge(contact);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null)
                tx.rollback();
            throw ex;
        }
    }
}
