package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.CommunicationBundleService;
import org.airahub.interophub.service.TopicSpaceAccessService;

public class EsTopicResourceServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(EsTopicResourceServlet.class.getName());
    private final AuthFlowService auth;
    private final EsTopicDao topics;
    private final TopicSpaceAccessService access;
    private final CommunicationBundleService bundles;

    public EsTopicResourceServlet() {
        this(new AuthFlowService(), new EsTopicDao(), new TopicSpaceAccessService(), new CommunicationBundleService());
    }

    EsTopicResourceServlet(AuthFlowService auth, EsTopicDao topics, TopicSpaceAccessService access,
            CommunicationBundleService bundles) {
        this.auth = auth;
        this.topics = topics;
        this.access = access;
        this.bundles = bundles;
    }

    @Override
    public void init() {
        bundles.storage().local().setDeploymentPath(getServletContext().getRealPath("/"));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        EsTopic topic = authorizedTopic(request, response);
        if (topic != null) {
            render(request, response, topic, auth.findAuthenticatedUser(request).orElseThrow(), null, null);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        EsTopic topic = authorizedTopic(request, response);
        if (topic == null) {
            return;
        }
        User user = auth.findAuthenticatedUser(request).orElseThrow();
        List<Part> parts = List.of();
        try {
            if (request.getContentType() != null
                    && request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("multipart/form-data")) {
                parts = List.copyOf(request.getParts());
            }
            if (!CsrfTokenSupport.isValid(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid request token. Reload the editor and retry.");
                return;
            }
            String action = request.getParameter("action");
            Long topicId = topic.getEsTopicId();
            String message;
            if ("upload".equals(action)) {
                var files = parts.stream().filter(p -> p.getSubmittedFileName() != null).toList();
                if (files.size() != 1 || !"resourceFile".equals(files.get(0).getName())
                        || files.get(0).getSize() == 0) {
                    throw new IllegalArgumentException("Choose exactly one non-empty resource file.");
                }
                Part file = files.get(0);
                try (var input = file.getInputStream()) {
                    bundles.uploadResource(user, topicId, input, file.getSubmittedFileName(), file.getContentType(),
                            request.getParameter("title"), request.getParameter("description"),
                            request.getParameter("attribution"));
                }
                message = "Resource uploaded. Select it for an Orientation role below.";
            } else if ("link".equals(action)) {
                bundles.registerExternalLinkResource(user, topicId, request.getParameter("externalUrl"),
                        request.getParameter("title"), request.getParameter("description"),
                        request.getParameter("attribution"));
                message = "External link added. Select it for an Orientation role below.";
            } else if ("metadata".equals(action)) {
                bundles.updateResourceMetadata(user, topicId, id(request.getParameter("resourceId")),
                        request.getParameter("title"), request.getParameter("description"),
                        request.getParameter("attribution"), request.getParameter("externalUrl"));
                message = "Resource metadata saved.";
            } else if ("createDraft".equals(action)) {
                bundles.createTopicOrientationDraft(user, topicId);
                message = "Orientation draft created.";
            } else if ("select".equals(action)) {
                bundles.selectOrientationResource(user, topicId, id(request.getParameter("componentId")),
                        id(request.getParameter("resourceId")));
                message = "Orientation resource selection saved.";
            } else if ("remove".equals(action)) {
                bundles.removeOrientationResource(user, topicId, id(request.getParameter("placementId")));
                message = "Resource removed from the role. It remains in this Topic's library.";
            } else {
                throw new IllegalArgumentException("Unknown resource operation.");
            }
            request.getSession().setAttribute(flashKey(topicId), message);
            response.sendRedirect(request.getContextPath() + "/es/topic-resources/" + topicId);
        } catch (SecurityException ex) {
            LOGGER.log(Level.WARNING, "Topic resource access denied", ex);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Topic resource request rejected", ex);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            render(request, response, topic, user, ex.getMessage(), null);
        } catch (IOException | ServletException | RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Topic resource operation failed", ex);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            render(request, response, topic, user,
                    "Resource operation failed. Check the 25 MiB limit, file format, storage permissions and server log.",
                    null);
        } finally {
            for (Part part : parts) {
                try {
                    part.delete();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Topic resource multipart cleanup failed", ex);
                }
            }
        }
    }

    private EsTopic authorizedTopic(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long topicId;
        try {
            String path = request.getPathInfo();
            topicId = id(path != null && path.matches("/[0-9]+") ? path.substring(1) : null);
        } catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        EsTopic topic = topics.findById(topicId).orElse(null);
        User user = auth.findAuthenticatedUser(request).orElse(null);
        if (topic == null || !access.canViewTopic(user, topic)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (user == null || !access.canEditTopic(user, topic)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only Topic stewards can manage resources.");
            return null;
        }
        return topic;
    }

    private void render(HttpServletRequest request, HttpServletResponse response, EsTopic topic,
            User user, String error, String message) throws IOException {
        var resources = bundles.listResourceDetailsForSteward(user, topic.getEsTopicId());
        var orientation = bundles.findOrientationResourcesForViewer(user, topic.getEsTopicId()).orElse(null);
        if (message == null && request.getSession(false) != null) {
            Object flash = request.getSession(false).getAttribute(flashKey(topic.getEsTopicId()));
            if (flash instanceof String text) {
                message = text;
                request.getSession(false).removeAttribute(flashKey(topic.getEsTopicId()));
            }
        }
        var page = InteropAiraPageFactory.base(request, "Topic Resources - InteropHub")
                .applicationSubtitle("Orientation resource management").mainClass("aira-main").build();
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("<div class=\"aira-container aira-stack\"><header><h1>Resources for "
                    + TopicOrientationResourceRenderer.escape(topic.getTopicName()) + "</h1>"
                    + "<p><a class=\"aira-inline-link\" href=\"" + request.getContextPath() + "/es/topic/"
                    + topic.getEsTopicId() + "\">View Topic page and draft preview</a></p></header>"
                    + "<p class=\"aira-alert aira-alert--info\">Only Topic stewards can use this editor and view Orientation drafts."
                    + " Publication and narrative editing follow in a later phase.</p>");
            if (error != null) {
                out.println("<p class=\"aira-alert aira-alert--error\" role=\"alert\">" + TopicOrientationResourceRenderer.escape(error) + "</p>");
            }
            if (message != null) {
                out.println("<p class=\"aira-alert aira-alert--success\" role=\"status\">" + TopicOrientationResourceRenderer.escape(message) + "</p>");
            }
            TopicOrientationResourceRenderer.editor(out, request.getContextPath(), topic.getEsTopicId(),
                    CsrfTokenSupport.getOrCreateToken(request), orientation, resources,
                    bundles.storage().writeProblem(null));
            out.println("</div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(request.getContextPath()));
            page.writeEnd(out);
        }
    }

    private static String flashKey(Long topicId) {
        return EsTopicResourceServlet.class.getName() + ".saved." + topicId;
    }

    private static Long id(String value) {
        try {
            long result = Long.parseLong(value);
            if (result > 0) {
                return result;
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("A valid resource, role or Topic identifier is required.", ex);
        }
        throw new IllegalArgumentException("A positive identifier is required.");
    }
}
