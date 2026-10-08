package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.airahub.interophub.model.*;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.CommunicationBundleService.ResourceDetails;
import org.junit.jupiter.api.Test;

class StarterPacketRendererTest {
    @Test
    void absentOrUnpublishedTeasersProduceNoSection() {
        assertEquals("", html(out -> StarterPacketRenderer.teasers(out, "/hub", List.of())));
        var draft = packet(1L, EsCommunicationBundle.Status.DRAFT);
        var retired = packet(2L, EsCommunicationBundle.Status.RETIRED);
        assertEquals("", html(out -> StarterPacketRenderer.teasers(out, "/hub", List.of(draft, retired))));
    }

    @Test
    void teasersUseSeparateSummaryAndPreserveServiceOrdering() {
        var latest = packet(12L, EsCommunicationBundle.Status.PUBLISHED);
        var older = packet(11L, EsCommunicationBundle.Status.PUBLISHED);
        older.bundle().setCommunicationMonth(9);
        String html = html(out -> StarterPacketRenderer.teasers(out, "/hub", List.of(latest, older)));
        assertTrue(html.contains("<h2>Starter Packet</h2>"));
        assertTrue(html.contains("October 2026"));
        assertTrue(html.contains("September 2026"));
        assertTrue(html.contains("&lt;Title &amp; project&gt;"));
        assertTrue(html.contains("Teaser only"));
        assertFalse(html.contains("Full explanation only"));
        assertTrue(html.indexOf("/es/bundle/12") < html.indexOf("/es/bundle/11"));
    }

    @Test
    void teaserImageUsesPlacementVersionNotCurrentResourceFile() {
        var original = packet(12L, EsCommunicationBundle.Status.PUBLISHED);
        var component = component(4L, "teaser_image", EsCommunicationBundleTemplateComponent.Kind.RESOURCE);
        var placement = placement(4L, 41L, 0);
        placement.setResourceVersionId(90L);
        var current = image(41L, "00000000-0000-0000-0000-000000000001", null);
        var preserved = image(41L, "00000000-0000-0000-0000-000000000002", 90L);
        var packet = new OrientationResources(original.bundle(), List.of(component), List.of(placement),
                List.of(current, preserved), List.of(), List.of(), List.of());
        String html = html(out -> StarterPacketRenderer.teasers(out, "/hub", List.of(packet)));
        assertTrue(html.contains("<img src=\"/hub/files/00000000-0000-0000-0000-000000000002\""));
        assertFalse(html.contains("/hub/files/00000000-0000-0000-0000-000000000001"));
        assertTrue(html.contains("alt=\"Image &amp; caption\""));
    }

    @Test
    void copiedDraftCanShowPreservedAndCurrentVersionsOfOneResourceInDifferentRoles() {
        var original = packet(12L, EsCommunicationBundle.Status.DRAFT);
        var teaser = component(4L, "teaser_image", EsCommunicationBundleTemplateComponent.Kind.RESOURCE);
        var supporting = component(5L, "supporting_resources", EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION);
        var preservedPlacement = placement(4L, 41L, 0);
        preservedPlacement.setPlacementId(51L);
        preservedPlacement.setResourceVersionId(90L);
        var currentPlacement = placement(5L, 41L, 0);
        currentPlacement.setPlacementId(52L);
        var current = image(41L, "00000000-0000-0000-0000-000000000001", null);
        current.resource().setTitle("Current title");
        var preserved = image(41L, "00000000-0000-0000-0000-000000000002", 90L);
        preserved.resource().setTitle("Preserved title");
        var packet = new OrientationResources(original.bundle(), List.of(teaser, supporting),
                List.of(preservedPlacement, currentPlacement), List.of(current, preserved));
        String html = management(packet, Map.of());
        assertTrue(html.contains("<h4>Preserved title</h4>"));
        assertTrue(html.contains("<h4>Current title</h4>"));
        assertTrue(html.indexOf("<h4>Preserved title</h4>") < html.indexOf("<h4>Current title</h4>"));
        String preview = html(out -> StarterPacketRenderer.full(out, "/hub", packet));
        assertTrue(preview.contains("/files/00000000-0000-0000-0000-000000000002"));
        assertTrue(preview.contains("/files/00000000-0000-0000-0000-000000000001"));
        assertFalse(preview.contains("<form"));
    }

    @Test
    void fullPageUsesOriginalComponentsExceptTitleAndTeaserSummary() {
        var packet = packet(12L, EsCommunicationBundle.Status.DRAFT);
        String html = html(out -> StarterPacketRenderer.full(out, "/hub", packet));
        assertTrue(html.contains("<h1>&lt;Title &amp; project&gt;</h1>"));
        assertTrue(html.contains("DRAFT preview"));
        assertTrue(html.contains("Full explanation only"));
        assertFalse(html.contains("Teaser only"));
        assertTrue(html.contains("/hub/es/topic/10"));
        assertTrue(html.contains("Back to Topic"));
        assertTrue(html.contains("/hub/es/starter-packets/10?bundleId=12"));
        packet.bundle().setStatus(EsCommunicationBundle.Status.RETIRED);
        assertTrue(html(out -> StarterPacketRenderer.full(out, "/hub", packet)).contains("RETIRED"));
    }

    @Test
    void draftEditorHasTokensLimitsPreservationAndRetainsRejectedText() {
        var packet = packet(12L, EsCommunicationBundle.Status.DRAFT);
        String html = management(packet, Map.of("action", "text", "componentId", "1", "content", "<Rejected & title>"));
        assertEquals(count(html, "<form "), count(html, "name=\"csrfToken\" value=\"test-token\""));
        assertTrue(html.contains("maxlength=\"140\" required"));
        assertTrue(html.contains("maxlength=\"50000\""));
        assertTrue(html.contains("&lt;Rejected &amp; title&gt;</textarea>"));
        assertTrue(html.contains("name=\"preserveConfirmed\" value=\"true\" required"));
        assertTrue(html.contains("file URLs are public"));
        assertTrue(html.contains("immutable, independently identified copies"));
        assertTrue(html.contains("remote link contents are not frozen"));
        assertTrue(html.contains("name=\"action\" value=\"settings\""));
        assertTrue(html.contains("name=\"action\" value=\"upload\""));
        assertTrue(html.contains("name=\"action\" value=\"link\""));
    }

    @Test
    void publishedAndRetiredPacketsHaveNoDraftMutationForms() {
        for (var status : List.of(EsCommunicationBundle.Status.PUBLISHED, EsCommunicationBundle.Status.RETIRED)) {
            var packet = packet(12L, status);
            String html = management(packet, Map.of());
            assertTrue(html.contains("name=\"action\" value=\"copy\""));
            assertEquals(status == EsCommunicationBundle.Status.PUBLISHED, html.contains("name=\"action\" value=\"retire\""));
            for (String action : List.of("settings", "text", "list", "select", "remove", "context", "upload", "link", "publish")) {
                assertFalse(html.contains("name=\"action\" value=\"" + action + "\""), action);
            }
            assertTrue(html.contains("packet is locked"));
        }
    }

    @Test
    void fullBundlePresentationIsReadOnlyForEveryStatus() {
        for (var status : EsCommunicationBundle.Status.values()) {
            var packet = packet(12L, status);
            String html = html(out -> StarterPacketRenderer.full(out, "/hub", packet));
            assertTrue(html.contains(status.name()));
            assertFalse(html.contains("<form"));
            assertFalse(html.contains("<textarea"));
            assertTrue(html.contains("Manage Starter Packet"));
        }
    }

    @Test
    void resourceEditorLimitsTeaserToRasterAndOrdersCollectionControls() {
        var original = packet(12L, EsCommunicationBundle.Status.DRAFT);
        var teaser = component(4L, "teaser_image", EsCommunicationBundleTemplateComponent.Kind.RESOURCE);
        var supporting = component(5L, "supporting_resources", EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION);
        var image = image(41L, "00000000-0000-0000-0000-000000000001", null);
        var link = new EsTopicResource();
        link.setTopicResourceId(42L);
        link.setTitle("Remote link");
        link.setResourceType(EsTopicResource.ResourceType.EXTERNAL_LINK);
        link.setExternalUrl("https://example.org");
        var details = new ResourceDetails(link, null);
        var p1 = placement(5L, 41L, 0);
        var p2 = placement(5L, 42L, 10);
        var packet = new OrientationResources(original.bundle(), List.of(teaser, supporting), List.of(p2, p1),
                List.of(image, details));
        String html = management(packet, Map.of("action", "context", "placementId", p1.getPlacementId().toString(),
                "contextNote", "<Keep this note>"));
        String imageSelect = html.substring(html.indexOf("id=\"resource-4\""), html.indexOf("</select>"));
        assertTrue(imageSelect.contains("value=\"41\""));
        assertFalse(imageSelect.contains("value=\"42\""));
        assertTrue(html.indexOf("<h4>Image &amp; caption") < html.indexOf("<h4>Remote link"));
        assertEquals(1, count(html, "name=\"action\" value=\"moveUp\""));
        assertEquals(1, count(html, "name=\"action\" value=\"moveDown\""));
        assertTrue(html.contains("&lt;Keep this note&gt;</textarea>"));
    }

    private static String management(OrientationResources packet, Map<String, String> submitted) {
        return html(out -> StarterPacketRenderer.management(out, "/hub", 10L, "test-token", List.of(packet), packet,
                packet.resources(), submitted, null));
    }

    static OrientationResources packet(Long id, EsCommunicationBundle.Status status) {
        var bundle = new EsCommunicationBundle();
        bundle.setBundleId(id);
        bundle.setEsTopicId(10L);
        bundle.setTemplateId(700L);
        bundle.setStatus(status);
        bundle.setCommunicationMonth(10);
        bundle.setCommunicationYear(2026);
        return new OrientationResources(bundle, List.of(
                component(1L, "title", EsCommunicationBundleTemplateComponent.Kind.TEXT),
                component(2L, "teaser_summary", EsCommunicationBundleTemplateComponent.Kind.TEXT),
                component(3L, "explanation", EsCommunicationBundleTemplateComponent.Kind.TEXT)),
                List.of(), List.of(), List.of(value(1L, "<Title & project>"), value(2L, "Teaser only"),
                        value(3L, "Full explanation only")), List.of(), List.of());
    }

    private static EsCommunicationBundleTemplateComponent component(Long id, String key,
            EsCommunicationBundleTemplateComponent.Kind kind) {
        var component = new EsCommunicationBundleTemplateComponent();
        component.setComponentId(id);
        component.setSemanticKey(key);
        component.setDisplayName(key);
        component.setKind(kind);
        component.setRequired("title".equals(key));
        component.setCardinality(kind == EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION
                ? EsCommunicationBundleTemplateComponent.Cardinality.REPEATING : EsCommunicationBundleTemplateComponent.Cardinality.SINGLE);
        return component;
    }

    private static EsCommunicationBundleComponentValue value(Long id, String text) {
        var value = new EsCommunicationBundleComponentValue();
        value.setComponentId(id);
        value.setContentText(text);
        return value;
    }

    private static EsCommunicationBundleResourcePlacement placement(Long componentId, Long resourceId, int order) {
        var placement = new EsCommunicationBundleResourcePlacement();
        placement.setPlacementId(resourceId);
        placement.setComponentId(componentId);
        placement.setTopicResourceId(resourceId);
        placement.setDisplayOrder(order);
        return placement;
    }

    private static ResourceDetails image(Long id, String publicId, Long versionId) {
        var resource = new EsTopicResource();
        resource.setTopicResourceId(id);
        resource.setTitle("Image & caption");
        resource.setResourceType(EsTopicResource.ResourceType.IMAGE);
        var file = new StoredFile();
        file.setPublicId(publicId);
        file.setContentType("image/png");
        return new ResourceDetails(resource, file, versionId);
    }

    private static String html(Consumer<PrintWriter> render) {
        var writer = new StringWriter();
        render.accept(new PrintWriter(writer));
        return writer.toString();
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
