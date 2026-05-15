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
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.model.EsMeetingAttendance;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.AuthService;
import org.airahub.interophub.service.EmailService;
import org.airahub.interophub.service.EsNormalizer;

public class EsMeetingAttendanceServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(EsMeetingAttendanceServlet.class.getName());

    private final EsTopicDao topicDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsMeetingDao meetingDao;
    private final EsMeetingAttendanceDao attendanceDao;
    private final AuthFlowService authFlowService;
    private final AuthService authService;
    private final EmailService emailService;

    public EsMeetingAttendanceServlet() {
        this.topicDao = new EsTopicDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.meetingDao = new EsMeetingDao();
        this.attendanceDao = new EsMeetingAttendanceDao();
        this.authFlowService = new AuthFlowService();
        this.authService = new AuthService();
        this.emailService = new EmailService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String topicCode = parseTopicCode(request);
        if (topicCode == null) {
            renderNotFound(response, request.getContextPath(), "Meeting link is incomplete.");
            return;
        }

        MeetingResolution resolution = resolveMeeting(topicCode);
        if (!resolution.valid()) {
            renderNotFound(response, request.getContextPath(), resolution.errorMessage());
            return;
        }

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        boolean submitted = "1".equals(request.getParameter("submitted"));
        boolean anonymousMode = "1".equals(request.getParameter("anon"));

        renderPage(response, request.getContextPath(), resolution, authenticatedUser.orElse(null),
                anonymousMode, null, null, null, null, null, false, submitted);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        String topicCode = parseTopicCode(request);
        if (topicCode == null) {
            renderNotFound(response, request.getContextPath(), "Meeting link is incomplete.");
            return;
        }

        MeetingResolution resolution = resolveMeeting(topicCode);
        if (!resolution.valid()) {
            renderNotFound(response, request.getContextPath(), resolution.errorMessage());
            return;
        }

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);

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
                    false, false);
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
                    firstName, lastName, email, organization, false, false);
            return;
        }
        if (anonymousMode && (email == null || email.isBlank())) {
            renderPage(response, request.getContextPath(), resolution, authenticatedUser.orElse(null),
                    anonymousMode, "Email address is required.",
                    firstName, lastName, email, organization, false, false);
            return;
        }

        String emailNormalized = anonymousMode
                ? EsNormalizer.normalizeEmail(email)
                : EsNormalizer.normalizeEmail(authenticatedUser.map(User::getEmail).orElse(email));

        if (emailNormalized == null) {
            renderPage(response, request.getContextPath(), resolution, authenticatedUser.orElse(null),
                    anonymousMode, "A valid email address is required.",
                    firstName, lastName, email, organization, false, false);
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

        try {
            attendanceDao.saveOrUpdate(record);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to save meeting attendance", ex);
            renderPage(response, request.getContextPath(), resolution, authenticatedUser.orElse(null),
                    anonymousMode, "Could not save your attendance. Please try again.",
                    firstName, lastName, email, organization, false, false);
            return;
        }

        if (sendLink && anonymousMode) {
            sendMagicLinkEmail(request, emailNormalized, firstName, lastName, organization,
                    resolution.topic().getTopicCode());
        }

        String agendaUrl = pickAgendaUrl(request.getContextPath(),
                resolution.meeting().getEsTopicMeetingId());
        response.sendRedirect(agendaUrl);
    }

    // -------------------------------------------------------------------------
    // Agenda redirect helper
    // -------------------------------------------------------------------------

    /**
     * Picks the best EsMeeting agenda URL for the given series (esTopicMeetingId)
     * based on today's date and current time.
     * Priority:
     * 1. Meetings on today's date (by scheduledStart date)
     * 2. If only one today → use it
     * 3. If multiple today → prefer one where now is between start and end
     * 4. Otherwise → pick the one with scheduledStart closest to now (absolute)
     * Falls back to the series list page if no today meetings exist.
     */
    private String pickAgendaUrl(String contextPath, Long esTopicMeetingId) {
        if (esTopicMeetingId == null) {
            return contextPath + "/es/topics";
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
            return contextPath + "/es/meetings?seriesId=" + esTopicMeetingId;
        }
        if (todayMeetings.size() == 1) {
            return contextPath + "/es/agenda?meetingId=" + todayMeetings.get(0).getEsMeetingId();
        }
        // Multiple today: prefer one where now is between start and end
        Optional<EsMeeting> inProgress = todayMeetings.stream()
                .filter(m -> m.getScheduledStart() != null && !m.getScheduledStart().isAfter(now)
                        && (m.getScheduledEnd() == null || m.getScheduledEnd().isAfter(now)))
                .findFirst();
        if (inProgress.isPresent()) {
            return contextPath + "/es/agenda?meetingId=" + inProgress.get().getEsMeetingId();
        }
        // Closest by absolute difference of scheduledStart to now
        EsMeeting closest = todayMeetings.stream()
                .filter(m -> m.getScheduledStart() != null)
                .min(Comparator.comparingLong(m -> Math.abs(
                        java.time.Duration.between(m.getScheduledStart(), now).toMinutes())))
                .orElse(todayMeetings.get(0));
        return contextPath + "/es/agenda?meetingId=" + closest.getEsMeetingId();
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
            String magicLinkUrl = authFlowService.issueMagicLink(user, request, null);
            emailService.sendWelcomeEmail(emailNormalized, magicLinkUrl);
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
            boolean generalUpdatesOptIn, boolean submitted) throws IOException {
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

    private String parseTopicCode(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || !pathInfo.startsWith("/")) {
            return null;
        }
        String trimmed = pathInfo.substring(1);
        if (trimmed.contains("/")) {
            return null;
        }
        return trimToNull(trimmed);
    }

    private MeetingResolution resolveMeeting(String topicCode) {
        Optional<EsTopic> topicOpt = topicDao.findByTopicCode(topicCode);
        if (topicOpt.isEmpty()) {
            return MeetingResolution.invalid("No meeting was found for this link.");
        }
        EsTopic topic = topicOpt.get();
        Optional<EsTopicMeeting> meetingOpt = topicMeetingDao.findByTopicId(topic.getEsTopicId())
                .filter(m -> m.getStatus() == EsTopicMeeting.MeetingStatus.ACTIVE);
        if (meetingOpt.isEmpty()) {
            return MeetingResolution.invalid("No active meeting is configured for this topic.");
        }
        return MeetingResolution.valid(topic, meetingOpt.get());
    }

    // -------------------------------------------------------------------------
    // Small utility records
    // -------------------------------------------------------------------------

    private record MeetingResolution(boolean valid, EsTopic topic, EsTopicMeeting meeting, String errorMessage) {
        static MeetingResolution valid(EsTopic topic, EsTopicMeeting meeting) {
            return new MeetingResolution(true, topic, meeting, null);
        }

        static MeetingResolution invalid(String message) {
            return new MeetingResolution(false, null, null, message);
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
}
