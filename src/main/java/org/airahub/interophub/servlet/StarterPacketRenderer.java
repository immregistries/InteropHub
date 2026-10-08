package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsCommunicationBundleResourcePlacement;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.CommunicationBundleService.ResourceDetails;
import org.airahub.interophub.service.CommunicationBundleStructuredList;
import org.airahub.interophub.service.StoredFileService;

final class StarterPacketRenderer {
    private StarterPacketRenderer() { }

    public static void teasers(PrintWriter out, String context, List<OrientationResources> packets) {
        var published = packets.stream()
                .filter(p -> p.bundle().getStatus() == EsCommunicationBundle.Status.PUBLISHED).toList();
        if (published.isEmpty()) {
            return;
        }
        out.println("<section class=\"aira-stack\"><h2>Starter Packet</h2>");
        for (var packet : published) {
            out.println("<article class=\"aira-section-card\"><div class=\"aira-section-card__body aira-stack\">");
            out.println("<h3><a class=\"aira-inline-link\" href=\"" + escape(context) + "/es/bundle/"
                    + packet.bundle().getBundleId() + "\">" + escape(title(packet)) + "</a></h3>");
            out.println("<p class=\"aira-meta\">" + escape(date(packet)) + "</p>");
            String summary = text(packet, "teaser_summary");
            if (!summary.isBlank()) {
                out.println("<p style=\"white-space:pre-wrap;\">" + escape(summary) + "</p>");
            }
            for (var component : packet.components()) {
                if (!"teaser_image".equals(component.getSemanticKey())) {
                    continue;
                }
                for (var placement : placements(packet, component)) {
                    var resource = resource(packet, placement);
                    if (resource.file() != null && resource.file().isImage()) {
                        out.println("<figure class=\"aira-figure\"><img src=\""
                                + escape(StoredFileService.readUrl(context, resource.file())) + "\" alt=\""
                                + escape(resource.resource().getTitle())
                                + "\" style=\"display:block;max-width:100%;height:auto;\"></figure>");
                    }
                }
            }
            out.println("<p><a class=\"aira-inline-link\" href=\"" + escape(context) + "/es/bundle/"
                    + packet.bundle().getBundleId() + "\">View full Starter Packet</a></p></div></article>");
        }
        out.println("</section>");
    }

    static void full(PrintWriter out, String context, OrientationResources packet) {
        out.println("<header><h1>" + escape(title(packet)) + "</h1>");
        out.println("<p class=\"aira-meta\">" + escape(date(packet)) + " &middot; "
                + packet.bundle().getStatus().name() + "</p></header>");
        if (packet.bundle().getStatus() != EsCommunicationBundle.Status.PUBLISHED) {
            out.println("<p class=\"aira-alert aira-alert--info\" role=\"status\">"
                    + (packet.bundle().getStatus() == EsCommunicationBundle.Status.DRAFT
                            ? "DRAFT preview — not published." : "RETIRED — retained for historical reference.")
                    + "</p>");
        }
        out.println("<p><a class=\"aira-inline-link\" href=\"" + escape(context) + "/es/topic/"
                + packet.bundle().getEsTopicId() + "\">Back to Topic</a> &middot; "
                + "<a class=\"aira-inline-link\" href=\"" + escape(context) + "/es/starter-packets/"
                + packet.bundle().getEsTopicId() + "?bundleId=" + packet.bundle().getBundleId()
                + "\">Manage Starter Packet</a></p>");
        var components = packet.components().stream()
                .filter(c -> !"title".equals(c.getSemanticKey()) && !"teaser_summary".equals(c.getSemanticKey())).toList();
        CommunicationBundleComponentRenderer.render(out, context, new OrientationResources(packet.bundle(),
                components, packet.placements(), packet.resources(), packet.componentValues(),
                packet.auditEntries(), packet.allowedAudiences()));
    }

    static void management(PrintWriter out, String context, Long topicId, String csrf,
            List<OrientationResources> packets, OrientationResources selected, List<ResourceDetails> available,
            Map<String, String> submitted, String uploadProblem) {
        String actionUrl = context + "/es/starter-packets/" + topicId;
        out.println("<section class=\"aira-stack\"><h2>Starter Packets</h2>"
                + "<p>Only global administrators and this Topic's active champions/support contacts can access these packets."
                + " Published packets are immutable snapshots; copy one into a new draft to revise it.</p>");
        form(out, actionUrl, csrf, "create", null, false);
        button(out, "Create blank draft");
        out.println("</form>");
        if (packets.isEmpty()) {
            out.println("<p>No Starter Packets yet.</p>");
        }
        for (var packet : packets) {
            out.println("<div class=\"aira-section-card\"><div class=\"aira-section-card__body aira-stack aira-stack--compact\">"
                    + "<h3><a class=\"aira-inline-link\" href=\"" + escape(actionUrl) + "?bundleId="
                    + packet.bundle().getBundleId() + "\">" + escape(title(packet)) + "</a></h3>"
                    + "<p class=\"aira-meta\">" + escape(date(packet)) + " &middot; "
                    + packet.bundle().getStatus().name() + "</p><p><a class=\"aira-inline-link\" href=\""
                    + escape(context) + "/es/bundle/" + packet.bundle().getBundleId() + "\">"
                    + (packet.bundle().getStatus() == EsCommunicationBundle.Status.DRAFT ? "Preview draft" : "View packet")
                    + "</a></p>");
            if (packet.bundle().getStatus() != EsCommunicationBundle.Status.DRAFT) {
                form(out, actionUrl, csrf, "copy", packet.bundle().getBundleId(), false);
                button(out, "Copy into new draft");
                out.println("</form>");
            }
            if (packet.bundle().getStatus() == EsCommunicationBundle.Status.PUBLISHED) {
                form(out, actionUrl, csrf, "retire", packet.bundle().getBundleId(), false);
                button(out, "Retire packet");
                out.println("</form>");
            }
            out.println("</div></div>");
        }
        out.println("</section>");
        if (selected == null) {
            return;
        }
        if (selected.bundle().getStatus() != EsCommunicationBundle.Status.DRAFT) {
            out.println("<p class=\"aira-alert aira-alert--info\">This "
                    + selected.bundle().getStatus().name() + " packet is locked. Copy it to make changes.</p>");
            return;
        }
        Long bundleId = selected.bundle().getBundleId();
        out.println("<section class=\"aira-stack\"><h2>Edit DRAFT: " + escape(title(selected)) + "</h2>"
                + "<p>Save each section before publishing. Month and Year are required for publication."
                + " Teaser summary is separate from the full explanation.</p>");
        form(out, actionUrl, csrf, "settings", bundleId, false);
        input(out, "communicationMonth", "Month", "number", submittedValue(submitted, "settings",
                "communicationMonth", null, selected.bundle().getCommunicationMonth()), " min=\"1\" max=\"12\"");
        input(out, "communicationYear", "Year", "number", submittedValue(submitted, "settings",
                "communicationYear", null, selected.bundle().getCommunicationYear()), " min=\"1\" max=\"9999\"");
        button(out, "Save Month/Year");
        out.println("</form>");
        for (var component : selected.components()) {
            boolean structured = component.getKind() == EsCommunicationBundleTemplateComponent.Kind.STRUCTURED_LIST;
            if (!structured && component.getKind() != EsCommunicationBundleTemplateComponent.Kind.TEXT) {
                continue;
            }
            var value = selected.componentValues().stream()
                    .filter(v -> v.getComponentId().equals(component.getComponentId())).findFirst().orElse(null);
            String content = value == null ? "" : structured
                    ? String.join("\n", CommunicationBundleStructuredList.items(value.getContentJson())) : value.getContentText();
            String action = structured ? "list" : "text";
            form(out, actionUrl, csrf, action, bundleId, false);
            hidden(out, "componentId", component.getComponentId());
            out.println("<label class=\"aira-label\" for=\"component-" + component.getComponentId() + "\">"
                    + escape(component.getDisplayName()) + (component.isRequired() ? " (required)" : " (optional)")
                    + "</label><p class=\"aira-meta\">" + escape(component.getAuthoringPrompt())
                    + (structured ? " Enter one item per line; line order is preserved." : "") + "</p>"
                    + "<textarea class=\"aira-textarea\" id=\"component-" + component.getComponentId()
                    + "\" name=\"content\" rows=\"5\" maxlength=\""
                    + ("title".equals(component.getSemanticKey()) ? "140" : "50000") + "\""
                    + (component.isRequired() ? " required" : "") + ">"
                    + escape(submittedValue(submitted, action, "content", "componentId", component.getComponentId(), content))
                    + "</textarea>");
            button(out, "Save " + component.getDisplayName());
            out.println("</form>");
        }
        resourceEditor(out, actionUrl, csrf, selected, available, submitted);
        out.println("<section class=\"aira-stack\"><h3>Add a Topic Resource</h3>"
                + "<p class=\"aira-meta\">Add a resource, then select it for a component above. Files must not contain"
                + " confidential information. Anyone with a file URL can read it.</p>");
        if (uploadProblem == null) {
            form(out, actionUrl, csrf, "upload", bundleId, true);
            input(out, "title", "File title", "text", submittedValue(submitted, "upload", "title", null, ""), " maxlength=\"255\" required");
            input(out, "resourceFile", "File (maximum 25 MiB)", "file", "",
                    " accept=\".png,.jpg,.jpeg,.webp,.gif,.pdf,.txt,.doc,.docx,.ppt,.pptx\" required");
            button(out, "Upload resource");
            out.println("</form>");
        } else {
            out.println("<p class=\"aira-alert aira-alert--info\">Uploads unavailable: " + escape(uploadProblem) + "</p>");
        }
        form(out, actionUrl, csrf, "link", bundleId, false);
        input(out, "title", "Link title", "text", submittedValue(submitted, "link", "title", null, ""), " maxlength=\"255\" required");
        input(out, "externalUrl", "External URL", "url", submittedValue(submitted, "link", "externalUrl", null, ""), " required");
        button(out, "Add external link");
        out.println("</form></section>");
        form(out, actionUrl, csrf, "publish", bundleId, false);
        out.println("<label class=\"aira-label\"><input type=\"checkbox\" name=\"preserveConfirmed\" value=\"true\" required>"
                + " I confirm publication and preservation: file URLs are public to anyone who has them;"
                + " selected uploaded files, including the teaser image, will become immutable, independently identified copies."
                + " Resource metadata, context and link URLs are frozen, but remote link contents are not frozen."
                + "</label>");
        button(out, "Publish Starter Packet");
        out.println("</form></section>");
    }

    private static void resourceEditor(PrintWriter out, String actionUrl, String csrf, OrientationResources packet,
            List<ResourceDetails> available, Map<String, String> submitted) {
        for (var component : packet.components()) {
            if (component.getKind() != EsCommunicationBundleTemplateComponent.Kind.RESOURCE
                    && component.getKind() != EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION) {
                continue;
            }
            out.println("<section class=\"aira-stack aira-stack--compact\"><h3>" + escape(component.getDisplayName())
                    + "</h3><p class=\"aira-meta\">" + escape(component.getAuthoringPrompt()) + "</p>");
            var selected = placements(packet, component);
            if (selected.isEmpty()) {
                out.println("<p class=\"aira-meta\">No resource selected.</p>");
            }
            for (int i = 0; i < selected.size(); i++) {
                var placement = selected.get(i);
                var details = resource(packet, placement);
                out.println("<h4>" + escape(details.resource().getTitle()) + "</h4>");
                placementAction(out, actionUrl, csrf, packet, placement, "remove", "Remove selection");
                if (component.getKind() == EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION) {
                    if (i > 0) {
                        placementAction(out, actionUrl, csrf, packet, placement, "moveUp", "Move up");
                    }
                    if (i < selected.size() - 1) {
                        placementAction(out, actionUrl, csrf, packet, placement, "moveDown", "Move down");
                    }
                }
                form(out, actionUrl, csrf, "context", packet.bundle().getBundleId(), false);
                hidden(out, "placementId", placement.getPlacementId());
                out.println("<label class=\"aira-label\" for=\"context-" + placement.getPlacementId() + "\">Context for "
                        + escape(details.resource().getTitle()) + "</label><textarea class=\"aira-textarea\" id=\"context-"
                        + placement.getPlacementId() + "\" name=\"contextNote\" rows=\"2\" maxlength=\"50000\">"
                        + escape(submittedValue(submitted, "context", "contextNote", "placementId", placement.getPlacementId(),
                                placement.getContextNote())) + "</textarea>");
                button(out, "Save resource context");
                out.println("</form>");
            }
            var choices = available.stream().filter(r -> !"teaser_image".equals(component.getSemanticKey())
                    || (r.file() != null && r.file().isImage())).toList();
            if (!choices.isEmpty()) {
                form(out, actionUrl, csrf, "select", packet.bundle().getBundleId(), false);
                hidden(out, "componentId", component.getComponentId());
                out.println("<label class=\"aira-label\" for=\"resource-" + component.getComponentId() + "\">Resource for "
                        + escape(component.getDisplayName()) + "</label><select class=\"aira-select\" id=\"resource-"
                        + component.getComponentId() + "\" name=\"resourceId\" required><option value=\"\">Choose a Topic Resource</option>");
                for (var choice : choices) {
                    out.println("<option value=\"" + choice.resource().getTopicResourceId() + "\">"
                            + escape(choice.resource().getTitle()) + "</option>");
                }
                out.println("</select>");
                button(out, component.getCardinality() == EsCommunicationBundleTemplateComponent.Cardinality.SINGLE
                        ? "Set resource" : "Add resource");
                out.println("</form>");
            } else {
                out.println("<p class=\"aira-meta\">"
                        + ("teaser_image".equals(component.getSemanticKey()) ? "Upload a raster image to use here." : "Add a Topic Resource to use here.")
                        + "</p>");
            }
            out.println("</section>");
        }
    }

    private static void placementAction(PrintWriter out, String url, String csrf, OrientationResources packet,
            EsCommunicationBundleResourcePlacement placement, String action, String label) {
        form(out, url, csrf, action, packet.bundle().getBundleId(), false);
        hidden(out, "placementId", placement.getPlacementId());
        button(out, label);
        out.println("</form>");
    }

    private static List<EsCommunicationBundleResourcePlacement> placements(OrientationResources packet,
            EsCommunicationBundleTemplateComponent component) {
        return packet.placements().stream().filter(p -> p.getComponentId().equals(component.getComponentId()))
                .sorted(Comparator.comparingInt(EsCommunicationBundleResourcePlacement::getDisplayOrder)).toList();
    }

    private static ResourceDetails resource(OrientationResources packet, EsCommunicationBundleResourcePlacement placement) {
        return packet.resources().stream().filter(r -> r.resource().getTopicResourceId().equals(placement.getTopicResourceId())
                && Objects.equals(r.preservedVersionId(), placement.getResourceVersionId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Selected packet resource is unavailable."));
    }

    static String title(OrientationResources packet) {
        String title = text(packet, "title");
        return title.isBlank() ? "Untitled Starter Packet" : title;
    }

    private static String text(OrientationResources packet, String key) {
        return packet.components().stream().filter(c -> key.equals(c.getSemanticKey()))
                .flatMap(c -> packet.componentValues().stream().filter(v -> c.getComponentId().equals(v.getComponentId())))
                .map(v -> v.getContentText() == null ? "" : v.getContentText()).findFirst().orElse("");
    }

    static String date(OrientationResources packet) {
        var bundle = packet.bundle();
        return bundle.getCommunicationMonth() == null || bundle.getCommunicationYear() == null ? "Month/Year not set"
                : YearMonth.of(bundle.getCommunicationYear(), bundle.getCommunicationMonth())
                        .format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.US));
    }

    private static String submittedValue(Map<String, String> submitted, String action, String name,
            String target, Object existing) {
        return submittedValue(submitted, action, name, target, null, existing);
    }

    private static String submittedValue(Map<String, String> submitted, String action, String name,
            String target, Object targetId, Object existing) {
        if (action.equals(submitted.get("action")) && (target == null || Objects.toString(targetId, "").equals(submitted.get(target)))
                && submitted.containsKey(name)) {
            return submitted.get(name);
        }
        return Objects.toString(existing, "");
    }

    private static void form(PrintWriter out, String url, String csrf, String action, Long bundleId, boolean multipart) {
        out.println("<form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\"" + escape(url) + "\""
                + (multipart ? " enctype=\"multipart/form-data\"" : "") + ">");
        hidden(out, "csrfToken", csrf);
        hidden(out, "action", action);
        if (bundleId != null) {
            hidden(out, "bundleId", bundleId);
        }
    }

    private static void hidden(PrintWriter out, String name, Object value) {
        out.println("<input type=\"hidden\" name=\"" + name + "\" value=\"" + escape(Objects.toString(value, "")) + "\">");
    }

    private static void input(PrintWriter out, String name, String label, String type, String value, String attributes) {
        String id = type + "-" + name + "-" + label.replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase(Locale.ROOT);
        out.println("<label class=\"aira-label\" for=\"" + id + "\">" + escape(label) + "</label>"
                + "<input class=\"aira-input\" id=\"" + id + "\" type=\"" + type + "\" name=\"" + name + "\""
                + ("file".equals(type) ? "" : " value=\"" + escape(value) + "\"") + attributes + ">");
    }

    private static void button(PrintWriter out, String label) {
        out.println("<button class=\"aira-button aira-button--secondary\" type=\"submit\">" + escape(label) + "</button>");
    }

    static String escape(String value) {
        return TopicOrientationResourceRenderer.escape(value);
    }
}
