package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.airahub.interophub.model.*;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.CommunicationBundleService.ResourceDetails;
import org.airahub.interophub.service.StoredFileService;
import org.junit.jupiter.api.Test;

class TopicOrientationResourceRendererTest {
    @Test
    void collectionOrderingAndContextControlsOnlyAppearInEditableResourceRoles() {
        var first = resource(1L, "First", null, null);
        var second = resource(2L, "Second", null, null);
        var content = orientation(List.of(first, second));
        content.placements().get(0).setContextNote("<b>Start here</b>");
        String html = editor(content, content.resources(), "Uploads disabled for this test");
        assertTrue(html.contains("Move First down"));
        assertTrue(html.contains("Move Second up"));
        assertFalse(html.contains("Move First up"));
        assertFalse(html.contains("Move Second down"));
        assertTrue(html.contains("name=\"action\" value=\"context\""));
        assertTrue(html.contains("&lt;b&gt;Start here&lt;/b&gt;"));
        assertFalse(preview(content).contains("Save resource context"));
        content.bundle().setStatus(EsCommunicationBundle.Status.RETIRED);
        html = editor(content, content.resources(), "Uploads disabled for this test");
        assertFalse(html.contains("name=\"action\" value=\"moveUp\""));
        assertFalse(html.contains("name=\"action\" value=\"context\""));
    }

    @Test
    void communicationDateAppearsBelowInfographicForPublishedAndDraftOrientations() {
        var orientation = orientation(List.of(resource(1L, "Infographic", "image/png", "visual.png")));
        orientation.bundle().setCommunicationMonth(10);
        orientation.bundle().setCommunicationYear(2026);
        orientation.bundle().setStatus(EsCommunicationBundle.Status.PUBLISHED);
        String published = preview(orientation);
        assertTrue(published.contains("Current as of October 2026"));
        assertTrue(published.indexOf("Current as of October 2026") > published.indexOf("</figure>"));
        orientation.bundle().setStatus(EsCommunicationBundle.Status.DRAFT);
        String draft = preview(orientation);
        assertTrue(draft.contains("Draft dated October 2026"));
        assertTrue(draft.indexOf("Draft dated October 2026") > draft.indexOf("</figure>"));
    }

    @Test
    void optionalNarrativePromptsRemainEditableWithoutRequiredAttributesAndBlankContentIsOmitted() {
        var original = orientation(List.of());
        var prompts = List.of("What this Topic is", "Why it matters", "How to get involved");
        var components = java.util.stream.IntStream.range(0, prompts.size()).mapToObj(index -> {
            var component = new EsCommunicationBundleTemplateComponent();
            component.setComponentId(30L + index);
            component.setDisplayName(prompts.get(index));
            component.setKind(EsCommunicationBundleTemplateComponent.Kind.TEXT);
            component.setRequired(false);
            return component;
        }).toList();
        var orientation = new OrientationResources(original.bundle(), components, List.of(), List.of());
        String html = editor(orientation, List.of(), null);
        for (String prompt : prompts) {
            assertTrue(html.contains(prompt));
            assertFalse(html.contains(prompt + " (required)"));
        }
        for (var component : components) {
            var textarea = java.util.regex.Pattern.compile("<textarea[^>]*id=\"component-"
                    + component.getComponentId() + "\"[^>]*>").matcher(html);
            assertTrue(textarea.find());
            assertFalse(textarea.group().contains(" required"));
        }
        StringWriter writer = new StringWriter();
        TopicOrientationResourceRenderer.narrative(new PrintWriter(writer), orientation);
        assertEquals("", writer.toString());
    }

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
        assertTrue(html.contains("No infographic selected"));
        assertTrue(html.contains("Draft preview - visible only to Topic stewards"));
        assertFalse(html.contains("<h3"));
    }

    @Test
    void unselectedImagesStayIconsAndAbsentOrientationKeepsPlaceholder() {
        var image = resource(1L, "Supporting diagram", "image/png", "diagram.png");
        var orientation = orientation(List.of(image));
        orientation.components().get(1).setSemanticKey("additional_resources");
        String html = preview(orientation);
        assertTrue(html.contains("No infographic selected"));
        assertFalse(html.contains("<img "));
        assertTrue(html.contains("Supporting diagram (IMG) - opens in a new tab"));
        assertTrue(html.contains("</svg> Supporting diagram</a>"));
        assertTrue(preview(null).contains("No infographic selected"));
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

    @Test
    void publishedOrientationRendersLiveEditingControlsButNotPublishAction() {
        var resource = resource(1L, "Link", null, null);
        var orientation = orientation(List.of(resource));
        orientation.bundle().setStatus(EsCommunicationBundle.Status.PUBLISHED);
        orientation.bundle().setAudience(EsCommunicationBundlePurpose.Audience.PUBLIC);
        var narrative = new EsCommunicationBundleTemplateComponent();
        narrative.setComponentId(30L);
        narrative.setKind(EsCommunicationBundleTemplateComponent.Kind.TEXT);
        narrative.setDisplayName("What this Topic is");
        orientation = new OrientationResources(orientation.bundle(),
                List.of(orientation.components().get(0), orientation.components().get(1), narrative),
                orientation.placements(), orientation.resources());
        String html = editor(orientation, List.of(resource), null);
        assertTrue(html.contains("Published Topic Orientation"));
        assertTrue(html.contains("name=\"action\" value=\"select\""));
        assertTrue(html.contains("name=\"action\" value=\"remove\""));
        assertTrue(html.contains("name=\"action\" value=\"settings\""));
        assertTrue(html.contains("name=\"action\" value=\"text\""));
        assertTrue(html.contains("min=\"1\" max=\"12\" required"));
        assertTrue(html.contains("min=\"1\" max=\"9999\" required"));
        assertTrue(html.contains("Each save updates the published Topic page immediately"));
        assertFalse(html.contains("name=\"action\" value=\"publish\""));
        assertTrue(html.contains("name=\"action\" value=\"retire\""));
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
                orientation, resources, new StoredFileService(), problem);
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
