package org.airahub.interophub.dao;

import java.util.List;
import java.util.function.Function;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsCommunicationBundleAudit;
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

    public EsCommunicationBundleResourcePlacement saveEditablePlacementWithAudit(
            EsCommunicationBundleResourcePlacement placement, Long userId) {
        return mutateEditable(placement.getBundleId(), session -> {
            EsCommunicationBundle bundle = session.find(EsCommunicationBundle.class, placement.getBundleId());
            EsCommunicationBundleTemplateComponent component =
                    session.find(EsCommunicationBundleTemplateComponent.class, placement.getComponentId());
            EsTopicResource resource = session.find(EsTopicResource.class, placement.getTopicResourceId());
            if (component == null || !bundle.getTemplateId().equals(component.getTemplateId())
                    || (component.getKind() != EsCommunicationBundleTemplateComponent.Kind.RESOURCE
                            && component.getKind()
                                    != EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION)) {
                throw new IllegalArgumentException("Choose a resource role from this Orientation.");
            }
            if (resource == null || !bundle.getEsTopicId().equals(resource.getEsTopicId())
                    || resource.getStatus() != EsTopicResource.Status.ACTIVE) {
                throw new IllegalArgumentException("Choose an active resource from this Topic.");
            }
            List<EsCommunicationBundleResourcePlacement> existing = session.createQuery(
                    "from EsCommunicationBundleResourcePlacement where bundleId = :bundle"
                            + " and componentId = :component",
                    EsCommunicationBundleResourcePlacement.class)
                    .setParameter("bundle", placement.getBundleId())
                    .setParameter("component", placement.getComponentId())
                    .getResultList();
            if (component.getCardinality() == EsCommunicationBundleTemplateComponent.Cardinality.SINGLE
                    && !existing.isEmpty()) {
                throw new IllegalStateException("This template component accepts only one resource.");
            }
            if (existing.stream().anyMatch(value -> value.getDisplayOrder() == placement.getDisplayOrder())) {
                throw new IllegalArgumentException("A resource already uses this display order in the component.");
            }
            session.persist(placement);
            session.persist(EsCommunicationBundleAudit.event(placement.getBundleId(), null,
                    "RESOURCE_ADDED", "Resource " + resource.getTopicResourceId()
                            + " added to " + component.getSemanticKey() + ".", userId));
            bundle.setUpdatedByUserId(userId);
            bundle.setUpdatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            return placement;
        });
    }

    public void selectResource(Long bundleId, Long componentId, Long resourceId, Long userId) {
        mutateEditable(bundleId, session -> {
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
                Long replacedResourceId = placement.getTopicResourceId();
                if (!resourceId.equals(replacedResourceId)) {
                    placement.setContextNote(null);
                    session.persist(EsCommunicationBundleAudit.event(bundleId, null,
                            "RESOURCE_ROLE_REPLACED", "Resource role " + component.getSemanticKey()
                                    + " changed from resource " + replacedResourceId + " to " + resourceId + ".",
                            userId));
                }
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
            if (placement.getPlacementId() == null) {
                session.persist(placement);
            }
            session.persist(EsCommunicationBundleAudit.event(bundleId, null, "RESOURCE_SELECTED",
                    "Resource " + resourceId + " selected for " + component.getSemanticKey() + ".", userId));
            bundle.setUpdatedByUserId(userId);
            bundle.setUpdatedAt(java.time.LocalDateTime.now());
            return null;
        });
    }

    public void removeResource(Long bundleId, Long placementId, Long userId) {
        mutateEditable(bundleId, session -> {
            EsCommunicationBundleResourcePlacement placement =
                    session.find(EsCommunicationBundleResourcePlacement.class, placementId);
            if (placement == null || !bundleId.equals(placement.getBundleId())) {
                throw new IllegalArgumentException("Selected resource does not belong to this Orientation.");
            }
            EsCommunicationBundle bundle = session.find(EsCommunicationBundle.class, bundleId);
            EsCommunicationBundleTemplateComponent component =
                    session.find(EsCommunicationBundleTemplateComponent.class, placement.getComponentId());
            session.persist(EsCommunicationBundleAudit.event(bundleId, null, "RESOURCE_REMOVED",
                    "Resource " + placement.getTopicResourceId() + " removed from "
                            + (component == null ? "component " + placement.getComponentId()
                                    : component.getSemanticKey()) + ".",
                    userId));
            session.remove(placement);
            bundle.setUpdatedByUserId(userId);
            bundle.setUpdatedAt(java.time.LocalDateTime.now());
            return null;
        });
    }

    public void moveResource(Long bundleId, Long placementId, boolean up, Long userId) {
        mutateEditable(bundleId, session -> {
            var placement = session.find(EsCommunicationBundleResourcePlacement.class, placementId);
            if (placement == null || !bundleId.equals(placement.getBundleId())) {
                throw new IllegalArgumentException("Selected resource does not belong to this bundle.");
            }
            var component = session.find(EsCommunicationBundleTemplateComponent.class, placement.getComponentId());
            if (component == null || component.getKind() != EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION) {
                throw new IllegalArgumentException("Only a resource collection can be reordered.");
            }
            var placements = session.createQuery(
                    "from EsCommunicationBundleResourcePlacement where bundleId = :bundle and componentId = :component order by displayOrder",
                    EsCommunicationBundleResourcePlacement.class).setParameter("bundle", bundleId)
                    .setParameter("component", placement.getComponentId()).getResultList();
            int index = placements.indexOf(placement);
            int targetIndex = index + (up ? -1 : 1);
            if (index < 0 || targetIndex < 0 || targetIndex >= placements.size()) {
                throw new IllegalArgumentException("The resource is already at that end of the collection.");
            }
            var target = placements.get(targetIndex);
            int oldOrder = placement.getDisplayOrder();
            int targetOrder = target.getDisplayOrder();
            int temporaryOrder = 0;
            var used = placements.stream().map(EsCommunicationBundleResourcePlacement::getDisplayOrder)
                    .collect(java.util.stream.Collectors.toSet());
            while (used.contains(temporaryOrder)) {
                temporaryOrder = Math.addExact(temporaryOrder, 1);
            }
            placement.setDisplayOrder(temporaryOrder);
            session.flush();
            target.setDisplayOrder(oldOrder);
            session.flush();
            placement.setDisplayOrder(targetOrder);
            session.persist(EsCommunicationBundleAudit.event(bundleId, null, "RESOURCE_REORDERED",
                    "Resource " + placement.getTopicResourceId() + " moved " + (up ? "up" : "down")
                            + " in " + component.getSemanticKey() + ".", userId));
            var bundle = session.find(EsCommunicationBundle.class, bundleId);
            bundle.setUpdatedByUserId(userId);
            bundle.setUpdatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            return null;
        });
    }

    public void updateContextNote(Long bundleId, Long placementId, String note, Long userId) {
        mutateEditable(bundleId, session -> {
            var placement = session.find(EsCommunicationBundleResourcePlacement.class, placementId);
            if (placement == null || !bundleId.equals(placement.getBundleId())) {
                throw new IllegalArgumentException("Selected resource does not belong to this bundle.");
            }
            if (note != null && note.length() > 20000) {
                throw new IllegalArgumentException("The resource context note must be at most 20000 characters.");
            }
            placement.setContextNote(note == null || note.isBlank() ? null : note.trim());
            session.persist(EsCommunicationBundleAudit.event(bundleId, null, "RESOURCE_CONTEXT_UPDATED",
                    "Context note updated for resource " + placement.getTopicResourceId() + ".", userId));
            var bundle = session.find(EsCommunicationBundle.class, bundleId);
            bundle.setUpdatedByUserId(userId);
            bundle.setUpdatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            return null;
        });
    }

    private <T> T mutateEditable(Long bundleId, Function<Session, T> mutation) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                EsCommunicationBundle bundle =
                        EsCommunicationBundleDao.requireEditable(session, bundleId);
                T result = mutation.apply(session);
                EsCommunicationBundleDao.validatePublishedContent(session, bundle);
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
