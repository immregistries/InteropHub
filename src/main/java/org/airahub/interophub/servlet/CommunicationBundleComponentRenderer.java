package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.util.List;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.CommunicationBundleStructuredList;
import org.airahub.interophub.service.StoredFileService;

final class CommunicationBundleComponentRenderer {
    private CommunicationBundleComponentRenderer() { }

    static void narrative(PrintWriter out, OrientationResources content) {
        if (content == null) {
            return;
        }
        for (var component : content.components()) {
            var value = content.componentValues().stream()
                    .filter(v -> v.getComponentId().equals(component.getComponentId())).findFirst().orElse(null);
            if (value == null) {
                continue;
            }
            if (component.getKind() == EsCommunicationBundleTemplateComponent.Kind.TEXT
                    && value.getContentText() != null && !value.getContentText().isBlank()) {
                out.println("<section><h3>" + escape(component.getDisplayName())
                        + "</h3><p style=\"white-space:pre-wrap;\">" + escape(value.getContentText()) + "</p></section>");
            } else if (component.getKind() == EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST) {
                var items = CommunicationBundleStructuredList.items(value.getContentJson());
                if (!items.isEmpty()) {
                    out.println("<section><h3>" + escape(component.getDisplayName()) + "</h3>");
                    list(out, items);
                    out.println("</section>");
                }
            }
        }
    }

    static void render(PrintWriter out, String context, OrientationResources content) {
        for (var component : content.components()) {
            if (component.getKind() == EsCommunicationBundleTemplateComponent.Kind.TEXT
                    || component.getKind() == EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST) {
                narrative(out, new OrientationResources(content.bundle(), List.of(component), content.placements(),
                        content.resources(), content.componentValues(), List.of(), content.allowedAudiences()));
            } else {
                var placements = content.placements().stream().filter(p -> p.getComponentId().equals(component.getComponentId()))
                        .sorted(java.util.Comparator.comparingInt(
                                org.airahub.interophub.model.EsCommunicationBundleResourcePlacement::getDisplayOrder)).toList();
                if (placements.isEmpty()) {
                    continue;
                }
                out.println("<section class=\"aira-stack\"><h3>" + escape(component.getDisplayName()) + "</h3>");
                for (var placement : placements) {
                    var resource = content.resources().stream()
                            .filter(r -> r.resource().getTopicResourceId().equals(placement.getTopicResourceId())).findFirst()
                            .orElseThrow(() -> new IllegalStateException("Selected resource is unavailable."));
                    String url = resource.file() == null ? resource.resource().getExternalUrl()
                            : StoredFileService.readUrl(context, resource.file());
                    out.println("<p><a class=\"aira-inline-link\" href=\"" + escape(url)
                            + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + escape(resource.resource().getTitle()) + "</a></p>");
                    if (placement.getContextNote() != null && !placement.getContextNote().isBlank()) {
                        out.println("<p style=\"white-space:pre-wrap;\">" + escape(placement.getContextNote()) + "</p>");
                    }
                }
                out.println("</section>");
            }
        }
    }

    static void valueEditor(PrintWriter out, String actionUrl, String csrf, OrientationResources content) {
        for (var component : content.components()) {
            boolean structured = component.getKind() == EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST;
            if (!structured && component.getKind() != EsCommunicationBundleTemplateComponent.Kind.TEXT) {
                continue;
            }
            var value = content.componentValues().stream()
                    .filter(v -> v.getComponentId().equals(component.getComponentId())).findFirst().orElse(null);
            String text = value == null ? "" : structured
                    ? String.join("\n", CommunicationBundleStructuredList.items(value.getContentJson())) : value.getContentText();
            out.println("<form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\"" + escape(actionUrl) + "\">"
                    + "<input type=\"hidden\" name=\"csrfToken\" value=\"" + escape(csrf) + "\">"
                    + "<input type=\"hidden\" name=\"action\" value=\"" + (structured ? "list" : "text") + "\">"
                    + "<input type=\"hidden\" name=\"bundleId\" value=\"" + content.bundle().getBundleId() + "\">"
                    + "<input type=\"hidden\" name=\"componentId\" value=\"" + component.getComponentId() + "\">"
                    + "<label class=\"aira-label\" for=\"component-" + component.getComponentId() + "\">"
                    + escape(component.getDisplayName()) + (component.isRequired() ? " (required)" : " (recommended)")
                    + "</label><p class=\"aira-meta\">" + escape(component.getAuthoringPrompt())
                    + (structured ? " Enter one item per line." : "") + "</p>"
                    + "<textarea class=\"aira-textarea\" id=\"component-" + component.getComponentId()
                    + "\" name=\"content\" rows=\"5\" maxlength=\"50000\"" + (component.isRequired() ? " required" : "")
                    + ">" + escape(text) + "</textarea><button class=\"aira-button aira-button--secondary\" type=\"submit\">Save "
                    + escape(component.getDisplayName()) + "</button></form>");
        }
    }

    static void templatePreview(PrintWriter out, List<EsCommunicationBundleTemplateComponent> components) {
        for (var component : components) {
            out.println("<section><h3>" + escape(component.getDisplayName()) + "</h3><p class=\"aira-meta\">"
                    + (component.isRequired() ? "Required" : "Recommended") + " - " + escape(component.getAuthoringPrompt()) + "</p>");
            switch (component.getKind()) {
                case TEXT -> out.println("<p>Example narrative for " + escape(component.getDisplayName()) + ".</p>");
                case STRUCTURED_LIST -> list(out, List.of("Example first item", "Example second item"));
                case RESOURCE -> out.println("<p>Selected Topic Resource</p>");
                case RESOURCE_COLLECTION -> list(out, List.of("First selected Topic Resource", "Second selected Topic Resource"));
            }
            out.println("</section>");
        }
    }

    private static void list(PrintWriter out, List<String> items) {
        out.println("<ul>");
        for (String item : items) {
            out.println("<li>" + escape(item) + "</li>");
        }
        out.println("</ul>");
    }

    private static String escape(String value) {
        return TopicOrientationResourceRenderer.escape(value);
    }
}
