package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.DandelionSyncConfig;

public class DandelionSyncConfigDao extends GenericDao<DandelionSyncConfig, Long> {
    public DandelionSyncConfigDao() {
        super(DandelionSyncConfig.class);
    }

    public Optional<DandelionSyncConfig> findActiveForSpace(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from DandelionSyncConfig c where c.esTopicSpaceId = :spaceId and c.active = true"
                            + " order by c.configId asc",
                    DandelionSyncConfig.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Optional<DandelionSyncConfig> findFirstForSpace(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from DandelionSyncConfig c where c.esTopicSpaceId = :spaceId order by c.configId asc",
                    DandelionSyncConfig.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    /**
     * Active, sync-enabled configs across every Topic Space. Drives the
     * scheduler and admin "which spaces are syncing" overview.
     */
    public List<DandelionSyncConfig> findAllEnabled() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from DandelionSyncConfig c where c.active = true and c.syncEnabled = true"
                            + " order by c.esTopicSpaceId asc",
                    DandelionSyncConfig.class)
                    .getResultList();
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
