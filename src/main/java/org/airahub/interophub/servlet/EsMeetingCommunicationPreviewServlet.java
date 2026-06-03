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
import org.airahub.interophub.service.AuthFlowService;
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

    private final AuthFlowService authFlowService;
    private final MeetingCommunicationPreviewService previewService;
    private final MeetingCommunicationService communicationService;
    private final MeetingCommunicationSendService sendService;

    public EsMeetingCommunicationPreviewServlet() {
        this.authFlowService = new AuthFlowService();
        this.previewService = new MeetingCommunicationPreviewService();
        this.communicationService = new MeetingCommunicationService();
        this.sendService = new MeetingCommunicationSendService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
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

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Communication Preview - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Communication Preview</h2>");
                panelOut.println("        <p>"
                        + "<a href=\"" + contextPath + "/es/agenda?meetingId=" + comm.getEsMeetingId()
                        + "\">&larr; Back to Meeting</a>"
                        + " &nbsp;|&nbsp; "
                        + "<a href=\"" + contextPath + "/es/meeting-communication?meetingId=" + comm.getEsMeetingId()
                        + "\">All Communications for this Meeting</a>"
                        + "</p>");

                // Summary
                panelOut.println("        <table class=\"data-table\" style=\"max-width:600px\">");
                panelOut.println("          <tbody>");
                row(panelOut, "Meeting", preview.getMeeting() != null
                        ? escapeHtml(orEmpty(preview.getMeeting().getMeetingName()))
                        : "Unknown");
                row(panelOut, "Type", escapeHtml(comm.getCommunicationType().name()));
                row(panelOut, "Status", escapeHtml(comm.getStatus().name()));
                row(panelOut, "Scheduled Send", escapeHtml(formatScheduledSendInCommunicationTimezone(comm)));
                row(panelOut, "Recipients", String.valueOf(preview.getTotalRecipientCount()));
                row(panelOut, "Include General Members", comm.isIncludeGeneralMembers() ? "Yes" : "No");
                row(panelOut, "Include Topic Subscribers", comm.isIncludeTopicSubscribers() ? "Yes" : "No");
                row(panelOut, "Include Topic Champions", comm.isIncludeTopicChampions() ? "Yes" : "No");
                row(panelOut, "Include Presenters", comm.isIncludePresenters() ? "Yes" : "No");
                if (comm.getSubjectOverride() != null) {
                    row(panelOut, "Subject Override", escapeHtml(comm.getSubjectOverride()));
                }
                panelOut.println("          </tbody></table>");

                // Eligibility banner
                if (!preview.getEligibility().isEligible()) {
                    panelOut.println(
                            "        <div style=\"background:#fff3cd;border:1px solid #ffc107;padding:10px 16px;margin:12px 0;border-radius:4px\">");
                    panelOut.println("          <strong>Not eligible to send:</strong> "
                            + escapeHtml(preview.getEligibility().getReason()));
                    panelOut.println("        </div>");
                }

                // Action buttons
                if (canSchedule || canSend || canCancel) {
                    panelOut.println("        <div style=\"display:flex;gap:8px;margin:16px 0;\">");
                    if (canSchedule) {
                        panelOut.println("          <form method=\"post\">");
                        panelOut.println("            <input type=\"hidden\" name=\"id\" value=\""
                                + comm.getEsMeetingCommunicationId() + "\" />");
                        panelOut.println("            <input type=\"hidden\" name=\"action\" value=\"schedule\" />");
                        panelOut.println("            <button type=\"submit\">Schedule</button>");
                        panelOut.println("          </form>");
                    }
                    if (canSend) {
                        panelOut.println(
                                "          <form method=\"post\" onsubmit=\"return confirm('Send this communication now to all "
                                        + preview.getTotalRecipientCount() + " recipient(s)?')\">");
                        panelOut.println("            <input type=\"hidden\" name=\"id\" value=\""
                                + comm.getEsMeetingCommunicationId() + "\" />");
                        panelOut.println("            <input type=\"hidden\" name=\"action\" value=\"sendNow\" />");
                        panelOut.println(
                                "            <button type=\"submit\" style=\"background:#198754;color:#fff\">Send Now</button>");
                        panelOut.println("          </form>");
                    }
                    if (canCancel) {
                        panelOut.println(
                                "          <form method=\"post\" onsubmit=\"return confirm('Cancel this communication?')\">");
                        panelOut.println("            <input type=\"hidden\" name=\"id\" value=\""
                                + comm.getEsMeetingCommunicationId() + "\" />");
                        panelOut.println("            <input type=\"hidden\" name=\"action\" value=\"cancel\" />");
                        panelOut.println(
                                "            <button type=\"submit\" style=\"background:#dc3545;color:#fff\">Cancel</button>");
                        panelOut.println("          </form>");
                    }
                    panelOut.println("        </div>");
                }

                // Group summary
                panelOut.println("        <h3>Recipient Groups</h3>");
                if (preview.getGroupSummaries().isEmpty()) {
                    panelOut.println("        <p>No recipients found with current settings.</p>");
                } else {
                    panelOut.println("        <table class=\"data-table\" style=\"max-width:400px\">");
                    panelOut.println("          <thead><tr><th>Group</th><th>Count</th></tr></thead><tbody>");
                    for (CommunicationRecipientGroupSummary summary : preview.getGroupSummaries()) {
                        panelOut.println("            <tr><td>" + escapeHtml(summary.getGroup().name())
                                + "</td><td>" + summary.getCount() + "</td></tr>");
                    }
                    panelOut.println("          </tbody></table>");
                }

                // Sample emails
                if (!preview.getSampleEmails().isEmpty()) {
                    panelOut.println("        <h3>Sample Emails</h3>");
                    for (CommunicationRenderedEmail sample : preview.getSampleEmails()) {
                        panelOut.println(
                                "        <details style=\"margin-bottom:12px;border:1px solid #dee2e6;padding:8px;border-radius:4px\">");
                        panelOut.println("          <summary><strong>" + escapeHtml(sample.getRecipient().getEmail())
                                + "</strong> (" + escapeHtml(sample.getRecipient().getPrimaryGroup().name())
                                + ") — " + escapeHtml(sample.getSubject()) + "</summary>");
                        panelOut.println("          <pre style=\"white-space:pre-wrap;margin-top:8px\">"
                                + escapeHtml(sample.getBodyText()) + "</pre>");
                        panelOut.println("        </details>");
                    }
                }

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
        out.println("            <tr><th style=\"text-align:left;white-space:nowrap\">"
                + escapeHtml(label) + "</th><td>" + value + "</td></tr>");
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
