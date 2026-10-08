package org.airahub.interophub.dao;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsCommunicationBundleAudit;
import org.airahub.interophub.model.EsCommunicationBundleComponentValue;
import org.airahub.interophub.model.EsCommunicationBundlePurpose;
import org.airahub.interophub.model.EsCommunicationBundleResourcePlacement;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicResource;
import org.airahub.interophub.model.EsTopicSpace;

public class EsCommunicationBundleDao extends GenericDao<EsCommunicationBundle, Long> {
    public EsCommunicationBundleDao() { super(EsCommunicationBundle.class); }

    public Optional<EsCommunicationBundle> findByTopicAndPurpose(Long topicId, Long purposeId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCommunicationBundle where esTopicId = :topicId and purposeId = :purposeId"
                            + " and status <> :retired",
                    EsCommunicationBundle.class)
                    .setParameter("topicId", topicId)
                    .setParameter("purposeId", purposeId)
                    .setParameter("retired", EsCommunicationBundle.Status.RETIRED)
                    .uniqueResultOptional();
        }
    }

    public List<EsCommunicationBundleAudit> findAuditEntries(Long bundleId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCommunicationBundleAudit where bundleId = :bundleId"
                            + " order by changedAt desc, auditId desc",
                    EsCommunicationBundleAudit.class)
                    .setParameter("bundleId", bundleId)
                    .setMaxResults(50)
                    .getResultList();
        }
    }

    public EsCommunicationBundle saveDraftWithAudit(EsCommunicationBundle bundle, Long userId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                session.persist(bundle);
                session.flush();
                session.persist(EsCommunicationBundleAudit.event(
                        bundle.getBundleId(), null, "DRAFT_CREATED", "Topic Orientation draft created.", userId));
                transaction.commit();
                return bundle;
            } catch (RuntimeException ex) {
                rollback(transaction, ex);
                throw ex;
            }
        }
    }

    public EsCommunicationBundle updateEditableSettings(Long bundleId, EsCommunicationBundlePurpose.Audience audience,
            Integer month, Integer year, Long userId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                EsCommunicationBundle bundle = requireEditable(session, bundleId);
                if (bundle.getStatus() == EsCommunicationBundle.Status.PUBLISHED) {
                    validateDate(month, year);
                } else {
                    validateDraftDate(month, year);
                }
                validateAudienceWithinTopic(session, bundle, audience);
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                if (bundle.getAudience() != audience) {
                    session.persist(EsCommunicationBundleAudit.event(bundleId, null, "AUDIENCE_UPDATED",
                            "Audience changed from " + bundle.getAudience() + " to " + audience + ".", userId));
                    bundle.setAudience(audience);
                }
                if (!java.util.Objects.equals(bundle.getCommunicationMonth(), month)
                        || !java.util.Objects.equals(bundle.getCommunicationYear(), year)) {
                    session.persist(EsCommunicationBundleAudit.event(bundleId, null, "DATE_UPDATED",
                            "Communication Month/Year changed to " + (month == null ? "unset" : month + "/" + year)
                                    + ".", userId));
                    bundle.setCommunicationMonth(month);
                    bundle.setCommunicationYear(year);
                }
                bundle.setUpdatedByUserId(userId);
                bundle.setUpdatedAt(now);
                transaction.commit();
                return bundle;
            } catch (RuntimeException ex) {
                rollback(transaction, ex);
                throw ex;
            }
        }
    }

    public EsCommunicationBundle publishDraft(Long bundleId, Long userId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                EsCommunicationBundle bundle = requireDraft(session, bundleId);
                validateDate(bundle.getCommunicationMonth(), bundle.getCommunicationYear());
                validateAudienceWithinTopic(session, bundle, bundle.getAudience());
                validateRequiredComponents(session, bundle);
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                bundle.setStatus(EsCommunicationBundle.Status.PUBLISHED);
                bundle.setPublishedAt(now);
                bundle.setUpdatedByUserId(userId);
                bundle.setUpdatedAt(now);
                session.persist(EsCommunicationBundleAudit.event(
                        bundleId, null, "PUBLISHED", "Topic Orientation published.", userId));
                transaction.commit();
                return bundle;
            } catch (RuntimeException ex) {
                rollback(transaction, ex);
                throw ex;
            }
        }
    }

    public EsCommunicationBundle retirePublished(Long bundleId, Long userId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                EsCommunicationBundle bundle =
                        session.find(EsCommunicationBundle.class, bundleId, LockModeType.PESSIMISTIC_WRITE);
                if (bundle == null || bundle.getStatus() != EsCommunicationBundle.Status.PUBLISHED) {
                    throw new IllegalStateException("Only a published Orientation can be retired.");
                }
                bundle.setStatus(EsCommunicationBundle.Status.RETIRED);
                bundle.setSingleInstanceGuard(null);
                bundle.setUpdatedByUserId(userId);
                bundle.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
                session.persist(EsCommunicationBundleAudit.event(
                        bundleId, null, "RETIRED", "Topic Orientation retired.", userId));
                transaction.commit();
                return bundle;
            } catch (RuntimeException ex) {
                rollback(transaction, ex);
                throw ex;
            }
        }
    }

    private static EsCommunicationBundle requireDraft(org.hibernate.Session session, Long bundleId) {
        EsCommunicationBundle bundle =
                session.find(EsCommunicationBundle.class, bundleId, LockModeType.PESSIMISTIC_WRITE);
        if (bundle == null || bundle.getStatus() != EsCommunicationBundle.Status.DRAFT) {
            throw new IllegalStateException("Only a draft Orientation can be edited.");
        }
        return bundle;
    }

    static EsCommunicationBundle requireEditable(org.hibernate.Session session, Long bundleId) {
        EsCommunicationBundle bundle =
                session.find(EsCommunicationBundle.class, bundleId, LockModeType.PESSIMISTIC_WRITE);
        if (bundle != null && bundle.getStatus() == EsCommunicationBundle.Status.DRAFT) {
            return bundle;
        }
        EsCommunicationBundlePurpose purpose = bundle == null ? null
                : session.find(EsCommunicationBundlePurpose.class, bundle.getPurposeId());
        if (bundle == null || bundle.getStatus() != EsCommunicationBundle.Status.PUBLISHED
                || purpose == null || purpose.getMode() != EsCommunicationBundlePurpose.Mode.LIVING) {
            throw new IllegalStateException("Only drafts and published living bundles can be edited.");
        }
        return bundle;
    }

    static void validatePublishedContent(org.hibernate.Session session, EsCommunicationBundle bundle) {
        if (bundle.getStatus() == EsCommunicationBundle.Status.PUBLISHED) {
            validateRequiredComponents(session, bundle);
        }
    }

    private static void validateDate(Integer month, Integer year) {
        if (month == null || year == null || month < 1 || month > 12 || year < 1 || year > 9999) {
            throw new IllegalStateException("Set a valid communication Month and Year before publishing.");
        }
    }

    private static void validateDraftDate(Integer month, Integer year) {
        if ((month == null) != (year == null)
                || (month != null && (month < 1 || month > 12 || year < 1 || year > 9999))) {
            throw new IllegalArgumentException(
                    "Enter both a Month from 1 to 12 and a Year from 1 to 9999, or leave both blank.");
        }
    }

    private static void validateAudienceWithinTopic(org.hibernate.Session session,
            EsCommunicationBundle bundle, EsCommunicationBundlePurpose.Audience audience) {
        if (audience == null) {
            throw new IllegalStateException("Choose an Orientation audience.");
        }
        if (audience != EsCommunicationBundlePurpose.Audience.PUBLIC) {
            return;
        }
        EsTopic topic = session.find(EsTopic.class, bundle.getEsTopicId());
        EsTopicSpace space = topic == null || topic.getEsTopicSpaceId() == null ? null
                : session.find(EsTopicSpace.class, topic.getEsTopicSpaceId());
        if (space == null || space.getVisibility() != EsTopicSpace.Visibility.PUBLIC) {
            throw new IllegalStateException("A Topic Orientation cannot have a broader audience than its Topic.");
        }
    }

    private static void validateRequiredComponents(org.hibernate.Session session, EsCommunicationBundle bundle) {
        List<EsCommunicationBundleTemplateComponent> components = session.createQuery(
                "from EsCommunicationBundleTemplateComponent where templateId = :templateId",
                EsCommunicationBundleTemplateComponent.class)
                .setParameter("templateId", bundle.getTemplateId())
                .getResultList();
        List<EsCommunicationBundleComponentValue> values = session.createQuery(
                "from EsCommunicationBundleComponentValue where bundleId = :bundleId",
                EsCommunicationBundleComponentValue.class)
                .setParameter("bundleId", bundle.getBundleId())
                .getResultList();
        List<EsCommunicationBundleResourcePlacement> placements = session.createQuery(
                "from EsCommunicationBundleResourcePlacement where bundleId = :bundleId",
                EsCommunicationBundleResourcePlacement.class)
                .setParameter("bundleId", bundle.getBundleId())
                .getResultList();
        for (EsCommunicationBundleTemplateComponent component : components) {
            if (!component.isRequired()) {
                continue;
            }
            boolean complete = switch (component.getKind()) {
                case TEXT -> values.stream().anyMatch(value ->
                        value.getComponentId().equals(component.getComponentId())
                                && value.getContentText() != null && !value.getContentText().isBlank());
                case STRUCTURED_LIST -> values.stream().anyMatch(value ->
                        value.getComponentId().equals(component.getComponentId())
                                && !org.airahub.interophub.service.CommunicationBundleStructuredList
                                        .items(value.getContentJson()).isEmpty());
                case RESOURCE, RESOURCE_COLLECTION -> placements.stream()
                        .filter(placement -> placement.getComponentId().equals(component.getComponentId()))
                        .map(EsCommunicationBundleResourcePlacement::getTopicResourceId)
                        .map(resourceId -> session.find(EsTopicResource.class, resourceId))
                        .anyMatch(resource -> resource != null
                                && resource.getStatus() == EsTopicResource.Status.ACTIVE);
            };
            if (!complete) {
                throw new IllegalStateException(component.getDisplayName() + " is required before publishing.");
            }
        }
    }

    private static void rollback(org.hibernate.Transaction transaction, RuntimeException ex) {
        try {
            transaction.rollback();
        } catch (RuntimeException rollback) {
            ex.addSuppressed(rollback);
        }
    }
}
