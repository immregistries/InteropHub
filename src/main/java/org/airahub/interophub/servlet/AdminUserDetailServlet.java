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
import org.airahub.interophub.dao.MagicLinkSendEventDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.MagicLinkSendEvent;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminUserDetailServlet extends HttpServlet {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuthFlowService authFlowService;
    private final UserDao userDao;
    private final MagicLinkSendEventDao magicLinkSendEventDao;

    public AdminUserDetailServlet() {
        this.authFlowService = new AuthFlowService();
        this.userDao = new UserDao();
        this.magicLinkSendEventDao = new MagicLinkSendEventDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        Long userId = parseUserId(request.getParameter("userId"));
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            renderError(response, request.getContextPath(), "A valid userId is required.");
            return;
        }

        Optional<User> targetUser = userDao.findById(userId);
        if (targetUser.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            renderError(response, request.getContextPath(), "User not found.");
            return;
        }

        List<MagicLinkSendEvent> sendEvents = magicLinkSendEventDao.findRecentByUserId(userId, 50);
        renderPage(response, request.getContextPath(), targetUser.get(), sendEvents);
    }

    private void renderPage(HttpServletResponse response, String contextPath, User user,
            List<MagicLinkSendEvent> sendEvents) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>User Detail - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>User Detail</h1>");
            out.println("    <p><a href=\"" + contextPath + "/admin/users\">&larr; Back to Registered Users</a></p>");

            out.println("    <h2>Profile</h2>");
            out.println("    <table class=\"data-table\">");
            out.println("      <tbody>");
            row(out, "User ID", String.valueOf(user.getUserId()));
            row(out, "Email", user.getEmail());
            row(out, "Email (normalized)", user.getEmailNormalized());
            row(out, "Display Name", user.getDisplayName());
            row(out, "Organization", user.getOrganization());
            row(out, "Role Title", user.getRoleTitle());
            row(out, "Email Verified", String.valueOf(Boolean.TRUE.equals(user.getEmailVerified())));
            row(out, "Status", user.getStatus() == null ? "" : user.getStatus().name());
            row(out, "Created At", formatDateTime(user.getCreatedAt()));
            row(out, "Last Login", formatDateTime(user.getLastLoginAt()));
            row(out, "Last Seen", formatDateTime(user.getLastSeenAt()));
            row(out, "Delete After", formatDateTime(user.getDeleteAfterAt()));
            out.println("      </tbody>");
            out.println("    </table>");

            out.println("    <h2>Magic Link Email Send Events (Last 50)</h2>");
            if (sendEvents.isEmpty()) {
                out.println("    <p>No send events found for this user.</p>");
            } else {
                out.println("    <table class=\"data-table\">");
                out.println("      <thead>");
                out.println("        <tr>");
                out.println("          <th>Event Time</th>");
                out.println("          <th>Type</th>");
                out.println("          <th>Magic ID</th>");
                out.println("          <th>Email</th>");
                out.println("          <th>Request ID</th>");
                out.println("          <th>Request IP</th>");
                out.println("          <th>SMTP Provider</th>");
                out.println("          <th>SMTP Message ID</th>");
                out.println("          <th>SMTP Reply</th>");
                out.println("          <th>Error</th>");
                out.println("        </tr>");
                out.println("      </thead>");
                out.println("      <tbody>");
                for (MagicLinkSendEvent event : sendEvents) {
                    out.println("        <tr>");
                    out.println("          <td>" + escapeHtml(formatDateTime(event.getEventAt())) + "</td>");
                    out.println("          <td>"
                            + escapeHtml(event.getEventType() == null ? "" : event.getEventType().name())
                            + "</td>");
                    out.println("          <td>"
                            + escapeHtml(event.getMagicId() == null ? "" : String.valueOf(event.getMagicId()))
                            + "</td>");
                    out.println("          <td>" + escapeHtml(orEmpty(event.getEmailNormalized())) + "</td>");
                    out.println("          <td>" + escapeHtml(orEmpty(event.getRequestId())) + "</td>");
                    out.println("          <td>" + escapeHtml(formatIp(event.getRequestIp())) + "</td>");
                    out.println("          <td>" + escapeHtml(orEmpty(event.getSmtpProvider())) + "</td>");
                    out.println("          <td>" + escapeHtml(orEmpty(event.getSmtpMessageId())) + "</td>");
                    out.println("          <td>" + escapeHtml(orEmpty(event.getSmtpReplyCode())) + "</td>");
                    out.println("          <td>" + escapeHtml(renderError(event)) + "</td>");
                    out.println("        </tr>");
                }
                out.println("      </tbody>");
                out.println("    </table>");
            }

            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
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
        out.println("        <tr>");
        out.println("          <th>" + escapeHtml(label) + "</th>");
        out.println("          <td>" + escapeHtml(orEmpty(value)) + "</td>");
        out.println("        </tr>");
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

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }

        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            renderForbidden(response, request.getContextPath());
            return Optional.empty();
        }

        return authenticatedUser;
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Access Denied - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Access Denied</h1>");
            out.println("    <p>You must be an InteropHub admin to view this page.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderError(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>User Detail Error - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Could Not Load User</h1>");
            out.println("    <p>" + escapeHtml(orEmpty(message)) + "</p>");
            out.println("    <p><a href=\"" + contextPath + "/admin/users\">Back to Registered Users</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
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
