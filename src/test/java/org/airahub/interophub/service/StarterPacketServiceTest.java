package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.dao.*;
import org.airahub.interophub.model.*;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.junit.jupiter.api.Test;

class StarterPacketServiceTest {
    @Test
    void accessIsTopicSpecificAndDoesNotFollowSpaceStewardshipOrOrdinaryMembership() {
        var topic = new EsTopic();
        topic.setEsTopicId(10L);
        var user = new User();
        user.setUserId(2L);
        user.setEmailNormalized("person@example.org");
        var sub = new EsSubscription();
        sub.setUserId(2L);
        sub.setEmailNormalized(user.getEmailNormalized());
        sub.setEsTopicId(10L);
        var subscriptions = new EsSubscriptionDao() {
            @Override public List<EsSubscription> findActiveByTopicId(Long id) {
                return id.equals(sub.getEsTopicId()) ? List.of(sub) : List.of();
            }
        };
        var access = new TopicSpaceAccessService() {
            @Override public boolean canViewTopic(User viewer, EsTopic value) { return value.getEsTopicId().equals(10L); }
            @Override public boolean canEditTopic(User viewer, EsTopic value) { return true; }
        };
        var topics = new EsTopicDao() {
            @Override public Optional<EsTopic> findById(Long id) { return Optional.of(topic); }
        };
        var service = new StarterPacketService(topics, subscriptions, access, new StoredFileService());
        assertFalse(service.canAccess(null, topic));
        sub.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
        assertFalse(service.canAccess(user, topic));
        sub.setStatus(EsSubscription.SubscriptionStatus.UNSUBSCRIBED);
        assertFalse(service.canAccess(user, topic));
        sub.setStatus(EsSubscription.SubscriptionStatus.CHAMPION);
        assertTrue(service.canAccess(user, topic));
        sub.setStatus(EsSubscription.SubscriptionStatus.SUPPORT);
        assertTrue(service.canAccess(user, topic));
        sub.setUserId(null);
        assertTrue(service.canAccess(user, topic));
        sub.setEmailNormalized("different@example.org");
        assertFalse(service.canAccess(user, topic));
        user.setIsAdmin(true);
        assertTrue(service.canAccess(user, topic));
        topic.setEsTopicId(11L);
        assertFalse(service.canAccess(user, topic));
        assertThrows(SecurityException.class, () -> service.get(null, 3L));
        assertThrows(SecurityException.class, () -> service.create(user, 11L, null));
        assertThrows(SecurityException.class, () -> service.saveValue(user, 11L, 3L, 4L, "denied"));
        assertThrows(SecurityException.class, () -> service.publish(user, 11L, 3L, true));
    }

    @Test
    void publicationRequiresTitleDateAndRealItemsRatherThanEmptyJson() {
        var bundle = new EsCommunicationBundle();
        var title = component(1L, "title", EsCommunicationBundleTemplateComponent.Kind.TEXT, true);
        var items = component(2L, "starting_points", EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST, true);
        var titleValue = new EsCommunicationBundleComponentValue();
        titleValue.setComponentId(1L);
        var listValue = new EsCommunicationBundleComponentValue();
        listValue.setComponentId(2L);
        var data = new OrientationResources(bundle, List.of(title, items), List.of(), List.of(),
                List.of(titleValue, listValue), List.of(), List.of());
        assertThrows(IllegalStateException.class, () -> StarterPacketService.validatePublication(data));
        bundle.setCommunicationMonth(10);
        bundle.setCommunicationYear(2026);
        assertThrows(IllegalStateException.class, () -> StarterPacketService.validatePublication(data));
        titleValue.setContentText("A new project");
        listValue.setContentJson("[]");
        assertThrows(IllegalStateException.class, () -> StarterPacketService.validatePublication(data));
        listValue.setContentJson("[\"Start here\"]");
        assertDoesNotThrow(() -> StarterPacketService.validatePublication(data));
        titleValue.setContentText("x".repeat(141));
        assertThrows(IllegalStateException.class, () -> StarterPacketService.validatePublication(data));
    }

    private static EsCommunicationBundleTemplateComponent component(Long id, String key,
            EsCommunicationBundleTemplateComponent.Kind kind, boolean required) {
        var component = new EsCommunicationBundleTemplateComponent();
        component.setComponentId(id);
        component.setSemanticKey(key);
        component.setDisplayName(key);
        component.setKind(kind);
        component.setRequired(required);
        return component;
    }
}
