package org.airahub.interophub.dao;

import java.util.Optional;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsCommunicationBundleAudit;
import org.airahub.interophub.model.EsCommunicationBundleComponentValue;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;

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

    public EsCommunicationBundleComponentValue saveEditableValue(EsCommunicationBundleComponentValue value,
            String semanticKey) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                EsCommunicationBundle bundle =
                        EsCommunicationBundleDao.requireEditable(session, value.getBundleId());
                EsCommunicationBundleTemplateComponent component =
                        session.find(EsCommunicationBundleTemplateComponent.class, value.getComponentId());
                if (!bundle.getTemplateId().equals(value.getTemplateId())
                        || component == null || !component.getTemplateId().equals(bundle.getTemplateId())
                        || (component.getKind() != EsCommunicationBundleTemplateComponent.Kind.TEXT
                                && component.getKind() != EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST)) {
                    throw new IllegalStateException("Only narrative components in this bundle's template can be edited.");
                }
                if (component.getKind() == EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST) {
                    if (value.getContentText() != null) {
                        throw new IllegalArgumentException("A structured list must use JSON text items.");
                    }
                    org.airahub.interophub.service.CommunicationBundleStructuredList.items(value.getContentJson());
                } else if (value.getContentJson() != null) {
                    throw new IllegalArgumentException("A text field must not contain structured data.");
                }
                EsCommunicationBundleComponentValue saved = session.createQuery(
                        "from EsCommunicationBundleComponentValue where bundleId = :bundleId"
                                + " and componentId = :componentId",
                        EsCommunicationBundleComponentValue.class)
                        .setParameter("bundleId", value.getBundleId())
                        .setParameter("componentId", value.getComponentId())
                        .uniqueResultOptional().orElse(null);
                if (saved == null) {
                    value.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
                    value.setUpdatedAt(value.getCreatedAt());
                    session.persist(value);
                    saved = value;
                } else {
                    saved.setContentText(value.getContentText());
                    saved.setContentJson(value.getContentJson());
                    saved.setUpdatedByUserId(value.getUpdatedByUserId());
                    saved.setUpdatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
                }
                bundle.setUpdatedByUserId(value.getUpdatedByUserId());
                bundle.setUpdatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
                session.persist(EsCommunicationBundleAudit.event(bundle.getBundleId(), null,
                        "COMPONENT_VALUE_UPDATED", "Narrative component updated: " + semanticKey + ".",
                        value.getUpdatedByUserId()));
                EsCommunicationBundleDao.validatePublishedContent(session, bundle);
                transaction.commit();
                return saved;
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
