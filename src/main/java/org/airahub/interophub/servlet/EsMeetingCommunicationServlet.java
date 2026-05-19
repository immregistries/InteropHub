package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
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
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.MeetingCommunicationService;

/**
 * Per-meeting list of communications and create form.
 * URL: /es/meeting-communication?meetingId=X
 */
public class EsMeetingCommunicationServlet extends HttpServlet {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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

    private final AuthFlowService authFlowService;
    private final MeetingCommunicationService communicationService;
    private final EsMeetingDao meetingDao;

    public EsMeetingCommunicationServlet() {
        this.authFlowService = new AuthFlowService();
        this.communicationService = new MeetingCommunicationService();
        this.meetingDao = new EsMeetingDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
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

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            String title = escapeHtml(orEmpty(meeting.getMeetingName())) + " — Communications";
            AdminShellRenderer.render(out, title + " - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println(
                        "        <h2>Communications — " + escapeHtml(orEmpty(meeting.getMeetingName())) + "</h2>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/es/meeting-communications\">&larr; All Communications</a></p>");

                // Existing communications table
                panelOut.println("        <h3>Existing Communications</h3>");
                if (communications.isEmpty()) {
                    panelOut.println("        <p>No communications created yet for this meeting.</p>");
                } else {
                    panelOut.println("        <table class=\"data-table\">");
                    panelOut.println("          <thead><tr>");
                    panelOut.println(
                            "            <th>Type</th><th>Status</th><th>Scheduled Send</th><th>Created</th><th></th>");
                    panelOut.println("          </tr></thead><tbody>");
                    for (EsMeetingCommunication comm : communications) {
                        String scheduledAt = comm.getScheduledSendAt() != null
                                ? DATETIME_FMT.format(comm.getScheduledSendAt())
                                : "—";
                        String createdAt = comm.getCreatedAt() != null
                                ? DATETIME_FMT.format(comm.getCreatedAt())
                                : "";
                        panelOut.println("            <tr>");
                        panelOut.println(
                                "              <td>" + escapeHtml(comm.getCommunicationType().name()) + "</td>");
                        panelOut.println("              <td>" + escapeHtml(comm.getStatus().name()) + "</td>");
                        panelOut.println("              <td>" + escapeHtml(scheduledAt) + "</td>");
                        panelOut.println("              <td>" + escapeHtml(createdAt) + "</td>");
                        panelOut.println("              <td><a href=\"" + contextPath
                                + "/es/meeting-communication-preview?id=" + comm.getEsMeetingCommunicationId()
                                + "\">Preview / Manage</a></td>");
                        panelOut.println("            </tr>");
                    }
                    panelOut.println("          </tbody></table>");
                }

                // Create form
                panelOut.println("        <h3>Create New Communication</h3>");
                panelOut.println(
                        "        <form method=\"post\" action=\"" + contextPath + "/es/meeting-communication\">");
                panelOut.println("          <input type=\"hidden\" name=\"meetingId\" value=\"" + meetingId + "\" />");

                // Type
                panelOut.println("          <div class=\"form-group\">");
                panelOut.println("            <label for=\"communicationType\">Communication Type</label>");
                panelOut.println("            <select id=\"communicationType\" name=\"communicationType\" required>");
                for (EsMeetingCommunication.CommunicationType type : EsMeetingCommunication.CommunicationType
                        .values()) {
                    String selected = type.name().equals(suggestType) ? " selected" : "";
                    panelOut.println("              <option value=\"" + type.name() + "\"" + selected + ">"
                            + escapeHtml(type.name()) + "</option>");
                }
                panelOut.println("            </select>");
                panelOut.println("          </div>");

                // Scheduled send at
                panelOut.println("          <div class=\"form-group\">");
                panelOut.println("            <label for=\"scheduledSendAt\">Scheduled Send At</label>");
                panelOut.println(
                        "            <input type=\"datetime-local\" id=\"scheduledSendAt\" name=\"scheduledSendAt\" />");
                panelOut.println("            <small>Leave blank to save as draft without scheduling.</small>");
                panelOut.println("          </div>");

                // Timezone
                panelOut.println("          <div class=\"form-group\">");
                panelOut.println("            <label for=\"timezoneId\">Timezone</label>");
                panelOut.println(
                        "            <input type=\"text\" id=\"timezoneId\" name=\"timezoneId\" value=\"America/New_York\" maxlength=\"64\" />");
                panelOut.println("          </div>");

                // Subject override
                panelOut.println("          <div class=\"form-group\">");
                panelOut.println("            <label for=\"subjectOverride\">Subject Override (optional)</label>");
                panelOut.println(
                        "            <input type=\"text\" id=\"subjectOverride\" name=\"subjectOverride\" maxlength=\"500\" />");
                panelOut.println("          </div>");

                // Note to include
                panelOut.println("          <div class=\"form-group\">");
                panelOut.println("            <label for=\"noteToInclude\">Note to Include (optional)</label>");
                panelOut.println(
                        "            <textarea id=\"noteToInclude\" name=\"noteToInclude\" rows=\"4\"></textarea>");
                panelOut.println("          </div>");

                // Recipient group checkboxes
                panelOut.println("          <div class=\"form-group\">");
                panelOut.println("            <label>Recipient Groups</label>");
                renderCheckbox(panelOut, "includeGeneralMembers", "General meeting members",
                        isGroupDefaulted(suggestType, "GENERAL"), suggestType);
                renderCheckbox(panelOut, "includeTopicSubscribers", "Topic subscribers",
                        isGroupDefaulted(suggestType, "SUBSCRIBER"), suggestType);
                renderCheckbox(panelOut, "includeTopicChampions", "Topic champions",
                        isGroupDefaulted(suggestType, "CHAMPION"), suggestType);
                renderCheckbox(panelOut, "includePresenters", "Agenda presenters",
                        isGroupDefaulted(suggestType, "PRESENTER"), suggestType);
                panelOut.println("          </div>");

                panelOut.println("          <button type=\"submit\">Create Communication</button>");
                panelOut.println("        </form>");
                panelOut.println("      </section>");
            });
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
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

        String scheduledSendAtParam = trimToNull(request.getParameter("scheduledSendAt"));
        if (scheduledSendAtParam != null) {
            try {
                // datetime-local format: yyyy-MM-ddTHH:mm
                communication.setScheduledSendAt(LocalDateTime.parse(scheduledSendAtParam));
            } catch (Exception ignored) {
            }
        }

        String timezoneId = trimToNull(request.getParameter("timezoneId"));
        if (timezoneId != null && ALLOWED_TIMEZONES.contains(timezoneId)) {
            communication.setTimezoneId(timezoneId);
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
        out.println("            <label style=\"display:block;\">");
        out.println("              <input type=\"checkbox\" name=\"" + name + "\"" + checkedAttr + " /> "
                + escapeHtml(label));
        out.println("            </label>");
    }

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(user.get())) {
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                AdminShellRenderer.render(out, "Access Denied - InteropHub", request.getContextPath(), panelOut -> {
                    panelOut.println("      <section class=\"panel\">");
                    panelOut.println("        <h2>Access Denied</h2>");
                    panelOut.println("        <p>Admin access required.</p>");
                    panelOut.println("        <p><a href=\"" + request.getContextPath()
                            + "/welcome\">Return to Welcome</a></p>");
                    panelOut.println("      </section>");
                });
            }
            return Optional.empty();
        }
        return user;
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

    private static String escapeHtml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
