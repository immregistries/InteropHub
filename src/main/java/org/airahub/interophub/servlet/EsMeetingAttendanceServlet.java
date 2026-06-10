package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsMeetingAttendanceDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EmailSendLogDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.model.EsMeetingAttendance;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EmailSendLog;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.AuthService;
import org.airahub.interophub.service.EmailReason;
import org.airahub.interophub.service.EmailService;
import org.airahub.interophub.service.EmailTemplates;
import org.airahub.interophub.service.EsNormalizer;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.service.EsInterestService;
import org.airahub.interophub.model.EsTopicMeetingSurvey;
import org.airahub.interophub.service.EsSurveyService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

public class EsMeetingAttendanceServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(EsMeetingAttendanceServlet.class.getName());

    private final EsTopicDao topicDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsMeetingDao meetingDao;
    private final EsMeetingAttendanceDao attendanceDao;
    private final EsMeetingAgendaItemDao agendaItemDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsInterestService esInterestService;
    private final AuthFlowService authFlowService;
    private final AuthService authService;
    private final EmailService emailService;
    private final EmailSendLogDao emailSendLogDao;
    private final EsSurveyService esSurveyService;

    public EsMeetingAttendanceServlet() {
        this.topicDao = new EsTopicDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.meetingDao = new EsMeetingDao();
        this.attendanceDao = new EsMeetingAttendanceDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.esInterestService = new EsInterestService();
        this.authFlowService = new AuthFlowService();
        this.authService = new AuthService();
        this.emailService = new EmailService();
        this.emailSendLogDao = new EmailSendLogDao();
        this.esSurveyService = new EsSurveyService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ParsedPath parsed = parseTopicCode(request);
        if (parsed == null) {
            renderNotFound(response, request.getContextPath(), "Meeting link is incomplete.");
            return;
        }

        MeetingResolution resolution = resolveMeeting(parsed);
        if (!resolution.valid()) {
            renderNotFound(response, request.getContextPath(), resolution.errorMessage());
            return;
        }

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        boolean submitted = "1".equals(request.getParameter("submitted"));
        boolean anonymousMode = "1".equals(request.getParameter("anon"));

        String emailForSubs = authenticatedUser.map(User::getEmailNormalized).orElse(null);
        Long userIdForSubs = authenticatedUser.map(User::getUserId).orElse(null);
        TopicInterestData topicData = loadTopicInterestData(resolution, emailForSubs, userIdForSubs);

        renderPage(response, request.getContextPath(), resolution, authenticatedUser.orElse(null),
                anonymousMode, null, null, null, null, null, false, topicData, submitted);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        ParsedPath parsed = parseTopicCode(request);
        if (parsed == null) {
            renderNotFound(response, request.getContextPath(), "Meeting link is incomplete.");
            return;
        }

        MeetingResolution resolution = resolveMeeting(parsed);
        if (!resolution.valid()) {
            renderNotFound(response, request.getContextPath(), resolution.errorMessage());
            return;
        }

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);

        // Pre-compute topic data for form re-renders (best-effort email for
        // pre-checking boxes)
        String previewEmail = authenticatedUser.map(User::getEmailNormalized)
                .orElse(EsNormalizer.normalizeEmail(trimToNull(request.getParameter("email"))));
        Long previewUserId = authenticatedUser.map(User::getUserId).orElse(null);
        TopicInterestData topicData = loadTopicInterestData(resolution, previewEmail, previewUserId);

        // Determine whether the user is submitting in "anonymous" mode
        // (either not logged in, or clicked "register under different info")
        boolean anonymousMode = "true".equals(request.getParameter("anonymousMode"))
                || authenticatedUser.isEmpty();

        boolean attending = request.getParameter("attending") != null;
        if (!attending) {
            renderPage(response, request.getContextPath(), resolution, authenticatedUser.orElse(null),
                    anonymousMode, "Please check the attendance checkbox to confirm you are attending.",
                    trimToNull(request.getParameter("firstName")),
                    trimToNull(request.getParameter("lastName")),
                    trimToNull(request.getParameter("email")),
                    trimToNull(request.getParameter("organization")),
                    false, topicData, false);
            return;
        }

        String firstName;
        String lastName;
        String email;
        String organization;
        Long userId;

        if (!anonymousMode && authenticatedUser.isPresent()) {
            User user = authenticatedUser.get();
            firstName = trimToNull(request.getParameter("firstName"));
            if (firstName == null) {
                firstName = orEmpty(user.getFirstName());
            }
            lastName = trimToNull(request.getParameter("lastName"));
            if (lastName == null) {
                lastName = orEmpty(user.getLastName());
            }
            email = user.getEmail();
            organization = trimToNull(request.getParameter("organization"));
            if (organization == null) {
                organization = user.getOrganization();
            }
            userId = user.getUserId();
        } else {
            firstName = trimToNull(request.getParameter("firstName"));
            lastName = trimToNull(request.getParameter("lastName"));
            email = trimToNull(request.getParameter("email"));
            organization = trimToNull(request.getParameter("organization"));
            userId = null;
        }

        if (firstName == null || firstName.isBlank()) {
            renderPage(response, request.getContextPath(), resolution, authenticatedUser.orElse(null),
                    anonymousMode, "First name is required.",
                    firstName, lastName, email, organization, false, topicData, false);
            return;
        }
        if (anonymousMode && (email == null || email.isBlank())) {
            renderPage(response, request.getContextPath(), resolution, authenticatedUser.orElse(null),
                    anonymousMode, "Email address is required.",
                    firstName, lastName, email, organization, false, topicData, false);
            return;
        }

        String emailNormalized = anonymousMode
                ? EsNormalizer.normalizeEmail(email)
                : EsNormalizer.normalizeEmail(authenticatedUser.map(User::getEmail).orElse(email));

        if (emailNormalized == null) {
            renderPage(response, request.getContextPath(), resolution, authenticatedUser.orElse(null),
                    anonymousMode, "A valid email address is required.",
                    firstName, lastName, email, organization, false, topicData, false);
            return;
        }

        String hopeText = trimToNull(request.getParameter("hopeText"));
        boolean sendLink = "sendLink".equals(request.getParameter("registrationAction"));

        LocalDate today = LocalDate.now();
        Long meetingId = resolution.meeting().getEsTopicMeetingId();

        Optional<EsMeetingAttendance> existing = attendanceDao.findByMeetingIdDateAndEmailNormalized(meetingId, today,
                emailNormalized);

        EsMeetingAttendance record;
        if (existing.isPresent()) {
            record = existing.get();
            record.setFirstName(firstName);
            record.setLastName(lastName);
            if (!anonymousMode && userId != null) {
                record.setUserId(userId);
            }
            record.setOrganization(organization);
            if (hopeText != null) {
                record.setHopeText(hopeText);
            }
        } else {
            record = new EsMeetingAttendance();
            record.setEsTopicMeetingId(meetingId);
            record.setAttendanceDate(today);
            record.setUserId(userId);
            record.setFirstName(firstName);
            record.setLastName(lastName);
            record.setEmail(anonymousMode ? email : authenticatedUser.map(User::getEmail).orElse(email));
            record.setEmailNormalized(emailNormalized);
            record.setOrganization(organization);
            record.setHopeText(hopeText);
        }

        // Link to the explicit meeting (if provided via
        // /attend/{topicCode}/{meetingKey})
        // or to today's auto-detected meeting (if any).
        Long esTopicMeetingId = resolution.meeting().getEsTopicMeetingId();
        Optional<EsMeeting> resolvedMeeting;
        if (resolution.explicitMeeting() != null) {
            resolvedMeeting = Optional.of(resolution.explicitMeeting());
        } else {
            resolvedMeeting = findTodaysMeeting(esTopicMeetingId);
        }
        resolvedMeeting.ifPresent(m -> record.setEsMeetingId(m.getEsMeetingId()));

        try {
            attendanceDao.saveOrUpdate(record);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to save meeting attendance", ex);
            renderPage(response, request.getContextPath(), resolution, authenticatedUser.orElse(null),
                    anonymousMode, "Could not save your attendance. Please try again.",
                    firstName, lastName, email, organization, false, topicData, false);
            return;
        }

        if (sendLink && anonymousMode) {
            sendMagicLinkEmail(request, emailNormalized, firstName, lastName, organization,
                    resolution.topic().getTopicCode());
        }

        // Process topic interest subscriptions from the attendance form.
        processTopicInterest(request, emailNormalized, userId, topicData);

        // Store normalized email in session so the agenda page can show the
        // topic-interest section for this anonymous (or authenticated) attendee.
        request.getSession().setAttribute("interophub.lastAttendedEmail", emailNormalized);

        String agendaUrl = buildAgendaUrl(request.getContextPath(), resolvedMeeting, esTopicMeetingId);
        try {
            Optional<EsTopicMeetingSurvey> pendingSurvey = esSurveyService.findPendingSurveyForAttendance(record,
                    authenticatedUser.orElse(null));
            if (pendingSurvey.isPresent()) {
                String returnUrlEncoded = URLEncoder.encode(agendaUrl, StandardCharsets.UTF_8);
                response.sendRedirect(request.getContextPath() + "/es/survey?assignmentId="
                        + pendingSurvey.get().getEsTopicMeetingSurveyId()
                        + "&returnUrl=" + returnUrlEncoded);
                return;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Survey check failed, continuing to agenda", ex);
        }

        response.sendRedirect(agendaUrl);
    }

    // -------------------------------------------------------------------------
    // Agenda redirect helper
    // -------------------------------------------------------------------------

    /**
     * Finds today's best matching EsMeeting for the given series
     * (esTopicMeetingId).
     * Priority:
     * 1. Only one meeting today → return it
     * 2. Multiple today → prefer one where now is between start and end
     * 3. Otherwise → pick the one with scheduledStart closest to now (absolute)
     * Returns empty if no today meetings exist.
     */
    private Optional<EsMeeting> findTodaysMeeting(Long esTopicMeetingId) {
        if (esTopicMeetingId == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        List<EsMeeting> all = meetingDao.findAllBySeriesDesc(esTopicMeetingId);
        List<EsMeeting> todayMeetings = all.stream()
                .filter(m -> m.getScheduledStart() != null
                        && m.getScheduledStart().toLocalDate().equals(today)
                        && m.getStatus() != EsMeeting.MeetingStatus.CANCELLED)
                .toList();
        if (todayMeetings.isEmpty()) {
            return Optional.empty();
        }
        if (todayMeetings.size() == 1) {
            return Optional.of(todayMeetings.get(0));
        }
        // Multiple today: prefer one where now is between start and end
        Optional<EsMeeting> inProgress = todayMeetings.stream()
                .filter(m -> m.getScheduledStart() != null && !m.getScheduledStart().isAfter(now)
                        && (m.getScheduledEnd() == null || m.getScheduledEnd().isAfter(now)))
                .findFirst();
        if (inProgress.isPresent()) {
            return inProgress;
        }
        // Closest by absolute difference of scheduledStart to now
        return todayMeetings.stream()
                .filter(m -> m.getScheduledStart() != null)
                .min(Comparator.comparingLong(m -> Math.abs(
                        java.time.Duration.between(m.getScheduledStart(), now).toMinutes())));
    }

    /**
     * Builds the agenda redirect URL given a resolved meeting (or empty) and
     * the series ID as a fallback.
     */
    private String buildAgendaUrl(String contextPath, Optional<EsMeeting> meeting, Long esTopicMeetingId) {
        if (meeting.isPresent()) {
            return contextPath + "/es/agenda?meetingId=" + meeting.get().getEsMeetingId();
        }
        if (esTopicMeetingId != null) {
            return contextPath + "/es/meetings?seriesId=" + esTopicMeetingId;
        }
        return contextPath + "/es/topics";
    }

    // -------------------------------------------------------------------------
    // Magic link helper
    // -------------------------------------------------------------------------

    private void sendMagicLinkEmail(HttpServletRequest request, String emailNormalized,
            String firstName, String lastName, String organization, String topicCode) {
        try {
            Optional<User> existing = authService.findUserByEmail(emailNormalized);
            User user;
            if (existing.isPresent()) {
                user = existing.get();
            } else {
                user = authService.registerUser(emailNormalized, firstName, lastName, organization, null);
            }
            AuthFlowService.IssuedMagicLink issued = authFlowService.issueMagicLinkWithMetadata(user, request, null);
            String emailSubject = EmailTemplates.meetingMagicLinkSubject();
            String emailBody = EmailTemplates.meetingMagicLinkBody(issued.getMagicLinkUrl());
            EmailService.SendResult sendResult = emailService.send(emailNormalized, emailSubject, emailBody);

            EmailSendLog logEntry = new EmailSendLog();
            logEntry.setEmailReason(EmailReason.MEETING_MAGIC_LINK);
            logEntry.setRecipientEmail(emailNormalized);
            logEntry.setRecipientEmailNormalized(emailNormalized);
            logEntry.setUserId(user.getUserId());
            logEntry.setSubject(emailSubject);
            logEntry.setBodyText(emailBody);
            logEntry.setSmtpMessageId(sendResult.getSmtpMessageId());
            logEntry.setSmtpProvider(sendResult.getSmtpProvider());
            logEntry.setMagicId(issued.getMagicId());
            emailSendLogDao.log(logEntry);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not send magic link email to " + emailNormalized, ex);
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void renderPage(HttpServletResponse response, String contextPath, MeetingResolution resolution,
            User authenticatedUser, boolean anonymousMode, String errorMessage,
            String firstName, String lastName, String email, String organization,
            boolean generalUpdatesOptIn, TopicInterestData topicData, boolean submitted) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        String topicCodeEncoded = URLEncoder.encode(resolution.topic().getTopicCode(), StandardCharsets.UTF_8);
        LocalDate today = LocalDate.now();
        List<EsMeetingAttendance> todayAttendees = attendanceDao
                .findByMeetingIdAndDate(resolution.meeting().getEsTopicMeetingId(), today);

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Meeting Attendance - " + escapeHtml(resolution.meeting().getMeetingName())
                    + " - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container attend-page\">");

            out.println(
                    "    <h1 class=\"attend-heading\">" + escapeHtml(resolution.meeting().getMeetingName()) + "</h1>");
            if (resolution.meeting().getMeetingDescription() != null
                    && !resolution.meeting().getMeetingDescription().isBlank()) {
                out.println("    <p class=\"attend-description\">"
                        + escapeHtml(resolution.meeting().getMeetingDescription()) + "</p>");
            }

            if (submitted) {
                out.println("    <p class=\"attend-success-msg\">Your attendance has been recorded. Thank you!</p>");
            }

            // --- Registration form ---
            out.println("    <form class=\"attend-form\" method=\"post\" action=\""
                    + contextPath + "/attend/" + topicCodeEncoded + "\">");

            if (errorMessage != null) {
                out.println("      <p class=\"attend-error\">" + escapeHtml(errorMessage) + "</p>");
            }

            // Logged-in, non-anonymous mode: show current user info (pre-populated,
            // editable)
            if (authenticatedUser != null && !anonymousMode) {
                out.println("      <input type=\"hidden\" name=\"anonymousMode\" value=\"false\" />");
                out.println("      <p class=\"attend-logged-in-notice\">Registering as: <strong>"
                        + escapeHtml(authenticatedUser.getFirstName() + " " + orEmpty(authenticatedUser.getLastName()))
                                .trim()
                        + "</strong> &lt;" + escapeHtml(authenticatedUser.getEmail()) + "&gt;</p>");

                String displayFirst = firstName != null ? firstName : orEmpty(authenticatedUser.getFirstName());
                String displayLast = lastName != null ? lastName : orEmpty(authenticatedUser.getLastName());
                String displayOrg = organization != null ? organization : orEmpty(authenticatedUser.getOrganization());

                out.println("      <label for=\"firstName\">First Name</label>");
                out.println("      <input id=\"firstName\" name=\"firstName\" type=\"text\" value=\""
                        + escapeHtml(displayFirst) + "\" />");

                out.println("      <label for=\"lastName\">Last Name</label>");
                out.println("      <input id=\"lastName\" name=\"lastName\" type=\"text\" value=\""
                        + escapeHtml(displayLast) + "\" />");

                out.println("      <label for=\"organization\">Organization</label>");
                out.println("      <input id=\"organization\" name=\"organization\" type=\"text\" value=\""
                        + escapeHtml(displayOrg) + "\" />");

                out.println("      <p class=\"attend-mode-toggle\">"
                        + "<a href=\"" + contextPath + "/attend/" + topicCodeEncoded + "?anon=1\">"
                        + "Register under a different name or email</a></p>");

            } else {
                // Anonymous form (not logged in, or toggled)
                out.println("      <input type=\"hidden\" name=\"anonymousMode\" value=\"true\" />");

                out.println(
                        "      <label for=\"firstName\">First Name <span class=\"attend-required\">*</span></label>");
                out.println("      <input id=\"firstName\" name=\"firstName\" type=\"text\" required value=\""
                        + escapeHtml(orEmpty(firstName)) + "\" />");

                out.println("      <label for=\"lastName\">Last Name</label>");
                out.println("      <input id=\"lastName\" name=\"lastName\" type=\"text\" value=\""
                        + escapeHtml(orEmpty(lastName)) + "\" />");

                out.println("      <label for=\"email\">Email <span class=\"attend-required\">*</span></label>");
                out.println("      <input id=\"email\" name=\"email\" type=\"email\" required value=\""
                        + escapeHtml(orEmpty(email)) + "\" />");

                out.println("      <label for=\"organization\">Organization</label>");
                out.println("      <input id=\"organization\" name=\"organization\" type=\"text\" value=\""
                        + escapeHtml(orEmpty(organization)) + "\" />");

                out.println("      <fieldset class=\"attend-action-fieldset\">");
                out.println("        <legend>Account Options</legend>");
                out.println("        <label class=\"attend-radio-label\">");
                out.println(
                        "          <input type=\"radio\" name=\"registrationAction\" value=\"registerOnly\" checked />");
                out.println("          Register me for today&rsquo;s meeting");
                out.println("        </label>");
                out.println("        <label class=\"attend-radio-label\">");
                out.println("          <input type=\"radio\" name=\"registrationAction\" value=\"sendLink\" />");
                out.println("          Register me and send a link to access InteropHub");
                out.println("        </label>");
                out.println("      </fieldset>");
            }

            // Meeting attendance checkbox (always shown)
            out.println("      <div class=\"attend-checkbox-row\">");
            out.println("        <label class=\"attend-checkbox-label\">");
            out.println("          <input type=\"checkbox\" name=\"attending\" value=\"1\" checked required />");
            out.println("          I am attending today&rsquo;s meeting: <strong>"
                    + escapeHtml(resolution.meeting().getMeetingName()) + "</strong>");
            out.println("        </label>");
            out.println("      </div>");

            // Topics of interest (if agenda is known for this meeting)
            renderTopicInterestFields(out, topicData);

            // Hope text (optional)
            out.println(
                    "      <label for=\"hopeText\">What are you hoping to get out of the meeting today? (optional)</label>");
            out.println("      <textarea id=\"hopeText\" name=\"hopeText\" rows=\"3\" class=\"attend-hope-textarea\">"
                    + escapeHtml(orEmpty(null)) + "</textarea>");

            out.println("      <button type=\"submit\" class=\"attend-submit-btn\">Submit Attendance</button>");
            out.println("    </form>");

            // --- Today's attendees ---
            renderAttendeeSections(out, todayAttendees, today);

            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderAttendeeSections(PrintWriter out, List<EsMeetingAttendance> attendees, LocalDate date) {
        out.println("    <section class=\"attend-attendees-section\">");
        out.println("      <h2 class=\"attend-attendees-heading\">Today&rsquo;s Attendees</h2>");

        if (attendees.isEmpty()) {
            out.println("      <p class=\"attend-no-attendees\">No attendees recorded yet for today.</p>");
        } else {
            out.println("      <table class=\"attend-attendee-table\">");
            out.println("        <thead>");
            out.println("          <tr>");
            out.println("            <th>Name</th>");
            out.println("            <th>Organization</th>");
            out.println("            <th>Email</th>");
            out.println("          </tr>");
            out.println("        </thead>");
            out.println("        <tbody>");
            for (EsMeetingAttendance a : attendees) {
                String name = escapeHtml(orEmpty(a.getFirstName()));
                if (a.getLastName() != null && !a.getLastName().isBlank()) {
                    name = name + " " + escapeHtml(a.getLastName());
                }
                out.println("          <tr>");
                out.println("            <td>" + name + "</td>");
                out.println("            <td>" + escapeHtml(orEmpty(a.getOrganization())) + "</td>");
                out.println("            <td>" + escapeHtml(orEmpty(a.getEmail())) + "</td>");
                out.println("          </tr>");
            }
            out.println("        </tbody>");
            out.println("      </table>");

            // Hopes section — only rendered if at least one hope_text is non-empty
            boolean anyHopes = attendees.stream()
                    .anyMatch(a -> a.getHopeText() != null && !a.getHopeText().isBlank());
            if (anyHopes) {
                out.println("      <div class=\"attend-hopes-section\">");
                out.println(
                        "        <h3 class=\"attend-hopes-heading\">What attendees are hoping to get out of today&rsquo;s meeting</h3>");
                out.println("        <ul class=\"attend-hopes-list\">");
                for (EsMeetingAttendance a : attendees) {
                    if (a.getHopeText() != null && !a.getHopeText().isBlank()) {
                        String name = escapeHtml(orEmpty(a.getFirstName()));
                        if (a.getLastName() != null && !a.getLastName().isBlank()) {
                            name = name + " " + escapeHtml(a.getLastName());
                        }
                        out.println("          <li><strong>" + name + ":</strong> "
                                + escapeHtml(a.getHopeText()) + "</li>");
                    }
                }
                out.println("        </ul>");
                out.println("      </div>");
            }
        }

        out.println("    </section>");
    }

    private void renderNotFound(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Meeting Not Found - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Meeting Not Found</h1>");
            out.println("    <p>" + escapeHtml(orEmpty(message)) + "</p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    // -------------------------------------------------------------------------
    // Path + resolution helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the path info into a (topicCode, optional meetingKey) pair.
     * Accepts:
     * /topicCode → ParsedPath(topicCode, null)
     * /topicCode/meetingKey → ParsedPath(topicCode, meetingKey)
     * Returns null for any other format (missing, empty, 3+ segments).
     */
    private ParsedPath parseTopicCode(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || !pathInfo.startsWith("/")) {
            return null;
        }
        String trimmed = pathInfo.substring(1);
        String[] parts = trimmed.split("/", -1);
        if (parts.length == 1) {
            String topicCode = trimToNull(parts[0]);
            return topicCode != null ? new ParsedPath(topicCode, null) : null;
        }
        if (parts.length == 2) {
            String topicCode = trimToNull(parts[0]);
            String meetingKey = trimToNull(parts[1]);
            return (topicCode != null && meetingKey != null) ? new ParsedPath(topicCode, meetingKey) : null;
        }
        return null;
    }

    private MeetingResolution resolveMeeting(ParsedPath parsed) {
        Optional<EsTopic> topicOpt = topicDao.findByTopicCode(parsed.topicCode());
        if (topicOpt.isEmpty()) {
            return MeetingResolution.invalid("No meeting was found for this link.");
        }
        EsTopic topic = topicOpt.get();
        Optional<EsTopicMeeting> meetingOpt = topicMeetingDao.findByTopicId(topic.getEsTopicId())
                .filter(m -> m.getStatus() == EsTopicMeeting.MeetingStatus.ACTIVE);
        if (meetingOpt.isEmpty()) {
            return MeetingResolution.invalid("No active meeting is configured for this topic.");
        }
        EsTopicMeeting topicMeeting = meetingOpt.get();
        if (parsed.meetingKey() == null) {
            return MeetingResolution.valid(topic, topicMeeting, null);
        }
        // Explicit meeting key: validate the meeting belongs to this series.
        Optional<EsMeeting> explicitOpt = meetingDao.findByMeetingKey(parsed.meetingKey());
        if (explicitOpt.isEmpty()) {
            return MeetingResolution.invalid("The meeting link is not valid.");
        }
        EsMeeting explicit = explicitOpt.get();
        if (!topicMeeting.getEsTopicMeetingId().equals(explicit.getEsTopicMeetingId())) {
            return MeetingResolution.invalid("The meeting link is not valid for this topic.");
        }
        return MeetingResolution.valid(topic, topicMeeting, explicit);
    }

    // -------------------------------------------------------------------------
    // Small utility records
    // -------------------------------------------------------------------------

    private record ParsedPath(String topicCode, String meetingKey) {
    }

    private record MeetingResolution(boolean valid, EsTopic topic, EsTopicMeeting meeting,
            EsMeeting explicitMeeting, String errorMessage) {
        static MeetingResolution valid(EsTopic topic, EsTopicMeeting meeting, EsMeeting explicitMeeting) {
            return new MeetingResolution(true, topic, meeting, explicitMeeting, null);
        }

        static MeetingResolution invalid(String message) {
            return new MeetingResolution(false, null, null, null, message);
        }
    }

    // -------------------------------------------------------------------------
    // String utilities
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Topic interest helpers
    // -------------------------------------------------------------------------

    private TopicInterestData loadTopicInterestData(MeetingResolution resolution,
            String emailNormalized, Long userId) {
        Long esTopicMeetingId = resolution.meeting().getEsTopicMeetingId();
        EsMeeting meeting;
        if (resolution.explicitMeeting() != null) {
            meeting = resolution.explicitMeeting();
        } else {
            Optional<EsMeeting> today = findTodaysMeeting(esTopicMeetingId);
            if (today.isEmpty()) {
                return TopicInterestData.empty();
            }
            meeting = today.get();
        }

        List<EsMeetingAgendaItem> allItems = agendaItemDao.findByMeetingIdOrdered(meeting.getEsMeetingId());
        List<EsMeetingAgendaItem> agendaItems = allItems.stream()
                .filter(i -> i.getEsTopicId() != null
                        && i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.CANCELLED
                        && i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.POSTPONED)
                .collect(Collectors.toList());

        if (agendaItems.isEmpty()) {
            return new TopicInterestData(meeting, List.of(), Map.of(), Map.of());
        }

        Map<Long, EsTopic> topicById = new LinkedHashMap<>();
        for (EsMeetingAgendaItem item : agendaItems) {
            topicDao.findById(item.getEsTopicId()).ifPresent(t -> topicById.put(t.getEsTopicId(), t));
        }

        Map<Long, EsSubscription> subsByTopicId = new LinkedHashMap<>();
        if (emailNormalized != null) {
            List<EsSubscription> emailSubs = subscriptionDao.findByEmailNormalizedAndType(
                    emailNormalized, EsSubscription.SubscriptionType.TOPIC);
            for (EsSubscription sub : emailSubs) {
                if (sub.getEsTopicId() != null && topicById.containsKey(sub.getEsTopicId())) {
                    subsByTopicId.merge(sub.getEsTopicId(), sub,
                            EsMeetingAttendanceServlet::preferHigherRankSub);
                }
            }
            if (userId != null) {
                List<EsSubscription> userSubs = subscriptionDao.findByUserIdAndType(
                        userId, EsSubscription.SubscriptionType.TOPIC);
                for (EsSubscription sub : userSubs) {
                    if (sub.getEsTopicId() != null && topicById.containsKey(sub.getEsTopicId())) {
                        subsByTopicId.merge(sub.getEsTopicId(), sub,
                                EsMeetingAttendanceServlet::preferHigherRankSub);
                    }
                }
            }
        }

        return new TopicInterestData(meeting, agendaItems, topicById, subsByTopicId);
    }

    private void processTopicInterest(HttpServletRequest request,
            String emailNormalized, Long userId, TopicInterestData topicData) {
        if (topicData == null || topicData.agendaItems().isEmpty()) {
            return;
        }
        String[] allIds = request.getParameterValues("topicInterestAll");
        if (allIds == null || allIds.length == 0) {
            return;
        }
        Set<Long> allShownTopicIds = new HashSet<>();
        for (String id : allIds) {
            Long tid = parseId(id);
            if (tid != null)
                allShownTopicIds.add(tid);
        }
        Set<Long> checkedTopicIds = new HashSet<>();
        String[] checkedIds = request.getParameterValues("topicInterest");
        if (checkedIds != null) {
            for (String id : checkedIds) {
                Long tid = parseId(id);
                if (tid != null)
                    checkedTopicIds.add(tid);
            }
        }

        // Reload existing subs using the real (post-normalization) email
        Map<Long, EsSubscription> existingByTopic = new LinkedHashMap<>();
        List<EsSubscription> existingSubs = subscriptionDao.findByEmailNormalizedAndType(
                emailNormalized, EsSubscription.SubscriptionType.TOPIC);
        for (EsSubscription sub : existingSubs) {
            if (sub.getEsTopicId() != null && allShownTopicIds.contains(sub.getEsTopicId())) {
                existingByTopic.merge(sub.getEsTopicId(), sub,
                        EsMeetingAttendanceServlet::preferHigherRankSub);
            }
        }
        if (userId != null) {
            List<EsSubscription> userSubs = subscriptionDao.findByUserIdAndType(
                    userId, EsSubscription.SubscriptionType.TOPIC);
            for (EsSubscription sub : userSubs) {
                if (sub.getEsTopicId() != null && allShownTopicIds.contains(sub.getEsTopicId())) {
                    existingByTopic.merge(sub.getEsTopicId(), sub,
                            EsMeetingAttendanceServlet::preferHigherRankSub);
                }
            }
        }

        for (Long topicId : allShownTopicIds) {
            EsSubscription existing = existingByTopic.get(topicId);
            // Never touch CHAMPION or SUPPORT subscriptions.
            if (existing != null && isChampionEquivalentStatus(existing.getStatus())) {
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
                if (userId != null) {
                    newSub.setUserId(userId);
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

    private void renderTopicInterestFields(PrintWriter out, TopicInterestData topicData) {
        if (topicData == null || topicData.agendaItems().isEmpty()) {
            return;
        }
        out.println("      <div class=\"attend-topic-interest\">");
        out.println("        <p class=\"attend-topic-interest-label\">Topics on today&rsquo;s agenda"
                + " &mdash; check any you&rsquo;d like to follow:</p>");
        out.println("        <ul class=\"attend-topic-list\">");
        for (EsMeetingAgendaItem item : topicData.agendaItems()) {
            EsTopic topic = topicData.topicById().get(item.getEsTopicId());
            if (topic == null)
                continue;
            Long topicId = topic.getEsTopicId();
            EsSubscription sub = topicData.subsByTopicId().get(topicId);
            boolean isChampionEquivalent = sub != null
                    && isChampionEquivalentStatus(sub.getStatus());
            boolean isChecked = sub != null && (sub.getStatus() == EsSubscription.SubscriptionStatus.SUBSCRIBED
                    || isChampionEquivalentStatus(sub.getStatus()));
            out.println("          <li class=\"attend-topic-item\">");
            out.println("            <input type=\"hidden\" name=\"topicInterestAll\" value=\"" + topicId + "\">");
            out.println("            <label class=\"attend-topic-label\">");
            out.print("              <input type=\"checkbox\" name=\"topicInterest\" value=\"" + topicId + "\""
                    + (isChecked ? " checked" : "") + "> ");
            out.print(escapeHtml(topic.getTopicName()));
            if (isChampionEquivalent) {
                String roleLabel = sub.getStatus() == EsSubscription.SubscriptionStatus.SUPPORT
                        ? "(support)"
                        : "(champion)";
                out.print(" <span class=\"attend-champion-badge\">" + roleLabel + "</span>");
            }
            out.println();
            out.println("            </label>");
            out.println("          </li>");
        }
        out.println("        </ul>");
        out.println("      </div>");
    }

    private static EsSubscription preferHigherRankSub(EsSubscription a, EsSubscription b) {
        if (isChampionEquivalentStatus(a.getStatus()))
            return a;
        if (isChampionEquivalentStatus(b.getStatus()))
            return b;
        if (a.getStatus() == EsSubscription.SubscriptionStatus.SUBSCRIBED)
            return a;
        return b;
    }

    private static boolean isChampionEquivalentStatus(EsSubscription.SubscriptionStatus status) {
        return status == EsSubscription.SubscriptionStatus.CHAMPION
                || status == EsSubscription.SubscriptionStatus.SUPPORT;
    }

    private static Long parseId(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Topic interest data record
    // -------------------------------------------------------------------------

    private record TopicInterestData(
            EsMeeting resolvedMeeting,
            List<EsMeetingAgendaItem> agendaItems,
            Map<Long, EsTopic> topicById,
            Map<Long, EsSubscription> subsByTopicId) {
        static TopicInterestData empty() {
            return new TopicInterestData(null, List.of(), Map.of(), Map.of());
        }
    }
}
