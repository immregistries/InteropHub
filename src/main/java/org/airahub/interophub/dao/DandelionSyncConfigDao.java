package org.airahub.interophub.dao;

import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.DandelionSyncConfig;

public class DandelionSyncConfigDao extends GenericDao<DandelionSyncConfig, Long> {
    public DandelionSyncConfigDao() {
        super(DandelionSyncConfig.class);
    }

    public Optional<DandelionSyncConfig> findActive() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from DandelionSyncConfig c where c.active = true order by c.configId asc",
                    DandelionSyncConfig.class)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Optional<DandelionSyncConfig> findFirst() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from DandelionSyncConfig c order by c.configId asc",
                    DandelionSyncConfig.class)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public DandelionSyncConfig saveOrUpdate(DandelionSyncConfig config) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            DandelionSyncConfig merged = (DandelionSyncConfig) session.merge(config);
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