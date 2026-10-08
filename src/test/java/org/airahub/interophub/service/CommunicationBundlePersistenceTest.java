package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.airahub.interophub.config.ArtifactStorageConfig;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsCommunicationBundleComponentValueDao;
import org.airahub.interophub.dao.EsCommunicationBundleDao;
import org.airahub.interophub.dao.EsCommunicationBundlePurposeDao;
import org.airahub.interophub.dao.EsCommunicationBundleResourcePlacementDao;
import org.airahub.interophub.dao.EsCommunicationBundleTemplateComponentDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicResourceDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsCommunicationBundleTemplateDao;
import org.airahub.interophub.dao.StoredFileDao;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsCommunicationBundlePurpose;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

@EnabledIfSystemProperty(named = "interophub.localBundleIntegration", matches = "true")
class CommunicationBundlePersistenceTest {
    private Path fileRoot;

    @BeforeEach
    void createIsolatedStorage() throws Exception {
        fileRoot = java.nio.file.Files.createTempDirectory(Path.of("target").toAbsolutePath(), "bundle-test-files-");
    }

    @AfterEach
    void removeIsolatedStorage() throws Exception {
        try (var files = java.nio.file.Files.walk(fileRoot)) {
            for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                java.nio.file.Files.delete(path);
            }
        }
    }

    @Test
    void validatesLocalSchemaAndPersistsAnOrientationWithoutLeavingTestContent() throws Exception {
        String url = System.getenv("INTEROPHUB_DB_URL");
        assertTrue(url == null || url.startsWith("jdbc:mysql://localhost:")
                || url.startsWith("jdbc:mysql://127.0.0.1:"), "Run only against the local database.");
        User steward;
        Long spaceId;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            steward = session.createQuery("from User where emailNormalized = :email", User.class)
                    .setParameter("email", "nbunker@immregistries.org").getSingleResult();
            spaceId = session.createQuery(
                    "select esTopicSpaceId from EsTopicSpace where visibility = 'PUBLIC' order by esTopicSpaceId",
                    Long.class).setMaxResults(1).getSingleResult();
        }

        EsTopic topic = new EsTopic();
        topic.setTopicCode("bundle-test-" + UUID.randomUUID());
        topic.setTopicName("Temporary bundle persistence verification");
        topic.setEsTopicSpaceId(spaceId);
        topic.setCreatedByUserId(steward.getUserId());
        topic = new EsTopicDao().save(topic);
        Long topicId = topic.getEsTopicId();
        try {
            var storage = new StoredFileService(new ArtifactStorageConfig(fileRoot.toString(), null, null, null),
                    () -> true, new StoredFileDao());
            CommunicationBundleService service = new CommunicationBundleService(new EsTopicDao(),
                    new EsTopicSpaceDao(), new EsTopicResourceDao(), new EsCommunicationBundlePurposeDao(),
                    new EsCommunicationBundleDao(), new EsCommunicationBundleComponentValueDao(),
                    new EsCommunicationBundleTemplateDao(), new EsCommunicationBundleTemplateComponentDao(),
                    new EsCommunicationBundleResourcePlacementDao(), new StoredFileDao(),
                    new TopicSpaceAccessService(), storage);
            var resource = service.registerExternalLinkResource(steward, topicId,
                    "https://www.immregistries.org/", "Persistence test resource", null, null);
            assertEquals(resource.getTitle(),
                    new EsTopicResourceDao().findById(resource.getTopicResourceId()).orElseThrow().getTitle());
            var bundle = service.createTopicOrientationDraft(steward, topicId);
            assertEquals(EsCommunicationBundle.Status.DRAFT,
                    new EsCommunicationBundleDao().findById(bundle.getBundleId()).orElseThrow().getStatus());
            assertEquals(EsCommunicationBundlePurpose.Audience.PUBLIC, bundle.getAudience());
            assertEquals(null, service.saveOrientationSettings(steward, topicId,
                    EsCommunicationBundlePurpose.Audience.PUBLIC, null, null).getCommunicationMonth());
            var components = new EsCommunicationBundleTemplateComponentDao()
                    .findByTemplateId(bundle.getTemplateId());
            assertEquals(7, components.size());
            var introduction = components.stream().filter(c -> "introduction".equals(c.getSemanticKey()))
                    .findFirst().orElseThrow();
            var sources = components.stream().filter(c -> "additional_resources".equals(c.getSemanticKey()))
                    .findFirst().orElseThrow();
            service.setTextComponentValue(steward, bundle.getBundleId(), introduction.getComponentId(),
                    "A persisted Orientation introduction.");
            assertEquals("A persisted Orientation introduction.",
                    new EsCommunicationBundleComponentValueDao()
                            .findByBundleAndComponent(bundle.getBundleId(), introduction.getComponentId())
                            .orElseThrow().getContentText());
            service.setTextComponentValue(steward, bundle.getBundleId(), introduction.getComponentId(), "");
            assertEquals(null, new EsCommunicationBundleComponentValueDao()
                    .findByBundleAndComponent(bundle.getBundleId(), introduction.getComponentId())
                    .orElseThrow().getContentText());
            service.addResourcePlacement(steward, bundle.getBundleId(), sources.getComponentId(),
                    resource.getTopicResourceId(), 0, "Use this first.");
            assertEquals(1, new EsCommunicationBundleResourcePlacementDao()
                    .findByBundleOrdered(bundle.getBundleId()).size());
            assertEquals(1, service.listResourcePlacementsForViewer(steward, bundle.getBundleId()).size());
            assertTrue(service.findTopicOrientationForViewer(null, topicId).isEmpty());
            assertEquals(0, service.listResourcePlacementsForViewer(null, bundle.getBundleId()).size());
            assertThrows(IllegalStateException.class, () -> service.createTopicOrientationDraft(steward, topicId));
            assertThrows(SecurityException.class, () -> service.registerExternalLinkResource(
                    null, topicId, "https://www.immregistries.org/", "Unauthorized", null, null));
            assertTrue(new EsCommunicationBundlePurposeDao()
                    .findByKey(CommunicationBundleService.TOPIC_ORIENTATION_KEY).isPresent());

            var uploaded = service.uploadResource(steward, topicId,
                    new ByteArrayInputStream("%PDF-1.4\n%Test resource\n".getBytes(StandardCharsets.US_ASCII)),
                    "bundle-test.pdf", "application/pdf", "A test PDF", "Original description", "Test author");
            assertEquals(org.airahub.interophub.model.EsTopicResource.ResourceType.PDF, uploaded.getResourceType());
            assertTrue(uploaded.getStoredFileId() != null && uploaded.getTopicResourceId() != null);
            var file = new StoredFileDao().findById(uploaded.getStoredFileId()).orElseThrow();
            assertTrue(java.nio.file.Files.exists(fileRoot.resolve(file.getStorageKey())));
            String originalPublicId = file.getPublicId();
            String originalStorageKey = file.getStorageKey();
            service.replaceResourceFile(steward, topicId, uploaded.getTopicResourceId(),
                    new ByteArrayInputStream("%PDF-1.7\n%Replacement resource\n".getBytes(StandardCharsets.US_ASCII)),
                    "bundle-test-replacement.pdf", "application/pdf");
            var replacedResource = new EsTopicResourceDao().findById(uploaded.getTopicResourceId()).orElseThrow();
            var replacedFile = new StoredFileDao().findById(uploaded.getStoredFileId()).orElseThrow();
            assertEquals(uploaded.getTopicResourceId(), replacedResource.getTopicResourceId());
            assertEquals(uploaded.getStoredFileId(), replacedResource.getStoredFileId());
            assertEquals(originalPublicId, replacedFile.getPublicId());
            assertNotEquals(originalStorageKey, replacedFile.getStorageKey());
            assertFalse(java.nio.file.Files.exists(fileRoot.resolve(originalStorageKey)));
            assertTrue(java.nio.file.Files.exists(fileRoot.resolve(replacedFile.getStorageKey())));
            service.updateResourceMetadata(steward, topicId, uploaded.getTopicResourceId(),
                    "Updated PDF", "Updated description", "Updated author", null);
            assertEquals("Updated PDF", new EsTopicResourceDao()
                    .findById(uploaded.getTopicResourceId()).orElseThrow().getTitle());
            var onePager = components.stream().filter(c -> "one_pager".equals(c.getSemanticKey()))
                    .findFirst().orElseThrow();
            service.selectOrientationResource(steward, topicId, onePager.getComponentId(), uploaded.getTopicResourceId());
            service.selectOrientationResource(steward, topicId, onePager.getComponentId(), resource.getTopicResourceId());
            var selected = new EsCommunicationBundleResourcePlacementDao()
                    .findByBundleAndComponent(bundle.getBundleId(), onePager.getComponentId());
            assertEquals(1, selected.size());
            assertEquals(resource.getTopicResourceId(), selected.get(0).getTopicResourceId());
            service.updateOrientationResourceContext(steward, topicId, selected.get(0).getPlacementId(), "Keep this context.");
            service.selectOrientationResource(steward, topicId, onePager.getComponentId(), resource.getTopicResourceId());
            assertEquals("Keep this context.", new EsCommunicationBundleResourcePlacementDao()
                    .findByBundleAndComponent(bundle.getBundleId(), onePager.getComponentId()).get(0).getContextNote());
            service.selectOrientationResource(steward, topicId, sources.getComponentId(), uploaded.getTopicResourceId());
            assertThrows(IllegalArgumentException.class, () -> service.selectOrientationResource(
                    steward, topicId, sources.getComponentId(), uploaded.getTopicResourceId()));
            assertThrows(IllegalArgumentException.class, () -> service.selectOrientationResource(
                    steward, topicId, introduction.getComponentId(), uploaded.getTopicResourceId()));
            var details = service.findOrientationResourcesForViewer(steward, topicId).orElseThrow();
            assertEquals(3, details.placements().size());
            assertEquals(2, details.resources().size());
            assertEquals(2, service.listResourceDetailsForSteward(steward, topicId).size());
            assertTrue(service.findOrientationResourcesForViewer(null, topicId).isEmpty());
            var collection = new EsCommunicationBundleResourcePlacementDao()
                    .findByBundleAndComponent(bundle.getBundleId(), sources.getComponentId());
            service.moveOrientationResource(steward, topicId, collection.get(0).getPlacementId(), false);
            var reordered = new EsCommunicationBundleResourcePlacementDao()
                    .findByBundleAndComponent(bundle.getBundleId(), sources.getComponentId());
            assertEquals(collection.get(1).getTopicResourceId(), reordered.get(0).getTopicResourceId());
            assertThrows(IllegalArgumentException.class, () -> service.moveOrientationResource(
                    steward, topicId, reordered.get(0).getPlacementId(), true));
            service.updateOrientationResourceContext(steward, topicId, reordered.get(0).getPlacementId(), "Read this first.");
            assertEquals("Read this first.", new EsCommunicationBundleResourcePlacementDao()
                    .findByBundleAndComponent(bundle.getBundleId(), sources.getComponentId()).get(0).getContextNote());
            assertThrows(IllegalArgumentException.class, () -> service.removeOrientationResource(
                    steward, topicId, Long.MAX_VALUE));
            service.removeOrientationResource(steward, topicId, selected.get(0).getPlacementId());
            assertEquals(2, service.listResourcePlacementsForViewer(steward, bundle.getBundleId()).size());
            assertTrue(new EsTopicResourceDao().findById(resource.getTopicResourceId()).isPresent());
            assertThrows(IllegalArgumentException.class, () -> service.updateResourceMetadata(
                    steward, 1L, uploaded.getTopicResourceId(), "Wrong Topic", null, null, null));

            service.setTextComponentValue(steward, bundle.getBundleId(), introduction.getComponentId(),
                    "Introduction before publication.");
            service.saveOrientationSettings(steward, topicId,
                    EsCommunicationBundlePurpose.Audience.PUBLIC, 9, 2026);
            service.publishOrientation(steward, topicId);
            var bundleDao = new EsCommunicationBundleDao();
            var publicationTime = bundleDao.findById(bundle.getBundleId()).orElseThrow().getPublishedAt();
            int auditCount = bundleDao.findAuditEntries(bundle.getBundleId()).size();
            service.setTextComponentValue(steward, bundle.getBundleId(), introduction.getComponentId(),
                    "Live updated introduction.");
            assertEquals(9, bundleDao.findById(bundle.getBundleId()).orElseThrow().getCommunicationMonth());
            service.saveOrientationSettings(steward, topicId,
                    EsCommunicationBundlePurpose.Audience.PUBLIC, 10, 2026);
            service.selectOrientationResource(steward, topicId, onePager.getComponentId(), uploaded.getTopicResourceId());
            var livePlacement = new EsCommunicationBundleResourcePlacementDao()
                    .findByBundleAndComponent(bundle.getBundleId(), onePager.getComponentId()).get(0);
            service.removeOrientationResource(steward, topicId, livePlacement.getPlacementId());
            service.addResourcePlacement(steward, bundle.getBundleId(), onePager.getComponentId(),
                    resource.getTopicResourceId(), 0, null);
            var live = service.findOrientationResourcesForViewer(null, topicId).orElseThrow();
            assertEquals(bundle.getBundleId(), live.bundle().getBundleId());
            assertEquals(EsCommunicationBundle.Status.PUBLISHED, live.bundle().getStatus());
            assertEquals(publicationTime, live.bundle().getPublishedAt());
            assertEquals(10, live.bundle().getCommunicationMonth());
            assertTrue(live.componentValues().stream()
                    .anyMatch(value -> "Live updated introduction.".equals(value.getContentText())));
            assertEquals(resource.getTopicResourceId(), new EsCommunicationBundleResourcePlacementDao()
                    .findByBundleAndComponent(bundle.getBundleId(), onePager.getComponentId()).get(0).getTopicResourceId());
            assertEquals(auditCount + 5, bundleDao.findAuditEntries(bundle.getBundleId()).size());
            assertTrue(bundleDao.findAuditEntries(bundle.getBundleId()).stream().limit(5)
                    .allMatch(entry -> steward.getUserId().equals(entry.getChangedByUserId())));
            assertThrows(IllegalArgumentException.class, () -> service.saveOrientationSettings(steward, topicId,
                    EsCommunicationBundlePurpose.Audience.PUBLIC, null, null));
            assertThrows(IllegalStateException.class, () -> bundleDao.updateEditableSettings(bundle.getBundleId(),
                    EsCommunicationBundlePurpose.Audience.PUBLIC, null, null, steward.getUserId()));
            assertEquals(10, bundleDao.findById(bundle.getBundleId()).orElseThrow().getCommunicationMonth());
            assertEquals(auditCount + 5, bundleDao.findAuditEntries(bundle.getBundleId()).size());
            service.retireOrientation(steward, topicId);
            assertThrows(IllegalStateException.class, () -> service.setTextComponentValue(
                    steward, bundle.getBundleId(), introduction.getComponentId(), "Cannot edit retired"));
        } finally {
            try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
                var transaction = session.beginTransaction();
                session.createNativeMutationQuery(
                        "DELETE FROM es_communication_bundle_audit WHERE bundle_id IN"
                                + " (SELECT bundle_id FROM es_communication_bundle WHERE es_topic_id = :topicId)"
                                + " OR topic_resource_id IN"
                                + " (SELECT topic_resource_id FROM es_topic_resource WHERE es_topic_id = :topicId)")
                        .setParameter("topicId", topicId).executeUpdate();
                session.createNativeMutationQuery(
                        "DELETE v FROM es_communication_bundle_component_value v"
                                + " JOIN es_communication_bundle b ON b.bundle_id = v.bundle_id"
                                + " WHERE b.es_topic_id = :topicId")
                        .setParameter("topicId", topicId).executeUpdate();
                session.createNativeMutationQuery(
                        "DELETE FROM es_communication_bundle_resource_placement WHERE es_topic_id = :topicId")
                        .setParameter("topicId", topicId).executeUpdate();
                session.createNativeMutationQuery(
                        "DELETE FROM es_communication_bundle WHERE es_topic_id = :topicId")
                        .setParameter("topicId", topicId).executeUpdate();
                var fileIds = session.createQuery(
                        "select storedFileId from EsTopicResource where esTopicId = :topicId and storedFileId is not null",
                        Long.class).setParameter("topicId", topicId).getResultList();
                session.createNativeMutationQuery(
                        "DELETE FROM es_topic_resource WHERE es_topic_id = :topicId")
                        .setParameter("topicId", topicId).executeUpdate();
                for (Long fileId : fileIds) {
                    session.remove(session.find(org.airahub.interophub.model.StoredFile.class, fileId));
                }
                session.createNativeMutationQuery("DELETE FROM es_topic WHERE es_topic_id = :topicId")
                        .setParameter("topicId", topicId).executeUpdate();
                transaction.commit();
            }
        }
    }
}
