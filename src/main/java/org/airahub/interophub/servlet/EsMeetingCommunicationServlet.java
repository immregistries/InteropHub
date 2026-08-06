package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.MeetingCommunicationService;

/**
 * Per-meeting list of communications and create form.
 * URL: /es/meeting-communication?meetingId=X
 */
public class EsMeetingCommunicationServlet extends HttpServlet {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DEFAULT_TIMEZONE = "America/New_York";

    /** Allowed timezones for the form. */
    private static final Set<String> ALLOWED_TIMEZONES = Set.of(
            "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
            "America/Phoenix", "America/Anchorage", "Pacific/Honolulu",
            "America/Sao_Paulo", "America/Santiago",
            "Europe/London", "Europe/Paris",
            "Africa/Johannesburg",
            "Asia/Kolkata", "Asia/Tokyo",
            "Australia/Sydney",
            "Pacific/Auckland");

    private final MeetingCommunicationService communicationService;
    private final EsMeetingDao meetingDao;

    public EsMeetingCommunicationServlet() {
        this.communicationService = new MeetingCommunicationService();
        this.meetingDao = new EsMeetingDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty())
            return;

        String contextPath = request.getContextPath();
        Long meetingId = parseId(trimToNull(request.getParameter("meetingId")));
        if (meetingId == null) {
            response.sendRedirect(contextPath + "/es/meeting-communications");
            return;
        }

        EsMeeting meeting = meetingDao.findById(meetingId).orElse(null);
        if (meeting == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Meeting not found.");
            return;
        }

        List<EsMeetingCommunication> communications = communicationService.findByMeetingId(meetingId);
        // Pre-select type if suggestType param is present
        String suggestType = trimToNull(request.getParameter("suggestType"));

        String title = escapeHtml(orEmpty(meeting.getMeetingName())) + " — Communications";
        AdminShellRenderer.render(request, response, title + " - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/meetings", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println(
                            "            <h2 class=\"aira-section-title\">Communications — "
                                    + escapeHtml(orEmpty(meeting.getMeetingName())) + "</h2>");
                    out.println("            <p>"
                            + "<a class=\"aira-inline-link\" href=\"" + contextPath + "/es/agenda?meetingId="
                            + meetingId + "\">&larr; Back to Meeting</a>"
                            + " &nbsp;|&nbsp; "
                            + "<a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/es/meeting-communications\">All Communications</a>"
                            + "</p>");

                    // Existing communications table
                    out.println("            <h3 class=\"aira-subsection-title\">Existing Communications</h3>");
                    if (communications.isEmpty()) {
                        out.println("            <p class=\"aira-meta\">No communications created yet for this meeting.</p>");
                    } else {
                        out.println("            <div class=\"aira-table-wrap\">");
                        out.println("            <table class=\"aira-table\">");
                        out.println("              <thead><tr>");
                        out.println(
                                "                <th>Type</th><th>Status</th><th>Scheduled Send</th><th>Created</th><th></th>");
                        out.println("              </tr></thead><tbody>");
                        for (EsMeetingCommunication comm : communications) {
                            String scheduledAt = formatScheduledSendInCommunicationTimezone(comm);
                            String createdAt = comm.getCreatedAt() != null
                                    ? DATETIME_FMT.format(comm.getCreatedAt())
                                    : "";
                            out.println("                <tr>");
                            out.println(
                                    "                  <td>" + escapeHtml(comm.getCommunicationType().name())
                                            + "</td>");
                            out.println("                  <td>" + escapeHtml(comm.getStatus().name()) + "</td>");
                            out.println("                  <td>" + escapeHtml(scheduledAt) + "</td>");
                            out.println("                  <td>" + escapeHtml(createdAt) + "</td>");
                            out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                    + "/es/meeting-communication-preview?id=" + comm.getEsMeetingCommunicationId()
                                    + "\">Preview / Manage</a></td>");
                            out.println("                </tr>");
                        }
                        out.println("              </tbody></table>");
                        out.println("            </div>");
                    }

                    // Create form
                    out.println("            <h3 class=\"aira-subsection-title\">Create New Communication</h3>");
                    out.println(
                            "            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                                    + "/es/meeting-communication\">");
                    out.println("              <input type=\"hidden\" name=\"meetingId\" value=\"" + meetingId
                            + "\" />");

                    // Type
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"communicationType\">Communication Type</label>");
                    out.println(
                            "                <select class=\"aira-select\" id=\"communicationType\" name=\"communicationType\" required>");
                    for (EsMeetingCommunication.CommunicationType type : EsMeetingCommunication.CommunicationType
                            .values()) {
                        String selected = type.name().equals(suggestType) ? " selected" : "";
                        out.println("                  <option value=\"" + type.name() + "\"" + selected + ">"
                                + escapeHtml(type.name()) + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");

                    // Scheduled send at
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"scheduledSendAt\">Scheduled Send At</label>");
                    out.println(
                            "                <input class=\"aira-input\" type=\"datetime-local\" id=\"scheduledSendAt\" name=\"scheduledSendAt\" />");
                    out.println(
                            "                <p class=\"aira-field-help\">Leave blank to save as draft without scheduling.</p>");
                    out.println("              </div>");

                    // Timezone
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"timezoneId\">Timezone</label>");
                    out.println(
                            "                <input class=\"aira-input\" type=\"text\" id=\"timezoneId\" name=\"timezoneId\" value=\"America/New_York\" maxlength=\"64\" />");
                    out.println("              </div>");

                    // Subject override
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"subjectOverride\">Subject Override (optional)</label>");
                    out.println(
                            "                <input class=\"aira-input\" type=\"text\" id=\"subjectOverride\" name=\"subjectOverride\" maxlength=\"500\" />");
                    out.println("              </div>");

                    // Note to include
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"noteToInclude\">Note to Include (optional)</label>");
                    out.println(
                            "                <textarea class=\"aira-textarea\" id=\"noteToInclude\" name=\"noteToInclude\" rows=\"4\"></textarea>");
                    out.println("              </div>");

                    // Recipient group checkboxes
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label>Recipient Groups</label>");
                    renderCheckbox(out, "includeGeneralMembers", "General meeting members",
                            isGroupDefaulted(suggestType, "GENERAL"), suggestType);
                    renderCheckbox(out, "includeTopicSubscribers", "Topic subscribers",
                            isGroupDefaulted(suggestType, "SUBSCRIBER"), suggestType);
                    renderCheckbox(out, "includeTopicChampions", "Topic champions/support",
                            isGroupDefaulted(suggestType, "CHAMPION"), suggestType);
                    renderCheckbox(out, "includePresenters", "Agenda presenters",
                            isGroupDefaulted(suggestType, "PRESENTER"), suggestType);
                    out.println("              </div>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Create Communication</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("          </section>");
                });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty())
            return;

        String contextPath = request.getContextPath();

        Long meetingId = parseId(trimToNull(request.getParameter("meetingId")));
        if (meetingId == null) {
            response.sendRedirect(contextPath + "/es/meeting-communications");
            return;
        }

        String typeParam = trimToNull(request.getParameter("communicationType"));
        EsMeetingCommunication.CommunicationType communicationType = null;
        try {
            if (typeParam != null) {
                communicationType = EsMeetingCommunication.CommunicationType.valueOf(typeParam);
            }
        } catch (IllegalArgumentException ignored) {
        }
        if (communicationType == null) {
            response.sendRedirect(contextPath + "/es/meeting-communication?meetingId=" + meetingId);
            return;
        }

        EsMeetingCommunication communication = new EsMeetingCommunication();
        communication.setEsMeetingId(meetingId);
        communication.setCommunicationType(communicationType);
        communication.setCreatedByUserId(adminUser.get().getUserId());

        String timezoneId = trimToNull(request.getParameter("timezoneId"));
        String effectiveTimezoneId = DEFAULT_TIMEZONE;
        if (timezoneId != null && ALLOWED_TIMEZONES.contains(timezoneId)) {
            effectiveTimezoneId = timezoneId;
        }
        communication.setTimezoneId(effectiveTimezoneId);

        String scheduledSendAtParam = trimToNull(request.getParameter("scheduledSendAt"));
        if (scheduledSendAtParam != null) {
            try {
                // datetime-local format: yyyy-MM-ddTHH:mm
                LocalDateTime scheduledLocal = LocalDateTime.parse(scheduledSendAtParam);
                communication.setScheduledSendAt(toUtcLocalDateTime(scheduledLocal, effectiveTimezoneId));
            } catch (Exception ignored) {
            }
        }

        communication.setSubjectOverride(trimToNull(request.getParameter("subjectOverride")));
        communication.setNoteToInclude(trimToNull(request.getParameter("noteToInclude")));

        communication.setIncludeGeneralMembers("on".equals(request.getParameter("includeGeneralMembers")));
        communication.setIncludeTopicSubscribers("on".equals(request.getParameter("includeTopicSubscribers")));
        communication.setIncludeTopicChampions("on".equals(request.getParameter("includeTopicChampions")));
        communication.setIncludePresenters("on".equals(request.getParameter("includePresenters")));

        EsMeetingCommunication saved = communicationService.create(communication);
        response.sendRedirect(contextPath + "/es/meeting-communication-preview?id="
                + saved.getEsMeetingCommunicationId());
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Returns true if the given group should be pre-checked based on the
     * suggested communication type.
     */
    private static boolean isGroupDefaulted(String suggestType, String groupKey) {
        if (suggestType == null) {
            // Default: all on
            return true;
        }
        return switch (suggestType) {
            case "CALL_FOR_TOPICS" -> "GENERAL".equals(groupKey) || "CHAMPION".equals(groupKey);
            case "PROPOSED_AGENDA" -> "CHAMPION".equals(groupKey) || "PRESENTER".equals(groupKey);
            case "FINAL_AGENDA", "REMINDER", "CANCELLED" -> true;
            default -> true;
        };
    }

    private static void renderCheckbox(PrintWriter out, String name, String label,
            boolean checked, String suggestType) {
        String checkedAttr = checked ? " checked" : "";
        out.println("                <label class=\"aira-radio\"><input type=\"checkbox\" name=\"" + name + "\""
                + checkedAttr + " /> " + escapeHtml(label) + "</label>");
    }

    private static Long parseId(String value) {
        if (value == null)
            return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null)
            return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static ZoneId safeZoneId(String tzId) {
        if (tzId != null && ALLOWED_TIMEZONES.contains(tzId)) {
            return ZoneId.of(tzId);
        }
        return ZoneId.of(DEFAULT_TIMEZONE);
    }

    private static LocalDateTime toUtcLocalDateTime(LocalDateTime localDateTime, String timezoneId) {
        ZoneId sourceZone = safeZoneId(timezoneId);
        return localDateTime.atZone(sourceZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private static String formatScheduledSendInCommunicationTimezone(EsMeetingCommunication communication) {
        if (communication.getScheduledSendAt() == null) {
            return "—";
        }
        ZoneId targetZone = safeZoneId(communication.getTimezoneId());
        ZonedDateTime local = communication.getScheduledSendAt()
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(targetZone);
        return DATETIME_FMT.format(local) + " " + targetZone.getId();
    }

    private static String escapeHtml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
