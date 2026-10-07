package org.airahub.interophub.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
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
        return bundleDao.save(bundle);
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
        if (bundle.getStatus() != EsCommunicationBundle.Status.DRAFT) {
            throw new IllegalStateException("Resources can only be placed in a draft bundle.");
        }
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
        return placementDao.save(placement);
    }

    public EsCommunicationBundleComponentValue setTextComponentValue(
            User user, Long bundleId, Long componentId, String content) {
        EsCommunicationBundle bundle = bundleDao.findById(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("Communication Bundle was not found."));
        requireSteward(user, bundle.getEsTopicId());
        if (bundle.getStatus() != EsCommunicationBundle.Status.DRAFT) {
            throw new IllegalStateException("Only a draft bundle can be edited.");
        }
        EsCommunicationBundleTemplateComponent component = componentDao.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Template component was not found."));
        if (!bundle.getTemplateId().equals(component.getTemplateId())
                || component.getKind() != EsCommunicationBundleTemplateComponent.Kind.TEXT) {
            throw new IllegalArgumentException("The component is not a text field in this bundle's template.");
        }
        String text = optional(content, 50000);
        EsCommunicationBundleComponentValue value = componentValueDao
                .findByBundleAndComponent(bundleId, componentId)
                .orElseGet(EsCommunicationBundleComponentValue::new);
        value.setBundleId(bundle.getBundleId());
        value.setTemplateId(bundle.getTemplateId());
        value.setComponentId(component.getComponentId());
        value.setContentText(text);
        value.setContentJson(null);
        value.setUpdatedByUserId(user.getUserId());
        return componentValueDao.save(value);
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
