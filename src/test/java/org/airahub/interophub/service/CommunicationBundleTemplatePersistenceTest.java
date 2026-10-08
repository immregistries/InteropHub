package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.*;
import org.airahub.interophub.model.*;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent.Kind;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent.Cardinality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "interophub.localBundleIntegration", matches = "true")
class CommunicationBundleTemplatePersistenceTest {
    @Test
    void draftActivationAndCopyPreserveExistingBundleTemplateAndReleasedComponents() {
        String url = System.getenv("INTEROPHUB_DB_URL");
        assertTrue(url == null || url.startsWith("jdbc:mysql://localhost:") || url.startsWith("jdbc:mysql://127.0.0.1:"));
        User admin;
        Long spaceId;
        try (var session = HibernateUtil.getSessionFactory().openSession()) {
            admin = session.createQuery("from User where emailNormalized = :email", User.class)
                    .setParameter("email", "nbunker@immregistries.org").getSingleResult();
            spaceId = session.createQuery("select esTopicSpaceId from EsTopicSpace where visibility = 'PUBLIC'",
                    Long.class).setMaxResults(1).getSingleResult();
        }
        var purpose = new EsCommunicationBundlePurpose();
        purpose.setPurposeKey("TEST_" + UUID.randomUUID());
        purpose.setDisplayName("Temporary template lifecycle test");
        purpose.setMode(EsCommunicationBundlePurpose.Mode.LIVING);
        purpose.setInstancePolicy(EsCommunicationBundlePurpose.InstancePolicy.SINGLE);
        purpose.setDefaultAudience(EsCommunicationBundlePurpose.Audience.PUBLIC);
        purpose.setActive(true);
        purpose = new EsCommunicationBundlePurposeDao().save(purpose);
        Long purposeId = purpose.getPurposeId();
        var topic = new EsTopic();
        topic.setTopicCode("template-test-" + UUID.randomUUID());
        topic.setTopicName("Temporary template lifecycle verification");
        topic.setEsTopicSpaceId(spaceId);
        topic.setCreatedByUserId(admin.getUserId());
        topic = new EsTopicDao().save(topic);
        Long topicId = topic.getEsTopicId();
        try {
            var service = new CommunicationBundleTemplateService();
            var first = service.createDraft(admin, purposeId, null);
            assertThrows(IllegalArgumentException.class, () -> service.activate(admin, first.getTemplateId()));
            service.saveComponent(admin, first.getTemplateId(), null, input("Original label", false));
            service.saveComponent(admin, first.getTemplateId(), null, new CommunicationBundleTemplateService.ComponentInput(
                    "starting_points", "Starting points", "One item per line.", Kind.STRUCTURED_LIST, true, Cardinality.REPEATING, 20));
            service.activate(admin, first.getTemplateId());
            var original = service.listComponents(admin, first.getTemplateId()).get(0);
            var bundle = new EsCommunicationBundle();
            bundle.setEsTopicId(topicId);
            bundle.setPurposeId(purposeId);
            bundle.setTemplateId(first.getTemplateId());
            bundle.setSingleInstanceGuard(1);
            bundle.setStatus(EsCommunicationBundle.Status.DRAFT);
            bundle.setAudience(EsCommunicationBundlePurpose.Audience.PUBLIC);
            bundle.setCommunicationMonth(1);
            bundle.setCommunicationYear(2026);
            bundle.setCreatedByUserId(admin.getUserId());
            bundle.setUpdatedByUserId(admin.getUserId());
            bundle = new EsCommunicationBundleDao().save(bundle);
            Long bundleId = bundle.getBundleId();
            assertThrows(IllegalStateException.class, () -> new EsCommunicationBundleDao().publishDraft(bundleId, admin.getUserId()));
            var list = service.listComponents(admin, first.getTemplateId()).get(1);
            new CommunicationBundleService().setStructuredListComponentValue(admin, topicId,
                    bundle.getBundleId(), list.getComponentId(), "First\nSecond");
            assertEquals(java.util.List.of("First", "Second"), CommunicationBundleStructuredList.items(
                    new EsCommunicationBundleComponentValueDao().findByBundleAndComponent(
                            bundle.getBundleId(), list.getComponentId()).orElseThrow().getContentJson()));
            new EsCommunicationBundleDao().publishDraft(bundleId, admin.getUserId());
            var emptyList = new EsCommunicationBundleComponentValueDao().findByBundleAndComponent(
                    bundleId, list.getComponentId()).orElseThrow();
            emptyList.setContentJson("[]");
            assertThrows(IllegalStateException.class, () -> new EsCommunicationBundleComponentValueDao()
                    .saveEditableValue(emptyList, "starting_points"));
            assertEquals(java.util.List.of("First", "Second"), CommunicationBundleStructuredList.items(
                    new EsCommunicationBundleComponentValueDao().findByBundleAndComponent(
                            bundleId, list.getComponentId()).orElseThrow().getContentJson()));
            EsCommunicationBundleTemplate nextVersion = service.createDraft(admin, purposeId, first.getTemplateId());
            var copied = service.listComponents(admin, nextVersion.getTemplateId()).get(0);
            assertNotEquals(original.getComponentId(), copied.getComponentId());
            assertThrows(IllegalArgumentException.class, () -> service.removeComponent(
                    admin, nextVersion.getTemplateId(), original.getComponentId()));
            service.saveComponent(admin, nextVersion.getTemplateId(), copied.getComponentId(), input("New label", true));
            service.activate(admin, nextVersion.getTemplateId());
            assertEquals(nextVersion.getTemplateId(), new EsCommunicationBundlePurposeDao().findById(purposeId)
                    .orElseThrow().getActiveTemplateId());
            assertEquals(EsCommunicationBundleTemplate.Status.RETIRED,
                    service.getTemplate(admin, first.getTemplateId()).getStatus());
            assertEquals(first.getTemplateId(), new EsCommunicationBundleDao().findById(bundle.getBundleId())
                    .orElseThrow().getTemplateId());
            assertEquals("Original label", service.listComponents(admin, first.getTemplateId()).get(0).getDisplayName());
            assertFalse(service.listComponents(admin, first.getTemplateId()).get(0).isRequired());
            assertThrows(IllegalStateException.class, () -> service.saveComponent(
                    admin, first.getTemplateId(), original.getComponentId(), input("Forbidden", false)));
            assertThrows(IllegalStateException.class, () -> service.removeComponent(admin, nextVersion.getTemplateId(), copied.getComponentId()));
            assertThrows(IllegalStateException.class, () -> service.activate(admin, nextVersion.getTemplateId()));
        } finally {
            try (var session = HibernateUtil.getSessionFactory().openSession()) {
                var tx = session.beginTransaction();
                session.createNativeMutationQuery("DELETE a FROM es_communication_bundle_audit a"
                        + " JOIN es_communication_bundle b ON b.bundle_id = a.bundle_id WHERE b.es_topic_id = :id")
                        .setParameter("id", topicId).executeUpdate();
                session.createNativeMutationQuery("DELETE v FROM es_communication_bundle_component_value v"
                        + " JOIN es_communication_bundle b ON b.bundle_id = v.bundle_id WHERE b.es_topic_id = :id")
                        .setParameter("id", topicId).executeUpdate();
                session.createMutationQuery("delete EsCommunicationBundle where esTopicId = :id").setParameter("id", topicId).executeUpdate();
                session.createMutationQuery("update EsCommunicationBundlePurpose set activeTemplateId = null where purposeId = :id")
                        .setParameter("id", purposeId).executeUpdate();
                session.createNativeMutationQuery("DELETE c FROM es_communication_bundle_template_component c"
                        + " JOIN es_communication_bundle_template t ON t.template_id = c.template_id WHERE t.purpose_id = :id")
                        .setParameter("id", purposeId).executeUpdate();
                session.createMutationQuery("delete EsCommunicationBundleTemplate where purposeId = :id")
                        .setParameter("id", purposeId).executeUpdate();
                session.createMutationQuery("delete EsCommunicationBundlePurpose where purposeId = :id")
                        .setParameter("id", purposeId).executeUpdate();
                session.createMutationQuery("delete EsTopic where esTopicId = :id").setParameter("id", topicId).executeUpdate();
                tx.commit();
            }
        }
    }

    private static CommunicationBundleTemplateService.ComponentInput input(String label, boolean required) {
        return new CommunicationBundleTemplateService.ComponentInput("introduction", label, "Explain this communication.",
                Kind.TEXT, required, Cardinality.SINGLE, 10);
    }
}
