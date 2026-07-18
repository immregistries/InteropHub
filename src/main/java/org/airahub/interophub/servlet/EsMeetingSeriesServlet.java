package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsMeeting.MeetingStatus;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicSpaceAccessService;

/**
 * Public-facing list of all meetings for a given meeting series
 * (es_topic_meeting). Accessible at /es/meetings?seriesId=X.
 * Shows all meetings — past, current, and future — across all statuses.
 */
public class EsMeetingSeriesServlet extends HttpServlet {

    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    private static final DateTimeFormatter DISPLAY_TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private static final Set<String> ALLOWED_TIMEZONES = Set.of(
            "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
            "America/Phoenix", "America/Anchorage", "Pacific/Honolulu",
            "America/Sao_Paulo", "America/Santiago",
            "Europe/London", "Europe/Paris",
            "Africa/Johannesburg",
            "Asia/Kolkata", "Asia/Tokyo",
            "Australia/Sydney",
            "Pacific/Auckland");

    private final AuthFlowService authFlowService;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsMeetingDao meetingDao;
    private final EsTopicDao topicDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final TopicSpaceAccessService topicSpaceAccessService;

    public EsMeetingSeriesServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.meetingDao = new EsMeetingDao();
        this.topicDao = new EsTopicDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();

        Long seriesId = parseId(trimToNull(request.getParameter("seriesId")));
        if (seriesId == null) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }

        EsTopicMeeting series = topicMeetingDao.findById(seriesId).orElse(null);
        if (series == null) {
            renderNotFound(response, contextPath);
            return;
        }

        List<EsMeeting> meetings = meetingDao.findAllBySeriesDesc(seriesId);
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        User viewer = authenticatedUser.orElse(null);
        boolean isAdmin = authenticatedUser.isPresent() && authFlowService.isAdminUser(authenticatedUser.get());

        EsTopic topic = series.getEsTopicId() != null
                ? topicDao.findById(series.getEsTopicId()).orElse(null)
                : null;
        if (topic == null || !topicSpaceAccessService.canViewTopic(viewer, topic)) {
            renderNotFound(response, contextPath);
            return;
        }
        EsTopicSpace topicSpace = topicSpaceDao.findById(topic.getEsTopicSpaceId()).orElse(null);
        String spaceCode = topicSpace == null ? "emerging-standards" : topicSpace.getSpaceCode();
        meetings = topicSpaceAccessService.filterVisibleMeetings(viewer, meetings);

        // Determine timezone for display: prefer user setting, fall back to ET
        String viewerTzId = authenticatedUser
                .map(User::getTimezoneId)
                .filter(tz -> tz != null && ALLOWED_TIMEZONES.contains(tz))
                .orElse("America/New_York");
        ZoneId viewerZone = ZoneId.of(viewerTzId);

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            String seriesName = series.getMeetingName() != null ? series.getMeetingName() : "Meeting Series";
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>" + escapeHtml(seriesName) + " Meetings — InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("  <style>");
            renderStyles(out);
            out.println("  </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<main class=\"mseries-page\">");

            // Header
            out.println("  <div class=\"mseries-header\">");
            out.println("    <div class=\"mseries-breadcrumb\">");
                out.println("      <a href=\"" + contextPath + "/spaces/" + urlEncodePathSegment(spaceCode)
                    + "/topics\">Topics</a>");
            if (topic != null) {
                out.println("      <span class=\"mseries-sep\">&rsaquo;</span>");
                out.println("      <a href=\"" + contextPath + "/spaces/" + urlEncodePathSegment(spaceCode)
                    + "/topic/" + topic.getEsTopicId() + "\">"
                        + escapeHtml(topic.getTopicName() != null ? topic.getTopicName() : "") + "</a>");
            }
            out.println("      <span class=\"mseries-sep\">&rsaquo;</span>");
            out.println("      <span>" + escapeHtml(seriesName) + "</span>");
            out.println("    </div>");
            out.println("    <h1 class=\"mseries-title\">" + escapeHtml(seriesName) + " Meetings</h1>");
            if (series.getMeetingDescription() != null && !series.getMeetingDescription().isBlank()) {
                out.println("    <p class=\"mseries-description\">"
                        + escapeHtml(series.getMeetingDescription()) + "</p>");
            }
            out.println("  </div>");

            if (meetings.isEmpty()) {
                out.println("  <p class=\"mseries-empty\">No meetings scheduled yet.</p>");
            } else {
                LocalDateTime now = LocalDateTime.now();

                // Split into upcoming (scheduledStart >= now) and past
                List<EsMeeting> upcoming = meetings.stream()
                        .filter(m -> m.getScheduledStart() != null && !m.getScheduledStart().isBefore(now))
                        .toList();
                List<EsMeeting> past = meetings.stream()
                        .filter(m -> m.getScheduledStart() == null || m.getScheduledStart().isBefore(now))
                        .toList();

                if (!upcoming.isEmpty()) {
                    // Upcoming is DESC from query, show ASC so nearest is first
                    List<EsMeeting> upcomingAsc = new ArrayList<>(upcoming);
                    Collections.reverse(upcomingAsc);
                    out.println("  <div class=\"mseries-section\">");
                    out.println("    <h2 class=\"mseries-section-heading\">Upcoming</h2>");
                    renderTable(out, upcomingAsc, contextPath, viewerZone, viewerTzId, true, spaceCode);
                    out.println("  </div>");
                }

                if (!past.isEmpty()) {
                    out.println("  <div class=\"mseries-section\">");
                    out.println("    <h2 class=\"mseries-section-heading\">Past Meetings</h2>");
                    renderTable(out, past, contextPath, viewerZone, viewerTzId, false, spaceCode);
                    out.println("  </div>");
                }
            }

            if (isAdmin) {
                out.println("  <div class=\"mseries-admin-bar\">");
                out.println("    Admin: ");
                out.println("    <a href=\"" + contextPath + "/admin/es/meetings?meetingId=" + seriesId
                        + "\">Meeting Admin</a>");
                if (topic != null) {
                    out.println("    &nbsp;&middot;&nbsp;");
                    out.println("    <a href=\"" + contextPath + "/admin/es/topics?esTopicId=" + topic.getEsTopicId()
                            + "\">Topic Admin</a>");
                }
                out.println("  </div>");
            }
            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

        private void renderTable(PrintWriter out, List<EsMeeting> meetings, String contextPath,
            ZoneId viewerZone, String viewerTzId, boolean isUpcoming, String spaceCode) {
        out.println("    <div class=\"mseries-table-wrap\">");
        out.println("    <table class=\"mseries-table\">");
        out.println("      <thead><tr>");
        out.println("        <th>Date &amp; Time</th>");
        out.println("        <th>Meeting Name</th>");
        out.println("        <th>Status</th>");
        out.println("        <th>Agenda</th>");
        out.println("      </tr></thead>");
        out.println("      <tbody>");
        for (EsMeeting m : meetings) {
            String dateStr = "";
            String timeStr = "";
            if (m.getScheduledStart() != null) {
                ZoneId meetingZone = (m.getTimezoneId() != null && ALLOWED_TIMEZONES.contains(m.getTimezoneId()))
                        ? ZoneId.of(m.getTimezoneId())
                        : viewerZone;
                ZonedDateTime display = ZonedDateTime.of(m.getScheduledStart(), meetingZone)
                        .withZoneSameInstant(viewerZone);
                dateStr = DISPLAY_DATE_FMT.format(display);
                timeStr = DISPLAY_TIME_FMT.format(display) + " " + viewerTzId.replace("America/", "").replace("_", " ");
            }
            String statusLabel = statusLabel(m.getStatus());
            String statusCls = statusCssClass(m.getStatus());
            boolean hasAgenda = m.getEsMeetingId() != null;

            out.println("      <tr class=\"mseries-row" + (isUpcoming ? " mseries-row-upcoming" : "") + "\">");
            out.println("        <td class=\"mseries-date\">");
            out.println("          <span class=\"mseries-date-date\">" + escapeHtml(dateStr) + "</span>");
            if (!timeStr.isEmpty()) {
                out.println("          <span class=\"mseries-date-time\">" + escapeHtml(timeStr) + "</span>");
            }
            out.println("        </td>");
            out.println("        <td class=\"mseries-name\">" + escapeHtml(
                    m.getMeetingName() != null ? m.getMeetingName() : "") + "</td>");
            out.println("        <td class=\"mseries-status\"><span class=\"mseries-badge " + statusCls + "\">"
                    + escapeHtml(statusLabel) + "</span></td>");
            out.println("        <td class=\"mseries-agenda\">");
            if (hasAgenda) {
                out.println("          <a href=\"" + contextPath + "/spaces/" + urlEncodePathSegment(spaceCode)
                        + "/meeting/" + m.getEsMeetingId() + "\" class=\"mseries-agenda-link\">View Agenda</a>");
            } else {
                out.println("          <span class=\"mseries-no-agenda\">—</span>");
            }
            out.println("        </td>");
            out.println("      </tr>");
        }
        out.println("      </tbody>");
        out.println("    </table>");
        out.println("    </div>");
    }

    private static String statusLabel(MeetingStatus status) {
        if (status == null)
            return "Unknown";
        return switch (status) {
            case DRAFT -> "Draft";
            case PROPOSED -> "Proposed";
            case FINALIZED -> "Finalized";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    private static String statusCssClass(MeetingStatus status) {
        if (status == null)
            return "mseries-badge-neutral";
        return switch (status) {
            case DRAFT -> "mseries-badge-draft";
            case PROPOSED -> "mseries-badge-proposed";
            case FINALIZED -> "mseries-badge-finalized";
            case COMPLETED -> "mseries-badge-completed";
            case CANCELLED -> "mseries-badge-cancelled";
        };
    }

    private static void renderStyles(PrintWriter out) {
        out.println(
                "    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f6f7f8; margin: 0; color: #0f1720; }");
        out.println("    .mseries-page { max-width: 900px; margin: 0 auto; padding: 1.5rem 1rem 3rem; }");
        out.println("    .mseries-breadcrumb { font-size: 0.85rem; color: #5b6673; margin-bottom: 0.5rem; }");
        out.println("    .mseries-breadcrumb a { color: #2563eb; text-decoration: none; }");
        out.println("    .mseries-breadcrumb a:hover { text-decoration: underline; }");
        out.println("    .mseries-sep { margin: 0 0.35rem; color: #94a3b8; }");
        out.println("    .mseries-title { margin: 0 0 0.4rem; font-size: 1.6rem; font-weight: 700; color: #0f1720; }");
        out.println(
                "    .mseries-description { margin: 0 0 1.25rem; color: #475569; font-size: 0.95rem; max-width: 680px; }");
        out.println(
                "    .mseries-header { border-bottom: 1px solid #e2e8f0; padding-bottom: 1rem; margin-bottom: 1.5rem; }");
        out.println("    .mseries-empty { color: #64748b; font-style: italic; margin-top: 1.5rem; }");
        out.println("    .mseries-section { margin-bottom: 2rem; }");
        out.println(
                "    .mseries-section-heading { font-size: 0.78rem; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 0.06em; margin: 0 0 0.6rem; }");
        out.println(
                "    .mseries-table-wrap { overflow-x: auto; border-radius: 10px; border: 1px solid #e2e8f0; box-shadow: 0 2px 8px rgba(15,23,32,0.05); }");
        out.println(
                "    .mseries-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; background: #fff; }");
        out.println(
                "    .mseries-table th { background: #f1f5f9; color: #475569; font-weight: 600; font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.04em; padding: 0.5rem 0.75rem; border-bottom: 2px solid #e2e8f0; text-align: left; white-space: nowrap; }");
        out.println(
                "    .mseries-table td { padding: 0.6rem 0.75rem; border-bottom: 1px solid #f1f5f9; vertical-align: middle; }");
        out.println("    .mseries-table tr:last-child td { border-bottom: none; }");
        out.println("    .mseries-table tr:hover td { background: #f8fafc; }");
        out.println("    .mseries-row-upcoming td { background: #f0f9ff; }");
        out.println("    .mseries-row-upcoming:hover td { background: #e0f2fe; }");
        out.println("    .mseries-date { white-space: nowrap; }");
        out.println("    .mseries-date-date { display: block; font-weight: 500; }");
        out.println(
                "    .mseries-date-time { display: block; font-size: 0.82rem; color: #64748b; margin-top: 0.1rem; }");
        out.println("    .mseries-name { max-width: 280px; }");
        out.println(
                "    .mseries-badge { font-size: 0.73rem; font-weight: 600; padding: 0.15rem 0.5rem; border-radius: 999px; white-space: nowrap; display: inline-block; border: 1px solid transparent; }");
        out.println("    .mseries-badge-draft { background: #f1f5f9; color: #475569; border-color: #cbd5e1; }");
        out.println("    .mseries-badge-proposed { background: #eff6ff; color: #1d4ed8; border-color: #bfdbfe; }");
        out.println("    .mseries-badge-finalized { background: #f0fdf4; color: #166534; border-color: #86efac; }");
        out.println("    .mseries-badge-completed { background: #f0fdf4; color: #166534; border-color: #86efac; }");
        out.println("    .mseries-badge-cancelled { background: #fee2e2; color: #991b1b; border-color: #fca5a5; }");
        out.println("    .mseries-badge-neutral { background: #f1f5f9; color: #64748b; border-color: #e2e8f0; }");
        out.println(
                "    .mseries-agenda-link { color: #2563eb; text-decoration: none; font-size: 0.85rem; font-weight: 500; }");
        out.println("    .mseries-agenda-link:hover { text-decoration: underline; }");
        out.println("    .mseries-no-agenda { color: #94a3b8; }");
        out.println(
                "    .mseries-admin-bar { margin-top: 1.5rem; padding-top: 1rem; border-top: 1px dashed #e2e8f0; font-size: 0.82rem; }");
        out.println("    .mseries-admin-bar a { color: #64748b; text-decoration: none; }");
        out.println("    .mseries-admin-bar a:hover { text-decoration: underline; color: #334155; }");
    }

    private void renderNotFound(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\" />");
            out.println("<title>Not Found - InteropHub</title>");
            out.println("<link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" /></head>");
            out.println("<body><main class=\"container\">");
            out.println("<h1>Meeting Series Not Found</h1>");
            out.println("<p>The requested meeting series does not exist.</p>");
            out.println("<p><a href=\"" + contextPath + "/es/topics\">Return to Topics</a></p>");
            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("</body></html>");
        }
    }

    private static Long parseId(String raw) {
        if (raw == null)
            return null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String s) {
        if (s == null)
            return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String escapeHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#x27;");
    }

    private static String urlEncodePathSegment(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
