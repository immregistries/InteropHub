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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsAgendaItemPresenterDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingAttendanceDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsMeetingRsvpDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsAgendaItemPresenter;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsMeetingAttendance;
import org.airahub.interophub.model.EsMeetingRsvp;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.MeetingRsvpResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsNormalizer;
import org.airahub.interophub.service.MeetingAttendanceInvitationService;
import org.airahub.interophub.service.MeetingAuthorizationService;
import org.airahub.interophub.service.MeetingWindowRules;
import org.airahub.interophub.service.TopicFollowerManagementService;
import org.immregistries.aira.web.AiraPage;

/**
 * Staff-facing attendance console (Phases 2-5 of
 * docs/meeting-attendance-console-design.md): lets meeting staff record
 * staff-observed attendance alongside participant self sign-in, edit or
 * remove an observed-only entry, invite an observed attendee with a known
 * email to confirm their own attendance, see RSVPs, and see who's expected
 * (by series membership, RSVP, presenting, or topic-following) but not yet
 * seen - meeting-wide and broken down per agenda topic. Self-reported
 * entries (selfSignedAt != null) are never editable or removable here -
 * only the participant's own self sign-in changes those.
 *
 * URL: /es/meeting-attendance?meetingId={id}
 */
public class EsMeetingAttendanceConsoleServlet extends HttpServlet {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final AuthFlowService authFlowService;
    private final MeetingAuthorizationService meetingAuthorizationService;
    private final EsMeetingDao meetingDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsTopicMeetingMemberDao topicMeetingMemberDao;
    private final EsTopicDao topicDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsMeetingAttendanceDao attendanceDao;
    private final EsMeetingRsvpDao rsvpDao;
    private final EsMeetingAgendaItemDao agendaItemDao;
    private final EsAgendaItemPresenterDao presenterDao;
    private final EsSubscriptionDao subscriptionDao;
    private final UserDao userDao;
    private final MeetingAttendanceInvitationService invitationService;

    public EsMeetingAttendanceConsoleServlet() {
        this.authFlowService = new AuthFlowService();
        this.meetingAuthorizationService = new MeetingAuthorizationService();
        this.meetingDao = new EsMeetingDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
        this.topicDao = new EsTopicDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.attendanceDao = new EsMeetingAttendanceDao();
        this.rsvpDao = new EsMeetingRsvpDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.presenterDao = new EsAgendaItemPresenterDao();
        this.subscriptionDao = new EsSubscriptionDao();
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

        render(request, response, meeting, savedMessage, errorMessage, editId);
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
    // Rendering
    // =========================================================================

    private void render(HttpServletRequest request, HttpServletResponse response, EsMeeting meeting,
            String savedMessage, String errorMessage, Long editId) throws IOException {
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
        long comingCount = rsvps.stream().filter(r -> r.getResponse() == MeetingRsvpResponse.COMING).count();
        long maybeCount = rsvps.stream().filter(r -> r.getResponse() == MeetingRsvpResponse.MAYBE).count();
        long notComingCount = rsvps.stream().filter(r -> r.getResponse() == MeetingRsvpResponse.NOT_COMING).count();
        Map<Long, User> rsvpUsers = resolveUsersById(rsvps.stream().map(EsMeetingRsvp::getUserId).toList());

        // --- Phase 5: expected-but-not-seen, meeting-wide and per agenda topic ---
        // "Seen" means an active attendance record (self-signed or observed) exists -
        // identity matched by userId first, falling back to email.
        Set<Long> seenUserIds = active.stream().map(EsMeetingAttendance::getUserId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<String> seenEmails = active.stream().map(EsMeetingAttendance::getEmailNormalized)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());

        List<EsMeetingAgendaItem> agendaItems = agendaItemDao.findByMeetingIdOrdered(meeting.getEsMeetingId());
        List<EsMeetingAgendaItem> topicItems = agendaItems.stream()
                .filter(i -> i.getEsTopicId() != null
                        && i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.CANCELLED
                        && i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.POSTPONED)
                .toList();
        List<Long> agendaTopicIds = topicItems.stream().map(EsMeetingAgendaItem::getEsTopicId).distinct().toList();
        List<Long> agendaItemIds = topicItems.stream().map(EsMeetingAgendaItem::getEsMeetingAgendaItemId).toList();
        Map<Long, EsTopic> topicById = new LinkedHashMap<>();
        for (Long topicId : agendaTopicIds) {
            topicDao.findById(topicId).ifPresent(t -> topicById.put(topicId, t));
        }
        List<EsAgendaItemPresenter> allPresenters = presenterDao.findByAgendaItemIds(agendaItemIds).stream()
                .filter(p -> p.getStatus() == EsAgendaItemPresenter.PresenterStatus.ACCEPTED)
                .toList();
        List<EsSubscription> allFollowers = subscriptionDao.findActiveSubscribersByTopicIds(agendaTopicIds);
        List<EsTopicMeetingMember> seriesMembers = topicMeetingMemberDao.findByMeetingIdAndStatus(
                meeting.getEsTopicMeetingId(), EsTopicMeetingMember.MembershipStatus.APPROVED);

        // Meeting-wide candidates: series members, RSVP Coming/Maybe, any accepted
        // presenter, any follower of a topic on this agenda.
        Map<String, InterestedPerson> meetingWideCandidates = new LinkedHashMap<>();
        for (EsTopicMeetingMember m : seriesMembers) {
            addCandidate(meetingWideCandidates, m.getUserId(), m.getEmailNormalized(), "Series member");
        }
        for (EsMeetingRsvp r : rsvps) {
            if (r.getResponse() != MeetingRsvpResponse.NOT_COMING) {
                addCandidate(meetingWideCandidates, r.getUserId(), null,
                        "RSVP: " + rsvpLabel(r.getResponse()));
            }
        }
        for (EsAgendaItemPresenter p : allPresenters) {
            addCandidate(meetingWideCandidates, p.getUserId(), p.getEmailNormalized(), "Presenter");
        }
        for (EsSubscription s : allFollowers) {
            addCandidate(meetingWideCandidates, s.getUserId(), s.getEmailNormalized(), "Topic follower");
        }
        List<InterestedPerson> expectedNotSeen = filterNotSeen(meetingWideCandidates.values(), seenUserIds, seenEmails);
        Map<Long, User> expectedUsers = resolveUsersById(
                meetingWideCandidates.values().stream().map(InterestedPerson::userId).toList());

        // Per-topic breakdown: presenters + followers of that specific topic.
        List<TopicBreakdown> topicBreakdowns = new ArrayList<>();
        Map<Long, List<EsAgendaItemPresenter>> presentersByItem = new LinkedHashMap<>();
        for (EsAgendaItemPresenter p : presenterDao.findByAgendaItemIds(agendaItemIds)) {
            if (p.getStatus() == EsAgendaItemPresenter.PresenterStatus.ACCEPTED) {
                presentersByItem.computeIfAbsent(p.getEsMeetingAgendaItemId(), k -> new ArrayList<>()).add(p);
            }
        }
        for (EsMeetingAgendaItem item : topicItems) {
            EsTopic topic = topicById.get(item.getEsTopicId());
            if (topic == null) {
                continue;
            }
            Map<String, InterestedPerson> topicCandidates = new LinkedHashMap<>();
            List<EsAgendaItemPresenter> itemPresenters = presentersByItem
                    .getOrDefault(item.getEsMeetingAgendaItemId(), List.of());
            for (EsAgendaItemPresenter p : itemPresenters) {
                addCandidate(topicCandidates, p.getUserId(), p.getEmailNormalized(), "Presenter");
            }
            for (EsSubscription s : allFollowers) {
                if (topic.getEsTopicId().equals(s.getEsTopicId())) {
                    addCandidate(topicCandidates, s.getUserId(), s.getEmailNormalized(), "Follower");
                }
            }
            List<InterestedPerson> notSeen = filterNotSeen(topicCandidates.values(), seenUserIds, seenEmails);
            topicBreakdowns.add(new TopicBreakdown(topic.getTopicName(), itemPresenters.size(),
                    topicCandidates.size(), notSeen));
        }
        Map<Long, User> topicPersonUsers = resolveUsersById(topicBreakdowns.stream()
                .flatMap(b -> b.notSeen().stream())
                .map(InterestedPerson::userId)
                .toList());

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

            // --- RSVP (intent, separate from attendance - lets staff compare expected vs actual) ---
            if (!rsvps.isEmpty()) {
                out.println("      <section class=\"aira-panel\">");
                out.println("        <h2 class=\"aira-section-title\">RSVP</h2>");
                out.println("        <div class=\"aira-cluster\">");
                out.println("          <span class=\"aira-badge aira-badge--success\">Coming: " + comingCount
                        + "</span>");
                out.println("          <span class=\"aira-badge aira-badge--info\">Maybe: " + maybeCount + "</span>");
                out.println("          <span class=\"aira-badge aira-badge--subtle\">Not coming: " + notComingCount
                        + "</span>");
                out.println("        </div>");
                out.println("        <ul>");
                for (EsMeetingRsvp r : rsvps) {
                    User rsvpUser = rsvpUsers.get(r.getUserId());
                    String name = rsvpUser != null ? userLabel(rsvpUser) : ("User #" + r.getUserId());
                    out.println("          <li>" + escapeHtml(name) + " &mdash; " + rsvpLabel(r.getResponse())
                            + (r.getNote() != null && !r.getNote().isBlank()
                                    ? " &mdash; " + escapeHtml(r.getNote())
                                    : "")
                            + "</li>");
                }
                out.println("        </ul>");
                out.println("      </section>");
            }

            // --- Expected but not seen (Phase 5: series members, RSVP, presenters,
            // topic followers, minus anyone with an active attendance record) ---
            if (!expectedNotSeen.isEmpty()) {
                out.println("      <section class=\"aira-panel\">");
                out.println("        <h2 class=\"aira-section-title\">Expected But Not Seen</h2>");
                out.println(
                        "        <p class=\"aira-meta\">Series members, RSVPs, presenters, and topic followers who don&rsquo;t have an attendance record yet.</p>");
                out.println("        <ul>");
                for (InterestedPerson p : expectedNotSeen) {
                    out.println("          <li>" + escapeHtml(personLabel(p, expectedUsers)) + " &mdash; "
                            + escapeHtml(p.source()) + "</li>");
                }
                out.println("        </ul>");
                out.println("      </section>");
            }

            // --- By topic (Phase 5: "we're discussing this topic, are the interested
            // people here?") ---
            if (!topicBreakdowns.isEmpty()) {
                out.println("      <section class=\"aira-panel\">");
                out.println("        <h2 class=\"aira-section-title\">By Topic</h2>");
                for (TopicBreakdown b : topicBreakdowns) {
                    int seenCount = b.interestedCount() - b.notSeen().size();
                    out.println("        <div class=\"aira-stack aira-stack--compact\">");
                    out.println("          <p><strong>" + escapeHtml(b.topicName()) + "</strong> &mdash; "
                            + b.presenterCount() + " presenter" + (b.presenterCount() == 1 ? "" : "s")
                            + ", " + seenCount + " of " + b.interestedCount() + " interested people seen</p>");
                    if (!b.notSeen().isEmpty()) {
                        out.println("          <p class=\"aira-meta\">Not yet seen: "
                                + escapeHtml(b.notSeen().stream()
                                        .map(p -> personLabel(p, topicPersonUsers) + " (" + p.source() + ")")
                                        .collect(java.util.stream.Collectors.joining(", ")))
                                + "</p>");
                    }
                    out.println("        </div>");
                }
                out.println("      </section>");
            }

            // --- Add observed attendee ---
            out.println("      <section class=\"aira-panel\">");
            out.println("        <h2 class=\"aira-section-title\">Add Observed Attendee</h2>");
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
                    + disabledAttr + ">Add Observed Attendee</button>");
            out.println("          </div>");
            out.println("        </form>");
            out.println("      </section>");

            // --- Attendee list ---
            out.println("      <section class=\"aira-panel\">");
            out.println("        <h2 class=\"aira-section-title\">Attendees</h2>");
            if (active.isEmpty()) {
                out.println("        <p class=\"aira-meta\">No attendance recorded yet.</p>");
            } else {
                out.println("        <div class=\"aira-table-wrap\">");
                out.println("        <table class=\"aira-table\">");
                out.println(
                        "          <thead><tr><th>Name</th><th>Status</th><th>Organization</th><th>Email</th><th>Details</th><th></th></tr></thead>");
                out.println("          <tbody>");
                for (EsMeetingAttendance a : active) {
                    boolean isSelfSigned = a.getSelfSignedAt() != null;
                    boolean isEditingThis = editing != null
                            && editing.getEsMeetingAttendanceId().equals(a.getEsMeetingAttendanceId());
                    out.println("            <tr>");
                    if (isEditingThis) {
                        out.println("              <td colspan=\"6\">");
                        renderEditForm(out, contextPath, meeting, a);
                        out.println("              </td>");
                    } else {
                        out.println("              <td>" + escapeHtml(a.getDisplayName()) + "</td>");
                        out.println("              <td>" + (isSelfSigned
                                ? "<span class=\"aira-badge aira-badge--success\">Signed in</span>"
                                : "<span class=\"aira-badge aira-badge--info\">Observed</span>") + "</td>");
                        out.println("              <td>" + escapeHtml(orEmpty(a.getOrganization())) + "</td>");
                        boolean hasEmail = a.getEmail() != null && !a.getEmail().isBlank();
                        out.println("              <td>" + (hasEmail
                                ? escapeHtml(a.getEmail())
                                : "<span class=\"aira-badge aira-badge--warning\">Email missing</span>") + "</td>");
                        Optional<LocalDateTime> lastInvited = !isSelfSigned && hasEmail
                                ? invitationService.lastInviteSentAt(a.getEmailNormalized())
                                : Optional.empty();
                        out.println("              <td class=\"aira-meta\">"
                                + escapeHtml(observationDetail(a, resolvedUsers, lastInvited)) + "</td>");
                        out.println("              <td>");
                        if (!isSelfSigned && windowOpen) {
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
                                    out.println(
                                            "                  <input type=\"hidden\" name=\"confirm\" value=\"1\">");
                                    out.println(
                                            "                  <button class=\"aira-button aira-button--link\" type=\"submit\">Send again</button>");
                                } else {
                                    out.println(
                                            "                  <button class=\"aira-button aira-button--link\" type=\"submit\">Invite</button>");
                                }
                                out.println("                </form>");
                            }
                            out.println(
                                    "                <form class=\"aira-inline-form\" method=\"post\" action=\""
                                            + contextPath
                                            + "/es/meeting-attendance\" onsubmit=\"return confirm('Remove this observed attendee?');\">");
                            out.println("                  <input type=\"hidden\" name=\"meetingId\" value=\""
                                    + meeting.getEsMeetingId() + "\">");
                            out.println(
                                    "                  <input type=\"hidden\" name=\"action\" value=\"removeObserved\">");
                            out.println("                  <input type=\"hidden\" name=\"attendanceId\" value=\""
                                    + a.getEsMeetingAttendanceId() + "\">");
                            out.println(
                                    "                  <button class=\"aira-button aira-button--link\" type=\"submit\">Remove</button>");
                            out.println("                </form>");
                        }
                        out.println("              </td>");
                    }
                    out.println("            </tr>");
                }
                out.println("          </tbody>");
                out.println("        </table>");
                out.println("        </div>");
            }
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
            page.writeEnd(out);
        }
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

    /** One candidate "expected" person, identified by userId (preferred) or email, with why they're expected. */
    private record InterestedPerson(Long userId, String emailNormalized, String source) {
    }

    /** One agenda topic's presenter/follower coverage for the "By Topic" facilitation view. */
    private record TopicBreakdown(String topicName, int presenterCount, int interestedCount,
            List<InterestedPerson> notSeen) {
    }

    /** Adds a candidate keyed by userId if present, else by email - first source given for a person wins. */
    private void addCandidate(Map<String, InterestedPerson> candidates, Long userId, String emailNormalized,
            String source) {
        if (userId == null && (emailNormalized == null || emailNormalized.isBlank())) {
            return;
        }
        String key = userId != null ? "u:" + userId : "e:" + emailNormalized;
        candidates.putIfAbsent(key, new InterestedPerson(userId, emailNormalized, source));
    }

    private List<InterestedPerson> filterNotSeen(Collection<InterestedPerson> candidates, Set<Long> seenUserIds,
            Set<String> seenEmails) {
        return candidates.stream()
                .filter(p -> p.userId() == null || !seenUserIds.contains(p.userId()))
                .filter(p -> p.userId() != null || p.emailNormalized() == null
                        || !seenEmails.contains(p.emailNormalized()))
                .toList();
    }

    private String personLabel(InterestedPerson p, Map<Long, User> resolvedUsers) {
        if (p.userId() != null) {
            User u = resolvedUsers.get(p.userId());
            return u != null ? userLabel(u) : ("User #" + p.userId());
        }
        return orEmpty(p.emailNormalized());
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
