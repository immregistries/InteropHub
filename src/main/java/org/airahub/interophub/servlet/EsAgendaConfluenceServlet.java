package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsAgendaItemPresenterDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.HubSettingDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsAgendaItemPresenter;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeeting.MeetingStatus;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsMeetingAgendaItem.AgendaItemStatus;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.HubSetting;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class EsAgendaConfluenceServlet extends HttpServlet {

    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    private static final DateTimeFormatter DISPLAY_TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private final AuthFlowService authFlowService;
    private final EsMeetingDao meetingDao;
    private final EsMeetingAgendaItemDao agendaItemDao;
    private final EsTopicDao topicDao;
    private final EsAgendaItemPresenterDao presenterDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final UserDao userDao;
    private final HubSettingDao hubSettingDao;

    public EsAgendaConfluenceServlet() {
        this.authFlowService = new AuthFlowService();
        this.meetingDao = new EsMeetingDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.topicDao = new EsTopicDao();
        this.presenterDao = new EsAgendaItemPresenterDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.userDao = new UserDao();
        this.hubSettingDao = new HubSettingDao();
    }

    // =========================================================================
    // GET
    // =========================================================================

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> userOpt = authFlowService.findAuthenticatedUser(request);
        User user = userOpt.orElse(null);

        if (user == null || !authFlowService.isAdminUser(user)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required.");
            return;
        }

        String contextPath = request.getContextPath();

        Long meetingId = parseId(trimToNull(request.getParameter("meetingId")));
        if (meetingId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid meetingId parameter.");
            return;
        }

        EsMeeting meeting = meetingDao.findById(meetingId).orElse(null);
        if (meeting == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Meeting not found.");
            return;
        }

        List<EsMeetingAgendaItem> items = agendaItemDao.findByMeetingIdOrdered(meetingId);

        // Load presenters keyed by agendaItemId
        Map<Long, List<EsAgendaItemPresenter>> presentersByItem = new LinkedHashMap<>();
        Map<Long, User> presenterUsers = new LinkedHashMap<>();
        for (EsMeetingAgendaItem item : items) {
            List<EsAgendaItemPresenter> ps = presenterDao.findByAgendaItemId(item.getEsMeetingAgendaItemId());
            presentersByItem.put(item.getEsMeetingAgendaItemId(), ps);
            for (EsAgendaItemPresenter p : ps) {
                if (p.getUserId() != null && !presenterUsers.containsKey(p.getUserId())) {
                    userDao.findById(p.getUserId()).ifPresent(u -> presenterUsers.put(u.getUserId(), u));
                }
            }
        }

        Map<Long, EsTopic> topicById = topicDao.findAllOrderByTopicName().stream()
                .collect(Collectors.toMap(EsTopic::getEsTopicId, t -> t));

        EsTopicMeeting topicMeeting = meeting.getEsTopicMeetingId() != null
                ? topicMeetingDao.findById(meeting.getEsTopicMeetingId()).orElse(null)
                : null;

        // Resolve the site base URL for building absolute links in the copy region
        String rawBaseUrl = hubSettingDao.findActive()
                .map(HubSetting::getExternalBaseUrl)
                .orElse(null);
        String externalBaseUrl = null;
        if (rawBaseUrl != null) {
            rawBaseUrl = rawBaseUrl.trim();
            if (!rawBaseUrl.isEmpty()) {
                externalBaseUrl = rawBaseUrl.endsWith("/")
                        ? rawBaseUrl.substring(0, rawBaseUrl.length() - 1)
                        : rawBaseUrl;
            }
        }

        EsMeeting nextMeeting = findNextMeeting(meeting);

        renderConfluencePage(response, contextPath, meeting, items, presentersByItem, presenterUsers,
                topicById, topicMeeting, externalBaseUrl, nextMeeting);
    }

    // =========================================================================
    // Page rendering
    // =========================================================================

    private void renderConfluencePage(HttpServletResponse response, String contextPath,
            EsMeeting meeting, List<EsMeetingAgendaItem> items,
            Map<Long, List<EsAgendaItemPresenter>> presentersByItem,
            Map<Long, User> presenterUsers,
            Map<Long, EsTopic> topicById,
            EsTopicMeeting topicMeeting,
            String externalBaseUrl,
            EsMeeting nextMeeting) throws IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        ZoneId meetingZone = safeZoneId(meeting.getTimezoneId(), "America/New_York");
        ZonedDateTime displayStart = meeting.getScheduledStart() != null
                ? ZonedDateTime.of(meeting.getScheduledStart(), meetingZone)
                : null;
        ZonedDateTime displayEnd = meeting.getScheduledEnd() != null
                ? ZonedDateTime.of(meeting.getScheduledEnd(), meetingZone)
                : null;

        String dateDisplay = displayStart != null ? DISPLAY_DATE_FMT.format(displayStart) : "";
        String startTimeDisplay = displayStart != null ? DISPLAY_TIME_FMT.format(displayStart) : "";
        String endTimeDisplay = displayEnd != null ? DISPLAY_TIME_FMT.format(displayEnd) : "";
        String tzDisplay = orEmpty(meeting.getTimezoneId());

        String seriesName = topicMeeting != null ? topicMeeting.getMeetingName() : null;
        boolean hasDescription = meeting.getMeetingDescription() != null
                && !meeting.getMeetingDescription().isBlank();
        boolean hasOnlineMeetingUrl = meeting.getOnlineMeetingUrl() != null
                && !meeting.getOnlineMeetingUrl().isBlank();

        // Prefer absolute base URL for links inside the copy region so that they
        // resolve correctly when the content is pasted into HL7 Confluence.
        String linkBase = externalBaseUrl != null ? externalBaseUrl : contextPath;

        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("  <meta charset=\"UTF-8\">");
        out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        out.println("  <title>Confluence Export \u2013 " + escapeHtml(orEmpty(meeting.getMeetingName())) + "</title>");
        out.println("  <style>");
        out.println(
                "    body { font-family: sans-serif; max-width: 960px; margin: 2rem auto; padding: 0 1rem; color: #222; }");
        out.println(
                "    .confluence-admin-bar { background: #f0f4ff; border: 1px solid #b8c8e8; border-radius: 4px; padding: 0.75rem 1rem; margin-bottom: 1.25rem; display: flex; align-items: center; gap: 1rem; font-size: 0.875rem; }");
        out.println("    #select-btn { padding: 0.3rem 0.75rem; cursor: pointer; white-space: nowrap; }");
        out.println("  </style>");
        out.println("</head>");
        out.println("<body>");

        // Admin instruction bar — outside the copy region
        out.println("  <div class=\"confluence-admin-bar\">");
        out.println("    <span>Copy the agenda content below and paste it into Confluence.</span>");
        out.println(
                "    <button id=\"select-btn\" onclick=\"selectConfluenceContent()\">Select agenda content</button>");
        out.println("  </div>");

        out.println("  <div id=\"confluence-copy-region\">");

        // Meeting title
        out.println("    <h2>" + escapeHtml(orEmpty(meeting.getMeetingName())) + "</h2>");

        // Date
        if (!dateDisplay.isEmpty()) {
            out.println("    <p>" + escapeHtml(dateDisplay) + "</p>");
        }

        // Time
        if (!startTimeDisplay.isEmpty()) {
            StringBuilder timeLine = new StringBuilder(startTimeDisplay);
            if (!endTimeDisplay.isEmpty()) {
                timeLine.append("\u2013").append(endTimeDisplay);
            }
            if (!tzDisplay.isEmpty()) {
                timeLine.append(" ").append(tzDisplay);
            }
            out.println("    <p>" + escapeHtml(timeLine.toString()) + "</p>");
        }

        // Meeting description
        if (hasDescription) {
            out.println("    <p>" + renderPlainText(meeting.getMeetingDescription()) + "</p>");
        }

        // Join Online link — no passcode/connection details included
        if (hasOnlineMeetingUrl) {
            out.println("    <p><a href=\"" + escapeHtml(meeting.getOnlineMeetingUrl())
                    + "\" target=\"_blank\" rel=\"noopener noreferrer\">Join Online</a></p>");
        }

        // Agenda table
        out.println("    <table style=\"border-collapse: collapse; width: 100%;\">");
        out.println("      <thead>");
        out.println("        <tr>");
        out.println(
                "          <th style=\"border: 1px solid #ccc; padding: 6px 10px; background: #f5f5f5; text-align: left; white-space: nowrap;\">Topic / Time</th>");
        out.println(
                "          <th style=\"border: 1px solid #ccc; padding: 6px 10px; background: #f5f5f5; text-align: left;\">Agenda</th>");
        out.println(
                "          <th style=\"border: 1px solid #ccc; padding: 6px 10px; background: #f5f5f5; text-align: left; white-space: nowrap;\">Presenter(s)</th>");
        out.println("        </tr>");
        out.println("      </thead>");
        out.println("      <tbody>");

        // Time cursor: start from meeting scheduledStart in meeting timezone.
        // Advances by each item's timeMinutes (skipping POSTPONED), mirroring the
        // display logic in the main agenda view.
        LocalDateTime cursor = meeting.getScheduledStart();

        for (EsMeetingAgendaItem item : items) {
            if (item.getStatus() == AgendaItemStatus.POSTPONED) {
                continue;
            }

            // Compute time range for this item and advance the cursor
            String itemTimeRange = "";
            if (cursor != null) {
                ZonedDateTime itemStart = ZonedDateTime.of(cursor, meetingZone);
                int itemMinutes = item.getTimeMinutes() != null ? item.getTimeMinutes() : 0;
                ZonedDateTime itemEnd = itemStart.plusMinutes(itemMinutes);
                itemTimeRange = DISPLAY_TIME_FMT.format(itemStart) + "\u2013" + DISPLAY_TIME_FMT.format(itemEnd);
                cursor = cursor.plusMinutes(itemMinutes);
            }

            // Topic / Time cell
            Long topicId = item.getEsTopicId();
            String titleText = orEmpty(item.getTitle());
            String topicCellContent;
            if (topicId != null && topicById.containsKey(topicId) && externalBaseUrl != null) {
                // Only link when we have an absolute base URL so the link works in Confluence
                String topicUrl = externalBaseUrl + "/es/topic/" + topicId;
                topicCellContent = "<a href=\"" + escapeHtml(topicUrl) + "\">"
                        + escapeHtml(titleText) + "</a>";
            } else {
                topicCellContent = "<strong>" + escapeHtml(titleText) + "</strong>";
            }
            if (!itemTimeRange.isEmpty()) {
                topicCellContent += "<br><span style=\"font-size: 0.85em; color: #555;\">"
                        + escapeHtml(itemTimeRange) + "</span>";
            }

            // Agenda cell
            String agendaCellContent = item.getAgendaMarkdown() != null && !item.getAgendaMarkdown().isBlank()
                    ? renderPlainText(item.getAgendaMarkdown())
                    : "";

            // Presenter(s) cell — ACCEPTED and INVITED only; DECLINED/REMOVED excluded
            List<EsAgendaItemPresenter> presenters = presentersByItem.getOrDefault(
                    item.getEsMeetingAgendaItemId(), List.of());
            StringBuilder presenterCellContent = new StringBuilder();
            for (EsAgendaItemPresenter p : presenters) {
                if (p.getStatus() == EsAgendaItemPresenter.PresenterStatus.DECLINED
                        || p.getStatus() == EsAgendaItemPresenter.PresenterStatus.REMOVED) {
                    continue;
                }
                if (presenterCellContent.length() > 0) {
                    presenterCellContent.append("<br>");
                }
                presenterCellContent.append(escapeHtml(presenterDisplayName(p, presenterUsers)));
            }

            out.println("        <tr>");
            out.println("          <td style=\"border: 1px solid #ccc; padding: 6px 10px; vertical-align: top;\">"
                    + topicCellContent + "</td>");
            out.println("          <td style=\"border: 1px solid #ccc; padding: 6px 10px; vertical-align: top;\">"
                    + agendaCellContent + "</td>");
            out.println("          <td style=\"border: 1px solid #ccc; padding: 6px 10px; vertical-align: top;\">"
                    + presenterCellContent + "</td>");
            out.println("        </tr>");
        }

        out.println("      </tbody>");
        out.println("    </table>");

        // Next meeting
        if (nextMeeting != null && nextMeeting.getScheduledStart() != null) {
            String nextLabel = seriesName != null ? "Next " + seriesName + " Meeting" : "Next Meeting";
            ZoneId nextZone = safeZoneId(nextMeeting.getTimezoneId(), "America/New_York");
            ZonedDateTime nextStart = ZonedDateTime.of(nextMeeting.getScheduledStart(), nextZone);
            String nextDateStr = DISPLAY_DATE_FMT.format(nextStart);
            String nextTimeStr = DISPLAY_TIME_FMT.format(nextStart) + " " + orEmpty(nextMeeting.getTimezoneId());
            String nextUrl = linkBase + "/es/agenda?meetingId=" + nextMeeting.getEsMeetingId();
            out.println("    <p>" + escapeHtml(nextLabel) + ": <a href=\"" + escapeHtml(nextUrl) + "\">"
                    + escapeHtml(nextDateStr) + ", at " + escapeHtml(nextTimeStr) + "</a></p>");
        }

        // All meetings link
        if (meeting.getEsTopicMeetingId() != null) {
            String allLabel = seriesName != null ? "All " + seriesName + " Meetings" : "All Meetings";
            String allUrl = linkBase + "/es/meetings?seriesId=" + meeting.getEsTopicMeetingId();
            out.println("    <p><a href=\"" + escapeHtml(allUrl) + "\">" + escapeHtml(allLabel) + "</a></p>");
        }

        out.println("  </div>"); // end confluence-copy-region

        // Select-all button script
        out.println("  <script>");
        out.println("  function selectConfluenceContent() {");
        out.println("    var el = document.getElementById('confluence-copy-region');");
        out.println("    if (window.getSelection && el) {");
        out.println("      var sel = window.getSelection();");
        out.println("      var range = document.createRange();");
        out.println("      range.selectNodeContents(el);");
        out.println("      sel.removeAllRanges();");
        out.println("      sel.addRange(range);");
        out.println("    }");
        out.println("  }");
        out.println("  </script>");

        PageFooterRenderer.render(out);
        out.println("</body>");
        out.println("</html>");
    }

    // =========================================================================
    // Helpers (duplicated from EsAgendaServlet — private in that class)
    // =========================================================================

    private EsMeeting findNextMeeting(EsMeeting current) {
        if (current.getScheduledStart() == null || current.getEsTopicMeetingId() == null) {
            return null;
        }
        List<EsMeeting> siblings = meetingDao.findByEsTopicMeetingId(current.getEsTopicMeetingId());
        return siblings.stream()
                .filter(m -> !m.getEsMeetingId().equals(current.getEsMeetingId()))
                .filter(m -> m.getStatus() != MeetingStatus.CANCELLED)
                .filter(m -> m.getScheduledStart() != null
                        && m.getScheduledStart().isAfter(current.getScheduledStart()))
                .min((a, b) -> a.getScheduledStart().compareTo(b.getScheduledStart()))
                .orElse(null);
    }

    private String presenterDisplayName(EsAgendaItemPresenter p, Map<Long, User> presenterUsers) {
        if (p.getDisplayName() != null && !p.getDisplayName().isBlank()) {
            return p.getDisplayName();
        }
        if (p.getUserId() != null) {
            User u = presenterUsers.get(p.getUserId());
            if (u != null && u.getFullName() != null && !u.getFullName().isBlank()) {
                return u.getFullName();
            }
        }
        return orEmpty(p.getEmail());
    }

    private String renderPlainText(String text) {
        if (text == null)
            return "";
        return escapeHtml(text).replace("\n", "<br>");
    }

    private ZoneId safeZoneId(String tzId, String fallback) {
        if (tzId != null && !tzId.isBlank()) {
            try {
                return ZoneId.of(tzId);
            } catch (Exception ex) {
                // fall through
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            try {
                return ZoneId.of(fallback);
            } catch (Exception ex) {
                // fall through
            }
        }
        return ZoneId.of("America/New_York");
    }

    private static Long parseId(String value) {
        if (value == null)
            return null;
        try {
            long v = Long.parseLong(value);
            return v > 0 ? v : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escapeHtml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
