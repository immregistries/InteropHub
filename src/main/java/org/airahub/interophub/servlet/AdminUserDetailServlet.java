package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EmailSendLogDao;
import org.airahub.interophub.dao.MagicLinkSendEventDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EmailSendLog;
import org.airahub.interophub.model.MagicLinkSendEvent;
import org.airahub.interophub.model.User;

public class AdminUserDetailServlet extends HttpServlet {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserDao userDao;
    private final MagicLinkSendEventDao magicLinkSendEventDao;
    private final EmailSendLogDao emailSendLogDao;

    public AdminUserDetailServlet() {
        this.userDao = new UserDao();
        this.magicLinkSendEventDao = new MagicLinkSendEventDao();
        this.emailSendLogDao = new EmailSendLogDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        Long userId = parseUserId(request.getParameter("userId"));
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            renderError(request, response, "A valid userId is required.");
            return;
        }

        Optional<User> targetUser = userDao.findById(userId);
        if (targetUser.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            renderError(request, response, "User not found.");
            return;
        }

        List<MagicLinkSendEvent> sendEvents = magicLinkSendEventDao.findRecentByUserId(userId, 50);
        List<EmailSendLog> emailLogs = emailSendLogDao.findByUserId(userId, 50);
        boolean saved = "1".equals(request.getParameter("saved"));
        renderPage(request, response, targetUser.get(), sendEvents, emailLogs, saved);
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response, User user,
            List<MagicLinkSendEvent> sendEvents, List<EmailSendLog> emailLogs, boolean saved) throws IOException {
        String contextPath = request.getContextPath();

        AdminShellRenderer.render(request, response, "User Detail - InteropHub", AdminSection.PEOPLE,
                "/admin/users", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">User Detail</h2>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/users\">&larr; Back to Registered Users</a></p>");
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/users/edit?userId=" + user.getUserId() + "\">Edit User</a>");
                    out.println("            </div>");

                    if (saved) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>Changes saved successfully.</p></div>");
                    }

                    out.println("            <h3 class=\"aira-subsection-title\">Profile</h3>");
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <tbody>");
                    row(out, "User ID", String.valueOf(user.getUserId()));
                    row(out, "Email", user.getEmail());
                    row(out, "Email (normalized)", user.getEmailNormalized());
                    row(out, "Name", user.getFullName());
                    row(out, "Organization", user.getOrganization());
                    row(out, "Role Title", user.getRoleTitle());
                    row(out, "Email Verified", String.valueOf(Boolean.TRUE.equals(user.getEmailVerified())));
                    row(out, "Status", user.getStatus() == null ? "" : user.getStatus().name());
                    row(out, "Created At", formatDateTime(user.getCreatedAt()));
                    row(out, "Last Login", formatDateTime(user.getLastLoginAt()));
                    row(out, "Last Seen", formatDateTime(user.getLastSeenAt()));
                    row(out, "Delete After", formatDateTime(user.getDeleteAfterAt()));
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println("            <h3 class=\"aira-subsection-title\">Magic Link Email Send Events (Last 50)</h3>");
                    if (sendEvents.isEmpty()) {
                        out.println("            <p class=\"aira-meta\">No send events found for this user.</p>");
                    } else {
                        out.println("            <div class=\"aira-table-wrap\">");
                        out.println("            <table class=\"aira-table\">");
                        out.println("              <thead>");
                        out.println("                <tr>");
                        out.println("                  <th>Event Time</th>");
                        out.println("                  <th>Type</th>");
                        out.println("                  <th>Magic ID</th>");
                        out.println("                  <th>Email</th>");
                        out.println("                  <th>Request ID</th>");
                        out.println("                  <th>Request IP</th>");
                        out.println("                  <th>SMTP Provider</th>");
                        out.println("                  <th>SMTP Message ID</th>");
                        out.println("                  <th>SMTP Reply</th>");
                        out.println("                  <th>Error</th>");
                        out.println("                </tr>");
                        out.println("              </thead>");
                        out.println("              <tbody>");
                        for (MagicLinkSendEvent event : sendEvents) {
                            out.println("                <tr>");
                            out.println(
                                    "                  <td>" + escapeHtml(formatDateTime(event.getEventAt())) + "</td>");
                            out.println("                  <td>"
                                    + escapeHtml(event.getEventType() == null ? "" : event.getEventType().name())
                                    + "</td>");
                            out.println("                  <td>"
                                    + escapeHtml(event.getMagicId() == null ? "" : String.valueOf(event.getMagicId()))
                                    + "</td>");
                            out.println(
                                    "                  <td>" + escapeHtml(orEmpty(event.getEmailNormalized())) + "</td>");
                            out.println("                  <td>" + escapeHtml(orEmpty(event.getRequestId())) + "</td>");
                            out.println("                  <td>" + escapeHtml(formatIp(event.getRequestIp())) + "</td>");
                            out.println("                  <td>" + escapeHtml(orEmpty(event.getSmtpProvider())) + "</td>");
                            out.println(
                                    "                  <td>" + escapeHtml(orEmpty(event.getSmtpMessageId())) + "</td>");
                            out.println(
                                    "                  <td>" + escapeHtml(orEmpty(event.getSmtpReplyCode())) + "</td>");
                            out.println("                  <td>" + escapeHtml(renderError(event)) + "</td>");
                            out.println("                </tr>");
                        }
                        out.println("              </tbody>");
                        out.println("            </table>");
                        out.println("            </div>");
                    }

                    out.println("            <h3 class=\"aira-subsection-title\">Email Log (Last 50)</h3>");
                    if (emailLogs.isEmpty()) {
                        out.println("            <p class=\"aira-meta\">No emails found for this user.</p>");
                    } else {
                        out.println("            <div class=\"aira-table-wrap\">");
                        out.println("            <table class=\"aira-table\">");
                        out.println("              <thead>");
                        out.println("                <tr>");
                        out.println("                  <th>Sent At</th>");
                        out.println("                  <th>Reason</th>");
                        out.println("                  <th>Subject</th>");
                        out.println("                  <th>To</th>");
                        out.println("                  <th>SMTP Provider</th>");
                        out.println("                  <th>Message ID</th>");
                        out.println("                  <th>Body</th>");
                        out.println("                </tr>");
                        out.println("              </thead>");
                        out.println("              <tbody>");
                        for (EmailSendLog logEntry : emailLogs) {
                            String sentAt = logEntry.getSentAt() != null
                                    ? DATETIME_FORMATTER.format(logEntry.getSentAt())
                                    : "";
                            out.println("                <tr>");
                            out.println("                  <td>" + escapeHtml(sentAt) + "</td>");
                            out.println(
                                    "                  <td>" + escapeHtml(orEmpty(logEntry.getEmailReason())) + "</td>");
                            out.println("                  <td>" + escapeHtml(orEmpty(logEntry.getSubject())) + "</td>");
                            out.println(
                                    "                  <td>" + escapeHtml(orEmpty(logEntry.getRecipientEmail())) + "</td>");
                            out.println(
                                    "                  <td>" + escapeHtml(orEmpty(logEntry.getSmtpProvider())) + "</td>");
                            out.println(
                                    "                  <td>" + escapeHtml(orEmpty(logEntry.getSmtpMessageId())) + "</td>");
                            out.println("                  <td>");
                            if (logEntry.getBodyText() != null && !logEntry.getBodyText().isBlank()) {
                                out.println("                    <details>");
                                out.println("                      <summary>View body</summary>");
                                out.println("                      <pre style=\"white-space:pre-wrap;\">"
                                        + escapeHtml(logEntry.getBodyText()) + "</pre>");
                                out.println("                    </details>");
                            }
                            out.println("                  </td>");
                            out.println("                </tr>");
                        }
                        out.println("              </tbody>");
                        out.println("            </table>");
                        out.println("            </div>");
                    }

                    out.println("          </section>");
                });
    }

    private String renderError(MagicLinkSendEvent event) {
        if (event.getErrorClass() == null && event.getErrorMessage() == null) {
            return "";
        }
        if (event.getErrorClass() == null) {
            return orEmpty(event.getErrorMessage());
        }
        if (event.getErrorMessage() == null) {
            return event.getErrorClass();
        }
        return event.getErrorClass() + ": " + event.getErrorMessage();
    }

    private String formatIp(byte[] rawIp) {
        if (rawIp == null || rawIp.length == 0) {
            return "";
        }
        try {
            return InetAddress.getByAddress(rawIp).getHostAddress();
        } catch (Exception ex) {
            return "";
        }
    }

    private void row(PrintWriter out, String label, String value) {
        out.println("                <tr>");
        out.println("                  <th>" + escapeHtml(label) + "</th>");
        out.println("                  <td>" + escapeHtml(orEmpty(value)) + "</td>");
        out.println("                </tr>");
    }

    private String formatDateTime(java.time.LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return DATETIME_FORMATTER.format(value);
    }

    private Long parseUserId(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(rawValue.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private void renderError(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        String contextPath = request.getContextPath();

        AdminShellRenderer.render(request, response, "User Detail Error - InteropHub", AdminSection.PEOPLE,
                "/admin/users", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Could Not Load User</h2>");
                    out.println("            <div class=\"aira-alert aira-alert--danger\"><p>" + escapeHtml(orEmpty(message)) + "</p></div>");
                    out.println(
                            "            <p><a class=\"aira-inline-link\" href=\"" + contextPath + "/admin/users\">Back to Registered Users</a></p>");
                    out.println("          </section>");
                });
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
