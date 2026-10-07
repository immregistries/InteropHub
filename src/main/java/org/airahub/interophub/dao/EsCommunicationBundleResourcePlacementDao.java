package org.airahub.interophub.dao;

import java.util.List;
import java.util.function.Function;
import jakarta.persistence.LockModeType;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsCommunicationBundleResourcePlacement;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.model.EsTopicResource;
import org.hibernate.Session;

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

    public void selectResource(Long bundleId, Long componentId, Long resourceId, Long userId) {
        mutateDraft(bundleId, session -> {
            EsCommunicationBundle bundle = session.find(EsCommunicationBundle.class, bundleId);
            EsCommunicationBundleTemplateComponent component =
                    session.find(EsCommunicationBundleTemplateComponent.class, componentId);
            EsTopicResource resource = session.find(EsTopicResource.class, resourceId);
            if (component == null || !bundle.getTemplateId().equals(component.getTemplateId())
                    || (component.getKind() != EsCommunicationBundleTemplateComponent.Kind.RESOURCE
                            && component.getKind() != EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION)) {
                throw new IllegalArgumentException("Choose a resource role from this Orientation.");
            }
            if (resource == null || !bundle.getEsTopicId().equals(resource.getEsTopicId())
                    || resource.getStatus() != EsTopicResource.Status.ACTIVE) {
                throw new IllegalArgumentException("Choose an active resource from this Topic.");
            }
            List<EsCommunicationBundleResourcePlacement> placements = session.createQuery(
                    "from EsCommunicationBundleResourcePlacement where bundleId = :bundle and componentId = :component"
                            + " order by displayOrder", EsCommunicationBundleResourcePlacement.class)
                    .setParameter("bundle", bundleId).setParameter("component", componentId).getResultList();
            boolean single = component.getCardinality() == EsCommunicationBundleTemplateComponent.Cardinality.SINGLE;
            if (!single && placements.stream().anyMatch(p -> resourceId.equals(p.getTopicResourceId()))) {
                throw new IllegalArgumentException("This resource is already selected for this role.");
            }
            EsCommunicationBundleResourcePlacement placement;
            if (single && !placements.isEmpty()) {
                placement = placements.get(0);
            } else {
                placement = new EsCommunicationBundleResourcePlacement();
                placement.setBundleId(bundleId);
                placement.setEsTopicId(bundle.getEsTopicId());
                placement.setTemplateId(bundle.getTemplateId());
                placement.setComponentId(componentId);
                placement.setDisplayOrder(single ? 0 : placements.stream()
                        .mapToInt(EsCommunicationBundleResourcePlacement::getDisplayOrder).max().orElse(-1) + 1);
            }
            placement.setTopicResourceId(resourceId);
            placement.setCreatedByUserId(userId);
            placement.setCreatedAt(java.time.LocalDateTime.now());
            placement.setContextNote(null);
            if (placement.getPlacementId() == null) {
                session.persist(placement);
            }
            bundle.setUpdatedByUserId(userId);
            bundle.setUpdatedAt(java.time.LocalDateTime.now());
            return null;
        });
    }

    public void removeResource(Long bundleId, Long placementId, Long userId) {
        mutateDraft(bundleId, session -> {
            EsCommunicationBundleResourcePlacement placement =
                    session.find(EsCommunicationBundleResourcePlacement.class, placementId);
            if (placement == null || !bundleId.equals(placement.getBundleId())) {
                throw new IllegalArgumentException("Selected resource does not belong to this Orientation.");
            }
            session.remove(placement);
            EsCommunicationBundle bundle = session.find(EsCommunicationBundle.class, bundleId);
            bundle.setUpdatedByUserId(userId);
            bundle.setUpdatedAt(java.time.LocalDateTime.now());
            return null;
        });
    }

    private <T> T mutateDraft(Long bundleId, Function<Session, T> mutation) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                EsCommunicationBundle bundle =
                        session.find(EsCommunicationBundle.class, bundleId, LockModeType.PESSIMISTIC_WRITE);
                if (bundle == null || bundle.getStatus() != EsCommunicationBundle.Status.DRAFT) {
                    throw new IllegalStateException("Only a draft Orientation can be edited.");
                }
                T result = mutation.apply(session);
                transaction.commit();
                return result;
            } catch (RuntimeException ex) {
                try {
                    transaction.rollback();
                } catch (RuntimeException rollback) {
                    ex.addSuppressed(rollback);
                }
                throw ex;
            }
        }
    }
}
