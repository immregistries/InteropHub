package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.airahub.interophub.model.EsCommunicationBundleResourcePlacement;
import org.airahub.interophub.model.EsCommunicationBundleTemplate;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicResource;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.StoredFile;
import org.airahub.interophub.model.User;
import org.junit.jupiter.api.Test;

class CommunicationBundleServiceTest {

    @Test
    void registersStoredFilesWithTechnicalTypeAndEnforcesStewardship() {
        Fixture fixture = new Fixture();
        StoredFile file = new StoredFile();
        file.setStoredFileId(21L);
        file.setContentType("application/pdf");
        fixture.files.file = file;
        CommunicationBundleService service = fixture.service();

        EsTopicResource resource = service.registerStoredFileResource(
                fixture.steward, 10L, 21L, "  Guide  ", "  Details  ", null);

        assertEquals(EsTopicResource.ResourceType.PDF, resource.getResourceType());
        assertEquals("Guide", resource.getTitle());
        assertEquals("Details", resource.getDescription());
        assertEquals(10L, resource.getEsTopicId());
        fixture.access.canEdit = false;
        assertThrows(SecurityException.class,
                () -> service.registerStoredFileResource(fixture.steward, 10L, 21L, "Guide", null, null));
    }

    @Test
    void externalLinksRequireHttpOrHttpsWithAHost() {
        Fixture fixture = new Fixture();
        CommunicationBundleService service = fixture.service();

        EsTopicResource resource = service.registerExternalLinkResource(
                fixture.steward, 10L, "https://example.org/resource", "Resource", null, null);
        assertEquals("https://example.org/resource", resource.getExternalUrl());
        assertEquals(EsTopicResource.ResourceType.EXTERNAL_LINK, resource.getResourceType());
        assertThrows(IllegalArgumentException.class,
                () -> service.registerExternalLinkResource(
                        fixture.steward, 10L, "javascript:alert(1)", "Unsafe", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerExternalLinkResource(
                        fixture.steward, 10L, "https://user:pass@example.org/", "Credentials", null, null));
    }

    @Test
    void createsSingleDraftOrientationFromTheActiveTemplateForSpaceStewards() {
        Fixture fixture = new Fixture();
        EsCommunicationBundlePurpose purpose = new EsCommunicationBundlePurpose();
        purpose.setPurposeId(4L);
        purpose.setPurposeKey(CommunicationBundleService.TOPIC_ORIENTATION_KEY);
        purpose.setActiveTemplateId(5L);
        purpose.setInstancePolicy(EsCommunicationBundlePurpose.InstancePolicy.SINGLE);
        purpose.setDefaultAudience(EsCommunicationBundlePurpose.Audience.STEWARDS);
        fixture.purposes.purpose = purpose;
        EsCommunicationBundleTemplate template = new EsCommunicationBundleTemplate();
        template.setTemplateId(5L);
        template.setPurposeId(4L);
        template.setStatus(EsCommunicationBundleTemplate.Status.ACTIVE);
        fixture.templates.template = template;

        EsCommunicationBundle bundle = fixture.service().createTopicOrientationDraft(fixture.steward, 10L);

        assertEquals(EsCommunicationBundle.Status.DRAFT, bundle.getStatus());
        assertEquals(EsCommunicationBundlePurpose.Audience.PUBLIC, bundle.getAudience());
        assertEquals(1, bundle.getSingleInstanceGuard());
        assertEquals(5L, bundle.getTemplateId());
        assertTrue(fixture.bundles.saved == bundle);
        fixture.bundles.existing = bundle;
        assertThrows(IllegalStateException.class,
                () -> fixture.service().createTopicOrientationDraft(fixture.steward, 10L));
    }

    @Test
    void onlyDraftBundlePlacementsCanUseResourcesFromTheSameTopicAndResourceSlots() {
        Fixture fixture = new Fixture();
        EsCommunicationBundle bundle = new EsCommunicationBundle();
        bundle.setBundleId(30L);
        bundle.setEsTopicId(10L);
        bundle.setTemplateId(5L);
        bundle.setStatus(EsCommunicationBundle.Status.DRAFT);
        fixture.bundles.bundle = bundle;

        EsCommunicationBundleTemplateComponent component = new EsCommunicationBundleTemplateComponent();
        component.setComponentId(40L);
        component.setTemplateId(5L);
        component.setKind(EsCommunicationBundleTemplateComponent.Kind.RESOURCE);
        component.setCardinality(EsCommunicationBundleTemplateComponent.Cardinality.SINGLE);
        fixture.components.component = component;

        EsTopicResource resource = new EsTopicResource();
        resource.setTopicResourceId(50L);
        resource.setEsTopicId(10L);
        resource.setStatus(EsTopicResource.Status.ACTIVE);
        fixture.resources.resource = resource;

        EsCommunicationBundleResourcePlacement placement = fixture.service()
                .addResourcePlacement(fixture.steward, 30L, 40L, 50L, 0, "Introductory visual");
        assertEquals(10L, placement.getEsTopicId());
        assertEquals(5L, placement.getTemplateId());
        assertEquals(50L, placement.getTopicResourceId());

        resource.setEsTopicId(11L);
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service().addResourcePlacement(fixture.steward, 30L, 40L, 50L, 1, null));

        resource.setEsTopicId(10L);
        fixture.placements.existing = List.of(placement);
        assertThrows(IllegalStateException.class,
                () -> fixture.service().addResourcePlacement(fixture.steward, 30L, 40L, 50L, 1, null));
    }

    @Test
    void draftBundlesAreNotVisibleToTopicAudience() {
        Fixture fixture = new Fixture();
        EsCommunicationBundlePurpose purpose = new EsCommunicationBundlePurpose();
        purpose.setPurposeId(4L);
        purpose.setPurposeKey(CommunicationBundleService.TOPIC_ORIENTATION_KEY);
        fixture.purposes.purpose = purpose;
        EsCommunicationBundle bundle = new EsCommunicationBundle();
        bundle.setBundleId(1L);
        bundle.setEsTopicId(10L);
        bundle.setPurposeId(4L);
        bundle.setStatus(EsCommunicationBundle.Status.DRAFT);
        bundle.setAudience(EsCommunicationBundlePurpose.Audience.PUBLIC);
        fixture.bundles.bundle = bundle;
        fixture.access.canEdit = false;

        assertTrue(fixture.service().findTopicOrientationForViewer(fixture.viewer, 10L).isEmpty());
        fixture.access.canEdit = true;
        assertFalse(fixture.service().findTopicOrientationForViewer(fixture.steward, 10L).isEmpty());
    }

    @Test
    void publishedAudienceScopesCannotExceedTopicVisibility() {
        Fixture fixture = new Fixture();
        EsCommunicationBundlePurpose purpose = new EsCommunicationBundlePurpose();
        purpose.setPurposeId(4L);
        purpose.setPurposeKey(CommunicationBundleService.TOPIC_ORIENTATION_KEY);
        fixture.purposes.purpose = purpose;
        EsCommunicationBundle bundle = new EsCommunicationBundle();
        bundle.setBundleId(1L);
        bundle.setEsTopicId(10L);
        bundle.setPurposeId(4L);
        bundle.setStatus(EsCommunicationBundle.Status.PUBLISHED);
        fixture.bundles.bundle = bundle;

        bundle.setAudience(EsCommunicationBundlePurpose.Audience.PUBLIC);
        assertTrue(fixture.service().findTopicOrientationForViewer(fixture.viewer, 10L).isPresent());

        bundle.setAudience(EsCommunicationBundlePurpose.Audience.PARTICIPANTS);
        assertTrue(fixture.service().findTopicOrientationForViewer(fixture.viewer, 10L).isEmpty());
        fixture.access.canParticipate = true;
        assertTrue(fixture.service().findTopicOrientationForViewer(fixture.viewer, 10L).isPresent());

        bundle.setAudience(EsCommunicationBundlePurpose.Audience.STEWARDS);
        fixture.access.canEdit = false;
        assertTrue(fixture.service().findTopicOrientationForViewer(fixture.viewer, 10L).isEmpty());
        fixture.access.canEdit = true;
        assertTrue(fixture.service().findTopicOrientationForViewer(fixture.viewer, 10L).isPresent());
        fixture.access.canViewTopic = false;
        assertTrue(fixture.service().findTopicOrientationForViewer(fixture.viewer, 10L).isEmpty());
    }

    @Test
    void storesNarrativeOnlyInDraftTextComponents() {
        Fixture fixture = new Fixture();
        EsCommunicationBundle bundle = new EsCommunicationBundle();
        bundle.setBundleId(30L);
        bundle.setEsTopicId(10L);
        bundle.setTemplateId(5L);
        bundle.setStatus(EsCommunicationBundle.Status.DRAFT);
        fixture.bundles.bundle = bundle;
        EsCommunicationBundleTemplateComponent component = new EsCommunicationBundleTemplateComponent();
        component.setComponentId(40L);
        component.setTemplateId(5L);
        component.setKind(EsCommunicationBundleTemplateComponent.Kind.TEXT);
        fixture.components.component = component;

        EsCommunicationBundleComponentValue value = fixture.service()
                .setTextComponentValue(fixture.steward, 30L, 40L, "  Introductory narrative  ");

        assertEquals("Introductory narrative", value.getContentText());
        assertEquals(30L, value.getBundleId());
        assertEquals(40L, value.getComponentId());
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service().setTextComponentValue(fixture.steward, 30L, 41L, "Not in template"));
        bundle.setStatus(EsCommunicationBundle.Status.PUBLISHED);
        assertThrows(IllegalStateException.class,
                () -> fixture.service().setTextComponentValue(fixture.steward, 30L, 40L, "No longer draft"));
    }

    private static final class Fixture {
        private final EsTopic topic = topic(10L, 100L);
        private final User steward = user(1L);
        private final User viewer = user(2L);
        private final Access access = new Access();
        private final Topics topics = new Topics();
        private final Spaces spaces = new Spaces();
        private final Resources resources = new Resources();
        private final Purposes purposes = new Purposes();
        private final Bundles bundles = new Bundles();
        private final ComponentValues componentValues = new ComponentValues();
        private final Templates templates = new Templates();
        private final Components components = new Components();
        private final Placements placements = new Placements();
        private final Files files = new Files();

        private Fixture() {
            topics.topic = topic;
            spaces.space = space(100L, EsTopicSpace.Visibility.PUBLIC);
        }

        private CommunicationBundleService service() {
            return new CommunicationBundleService(topics, spaces, resources, purposes, bundles,
                    componentValues, templates, components, placements, files, access);
        }

        private static EsTopic topic(Long id, Long spaceId) {
            EsTopic topic = new EsTopic();
            topic.setEsTopicId(id);
            topic.setEsTopicSpaceId(spaceId);
            return topic;
        }

        private static EsTopicSpace space(Long id, EsTopicSpace.Visibility visibility) {
            EsTopicSpace space = new EsTopicSpace();
            space.setEsTopicSpaceId(id);
            space.setVisibility(visibility);
            return space;
        }

        private static User user(Long id) {
            User user = new User();
            user.setUserId(id);
            return user;
        }
    }

    private static final class Access extends TopicSpaceAccessService {
        private boolean canEdit = true;
        private boolean canParticipate;
        private boolean canViewTopic = true;

        @Override public boolean canEditTopic(User user, EsTopic topic) { return canEdit; }
        @Override public boolean canViewTopic(User user, EsTopic topic) { return canViewTopic; }
        @Override public boolean canParticipateInSpace(User user, Long spaceId) { return canParticipate; }
        @Override public boolean canAdministerSpace(User user, Long spaceId) { return canEdit; }
    }

    private static final class Topics extends EsTopicDao {
        private EsTopic topic;
        @Override public Optional<EsTopic> findById(Long id) { return Optional.ofNullable(topic); }
    }

    private static final class Spaces extends EsTopicSpaceDao {
        private EsTopicSpace space;
        @Override public Optional<EsTopicSpace> findById(Long id) { return Optional.ofNullable(space); }
    }

    private static final class Resources extends EsTopicResourceDao {
        private EsTopicResource resource;
        @Override public EsTopicResource save(EsTopicResource value) { resource = value; return value; }
        @Override public Optional<EsTopicResource> findByStoredFileId(Long id) { return Optional.empty(); }
        @Override public Optional<EsTopicResource> findById(Long id) { return Optional.ofNullable(resource); }
    }

    private static final class Purposes extends EsCommunicationBundlePurposeDao {
        private EsCommunicationBundlePurpose purpose;
        @Override public Optional<EsCommunicationBundlePurpose> findActiveByKey(String key) {
            return Optional.ofNullable(purpose);
        }
        @Override public Optional<EsCommunicationBundlePurpose> findByKey(String key) {
            return Optional.ofNullable(purpose);
        }
    }

    private static final class Bundles extends EsCommunicationBundleDao {
        private EsCommunicationBundle existing;
        private EsCommunicationBundle bundle;
        private EsCommunicationBundle saved;
        @Override public Optional<EsCommunicationBundle> findByTopicAndPurpose(Long topicId, Long purposeId) {
            return Optional.ofNullable(existing != null ? existing : bundle);
        }
        @Override public Optional<EsCommunicationBundle> findById(Long id) {
            return Optional.ofNullable(bundle != null ? bundle : saved);
        }
        @Override public EsCommunicationBundle save(EsCommunicationBundle value) { saved = value; return value; }
    }

    private static final class Templates extends EsCommunicationBundleTemplateDao {
        private EsCommunicationBundleTemplate template;
        @Override public Optional<EsCommunicationBundleTemplate> findById(Long id) {
            return Optional.ofNullable(template);
        }
    }

    private static final class ComponentValues extends EsCommunicationBundleComponentValueDao {
        @Override public Optional<EsCommunicationBundleComponentValue> findByBundleAndComponent(
                Long bundleId, Long componentId) { return Optional.empty(); }
        @Override public EsCommunicationBundleComponentValue save(EsCommunicationBundleComponentValue value) {
            return value;
        }
    }

    private static final class Components extends EsCommunicationBundleTemplateComponentDao {
        private EsCommunicationBundleTemplateComponent component;
        @Override public Optional<EsCommunicationBundleTemplateComponent> findById(Long id) {
            return component != null && component.getComponentId().equals(id)
                    ? Optional.of(component) : Optional.empty();
        }
    }

    private static final class Placements extends EsCommunicationBundleResourcePlacementDao {
        private List<EsCommunicationBundleResourcePlacement> existing = List.of();
        @Override public List<EsCommunicationBundleResourcePlacement> findByBundleAndComponent(
                Long bundleId, Long componentId) { return existing; }
        @Override public EsCommunicationBundleResourcePlacement save(
                EsCommunicationBundleResourcePlacement value) { return value; }
    }

    private static final class Files extends StoredFileDao {
        private StoredFile file;
        @Override public Optional<StoredFile> findById(Long id) { return Optional.ofNullable(file); }
    }
}
