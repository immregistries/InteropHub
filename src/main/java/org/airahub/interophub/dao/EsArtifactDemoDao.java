package org.airahub.interophub.dao;

import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsArtifactDemo;

/** TEMPORARY - see {@link EsArtifactDemo}. */
public class EsArtifactDemoDao extends GenericDao<EsArtifactDemo, Long> {

    public EsArtifactDemoDao() {
        super(EsArtifactDemo.class);
    }

    public Optional<EsArtifactDemo> findBySlotKey(String slotKey) {
        if (slotKey == null || slotKey.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsArtifactDemo a where a.slotKey = :slotKey", EsArtifactDemo.class)
                    .setParameter("slotKey", slotKey)
                    .uniqueResultOptional();
        }
    }
}
