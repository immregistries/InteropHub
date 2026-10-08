package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.EsCommunicationBundleTemplate;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent.Cardinality;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent.Kind;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.CommunicationBundleTemplateService;

public class AdminCommunicationBundleTemplateServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(AdminCommunicationBundleTemplateServlet.class.getName());
    static final String ROUTE = "/admin/content/bundle-templates";
    private final CommunicationBundleTemplateService templates = new CommunicationBundleTemplateService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var user = AdminAccessGuard.requireAdmin(request, response);
        if (user.isEmpty()) {
            return;
        }
        try {
            render(request, response, user.get(), null);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "Bundle template lookup rejected", ex);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, ex.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        var user = AdminAccessGuard.requireAdmin(request, response);
        if (user.isEmpty()) {
            return;
        }
        if (!CsrfTokenSupport.isValid(request)) {
            LOGGER.warning("Bundle template mutation rejected: invalid CSRF token");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Refresh the page and try again.");
            return;
        }
        try {
            Long templateId;
            if ("create".equals(request.getParameter("action"))) {
                templateId = templates.createDraft(user.get(), id(request.getParameter("purposeId"), false),
                        id(request.getParameter("sourceId"), true)).getTemplateId();
            } else {
                templateId = id(request.getParameter("templateId"), false);
                switch (request.getParameter("action") == null ? "" : request.getParameter("action")) {
                    case "save" -> templates.saveComponent(user.get(), templateId,
                            id(request.getParameter("componentId"), true), readInput(request));
                    case "remove" -> templates.removeComponent(user.get(), templateId,
                            id(request.getParameter("componentId"), false));
                    case "activate" -> templates.activate(user.get(), templateId);
                    default -> throw new IllegalArgumentException("Unknown template operation.");
                }
            }
            request.getSession().setAttribute(ROUTE + ".message", "Template operation saved.");
            response.sendRedirect(request.getContextPath() + ROUTE + "?templateId=" + templateId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Bundle template mutation rejected", ex);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try {
                render(request, response, user.get(), ex.getMessage());
            } catch (IllegalArgumentException lookup) {
                LOGGER.log(Level.WARNING, "Rejected template request cannot be rendered", lookup);
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            }
        }
    }

    private void render(HttpServletRequest request, HttpServletResponse response, User user, String error) throws IOException {
        var purposes = templates.listPurposes(user);
        Long selectedId = id(request.getParameter("templateId"), true);
        var selected = selectedId == null ? null : templates.getTemplate(user, selectedId);
        var components = selected == null ? List.<EsCommunicationBundleTemplateComponent>of()
                : templates.listComponents(user, selectedId);
        String context = request.getContextPath();
        String token = CsrfTokenSupport.getOrCreateToken(request);
        Object flash = request.getSession().getAttribute(ROUTE + ".message");
        request.getSession().removeAttribute(ROUTE + ".message");
        response.setHeader("Cache-Control", "no-store");
        AdminShellRenderer.render(request, response, "Communication Bundle Templates - InteropHub",
                AdminSection.CONTENT, ROUTE, out -> {
                    out.println("<h1 class=\"aira-page-title\">Communication Bundle Templates</h1>"
                            + "<p>Administrators define templates for product-defined Purposes. Topic stewards author "
                            + "the actual communication under their Topics. Activating a template does not publish a bundle "
                            + "or change the template used by an existing bundle.</p>");
                    if (error != null) {
                        out.println("<p class=\"aira-alert aira-alert--error\" role=\"alert\">" + escape(error) + "</p>");
                    } else if (flash instanceof String message) {
                        out.println("<p class=\"aira-alert aira-alert--success\" role=\"status\">" + escape(message) + "</p>");
                    }
                    for (var purpose : purposes) {
                        out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                                + "<h2 class=\"aira-section-card__title\">" + escape(purpose.getDisplayName())
                                + "</h2></div><div class=\"aira-section-card__body aira-stack\">"
                                + "<p>" + escape(purpose.getDescription()) + "</p><p class=\"aira-meta\">"
                                + escape(purpose.getMode().name()) + " / " + escape(purpose.getInstancePolicy().name())
                                + "; default audience: " + escape(purpose.getDefaultAudience().name()) + "</p>");
                        var versions = templates.listVersions(user, purpose.getPurposeId());
                        for (var version : versions) {
                            out.println("<div class=\"aira-cluster\"><a class=\"aira-inline-link\" href=\"" + context
                                    + ROUTE + "?templateId=" + version.getTemplateId() + "\">Version " + version.getVersion()
                                    + " - " + version.getStatus() + "</a>");
                            if (purpose.isActive() && version.getStatus() != EsCommunicationBundleTemplate.Status.DRAFT) {
                                form(out, context, token, "create", null);
                                hidden(out, "purposeId", purpose.getPurposeId().toString());
                                hidden(out, "sourceId", version.getTemplateId().toString());
                                button(out, "Create draft from version " + version.getVersion());
                                out.println("</form>");
                            }
                            out.println("</div>");
                        }
                        if (purpose.isActive() && versions.isEmpty()) {
                            form(out, context, token, "create", null);
                            hidden(out, "purposeId", purpose.getPurposeId().toString());
                            button(out, "Create first draft");
                            out.println("</form>");
                        }
                        out.println("</div></section>");
                    }
                    if (selected != null) {
                        renderVersion(out, request, selected, components, token, error != null);
                    }
                });
    }

    private static void renderVersion(PrintWriter out, HttpServletRequest request, EsCommunicationBundleTemplate template,
            List<EsCommunicationBundleTemplateComponent> components, String token, boolean rejected) {
        String context = request.getContextPath();
        boolean draft = template.getStatus() == EsCommunicationBundleTemplate.Status.DRAFT;
        out.println("<h2>Template version " + template.getVersion() + " - " + template.getStatus() + "</h2>"
                + "<p>Semantic keys identify components independently of their labels. Released versions are read-only. "
                + "Lists use one text item per line; resource collections follow the steward's explicit order.</p>");
        for (var component : components) {
            if (draft) {
                componentForm(out, request, token, template.getTemplateId(), component, rejected);
                form(out, context, token, "remove", template.getTemplateId());
                hidden(out, "componentId", component.getComponentId().toString());
                button(out, "Remove " + component.getDisplayName());
                out.println("</form>");
            } else {
                out.println("<p><strong>" + escape(component.getDisplayName()) + "</strong> - "
                        + escape(component.getSemanticKey()) + " / " + component.getKind() + " / "
                        + component.getCardinality() + " / " + (component.isRequired() ? "required" : "recommended")
                        + " / order " + component.getDisplayOrder() + "<br>" + escape(component.getAuthoringPrompt()) + "</p>");
            }
        }
        if (draft) {
            out.println("<h3>Add component</h3>");
            componentForm(out, request, token, template.getTemplateId(), null, rejected);
            form(out, context, token, "activate", template.getTemplateId());
            out.println("<button class=\"aira-button aira-button--primary\" type=\"submit\" "
                    + "onclick=\"return confirm('Activate this version for new bundles? Existing bundles keep their original template.');\">"
                    + "Activate template version</button></form>");
        }
        out.println("<section class=\"aira-section-card\"><div class=\"aira-section-card__header\">"
                + "<h2 class=\"aira-section-card__title\">Template preview</h2></div>"
                + "<div class=\"aira-section-card__body aira-stack\"><p class=\"aira-meta\">"
                + "Illustrative content only; this is not a published bundle. Empty optional components are omitted in real bundles.</p>");
        CommunicationBundleComponentRenderer.templatePreview(out, components);
        out.println("</div></section>");
    }

    private static void componentForm(PrintWriter out, HttpServletRequest request, String token, Long templateId,
            EsCommunicationBundleTemplateComponent component, boolean rejected) {
        boolean submitted = rejected && "save".equals(request.getParameter("action"))
                && java.util.Objects.equals(request.getParameter("componentId"),
                        component == null ? null : component.getComponentId().toString());
        String prefix = "component-" + (component == null ? "new" : component.getComponentId());
        form(out, request.getContextPath(), token, "save", templateId);
        if (component != null) {
            hidden(out, "componentId", component.getComponentId().toString());
        }
        field(out, prefix + "-key", "key", "Semantic key", submitted ? request.getParameter("key")
                : component == null ? "" : component.getSemanticKey(), 80, component != null);
        field(out, prefix + "-label", "label", "Display name", submitted ? request.getParameter("label")
                : component == null ? "" : component.getDisplayName(), 140, false);
        out.println("<label class=\"aira-label\" for=\"" + prefix + "-prompt\">Authoring prompt</label>"
                + "<textarea class=\"aira-textarea\" id=\"" + prefix + "-prompt\" name=\"prompt\" maxlength=\"20000\" rows=\"3\">"
                + escape(submitted ? request.getParameter("prompt") : component == null ? "" : component.getAuthoringPrompt())
                + "</textarea>");
        select(out, prefix + "-kind", "kind", "Kind", Kind.values(), submitted ? request.getParameter("kind")
                : component == null ? "TEXT" : component.getKind().name());
        select(out, prefix + "-cardinality", "cardinality", "Cardinality", Cardinality.values(),
                submitted ? request.getParameter("cardinality") : component == null ? "SINGLE" : component.getCardinality().name());
        out.println("<label class=\"aira-label\"><input type=\"checkbox\" name=\"required\" value=\"true\""
                + ((submitted ? "true".equals(request.getParameter("required")) : component != null && component.isRequired())
                        ? " checked" : "") + "> Required</label>"
                + "<label class=\"aira-label\" for=\"" + prefix + "-order\">Display order</label>"
                + "<input class=\"aira-input\" id=\"" + prefix + "-order\" name=\"order\" type=\"number\" min=\"0\" max=\"2147483647\" required value=\""
                + escape(submitted ? request.getParameter("order") : component == null ? "0" : String.valueOf(component.getDisplayOrder()))
                + "\">");
        button(out, component == null ? "Add component" : "Save component");
        out.println("</form>");
    }

    private static void field(PrintWriter out, String id, String name, String label, String value, int max, boolean readonly) {
        out.println("<label class=\"aira-label\" for=\"" + id + "\">" + label + "</label>"
                + "<input class=\"aira-input\" id=\"" + id + "\" name=\"" + name + "\" maxlength=\"" + max
                + "\" required value=\"" + escape(value) + "\"" + (readonly ? " readonly" : "") + ">");
    }

    private static void select(PrintWriter out, String id, String name, String label, Enum<?>[] choices, String selected) {
        out.println("<label class=\"aira-label\" for=\"" + id + "\">" + label + "</label>"
                + "<select class=\"aira-select\" id=\"" + id + "\" name=\"" + name + "\" required>");
        for (var choice : choices) {
            out.println("<option value=\"" + choice.name() + "\"" + (choice.name().equals(selected) ? " selected" : "")
                    + ">" + choice.name() + "</option>");
        }
        out.println("</select>");
    }

    private static CommunicationBundleTemplateService.ComponentInput readInput(HttpServletRequest request) {
        try {
            return new CommunicationBundleTemplateService.ComponentInput(request.getParameter("key"), request.getParameter("label"),
                    request.getParameter("prompt"), Kind.valueOf(request.getParameter("kind")),
                    "true".equals(request.getParameter("required")), Cardinality.valueOf(request.getParameter("cardinality")),
                    Integer.parseInt(request.getParameter("order")));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("Choose a valid component kind, cardinality and display order.", ex);
        }
    }

    private static Long id(String value, boolean optional) {
        if (optional && (value == null || value.isBlank())) {
            return null;
        }
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Choose a valid template, Purpose or component identifier.", ex);
        }
        throw new IllegalArgumentException("Identifiers must be positive.");
    }

    private static void form(PrintWriter out, String context, String token, String action, Long templateId) {
        out.println("<form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\"" + context + ROUTE + "\">");
        hidden(out, "csrfToken", token);
        hidden(out, "action", action);
        if (templateId != null) {
            hidden(out, "templateId", templateId.toString());
        }
    }

    private static void hidden(PrintWriter out, String name, String value) {
        out.println("<input type=\"hidden\" name=\"" + name + "\" value=\"" + escape(value) + "\">");
    }

    private static void button(PrintWriter out, String label) {
        out.println("<button class=\"aira-button aira-button--secondary\" type=\"submit\">" + escape(label) + "</button>");
    }

    private static String escape(String value) {
        return TopicOrientationResourceRenderer.escape(value);
    }
}
