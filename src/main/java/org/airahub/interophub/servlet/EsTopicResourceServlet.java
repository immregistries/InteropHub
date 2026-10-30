package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsCommunicationBundlePurpose;
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
            render(request, response, topic, auth.findAuthenticatedUser(request).orElseThrow(), FormInput.none());
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
                CommunicationBundleService.LibraryAddition added;
                try (var input = file.getInputStream()) {
                    added = bundles.uploadResourceIntoRole(user, topicId, input, file.getSubmittedFileName(),
                            file.getContentType(), request.getParameter("title"), request.getParameter("description"),
                            request.getParameter("attribution"), optionalId(request.getParameter("componentId")));
                }
                message = placedMessage(added, "Resource uploaded.");
            } else if ("replaceResourceFile".equals(action)) {
                var files = parts.stream().filter(p -> p.getSubmittedFileName() != null).toList();
                if (files.size() != 1 || !"replacementFile".equals(files.get(0).getName())
                        || files.get(0).getSize() == 0) {
                    throw new IllegalArgumentException("Choose exactly one non-empty replacement file.");
                }
                Part file = files.get(0);
                try (var input = file.getInputStream()) {
                    bundles.replaceResourceFile(user, topicId, id(request.getParameter("resourceId")),
                            input, file.getSubmittedFileName(), file.getContentType());
                }
                message = "Current resource file replaced. Existing Orientation placements now use the replacement.";
            } else if ("link".equals(action)) {
                var added = bundles.registerLinkIntoRole(user, topicId, request.getParameter("externalUrl"),
                        request.getParameter("title"), request.getParameter("description"),
                        request.getParameter("attribution"), optionalId(request.getParameter("componentId")));
                message = placedMessage(added, "External link added.");
            } else if ("metadata".equals(action)) {
                bundles.updateResourceMetadata(user, topicId, id(request.getParameter("resourceId")),
                        request.getParameter("title"), request.getParameter("description"),
                        request.getParameter("attribution"), request.getParameter("externalUrl"));
                message = "Resource metadata saved.";
            } else if ("createDraft".equals(action)) {
                bundles.createTopicOrientationDraft(user, topicId);
                message = "Orientation draft created.";
            } else if ("text".equals(action)) {
                bundles.setTextComponentValue(user, topicId, id(request.getParameter("bundleId")),
                        id(request.getParameter("componentId")), request.getParameter("content"));
                message = "Orientation narrative saved.";
            } else if ("list".equals(action)) {
                bundles.setStructuredListComponentValue(user, topicId, id(request.getParameter("bundleId")),
                        id(request.getParameter("componentId")), request.getParameter("content"));
                message = "Orientation list saved.";
            } else if ("narrative".equals(action)) {
                bundles.saveNarrativeValues(user, topicId, id(request.getParameter("bundleId")), narrativeValues(request));
                message = "Orientation narrative saved.";
            } else if ("settings".equals(action)) {
                EsCommunicationBundlePurpose.Audience audience;
                try {
                    audience = EsCommunicationBundlePurpose.Audience.valueOf(request.getParameter("audience"));
                } catch (IllegalArgumentException | NullPointerException ex) {
                    throw new IllegalArgumentException("Choose a valid Orientation audience.", ex);
                }
                bundles.saveOrientationAudience(user, topicId, audience);
                message = "Orientation audience saved.";
            } else if ("refreshDate".equals(action)) {
                bundles.markOrientationRefreshed(user, topicId);
                message = "Orientation marked as refreshed this month.";
            } else if ("publish".equals(action)) {
                bundles.publishOrientation(user, topicId);
                message = "Orientation published for its selected audience.";
            } else if ("retire".equals(action)) {
                bundles.retireOrientation(user, topicId);
                message = "Orientation retired. Its history is retained; create a new draft to replace it.";
            } else if ("select".equals(action)) {
                bundles.selectOrientationResource(user, topicId, id(request.getParameter("componentId")),
                        id(request.getParameter("resourceId")));
                message = "Orientation resource selection saved.";
            } else if ("remove".equals(action)) {
                bundles.removeOrientationResource(user, topicId, id(request.getParameter("placementId")));
                message = "Resource removed from the role. It remains in this Topic's library.";
            } else if ("moveUp".equals(action) || "moveDown".equals(action)) {
                bundles.moveOrientationResource(user, topicId, id(request.getParameter("placementId")), "moveUp".equals(action));
                message = "Resource collection order saved.";
            } else if ("context".equals(action)) {
                bundles.updateOrientationResourceContext(user, topicId, id(request.getParameter("placementId")),
                        request.getParameter("contextNote"));
                message = "Resource context note saved.";
            } else {
                throw new IllegalArgumentException("Unknown resource operation.");
            }
            request.getSession().setAttribute(flashKey(topicId), message);
            response.sendRedirect(request.getContextPath() + "/es/topic-resources/" + topicId + anchor(request, action));
        } catch (SecurityException ex) {
            LOGGER.log(Level.WARNING, "Topic resource access denied", ex);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Topic resource request rejected", ex);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            String action = request.getParameter("action");
            render(request, response, topic, user, FormInput.replay(request, action, rejectionMessage(action, ex),
                    dialogFor(request, action)));
        } catch (IOException | ServletException | RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Topic resource operation failed", ex);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            String action = request.getParameter("action");
            render(request, response, topic, user, FormInput.replay(request, action,
                    "Resource operation failed. Check the 25 MiB limit, file format, storage permissions and server log."
                            + fileAgain(action),
                    dialogFor(request, action)));
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

    void render(HttpServletRequest request, HttpServletResponse response, EsTopic topic,
            User user, FormInput input) throws IOException {
        var resources = bundles.listResourceDetailsForSteward(user, topic.getEsTopicId());
        var orientation = bundles.findOrientationResourcesForViewer(user, topic.getEsTopicId()).orElse(null);
        String message = null;
        if (input.error() == null && request.getSession(false) != null) {
            Object flash = request.getSession(false).getAttribute(flashKey(topic.getEsTopicId()));
            if (flash instanceof String text) {
                message = text;
                request.getSession(false).removeAttribute(flashKey(topic.getEsTopicId()));
            }
        }
        var topicSpace = new EsTopicSpaceDao().findById(topic.getEsTopicSpaceId())
                .orElseThrow(() -> new IllegalStateException("The Topic's Topic Space was not found."));
        var page = InteropAiraPageFactory.base(request, "Topic Resources - InteropHub")
                .applicationSubtitle("Orientation resource management")
                .mainClass("aira-main")
                .addLocalStylesheet("/css/topic-resources.css?v=1")
                .context(InteropAiraPageFactory.topicsMeetingsContext(
                        topicSpace.getSpaceName(), topicSpace.getSpaceCode(), true, false))
                .build();
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("<div class=\"aira-container aira-stack\">");
            if (input.error() != null) {
                out.println("<p class=\"aira-alert aira-alert--error\" role=\"alert\">"
                        + TopicOrientationResourceRenderer.escape(input.error()) + "</p>");
            }
            if (message != null) {
                out.println("<p class=\"aira-alert aira-alert--success\" role=\"status\">"
                        + TopicOrientationResourceRenderer.escape(message) + "</p>");
            }
            TopicOrientationResourceRenderer.editor(out, request.getContextPath(), topic.getEsTopicId(),
                    topic.getTopicName(), CsrfTokenSupport.getOrCreateToken(request), orientation, resources,
                    bundles.storage(), bundles.storage().writeProblem(null), input);
            out.println("</div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(request.getContextPath()));
            out.println("<script src=\"" + TopicOrientationResourceRenderer.escape(request.getContextPath())
                    + "/js/topic-resources.js?v=1\"></script>");
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

    private static Long optionalId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return id(value);
    }

    private static Map<Long, String> narrativeValues(HttpServletRequest request) {
        Map<Long, String> values = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, list) -> {
            if (name != null && name.startsWith("content-") && list != null && list.length > 0) {
                values.put(id(name.substring("content-".length())), list[0]);
            }
        });
        return values;
    }

    private static String placedMessage(CommunicationBundleService.LibraryAddition added, String libraryMessage) {
        if (added.roleName() == null || added.roleName().isBlank()) {
            return libraryMessage;
        }
        return added.resource().getTitle() + " added to " + added.roleName() + ".";
    }

    private static String rejectionMessage(String action, RuntimeException ex) {
        String message = ex.getMessage() == null ? "The Orientation could not be saved." : ex.getMessage();
        return message + fileAgain(action);
    }

    private static String fileAgain(String action) {
        if (!"upload".equals(action) && !"replaceResourceFile".equals(action)) {
            return "";
        }
        return " Choose the file again.";
    }

    private static String anchor(HttpServletRequest request, String action) {
        if (action == null) {
            return "";
        }
        boolean roleAction = switch (action) {
            case "select", "remove", "moveUp", "moveDown", "context", "upload", "link" -> true;
            default -> false;
        };
        if (!roleAction) {
            return "";
        }
        String componentId = request.getParameter("componentId");
        if (componentId == null || !componentId.matches("[0-9]+")) {
            return "";
        }
        return "#role-" + componentId;
    }

    private static String dialogFor(HttpServletRequest request, String action) {
        String componentId = request.getParameter("componentId");
        String resourceId = request.getParameter("resourceId");
        String placementId = request.getParameter("placementId");
        return switch (action == null ? "" : action) {
            case "narrative" -> "narrative";
            case "settings", "refreshDate", "retire" -> "orientation-settings";
            case "context" -> numeric(placementId) ? "context-" + placementId : null;
            case "select", "upload", "link" -> numeric(componentId) ? "add-resource-" + componentId : "library-add";
            case "metadata", "replaceResourceFile" -> numeric(resourceId) ? "edit-resource-" + resourceId : null;
            default -> null;
        };
    }

    private static boolean numeric(String value) {
        return value != null && value.matches("[0-9]+");
    }
}
