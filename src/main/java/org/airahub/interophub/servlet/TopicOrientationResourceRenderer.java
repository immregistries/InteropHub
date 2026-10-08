package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsCommunicationBundlePurpose.Audience;
import org.airahub.interophub.model.EsCommunicationBundleResourcePlacement;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.model.EsTopicResource;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.CommunicationBundleService.ResourceDetails;
import org.airahub.interophub.service.StoredFileService;

final class TopicOrientationResourceRenderer {
    private TopicOrientationResourceRenderer() { }

    static void overview(PrintWriter out, String context, OrientationResources orientation) {
        List<ResourceDetails> selected = selectedResources(orientation);
        ResourceDetails infographic = infographic(orientation, selected);

        out.println("<div class=\"aira-stack aira-stack--compact\">");
        if (orientation != null && orientation.bundle().getStatus() == EsCommunicationBundle.Status.DRAFT) {
            out.println("<p class=\"aira-meta\">Draft preview - visible only to Topic stewards.</p>");
        }
        if (infographic == null) {
            out.println("<div class=\"aira-alert aira-alert--info\" role=\"status\">"
                    + "<p class=\"aira-alert__title\">No infographic selected</p>"
                    + "<p>The Topic Orientation does not currently include a primary infographic.</p>"
                    + "</div>");
        } else {
            String url = escape(StoredFileService.readUrl(context, infographic.file()));
            String title = escape(infographic.resource().getTitle());
            out.println("<figure class=\"aira-figure\"><a href=\"" + url
                    + "\" target=\"_blank\" rel=\"noopener noreferrer\" aria-label=\"View " + title
                    + " in a new tab\"><img src=\"" + url + "\" alt=\"" + title
                    + "\" style=\"display:block;max-width:100%;height:auto;\" /></a></figure>");
        }
        String date = communicationDate(orientation);
        if (date != null) {
            out.println("<p class=\"aira-meta\">" + (orientation.bundle().getStatus() == EsCommunicationBundle.Status.DRAFT
                    ? "Draft dated " : "Current as of ") + escape(date) + "</p>");
        }
        out.println("</div>");
    }

    static void narrative(PrintWriter out, OrientationResources orientation) {
        CommunicationBundleComponentRenderer.narrative(out, orientation);
    }

    private static List<ResourceDetails> selectedResources(OrientationResources orientation) {
        return orientation == null ? List.of() : orientation.placements().stream()
                .map(p -> orientation.resources().stream()
                        .filter(r -> r.resource().getTopicResourceId().equals(p.getTopicResourceId()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Selected resource is unavailable.")))
                .distinct().toList();
    }

    private static ResourceDetails infographic(OrientationResources orientation, List<ResourceDetails> selected) {
        return orientation == null ? null : orientation.components().stream()
                .filter(c -> "primary_infographic".equals(c.getSemanticKey()))
                .flatMap(c -> placements(orientation, c).stream())
                .flatMap(p -> selected.stream()
                        .filter(r -> r.resource().getTopicResourceId().equals(p.getTopicResourceId())))
                .filter(r -> r.file() != null && r.file().isImage()).findFirst().orElse(null);
    }

    static void supportingChips(PrintWriter out, String context, OrientationResources orientation) {
        List<ResourceDetails> selected = selectedResources(orientation);
        ResourceDetails infographic = infographic(orientation, selected);
        List<ResourceDetails> links = selected.stream().filter(r -> !r.equals(infographic)).toList();
        for (ResourceDetails resource : links) {
            String type = switch (resource.resource().getResourceType()) {
                case IMAGE -> "IMG";
                case PDF -> "PDF";
                case DOCUMENT -> "DOC";
                case PRESENTATION -> "PPT";
                case EXTERNAL_LINK -> "LINK";
            };
            String label = escape(resource.resource().getTitle() + " (" + type + ") - "
                    + (resource.file() != null && !StoredFileService.inline(resource.file())
                            ? "download" : "opens in a new tab"));
            String url = resource.file() == null ? resource.resource().getExternalUrl()
                    : StoredFileService.readUrl(context, resource.file());
            out.println("<a class=\"aira-chip\" href=\""
                    + escape(url) + "\" target=\"_blank\" rel=\"noopener noreferrer\" title=\"" + label
                    + "\" aria-label=\"" + label + "\""
                    + (resource.file() != null && !StoredFileService.inline(resource.file()) ? " download" : "")
                    + "><svg width=\"16\" height=\"18\" viewBox=\"0 0 28 32\" aria-hidden=\"true\""
                    + " focusable=\"false\" fill=\"none\" stroke=\"currentColor\">"
                    + "<path d=\"M4 1h13l7 7v23H4z M17 1v7h7\" />"
                    + "<text x=\"14\" y=\"23\" text-anchor=\"middle\" fill=\"currentColor\" stroke=\"none\""
                    + " font-family=\"sans-serif\" font-size=\"7\" font-weight=\"bold\">" + type
                    + "</text></svg> " + escape(resource.resource().getTitle()) + "</a>");
        }
    }

    static void editor(PrintWriter out, String context, Long topicId, String csrf,
            OrientationResources orientation, List<ResourceDetails> resources,
            StoredFileService storage, String uploadProblem) {
        if (orientation == null) {
            out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                    + "<h2 class=\"aira-section-card__title\">Topic Orientation</h2></div>"
                    + "<div class=\"aira-section-card__body aira-stack\">");
            out.println("<p>Create the draft to select resources for named Orientation roles.</p>");
            formStart(out, context, topicId, csrf, "createDraft", false);
            button(out, "Create Orientation draft");
            out.println("</form></div></section>");
        } else if (orientation.bundle().getStatus() == EsCommunicationBundle.Status.DRAFT) {
            renderEditableOrientation(out, context, topicId, csrf, orientation);
        } else if (orientation.bundle().getStatus() == EsCommunicationBundle.Status.PUBLISHED) {
            out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                    + "<h2 class=\"aira-section-card__title\">Published Topic Orientation</h2></div>"
                    + "<div class=\"aira-section-card__body aira-stack\"><p>Audience: "
                    + escape(audienceLabel(orientation.bundle().getAudience())) + "; "
                    + (communicationDate(orientation) == null ? "no Month/Year set."
                            : "dated " + escape(communicationDate(orientation)) + ".")
                    + "</p><p>This is a living Orientation. Each save updates the published Topic page immediately "
                    + "and is recorded in the change history. Retire it only when it is no longer in use.</p>");
            formStart(out, context, topicId, csrf, "retire", false);
            out.println("<button type=\"submit\" class=\"aira-button aira-button--tertiary\""
                    + " onclick=\"return confirm('Retire this published Orientation? It will no longer appear on the Topic page.');\">"
                    + "Retire Orientation</button></form></div></section>");
            renderEditableOrientation(out, context, topicId, csrf, orientation);
        }

        out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                + "<h2 class=\"aira-section-card__title\">Orientation roles</h2></div>"
                + "<div class=\"aira-section-card__body aira-stack\">");
        if (orientation != null) {
            boolean editable = orientation.bundle().getStatus() == EsCommunicationBundle.Status.DRAFT
                    || orientation.bundle().getStatus() == EsCommunicationBundle.Status.PUBLISHED;
            for (var component : resourceComponents(orientation)) {
                out.println("<section class=\"aira-stack aira-stack--compact\"><h3>"
                        + escape(component.getDisplayName()) + (component.isRequired() ? " (required)" : " (recommended)")
                        + "</h3><p class=\"aira-meta\">"
                        + escape(component.getAuthoringPrompt()) + "</p>");
                var selected = placements(orientation, component);
                for (var placement : selected) {
                    var resource = resources.stream()
                            .filter(r -> r.resource().getTopicResourceId().equals(placement.getTopicResourceId()))
                            .findFirst().orElseThrow(() -> new IllegalStateException("Selected resource is unavailable."));
                    out.println("<div class=\"aira-cluster\"><span>" + escape(resource.resource().getTitle())
                            + "</span>");
                    if (editable) {
                        formStart(out, context, topicId, csrf, "remove", false);
                        hidden(out, "placementId", placement.getPlacementId().toString());
                        out.println("<button class=\"aira-button aira-button--tertiary aira-button--small\""
                                + " aria-label=\"Remove " + escape(resource.resource().getTitle()) + " from "
                                + escape(component.getDisplayName()) + "\">Remove</button></form>");
                    }
                    out.println("</div>");
                    if (editable) {
                        if (component.getKind() == EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION) {
                            int index = selected.indexOf(placement);
                            if (index > 0) {
                                formStart(out, context, topicId, csrf, "moveUp", false);
                                hidden(out, "placementId", placement.getPlacementId().toString());
                                button(out, "Move " + resource.resource().getTitle() + " up");
                                out.println("</form>");
                            }
                            if (index < selected.size() - 1) {
                                formStart(out, context, topicId, csrf, "moveDown", false);
                                hidden(out, "placementId", placement.getPlacementId().toString());
                                button(out, "Move " + resource.resource().getTitle() + " down");
                                out.println("</form>");
                            }
                        }
                        formStart(out, context, topicId, csrf, "context", false);
                        hidden(out, "placementId", placement.getPlacementId().toString());
                        out.println("<label class=\"aira-label\" for=\"context-" + placement.getPlacementId()
                                + "\">Context for " + escape(resource.resource().getTitle()) + "</label>"
                                + "<textarea class=\"aira-textarea\" id=\"context-" + placement.getPlacementId()
                                + "\" name=\"contextNote\" rows=\"2\" maxlength=\"20000\">"
                                + escape(placement.getContextNote()) + "</textarea>");
                        button(out, "Save resource context");
                        out.println("</form>");
                    }
                }
                if (selected.isEmpty()) {
                    out.println("<p class=\"aira-meta\">No resource selected.</p>");
                }
                if (editable && !resources.isEmpty()) {
                    formStart(out, context, topicId, csrf, "select", false);
                    hidden(out, "componentId", component.getComponentId().toString());
                    out.println("<label class=\"aira-label\" for=\"role-" + component.getComponentId()
                            + "\">Resource for " + escape(component.getDisplayName()) + "</label>");
                    out.println("<select class=\"aira-select\" id=\"role-" + component.getComponentId()
                            + "\" name=\"resourceId\" required><option value=\"\">Choose a Topic Resource</option>");
                    for (var resource : resources) {
                        out.println("<option value=\"" + resource.resource().getTopicResourceId() + "\">"
                                + escape(resource.resource().getTitle()) + " ("
                                + resource.resource().getResourceType().name() + ")</option>");
                    }
                    out.println("</select>");
                    button(out, component.getCardinality() == EsCommunicationBundleTemplateComponent.Cardinality.SINGLE
                            ? "Set " + component.getDisplayName() : "Add to " + component.getDisplayName());
                    out.println("</form>");
                }
                out.println("</section>");
            }
        } else {
            out.println("<p>Create a draft before selecting resources for named Orientation roles.</p>");
        }
        out.println("</div></section>");

        if (orientation != null && !orientation.auditEntries().isEmpty()) {
            out.println("<details class=\"aira-section-card\"><summary>Recent Orientation changes</summary>"
                    + "<ul class=\"aira-section-card__body aira-stack\">");
            for (var entry : orientation.auditEntries()) {
                out.println("<li><strong>" + escape(entry.getEventType()) + "</strong>: "
                        + escape(entry.getDetails()) + " <span class=\"aira-meta\">User #"
                        + entry.getChangedByUserId() + ", "
                        + escape(entry.getChangedAt() == null ? "" : entry.getChangedAt().toString())
                        + " UTC</span></li>");
            }
            out.println("</ul></details>");
        }

        out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                + "<h2 class=\"aira-section-card__title\">Add a Topic Resource</h2></div>"
                + "<div class=\"aira-section-card__body aira-stack\">"
                + "<p>Resources belong to this Topic and can be reused in Orientation roles without uploading again."
                + " Uploads do not replace existing file bytes.</p>"
                + "<p class=\"aira-alert aira-alert--warning\">Draft visibility does not protect file URLs."
                + " Anyone with a file URL can read it. Do not upload confidential material.</p>");
        if (uploadProblem == null) {
            out.println("<h3>Upload a file</h3>");
            formStart(out, context, topicId, csrf, "upload", true);
            metadataFields(out, "upload", null);
            out.println("<label class=\"aira-label\" for=\"resource-file\">File (25 MiB maximum)</label>"
                    + "<input class=\"aira-input\" id=\"resource-file\" type=\"file\" name=\"resourceFile\" required"
                    + " accept=\".png,.jpg,.jpeg,.webp,.gif,.pdf,.txt,.doc,.docx,.ppt,.pptx\" />"
                    + "<p class=\"aira-meta\">Raster images, PDF, TXT, Word and PowerPoint."
                    + " Office files are downloads; document previews are deferred.</p>");
            button(out, "Upload Topic Resource");
            out.println("</form>");
        } else {
            out.println("<p class=\"aira-alert aira-alert--warning\">Uploads unavailable: " + escape(uploadProblem) + "</p>");
        }
        out.println("<h3>Add an external link</h3>");
        formStart(out, context, topicId, csrf, "link", false);
        metadataFields(out, "link", null);
        field(out, "link-url", "externalUrl", "HTTP(S) URL", "", 2000, true, "url");
        button(out, "Add external link");
        out.println("</form></div></section>");

        out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                + "<h2 class=\"aira-section-card__title\">Topic Resource library</h2></div>"
                + "<div class=\"aira-section-card__body aira-stack\">");
        if (resources.isEmpty()) {
            out.println("<p class=\"aira-meta\">No Topic Resources yet.</p>");
        }
        for (var resource : resources) {
            out.println("<details><summary>" + escape(resource.resource().getTitle()) + " ("
                    + resource.resource().getResourceType().name() + ")</summary>");
            renderResource(out, context, resource, false);
            if (resource.file() != null) {
                String replacementProblem = storage.writeProblem(resource.file());
                if (replacementProblem == null) {
                    formStart(out, context, topicId, csrf, "replaceResourceFile", true);
                    hidden(out, "resourceId", resource.resource().getTopicResourceId().toString());
                    out.println("<label class=\"aira-label\" for=\"replace-file-"
                            + resource.resource().getTopicResourceId() + "\">Replace current file (25 MiB maximum)</label>"
                            + "<input class=\"aira-input\" id=\"replace-file-" + resource.resource().getTopicResourceId()
                            + "\" type=\"file\" name=\"replacementFile\" required"
                            + " accept=\".png,.jpg,.jpeg,.webp,.gif,.pdf,.txt,.doc,.docx,.ppt,.pptx\" />");
                    button(out, "Replace current file");
                    out.println("</form><p class=\"aira-meta\">The Topic Resource, placements, and stable file URL remain; "
                            + "previous local bytes are not retained as a version.</p>");
                } else {
                    out.println("<p class=\"aira-alert aira-alert--warning\">File replacement unavailable: "
                            + escape(replacementProblem) + "</p>");
                }
            }
            formStart(out, context, topicId, csrf, "metadata", false);
            hidden(out, "resourceId", resource.resource().getTopicResourceId().toString());
            String prefix = "resource-" + resource.resource().getTopicResourceId();
            metadataFields(out, prefix, resource.resource());
            if (resource.resource().getResourceType() == EsTopicResource.ResourceType.EXTERNAL_LINK) {
                field(out, prefix + "-url", "externalUrl", "HTTP(S) URL",
                        resource.resource().getExternalUrl(), 2000, true, "url");
            }
            button(out, "Save resource metadata");
            out.println("</form></details>");
        }
        out.println("</div></section>");
    }

    private static void renderEditableOrientation(PrintWriter out, String context, Long topicId, String csrf,
            OrientationResources orientation) {
        boolean published = orientation.bundle().getStatus() == EsCommunicationBundle.Status.PUBLISHED;
        out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                + "<h2 class=\"aira-section-card__title\">Orientation narrative</h2></div>"
                + "<div class=\"aira-section-card__body aira-stack\">");
        CommunicationBundleComponentRenderer.valueEditor(out, context + "/es/topic-resources/" + topicId, csrf, orientation);
        out.println("</div></section>");

        out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                + "<h2 class=\"aira-section-card__title\">Audience and communication date</h2></div>"
                + "<div class=\"aira-section-card__body aira-stack\">");
        formStart(out, context, topicId, csrf, "settings", false);
        out.println("<label class=\"aira-label\" for=\"orientation-audience\">Audience</label>"
                + "<select class=\"aira-select\" id=\"orientation-audience\" name=\"audience\" required>");
        for (Audience audience : orientation.allowedAudiences()) {
            out.println("<option value=\"" + audience.name() + "\""
                    + (audience == orientation.bundle().getAudience() ? " selected" : "") + ">"
                    + escape(audienceLabel(audience)) + "</option>");
        }
        out.println("</select><label class=\"aira-label\" for=\"orientation-month\">Month</label>"
                + "<input class=\"aira-input\" id=\"orientation-month\" name=\"communicationMonth\" type=\"number\""
                + " min=\"1\" max=\"12\"" + (published ? " required" : "") + " value=\""
                + (orientation.bundle().getCommunicationMonth() == null ? ""
                        : orientation.bundle().getCommunicationMonth()) + "\" />"
                + "<label class=\"aira-label\" for=\"orientation-year\">Year</label>"
                + "<input class=\"aira-input\" id=\"orientation-year\" name=\"communicationYear\" type=\"number\""
                + " min=\"1\" max=\"9999\"" + (published ? " required" : "") + " value=\""
                + (orientation.bundle().getCommunicationYear() == null ? ""
                        : orientation.bundle().getCommunicationYear()) + "\" />");
        button(out, "Save audience and date");
        out.println("</form><p class=\"aira-meta\">Month and Year describe the communication, not its publication timestamp. "
                + "Both are required for a published Orientation. Update them for a meaningful refresh, "
                + "not a minor editorial correction.</p>");
        if (!published) {
            formStart(out, context, topicId, csrf, "publish", false);
            button(out, "Publish Orientation");
            out.println("</form>");
        }
        out.println("</div></section>");
    }

    private static void renderResource(PrintWriter out, String context, ResourceDetails details, boolean image) {
        var resource = details.resource();
        String url = resource.getStoredFileId() == null ? resource.getExternalUrl()
                : StoredFileService.readUrl(context, details.file());
        out.println("<article class=\"aira-stack aira-stack--compact\"><p><a class=\"aira-inline-link\" href=\""
                + escape(url) + "\""
                + (details.file() != null && !details.file().isImage() ? " download" : " rel=\"noopener noreferrer\"")
                + ">" + escape(resource.getTitle()) + "</a> <span class=\"aira-meta\">"
                + (details.file() == null ? "External link" : escape(details.file().getOriginalFilename()))
                + "</span></p>");
        if (image && details.file() != null && details.file().isImage()) {
            out.println("<a href=\"" + escape(url) + "\"><img src=\"" + escape(url) + "\" alt=\""
                    + escape(resource.getTitle()) + "\" loading=\"lazy\" style=\"max-width:100%;height:auto;\" /></a>");
        }
        if (resource.getDescription() != null) {
            out.println("<p style=\"white-space:pre-wrap;\">" + escape(resource.getDescription()) + "</p>");
        }
        if (resource.getAttribution() != null) {
            out.println("<p class=\"aira-meta\">Attribution: " + escape(resource.getAttribution()) + "</p>");
        }
        out.println("</article>");
    }

    private static List<EsCommunicationBundleTemplateComponent> resourceComponents(OrientationResources orientation) {
        return orientation.components().stream()
                .filter(c -> c.getKind() == EsCommunicationBundleTemplateComponent.Kind.RESOURCE
                        || c.getKind() == EsCommunicationBundleTemplateComponent.Kind.RESOURCE_COLLECTION).toList();
    }

    private static List<EsCommunicationBundleResourcePlacement> placements(OrientationResources orientation,
            EsCommunicationBundleTemplateComponent component) {
        return orientation.placements().stream().filter(p -> p.getComponentId().equals(component.getComponentId()))
                .toList();
    }

    private static String communicationDate(OrientationResources orientation) {
        if (orientation == null || orientation.bundle().getCommunicationMonth() == null
                || orientation.bundle().getCommunicationYear() == null) {
            return null;
        }
        return YearMonth.of(orientation.bundle().getCommunicationYear(),
                orientation.bundle().getCommunicationMonth())
                .format(DateTimeFormatter.ofPattern("MMMM uuuu", Locale.ENGLISH));
    }

    private static String audienceLabel(Audience audience) {
        return switch (audience) {
            case PUBLIC -> "Anyone who can view the Topic";
            case PARTICIPANTS -> "Topic Space participants";
            case STEWARDS -> "Topic stewards";
        };
    }

    private static void metadataFields(PrintWriter out, String prefix, EsTopicResource resource) {
        field(out, prefix + "-title", "title", "Title", resource == null ? "" : resource.getTitle(), 255, true, "text");
        out.println("<label class=\"aira-label\" for=\"" + prefix + "-description\">Description</label>"
                + "<textarea class=\"aira-textarea\" id=\"" + prefix
                + "-description\" name=\"description\" rows=\"3\" maxlength=\"20000\">"
                + escape(resource == null ? "" : resource.getDescription()) + "</textarea>");
        field(out, prefix + "-attribution", "attribution", "Attribution",
                resource == null ? "" : resource.getAttribution(), 500, false, "text");
    }

    private static void field(PrintWriter out, String id, String name, String label, String value,
            int max, boolean required, String type) {
        out.println("<label class=\"aira-label\" for=\"" + id + "\">" + label + "</label>"
                + "<input class=\"aira-input\" type=\"" + type + "\" id=\"" + id + "\" name=\"" + name
                + "\" maxlength=\"" + max + "\" value=\"" + escape(value) + "\"" + (required ? " required" : "") + " />");
    }

    private static void formStart(PrintWriter out, String context, Long topicId, String csrf,
            String action, boolean multipart) {
        out.println("<form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                + editorUrl(context, topicId) + "\"" + (multipart ? " enctype=\"multipart/form-data\"" : "") + ">");
        hidden(out, "csrfToken", csrf);
        hidden(out, "action", action);
    }

    private static void hidden(PrintWriter out, String name, String value) {
        out.println("<input type=\"hidden\" name=\"" + name + "\" value=\"" + escape(value) + "\" />");
    }

    private static void button(PrintWriter out, String label) {
        out.println("<button type=\"submit\" class=\"aira-button aira-button--secondary\">" + escape(label) + "</button>");
    }

    static String editorUrl(String context, Long topicId) {
        return escape(context + "/es/topic-resources/" + topicId);
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
