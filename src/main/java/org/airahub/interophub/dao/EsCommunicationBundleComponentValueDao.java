package org.airahub.interophub.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        Map<Long, String> keys = new HashMap<>();
        keys.put(value.getComponentId(), semanticKey);
        return saveEditableValues(List.of(value), keys).get(0);
    }

    public List<EsCommunicationBundleComponentValue> saveEditableValues(
            List<EsCommunicationBundleComponentValue> values, Map<Long, String> semanticKeys) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                Long bundleId = values.get(0).getBundleId();
                EsCommunicationBundle bundle = EsCommunicationBundleDao.requireEditable(session, bundleId);
                List<EsCommunicationBundleComponentValue> saved = new ArrayList<>();
                for (EsCommunicationBundleComponentValue value : values) {
                    if (value == null || !bundleId.equals(value.getBundleId())) {
                        throw new IllegalArgumentException("Narrative values must belong to one bundle.");
                    }
                    String semanticKey = semanticKeys == null ? null : semanticKeys.get(value.getComponentId());
                    saved.add(writeEditableValue(session, bundle, value, semanticKey));
                }
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

    private static EsCommunicationBundleComponentValue writeEditableValue(org.hibernate.Session session,
            EsCommunicationBundle bundle, EsCommunicationBundleComponentValue value, String semanticKey) {
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
        return saved;
    }
}
