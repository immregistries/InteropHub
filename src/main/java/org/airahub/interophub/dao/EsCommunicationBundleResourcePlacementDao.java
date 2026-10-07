package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCommunicationBundleResourcePlacement;

public class EsCommunicationBundleResourcePlacementDao
        extends GenericDao<EsCommunicationBundleResourcePlacement, Long> {
    public EsCommunicationBundleResourcePlacementDao() {
        super(EsCommunicationBundleResourcePlacement.class);
    }

    public List<EsCommunicationBundleResourcePlacement> findByBundleAndComponent(
            Long bundleId, Long componentId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCommunicationBundleResourcePlacement"
                            + " where bundleId = :bundleId and componentId = :componentId"
                            + " order by displayOrder, placementId",
                    EsCommunicationBundleResourcePlacement.class)
                    .setParameter("bundleId", bundleId)
                    .setParameter("componentId", componentId)
                    .getResultList();
        }
    }

    public List<EsCommunicationBundleResourcePlacement> findByBundleOrdered(Long bundleId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select p from EsCommunicationBundleResourcePlacement p,"
                            + " EsCommunicationBundleTemplateComponent c"
                            + " where p.bundleId = :bundleId and c.componentId = p.componentId"
                            + " order by c.displayOrder, p.displayOrder, p.placementId",
                    EsCommunicationBundleResourcePlacement.class)
                    .setParameter("bundleId", bundleId)
                    .getResultList();
        }
    }
}
