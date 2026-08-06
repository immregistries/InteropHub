package org.airahub.interophub.servlet;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
import org.airahub.interophub.service.MeetingCommunicationService;

/**
 * Admin dashboard for all recent meeting communications.
 * URL: /es/meeting-communications
 */
public class EsMeetingCommunicationsServlet extends HttpServlet {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DEFAULT_TIMEZONE = "America/New_York";
    private static final int RECENT_LIMIT = 50;

    private final MeetingCommunicationService communicationService;
    private final EsMeetingDao meetingDao;

    public EsMeetingCommunicationsServlet() {
        this.communicationService = new MeetingCommunicationService();
        this.meetingDao = new EsMeetingDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        List<EsMeetingCommunication> communications = communicationService.findAllRecent(RECENT_LIMIT);

        AdminShellRenderer.render(request, response, "Meeting Communications - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/meetings", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Meeting Communications</h2>");
                    out.println("            <p class=\"aira-meta\">Showing the " + RECENT_LIMIT
                            + " most recent communications.</p>");

                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead><tr>");
                    out.println("                <th>Meeting</th>");
                    out.println("                <th>Type</th>");
                    out.println("                <th>Status</th>");
                    out.println("                <th>Scheduled Send</th>");
                    out.println("                <th>Created At</th>");
                    out.println("              </tr></thead>");
                    out.println("              <tbody>");

                    if (communications.isEmpty()) {
                        out.println("                <tr><td colspan=\"5\">No communications found.</td></tr>");
                    }

                    for (EsMeetingCommunication comm : communications) {
                        Optional<EsMeeting> meeting = meetingDao.findById(comm.getEsMeetingId());
                        String meetingName = meeting.map(EsMeeting::getMeetingName)
                                .orElse("Meeting #" + comm.getEsMeetingId());
                        String scheduledAt = formatScheduledSendInCommunicationTimezone(comm);
                        String createdAt = comm.getCreatedAt() != null
                                ? DATETIME_FMT.format(comm.getCreatedAt())
                                : "";
                        String statusBadge = renderStatusBadge(comm.getStatus());

                        out.println("                <tr>");
                        out.println(
                                "                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                        + "/es/agenda?meetingId="
                                        + comm.getEsMeetingId() + "\">" + escapeHtml(meetingName) + "</a></td>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/es/meeting-communication-preview?id=" + comm.getEsMeetingCommunicationId()
                                + "\">" + escapeHtml(comm.getCommunicationType().name()) + "</a></td>");
                        out.println("                  <td>" + statusBadge + "</td>");
                        out.println("                  <td>" + escapeHtml(scheduledAt) + "</td>");
                        out.println("                  <td>" + escapeHtml(createdAt) + "</td>");
                        out.println("                </tr>");
                    }

                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");
                    out.println("          </section>");
                });
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static String renderStatusBadge(EsMeetingCommunication.CommunicationStatus status) {
        String variant = switch (status) {
            case DRAFT -> "aira-badge--subtle";
            case SCHEDULED -> "aira-badge--info";
            case SENDING -> "aira-badge--warning";
            case SENT -> "aira-badge--success";
            case CANCELLED -> "aira-badge--subtle";
            case FAILED -> "aira-badge--danger";
        };
        return "<span class=\"aira-badge " + variant + "\">" + escapeHtml(status.name()) + "</span>";
    }

    private static String formatScheduledSendInCommunicationTimezone(EsMeetingCommunication communication) {
        if (communication.getScheduledSendAt() == null) {
            return "—";
        }
        ZoneId targetZone = safeZoneId(communication.getTimezoneId());
        ZonedDateTime local = communication.getScheduledSendAt()
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(targetZone);
        return DATETIME_FMT.format(local) + " " + targetZone.getId();
    }

    private static ZoneId safeZoneId(String timezoneId) {
        if (timezoneId != null && !timezoneId.isBlank()) {
            try {
                return ZoneId.of(timezoneId);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return ZoneId.of(DEFAULT_TIMEZONE);
    }

    private static String escapeHtml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
