package org.airahub.interophub.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.logging.Logger;
import jakarta.persistence.LockModeType;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.*;
import org.airahub.interophub.model.*;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent.Kind;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.CommunicationBundleService.ResourceDetails;
import org.hibernate.Session;

public class StarterPacketService {
    public static final String PURPOSE_KEY = "STARTER_PACKET";
    private static final Logger LOGGER = Logger.getLogger(StarterPacketService.class.getName());
    private final EsTopicDao topics;
    private final EsSubscriptionDao subscriptions;
    private final TopicSpaceAccessService access;
    private final StoredFileService storage;

    public StarterPacketService() {
        this(new EsTopicDao(), new EsSubscriptionDao(), new TopicSpaceAccessService(), new StoredFileService());
    }

    StarterPacketService(EsTopicDao topics, EsSubscriptionDao subscriptions,
            TopicSpaceAccessService access, StoredFileService storage) {
        this.topics = topics;
        this.subscriptions = subscriptions;
        this.access = access;
        this.storage = storage;
    }

    public boolean canAccess(User user, EsTopic topic) {
        return user != null && user.getUserId() != null && topic != null && access.canViewTopic(user, topic)
                && (Boolean.TRUE.equals(user.getIsAdmin()) || TopicFollowerManagementService.isChampionOrSupportForTopic(
                        user, subscriptions.findActiveByTopicId(topic.getEsTopicId())));
    }

    public EsTopic requireAccess(User user, Long topicId) {
        var topic = topics.findById(topicId).orElseThrow(() -> new IllegalArgumentException("Topic was not found."));
        if (!canAccess(user, topic)) {
            throw new SecurityException("Only this Topic's active champions/support contacts and global administrators can access Starter Packets.");
        }
        return topic;
    }

    public StoredFileService storage() { return storage; }

    public List<OrientationResources> list(User user, Long topicId, boolean publishedOnly) {
        requireAccess(user, topicId);
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            var bundles = session.createQuery("select b from EsCommunicationBundle b, EsCommunicationBundlePurpose p"
                    + " where b.purposeId = p.purposeId and p.purposeKey = :key and b.esTopicId = :topic"
                    + (publishedOnly ? " and b.status = :status" : "")
                    + " order by b.communicationYear desc, b.communicationMonth desc, b.bundleId desc",
                    EsCommunicationBundle.class).setParameter("key", PURPOSE_KEY).setParameter("topic", topicId);
            if (publishedOnly) {
                bundles.setParameter("status", EsCommunicationBundle.Status.PUBLISHED);
            }
            return bundles.getResultList().stream().map(b -> content(session, b)).toList();
        }
    }

    public OrientationResources get(User user, Long bundleId) {
        if (user == null || user.getUserId() == null) {
            throw new SecurityException("Authentication is required to view Starter Packets.");
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            var bundle = packet(session, bundleId, null, false);
            requireAccess(user, bundle.getEsTopicId());
            return content(session, bundle);
        }
    }

    public EsCommunicationBundle create(User user, Long topicId, Long sourceBundleId) {
        requireAccess(user, topicId);
        return transact(session -> {
            var purpose = session.createQuery("from EsCommunicationBundlePurpose where purposeKey = :key",
                    EsCommunicationBundlePurpose.class).setParameter("key", PURPOSE_KEY).getSingleResult();
            session.refresh(purpose, LockModeType.PESSIMISTIC_WRITE);
            if (!purpose.isActive() || purpose.getActiveTemplateId() == null) {
                throw new IllegalStateException("Starter Packet template is not active.");
            }
            var source = sourceBundleId == null ? null : packet(session, sourceBundleId, topicId, true);
            if (source != null && source.getStatus() == EsCommunicationBundle.Status.DRAFT) {
                throw new IllegalArgumentException("Copy a published or retired packet.");
            }
            var template = session.find(EsCommunicationBundleTemplate.class,
                    source == null ? purpose.getActiveTemplateId() : source.getTemplateId(), LockModeType.PESSIMISTIC_READ);
            if (template == null || !purpose.getPurposeId().equals(template.getPurposeId())
                    || template.getStatus() == EsCommunicationBundleTemplate.Status.DRAFT) {
                throw new IllegalStateException("Choose a released Starter Packet template.");
            }
            var bundle = new EsCommunicationBundle();
            bundle.setEsTopicId(topicId);
            bundle.setPurposeId(purpose.getPurposeId());
            bundle.setTemplateId(template.getTemplateId());
            bundle.setAudience(EsCommunicationBundlePurpose.Audience.STEWARDS);
            bundle.setStatus(EsCommunicationBundle.Status.DRAFT);
            bundle.setCreatedByUserId(user.getUserId());
            bundle.setUpdatedByUserId(user.getUserId());
            if (source != null) {
                bundle.setCommunicationMonth(source.getCommunicationMonth());
                bundle.setCommunicationYear(source.getCommunicationYear());
            }
            session.persist(bundle);
            session.flush();
            if (source != null) {
                for (var original : values(session, source.getBundleId())) {
                    var value = new EsCommunicationBundleComponentValue();
                    value.setBundleId(bundle.getBundleId());
                    value.setTemplateId(bundle.getTemplateId());
                    value.setComponentId(original.getComponentId());
                    value.setContentText(original.getContentText());
                    value.setContentJson(original.getContentJson());
                    value.setUpdatedByUserId(user.getUserId());
                    session.persist(value);
                }
                for (var original : placements(session, source.getBundleId())) {
                    if (original.getResourceVersionId() == null) {
                        throw new IllegalStateException("The source packet is missing a preserved resource version.");
                    }
                    var placement = newPlacement(bundle, original.getComponentId(), original.getTopicResourceId(), user);
                    placement.setResourceVersionId(original.getResourceVersionId());
                    placement.setDisplayOrder(original.getDisplayOrder());
                    placement.setContextNote(original.getContextNote());
                    session.persist(placement);
                }
            }
            audit(session, bundle, user, "DRAFT_CREATED",
                    source == null ? "Starter Packet draft created." : "Draft copied from packet " + sourceBundleId + "; original template and preserved resources retained.");
            return bundle;
        });
    }

    public void saveSettings(User user, Long topicId, Long bundleId, Integer month, Integer year) {
        requireAccess(user, topicId);
        if ((month == null) != (year == null) || (month != null && (month < 1 || month > 12 || year < 1 || year > 9999))) {
            throw new IllegalArgumentException("Set both Month (1-12) and Year (1-9999), or leave both blank.");
        }
        mutate(user, topicId, bundleId, (session, bundle) -> {
            bundle.setCommunicationMonth(month);
            bundle.setCommunicationYear(year);
            audit(session, bundle, user, "DATE_UPDATED", "Packet Month/Year updated.");
        });
    }

    public void saveValue(User user, Long topicId, Long bundleId, Long componentId, String text) {
        requireAccess(user, topicId);
        String normalized = optional(text, 50000);
        mutate(user, topicId, bundleId, (session, bundle) -> {
            var component = component(session, bundle, componentId);
            if (component.getKind() != Kind.TEXT && component.getKind() != Kind.STRUCTURED_LIST) {
                throw new IllegalArgumentException("Choose a text or list component.");
            }
            if ("title".equals(component.getSemanticKey()) && normalized != null && normalized.length() > 140) {
                throw new IllegalArgumentException("Packet title must be at most 140 characters.");
            }
            var value = values(session, bundleId).stream().filter(v -> componentId.equals(v.getComponentId()))
                    .findFirst().orElse(null);
            if (value == null) {
                value = new EsCommunicationBundleComponentValue();
                value.setBundleId(bundleId);
                value.setTemplateId(bundle.getTemplateId());
                value.setComponentId(componentId);
                value.setUpdatedByUserId(user.getUserId());
                session.persist(value);
            }
            value.setContentText(component.getKind() == Kind.TEXT ? normalized : null);
            value.setContentJson(component.getKind() == Kind.STRUCTURED_LIST
                    ? CommunicationBundleStructuredList.fromLines(normalized) : null);
            value.setUpdatedByUserId(user.getUserId());
            value.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            audit(session, bundle, user, "COMPONENT_VALUE_UPDATED", "Packet component updated: " + component.getSemanticKey() + ".");
        });
    }

    public void selectResource(User user, Long topicId, Long bundleId, Long componentId, Long resourceId) {
        requireAccess(user, topicId);
        mutate(user, topicId, bundleId, (session, bundle) -> {
            var component = component(session, bundle, componentId);
            if (component.getKind() != Kind.RESOURCE && component.getKind() != Kind.RESOURCE_COLLECTION) {
                throw new IllegalArgumentException("Choose a resource component.");
            }
            var resource = currentResource(session, resourceId, topicId);
            if ("teaser_image".equals(component.getSemanticKey())) {
                var file = resource.getStoredFileId() == null ? null : session.find(StoredFile.class, resource.getStoredFileId());
                if (file == null || !file.isImage()) {
                    throw new IllegalArgumentException("The teaser image must be an uploaded image.");
                }
            }
            var selected = placements(session, bundleId).stream().filter(p -> componentId.equals(p.getComponentId())).toList();
            if (component.getKind() == Kind.RESOURCE) {
                selected.forEach(session::remove);
                session.flush();
            } else if (selected.stream().anyMatch(p -> resourceId.equals(p.getTopicResourceId()))) {
                throw new IllegalArgumentException("This resource is already selected.");
            }
            var placement = newPlacement(bundle, componentId, resourceId, user);
            if (component.getKind() == Kind.RESOURCE && !selected.isEmpty()
                    && resourceId.equals(selected.get(0).getTopicResourceId())) {
                placement.setContextNote(selected.get(0).getContextNote());
            }
            placement.setDisplayOrder(component.getKind() == Kind.RESOURCE ? 0
                    : Math.addExact(selected.stream().mapToInt(EsCommunicationBundleResourcePlacement::getDisplayOrder).max().orElse(-1), 1));
            session.persist(placement);
            audit(session, bundle, user, "RESOURCE_SELECTED", "Resource selected for " + component.getSemanticKey() + ".");
        });
    }

    public void removeResource(User user, Long topicId, Long bundleId, Long placementId) {
        requirePacketDraft(user, topicId, bundleId);
        new EsCommunicationBundleResourcePlacementDao().removeResource(bundleId, placementId, user.getUserId());
    }

    public void saveContext(User user, Long topicId, Long bundleId, Long placementId, String note) {
        requirePacketDraft(user, topicId, bundleId);
        new EsCommunicationBundleResourcePlacementDao().updateContextNote(bundleId, placementId, optional(note, 20000), user.getUserId());
    }

    public void moveResource(User user, Long topicId, Long bundleId, Long placementId, boolean up) {
        requirePacketDraft(user, topicId, bundleId);
        new EsCommunicationBundleResourcePlacementDao().moveResource(bundleId, placementId, up, user.getUserId());
    }

    public void publish(User user, Long topicId, Long bundleId, boolean confirmed) throws IOException {
        requireAccess(user, topicId);
        if (!confirmed) {
            throw new IllegalArgumentException("Confirm intentional preservation of every selected uploaded file before publishing.");
        }
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            var tx = session.beginTransaction();
            var preservedFiles = new ArrayList<StoredFile>();
            try {
                var bundle = packet(session, bundleId, topicId, true);
                requireDraft(bundle);
                var content = content(session, bundle);
                validatePublication(content);
                // Lock resources and files in ID order so replacement cannot remove bytes during preservation.
                var selected = placements(session, bundleId);
                var sourceIds = selected.stream().filter(p -> p.getResourceVersionId() == null)
                        .map(EsCommunicationBundleResourcePlacement::getTopicResourceId).distinct().sorted().toList();
                var versions = new HashMap<Long, EsTopicResourceVersion>();
                for (Long resourceId : sourceIds) {
                    var resource = session.find(EsTopicResource.class, resourceId, LockModeType.PESSIMISTIC_WRITE);
                    if (resource == null) {
                        throw new IllegalStateException("Selected resource was not found; update the draft's selection before publishing.");
                    }
                    session.refresh(resource, LockModeType.PESSIMISTIC_WRITE);
                    currentResource(session, resourceId, topicId);
                    var version = new EsTopicResourceVersion();
                    version.setTopicResourceId(resourceId);
                    version.setEsTopicId(topicId);
                    version.setResourceType(resource.getResourceType());
                    version.setTitle(resource.getTitle());
                    version.setDescription(resource.getDescription());
                    version.setAttribution(resource.getAttribution());
                    version.setExternalUrl(resource.getExternalUrl());
                    version.setPreservedByUserId(user.getUserId());
                    version.setPreservedAt(LocalDateTime.now(ZoneOffset.UTC));
                    if (resource.getStoredFileId() != null) {
                        var file = session.find(StoredFile.class, resource.getStoredFileId(), LockModeType.PESSIMISTIC_WRITE);
                        if (file == null) {
                            throw new IllegalStateException("Selected resource file was not found; replace it before publishing.");
                        }
                        session.refresh(file, LockModeType.PESSIMISTIC_WRITE);
                        var copy = storage.preserve(file, user.getUserId(), candidate -> {
                            session.persist(candidate);
                            session.flush();
                            return candidate;
                        });
                        preservedFiles.add(copy);
                        version.setStoredFileId(copy.getStoredFileId());
                    }
                    session.persist(version);
                    session.flush();
                    versions.put(resourceId, version);
                }
                for (var selection : selected) {
                    if (selection.getResourceVersionId() == null) {
                        selection.setResourceVersionId(versions.get(selection.getTopicResourceId()).getResourceVersionId());
                    }
                }
                session.flush();
                validatePublication(content(session, bundle));
                bundle.setStatus(EsCommunicationBundle.Status.PUBLISHED);
                bundle.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC));
                audit(session, bundle, user, "PUBLISHED", "Starter Packet published with explicitly preserved resources.");
                tx.commit();
            } catch (IOException | RuntimeException ex) {
                rollback(tx, ex);
                for (var file : preservedFiles) {
                    LOGGER.severe("Packet publication failed; reconcile preserved object before cleanup: "
                            + file.getBackend() + " " + file.getStorageKey());
                }
                throw ex;
            }
        }
    }

    public void retire(User user, Long topicId, Long bundleId) {
        requireAccess(user, topicId);
        transact(session -> {
            var bundle = packet(session, bundleId, topicId, true);
            if (bundle.getStatus() != EsCommunicationBundle.Status.PUBLISHED) {
                throw new IllegalStateException("Only published packets can be retired.");
            }
            bundle.setStatus(EsCommunicationBundle.Status.RETIRED);
            audit(session, bundle, user, "RETIRED", "Starter Packet retired; preserved history retained.");
            return null;
        });
    }

    public List<ResourceDetails> resources(User user, Long topicId) {
        requireAccess(user, topicId);
        var files = new StoredFileDao();
        return new EsTopicResourceDao().findActiveByTopicId(topicId).stream().map(r -> new ResourceDetails(r,
                r.getStoredFileId() == null ? null : files.findById(r.getStoredFileId())
                        .orElseThrow(() -> new IllegalStateException("Resource file was not found.")))).toList();
    }

    public EsTopicResource upload(User user, Long topicId, InputStream input, String filename, String contentType,
            String title) throws IOException {
        requireAccess(user, topicId);
        var resource = resource(topicId, user, title);
        storage.upload(null, input, filename, contentType, user.getUserId(), file -> {
            requireAccess(user, topicId);
            resource.setResourceType(CommunicationBundleService.resourceTypeFor(file));
            return new EsTopicResourceDao().registerUpload(file, resource);
        });
        return resource;
    }

    public EsTopicResource addLink(User user, Long topicId, String url, String title) {
        requireAccess(user, topicId);
        String link = CommunicationBundleService.validateExternalUrl(url);
        var resource = resource(topicId, user, title);
        resource.setResourceType(EsTopicResource.ResourceType.EXTERNAL_LINK);
        resource.setExternalUrl(link);
        return new EsTopicResourceDao().save(resource);
    }

    public static void validatePublication(OrientationResources content) {
        var bundle = content.bundle();
        if (bundle.getCommunicationMonth() == null || bundle.getCommunicationYear() == null
                || bundle.getCommunicationMonth() < 1 || bundle.getCommunicationMonth() > 12
                || bundle.getCommunicationYear() < 1 || bundle.getCommunicationYear() > 9999) {
            throw new IllegalStateException("Set a valid Month and Year before publishing.");
        }
        for (var component : content.components()) {
            if ("teaser_image".equals(component.getSemanticKey())) {
                for (var placement : content.placements().stream().filter(p -> p.getComponentId().equals(component.getComponentId())).toList()) {
                    var details = content.resources().stream().filter(r -> r.resource().getTopicResourceId().equals(placement.getTopicResourceId())
                            && Objects.equals(r.preservedVersionId(), placement.getResourceVersionId())).findFirst()
                            .orElseThrow(() -> new IllegalStateException("Teaser image is unavailable."));
                    if (details.file() == null || !details.file().isImage()) {
                        throw new IllegalStateException("The teaser image must be an uploaded image.");
                    }
                }
            }
            if (!component.isRequired() && !"title".equals(component.getSemanticKey())) { continue; }
            var value = content.componentValues().stream().filter(v -> v.getComponentId().equals(component.getComponentId())).findFirst().orElse(null);
            boolean complete = switch (component.getKind()) {
                case TEXT -> value != null && value.getContentText() != null && !value.getContentText().isBlank();
                case STRUCTURED_LIST -> value != null && !CommunicationBundleStructuredList.items(value.getContentJson()).isEmpty();
                case RESOURCE, RESOURCE_COLLECTION -> content.placements().stream().anyMatch(p -> p.getComponentId().equals(component.getComponentId()));
            };
            if (!complete) { throw new IllegalStateException(component.getDisplayName() + " is required before publishing."); }
            if ("title".equals(component.getSemanticKey()) && value != null && value.getContentText().length() > 140) {
                throw new IllegalStateException("Packet title must be at most 140 characters.");
            }
        }
        if (content.components().stream().noneMatch(c -> "title".equals(c.getSemanticKey()) && c.getKind() == Kind.TEXT)) {
            throw new IllegalStateException("Starter Packet template must include its title.");
        }
    }

    private void mutate(User user, Long topicId, Long bundleId, java.util.function.BiConsumer<Session, EsCommunicationBundle> operation) {
        transact(session -> {
            var bundle = packet(session, bundleId, topicId, true);
            requireDraft(bundle);
            operation.accept(session, bundle);
            return null;
        });
    }

    private void requirePacketDraft(User user, Long topicId, Long bundleId) {
        requireAccess(user, topicId);
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            requireDraft(packet(session, bundleId, topicId, false));
        }
    }

    private static EsCommunicationBundle packet(Session session, Long id, Long topicId, boolean lock) {
        var bundle = session.find(EsCommunicationBundle.class, id, lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if (bundle == null || (topicId != null && !topicId.equals(bundle.getEsTopicId()))
                || !PURPOSE_KEY.equals(session.find(EsCommunicationBundlePurpose.class, bundle.getPurposeId()).getPurposeKey())) {
            throw new IllegalArgumentException("Starter Packet was not found in this Topic.");
        }
        return bundle;
    }

    private static void requireDraft(EsCommunicationBundle bundle) {
        if (bundle.getStatus() != EsCommunicationBundle.Status.DRAFT) {
            throw new IllegalStateException("Published and retired packets are locked. Copy to a new draft for revisions.");
        }
    }

    private static EsCommunicationBundleTemplateComponent component(Session session, EsCommunicationBundle bundle, Long id) {
        var component = session.find(EsCommunicationBundleTemplateComponent.class, id);
        if (component == null || !bundle.getTemplateId().equals(component.getTemplateId())) {
            throw new IllegalArgumentException("Component does not belong to this packet's template.");
        }
        return component;
    }

    private static EsTopicResource currentResource(Session session, Long id, Long topicId) {
        var resource = session.find(EsTopicResource.class, id);
        if (resource == null || !topicId.equals(resource.getEsTopicId()) || resource.getStatus() != EsTopicResource.Status.ACTIVE) {
            throw new IllegalArgumentException("Choose an active resource from this Topic.");
        }
        return resource;
    }

    private static EsCommunicationBundleResourcePlacement newPlacement(EsCommunicationBundle bundle, Long componentId, Long resourceId, User user) {
        var placement = new EsCommunicationBundleResourcePlacement();
        placement.setBundleId(bundle.getBundleId());
        placement.setEsTopicId(bundle.getEsTopicId());
        placement.setTemplateId(bundle.getTemplateId());
        placement.setComponentId(componentId);
        placement.setTopicResourceId(resourceId);
        placement.setCreatedByUserId(user.getUserId());
        return placement;
    }

    private static List<EsCommunicationBundleTemplateComponent> components(Session session, Long templateId) {
        return session.createQuery("from EsCommunicationBundleTemplateComponent where templateId = :id order by displayOrder, componentId",
                EsCommunicationBundleTemplateComponent.class).setParameter("id", templateId).getResultList();
    }

    private static List<EsCommunicationBundleComponentValue> values(Session session, Long bundleId) {
        return session.createQuery("from EsCommunicationBundleComponentValue where bundleId = :id",
                EsCommunicationBundleComponentValue.class).setParameter("id", bundleId).getResultList();
    }

    private static List<EsCommunicationBundleResourcePlacement> placements(Session session, Long bundleId) {
        return session.createQuery("from EsCommunicationBundleResourcePlacement where bundleId = :id order by componentId, displayOrder, placementId",
                EsCommunicationBundleResourcePlacement.class).setParameter("id", bundleId).getResultList();
    }

    private static OrientationResources content(Session session, EsCommunicationBundle bundle) {
        var placements = placements(session, bundle.getBundleId());
        var resources = new ArrayList<ResourceDetails>();
        for (var placement : placements) {
            EsTopicResource resource;
            if (placement.getResourceVersionId() == null) {
                if (bundle.getStatus() != EsCommunicationBundle.Status.DRAFT) {
                    throw new IllegalStateException("Published packet is missing a preserved resource version.");
                }
                resource = currentResource(session, placement.getTopicResourceId(), bundle.getEsTopicId());
            } else {
                var version = session.find(EsTopicResourceVersion.class, placement.getResourceVersionId());
                if (version == null || !bundle.getEsTopicId().equals(version.getEsTopicId())
                        || !placement.getTopicResourceId().equals(version.getTopicResourceId())) {
                    throw new IllegalStateException("Packet resource version is inconsistent.");
                }
                resource = version.resource();
            }
            var file = resource.getStoredFileId() == null ? null : session.find(StoredFile.class, resource.getStoredFileId());
            if (resource.getStoredFileId() != null && file == null) {
                throw new IllegalStateException("Packet resource file was not found.");
            }
            resources.add(new ResourceDetails(resource, file, placement.getResourceVersionId()));
        }
        return new OrientationResources(bundle, components(session, bundle.getTemplateId()), placements, resources,
                values(session, bundle.getBundleId()), List.of(), List.of(EsCommunicationBundlePurpose.Audience.STEWARDS));
    }

    private static void audit(Session session, EsCommunicationBundle bundle, User user, String event, String detail) {
        bundle.setUpdatedByUserId(user.getUserId());
        bundle.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        session.persist(EsCommunicationBundleAudit.event(bundle.getBundleId(), null, event, detail, user.getUserId()));
    }

    private static EsTopicResource resource(Long topicId, User user, String title) {
        String name = optional(title, 255);
        if (name == null) { throw new IllegalArgumentException("A resource title is required."); }
        var resource = new EsTopicResource();
        resource.setEsTopicId(topicId);
        resource.setTitle(name);
        resource.setCreatedByUserId(user.getUserId());
        resource.setUpdatedByUserId(user.getUserId());
        resource.setStatus(EsTopicResource.Status.ACTIVE);
        return resource;
    }

    private static String optional(String value, int max) {
        if (value != null && value.length() > max) { throw new IllegalArgumentException("Text must be at most " + max + " characters."); }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T transact(java.util.function.Function<Session, T> work) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            var tx = session.beginTransaction();
            try {
                T result = work.apply(session);
                tx.commit();
                return result;
            } catch (RuntimeException ex) {
                rollback(tx, ex);
                throw ex;
            }
        }
    }

    private static void rollback(org.hibernate.Transaction tx, Exception ex) {
        try { tx.rollback(); } catch (RuntimeException rollback) { ex.addSuppressed(rollback); }
    }
}
