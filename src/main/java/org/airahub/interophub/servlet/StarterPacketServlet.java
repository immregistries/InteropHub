package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsCommunicationBundle;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.CommunicationBundleService.ResourceDetails;
import org.airahub.interophub.service.StarterPacketService;

public class StarterPacketServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StarterPacketServlet.class.getName());
    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private final AuthFlowService auth;
    private final StarterPacketService packets;
    private final EsTopicSpaceDao spaces;

    public StarterPacketServlet() {
        this(new AuthFlowService(), new StarterPacketService(), new EsTopicSpaceDao());
    }

    StarterPacketServlet(AuthFlowService auth, StarterPacketService packets, EsTopicSpaceDao spaces) {
        this.auth = auth;
        this.packets = packets;
        this.spaces = spaces;
    }

    @Override
    public void init() {
        packets.storage().local().setDeploymentPath(getServletContext().getRealPath("/"));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            User user = auth.findAuthenticatedUser(request).orElse(null);
            EsTopic topic = packets.requireAccess(user, pathId(request.getPathInfo()));
            render(request, response, topic, user, null, Map.of());
        } catch (SecurityException ex) {
            denied(response, ex);
        } catch (IllegalArgumentException ex) {
            rejected(response, ex);
        } catch (IOException | RuntimeException ex) {
            unavailable(response, ex);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        List<Part> parts = List.of();
        EsTopic topic = null;
        User user = null;
        try {
            user = auth.findAuthenticatedUser(request).orElse(null);
            topic = packets.requireAccess(user, pathId(request.getPathInfo()));
            if (request.getContentType() != null
                    && request.getContentType().toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
                try {
                    parts = List.copyOf(request.getParts());
                } catch (IllegalStateException ex) {
                    throw new IllegalArgumentException("Upload request is too large. The per-file limit is 25 MiB.", ex);
                }
            }
            if (!CsrfTokenSupport.isValid(request)) {
                throw new SecurityException("Invalid request token. Reload the editor and retry.");
            }
            Long selectedId = mutate(request, user, topic.getEsTopicId(), parts);
            response.sendRedirect(request.getContextPath() + "/es/starter-packets/" + topic.getEsTopicId()
                    + (selectedId == null ? "" : "?bundleId=" + selectedId));
        } catch (SecurityException ex) {
            denied(response, ex);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "Starter Packet request rejected", ex);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            if (topic == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            } else {
                try {
                    render(request, response, topic, user, ex.getMessage(), submitted(request));
                } catch (SecurityException renderFailure) {
                    denied(response, renderFailure);
                } catch (IllegalArgumentException renderFailure) {
                    rejected(response, ex);
                } catch (IOException | RuntimeException renderFailure) {
                    unavailable(response, renderFailure);
                }
            }
        } catch (IOException | ServletException | RuntimeException ex) {
            unavailable(response, ex);
        } finally {
            for (Part part : parts) {
                try {
                    part.delete();
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Starter Packet multipart cleanup failed", ex);
                }
            }
        }
    }

    private Long mutate(HttpServletRequest request, User user, Long topicId, List<Part> parts) throws IOException {
        String action = request.getParameter("action");
        if ("create".equals(action)) {
            return packets.create(user, topicId, null).getBundleId();
        }
        Long bundleId = id(request.getParameter("bundleId"));
        var packet = packetForTopic(user, topicId, bundleId);
        if ("copy".equals(action)) {
            if (packet.bundle().getStatus() == EsCommunicationBundle.Status.DRAFT) {
                throw new IllegalArgumentException("Copy a published or retired packet, not a draft.");
            }
            return packets.create(user, topicId, bundleId).getBundleId();
        }
        if ("retire".equals(action)) {
            if (packet.bundle().getStatus() != EsCommunicationBundle.Status.PUBLISHED) {
                throw new IllegalArgumentException("Only a published packet can be retired.");
            }
            packets.retire(user, topicId, bundleId);
            return bundleId;
        }
        if (packet.bundle().getStatus() != EsCommunicationBundle.Status.DRAFT) {
            throw new IllegalArgumentException("This packet is locked. Copy it into a new draft to make changes.");
        }
        if ("text".equals(action) || "list".equals(action)) {
            packets.saveValue(user, topicId, bundleId, id(request.getParameter("componentId")), request.getParameter("content"));
        } else if ("settings".equals(action)) {
            packets.saveSettings(user, topicId, bundleId, optionalInteger(request.getParameter("communicationMonth")),
                    optionalInteger(request.getParameter("communicationYear")));
        } else if ("select".equals(action)) {
            packets.selectResource(user, topicId, bundleId, id(request.getParameter("componentId")), id(request.getParameter("resourceId")));
        } else if ("remove".equals(action)) {
            packets.removeResource(user, topicId, bundleId, id(request.getParameter("placementId")));
        } else if ("moveUp".equals(action) || "moveDown".equals(action)) {
            packets.moveResource(user, topicId, bundleId, id(request.getParameter("placementId")), "moveUp".equals(action));
        } else if ("context".equals(action)) {
            packets.saveContext(user, topicId, bundleId, id(request.getParameter("placementId")), request.getParameter("contextNote"));
        } else if ("publish".equals(action)) {
            boolean confirmed = "true".equals(request.getParameter("preserveConfirmed"));
            if (!confirmed) {
                throw new IllegalArgumentException("Confirm file preservation and public file URL access before publishing.");
            }
            packets.publish(user, topicId, bundleId, true);
        } else if ("link".equals(action)) {
            packets.addLink(user, topicId, request.getParameter("externalUrl"), request.getParameter("title"));
        } else if ("upload".equals(action)) {
            var files = parts.stream().filter(p -> p.getSubmittedFileName() != null).toList();
            if (files.size() != 1 || !"resourceFile".equals(files.get(0).getName())
                    || files.get(0).getSize() == 0 || files.get(0).getSize() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("Choose exactly one non-empty resource file, at most 25 MiB.");
            }
            Part file = files.get(0);
            try (var input = file.getInputStream()) {
                packets.upload(user, topicId, input, file.getSubmittedFileName(), file.getContentType(), request.getParameter("title"));
            }
        } else {
            throw new IllegalArgumentException("Unknown Starter Packet operation.");
        }
        return bundleId;
    }

    private OrientationResources packetForTopic(User user, Long topicId, Long bundleId) {
        var packet = packets.get(user, bundleId);
        if (!topicId.equals(packet.bundle().getEsTopicId())) {
            throw new SecurityException("This packet does not belong to this Topic.");
        }
        return packet;
    }

    void render(HttpServletRequest request, HttpServletResponse response, EsTopic topic, User user,
            String error, Map<String, String> submitted) throws IOException {
        String rawId = request.getParameter("bundleId");
        Long selectedId = rawId == null || rawId.isBlank() ? null : id(rawId);
        var selected = selectedId == null ? null : packetForTopic(user, topic.getEsTopicId(), selectedId);
        var all = packets.list(user, topic.getEsTopicId(), false);
        boolean editing = selected != null && selected.bundle().getStatus() == EsCommunicationBundle.Status.DRAFT;
        var resources = editing ? packets.resources(user, topic.getEsTopicId()) : List.<ResourceDetails>of();
        String uploadProblem = editing ? packets.storage().writeProblem(null) : null;
        var space = spaces.findById(topic.getEsTopicSpaceId())
                .orElseThrow(() -> new IllegalStateException("The Topic's Topic Space is unavailable."));
        var page = InteropAiraPageFactory.base(request, "Starter Packets - InteropHub")
                .applicationSubtitle("Starter Packet management")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.topicsMeetingsContext(space.getSpaceName(), space.getSpaceCode(), true, false))
                .build();
        var body = new StringWriter();
        try (PrintWriter out = new PrintWriter(body)) {
            out.println("<div class=\"aira-container aira-stack\"><header><h1>Starter Packets for "
                    + StarterPacketRenderer.escape(topic.getTopicName()) + "</h1>"
                    + "<p><a class=\"aira-inline-link\" href=\"" + StarterPacketRenderer.escape(request.getContextPath())
                    + "/es/topic/" + topic.getEsTopicId() + "\">Back to Topic</a></p></header>");
            if (error != null) {
                out.println("<p class=\"aira-alert aira-alert--error\" role=\"alert\">" + StarterPacketRenderer.escape(error) + "</p>");
            }
            StarterPacketRenderer.management(out, request.getContextPath(), topic.getEsTopicId(),
                    CsrfTokenSupport.getOrCreateToken(request), all, selected, resources, submitted, uploadProblem);
            if (selected != null) {
                out.println("<details class=\"aira-section-card\"><summary>Saved packet preview</summary>"
                        + "<div class=\"aira-section-card__body aira-stack\">");
                StarterPacketRenderer.full(out, request.getContextPath(), selected);
                out.println("</div></details>");
            }
            out.println("</div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(request.getContextPath()));
        }
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.print(body);
            page.writeEnd(out);
        }
    }

    private static Map<String, String> submitted(HttpServletRequest request) {
        Map<String, String> values = new HashMap<>();
        for (String name : List.of("action", "bundleId", "componentId", "placementId", "content", "contextNote",
                "communicationMonth", "communicationYear", "title", "externalUrl")) {
            String value = request.getParameter(name);
            if (value != null) {
                values.put(name, value);
            }
        }
        return values;
    }

    static Long pathId(String path) {
        if (path == null || !path.matches("/[0-9]+")) {
            throw new IllegalArgumentException("A valid Topic or bundle URL is required.");
        }
        return id(path.substring(1));
    }

    private static Long id(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("A positive identifier is required.", ex);
        }
        throw new IllegalArgumentException("A positive identifier is required.");
    }

    private static Integer optionalInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Month and Year must be whole numbers.", ex);
        }
    }

    private static void denied(HttpServletResponse response, SecurityException ex) throws IOException {
        LOGGER.log(Level.WARNING, "Starter Packet access denied", ex);
        response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
    }

    private static void rejected(HttpServletResponse response, IllegalArgumentException ex) throws IOException {
        LOGGER.log(Level.WARNING, "Starter Packet request rejected", ex);
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
    }

    private static void unavailable(HttpServletResponse response, Exception ex) throws IOException {
        LOGGER.log(Level.SEVERE, "Starter Packet operation failed", ex);
        if (!response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Starter Packet operation failed. The draft remains recoverable. Check storage configuration,"
                    + " permissions and the server log before retrying. Reopen the editor to verify saved content.");
        }
    }
}
