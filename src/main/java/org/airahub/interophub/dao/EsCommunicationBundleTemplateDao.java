package org.airahub.interophub.dao;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCommunicationBundlePurpose;
import org.airahub.interophub.model.EsCommunicationBundleTemplate;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.service.CommunicationBundleTemplateService;
import org.hibernate.Session;

public class EsCommunicationBundleTemplateDao extends GenericDao<EsCommunicationBundleTemplate, Long> {
    public EsCommunicationBundleTemplateDao() { super(EsCommunicationBundleTemplate.class); }

    public List<EsCommunicationBundleTemplate> findByPurpose(Long purposeId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from EsCommunicationBundleTemplate where purposeId = :purpose order by version desc",
                    EsCommunicationBundleTemplate.class).setParameter("purpose", purposeId).getResultList();
        }
    }

    public EsCommunicationBundleTemplate createDraftVersion(Long purposeId, Long sourceId, Long userId) {
        return transact(session -> {
            var purpose = session.find(EsCommunicationBundlePurpose.class, purposeId, LockModeType.PESSIMISTIC_WRITE);
            if (purpose == null || !purpose.isActive()) {
                throw new IllegalArgumentException("Choose an active product-defined Purpose.");
            }
            var source = sourceId == null ? null : session.find(EsCommunicationBundleTemplate.class, sourceId);
            if (sourceId != null && (source == null || !purposeId.equals(source.getPurposeId())
                    || source.getStatus() == EsCommunicationBundleTemplate.Status.DRAFT)) {
                throw new IllegalArgumentException("Copy a released template version from this Purpose.");
            }
            Integer max = session.createQuery("select max(version) from EsCommunicationBundleTemplate where purposeId = :purpose",
                    Integer.class).setParameter("purpose", purposeId).getSingleResult();
            var draft = new EsCommunicationBundleTemplate();
            draft.setPurposeId(purposeId);
            draft.setVersion(max == null ? 1 : Math.addExact(max, 1));
            draft.setStatus(EsCommunicationBundleTemplate.Status.DRAFT);
            draft.setCreatedByUserId(userId);
            session.persist(draft);
            session.flush();
            if (source != null) {
                for (var original : components(session, sourceId)) {
                    var copy = new EsCommunicationBundleTemplateComponent();
                    new CommunicationBundleTemplateService.ComponentInput(original.getSemanticKey(), original.getDisplayName(),
                            original.getAuthoringPrompt(), original.getKind(), original.isRequired(),
                            original.getCardinality(), original.getDisplayOrder()).applyTo(copy);
                    copy.setTemplateId(draft.getTemplateId());
                    session.persist(copy);
                }
            }
            return draft;
        });
    }

    public void saveDraftComponent(Long templateId, Long componentId, CommunicationBundleTemplateService.ComponentInput input) {
        input.validate();
        transact(session -> {
            requireUnusedDraft(session, templateId);
            var all = components(session, templateId);
            if (all.stream().anyMatch(c -> !java.util.Objects.equals(componentId, c.getComponentId())
                    && (input.key().equals(c.getSemanticKey()) || input.order() == c.getDisplayOrder()))) {
                throw new IllegalArgumentException("Use a unique semantic key and display order.");
            }
            var component = componentId == null ? new EsCommunicationBundleTemplateComponent()
                    : all.stream().filter(c -> c.getComponentId().equals(componentId)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Component is not in this template."));
            if (componentId != null && !input.key().equals(component.getSemanticKey())) {
                throw new IllegalArgumentException("Semantic keys cannot be renamed. Remove the draft component and add a new one instead.");
            }
            input.applyTo(component);
            if (componentId == null) {
                component.setTemplateId(templateId);
                session.persist(component);
            }
            return null;
        });
    }

    public void removeDraftComponent(Long templateId, Long componentId) {
        transact(session -> {
            requireUnusedDraft(session, templateId);
            var component = session.find(EsCommunicationBundleTemplateComponent.class, componentId);
            if (component == null || !templateId.equals(component.getTemplateId())) {
                throw new IllegalArgumentException("Component is not in this template.");
            }
            session.remove(component);
            return null;
        });
    }

    public void activateDraft(Long templateId) {
        transact(session -> {
            var found = session.find(EsCommunicationBundleTemplate.class, templateId);
            if (found == null) {
                throw new IllegalArgumentException("Template was not found.");
            }
            var purpose = session.find(EsCommunicationBundlePurpose.class, found.getPurposeId(), LockModeType.PESSIMISTIC_WRITE);
            var draft = requireUnusedDraft(session, templateId);
            if (purpose == null || !purpose.isActive()) {
                throw new IllegalStateException("This Purpose is not active.");
            }
            CommunicationBundleTemplateService.validateComponents(purpose.getPurposeKey(), components(session, templateId));
            if (purpose.getActiveTemplateId() != null) {
                var previous = session.find(EsCommunicationBundleTemplate.class, purpose.getActiveTemplateId(),
                        LockModeType.PESSIMISTIC_WRITE);
                if (previous == null || !purpose.getPurposeId().equals(previous.getPurposeId())) {
                    throw new IllegalStateException("The current active template is inconsistent.");
                }
                previous.setStatus(EsCommunicationBundleTemplate.Status.RETIRED);
            }
            draft.setStatus(EsCommunicationBundleTemplate.Status.ACTIVE);
            purpose.setActiveTemplateId(templateId);
            return null;
        });
    }

    private static EsCommunicationBundleTemplate requireUnusedDraft(Session session, Long id) {
        var template = session.find(EsCommunicationBundleTemplate.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (template != null) {
            session.refresh(template, LockModeType.PESSIMISTIC_WRITE);
        }
        if (template == null || template.getStatus() != EsCommunicationBundleTemplate.Status.DRAFT) {
            throw new IllegalStateException("Only unused draft templates can be edited or activated. Create a new version first.");
        }
        Long count = session.createQuery("select count(b) from EsCommunicationBundle b where templateId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (count != 0) {
            throw new IllegalStateException("A template already used by bundles cannot be changed.");
        }
        return template;
    }

    private static List<EsCommunicationBundleTemplateComponent> components(Session session, Long id) {
        return session.createQuery("from EsCommunicationBundleTemplateComponent where templateId = :id order by displayOrder, componentId",
                EsCommunicationBundleTemplateComponent.class).setParameter("id", id).getResultList();
    }

    private static <T> T transact(java.util.function.Function<Session, T> operation) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                T result = operation.apply(session);
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
