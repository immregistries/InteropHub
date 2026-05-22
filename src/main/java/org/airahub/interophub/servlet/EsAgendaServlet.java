package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsAgendaItemPresenterDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsMeetingAttendanceDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsMeetingAttendance;
import org.airahub.interophub.model.EsAgendaItemPresenter;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeeting.MeetingStatus;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsMeetingAgendaItem.AgendaItemStatus;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.User;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.dao.HubSettingDao;
import org.airahub.interophub.model.HubSetting;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EmailService;
import org.airahub.interophub.service.EmailTemplates;
import org.airahub.interophub.service.EsInterestService;
import org.airahub.interophub.service.MeetingCommunicationService;

public class EsAgendaServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(EsAgendaServlet.class.getName());
    private static final DateTimeFormatter DATE_PARSE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_PARSE_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    private static final DateTimeFormatter DISPLAY_TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter INPUT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter INPUT_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

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
    private final EsMeetingDao meetingDao;
    private final EsMeetingAgendaItemDao agendaItemDao;
    private final EsTopicDao topicDao;
    private final EsAgendaItemPresenterDao presenterDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final UserDao userDao;
    private final EmailService emailService;
    private final HubSettingDao hubSettingDao;
    private final MeetingCommunicationService meetingCommunicationService;
    private final EsInterestService esInterestService;
    private final EsMeetingAttendanceDao attendanceDao;

    public EsAgendaServlet() {
        this.authFlowService = new AuthFlowService();
        this.meetingDao = new EsMeetingDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.topicDao = new EsTopicDao();
        this.presenterDao = new EsAgendaItemPresenterDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.userDao = new UserDao();
        this.emailService = new EmailService();
        this.hubSettingDao = new HubSettingDao();
        this.meetingCommunicationService = new MeetingCommunicationService();
        this.esInterestService = new EsInterestService();
        this.attendanceDao = new EsMeetingAttendanceDao();
    }

    // =========================================================================
    // GET
    // =========================================================================

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> userOpt = authFlowService.findAuthenticatedUser(request);
        User user = userOpt.orElse(null);
        String contextPath = request.getContextPath();

        // If the link contains a loginHint, redirect unauthenticated visitors to the
        // login page with the email pre-filled. The return URL was already saved by
        // findAuthenticatedUser so the user lands back here after signing in.
        String loginHint = trimToNull(request.getParameter("loginHint"));
        if (loginHint != null && user == null) {
            response.sendRedirect(contextPath + "/home?email="
                    + URLEncoder.encode(loginHint, StandardCharsets.UTF_8));
            return;
        }
        // If already logged in under a different email, surface a warning.
        String loginHintMismatch = null;
        if (loginHint != null && user != null
                && !loginHint.trim().equalsIgnoreCase(user.getEmailNormalized())) {
            loginHintMismatch = loginHint.trim();
        }

        Long meetingId = parseId(trimToNull(request.getParameter("meetingId")));
        if (meetingId == null) {
            renderError(response, contextPath, "Missing or invalid meetingId parameter.");
            return;
        }

        EsMeeting meeting = meetingDao.findById(meetingId).orElse(null);
        if (meeting == null) {
            renderError(response, contextPath, "Meeting not found.");
            return;
        }

        List<EsMeetingAgendaItem> items = agendaItemDao.findByMeetingIdOrdered(meetingId);
        boolean isEditor = user != null && isEditor(user, meeting, items);

        // Seed default agenda items when empty and meeting is editable
        if (items.isEmpty() && meeting.getStatus() != MeetingStatus.COMPLETED
                && meeting.getStatus() != MeetingStatus.CANCELLED) {
            createDefaultAgendaItems(meeting);
            items = agendaItemDao.findByMeetingIdOrdered(meetingId);
        }

        boolean editOverride = "true".equals(request.getParameter("edit"));
        boolean canEdit = canEdit(user, meeting, editOverride, isEditor);

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

        // Next meeting for footer
        EsMeeting nextMeeting = findNextMeeting(meeting);

        String savedMsg = request.getParameter("saved") != null ? "Changes saved." : null;
        if (savedMsg == null && "1".equals(request.getParameter("topicsSaved"))) {
            savedMsg = "Topic interests saved.";
        }
        String errorMsg = trimToNull(request.getParameter("err"));
        String suggestBanner = buildSuggestBanner(contextPath, meeting.getEsMeetingId(),
                trimToNull(request.getParameter("suggest")));

        // Topic interest section — determine attendee email and existing subscriptions.
        // Read the session attribute (set by EsMeetingAttendanceServlet after check-in)
        // and remove it immediately to prevent stale state on back-navigation.
        String lastAttendedEmail = null;
        {
            jakarta.servlet.http.HttpSession sess = request.getSession(false);
            if (sess != null) {
                lastAttendedEmail = (String) sess.getAttribute("interophub.lastAttendedEmail");
                sess.removeAttribute("interophub.lastAttendedEmail");
            }
        }
        String attendeeEmailForInterest = user != null ? user.getEmailNormalized() : lastAttendedEmail;

        // Collect agenda topic IDs (linked, non-cancelled) for subscription lookup.
        List<Long> agendaTopicIds = items.stream()
                .filter(i -> i.getEsTopicId() != null && i.getStatus() != AgendaItemStatus.CANCELLED)
                .map(EsMeetingAgendaItem::getEsTopicId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, EsSubscription> subsByTopicId = new java.util.LinkedHashMap<>();
        if (attendeeEmailForInterest != null && !agendaTopicIds.isEmpty()) {
            List<EsSubscription> emailSubs = subscriptionDao.findByEmailNormalizedAndType(
                    attendeeEmailForInterest, EsSubscription.SubscriptionType.TOPIC);
            for (EsSubscription sub : emailSubs) {
                if (sub.getEsTopicId() != null && agendaTopicIds.contains(sub.getEsTopicId())) {
                    subsByTopicId.merge(sub.getEsTopicId(), sub, EsAgendaServlet::preferHigherRankSub);
                }
            }
            if (user != null && user.getUserId() != null) {
                List<EsSubscription> userSubs = subscriptionDao.findByUserId(user.getUserId());
                for (EsSubscription sub : userSubs) {
                    if (sub.getEsTopicId() != null && agendaTopicIds.contains(sub.getEsTopicId())) {
                        subsByTopicId.merge(sub.getEsTopicId(), sub, EsAgendaServlet::preferHigherRankSub);
                    }
                }
            }
        }

        // --- ATTENDANCE window check and data load ---
        ZoneId attendanceCheckZone = safeZoneId(meeting.getTimezoneId(), "America/New_York");
        boolean isWithinAttendanceWindow = meeting.getScheduledStart() != null
                && ZonedDateTime.now(attendanceCheckZone)
                        .isAfter(ZonedDateTime.of(meeting.getScheduledStart(), attendanceCheckZone).minusMinutes(15));
        List<EsMeetingAttendance> meetingAttendees = isWithinAttendanceWindow
                ? attendanceDao.findByEsMeetingId(meetingId)
                : List.of();

        renderPage(response, contextPath, user, meeting, items, presentersByItem, presenterUsers,
                isEditor, canEdit, editOverride, nextMeeting, savedMsg, errorMsg, loginHintMismatch, suggestBanner,
                attendeeEmailForInterest, subsByTopicId, agendaTopicIds,
                isWithinAttendanceWindow, meetingAttendees);
    }

    // =========================================================================
    // POST
    // =========================================================================

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        // updateTopicInterest is allowed for anonymous attendees (identified via
        // hidden attendeeEmail form field set during check-in redirect).
        String actionEarly = trimToNull(request.getParameter("action"));
        if ("updateTopicInterest".equals(actionEarly)) {
            handleUpdateTopicInterest(request, response);
            return;
        }

        Optional<User> userOpt = requireLogin(request, response);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        String contextPath = request.getContextPath();

        Long meetingId = parseId(trimToNull(request.getParameter("meetingId")));
        if (meetingId == null) {
            response.sendRedirect(contextPath + "/es/agenda");
            return;
        }

        EsMeeting meeting = meetingDao.findById(meetingId).orElse(null);
        if (meeting == null) {
            response.sendRedirect(contextPath + "/es/agenda");
            return;
        }

        List<EsMeetingAgendaItem> items = agendaItemDao.findByMeetingIdOrdered(meetingId);
        boolean isEditor = isEditor(user, meeting, items);
        boolean editOverride = "true".equals(request.getParameter("edit"));
        boolean canEdit = canEdit(user, meeting, editOverride, isEditor);

        String action = trimToNull(request.getParameter("action"));
        if (action == null) {
            redirectBack(response, contextPath, meetingId, editOverride);
            return;
        }

        // Viewer timezone update does not require edit permission
        if ("updateViewerTimezone".equals(action)) {
            handleUpdateViewerTimezone(request, response, contextPath, user, meetingId, editOverride);
            return;
        }

        // Presenter response actions (invitee responding to their own invitation)
        if ("presenterAccept".equals(action)) {
            handlePresenterAccept(request, response, contextPath, meeting, user, editOverride);
            return;
        }
        if ("presenterDecline".equals(action)) {
            handlePresenterDecline(request, response, contextPath, meeting, user, editOverride);
            return;
        }

        // Meeting status transitions only require editor role, not full canEdit
        // (allows transitioning out of COMPLETED or FINALIZED without edit override)
        if ("updateMeetingStatus".equals(action)) {
            if (!isEditor) {
                redirectBackWithError(response, contextPath, meetingId, editOverride,
                        "You do not have permission to edit this agenda.");
                return;
            }
            handleUpdateMeetingStatus(request, response, contextPath, meeting, items, user, editOverride);
            return;
        }

        // All other actions require edit permission
        if (!canEdit) {
            redirectBackWithError(response, contextPath, meetingId, editOverride,
                    "You do not have permission to edit this agenda.");
            return;
        }

        switch (action) {
            case "updateMeetingName":
                handleUpdateMeetingName(request, response, contextPath, meeting, editOverride);
                break;
            case "updateMeetingDate":
                handleUpdateMeetingDate(request, response, contextPath, meeting, editOverride);
                break;
            case "updateMeetingTime":
                handleUpdateMeetingTime(request, response, contextPath, meeting, editOverride);
                break;
            case "updateMeetingTimezone":
                handleUpdateMeetingTimezone(request, response, contextPath, meeting, editOverride);
                break;
            case "updateMeetingTimeAndTimezone":
                handleUpdateMeetingTimeAndTimezone(request, response, contextPath, meeting, user, editOverride);
                break;
            case "updateMeetingDescription":
                handleUpdateMeetingDescription(request, response, contextPath, meeting, editOverride);
                break;
            case "updateMeetingOnlineInfo":
                handleUpdateMeetingOnlineInfo(request, response, contextPath, meeting, editOverride);
                break;
            case "addAgendaItem":
                handleAddAgendaItem(request, response, contextPath, meeting, items, editOverride);
                break;
            case "updateAgendaItem":
                handleUpdateAgendaItem(request, response, contextPath, meeting, editOverride);
                break;
            case "moveItemUp":
                handleMoveItem(request, response, contextPath, meeting, items, editOverride, true);
                break;
            case "moveItemDown":
                handleMoveItem(request, response, contextPath, meeting, items, editOverride, false);
                break;
            case "cancelAgendaItem":
                handleCancelAgendaItem(request, response, contextPath, meeting, editOverride);
                break;
            case "updateItemStatus":
                handleUpdateItemStatus(request, response, contextPath, meeting, editOverride);
                break;
            case "addPresenter":
                handleAddPresenter(request, response, contextPath, meeting, user, editOverride);
                break;
            case "acceptPresenter":
                handleAcceptPresenter(request, response, contextPath, meeting, editOverride);
                break;
            case "removePresenter":
                handleRemovePresenter(request, response, contextPath, meeting, editOverride);
                break;
            case "updatePresenterRole":
                handleUpdatePresenterRole(request, response, contextPath, meeting, editOverride);
                break;
            case "copyAgendaItem":
                handleCopyAgendaItem(request, response, contextPath, meeting, items, user, editOverride);
                break;
            default:
                redirectBack(response, contextPath, meetingId, editOverride);
        }
    }

    // =========================================================================
    // Access control
    // =========================================================================

    private Optional<User> requireLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> userOpt = authFlowService.findAuthenticatedUser(request);
        if (userOpt.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
        }
        return userOpt;
    }

    private boolean isEditor(User user, EsMeeting meeting, List<EsMeetingAgendaItem> items) {
        if (authFlowService.isAdminUser(user)) {
            return true;
        }
        // Check if user is an active presenter on any agenda item of this meeting
        Set<Long> itemIds = items.stream()
                .map(EsMeetingAgendaItem::getEsMeetingAgendaItemId)
                .collect(Collectors.toSet());
        if (itemIds.isEmpty()) {
            return false;
        }
        for (Long itemId : itemIds) {
            List<EsAgendaItemPresenter> presenters = presenterDao.findByAgendaItemId(itemId);
            for (EsAgendaItemPresenter p : presenters) {
                if (p.getStatus() == EsAgendaItemPresenter.PresenterStatus.REMOVED
                        || p.getStatus() == EsAgendaItemPresenter.PresenterStatus.DECLINED) {
                    continue;
                }
                if (user.getUserId() != null && user.getUserId().equals(p.getUserId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canEdit(User user, EsMeeting meeting, boolean editOverride, boolean isEditor) {
        if (!isEditor) {
            return false;
        }
        MeetingStatus status = meeting.getStatus();
        if (status == MeetingStatus.COMPLETED || status == MeetingStatus.CANCELLED) {
            return false;
        }
        if (status == MeetingStatus.FINALIZED) {
            return editOverride;
        }
        // DRAFT or PROPOSED
        return true;
    }

    // =========================================================================
    // POST handlers — meeting fields
    // =========================================================================

    private void handleUpdateMeetingName(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        String name = trimToNull(request.getParameter("name"));
        if (name == null) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Meeting name is required.");
            return;
        }
        meeting.setMeetingName(name);
        meetingDao.saveOrUpdate(meeting);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleUpdateMeetingDate(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        String dateRaw = trimToNull(request.getParameter("date"));
        if (dateRaw == null) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride, "Date is required.");
            return;
        }
        LocalDate newDate;
        try {
            newDate = LocalDate.parse(dateRaw, DATE_PARSE_FMT);
        } catch (DateTimeParseException ex) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Invalid date format.");
            return;
        }
        LocalTime existingStartTime = meeting.getScheduledStart() != null
                ? meeting.getScheduledStart().toLocalTime()
                : LocalTime.of(11, 0);
        LocalDateTime newStart = newDate.atTime(existingStartTime);
        if (meeting.getScheduledEnd() != null && meeting.getScheduledStart() != null) {
            long durationMinutes = ChronoUnit.MINUTES.between(meeting.getScheduledStart(), meeting.getScheduledEnd());
            meeting.setScheduledEnd(newStart.plusMinutes(durationMinutes));
        }
        meeting.setScheduledStart(newStart);
        meetingDao.saveOrUpdate(meeting);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleUpdateMeetingTime(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        String startTimeRaw = trimToNull(request.getParameter("startTime"));
        String endTimeRaw = trimToNull(request.getParameter("endTime"));
        if (startTimeRaw == null) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Start time is required.");
            return;
        }
        LocalTime newStart;
        try {
            newStart = LocalTime.parse(startTimeRaw, TIME_PARSE_FMT);
        } catch (DateTimeParseException ex) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Invalid start time format (use HH:mm).");
            return;
        }
        LocalDate existingDate = meeting.getScheduledStart() != null
                ? meeting.getScheduledStart().toLocalDate()
                : LocalDate.now();
        meeting.setScheduledStart(existingDate.atTime(newStart));
        if (endTimeRaw != null && !endTimeRaw.isBlank()) {
            try {
                LocalTime newEnd = LocalTime.parse(endTimeRaw, TIME_PARSE_FMT);
                meeting.setScheduledEnd(existingDate.atTime(newEnd));
            } catch (DateTimeParseException ex) {
                redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                        "Invalid end time format (use HH:mm).");
                return;
            }
        } else {
            meeting.setScheduledEnd(null);
        }
        meetingDao.saveOrUpdate(meeting);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleUpdateMeetingTimezone(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        String tz = trimToNull(request.getParameter("timezoneId"));
        if (tz == null || !ALLOWED_TIMEZONES.contains(tz)) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Invalid or unsupported timezone.");
            return;
        }
        meeting.setTimezoneId(tz);
        meetingDao.saveOrUpdate(meeting);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleUpdateMeetingTimeAndTimezone(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, User user, boolean editOverride) throws IOException {
        // 1. Meeting timezone (set first; times are entered relative to this zone)
        String meetingTzRaw = trimToNull(request.getParameter("meetingTimezone"));
        if (meetingTzRaw != null && ALLOWED_TIMEZONES.contains(meetingTzRaw)) {
            meeting.setTimezoneId(meetingTzRaw);
        }
        // 2. Start time (required)
        String startTimeRaw = trimToNull(request.getParameter("startTime"));
        if (startTimeRaw == null) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Start time is required.");
            return;
        }
        LocalTime newStart;
        try {
            newStart = LocalTime.parse(startTimeRaw, TIME_PARSE_FMT);
        } catch (DateTimeParseException ex) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Invalid start time format (use HH:mm).");
            return;
        }
        LocalDate existingDate = meeting.getScheduledStart() != null
                ? meeting.getScheduledStart().toLocalDate()
                : LocalDate.now();
        meeting.setScheduledStart(existingDate.atTime(newStart));
        // 3. End time (optional)
        String endTimeRaw = trimToNull(request.getParameter("endTime"));
        if (endTimeRaw != null && !endTimeRaw.isBlank()) {
            try {
                LocalTime newEnd = LocalTime.parse(endTimeRaw, TIME_PARSE_FMT);
                meeting.setScheduledEnd(existingDate.atTime(newEnd));
            } catch (DateTimeParseException ex) {
                redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                        "Invalid end time format (use HH:mm).");
                return;
            }
        } else {
            meeting.setScheduledEnd(null);
        }
        meetingDao.saveOrUpdate(meeting);
        // 4. Viewer (My) timezone
        String viewerTzRaw = trimToNull(request.getParameter("viewerTimezone"));
        if (viewerTzRaw != null && ALLOWED_TIMEZONES.contains(viewerTzRaw)) {
            user.setTimezoneId(viewerTzRaw);
            userDao.saveOrUpdate(user);
        }
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleUpdateMeetingDescription(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        String desc = trimToNull(request.getParameter("description"));
        meeting.setMeetingDescription(desc);
        meetingDao.saveOrUpdate(meeting);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleUpdateMeetingOnlineInfo(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        meeting.setOnlineMeetingUrl(trimToNull(request.getParameter("onlineMeetingUrl")));
        meeting.setOnlineMeetingDetails(trimToNull(request.getParameter("onlineMeetingDetails")));
        meetingDao.saveOrUpdate(meeting);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleUpdateViewerTimezone(HttpServletRequest request, HttpServletResponse response,
            String contextPath, User user, Long meetingId, boolean editOverride) throws IOException {
        String tz = trimToNull(request.getParameter("timezoneId"));
        if (tz == null || !ALLOWED_TIMEZONES.contains(tz)) {
            redirectBackWithError(response, contextPath, meetingId, editOverride, "Invalid or unsupported timezone.");
            return;
        }
        user.setTimezoneId(tz);
        userDao.saveOrUpdate(user);
        redirectBack(response, contextPath, meetingId, editOverride);
    }

    // =========================================================================
    // POST handlers — agenda items
    // =========================================================================

    private void handleAddAgendaItem(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, List<EsMeetingAgendaItem> items,
            boolean editOverride) throws IOException {
        String title = trimToNull(request.getParameter("title"));
        if (title == null) {
            title = "New Agenda Item";
        }
        String topicIdRaw = trimToNull(request.getParameter("topicId"));
        Long topicId = null;
        if (topicIdRaw != null) {
            try {
                topicId = Long.parseLong(topicIdRaw);
            } catch (NumberFormatException ignored) {
            }
        }
        int maxOrder = items.stream()
                .mapToInt(i -> i.getDisplayOrder() != null ? i.getDisplayOrder() : 0)
                .max().orElse(0);
        // Default to end; insert before last item if it looks like a "Wrap Up" closer
        List<EsMeetingAgendaItem> visibleItems = items.stream()
                .filter(i -> i.getStatus() != AgendaItemStatus.CANCELLED)
                .collect(Collectors.toList());
        int newOrder = maxOrder + 10;
        if (!visibleItems.isEmpty()) {
            EsMeetingAgendaItem last = visibleItems.get(visibleItems.size() - 1);
            boolean isWrapUp = Integer.valueOf(5).equals(last.getTimeMinutes())
                    || "wrap up".equalsIgnoreCase(last.getTitle() != null ? last.getTitle().trim() : "");
            if (isWrapUp) {
                int wrapUpOrder = last.getDisplayOrder() != null ? last.getDisplayOrder() : maxOrder;
                if (visibleItems.size() > 1) {
                    EsMeetingAgendaItem prev = visibleItems.get(visibleItems.size() - 2);
                    int prevOrder = prev.getDisplayOrder() != null ? prev.getDisplayOrder() : 0;
                    newOrder = wrapUpOrder - prevOrder > 1
                            ? prevOrder + (wrapUpOrder - prevOrder) / 2
                            : wrapUpOrder - 1;
                } else {
                    newOrder = wrapUpOrder - 5;
                }
            }
        }
        EsMeetingAgendaItem newItem = new EsMeetingAgendaItem();
        newItem.setEsMeetingId(meeting.getEsMeetingId());
        newItem.setTitle(title);
        newItem.setAgendaMarkdown("");
        newItem.setTimeMinutes(20);
        newItem.setDisplayOrder(newOrder);
        newItem.setStatus(AgendaItemStatus.DRAFT);
        if (topicId != null) {
            newItem.setEsTopicId(topicId);
        }
        agendaItemDao.save(newItem);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleUpdateAgendaItem(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        Long itemId = parseId(trimToNull(request.getParameter("itemId")));
        if (itemId == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        EsMeetingAgendaItem item = agendaItemDao.findById(itemId).orElse(null);
        if (item == null || !meeting.getEsMeetingId().equals(item.getEsMeetingId())) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        String title = trimToNull(request.getParameter("title"));
        if (title != null && !title.isBlank()) {
            item.setTitle(title);
        }
        String markdown = request.getParameter("agendaMarkdown");
        if (markdown != null) {
            item.setAgendaMarkdown(markdown.isBlank() ? null : markdown);
        }
        String minutesRaw = trimToNull(request.getParameter("timeMinutes"));
        if (minutesRaw != null) {
            try {
                item.setTimeMinutes(Integer.parseInt(minutesRaw));
            } catch (NumberFormatException ignored) {
                // keep existing value
            }
        }
        agendaItemDao.saveOrUpdate(item);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleMoveItem(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, List<EsMeetingAgendaItem> items,
            boolean editOverride, boolean moveUp) throws IOException {
        Long itemId = parseId(trimToNull(request.getParameter("itemId")));
        if (itemId == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        // Find the item and its neighbor in the ordered list
        int idx = -1;
        for (int i = 0; i < items.size(); i++) {
            if (itemId.equals(items.get(i).getEsMeetingAgendaItemId())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        int neighborIdx = moveUp ? idx - 1 : idx + 1;
        if (neighborIdx < 0 || neighborIdx >= items.size()) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        EsMeetingAgendaItem item = items.get(idx);
        EsMeetingAgendaItem neighbor = items.get(neighborIdx);
        int itemOrder = item.getDisplayOrder() != null ? item.getDisplayOrder() : 0;
        int neighborOrder = neighbor.getDisplayOrder() != null ? neighbor.getDisplayOrder() : 0;
        if (itemOrder == neighborOrder) {
            // Ensure distinct values before swap
            neighborOrder = itemOrder + (moveUp ? -1 : 1);
        }
        Map<Long, Integer> reorderMap = new LinkedHashMap<>();
        reorderMap.put(item.getEsMeetingAgendaItemId(), neighborOrder);
        reorderMap.put(neighbor.getEsMeetingAgendaItemId(), itemOrder);
        agendaItemDao.reorderItems(reorderMap);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleCancelAgendaItem(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        Long itemId = parseId(trimToNull(request.getParameter("itemId")));
        if (itemId != null) {
            EsMeetingAgendaItem item = agendaItemDao.findById(itemId).orElse(null);
            if (item != null && meeting.getEsMeetingId().equals(item.getEsMeetingId())) {
                agendaItemDao.cancelItem(itemId);
            }
        }
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleUpdateItemStatus(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        Long itemId = parseId(trimToNull(request.getParameter("itemId")));
        String targetStatusRaw = trimToNull(request.getParameter("targetStatus"));
        if (itemId == null || targetStatusRaw == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        EsMeetingAgendaItem item = agendaItemDao.findById(itemId).orElse(null);
        if (item == null || !meeting.getEsMeetingId().equals(item.getEsMeetingId())) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        AgendaItemStatus targetStatus;
        try {
            targetStatus = AgendaItemStatus.valueOf(targetStatusRaw);
        } catch (IllegalArgumentException ex) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        AgendaItemStatus current = item.getStatus();
        if (!isValidItemStatusTransition(current, targetStatus)) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Invalid status transition: " + current + " → " + targetStatus);
            return;
        }
        applyItemStatusTransition(item, targetStatus);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private boolean isValidItemStatusTransition(AgendaItemStatus from, AgendaItemStatus to) {
        switch (from) {
            case DRAFT:
                return to == AgendaItemStatus.PROPOSED || to == AgendaItemStatus.ACCEPTED
                        || to == AgendaItemStatus.NEEDS_REVISION || to == AgendaItemStatus.POSTPONED
                        || to == AgendaItemStatus.CANCELLED;
            case PROPOSED:
                return to == AgendaItemStatus.ACCEPTED || to == AgendaItemStatus.NEEDS_REVISION
                        || to == AgendaItemStatus.POSTPONED || to == AgendaItemStatus.CANCELLED;
            case ACCEPTED:
                return to == AgendaItemStatus.POSTPONED || to == AgendaItemStatus.COVERED
                        || to == AgendaItemStatus.NOT_COVERED || to == AgendaItemStatus.CANCELLED;
            case NEEDS_REVISION:
                return to == AgendaItemStatus.ACCEPTED || to == AgendaItemStatus.PROPOSED
                        || to == AgendaItemStatus.POSTPONED || to == AgendaItemStatus.CANCELLED;
            case POSTPONED:
                return to == AgendaItemStatus.CANCELLED;
            default:
                return false;
        }
    }

    private void applyItemStatusTransition(EsMeetingAgendaItem item, AgendaItemStatus targetStatus) {
        switch (targetStatus) {
            case POSTPONED:
                agendaItemDao.postponeItem(item.getEsMeetingAgendaItemId(), null);
                break;
            case COVERED:
                agendaItemDao.markCovered(item.getEsMeetingAgendaItemId());
                break;
            case NOT_COVERED:
                agendaItemDao.markNotCovered(item.getEsMeetingAgendaItemId());
                break;
            case CANCELLED:
                agendaItemDao.cancelItem(item.getEsMeetingAgendaItemId());
                break;
            default:
                item.setStatus(targetStatus);
                if (targetStatus == AgendaItemStatus.ACCEPTED) {
                    item.setAcceptedAt(LocalDateTime.now());
                }
                agendaItemDao.saveOrUpdate(item);
        }
    }

    // =========================================================================
    // POST handlers — meeting status
    // =========================================================================

    private void handleUpdateMeetingStatus(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, List<EsMeetingAgendaItem> items,
            User user, boolean editOverride) throws IOException {
        String targetStatusRaw = trimToNull(request.getParameter("targetStatus"));
        if (targetStatusRaw == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        MeetingStatus targetStatus;
        try {
            targetStatus = MeetingStatus.valueOf(targetStatusRaw);
        } catch (IllegalArgumentException ex) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Unknown meeting status.");
            return;
        }
        MeetingStatus current = meeting.getStatus();
        if (!isValidMeetingStatusTransition(current, targetStatus)) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Invalid meeting status transition: " + current + " → " + targetStatus);
            return;
        }
        // Finalization requires no items still in DRAFT or PROPOSED
        if (targetStatus == MeetingStatus.FINALIZED) {
            boolean blocked = items.stream()
                    .anyMatch(i -> i.getStatus() == AgendaItemStatus.DRAFT
                            || i.getStatus() == AgendaItemStatus.PROPOSED);
            if (blocked) {
                redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                        "This meeting cannot be finalized until all draft and proposed agenda items are resolved.");
                return;
            }
        }
        // Completion requires meeting to have already started
        if (targetStatus == MeetingStatus.COMPLETED && !isMeetingStarted(meeting)) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "This meeting cannot be marked complete before it has started.");
            return;
        }
        applyMeetingStatusTransition(meeting, targetStatus,
                trimToNull(request.getParameter("cancellationReason")));
        // After finalize or complete, edit override no longer makes sense
        boolean keepEdit = targetStatus == MeetingStatus.FINALIZED ? false : editOverride;
        // Auto-cancel pending communications when a meeting is cancelled
        if (targetStatus == MeetingStatus.CANCELLED) {
            meetingCommunicationService.cancelAllPendingForMeeting(
                    meeting.getEsMeetingId(), user.getUserId());
        }
        // Suggest relevant communication type via redirect param
        String suggestType = switch (targetStatus) {
            case PROPOSED -> "PROPOSED_AGENDA";
            case FINALIZED -> "FINAL_AGENDA";
            case CANCELLED -> "CANCELLED";
            default -> null;
        };
        redirectBackWithSuggest(response, contextPath, meeting.getEsMeetingId(), keepEdit, suggestType);
    }

    private boolean isValidMeetingStatusTransition(MeetingStatus from, MeetingStatus to) {
        switch (from) {
            case DRAFT:
                return to == MeetingStatus.PROPOSED || to == MeetingStatus.FINALIZED
                        || to == MeetingStatus.CANCELLED;
            case PROPOSED:
                return to == MeetingStatus.FINALIZED || to == MeetingStatus.COMPLETED
                        || to == MeetingStatus.CANCELLED;
            case FINALIZED:
                return to == MeetingStatus.COMPLETED || to == MeetingStatus.CANCELLED;
            case COMPLETED:
                return to == MeetingStatus.FINALIZED;
            default:
                return false;
        }
    }

    private void applyMeetingStatusTransition(EsMeeting meeting, MeetingStatus targetStatus,
            String cancellationReason) {
        switch (targetStatus) {
            case CANCELLED:
                meetingDao.cancelMeeting(meeting.getEsMeetingId(), cancellationReason);
                break;
            case FINALIZED:
                if (meeting.getStatus() == MeetingStatus.PROPOSED) {
                    meetingDao.finalizeMeeting(meeting.getEsMeetingId());
                } else if (meeting.getStatus() == MeetingStatus.COMPLETED) {
                    meetingDao.uncompleteMeeting(meeting.getEsMeetingId());
                } else {
                    meeting.setStatus(MeetingStatus.FINALIZED);
                    meeting.setFinalizedAt(LocalDateTime.now());
                    meetingDao.saveOrUpdate(meeting);
                }
                break;
            case COMPLETED:
                if (meeting.getStatus() == MeetingStatus.FINALIZED) {
                    meetingDao.completeMeeting(meeting.getEsMeetingId());
                } else {
                    meeting.setStatus(MeetingStatus.COMPLETED);
                    meeting.setCompletedAt(LocalDateTime.now());
                    meetingDao.saveOrUpdate(meeting);
                }
                break;
            default:
                meeting.setStatus(targetStatus);
                meetingDao.saveOrUpdate(meeting);
        }
    }

    // =========================================================================
    // POST handlers — presenters
    // =========================================================================

    private void handleAddPresenter(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, User currentUser, boolean editOverride) throws IOException {
        Long itemId = parseId(trimToNull(request.getParameter("itemId")));
        if (itemId == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        EsMeetingAgendaItem item = agendaItemDao.findById(itemId).orElse(null);
        if (item == null || !meeting.getEsMeetingId().equals(item.getEsMeetingId())) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }

        String userIdRaw = trimToNull(request.getParameter("userId"));
        Long userId = null;
        String email = null;
        String displayName = null;

        if (userIdRaw != null) {
            try {
                userId = Long.parseLong(userIdRaw);
            } catch (NumberFormatException ignored) {
            }
        }

        if (userId != null) {
            User u = userDao.findById(userId).orElse(null);
            if (u == null) {
                redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                        "User not found.");
                return;
            }
            email = u.getEmail();
            displayName = u.getFullName();
        } else {
            email = trimToNull(request.getParameter("email"));
            displayName = trimToNull(request.getParameter("displayName"));
            if (email == null) {
                redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                        "Email is required.");
                return;
            }
        }

        String emailNormalized = email.trim().toLowerCase();

        // Check for duplicate active presenter
        for (EsAgendaItemPresenter existing : presenterDao.findByAgendaItemId(itemId)) {
            if (existing.getStatus() != EsAgendaItemPresenter.PresenterStatus.REMOVED
                    && existing.getStatus() != EsAgendaItemPresenter.PresenterStatus.DECLINED
                    && emailNormalized.equals(existing.getEmailNormalized())) {
                redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                        "This presenter is already on the agenda item.");
                return;
            }
        }

        boolean isAddingSelf = currentUser.getEmail() != null
                && emailNormalized.equals(currentUser.getEmail().trim().toLowerCase());
        EsAgendaItemPresenter.PresenterStatus status = isAddingSelf
                ? EsAgendaItemPresenter.PresenterStatus.ACCEPTED
                : EsAgendaItemPresenter.PresenterStatus.INVITED;

        EsAgendaItemPresenter p = new EsAgendaItemPresenter();
        p.setEsMeetingAgendaItemId(itemId);
        if (userId != null) {
            p.setUserId(userId);
        }
        p.setEmail(email.trim());
        p.setEmailNormalized(emailNormalized);
        if (displayName != null && !displayName.isBlank()) {
            p.setDisplayName(displayName);
        }
        EsAgendaItemPresenter.PresenterRole role = EsAgendaItemPresenter.PresenterRole.LEAD;
        String roleRaw = trimToNull(request.getParameter("presenterRole"));
        if (roleRaw != null) {
            try {
                role = EsAgendaItemPresenter.PresenterRole.valueOf(roleRaw);
            } catch (IllegalArgumentException ignored) {
            }
        }
        p.setPresenterRole(role);
        p.setStatus(status);
        presenterDao.save(p);
        if (status == EsAgendaItemPresenter.PresenterStatus.INVITED) {
            sendPresenterInvitationEmail(email, displayName, item, meeting, role);
        }
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleAcceptPresenter(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        Long presenterId = parseId(trimToNull(request.getParameter("presenterId")));
        EsAgendaItemPresenter p = validatePresenterForMeeting(presenterId, meeting);
        if (p == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        presenterDao.acceptInvitation(presenterId);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleRemovePresenter(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        Long presenterId = parseId(trimToNull(request.getParameter("presenterId")));
        EsAgendaItemPresenter p = validatePresenterForMeeting(presenterId, meeting);
        if (p == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        presenterDao.removePresenter(presenterId);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handleUpdatePresenterRole(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, boolean editOverride) throws IOException {
        Long presenterId = parseId(trimToNull(request.getParameter("presenterId")));
        EsAgendaItemPresenter p = validatePresenterForMeeting(presenterId, meeting);
        if (p == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        String roleRaw = trimToNull(request.getParameter("presenterRole"));
        if (roleRaw == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        EsAgendaItemPresenter.PresenterRole role;
        try {
            role = EsAgendaItemPresenter.PresenterRole.valueOf(roleRaw);
        } catch (IllegalArgumentException ex) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Invalid presenter role.");
            return;
        }
        presenterDao.updateRole(presenterId, role);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handlePresenterAccept(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, User currentUser, boolean editOverride) throws IOException {
        Long presenterId = parseId(trimToNull(request.getParameter("presenterId")));
        if (presenterId == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        EsAgendaItemPresenter p = presenterDao.findById(presenterId).orElse(null);
        if (p == null || !meeting.getEsMeetingId().equals(
                agendaItemDao.findById(p.getEsMeetingAgendaItemId()).map(EsMeetingAgendaItem::getEsMeetingId)
                        .orElse(null))) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        if (currentUser.getUserId() == null || !currentUser.getUserId().equals(p.getUserId())) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "You can only respond to your own invitations.");
            return;
        }
        String roleRaw = trimToNull(request.getParameter("presenterRole"));
        if (roleRaw != null) {
            try {
                EsAgendaItemPresenter.PresenterRole newRole = EsAgendaItemPresenter.PresenterRole.valueOf(roleRaw);
                presenterDao.updateRole(presenterId, newRole);
            } catch (IllegalArgumentException ignored) {
            }
        }
        presenterDao.acceptInvitation(presenterId);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void handlePresenterDecline(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, User currentUser, boolean editOverride) throws IOException {
        Long presenterId = parseId(trimToNull(request.getParameter("presenterId")));
        if (presenterId == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        EsAgendaItemPresenter p = presenterDao.findById(presenterId).orElse(null);
        if (p == null || !meeting.getEsMeetingId().equals(
                agendaItemDao.findById(p.getEsMeetingAgendaItemId()).map(EsMeetingAgendaItem::getEsMeetingId)
                        .orElse(null))) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        if (currentUser.getUserId() == null || !currentUser.getUserId().equals(p.getUserId())) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "You can only respond to your own invitations.");
            return;
        }
        String responseNote = trimToNull(request.getParameter("responseNote"));
        presenterDao.declineInvitation(presenterId, responseNote);
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void sendPresenterInvitationEmail(String recipientEmail, String recipientName,
            EsMeetingAgendaItem item, EsMeeting meeting, EsAgendaItemPresenter.PresenterRole role) {
        try {
            HubSetting settings = hubSettingDao.findActive()
                    .or(() -> hubSettingDao.findFirst())
                    .orElse(null);
            String baseUrl = (settings != null && settings.getExternalBaseUrl() != null
                    && !settings.getExternalBaseUrl().isBlank())
                            ? settings.getExternalBaseUrl().trim()
                            : "http://localhost:8080/hub";
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String agendaLink = baseUrl + "/es/agenda?meetingId=" + meeting.getEsMeetingId()
                    + "&loginHint=" + URLEncoder.encode(recipientEmail, StandardCharsets.UTF_8);
            String itemTitle = item.getTitle() != null ? item.getTitle() : "Agenda Item";
            String topicName = null;
            if (item.getEsTopicId() != null) {
                topicName = topicDao.findById(item.getEsTopicId())
                        .map(t -> t.getTopicName()).orElse(null);
            }
            String roleLabel = titleCase(role != null ? role.name() : "LEAD");
            String dateDisplay = meeting.getScheduledStart() != null
                    ? DISPLAY_DATE_FMT.format(meeting.getScheduledStart().toLocalDate())
                    : null;
            String subject = EmailTemplates.presenterInvitationSubject(itemTitle);
            String body = EmailTemplates.presenterInvitationBody(
                    recipientName, itemTitle, topicName,
                    meeting.getMeetingName(), dateDisplay, roleLabel, agendaLink);
            if (!subscriptionDao.hasGeneralUnsubscribed(recipientEmail.trim().toLowerCase())) {
                emailService.send(recipientEmail, subject, body);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to send presenter invitation email to " + recipientEmail, ex);
        }
    }

    private EsAgendaItemPresenter validatePresenterForMeeting(Long presenterId, EsMeeting meeting) {
        if (presenterId == null || meeting == null) {
            return null;
        }
        EsAgendaItemPresenter p = presenterDao.findById(presenterId).orElse(null);
        if (p == null) {
            return null;
        }
        EsMeetingAgendaItem item = agendaItemDao.findById(p.getEsMeetingAgendaItemId()).orElse(null);
        if (item == null) {
            return null;
        }
        return meeting.getEsMeetingId().equals(item.getEsMeetingId()) ? p : null;
    }

    // =========================================================================
    // Default agenda items
    // =========================================================================

    private void handleCopyAgendaItem(HttpServletRequest request, HttpServletResponse response,
            String contextPath, EsMeeting meeting, List<EsMeetingAgendaItem> items,
            User user, boolean editOverride) throws IOException {
        Long sourceItemId = parseId(trimToNull(request.getParameter("sourceItemId")));
        if (sourceItemId == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        EsMeetingAgendaItem src = agendaItemDao.findById(sourceItemId).orElse(null);
        if (src == null) {
            redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
            return;
        }
        // Verify the source item belongs to the same meeting series
        EsMeeting srcMeeting = meetingDao.findById(src.getEsMeetingId()).orElse(null);
        if (srcMeeting == null
                || !meeting.getEsTopicMeetingId().equals(srcMeeting.getEsTopicMeetingId())) {
            redirectBackWithError(response, contextPath, meeting.getEsMeetingId(), editOverride,
                    "Source item does not belong to this meeting series.");
            return;
        }
        // Compute insertion order — before Wrap Up if present (same logic as
        // addAgendaItem)
        int maxOrder = items.stream()
                .mapToInt(i -> i.getDisplayOrder() != null ? i.getDisplayOrder() : 0)
                .max().orElse(0);
        List<EsMeetingAgendaItem> visibleItems = items.stream()
                .filter(i -> i.getStatus() != AgendaItemStatus.CANCELLED)
                .collect(Collectors.toList());
        int newOrder = maxOrder + 10;
        if (!visibleItems.isEmpty()) {
            EsMeetingAgendaItem last = visibleItems.get(visibleItems.size() - 1);
            boolean isWrapUp = Integer.valueOf(5).equals(last.getTimeMinutes())
                    || "wrap up".equalsIgnoreCase(last.getTitle() != null ? last.getTitle().trim() : "");
            if (isWrapUp) {
                int wrapUpOrder = last.getDisplayOrder() != null ? last.getDisplayOrder() : maxOrder;
                if (visibleItems.size() > 1) {
                    EsMeetingAgendaItem prev = visibleItems.get(visibleItems.size() - 2);
                    int prevOrder = prev.getDisplayOrder() != null ? prev.getDisplayOrder() : 0;
                    newOrder = wrapUpOrder - prevOrder > 1
                            ? prevOrder + (wrapUpOrder - prevOrder) / 2
                            : wrapUpOrder - 1;
                } else {
                    newOrder = wrapUpOrder - 5;
                }
            }
        }
        // Create the new agenda item
        EsMeetingAgendaItem newItem = new EsMeetingAgendaItem();
        newItem.setEsMeetingId(meeting.getEsMeetingId());
        newItem.setTitle(src.getTitle());
        newItem.setAgendaMarkdown(src.getAgendaMarkdown());
        newItem.setTimeMinutes(src.getTimeMinutes());
        newItem.setEsTopicId(src.getEsTopicId());
        newItem.setDisplayOrder(newOrder);
        newItem.setStatus(AgendaItemStatus.DRAFT);
        if (user.getUserId() != null) {
            newItem.setProposedByUserId(user.getUserId());
        }
        EsMeetingAgendaItem saved = agendaItemDao.save(newItem);
        // Copy active presenters, applying ACCEPTED for self and INVITED for others
        if (saved != null && saved.getEsMeetingAgendaItemId() != null) {
            String myEmailNorm = user.getEmail() != null ? user.getEmail().trim().toLowerCase() : null;
            for (EsAgendaItemPresenter srcP : presenterDao.findByAgendaItemId(sourceItemId)) {
                if (srcP.getStatus() == EsAgendaItemPresenter.PresenterStatus.REMOVED
                        || srcP.getStatus() == EsAgendaItemPresenter.PresenterStatus.DECLINED) {
                    continue;
                }
                boolean isSelf = myEmailNorm != null
                        && myEmailNorm.equals(srcP.getEmailNormalized());
                EsAgendaItemPresenter np = new EsAgendaItemPresenter();
                np.setEsMeetingAgendaItemId(saved.getEsMeetingAgendaItemId());
                if (srcP.getUserId() != null) {
                    np.setUserId(srcP.getUserId());
                }
                np.setEmail(srcP.getEmail());
                np.setEmailNormalized(srcP.getEmailNormalized());
                if (srcP.getDisplayName() != null) {
                    np.setDisplayName(srcP.getDisplayName());
                }
                np.setPresenterRole(srcP.getPresenterRole());
                np.setStatus(isSelf
                        ? EsAgendaItemPresenter.PresenterStatus.ACCEPTED
                        : EsAgendaItemPresenter.PresenterStatus.INVITED);
                presenterDao.save(np);
            }
        }
        redirectBack(response, contextPath, meeting.getEsMeetingId(), editOverride);
    }

    private void createDefaultAgendaItems(EsMeeting meeting) {
        EsMeetingAgendaItem welcome = new EsMeetingAgendaItem();
        welcome.setEsMeetingId(meeting.getEsMeetingId());
        welcome.setTitle("Welcome and Introductions");
        welcome.setAgendaMarkdown("Welcome\nIntroductions");
        welcome.setTimeMinutes(5);
        welcome.setDisplayOrder(10);
        welcome.setStatus(AgendaItemStatus.DRAFT);
        agendaItemDao.save(welcome);

        EsMeetingAgendaItem wrapUp = new EsMeetingAgendaItem();
        wrapUp.setEsMeetingId(meeting.getEsMeetingId());
        wrapUp.setTitle("Wrap Up");
        wrapUp.setAgendaMarkdown("Next steps\nNext topics");
        wrapUp.setTimeMinutes(5);
        wrapUp.setDisplayOrder(20);
        wrapUp.setStatus(AgendaItemStatus.DRAFT);
        agendaItemDao.save(wrapUp);
    }

    // =========================================================================
    // Next meeting lookup
    // =========================================================================

    private EsMeeting findNextMeeting(EsMeeting current) {
        if (current.getScheduledStart() == null) {
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

    // =========================================================================
    // Redirect helpers
    // =========================================================================

    private void redirectBack(HttpServletResponse response, String contextPath, Long meetingId,
            boolean editOverride) throws IOException {
        String url = contextPath + "/es/agenda?meetingId=" + meetingId + "&saved=1";
        if (editOverride) {
            url += "&edit=true";
        }
        response.sendRedirect(url);
    }

    private void redirectBackWithSuggest(HttpServletResponse response, String contextPath,
            Long meetingId, boolean editOverride, String suggestType) throws IOException {
        String url = contextPath + "/es/agenda?meetingId=" + meetingId + "&saved=1";
        if (editOverride) {
            url += "&edit=true";
        }
        if (suggestType != null) {
            url += "&suggest=" + URLEncoder.encode(suggestType, StandardCharsets.UTF_8);
        }
        response.sendRedirect(url);
    }

    private String buildSuggestBanner(String contextPath, Long meetingId, String suggestType) {
        if (suggestType == null || meetingId == null) {
            return null;
        }
        String link = contextPath + "/es/meeting-communication?meetingId=" + meetingId
                + "&suggestType=" + URLEncoder.encode(suggestType, StandardCharsets.UTF_8);
        String label = switch (suggestType) {
            case "PROPOSED_AGENDA" -> "Send Proposed Agenda communication";
            case "FINAL_AGENDA" -> "Send Final Agenda communication";
            case "CANCELLED" -> "Send Cancellation notice to attendees";
            default -> null;
        };
        if (label == null)
            return null;
        return "<a href=\"" + link + "\">" + label + "</a>";
    }

    private void redirectBackWithError(HttpServletResponse response, String contextPath, Long meetingId,
            boolean editOverride, String errorMsg) throws IOException {
        String url = contextPath + "/es/agenda?meetingId=" + meetingId
                + "&err=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8);
        if (editOverride) {
            url += "&edit=true";
        }
        response.sendRedirect(url);
    }

    // =========================================================================
    // Page rendering
    // =========================================================================

    private void renderPage(HttpServletResponse response, String contextPath, User user,
            EsMeeting meeting, List<EsMeetingAgendaItem> items,
            Map<Long, List<EsAgendaItemPresenter>> presentersByItem,
            Map<Long, User> presenterUsers,
            boolean isEditor, boolean canEdit, boolean editOverride,
            EsMeeting nextMeeting, String savedMsg, String errorMsg,
            String loginHintMismatch, String suggestBanner,
            String attendeeEmailForInterest, Map<Long, EsSubscription> subsByTopicId,
            List<Long> agendaTopicIds,
            boolean isWithinAttendanceWindow, List<EsMeetingAttendance> meetingAttendees) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        String effectiveTz = resolveEffectiveTz(user, meeting);
        ZoneId meetingZone = safeZoneId(meeting.getTimezoneId(), effectiveTz);
        ZoneId viewerZone = safeZoneId(effectiveTz, "America/New_York");

        // Convert meeting times to viewer's timezone for display
        ZonedDateTime displayStart = meeting.getScheduledStart() != null
                ? ZonedDateTime.of(meeting.getScheduledStart(), meetingZone).withZoneSameInstant(viewerZone)
                : null;
        ZonedDateTime displayEnd = meeting.getScheduledEnd() != null
                ? ZonedDateTime.of(meeting.getScheduledEnd(), meetingZone).withZoneSameInstant(viewerZone)
                : null;

        String dateDisplay = displayStart != null ? DISPLAY_DATE_FMT.format(displayStart) : "";
        String startTimeDisplay = displayStart != null ? DISPLAY_TIME_FMT.format(displayStart) : "";
        String endTimeDisplay = displayEnd != null ? DISPLAY_TIME_FMT.format(displayEnd) : "";
        String tzAbbr = displayStart != null ? displayStart.getZone().getId() : effectiveTz;
        String meetingTzDisplay = orEmpty(meeting.getTimezoneId()).isEmpty() ? "America/New_York"
                : meeting.getTimezoneId();

        boolean isCancelled = meeting.getStatus() == MeetingStatus.CANCELLED;
        String selfUrl = contextPath + "/es/agenda?meetingId=" + meeting.getEsMeetingId();
        String editUrl = selfUrl + "&edit=true";

        EsTopicMeeting topicMeeting = meeting.getEsTopicMeetingId() != null
                ? topicMeetingDao.findById(meeting.getEsTopicMeetingId()).orElse(null)
                : null;
        String seriesName = topicMeeting != null && topicMeeting.getMeetingName() != null
                ? topicMeeting.getMeetingName()
                : null;

        // Duration warning calculation
        int agendaMinutes = items.stream()
                .filter(i -> i.getStatus() != AgendaItemStatus.CANCELLED
                        && i.getStatus() != AgendaItemStatus.POSTPONED)
                .mapToInt(i -> i.getTimeMinutes() != null ? i.getTimeMinutes() : 0)
                .sum();
        Long meetingDurationMinutes = null;
        if (meeting.getScheduledStart() != null && meeting.getScheduledEnd() != null) {
            meetingDurationMinutes = ChronoUnit.MINUTES.between(meeting.getScheduledStart(), meeting.getScheduledEnd());
        }

        // Topics: needed for linked-topic display (all users) and autocomplete
        // (editors)
        List<EsTopic> allTopics = topicDao.findAllOrderByTopicName();
        Map<Long, EsTopic> topicById = allTopics.stream()
                .collect(Collectors.toMap(EsTopic::getEsTopicId, t -> t));

        // Champions and all users: only needed when canEdit (for add-presenter
        // quick-pick)
        Map<Long, List<EsSubscription>> championsByTopic = new LinkedHashMap<>();
        Map<Long, User> championUserMap = new LinkedHashMap<>();
        List<User> allUsers = List.of();
        if (canEdit) {
            Set<Long> linkedTopicIds = items.stream()
                    .filter(i -> i.getEsTopicId() != null)
                    .map(EsMeetingAgendaItem::getEsTopicId)
                    .collect(Collectors.toSet());
            for (Long tid : linkedTopicIds) {
                List<EsSubscription> champs = subscriptionDao.findChampionsByTopicId(tid);
                championsByTopic.put(tid, champs);
                List<Long> champUserIds = champs.stream()
                        .filter(c -> c.getUserId() != null)
                        .map(EsSubscription::getUserId)
                        .collect(Collectors.toList());
                if (!champUserIds.isEmpty()) {
                    for (User cu : userDao.findByIds(champUserIds)) {
                        championUserMap.put(cu.getUserId(), cu);
                    }
                }
            }
            allUsers = userDao.findAllOrderByName();
        }

        // Previous meeting items for "open items" and "copy from previous" panels
        // (canEdit only)
        List<EsMeetingAgendaItem> openItems = List.of();
        Map<Long, EsMeeting> openMeetingById = new LinkedHashMap<>();
        List<EsMeeting> copyMeetings = List.of();
        Map<Long, List<EsMeetingAgendaItem>> copyItemsByMeeting = new LinkedHashMap<>();
        Map<Long, List<EsAgendaItemPresenter>> prevPresentersByItem = new LinkedHashMap<>();
        if (canEdit && meeting.getEsTopicMeetingId() != null) {
            // Fetch ALL previous meetings in this series (all statuses, including
            // DRAFT/PROPOSED)
            List<EsMeeting> allPrevMeetings = meetingDao.findAllPreviousByTopicMeeting(
                    meeting.getEsTopicMeetingId(), meeting.getEsMeetingId());
            if (!allPrevMeetings.isEmpty()) {
                List<Long> allPrevIds = allPrevMeetings.stream()
                        .map(EsMeeting::getEsMeetingId).collect(Collectors.toList());
                List<EsMeetingAgendaItem> allPrevItems = agendaItemDao.findByMeetingIds(allPrevIds);

                // Group items by meeting (DAO returns ordered by meetingId, displayOrder)
                Map<Long, List<EsMeetingAgendaItem>> itemsByMtg = new LinkedHashMap<>();
                for (EsMeeting pm : allPrevMeetings) {
                    itemsByMtg.put(pm.getEsMeetingId(), new ArrayList<>());
                }
                for (EsMeetingAgendaItem pi : allPrevItems) {
                    List<EsMeetingAgendaItem> bucket = itemsByMtg.get(pi.getEsMeetingId());
                    if (bucket != null)
                        bucket.add(pi);
                }

                // Build current meeting's occupied topic IDs and titles (suppress from open
                // items)
                Set<Long> currentTopicIds = items.stream()
                        .filter(i -> i.getStatus() != AgendaItemStatus.CANCELLED && i.getEsTopicId() != null)
                        .map(EsMeetingAgendaItem::getEsTopicId)
                        .collect(Collectors.toSet());
                Set<String> currentTitles = items.stream()
                        .filter(i -> i.getStatus() != AgendaItemStatus.CANCELLED
                                && i.getEsTopicId() == null
                                && i.getTitle() != null && !i.getTitle().isBlank())
                        .map(i -> i.getTitle().trim().toLowerCase())
                        .collect(Collectors.toSet());

                // ── Open Items ──
                // Iterate most-recent-first; first (most recent) occurrence of each
                // topic/title wins. Surface only if that occurrence is POSTPONED,
                // NOT_COVERED, or NEEDS_REVISION. Suppress if already on this meeting.
                Set<Long> seenTopicIds = new HashSet<>();
                Set<String> seenTitles = new HashSet<>();
                List<EsMeetingAgendaItem> open = new ArrayList<>();
                Map<Long, EsMeeting> openMtgMap = new LinkedHashMap<>();
                for (EsMeeting pm : allPrevMeetings) {
                    for (EsMeetingAgendaItem mi : itemsByMtg.getOrDefault(pm.getEsMeetingId(), List.of())) {
                        if (mi.getStatus() == AgendaItemStatus.CANCELLED)
                            continue;
                        Long topicId = mi.getEsTopicId();
                        String normTitle = topicId == null
                                ? (mi.getTitle() != null ? mi.getTitle().trim().toLowerCase() : "")
                                : null;
                        // Dedup: first occurrence (most recent) wins
                        if (topicId != null) {
                            if (!seenTopicIds.add(topicId))
                                continue;
                        } else {
                            if (normTitle == null || normTitle.isEmpty())
                                continue;
                            if (!seenTitles.add(normTitle))
                                continue;
                        }
                        // Only surface actionable statuses
                        if (mi.getStatus() != AgendaItemStatus.POSTPONED
                                && mi.getStatus() != AgendaItemStatus.NOT_COVERED
                                && mi.getStatus() != AgendaItemStatus.NEEDS_REVISION)
                            continue;
                        // Suppress if already on current meeting's agenda
                        if (topicId != null && currentTopicIds.contains(topicId))
                            continue;
                        if (topicId == null && currentTitles.contains(normTitle))
                            continue;
                        open.add(mi);
                        openMtgMap.put(pm.getEsMeetingId(), pm);
                    }
                }
                // Sort: POSTPONED first, then NOT_COVERED, then NEEDS_REVISION
                open.sort((a, b) -> {
                    int rankA = a.getStatus() == AgendaItemStatus.POSTPONED ? 0
                            : a.getStatus() == AgendaItemStatus.NOT_COVERED ? 1 : 2;
                    int rankB = b.getStatus() == AgendaItemStatus.POSTPONED ? 0
                            : b.getStatus() == AgendaItemStatus.NOT_COVERED ? 1 : 2;
                    return Integer.compare(rankA, rankB);
                });
                openItems = open;
                openMeetingById = openMtgMap;

                // ── Copy Section: last 2 previous meetings ──
                copyMeetings = allPrevMeetings.size() > 2
                        ? allPrevMeetings.subList(0, 2)
                        : new ArrayList<>(allPrevMeetings);
                for (EsMeeting cm : copyMeetings) {
                    List<EsMeetingAgendaItem> cmItems = itemsByMtg
                            .getOrDefault(cm.getEsMeetingId(), List.of()).stream()
                            .filter(i -> i.getStatus() != AgendaItemStatus.CANCELLED)
                            .collect(Collectors.toList());
                    copyItemsByMeeting.put(cm.getEsMeetingId(), cmItems);
                }

                // ── Batch-load presenters for all candidate items ──
                List<Long> allCandidateIds = new ArrayList<>();
                for (EsMeetingAgendaItem oi : openItems)
                    allCandidateIds.add(oi.getEsMeetingAgendaItemId());
                for (EsMeeting cm : copyMeetings) {
                    for (EsMeetingAgendaItem ci : copyItemsByMeeting.getOrDefault(cm.getEsMeetingId(), List.of())) {
                        allCandidateIds.add(ci.getEsMeetingAgendaItemId());
                    }
                }
                if (!allCandidateIds.isEmpty()) {
                    for (EsAgendaItemPresenter p : presenterDao.findByAgendaItemIds(allCandidateIds)) {
                        prevPresentersByItem
                                .computeIfAbsent(p.getEsMeetingAgendaItemId(), k -> new ArrayList<>())
                                .add(p);
                    }
                }
            }
        }

        // Compute current user's INVITED presenter records for the response banner
        Map<Long, EsMeetingAgendaItem> itemById = new LinkedHashMap<>();
        for (EsMeetingAgendaItem i : items) {
            itemById.put(i.getEsMeetingAgendaItemId(), i);
        }
        List<EsAgendaItemPresenter> myInvitations = new ArrayList<>();
        if (user != null && user.getUserId() != null) {
            for (List<EsAgendaItemPresenter> ps : presentersByItem.values()) {
                for (EsAgendaItemPresenter inv : ps) {
                    if (inv.getStatus() == EsAgendaItemPresenter.PresenterStatus.INVITED
                            && user.getUserId().equals(inv.getUserId())) {
                        myInvitations.add(inv);
                    }
                }
            }
        }

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>" + escapeHtml(orEmpty(meeting.getMeetingName())) + " — Agenda</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("  <style>");
            renderAgendaStyles(out);
            out.println("  </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<main class=\"agenda-page\">");

            // Cancellation banner
            if (isCancelled) {
                out.println("  <div class=\"agenda-cancelled-banner\">");
                out.println("    <strong>CANCELLED</strong>");
                if (meeting.getCancellationReason() != null && !meeting.getCancellationReason().isBlank()) {
                    out.println("    &mdash; " + escapeHtml(meeting.getCancellationReason()));
                }
                out.println("  </div>");
            }

            // Messages
            if (savedMsg != null) {
                out.println("  <div class=\"agenda-msg-success no-print\">" + escapeHtml(savedMsg) + "</div>");
            }
            if (suggestBanner != null) {
                out.println(
                        "  <div class=\"agenda-msg-success no-print\" style=\"background:#cfe2ff;border-color:#9ec5fe\">"
                                + suggestBanner + "</div>");
            }
            if (errorMsg != null) {
                out.println("  <div class=\"agenda-msg-error no-print\">" + escapeHtml(errorMsg) + "</div>");
            }
            if (loginHintMismatch != null) {
                out.println("  <div class=\"agenda-msg-login-hint no-print\">");
                out.println("    <strong>Wrong account?</strong> This invitation was sent to"
                        + " <strong>" + escapeHtml(loginHintMismatch) + "</strong>."
                        + " You are currently signed in as <strong>"
                        + escapeHtml(user.getEmailNormalized()) + "</strong>."
                        + " Sign in with the correct account to respond to this invitation.");
                out.println("  </div>");
            }

            // --- YOUR AGENDA ITEMS BANNER ---
            if (!myInvitations.isEmpty()) {
                out.println("  <div class=\"my-invitations-banner no-print\">");
                out.println("    <div class=\"my-inv-heading\">&#128203; Your Agenda Items</div>");
                out.println(
                        "    <div class=\"my-inv-subtext\">You have been added as a presenter. Please confirm or decline each item below.</div>");
                for (EsAgendaItemPresenter inv : myInvitations) {
                    EsMeetingAgendaItem invItem = itemById.get(inv.getEsMeetingAgendaItemId());
                    if (invItem == null)
                        continue;
                    EsTopic invTopic = invItem.getEsTopicId() != null ? topicById.get(invItem.getEsTopicId()) : null;
                    String invTitle = invItem.getTitle() != null ? invItem.getTitle() : "Agenda Item";
                    String invRoleLabel = titleCase(
                            inv.getPresenterRole() != null ? inv.getPresenterRole().name() : "LEAD");
                    out.println("    <div class=\"my-inv-card\">");
                    out.println("      <div class=\"my-inv-title\">");
                    if (invTopic != null) {
                        out.println("        <span class=\"my-inv-topic\">" + escapeHtml(invTopic.getTopicName())
                                + "</span>");
                        out.println("        <span class=\"my-inv-sep\">&rsaquo;</span>");
                    }
                    out.println("        " + escapeHtml(invTitle));
                    out.println("      </div>");
                    out.println("      <div class=\"my-inv-meta\">Invited as: <strong>" + escapeHtml(invRoleLabel)
                            + "</strong></div>");
                    out.println("      <div class=\"my-inv-actions\">");
                    // Accept form
                    out.println("        <form method=\"post\" action=\"" + contextPath
                            + "/es/agenda\" class=\"my-inv-form\">");
                    out.println("          <input type=\"hidden\" name=\"meetingId\" value=\""
                            + meeting.getEsMeetingId() + "\">");
                    out.println("          <input type=\"hidden\" name=\"action\" value=\"presenterAccept\">");
                    out.println("          <input type=\"hidden\" name=\"presenterId\" value=\""
                            + inv.getEsAgendaItemPresenterId() + "\">");
                    if (editOverride)
                        out.println("          <input type=\"hidden\" name=\"edit\" value=\"true\">");
                    out.println("          <label class=\"my-inv-role-label\">Role:");
                    out.println("          <select name=\"presenterRole\" class=\"my-inv-role-select\">");
                    for (EsAgendaItemPresenter.PresenterRole r : EsAgendaItemPresenter.PresenterRole.values()) {
                        boolean sel = r == inv.getPresenterRole();
                        out.println("            <option value=\"" + r.name() + "\"" + (sel ? " selected" : "") + ">"
                                + escapeHtml(titleCase(r.name())) + "</option>");
                    }
                    out.println("          </select></label>");
                    out.println("          <button type=\"submit\" class=\"inv-btn-accept\">&#10003; Accept</button>");
                    out.println("        </form>");
                    // Decline form
                    out.println("        <form method=\"post\" action=\"" + contextPath
                            + "/es/agenda\" class=\"my-inv-form\">");
                    out.println("          <input type=\"hidden\" name=\"meetingId\" value=\""
                            + meeting.getEsMeetingId() + "\">");
                    out.println("          <input type=\"hidden\" name=\"action\" value=\"presenterDecline\">");
                    out.println("          <input type=\"hidden\" name=\"presenterId\" value=\""
                            + inv.getEsAgendaItemPresenterId() + "\">");
                    if (editOverride)
                        out.println("          <input type=\"hidden\" name=\"edit\" value=\"true\">");
                    out.println(
                            "          <input type=\"text\" name=\"responseNote\" placeholder=\"Reason (optional)\" class=\"my-inv-note\">");
                    out.println(
                            "          <button type=\"submit\" class=\"inv-btn-decline\">&#10007; Decline</button>");
                    out.println("        </form>");
                    out.println("      </div>");
                    out.println("    </div>");
                }
                out.println("  </div>");
            }

            // --- HEADER ---
            out.println("  <div class=\"agenda-header\">");
            out.println("    <div class=\"agenda-logo-cell\">");
            out.println("      <img src=\"" + contextPath
                    + "/image/aira_logo.webp\" alt=\"AIRA\" class=\"agenda-logo\" />");
            out.println("    </div>");
            out.println("    <div class=\"agenda-title-cell\">");
            if (meeting.getStatus() == MeetingStatus.DRAFT || meeting.getStatus() == MeetingStatus.PROPOSED) {
                out.println("      <h1 class=\"agenda-title\"><span class=\"agenda-draft-label\">"
                        + meeting.getStatus().name() + "</span> AGENDA</h1>");
            } else {
                out.println("      <h1 class=\"agenda-title\">AGENDA</h1>");
            }
            out.println("    </div>");
            out.println("  </div>");

            // --- METADATA BLOCK ---
            out.println("  <div class=\"agenda-meta panel\">");

            // Meeting name (editable)
            if (canEdit) {
                out.println("    <div class=\"agenda-meta-row\">");
                out.println("      <span class=\"agenda-meta-label\">Meeting:</span>");
                out.println(
                        "      <span id=\"meeting-name-display\" class=\"agenda-meta-value click-to-edit\" onclick=\"esShowEdit('meeting-name')\" title=\"Click to edit\">"
                                + escapeHtml(orEmpty(meeting.getMeetingName())) + "</span>");
                out.println(
                        "      <form id=\"meeting-name-form\" class=\"agenda-inline-form no-print\" method=\"post\" action=\""
                                + contextPath
                                + "/es/agenda\" style=\"display:none\">");
                out.println("        <input type=\"hidden\" name=\"meetingId\" value=\"" + meeting.getEsMeetingId()
                        + "\">");
                out.println("        <input type=\"hidden\" name=\"action\" value=\"updateMeetingName\">");
                if (editOverride)
                    out.println("        <input type=\"hidden\" name=\"edit\" value=\"true\">");
                out.println("        <input type=\"text\" name=\"name\" value=\""
                        + escapeHtml(orEmpty(meeting.getMeetingName())) + "\" required size=\"40\">");
                out.println("        <button type=\"submit\">Save</button>");
                out.println("        <button type=\"button\" onclick=\"esHideEdit('meeting-name')\">Cancel</button>");
                out.println("      </form>");
                out.println("    </div>");
            } else {
                out.println("    <div class=\"agenda-meta-row\">");
                out.println("      <span class=\"agenda-meta-label\">Meeting:</span>");
                out.println("      <span class=\"agenda-meta-value\">" + escapeHtml(orEmpty(meeting.getMeetingName()))
                        + "</span>");
                out.println("    </div>");
            }

            // Date (editable)
            if (canEdit) {
                String dateInputVal = meeting.getScheduledStart() != null
                        ? INPUT_DATE_FMT.format(meeting.getScheduledStart().toLocalDate())
                        : "";
                out.println("    <div class=\"agenda-meta-row\">");
                out.println("      <span class=\"agenda-meta-label\">Date:</span>");
                out.println(
                        "      <span id=\"meeting-date-display\" class=\"agenda-meta-value click-to-edit\" onclick=\"esShowEdit('meeting-date')\" title=\"Click to edit\">"
                                + escapeHtml(dateDisplay) + "</span>");
                out.println(
                        "      <form id=\"meeting-date-form\" class=\"agenda-inline-form no-print\" method=\"post\" action=\""
                                + contextPath
                                + "/es/agenda\" style=\"display:none\">");
                out.println("        <input type=\"hidden\" name=\"meetingId\" value=\"" + meeting.getEsMeetingId()
                        + "\">");
                out.println("        <input type=\"hidden\" name=\"action\" value=\"updateMeetingDate\">");
                if (editOverride)
                    out.println("        <input type=\"hidden\" name=\"edit\" value=\"true\">");
                out.println("        <span class=\"agenda-tz-note\">Date in meeting timezone: "
                        + escapeHtml(meetingTzDisplay) + "</span>");
                out.println("        <input type=\"date\" name=\"date\" value=\"" + escapeHtml(dateInputVal)
                        + "\" required>");
                out.println("        <button type=\"submit\">Save</button>");
                out.println("        <button type=\"button\" onclick=\"esHideEdit('meeting-date')\">Cancel</button>");
                out.println("      </form>");
                out.println("    </div>");
            } else {
                out.println("    <div class=\"agenda-meta-row\">");
                out.println("      <span class=\"agenda-meta-label\">Date:</span>");
                out.println("      <span class=\"agenda-meta-value\">" + escapeHtml(dateDisplay) + "</span>");
                out.println("    </div>");
            }

            // Time + Timezone (combined click-to-edit)
            String timeDisplay = startTimeDisplay.isEmpty() ? ""
                    : (endTimeDisplay.isEmpty() ? startTimeDisplay + " " + tzAbbr
                            : startTimeDisplay + " – " + endTimeDisplay + " " + tzAbbr);
            {
                String timeClickTitle = canEdit ? "Click to edit time or timezones" : "Click to set My Timezone";
                out.println("    <div class=\"agenda-meta-row\">");
                out.println("      <span class=\"agenda-meta-label\">Time:</span>");
                out.println("      <span id=\"meeting-time-display\" class=\"agenda-meta-value click-to-edit\""
                        + " onclick=\"esShowEdit('meeting-time')\" title=\"" + escapeHtml(timeClickTitle) + "\">"
                        + escapeHtml(timeDisplay.isEmpty() ? "Not set" : timeDisplay) + "</span>");
                if (canEdit) {
                    String startInput = meeting.getScheduledStart() != null
                            ? INPUT_TIME_FMT.format(meeting.getScheduledStart().toLocalTime())
                            : "";
                    String endInput = meeting.getScheduledEnd() != null
                            ? INPUT_TIME_FMT.format(meeting.getScheduledEnd().toLocalTime())
                            : "";
                    out.println("      <form id=\"meeting-time-form\" class=\"agenda-inline-form no-print\""
                            + " method=\"post\" action=\"" + contextPath + "/es/agenda\" style=\"display:none\">");
                    out.println("        <input type=\"hidden\" name=\"meetingId\" value=\""
                            + meeting.getEsMeetingId() + "\">");
                    out.println(
                            "        <input type=\"hidden\" name=\"action\" value=\"updateMeetingTimeAndTimezone\">");
                    if (editOverride)
                        out.println("        <input type=\"hidden\" name=\"edit\" value=\"true\">");
                    out.println("        <span class=\"agenda-tz-note\">Times are in meeting timezone: "
                            + escapeHtml(meetingTzDisplay) + "</span>");
                    out.println("        <label>Start <input type=\"time\" name=\"startTime\" value=\""
                            + escapeHtml(startInput) + "\" required></label>");
                    out.println("        <label>End <input type=\"time\" name=\"endTime\" value=\""
                            + escapeHtml(endInput) + "\"></label>");
                    out.println("        <label>Meeting Timezone "
                            + renderTimezoneSelect("meetingTimezone", meetingTzDisplay) + "</label>");
                    out.println("        <label>My Timezone "
                            + renderTimezoneSelect("viewerTimezone", effectiveTz) + "</label>");
                    out.println("        <button type=\"submit\">Save</button>");
                    out.println(
                            "        <button type=\"button\" onclick=\"esHideEdit('meeting-time')\">Cancel</button>");
                    out.println("      </form>");
                } else {
                    out.println("      <form id=\"meeting-time-form\" class=\"agenda-inline-form no-print\""
                            + " method=\"post\" action=\"" + contextPath + "/es/agenda\" style=\"display:none\">");
                    out.println("        <input type=\"hidden\" name=\"meetingId\" value=\""
                            + meeting.getEsMeetingId() + "\">");
                    out.println("        <input type=\"hidden\" name=\"action\" value=\"updateViewerTimezone\">");
                    if (editOverride)
                        out.println("        <input type=\"hidden\" name=\"edit\" value=\"true\">");
                    out.println("        <label>My Timezone "
                            + renderTimezoneSelect("timezoneId", effectiveTz) + "</label>");
                    out.println("        <button type=\"submit\">Save</button>");
                    out.println(
                            "        <button type=\"button\" onclick=\"esHideEdit('meeting-time')\">Cancel</button>");
                    out.println("      </form>");
                }
                out.println("    </div>");
            }

            // Meeting status (click-to-edit for editors with available transitions)
            List<MeetingStatus> statusTransitions = (isEditor && !isCancelled)
                    ? validMeetingTransitions(meeting.getStatus())
                    : List.of();
            boolean finalizeBlocked = statusTransitions.contains(MeetingStatus.FINALIZED)
                    && items.stream()
                            .anyMatch(it -> it.getStatus() == AgendaItemStatus.DRAFT
                                    || it.getStatus() == AgendaItemStatus.PROPOSED);
            boolean completionAllowed = !statusTransitions.contains(MeetingStatus.COMPLETED)
                    || isMeetingStarted(meeting);
            out.println("    <div class=\"agenda-meta-row no-print\">");
            out.println("      <span class=\"agenda-meta-label\">Status:</span>");
            if (!statusTransitions.isEmpty()) {
                out.println("      <span id=\"meeting-status-display\" class=\"agenda-status-badge agenda-status-"
                        + meeting.getStatus().name().toLowerCase()
                        + " click-to-edit\" onclick=\"esShowEdit('meeting-status')\" title=\"Click to change status\">"
                        + escapeHtml(meeting.getStatus().name().substring(0, 1)
                                + meeting.getStatus().name().substring(1).toLowerCase())
                        + "</span>");
                out.println(
                        "      <div id=\"meeting-status-form\" class=\"agenda-inline-form no-print\" style=\"display:none\">");
                for (MeetingStatus ts : statusTransitions) {
                    out.println("        <form method=\"post\" action=\"" + contextPath
                            + "/es/agenda\" style=\"display:contents\">");
                    out.println("          <input type=\"hidden\" name=\"meetingId\" value=\""
                            + meeting.getEsMeetingId() + "\">");
                    out.println("          <input type=\"hidden\" name=\"action\" value=\"updateMeetingStatus\">");
                    out.println("          <input type=\"hidden\" name=\"targetStatus\" value=\"" + ts.name() + "\">");
                    if (editOverride)
                        out.println("          <input type=\"hidden\" name=\"edit\" value=\"true\">");
                    if (ts == MeetingStatus.FINALIZED) {
                        if (finalizeBlocked) {
                            out.println(
                                    "          <button type=\"submit\" disabled title=\"All active items must be ACCEPTED first\">Finalize</button>");
                        } else {
                            out.println(
                                    "          <button type=\"submit\" onclick=\"return confirm('Finalize this meeting?')\">Finalize</button>");
                        }
                    } else if (ts == MeetingStatus.COMPLETED) {
                        if (!completionAllowed) {
                            out.println(
                                    "          <button type=\"submit\" disabled title=\"Meeting has not started yet\">Complete</button>");
                        } else {
                            out.println(
                                    "          <button type=\"submit\" onclick=\"return confirm('Mark this meeting as complete?')\">Complete</button>");
                        }
                    } else if (ts == MeetingStatus.CANCELLED) {
                        out.println(
                                "          <input type=\"text\" name=\"cancellationReason\" placeholder=\"Cancellation reason (optional)\" size=\"20\">");
                        out.println(
                                "          <button type=\"submit\" onclick=\"return confirm('Cancel this meeting?')\">Cancel Meeting</button>");
                    } else {
                        out.println("          <button type=\"submit\">"
                                + escapeHtml(ts.name().substring(0, 1) + ts.name().substring(1).toLowerCase())
                                + "</button>");
                    }
                    out.println("        </form>");
                }
                if (finalizeBlocked) {
                    out.println(
                            "        <span class=\"agenda-tz-note\">All active items must be accepted before finalizing.</span>");
                }
                out.println("        <button type=\"button\" onclick=\"esHideEdit('meeting-status')\">Close</button>");
                out.println("      </div>");
            } else {
                out.println("      <span class=\"agenda-status-badge agenda-status-"
                        + meeting.getStatus().name().toLowerCase() + "\">"
                        + escapeHtml(meeting.getStatus().name().substring(0, 1)
                                + meeting.getStatus().name().substring(1).toLowerCase())
                        + "</span>");
            }
            out.println("    </div>");

            // Enable editing link for FINALIZED
            if (isEditor && meeting.getStatus() == MeetingStatus.FINALIZED && !canEdit) {
                out.println("    <div class=\"agenda-meta-row no-print\">");
                out.println("      <a href=\"" + escapeHtml(editUrl)
                        + "\" class=\"agenda-edit-enable-link\">Enable editing</a>");
                out.println("    </div>");
            }

            out.println("  </div>"); // end agenda-meta

            // --- DESCRIPTION / MEETING INFORMATION ---
            boolean hasDescription = meeting.getMeetingDescription() != null
                    && !meeting.getMeetingDescription().isBlank();
            boolean hasOnlineMeetingUrl = meeting.getOnlineMeetingUrl() != null
                    && !meeting.getOnlineMeetingUrl().isBlank();
            boolean hasOnlineMeetingDetails = meeting.getOnlineMeetingDetails() != null
                    && !meeting.getOnlineMeetingDetails().isBlank();
            if (hasDescription || canEdit || hasOnlineMeetingUrl) {
                out.println("  <div class=\"agenda-description panel\">");
                out.println("    <h3 class=\"agenda-section-heading\">Meeting Information</h3>");
                if (hasDescription) {
                    if (canEdit) {
                        out.println(
                                "    <div id=\"description-display\" class=\"agenda-description-text click-to-edit\" onclick=\"esShowEdit('description')\" title=\"Click to edit\">"
                                        + renderPlainText(meeting.getMeetingDescription()) + "</div>");
                    } else {
                        out.println("    <div class=\"agenda-description-text\">"
                                + renderPlainText(meeting.getMeetingDescription()) + "</div>");
                    }
                } else {
                    out.println(
                            "    <span id=\"description-display\" class=\"agenda-muted click-to-edit\" onclick=\"esShowEdit('description')\" title=\"Click to add\">No meeting information provided. Click to add.</span>");
                }
                if (canEdit) {
                    out.println(
                            "    <div id=\"description-form\" class=\"no-print\" style=\"display:none;flex-direction:column;gap:0.4rem;margin-top:0.6rem\">");
                    out.println("      <form method=\"post\" action=\"" + contextPath + "/es/agenda\">");
                    out.println("        <input type=\"hidden\" name=\"meetingId\" value=\"" + meeting.getEsMeetingId()
                            + "\">");
                    out.println("        <input type=\"hidden\" name=\"action\" value=\"updateMeetingDescription\">");
                    if (editOverride)
                        out.println("        <input type=\"hidden\" name=\"edit\" value=\"true\">");
                    out.println(
                            "        <textarea name=\"description\" rows=\"4\" style=\"width:100%;box-sizing:border-box;border:1px solid #cbd5e1;border-radius:4px;font-size:0.9rem;padding:0.3rem 0.5rem\" placeholder=\"Join information, conference link, etc.\">"
                                    + escapeHtml(orEmpty(meeting.getMeetingDescription())) + "</textarea>");
                    out.println("        <div class=\"agenda-inline-form\" style=\"margin-top:0.4rem\">");
                    out.println("          <button type=\"submit\">Save</button>");
                    out.println(
                            "          <button type=\"button\" onclick=\"esHideEdit('description')\">Cancel</button>");
                    out.println("        </div>");
                    out.println("      </form>");
                    out.println("    </div>");
                }
                // --- ONLINE MEETING LINK ---
                if (canEdit) {
                    String displayUrl = hasOnlineMeetingUrl ? meeting.getOnlineMeetingUrl() : "Add Meeting Link";
                    String linkDisplayClass = hasOnlineMeetingUrl
                            ? "agenda-join-link-edit click-to-edit"
                            : "agenda-muted click-to-edit";
                    out.println("    <div class=\"agenda-online-meeting no-print\" style=\"margin-top:0.5rem\">");
                    out.println("      <span id=\"meeting-link-display\" class=\"" + linkDisplayClass
                            + "\" onclick=\"esShowEdit('meeting-link')\" title=\"Click to edit\">"
                            + escapeHtml(displayUrl) + "</span>");
                    out.println(
                            "      <div id=\"meeting-link-form\" style=\"display:none;flex-direction:column;gap:0.4rem;margin-top:0.4rem\">");
                    out.println("        <form method=\"post\" action=\"" + contextPath + "/es/agenda\">");
                    out.println("          <input type=\"hidden\" name=\"meetingId\" value=\""
                            + meeting.getEsMeetingId() + "\">");
                    out.println(
                            "          <input type=\"hidden\" name=\"action\" value=\"updateMeetingOnlineInfo\">");
                    if (editOverride)
                        out.println("          <input type=\"hidden\" name=\"edit\" value=\"true\">");
                    out.println(
                            "          <label style=\"font-size:0.85rem;font-weight:600;color:#475569\">Meeting URL</label>");
                    out.println(
                            "          <input type=\"text\" name=\"onlineMeetingUrl\" value=\""
                                    + escapeHtml(orEmpty(meeting.getOnlineMeetingUrl()))
                                    + "\" placeholder=\"https://zoom.us/j/...\" style=\"width:100%;box-sizing:border-box;border:1px solid #cbd5e1;border-radius:4px;font-size:0.9rem;padding:0.3rem 0.5rem\">");
                    out.println(
                            "          <label style=\"font-size:0.85rem;font-weight:600;color:#475569;margin-top:0.3rem\">Connection Details</label>");
                    out.println(
                            "          <textarea name=\"onlineMeetingDetails\" rows=\"5\" style=\"width:100%;box-sizing:border-box;border:1px solid #cbd5e1;border-radius:4px;font-size:0.9rem;padding:0.3rem 0.5rem\" placeholder=\"Dial-in numbers, passcode, etc.\">"
                                    + escapeHtml(orEmpty(meeting.getOnlineMeetingDetails())) + "</textarea>");
                    out.println("          <div class=\"agenda-inline-form\" style=\"margin-top:0.4rem\">");
                    out.println("            <button type=\"submit\">Save</button>");
                    out.println(
                            "            <button type=\"button\" onclick=\"esHideEdit('meeting-link')\">Cancel</button>");
                    out.println("          </div>");
                    out.println("        </form>");
                    out.println("      </div>");
                    out.println("    </div>");
                } else if (hasOnlineMeetingUrl) {
                    out.println("    <div class=\"agenda-online-meeting\" style=\"margin-top:0.5rem\">");
                    out.println("      <a href=\"" + escapeHtml(meeting.getOnlineMeetingUrl())
                            + "\" target=\"_blank\" rel=\"noopener noreferrer\" class=\"agenda-join-link\">Join Online &#8599;</a>"
                            + (hasOnlineMeetingDetails
                                    ? "&nbsp;&nbsp;&nbsp;<span id=\"join-details-toggle\" class=\"agenda-join-more\" onclick=\"esShowJoinDetails()\">More information&hellip;</span>"
                                    : ""));
                    if (hasOnlineMeetingDetails) {
                        out.println(
                                "      <div id=\"join-details-content\" class=\"agenda-join-details\" style=\"display:none\">");
                        out.println("        <div class=\"agenda-join-details-text\">"
                                + renderPlainText(meeting.getOnlineMeetingDetails()) + "</div>");
                        out.println(
                                "        <div style=\"margin-top:0.3rem\"><button type=\"button\" class=\"agenda-join-details-close\" onclick=\"esHideJoinDetails()\">Close</button></div>");
                        out.println("      </div>");
                    }
                    out.println("    </div>");
                }
                out.println("  </div>");
            }

            // --- ATTENDANCE SECTION ---
            if (isWithinAttendanceWindow) {
                boolean viewerIsRegistered = attendeeEmailForInterest != null
                        && meetingAttendees.stream()
                                .anyMatch(a -> attendeeEmailForInterest.equalsIgnoreCase(a.getEmailNormalized()));

                // Build the /attend/ URL using the topic code from the meeting series topic
                EsTopic meetingSeriesTopic = (topicMeeting != null && topicMeeting.getEsTopicId() != null)
                        ? topicById.get(topicMeeting.getEsTopicId())
                        : null;
                String attendUrl = null;
                if (meetingSeriesTopic != null && meetingSeriesTopic.getTopicCode() != null) {
                    attendUrl = contextPath + "/attend/"
                            + URLEncoder.encode(meetingSeriesTopic.getTopicCode(), StandardCharsets.UTF_8);
                    if (meeting.getMeetingKey() != null) {
                        attendUrl += "/" + URLEncoder.encode(meeting.getMeetingKey(), StandardCharsets.UTF_8);
                    }
                }

                out.println("  <div class=\"agenda-attendance panel\">");
                out.println("    <h3 class=\"agenda-section-heading\">Attendance</h3>");
                if (!viewerIsRegistered) {
                    out.println(
                            "    <p class=\"agenda-attendance-prompt\">Haven't signed in for this meeting yet?</p>");
                    if (attendUrl != null) {
                        out.println("    <p><a href=\"" + escapeHtml(attendUrl)
                                + "\" class=\"agenda-attend-link\">Sign in for this meeting &rarr;</a></p>");
                    }
                } else {
                    if (!meetingAttendees.isEmpty()) {
                        out.println("    <ul class=\"agenda-attendance-list\">");
                        for (EsMeetingAttendance a : meetingAttendees) {
                            String name = escapeHtml(a.getFirstName()
                                    + (a.getLastName() != null && !a.getLastName().isBlank()
                                            ? " " + a.getLastName()
                                            : ""));
                            String org = (a.getOrganization() != null && !a.getOrganization().isBlank())
                                    ? escapeHtml(a.getOrganization())
                                    : null;
                            out.println("      <li class=\"agenda-attendee-row\">"
                                    + name
                                    + (org != null
                                            ? " &mdash; <span class=\"agenda-attendee-org\">" + org + "</span>"
                                            : "")
                                    + "</li>");
                        }
                        out.println("    </ul>");
                    }
                    out.println("    <p class=\"agenda-attendance-count\">"
                            + meetingAttendees.size() + " registered attendee"
                            + (meetingAttendees.size() == 1 ? "" : "s") + "</p>");
                }
                out.println("  </div>");
            }

            // --- DURATION WARNING ---
            if (meetingDurationMinutes != null) {
                long diff = agendaMinutes - meetingDurationMinutes;
                if (diff > 0) {
                    out.println("  <div class=\"agenda-duration-warning\">");
                    out.println("    &#9888; Agenda uses <strong>" + agendaMinutes + " minutes</strong>, "
                            + "but meeting is scheduled for <strong>" + meetingDurationMinutes + " minutes</strong>. "
                            + "Reduce agenda by <strong>" + diff + " minute" + (diff == 1 ? "" : "s") + "</strong>.");
                    out.println("  </div>");
                } else if (diff < 0) {
                    out.println("  <div class=\"agenda-duration-info\">");
                    out.println("    Agenda has <strong>" + (-diff) + " unallocated minute"
                            + ((-diff) == 1 ? "" : "s") + "</strong>.");
                    out.println("  </div>");
                } else {
                    out.println("  <div class=\"agenda-duration-ok\">");
                    out.println("    Agenda time matches scheduled meeting length.");
                    out.println("  </div>");
                }
            }

            // --- AGENDA TABLE ---
            out.println("  <div class=\"agenda-table-container\">");
            out.println("  <table class=\"agenda-table\">");
            out.println("    <thead>");
            out.println("      <tr>");
            if (canEdit)
                out.println("        <th class=\"no-print\">Controls</th>");
            out.println("        <th class=\"col-topic\">Topic / Time</th>");
            out.println("        <th class=\"col-agenda\">Agenda</th>");
            out.println("        <th class=\"col-presenter\">Presenter(s)</th>");
            if (isEditor)
                out.println("        <th class=\"col-status no-print\">Status</th>");
            out.println("      </tr>");
            out.println("    </thead>");
            out.println("    <tbody>");

            // Calculate item time ranges
            LocalDateTime cursor = meeting.getScheduledStart();

            for (int i = 0; i < items.size(); i++) {
                EsMeetingAgendaItem item = items.get(i);
                if (item.getStatus() == AgendaItemStatus.CANCELLED)
                    continue;
                boolean isPostponedItem = item.getStatus() == AgendaItemStatus.POSTPONED;
                if (isPostponedItem && !isEditor)
                    continue;
                String rowClass = "";

                out.println("      <tr class=\"" + rowClass + "\">");

                // Controls column
                if (canEdit) {
                    out.println("        <td class=\"col-controls no-print\">");
                    if (true) {
                        // Move up
                        if (i > 0) {
                            out.println("          <form method=\"post\" action=\"" + contextPath
                                    + "/es/agenda\" style=\"display:inline\">");
                            out.println("            <input type=\"hidden\" name=\"meetingId\" value=\""
                                    + meeting.getEsMeetingId() + "\">");
                            out.println("            <input type=\"hidden\" name=\"action\" value=\"moveItemUp\">");
                            out.println("            <input type=\"hidden\" name=\"itemId\" value=\""
                                    + item.getEsMeetingAgendaItemId() + "\">");
                            if (editOverride)
                                out.println("            <input type=\"hidden\" name=\"edit\" value=\"true\">");
                            out.println("            <button type=\"submit\" title=\"Move up\">&#8593;</button>");
                            out.println("          </form>");
                        }
                        // Move down
                        if (i < items.size() - 1) {
                            out.println("          <form method=\"post\" action=\"" + contextPath
                                    + "/es/agenda\" style=\"display:inline\">");
                            out.println("            <input type=\"hidden\" name=\"meetingId\" value=\""
                                    + meeting.getEsMeetingId() + "\">");
                            out.println("            <input type=\"hidden\" name=\"action\" value=\"moveItemDown\">");
                            out.println("            <input type=\"hidden\" name=\"itemId\" value=\""
                                    + item.getEsMeetingAgendaItemId() + "\">");
                            if (editOverride)
                                out.println("            <input type=\"hidden\" name=\"edit\" value=\"true\">");
                            out.println("            <button type=\"submit\" title=\"Move down\">&#8595;</button>");
                            out.println("          </form>");
                        }
                    }
                    out.println("        </td>");
                }

                // Topic / Time column
                int itemMinutes = item.getTimeMinutes() != null ? item.getTimeMinutes() : 0;
                String itemTimeRange = "";
                if (cursor != null && !isPostponedItem) {
                    ZonedDateTime itemStart = ZonedDateTime.of(cursor, meetingZone).withZoneSameInstant(viewerZone);
                    ZonedDateTime itemEnd = itemStart.plusMinutes(itemMinutes);
                    itemTimeRange = DISPLAY_TIME_FMT.format(itemStart) + "–" + DISPLAY_TIME_FMT.format(itemEnd);
                }

                if (canEdit) {
                    out.println("        <td class=\"col-topic click-to-edit\" onclick=\"esShowEdit('item-"
                            + item.getEsMeetingAgendaItemId() + "')\" title=\"Click to edit\">");
                } else {
                    out.println("        <td class=\"col-topic\">");
                }
                EsTopic linkedTopic = item.getEsTopicId() != null ? topicById.get(item.getEsTopicId()) : null;
                if (linkedTopic != null && !canEdit) {
                    out.println("          <div class=\"agenda-item-title\"><a href=\"" + contextPath
                            + "/es/topic/" + item.getEsTopicId() + "\" class=\"agenda-topic-link\">"
                            + escapeHtml(orEmpty(item.getTitle())) + "</a></div>");
                } else {
                    out.println("          <div class=\"agenda-item-title\">" + escapeHtml(orEmpty(item.getTitle()))
                            + "</div>");
                }
                if (!itemTimeRange.isEmpty()) {
                    out.println("          <div class=\"agenda-item-time\">" + escapeHtml(itemTimeRange) + "</div>");
                }
                if (item.getTimeMinutes() != null && !isPostponedItem) {
                    out.println("          <div class=\"agenda-item-duration no-print\">" + item.getTimeMinutes()
                            + " min</div>");
                }
                out.println("        </td>");

                // Agenda text column
                out.println("        <td class=\"col-agenda\">");
                if (canEdit) {
                    out.println("          <div id=\"item-" + item.getEsMeetingAgendaItemId()
                            + "-display\" class=\"click-to-edit\" onclick=\"esShowEdit('item-"
                            + item.getEsMeetingAgendaItemId() + "')\" title=\"Click to edit\">");
                    if (item.getAgendaMarkdown() != null && !item.getAgendaMarkdown().isBlank()) {
                        out.println("            <div class=\"agenda-item-text\">"
                                + renderPlainText(item.getAgendaMarkdown()) + "</div>");
                    } else {
                        out.println("            <span class=\"agenda-muted\">Click to edit...</span>");
                    }
                    out.println("          </div>");
                    out.println("          <form id=\"item-" + item.getEsMeetingAgendaItemId()
                            + "-form\" class=\"agenda-edit-form no-print\" method=\"post\" action=\""
                            + contextPath + "/es/agenda\" style=\"display:none\">");
                    out.println("            <input type=\"hidden\" name=\"meetingId\" value=\""
                            + meeting.getEsMeetingId() + "\">");
                    out.println("            <input type=\"hidden\" name=\"action\" value=\"updateAgendaItem\">");
                    out.println("            <input type=\"hidden\" name=\"itemId\" value=\""
                            + item.getEsMeetingAgendaItemId() + "\">");
                    if (editOverride)
                        out.println("            <input type=\"hidden\" name=\"edit\" value=\"true\">");
                    EsTopic editLinkedTopic = item.getEsTopicId() != null ? topicById.get(item.getEsTopicId()) : null;
                    if (editLinkedTopic != null) {
                        out.println("            <div class=\"agenda-item-linked-topic\">Linked to: <a href=\""
                                + contextPath + "/es/topic/" + item.getEsTopicId() + "\" target=\"_blank\">"
                                + escapeHtml(orEmpty(editLinkedTopic.getTopicName())) + "</a></div>");
                    }
                    out.println("            <input type=\"text\" name=\"title\" value=\""
                            + escapeHtml(orEmpty(item.getTitle())) + "\" placeholder=\"Title\" size=\"30\">");
                    out.println(
                            "            <textarea name=\"agendaMarkdown\" rows=\"2\" style=\"width:100%\" placeholder=\"Agenda text\">"
                                    + escapeHtml(orEmpty(item.getAgendaMarkdown())) + "</textarea>");
                    out.println("            <div style=\"display:flex;align-items:center;gap:0.3rem\">");
                    out.println("              <input type=\"number\" name=\"timeMinutes\" value=\""
                            + (item.getTimeMinutes() != null ? item.getTimeMinutes() : "")
                            + "\" min=\"0\" max=\"480\" style=\"width:3.5rem\">");
                    out.println("              <span>min</span>");
                    out.println("            </div>");
                    out.println("            <div style=\"display:flex;gap:0.3rem\">");
                    out.println("              <button type=\"submit\">Save</button>");
                    out.println("              <button type=\"button\" onclick=\"esHideEdit('item-"
                            + item.getEsMeetingAgendaItemId() + "')\">Cancel</button>");
                    out.println("            </div>");
                    out.println("          </form>");
                } else {
                    if (item.getAgendaMarkdown() != null && !item.getAgendaMarkdown().isBlank()) {
                        out.println("          <div class=\"agenda-item-text\">"
                                + renderPlainText(item.getAgendaMarkdown()) + "</div>");
                    }
                }
                out.println("        </td>");

                // Presenter(s) column
                out.println("        <td class=\"col-presenter\">");
                List<EsAgendaItemPresenter> pList = presentersByItem.getOrDefault(
                        item.getEsMeetingAgendaItemId(), List.of());
                // Build set of active presenter emails on this item (for deduplication in
                // quick-pick)
                Set<String> activeEmailsOnItem = new HashSet<>();
                for (EsAgendaItemPresenter p : pList) {
                    if (p.getStatus() != EsAgendaItemPresenter.PresenterStatus.REMOVED
                            && p.getStatus() != EsAgendaItemPresenter.PresenterStatus.DECLINED
                            && p.getEmailNormalized() != null) {
                        activeEmailsOnItem.add(p.getEmailNormalized());
                    }
                }
                // Render each presenter
                for (EsAgendaItemPresenter p : pList) {
                    if (p.getStatus() == EsAgendaItemPresenter.PresenterStatus.REMOVED) {
                        continue;
                    }
                    String pName = presenterDisplayName(p, presenterUsers);
                    String pStatusLabel = titleCase(p.getStatus().name());
                    String pId = "pres-" + p.getEsAgendaItemPresenterId();
                    if (canEdit) {
                        out.println("          <div class=\"agenda-presenter\">");
                        out.println(
                                "            <span class=\"presenter-name click-to-edit\" onclick=\"esShowPresEdit('"
                                        + pId + "')\">"
                                        + escapeHtml(pName) + " <span class=\"presenter-status\">("
                                        + escapeHtml(pStatusLabel) + ")</span></span>");
                        out.println("            <div id=\"" + pId
                                + "-panel\" class=\"presenter-edit-panel no-print\" style=\"display:none\">");
                        // Role form
                        out.println("              <form method=\"post\" action=\"" + contextPath
                                + "/es/agenda\" class=\"pres-action-form\">");
                        out.println("                <input type=\"hidden\" name=\"meetingId\" value=\""
                                + meeting.getEsMeetingId() + "\">");
                        out.println(
                                "                <input type=\"hidden\" name=\"action\" value=\"updatePresenterRole\">");
                        out.println("                <input type=\"hidden\" name=\"presenterId\" value=\""
                                + p.getEsAgendaItemPresenterId() + "\">");
                        if (editOverride)
                            out.println("                <input type=\"hidden\" name=\"edit\" value=\"true\">");
                        out.println(
                                "                <label class=\"pres-role-label\">Role: <select name=\"presenterRole\">");
                        for (EsAgendaItemPresenter.PresenterRole r : EsAgendaItemPresenter.PresenterRole.values()) {
                            String sel = r == p.getPresenterRole() ? " selected" : "";
                            out.println("                  <option value=\"" + r.name() + "\"" + sel + ">"
                                    + escapeHtml(titleCase(r.name())) + "</option>");
                        }
                        out.println("                </select></label>");
                        out.println("                <button type=\"submit\" class=\"pres-btn\">Save Role</button>");
                        out.println("              </form>");
                        // Accept form (if not already accepted)
                        if (p.getStatus() != EsAgendaItemPresenter.PresenterStatus.ACCEPTED) {
                            out.println("              <form method=\"post\" action=\"" + contextPath
                                    + "/es/agenda\" style=\"display:inline\">");
                            out.println("                <input type=\"hidden\" name=\"meetingId\" value=\""
                                    + meeting.getEsMeetingId() + "\">");
                            out.println(
                                    "                <input type=\"hidden\" name=\"action\" value=\"acceptPresenter\">");
                            out.println("                <input type=\"hidden\" name=\"presenterId\" value=\""
                                    + p.getEsAgendaItemPresenterId() + "\">");
                            if (editOverride)
                                out.println("                <input type=\"hidden\" name=\"edit\" value=\"true\">");
                            out.println(
                                    "                <button type=\"submit\" class=\"pres-btn pres-btn-accept\">Accept</button>");
                            out.println("              </form>");
                        }
                        // Remove form
                        out.println("              <form method=\"post\" action=\"" + contextPath
                                + "/es/agenda\" style=\"display:inline\">");
                        out.println("                <input type=\"hidden\" name=\"meetingId\" value=\""
                                + meeting.getEsMeetingId() + "\">");
                        out.println(
                                "                <input type=\"hidden\" name=\"action\" value=\"removePresenter\">");
                        out.println("                <input type=\"hidden\" name=\"presenterId\" value=\""
                                + p.getEsAgendaItemPresenterId() + "\">");
                        if (editOverride)
                            out.println("                <input type=\"hidden\" name=\"edit\" value=\"true\">");
                        out.println(
                                "                <button type=\"submit\" class=\"pres-btn pres-btn-remove\" onclick=\"return confirm('Remove this presenter?')\">Remove</button>");
                        out.println("              </form>");
                        out.println(
                                "              <button type=\"button\" class=\"pres-btn pres-btn-close\" onclick=\"esHidePresEdit('"
                                        + pId + "')\">Close</button>");
                        out.println("            </div>"); // end panel
                        out.println("          </div>"); // end agenda-presenter
                    } else {
                        // View mode: just show name and status for editors
                        String statusSuffix = isEditor ? " (" + pStatusLabel + ")" : "";
                        out.println("          <div class=\"agenda-presenter\">" + escapeHtml(pName)
                                + escapeHtml(statusSuffix) + "</div>");
                    }
                }
                // Add Presenter UI (canEdit only)
                if (canEdit) {
                    String addPanelId = "add-pres-" + item.getEsMeetingAgendaItemId();
                    out.println("          <div class=\"no-print\" style=\"margin-top:0.3rem\">");
                    out.println("            <button type=\"button\" class=\"add-pres-btn\" onclick=\"esShowAddPres('"
                            + addPanelId + "')\">+ Add Presenter</button>");
                    out.println("          </div>");
                    out.println("          <div id=\"" + addPanelId
                            + "\" class=\"add-pres-panel no-print\" style=\"display:none\">");

                    // --- Role Picker ---
                    out.println("            <div class=\"role-picker-row\">");
                    out.println("              <span class=\"role-picker-label\">Role:</span>");
                    out.println("              <div class=\"role-picker-toggle\">");
                    boolean firstRole = true;
                    for (EsAgendaItemPresenter.PresenterRole presRole : EsAgendaItemPresenter.PresenterRole.values()) {
                        String roleOptLabel = titleCase(presRole.name());
                        String activeClass = firstRole ? " role-option-active" : "";
                        out.println("                <button type=\"button\" class=\"role-option" + activeClass + "\""
                                + " onclick=\"esOnPresRoleChange('" + addPanelId + "','" + presRole.name()
                                + "',this)\">" + escapeHtml(roleOptLabel) + "</button>");
                        firstRole = false;
                    }
                    out.println("              </div>");
                    out.println("            </div>");

                    // --- Quick Pick: Myself ---
                    if (user.getEmail() != null) {
                        String myEmailNorm = user.getEmail().trim().toLowerCase();
                        if (!activeEmailsOnItem.contains(myEmailNorm)) {
                            String myName = user.getFullName();
                            if (myName == null || myName.isBlank())
                                myName = user.getEmail();
                            out.println("            <div class=\"quick-pick-section\">");
                            out.println("              <div class=\"quick-pick-label\">Quick Add:</div>");
                            out.println("              <form method=\"post\" action=\"" + contextPath
                                    + "/es/agenda\" style=\"display:inline\">");
                            out.println("                <input type=\"hidden\" name=\"meetingId\" value=\""
                                    + meeting.getEsMeetingId() + "\">");
                            out.println(
                                    "                <input type=\"hidden\" name=\"action\" value=\"addPresenter\">");
                            out.println("                <input type=\"hidden\" name=\"itemId\" value=\""
                                    + item.getEsMeetingAgendaItemId() + "\">");
                            if (user.getUserId() != null)
                                out.println("                <input type=\"hidden\" name=\"userId\" value=\""
                                        + user.getUserId() + "\">");
                            out.println("                <input type=\"hidden\" name=\"email\" value=\""
                                    + escapeHtml(user.getEmail()) + "\">");
                            String myFullName = user.getFullName();
                            if (myFullName != null && !myFullName.isBlank())
                                out.println("                <input type=\"hidden\" name=\"displayName\" value=\""
                                        + escapeHtml(myFullName) + "\">");
                            if (editOverride)
                                out.println("                <input type=\"hidden\" name=\"edit\" value=\"true\">");
                            out.println(
                                    "                <input type=\"hidden\" name=\"presenterRole\" class=\"pres-role-input\" value=\"LEAD\">");
                            out.println(
                                    "                <button type=\"submit\" class=\"quick-pick-chip quick-pick-self\">"
                                            + escapeHtml(myName) + " (me)</button>");
                            out.println("              </form>");
                            out.println("            </div>");
                        }
                    }

                    // --- Quick Pick: From this meeting ---
                    Set<String> shownInMeeting = new HashSet<>();
                    List<EsAgendaItemPresenter> meetingQuickPicks = new ArrayList<>();
                    for (Map.Entry<Long, List<EsAgendaItemPresenter>> entry : presentersByItem.entrySet()) {
                        if (entry.getKey().equals(item.getEsMeetingAgendaItemId()))
                            continue;
                        for (EsAgendaItemPresenter qp : entry.getValue()) {
                            if (qp.getStatus() == EsAgendaItemPresenter.PresenterStatus.REMOVED
                                    || qp.getStatus() == EsAgendaItemPresenter.PresenterStatus.DECLINED)
                                continue;
                            if (qp.getEmailNormalized() == null)
                                continue;
                            if (activeEmailsOnItem.contains(qp.getEmailNormalized()))
                                continue;
                            if (!shownInMeeting.add(qp.getEmailNormalized()))
                                continue;
                            meetingQuickPicks.add(qp);
                        }
                    }
                    if (!meetingQuickPicks.isEmpty()) {
                        out.println("            <div class=\"quick-pick-section\">");
                        out.println("              <div class=\"quick-pick-label\">From this meeting:</div>");
                        for (EsAgendaItemPresenter qp : meetingQuickPicks) {
                            String qpName = presenterDisplayName(qp, presenterUsers);
                            out.println("              <form method=\"post\" action=\"" + contextPath
                                    + "/es/agenda\" style=\"display:inline\">");
                            out.println("                <input type=\"hidden\" name=\"meetingId\" value=\""
                                    + meeting.getEsMeetingId() + "\">");
                            out.println(
                                    "                <input type=\"hidden\" name=\"action\" value=\"addPresenter\">");
                            out.println("                <input type=\"hidden\" name=\"itemId\" value=\""
                                    + item.getEsMeetingAgendaItemId() + "\">");
                            if (qp.getUserId() != null)
                                out.println("                <input type=\"hidden\" name=\"userId\" value=\""
                                        + qp.getUserId() + "\">");
                            out.println("                <input type=\"hidden\" name=\"email\" value=\""
                                    + escapeHtml(qp.getEmail()) + "\">");
                            if (qp.getDisplayName() != null)
                                out.println("                <input type=\"hidden\" name=\"displayName\" value=\""
                                        + escapeHtml(qp.getDisplayName()) + "\">");
                            if (editOverride)
                                out.println("                <input type=\"hidden\" name=\"edit\" value=\"true\">");
                            out.println(
                                    "                <input type=\"hidden\" name=\"presenterRole\" class=\"pres-role-input\" value=\"LEAD\">");
                            out.println("                <button type=\"submit\" class=\"quick-pick-chip\">"
                                    + escapeHtml(qpName) + "</button>");
                            out.println("              </form>");
                        }
                        out.println("            </div>");
                    }

                    // --- Quick Pick: Topic Champions ---
                    if (item.getEsTopicId() != null) {
                        List<EsSubscription> champs = championsByTopic.getOrDefault(item.getEsTopicId(), List.of());
                        List<EsSubscription> champPicks = new ArrayList<>();
                        Set<String> shownChamps = new HashSet<>();
                        for (EsSubscription ch : champs) {
                            if (ch.getEmailNormalized() == null)
                                continue;
                            if (activeEmailsOnItem.contains(ch.getEmailNormalized()))
                                continue;
                            if (!shownChamps.add(ch.getEmailNormalized()))
                                continue;
                            champPicks.add(ch);
                        }
                        if (!champPicks.isEmpty()) {
                            out.println("            <div class=\"quick-pick-section\">");
                            out.println("              <div class=\"quick-pick-label\">Topic Champions:</div>");
                            for (EsSubscription ch : champPicks) {
                                String champName = ch.getEmail();
                                if (ch.getUserId() != null) {
                                    User cu = championUserMap.get(ch.getUserId());
                                    if (cu != null && cu.getFullName() != null && !cu.getFullName().isBlank()) {
                                        champName = cu.getFullName();
                                    }
                                }
                                out.println("              <form method=\"post\" action=\"" + contextPath
                                        + "/es/agenda\" style=\"display:inline\">");
                                out.println("                <input type=\"hidden\" name=\"meetingId\" value=\""
                                        + meeting.getEsMeetingId() + "\">");
                                out.println(
                                        "                <input type=\"hidden\" name=\"action\" value=\"addPresenter\">");
                                out.println("                <input type=\"hidden\" name=\"itemId\" value=\""
                                        + item.getEsMeetingAgendaItemId() + "\">");
                                if (ch.getUserId() != null)
                                    out.println("                <input type=\"hidden\" name=\"userId\" value=\""
                                            + ch.getUserId() + "\">");
                                out.println("                <input type=\"hidden\" name=\"email\" value=\""
                                        + escapeHtml(ch.getEmail()) + "\">");
                                if (editOverride)
                                    out.println("                <input type=\"hidden\" name=\"edit\" value=\"true\">");
                                out.println(
                                        "                <input type=\"hidden\" name=\"presenterRole\" class=\"pres-role-input\" value=\"LEAD\">");
                                out.println("                <button type=\"submit\" class=\"quick-pick-chip\">"
                                        + escapeHtml(champName) + "</button>");
                                out.println("              </form>");
                            }
                            out.println("            </div>");
                        }
                    }

                    // --- Find registered user ---
                    String userSearchId = "pres-user-search-" + item.getEsMeetingAgendaItemId();
                    String userHiddenId = "pres-user-id-" + item.getEsMeetingAgendaItemId();
                    out.println("            <div class=\"quick-pick-section\">");
                    out.println("              <div class=\"quick-pick-label\">Find registered user:</div>");
                    out.println("              <form method=\"post\" action=\"" + contextPath
                            + "/es/agenda\" class=\"pres-action-form\">");
                    out.println("                <input type=\"hidden\" name=\"meetingId\" value=\""
                            + meeting.getEsMeetingId() + "\">");
                    out.println("                <input type=\"hidden\" name=\"action\" value=\"addPresenter\">");
                    out.println("                <input type=\"hidden\" name=\"itemId\" value=\""
                            + item.getEsMeetingAgendaItemId() + "\">");
                    out.println("                <input type=\"hidden\" name=\"userId\" id=\"" + userHiddenId + "\">");
                    if (editOverride)
                        out.println("                <input type=\"hidden\" name=\"edit\" value=\"true\">");
                    out.println(
                            "                <input type=\"hidden\" name=\"presenterRole\" class=\"pres-role-input\" value=\"LEAD\">");
                    out.println("                <input type=\"text\" id=\"" + userSearchId
                            + "\" list=\"pres-user-list\" placeholder=\"Search name or email...\" autocomplete=\"off\" class=\"pres-text-input\" oninput=\"esOnPresUserInput(this.value,'"
                            + userHiddenId + "')\">");
                    out.println("                <button type=\"submit\" class=\"pres-btn\">Add User</button>");
                    out.println("              </form>");
                    out.println("            </div>");

                    // --- Add by name & email ---
                    out.println("            <div class=\"quick-pick-section\">");
                    out.println("              <div class=\"quick-pick-label\">Add by name &amp; email:</div>");
                    out.println("              <form method=\"post\" action=\"" + contextPath
                            + "/es/agenda\" class=\"pres-action-form\">");
                    out.println("                <input type=\"hidden\" name=\"meetingId\" value=\""
                            + meeting.getEsMeetingId() + "\">");
                    out.println("                <input type=\"hidden\" name=\"action\" value=\"addPresenter\">");
                    out.println("                <input type=\"hidden\" name=\"itemId\" value=\""
                            + item.getEsMeetingAgendaItemId() + "\">");
                    if (editOverride)
                        out.println("                <input type=\"hidden\" name=\"edit\" value=\"true\">");
                    out.println(
                            "                <input type=\"hidden\" name=\"presenterRole\" class=\"pres-role-input\" value=\"LEAD\">");
                    out.println(
                            "                <input type=\"text\" name=\"displayName\" placeholder=\"Name\" class=\"pres-text-input\">");
                    out.println(
                            "                <input type=\"email\" name=\"email\" placeholder=\"Email (required)\" required class=\"pres-text-input\">");
                    out.println("                <button type=\"submit\" class=\"pres-btn\">Add</button>");
                    out.println("              </form>");
                    out.println("            </div>");

                    out.println(
                            "            <button type=\"button\" class=\"pres-btn pres-btn-close\" style=\"margin-top:0.3rem\" onclick=\"esHideAddPres('"
                                    + addPanelId + "')\">Close</button>");
                    out.println("          </div>"); // end add-pres-panel
                }
                out.println("        </td>");

                // Status column (editor only)
                if (isEditor) {
                    String statusLabel = Arrays.stream(item.getStatus().name().split("_"))
                            .map(w -> w.substring(0, 1) + w.substring(1).toLowerCase())
                            .collect(Collectors.joining(" "));
                    AgendaItemStatus s = item.getStatus();
                    String statusColorClass = (s == AgendaItemStatus.ACCEPTED || s == AgendaItemStatus.COVERED)
                            ? "status-green"
                            : (s == AgendaItemStatus.NEEDS_REVISION || s == AgendaItemStatus.NOT_COVERED)
                                    ? "status-red"
                                    : "";
                    out.println("        <td class=\"col-status no-print\">");
                    if (canEdit) {
                        List<AgendaItemStatus> transitions = validItemTransitions(item.getStatus());
                        if (!transitions.isEmpty()) {
                            out.println("          <span class=\"agenda-item-status-badge " + statusColorClass
                                    + " click-to-edit\" onclick=\"document.getElementById('status-"
                                    + item.getEsMeetingAgendaItemId()
                                    + "-form').style.display='flex'\" title=\"Click to change status\">"
                                    + escapeHtml(statusLabel) + "</span>");
                            out.println("          <div id=\"status-" + item.getEsMeetingAgendaItemId()
                                    + "-form\" style=\"display:none;flex-direction:column;margin-top:0.3rem\">");
                            for (AgendaItemStatus ts : transitions) {
                                String tsLabel = Arrays.stream(ts.name().split("_"))
                                        .map(w -> w.substring(0, 1) + w.substring(1).toLowerCase())
                                        .collect(Collectors.joining(" "));
                                out.println("            <form method=\"post\" action=\"" + contextPath
                                        + "/es/agenda\" style=\"display:block;margin-top:2px\">");
                                out.println("              <input type=\"hidden\" name=\"meetingId\" value=\""
                                        + meeting.getEsMeetingId() + "\">");
                                out.println(
                                        "              <input type=\"hidden\" name=\"action\" value=\"updateItemStatus\">");
                                out.println("              <input type=\"hidden\" name=\"itemId\" value=\""
                                        + item.getEsMeetingAgendaItemId() + "\">");
                                out.println("              <input type=\"hidden\" name=\"targetStatus\" value=\""
                                        + ts.name()
                                        + "\">");
                                if (editOverride)
                                    out.println("              <input type=\"hidden\" name=\"edit\" value=\"true\">");
                                out.println("              <button type=\"submit\" class=\"agenda-status-btn\">"
                                        + escapeHtml(tsLabel) + "</button>");
                                out.println("            </form>");
                            }
                            out.println("          </div>");
                        } else {
                            out.println("          <span class=\"agenda-item-status-badge " + statusColorClass + "\">"
                                    + escapeHtml(statusLabel) + "</span>");
                        }
                    } else {
                        out.println("          <span class=\"agenda-item-status-badge " + statusColorClass + "\">"
                                + escapeHtml(statusLabel) + "</span>");
                    }
                    out.println("        </td>");
                }

                out.println("      </tr>");

                // Advance cursor (skip postponed — their slot is no longer part of this
                // meeting)
                if (!isPostponedItem && cursor != null) {
                    cursor = cursor.plusMinutes(itemMinutes);
                }
            }

            if (items.isEmpty()) {
                int colspan = 3 + (canEdit ? 1 : 0) + (isEditor ? 1 : 0);
                out.println("      <tr><td colspan=\"" + colspan + "\">No agenda items.</td></tr>");
            }

            out.println("    </tbody>");
            out.println("  </table>");
            out.println("  </div>"); // end agenda-table-container

            // --- ADD AGENDA ITEM ---
            if (canEdit) {
                out.println("  <div class=\"agenda-add-item no-print\">");
                out.println("    <form method=\"post\" action=\"" + contextPath + "/es/agenda\">");
                out.println(
                        "      <input type=\"hidden\" name=\"meetingId\" value=\"" + meeting.getEsMeetingId() + "\">");
                out.println("      <input type=\"hidden\" name=\"action\" value=\"addAgendaItem\">");
                if (editOverride)
                    out.println("      <input type=\"hidden\" name=\"edit\" value=\"true\">");
                out.println("      <input type=\"hidden\" name=\"topicId\" id=\"add-topic-id\">");
                out.println("      <div class=\"add-item-row\">");
                out.println("        <input type=\"text\" id=\"add-topic-search\" list=\"add-topic-list\"");
                out.println(
                        "               placeholder=\"Search topics...\" autocomplete=\"off\" class=\"add-item-input\"");
                out.println("               oninput=\"esOnTopicInput(this.value)\">");
                out.println("        <datalist id=\"add-topic-list\"></datalist>");
                out.println("      </div>");
                out.println("      <div class=\"add-item-row\">");
                out.println(
                        "        <input type=\"text\" name=\"title\" id=\"add-item-title\" placeholder=\"Title (optional)\" class=\"add-item-input\">");
                out.println("        <button type=\"submit\">+ Add Agenda Item</button>");
                out.println("      </div>");
                out.println("    </form>");
                out.println("  </div>");
            }

            // --- OPEN ITEMS (carry-forward: POSTPONED / NOT_COVERED / NEEDS_REVISION) ---
            if (canEdit && !openItems.isEmpty()) {
                out.println("  <div class=\"open-items-section no-print\">");
                out.println("    <div class=\"open-items-heading\">Open Items"
                        + "<span class=\"open-items-subtext\"> — not addressed in a previous meeting and needs to be rescheduled</span></div>");
                out.println("    <div class=\"agenda-table-container\">");
                out.println("    <table class=\"prev-items-table\">");
                out.println("      <thead><tr>");
                out.println("        <th>Topic / Title</th>");
                out.println("        <th>Min</th>");
                out.println("        <th>Status</th>");
                out.println("        <th>From</th>");
                out.println("        <th></th>");
                out.println("      </tr></thead>");
                out.println("      <tbody>");
                for (EsMeetingAgendaItem oi : openItems) {
                    EsMeeting srcMtg = openMeetingById.get(oi.getEsMeetingId());
                    String srcLabel = srcMtg != null && srcMtg.getScheduledStart() != null
                            ? DISPLAY_DATE_FMT.format(srcMtg.getScheduledStart().toLocalDate())
                            : "Previous";
                    String oiStatusLabel = titleCase(oi.getStatus().name());
                    String statusCls = oi.getStatus() == AgendaItemStatus.POSTPONED ? "prev-status-postponed"
                            : "prev-status-warn";
                    out.println("      <tr>");
                    out.println("        <td class=\"prev-item-title\">");
                    EsTopic oiTopic = oi.getEsTopicId() != null ? topicById.get(oi.getEsTopicId()) : null;
                    if (oiTopic != null) {
                        out.println("          <a href=\"" + contextPath + "/es/topic/" + oi.getEsTopicId()
                                + "\" class=\"agenda-topic-link\" target=\"_blank\">"
                                + escapeHtml(oiTopic.getTopicName()) + "</a>");
                        if (oi.getTitle() != null && !oi.getTitle().isBlank())
                            out.println("          <div>" + escapeHtml(oi.getTitle()) + "</div>");
                    } else {
                        out.println("          " + escapeHtml(oi.getTitle()));
                    }
                    List<EsAgendaItemPresenter> oiPs = prevPresentersByItem
                            .getOrDefault(oi.getEsMeetingAgendaItemId(), List.of()).stream()
                            .filter(p -> p.getStatus() != EsAgendaItemPresenter.PresenterStatus.REMOVED
                                    && p.getStatus() != EsAgendaItemPresenter.PresenterStatus.DECLINED)
                            .collect(Collectors.toList());
                    if (!oiPs.isEmpty()) {
                        out.println("          <div class=\"prev-item-presenters\">");
                        for (EsAgendaItemPresenter pp : oiPs) {
                            out.println("            <span class=\"prev-presenter-chip\">"
                                    + escapeHtml(presenterDisplayName(pp, presenterUsers)) + "</span>");
                        }
                        out.println("          </div>");
                    }
                    out.println("        </td>");
                    out.println("        <td class=\"prev-item-min\">"
                            + (oi.getTimeMinutes() != null ? oi.getTimeMinutes() : "") + "</td>");
                    out.println("        <td><span class=\"prev-item-status " + statusCls + "\">"
                            + escapeHtml(oiStatusLabel) + "</span></td>");
                    out.println("        <td class=\"prev-item-from\">" + escapeHtml(srcLabel) + "</td>");
                    out.println("        <td class=\"prev-item-controls\">");
                    out.println("          <form method=\"post\" action=\"" + contextPath + "/es/agenda\">");
                    out.println("            <input type=\"hidden\" name=\"meetingId\" value=\""
                            + meeting.getEsMeetingId() + "\">");
                    out.println("            <input type=\"hidden\" name=\"action\" value=\"copyAgendaItem\">");
                    out.println("            <input type=\"hidden\" name=\"sourceItemId\" value=\""
                            + oi.getEsMeetingAgendaItemId() + "\">");
                    if (editOverride)
                        out.println("            <input type=\"hidden\" name=\"edit\" value=\"true\">");
                    out.println("            <button type=\"submit\" class=\"prev-copy-btn\">Add to Agenda</button>");
                    out.println("          </form>");
                    out.println("        </td>");
                    out.println("      </tr>");
                }
                out.println("      </tbody>");
                out.println("    </table>");
                out.println("    </div>"); // agenda-table-container
                out.println("  </div>"); // open-items-section
            }

            // --- COPY FROM PREVIOUS MEETINGS (last 2) ---
            if (canEdit && !copyMeetings.isEmpty()) {
                out.println("  <div class=\"prev-items-section no-print\">");
                out.println("    <div class=\"prev-items-heading\">Copy from Previous Meetings</div>");
                for (EsMeeting cm : copyMeetings) {
                    List<EsMeetingAgendaItem> cmItems = copyItemsByMeeting.getOrDefault(cm.getEsMeetingId(), List.of());
                    if (cmItems.isEmpty())
                        continue;
                    String cmLabel = (seriesName != null ? seriesName + " \u00b7 " : "")
                            + (cm.getScheduledStart() != null
                                    ? DISPLAY_DATE_FMT.format(cm.getScheduledStart().toLocalDate())
                                    : "Previous");
                    out.println("    <div class=\"copy-prev-meeting\">");
                    out.println("      <div class=\"copy-prev-meeting-heading\">" + escapeHtml(cmLabel) + "</div>");
                    out.println("      <div class=\"agenda-table-container\">");
                    out.println("      <table class=\"prev-items-table\">");
                    out.println("        <thead><tr>");
                    out.println("          <th>Topic / Title</th>");
                    out.println("          <th>Min</th>");
                    out.println("          <th>Status</th>");
                    out.println("          <th></th>");
                    out.println("        </tr></thead>");
                    out.println("        <tbody>");
                    for (EsMeetingAgendaItem ci : cmItems) {
                        String ciStatusLabel = titleCase(ci.getStatus().name());
                        String statusCls = ci.getStatus() == AgendaItemStatus.POSTPONED ? "prev-status-postponed"
                                : (ci.getStatus() == AgendaItemStatus.NOT_COVERED
                                        || ci.getStatus() == AgendaItemStatus.NEEDS_REVISION)
                                                ? "prev-status-warn"
                                                : "prev-status-neutral";
                        out.println("        <tr>");
                        out.println("          <td class=\"prev-item-title\">");
                        EsTopic ciTopic = ci.getEsTopicId() != null ? topicById.get(ci.getEsTopicId()) : null;
                        if (ciTopic != null) {
                            out.println("            <a href=\"" + contextPath + "/es/topic/" + ci.getEsTopicId()
                                    + "\" class=\"agenda-topic-link\" target=\"_blank\">"
                                    + escapeHtml(ciTopic.getTopicName()) + "</a>");
                            if (ci.getTitle() != null && !ci.getTitle().isBlank())
                                out.println("            <div>" + escapeHtml(ci.getTitle()) + "</div>");
                        } else {
                            out.println("            " + escapeHtml(ci.getTitle()));
                        }
                        List<EsAgendaItemPresenter> ciPs = prevPresentersByItem
                                .getOrDefault(ci.getEsMeetingAgendaItemId(), List.of()).stream()
                                .filter(p -> p.getStatus() != EsAgendaItemPresenter.PresenterStatus.REMOVED
                                        && p.getStatus() != EsAgendaItemPresenter.PresenterStatus.DECLINED)
                                .collect(Collectors.toList());
                        if (!ciPs.isEmpty()) {
                            out.println("            <div class=\"prev-item-presenters\">");
                            for (EsAgendaItemPresenter pp : ciPs) {
                                out.println("              <span class=\"prev-presenter-chip\">"
                                        + escapeHtml(presenterDisplayName(pp, presenterUsers)) + "</span>");
                            }
                            out.println("            </div>");
                        }
                        out.println("          </td>");
                        out.println("          <td class=\"prev-item-min\">"
                                + (ci.getTimeMinutes() != null ? ci.getTimeMinutes() : "") + "</td>");
                        out.println("          <td><span class=\"prev-item-status " + statusCls + "\">"
                                + escapeHtml(ciStatusLabel) + "</span></td>");
                        out.println("          <td class=\"prev-item-controls\">");
                        out.println("            <form method=\"post\" action=\"" + contextPath + "/es/agenda\">");
                        out.println("              <input type=\"hidden\" name=\"meetingId\" value=\""
                                + meeting.getEsMeetingId() + "\">");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"copyAgendaItem\">");
                        out.println("              <input type=\"hidden\" name=\"sourceItemId\" value=\""
                                + ci.getEsMeetingAgendaItemId() + "\">");
                        if (editOverride)
                            out.println("              <input type=\"hidden\" name=\"edit\" value=\"true\">");
                        out.println("              <button type=\"submit\" class=\"prev-copy-btn\">Copy</button>");
                        out.println("            </form>");
                        out.println("          </td>");
                        out.println("        </tr>");
                    }
                    out.println("        </tbody>");
                    out.println("      </table>");
                    out.println("      </div>"); // agenda-table-container
                    out.println("    </div>"); // copy-prev-meeting
                }
                out.println("  </div>"); // prev-items-section
            }

            // --- TOPIC INTEREST SECTION ---
            if (attendeeEmailForInterest != null && !agendaTopicIds.isEmpty()) {
                renderTopicInterestSection(out, contextPath, meeting, items, topicById,
                        agendaTopicIds, subsByTopicId, attendeeEmailForInterest, user);
            }

            out.println("  <div class=\"agenda-footer\">");
            if (nextMeeting != null && nextMeeting.getScheduledStart() != null) {
                String nextSeriesLabel = seriesName != null ? "Next " + seriesName + " Meeting" : "Next Meeting";
                ZoneId nextMeetingZone = safeZoneId(nextMeeting.getTimezoneId(), effectiveTz);
                ZonedDateTime nextDisplay = ZonedDateTime.of(nextMeeting.getScheduledStart(), nextMeetingZone)
                        .withZoneSameInstant(viewerZone);
                String nextDateStr = DISPLAY_DATE_FMT.format(nextDisplay);
                String nextTimeStr = DISPLAY_TIME_FMT.format(nextDisplay) + " " + nextDisplay.getZone().getId();
                out.println("    <p class=\"agenda-next-meeting\">");
                out.println("      " + escapeHtml(nextSeriesLabel) + ": <a href=\"" + contextPath
                        + "/es/agenda?meetingId=" + nextMeeting.getEsMeetingId()
                        + "\" class=\"agenda-next-meeting-link\">"
                        + escapeHtml(nextDateStr) + ", at " + escapeHtml(nextTimeStr) + "</a>");
                out.println("    </p>");
            }
            if (meeting.getEsTopicMeetingId() != null) {
                String allLabel = seriesName != null ? "All " + seriesName + " Meetings" : "All Meetings";
                out.println("    <p class=\"agenda-all-meetings\">");
                out.println("      <a href=\"" + contextPath + "/es/meetings?seriesId="
                        + meeting.getEsTopicMeetingId() + "\">" + escapeHtml(allLabel) + "</a>");
                out.println("    </p>");
            }
            out.println("  </div>");

            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("<script>");
            // Topic autocomplete data
            if (canEdit && !allTopics.isEmpty()) {
                out.print("const esTopics=[");
                for (int i = 0; i < allTopics.size(); i++) {
                    EsTopic t = allTopics.get(i);
                    if (i > 0)
                        out.print(",");
                    out.print("{id:" + t.getEsTopicId() + ",name:" + jsString(t.getTopicName()) + "}");
                }
                out.println("];");
                out.println("const esTopicNameMap={};");
                out.println("esTopics.forEach(function(t){esTopicNameMap[t.name]=t.id;});");
                out.println("(function(){");
                out.println("  var dl=document.getElementById('add-topic-list');");
                out.println("  if(!dl) return;");
                out.println(
                        "  esTopics.forEach(function(t){var o=document.createElement('option');o.value=t.name;dl.appendChild(o);});");
                out.println("})();");
                out.println("function esOnTopicInput(val){");
                out.println("  var tid=esTopicNameMap[val];");
                out.println("  document.getElementById('add-topic-id').value=tid!==undefined?tid:'';");
                out.println("  if(tid!==undefined) document.getElementById('add-item-title').value=val;");
                out.println("}");
            }
            out.println("function esShowEdit(id) {");
            out.println("  document.getElementById(id+'-display').style.display='none';");
            out.println("  var form=document.getElementById(id+'-form');");
            out.println("  form.style.display='flex';");
            out.println(
                    "  var inp=form.querySelector('input[type=text],input[type=date],input[type=time],select,textarea');");
            out.println("  if(inp) inp.focus();");
            out.println("}");
            out.println("function esHideEdit(id) {");
            out.println("  document.getElementById(id+'-display').style.display='';");
            out.println("  document.getElementById(id+'-form').style.display='none';");
            out.println("}");
            out.println("function esShowJoinDetails() {");
            out.println("  document.getElementById('join-details-toggle').style.display='none';");
            out.println("  document.getElementById('join-details-content').style.display='block';");
            out.println("}");
            out.println("function esHideJoinDetails() {");
            out.println("  document.getElementById('join-details-toggle').style.display='';");
            out.println("  document.getElementById('join-details-content').style.display='none';");
            out.println("}");
            // Presenter panel functions
            out.println("function esShowPresEdit(id) {");
            out.println("  document.getElementById(id+'-panel').style.display='flex';");
            out.println("}");
            out.println("function esHidePresEdit(id) {");
            out.println("  document.getElementById(id+'-panel').style.display='none';");
            out.println("}");
            out.println("function esShowAddPres(id) {");
            out.println("  document.getElementById(id).style.display='flex';");
            out.println("}");
            out.println("function esHideAddPres(id) {");
            out.println("  document.getElementById(id).style.display='none';");
            out.println("}");
            out.println("function esOnPresRoleChange(panelId,role,btn){");
            out.println("  var panel=document.getElementById(panelId);");
            out.println("  if(!panel) return;");
            out.println("  panel.querySelectorAll('.pres-role-input').forEach(function(inp){inp.value=role;});");
            out.println("  if(btn){var pp=btn.parentElement;if(pp){");
            out.println(
                    "    pp.querySelectorAll('.role-option').forEach(function(b){b.classList.remove('role-option-active');});");
            out.println("    btn.classList.add('role-option-active');");
            out.println("  }}");
            out.println("}");
            // User search datalist and lookup map
            if (canEdit && !allUsers.isEmpty()) {
                out.print("const esPresUsers=[");
                for (int j = 0; j < allUsers.size(); j++) {
                    User u = allUsers.get(j);
                    if (j > 0)
                        out.print(",");
                    String fullName = u.getFullName();
                    out.print("{id:" + u.getUserId() + ",name:" + jsString(fullName) + ",email:"
                            + jsString(u.getEmail()) + "}");
                }
                out.println("];");
                out.println("const esPresUserMap={};");
                out.println("esPresUsers.forEach(function(u){");
                out.println("  var label=u.name&&u.name.length>0?u.name+' <'+u.email+'>':u.email;");
                out.println("  esPresUserMap[label]=u.id;");
                out.println("});");
                out.println("document.addEventListener('DOMContentLoaded',function(){");
                out.println("  var dl=document.getElementById('pres-user-list');");
                out.println("  if(!dl) return;");
                out.println("  esPresUsers.forEach(function(u){");
                out.println("    var label=u.name&&u.name.length>0?u.name+' <'+u.email+'>':u.email;");
                out.println("    var o=document.createElement('option');o.value=label;dl.appendChild(o);");
                out.println("  });");
                out.println("});");
                out.println("function esOnPresUserInput(val,hiddenId){");
                out.println("  var uid=esPresUserMap[val];");
                out.println("  document.getElementById(hiddenId).value=uid!==undefined?uid:'';");
                out.println("}");
            }
            out.println("</script>");
            // Global datalist for presenter user search
            if (canEdit) {
                out.println("<datalist id=\"pres-user-list\"></datalist>");
            }
            out.println("</body>");
            out.println("</html>");
        }
    }

    private static String jsString(String s) {
        if (s == null)
            return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n") + "\"";
    }

    private static String titleCase(String enumName) {
        if (enumName == null)
            return "";
        return Arrays.stream(enumName.split("_"))
                .map(w -> w.isEmpty() ? w : w.substring(0, 1) + w.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private void renderAgendaStyles(PrintWriter out) {
        out.println("    body { background: #f7f9fc; }");
        out.println(
                "    .agenda-page { max-width: 900px; margin: 1.5rem auto; padding: 0 1rem; font-family: sans-serif; }");
        out.println(
                "    .agenda-cancelled-banner { background: #fee2e2; border: 1px solid #fca5a5; color: #7f1d1d; padding: 0.75rem 1rem; border-radius: 6px; margin-bottom: 1rem; font-weight: bold; }");
        out.println(
                "    .agenda-msg-success { background: #dcfce7; border: 1px solid #86efac; color: #14532d; padding: 0.5rem 1rem; border-radius: 6px; margin-bottom: 0.8rem; }");
        out.println(
                "    .agenda-msg-error { background: #fee2e2; border: 1px solid #fca5a5; color: #7f1d1d; padding: 0.5rem 1rem; border-radius: 6px; margin-bottom: 0.8rem; }");
        out.println(
                "    .agenda-header { display: flex; align-items: center; gap: 1.5rem; margin-bottom: 1.2rem; border-bottom: 2px solid #0f766e; padding-bottom: 0.75rem; }");
        out.println("    .agenda-logo { max-height: 60px; max-width: 140px; object-fit: contain; }");
        out.println(
                "    .agenda-title { font-size: 2.4rem; font-weight: 800; letter-spacing: 0.15em; color: #0f766e; margin: 0; }");
        out.println(
                "    .agenda-draft-label { color: #dc2626; font-size: 1.6rem; font-weight: 800; letter-spacing: 0.1em; margin-right: 0.4rem; }");
        out.println("    .agenda-meta { margin-bottom: 1rem; padding: 1rem; }");
        out.println(
                "    .agenda-meta-row { display: flex; flex-wrap: wrap; align-items: baseline; gap: 0.5rem; padding: 0.3rem 0; border-bottom: 1px solid #e2e8f0; }");
        out.println("    .agenda-meta-row:last-child { border-bottom: none; }");
        out.println(
                "    .agenda-meta-label { font-weight: 600; min-width: 130px; color: #475569; font-size: 0.9rem; }");
        out.println("    .agenda-meta-value { font-weight: 500; flex: 1; }");
        out.println("    .agenda-inline-form { display: flex; flex-wrap: wrap; align-items: center; gap: 0.3rem; }");
        out.println("    .click-to-edit { cursor: pointer; }");
        out.println("    .click-to-edit:hover { color: #1a56db; }");
        out.println(
                "    .agenda-inline-form input[type=text], .agenda-inline-form input[type=date], .agenda-inline-form input[type=time], .agenda-inline-form select { padding: 0.2rem 0.4rem; border: 1px solid #cbd5e1; border-radius: 4px; font-size: 0.88rem; }");
        out.println("    .agenda-tz-note { font-size: 0.8rem; color: #64748b; font-style: italic; width: 100%; }");
        out.println(
                "    .agenda-inline-form button { padding: 0.2rem 0.6rem; background: #0f766e; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.82rem; }");
        out.println(
                "    .agenda-inline-form button:disabled { opacity: 0.4; cursor: not-allowed; }");
        out.println(
                "    .agenda-section-heading { font-size: 0.95rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: #475569; margin: 0 0 0.5rem 0; }");
        out.println("    .agenda-description { margin-bottom: 1rem; padding: 1rem; }");
        out.println(
                "    .agenda-description-text { white-space: pre-wrap; font-size: 0.95rem; line-height: 1.6; color: #334155; }");
        out.println("    .agenda-attendance { margin-bottom: 1rem; padding: 1rem; }");
        out.println("    .agenda-attendance-prompt { font-size: 0.95rem; color: #475569; margin: 0 0 0.5rem 0; }");
        out.println("    .agenda-attend-link { font-weight: 600; color: #1d4ed8; text-decoration: none; }");
        out.println("    .agenda-attend-link:hover { text-decoration: underline; }");
        out.println("    .agenda-attendance-list { list-style: none; padding: 0; margin: 0.4rem 0 0.6rem 0; }");
        out.println(
                "    .agenda-attendee-row { padding: 0.3rem 0; border-bottom: 1px solid var(--border); font-size: 0.9rem; }");
        out.println("    .agenda-attendee-org { color: #64748b; }");
        out.println(
                "    .agenda-attendance-count { font-size: 0.85rem; color: #64748b; margin: 0; font-style: italic; }");
        out.println("    .agenda-muted { color: #94a3b8; font-size: 0.9rem; }");
        ;
        out.println("    .agenda-online-meeting { margin-top: 0.6rem; }");
        out.println(
                "    .agenda-join-link { font-weight: 600; color: #0f766e; text-decoration: none; font-size: 0.95rem; }");
        out.println("    .agenda-join-link:hover { text-decoration: underline; }");
        out.println("    .agenda-join-link-edit { font-size: 0.9rem; color: #334155; word-break: break-all; }");
        out.println(
                "    .agenda-join-more { cursor: pointer; color: #0369a1; font-size: 0.88rem; text-decoration: underline; }");
        out.println(
                "    .agenda-join-details { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 0.35rem 0.6rem; margin-top: 0.4rem; }");
        out.println(
                "    .agenda-join-details-text { white-space: pre-wrap; font-size: 0.82rem; color: #374151; line-height: 1; }");
        out.println(
                "    .agenda-join-details-close { font-size: 0.8rem; padding: 0.15rem 0.5rem; background: #e2e8f0; color: #334155; border: none; border-radius: 4px; cursor: pointer; }");
        out.println(
                "    .agenda-duration-warning { background: #fef9c3; border: 1px solid #fde68a; color: #713f12; padding: 0.6rem 1rem; border-radius: 6px; margin-bottom: 0.8rem; font-size: 0.92rem; }");
        out.println(
                "    .agenda-duration-info { background: #eff6ff; border: 1px solid #bfdbfe; color: #1e40af; padding: 0.6rem 1rem; border-radius: 6px; margin-bottom: 0.8rem; font-size: 0.92rem; }");
        out.println(
                "    .agenda-duration-ok { background: #f0fdf4; border: 1px solid #bbf7d0; color: #166534; padding: 0.6rem 1rem; border-radius: 6px; margin-bottom: 0.8rem; font-size: 0.92rem; }");
        out.println("    .agenda-table-container { overflow-x: auto; }");
        out.println(
                "    .agenda-table { width: 100%; border-collapse: collapse; border: 1px solid #d7deea; font-size: 0.92rem; }");
        out.println(
                "    .agenda-table th { background: #0f766e; color: white; padding: 0.55rem 0.8rem; text-align: left; font-weight: 600; font-size: 0.85rem; }");
        out.println(
                "    .agenda-table td { padding: 0.6rem 0.8rem; vertical-align: top; border-bottom: 1px solid #e2e8f0; }");
        out.println("    .agenda-table tr:last-child td { border-bottom: none; }");
        out.println("    .agenda-table tr:nth-child(even) td { background: #f8fafd; }");
        out.println(
                "    .agenda-row-cancelled td { color: #94a3b8; text-decoration: line-through; background: #f8fafd !important; }");
        out.println("    .col-topic { width: 18%; }");
        out.println("    .col-agenda { width: 42%; }");
        out.println("    .col-presenter { width: 18%; }");
        out.println("    .col-status { width: 14%; }");
        out.println("    .col-controls { width: 8%; white-space: nowrap; }");
        out.println("    .agenda-item-title { font-weight: 600; color: #0f172a; }");
        out.println("    .agenda-topic-link { color: #0f766e; text-decoration: none; font-weight: 600; }");
        out.println("    .agenda-topic-link:hover { text-decoration: underline; }");
        out.println("    .agenda-item-linked-topic { font-size: 0.78rem; color: #0f766e; margin-bottom: 0.2rem; }");
        out.println("    .agenda-item-linked-topic a { color: #0f766e; }");
        out.println("    .agenda-item-time { font-size: 0.82rem; color: #475569; margin-top: 2px; }");
        out.println("    .agenda-item-duration { font-size: 0.78rem; color: #94a3b8; }");
        out.println(
                "    .agenda-item-text { white-space: pre-wrap; font-size: 0.9rem; line-height: 1.5; color: #334155; }");
        out.println(
                "    .agenda-edit-form { margin-top: 0.4rem; display: flex; flex-direction: column; gap: 0.25rem; }");
        out.println(
                "    .agenda-edit-form input, .agenda-edit-form textarea { font-size: 0.85rem; padding: 0.25rem 0.4rem; border: 1px solid #cbd5e1; border-radius: 4px; }");
        out.println(
                "    .agenda-edit-form button { align-self: flex-start; padding: 0.2rem 0.6rem; background: #0f766e; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.82rem; }");
        out.println("    .agenda-presenter { font-size: 0.88rem; color: #334155; line-height: 1.5; }");
        out.println("    .presenter-name { cursor: pointer; }");
        out.println("    .presenter-name:hover { color: #0f766e; }");
        out.println("    .presenter-status { font-size: 0.78rem; color: #64748b; font-style: italic; }");
        out.println(
                "    .presenter-edit-panel { position: fixed; bottom: 1.5rem; left: 50%; transform: translateX(-50%); z-index: 300; width: 92%; max-width: 440px; max-height: 80vh; overflow-y: auto; padding: 0.75rem; background: #fff; border: 1px solid #94a3b8; border-radius: 8px; box-shadow: 0 8px 32px rgba(0,0,0,0.22); font-size: 0.82rem; display: flex; flex-direction: column; gap: 0.3rem; }");
        out.println("    .pres-role-label { display: flex; align-items: center; gap: 0.3rem; flex-wrap: wrap; }");
        out.println(
                "    .pres-role-label select { font-size: 0.82rem; padding: 0.15rem 0.3rem; border: 1px solid #cbd5e1; border-radius: 4px; }");
        out.println("    .pres-action-form { display: flex; flex-wrap: wrap; align-items: center; gap: 0.3rem; }");
        out.println(
                "    .pres-btn { padding: 0.15rem 0.5rem; font-size: 0.78rem; border: 1px solid #cbd5e1; border-radius: 4px; cursor: pointer; background: #f1f5f9; color: #334155; }");
        out.println("    .pres-btn:hover { background: #e2e8f0; }");
        out.println("    .pres-btn-accept { background: #dcfce7; border-color: #86efac; color: #166534; }");
        out.println("    .pres-btn-accept:hover { background: #bbf7d0; }");
        out.println("    .pres-btn-remove { background: #fee2e2; border-color: #fca5a5; color: #991b1b; }");
        out.println("    .pres-btn-remove:hover { background: #fecaca; }");
        out.println("    .pres-btn-close { background: #f1f5f9; color: #64748b; }");
        out.println(
                "    .add-pres-btn { padding: 0.15rem 0.5rem; font-size: 0.78rem; background: #f0fdf4; border: 1px solid #86efac; border-radius: 4px; color: #166534; cursor: pointer; }");
        out.println("    .add-pres-btn:hover { background: #dcfce7; }");
        out.println(
                "    .add-pres-panel { position: fixed; bottom: 1.5rem; left: 50%; transform: translateX(-50%); z-index: 300; width: 92%; max-width: 440px; max-height: 80vh; overflow-y: auto; padding: 0.75rem; background: #fff; border: 1px solid #94a3b8; border-radius: 8px; box-shadow: 0 8px 32px rgba(0,0,0,0.22); font-size: 0.82rem; display: flex; flex-direction: column; gap: 0.4rem; }");
        out.println(
                "    .quick-pick-self { background: #f0fdf4; border-color: #86efac; color: #166534; font-weight: 600; }");
        out.println("    .quick-pick-section { display: flex; flex-direction: column; gap: 0.25rem; }");
        out.println(
                "    .quick-pick-label { font-size: 0.75rem; font-weight: 600; color: #64748b; text-transform: uppercase; letter-spacing: 0.04em; }");
        out.println(
                "    .quick-pick-chip { padding: 0.15rem 0.5rem; font-size: 0.78rem; background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 12px; color: #1e40af; cursor: pointer; white-space: nowrap; }");
        out.println("    .quick-pick-chip:hover { background: #dbeafe; }");
        out.println(
                "    .pres-text-input { font-size: 0.82rem; padding: 0.2rem 0.4rem; border: 1px solid #cbd5e1; border-radius: 4px; min-width: 0; flex: 1; }");
        out.println(
                "    .agenda-item-status-badge { display: inline-block; font-size: 0.75rem; padding: 0.1rem 0.4rem; border-radius: 4px; background: #e2e8f0; color: #334155; font-weight: 600; }");
        out.println(
                "    .agenda-item-status-badge.status-green { background: #dcfce7; color: #166534; }");
        out.println(
                "    .agenda-item-status-badge.status-red { background: #fee2e2; color: #991b1b; }");
        out.println(
                "    .agenda-status-btn { display: block; font-size: 0.78rem; padding: 0.15rem 0.4rem; margin-top: 2px; background: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 4px; cursor: pointer; width: 100%; text-align: left; }");
        out.println("    .agenda-status-btn:hover { background: #e2e8f0; }");
        out.println(
                "    .col-controls button { padding: 0.1rem 0.35rem; font-size: 0.85rem; background: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 3px; cursor: pointer; }");
        out.println("    .col-controls button:hover { background: #e2e8f0; }");
        out.println(
                "    .agenda-add-item { margin: 0.8rem 0; padding: 0.6rem; background: #f8fafd; border: 1px dashed #cbd5e1; border-radius: 6px; }");
        out.println(
                "    .add-item-row { display: flex; gap: 0.5rem; align-items: center; margin-top: 0.4rem; }");
        out.println(
                "    .add-item-row:first-child { margin-top: 0; }");
        out.println(
                "    .add-item-input { flex: 1; padding: 0.25rem 0.5rem; border: 1px solid #cbd5e1; border-radius: 4px; font-size: 0.9rem; min-width: 0; }");
        out.println(
                "    .agenda-add-item button { padding: 0.25rem 0.8rem; background: #0f766e; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.88rem; white-space: nowrap; }");
        out.println(
                "    .agenda-status-controls { margin: 1rem 0; padding: 1rem; background: #f8fafd; border: 1px solid #d7deea; border-radius: 6px; }");
        out.println("    .agenda-status-controls h3 { margin: 0 0 0.6rem 0; font-size: 0.95rem; }");
        out.println(
                "    .agenda-meeting-transitions { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: flex-end; }");
        out.println(
                "    .agenda-meeting-transitions button { padding: 0.3rem 0.8rem; border: 1px solid #cbd5e1; border-radius: 4px; cursor: pointer; background: #f1f5f9; font-size: 0.88rem; }");
        out.println("    .agenda-meeting-transitions button:hover { background: #e2e8f0; }");
        out.println("    .agenda-meeting-transitions button:disabled { opacity: 0.4; cursor: not-allowed; }");
        out.println("    .agenda-finalize-blocked { color: #b42318; font-size: 0.88rem; margin-top: 0.4rem; }");
        out.println("    .agenda-edit-enable-link { font-size: 0.88rem; color: #0f766e; text-decoration: underline; }");
        out.println(
                "    .agenda-status-badge { display: inline-block; padding: 0.15rem 0.5rem; border-radius: 4px; font-size: 0.82rem; font-weight: 700; }");
        out.println("    .agenda-status-draft { background: #e2e8f0; color: #334155; }");
        out.println("    .agenda-status-proposed { background: #dbeafe; color: #1e40af; }");
        out.println("    .agenda-status-finalized { background: #dcfce7; color: #166534; }");
        out.println("    .agenda-status-completed { background: #f0fdf4; color: #166534; border: 1px solid #86efac; }");
        out.println("    .agenda-status-cancelled { background: #fee2e2; color: #7f1d1d; }");
        out.println(
                "    .agenda-footer { margin-top: 2rem; padding: 0.75rem 0; border-top: 1px solid #d7deea; font-size: 0.9rem; color: #475569; }");
        out.println("    .agenda-next-meeting { margin: 0; font-style: italic; }");
        out.println("    .agenda-next-meeting-link { color: #2563eb; text-decoration: none; }");
        out.println("    .agenda-next-meeting-link:hover { text-decoration: underline; }");
        out.println("    .agenda-all-meetings { margin: 0.35rem 0 0; }");
        out.println("    .agenda-all-meetings a { color: #475569; font-size: 0.85rem; text-decoration: none; }");
        out.println("    .agenda-all-meetings a:hover { text-decoration: underline; color: #1e40af; }");
        out.println("    @media print {");
        out.println("      .no-print { display: none !important; }");
        out.println("      body { background: white; }");
        out.println("      .agenda-page { max-width: 100%; margin: 0; padding: 0; }");
        out.println("      .agenda-header { border-bottom: 2pt solid #000; }");
        out.println("      .agenda-title { color: #000; font-size: 2rem; }");
        out.println("      .agenda-meta, .agenda-description { border: none; box-shadow: none; padding: 0.5rem 0; }");
        out.println("      .agenda-table { border: 1pt solid #000; font-size: 10pt; }");
        out.println(
                "      .agenda-table th { background: #ccc !important; color: #000; -webkit-print-color-adjust: exact; print-color-adjust: exact; }");
        out.println("      .agenda-table td { padding: 4pt 6pt; }");
        out.println("      .agenda-table tr { page-break-inside: avoid; }");
        out.println("      .agenda-duration-warning, .agenda-duration-info, .agenda-duration-ok { display: none; }");
        out.println("      .agenda-attendance { display: none; }");
        out.println("      .panel { border: none; box-shadow: none; background: transparent; }");
        out.println("    }");
        // Previous / open meeting items sections
        out.println("    .prev-items-section { margin-top: 1.5rem; }");
        out.println(
                "    .prev-items-heading { font-size: 0.78rem; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 0.06em; margin-bottom: 0.5rem; padding-left: 0.25rem; }");
        out.println(
                "    .open-items-section { margin-top: 1.5rem; border-left: 3px solid #f59e0b; padding-left: 0.6rem; }");
        out.println(
                "    .open-items-heading { font-size: 0.78rem; font-weight: 700; color: #b45309; text-transform: uppercase; letter-spacing: 0.06em; margin-bottom: 0.5rem; }");
        out.println(
                "    .open-items-subtext { font-size: 0.75rem; font-weight: 400; color: #78716c; text-transform: none; letter-spacing: 0; }");
        out.println("    .copy-prev-meeting { margin-bottom: 1rem; }");
        out.println(
                "    .copy-prev-meeting-heading { font-size: 0.8rem; font-weight: 600; color: #475569; margin-bottom: 0.3rem; padding-left: 0.25rem; }");
        out.println(
                "    .prev-items-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; background: #f8fafd; }");
        out.println(
                "    .prev-items-table th { background: #f1f5f9; color: #475569; font-weight: 600; font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.04em; padding: 0.35rem 0.5rem; border-bottom: 1px solid #e2e8f0; text-align: left; white-space: nowrap; }");
        out.println(
                "    .prev-items-table td { padding: 0.4rem 0.5rem; border-bottom: 1px solid #e2e8f0; vertical-align: top; }");
        out.println("    .prev-items-table tr:last-child td { border-bottom: none; }");
        out.println("    .prev-items-table tr:hover td { background: #f1f5f9; }");
        out.println("    .prev-item-title { max-width: 260px; }");
        out.println("    .prev-item-min { text-align: center; color: #64748b; white-space: nowrap; }");
        out.println("    .prev-item-from { font-size: 0.78rem; color: #64748b; white-space: nowrap; }");
        out.println("    .prev-item-presenters { margin-top: 0.2rem; display: flex; flex-wrap: wrap; gap: 0.2rem; }");
        out.println(
                "    .prev-presenter-chip { font-size: 0.72rem; padding: 0.1rem 0.35rem; background: #e0e7ff; border: 1px solid #c7d2fe; border-radius: 10px; color: #3730a3; }");
        out.println(
                "    .prev-item-status { font-size: 0.75rem; font-weight: 600; padding: 0.1rem 0.35rem; border-radius: 4px; white-space: nowrap; display: inline-block; }");
        out.println("    .prev-status-postponed { background: #fef9c3; color: #854d0e; border: 1px solid #fde047; }");
        out.println("    .prev-status-warn { background: #fee2e2; color: #991b1b; border: 1px solid #fca5a5; }");
        out.println("    .prev-status-neutral { background: #e2e8f0; color: #334155; border: 1px solid #cbd5e1; }");
        out.println(
                "    .prev-copy-btn { padding: 0.15rem 0.5rem; font-size: 0.78rem; background: #f0fdf4; border: 1px solid #86efac; border-radius: 4px; color: #166534; cursor: pointer; white-space: nowrap; }");
        out.println("    .prev-copy-btn:hover { background: #dcfce7; }");
        // Role picker in add-presenter panel
        out.println(
                "    .role-picker-row { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; padding-bottom: 0.4rem; border-bottom: 1px solid #e2e8f0; }");
        out.println(
                "    .role-picker-label { font-size: 0.75rem; font-weight: 600; color: #64748b; text-transform: uppercase; letter-spacing: 0.04em; white-space: nowrap; }");
        out.println("    .role-picker-toggle { display: flex; flex-wrap: wrap; gap: 0.2rem; }");
        out.println(
                "    .role-option { padding: 0.15rem 0.5rem; font-size: 0.78rem; border: 1px solid #cbd5e1; border-radius: 10px; cursor: pointer; background: #f1f5f9; color: #475569; white-space: nowrap; }");
        out.println("    .role-option:hover { background: #e2e8f0; }");
        out.println(
                "    .role-option-active { background: #0f766e; color: #fff; border-color: #0f766e; font-weight: 600; }");
        out.println("    .role-option-active:hover { background: #0d6460; }");
        // Your Agenda Items response banner
        out.println(
                "    .my-invitations-banner { background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 8px; padding: 0.9rem 1rem; margin-bottom: 1rem; }");
        out.println(
                "    .my-inv-heading { font-size: 1rem; font-weight: 700; color: #1e40af; margin-bottom: 0.2rem; }");
        out.println("    .my-inv-subtext { font-size: 0.82rem; color: #3730a3; margin-bottom: 0.7rem; }");
        out.println(
                "    .my-inv-card { background: #fff; border: 1px solid #bfdbfe; border-radius: 6px; padding: 0.6rem 0.75rem; margin-bottom: 0.5rem; }");
        out.println("    .my-inv-card:last-child { margin-bottom: 0; }");
        out.println(
                "    .my-inv-title { font-weight: 600; font-size: 0.95rem; color: #0f172a; margin-bottom: 0.2rem; }");
        out.println("    .my-inv-topic { font-size: 0.82rem; color: #0f766e; font-weight: 600; }");
        out.println("    .my-inv-sep { color: #94a3b8; margin: 0 0.25rem; }");
        out.println("    .my-inv-meta { font-size: 0.82rem; color: #475569; margin-bottom: 0.5rem; }");
        out.println("    .my-inv-actions { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }");
        out.println("    .my-inv-form { display: flex; flex-wrap: wrap; align-items: center; gap: 0.3rem; }");
        out.println(
                "    .my-inv-role-label { font-size: 0.82rem; color: #334155; display: flex; align-items: center; gap: 0.3rem; }");
        out.println(
                "    .my-inv-role-select { font-size: 0.82rem; padding: 0.15rem 0.3rem; border: 1px solid #cbd5e1; border-radius: 4px; }");
        out.println(
                "    .my-inv-note { font-size: 0.82rem; padding: 0.15rem 0.4rem; border: 1px solid #cbd5e1; border-radius: 4px; min-width: 120px; }");
        out.println(
                "    .inv-btn-accept { padding: 0.2rem 0.7rem; font-size: 0.82rem; background: #dcfce7; border: 1px solid #86efac; border-radius: 4px; color: #166534; cursor: pointer; font-weight: 600; }");
        out.println("    .inv-btn-accept:hover { background: #bbf7d0; }");
        out.println(
                "    .inv-btn-decline { padding: 0.2rem 0.7rem; font-size: 0.82rem; background: #fee2e2; border: 1px solid #fca5a5; border-radius: 4px; color: #991b1b; cursor: pointer; }");
        out.println("    .inv-btn-decline:hover { background: #fecaca; }");
        out.println(
                "    .agenda-msg-login-hint { background: #fffbeb; border: 1px solid #fcd34d; border-radius: 6px; padding: 0.6rem 0.85rem; margin-bottom: 0.75rem; font-size: 0.88rem; color: #92400e; }");
        // Topic interest section
        out.println(
                "    .es-topic-interest { margin-top: 1.5rem; background: #f0fdf4; border: 1px solid #86efac; border-radius: 8px; padding: 1rem 1.25rem; }");
        out.println(
                "    .es-topic-interest-heading { font-size: 1rem; font-weight: 700; color: #166534; margin: 0 0 0.3rem; }");
        out.println("    .es-topic-interest-notice { font-size: 0.85rem; color: #166534; margin: 0 0 0.3rem; }");
        out.println("    .es-topic-interest-desc { font-size: 0.85rem; color: #475569; margin: 0 0 0.6rem; }");
        out.println("    .es-topic-interest-list { list-style: none; padding: 0; margin: 0 0 0.75rem; }");
        out.println("    .es-topic-interest-item { padding: 0.2rem 0; }");
        out.println(
                "    .es-topic-interest-label { font-size: 0.9rem; color: #1e293b; cursor: pointer; display: flex; align-items: center; gap: 0.4rem; }");
        out.println("    .es-champion-badge { font-size: 0.75rem; color: #7c3aed; font-weight: 600; }");
        out.println(
                "    .es-topic-interest-save { padding: 0.35rem 1rem; font-size: 0.88rem; background: #16a34a; color: #fff; border: none; border-radius: 5px; cursor: pointer; font-weight: 600; }");
        out.println("    .es-topic-interest-save:hover { background: #15803d; }");
    }

    // =========================================================================
    // Error pages
    // =========================================================================

    private void renderError(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\"><head><meta charset=\"UTF-8\" />");
            out.println("<title>Not Found - InteropHub</title>");
            out.println("<link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" /></head>");
            out.println("<body><main class=\"container\">");
            out.println("<h1>Agenda Not Found</h1>");
            out.println("<p>" + escapeHtml(message) + "</p>");
            out.println("<p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("</main>");
            PageFooterRenderer.render(out);
            out.println("</body></html>");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String resolveEffectiveTz(User user, EsMeeting meeting) {
        if (user != null && user.getTimezoneId() != null && !user.getTimezoneId().isBlank()) {
            return user.getTimezoneId();
        }
        if (meeting.getTimezoneId() != null && !meeting.getTimezoneId().isBlank()) {
            return meeting.getTimezoneId();
        }
        return "America/New_York";
    }

    private ZoneId safeZoneId(String tzId, String fallback) {
        if (tzId != null && !tzId.isBlank()) {
            try {
                return ZoneId.of(tzId);
            } catch (Exception ignored) {
                // fall through
            }
        }
        try {
            return ZoneId.of(fallback);
        } catch (Exception ignored) {
            return ZoneId.of("America/New_York");
        }
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

    private String renderTimezoneSelect(String name, String selected) {
        StringBuilder sb = new StringBuilder();
        sb.append("<select name=\"").append(escapeHtml(name)).append("\">");
        for (String tz : ALLOWED_TIMEZONES.stream().sorted().collect(Collectors.toList())) {
            sb.append("<option value=\"").append(escapeHtml(tz)).append("\"");
            if (tz.equals(selected))
                sb.append(" selected");
            sb.append(">").append(escapeHtml(tz)).append("</option>");
        }
        sb.append("</select>");
        return sb.toString();
    }

    private List<AgendaItemStatus> validItemTransitions(AgendaItemStatus from) {
        switch (from) {
            case DRAFT:
                return List.of(AgendaItemStatus.PROPOSED, AgendaItemStatus.ACCEPTED,
                        AgendaItemStatus.NEEDS_REVISION, AgendaItemStatus.POSTPONED,
                        AgendaItemStatus.CANCELLED);
            case PROPOSED:
                return List.of(AgendaItemStatus.ACCEPTED, AgendaItemStatus.NEEDS_REVISION,
                        AgendaItemStatus.POSTPONED, AgendaItemStatus.CANCELLED);
            case ACCEPTED:
                return List.of(AgendaItemStatus.POSTPONED, AgendaItemStatus.COVERED,
                        AgendaItemStatus.NOT_COVERED, AgendaItemStatus.CANCELLED);
            case NEEDS_REVISION:
                return List.of(AgendaItemStatus.ACCEPTED, AgendaItemStatus.PROPOSED,
                        AgendaItemStatus.POSTPONED, AgendaItemStatus.CANCELLED);
            case POSTPONED:
                return List.of(AgendaItemStatus.CANCELLED);
            default:
                return List.of();
        }
    }

    private List<MeetingStatus> validMeetingTransitions(MeetingStatus from) {
        switch (from) {
            case DRAFT:
                return List.of(MeetingStatus.PROPOSED, MeetingStatus.FINALIZED, MeetingStatus.CANCELLED);
            case PROPOSED:
                return List.of(MeetingStatus.FINALIZED, MeetingStatus.COMPLETED, MeetingStatus.CANCELLED);
            case FINALIZED:
                return List.of(MeetingStatus.COMPLETED, MeetingStatus.CANCELLED);
            case COMPLETED:
                return List.of(MeetingStatus.FINALIZED);
            default:
                return List.of();
        }
    }

    private boolean isMeetingStarted(EsMeeting meeting) {
        LocalDateTime start = meeting.getScheduledStart();
        String tzId = meeting.getTimezoneId();
        if (tzId != null) {
            try {
                ZoneId zone = ZoneId.of(tzId);
                return !ZonedDateTime.now(zone).isBefore(start.atZone(zone));
            } catch (Exception ex) {
                // fall through to raw comparison
            }
        }
        return !LocalDateTime.now().isBefore(start);
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

    // =========================================================================
    // Topic interest helpers
    // =========================================================================

    /**
     * Handles POST action=updateTopicInterest.
     * Allowed for both authenticated users and anonymous attendees (identified
     * by a hidden attendeeEmail form field echoed from the topic interest form).
     */
    private void handleUpdateTopicInterest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String contextPath = request.getContextPath();
        Long meetingId = parseId(trimToNull(request.getParameter("meetingId")));
        if (meetingId == null) {
            response.sendRedirect(contextPath + "/es/agenda");
            return;
        }

        // Determine the attendee's normalized email.
        Optional<User> userOpt = authFlowService.findAuthenticatedUser(request);
        String emailNormalized;
        if (userOpt.isPresent()) {
            emailNormalized = userOpt.get().getEmailNormalized();
        } else {
            emailNormalized = trimToNull(request.getParameter("attendeeEmail"));
            if (emailNormalized == null) {
                response.sendRedirect(contextPath + "/es/agenda?meetingId=" + meetingId);
                return;
            }
        }

        // Collect topic IDs that were shown to the user (hidden topicInterestAll
        // fields).
        String[] allIds = request.getParameterValues("topicInterestAll");
        Set<Long> allShownTopicIds = new HashSet<>();
        if (allIds != null) {
            for (String id : allIds) {
                Long tid = parseId(id);
                if (tid != null) {
                    allShownTopicIds.add(tid);
                }
            }
        }

        // Collect the topic IDs the user checked.
        String[] checkedIds = request.getParameterValues("topicInterest");
        Set<Long> checkedTopicIds = new HashSet<>();
        if (checkedIds != null) {
            for (String id : checkedIds) {
                Long tid = parseId(id);
                if (tid != null) {
                    checkedTopicIds.add(tid);
                }
            }
        }

        if (!allShownTopicIds.isEmpty()) {
            // Load existing TOPIC subscriptions for this email.
            List<EsSubscription> existingSubs = subscriptionDao.findByEmailNormalizedAndType(
                    emailNormalized, EsSubscription.SubscriptionType.TOPIC);
            Map<Long, EsSubscription> existingByTopic = new java.util.LinkedHashMap<>();
            for (EsSubscription sub : existingSubs) {
                if (sub.getEsTopicId() != null && allShownTopicIds.contains(sub.getEsTopicId())) {
                    existingByTopic.merge(sub.getEsTopicId(), sub, EsAgendaServlet::preferHigherRankSub);
                }
            }

            for (Long topicId : allShownTopicIds) {
                EsSubscription existing = existingByTopic.get(topicId);
                // Never touch CHAMPION subscriptions.
                if (existing != null && existing.getStatus() == EsSubscription.SubscriptionStatus.CHAMPION) {
                    continue;
                }
                boolean checked = checkedTopicIds.contains(topicId);
                if (checked) {
                    EsSubscription newSub = new EsSubscription();
                    newSub.setEmail(emailNormalized);
                    newSub.setEmailNormalized(emailNormalized);
                    newSub.setSubscriptionType(EsSubscription.SubscriptionType.TOPIC);
                    newSub.setEsTopicId(topicId);
                    newSub.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
                    if (userOpt.isPresent()) {
                        newSub.setUserId(userOpt.get().getUserId());
                    }
                    esInterestService.subscribeOrUpdate(newSub);
                } else if (existing != null
                        && existing.getStatus() == EsSubscription.SubscriptionStatus.SUBSCRIBED) {
                    subscriptionDao.setTopicSubscriptionStatus(
                            existing.getEsSubscriptionId(),
                            EsSubscription.SubscriptionStatus.UNSUBSCRIBED,
                            LocalDateTime.now());
                }
            }
        }

        // Clear the attended-email session attribute now that the form has been
        // processed.
        jakarta.servlet.http.HttpSession sess = request.getSession(false);
        if (sess != null) {
            sess.removeAttribute("interophub.lastAttendedEmail");
        }

        response.sendRedirect(contextPath + "/es/agenda?meetingId=" + meetingId + "&topicsSaved=1");
    }

    /**
     * Renders the "Topics of Interest" section onto the agenda page.
     * Shown to any visitor who either has a session-linked email (anonymous
     * attendee)
     * or is authenticated.
     */
    private void renderTopicInterestSection(PrintWriter out, String contextPath, EsMeeting meeting,
            List<EsMeetingAgendaItem> items, Map<Long, EsTopic> topicById,
            List<Long> agendaTopicIds, Map<Long, EsSubscription> subsByTopicId,
            String attendeeEmail, User user) {
        // Collect items that are topic-linked and not cancelled, preserving agenda
        // order.
        List<EsMeetingAgendaItem> topicItems = items.stream()
                .filter(i -> i.getEsTopicId() != null && i.getStatus() != AgendaItemStatus.CANCELLED)
                .collect(Collectors.toList());
        if (topicItems.isEmpty()) {
            return;
        }

        out.println("  <div class=\"es-topic-interest no-print\">");
        out.println("    <h2 class=\"es-topic-interest-heading\">Topics of Interest</h2>");
        if (user == null) {
            out.println("    <p class=\"es-topic-interest-notice\">You're registered as <strong>"
                    + escapeHtml(attendeeEmail) + "</strong>.</p>");
        }
        out.println("    <p class=\"es-topic-interest-desc\">Check the topics you'd like to follow:</p>");
        out.println("    <form method=\"post\" action=\"" + contextPath + "/es/agenda\">");
        out.println("      <input type=\"hidden\" name=\"action\" value=\"updateTopicInterest\">");
        out.println("      <input type=\"hidden\" name=\"meetingId\" value=\"" + meeting.getEsMeetingId() + "\">");
        if (user == null) {
            out.println("      <input type=\"hidden\" name=\"attendeeEmail\" value=\""
                    + escapeHtml(attendeeEmail) + "\">");
        }
        out.println("      <ul class=\"es-topic-interest-list\">");
        for (EsMeetingAgendaItem item : topicItems) {
            Long topicId = item.getEsTopicId();
            EsTopic topic = topicById.get(topicId);
            if (topic == null) {
                continue;
            }
            EsSubscription sub = subsByTopicId.get(topicId);
            boolean isChampion = sub != null && sub.getStatus() == EsSubscription.SubscriptionStatus.CHAMPION;
            boolean isChecked = sub != null && (sub.getStatus() == EsSubscription.SubscriptionStatus.SUBSCRIBED
                    || sub.getStatus() == EsSubscription.SubscriptionStatus.CHAMPION);
            out.println("        <li class=\"es-topic-interest-item\">");
            // Hidden field to track which topics were shown (needed for unsubscribe logic).
            out.println("          <input type=\"hidden\" name=\"topicInterestAll\" value=\"" + topicId + "\">");
            out.println("          <label class=\"es-topic-interest-label\">");
            out.print("            <input type=\"checkbox\" name=\"topicInterest\" value=\"" + topicId + "\""
                    + (isChecked ? " checked" : "") + "> ");
            out.print(escapeHtml(topic.getTopicName()));
            if (isChampion) {
                out.print(" <span class=\"es-champion-badge\">(champion)</span>");
            }
            out.println();
            out.println("          </label>");
            out.println("        </li>");
        }
        out.println("      </ul>");
        out.println("      <button type=\"submit\" class=\"es-topic-interest-save\">Save Topic Interests</button>");
        out.println("    </form>");
        out.println("  </div>");
    }

    /**
     * Merge helper: returns the subscription with higher status rank.
     * Priority: CHAMPION > SUBSCRIBED > others.
     */
    private static EsSubscription preferHigherRankSub(EsSubscription a, EsSubscription b) {
        if (a.getStatus() == EsSubscription.SubscriptionStatus.CHAMPION) {
            return a;
        }
        if (b.getStatus() == EsSubscription.SubscriptionStatus.CHAMPION) {
            return b;
        }
        if (a.getStatus() == EsSubscription.SubscriptionStatus.SUBSCRIBED) {
            return a;
        }
        return b;
    }

}
