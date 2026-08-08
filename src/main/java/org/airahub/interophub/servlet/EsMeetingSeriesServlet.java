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
import org.airahub.interophub.service.EsTopicViewHistoryService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.immregistries.aira.web.AiraPage;

/**
 * Public-facing list of all meetings for a given meeting series
 * (es_topic_meeting). Accessible at /es/meeting-series?seriesId=X.
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
    private final EsTopicViewHistoryService topicViewHistoryService;

    public EsMeetingSeriesServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.meetingDao = new EsMeetingDao();
        this.topicDao = new EsTopicDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
        this.topicViewHistoryService = new EsTopicViewHistoryService();
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
            renderNotFound(request, response, contextPath);
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
            renderNotFound(request, response, contextPath);
            return;
        }
        EsTopicSpace topicSpace = topicSpaceDao.findById(topic.getEsTopicSpaceId()).orElse(null);
        String spaceCode = topicSpace == null ? "emerging-standards" : topicSpace.getSpaceCode();
        String spaceName = topicSpace == null ? "InteropHub" : topicSpace.getSpaceName();
        meetings = topicSpaceAccessService.filterVisibleMeetings(viewer, meetings);

        // Determine timezone for display: prefer user setting, fall back to ET
        String viewerTzId = authenticatedUser
                .map(User::getTimezoneId)
                .filter(tz -> tz != null && ALLOWED_TIMEZONES.contains(tz))
                .orElse("America/New_York");
        ZoneId viewerZone = ZoneId.of(viewerTzId);

        String seriesName = series.getMeetingName() != null ? series.getMeetingName() : "Meeting Series";
        String topicName = topic.getTopicName() != null ? topic.getTopicName() : "Topic";

        List<EsTopicViewHistoryService.RecentlyViewedTopic> recentlyViewedTopics = RecentlyViewedTopicsRenderer
                .fetchVisible(topicViewHistoryService, topicSpaceAccessService, viewer);

        AiraPage page = InteropAiraPageFactory.base(request, seriesName + " Meetings - InteropHub")
                .applicationSubtitle("Meetings")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.topicsMeetingsContext(spaceName, spaceCode, false, true))
                .build();

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container--wide aira-stack\">");
            out.println("      <div class=\"aira-right-rail-layout\">");
            out.println("        <div class=\"aira-stack\">");

            out.println("          <div class=\"aira-page-header\">");
            out.println("            <div>");
            out.println("              <h1 class=\"aira-page-title\">" + escapeHtml(seriesName) + " Meetings</h1>");
            if (series.getMeetingDescription() != null && !series.getMeetingDescription().isBlank()) {
                out.println("              <p class=\"aira-page-intro\">"
                        + escapeHtml(series.getMeetingDescription()) + "</p>");
            }
            out.println("            </div>");
            out.println("            <div class=\"aira-action-group\">");
            out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                    + "/es/topic/" + topic.getEsTopicId() + "\">&larr; Back to " + escapeHtml(topicName) + "</a>");
            out.println("            </div>");
            out.println("          </div>");

            if (meetings.isEmpty()) {
                out.println("          <div class=\"aira-empty-state\">");
                out.println("            <p class=\"aira-empty-state__title\">No meetings scheduled yet.</p>");
                out.println("          </div>");
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
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Upcoming</h2>");
                    renderTable(out, upcomingAsc, contextPath, viewerZone, viewerTzId);
                    out.println("          </section>");
                }

                if (!past.isEmpty()) {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Past Meetings</h2>");
                    renderTable(out, past, contextPath, viewerZone, viewerTzId);
                    out.println("          </section>");
                }
            }

            out.println("        </div>");

            out.println("        <aside class=\"aira-right-rail\" aria-label=\"Meeting series activity\">");
            RecentlyViewedTopicsRenderer.render(out, topic.getEsTopicId(), recentlyViewedTopics, viewer != null,
                    otherTopicId -> contextPath + "/es/topic/" + otherTopicId);
            if (isAdmin) {
                out.println("          <section class=\"aira-section-card\">");
                out.println(
                        "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Admin</h2></div>");
                out.println("            <div class=\"aira-section-card__body\">");
                out.println("              <nav class=\"aira-sidebar-nav\" aria-label=\"Meeting series admin links\">");
                out.println("                <a class=\"aira-sidebar-link\" href=\"" + contextPath
                        + "/admin/es/meetings?meetingId=" + seriesId + "\">Meeting Admin</a>");
                out.println("                <a class=\"aira-sidebar-link\" href=\"" + contextPath
                        + "/admin/es/meeting-polls\">Meeting Polls</a>");
                out.println("                <a class=\"aira-sidebar-link\" href=\"" + contextPath
                        + "/admin/es/meeting-survey\">Meeting Surveys</a>");
                out.println("              </nav>");
                out.println("            </div>");
                out.println("          </section>");
            }
            out.println("        </aside>");

            out.println("      </div>");
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    private void renderTable(PrintWriter out, List<EsMeeting> meetings, String contextPath,
            ZoneId viewerZone, String viewerTzId) {
        out.println("        <div class=\"aira-table-wrap\">");
        out.println("        <table class=\"aira-table\">");
        out.println("          <thead><tr>");
        out.println("            <th>Date &amp; Time</th>");
        out.println("            <th>Meeting Name</th>");
        out.println("            <th>Status</th>");
        out.println("            <th class=\"aira-table__cell--actions\">Agenda</th>");
        out.println("          </tr></thead>");
        out.println("          <tbody>");
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
            String statusVariant = statusBadgeVariant(m.getStatus());

            out.println("          <tr>");
            out.println("            <td class=\"aira-table__cell--date\">");
            out.println("              " + escapeHtml(dateStr));
            if (!timeStr.isEmpty()) {
                out.println("              <div class=\"aira-meta\">" + escapeHtml(timeStr) + "</div>");
            }
            out.println("            </td>");
            out.println("            <td class=\"aira-table__cell--primary\">" + escapeHtml(
                    m.getMeetingName() != null ? m.getMeetingName() : "") + "</td>");
            out.println("            <td><span class=\"aira-badge " + statusVariant + "\">"
                    + escapeHtml(statusLabel) + "</span></td>");
            out.println("            <td class=\"aira-table__cell--actions\">");
            out.println("              <a href=\"" + contextPath + "/es/agenda?meetingId=" + m.getEsMeetingId()
                    + "\" class=\"aira-button aira-button--tertiary aira-button--small\">View Agenda</a>");
            out.println("            </td>");
            out.println("          </tr>");
        }
        out.println("          </tbody>");
        out.println("        </table>");
        out.println("        </div>");
    }

    private static String statusLabel(MeetingStatus status) {
        if (status == null)
            return "Unknown";
        return switch (status) {
            case DRAFT -> "Draft";
            case PROPOSED -> "Proposed";
            case FINALIZED -> "Finalized";
            case IN_SESSION -> "In Session";
            case COMPLETED -> "Completed";
            case CLOSED -> "Closed";
            case CANCELLED -> "Cancelled";
        };
    }

    private static String statusBadgeVariant(MeetingStatus status) {
        if (status == null)
            return "aira-badge--subtle";
        return switch (status) {
            case DRAFT -> "aira-badge--outline";
            case PROPOSED -> "aira-badge--info";
            case FINALIZED -> "aira-badge--success";
            case IN_SESSION -> "aira-badge--warning";
            case COMPLETED -> "aira-badge--success";
            case CLOSED -> "aira-badge--subtle";
            case CANCELLED -> "aira-badge--danger";
        };
    }

    private void renderNotFound(HttpServletRequest request, HttpServletResponse response, String contextPath)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AiraPage page = InteropAiraPageFactory.base(request, "Meeting Series Not Found - InteropHub")
                    .applicationSubtitle("Meetings")
                    .mainClass("aira-main")
                    .build();
            page.writeStart(out);
            out.println("    <div class=\"aira-container--narrow aira-stack aira-stack--compact\">");
            out.println("      <section class=\"aira-alert aira-alert--warning\" role=\"status\" aria-live=\"polite\">");
            out.println("        <p class=\"aira-alert__title\">Meeting series not found</p>");
            out.println("        <p>The requested meeting series does not exist.</p>");
            out.println("        <p><a class=\"aira-inline-link\" href=\"" + contextPath
                    + "/es/topics\">Return to Topics</a></p>");
            out.println("      </section>");
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
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
}
