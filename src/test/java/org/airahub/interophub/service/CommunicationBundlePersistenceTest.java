package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsCommunicationBundleComponentValueDao;
import org.airahub.interophub.dao.EsCommunicationBundleDao;
import org.airahub.interophub.dao.EsCommunicationBundlePurposeDao;
import org.airahub.interophub.dao.EsCommunicationBundleResourcePlacementDao;
import org.airahub.interophub.dao.EsCommunicationBundleTemplateComponentDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicResourceDao;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsCommunicationBundlePurpose;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "interophub.localBundleIntegration", matches = "true")
class CommunicationBundlePersistenceTest {

    @Test
    void validatesLocalSchemaAndPersistsAnOrientationWithoutLeavingTestContent() {
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
            CommunicationBundleService service = new CommunicationBundleService();
            var resource = service.registerExternalLinkResource(steward, topicId,
                    "https://www.immregistries.org/", "Persistence test resource", null, null);
            assertEquals(resource.getTitle(),
                    new EsTopicResourceDao().findById(resource.getTopicResourceId()).orElseThrow().getTitle());
            var bundle = service.createTopicOrientationDraft(steward, topicId);
            assertEquals(EsCommunicationBundle.Status.DRAFT,
                    new EsCommunicationBundleDao().findById(bundle.getBundleId()).orElseThrow().getStatus());
            assertEquals(EsCommunicationBundlePurpose.Audience.PUBLIC, bundle.getAudience());
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
        } finally {
            try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
                var transaction = session.beginTransaction();
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
                session.createNativeMutationQuery(
                        "DELETE FROM es_topic_resource WHERE es_topic_id = :topicId")
                        .setParameter("topicId", topicId).executeUpdate();
                session.createNativeMutationQuery("DELETE FROM es_topic WHERE es_topic_id = :topicId")
                        .setParameter("topicId", topicId).executeUpdate();
                transaction.commit();
            }
        }
    }
}
