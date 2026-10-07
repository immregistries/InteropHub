package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.airahub.interophub.model.*;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.CommunicationBundleService.ResourceDetails;
import org.junit.jupiter.api.Test;

class TopicOrientationResourceRendererTest {
    @Test
    void overviewUsesOnlyThePrimaryInfographicAndNamedChipsForOtherResources() {
        var image = resource(1L, "An infographic", "image/png", "visual.png");
        var pdf = resource(2L, "One page", "application/pdf", "one.pdf");
        var office = resource(3L, "Slides", "application/vnd.ms-powerpoint", "slides.ppt");
        var link = resource(4L, "Reference", null, null);
        var supportingImage = resource(5L, "Supporting diagram", "image/png", "diagram.png");
        String html = preview(orientation(List.of(image, pdf, office, link, supportingImage)));
        assertTrue(html.contains("Draft preview - visible only to Topic stewards"));
        assertFalse(html.contains("Orientation resources"));
        assertFalse(html.contains("aira-section-card"));
        assertFalse(html.contains("Coming soon"));
        assertEquals(1, html.split("<img ", -1).length - 1);
        assertTrue(html.contains("<img src=\"/hub/files/00000000-0000-0000-0000-000000000001\""));
        assertTrue(html.contains("alt=\"An infographic\""));
        assertTrue(html.contains("max-width:100%;height:auto;"));
        assertTrue(html.contains("Supporting diagram (IMG) - opens in a new tab"));
        assertEquals(4, html.split("<svg ", -1).length - 1);
        assertTrue(html.contains("Slides (PPT) - download"));
        assertTrue(html.contains("One page (PDF) - opens in a new tab"));
        assertEquals(4, html.split("class=\"aira-chip\"", -1).length - 1);
        assertTrue(html.contains("</svg> Slides</a>"));
        assertFalse(html.contains("aira-button--icon"));
        assertTrue(html.contains("target=\"_blank\" rel=\"noopener noreferrer\""));
        assertTrue(html.contains(" download><svg"));
        assertTrue(html.contains("href=\"https://example.org/resource\""));
        assertFalse(html.contains("<iframe"));
    }

    @Test
    void resourceMetadataIsEscapedNotRenderedAsHtml() {
        var resource = resource(1L, "<script>title</script>", "image/png", "\"unsafe.png");
        resource.resource().setDescription("<img src=x onerror=alert(1)>");
        resource.resource().setAttribution("A & B");
        String html = preview(orientation(List.of(resource)));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<img src=x"));
        assertTrue(html.contains("&lt;script&gt;title&lt;/script&gt;"));
        assertFalse(html.contains("Attribution:"));
        assertFalse(html.contains("onerror"));
    }

    @Test
    void emptyOptionalRolesAreOmittedAndDraftIsClearlyEmpty() {
        String html = preview(orientation(List.of()));
        assertTrue(html.contains("Coming soon"));
        assertFalse(html.contains("Draft preview"));
        assertFalse(html.contains("<h3"));
    }

    @Test
    void unselectedImagesStayIconsAndAbsentOrientationKeepsPlaceholder() {
        var image = resource(1L, "Supporting diagram", "image/png", "diagram.png");
        var orientation = orientation(List.of(image));
        orientation.components().get(1).setSemanticKey("additional_resources");
        String html = preview(orientation);
        assertTrue(html.contains("Coming soon"));
        assertFalse(html.contains("<img "));
        assertTrue(html.contains("Supporting diagram (IMG) - opens in a new tab"));
        assertTrue(html.contains("</svg> Supporting diagram</a>"));
        assertTrue(preview(null).contains("Coming soon"));
    }

    @Test
    void supportingChipTitlesAreEscapedAndEmptySelectionsProduceNoLinks() {
        var document = resource(1L, "<script>Notes & references</script>", "application/msword", "notes.doc");
        StringWriter writer = new StringWriter();
        TopicOrientationResourceRenderer.supportingChips(new PrintWriter(writer), "/hub",
                orientation(List.of(document)));
        String html = writer.toString();
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("</svg> &lt;script&gt;Notes &amp; references&lt;/script&gt;</a>"));
        assertTrue(html.contains("(DOC) - download"));
        writer = new StringWriter();
        TopicOrientationResourceRenderer.supportingChips(new PrintWriter(writer), "/hub", null);
        assertEquals("", writer.toString());
    }

    @Test
    void disabledStorageStillAllowsLinksAndMetadataButNotUploads() {
        String html = editor(null, List.of(resource(1L, "Link", null, null)), "Storage is not configured.");
        assertTrue(html.contains("Uploads unavailable: Storage is not configured."));
        assertFalse(html.contains("type=\"file\""));
        assertTrue(html.contains("Add external link"));
        assertTrue(html.contains("Save resource metadata"));
        assertTrue(html.contains("Create Orientation draft"));
        assertTrue(html.contains("name=\"csrfToken\" value=\"test-token\""));
    }

    @Test
    void roleSelectionHasStableComponentAndResourceIdsAndRemovalRetainsLibrary() {
        var resource = resource(1L, "Link", null, null);
        String html = editor(orientation(List.of(resource)), List.of(resource), null);
        assertTrue(html.contains("name=\"componentId\" value=\"21\""));
        assertTrue(html.contains("<option value=\"1\">Link"));
        assertTrue(html.contains("name=\"placementId\" value=\"1\""));
        assertTrue(html.contains("aria-label=\"Remove Link from Additional resources\""));
        assertTrue(html.contains("enctype=\"multipart/form-data\""));
        assertTrue(html.contains("Draft visibility does not protect file URLs"));
    }

    private static String preview(OrientationResources orientation) {
        StringWriter writer = new StringWriter();
        TopicOrientationResourceRenderer.overview(new PrintWriter(writer), "/hub", orientation);
        assertFalse(writer.toString().contains("aira-chip"));
        TopicOrientationResourceRenderer.supportingChips(new PrintWriter(writer), "/hub", orientation);
        return writer.toString();
    }

    private static String editor(OrientationResources orientation, List<ResourceDetails> resources, String problem) {
        StringWriter writer = new StringWriter();
        TopicOrientationResourceRenderer.editor(new PrintWriter(writer), "/hub", 10L, "test-token",
                orientation, resources, problem);
        return writer.toString();
    }

    private static OrientationResources orientation(List<ResourceDetails> resources) {
        var bundle = new EsCommunicationBundle();
        bundle.setBundleId(10L);
        bundle.setStatus(EsCommunicationBundle.Status.DRAFT);
        var component = new EsCommunicationBundleTemplateComponent();
        component.setComponentId(20L);
        component.setSemanticKey("primary_infographic");
        component.setDisplayName("Infographic");
        component.setKind(EsCommunicationBundleTemplateComponent.Kind.RESOURCE);
        component.setCardinality(EsCommunicationBundleTemplateComponent.Cardinality.SINGLE);
        var additional = new EsCommunicationBundleTemplateComponent();
        additional.setComponentId(21L);
        additional.setSemanticKey("additional_resources");
        additional.setDisplayName("Additional resources");
        additional.setKind(EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION);
        additional.setCardinality(EsCommunicationBundleTemplateComponent.Cardinality.REPEATING);
        var placements = resources.stream().map(r -> {
            var p = new EsCommunicationBundleResourcePlacement();
            p.setPlacementId(r.resource().getTopicResourceId());
            p.setComponentId(r.resource().getTitle().equals("Supporting diagram")
                    || !r.resource().getResourceType().equals(EsTopicResource.ResourceType.IMAGE) ? 21L : 20L);
            p.setTopicResourceId(r.resource().getTopicResourceId());
            return p;
        }).toList();
        return new OrientationResources(bundle, List.of(component, additional), placements, resources);
    }

    private static ResourceDetails resource(Long id, String title, String type, String filename) {
        var resource = new EsTopicResource();
        resource.setTopicResourceId(id);
        resource.setTitle(title);
        resource.setResourceType(type == null ? EsTopicResource.ResourceType.EXTERNAL_LINK
                : type.startsWith("image/") ? EsTopicResource.ResourceType.IMAGE
                : type.equals("application/pdf") ? EsTopicResource.ResourceType.PDF
                : type.equals("application/vnd.ms-powerpoint") ? EsTopicResource.ResourceType.PRESENTATION
                : EsTopicResource.ResourceType.DOCUMENT);
        StoredFile file = null;
        if (type != null) {
            resource.setStoredFileId(id);
            file = new StoredFile();
            file.setPublicId(String.format("00000000-0000-0000-0000-%012d", id));
            file.setContentType(type);
            file.setOriginalFilename(filename);
        } else {
            resource.setExternalUrl("https://example.org/resource");
        }
        return new ResourceDetails(resource, file);
    }
}
