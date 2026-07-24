package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsAgendaItemPresenterDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingAttendanceDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicNoteDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsAgendaItemPresenter;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsMeetingAttendance;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.TopicNoteStatus;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.MeetingAuthorizationService;
import org.airahub.interophub.service.MeetingLifecycleService;
import org.airahub.interophub.service.MeetingWindowRules;
import org.immregistries.aira.web.AiraContextConfig;
import org.immregistries.aira.web.AiraNavigationItem;
import org.immregistries.aira.web.AiraPage;

public class EsMeetingWorkspaceServlet extends HttpServlet {

    public static final String WORKSPACE_PATH = "/es/meeting-workspace";

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy h:mm a");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private final AuthFlowService authFlowService;
    private final MeetingAuthorizationService meetingAuthorizationService;
    private final EsMeetingDao meetingDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsMeetingAgendaItemDao agendaItemDao;
    private final EsAgendaItemPresenterDao presenterDao;
    private final EsTopicDao topicDao;
    private final EsTopicNoteDao noteDao;
    private final EsMeetingAttendanceDao attendanceDao;
    private final UserDao userDao;
    private final MeetingLifecycleService meetingLifecycleService;

    public EsMeetingWorkspaceServlet() {
        this.authFlowService = new AuthFlowService();
        this.meetingAuthorizationService = new MeetingAuthorizationService();
        this.meetingDao = new EsMeetingDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.presenterDao = new EsAgendaItemPresenterDao();
        this.topicDao = new EsTopicDao();
        this.noteDao = new EsTopicNoteDao();
        this.attendanceDao = new EsMeetingAttendanceDao();
        this.userDao = new UserDao();
        this.meetingLifecycleService = new MeetingLifecycleService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long meetingId = parseId(request.getParameter("meetingId"));
        if (meetingId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "meetingId is required.");
            return;
        }

        User user = authFlowService.findAuthenticatedUser(request).orElse(null);
        EsMeeting meeting = meetingDao.findById(meetingId).orElse(null);
        if (meeting == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Meeting was not found.");
            return;
        }
        if (!meetingAuthorizationService.canControlMeeting(user != null ? user.getUserId() : null, meeting)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have access to this meeting workspace.");
            return;
        }

        Long selectedItemId = parseId(request.getParameter("itemId"));
        String feedbackMessage = resolveFeedbackMessage(request);
        String errorMessage = trimToNull(request.getParameter("err"));
        WorkspaceView view = buildWorkspaceView(meeting, selectedItemId, feedbackMessage, errorMessage);

        response.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AiraPage page = InteropAiraPageFactory
                    .base(request, escapeHtml(view.meeting().getMeetingName()) + " - Meeting Workspace")
                    .applicationSubtitle("Meeting Workspace")
                    .pageHeading(view.meeting().getMeetingName())
                    .pageIntro(buildWorkspaceIntro(view))
                    .mainClass("aira-main interophub-meeting-workspace-main")
                    .context(new AiraContextConfig(
                            view.seriesName() != null ? view.seriesName() : "Meeting Workspace",
                            List.of(
                                    new AiraNavigationItem("Workspace",
                                            WORKSPACE_PATH + "?meetingId=" + meeting.getEsMeetingId(), true),
                                    new AiraNavigationItem("Agenda",
                                            "/es/agenda?meetingId=" + meeting.getEsMeetingId(), false),
                                    new AiraNavigationItem("Topics", "/es/topics", false))))
                    .build();

            page.writeStart(out);
            renderWorkspaceContent(out, request.getContextPath(), view);
            page.writeEnd(out);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        Long meetingId = parseId(request.getParameter("meetingId"));
        if (meetingId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "meetingId is required.");
            return;
        }

        Long selectedItemId = parseId(request.getParameter("itemId"));
        User user = authFlowService.findAuthenticatedUser(request).orElse(null);
        EsMeeting meeting = meetingDao.findById(meetingId).orElse(null);
        if (meeting == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Meeting was not found.");
            return;
        }
        if (!meetingAuthorizationService.canControlMeeting(user != null ? user.getUserId() : null, meeting)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have access to this meeting workspace.");
            return;
        }

        String action = trimToNull(request.getParameter("action"));
        if (action == null) {
            redirectWorkspace(response, request.getContextPath(), meetingId, selectedItemId, null);
            return;
        }

        try {
            if ("startSession".equals(action)) {
                meetingLifecycleService.startMeeting(meetingId, user.getUserId());
                redirectWorkspace(response, request.getContextPath(), meetingId, selectedItemId, "started=1");
                return;
            }
            if ("endMeeting".equals(action)) {
                meetingLifecycleService.completeMeeting(meetingId, user.getUserId());
                redirectWorkspace(response, request.getContextPath(), meetingId, selectedItemId, "ended=1");
                return;
            }
            redirectWorkspace(response, request.getContextPath(), meetingId, selectedItemId, null);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            String errorMessage = ex.getMessage() != null ? ex.getMessage() : "Meeting action could not be completed.";
            redirectWorkspace(response, request.getContextPath(), meetingId, selectedItemId,
                    "err=" + URLEncoder.encode(errorMessage, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String buildWorkspaceIntro(WorkspaceView view) {
        String schedule = trimToNull(view.scheduleText());
        String status = trimToNull(view.meetingStatusLabel());
        if (schedule == null && status == null) {
            return "Read-only workspace overview for this meeting.";
        }
        if (schedule == null) {
            return "Status: " + status + ".";
        }
        if (status == null) {
            return schedule;
        }
        return schedule + " · " + status;
    }

    private static void renderWorkspaceContent(PrintWriter out, String contextPath, WorkspaceView view) {
        out.println("    <div class=\"aira-container--wide aira-stack aira-stack--compact\">");
        if (view.feedbackMessage() != null) {
            out.println("      <div class=\"aira-alert aira-alert--success\" role=\"status\">"
                    + escapeHtml(view.feedbackMessage()) + "</div>");
        }
        if (view.errorMessage() != null) {
            out.println("      <div class=\"aira-alert aira-alert--danger\" role=\"alert\">"
                    + escapeHtml(view.errorMessage()) + "</div>");
        }
        out.println("      <div class=\"aira-sidebar-layout\">");
        out.println("        <aside class=\"aira-sidebar\">");
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h3 class=\"aira-sidebar-title\">Agenda</h3>");
        if (view.agendaItems().isEmpty()) {
            out.println(
                    "            <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No agenda items were found for this meeting.</p></div>");
        } else {
            out.println("            <nav class=\"aira-sidebar-nav\" aria-label=\"Agenda items\">");
            for (AgendaItemView item : view.agendaItems()) {
                out.println("              <a class=\"aira-sidebar-link\" href=\""
                        + escapeHtml(workspaceUrl(contextPath, view.meeting().getEsMeetingId(), item.agendaItemId()))
                        + "\"" + (item.selected() ? " aria-current=\"page\"" : "") + ">");
                out.println(
                        "                <span><strong>" + escapeHtml(displayOrder(item.displayOrder())) + "</strong> "
                                + escapeHtml(item.title()) + "</span>");
                if (item.topicName() != null && !item.topicName().isBlank()) {
                    out.println(
                            "                <span class=\"aira-meta\">" + escapeHtml(item.topicName()) + "</span>");
                }
                if (item.startTimeLabel() != null && !item.startTimeLabel().isBlank()) {
                    out.println("                <span class=\"aira-meta\">Starts "
                            + escapeHtml(item.startTimeLabel()) + "</span>");
                }
                if (item.presenterSummary() != null && !item.presenterSummary().isBlank()) {
                    out.println("                <span class=\"aira-meta\">" + escapeHtml(item.presenterSummary())
                            + "</span>");
                }
                out.println("              </a>");
            }
            out.println("            </nav>");
        }
        out.println("          </section>");

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h3 class=\"aira-sidebar-title\">Roles</h3>");
        out.println("            <div class=\"aira-stack aira-stack--compact\">");
        for (RoleSummary role : view.roleSummaries()) {
            out.println("              <div class=\"aira-sidebar-section\">");
            out.println("                <p class=\"aira-sidebar-title\">" + escapeHtml(role.label()) + "</p>");
            out.println("                <p><strong>" + escapeHtml(role.name()) + "</strong></p>");
            if (role.meta() != null && !role.meta().isBlank()) {
                out.println("                <p class=\"aira-meta\">" + escapeHtml(role.meta()) + "</p>");
            }
            out.println("              </div>");
        }
        out.println("            </div>");
        out.println("          </section>");

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h3 class=\"aira-section-title\">Meeting Activity</h3>");
        out.println("            <p><strong>" + view.attendeeCount() + "</strong> registered attendee"
                + (view.attendeeCount() == 1 ? "" : "s") + "</p>");
        out.println("            <p class=\"aira-meta\">Open notes: " + view.openNoteCount() + "</p>");
        out.println("          </section>");
        out.println("        </aside>");

        out.println("        <div class=\"aira-stack aira-stack--compact\">");
        out.println("          <section class=\"aira-panel\">");
        if (view.selectedItem() != null) {
            out.println("            <div class=\"aira-cluster aira-cluster--between\">");
            out.println("              <div class=\"aira-stack aira-stack--compact\">");
            out.println("                <h2 class=\"aira-section-title\">" + escapeHtml(view.selectedItem().title())
                    + "</h2>");
            if (view.selectedItem().topicName() != null && !view.selectedItem().topicName().isBlank()) {
                out.println("                <p class=\"aira-meta\">Topic: "
                        + escapeHtml(view.selectedItem().topicName()) + "</p>");
            }
            out.println("              </div>");
            out.println("              <div class=\"aira-stack aira-stack--compact\">");
            if (view.selectedItem().timeRangeLabel() != null && !view.selectedItem().timeRangeLabel().isBlank()) {
                out.println("                <div>" + escapeHtml(view.selectedItem().timeRangeLabel()) + "</div>");
            } else if (view.selectedItem().timeMinutes() != null) {
                out.println("                <div>" + view.selectedItem().timeMinutes() + " minutes</div>");
            }
            out.println("              </div>");
            out.println("            </div>");
            if (view.selectedItem().presenterSummary() != null && !view.selectedItem().presenterSummary().isBlank()) {
                out.println("            <p class=\"aira-meta\">Presenters: "
                        + escapeHtml(view.selectedItem().presenterSummary()) + "</p>");
            }
            if (view.selectedItem().agendaMarkdown() != null && !view.selectedItem().agendaMarkdown().isBlank()) {
                out.println("            <p>" + formatAgendaDetailsHtml(view.selectedItem().agendaMarkdown()) + "</p>");
            } else {
                out.println(
                        "            <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No agenda notes were provided for this item.</p></div>");
            }
            out.println("          </section>");

            out.println("          <section class=\"aira-grid\">");
            out.println("            <article class=\"aira-panel\">");
            out.println("              <h3 class=\"aira-section-title\">Note Workspace</h3>");
            if (view.selectedItem().noteSummary() != null) {
                out.println(
                        "              <p><strong>" + escapeHtml(view.selectedItem().noteSummary()) + "</strong></p>");
                if (view.selectedItem().noteStatusLabel() != null) {
                    out.println("              <span class=\"aira-badge aira-badge--subtle\">"
                            + escapeHtml(view.selectedItem().noteStatusLabel()) + "</span>");
                }
            } else {
                out.println(
                        "              <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No open note is attached to this item yet.</p></div>");
            }
            out.println(
                    "              <p class=\"aira-meta\">Read-only in this release. Editing tools will appear here later.</p>");
            out.println("            </article>");

            out.println("            <article class=\"aira-panel\">");
                out.println("              <h3 class=\"aira-section-title\">Meeting Controls</h3>");
            out.println("              <div class=\"aira-grid\">");
                    out.println("                <form method=\"post\" action=\"" + escapeHtml((contextPath == null ? "" : contextPath) + WORKSPACE_PATH)
                    + "\" class=\"aira-stack aira-stack--compact\">");
                out.println("                  <input type=\"hidden\" name=\"meetingId\" value=\""
                    + view.meeting().getEsMeetingId() + "\" />");
                if (view.selectedItem() != null && view.selectedItem().agendaItemId() != null) {
                out.println("                  <input type=\"hidden\" name=\"itemId\" value=\""
                    + view.selectedItem().agendaItemId() + "\" />");
                }
                out.println("                  <input type=\"hidden\" name=\"action\" value=\"startSession\" />");
                out.println("                  <button class=\"aira-button aira-button--primary\" type=\"submit\""
                    + (view.canStartSession() ? "" : " disabled")
                    + " onclick=\"return confirm('Start session now?')\">Start session</button>");
                if (view.startSessionHelpText() != null) {
                out.println("                  <p class=\"aira-meta\">" + escapeHtml(view.startSessionHelpText()) + "</p>");
                }
                out.println("                </form>");
            out.println(
                    "                <button class=\"aira-button aira-button--tertiary\" type=\"button\" disabled>Mark item covered</button>");
            out.println(
                    "                <button class=\"aira-button aira-button--tertiary\" type=\"button\" disabled>Add note</button>");
                    out.println("                <form method=\"post\" action=\"" + escapeHtml((contextPath == null ? "" : contextPath) + WORKSPACE_PATH)
                    + "\" class=\"aira-stack aira-stack--compact\">");
                out.println("                  <input type=\"hidden\" name=\"meetingId\" value=\""
                    + view.meeting().getEsMeetingId() + "\" />");
                if (view.selectedItem() != null && view.selectedItem().agendaItemId() != null) {
                out.println("                  <input type=\"hidden\" name=\"itemId\" value=\""
                    + view.selectedItem().agendaItemId() + "\" />");
                }
                out.println("                  <input type=\"hidden\" name=\"action\" value=\"endMeeting\" />");
                out.println("                  <button class=\"aira-button aira-button--danger\" type=\"submit\""
                    + (view.canEndMeeting() ? "" : " disabled")
                    + " onclick=\"return confirm('End meeting now?')\">End meeting</button>");
                if (view.endMeetingHelpText() != null) {
                out.println("                  <p class=\"aira-meta\">" + escapeHtml(view.endMeetingHelpText()) + "</p>");
                }
                out.println("                </form>");
            out.println("              </div>");
                out.println("              <p class=\"aira-meta\">Lifecycle transitions are server-enforced and role-aware.</p>");
            out.println("            </article>");
            out.println("          </section>");
        } else {
            out.println("            <h3 class=\"aira-section-title\">Selected agenda item</h3>");
            out.println(
                    "            <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No agenda item is available for this meeting.</p></div>");
            out.println("          </section>");
        }

        out.println("        </div>");
        out.println("      </div>");
        out.println("    </div>");
    }

        WorkspaceView buildWorkspaceView(EsMeeting meeting, Long requestedSelectedItemId, String feedbackMessage,
            String errorMessage) {
        List<EsMeetingAgendaItem> agendaItems = agendaItemDao.findByMeetingIdOrdered(meeting.getEsMeetingId()).stream()
                .filter(item -> item.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.CANCELLED)
                .sorted(Comparator.comparingInt(EsMeetingWorkspaceServlet::agendaOrder)
                        .thenComparingLong(item -> item.getEsMeetingAgendaItemId() == null ? Long.MAX_VALUE
                                : item.getEsMeetingAgendaItemId()))
                .toList();

        List<Long> agendaItemIds = agendaItems.stream()
                .map(EsMeetingAgendaItem::getEsMeetingAgendaItemId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<EsAgendaItemPresenter>> presentersByItemId = presenterDao.findByAgendaItemIds(agendaItemIds)
                .stream()
                .collect(Collectors.groupingBy(EsAgendaItemPresenter::getEsMeetingAgendaItemId,
                        LinkedHashMap::new, Collectors.toList()));

        List<Long> topicIds = agendaItems.stream()
                .map(EsMeetingAgendaItem::getEsTopicId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> topicNames = topicDao.findTopicNamesByTopicIds(topicIds);
        Map<Long, AgendaTimeRange> agendaTimes = computeAgendaTimes(agendaItems, meeting);

        Map<Long, EsTopicNote> openNotesByAgendaItemId = noteDao.findOpenByMeetingId(meeting.getEsMeetingId()).stream()
                .filter(note -> note.getEsMeetingAgendaItemId() != null)
                .collect(Collectors.toMap(EsTopicNote::getEsMeetingAgendaItemId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));

        Map<Long, User> resolvedUsers = resolveMeetingUsers(meeting);

        List<RoleSummary> roleSummaries = List.of(
                new RoleSummary("Designated chair", userLabel(resolvedUsers.get(meeting.getDesignatedChairUserId())),
                        roleMeta(meeting.getDesignatedChairUserId(),
                                resolvedUsers.containsKey(meeting.getDesignatedChairUserId()))),
                new RoleSummary("Current chair", userLabel(resolvedUsers.get(meeting.getCurrentChairUserId())),
                        roleMeta(meeting.getCurrentChairUserId(),
                                resolvedUsers.containsKey(meeting.getCurrentChairUserId()))),
                new RoleSummary("Designated scribe", userLabel(resolvedUsers.get(meeting.getDesignatedScribeUserId())),
                        roleMeta(meeting.getDesignatedScribeUserId(),
                                resolvedUsers.containsKey(meeting.getDesignatedScribeUserId()))),
                new RoleSummary("Current scribe", userLabel(resolvedUsers.get(meeting.getCurrentScribeUserId())),
                        roleMeta(meeting.getCurrentScribeUserId(),
                                resolvedUsers.containsKey(meeting.getCurrentScribeUserId()))),
                new RoleSummary("Created by", userLabel(resolvedUsers.get(meeting.getCreatedByUserId())),
                        roleMeta(meeting.getCreatedByUserId(),
                                resolvedUsers.containsKey(meeting.getCreatedByUserId()))));

        List<AgendaItemView> agendaViews = new ArrayList<>();
        for (EsMeetingAgendaItem item : agendaItems) {
            List<EsAgendaItemPresenter> presenters = presentersByItemId.getOrDefault(item.getEsMeetingAgendaItemId(),
                    List.of());
            EsTopicNote openNote = openNotesByAgendaItemId.get(item.getEsMeetingAgendaItemId());
            String topicName = topicNames.get(item.getEsTopicId());
            AgendaTimeRange agendaTime = agendaTimes.get(item.getEsMeetingAgendaItemId());
            agendaViews.add(new AgendaItemView(
                    item.getEsMeetingAgendaItemId(),
                    item.getDisplayOrder(),
                    effectiveAgendaTitle(item, topicName),
                    topicName,
                    agendaStatusLabel(item.getStatus()),
                    agendaStatusClass(item.getStatus()),
                    presenterSummary(presenters),
                    item.getTimeMinutes(),
                    item.getAgendaMarkdown(),
                    false,
                    openNote != null ? noteSummary(openNote) : null,
                    openNote != null ? topicNoteStatusLabel(openNote.getStatus()) : null,
                    agendaTime != null ? agendaTime.startTimeLabel() : null,
                    agendaTime != null ? agendaTime.endTimeLabel() : null,
                    agendaTime != null ? agendaTime.timeRangeLabel() : null));
        }

        Long selectedItemId = selectedAgendaItemId(meeting, agendaViews, requestedSelectedItemId);
        List<AgendaItemView> selectedMarked = new ArrayList<>(agendaViews.size());
        AgendaItemView selectedItem = null;
        for (AgendaItemView view : agendaViews) {
            boolean isSelected = Objects.equals(view.agendaItemId(), selectedItemId);
            AgendaItemView current = new AgendaItemView(view.agendaItemId(), view.displayOrder(), view.title(),
                    view.topicName(), view.statusLabel(), view.statusClass(), view.presenterSummary(),
                    view.timeMinutes(),
                    view.agendaMarkdown(), isSelected, view.noteSummary(), view.noteStatusLabel(),
                    view.startTimeLabel(), view.endTimeLabel(), view.timeRangeLabel());
            selectedMarked.add(current);
            if (isSelected) {
                selectedItem = current;
            }
        }

        if (selectedItem == null && !selectedMarked.isEmpty()) {
            selectedItem = selectedMarked.get(0);
            selectedMarked.set(0, new AgendaItemView(selectedItem.agendaItemId(), selectedItem.displayOrder(),
                    selectedItem.title(), selectedItem.topicName(), selectedItem.statusLabel(),
                    selectedItem.statusClass(),
                    selectedItem.presenterSummary(), selectedItem.timeMinutes(), selectedItem.agendaMarkdown(), true,
                    selectedItem.noteSummary(), selectedItem.noteStatusLabel(), selectedItem.startTimeLabel(),
                    selectedItem.endTimeLabel(), selectedItem.timeRangeLabel()));
        }

        List<EsMeetingAttendance> attendance = attendanceDao.findByEsMeetingId(meeting.getEsMeetingId());

        EsTopicMeeting topicMeeting = topicMeetingDao.findById(meeting.getEsTopicMeetingId()).orElse(null);
        String seriesName = topicMeeting != null ? topicMeeting.getMeetingName() : "Meeting";
        String seriesDescription = topicMeeting != null ? topicMeeting.getMeetingDescription() : null;
        String scheduleText = formatMeetingSchedule(meeting);
        String meetingStatusLabel = meetingStatusLabel(meeting.getStatus());
        String meetingStatusClass = meetingStatusClass(meeting.getStatus());
        boolean canStartSession = meeting.getStatus() == EsMeeting.MeetingStatus.FINALIZED
            && MeetingWindowRules.isMeetingStartWindowOpen(meeting);
        boolean canEndMeeting = meeting.getStatus() == EsMeeting.MeetingStatus.IN_SESSION;

        return new WorkspaceView(meeting, topicMeeting, seriesName, seriesDescription, scheduleText,
                meetingStatusLabel, meetingStatusClass, roleSummaries, selectedMarked, selectedItem,
                attendance.size(), openNotesByAgendaItemId.size(),
            userLabel(resolvedUsers.get(meeting.getCreatedByUserId())),
            canStartSession,
            canEndMeeting,
            startSessionHelpText(meeting, canStartSession),
            endMeetingHelpText(meeting, canEndMeeting),
            feedbackMessage,
            errorMessage);
    }

    static Long selectedAgendaItemId(EsMeeting meeting, List<AgendaItemView> agendaItems,
            Long requestedSelectedItemId) {
        if (agendaItems == null || agendaItems.isEmpty()) {
            return null;
        }
        if (requestedSelectedItemId != null) {
            for (AgendaItemView item : agendaItems) {
                if (Objects.equals(item.agendaItemId(), requestedSelectedItemId)) {
                    return requestedSelectedItemId;
                }
            }
        }
        if (meeting != null && meeting.getCurrentAgendaItemId() != null) {
            for (AgendaItemView item : agendaItems) {
                if (Objects.equals(item.agendaItemId(), meeting.getCurrentAgendaItemId())) {
                    return meeting.getCurrentAgendaItemId();
                }
            }
        }
        return agendaItems.get(0).agendaItemId();
    }

    static List<AgendaItemView> sortAgendaItems(List<AgendaItemView> agendaItems) {
        if (agendaItems == null) {
            return List.of();
        }
        return agendaItems.stream()
                .sorted(Comparator.comparingInt(EsMeetingWorkspaceServlet::viewOrder)
                        .thenComparingLong(item -> item.agendaItemId() == null ? Long.MAX_VALUE : item.agendaItemId()))
                .toList();
    }

    static String agendaStatusLabel(EsMeetingAgendaItem.AgendaItemStatus status) {
        if (status == null) {
            return "Draft";
        }
        return switch (status) {
            case DRAFT -> "Draft";
            case PROPOSED -> "Proposed";
            case ACCEPTED -> "Accepted";
            case NEEDS_REVISION -> "Needs revision";
            case POSTPONED -> "Postponed";
            case COVERED -> "Covered";
            case NOT_COVERED -> "Not covered";
            case CANCELLED -> "Cancelled";
        };
    }

    static String agendaStatusClass(EsMeetingAgendaItem.AgendaItemStatus status) {
        if (status == null) {
            return "aira-badge--subtle";
        }
        return switch (status) {
            case DRAFT -> "aira-badge--subtle";
            case PROPOSED -> "aira-badge--info";
            case ACCEPTED -> "aira-badge--success";
            case NEEDS_REVISION -> "aira-badge--warning";
            case POSTPONED -> "aira-badge--warning";
            case COVERED -> "aira-badge--success";
            case NOT_COVERED -> "aira-badge--danger";
            case CANCELLED -> "aira-badge--danger";
        };
    }

    static String meetingStatusLabel(EsMeeting.MeetingStatus status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case DRAFT -> "Draft";
            case PROPOSED -> "Proposed";
            case FINALIZED -> "Finalized";
            case IN_SESSION -> "In session";
            case COMPLETED -> "Completed";
            case CLOSED -> "Closed";
            case CANCELLED -> "Cancelled";
        };
    }

    static String meetingStatusClass(EsMeeting.MeetingStatus status) {
        if (status == null) {
            return "aira-badge--subtle";
        }
        return switch (status) {
            case DRAFT -> "aira-badge--subtle";
            case PROPOSED -> "aira-badge--info";
            case FINALIZED -> "aira-badge--success";
            case IN_SESSION -> "aira-badge--info";
            case COMPLETED -> "aira-badge--outline";
            case CLOSED -> "aira-badge--outline";
            case CANCELLED -> "aira-badge--danger";
        };
    }

    static String topicNoteStatusLabel(TopicNoteStatus status) {
        if (status == null) {
            return "Open";
        }
        return switch (status) {
            case OPEN -> "Open";
            case FINALIZED -> "Finalized";
        };
    }

    static String effectiveAgendaTitle(EsMeetingAgendaItem item, String topicName) {
        String title = item != null ? trimToNull(item.getTitle()) : null;
        if (title != null) {
            return title;
        }
        if (topicName != null && !topicName.isBlank()) {
            return topicName;
        }
        return "Agenda item";
    }

    static String workspaceUrl(String contextPath, Long meetingId, Long itemId) {
        String base = (contextPath == null ? "" : contextPath) + WORKSPACE_PATH + "?meetingId=" + meetingId;
        if (itemId != null) {
            return base + "&itemId=" + itemId;
        }
        return base;
    }

    private static String resolveFeedbackMessage(HttpServletRequest request) {
        if ("1".equals(request.getParameter("started"))) {
            return "Session started.";
        }
        if ("1".equals(request.getParameter("ended"))) {
            return "Meeting ended.";
        }
        return null;
    }

    private static void redirectWorkspace(HttpServletResponse response, String contextPath, Long meetingId,
            Long selectedItemId, String querySuffix) throws IOException {
        StringBuilder url = new StringBuilder(workspaceUrl(contextPath, meetingId, selectedItemId));
        if (querySuffix != null && !querySuffix.isBlank()) {
            url.append("&").append(querySuffix);
        }
        response.sendRedirect(url.toString());
    }

    private static String startSessionHelpText(EsMeeting meeting, boolean canStartSession) {
        if (canStartSession) {
            return "Session can now be started.";
        }
        if (meeting == null || meeting.getStatus() == null) {
            return "Meeting status is required before starting.";
        }
        if (meeting.getStatus() == EsMeeting.MeetingStatus.IN_SESSION) {
            return "Meeting is already in session.";
        }
        if (meeting.getStatus() != EsMeeting.MeetingStatus.FINALIZED) {
            return "Start session is only available after finalization.";
        }
        return "Start session opens 15 minutes before the scheduled start time.";
    }

    private static String endMeetingHelpText(EsMeeting meeting, boolean canEndMeeting) {
        if (canEndMeeting) {
            return "Ending the meeting will set close due date to 7 days from completion.";
        }
        if (meeting != null && meeting.getStatus() == EsMeeting.MeetingStatus.COMPLETED) {
            return "Meeting has already ended.";
        }
        return "End meeting is only available when the meeting is in session.";
    }

    static void renderPage(PrintWriter out, String contextPath, WorkspaceView view) {
        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("  <meta charset=\"UTF-8\">");
        out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        out.println("  <title>" + escapeHtml(view.meeting().getMeetingName()) + " - Meeting Workspace</title>");
        out.println("  <link rel=\"stylesheet\" href=\"" + (contextPath == null ? "" : contextPath)
                + "/css/main.css\">");
        out.println("</head>");
        out.println("<body>");
        renderWorkspaceContent(out, contextPath, view);
        PageFooterRenderer.render(out);
        out.println("</body>");
        out.println("</html>");
    }

    private Map<Long, User> resolveMeetingUsers(EsMeeting meeting) {
        List<Long> userIds = new ArrayList<>();
        addIfNotNull(userIds, meeting.getDesignatedChairUserId());
        addIfNotNull(userIds, meeting.getCurrentChairUserId());
        addIfNotNull(userIds, meeting.getDesignatedScribeUserId());
        addIfNotNull(userIds, meeting.getCurrentScribeUserId());
        addIfNotNull(userIds, meeting.getCreatedByUserId());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userDao.findByIds(userIds).stream()
                .filter(user -> user.getUserId() != null)
                .collect(Collectors.toMap(User::getUserId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
    }

    private static String userLabel(User user) {
        if (user == null) {
            return "Unassigned";
        }
        String fullName = trimToNull(user.getFullName());
        if (fullName != null) {
            return fullName;
        }
        return trimToNull(user.getEmail()) != null ? user.getEmail() : "Unassigned";
    }

    private static String roleMeta(Long userId, boolean resolved) {
        if (userId == null) {
            return "Not assigned";
        }
        return resolved ? ("User #" + userId) : "Unknown user";
    }

    private static String noteSummary(EsTopicNote note) {
        String title = trimToNull(note.getNoteTitle());
        if (title != null) {
            return title;
        }
        return "Open note #" + note.getEsTopicNoteId();
    }

    private static String presenterSummary(List<EsAgendaItemPresenter> presenters) {
        if (presenters == null || presenters.isEmpty()) {
            return null;
        }
        return presenters.stream()
                .filter(p -> p.getStatus() != EsAgendaItemPresenter.PresenterStatus.REMOVED
                        && p.getStatus() != EsAgendaItemPresenter.PresenterStatus.DECLINED)
                .map(EsMeetingWorkspaceServlet::presenterLabel)
                .filter(label -> label != null && !label.isBlank())
                .collect(Collectors.joining(", "));
    }

    private static String presenterLabel(EsAgendaItemPresenter presenter) {
        if (presenter == null) {
            return null;
        }
        String displayName = trimToNull(presenter.getDisplayName());
        if (displayName != null) {
            return displayName;
        }
        if (trimToNull(presenter.getEmail()) != null) {
            return presenter.getEmail();
        }
        if (presenter.getPresenterRole() != null) {
            return presenter.getPresenterRole().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        return null;
    }

    private static String formatMeetingSchedule(EsMeeting meeting) {
        if (meeting == null || meeting.getScheduledStart() == null) {
            return "Schedule not set";
        }
        ZoneId zone = meeting.getTimezoneId() != null && !meeting.getTimezoneId().isBlank()
                ? ZoneId.of(meeting.getTimezoneId())
                : ZoneId.systemDefault();
        ZonedDateTime start = ZonedDateTime.of(meeting.getScheduledStart(), zone);
        if (meeting.getScheduledEnd() != null) {
            ZonedDateTime end = ZonedDateTime.of(meeting.getScheduledEnd(), zone);
            return DATE_TIME_FMT.format(start) + " - " + TIME_FMT.format(end) + " " + zone.getId();
        }
        return DATE_TIME_FMT.format(start) + " " + zone.getId();
    }

    private static Map<Long, AgendaTimeRange> computeAgendaTimes(List<EsMeetingAgendaItem> agendaItems,
            EsMeeting meeting) {
        if (agendaItems == null || agendaItems.isEmpty() || meeting == null || meeting.getScheduledStart() == null) {
            return Map.of();
        }
        ZoneId zone = meeting.getTimezoneId() != null && !meeting.getTimezoneId().isBlank()
                ? ZoneId.of(meeting.getTimezoneId())
                : ZoneId.systemDefault();
        Map<Long, AgendaTimeRange> times = new LinkedHashMap<>();
        ZonedDateTime cursor = ZonedDateTime.of(meeting.getScheduledStart(), zone);
        for (EsMeetingAgendaItem item : agendaItems) {
            if (item == null || item.getEsMeetingAgendaItemId() == null) {
                continue;
            }
            ZonedDateTime start = cursor;
            int durationMinutes = item.getTimeMinutes() != null ? Math.max(0, item.getTimeMinutes()) : 0;
            ZonedDateTime end = start.plusMinutes(durationMinutes);
            times.put(item.getEsMeetingAgendaItemId(), new AgendaTimeRange(
                    TIME_FMT.format(start),
                    TIME_FMT.format(end),
                    TIME_FMT.format(start) + " - " + TIME_FMT.format(end)));
            cursor = end;
        }
        return times;
    }

    private static String formatAgendaDetailsHtml(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return escapeHtml(value).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>");
    }

    private static int agendaOrder(EsMeetingAgendaItem item) {
        return item != null && item.getDisplayOrder() != null ? item.getDisplayOrder() : Integer.MAX_VALUE;
    }

    private static int viewOrder(AgendaItemView view) {
        return view != null && view.displayOrder() != null ? view.displayOrder() : Integer.MAX_VALUE;
    }

    private static Long parseId(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String displayOrder(Integer order) {
        return order == null ? "#" : "#" + order;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void addIfNotNull(List<Long> values, Long candidate) {
        if (candidate != null) {
            values.add(candidate);
        }
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public record WorkspaceView(EsMeeting meeting, EsTopicMeeting topicMeeting, String seriesName,
            String seriesDescription, String scheduleText, String meetingStatusLabel, String meetingStatusClass,
            List<RoleSummary> roleSummaries, List<AgendaItemView> agendaItems, AgendaItemView selectedItem,
            int attendeeCount, int openNoteCount, String createdByName,
            boolean canStartSession, boolean canEndMeeting,
            String startSessionHelpText, String endMeetingHelpText,
            String feedbackMessage, String errorMessage) {
    }

    public record RoleSummary(String label, String name, String meta) {
    }

    private record AgendaTimeRange(String startTimeLabel, String endTimeLabel, String timeRangeLabel) {
    }

    public record AgendaItemView(Long agendaItemId, Integer displayOrder, String title, String topicName,
            String statusLabel, String statusClass, String presenterSummary, Integer timeMinutes,
            String agendaMarkdown, boolean selected, String noteSummary, String noteStatusLabel,
            String startTimeLabel, String endTimeLabel, String timeRangeLabel) {
    }
}