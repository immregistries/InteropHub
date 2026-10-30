package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsCommunicationBundleComponentValue;
import org.airahub.interophub.model.EsCommunicationBundlePurpose.Audience;
import org.airahub.interophub.model.EsCommunicationBundleResourcePlacement;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent.Cardinality;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent.Kind;
import org.airahub.interophub.model.EsTopicResource;
import org.airahub.interophub.service.CommunicationBundleService;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.CommunicationBundleService.ResourceDetails;
import org.airahub.interophub.service.CommunicationBundleStructuredList;
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

    static void editor(PrintWriter out, String context, Long topicId, String topicName, String csrf,
            OrientationResources orientation, List<ResourceDetails> resources,
            StoredFileService storage, String uploadProblem, FormInput input) {
        if (input == null) {
            input = FormInput.none();
        }
        out.println("<script>document.documentElement.setAttribute(\"data-topic-resources\",\"js\");</script>");
        boolean editable = orientation != null && (orientation.bundle().getStatus() == EsCommunicationBundle.Status.DRAFT
                || orientation.bundle().getStatus() == EsCommunicationBundle.Status.PUBLISHED);
        header(out, context, topicId, topicName, csrf, orientation, editable);
        if (orientation == null) {
            out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                    + "<h2 class=\"aira-section-card__title\">Topic Orientation</h2></div>"
                    + "<div class=\"aira-section-card__body aira-stack\">");
            out.println("<p>Create the draft to write the Orientation and assign resources to its roles.</p>");
            formStart(out, context, topicId, csrf, "createDraft", false);
            button(out, "Create Orientation draft");
            out.println("</form></div></section>");
        } else {
            narrativeCard(out, context, topicId, csrf, orientation, editable, input);
        }
        rolesCard(out, context, topicId, csrf, orientation, resources, editable);
        libraryCard(out, resources, orientation);
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
        if (editable) {
            settingsDialog(out, context, topicId, csrf, orientation, input);
            for (var component : resourceComponents(orientation)) {
                addResourceDialog(out, context, topicId, csrf, component,
                        !placements(orientation, component).isEmpty(), resources, uploadProblem, input);
                for (var placement : placements(orientation, component)) {
                    var resource = libraryResource(resources, placement.getTopicResourceId());
                    contextDialog(out, context, topicId, csrf, component, placement, resource, input);
                }
            }
        }
        addResourceDialog(out, context, topicId, csrf, null, false, resources, uploadProblem, input);
        for (var resource : resources) {
            editResourceDialog(out, context, topicId, csrf, resource, storage, input);
        }
    }

    private static void header(PrintWriter out, String context, Long topicId, String topicName, String csrf,
            OrientationResources orientation, boolean editable) {
        out.println("<header class=\"aira-page-header\"><div>");
        out.println("<div class=\"aira-cluster\"><h1 class=\"aira-page-title\">Resources for "
                + escape(topicName) + "</h1>");
        if (orientation != null) {
            out.println(statusBadge(orientation.bundle().getStatus()));
        }
        out.println("</div>");
        if (orientation == null) {
            out.println("<p class=\"aira-meta\">Only Topic stewards can edit Orientation content.</p>");
        } else {
            String date = communicationDate(orientation);
            out.println("<p class=\"aira-meta\">Audience: "
                    + escape(audienceLabel(orientation.bundle().getAudience()))
                    + " · " + (date == null ? "Not dated yet" : "Current as of " + escape(date)) + "</p>");
            out.println("<p class=\"aira-meta\">" + (orientation.bundle().getStatus() == EsCommunicationBundle.Status.PUBLISHED
                    ? "Edits update the published Topic page immediately."
                    : orientation.bundle().getStatus() == EsCommunicationBundle.Status.DRAFT
                            ? "Draft preview is visible only to Topic stewards."
                            : "This Orientation is retired. Its history is kept.") + "</p>");
        }
        out.println("</div><div class=\"aira-cluster\">");
        if (orientation != null && orientation.bundle().getStatus() == EsCommunicationBundle.Status.DRAFT) {
            formStart(out, context, topicId, csrf, "publish", false);
            out.println("<button type=\"submit\" class=\"aira-button aira-button--primary\">Publish</button></form>");
        }
        out.println("<a class=\"aira-button aira-button--secondary\" href=\"" + escape(context) + "/es/topic/"
                + topicId + "\">View Topic page</a>");
        if (editable) {
            out.println("<button type=\"button\" class=\"aira-button aira-button--tertiary aira-button--small\""
                    + " data-dialog-open=\"orientation-settings\">Settings</button>");
        }
        out.println("</div></header>");
    }

    private static void narrativeCard(PrintWriter out, String context, Long topicId, String csrf,
            OrientationResources orientation, boolean editable, FormInput input) {
        List<EsCommunicationBundleTemplateComponent> components = orientation.components().stream()
                .filter(component -> component.getKind() == Kind.TEXT || component.getKind() == Kind.STRUCTURED_LIST)
                .toList();
        if (components.isEmpty()) {
            return;
        }
        boolean replaying = input.failed("narrative");
        out.println("<section class=\"aira-section-card\" id=\"orientation-narrative\">"
                + "<div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Orientation narrative</h2></div>"
                + "<div class=\"aira-section-card__body aira-stack\">");
        if (!editable) {
            for (var component : components) {
                readNarrative(out, component, narrativeText(orientation, component), false);
            }
            out.println("</div></section>");
            return;
        }
        formStart(out, context, topicId, csrf, "narrative", false);
        if (replaying) {
            out.println("<div class=\"is-editing\" data-narrative-form>");
        } else {
            out.println("<div data-narrative-form>");
        }
        hidden(out, "bundleId", orientation.bundle().getBundleId().toString());
        InitiativePageRenderer.formError(out, input, "narrative");
        for (var component : components) {
            String stored = narrativeText(orientation, component);
            String value = replaying ? input.posted("content-" + component.getComponentId()) : stored;
            boolean structured = component.getKind() == Kind.STRUCTURED_LIST;
            out.println("<section id=\"narrative-" + component.getComponentId() + "\" data-narrative-section"
                    + (replaying ? " class=\"is-editing\"" : "") + ">");
            out.println("<div data-narrative-read>");
            readNarrative(out, component, stored, true);
            out.println("<button type=\"button\" class=\"aira-button aira-button--tertiary aira-button--small\""
                    + " data-narrative-edit>Edit</button></div>");
            out.println("<div data-narrative-editor>");
            out.println("<label class=\"aira-label\" for=\"component-" + component.getComponentId() + "\">"
                    + escape(component.getDisplayName()) + "</label>");
            out.println("<p class=\"aira-meta\">" + escape(component.getAuthoringPrompt())
                    + (structured ? " Enter one item per line." : "") + "</p>");
            out.println("<textarea class=\"aira-textarea\" id=\"component-" + component.getComponentId()
                    + "\" name=\"content-" + component.getComponentId() + "\" rows=\"5\" maxlength=\"50000\""
                    + " data-watch data-original=\"" + attribute(stored) + "\">" + escape(value) + "</textarea>");
            out.println("</div></section>");
        }
        out.println("<div data-narrative-actions class=\"aira-cluster\">"
                + "<button type=\"submit\" class=\"aira-button aira-button--primary\">Save narrative</button>"
                + "<button type=\"button\" class=\"aira-button aira-button--ghost\" data-narrative-cancel>Cancel</button>"
                + "</div></div></form></div></section>");
    }

    private static void readNarrative(PrintWriter out, EsCommunicationBundleTemplateComponent component,
            String text, boolean editNearby) {
        boolean structured = component.getKind() == Kind.STRUCTURED_LIST;
        out.println("<h3>" + escape(component.getDisplayName())
                + (component.isRequired() ? " (required)" : " (recommended)") + "</h3>");
        if (text == null || text.isBlank()) {
            out.println("<p class=\"aira-meta\">Not written yet</p>");
            if (component.getAuthoringPrompt() != null && !component.getAuthoringPrompt().isBlank()) {
                out.println("<p class=\"aira-meta\">" + escape(component.getAuthoringPrompt()) + "</p>");
            }
            return;
        }
        if (structured) {
            out.println("<ul>");
            for (String item : text.lines().map(String::trim).filter(line -> !line.isEmpty()).toList()) {
                out.println("<li>" + escape(item) + "</li>");
            }
            out.println("</ul>");
        } else {
            out.println("<p style=\"white-space:pre-wrap;\">" + escape(text) + "</p>");
        }
        if (!editNearby && component.getAuthoringPrompt() != null) {
            return;
        }
    }

    private static void rolesCard(PrintWriter out, String context, Long topicId, String csrf,
            OrientationResources orientation, List<ResourceDetails> resources, boolean editable) {
        out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                + "<h2 class=\"aira-section-card__title\">Orientation roles</h2></div>"
                + "<div class=\"aira-section-card__body aira-stack\">");
        if (orientation == null) {
            out.println("<p>Create a draft before selecting resources for named Orientation roles.</p></div></section>");
            return;
        }
        for (var component : resourceComponents(orientation)) {
            var selected = placements(orientation, component);
            boolean single = component.getCardinality() == Cardinality.SINGLE;
            out.println("<section class=\"aira-stack aira-stack--compact\" id=\"role-" + component.getComponentId() + "\">");
            out.println("<div class=\"aira-cluster aira-cluster--between\"><h3>"
                    + escape(component.getDisplayName()) + (component.isRequired() ? " (required)" : " (recommended)")
                    + "</h3>");
            if (editable) {
                String label = single ? (selected.isEmpty() ? "Set" : "Replace") : "Add resource";
                out.println("<button type=\"button\" class=\"aira-button aira-button--secondary aira-button--small\""
                        + " data-dialog-open=\"add-resource-" + component.getComponentId()
                        + "\" data-add-tab=\"library\">" + label + "</button>");
            }
            out.println("</div>");
            if (component.getAuthoringPrompt() != null && !component.getAuthoringPrompt().isBlank()) {
                out.println("<p class=\"aira-meta\">" + escape(component.getAuthoringPrompt()) + "</p>");
            }
            if (selected.isEmpty()) {
                out.println("<p class=\"aira-meta\">No resource selected.</p>");
            }
            for (var placement : selected) {
                var resource = libraryResource(resources, placement.getTopicResourceId());
                String title = resource.resource().getTitle();
                out.println("<div class=\"aira-stack aira-stack--compact\"><div class=\"aira-cluster aira-cluster--between\"><div class=\"aira-cluster\">");
                out.println("<span>" + escape(title) + "</span><span class=\"aira-badge aira-badge--subtle\">"
                        + escape(resource.resource().getResourceType().name()) + "</span></div>");
                if (editable) {
                    out.println("<div class=\"aira-cluster\">");
                    if (component.getKind() == Kind.RESOURCE_COLLECTION) {
                        int index = selected.indexOf(placement);
                        if (index > 0) {
                            moveForm(out, context, topicId, csrf, "moveUp", placement, component, "Move up",
                                    "Move " + title + " up");
                        }
                        if (index < selected.size() - 1) {
                            moveForm(out, context, topicId, csrf, "moveDown", placement, component, "Move down",
                                    "Move " + title + " down");
                        }
                    }
                    String contextLabel = placement.getContextNote() == null || placement.getContextNote().isBlank()
                            ? "Add context" : "Edit context";
                    out.println("<button type=\"button\" class=\"aira-button aira-button--tertiary aira-button--small\""
                            + " data-dialog-open=\"context-" + placement.getPlacementId() + "\">"
                            + contextLabel + "</button>");
                    formStart(out, context, topicId, csrf, "remove", false);
                    hidden(out, "placementId", placement.getPlacementId().toString());
                    hidden(out, "componentId", component.getComponentId().toString());
                    out.println("<button class=\"aira-button aira-button--tertiary aira-button--small\" aria-label=\"Remove "
                            + escape(title) + " from " + escape(component.getDisplayName()) + "\">Remove</button></form>");
                    out.println("</div>");
                }
                out.println("</div>");
                if (placement.getContextNote() != null && !placement.getContextNote().isBlank()) {
                    out.println("<p class=\"aira-meta\">" + escape(placement.getContextNote()) + "</p>");
                }
                out.println("</div>");
            }
            out.println("</section>");
        }
        out.println("</div></section>");
    }

    private static void libraryCard(PrintWriter out, List<ResourceDetails> resources, OrientationResources orientation) {
        out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                + "<h2 class=\"aira-section-card__title\">Topic Resource library</h2>"
                + "<div class=\"aira-cluster\">"
                + "<button type=\"button\" class=\"aira-button aira-button--secondary aira-button--small\""
                + " data-dialog-open=\"library-add\" data-add-tab=\"upload\">Upload file</button>"
                + "<button type=\"button\" class=\"aira-button aira-button--secondary aira-button--small\""
                + " data-dialog-open=\"library-add\" data-add-tab=\"link\">Add link</button>"
                + "</div></div><div class=\"aira-section-card__body aira-stack\">");
        if (resources.isEmpty()) {
            out.println("<p class=\"aira-meta\">No Topic Resources yet.</p>");
        }
        for (var resource : resources) {
            String used = usedIn(orientation, resource.resource().getTopicResourceId());
            out.println("<div class=\"aira-cluster aira-cluster--between\"><div class=\"aira-stack aira-stack--compact\">");
            out.println("<div class=\"aira-cluster\"><span>" + escape(resource.resource().getTitle())
                    + "</span><span class=\"aira-badge aira-badge--subtle\">"
                    + escape(resource.resource().getResourceType().name()) + "</span><span class=\"aira-meta\">"
                    + escape(locator(resource)) + "</span></div>");
            if (!used.isBlank()) {
                out.println("<p class=\"aira-meta\">Used in: " + escape(used) + "</p>");
            }
            out.println("</div><button type=\"button\" class=\"aira-button aira-button--tertiary aira-button--small\""
                    + " data-dialog-open=\"edit-resource-" + resource.resource().getTopicResourceId()
                    + "\">Edit</button></div>");
        }
        out.println("</div></section>");
    }

    private static void settingsDialog(PrintWriter out, String context, Long topicId, String csrf,
            OrientationResources orientation, FormInput input) {
        String id = "orientation-settings";
        InitiativePageRenderer.dialogStart(out, id, "Orientation settings", input);
        out.println("<h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">Orientation settings</h3>");
        formStart(out, context, topicId, csrf, "settings", false);
        InitiativePageRenderer.formError(out, input, "settings");
        String audience = input.value("settings", "audience",
                orientation.bundle().getAudience() == null ? "" : orientation.bundle().getAudience().name());
        out.println("<label class=\"aira-label\" for=\"orientation-audience\">Audience</label>"
                + "<select class=\"aira-select\" id=\"orientation-audience\" name=\"audience\" required>");
        for (Audience option : orientation.allowedAudiences()) {
            out.println("<option value=\"" + option.name() + "\"" + (option.name().equals(audience) ? " selected" : "")
                    + ">" + escape(audienceLabel(option)) + "</option>");
        }
        out.println("</select>");
        InitiativePageRenderer.dialogActions(out, id, "Save audience");
        out.println("</form>");
        out.println("<p class=\"aira-meta\">Month and Year are the communication date, not the publication timestamp. "
                + "Publishing sets them when they are blank. Mark as refreshed this month after a meaningful update, "
                + "not a minor correction.</p>");
        formStart(out, context, topicId, csrf, "refreshDate", false);
        InitiativePageRenderer.formError(out, input, "refreshDate");
        out.println("<button type=\"submit\" class=\"aira-button aira-button--secondary\">Mark as refreshed this month</button></form>");
        if (orientation.bundle().getStatus() == EsCommunicationBundle.Status.PUBLISHED) {
            formStart(out, context, topicId, csrf, "retire", false);
            out.println("<button type=\"submit\" class=\"aira-button aira-button--tertiary\""
                    + " onclick=\"return confirm('Retire this published Orientation? It will no longer appear on the Topic page.');\">"
                    + "Retire Orientation</button></form>");
        }
        out.println("<div class=\"aira-dialog__actions\"><button class=\"aira-button aira-button--ghost\" type=\"button\""
                + " data-dialog-close=\"" + id + "\">Close</button></div></dialog>");
    }

    private static void contextDialog(PrintWriter out, String context, Long topicId, String csrf,
            EsCommunicationBundleTemplateComponent component, EsCommunicationBundleResourcePlacement placement,
            ResourceDetails resource, FormInput input) {
        String id = "context-" + placement.getPlacementId();
        InitiativePageRenderer.dialogStart(out, id, "Context", input);
        formStart(out, context, topicId, csrf, "context", false);
        out.println("<h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">Context for "
                + escape(resource.resource().getTitle()) + "</h3>");
        InitiativePageRenderer.formError(out, input, "context");
        hidden(out, "placementId", placement.getPlacementId().toString());
        hidden(out, "componentId", component.getComponentId().toString());
        String note = replaying(input, id, "context") ? input.posted("contextNote") : placement.getContextNote();
        out.println("<label class=\"aira-label\" for=\"" + id + "-note\">Why this belongs in "
                + escape(component.getDisplayName()) + "</label>");
        out.println("<textarea class=\"aira-textarea\" id=\"" + id + "-note\" name=\"contextNote\" rows=\"3\""
                + " maxlength=\"20000\" data-watch data-original=\"" + attribute(placement.getContextNote()) + "\">"
                + escape(note) + "</textarea>");
        InitiativePageRenderer.dialogActions(out, id, "Save context");
        out.println("</form></dialog>");
    }

    private static void addResourceDialog(PrintWriter out, String context, Long topicId, String csrf,
            EsCommunicationBundleTemplateComponent component, boolean occupied, List<ResourceDetails> resources,
            String uploadProblem, FormInput input) {
        boolean library = component == null;
        String id = library ? "library-add" : "add-resource-" + component.getComponentId();
        String title = library ? "Add a Topic Resource" : addTitle(component, occupied);
        InitiativePageRenderer.dialogStart(out, id, title, input);
        out.println("<h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">" + escape(title) + "</h3>");
        String selected = selectedAddTab(input, id, library);
        out.println("<div class=\"aira-segmented-control\" role=\"tablist\">");
        if (!library) {
            tabButton(out, "library", "From library", selected);
        }
        tabButton(out, "upload", "Upload file", selected);
        tabButton(out, "link", "External link", selected);
        out.println("</div>");
        if (!library) {
            out.println("<div data-add-panel=\"library\"" + (selected.equals("library") ? " class=\"is-selected\"" : "") + ">");
            List<ResourceDetails> offered = resources.stream()
                    .filter(resource -> CommunicationBundleService.resourceFitsRole(component.getSemanticKey(),
                            resource.resource().getResourceType()))
                    .toList();
            if (offered.isEmpty()) {
                out.println("<p class=\"aira-meta\">No matching Topic Resources in the library yet.</p>");
            } else {
                formStart(out, context, topicId, csrf, "select", false);
                InitiativePageRenderer.formError(out, input.failed("select") && id.equals(input.openDialog()) ? input : FormInput.none(), "select");
                hidden(out, "componentId", component.getComponentId().toString());
                String chosen = replaying(input, id, "select") ? input.posted("resourceId") : "";
                out.println("<label class=\"aira-label\" for=\"" + id + "-library\">Topic Resource</label>");
                out.println("<select class=\"aira-select\" id=\"" + id + "-library\" name=\"resourceId\" required>"
                        + "<option value=\"\">Choose a Topic Resource</option>");
                for (var resource : offered) {
                    String value = resource.resource().getTopicResourceId().toString();
                    out.println("<option value=\"" + value + "\"" + (value.equals(chosen) ? " selected" : "") + ">"
                            + escape(resource.resource().getTitle()) + " ("
                            + resource.resource().getResourceType().name() + ")</option>");
                }
                out.println("</select>");
                InitiativePageRenderer.dialogActions(out, id, addLabel(component, occupied));
                out.println("</form>");
            }
            out.println("</div>");
        }
        uploadPanel(out, context, topicId, csrf, id, component, uploadProblem, input, selected);
        linkPanel(out, context, topicId, csrf, id, component, input, selected);
        out.println("</dialog>");
    }

    private static void uploadPanel(PrintWriter out, String context, Long topicId, String csrf, String dialogId,
            EsCommunicationBundleTemplateComponent component, String uploadProblem, FormInput input, String selected) {
        boolean replay = replaying(input, dialogId, "upload");
        out.println("<div data-add-panel=\"upload\"" + (selected.equals("upload") ? " class=\"is-selected\"" : "") + ">");
        if (uploadProblem != null) {
            out.println("<p class=\"aira-alert aira-alert--warning\">Uploads unavailable: " + escape(uploadProblem) + "</p>");
        } else {
            formStart(out, context, topicId, csrf, "upload", true);
            if (replay) {
                InitiativePageRenderer.formError(out, input, "upload");
            }
            if (component != null) {
                hidden(out, "componentId", component.getComponentId().toString());
            }
            field(out, dialogId + "-upload-title", "title", "Title", replay ? input.posted("title") : "", 255, true, "text");
            out.println("<label class=\"aira-label\" for=\"" + dialogId + "-upload-description\">Description</label>"
                    + "<textarea class=\"aira-textarea\" id=\"" + dialogId + "-upload-description\" name=\"description\""
                    + " rows=\"3\" maxlength=\"20000\">" + escape(replay ? input.posted("description") : "") + "</textarea>");
            field(out, dialogId + "-upload-attribution", "attribution", "Attribution",
                    replay ? input.posted("attribution") : "", 500, false, "text");
            out.println("<label class=\"aira-label\" for=\"" + dialogId + "-file\">File (25 MiB maximum)</label>"
                    + "<input class=\"aira-input\" id=\"" + dialogId + "-file\" type=\"file\" name=\"resourceFile\" required"
                    + " accept=\".png,.jpg,.jpeg,.webp,.gif,.pdf,.txt,.doc,.docx,.ppt,.pptx\" />"
                    + "<p class=\"aira-meta\">Raster images, PDF, TXT, Word and PowerPoint."
                    + " Office files are downloads; document previews are deferred.</p>");
            out.println("<p class=\"aira-alert aira-alert--warning\">Draft visibility does not protect file URLs."
                    + " Anyone with a file URL can read it. Do not upload confidential material.</p>");
            InitiativePageRenderer.dialogActions(out, dialogId, "Upload file");
            out.println("</form>");
        }
        out.println("</div>");
    }

    private static void linkPanel(PrintWriter out, String context, Long topicId, String csrf, String dialogId,
            EsCommunicationBundleTemplateComponent component, FormInput input, String selected) {
        boolean replay = replaying(input, dialogId, "link");
        out.println("<div data-add-panel=\"link\"" + (selected.equals("link") ? " class=\"is-selected\"" : "") + ">");
        formStart(out, context, topicId, csrf, "link", false);
        if (replay) {
            InitiativePageRenderer.formError(out, input, "link");
        }
        if (component != null) {
            hidden(out, "componentId", component.getComponentId().toString());
        }
        field(out, dialogId + "-link-title", "title", "Title", replay ? input.posted("title") : "", 255, true, "text");
        out.println("<label class=\"aira-label\" for=\"" + dialogId + "-link-description\">Description</label>"
                + "<textarea class=\"aira-textarea\" id=\"" + dialogId + "-link-description\" name=\"description\""
                + " rows=\"3\" maxlength=\"20000\">" + escape(replay ? input.posted("description") : "") + "</textarea>");
        field(out, dialogId + "-link-attribution", "attribution", "Attribution",
                replay ? input.posted("attribution") : "", 500, false, "text");
        field(out, dialogId + "-link-url", "externalUrl", "HTTP(S) URL",
                replay ? input.posted("externalUrl") : "", 2000, true, "url");
        InitiativePageRenderer.dialogActions(out, dialogId, "Add external link");
        out.println("</form></div>");
    }

    private static void editResourceDialog(PrintWriter out, String context, Long topicId, String csrf,
            ResourceDetails details, StoredFileService storage, FormInput input) {
        var resource = details.resource();
        String id = "edit-resource-" + resource.getTopicResourceId();
        InitiativePageRenderer.dialogStart(out, id, "Edit resource", input);
        out.println("<h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">Edit "
                + escape(resource.getTitle()) + "</h3>");
        boolean replay = replaying(input, id, "metadata");
        formStart(out, context, topicId, csrf, "metadata", false);
        if (replay) {
            InitiativePageRenderer.formError(out, input, "metadata");
        }
        hidden(out, "resourceId", resource.getTopicResourceId().toString());
        field(out, id + "-title", "title", "Title", replay ? input.posted("title") : resource.getTitle(), 255, true, "text");
        out.println("<label class=\"aira-label\" for=\"" + id + "-description\">Description</label>"
                + "<textarea class=\"aira-textarea\" id=\"" + id + "-description\" name=\"description\" rows=\"3\""
                + " maxlength=\"20000\">" + escape(replay ? input.posted("description") : resource.getDescription())
                + "</textarea>");
        field(out, id + "-attribution", "attribution", "Attribution",
                replay ? input.posted("attribution") : resource.getAttribution(), 500, false, "text");
        if (resource.getResourceType() == EsTopicResource.ResourceType.EXTERNAL_LINK) {
            field(out, id + "-url", "externalUrl", "HTTP(S) URL",
                    replay ? input.posted("externalUrl") : resource.getExternalUrl(), 2000, true, "url");
        }
        InitiativePageRenderer.dialogActions(out, id, "Save resource metadata");
        out.println("</form>");
        if (details.file() != null) {
            String replacementProblem = storage.writeProblem(details.file());
            if (replacementProblem == null) {
                formStart(out, context, topicId, csrf, "replaceResourceFile", true);
                if (replaying(input, id, "replaceResourceFile")) {
                    InitiativePageRenderer.formError(out, input, "replaceResourceFile");
                }
                hidden(out, "resourceId", resource.getTopicResourceId().toString());
                out.println("<label class=\"aira-label\" for=\"replace-file-" + resource.getTopicResourceId()
                        + "\">Replace current file (25 MiB maximum)</label>"
                        + "<input class=\"aira-input\" id=\"replace-file-" + resource.getTopicResourceId()
                        + "\" type=\"file\" name=\"replacementFile\" required"
                        + " accept=\".png,.jpg,.jpeg,.webp,.gif,.pdf,.txt,.doc,.docx,.ppt,.pptx\" />"
                        + "<p class=\"aira-meta\">The Topic Resource, placements, and stable file URL remain; "
                        + "previous local bytes are not retained as a version.</p>");
                out.println("<button type=\"submit\" class=\"aira-button aira-button--secondary\">Replace current file</button></form>");
            } else {
                out.println("<p class=\"aira-alert aira-alert--warning\">File replacement unavailable: "
                        + escape(replacementProblem) + "</p>");
            }
        }
        out.println("</dialog>");
    }

    private static void moveForm(PrintWriter out, String context, Long topicId, String csrf, String action,
            EsCommunicationBundleResourcePlacement placement, EsCommunicationBundleTemplateComponent component,
            String label, String aria) {
        formStart(out, context, topicId, csrf, action, false);
        hidden(out, "placementId", placement.getPlacementId().toString());
        hidden(out, "componentId", component.getComponentId().toString());
        out.println("<button class=\"aira-button aira-button--tertiary aira-button--small\" aria-label=\""
                + escape(aria) + "\">" + escape(label) + "</button></form>");
    }

    private static void tabButton(PrintWriter out, String tab, String label, String selected) {
        out.println("<button type=\"button\" class=\"aira-segment\" data-add-tab=\"" + tab + "\" aria-selected=\""
                + tab.equals(selected) + "\">" + escape(label) + "</button>");
    }

    private static String selectedAddTab(FormInput input, String dialogId, boolean library) {
        if (!dialogId.equals(input.openDialog())) {
            return library ? "upload" : "library";
        }
        if (input.failed("upload")) {
            return "upload";
        }
        if (input.failed("link")) {
            return "link";
        }
        return library ? "upload" : "library";
    }

    private static String addLabel(EsCommunicationBundleTemplateComponent component, boolean occupied) {
        if (component.getCardinality() == Cardinality.SINGLE) {
            return occupied ? "Replace" : "Set";
        }
        return "Add to " + component.getDisplayName();
    }

    private static String addTitle(EsCommunicationBundleTemplateComponent component, boolean occupied) {
        if (component.getCardinality() == Cardinality.SINGLE) {
            return (occupied ? "Replace " : "Set ") + component.getDisplayName();
        }
        return "Add to " + component.getDisplayName();
    }

    private static String narrativeText(OrientationResources orientation, EsCommunicationBundleTemplateComponent component) {
        EsCommunicationBundleComponentValue value = orientation.componentValues().stream()
                .filter(candidate -> candidate.getComponentId().equals(component.getComponentId()))
                .findFirst().orElse(null);
        if (value == null) {
            return "";
        }
        if (component.getKind() == Kind.STRUCTURED_LIST) {
            return String.join("\n", CommunicationBundleStructuredList.items(value.getContentJson()));
        }
        return value.getContentText() == null ? "" : value.getContentText();
    }

    private static String usedIn(OrientationResources orientation, Long resourceId) {
        if (orientation == null) {
            return "";
        }
        return orientation.placements().stream()
                .filter(placement -> resourceId.equals(placement.getTopicResourceId()))
                .map(placement -> orientation.components().stream()
                        .filter(component -> component.getComponentId().equals(placement.getComponentId()))
                        .map(EsCommunicationBundleTemplateComponent::getDisplayName)
                        .findFirst().orElse(""))
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .reduce((left, right) -> left + ", " + right).orElse("");
    }

    private static String locator(ResourceDetails details) {
        if (details.file() != null) {
            return details.file().getOriginalFilename() == null ? "" : details.file().getOriginalFilename();
        }
        String url = details.resource().getExternalUrl();
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = new URI(url).getHost();
            return host == null || host.isBlank() ? url : host;
        } catch (URISyntaxException ex) {
            return url;
        }
    }

    private static ResourceDetails libraryResource(List<ResourceDetails> resources, Long resourceId) {
        return resources.stream().filter(resource -> resource.resource().getTopicResourceId().equals(resourceId))
                .findFirst().orElseThrow(() -> new IllegalStateException("Selected resource is unavailable."));
    }

    private static boolean replaying(FormInput input, String dialogId, String action) {
        return dialogId.equals(input.openDialog()) && input.failed(action);
    }

    private static String statusBadge(EsCommunicationBundle.Status status) {
        String variant = switch (status) {
            case DRAFT -> " aira-badge--warning";
            case PUBLISHED -> " aira-badge--success";
            case RETIRED -> "";
        };
        String label = switch (status) {
            case DRAFT -> "Draft";
            case PUBLISHED -> "Published";
            case RETIRED -> "Retired";
        };
        return "<span class=\"aira-badge" + variant + "\">" + label + "</span>";
    }

    private static List<EsCommunicationBundleTemplateComponent> resourceComponents(OrientationResources orientation) {
        return orientation.components().stream()
                .filter(c -> c.getKind() == Kind.RESOURCE || c.getKind() == Kind.RESOURCE_COLLECTION).toList();
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
        if (audience == null) {
            return "Not set";
        }
        return switch (audience) {
            case PUBLIC -> "Anyone who can view the Topic";
            case PARTICIPANTS -> "Topic Space participants";
            case STEWARDS -> "Topic stewards";
        };
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

    private static String attribute(String value) {
        return escape(value).replace("\r\n", "&#10;").replace("\n", "&#10;").replace("\r", "&#10;");
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
