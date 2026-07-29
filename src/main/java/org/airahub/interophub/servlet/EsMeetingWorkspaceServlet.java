package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.airahub.interophub.model.RecordedOutcomeType;
import org.airahub.interophub.model.TopicNoteStatus;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.MeetingAuthorizationService;
import org.airahub.interophub.service.MeetingLifecycleService;
import org.airahub.interophub.service.MeetingWindowRules;
import org.airahub.interophub.service.TopicNoteDocumentSupport;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.json.JSONObject;
import org.immregistries.aira.web.AiraContextConfig;
import org.immregistries.aira.web.AiraNavigationItem;
import org.immregistries.aira.web.AiraPage;

public class EsMeetingWorkspaceServlet extends HttpServlet {

    public static final String WORKSPACE_PATH = "/es/meeting-workspace";

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy h:mm a");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private final AuthFlowService authFlowService;
    private final MeetingAuthorizationService meetingAuthorizationService;
    private final TopicSpaceAccessService topicSpaceAccessService;
    private final EsMeetingDao meetingDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsMeetingAgendaItemDao agendaItemDao;
    private final EsAgendaItemPresenterDao presenterDao;
    private final EsTopicDao topicDao;
    private final EsTopicNoteDao noteDao;
    private final EsMeetingAttendanceDao attendanceDao;
    private final UserDao userDao;
    private final MeetingLifecycleService meetingLifecycleService;
    private final TopicNoteDocumentSupport topicNoteDocumentSupport;

    public EsMeetingWorkspaceServlet() {
        this.authFlowService = new AuthFlowService();
        this.meetingAuthorizationService = new MeetingAuthorizationService();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
        this.meetingDao = new EsMeetingDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.presenterDao = new EsAgendaItemPresenterDao();
        this.topicDao = new EsTopicDao();
        this.noteDao = new EsTopicNoteDao();
        this.attendanceDao = new EsMeetingAttendanceDao();
        this.userDao = new UserDao();
        this.meetingLifecycleService = new MeetingLifecycleService();
        this.topicNoteDocumentSupport = new TopicNoteDocumentSupport();
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
        if (!topicSpaceAccessService.canViewMeeting(user, meeting)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have access to this meeting workspace.");
            return;
        }

        Long selectedItemId = parseId(request.getParameter("itemId"));
        String feedbackMessage = resolveFeedbackMessage(request);
        String errorMessage = trimToNull(request.getParameter("err"));
        String csrfToken = CsrfTokenSupport.getOrCreateToken(request);
        WorkspaceView view = buildWorkspaceView(meeting, selectedItemId, user, feedbackMessage, errorMessage,
                csrfToken);

        response.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AiraPage page = InteropAiraPageFactory
                    .base(request, escapeHtml(view.meeting().getMeetingName()) + " - Meeting Workspace")
                    .applicationSubtitle("Meeting Workspace")
                    .pageHeading(view.meeting().getMeetingName())
                    .pageIntro(buildWorkspaceIntro(view))
                    .mainClass("aira-main interophub-meeting-workspace-main")
                    .addLocalStylesheet("/css/meeting-workspace.css")
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

    static void renderWorkspaceContent(PrintWriter out, String contextPath, WorkspaceView view) {
        out.println("    <div class=\"aira-container--wide aira-stack aira-stack--compact\">");
        if (view.feedbackMessage() != null && !"Session started.".equals(view.feedbackMessage())
                && !"Meeting ended.".equals(view.feedbackMessage())) {
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
                        "                <span><strong>" + escapeHtml(item.title()) + "</strong></span>");
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
                if (item.noteResponsibilityLabel() != null && !item.noteResponsibilityLabel().isBlank()) {
                    out.println(
                            "                <span class=\"aira-meta\">" + escapeHtml(item.noteResponsibilityLabel())
                                    + "</span>");
                }
                out.println("              </a>");
            }
            out.println("            </nav>");
        }
        out.println("          </section>");

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h3 class=\"aira-section-title\">Meeting Activity</h3>");
        out.println("            <div class=\"aira-stack aira-stack--compact\">");
        out.println("              <a class=\"aira-link\" href=\""
                + escapeHtml((contextPath == null ? "" : contextPath) + "/es/agenda?meetingId="
                        + view.meeting().getEsMeetingId())
                + "\">View Agenda</a>");
        out.println(
                "              <button class=\"aira-button aira-button--secondary\" type=\"button\" disabled>Next topic</button>");
        out.println("            </div>");
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
            if (view.selectedItem().noteResponsibilityLabel() != null
                    && !view.selectedItem().noteResponsibilityLabel().isBlank()) {
                out.println("            <p class=\"aira-meta\">"
                        + escapeHtml(view.selectedItem().noteResponsibilityLabel())
                        + "</p>");
            }
            if (view.selectedItem().agendaMarkdown() != null && !view.selectedItem().agendaMarkdown().isBlank()) {
                out.println("            <p>" + formatAgendaDetailsHtml(view.selectedItem().agendaMarkdown()) + "</p>");
            } else {
                out.println(
                        "            <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No agenda notes were provided for this item.</p></div>");
            }
            out.println("          </section>");

            out.println("          <section class=\"aira-grid\">");
            renderTopicNotePanel(out, contextPath, view);

            out.println("            <article class=\"aira-panel\">");
            out.println("              <h3 class=\"aira-section-title\">Meeting Controls</h3>");
            out.println("              <div class=\"aira-grid\">");
            out.println("                <form method=\"post\" action=\""
                    + escapeHtml((contextPath == null ? "" : contextPath) + WORKSPACE_PATH)
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
                    + ">Start session</button>");
            out.println("                </form>");
            out.println(
                    "                <button class=\"aira-button aira-button--tertiary\" type=\"button\" disabled>Mark item covered</button>");
            out.println(
                    "                <button class=\"aira-button aira-button--tertiary\" type=\"button\" disabled>Add note</button>");
            out.println("                <form method=\"post\" action=\""
                    + escapeHtml((contextPath == null ? "" : contextPath) + WORKSPACE_PATH)
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
                    + ">End meeting</button>");
            out.println("                </form>");
            out.println("              </div>");
            out.println("              <div class=\"aira-stack aira-stack--compact\" style=\"margin-top: 1rem;\">");
            out.println("                <h4 class=\"aira-section-title\">Roles</h4>");
            out.println("                <table class=\"aira-table\">");
            out.println(
                    "                  <thead><tr><th>Role</th><th>Person assigned</th><th>Action</th></tr></thead>");
            out.println("                  <tbody>");
            boolean hasVisibleRole = false;
            for (RoleSummary role : view.roleSummaries()) {
                if (!shouldRenderRoleSummary(role)) {
                    continue;
                }
                hasVisibleRole = true;
                out.println("                    <tr>");
                out.println("                      <td>" + escapeHtml(role.label()) + "</td>");
                out.println("                      <td>" + escapeHtml(role.name()) + "</td>");
                out.println(
                        "                      <td><button class=\"aira-button aira-button--small\" type=\"button\" disabled>Assign</button></td>");
                out.println("                    </tr>");
            }
            if (!hasVisibleRole) {
                out.println(
                        "                    <tr><td colspan=\"3\" class=\"aira-meta\">No assigned roles to display.</td></tr>");
            }
            out.println("                  </tbody>");
            out.println("                </table>");
            out.println("              </div>");
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
        out.println("    <script src=\"" + escapeHtml((contextPath == null ? "" : contextPath)
                + "/js/meeting-workspace-notes.js") + "\"></script>");
    }

    WorkspaceView buildWorkspaceView(EsMeeting meeting, Long requestedSelectedItemId, User user,
            String feedbackMessage, String errorMessage, String csrfToken) {
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

        Map<Long, EsTopicNote> notesByAgendaItemId = new LinkedHashMap<>();
        for (Long agendaItemId : agendaItemIds) {
            noteDao.findByAgendaItemId(agendaItemId).ifPresent(note -> notesByAgendaItemId.put(agendaItemId, note));
        }

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

        List<Long> noteEditorUserIds = notesByAgendaItemId.values().stream()
                .map(EsTopicNote::getActiveEditorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> noteEditors = noteEditorUserIds.isEmpty()
                ? Map.of()
                : userDao.findByIds(noteEditorUserIds).stream()
                        .filter(resolved -> resolved.getUserId() != null)
                        .collect(Collectors.toMap(User::getUserId, Function.identity(), (left, right) -> left,
                                LinkedHashMap::new));

        List<AgendaItemView> agendaViews = new ArrayList<>();
        for (EsMeetingAgendaItem item : agendaItems) {
            List<EsAgendaItemPresenter> presenters = presentersByItemId.getOrDefault(item.getEsMeetingAgendaItemId(),
                    List.of());
            EsTopicNote openNote = notesByAgendaItemId.get(item.getEsMeetingAgendaItemId());
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
                    agendaResponsibilityLabel(openNote, noteEditors),
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
                    view.noteResponsibilityLabel(),
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
                    selectedItem.noteSummary(), selectedItem.noteStatusLabel(), selectedItem.noteResponsibilityLabel(),
                    selectedItem.startTimeLabel(),
                    selectedItem.endTimeLabel(), selectedItem.timeRangeLabel()));
        }

        List<EsMeetingAttendance> attendance = attendanceDao.findByEsMeetingId(meeting.getEsMeetingId());

        EsTopicMeeting topicMeeting = topicMeetingDao.findById(meeting.getEsTopicMeetingId()).orElse(null);
        String seriesName = topicMeeting != null ? topicMeeting.getMeetingName() : "Meeting";
        String seriesDescription = topicMeeting != null ? topicMeeting.getMeetingDescription() : null;
        String scheduleText = formatMeetingSchedule(meeting);
        String meetingStatusLabel = meetingStatusLabel(meeting.getStatus());
        String meetingStatusClass = meetingStatusClass(meeting.getStatus());
        boolean canStartSession = (meeting.getStatus() == EsMeeting.MeetingStatus.FINALIZED
                || meeting.getStatus() == EsMeeting.MeetingStatus.COMPLETED)
                && MeetingWindowRules.isMeetingStartWindowOpen(meeting);
        boolean canEndMeeting = meeting.getStatus() == EsMeeting.MeetingStatus.IN_SESSION;

        NotePanelView notePanel = buildNotePanelView(meeting, topicMeeting, selectedItem, user, csrfToken, noteEditors);

        return new WorkspaceView(meeting, topicMeeting, seriesName, seriesDescription, scheduleText,
                meetingStatusLabel, meetingStatusClass, roleSummaries, selectedMarked, selectedItem,
                attendance.size(), (int) notesByAgendaItemId.values().stream()
                        .filter(note -> note.getStatus() == TopicNoteStatus.OPEN)
                        .count(),
                userLabel(resolvedUsers.get(meeting.getCreatedByUserId())),
                canStartSession,
                canEndMeeting,
                startSessionHelpText(meeting, canStartSession),
                endMeetingHelpText(meeting, canEndMeeting),
                feedbackMessage,
                errorMessage,
                notePanel);
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

    static boolean canStartSession(EsMeeting meeting, boolean startWindowOpen) {
        return meeting != null
                && meeting.getStatus() == EsMeeting.MeetingStatus.COMPLETED
                && startWindowOpen;
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

    private static boolean shouldRenderRoleSummary(RoleSummary role) {
        if (role == null) {
            return false;
        }
        String label = role.label() != null ? role.label().toLowerCase(Locale.ROOT) : "";
        String name = role.name() != null ? role.name().trim() : "";
        if (label.contains("designated") || label.contains("created by")) {
            return !name.isEmpty() && !"unassigned".equalsIgnoreCase(name);
        }
        if (label.contains("current")) {
            return !name.isEmpty() && !"unassigned".equalsIgnoreCase(name);
        }
        return !name.isEmpty() && !"unassigned".equalsIgnoreCase(name);
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

    private NotePanelView buildNotePanelView(EsMeeting meeting, EsTopicMeeting topicMeeting,
            AgendaItemView selectedItem,
            User user, String csrfToken, Map<Long, User> noteEditors) {
        if (selectedItem == null || selectedItem.agendaItemId() == null) {
            return null;
        }
        EsTopicNote note = noteDao.findByAgendaItemId(selectedItem.agendaItemId()).orElse(null);
        String effectiveTopicName = trimToNull(selectedItem.topicName()) != null
                ? selectedItem.topicName()
                : topicMeeting != null ? topicMeeting.getMeetingName() : "Topic";

        EsTopicNote authorizationCandidate = note != null ? note
                : buildNoteAuthorizationCandidate(meeting, selectedItem.agendaItemId(), effectiveTopicName);
        boolean userCanEdit = user != null
                && meetingAuthorizationService.canEditTopicNote(user.getUserId(), authorizationCandidate);
        boolean noteMutable = userCanEdit
                && meeting.getStatus() != EsMeeting.MeetingStatus.CLOSED
                && (note == null || note.getStatus() != TopicNoteStatus.FINALIZED);
        Long activeEditorUserId = note != null ? note.getActiveEditorUserId() : null;
        User activeEditor = activeEditorUserId != null
                ? noteEditors.getOrDefault(activeEditorUserId, userDao.findById(activeEditorUserId).orElse(null))
                : null;
        String activeEditorDisplayName = activeEditorUserId == null ? null : userLabel(activeEditor);
        boolean currentUserIsActiveEditor = user != null && activeEditorUserId != null
                && activeEditorUserId.equals(user.getUserId());
        boolean canAssumeEditor = noteMutable && userCanEdit;
        boolean showAssumeAction = canAssumeEditor && !currentUserIsActiveEditor;
        String assumeActionLabel = activeEditorUserId == null ? "Start taking notes" : "Take over notes";
        String assumeActionAriaLabel = activeEditorUserId == null || activeEditorDisplayName == null
                ? assumeActionLabel
                : "Take over notes from " + activeEditorDisplayName;
        String responsibilityMessage;
        if (meeting.getStatus() == EsMeeting.MeetingStatus.CLOSED
                || (note != null && note.getStatus() == TopicNoteStatus.FINALIZED)) {
            if (activeEditorDisplayName != null) {
                responsibilityMessage = "Last editor: " + activeEditorDisplayName;
            } else {
                responsibilityMessage = "Notes are read-only.";
            }
        } else if (activeEditorUserId == null) {
            responsibilityMessage = "No one is currently taking notes for this topic.";
        } else if (currentUserIsActiveEditor) {
            responsibilityMessage = "You are taking notes for this topic as " + userLabel(user) + ".";
        } else {
            responsibilityMessage = activeEditorDisplayName + " is taking notes for this topic.";
        }

        boolean noteEditable = noteMutable && currentUserIsActiveEditor;
        String noteDocumentJson = note != null ? note.getDocumentJson() : null;
        String editorSeedJson = noteDocumentJson != null ? noteDocumentJson
                : topicNoteDocumentSupport.buildEmptyBulletDocument();
        String actionLabel = noteDocumentJson == null ? "Edit notes" : "Edit notes";

        return new NotePanelView(
                effectiveTopicName,
                selectedItem.title(),
                note != null,
                note != null ? note.getEsTopicNoteId() : null,
                note != null ? note.getRevisionNo() : 0L,
                note != null && note.getActiveEditorVersion() != null ? note.getActiveEditorVersion() : 0L,
                note != null ? topicNoteStatusLabel(note.getStatus()) : "Open",
                note != null ? note.getStatus().name() : "OPEN",
                note != null ? noteLastSavedLabel(note) : null,
                note != null ? noteUpdatedAtIso(note.getUpdatedAt()) : null,
                noteDocumentJson,
                editorSeedJson,
                activeEditorUserId,
                activeEditorDisplayName,
                responsibilityMessage,
                assumeActionLabel,
                assumeActionAriaLabel,
                actionLabel,
                noteEditable,
                canAssumeEditor,
                showAssumeAction,
                canAssumeEditor,
                user != null ? user.getUserId() : null,
                csrfToken);
    }

    private static String agendaResponsibilityLabel(EsTopicNote openNote, Map<Long, User> noteEditors) {
        if (openNote == null) {
            return "No note-taker";
        }
        if (openNote.getStatus() == TopicNoteStatus.FINALIZED) {
            return "Notes finalized";
        }
        if (openNote.getActiveEditorUserId() == null) {
            return "No note-taker";
        }
        User editor = noteEditors.get(openNote.getActiveEditorUserId());
        String label = userLabel(editor);
        if (label == null || label.isBlank() || "Unassigned".equals(label)) {
            return "User #" + openNote.getActiveEditorUserId() + " is taking notes";
        }
        return label + " is taking notes";
    }

    private EsTopicNote buildNoteAuthorizationCandidate(EsMeeting meeting, Long agendaItemId, String noteTitle) {
        EsTopicNote note = new EsTopicNote();
        note.setEsMeetingId(meeting.getEsMeetingId());
        note.setEsMeetingAgendaItemId(agendaItemId);
        note.setEsTopicId(resolveEffectiveTopicId(meeting, agendaItemId));
        note.setNoteTitle(noteTitle);
        return note;
    }

    private Long resolveEffectiveTopicId(EsMeeting meeting, Long agendaItemId) {
        EsMeetingAgendaItem agendaItem = agendaItemDao.findById(agendaItemId).orElse(null);
        if (agendaItem != null && agendaItem.getEsTopicId() != null) {
            return agendaItem.getEsTopicId();
        }
        return topicMeetingDao.findById(meeting.getEsTopicMeetingId())
                .map(EsTopicMeeting::getEsTopicId)
                .orElse(null);
    }

    private static String noteLastSavedLabel(EsTopicNote note) {
        LocalDateTime updatedAt = note.getUpdatedAt();
        if (updatedAt == null) {
            return null;
        }
        return DATE_TIME_FMT.format(updatedAt.atZone(ZoneOffset.UTC));
    }

    private static String noteUpdatedAtIso(LocalDateTime updatedAt) {
        if (updatedAt == null) {
            return null;
        }
        return updatedAt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static void renderTopicNotePanel(PrintWriter out, String contextPath, WorkspaceView view) {
        NotePanelView notePanel = view.notePanel();
        out.println("            <article class=\"aira-panel\">");
        out.println("              <h3 class=\"aira-section-title\">Topic Notes</h3>");
        if (notePanel == null) {
            out.println(
                    "              <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">Select an agenda item to view its notes.</p></div>");
            out.println("            </article>");
            return;
        }
        out.println("              <div class=\"aira-stack aira-stack--compact\" data-meeting-note-root>");
        out.println("                <div class=\"aira-cluster aira-cluster--between\">");
        out.println("                  <div class=\"aira-stack aira-stack--compact\">");
        if (notePanel.noteId() != null) {
            out.println("                    <p class=\"aira-meta\">Revision " + notePanel.revision() + "</p>");
        }
        out.println("                  </div>");
        out.println("                </div>");
        out.println("                <p class=\"aira-alert aira-alert--info\" data-note-owner-text>"
                + escapeHtml(notePanel.responsibilityMessage()) + "</p>");
        out.println("                <div class=\"aira-alert aira-alert--info\" data-note-message hidden></div>");
        out.println("                <div class=\"aira-panel aira-prose\" data-note-readonly"
                + (notePanel.noteExists() ? "" : " hidden")
                + "></div>");
        if (!notePanel.noteExists()) {
            out.println(
                    "                <div class=\"aira-empty-state\" data-note-empty-state><p class=\"aira-empty-state__title\">No note has been saved for this agenda item yet.</p></div>");
        } else {
            out.println("                <div class=\"aira-meta\" data-note-empty-state hidden></div>");
        }
        if (notePanel.canAssumeEditor() && notePanel.showAssumeAction()) {
            out.println(
                    "                <button type=\"button\" class=\"aira-button aira-button--secondary\" data-note-assume-editorship"
                            + " aria-label=\""
                            + escapeHtml(notePanel.assumeActionAriaLabel()) + "\">"
                            + escapeHtml(notePanel.assumeActionLabel()) + "</button>");
        }
        if (notePanel.canShowEditButton() && !notePanel.canEdit()) {
            out.println(
                    "                <button type=\"button\" class=\"aira-button aira-button--secondary\" data-note-edit-toggle>"
                            + escapeHtml(notePanel.actionLabel()) + "</button>");
        }
        out.println(
            "                <div class=\"aira-stack aira-stack--compact\" data-note-edit hidden>");
        out.println("                  <div class=\"aira-panel aira-prose\" data-note-editor></div>");
        out.println("                  <div class=\"aira-cluster aira-cluster--between imw-note-edit-footer\">");
        out.println("                    <div class=\"aira-cluster imw-note-edit-actions\">");
        out.println(
            "                      <button type=\"button\" class=\"aira-button aira-button--primary\" data-note-save>Save now</button>");
        out.println(
            "                      <button type=\"button\" class=\"aira-button aira-button--secondary\" data-note-cancel>Done</button>");
        out.println("                    </div>");
        out.println("                    <div class=\"aira-stack aira-stack--compact imw-note-edit-status\">");
        if (notePanel.lastSavedLabel() != null) {
            out.println("                      <p class=\"aira-meta\">Last saved " + escapeHtml(notePanel.lastSavedLabel())
                + "</p>");
        }
        out.println("                      <span class=\"aira-badge aira-badge--warning\" data-note-dirty hidden>Unsaved changes</span>");
        out.println("                      <p class=\"aira-meta\" data-note-save-state>Saved</p>");
        out.println("                    </div>");
        out.println("                  </div>");
        out.println("                </div>");
        out.println(
            "                <section class=\"aira-panel aira-stack aira-stack--compact\" data-note-outcomes-root>");
        out.println("                  <div class=\"aira-cluster aira-cluster--between\">");
        out.println("                    <h4 class=\"aira-section-title\">Recorded Outcomes</h4>");
        out.println(
                "                    <button type=\"button\" class=\"aira-button aira-button--secondary\" data-outcome-add hidden>Add outcome from selected bullet</button>");
        out.println("                  </div>");
        out.println("                  <div class=\"aira-stack aira-stack--compact\" data-outcome-list></div>");
        out.println("                </section>");
        out.println("                <script type=\"application/json\" data-note-config>"
                + escapeScriptData(noteConfigJson(contextPath, view, notePanel))
                + "</script>");
        out.println("              </div>");
        out.println("            </article>");
    }

    private static String noteConfigJson(String contextPath, WorkspaceView view, NotePanelView notePanel) {
        JSONObject json = new JSONObject();
        json.put("meetingId", view.meeting().getEsMeetingId());
        json.put("agendaItemId", view.selectedItem().agendaItemId());
        json.put("noteId", notePanel.noteId() == null ? JSONObject.NULL : notePanel.noteId());
        json.put("revision", notePanel.revision());
        json.put("editorVersion", notePanel.editorVersion());
        json.put("canEdit", notePanel.canEdit());
        json.put("canAssumeEditor", notePanel.canAssumeEditor());
        json.put("showAssumeAction", notePanel.showAssumeAction());
        json.put("canShowEditButton", notePanel.canShowEditButton());
        json.put("currentUserId", notePanel.currentUserId() == null ? JSONObject.NULL : notePanel.currentUserId());
        json.put("activeEditorUserId",
                notePanel.activeEditorUserId() == null ? JSONObject.NULL : notePanel.activeEditorUserId());
        json.put("activeEditorDisplayName",
                notePanel.activeEditorDisplayName() == null ? JSONObject.NULL : notePanel.activeEditorDisplayName());
        json.put("responsibilityMessage",
                notePanel.responsibilityMessage() == null ? JSONObject.NULL : notePanel.responsibilityMessage());
        json.put("saveUrl", (contextPath == null ? "" : contextPath) + "/es/meeting-topic-note?meetingId="
                + view.meeting().getEsMeetingId() + "&agendaItemId=" + view.selectedItem().agendaItemId());
        json.put("startEditingUrl", (contextPath == null ? "" : contextPath)
                + "/es/meeting-topic-note?action=startEditing&meetingId=" + view.meeting().getEsMeetingId()
                + "&agendaItemId=" + view.selectedItem().agendaItemId());
        json.put("takeOverUrl", (contextPath == null ? "" : contextPath)
                + "/es/meeting-topic-note?action=takeOver");
        json.put("editorStateUrl", (contextPath == null ? "" : contextPath)
                + "/es/meeting-topic-note?action=editorState");
        json.put("contentUrl", (contextPath == null ? "" : contextPath)
                + "/es/meeting-topic-note?action=content");
        json.put("outcomesUrl", (contextPath == null ? "" : contextPath)
                + "/es/meeting-topic-note?action=outcomes");
        json.put("createOutcomeUrl", (contextPath == null ? "" : contextPath)
                + "/es/meeting-topic-note?action=createOutcome");
        json.put("updateOutcomeUrl", (contextPath == null ? "" : contextPath)
                + "/es/meeting-topic-note?action=updateOutcome");
        json.put("removeOutcomeUrl", (contextPath == null ? "" : contextPath)
                + "/es/meeting-topic-note?action=removeOutcome");
        json.put("streamUrl", (contextPath == null ? "" : contextPath)
                + "/es/meeting-topic-note-stream");
        json.put("pollIntervalMs", 5000);
        json.put("autosaveDebounceMs", 1500);
        json.put("autosaveMaxIntervalMs", 10000);
        json.put("csrfToken", notePanel.csrfToken());
        json.put("noteStatus", notePanel.statusCode());
        json.put("meetingStatus",
                view.meeting().getStatus() == null ? JSONObject.NULL : view.meeting().getStatus().name());
        json.put("initialDocument", notePanel.editorDocumentJson() == null ? JSONObject.NULL
                : new JSONObject(notePanel.editorDocumentJson()));
        json.put("readOnlyDocument", notePanel.readOnlyDocumentJson() == null ? JSONObject.NULL
                : new JSONObject(notePanel.readOnlyDocumentJson()));
        json.put("lastSavedAt", notePanel.lastSavedIso() == null ? JSONObject.NULL : notePanel.lastSavedIso());
        json.put("canManageOutcomes", notePanel.canEdit());
        json.put("outcomeTypes", recordedOutcomeTypesJson());
        return json.toString();
    }

    private static org.json.JSONArray recordedOutcomeTypesJson() {
        org.json.JSONArray values = new org.json.JSONArray();
        for (RecordedOutcomeType type : RecordedOutcomeType.values()) {
            values.put(type.name());
        }
        return values;
    }

    private static String escapeScriptData(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("<", "\\u003c");
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
            String feedbackMessage, String errorMessage, NotePanelView notePanel) {
    }

    public record RoleSummary(String label, String name, String meta) {
    }

    private record AgendaTimeRange(String startTimeLabel, String endTimeLabel, String timeRangeLabel) {
    }

    public record AgendaItemView(Long agendaItemId, Integer displayOrder, String title, String topicName,
            String statusLabel, String statusClass, String presenterSummary, Integer timeMinutes,
            String agendaMarkdown, boolean selected, String noteSummary, String noteStatusLabel,
            String noteResponsibilityLabel,
            String startTimeLabel, String endTimeLabel, String timeRangeLabel) {
    }

    public record NotePanelView(String topicName, String agendaItemTitle, boolean noteExists, Long noteId,
            Long revision, Long editorVersion, String statusLabel, String statusCode,
            String lastSavedLabel, String lastSavedIso, String readOnlyDocumentJson, String editorDocumentJson,
            Long activeEditorUserId, String activeEditorDisplayName, String responsibilityMessage,
            String assumeActionLabel, String assumeActionAriaLabel, String actionLabel,
            boolean canEdit, boolean canAssumeEditor, boolean showAssumeAction, boolean canShowEditButton,
            Long currentUserId, String csrfToken) {
    }
}