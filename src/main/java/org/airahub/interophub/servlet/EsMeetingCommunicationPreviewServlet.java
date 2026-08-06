package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.CommunicationPreview;
import org.airahub.interophub.model.CommunicationRecipientGroupSummary;
import org.airahub.interophub.model.CommunicationRenderedEmail;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.MeetingCommunicationPreviewService;
import org.airahub.interophub.service.MeetingCommunicationSendService;
import org.airahub.interophub.service.MeetingCommunicationService;

/**
 * Preview, schedule, send-now, or cancel a single meeting communication.
 * URL: /es/meeting-communication-preview?id=X
 */
public class EsMeetingCommunicationPreviewServlet extends HttpServlet {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DEFAULT_TIMEZONE = "America/New_York";

    private final MeetingCommunicationPreviewService previewService;
    private final MeetingCommunicationService communicationService;
    private final MeetingCommunicationSendService sendService;

    public EsMeetingCommunicationPreviewServlet() {
        this.previewService = new MeetingCommunicationPreviewService();
        this.communicationService = new MeetingCommunicationService();
        this.sendService = new MeetingCommunicationSendService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty())
            return;

        String contextPath = request.getContextPath();
        Long id = parseId(trimToNull(request.getParameter("id")));
        if (id == null) {
            response.sendRedirect(contextPath + "/es/meeting-communications");
            return;
        }

        CommunicationPreview preview;
        try {
            preview = previewService.preview(id);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return;
        }

        EsMeetingCommunication comm = preview.getCommunication();
        boolean isDraft = comm.getStatus() == EsMeetingCommunication.CommunicationStatus.DRAFT;
        boolean isScheduled = comm.getStatus() == EsMeetingCommunication.CommunicationStatus.SCHEDULED;
        boolean canSend = (isDraft || isScheduled) && preview.getEligibility().isEligible();
        boolean canCancel = isDraft || isScheduled;
        boolean canSchedule = isDraft && comm.getScheduledSendAt() != null;

        AdminShellRenderer.render(request, response, "Communication Preview - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/meetings", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Communication Preview</h2>");
                    out.println("            <p>"
                            + "<a class=\"aira-inline-link\" href=\"" + contextPath + "/es/agenda?meetingId="
                            + comm.getEsMeetingId() + "\">&larr; Back to Meeting</a>"
                            + " &nbsp;|&nbsp; "
                            + "<a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/es/meeting-communication?meetingId=" + comm.getEsMeetingId()
                            + "\">All Communications for this Meeting</a>"
                            + "</p>");

                    // Summary
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <tbody>");
                    row(out, "Meeting", preview.getMeeting() != null
                            ? escapeHtml(orEmpty(preview.getMeeting().getMeetingName()))
                            : "Unknown");
                    row(out, "Type", escapeHtml(comm.getCommunicationType().name()));
                    row(out, "Status", escapeHtml(comm.getStatus().name()));
                    row(out, "Scheduled Send", escapeHtml(formatScheduledSendInCommunicationTimezone(comm)));
                    row(out, "Recipients", String.valueOf(preview.getTotalRecipientCount()));
                    row(out, "Include General Members", comm.isIncludeGeneralMembers() ? "Yes" : "No");
                    row(out, "Include Topic Subscribers", comm.isIncludeTopicSubscribers() ? "Yes" : "No");
                    row(out, "Include Topic Champions/Support", comm.isIncludeTopicChampions() ? "Yes" : "No");
                    row(out, "Include Presenters", comm.isIncludePresenters() ? "Yes" : "No");
                    if (comm.getSubjectOverride() != null) {
                        row(out, "Subject Override", escapeHtml(comm.getSubjectOverride()));
                    }
                    out.println("              </tbody></table>");
                    out.println("            </div>");

                    // Eligibility banner
                    if (!preview.getEligibility().isEligible()) {
                        out.println("            <div class=\"aira-alert aira-alert--warning\"><p><strong>Not eligible to send:</strong> "
                                + escapeHtml(preview.getEligibility().getReason()) + "</p></div>");
                    }

                    // Action buttons
                    if (canSchedule || canSend || canCancel) {
                        out.println("            <div class=\"aira-action-group\">");
                        if (canSchedule) {
                            out.println("              <form method=\"post\">");
                            out.println("                <input type=\"hidden\" name=\"id\" value=\""
                                    + comm.getEsMeetingCommunicationId() + "\" />");
                            out.println("                <input type=\"hidden\" name=\"action\" value=\"schedule\" />");
                            out.println(
                                    "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Schedule</button>");
                            out.println("              </form>");
                        }
                        if (canSend) {
                            out.println(
                                    "              <form method=\"post\" onsubmit=\"return confirm('Send this communication now to all "
                                            + preview.getTotalRecipientCount() + " recipient(s)?')\">");
                            out.println("                <input type=\"hidden\" name=\"id\" value=\""
                                    + comm.getEsMeetingCommunicationId() + "\" />");
                            out.println("                <input type=\"hidden\" name=\"action\" value=\"sendNow\" />");
                            out.println(
                                    "                <button class=\"aira-button aira-button--success\" type=\"submit\">Send Now</button>");
                            out.println("              </form>");
                        }
                        if (canCancel) {
                            out.println(
                                    "              <form method=\"post\" onsubmit=\"return confirm('Cancel this communication?')\">");
                            out.println("                <input type=\"hidden\" name=\"id\" value=\""
                                    + comm.getEsMeetingCommunicationId() + "\" />");
                            out.println("                <input type=\"hidden\" name=\"action\" value=\"cancel\" />");
                            out.println(
                                    "                <button class=\"aira-button aira-button--danger\" type=\"submit\">Cancel</button>");
                            out.println("              </form>");
                        }
                        out.println("            </div>");
                    }

                    // Group summary
                    out.println("            <h3 class=\"aira-subsection-title\">Recipient Groups</h3>");
                    if (preview.getGroupSummaries().isEmpty()) {
                        out.println("            <p class=\"aira-meta\">No recipients found with current settings.</p>");
                    } else {
                        out.println("            <div class=\"aira-table-wrap\">");
                        out.println("            <table class=\"aira-table\">");
                        out.println("              <thead><tr><th>Group</th><th>Count</th></tr></thead><tbody>");
                        for (CommunicationRecipientGroupSummary summary : preview.getGroupSummaries()) {
                            out.println("                <tr><td>" + escapeHtml(summary.getGroup().name())
                                    + "</td><td>" + summary.getCount() + "</td></tr>");
                        }
                        out.println("              </tbody></table>");
                        out.println("            </div>");
                    }

                    // Sample emails
                    if (!preview.getSampleEmails().isEmpty()) {
                        out.println("            <h3 class=\"aira-subsection-title\">Sample Emails</h3>");
                        for (CommunicationRenderedEmail sample : preview.getSampleEmails()) {
                            out.println(
                                    "            <details style=\"margin-bottom:12px;border:1px solid #dee2e6;padding:8px;border-radius:4px\">");
                            out.println("              <summary><strong>"
                                    + escapeHtml(sample.getRecipient().getEmail())
                                    + "</strong> (" + escapeHtml(sample.getRecipient().getPrimaryGroup().name())
                                    + ") — " + escapeHtml(sample.getSubject()) + "</summary>");
                            out.println("              <pre style=\"white-space:pre-wrap;margin-top:8px\">"
                                    + escapeHtml(sample.getBodyText()) + "</pre>");
                            out.println("            </details>");
                        }
                    }

                    out.println("          </section>");
                });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty())
            return;

        String contextPath = request.getContextPath();
        Long id = parseId(trimToNull(request.getParameter("id")));
        if (id == null) {
            response.sendRedirect(contextPath + "/es/meeting-communications");
            return;
        }

        String action = trimToNull(request.getParameter("action"));
        if ("schedule".equals(action)) {
            communicationService.schedule(id);
        } else if ("sendNow".equals(action)) {
            sendService.sendNow(id);
        } else if ("cancel".equals(action)) {
            communicationService.cancel(id, adminUser.get().getUserId(), "Cancelled by admin.");
        }

        response.sendRedirect(contextPath + "/es/meeting-communication-preview?id=" + id);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static void row(PrintWriter out, String label, String value) {
        out.println("                <tr><th>" + escapeHtml(label) + "</th><td>" + value + "</td></tr>");
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

    private static ZoneId safeZoneId(String timezoneId) {
        if (timezoneId != null && !timezoneId.isBlank()) {
            try {
                return ZoneId.of(timezoneId);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return ZoneId.of(DEFAULT_TIMEZONE);
    }

    private static String escapeHtml(String value) {
        if (value == null)
            return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
