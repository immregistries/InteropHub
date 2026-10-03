package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.AdminMeetingBrowseRow;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.dao.EsTopicMeetingSurveyDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.EsTopicMeetingMember.MembershipStatus;
import org.airahub.interophub.model.User;
import org.airahub.interophub.model.EsSurvey;
import org.airahub.interophub.model.EsTopicMeetingSurvey;
import org.airahub.interophub.service.EsSurveyService;
import org.airahub.interophub.service.MeetingCommunicationService;
import org.airahub.interophub.service.PublicUrlService;

public class AdminEsMeetingServlet extends HttpServlet {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_TIMEZONE = "America/New_York";
    private static final String ACTIVE_HREF = "/admin/es/meetings";

    private final EsTopicMeetingDao meetingDao;
    private final EsTopicMeetingMemberDao memberDao;
    private final EsTopicDao topicDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final UserDao userDao;
    private final PublicUrlService publicUrlService;
    private final EsMeetingDao esMeetingDao;
    private final MeetingCommunicationService meetingCommunicationService;
    private final EsTopicMeetingSurveyDao topicMeetingSurveyDao;
    private final EsSurveyService esSurveyService;

    public AdminEsMeetingServlet() {
        this.meetingDao = new EsTopicMeetingDao();
        this.memberDao = new EsTopicMeetingMemberDao();
        this.topicDao = new EsTopicDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.userDao = new UserDao();
        this.publicUrlService = new PublicUrlService();
        this.esMeetingDao = new EsMeetingDao();
        this.meetingCommunicationService = new MeetingCommunicationService();
        this.topicMeetingSurveyDao = new EsTopicMeetingSurveyDao();
        this.esSurveyService = new EsSurveyService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        if ("new".equals(request.getParameter("action"))) {
            EsTopicMeeting draft = new EsTopicMeeting();
            draft.setEsTopicId(parseId(trimToNull(request.getParameter("topicId"))));
            renderNewSeriesForm(request, response, draft, null);
            return;
        }

        String meetingIdRaw = trimToNull(request.getParameter("meetingId"));

        if (meetingIdRaw != null) {
            Long meetingId = parseId(meetingIdRaw);
            if (meetingId == null) {
                renderList(request, response, "Invalid meeting identifier.");
                return;
            }

            EsTopicMeeting meeting = meetingDao.findById(meetingId).orElse(null);
            if (meeting == null) {
                renderList(request, response, "Meeting not found.");
                return;
            }

            String savedMsg = request.getParameter("created") != null
                    ? "Meeting series created. Create its first agenda below."
                    : request.getParameter("saved") != null ? "Membership status updated." : null;
            renderDetail(request, response, meeting, savedMsg);
            return;
        }

        renderList(request, response, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        Long memberId = parseId(trimToNull(request.getParameter("memberId")));
        Long meetingId = parseId(trimToNull(request.getParameter("meetingId")));
        String action = trimToNull(request.getParameter("action"));

        if ("createSeries".equals(action)) {
            handleCreateSeries(request, response, adminUser.get());
            return;
        }

        if (meetingId == null || action == null) {
            response.sendRedirect(contextPath + "/admin/es/meetings");
            return;
        }

        if ("createAgenda".equals(action)) {
            handleCreateAgenda(request, response, meetingId, adminUser.get());
            return;
        }

        if (memberId == null) {
            response.sendRedirect(contextPath + "/admin/es/meetings");
            return;
        }

        EsTopicMeetingMember member = memberDao.findById(memberId).orElse(null);
        if (member == null || !meetingId.equals(member.getEsTopicMeetingId())) {
            response.sendRedirect(contextPath + "/admin/es/meetings?meetingId=" + meetingId);
            return;
        }

        applyTransition(member, action, adminUser.get().getUserId());

        response.sendRedirect(contextPath + "/admin/es/meetings?meetingId=" + meetingId + "&saved=1");
    }

    private void applyTransition(EsTopicMeetingMember member, String action, Long adminUserId) {
        MembershipStatus current = member.getMembershipStatus();
        MembershipStatus newStatus = null;

        switch (action.toLowerCase()) {
            case "approve":
                if (current == MembershipStatus.REQUESTED
                        || current == MembershipStatus.DECLINED
                        || current == MembershipStatus.REMOVED) {
                    newStatus = MembershipStatus.APPROVED;
                }
                break;
            case "decline":
                if (current == MembershipStatus.REQUESTED) {
                    newStatus = MembershipStatus.DECLINED;
                }
                break;
            case "remove":
                if (current == MembershipStatus.APPROVED) {
                    newStatus = MembershipStatus.REMOVED;
                }
                break;
            default:
                break;
        }

        if (newStatus != null) {
            member.setMembershipStatus(newStatus);
            if (newStatus == MembershipStatus.APPROVED) {
                member.setApprovedByUserId(adminUserId);
                member.setApprovedAt(LocalDateTime.now());
            }
            memberDao.saveOrUpdate(member);
        }
    }

    private void handleCreateAgenda(HttpServletRequest request, HttpServletResponse response,
            Long meetingId, User adminUser) throws IOException {
        String contextPath = request.getContextPath();
        EsTopicMeeting topicMeeting = meetingDao.findById(meetingId).orElse(null);
        if (topicMeeting == null) {
            renderList(request, response, "Meeting not found.");
            return;
        }

        EsTopic hostTopic = topicMeeting.getEsTopicId() == null
                ? null
                : topicDao.findById(topicMeeting.getEsTopicId()).orElse(null);
        if (hostTopic == null || !topicSpaceDao.isActiveSpaceId(hostTopic.getEsTopicSpaceId())) {
            renderDetail(request, response, topicMeeting,
                    "Cannot create a new meeting because the host Topic Space is inactive.");
            return;
        }

        String agendaDateRaw = trimToNull(request.getParameter("agendaDate"));
        if (agendaDateRaw == null) {
            renderDetail(request, response, topicMeeting, "Date is required to create an agenda.");
            return;
        }

        LocalDate submittedDate;
        try {
            submittedDate = LocalDate.parse(agendaDateRaw, DATE_ONLY_FORMAT);
        } catch (DateTimeParseException ex) {
            renderDetail(request, response, topicMeeting,
                    "Invalid date: \"" + agendaDateRaw + "\". Use YYYY-MM-DD format.");
            return;
        }

        Optional<EsMeeting> previous = esMeetingDao.findMostRecentPrevious(
                topicMeeting.getEsTopicMeetingId(), submittedDate.atStartOfDay());

        LocalDateTime scheduledStart;
        LocalDateTime scheduledEnd = null;
        String timezoneId;
        String onlineMeetingUrl;
        String onlineMeetingDetails;

        if (previous.isPresent()) {
            EsMeeting prev = previous.get();
            timezoneId = prev.getTimezoneId();
            LocalTime prevTime = prev.getScheduledStart().toLocalTime();
            scheduledStart = submittedDate.atTime(prevTime);
            if (prev.getScheduledEnd() != null) {
                long durationMinutes = java.time.Duration.between(
                        prev.getScheduledStart(), prev.getScheduledEnd()).toMinutes();
                scheduledEnd = scheduledStart.plusMinutes(durationMinutes);
            }
            onlineMeetingUrl = prev.getOnlineMeetingUrl();
            onlineMeetingDetails = prev.getOnlineMeetingDetails();
        } else {
            timezoneId = "America/New_York";
            scheduledStart = submittedDate.atTime(11, 0);
            onlineMeetingUrl = topicMeeting.getOnlineMeetingUrl();
            onlineMeetingDetails = topicMeeting.getOnlineMeetingDetails();
        }

        if (scheduledStart.atZone(safeZoneId(timezoneId)).isBefore(ZonedDateTime.now(safeZoneId(timezoneId)))) {
            renderDetail(request, response, topicMeeting,
                    "Meeting date cannot be in the past: \"" + agendaDateRaw + "\".");
            return;
        }

        EsMeeting newMeeting = new EsMeeting();
        newMeeting.setEsTopicMeetingId(topicMeeting.getEsTopicMeetingId());
        newMeeting.setEsTopicSpaceId(hostTopic.getEsTopicSpaceId());
        newMeeting.setMeetingName(topicMeeting.getMeetingName());
        newMeeting.setMeetingDescription(topicMeeting.getMeetingDescription());
        newMeeting.setScheduledStart(scheduledStart);
        newMeeting.setScheduledEnd(scheduledEnd);
        newMeeting.setTimezoneId(timezoneId);
        newMeeting.setOnlineMeetingUrl(onlineMeetingUrl);
        newMeeting.setOnlineMeetingDetails(onlineMeetingDetails);
        newMeeting.setStatus(EsMeeting.MeetingStatus.DRAFT);
        newMeeting.setCreatedByUserId(adminUser.getUserId());

        esMeetingDao.save(newMeeting);

        response.sendRedirect(contextPath + "/admin/es/meetings?meetingId=" + meetingId + "&saved=1");
    }

    private void handleCreateSeries(HttpServletRequest request, HttpServletResponse response, User adminUser)
            throws IOException {
        EsTopicMeeting draft = new EsTopicMeeting();
        draft.setEsTopicId(parseId(trimToNull(request.getParameter("topicId"))));
        draft.setMeetingName(trimToNull(request.getParameter("meetingName")));
        draft.setMeetingDescription(trimToNull(request.getParameter("meetingDescription")));
        draft.setOnlineMeetingUrl(trimToNull(request.getParameter("onlineMeetingUrl")));
        draft.setOnlineMeetingDetails(trimToNull(request.getParameter("onlineMeetingDetails")));
        draft.setJoinRequiresApproval(request.getParameter("meetingRequiresApproval") != null);

        EsTopic topic = draft.getEsTopicId() == null
                ? null
                : topicDao.findById(draft.getEsTopicId()).orElse(null);
        if (topic == null) {
            renderNewSeriesForm(request, response, draft, "Select the topic this meeting series belongs to.");
            return;
        }
        if (topic.getStatus() != EsTopic.EsTopicStatus.ACTIVE
                || !topicSpaceDao.isActiveSpaceId(topic.getEsTopicSpaceId())) {
            renderNewSeriesForm(request, response, draft,
                    "Meeting series can only be created for active topics in an active Topic Space.");
            return;
        }

        // A topic hosts at most one series; a previously disabled one is reactivated
        // rather than duplicated.
        EsTopicMeeting meeting = meetingDao.findByTopicId(topic.getEsTopicId()).orElse(null);
        if (meeting != null && meeting.getStatus() == EsTopicMeeting.MeetingStatus.ACTIVE) {
            renderNewSeriesForm(request, response, draft,
                    "\"" + topic.getTopicName() + "\" already has a meeting series.");
            return;
        }
        if (meeting == null) {
            meeting = new EsTopicMeeting();
            meeting.setEsTopicId(topic.getEsTopicId());
        }
        meeting.setMeetingName(draft.getMeetingName() != null ? draft.getMeetingName() : topic.getTopicName());
        meeting.setMeetingDescription(draft.getMeetingDescription());
        meeting.setOnlineMeetingUrl(draft.getOnlineMeetingUrl());
        meeting.setOnlineMeetingDetails(draft.getOnlineMeetingDetails());
        meeting.setJoinRequiresApproval(draft.getJoinRequiresApproval());
        meeting.setStatus(EsTopicMeeting.MeetingStatus.ACTIVE);
        meeting.setDisabledAt(null);
        meeting.setDisabledByUserId(null);
        EsTopicMeeting saved = meetingDao.saveOrUpdate(meeting);

        response.sendRedirect(request.getContextPath() + "/admin/es/meetings?meetingId="
                + saved.getEsTopicMeetingId() + "&created=1");
    }

    private void renderNewSeriesForm(HttpServletRequest request, HttpServletResponse response,
            EsTopicMeeting draft, String message) throws IOException {
        String contextPath = request.getContextPath();
        java.util.Set<Long> topicIdsWithSeries = meetingDao.findTopicIdsWithActiveMeeting();
        Map<String, List<EsTopic>> candidatesBySpace = new LinkedHashMap<>();
        for (org.airahub.interophub.model.EsTopicSpace space : topicSpaceDao.findAllActiveOrdered()) {
            List<EsTopic> candidates = topicDao.findActiveBySpaceIdOrderByTopicName(space.getEsTopicSpaceId())
                    .stream()
                    .filter(t -> !topicIdsWithSeries.contains(t.getEsTopicId()))
                    .collect(Collectors.toList());
            if (!candidates.isEmpty()) {
                candidatesBySpace.put(orEmpty(space.getSpaceName()), candidates);
            }
        }

        AdminShellRenderer.render(request, response, "New Meeting Series - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">New Meeting Series</h2>");
                    out.println("            <p class=\"aira-meta\">A meeting series belongs to a topic; its attendance"
                            + " link and membership come from that topic. Each topic can host one series."
                            + " (You can also enable a series from the topic's edit page.)</p>");
                    if (message != null && !message.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--info\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }

                    if (candidatesBySpace.isEmpty()) {
                        out.println("            <p>Every active topic already has a meeting series."
                                + " Create a topic first, then add its series.</p>");
                    } else {
                        out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                                + "/admin/es/meetings\">");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"createSeries\">");

                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"topicId\">Topic</label>");
                        out.println("                <select class=\"aira-select\" id=\"topicId\" name=\"topicId\" required>");
                        out.println("                  <option value=\"\">Select a topic&hellip;</option>");
                        for (Map.Entry<String, List<EsTopic>> group : candidatesBySpace.entrySet()) {
                            out.println("                  <optgroup label=\"" + escapeHtml(group.getKey()) + "\">");
                            for (EsTopic t : group.getValue()) {
                                boolean selected = t.getEsTopicId().equals(draft.getEsTopicId());
                                out.println("                    <option value=\"" + t.getEsTopicId() + "\""
                                        + (selected ? " selected" : "") + ">"
                                        + escapeHtml(orEmpty(t.getTopicName())) + "</option>");
                            }
                            out.println("                  </optgroup>");
                        }
                        out.println("                </select>");
                        out.println("              </div>");

                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"meetingName\">Series Name (defaults to the topic name)</label>");
                        out.println("                <input class=\"aira-input\" id=\"meetingName\" name=\"meetingName\" type=\"text\" value=\""
                                + escapeHtml(orEmpty(draft.getMeetingName())) + "\" />");
                        out.println("              </div>");

                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"meetingDescription\">Description</label>");
                        out.println("                <textarea class=\"aira-textarea\" id=\"meetingDescription\" name=\"meetingDescription\" rows=\"4\">"
                                + escapeHtml(orEmpty(draft.getMeetingDescription())) + "</textarea>");
                        out.println("              </div>");

                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"onlineMeetingUrl\">Meeting URL (e.g. Zoom link)</label>");
                        out.println("                <input class=\"aira-input\" id=\"onlineMeetingUrl\" name=\"onlineMeetingUrl\" type=\"text\" value=\""
                                + escapeHtml(orEmpty(draft.getOnlineMeetingUrl())) + "\" />");
                        out.println("              </div>");

                        out.println("              <div class=\"aira-field\">");
                        out.println("                <label for=\"onlineMeetingDetails\">Connection Details (dial-in info, passcode, etc.)</label>");
                        out.println("                <textarea class=\"aira-textarea\" id=\"onlineMeetingDetails\" name=\"onlineMeetingDetails\" rows=\"5\">"
                                + escapeHtml(orEmpty(draft.getOnlineMeetingDetails())) + "</textarea>");
                        out.println("              </div>");

                        out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"meetingRequiresApproval\""
                                + (Boolean.TRUE.equals(draft.getJoinRequiresApproval()) ? " checked" : "")
                                + " /> Join Requires Approval</label>");

                        out.println("              <div class=\"aira-action-group\">");
                        out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Create Meeting Series</button>");
                        out.println("              </div>");
                        out.println("            </form>");
                    }

                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/meetings\">Back to Meeting Series</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderList(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        String contextPath = request.getContextPath();
        List<AdminMeetingBrowseRow> meetings = meetingDao.findAllActiveBrowseRows();

        AdminShellRenderer.render(request, response, "Meeting Series Admin - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Meeting Series</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Each topic can host one recurring meeting series."
                                    + " Open a series to manage its members and create agendas for individual meetings.</p>");
                    if (message != null && !message.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--info\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                            + "/admin/es/meetings?action=new\">+ New Meeting Series</a>");
                    out.println("            </div>");

                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead>");
                    out.println("                <tr>");
                    out.println("                  <th>Series Name</th>");
                    out.println("                  <th>Approved</th>");
                    out.println("                  <th>Requested</th>");
                    out.println("                </tr>");
                    out.println("              </thead>");
                    out.println("              <tbody>");
                    for (AdminMeetingBrowseRow row : meetings) {
                        out.println("                <tr>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es/meetings?meetingId="
                                + row.getEsTopicMeetingId() + "\">"
                                + escapeHtml(orEmpty(row.getMeetingName())) + "</a></td>");
                        out.println("                  <td>" + row.getApprovedCount() + "</td>");
                        out.println("                  <td>" + row.getRequestedCount() + "</td>");
                        out.println("                </tr>");
                    }
                    if (meetings.isEmpty()) {
                        out.println("                <tr>");
                        out.println("                  <td colspan=\"3\">No active meeting series found.</td>");
                        out.println("                </tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es\">Back to Emerging Standards</a></p>");
                    out.println("          </section>");
                    // Meeting Surveys section
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Meeting Survey Assignments</h2>");
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                            + "/admin/es/meeting-survey?action=new\">+ New Assignment</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/meeting-survey\">All Assignments</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/surveys\">Manage Surveys</a>");
                    out.println("            </div>");
                    List<EsTopicMeetingSurvey> assignments = topicMeetingSurveyDao.findAllOrdered();
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead><tr>"
                            + "<th>ID</th><th>Meeting ID</th><th>Survey</th>"
                            + "<th>Start</th><th>End</th><th>Status</th><th>Responses</th><th>Actions</th>"
                            + "</tr></thead>");
                    out.println("              <tbody>");
                    for (EsTopicMeetingSurvey a : assignments) {
                        EsSurvey s = esSurveyService.getSurvey(a.getEsSurveyId()).orElse(null);
                        long responseCount = 0;
                        try {
                            responseCount = new org.airahub.interophub.dao.EsSurveyResponseDao()
                                    .countByTopicMeetingSurveyId(a.getEsTopicMeetingSurveyId());
                        } catch (Exception ignore) {
                        }
                        String editUrl = contextPath + "/admin/es/meeting-survey?assignmentId="
                                + a.getEsTopicMeetingSurveyId();
                        String resultsUrl = contextPath + "/admin/es/survey-results?assignmentId="
                                + a.getEsTopicMeetingSurveyId();
                        out.println("                <tr>");
                        out.println("                  <td>" + a.getEsTopicMeetingSurveyId() + "</td>");
                        out.println("                  <td>" + a.getEsTopicMeetingId() + "</td>");
                        out.println("                  <td>"
                                + escapeHtml(s != null ? s.getSurveyName() : "?") + "</td>");
                        out.println("                  <td>" + escapeHtml(a.getStartDate().toString()) + "</td>");
                        out.println("                  <td>" + escapeHtml(a.getEndDate().toString()) + "</td>");
                        out.println("                  <td>"
                                + escapeHtml(a.getStatus() != null ? a.getStatus().name() : "") + "</td>");
                        out.println("                  <td>" + responseCount + "</td>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + editUrl
                                + "\">Edit</a>"
                                + " | <a class=\"aira-inline-link\" href=\"" + resultsUrl + "\">Results</a></td>");
                        out.println("                </tr>");
                    }
                    if (assignments.isEmpty()) {
                        out.println(
                                "                <tr><td colspan=\"8\">No survey assignments found.</td></tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");
                    out.println("          </section>");
                });
    }

    private void renderDetail(HttpServletRequest request, HttpServletResponse response, EsTopicMeeting meeting,
            String message) throws IOException {
        String contextPath = request.getContextPath();

        List<EsTopicMeetingMember> requested = memberDao.findByMeetingIdAndStatus(
                meeting.getEsTopicMeetingId(), MembershipStatus.REQUESTED);
        List<EsTopicMeetingMember> approved = memberDao.findByMeetingIdAndStatus(
                meeting.getEsTopicMeetingId(), MembershipStatus.APPROVED);
        List<EsTopicMeetingMember> declined = memberDao.findByMeetingIdAndStatus(
                meeting.getEsTopicMeetingId(), MembershipStatus.DECLINED);
        List<EsTopicMeetingMember> removed = memberDao.findByMeetingIdAndStatus(
                meeting.getEsTopicMeetingId(), MembershipStatus.REMOVED);

        Map<Long, User> usersById = loadUsersByMembers(requested, approved, declined, removed);
        Long meetingId = meeting.getEsTopicMeetingId();

        EsTopic topic = meeting.getEsTopicId() != null
                ? topicDao.findById(meeting.getEsTopicId()).orElse(null)
                : null;
        String topicCode = topic != null ? topic.getTopicCode() : null;
        String backPath = "/admin/es/meetings?meetingId=" + meetingId;
        String attendancePath = topicCode != null ? "/attend/" + encodePathSegment(topicCode) : null;
        String attendanceAbsoluteUrl = attendancePath != null
                ? publicUrlService.resolveExternalUrl(attendancePath)
                : null;
        String attendanceQrUrl = attendancePath != null
                ? buildQrPageUrl(contextPath, attendancePath, "Meeting Attendance", backPath)
                : null;

        List<EsMeeting> agendas = esMeetingDao.findByEsTopicMeetingId(meetingId);
        Map<Long, EsMeetingCommunication> nextScheduledByMeetingId = new LinkedHashMap<>();
        for (EsMeeting agenda : agendas) {
            if (agenda.getEsMeetingId() == null) {
                continue;
            }
            meetingCommunicationService.findNextScheduledByMeetingId(agenda.getEsMeetingId())
                    .ifPresent(c -> nextScheduledByMeetingId.put(agenda.getEsMeetingId(), c));
        }

        AdminShellRenderer.render(request, response, "Meeting - InteropHub", AdminSection.TOPIC_SPACES, ACTIVE_HREF,
                out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">"
                            + escapeHtml(orEmpty(meeting.getMeetingName())) + "</h2>");

                    if (message != null && !message.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--info\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }

                    if (attendanceAbsoluteUrl != null) {
                        out.println("            <p><strong>Attendance URL:</strong> <a class=\"aira-inline-link\" href=\""
                                + escapeHtml(attendanceAbsoluteUrl) + "\">" + escapeHtml(attendanceAbsoluteUrl)
                                + "</a> (<a class=\"aira-inline-link\" href=\"" + escapeHtml(attendanceQrUrl)
                                + "\">qr code</a>)</p>");
                    }

                    renderAgendasSection(out, contextPath, meetingId, agendas, nextScheduledByMeetingId);

                    if (!requested.isEmpty()) {
                        out.println("            <h3 class=\"aira-subsection-title\">Requested (" + requested.size()
                                + ")</h3>");
                        renderMemberTable(out, contextPath, meetingId, requested, usersById,
                                List.of("approve", "decline"));
                    }

                    if (!approved.isEmpty()) {
                        out.println("            <h3 class=\"aira-subsection-title\">Approved (" + approved.size()
                                + ")</h3>");
                        renderMemberTable(out, contextPath, meetingId, approved, usersById,
                                List.of("remove"));
                    }

                    if (!declined.isEmpty()) {
                        out.println("            <h3 class=\"aira-subsection-title\">Declined (" + declined.size()
                                + ")</h3>");
                        renderMemberTable(out, contextPath, meetingId, declined, usersById,
                                List.of("approve"));
                    }

                    if (!removed.isEmpty()) {
                        out.println("            <h3 class=\"aira-subsection-title\">Removed (" + removed.size()
                                + ")</h3>");
                        renderMemberTable(out, contextPath, meetingId, removed, usersById,
                                List.of("approve"));
                    }

                    if (requested.isEmpty() && approved.isEmpty() && declined.isEmpty() && removed.isEmpty()) {
                        out.println("            <p class=\"aira-meta\">No members found for this meeting.</p>");
                    }

                    if (topic != null) {
                        out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/es/topic/"
                                + topic.getEsTopicId() + "\">View Topic Page</a></p>");
                        out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/es/topic-edit/"
                                + topic.getEsTopicId() + "\">Edit Series Settings</a>"
                                + " <span class=\"aira-meta\">(name, meeting URL, and approval are"
                                + " configured on the topic)</span></p>");
                    }
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/meeting-polls?esTopicMeetingId="
                            + meetingId + "\">Manage Meeting Polls</a></p>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/es/meeting-series?seriesId=" + meetingId
                            + "\">View Public Meetings</a></p>");
                    out.println(
                            "            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                    + "/admin/es/meetings\">Back to Meeting Series</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderAgendasSection(PrintWriter out, String contextPath, Long meetingId,
            List<EsMeeting> agendas, Map<Long, EsMeetingCommunication> nextScheduledByMeetingId) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(14);
        List<EsMeeting> visibleAgendas = agendas.stream()
                .filter(a -> a.getScheduledStart() == null || !a.getScheduledStart().isBefore(cutoff))
                .collect(Collectors.toList());

        out.println("            <h3 class=\"aira-subsection-title\">Agendas</h3>");
        out.println("            <div class=\"aira-table-wrap\">");
        out.println("            <table class=\"aira-table\">");
        out.println("              <thead>");
        out.println("                <tr>");
        out.println("                  <th>Meeting Name</th>");
        out.println("                  <th>Date</th>");
        out.println("                  <th>Communication</th>");
        out.println("                  <th>Status</th>");
        out.println("                  <th>Actions</th>");
        out.println("                </tr>");
        out.println("              </thead>");
        out.println("              <tbody>");
        for (EsMeeting agenda : visibleAgendas) {
            String dateStr = agenda.getScheduledStart() != null
                    ? DATE_ONLY_FORMAT.format(agenda.getScheduledStart())
                    : "";
            out.println("                <tr>");
            out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                    + "/es/agenda?meetingId=" + agenda.getEsMeetingId()
                    + "\">"
                    + escapeHtml(orEmpty(agenda.getMeetingName())) + "</a></td>");
            out.println("                  <td>" + escapeHtml(dateStr) + "</td>");
            renderCommunicationCell(out, contextPath, agenda,
                    nextScheduledByMeetingId.get(agenda.getEsMeetingId()));
            out.println("                  <td>" + escapeHtml(agenda.getStatus() != null ? agenda.getStatus().name() : "")
                    + "</td>");
            out.println("                  <td>&mdash;</td>");
            out.println("                </tr>");
        }
        if (visibleAgendas.isEmpty()) {
            out.println("                <tr>");
            out.println("                  <td colspan=\"5\">No agendas yet.</td>");
            out.println("                </tr>");
        }
        out.println("              </tbody>");
        out.println("            </table>");
        out.println("            </div>");
        out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                + "/admin/es/meetings\">");
        out.println("              <input type=\"hidden\" name=\"meetingId\" value=\"" + meetingId + "\">");
        out.println("              <input type=\"hidden\" name=\"action\" value=\"createAgenda\">");
        out.println("              <div class=\"aira-field\">");
        out.println("                <label for=\"agendaDate\">Date</label>");
        out.println(
                "                <input class=\"aira-input\" id=\"agendaDate\" type=\"date\" name=\"agendaDate\" required>");
        out.println("              </div>");
        out.println("              <div class=\"aira-action-group\">");
        out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Create Agenda</button>");
        out.println("              </div>");
        out.println("            </form>");
    }

    private void renderCommunicationCell(PrintWriter out, String contextPath, EsMeeting agenda,
            EsMeetingCommunication nextScheduledCommunication) {
        if (agenda.getEsMeetingId() == null) {
            out.println("                  <td>&mdash;</td>");
            return;
        }

        if (nextScheduledCommunication != null) {
            String type = nextScheduledCommunication.getCommunicationType() != null
                    ? nextScheduledCommunication.getCommunicationType().name()
                    : "SCHEDULED";
            String scheduledSend = formatScheduledSendInCommunicationTimezone(nextScheduledCommunication);
            out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                    + "/es/meeting-communication-preview?id="
                    + nextScheduledCommunication.getEsMeetingCommunicationId() + "\">Next: "
                    + escapeHtml(type) + " at " + escapeHtml(scheduledSend) + "</a></td>");
            return;
        }

        String scheduleUrl = contextPath + "/es/meeting-communication?meetingId=" + agenda.getEsMeetingId();
        String suggestType = suggestTypeForMeetingStatus(agenda.getStatus());
        if (suggestType != null) {
            scheduleUrl += "&suggestType=" + encodeQueryComponent(suggestType);
        }
        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + scheduleUrl
                + "\">Schedule communication</a></td>");
    }

    private String suggestTypeForMeetingStatus(EsMeeting.MeetingStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PROPOSED -> "PROPOSED_AGENDA";
            case FINALIZED -> "FINAL_AGENDA";
            case CANCELLED -> "CANCELLED";
            default -> null;
        };
    }

    private String formatScheduledSendInCommunicationTimezone(EsMeetingCommunication communication) {
        if (communication.getScheduledSendAt() == null) {
            return "";
        }
        ZoneId targetZone = safeZoneId(communication.getTimezoneId());
        ZonedDateTime local = communication.getScheduledSendAt()
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(targetZone);
        return DATE_FORMAT.format(local) + " " + targetZone.getId();
    }

    private ZoneId safeZoneId(String timezoneId) {
        if (timezoneId != null && !timezoneId.isBlank()) {
            try {
                return ZoneId.of(timezoneId);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return ZoneId.of(DEFAULT_TIMEZONE);
    }

    private void renderMemberTable(PrintWriter out, String contextPath, Long meetingId,
            List<EsTopicMeetingMember> members, Map<Long, User> usersById, List<String> actions) {
        out.println("            <div class=\"aira-table-wrap\">");
        out.println("            <table class=\"aira-table\">");
        out.println("              <thead>");
        out.println("                <tr>");
        out.println("                  <th>Email</th>");
        out.println("                  <th>Display Name</th>");
        out.println("                  <th>Organization</th>");
        out.println("                  <th>Joined</th>");
        out.println("                  <th>Actions</th>");
        out.println("                </tr>");
        out.println("              </thead>");
        out.println("              <tbody>");
        for (EsTopicMeetingMember member : members) {
            User user = member.getUserId() != null ? usersById.get(member.getUserId()) : null;
            out.println("                <tr>");
            out.println("                  <td>" + escapeHtml(orEmpty(member.getEmail())) + "</td>");
            out.println("                  <td>"
                    + escapeHtml(user != null ? orEmpty(user.getFullName()) : "") + "</td>");
            out.println("                  <td>"
                    + escapeHtml(user != null ? orEmpty(user.getOrganization()) : "") + "</td>");
            out.println("                  <td>" + escapeHtml(formatDate(member.getCreatedAt())) + "</td>");
            out.println("                  <td>");
            out.println("                    <div class=\"aira-action-group\">");
            for (String action : actions) {
                out.println("                    <form method=\"post\" action=\"" + contextPath
                        + "/admin/es/meetings\">");
                out.println(
                        "                      <input type=\"hidden\" name=\"meetingId\" value=\"" + meetingId + "\">");
                out.println("                      <input type=\"hidden\" name=\"memberId\" value=\""
                        + member.getEsTopicMeetingMemberId() + "\">");
                out.println("                      <input type=\"hidden\" name=\"action\" value=\""
                        + escapeHtml(action) + "\">");
                out.println("                      <button class=\"aira-button aira-button--secondary aira-button--small\" type=\"submit\">"
                        + escapeHtml(capitalize(action)) + "</button>");
                out.println("                    </form>");
            }
            out.println("                    </div>");
            out.println("                  </td>");
            out.println("                </tr>");
        }
        out.println("              </tbody>");
        out.println("            </table>");
        out.println("            </div>");
    }

    @SafeVarargs
    private Map<Long, User> loadUsersByMembers(List<EsTopicMeetingMember>... groups) {
        List<Long> userIds = new ArrayList<>();
        for (List<EsTopicMeetingMember> group : groups) {
            for (EsTopicMeetingMember m : group) {
                if (m.getUserId() != null) {
                    userIds.add(m.getUserId());
                }
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctIds = userIds.stream().distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        return userDao.findByIds(distinctIds).stream()
                .collect(Collectors.toMap(User::getUserId, u -> u, (a, b) -> a, LinkedHashMap::new));
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
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

    private String formatDate(LocalDateTime dt) {
        return dt == null ? "" : DATE_FORMAT.format(dt);
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String buildQrPageUrl(String contextPath, String targetPath, String label, String backPath) {
        return contextPath + "/admin/qr?target=" + encodeQueryComponent(targetPath)
                + "&label=" + encodeQueryComponent(label)
                + "&back=" + encodeQueryComponent(backPath);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodeQueryComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
