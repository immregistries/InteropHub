package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsMeetingAttendanceDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsMeetingRsvpDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAttendance;
import org.airahub.interophub.model.EsMeetingRsvp;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.MeetingRsvpResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsNormalizer;
import org.airahub.interophub.service.MeetingAttendanceInvitationService;
import org.airahub.interophub.service.MeetingAuthorizationService;
import org.airahub.interophub.service.MeetingExpectedPeopleService;
import org.airahub.interophub.service.MeetingExpectedPeopleService.ExpectedPerson;
import org.airahub.interophub.service.MeetingWindowRules;
import org.airahub.interophub.service.TopicFollowerManagementService;
import org.immregistries.aira.web.AiraPage;

/**
 * Staff-facing attendance console (Phases 2-5 of
 * docs/meeting-attendance-console-design.md): lets meeting staff record
 * staff-observed attendance alongside participant self sign-in, edit or
 * remove an observed-only entry, invite an observed attendee with a known
 * email to confirm their own attendance, scan RSVPs against arrivals, and
 * find/check in anyone expected (series member, RSVP, presenter, or topic
 * follower) or anyone else registered in InteropHub. Self-reported entries
 * (selfSignedAt != null) are never editable or removable here - only the
 * participant's own self sign-in changes those.
 *
 * URL: /es/meeting-attendance?meetingId={id}[&topicId={id}]
 */
public class EsMeetingAttendanceConsoleServlet extends HttpServlet {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    /** Below this many rows, render a plain table; at/above it, switch to search-to-reveal. */
    static final int LIST_VS_SEARCH_THRESHOLD = 20;

    private final AuthFlowService authFlowService;
    private final MeetingAuthorizationService meetingAuthorizationService;
    private final EsMeetingDao meetingDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsTopicDao topicDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsMeetingAttendanceDao attendanceDao;
    private final EsMeetingRsvpDao rsvpDao;
    private final MeetingExpectedPeopleService expectedPeopleService;
    private final UserDao userDao;
    private final MeetingAttendanceInvitationService invitationService;

    public EsMeetingAttendanceConsoleServlet() {
        this.authFlowService = new AuthFlowService();
        this.meetingAuthorizationService = new MeetingAuthorizationService();
        this.meetingDao = new EsMeetingDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.topicDao = new EsTopicDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.attendanceDao = new EsMeetingAttendanceDao();
        this.rsvpDao = new EsMeetingRsvpDao();
        this.expectedPeopleService = new MeetingExpectedPeopleService();
        this.userDao = new UserDao();
        this.invitationService = new MeetingAttendanceInvitationService();
    }

    // =========================================================================
    // GET
    // =========================================================================

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long meetingId = parseId(request.getParameter("meetingId"));
        if (meetingId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "meetingId is required.");
            return;
        }
        EsMeeting meeting = meetingDao.findById(meetingId).orElse(null);
        if (meeting == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Meeting was not found.");
            return;
        }
        Optional<User> userOpt = authFlowService.findAuthenticatedUser(request);
        if (userOpt.isEmpty() || !meetingAuthorizationService.canControlMeeting(userOpt.get().getUserId(), meeting)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "You do not have access to the attendance console for this meeting.");
            return;
        }

        String savedMessage = "1".equals(request.getParameter("saved")) ? "Attendance updated." : null;
        String errorMessage = trimToNull(request.getParameter("err"));
        Long editId = parseId(request.getParameter("editId"));
        Long topicId = parseId(request.getParameter("topicId"));

        render(request, response, meeting, savedMessage, errorMessage, editId, topicId);
    }

    // =========================================================================
    // POST
    // =========================================================================

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        String contextPath = request.getContextPath();

        Long meetingId = parseId(request.getParameter("meetingId"));
        if (meetingId == null) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }
        EsMeeting meeting = meetingDao.findById(meetingId).orElse(null);
        if (meeting == null) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }
        Optional<User> userOpt = authFlowService.findAuthenticatedUser(request);
        if (userOpt.isEmpty() || !meetingAuthorizationService.canControlMeeting(userOpt.get().getUserId(), meeting)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "You do not have access to the attendance console for this meeting.");
            return;
        }
        User user = userOpt.get();

        if (!isAttendanceManagementWindowOpen(meeting)) {
            redirectWithError(response, contextPath, meetingId,
                    "This meeting is outside its attendance-management window.");
            return;
        }

        String action = trimToNull(request.getParameter("action"));
        if ("addObserved".equals(action)) {
            handleAddObserved(request, response, contextPath, meeting, user);
        } else if ("updateObserved".equals(action)) {
            handleUpdateObserved(request, response, contextPath, meeting);
        } else if ("removeObserved".equals(action)) {
            handleRemoveObserved(request, response, contextPath, meeting, user);
        } else if ("sendInvite".equals(action)) {
            handleSendInvite(request, response, contextPath, meeting, user);
        } else {
            response.sendRedirect(contextPath + "/es/meeting-attendance?meetingId=" + meetingId);
        }
    }

    /**
     * Also used as the one-click "Present" check-in for anyone already known
     * (RSVP'd, expected, or found via search) - those flows submit this same
     * action with displayName/firstName/lastName/organization/email/userId
     * already filled in as hidden fields, so no typing is required.
     */
    private void handleAddObserved(HttpServletRequest request, HttpServletResponse response, String contextPath,
            EsMeeting meeting, User user) throws IOException {
        String displayName = trimToNull(request.getParameter("displayName"));
        if (displayName == null) {
            redirectWithError(response, contextPath, meeting.getEsMeetingId(), "Display name is required.");
            return;
        }
        String firstName = trimToNull(request.getParameter("firstName"));
        String lastName = trimToNull(request.getParameter("lastName"));
        String organization = trimToNull(request.getParameter("organization"));
        String emailRaw = trimToNull(request.getParameter("email"));
        String note = trimToNull(request.getParameter("note"));
        String emailNormalized = emailRaw != null ? EsNormalizer.normalizeEmail(emailRaw) : null;

        // Prefer an explicitly-known userId (from RSVP/expected/search); otherwise
        // auto-link by email match, same as self sign-in already does.
        Long userId = parseId(trimToNull(request.getParameter("userId")));
        if (userId == null && emailNormalized != null) {
            userId = userDao.findByEmailNormalized(emailNormalized)
                    .filter(u -> u.getStatus() != User.UserStatus.DELETED)
                    .map(User::getUserId)
                    .orElse(null);
        }

        LocalDate attendanceDate = meeting.getScheduledStart() != null
                ? meeting.getScheduledStart().toLocalDate()
                : LocalDate.now();

        EsMeetingAttendance record = emailNormalized != null
                ? attendanceDao.findByMeetingIdDateAndEmailNormalized(
                        meeting.getEsTopicMeetingId(), attendanceDate, emailNormalized).orElse(null)
                : null;

        if (record != null) {
            // Already have a record for this email on this occurrence - just stamp the
            // observation. Never overwrite a self-reported attendee's own identity.
            if (record.getSelfSignedAt() == null) {
                record.setDisplayName(displayName);
                record.setFirstName(firstName);
                record.setLastName(lastName);
                record.setOrganization(organization);
                record.setEmail(emailRaw);
                record.setEmailNormalized(emailNormalized);
                if (note != null) {
                    record.setObservationNote(note);
                }
                if (userId != null) {
                    record.setUserId(userId);
                }
            }
        } else {
            record = new EsMeetingAttendance();
            record.setEsTopicMeetingId(meeting.getEsTopicMeetingId());
            record.setAttendanceDate(attendanceDate);
            record.setDisplayName(displayName);
            record.setFirstName(firstName);
            record.setLastName(lastName);
            record.setOrganization(organization);
            record.setEmail(emailRaw);
            record.setEmailNormalized(emailNormalized);
            record.setObservationNote(note);
            record.setUserId(userId);
        }
        record.setEsMeetingId(meeting.getEsMeetingId());
        record.setObservedAt(LocalDateTime.now());
        record.setObservedByUserId(user.getUserId());

        attendanceDao.saveOrUpdate(record);
        response.sendRedirect(contextPath + "/es/meeting-attendance?meetingId=" + meeting.getEsMeetingId() + "&saved=1");
    }

    private void handleUpdateObserved(HttpServletRequest request, HttpServletResponse response, String contextPath,
            EsMeeting meeting) throws IOException {
        EsMeetingAttendance record = loadEditableObservedRecord(request, meeting);
        if (record == null) {
            redirectWithError(response, contextPath, meeting.getEsMeetingId(),
                    "That attendance entry can no longer be edited.");
            return;
        }
        String displayName = trimToNull(request.getParameter("displayName"));
        if (displayName == null) {
            redirectWithError(response, contextPath, meeting.getEsMeetingId(), "Display name is required.");
            return;
        }
        record.setDisplayName(displayName);
        record.setFirstName(trimToNull(request.getParameter("firstName")));
        record.setLastName(trimToNull(request.getParameter("lastName")));
        record.setOrganization(trimToNull(request.getParameter("organization")));
        String emailRaw = trimToNull(request.getParameter("email"));
        record.setEmail(emailRaw);
        record.setEmailNormalized(emailRaw != null ? EsNormalizer.normalizeEmail(emailRaw) : null);
        record.setObservationNote(trimToNull(request.getParameter("note")));

        attendanceDao.saveOrUpdate(record);
        response.sendRedirect(contextPath + "/es/meeting-attendance?meetingId=" + meeting.getEsMeetingId() + "&saved=1");
    }

    private void handleRemoveObserved(HttpServletRequest request, HttpServletResponse response, String contextPath,
            EsMeeting meeting, User user) throws IOException {
        EsMeetingAttendance record = loadEditableObservedRecord(request, meeting);
        if (record == null) {
            redirectWithError(response, contextPath, meeting.getEsMeetingId(),
                    "That attendance entry can no longer be removed.");
            return;
        }
        record.setRemovedAt(LocalDateTime.now());
        record.setRemovedByUserId(user.getUserId());
        attendanceDao.saveOrUpdate(record);
        response.sendRedirect(contextPath + "/es/meeting-attendance?meetingId=" + meeting.getEsMeetingId() + "&saved=1");
    }

    /**
     * Non-admins are blocked from resending within the cooldown unless they
     * pass confirm=1 (the "Send again" link) - admins can always send,
     * matching TopicFollowerManagementService's resend-cooldown convention.
     */
    private void handleSendInvite(HttpServletRequest request, HttpServletResponse response, String contextPath,
            EsMeeting meeting, User user) throws IOException {
        EsMeetingAttendance record = loadEditableObservedRecord(request, meeting);
        if (record == null) {
            redirectWithError(response, contextPath, meeting.getEsMeetingId(),
                    "That attendance entry can no longer be invited.");
            return;
        }
        boolean isAdmin = authFlowService.isAdminUser(user);
        boolean confirm = "1".equals(request.getParameter("confirm"));
        if (!isAdmin && !confirm) {
            Optional<LocalDateTime> lastSent = invitationService.lastInviteSentAt(record.getEmailNormalized());
            if (lastSent.isPresent() && TopicFollowerManagementService.isWithinCooldown(
                    lastSent.get(), LocalDateTime.now(), MeetingAttendanceInvitationService.RESEND_COOLDOWN_DAYS)) {
                redirectWithError(response, contextPath, meeting.getEsMeetingId(),
                        "An invite was sent recently. Use \"Send again\" to confirm.");
                return;
            }
        }
        TopicFollowerManagementService.Outcome<Void> outcome = invitationService.sendInvite(
                record.getEsMeetingAttendanceId(), request);
        if (!outcome.isSuccess()) {
            redirectWithError(response, contextPath, meeting.getEsMeetingId(), outcome.getErrorMessage());
            return;
        }
        response.sendRedirect(contextPath + "/es/meeting-attendance?meetingId=" + meeting.getEsMeetingId() + "&saved=1");
    }

    /** An entry is only editable/removable here while it's observed-only, active, and belongs to this occurrence. */
    private EsMeetingAttendance loadEditableObservedRecord(HttpServletRequest request, EsMeeting meeting) {
        Long attendanceId = parseId(request.getParameter("attendanceId"));
        if (attendanceId == null) {
            return null;
        }
        EsMeetingAttendance record = attendanceDao.findById(attendanceId).orElse(null);
        if (record == null || !meeting.getEsMeetingId().equals(record.getEsMeetingId())
                || record.getSelfSignedAt() != null || record.getRemovedAt() != null) {
            return null;
        }
        return record;
    }

    private void redirectWithError(HttpServletResponse response, String contextPath, Long meetingId, String message)
            throws IOException {
        response.sendRedirect(contextPath + "/es/meeting-attendance?meetingId=" + meetingId
                + "&err=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    /**
     * Mirrors the practical window described in the design doc: from 15 minutes
     * before scheduled start through the meeting's note-editing period (same
     * close_due_at anchor used elsewhere - see MeetingActionQueueService's class
     * doc for why it's UTC-anchored), and never once the meeting is Closed or
     * Cancelled.
     */
    private boolean isAttendanceManagementWindowOpen(EsMeeting meeting) {
        if (meeting.getStatus() == EsMeeting.MeetingStatus.CLOSED
                || meeting.getStatus() == EsMeeting.MeetingStatus.CANCELLED) {
            return false;
        }
        if (!MeetingWindowRules.isMeetingStartWindowOpen(meeting)) {
            return false;
        }
        if (meeting.getCloseDueAt() == null) {
            return true;
        }
        Instant closeDueInstant = meeting.getCloseDueAt().atZone(ZoneOffset.UTC).toInstant();
        return Instant.now().isBefore(closeDueInstant);
    }

    // =========================================================================
    // Roster row model - shared by the top Roster table and the By-Topic table
    // =========================================================================

    /** One person in a roster-style table: known identity, optional RSVP, optional attendance record. */
    private record RosterRow(Long userId, String emailNormalized, String displayName, String organization,
            String email, MeetingRsvpResponse rsvp, EsMeetingAttendance attendance) {
    }

    /** Everyone with an RSVP and/or an active attendance record for this meeting - the top "Roster" table. */
    private List<RosterRow> buildMeetingRoster(List<EsMeetingAttendance> active, List<EsMeetingRsvp> rsvps,
            Map<Long, User> rsvpUsers) {
        Map<String, RosterRow> byKey = new LinkedHashMap<>();
        for (EsMeetingAttendance a : active) {
            byKey.put(identityKey(a.getUserId(), a.getEmailNormalized()),
                    new RosterRow(a.getUserId(), a.getEmailNormalized(), a.getDisplayName(),
                            orEmpty(a.getOrganization()), orEmpty(a.getEmail()), null, a));
        }
        for (EsMeetingRsvp r : rsvps) {
            String key = identityKey(r.getUserId(), null);
            RosterRow existing = byKey.get(key);
            if (existing != null) {
                byKey.put(key, new RosterRow(existing.userId(), existing.emailNormalized(), existing.displayName(),
                        existing.organization(), existing.email(), r.getResponse(), existing.attendance()));
            } else {
                User u = rsvpUsers.get(r.getUserId());
                byKey.put(key, new RosterRow(r.getUserId(), u != null ? u.getEmailNormalized() : null,
                        u != null ? userLabel(u) : ("User #" + r.getUserId()),
                        u != null ? orEmpty(u.getOrganization()) : "",
                        u != null ? orEmpty(u.getEmail()) : "",
                        r.getResponse(), null));
            }
        }
        return sortedRows(byKey.values());
    }

    /**
     * Expected people (series member / RSVP / presenter / topic follower) who
     * have neither RSVP'd nor attended yet - the ones the Roster table above
     * doesn't already cover, so nobody appears twice with two "Present"
     * buttons.
     */
    private List<RosterRow> buildNotYetCheckedIn(List<ExpectedPerson> expected, List<EsMeetingAttendance> active,
            List<EsMeetingRsvp> rsvps, Map<Long, User> resolvedUsers) {
        Set<String> alreadyCovered = new java.util.HashSet<>();
        for (EsMeetingAttendance a : active) {
            alreadyCovered.add(identityKey(a.getUserId(), a.getEmailNormalized()));
        }
        for (EsMeetingRsvp r : rsvps) {
            alreadyCovered.add(identityKey(r.getUserId(), null));
        }
        List<RosterRow> rows = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (ExpectedPerson p : expected) {
            String key = identityKey(p.userId(), p.emailNormalized());
            if (alreadyCovered.contains(key) || !seen.add(key)) {
                continue;
            }
            User u = p.userId() != null ? resolvedUsers.get(p.userId()) : null;
            rows.add(new RosterRow(p.userId(), p.emailNormalized(),
                    u != null ? userLabel(u) : orEmpty(p.emailNormalized()),
                    u != null ? orEmpty(u.getOrganization()) : "",
                    u != null ? orEmpty(u.getEmail()) : orEmpty(p.emailNormalized()),
                    null, null));
        }
        return sortedRows(rows);
    }

    /** Everyone interested in one topic (followers + presenters), decorated with RSVP/attendance status if any. */
    private List<RosterRow> buildTopicRoster(List<ExpectedPerson> interested, List<EsMeetingAttendance> active,
            List<EsMeetingRsvp> rsvps, Map<Long, User> resolvedUsers) {
        Map<String, EsMeetingAttendance> attendanceByKey = new LinkedHashMap<>();
        for (EsMeetingAttendance a : active) {
            attendanceByKey.put(identityKey(a.getUserId(), a.getEmailNormalized()), a);
        }
        Map<Long, MeetingRsvpResponse> rsvpByUserId = rsvps.stream()
                .collect(Collectors.toMap(EsMeetingRsvp::getUserId, EsMeetingRsvp::getResponse, (x, y) -> x));

        List<RosterRow> rows = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (ExpectedPerson p : interested) {
            String key = identityKey(p.userId(), p.emailNormalized());
            if (!seen.add(key)) {
                continue;
            }
            EsMeetingAttendance attendance = attendanceByKey.get(key);
            MeetingRsvpResponse rsvp = p.userId() != null ? rsvpByUserId.get(p.userId()) : null;
            String displayName;
            String organization;
            String email;
            if (attendance != null) {
                displayName = attendance.getDisplayName();
                organization = orEmpty(attendance.getOrganization());
                email = orEmpty(attendance.getEmail());
            } else {
                User u = p.userId() != null ? resolvedUsers.get(p.userId()) : null;
                displayName = u != null ? userLabel(u) : orEmpty(p.emailNormalized());
                organization = u != null ? orEmpty(u.getOrganization()) : "";
                email = u != null ? orEmpty(u.getEmail()) : orEmpty(p.emailNormalized());
            }
            rows.add(new RosterRow(p.userId(), p.emailNormalized(), displayName, organization, email, rsvp,
                    attendance));
        }
        return sortedRows(rows);
    }

    private List<RosterRow> sortedRows(java.util.Collection<RosterRow> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(r -> orEmpty(r.displayName()).toLowerCase()))
                .toList();
    }

    private String identityKey(Long userId, String emailNormalized) {
        return userId != null ? "u:" + userId : "e:" + orEmpty(emailNormalized);
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    private void render(HttpServletRequest request, HttpServletResponse response, EsMeeting meeting,
            String savedMessage, String errorMessage, Long editId, Long selectedTopicId) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String contextPath = request.getContextPath();

        EsTopicMeeting topicMeeting = topicMeetingDao.findById(meeting.getEsTopicMeetingId()).orElse(null);
        EsTopic hostTopic = topicMeeting != null && topicMeeting.getEsTopicId() != null
                ? topicDao.findById(topicMeeting.getEsTopicId()).orElse(null)
                : null;
        EsTopicSpace hostTopicSpace = hostTopic != null && hostTopic.getEsTopicSpaceId() != null
                ? topicSpaceDao.findById(hostTopic.getEsTopicSpaceId()).orElse(null)
                : null;

        List<EsMeetingAttendance> all = attendanceDao.findAllByEsMeetingIdIncludingRemoved(meeting.getEsMeetingId());
        List<EsMeetingAttendance> active = all.stream().filter(a -> a.getRemovedAt() == null).toList();
        List<EsMeetingAttendance> removed = all.stream().filter(a -> a.getRemovedAt() != null).toList();

        long signedInCount = active.stream().filter(a -> a.getSelfSignedAt() != null).count();
        long observedOnlyCount = active.stream().filter(a -> a.getSelfSignedAt() == null).count();
        long missingEmailCount = active.stream()
                .filter(a -> a.getEmail() == null || a.getEmail().isBlank())
                .count();

        boolean windowOpen = isAttendanceManagementWindowOpen(meeting);
        Map<Long, User> resolvedUsers = resolveObserverUsers(all);

        List<EsMeetingRsvp> rsvps = rsvpDao.findByMeetingId(meeting.getEsMeetingId());
        Map<Long, User> rsvpUsers = resolveUsersById(rsvps.stream().map(EsMeetingRsvp::getUserId).toList());

        List<ExpectedPerson> expectedMeetingWide = expectedPeopleService.findExpectedMeetingWide(meeting, rsvps);
        Map<Long, User> expectedUsers = resolveUsersById(
                expectedMeetingWide.stream().map(ExpectedPerson::userId).toList());

        List<RosterRow> roster = buildMeetingRoster(active, rsvps, rsvpUsers);
        List<RosterRow> notYetCheckedIn = buildNotYetCheckedIn(expectedMeetingWide, active, rsvps, expectedUsers);

        List<EsTopic> agendaTopics = expectedPeopleService.listAgendaTopics(meeting.getEsMeetingId());
        EsTopic selectedTopic = selectedTopicId != null
                ? agendaTopics.stream().filter(t -> t.getEsTopicId().equals(selectedTopicId)).findFirst().orElse(null)
                : null;
        List<RosterRow> topicRoster = List.of();
        if (selectedTopic != null) {
            List<ExpectedPerson> interested = expectedPeopleService.findExpectedForTopic(
                    meeting.getEsMeetingId(), selectedTopic.getEsTopicId());
            Map<Long, User> topicUsers = resolveUsersById(interested.stream().map(ExpectedPerson::userId).toList());
            topicRoster = buildTopicRoster(interested, active, rsvps, topicUsers);
        }

        EsMeetingAttendance editing = editId != null
                ? active.stream()
                        .filter(a -> a.getEsMeetingAttendanceId().equals(editId) && a.getSelfSignedAt() == null)
                        .findFirst().orElse(null)
                : null;

        AiraPage page = InteropAiraPageFactory.base(request,
                "Attendance - " + orEmpty(meeting.getMeetingName()) + " - InteropHub")
                .applicationSubtitle("Meeting Attendance")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.topicsMeetingsContext(
                        hostTopicSpace != null ? hostTopicSpace.getSpaceName() : "InteropHub",
                        hostTopicSpace != null ? hostTopicSpace.getSpaceCode() : null,
                        false,
                        true))
                .build();

        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");

            out.println("      <div class=\"aira-page-header\">");
            out.println("        <div>");
            out.println("          <h1 class=\"aira-page-title\">Attendance &mdash; "
                    + escapeHtml(meeting.getMeetingName()) + "</h1>");
            out.println(
                    "          <p class=\"aira-page-intro\">Track who attended, whether self-signed or observed by staff.</p>");
            out.println("        </div>");
            out.println("        <a class=\"aira-link\" href=\"" + contextPath + "/es/meeting-workspace?meetingId="
                    + meeting.getEsMeetingId() + "\">Back to Workspace</a>");
            out.println("      </div>");

            if (savedMessage != null) {
                out.println("      <div class=\"aira-alert aira-alert--success\"><p>" + escapeHtml(savedMessage)
                        + "</p></div>");
            }
            if (errorMessage != null) {
                out.println("      <div class=\"aira-alert aira-alert--danger\"><p>" + escapeHtml(errorMessage)
                        + "</p></div>");
            }
            if (!windowOpen) {
                out.println("      <div class=\"aira-alert aira-alert--warning\"><p>This meeting is outside its "
                        + "attendance-management window (15 minutes before start, through the note-editing period). "
                        + "You can still view attendance, but adding, editing, or removing entries is disabled.</p></div>");
            }

            // --- Summary ---
            out.println("      <section class=\"aira-panel\">");
            out.println("        <h2 class=\"aira-section-title\">Summary</h2>");
            out.println("        <div class=\"aira-cluster\">");
            out.println("          <span class=\"aira-badge aira-badge--success\">Signed in: " + signedInCount
                    + "</span>");
            out.println("          <span class=\"aira-badge aira-badge--info\">Observed only: " + observedOnlyCount
                    + "</span>");
            out.println("          <span class=\"aira-badge aira-badge--warning\">Missing email: " + missingEmailCount
                    + "</span>");
            if (!removed.isEmpty()) {
                out.println("          <span class=\"aira-badge aira-badge--subtle\">Removed: " + removed.size()
                        + "</span>");
            }
            out.println("        </div>");
            out.println("      </section>");

            // --- Roster: everyone who RSVP'd and/or attended, one combined table ---
            out.println("      <section class=\"aira-panel\">");
            out.println("        <h2 class=\"aira-section-title\">Roster</h2>");
            out.println(
                    "        <p class=\"aira-meta\">Everyone who RSVP&rsquo;d or has an attendance record. Scan for key people and mark them present as they arrive.</p>");
            renderRosterTable(out, contextPath, meeting, roster, resolvedUsers, windowOpen, editing, true);
            out.println("      </section>");

            // --- Not yet checked in: expected people with neither an RSVP nor attendance ---
            out.println("      <section class=\"aira-panel\">");
            out.println("        <h2 class=\"aira-section-title\">Not Yet Checked In</h2>");
            out.println(
                    "        <p class=\"aira-meta\">Series members, presenters, and topic followers with no RSVP or attendance record yet.</p>");
            renderListOrSearch(out, contextPath, meeting, notYetCheckedIn, resolvedUsers, windowOpen, editing,
                    "not-yet-checked-in", null);
            out.println("      </section>");

            // --- By topic: pick a topic, see everyone interested in it ---
            if (!agendaTopics.isEmpty()) {
                out.println("      <section class=\"aira-panel\">");
                out.println("        <h2 class=\"aira-section-title\">By Topic</h2>");
                out.println("        <div class=\"aira-cluster\">");
                for (EsTopic t : agendaTopics) {
                    boolean isSelected = selectedTopic != null
                            && selectedTopic.getEsTopicId().equals(t.getEsTopicId());
                    out.println("          <a class=\"aira-button "
                            + (isSelected ? "aira-button--primary" : "aira-button--secondary") + "\" href=\""
                            + contextPath + "/es/meeting-attendance?meetingId=" + meeting.getEsMeetingId()
                            + "&topicId=" + t.getEsTopicId() + "\">" + escapeHtml(t.getTopicName()) + "</a>");
                }
                out.println("        </div>");
                if (selectedTopic != null) {
                    out.println("        <h3 class=\"aira-section-title\">" + escapeHtml(selectedTopic.getTopicName())
                            + "</h3>");
                    renderListOrSearch(out, contextPath, meeting, topicRoster, resolvedUsers, windowOpen, editing,
                            "topic", selectedTopic.getEsTopicId());
                }
                out.println("      </section>");
            }

            // --- Add observed attendee (fallback for someone not found anywhere above) ---
            out.println("      <section class=\"aira-panel\">");
            out.println("        <h2 class=\"aira-section-title\">Add Someone Not Found Above</h2>");
            out.println(
                    "        <p class=\"aira-meta\">For someone with no InteropHub account at all - e.g. a bare Zoom display name.</p>");
            out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                    + "/es/meeting-attendance\">");
            out.println("          <input type=\"hidden\" name=\"meetingId\" value=\"" + meeting.getEsMeetingId()
                    + "\">");
            out.println("          <input type=\"hidden\" name=\"action\" value=\"addObserved\">");
            String disabledAttr = windowOpen ? "" : " disabled";
            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"displayName\">Display Name *</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"displayName\" name=\"displayName\" type=\"text\" required"
                            + " placeholder=\"e.g. Zoom name as shown\"" + disabledAttr + " />");
            out.println("          </div>");
            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"firstName\">First Name</label>");
            out.println("            <input class=\"aira-input\" id=\"firstName\" name=\"firstName\" type=\"text\""
                    + disabledAttr + " />");
            out.println("          </div>");
            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"lastName\">Last Name</label>");
            out.println("            <input class=\"aira-input\" id=\"lastName\" name=\"lastName\" type=\"text\""
                    + disabledAttr + " />");
            out.println("          </div>");
            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"organization\">Organization</label>");
            out.println(
                    "            <input class=\"aira-input\" id=\"organization\" name=\"organization\" type=\"text\""
                            + disabledAttr + " />");
            out.println("          </div>");
            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"email\">Email (optional)</label>");
            out.println("            <input class=\"aira-input\" id=\"email\" name=\"email\" type=\"email\""
                    + disabledAttr + " />");
            out.println("          </div>");
            out.println("          <div class=\"aira-field\">");
            out.println("            <label for=\"note\">Note (optional)</label>");
            out.println("            <textarea class=\"aira-textarea\" id=\"note\" name=\"note\" rows=\"2\""
                    + disabledAttr + "></textarea>");
            out.println("          </div>");
            out.println("          <div class=\"aira-action-group\">");
            out.println("            <button class=\"aira-button aira-button--primary\" type=\"submit\""
                    + disabledAttr + ">Add</button>");
            out.println("          </div>");
            out.println("        </form>");
            out.println("      </section>");

            if (!removed.isEmpty()) {
                out.println("      <section class=\"aira-panel\">");
                out.println("        <h2 class=\"aira-section-title\">Removed Observed Records</h2>");
                out.println("        <ul>");
                for (EsMeetingAttendance a : removed) {
                    out.println("          <li>" + escapeHtml(a.getDisplayName()) + " &mdash; "
                            + escapeHtml(observationDetail(a, resolvedUsers, Optional.empty())) + "</li>");
                }
                out.println("        </ul>");
                out.println("      </section>");
            }

            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            out.println("    <script src=\"" + contextPath + "/js/meeting-attendance-search.js\" defer></script>");
            page.writeEnd(out);
        }
    }

    /**
     * Renders {@code rows} as a plain table when under
     * {@link #LIST_VS_SEARCH_THRESHOLD}, otherwise as a search-to-reveal widget
     * (mirrors the header search's debounced-fetch pattern) backed by
     * {@code EsMeetingAttendanceSearchServlet} - searching every registered
     * user, not just this candidate pool, so a walk-in can still be found.
     */
    private void renderListOrSearch(PrintWriter out, String contextPath, EsMeeting meeting, List<RosterRow> rows,
            Map<Long, User> resolvedUsers, boolean windowOpen, EsMeetingAttendance editing, String widgetId,
            Long topicId) {
        if (rows.size() < LIST_VS_SEARCH_THRESHOLD) {
            renderRosterTable(out, contextPath, meeting, rows, resolvedUsers, windowOpen, editing, false);
            return;
        }
        out.println("        <div class=\"aira-people-search\" data-widget-id=\"" + widgetId
                + "\" data-meeting-id=\"" + meeting.getEsMeetingId() + "\""
                + (topicId != null ? " data-topic-id=\"" + topicId + "\"" : "")
                + " data-window-open=\"" + windowOpen + "\">");
        out.println("          <div class=\"aira-field\">");
        out.println("            <label>" + rows.size()
                + " people - too many to list. Search InteropHub to check someone in:</label>");
        out.println(
                "            <input class=\"aira-input\" type=\"text\" data-role=\"query\" autocomplete=\"off\" placeholder=\"Type a name or email...\" />");
        out.println("          </div>");
        out.println("          <div data-role=\"status\" class=\"aira-meta\"></div>");
        out.println("          <div class=\"aira-table-wrap\"><table class=\"aira-table\" data-role=\"results\">"
                + "<thead><tr><th>Name</th><th>Organization</th><th>Email</th><th></th></tr></thead>"
                + "<tbody></tbody></table></div>");
        out.println("        </div>");
    }

    private void renderRosterTable(PrintWriter out, String contextPath, EsMeeting meeting, List<RosterRow> rows,
            Map<Long, User> resolvedUsers, boolean windowOpen, EsMeetingAttendance editing, boolean showRsvpColumn) {
        if (rows.isEmpty()) {
            out.println("        <p class=\"aira-meta\">No one yet.</p>");
            return;
        }
        out.println("        <div class=\"aira-table-wrap\">");
        out.println("        <table class=\"aira-table\">");
        out.println("          <thead><tr><th>Name</th><th>Organization</th><th>Email</th>"
                + (showRsvpColumn ? "<th>RSVP</th>" : "") + "<th>Status</th><th>Details</th><th></th></tr></thead>");
        out.println("          <tbody>");
        for (RosterRow row : rows) {
            EsMeetingAttendance a = row.attendance();
            boolean isSelfSigned = a != null && a.getSelfSignedAt() != null;
            boolean isEditingThis = editing != null && a != null
                    && editing.getEsMeetingAttendanceId().equals(a.getEsMeetingAttendanceId());
            int colspan = showRsvpColumn ? 7 : 6;
            out.println("            <tr>");
            if (isEditingThis) {
                out.println("              <td colspan=\"" + colspan + "\">");
                renderEditForm(out, contextPath, meeting, a);
                out.println("              </td>");
                out.println("            </tr>");
                continue;
            }
            out.println("              <td>" + escapeHtml(orEmpty(row.displayName())) + "</td>");
            out.println("              <td>" + escapeHtml(orEmpty(row.organization())) + "</td>");
            boolean hasEmail = row.email() != null && !row.email().isBlank();
            out.println("              <td>" + (hasEmail ? escapeHtml(row.email())
                    : "<span class=\"aira-badge aira-badge--warning\">Email missing</span>") + "</td>");
            if (showRsvpColumn) {
                out.println("              <td>" + (row.rsvp() != null ? rsvpLabel(row.rsvp()) : "&mdash;")
                        + "</td>");
            }
            out.println("              <td>" + statusBadge(a) + "</td>");
            Optional<LocalDateTime> lastInvited = a != null && !isSelfSigned && hasEmail
                    ? invitationService.lastInviteSentAt(a.getEmailNormalized())
                    : Optional.empty();
            out.println("              <td class=\"aira-meta\">"
                    + escapeHtml(a != null ? observationDetail(a, resolvedUsers, lastInvited) : "") + "</td>");
            out.println("              <td>");
            if (a == null) {
                renderPresentForm(out, contextPath, meeting.getEsMeetingId(), windowOpen, row.userId(),
                        row.displayName(), row.organization(), row.email());
            } else if (!isSelfSigned && windowOpen) {
                out.println("                <a class=\"aira-link\" href=\"" + contextPath
                        + "/es/meeting-attendance?meetingId=" + meeting.getEsMeetingId() + "&editId="
                        + a.getEsMeetingAttendanceId() + "\">Edit</a>");
                if (hasEmail) {
                    out.println("                <form class=\"aira-inline-form\" method=\"post\" action=\""
                            + contextPath + "/es/meeting-attendance\">");
                    out.println("                  <input type=\"hidden\" name=\"meetingId\" value=\""
                            + meeting.getEsMeetingId() + "\">");
                    out.println(
                            "                  <input type=\"hidden\" name=\"action\" value=\"sendInvite\">");
                    out.println("                  <input type=\"hidden\" name=\"attendanceId\" value=\""
                            + a.getEsMeetingAttendanceId() + "\">");
                    if (lastInvited.isPresent()) {
                        out.println("                  <input type=\"hidden\" name=\"confirm\" value=\"1\">");
                        out.println(
                                "                  <button class=\"aira-button aira-button--link\" type=\"submit\">Send again</button>");
                    } else {
                        out.println(
                                "                  <button class=\"aira-button aira-button--link\" type=\"submit\">Invite</button>");
                    }
                    out.println("                </form>");
                }
                out.println("                <form class=\"aira-inline-form\" method=\"post\" action=\""
                        + contextPath
                        + "/es/meeting-attendance\" onsubmit=\"return confirm('Remove this observed attendee?');\">");
                out.println("                  <input type=\"hidden\" name=\"meetingId\" value=\""
                        + meeting.getEsMeetingId() + "\">");
                out.println("                  <input type=\"hidden\" name=\"action\" value=\"removeObserved\">");
                out.println("                  <input type=\"hidden\" name=\"attendanceId\" value=\""
                        + a.getEsMeetingAttendanceId() + "\">");
                out.println(
                        "                  <button class=\"aira-button aira-button--link\" type=\"submit\">Remove</button>");
                out.println("                </form>");
            }
            out.println("              </td>");
            out.println("            </tr>");
        }
        out.println("          </tbody>");
        out.println("        </table>");
        out.println("        </div>");
    }

    private String statusBadge(EsMeetingAttendance a) {
        if (a == null) {
            return "<span class=\"aira-badge aira-badge--subtle\">Not checked in</span>";
        }
        return a.getSelfSignedAt() != null
                ? "<span class=\"aira-badge aira-badge--success\">Signed in</span>"
                : "<span class=\"aira-badge aira-badge--info\">Observed</span>";
    }

    /** One-click check-in: submits addObserved with everything already known about this person prefilled. */
    private void renderPresentForm(PrintWriter out, String contextPath, Long meetingId, boolean windowOpen,
            Long userId, String displayName, String organization, String email) {
        if (!windowOpen) {
            return;
        }
        out.println("                <form class=\"aira-inline-form\" method=\"post\" action=\"" + contextPath
                + "/es/meeting-attendance\">");
        out.println("                  <input type=\"hidden\" name=\"meetingId\" value=\"" + meetingId + "\">");
        out.println("                  <input type=\"hidden\" name=\"action\" value=\"addObserved\">");
        out.println("                  <input type=\"hidden\" name=\"displayName\" value=\""
                + escapeHtml(orEmpty(displayName)) + "\">");
        if (userId != null) {
            out.println("                  <input type=\"hidden\" name=\"userId\" value=\"" + userId + "\">");
        }
        out.println("                  <input type=\"hidden\" name=\"organization\" value=\""
                + escapeHtml(orEmpty(organization)) + "\">");
        if (email != null && !email.isBlank()) {
            out.println("                  <input type=\"hidden\" name=\"email\" value=\"" + escapeHtml(email)
                    + "\">");
        }
        out.println(
                "                  <button class=\"aira-button aira-button--primary\" type=\"submit\">Present</button>");
        out.println("                </form>");
    }

    private void renderEditForm(PrintWriter out, String contextPath, EsMeeting meeting, EsMeetingAttendance a) {
        out.println("                <form class=\"aira-form aira-form--inline\" method=\"post\" action=\""
                + contextPath + "/es/meeting-attendance\">");
        out.println("                  <input type=\"hidden\" name=\"meetingId\" value=\"" + meeting.getEsMeetingId()
                + "\">");
        out.println("                  <input type=\"hidden\" name=\"action\" value=\"updateObserved\">");
        out.println("                  <input type=\"hidden\" name=\"attendanceId\" value=\""
                + a.getEsMeetingAttendanceId() + "\">");
        out.println("                  <div class=\"aira-field\"><label>Display Name *</label>"
                + "<input class=\"aira-input\" name=\"displayName\" type=\"text\" required value=\""
                + escapeHtml(a.getDisplayName()) + "\"></div>");
        out.println("                  <div class=\"aira-field\"><label>First Name</label>"
                + "<input class=\"aira-input\" name=\"firstName\" type=\"text\" value=\""
                + escapeHtml(orEmpty(a.getFirstName())) + "\"></div>");
        out.println("                  <div class=\"aira-field\"><label>Last Name</label>"
                + "<input class=\"aira-input\" name=\"lastName\" type=\"text\" value=\""
                + escapeHtml(orEmpty(a.getLastName())) + "\"></div>");
        out.println("                  <div class=\"aira-field\"><label>Organization</label>"
                + "<input class=\"aira-input\" name=\"organization\" type=\"text\" value=\""
                + escapeHtml(orEmpty(a.getOrganization())) + "\"></div>");
        out.println("                  <div class=\"aira-field\"><label>Email</label>"
                + "<input class=\"aira-input\" name=\"email\" type=\"email\" value=\""
                + escapeHtml(orEmpty(a.getEmail())) + "\"></div>");
        out.println("                  <div class=\"aira-field\"><label>Note</label>"
                + "<textarea class=\"aira-textarea\" name=\"note\" rows=\"2\">"
                + escapeHtml(orEmpty(a.getObservationNote())) + "</textarea></div>");
        out.println("                  <div class=\"aira-action-group\">");
        out.println("                    <button class=\"aira-button aira-button--primary\" type=\"submit\">Save</button>");
        out.println("                    <a class=\"aira-link\" href=\"" + contextPath
                + "/es/meeting-attendance?meetingId=" + meeting.getEsMeetingId() + "\">Cancel</a>");
        out.println("                  </div>");
        out.println("                </form>");
    }

    private String observationDetail(EsMeetingAttendance a, Map<Long, User> resolvedUsers,
            Optional<LocalDateTime> lastInvited) {
        if (a.getSelfSignedAt() != null) {
            return "Self-signed " + DATE_TIME_FMT.format(a.getSelfSignedAt());
        }
        StringBuilder sb = new StringBuilder();
        if (a.getObservedAt() != null) {
            sb.append("Observed ").append(DATE_TIME_FMT.format(a.getObservedAt()));
        }
        User observer = a.getObservedByUserId() != null ? resolvedUsers.get(a.getObservedByUserId()) : null;
        if (observer != null) {
            sb.append(" by ").append(userLabel(observer));
        }
        if (a.getObservationNote() != null && !a.getObservationNote().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" — ");
            }
            sb.append(a.getObservationNote());
        }
        if (lastInvited.isPresent()) {
            if (sb.length() > 0) {
                sb.append(" — ");
            }
            sb.append("Last invited ").append(DATE_TIME_FMT.format(lastInvited.get()));
        }
        return sb.toString();
    }

    private Map<Long, User> resolveObserverUsers(List<EsMeetingAttendance> records) {
        return resolveUsersById(records.stream()
                .map(EsMeetingAttendance::getObservedByUserId)
                .filter(Objects::nonNull)
                .toList());
    }

    private Map<Long, User> resolveUsersById(List<Long> ids) {
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> map = new LinkedHashMap<>();
        for (User u : userDao.findByIds(distinctIds)) {
            map.put(u.getUserId(), u);
        }
        return map;
    }

    private String rsvpLabel(MeetingRsvpResponse response) {
        return switch (response) {
            case COMING -> "Coming";
            case MAYBE -> "Maybe";
            case NOT_COMING -> "Not coming";
        };
    }

    private String userLabel(User user) {
        String name = (orEmpty(user.getFirstName()) + " " + orEmpty(user.getLastName())).trim();
        return name.isEmpty() ? orEmpty(user.getEmail()) : name;
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
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
