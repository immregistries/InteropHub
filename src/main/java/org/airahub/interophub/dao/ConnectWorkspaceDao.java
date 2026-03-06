package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.ConnectWorkspace;

public class ConnectWorkspaceDao extends GenericDao<ConnectWorkspace, Long> {
    public ConnectWorkspaceDao() {
        super(ConnectWorkspace.class);
    }

    public List<ConnectWorkspace> findByTopicIdOrdered(Long topicId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from ConnectWorkspace cw where cw.topicId = :topicId order by cw.startDate asc, cw.workspaceId asc",
                    ConnectWorkspace.class)
                    .setParameter("topicId", topicId)
                    .getResultList();
        }
    }

    public List<ConnectWorkspace> findActiveOrderedByStartDate() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from ConnectWorkspace cw where cw.status = :status order by cw.startDate asc, cw.workspaceId asc",
                    ConnectWorkspace.class)
                    .setParameter("status", ConnectWorkspace.WorkspaceStatus.ACTIVE)
                    .getResultList();
        }
    }

    public ConnectWorkspace saveOrUpdate(ConnectWorkspace workspace) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            ConnectWorkspace merged = (ConnectWorkspace) session.merge(workspace);
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
