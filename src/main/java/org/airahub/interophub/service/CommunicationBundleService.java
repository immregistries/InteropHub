package org.airahub.interophub.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;
import org.airahub.interophub.dao.EsCommunicationBundleDao;
import org.airahub.interophub.dao.EsCommunicationBundleComponentValueDao;
import org.airahub.interophub.dao.EsCommunicationBundlePurposeDao;
import org.airahub.interophub.dao.EsCommunicationBundleResourcePlacementDao;
import org.airahub.interophub.dao.EsCommunicationBundleTemplateComponentDao;
import org.airahub.interophub.dao.EsCommunicationBundleTemplateDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicResourceDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.StoredFileDao;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsCommunicationBundleAudit;
import org.airahub.interophub.model.EsCommunicationBundleComponentValue;
import org.airahub.interophub.model.EsCommunicationBundlePurpose;
import org.airahub.interophub.model.EsCommunicationBundlePurpose.Audience;
import org.airahub.interophub.model.EsCommunicationBundlePurpose.InstancePolicy;
import org.airahub.interophub.model.EsCommunicationBundleResourcePlacement;
import org.airahub.interophub.model.EsCommunicationBundleTemplate;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicResource;
import org.airahub.interophub.model.EsTopicResource.ResourceType;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.StoredFile;
import org.airahub.interophub.model.User;

public class CommunicationBundleService {
    public static final String TOPIC_ORIENTATION_KEY = "TOPIC_ORIENTATION";

    private final EsTopicDao topicDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsTopicResourceDao resourceDao;
    private final EsCommunicationBundlePurposeDao purposeDao;
    private final EsCommunicationBundleDao bundleDao;
    private final EsCommunicationBundleComponentValueDao componentValueDao;
    private final EsCommunicationBundleTemplateDao templateDao;
    private final EsCommunicationBundleTemplateComponentDao componentDao;
    private final EsCommunicationBundleResourcePlacementDao placementDao;
    private final StoredFileDao storedFileDao;
    private final TopicSpaceAccessService access;
    private final StoredFileService storage;

    public record ResourceDetails(EsTopicResource resource, StoredFile file) { }

    public record OrientationResources(EsCommunicationBundle bundle,
            List<EsCommunicationBundleTemplateComponent> components,
            List<EsCommunicationBundleResourcePlacement> placements,
            List<ResourceDetails> resources,
            List<EsCommunicationBundleComponentValue> componentValues,
            List<EsCommunicationBundleAudit> auditEntries,
            List<Audience> allowedAudiences) {
        public OrientationResources(EsCommunicationBundle bundle,
                List<EsCommunicationBundleTemplateComponent> components,
                List<EsCommunicationBundleResourcePlacement> placements,
                List<ResourceDetails> resources) {
            this(bundle, components, placements, resources, List.of(), List.of(),
                    List.of(Audience.PUBLIC, Audience.PARTICIPANTS, Audience.STEWARDS));
        }
    }

    public CommunicationBundleService() {
        this(new EsTopicDao(), new EsTopicSpaceDao(), new EsTopicResourceDao(),
                new EsCommunicationBundlePurposeDao(), new EsCommunicationBundleDao(),
                new EsCommunicationBundleComponentValueDao(), new EsCommunicationBundleTemplateDao(),
                new EsCommunicationBundleTemplateComponentDao(),
                new EsCommunicationBundleResourcePlacementDao(), new StoredFileDao(),
                new TopicSpaceAccessService());
    }

    CommunicationBundleService(EsTopicDao topicDao, EsTopicSpaceDao topicSpaceDao,
            EsTopicResourceDao resourceDao, EsCommunicationBundlePurposeDao purposeDao,
            EsCommunicationBundleDao bundleDao, EsCommunicationBundleComponentValueDao componentValueDao,
            EsCommunicationBundleTemplateDao templateDao,
            EsCommunicationBundleTemplateComponentDao componentDao,
            EsCommunicationBundleResourcePlacementDao placementDao, StoredFileDao storedFileDao,
            TopicSpaceAccessService access) {
        this(topicDao, topicSpaceDao, resourceDao, purposeDao, bundleDao, componentValueDao, templateDao,
                componentDao, placementDao, storedFileDao, access, new StoredFileService());
    }

    CommunicationBundleService(EsTopicDao topicDao, EsTopicSpaceDao topicSpaceDao,
            EsTopicResourceDao resourceDao, EsCommunicationBundlePurposeDao purposeDao,
            EsCommunicationBundleDao bundleDao, EsCommunicationBundleComponentValueDao componentValueDao,
            EsCommunicationBundleTemplateDao templateDao,
            EsCommunicationBundleTemplateComponentDao componentDao,
            EsCommunicationBundleResourcePlacementDao placementDao, StoredFileDao storedFileDao,
            TopicSpaceAccessService access, StoredFileService storage) {
        this.topicDao = topicDao;
        this.topicSpaceDao = topicSpaceDao;
        this.resourceDao = resourceDao;
        this.purposeDao = purposeDao;
        this.bundleDao = bundleDao;
        this.componentValueDao = componentValueDao;
        this.templateDao = templateDao;
        this.componentDao = componentDao;
        this.placementDao = placementDao;
        this.storedFileDao = storedFileDao;
        this.access = access;
        this.storage = storage;
    }

    public EsTopicResource registerStoredFileResource(User user, Long topicId, Long storedFileId,
            String title, String description, String attribution) {
        EsTopic topic = requireSteward(user, topicId);
        StoredFile file = storedFileDao.findById(storedFileId)
                .orElseThrow(() -> new IllegalArgumentException("Stored file was not found."));
        if (resourceDao.findByStoredFileId(storedFileId).isPresent()) {
            throw new IllegalArgumentException("This stored file is already registered as a Topic Resource.");
        }

        EsTopicResource resource = new EsTopicResource();
        resource.setEsTopicId(topic.getEsTopicId());
        resource.setStoredFileId(file.getStoredFileId());
        resource.setResourceType(resourceTypeFor(file));
        resource.setTitle(required(title, "Resource title", 255));
        resource.setDescription(optional(description, 20000));
        resource.setAttribution(optional(attribution, 500));
        resource.setCreatedByUserId(user.getUserId());
        resource.setUpdatedByUserId(user.getUserId());
        return resourceDao.save(resource);
    }

    public EsTopicResource registerExternalLinkResource(User user, Long topicId, String url,
            String title, String description, String attribution) {
        EsTopic topic = requireSteward(user, topicId);
        String validUrl = validateExternalUrl(url);

        EsTopicResource resource = new EsTopicResource();
        resource.setEsTopicId(topic.getEsTopicId());
        resource.setResourceType(ResourceType.EXTERNAL_LINK);
        resource.setExternalUrl(validUrl);
        resource.setTitle(required(title, "Resource title", 255));
        resource.setDescription(optional(description, 20000));
        resource.setAttribution(optional(attribution, 500));
        resource.setCreatedByUserId(user.getUserId());
        resource.setUpdatedByUserId(user.getUserId());
        return resourceDao.save(resource);
    }

    public List<EsTopicResource> listResourcesForSteward(User user, Long topicId) {
        requireSteward(user, topicId);
        return resourceDao.findActiveByTopicId(topicId);
    }

    public StoredFileService storage() { return storage; }

    public EsTopicResource uploadResource(User user, Long topicId, InputStream input, String filename,
            String contentType, String title, String description, String attribution) throws IOException {
        requireSteward(user, topicId);
        EsTopicResource resource = new EsTopicResource();
        resource.setEsTopicId(topicId);
        resource.setTitle(required(title, "Resource title", 255));
        resource.setDescription(optional(description, 20000));
        resource.setAttribution(optional(attribution, 500));
        resource.setCreatedByUserId(user.getUserId());
        resource.setUpdatedByUserId(user.getUserId());
        storage.upload(null, input, filename, contentType, user.getUserId(), file -> {
            requireSteward(user, topicId);
            resource.setResourceType(resourceTypeFor(file));
            return resourceDao.registerUpload(file, resource);
        });
        return resource;
    }

    public EsTopicResource updateResourceMetadata(User user, Long topicId, Long resourceId,
            String title, String description, String attribution, String externalUrl) {
        requireSteward(user, topicId);
        EsTopicResource resource = resourceDao.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Topic Resource was not found."));
        if (!topicId.equals(resource.getEsTopicId()) || resource.getStatus() != EsTopicResource.Status.ACTIVE) {
            throw new IllegalArgumentException("Choose an active resource from this Topic.");
        }
        String validTitle = required(title, "Resource title", 255);
        String validDescription = optional(description, 20000);
        String validAttribution = optional(attribution, 500);
        String validUrl = resource.getResourceType() == ResourceType.EXTERNAL_LINK
                ? validateExternalUrl(externalUrl) : null;
        resource.setTitle(validTitle);
        resource.setDescription(validDescription);
        resource.setAttribution(validAttribution);
        resource.setExternalUrl(validUrl);
        resource.setUpdatedByUserId(user.getUserId());
        return resourceDao.updateMetadataWithAudit(resource, user.getUserId());
    }

    public EsTopicResource replaceResourceFile(User user, Long topicId, Long resourceId, InputStream input,
            String filename, String contentType) throws IOException {
        requireSteward(user, topicId);
        EsTopicResource resource = resourceDao.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Topic Resource was not found."));
        if (!topicId.equals(resource.getEsTopicId()) || resource.getStatus() != EsTopicResource.Status.ACTIVE
                || resource.getStoredFileId() == null) {
            throw new IllegalArgumentException("Choose an active file-backed resource from this Topic.");
        }
        StoredFile existing = storedFileDao.findById(resource.getStoredFileId())
                .orElseThrow(() -> new IllegalStateException("The Topic Resource file was not found."));
        storage.upload(existing, input, filename, contentType, user.getUserId(), file -> {
            resource.setResourceType(resourceTypeFor(file));
            resource.setUpdatedByUserId(user.getUserId());
            resourceDao.replaceUpload(file, resource, user.getUserId());
            return file;
        });
        return resourceDao.findById(resourceId)
                .orElseThrow(() -> new IllegalStateException("The replaced Topic Resource was not found."));
    }

    public List<ResourceDetails> listResourceDetailsForSteward(User user, Long topicId) {
        return listResourcesForSteward(user, topicId).stream().map(this::resourceDetails).toList();
    }

    public Optional<OrientationResources> findOrientationResourcesForViewer(User user, Long topicId) {
        return findTopicOrientationForViewer(user, topicId).map(bundle -> {
            List<EsCommunicationBundleResourcePlacement> placements =
                    placementDao.findByBundleOrdered(bundle.getBundleId());
            List<ResourceDetails> resources = placements.stream()
                    .map(EsCommunicationBundleResourcePlacement::getTopicResourceId).distinct()
                    .map(id -> resourceDao.findById(id)
                            .orElseThrow(() -> new IllegalStateException("An Orientation resource is missing.")))
                    .filter(resource -> resource.getStatus() == EsTopicResource.Status.ACTIVE)
                    .map(this::resourceDetails).toList();
            List<Long> activeResourceIds = resources.stream()
                    .map(details -> details.resource().getTopicResourceId()).toList();
            List<EsCommunicationBundleResourcePlacement> visiblePlacements = placements.stream()
                    .filter(placement -> activeResourceIds.contains(placement.getTopicResourceId())).toList();
            List<EsCommunicationBundleAudit> auditEntries = access.canEditTopic(user,
                    topicDao.findById(topicId).orElseThrow())
                    ? bundleDao.findAuditEntries(bundle.getBundleId()) : List.of();
            return new OrientationResources(bundle, componentDao.findByTemplateId(bundle.getTemplateId()),
                    visiblePlacements, resources, componentValueDao.findByBundleId(bundle.getBundleId()),
                    auditEntries, allowedAudiences(topicId));
        });
    }

    private ResourceDetails resourceDetails(EsTopicResource resource) {
        StoredFile file = resource.getStoredFileId() == null ? null
                : storedFileDao.findById(resource.getStoredFileId())
                        .orElseThrow(() -> new IllegalStateException("A Topic Resource file is missing."));
        return new ResourceDetails(resource, file);
    }

    public void selectOrientationResource(User user, Long topicId, Long componentId, Long resourceId) {
        EsCommunicationBundle bundle = requireEditableOrientation(user, topicId);
        placementDao.selectResource(bundle.getBundleId(), componentId, resourceId, user.getUserId());
    }

    public void removeOrientationResource(User user, Long topicId, Long placementId) {
        EsCommunicationBundle bundle = requireEditableOrientation(user, topicId);
        placementDao.removeResource(bundle.getBundleId(), placementId, user.getUserId());
    }

    public void moveOrientationResource(User user, Long topicId, Long placementId, boolean up) {
        var bundle = requireEditableOrientation(user, topicId);
        placementDao.moveResource(bundle.getBundleId(), placementId, up, user.getUserId());
    }

    public void updateOrientationResourceContext(User user, Long topicId, Long placementId, String note) {
        var bundle = requireEditableOrientation(user, topicId);
        placementDao.updateContextNote(bundle.getBundleId(), placementId, optional(note, 20000), user.getUserId());
    }

    private EsCommunicationBundle requireDraftOrientation(User user, Long topicId) {
        requireSteward(user, topicId);
        EsCommunicationBundle bundle = findTopicOrientationForViewer(user, topicId)
                .orElseThrow(() -> new IllegalStateException("Create an Orientation draft first."));
        if (bundle.getStatus() != EsCommunicationBundle.Status.DRAFT) {
            throw new IllegalStateException("Only a draft Orientation can be edited.");
        }
        return bundle;
    }

    private EsCommunicationBundle requireEditableOrientation(User user, Long topicId) {
        requireSteward(user, topicId);
        EsCommunicationBundle bundle = findTopicOrientationForViewer(user, topicId)
                .orElseThrow(() -> new IllegalStateException("Create an Orientation draft first."));
        requireEditableBundle(bundle);
        return bundle;
    }

    private void requireEditableBundle(EsCommunicationBundle bundle) {
        if (bundle.getStatus() == EsCommunicationBundle.Status.DRAFT) {
            return;
        }
        EsCommunicationBundlePurpose purpose = purposeDao.findById(bundle.getPurposeId())
                .orElseThrow(() -> new IllegalStateException("The bundle purpose was not found."));
        if (bundle.getStatus() != EsCommunicationBundle.Status.PUBLISHED
                || purpose.getMode() != EsCommunicationBundlePurpose.Mode.LIVING) {
            throw new IllegalStateException("Only drafts and published living bundles can be edited.");
        }
    }

    public EsCommunicationBundle createTopicOrientationDraft(User user, Long topicId) {
        EsTopic topic = requireSteward(user, topicId);
        EsCommunicationBundlePurpose purpose = purposeDao.findActiveByKey(TOPIC_ORIENTATION_KEY)
                .orElseThrow(() -> new IllegalStateException("The Topic Orientation purpose is not configured."));
        if (purpose.getActiveTemplateId() == null) {
            throw new IllegalStateException("The Topic Orientation has no active template.");
        }
        EsCommunicationBundleTemplate template = templateDao.findById(purpose.getActiveTemplateId())
                .orElseThrow(() -> new IllegalStateException("The active Topic Orientation template was not found."));
        if (!purpose.getPurposeId().equals(template.getPurposeId())
                || template.getStatus() != EsCommunicationBundleTemplate.Status.ACTIVE) {
            throw new IllegalStateException("The active Topic Orientation template is inconsistent.");
        }
        if (bundleDao.findByTopicAndPurpose(topicId, purpose.getPurposeId()).isPresent()) {
            throw new IllegalStateException("A Topic Orientation already exists for this Topic.");
        }

        EsCommunicationBundle bundle = new EsCommunicationBundle();
        bundle.setEsTopicId(topic.getEsTopicId());
        bundle.setPurposeId(purpose.getPurposeId());
        bundle.setTemplateId(template.getTemplateId());
        bundle.setSingleInstanceGuard(purpose.getInstancePolicy() == InstancePolicy.SINGLE ? 1 : null);
        bundle.setStatus(EsCommunicationBundle.Status.DRAFT);
        bundle.setAudience(defaultAudience(topic, purpose));
        bundle.setCreatedByUserId(user.getUserId());
        bundle.setUpdatedByUserId(user.getUserId());
        return bundleDao.saveDraftWithAudit(bundle, user.getUserId());
    }

    public Optional<EsCommunicationBundle> findTopicOrientationForViewer(User user, Long topicId) {
        EsTopic topic = topicDao.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic was not found."));
        if (!access.canViewTopic(user, topic)) {
            return Optional.empty();
        }
        Optional<EsCommunicationBundlePurpose> purpose = purposeDao.findByKey(TOPIC_ORIENTATION_KEY);
        if (purpose.isEmpty()) {
            return Optional.empty();
        }
        Optional<EsCommunicationBundle> bundle =
                bundleDao.findByTopicAndPurpose(topicId, purpose.get().getPurposeId());
        if (bundle.isEmpty() || !canViewBundle(user, topic, bundle.get())) {
            return Optional.empty();
        }
        return bundle;
    }

    public EsCommunicationBundleResourcePlacement addResourcePlacement(User user, Long bundleId,
            Long componentId, Long resourceId, int displayOrder, String contextNote) {
        EsCommunicationBundle bundle = bundleDao.findById(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("Communication Bundle was not found."));
        EsTopic topic = requireSteward(user, bundle.getEsTopicId());
        requireEditableBundle(bundle);
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Display order cannot be negative.");
        }
        EsCommunicationBundleTemplateComponent component = componentDao.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Template component was not found."));
        if (!bundle.getTemplateId().equals(component.getTemplateId())
                || (component.getKind() != EsCommunicationBundleTemplateComponent.Kind.RESOURCE
                        && component.getKind()
                                != EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION)) {
            throw new IllegalArgumentException("The component cannot contain resources for this bundle.");
        }
        EsTopicResource resource = resourceDao.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Topic Resource was not found."));
        if (!topic.getEsTopicId().equals(resource.getEsTopicId())
                || resource.getStatus() != EsTopicResource.Status.ACTIVE) {
            throw new IllegalArgumentException("The resource is not active on this Topic.");
        }
        List<EsCommunicationBundleResourcePlacement> existingPlacements =
                placementDao.findByBundleAndComponent(bundleId, componentId);
        if (component.getCardinality() == EsCommunicationBundleTemplateComponent.Cardinality.SINGLE
                && !existingPlacements.isEmpty()) {
            throw new IllegalStateException("This template component accepts only one resource.");
        }
        if (existingPlacements.stream().anyMatch(existing -> existing.getDisplayOrder() == displayOrder)) {
            throw new IllegalArgumentException("A resource already uses this display order in the component.");
        }

        EsCommunicationBundleResourcePlacement placement = new EsCommunicationBundleResourcePlacement();
        placement.setBundleId(bundle.getBundleId());
        placement.setEsTopicId(topic.getEsTopicId());
        placement.setTemplateId(bundle.getTemplateId());
        placement.setComponentId(component.getComponentId());
        placement.setTopicResourceId(resource.getTopicResourceId());
        placement.setDisplayOrder(displayOrder);
        placement.setContextNote(optional(contextNote, 20000));
        placement.setCreatedByUserId(user.getUserId());
        return placementDao.saveEditablePlacementWithAudit(placement, user.getUserId());
    }

    public EsCommunicationBundleComponentValue setTextComponentValue(
            User user, Long bundleId, Long componentId, String content) {
        return setNarrativeComponentValue(user, bundleId, componentId, content,
                EsCommunicationBundleTemplateComponent.Kind.TEXT);
    }

    public EsCommunicationBundleComponentValue setStructuredListComponentValue(
            User user, Long topicId, Long bundleId, Long componentId, String content) {
        var bundle = bundleDao.findById(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("Communication Bundle was not found."));
        if (!topicId.equals(bundle.getEsTopicId())) {
            throw new IllegalArgumentException("The bundle does not belong to this Topic.");
        }
        return setNarrativeComponentValue(user, bundleId, componentId, content,
                EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST);
    }

    private EsCommunicationBundleComponentValue setNarrativeComponentValue(
            User user, Long bundleId, Long componentId, String content, EsCommunicationBundleTemplateComponent.Kind kind) {
        EsCommunicationBundle bundle = bundleDao.findById(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("Communication Bundle was not found."));
        requireSteward(user, bundle.getEsTopicId());
        requireEditableBundle(bundle);
        EsCommunicationBundleTemplateComponent component = componentDao.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Template component was not found."));
        if (!bundle.getTemplateId().equals(component.getTemplateId())
                || component.getKind() != kind) {
            throw new IllegalArgumentException("The component is not the requested field kind in this bundle's template.");
        }
        String text = optional(content, 50000);
        if (bundle.getStatus() == EsCommunicationBundle.Status.PUBLISHED
                && component.isRequired() && text == null) {
            throw new IllegalStateException(component.getDisplayName() + " is required for a published bundle.");
        }
        EsCommunicationBundleComponentValue value = componentValueDao
                .findByBundleAndComponent(bundleId, componentId)
                .orElseGet(EsCommunicationBundleComponentValue::new);
        value.setBundleId(bundle.getBundleId());
        value.setTemplateId(bundle.getTemplateId());
        value.setComponentId(component.getComponentId());
        value.setContentText(kind == EsCommunicationBundleTemplateComponent.Kind.TEXT ? text : null);
        value.setContentJson(kind == EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST
                ? CommunicationBundleStructuredList.fromLines(text) : null);
        value.setUpdatedByUserId(user.getUserId());
        return componentValueDao.saveEditableValue(value, component.getSemanticKey());
    }

    public EsCommunicationBundleComponentValue setTextComponentValue(
            User user, Long topicId, Long bundleId, Long componentId, String content) {
        EsCommunicationBundle bundle = bundleDao.findById(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("Communication Bundle was not found."));
        if (!topicId.equals(bundle.getEsTopicId())) {
            throw new IllegalArgumentException("The Orientation does not belong to this Topic.");
        }
        return setTextComponentValue(user, bundleId, componentId, content);
    }

    public EsCommunicationBundle saveOrientationSettings(User user, Long topicId, Audience audience,
            Integer month, Integer year) {
        EsCommunicationBundle bundle = requireEditableOrientation(user, topicId);
        if (audience == null || !allowedAudiences(topicId).contains(audience)) {
            throw new IllegalArgumentException("Choose an audience no broader than this Topic's visibility.");
        }
        if ((month == null) != (year == null)
                || (month != null && (month < 1 || month > 12 || year < 1 || year > 9999))) {
            throw new IllegalArgumentException("Enter both a Month from 1 to 12 and a Year from 1 to 9999, or leave both blank.");
        }
        if (bundle.getStatus() == EsCommunicationBundle.Status.PUBLISHED && month == null) {
            throw new IllegalArgumentException("A published Orientation must retain its communication Month and Year.");
        }
        return bundleDao.updateEditableSettings(bundle.getBundleId(), audience, month, year, user.getUserId());
    }

    public EsCommunicationBundle publishOrientation(User user, Long topicId) {
        EsCommunicationBundle bundle = requireDraftOrientation(user, topicId);
        if (bundle.getCommunicationMonth() == null || bundle.getCommunicationYear() == null) {
            throw new IllegalStateException("Set the communication Month and Year before publishing.");
        }
        if (!allowedAudiences(topicId).contains(bundle.getAudience())) {
            throw new IllegalStateException("The selected audience is broader than this Topic's visibility.");
        }
        List<EsCommunicationBundleTemplateComponent> components =
                componentDao.findByTemplateId(bundle.getTemplateId());
        Map<Long, EsCommunicationBundleComponentValue> values = componentValueDao
                .findByBundleId(bundle.getBundleId()).stream()
                .collect(Collectors.toMap(EsCommunicationBundleComponentValue::getComponentId, value -> value));
        List<EsCommunicationBundleResourcePlacement> placements =
                placementDao.findByBundleOrdered(bundle.getBundleId());
        for (EsCommunicationBundleTemplateComponent component : components) {
            if (!component.isRequired()) {
                continue;
            }
            boolean complete = switch (component.getKind()) {
                case TEXT -> values.containsKey(component.getComponentId())
                        && trimToNull(values.get(component.getComponentId()).getContentText()) != null;
                case STRUCTURED_LIST -> values.containsKey(component.getComponentId())
                        && !CommunicationBundleStructuredList.items(values.get(component.getComponentId()).getContentJson()).isEmpty();
                case RESOURCE, RESOURCE_COLLECTION -> placements.stream()
                        .anyMatch(p -> p.getComponentId().equals(component.getComponentId()));
            };
            if (!complete) {
                throw new IllegalStateException(component.getDisplayName() + " is required before publishing.");
            }
        }
        return bundleDao.publishDraft(bundle.getBundleId(), user.getUserId());
    }

    public EsCommunicationBundle retireOrientation(User user, Long topicId) {
        requireSteward(user, topicId);
        EsCommunicationBundle bundle = findTopicOrientationForViewer(user, topicId)
                .orElseThrow(() -> new IllegalStateException("There is no current Orientation to retire."));
        if (bundle.getStatus() != EsCommunicationBundle.Status.PUBLISHED) {
            throw new IllegalStateException("Only a published Orientation can be retired.");
        }
        return bundleDao.retirePublished(bundle.getBundleId(), user.getUserId());
    }

    public List<EsCommunicationBundleResourcePlacement> listResourcePlacementsForViewer(
            User user, Long bundleId) {
        EsCommunicationBundle bundle = bundleDao.findById(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("Communication Bundle was not found."));
        EsTopic topic = topicDao.findById(bundle.getEsTopicId())
                .orElseThrow(() -> new IllegalStateException("The bundle Topic was not found."));
        if (!canViewBundle(user, topic, bundle)) {
            return List.of();
        }
        return placementDao.findByBundleOrdered(bundleId);
    }

    private boolean canViewBundle(User user, EsTopic topic, EsCommunicationBundle bundle) {
        if (bundle.getStatus() == EsCommunicationBundle.Status.DRAFT
                || bundle.getStatus() == EsCommunicationBundle.Status.RETIRED) {
            return access.canEditTopic(user, topic);
        }
        if (!access.canViewTopic(user, topic)) {
            return false;
        }
        if (access.canEditTopic(user, topic)) {
            return true;
        }
        Long spaceId = topic.getEsTopicSpaceId();
        return switch (bundle.getAudience()) {
            case PUBLIC -> true;
            case PARTICIPANTS -> access.canParticipateInSpace(user, spaceId);
            case STEWARDS -> access.canAdministerSpace(user, spaceId);
        };
    }

    private EsTopic requireSteward(User user, Long topicId) {
        if (user == null || user.getUserId() == null) {
            throw new SecurityException("Authentication is required.");
        }
        EsTopic topic = topicDao.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic was not found."));
        if (!access.canEditTopic(user, topic)) {
            throw new SecurityException("Only Topic stewards may manage Topic Resources and bundles.");
        }
        return topic;
    }

    private Audience defaultAudience(EsTopic topic, EsCommunicationBundlePurpose purpose) {
        if (topic.getEsTopicSpaceId() == null) {
            return purpose.getDefaultAudience();
        }
        return topicSpaceDao.findById(topic.getEsTopicSpaceId())
                .map(space -> space.getVisibility() == EsTopicSpace.Visibility.PUBLIC
                        ? Audience.PUBLIC : Audience.PARTICIPANTS)
                .orElse(purpose.getDefaultAudience());
    }

    private List<Audience> allowedAudiences(Long topicId) {
        EsTopic topic = topicDao.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic was not found."));
        boolean publicTopic = topic.getEsTopicSpaceId() != null
                && topicSpaceDao.findById(topic.getEsTopicSpaceId())
                        .map(space -> space.getVisibility() == EsTopicSpace.Visibility.PUBLIC)
                        .orElse(false);
        return publicTopic ? List.of(Audience.PUBLIC, Audience.PARTICIPANTS, Audience.STEWARDS)
                : List.of(Audience.PARTICIPANTS, Audience.STEWARDS);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ResourceType resourceTypeFor(StoredFile file) {
        String contentType = file.getContentType();
        if (file.isImage()) {
            return ResourceType.IMAGE;
        }
        return switch (contentType) {
            case "application/pdf" -> ResourceType.PDF;
            case "text/plain", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                ResourceType.DOCUMENT;
            case "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                ResourceType.PRESENTATION;
            default -> throw new IllegalArgumentException("This stored-file type cannot be a Topic Resource.");
        };
    }

    private static String validateExternalUrl(String value) {
        String url = required(value, "External URL", 2000);
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("External URL must be an http or https URL with a host.");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("External URL is invalid.", ex);
        }
        return url;
    }

    private static String required(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " is required and must be at most "
                    + maxLength + " characters.");
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Text must be at most " + maxLength + " characters.");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
