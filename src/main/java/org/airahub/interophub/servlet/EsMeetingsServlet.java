package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsMeetingViewHistoryService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.immregistries.aira.web.AiraPage;

/**
 * Meeting-centered view of a Topic Space: a month calendar plus search,
 * My Meetings, and Past Meetings views. See
 * docs/InteropHub_Meetings_Page_Design.md.
 */
public class EsMeetingsServlet extends HttpServlet {

    private static final String VIEW_MY = "my";
    private static final String VIEW_PAST = "past";
    private static final String DEFAULT_SPACE_CODE = "emerging-standards";
    private static final String DEFAULT_TIMEZONE = "America/New_York";
    private static final int LIST_LIMIT = 12;
    private static final int UPCOMING_SIDEBAR_LIMIT = 5;
    private static final int RECENTLY_VIEWED_LIMIT = 8;
    private static final int MAX_VISIBLE_CARDS_PER_DAY = 3;

    private static final Set<String> ALLOWED_TIMEZONES = Set.of(
            "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
            "America/Phoenix", "America/Anchorage", "Pacific/Honolulu",
            "America/Sao_Paulo", "America/Santiago",
            "Europe/London", "Europe/Paris",
            "Africa/Johannesburg",
            "Asia/Kolkata", "Asia/Tokyo",
            "Australia/Sydney",
            "Pacific/Auckland");

    private static final Map<String, String> TIMEZONE_LABELS = buildTimezoneLabels();

    private static final String SESSION_ATTR_TIMEZONE = "interophub.meetings.timezoneId";
    private static final String SESSION_ATTR_WEEK_START = "interophub.meetings.weekStartDay";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_PARAM_FMT = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH);

    private final AuthFlowService authFlowService;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsMeetingDao meetingDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsTopicMeetingMemberDao topicMeetingMemberDao;
    private final UserDao userDao;
    private final TopicSpaceAccessService topicSpaceAccessService;
    private final EsMeetingViewHistoryService meetingViewHistoryService;

    public EsMeetingsServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.meetingDao = new EsMeetingDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
        this.userDao = new UserDao();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
        this.meetingViewHistoryService = new EsMeetingViewHistoryService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();
        String requestedSpaceCode = trimToNull(request.getParameter("space"));
        String view = trimToNull(request.getParameter("view"));
        String query = trimToNull(request.getParameter("q"));
        String monthParam = trimToNull(request.getParameter("month"));
        String tzChange = trimToNull(request.getParameter("tz"));
        String weekStartChange = trimToNull(request.getParameter("weekStart"));

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        User viewer = authenticatedUser.orElse(null);

        List<EsTopicSpace> visibleSpaces = topicSpaceAccessService
                .filterVisibleSpaces(viewer, topicSpaceDao.findAllActiveOrdered());
        EsTopicSpace selectedSpace = findSelectedSpace(visibleSpaces, requestedSpaceCode);
        if (selectedSpace == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.println("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\" />"
                        + "<title>Topic Spaces Not Found - InteropHub</title></head>"
                        + "<body><p>No visible Topic Spaces are available.</p></body></html>");
            }
            return;
        }
        String selectedSpaceCode = selectedSpace.getSpaceCode();
        Long selectedSpaceId = selectedSpace.getEsTopicSpaceId();

        HttpSession session = request.getSession(true);

        // Preference changes: apply, then redirect to a clean URL (PRG pattern) so
        // the change isn't re-applied on reload/back and doesn't linger in the URL.
        if (tzChange != null) {
            if (!ALLOWED_TIMEZONES.contains(tzChange)) {
                redirectToCleanUrl(response, contextPath, selectedSpaceCode, view, query, monthParam, null);
                return;
            }
            String flash = null;
            if (viewer != null) {
                viewer.setTimezoneId(tzChange);
                userDao.saveOrUpdate(viewer);
                flash = "tz";
            } else {
                session.setAttribute(SESSION_ATTR_TIMEZONE, tzChange);
            }
            redirectToCleanUrl(response, contextPath, selectedSpaceCode, view, query, monthParam, flash);
            return;
        }
        if (weekStartChange != null) {
            User.WeekStartDay parsed = "monday".equalsIgnoreCase(weekStartChange) ? User.WeekStartDay.MONDAY
                    : "sunday".equalsIgnoreCase(weekStartChange) ? User.WeekStartDay.SUNDAY : null;
            if (parsed == null) {
                redirectToCleanUrl(response, contextPath, selectedSpaceCode, view, query, monthParam, null);
                return;
            }
            String flash = null;
            if (viewer != null) {
                viewer.setWeekStartDay(parsed);
                userDao.saveOrUpdate(viewer);
                flash = "week";
            } else {
                session.setAttribute(SESSION_ATTR_WEEK_START, parsed.name());
            }
            redirectToCleanUrl(response, contextPath, selectedSpaceCode, view, query, monthParam, flash);
            return;
        }

        String flashParam = trimToNull(request.getParameter("flash"));
        String effectiveTzId = resolveEffectiveTimezone(viewer, session);
        DayOfWeek effectiveWeekStart = resolveEffectiveWeekStart(viewer, session);
        ZoneId viewerZone = ZoneId.of(effectiveTzId);

        List<EsMeeting> spaceMeetings = topicSpaceAccessService
                .filterVisibleMeetings(viewer, meetingDao.findByTopicSpaceId(selectedSpaceId));

        List<Long> seriesIds = new ArrayList<>(spaceMeetings.stream()
                .map(EsMeeting::getEsTopicMeetingId)
                .filter(java.util.Objects::nonNull)
                .collect(LinkedHashSet::new, Set::add, Set::addAll));
        Map<Long, EsTopicMeeting> seriesById = new LinkedHashMap<>();
        for (EsTopicMeeting series : topicMeetingDao.findByIds(seriesIds)) {
            seriesById.put(series.getEsTopicMeetingId(), series);
        }

        Long authenticatedUserId = viewer == null ? null : viewer.getUserId();
        String authenticatedEmailNormalized = viewer == null ? null : trimToNull(viewer.getEmailNormalized());
        Set<Long> followedSeriesIds = new LinkedHashSet<>();
        if (viewer != null) {
            for (EsTopicMeetingMember member : topicMeetingMemberDao.findByMeetingIdsAndUserOrEmail(
                    seriesIds, authenticatedUserId, authenticatedEmailNormalized)) {
                if (member.getMembershipStatus() == EsTopicMeetingMember.MembershipStatus.REQUESTED
                        || member.getMembershipStatus() == EsTopicMeetingMember.MembershipStatus.APPROVED) {
                    followedSeriesIds.add(member.getEsTopicMeetingId());
                }
            }
        }

        List<Occurrence> occurrences = new ArrayList<>();
        for (EsMeeting meeting : spaceMeetings) {
            if (meeting.getScheduledStart() == null) {
                continue;
            }
            EsTopicMeeting series = seriesById.get(meeting.getEsTopicMeetingId());
            ZoneId meetingZone = (meeting.getTimezoneId() != null && ALLOWED_TIMEZONES.contains(meeting.getTimezoneId()))
                    ? ZoneId.of(meeting.getTimezoneId())
                    : viewerZone;
            ZonedDateTime display = ZonedDateTime.of(meeting.getScheduledStart(), meetingZone)
                    .withZoneSameInstant(viewerZone);
            boolean following = meeting.getEsTopicMeetingId() != null
                    && followedSeriesIds.contains(meeting.getEsTopicMeetingId());
            occurrences.add(new Occurrence(meeting, series, display, following));
        }
        occurrences.sort(Comparator.comparing(Occurrence::display));

        boolean listMode = query != null || VIEW_MY.equalsIgnoreCase(view) || VIEW_PAST.equalsIgnoreCase(view);

        response.setContentType("text/html;charset=UTF-8");
        AiraPage page = InteropAiraPageFactory.base(request, selectedSpace.getSpaceName() + " Meetings - InteropHub")
                .applicationSubtitle("Meetings")
                .mainClass("aira-main")
                .addLocalStylesheet("/css/meetings-calendar.css")
                .context(InteropAiraPageFactory.topicsMeetingsContext(
                        selectedSpace.getSpaceName(),
                        selectedSpaceCode,
                        false,
                        true))
                .build();

        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container--wide aira-stack\">");
            out.println("      <div class=\"aira-page-header\">");
            out.println("        <div>");
            out.println("          <h1 class=\"aira-page-title\">" + escapeHtml(selectedSpace.getSpaceName())
                    + " Meetings</h1>");
            out.println("        </div>");
            out.println("      </div>");

            out.println("      <div class=\"aira-right-rail-layout\">");
            out.println("        <div class=\"aira-stack\">");

            if (flashParam != null && viewer != null) {
                renderFlashConfirmation(out, flashParam);
            }

            renderPreferenceControls(out, contextPath, selectedSpaceCode, view, query, monthParam,
                    effectiveTzId, effectiveWeekStart);

            if (listMode) {
                renderListMode(out, contextPath, selectedSpaceCode, occurrences, view, query, viewer != null);
            } else {
                renderCalendar(out, contextPath, selectedSpaceCode, occurrences, monthParam, effectiveWeekStart,
                        viewerZone);
            }

            out.println("        </div>");

            renderRightRail(out, contextPath, selectedSpaceCode, view, query, monthParam, occurrences,
                    viewer, viewerZone);

            out.println("      </div>");
            out.println("    </div>");
            page.writeEnd(out);
        }
    }

    // =========================================================================
    // Calendar (month grid) view
    // =========================================================================

    private void renderCalendar(PrintWriter out, String contextPath, String spaceCode, List<Occurrence> occurrences,
            String monthParam, DayOfWeek weekStart, ZoneId viewerZone) {
        YearMonth displayedMonth = parseMonth(monthParam, viewerZone);
        LocalDate today = LocalDate.now(viewerZone);

        Map<LocalDate, List<Occurrence>> byDate = new LinkedHashMap<>();
        for (Occurrence occurrence : occurrences) {
            LocalDate date = occurrence.display().toLocalDate();
            if (!YearMonth.from(date).equals(displayedMonth)) {
                continue;
            }
            byDate.computeIfAbsent(date, d -> new ArrayList<>()).add(occurrence);
        }

        out.println("          <section class=\"aira-panel\">");
        renderMonthNav(out, contextPath, spaceCode, displayedMonth, viewerZone);

        LocalDate firstOfMonth = displayedMonth.atDay(1);
        LocalDate gridStart = firstOfMonth;
        while (gridStart.getDayOfWeek() != weekStart) {
            gridStart = gridStart.minusDays(1);
        }
        LocalDate lastOfMonth = displayedMonth.atEndOfMonth();
        LocalDate gridEnd = lastOfMonth;
        DayOfWeek lastColumn = weekStart.plus(6);
        while (gridEnd.getDayOfWeek() != lastColumn) {
            gridEnd = gridEnd.plusDays(1);
        }

        if (byDate.isEmpty()) {
            out.println(
                    "            <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No meetings are scheduled in this Topic Space for this month.</p></div>");
        }

        out.println("            <div class=\"es-meetings-calendar\">");
        for (int i = 0; i < 7; i++) {
            DayOfWeek column = weekStart.plus(i);
            out.println("              <div class=\"es-meetings-calendar__weekday\">"
                    + column.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + "</div>");
        }
        for (LocalDate date = gridStart; !date.isAfter(gridEnd); date = date.plusDays(1)) {
            boolean outsideMonth = !YearMonth.from(date).equals(displayedMonth);
            boolean isToday = date.equals(today);
            String cellClass = "es-meetings-calendar__day"
                    + (outsideMonth ? " es-meetings-calendar__day--outside" : "")
                    + (isToday ? " es-meetings-calendar__day--today" : "");
            out.println("              <div class=\"" + cellClass + "\">");
            out.println("                <span class=\"es-meetings-calendar__date\">" + date.getDayOfMonth()
                    + "</span>");
            List<Occurrence> dayOccurrences = byDate.getOrDefault(date, List.of());
            int shown = 0;
            for (Occurrence occurrence : dayOccurrences) {
                if (shown >= MAX_VISIBLE_CARDS_PER_DAY) {
                    break;
                }
                renderMeetingCard(out, contextPath, occurrence, today);
                shown++;
            }
            if (dayOccurrences.size() > shown) {
                int remaining = dayOccurrences.size() - shown;
                out.println("                <details class=\"es-meetings-calendar__more\">");
                out.println("                  <summary>+" + remaining + " more</summary>");
                for (int i = shown; i < dayOccurrences.size(); i++) {
                    renderMeetingCard(out, contextPath, dayOccurrences.get(i), today);
                }
                out.println("                </details>");
            }
            out.println("              </div>");
        }
        out.println("            </div>");
        out.println("          </section>");
    }

    private void renderMonthNav(PrintWriter out, String contextPath, String spaceCode, YearMonth displayedMonth,
            ZoneId viewerZone) {
        YearMonth previous = displayedMonth.minusMonths(1);
        YearMonth next = displayedMonth.plusMonths(1);
        YearMonth current = YearMonth.now(viewerZone);

        out.println("            <div class=\"aira-cluster aira-cluster--between\">");
        out.println("              <div class=\"aira-cluster\">");
        out.println("                <a class=\"aira-button aira-button--secondary aira-button--small\" href=\""
                + buildMeetingsUrl(contextPath, spaceCode, null, null, MONTH_PARAM_FMT.format(previous))
                + "\" aria-label=\"Previous month\">&laquo; Prev</a>");
        out.println("                <span class=\"aira-section-title\" style=\"margin:0;\">"
                + MONTH_FMT.format(displayedMonth.atDay(1)) + "</span>");
        out.println("                <a class=\"aira-button aira-button--secondary aira-button--small\" href=\""
                + buildMeetingsUrl(contextPath, spaceCode, null, null, MONTH_PARAM_FMT.format(next))
                + "\" aria-label=\"Next month\">Next &raquo;</a>");
        if (!displayedMonth.equals(current)) {
            out.println("                <a class=\"aira-button aira-button--tertiary aira-button--small\" href=\""
                    + buildMeetingsUrl(contextPath, spaceCode, null, null, null) + "\">Today</a>");
        }
        out.println("              </div>");
        out.println("            </div>");
    }

    private void renderMeetingCard(PrintWriter out, String contextPath, Occurrence occurrence, LocalDate today) {
        EsMeeting meeting = occurrence.meeting();
        boolean cancelled = meeting.getStatus() == EsMeeting.MeetingStatus.CANCELLED;
        boolean past = occurrence.display().toLocalDate().isBefore(today);
        String cssClass = "es-meetings-calendar__card"
                + (occurrence.following() ? " es-meetings-calendar__card--following" : "")
                + (past ? " es-meetings-calendar__card--past" : "")
                + (cancelled ? " es-meetings-calendar__card--cancelled" : "");

        String accessibleName = TIME_FMT.format(occurrence.display()) + " " + DATE_FMT.format(occurrence.display())
                + ", " + orEmpty(meeting.getMeetingName())
                + (cancelled ? " (Cancelled)" : "")
                + (occurrence.following() ? " (Following)" : "");

        out.println("                <a class=\"" + cssClass + "\" href=\"" + contextPath + "/es/agenda?meetingId="
                + meeting.getEsMeetingId() + "\" aria-label=\"" + escapeHtml(accessibleName) + "\">");
        out.println("                  <span class=\"es-meetings-calendar__card-time\">"
                + TIME_FMT.format(occurrence.display()) + "</span>");
        out.println("                  <span class=\"es-meetings-calendar__card-title\">"
                + escapeHtml(orEmpty(meeting.getMeetingName())) + "</span>");
        if (cancelled) {
            out.println(
                    "                  <span class=\"es-meetings-calendar__card-cancelled-label\">Cancelled</span>");
        }
        out.println("                </a>");
    }

    // =========================================================================
    // List mode (search / My Meetings / Past Meetings)
    // =========================================================================

    private void renderListMode(PrintWriter out, String contextPath, String spaceCode, List<Occurrence> occurrences,
            String view, String query, boolean authenticated) {
        boolean myView = VIEW_MY.equalsIgnoreCase(view) && query == null;

        out.println("          <section class=\"aira-panel\">");

        if (myView && !authenticated) {
            out.println("            <div class=\"aira-alert aira-alert--info\">");
            out.println("              <p>Sign in to follow meetings and see them here.</p>");
            out.println("              <p><a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                    + "/home\">Sign in</a></p>");
            out.println("            </div>");
            out.println("          </section>");
            return;
        }

        List<Occurrence> filtered = occurrences;
        if (query != null) {
            String search = query.toLowerCase(Locale.ROOT);
            filtered = filtered.stream()
                    .filter(o -> ((orEmpty(o.meeting().getMeetingName()) + " "
                            + orEmpty(o.meeting().getMeetingDescription()))
                            .toLowerCase(Locale.ROOT).contains(search)))
                    .toList();
        } else if (myView) {
            filtered = filtered.stream().filter(Occurrence::following).toList();
        }

        ZonedDateTime now = ZonedDateTime.now(filtered.isEmpty()
                ? java.time.ZoneOffset.UTC
                : filtered.get(0).display().getZone());

        List<Occurrence> upcoming = filtered.stream()
                .filter(o -> !o.display().isBefore(now))
                .sorted(Comparator.comparing(Occurrence::display))
                .limit(LIST_LIMIT)
                .toList();
        List<Occurrence> past = filtered.stream()
                .filter(o -> o.display().isBefore(now))
                .sorted(Comparator.comparing(Occurrence::display, Comparator.reverseOrder()))
                .limit(LIST_LIMIT)
                .toList();

        String heading = query != null ? "Search results" : myView ? "My Meetings" : "Past Meetings";
        out.println("            <h2 class=\"aira-section-title\">" + escapeHtml(heading) + "</h2>");
        if (query != null) {
            out.println("            <p class=\"aira-meta\">Matching &ldquo;" + escapeHtml(query) + "&rdquo;</p>");
        }

        if (upcoming.isEmpty() && past.isEmpty()) {
            String emptyMessage;
            if (query != null) {
                emptyMessage = "No meetings in this Topic Space match your search.";
            } else if (myView) {
                emptyMessage = "You are not following any meetings in this Topic Space.";
            } else {
                emptyMessage = "No past meetings are available in this Topic Space.";
            }
            out.println("            <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">"
                    + escapeHtml(emptyMessage) + "</p></div>");
            out.println("          </section>");
            return;
        }

        if (!upcoming.isEmpty()) {
            out.println("            <h3 class=\"aira-subsection-title\">Upcoming</h3>");
            renderOccurrenceList(out, contextPath, upcoming);
        }
        if (!past.isEmpty()) {
            out.println("            <h3 class=\"aira-subsection-title\">Past</h3>");
            renderOccurrenceList(out, contextPath, past);
        }
        out.println("          </section>");
    }

    private void renderOccurrenceList(PrintWriter out, String contextPath, List<Occurrence> occurrenceList) {
        out.println("            <div class=\"aira-choice-list\">");
        for (Occurrence occurrence : occurrenceList) {
            EsMeeting meeting = occurrence.meeting();
            boolean cancelled = meeting.getStatus() == EsMeeting.MeetingStatus.CANCELLED;
            out.println("              <a class=\"aira-choice-row aira-interactive-card\" href=\"" + contextPath
                    + "/es/agenda?meetingId=" + meeting.getEsMeetingId() + "\">");
            out.println("                <div>");
            out.println("                  <p class=\"aira-choice-row__title\">"
                    + escapeHtml(orEmpty(meeting.getMeetingName())) + "</p>");
            out.println("                  <p class=\"aira-choice-row__description\">"
                    + DATE_FMT.format(occurrence.display()) + " at " + TIME_FMT.format(occurrence.display())
                    + "</p>");
            out.println("                  <div class=\"aira-tag-list\">");
            if (occurrence.following()) {
                out.println("                    <span class=\"aira-tag aira-tag--info\">Following</span>");
            }
            if (cancelled) {
                out.println("                    <span class=\"aira-badge aira-badge--danger\">Cancelled</span>");
            }
            out.println("                  </div>");
            out.println("                </div>");
            out.println("              </a>");
        }
        out.println("            </div>");
    }

    // =========================================================================
    // Right rail
    // =========================================================================

    private void renderRightRail(PrintWriter out, String contextPath, String spaceCode, String view, String query,
            String monthParam, List<Occurrence> occurrences, User viewer, ZoneId viewerZone) {
        out.println("        <aside class=\"aira-right-rail\" aria-label=\"Meeting search and navigation\">");

        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Search this Topic Space</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        out.println("              <form class=\"aira-inline-form\" method=\"get\" action=\"" + contextPath
                + "/es/meetings\">");
        out.println("                <input type=\"hidden\" name=\"space\" value=\"" + escapeHtml(spaceCode)
                + "\" />");
        out.println(
                "                <input class=\"aira-input\" type=\"search\" name=\"q\" value=\""
                        + escapeHtml(orEmpty(query)) + "\" placeholder=\"Search meetings\" autocomplete=\"off\" />");
        out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Search</button>");
        out.println("              </form>");
        out.println("            </div>");
        out.println("          </section>");

        boolean myView = VIEW_MY.equalsIgnoreCase(view) && query == null;
        boolean pastView = VIEW_PAST.equalsIgnoreCase(view) && query == null;
        boolean allView = !myView && !pastView && query == null;
        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Explore</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        out.println("              <nav class=\"aira-sidebar-nav\" aria-label=\"Meeting views\">");
        out.println("                <a class=\"aira-sidebar-link\" href=\""
                + buildMeetingsUrl(contextPath, spaceCode, null, null, null) + "\""
                + (allView ? " aria-current=\"page\"" : "") + ">All Meetings</a>");
        out.println("                <a class=\"aira-sidebar-link\" href=\""
                + buildMeetingsUrl(contextPath, spaceCode, VIEW_MY, null, null) + "\""
                + (myView ? " aria-current=\"page\"" : "") + ">My Meetings</a>");
        out.println("                <a class=\"aira-sidebar-link\" href=\""
                + buildMeetingsUrl(contextPath, spaceCode, VIEW_PAST, null, null) + "\""
                + (pastView ? " aria-current=\"page\"" : "") + ">Past Meetings</a>");
        out.println("              </nav>");
        out.println("            </div>");
        out.println("          </section>");

        renderRecentlyViewedCard(out, contextPath, viewer);
        renderMyUpcomingCard(out, contextPath, spaceCode, occurrences, viewer);

        out.println("        </aside>");
    }

    private void renderRecentlyViewedCard(PrintWriter out, String contextPath, User viewer) {
        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Recently Viewed Meetings</h2></div>");
        out.println("            <div class=\"aira-section-card__body aira-stack aira-stack--compact\">");
        if (viewer == null) {
            out.println(
                    "              <p class=\"aira-meta\">Sign in to keep a persistent recently viewed meeting list.</p>");
        } else {
            List<EsMeetingViewHistoryService.RecentlyViewedMeeting> recent = meetingViewHistoryService
                    .findRecentAuthenticatedMeetingViews(viewer.getUserId(), RECENTLY_VIEWED_LIMIT);
            if (recent.isEmpty()) {
                out.println("              <p class=\"aira-meta\">No meetings viewed yet.</p>");
            } else {
                for (EsMeetingViewHistoryService.RecentlyViewedMeeting item : recent) {
                    out.println("              <a class=\"aira-recent-topic\" href=\"" + contextPath
                            + "/es/agenda?meetingId=" + item.esMeetingId() + "\">");
                    out.println("                <span><span class=\"aira-recent-topic__title\">"
                            + escapeHtml(orEmpty(item.meetingName())) + "</span>");
                    out.println("                <span class=\"aira-recent-topic__meta\">"
                            + (item.scheduledStart() == null ? "" : item.scheduledStart().toLocalDate().toString())
                            + "</span></span>");
                    out.println("              </a>");
                }
            }
        }
        out.println("            </div>");
        out.println("          </section>");
    }

    private void renderMyUpcomingCard(PrintWriter out, String contextPath, String spaceCode,
            List<Occurrence> occurrences, User viewer) {
        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">My Upcoming Meetings</h2></div>");
        out.println("            <div class=\"aira-section-card__body aira-stack aira-stack--compact\">");
        if (viewer == null) {
            out.println("              <p class=\"aira-meta\">Sign in to follow meetings and see them here.</p>");
        } else {
            ZonedDateTime now = ZonedDateTime.now();
            List<Occurrence> upcoming = occurrences.stream()
                    .filter(Occurrence::following)
                    .filter(o -> o.meeting().getStatus() != EsMeeting.MeetingStatus.CANCELLED)
                    .filter(o -> !o.display().isBefore(now.withZoneSameInstant(o.display().getZone())))
                    .sorted(Comparator.comparing(Occurrence::display))
                    .limit(UPCOMING_SIDEBAR_LIMIT)
                    .toList();
            if (upcoming.isEmpty()) {
                out.println("              <p class=\"aira-meta\">None of your meetings are coming up.</p>");
            } else {
                for (Occurrence occurrence : upcoming) {
                    out.println("              <a class=\"aira-recent-topic\" href=\"" + contextPath
                            + "/es/agenda?meetingId=" + occurrence.meeting().getEsMeetingId() + "\">");
                    out.println("                <span><span class=\"aira-recent-topic__title\">"
                            + escapeHtml(orEmpty(occurrence.meeting().getMeetingName())) + "</span>");
                    out.println("                <span class=\"aira-recent-topic__meta\">"
                            + DATE_FMT.format(occurrence.display()) + " at " + TIME_FMT.format(occurrence.display())
                            + "</span></span>");
                    out.println("              </a>");
                }
            }
            out.println("              <a class=\"aira-inline-link\" href=\""
                    + buildMeetingsUrl(contextPath, spaceCode, VIEW_MY, null, null) + "\">View all my meetings</a>");
        }
        out.println("            </div>");
        out.println("          </section>");
    }

    // =========================================================================
    // Preference controls (timezone / week start)
    // =========================================================================

    private void renderPreferenceControls(PrintWriter out, String contextPath, String spaceCode, String view,
            String query, String monthParam, String effectiveTzId, DayOfWeek effectiveWeekStart) {
        out.println("          <div class=\"aira-cluster\">");

        out.println("            <form class=\"aira-inline-form\" method=\"get\" action=\"" + contextPath
                + "/es/meetings\" aria-label=\"Time zone\">");
        appendHiddenStateInputs(out, spaceCode, view, query, monthParam);
        out.println("              <label class=\"aira-visually-hidden\" for=\"meetings-tz-select\">Time zone</label>");
        out.println("              <select class=\"aira-select\" id=\"meetings-tz-select\" name=\"tz\" onchange=\"this.form.submit()\">");
        for (String tzId : ALLOWED_TIMEZONES) {
            out.println("                <option value=\"" + tzId + "\"" + (tzId.equals(effectiveTzId) ? " selected" : "")
                    + ">" + escapeHtml(TIMEZONE_LABELS.getOrDefault(tzId, tzId)) + "</option>");
        }
        out.println("              </select>");
        out.println("              <noscript><button class=\"aira-button aira-button--small aira-button--secondary\" type=\"submit\">Set time zone</button></noscript>");
        out.println("            </form>");

        out.println("            <form class=\"aira-inline-form\" method=\"get\" action=\"" + contextPath
                + "/es/meetings\" aria-label=\"Week starts on\">");
        appendHiddenStateInputs(out, spaceCode, view, query, monthParam);
        out.println("              <label class=\"aira-visually-hidden\" for=\"meetings-weekstart-select\">Week starts on</label>");
        out.println("              <select class=\"aira-select\" id=\"meetings-weekstart-select\" name=\"weekStart\" onchange=\"this.form.submit()\">");
        out.println("                <option value=\"sunday\"" + (effectiveWeekStart == DayOfWeek.SUNDAY ? " selected" : "")
                + ">Week starts Sunday</option>");
        out.println("                <option value=\"monday\"" + (effectiveWeekStart == DayOfWeek.MONDAY ? " selected" : "")
                + ">Week starts Monday</option>");
        out.println("              </select>");
        out.println("              <noscript><button class=\"aira-button aira-button--small aira-button--secondary\" type=\"submit\">Set week start</button></noscript>");
        out.println("            </form>");

        out.println("          </div>");
    }

    private void appendHiddenStateInputs(PrintWriter out, String spaceCode, String view, String query,
            String monthParam) {
        out.println("                <input type=\"hidden\" name=\"space\" value=\"" + escapeHtml(spaceCode)
                + "\" />");
        if (trimToNull(view) != null) {
            out.println("                <input type=\"hidden\" name=\"view\" value=\"" + escapeHtml(view) + "\" />");
        }
        if (trimToNull(query) != null) {
            out.println("                <input type=\"hidden\" name=\"q\" value=\"" + escapeHtml(query) + "\" />");
        }
        if (trimToNull(monthParam) != null) {
            out.println("                <input type=\"hidden\" name=\"month\" value=\"" + escapeHtml(monthParam)
                    + "\" />");
        }
    }

    private void renderFlashConfirmation(PrintWriter out, String flash) {
        String message = "tz".equals(flash) ? "Time zone saved to your profile."
                : "week".equals(flash) ? "Week start saved to your profile." : null;
        if (message == null) {
            return;
        }
        String elementId = "meetings-flash-" + flash;
        out.println("          <div class=\"aira-alert aira-alert--success\" id=\"" + elementId + "\">");
        out.println("            <p>" + message + "</p>");
        out.println("          </div>");
        out.println("          <script>");
        out.println("            (function() {");
        out.println("              var el = document.getElementById('" + elementId + "');");
        out.println("              if (el) { setTimeout(function() { el.style.display = 'none'; }, 4000); }");
        out.println("            })();");
        out.println("          </script>");
    }

    // =========================================================================
    // Preference resolution
    // =========================================================================

    private String resolveEffectiveTimezone(User viewer, HttpSession session) {
        if (viewer != null) {
            String tz = viewer.getTimezoneId();
            if (tz != null && ALLOWED_TIMEZONES.contains(tz)) {
                return tz;
            }
            return DEFAULT_TIMEZONE;
        }
        Object sessionTz = session.getAttribute(SESSION_ATTR_TIMEZONE);
        if (sessionTz instanceof String tz && ALLOWED_TIMEZONES.contains(tz)) {
            return tz;
        }
        return DEFAULT_TIMEZONE;
    }

    private DayOfWeek resolveEffectiveWeekStart(User viewer, HttpSession session) {
        if (viewer != null) {
            if (viewer.getWeekStartDay() == User.WeekStartDay.MONDAY) {
                return DayOfWeek.MONDAY;
            }
            return DayOfWeek.SUNDAY;
        }
        Object sessionValue = session.getAttribute(SESSION_ATTR_WEEK_START);
        if ("MONDAY".equals(sessionValue)) {
            return DayOfWeek.MONDAY;
        }
        return DayOfWeek.SUNDAY;
    }

    private void redirectToCleanUrl(HttpServletResponse response, String contextPath, String spaceCode, String view,
            String query, String monthParam, String flash) throws IOException {
        StringBuilder url = new StringBuilder(buildMeetingsUrl(contextPath, spaceCode, view, query, monthParam));
        if (flash != null) {
            url.append(url.indexOf("?") >= 0 ? "&" : "?").append("flash=").append(urlEncode(flash));
        }
        response.sendRedirect(url.toString());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private record Occurrence(EsMeeting meeting, EsTopicMeeting series, ZonedDateTime display, boolean following) {
    }

    private YearMonth parseMonth(String monthParam, ZoneId viewerZone) {
        if (monthParam != null) {
            try {
                return YearMonth.parse(monthParam, MONTH_PARAM_FMT);
            } catch (Exception ex) {
                // fall through to current month
            }
        }
        return YearMonth.now(viewerZone);
    }

    private EsTopicSpace findSelectedSpace(List<EsTopicSpace> visibleSpaces, String requestedSpaceCode) {
        if (visibleSpaces == null || visibleSpaces.isEmpty()) {
            return null;
        }
        String normalizedRequested = trimToNull(requestedSpaceCode);
        if (normalizedRequested != null) {
            for (EsTopicSpace topicSpace : visibleSpaces) {
                if (equalsIgnoreCaseTrimmed(topicSpace.getSpaceCode(), normalizedRequested)) {
                    return topicSpace;
                }
            }
        }
        for (EsTopicSpace topicSpace : visibleSpaces) {
            if (equalsIgnoreCaseTrimmed(DEFAULT_SPACE_CODE, topicSpace.getSpaceCode())) {
                return topicSpace;
            }
        }
        return visibleSpaces.get(0);
    }

    private String buildMeetingsUrl(String contextPath, String spaceCode, String view, String query,
            String monthParam) {
        List<String> params = new ArrayList<>();
        if (trimToNull(spaceCode) != null) {
            params.add("space=" + urlEncode(spaceCode));
        }
        if (trimToNull(view) != null) {
            params.add("view=" + urlEncode(view));
        }
        if (trimToNull(query) != null) {
            params.add("q=" + urlEncode(query));
        }
        if (trimToNull(monthParam) != null) {
            params.add("month=" + urlEncode(monthParam));
        }
        if (params.isEmpty()) {
            return contextPath + "/es/meetings";
        }
        return contextPath + "/es/meetings?" + String.join("&", params);
    }

    private static Map<String, String> buildTimezoneLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("America/New_York", "Eastern Time (America/New_York)");
        labels.put("America/Chicago", "Central Time (America/Chicago)");
        labels.put("America/Denver", "Mountain Time (America/Denver)");
        labels.put("America/Los_Angeles", "Pacific Time (America/Los_Angeles)");
        labels.put("America/Phoenix", "Mountain Time, no DST (America/Phoenix)");
        labels.put("America/Anchorage", "Alaska Time (America/Anchorage)");
        labels.put("Pacific/Honolulu", "Hawaii Time (Pacific/Honolulu)");
        labels.put("America/Sao_Paulo", "Brasilia Time (America/Sao_Paulo)");
        labels.put("America/Santiago", "Chile Time (America/Santiago)");
        labels.put("Europe/London", "UK Time (Europe/London)");
        labels.put("Europe/Paris", "Central European Time (Europe/Paris)");
        labels.put("Africa/Johannesburg", "South Africa Time (Africa/Johannesburg)");
        labels.put("Asia/Kolkata", "India Time (Asia/Kolkata)");
        labels.put("Asia/Tokyo", "Japan Time (Asia/Tokyo)");
        labels.put("Australia/Sydney", "Sydney Time (Australia/Sydney)");
        labels.put("Pacific/Auckland", "Auckland Time (Pacific/Auckland)");
        return labels;
    }

    private boolean equalsIgnoreCaseTrimmed(String a, String b) {
        String normalizedA = trimToNull(a);
        String normalizedB = trimToNull(b);
        if (normalizedA == null || normalizedB == null) {
            return false;
        }
        return normalizedA.equalsIgnoreCase(normalizedB);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
