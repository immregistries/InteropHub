package org.airahub.interophub.dao;

import java.util.Optional;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCommunicationBundleComponentValue;

public class EsCommunicationBundleComponentValueDao
        extends GenericDao<EsCommunicationBundleComponentValue, Long> {
    public EsCommunicationBundleComponentValueDao() {
        super(EsCommunicationBundleComponentValue.class);
    }

    public Optional<EsCommunicationBundleComponentValue> findByBundleAndComponent(
            Long bundleId, Long componentId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCommunicationBundleComponentValue"
                            + " where bundleId = :bundleId and componentId = :componentId",
                    EsCommunicationBundleComponentValue.class)
                    .setParameter("bundleId", bundleId)
                    .setParameter("componentId", componentId)
                    .uniqueResultOptional();
        }
    }

    public List<EsCommunicationBundleComponentValue> findByBundleId(Long bundleId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCommunicationBundleComponentValue where bundleId = :bundleId"
                            + " order by componentId",
                    EsCommunicationBundleComponentValue.class)
                    .setParameter("bundleId", bundleId)
                    .getResultList();
        }
    }
}
