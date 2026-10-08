package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.CommunicationBundleService.OrientationResources;
import org.airahub.interophub.service.StarterPacketService;

public class CommunicationBundleServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(CommunicationBundleServlet.class.getName());
    private final AuthFlowService auth;
    private final StarterPacketService packets;
    private final EsTopicSpaceDao spaces;

    public CommunicationBundleServlet() {
        this(new AuthFlowService(), new StarterPacketService(), new EsTopicSpaceDao());
    }

    CommunicationBundleServlet(AuthFlowService auth, StarterPacketService packets, EsTopicSpaceDao spaces) {
        this.auth = auth;
        this.packets = packets;
        this.spaces = spaces;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            var user = auth.findAuthenticatedUser(request).orElse(null);
            var packet = packets.get(user, StarterPacketServlet.pathId(request.getPathInfo()));
            var topic = packets.requireAccess(user, packet.bundle().getEsTopicId());
            render(request, response, topic, packet);
        } catch (SecurityException ex) {
            LOGGER.log(Level.WARNING, "Bundle access denied", ex);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, "Bundle request rejected", ex);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } catch (IOException | RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Bundle rendering failed", ex);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "The packet is temporarily unavailable. Check stored resource availability and the server log.");
            }
        }
    }

    void render(HttpServletRequest request, HttpServletResponse response, EsTopic topic,
            OrientationResources packet) throws IOException {
        var space = spaces.findById(topic.getEsTopicSpaceId())
                .orElseThrow(() -> new IllegalStateException("The Topic's Topic Space is unavailable."));
        var page = InteropAiraPageFactory.base(request, StarterPacketRenderer.title(packet) + " - InteropHub")
                .applicationSubtitle("Starter Packet")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.topicsMeetingsContext(space.getSpaceName(), space.getSpaceCode(), true, false))
                .build();
        var body = new StringWriter();
        try (PrintWriter out = new PrintWriter(body)) {
            out.println("<div class=\"aira-container aira-stack\">");
            StarterPacketRenderer.full(out, request.getContextPath(), packet);
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
}
