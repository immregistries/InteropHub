package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.MeetingCommunicationService;

/**
 * Admin dashboard for all recent meeting communications.
 * URL: /es/meeting-communications
 */
public class EsMeetingCommunicationsServlet extends HttpServlet {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int RECENT_LIMIT = 50;

    private final AuthFlowService authFlowService;
    private final MeetingCommunicationService communicationService;
    private final EsMeetingDao meetingDao;

    public EsMeetingCommunicationsServlet() {
        this.authFlowService = new AuthFlowService();
        this.communicationService = new MeetingCommunicationService();
        this.meetingDao = new EsMeetingDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        List<EsMeetingCommunication> communications = communicationService.findAllRecent(RECENT_LIMIT);

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Meeting Communications - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Meeting Communications</h2>");
                panelOut.println("        <p>Showing the " + RECENT_LIMIT + " most recent communications.</p>");

                panelOut.println("        <table class=\"data-table\">");
                panelOut.println("          <thead><tr>");
                panelOut.println("            <th>Meeting</th>");
                panelOut.println("            <th>Type</th>");
                panelOut.println("            <th>Status</th>");
                panelOut.println("            <th>Scheduled Send</th>");
                panelOut.println("            <th>Created At</th>");
                panelOut.println("            <th>Actions</th>");
                panelOut.println("          </tr></thead>");
                panelOut.println("          <tbody>");

                if (communications.isEmpty()) {
                    panelOut.println("            <tr><td colspan=\"6\">No communications found.</td></tr>");
                }

                for (EsMeetingCommunication comm : communications) {
                    Optional<EsMeeting> meeting = meetingDao.findById(comm.getEsMeetingId());
                    String meetingName = meeting.map(EsMeeting::getMeetingName)
                            .orElse("Meeting #" + comm.getEsMeetingId());
                    String scheduledAt = comm.getScheduledSendAt() != null
                            ? DATETIME_FMT.format(comm.getScheduledSendAt())
                            : "—";
                    String createdAt = comm.getCreatedAt() != null
                            ? DATETIME_FMT.format(comm.getCreatedAt())
                            : "";
                    String statusBadge = renderStatusBadge(comm.getStatus());

                    panelOut.println("            <tr>");
                    panelOut.println(
                            "              <td><a href=\"" + contextPath + "/es/meeting-communication?meetingId="
                                    + comm.getEsMeetingId() + "\">" + escapeHtml(meetingName) + "</a></td>");
                    panelOut.println("              <td>" + escapeHtml(comm.getCommunicationType().name()) + "</td>");
                    panelOut.println("              <td>" + statusBadge + "</td>");
                    panelOut.println("              <td>" + escapeHtml(scheduledAt) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(createdAt) + "</td>");
                    panelOut.println("              <td><a href=\"" + contextPath
                            + "/es/meeting-communication-preview?id=" + comm.getEsMeetingCommunicationId()
                            + "\">Preview</a></td>");
                    panelOut.println("            </tr>");
                }

                panelOut.println("          </tbody>");
                panelOut.println("        </table>");
                panelOut.println("      </section>");
            });
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static String renderStatusBadge(EsMeetingCommunication.CommunicationStatus status) {
        String color = switch (status) {
            case DRAFT -> "#6c757d";
            case SCHEDULED -> "#0d6efd";
            case SENDING -> "#fd7e14";
            case SENT -> "#198754";
            case CANCELLED -> "#6c757d";
            case FAILED -> "#dc3545";
        };
        return "<span style=\"color:" + color + ";font-weight:600\">" + escapeHtml(status.name()) + "</span>";
    }

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(user.get())) {
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                AdminShellRenderer.render(out, "Access Denied - InteropHub", request.getContextPath(), panelOut -> {
                    panelOut.println("      <section class=\"panel\">");
                    panelOut.println("        <h2>Access Denied</h2>");
                    panelOut.println("        <p>Admin access required.</p>");
                    panelOut.println("        <p><a href=\"" + request.getContextPath()
                            + "/welcome\">Return to Welcome</a></p>");
                    panelOut.println("      </section>");
                });
            }
            return Optional.empty();
        }
        return user;
    }

    private static String escapeHtml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
