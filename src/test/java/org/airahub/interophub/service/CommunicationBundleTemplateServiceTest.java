package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent.Cardinality;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent.Kind;
import org.junit.jupiter.api.Test;

class CommunicationBundleTemplateServiceTest {
    @Test
    void templateOperationsRequireGlobalAdministrationNotTopicStewardship() {
        var service = new CommunicationBundleTemplateService();
        var nonAdmin = new org.airahub.interophub.model.User();
        nonAdmin.setIsAdmin(false);
        assertThrows(SecurityException.class, () -> service.listPurposes(null));
        assertThrows(SecurityException.class, () -> service.getTemplate(nonAdmin, 1L));
        assertThrows(SecurityException.class, () -> service.createDraft(nonAdmin, 1L, 1L));
        assertThrows(SecurityException.class, () -> service.saveComponent(nonAdmin, 1L, null,
                input("text", Kind.TEXT, Cardinality.SINGLE)));
        assertThrows(SecurityException.class, () -> service.removeComponent(nonAdmin, 1L, 1L));
        assertThrows(SecurityException.class, () -> service.activate(nonAdmin, 1L));
    }

    @Test
    void validatesKeysLabelsKindsCardinalitiesAndOrdering() {
        assertThrows(IllegalArgumentException.class, () -> input("Bad Key", Kind.TEXT, Cardinality.SINGLE).validate());
        assertThrows(IllegalArgumentException.class, () -> input("items", Kind.RESOURCE_COLLECTION, Cardinality.SINGLE).validate());
        assertThrows(IllegalArgumentException.class, () -> input("text", Kind.TEXT, Cardinality.REPEATING).validate());
        assertThrows(IllegalArgumentException.class, () -> new CommunicationBundleTemplateService.ComponentInput(
                "text", " ", null, Kind.TEXT, false, Cardinality.SINGLE, 0).validate());
        assertDoesNotThrow(() -> input("items", Kind.STRUCTURED_LIST, Cardinality.REPEATING).validate());
    }

    @Test
    void orientationMustKeepItsNamedResourceRolesAndGenericKeysAreUnique() {
        var infographic = component("primary_infographic", Kind.RESOURCE, Cardinality.SINGLE);
        assertThrows(IllegalArgumentException.class, () -> CommunicationBundleTemplateService.validateComponents(
                CommunicationBundleService.TOPIC_ORIENTATION_KEY, List.of(infographic)));
        assertThrows(IllegalArgumentException.class, () -> CommunicationBundleTemplateService.validateComponents(
                "FUTURE_PURPOSE", List.of(infographic, infographic)));
        assertDoesNotThrow(() -> CommunicationBundleTemplateService.validateComponents("FUTURE_PURPOSE",
                List.of(component("items", Kind.STRUCTURED_LIST, Cardinality.REPEATING))));
    }

    private static CommunicationBundleTemplateService.ComponentInput input(String key, Kind kind, Cardinality cardinality) {
        return new CommunicationBundleTemplateService.ComponentInput(key, "Display", "Prompt", kind, false, cardinality, 10);
    }

    @Test
    void starterPacketTemplatesRetainTheirSemanticContractAndRequiredTitle() {
        var fields = new java.util.ArrayList<EsCommunicationBundleTemplateComponent>();
        String[] keys = {"title", "teaser_summary", "teaser_image", "explanation", "starting_points",
                "next_actions", "supporting_resources"};
        Kind[] kinds = {Kind.TEXT, Kind.TEXT, Kind.RESOURCE, Kind.TEXT, Kind.STRUCTURED_LIST,
                Kind.STRUCTURED_LIST, Kind.RESOURCE_COLLECTION};
        for (int i = 0; i < keys.length; i++) {
            var field = component(keys[i], kinds[i], i >= 4 ? Cardinality.REPEATING : Cardinality.SINGLE);
            field.setDisplayOrder(i);
            field.setRequired(i == 0);
            fields.add(field);
        }
        assertDoesNotThrow(() -> CommunicationBundleTemplateService.validateComponents(StarterPacketService.PURPOSE_KEY, fields));
        fields.get(0).setRequired(false);
        assertThrows(IllegalArgumentException.class, () -> CommunicationBundleTemplateService.validateComponents(StarterPacketService.PURPOSE_KEY, fields));
        fields.get(0).setRequired(true);
        fields.get(2).setKind(Kind.TEXT);
        assertThrows(IllegalArgumentException.class, () -> CommunicationBundleTemplateService.validateComponents(StarterPacketService.PURPOSE_KEY, fields));
        fields.remove(2);
        assertThrows(IllegalArgumentException.class, () -> CommunicationBundleTemplateService.validateComponents(StarterPacketService.PURPOSE_KEY, fields));
    }

    private static EsCommunicationBundleTemplateComponent component(String key, Kind kind, Cardinality cardinality) {
        var result = new EsCommunicationBundleTemplateComponent();
        input(key, kind, cardinality).applyTo(result);
        return result;
    }
}
