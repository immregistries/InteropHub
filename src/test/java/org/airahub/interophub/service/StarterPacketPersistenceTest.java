package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import org.airahub.interophub.config.*;
import org.airahub.interophub.dao.*;
import org.airahub.interophub.model.*;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "interophub.localBundleIntegration", matches = "true")
class StarterPacketPersistenceTest {
    @Test
    void publicationPreservesResourcesAndMetadataWhileCopyAndRetirementRetainTheOriginal() throws Exception {
        String url = System.getenv("INTEROPHUB_DB_URL");
        assertTrue(url == null || url.startsWith("jdbc:mysql://localhost:") || url.startsWith("jdbc:mysql://127.0.0.1:"),
                "Run only against the local database.");
        User admin;
        Long spaceId;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            admin = session.createQuery("from User where emailNormalized = :email", User.class)
                    .setParameter("email", "nbunker@immregistries.org").getSingleResult();
            spaceId = session.createQuery("select esTopicSpaceId from EsTopicSpace where visibility = 'PUBLIC'",
                    Long.class).setMaxResults(1).getSingleResult();
        }
        Path root = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "starter-packet-test-");
        var storage = new StoredFileService(new ArtifactStorageConfig(root.toString(), null, null, null),
                () -> true, new StoredFileDao());
        var service = new StarterPacketService(new EsTopicDao(), new EsSubscriptionDao(),
                new TopicSpaceAccessService(), storage);
        var topic = new EsTopic();
        topic.setEsTopicSpaceId(spaceId);
        topic.setTopicCode("starter-test-" + UUID.randomUUID());
        topic.setTopicName("Temporary Starter Packet persistence verification");
        topic.setCreatedByUserId(admin.getUserId());
        topic = new EsTopicDao().save(topic);
        Long topicId = topic.getEsTopicId();
        Set<Long> fileIds = new HashSet<>();
        try {
            var packet = service.create(admin, topicId, null);
            Long packetId = packet.getBundleId();
            OrientationResources data = service.get(admin, packetId);
            var title = component(data, "title");
            assertThrows(IllegalStateException.class, () -> service.publish(admin, topicId, packetId, true));
            service.saveSettings(admin, topicId, packetId, 10, 2026);
            assertThrows(IllegalStateException.class, () -> service.publish(admin, topicId, packetId, true));
            service.saveValue(admin, topicId, packetId, title.getComponentId(), "Starting a project");
            service.saveValue(admin, topicId, packetId, component(data, "teaser_summary").getComponentId(), "A distinct short summary");
            service.saveValue(admin, topicId, packetId, component(data, "explanation").getComponentId(), "Full project context");
            service.saveValue(admin, topicId, packetId, component(data, "starting_points").getComponentId(), "First\nSecond");
            service.saveSettings(admin, topicId, packetId, 10, 2026);
            var resource = service.upload(admin, topicId, new ByteArrayInputStream(pdf("Original")),
                    "original.pdf", "application/pdf", "Original document");
            fileIds.add(resource.getStoredFileId());
            Long supportingId = component(data, "supporting_resources").getComponentId();
            service.selectResource(admin, topicId, packetId, supportingId, resource.getTopicResourceId());
            byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aQ1sAAAAASUVORK5CYII=");
            var image = service.upload(admin, topicId, new ByteArrayInputStream(png),
                    "teaser.png", "image/png", "Teaser illustration");
            fileIds.add(image.getStoredFileId());
            service.selectResource(admin, topicId, packetId, component(data, "teaser_image").getComponentId(), image.getTopicResourceId());
            var link = service.addLink(admin, topicId, "https://example.org/original", "Original link");
            service.selectResource(admin, topicId, packetId, supportingId, link.getTopicResourceId());
            assertThrows(IllegalArgumentException.class, () -> service.publish(admin, topicId, packetId, false));
            assertEquals(EsCommunicationBundle.Status.DRAFT, service.get(admin, packetId).bundle().getStatus());
            var failingStorage = new StoredFileService(new ArtifactStorageConfig(root.toString(), null, null, null),
                    () -> true, new StoredFileDao()) {
                @Override public StoredFile preserve(StoredFile source, Long userId,
                        java.util.function.Function<StoredFile, StoredFile> register) throws java.io.IOException {
                    throw new java.io.IOException("Simulated preservation failure");
                }
            };
            var failingService = new StarterPacketService(new EsTopicDao(), new EsSubscriptionDao(),
                    new TopicSpaceAccessService(), failingStorage);
            assertThrows(java.io.IOException.class, () -> failingService.publish(admin, topicId, packetId, true));
            assertEquals(EsCommunicationBundle.Status.DRAFT, service.get(admin, packetId).bundle().getStatus());
            assertTrue(service.get(admin, packetId).placements().stream().allMatch(p -> p.getResourceVersionId() == null));
            service.publish(admin, topicId, packetId, true);
            data = service.get(admin, packetId);
            assertEquals(EsCommunicationBundle.Status.PUBLISHED, data.bundle().getStatus());
            assertTrue(data.placements().stream().allMatch(p -> p.getResourceVersionId() != null));
            var frozenFile = data.resources().stream().filter(r -> resource.getTopicResourceId().equals(r.resource().getTopicResourceId()))
                    .findFirst().orElseThrow().file();
            data.resources().stream().filter(r -> r.file() != null).forEach(r -> fileIds.add(r.file().getStoredFileId()));
            var frozenImage = data.resources().stream().filter(r -> image.getTopicResourceId().equals(r.resource().getTopicResourceId()))
                    .findFirst().orElseThrow().file();
            assertNotEquals(image.getStoredFileId(), frozenImage.getStoredFileId());
            var originalImage = new StoredFileDao().findById(image.getStoredFileId()).orElseThrow();
            storage.upload(originalImage, new ByteArrayInputStream(png), "new-teaser.png", "image/png", admin.getUserId(),
                    candidate -> new EsTopicResourceDao().replaceUpload(candidate, image, admin.getUserId()));
            assertFalse(Files.exists(root.resolve(originalImage.getStorageKey())));
            assertArrayEquals(png, Files.readAllBytes(root.resolve(frozenImage.getStorageKey())));
            assertNotEquals(resource.getStoredFileId(), frozenFile.getStoredFileId());
            assertArrayEquals(pdf("Original"), Files.readAllBytes(root.resolve(frozenFile.getStorageKey())));
            assertThrows(IllegalStateException.class, () -> storage.upload(frozenFile,
                    new ByteArrayInputStream(pdf("Forbidden")), "forbidden.pdf", "application/pdf", admin.getUserId()));
            assertThrows(IllegalStateException.class, () -> service.saveValue(admin, topicId, packetId, title.getComponentId(), "Forbidden"));
            assertThrows(IllegalStateException.class, () -> service.selectResource(admin, topicId, packetId, supportingId, link.getTopicResourceId()));
            assertThrows(IllegalStateException.class, () -> service.saveSettings(admin, topicId, packetId, 11, 2026));
            assertThrows(IllegalStateException.class, () -> service.publish(admin, topicId, packetId, true));
            assertThrows(IllegalArgumentException.class, () -> service.saveValue(admin, Long.MAX_VALUE, packetId, title.getComponentId(), "Wrong Topic"));

            var oldFile = new StoredFileDao().findById(resource.getStoredFileId()).orElseThrow();
            storage.upload(oldFile, new ByteArrayInputStream(pdf("Replacement")), "replacement.pdf", "application/pdf",
                    admin.getUserId(), candidate -> new EsTopicResourceDao().replaceUpload(candidate, resource, admin.getUserId()));
            resource.setTitle("Updated document title");
            new EsTopicResourceDao().updateMetadataWithAudit(resource, admin.getUserId());
            link.setTitle("Changed link");
            link.setExternalUrl("https://example.org/changed");
            new EsTopicResourceDao().updateMetadataWithAudit(link, admin.getUserId());
            data = service.get(admin, packetId);
            assertTrue(data.resources().stream().anyMatch(r -> "Original document".equals(r.resource().getTitle())));
            assertTrue(data.resources().stream().anyMatch(r -> "https://example.org/original".equals(r.resource().getExternalUrl())));
            assertArrayEquals(pdf("Original"), Files.readAllBytes(root.resolve(frozenFile.getStorageKey())));
            assertFalse(Files.exists(root.resolve(oldFile.getStorageKey())));
            var revised = service.create(admin, topicId, packetId);
            assertEquals(packet.getTemplateId(), revised.getTemplateId());
            assertTrue(service.get(admin, revised.getBundleId()).placements().stream().allMatch(p -> p.getResourceVersionId() != null));
            service.saveValue(admin, topicId, revised.getBundleId(), title.getComponentId(), "Revised project starting point");
            service.publish(admin, topicId, revised.getBundleId(), true);
            assertEquals(2, service.list(admin, topicId, true).size());
            assertEquals(revised.getBundleId(), service.list(admin, topicId, true).get(0).bundle().getBundleId());
            service.retire(admin, topicId, packetId);
            assertEquals(1, service.list(admin, topicId, true).size());
            assertEquals(EsCommunicationBundle.Status.RETIRED, service.get(admin, packetId).bundle().getStatus());
            assertArrayEquals(pdf("Original"), Files.readAllBytes(root.resolve(frozenFile.getStorageKey())));
            assertEquals("Starting a project", service.get(admin, packetId).componentValues().stream()
                    .filter(v -> v.getComponentId().equals(title.getComponentId())).findFirst().orElseThrow().getContentText());
        } finally {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                var tx = session.beginTransaction();
                for (String table : List.of("es_communication_bundle_audit", "es_communication_bundle_component_value")) {
                    session.createNativeMutationQuery("DELETE x FROM " + table
                            + " x JOIN es_communication_bundle b ON b.bundle_id=x.bundle_id WHERE b.es_topic_id=:id")
                            .setParameter("id", topicId).executeUpdate();
                }
                session.createMutationQuery("delete EsCommunicationBundleResourcePlacement where esTopicId=:id").setParameter("id", topicId).executeUpdate();
                session.createMutationQuery("delete EsCommunicationBundle where esTopicId=:id").setParameter("id", topicId).executeUpdate();
                session.createMutationQuery("delete EsTopicResourceVersion where esTopicId=:id").setParameter("id", topicId).executeUpdate();
                session.createNativeMutationQuery("DELETE a FROM es_communication_bundle_audit a JOIN es_topic_resource r"
                        + " ON r.topic_resource_id=a.topic_resource_id WHERE r.es_topic_id=:id").setParameter("id", topicId).executeUpdate();
                session.createMutationQuery("delete EsTopicResource where esTopicId=:id").setParameter("id", topicId).executeUpdate();
                for (Long id : fileIds) {
                    session.createMutationQuery("delete StoredFile where storedFileId=:id").setParameter("id", id).executeUpdate();
                }
                session.createMutationQuery("delete EsTopic where esTopicId=:id").setParameter("id", topicId).executeUpdate();
                tx.commit();
            } finally {
                try (var paths = Files.walk(root)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
                }
            }
        }
    }

    private static EsCommunicationBundleTemplateComponent component(OrientationResources data, String key) {
        return data.components().stream().filter(c -> key.equals(c.getSemanticKey())).findFirst().orElseThrow();
    }

    private static byte[] pdf(String text) {
        return ("%PDF-1.4\n%" + text + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
