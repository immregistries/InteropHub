package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void resourceMetadataEditsAreScopedToAnActiveTopicResourceAndValidateExternalUrls() {
        Fixture fixture = new Fixture();
        var resource = fixture.service().registerExternalLinkResource(fixture.steward, 10L,
                "https://example.org/old", "Old", null, null);
        resource.setTopicResourceId(50L);
        resource.setStatus(EsTopicResource.Status.ACTIVE);
        fixture.service().updateResourceMetadata(fixture.steward, 10L, 50L, " New ", " Description ",
                " Author ", "https://example.org/new");
        assertEquals("New", resource.getTitle());
        assertEquals("Description", resource.getDescription());
        assertEquals("Author", resource.getAttribution());
        assertEquals("https://example.org/new", resource.getExternalUrl());
        assertThrows(IllegalArgumentException.class, () -> fixture.service().updateResourceMetadata(
                fixture.steward, 10L, 50L, "Bad", null, null, "javascript:alert(1)"));
        assertEquals("New", resource.getTitle());
        resource.setEsTopicId(11L);
        assertThrows(IllegalArgumentException.class, () -> fixture.service().updateResourceMetadata(
                fixture.steward, 10L, 50L, "Bad", null, null, "https://example.org/"));
        resource.setEsTopicId(10L);
        resource.setStatus(EsTopicResource.Status.ARCHIVED);
        assertThrows(IllegalArgumentException.class, () -> fixture.service().updateResourceMetadata(
                fixture.steward, 10L, 50L, "Bad", null, null, "https://example.org/"));
        fixture.access.canEdit = false;
        assertThrows(SecurityException.class, () -> fixture.service().updateResourceMetadata(
                fixture.steward, 10L, 50L, "Bad", null, null, "https://example.org/"));
    }

    @Test
    void orientationResourceDetailsDoNotReadDraftPlacementsForNonStewards() {
        Fixture fixture = new Fixture();
        var purpose = new EsCommunicationBundlePurpose();
        purpose.setPurposeId(4L);
        fixture.purposes.purpose = purpose;
        var bundle = new EsCommunicationBundle();
        bundle.setBundleId(30L);
        bundle.setEsTopicId(10L);
        bundle.setPurposeId(4L);
        bundle.setStatus(EsCommunicationBundle.Status.DRAFT);
        fixture.bundles.bundle = bundle;
        fixture.access.canEdit = false;
        assertTrue(fixture.service().findOrientationResourcesForViewer(fixture.viewer, 10L).isEmpty());
        assertThrows(SecurityException.class,
                () -> fixture.service().selectOrientationResource(fixture.viewer, 10L, 40L, 50L));
        assertThrows(SecurityException.class,
                () -> fixture.service().removeOrientationResource(fixture.viewer, 10L, 60L));
        fixture.access.canEdit = true;
        bundle.setStatus(EsCommunicationBundle.Status.PUBLISHED);
        bundle.setAudience(EsCommunicationBundlePurpose.Audience.PUBLIC);
        assertThrows(IllegalStateException.class,
                () -> fixture.service().selectOrientationResource(fixture.steward, 10L, 40L, 50L));
        assertThrows(IllegalStateException.class,
                () -> fixture.service().removeOrientationResource(fixture.steward, 10L, 60L));
    }

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
        bundle.setPurposeId(4L);
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
        fixture.access.canEdit = false;

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
    void publishingRequiresMonthYearAndEveryRequiredComponent() {
        Fixture fixture = new Fixture();
        EsCommunicationBundlePurpose purpose = Fixture.orientationPurpose();
        fixture.purposes.purpose = purpose;
        EsCommunicationBundle bundle = Fixture.orientationBundle(30L, EsCommunicationBundle.Status.DRAFT);
        fixture.bundles.bundle = bundle;
        var requiredNarrative = new EsCommunicationBundleTemplateComponent();
        requiredNarrative.setComponentId(40L);
        requiredNarrative.setTemplateId(5L);
        requiredNarrative.setSemanticKey("introduction");
        requiredNarrative.setDisplayName("Introduction");
        requiredNarrative.setKind(EsCommunicationBundleTemplateComponent.Kind.TEXT);
        requiredNarrative.setRequired(true);
        fixture.components.byTemplate = List.of(requiredNarrative);

        assertThrows(IllegalStateException.class,
                () -> fixture.service().publishOrientation(fixture.steward, 10L));
        bundle.setCommunicationMonth(4);
        bundle.setCommunicationYear(2026);
        assertThrows(IllegalStateException.class,
                () -> fixture.service().publishOrientation(fixture.steward, 10L));

        EsCommunicationBundleComponentValue value = new EsCommunicationBundleComponentValue();
        value.setComponentId(40L);
        value.setContentText("A clear introduction.");
        fixture.componentValues.saved = value;
        assertEquals(EsCommunicationBundle.Status.PUBLISHED,
                fixture.service().publishOrientation(fixture.steward, 10L).getStatus());
    }

    @Test
    void publishingAllowsAllThreeOptionalNarrativesToBeBlank() {
        Fixture fixture = new Fixture();
        fixture.purposes.purpose = Fixture.orientationPurpose();
        var bundle = Fixture.orientationBundle(30L, EsCommunicationBundle.Status.DRAFT);
        bundle.setCommunicationMonth(10);
        bundle.setCommunicationYear(2026);
        fixture.bundles.bundle = bundle;
        fixture.components.byTemplate = List.of("introduction", "why_it_matters", "how_to_get_involved")
                .stream().map(key -> {
                    var component = new EsCommunicationBundleTemplateComponent();
                    component.setKind(EsCommunicationBundleTemplateComponent.Kind.TEXT);
                    component.setSemanticKey(key);
                    component.setRequired(false);
                    return component;
                }).toList();

        assertEquals(EsCommunicationBundle.Status.PUBLISHED,
                fixture.service().publishOrientation(fixture.steward, 10L).getStatus());
    }

    @Test
    void publishedOrientationCanBeRetiredAndReplacedByANewDraft() {
        Fixture fixture = new Fixture();
        fixture.purposes.purpose = Fixture.orientationPurpose();
        EsCommunicationBundle oldBundle = Fixture.orientationBundle(30L, EsCommunicationBundle.Status.PUBLISHED);
        oldBundle.setSingleInstanceGuard(1);
        fixture.bundles.bundle = oldBundle;
        EsCommunicationBundleTemplate template = new EsCommunicationBundleTemplate();
        template.setTemplateId(5L);
        template.setPurposeId(4L);
        template.setStatus(EsCommunicationBundleTemplate.Status.ACTIVE);
        fixture.templates.template = template;

        assertEquals(EsCommunicationBundle.Status.RETIRED,
                fixture.service().retireOrientation(fixture.steward, 10L).getStatus());
        assertEquals(null, oldBundle.getSingleInstanceGuard());
        EsCommunicationBundle replacement = fixture.service().createTopicOrientationDraft(fixture.steward, 10L);
        assertEquals(EsCommunicationBundle.Status.DRAFT, replacement.getStatus());
        assertTrue(replacement != oldBundle);
    }

    @Test
    void audienceSettingsCannotBroadenPrivateTopicVisibility() {
        Fixture fixture = new Fixture();
        fixture.spaces.space.setVisibility(EsTopicSpace.Visibility.PRIVATE);
        fixture.purposes.purpose = Fixture.orientationPurpose();
        fixture.bundles.bundle = Fixture.orientationBundle(30L, EsCommunicationBundle.Status.DRAFT);

        assertThrows(IllegalArgumentException.class, () -> fixture.service().saveOrientationSettings(
                fixture.steward, 10L, EsCommunicationBundlePurpose.Audience.PUBLIC, null, null));
        assertThrows(IllegalArgumentException.class, () -> fixture.service().saveOrientationSettings(
                fixture.steward, 10L, EsCommunicationBundlePurpose.Audience.PARTICIPANTS, 4, null));
        assertEquals(EsCommunicationBundlePurpose.Audience.PARTICIPANTS,
                fixture.service().saveOrientationSettings(fixture.steward, 10L,
                        EsCommunicationBundlePurpose.Audience.PARTICIPANTS, null, null).getAudience());
    }

    @Test
    void publishedLivingOrientationCanUpdateSettingsAndRolesWithoutRepublishing() {
        Fixture fixture = new Fixture();
        fixture.purposes.purpose = Fixture.orientationPurpose();
        var bundle = Fixture.orientationBundle(30L, EsCommunicationBundle.Status.PUBLISHED);
        bundle.setPublishedAt(java.time.LocalDateTime.of(2026, 9, 1, 12, 0));
        fixture.bundles.bundle = bundle;

        var updated = fixture.service().saveOrientationSettings(fixture.steward, 10L,
                EsCommunicationBundlePurpose.Audience.PARTICIPANTS, 10, 2026);
        assertEquals(EsCommunicationBundle.Status.PUBLISHED, updated.getStatus());
        assertEquals(30L, updated.getBundleId());
        assertEquals(java.time.LocalDateTime.of(2026, 9, 1, 12, 0), updated.getPublishedAt());
        assertEquals(10, updated.getCommunicationMonth());
        assertThrows(IllegalArgumentException.class, () -> fixture.service().saveOrientationSettings(
                fixture.steward, 10L, EsCommunicationBundlePurpose.Audience.PUBLIC, null, null));
        fixture.service().selectOrientationResource(fixture.steward, 10L, 40L, 50L);
        fixture.service().removeOrientationResource(fixture.steward, 10L, 60L);
        assertEquals(50L, fixture.placements.selectedResourceId);
        assertEquals(60L, fixture.placements.removedPlacementId);
        assertThrows(IllegalStateException.class,
                () -> fixture.service().publishOrientation(fixture.steward, 10L));

        fixture.access.canEdit = false;
        assertThrows(SecurityException.class, () -> fixture.service().saveOrientationSettings(
                fixture.viewer, 10L, EsCommunicationBundlePurpose.Audience.PUBLIC, 10, 2026));
        assertThrows(SecurityException.class,
                () -> fixture.service().selectOrientationResource(fixture.viewer, 10L, 40L, 50L));
        fixture.access.canEdit = true;
        fixture.purposes.purpose.setMode(EsCommunicationBundlePurpose.Mode.SNAPSHOT);
        assertThrows(IllegalStateException.class, () -> fixture.service().saveOrientationSettings(
                fixture.steward, 10L, EsCommunicationBundlePurpose.Audience.PUBLIC, 10, 2026));
        assertThrows(IllegalStateException.class,
                () -> fixture.service().removeOrientationResource(fixture.steward, 10L, 60L));
    }

    @Test
    void structuredListValuesUseJsonAndMustBelongToTheTopicAndTemplate() {
        Fixture fixture = new Fixture();
        fixture.bundles.bundle = Fixture.orientationBundle(30L, EsCommunicationBundle.Status.DRAFT);
        var component = new EsCommunicationBundleTemplateComponent();
        component.setComponentId(40L);
        component.setTemplateId(5L);
        component.setKind(EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST);
        fixture.components.component = component;
        var value = fixture.service().setStructuredListComponentValue(fixture.steward, 10L, 30L, 40L, "First\n\nSecond");
        assertNull(value.getContentText());
        assertEquals(List.of("First", "Second"), CommunicationBundleStructuredList.items(value.getContentJson()));
        assertThrows(IllegalArgumentException.class, () -> fixture.service()
                .setStructuredListComponentValue(fixture.steward, 11L, 30L, 40L, "Wrong Topic"));
        assertThrows(IllegalArgumentException.class, () -> fixture.service()
                .setTextComponentValue(fixture.steward, 30L, 40L, "Wrong kind"));
        fixture.access.canEdit = false;
        assertThrows(SecurityException.class, () -> fixture.service()
                .setStructuredListComponentValue(fixture.viewer, 10L, 30L, 40L, "Not a steward"));
    }

    @Test
    void storesNarrativeInDraftAndPublishedLivingTextComponentsButNotSnapshotsOrRetiredBundles() {
        Fixture fixture = new Fixture();
        EsCommunicationBundle bundle = new EsCommunicationBundle();
        bundle.setBundleId(30L);
        bundle.setEsTopicId(10L);
        bundle.setPurposeId(4L);
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
        bundle.setPurposeId(4L);
        fixture.purposes.purpose = Fixture.orientationPurpose();
        bundle.setStatus(EsCommunicationBundle.Status.PUBLISHED);
        assertEquals("Updated while published", fixture.service()
                .setTextComponentValue(fixture.steward, 30L, 40L, "Updated while published").getContentText());
        component.setRequired(true);
        assertThrows(IllegalStateException.class,
                () -> fixture.service().setTextComponentValue(fixture.steward, 30L, 40L, ""));
        fixture.purposes.purpose.setMode(EsCommunicationBundlePurpose.Mode.SNAPSHOT);
        assertThrows(IllegalStateException.class,
                () -> fixture.service().setTextComponentValue(fixture.steward, 30L, 40L, "Snapshot"));
        fixture.purposes.purpose.setMode(EsCommunicationBundlePurpose.Mode.LIVING);
        bundle.setStatus(EsCommunicationBundle.Status.RETIRED);
        assertThrows(IllegalStateException.class,
                () -> fixture.service().setTextComponentValue(fixture.steward, 30L, 40L, "Retired"));
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
            purposes.purpose = orientationPurpose();
        }

        private CommunicationBundleService service() {
            return new CommunicationBundleService(topics, spaces, resources, purposes, bundles,
                    componentValues, templates, components, placements, files, access);
        }

            private static EsCommunicationBundlePurpose orientationPurpose() {
                EsCommunicationBundlePurpose purpose = new EsCommunicationBundlePurpose();
                purpose.setPurposeId(4L);
                purpose.setPurposeKey(CommunicationBundleService.TOPIC_ORIENTATION_KEY);
                purpose.setMode(EsCommunicationBundlePurpose.Mode.LIVING);
                purpose.setActiveTemplateId(5L);
                purpose.setInstancePolicy(EsCommunicationBundlePurpose.InstancePolicy.SINGLE);
                purpose.setDefaultAudience(EsCommunicationBundlePurpose.Audience.STEWARDS);
                return purpose;
            }

            private static EsCommunicationBundle orientationBundle(Long id, EsCommunicationBundle.Status status) {
                EsCommunicationBundle bundle = new EsCommunicationBundle();
                bundle.setBundleId(id);
                bundle.setEsTopicId(10L);
                bundle.setPurposeId(4L);
                bundle.setTemplateId(5L);
                bundle.setStatus(status);
                bundle.setAudience(EsCommunicationBundlePurpose.Audience.PUBLIC);
                return bundle;
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
        @Override public EsTopicResource updateMetadataWithAudit(EsTopicResource value, Long userId) {
            resource = value;
            return value;
        }
        @Override public Optional<EsTopicResource> findByStoredFileId(Long id) { return Optional.empty(); }
        @Override public Optional<EsTopicResource> findById(Long id) { return Optional.ofNullable(resource); }
    }

    private static final class Purposes extends EsCommunicationBundlePurposeDao {
        private EsCommunicationBundlePurpose purpose;
        @Override public Optional<EsCommunicationBundlePurpose> findById(Long id) {
            return purpose != null && purpose.getPurposeId().equals(id) ? Optional.of(purpose) : Optional.empty();
        }
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
            EsCommunicationBundle result = existing != null ? existing : bundle;
            return result != null && result.getStatus() != EsCommunicationBundle.Status.RETIRED
                    ? Optional.of(result) : Optional.empty();
        }
        @Override public Optional<EsCommunicationBundle> findById(Long id) {
            return Optional.ofNullable(bundle != null ? bundle : saved);
        }
        @Override public EsCommunicationBundle save(EsCommunicationBundle value) { saved = value; return value; }
        @Override public EsCommunicationBundle saveDraftWithAudit(EsCommunicationBundle value, Long userId) {
            value.setBundleId(31L);
            saved = value;
            bundle = value;
            return value;
        }
        @Override public List<org.airahub.interophub.model.EsCommunicationBundleAudit> findAuditEntries(Long id) {
            return List.of();
        }
        @Override public EsCommunicationBundle updateEditableSettings(Long id,
                EsCommunicationBundlePurpose.Audience audience, Integer month, Integer year, Long userId) {
            bundle.setAudience(audience);
            bundle.setCommunicationMonth(month);
            bundle.setCommunicationYear(year);
            return bundle;
        }
        @Override public EsCommunicationBundle publishDraft(Long id, Long userId) {
            bundle.setStatus(EsCommunicationBundle.Status.PUBLISHED);
            return bundle;
        }
        @Override public EsCommunicationBundle retirePublished(Long id, Long userId) {
            bundle.setStatus(EsCommunicationBundle.Status.RETIRED);
            bundle.setSingleInstanceGuard(null);
            return bundle;
        }
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
        @Override public EsCommunicationBundleComponentValue saveEditableValue(
                EsCommunicationBundleComponentValue value, String semanticKey) {
            saved = value;
            return value;
        }
        @Override public List<EsCommunicationBundleComponentValue> findByBundleId(Long bundleId) {
            return saved == null ? List.of() : List.of(saved);
        }
        private EsCommunicationBundleComponentValue saved;
    }

    private static final class Components extends EsCommunicationBundleTemplateComponentDao {
        private EsCommunicationBundleTemplateComponent component;
        private List<EsCommunicationBundleTemplateComponent> byTemplate = List.of();
        @Override public Optional<EsCommunicationBundleTemplateComponent> findById(Long id) {
            return component != null && component.getComponentId().equals(id)
                    ? Optional.of(component) : Optional.empty();
        }
        @Override public List<EsCommunicationBundleTemplateComponent> findByTemplateId(Long templateId) {
            return byTemplate;
        }
    }

    private static final class Placements extends EsCommunicationBundleResourcePlacementDao {
        private List<EsCommunicationBundleResourcePlacement> existing = List.of();
        private List<EsCommunicationBundleResourcePlacement> ordered = List.of();
        @Override public List<EsCommunicationBundleResourcePlacement> findByBundleAndComponent(
                Long bundleId, Long componentId) { return existing; }
        @Override public List<EsCommunicationBundleResourcePlacement> findByBundleOrdered(Long bundleId) {
            return ordered;
        }
        @Override public EsCommunicationBundleResourcePlacement save(
                EsCommunicationBundleResourcePlacement value) { return value; }
        @Override public EsCommunicationBundleResourcePlacement saveEditablePlacementWithAudit(
                EsCommunicationBundleResourcePlacement value, Long userId) { return value; }
        @Override public void selectResource(Long bundleId, Long componentId, Long resourceId, Long userId) {
            selectedResourceId = resourceId;
        }
        @Override public void removeResource(Long bundleId, Long placementId, Long userId) {
            removedPlacementId = placementId;
        }
        private Long selectedResourceId;
        private Long removedPlacementId;
    }

    @Test
    void packetWritesCannotBypassTopicSpecificAuthoringAndPreservedFilesCannotBecomeCurrentResources() {
        Fixture fixture = new Fixture();
        fixture.bundles.bundle = Fixture.orientationBundle(30L, EsCommunicationBundle.Status.DRAFT);
        fixture.purposes.purpose.setPurposeKey(StarterPacketService.PURPOSE_KEY);
        assertThrows(SecurityException.class, () -> fixture.service().setTextComponentValue(fixture.steward, 30L, 40L, "Bypass"));
        assertThrows(SecurityException.class, () -> fixture.service().setStructuredListComponentValue(fixture.steward, 10L, 30L, 40L, "Bypass"));
        assertThrows(SecurityException.class, () -> fixture.service().addResourcePlacement(fixture.steward, 30L, 40L, 50L, 0, null));
        var file = new StoredFile();
        file.setStoredFileId(21L);
        file.setContentType("application/pdf");
        fixture.files.file = file;
        fixture.files.preserved = true;
        assertThrows(IllegalArgumentException.class, () -> fixture.service().registerStoredFileResource(
                fixture.steward, 10L, 21L, "Cannot edit snapshot bytes", null, null));
    }

    private static final class Files extends StoredFileDao {
        private StoredFile file;
        private boolean preserved;
        @Override public Optional<StoredFile> findById(Long id) { return Optional.ofNullable(file); }
        @Override public boolean isPreserved(Long id) { return preserved; }
    }
}
