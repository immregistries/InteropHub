package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.airahub.interophub.model.*;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.CommunicationBundleService.ResourceDetails;
import org.airahub.interophub.service.CommunicationBundleStructuredList;
import org.junit.jupiter.api.Test;

class CommunicationBundleComponentRendererTest {
    @Test
    void structuredListsAreEscapedAndEmptyOptionalComponentsDisappear() {
        var component = component(1L, EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST);
        var value = new EsCommunicationBundleComponentValue();
        value.setComponentId(1L);
        value.setContentJson(CommunicationBundleStructuredList.fromLines("<script>alert(1)</script>\nSecond item"));
        var data = content(List.of(component), List.of(value), List.of(), List.of());
        StringWriter writer = new StringWriter();
        CommunicationBundleComponentRenderer.render(new PrintWriter(writer), "/hub", data);
        String html = writer.toString();
        assertTrue(html.contains("<li>&lt;script&gt;alert(1)&lt;/script&gt;</li>"));
        assertFalse(html.contains("<script>"));
        value.setContentJson(null);
        writer = new StringWriter();
        CommunicationBundleComponentRenderer.render(new PrintWriter(writer), "/hub", data);
        assertEquals("", writer.toString());
    }

    @Test
    void valueEditorUsesTheFieldKindAndRequiredFlag() {
        var text = component(1L, EsCommunicationBundleTemplateComponent.Kind.TEXT);
        text.setRequired(true);
        var list = component(2L, EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST);
        var writer = new StringWriter();
        CommunicationBundleComponentRenderer.valueEditor(new PrintWriter(writer), "/hub/es/topic-resources/10", "token",
                content(List.of(text, list), List.of(), List.of(), List.of()));
        String html = writer.toString();
        assertTrue(html.contains("name=\"action\" value=\"text\""));
        assertTrue(html.contains("name=\"action\" value=\"list\""));
        assertTrue(html.contains("maxlength=\"50000\" required"));
        assertTrue(html.contains("(recommended)"));
        assertTrue(html.contains("Enter one item per line."));
    }

    @Test
    void resourceCollectionUsesExplicitOrderAndEscapesContext() {
        var component = component(1L, EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION);
        var first = new EsTopicResource();
        first.setTopicResourceId(11L);
        first.setTitle("First");
        first.setExternalUrl("https://example.org/first");
        var second = new EsTopicResource();
        second.setTopicResourceId(12L);
        second.setTitle("Second");
        second.setExternalUrl("https://example.org/second");
        var p1 = placement(11L, 0);
        var p2 = placement(12L, 10);
        p1.setContextNote("<b>Start here</b>");
        var writer = new StringWriter();
        CommunicationBundleComponentRenderer.render(new PrintWriter(writer), "/hub",
                content(List.of(component), List.of(), List.of(p2, p1),
                        List.of(new ResourceDetails(first, null), new ResourceDetails(second, null))));
        String html = writer.toString();
        assertTrue(html.indexOf(">First</a>") < html.indexOf(">Second</a>"));
        assertTrue(html.contains("&lt;b&gt;Start here&lt;/b&gt;"));
    }

    private static EsCommunicationBundleResourcePlacement placement(Long resourceId, int order) {
        var placement = new EsCommunicationBundleResourcePlacement();
        placement.setComponentId(1L);
        placement.setTopicResourceId(resourceId);
        placement.setDisplayOrder(order);
        return placement;
    }

    private static EsCommunicationBundleTemplateComponent component(Long id, EsCommunicationBundleTemplateComponent.Kind kind) {
        var component = new EsCommunicationBundleTemplateComponent();
        component.setComponentId(id);
        component.setDisplayName("Example");
        component.setKind(kind);
        return component;
    }

    private static OrientationResources content(List<EsCommunicationBundleTemplateComponent> components,
            List<EsCommunicationBundleComponentValue> values, List<EsCommunicationBundleResourcePlacement> placements,
            List<ResourceDetails> resources) {
        var bundle = new EsCommunicationBundle();
        bundle.setBundleId(10L);
        return new OrientationResources(bundle, components, placements, resources, values, List.of(), List.of());
    }
}
