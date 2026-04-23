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
            AdminShellRenderer.render(out, "User Detail - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>User Detail</h2>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/users\">&larr; Back to Registered Users</a></p>");

                panelOut.println("        <h2>Profile</h2>");
                panelOut.println("        <table class=\"data-table\">");
                panelOut.println("          <tbody>");
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
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");

                panelOut.println("        <h2>Magic Link Email Send Events (Last 50)</h2>");
                if (sendEvents.isEmpty()) {
                    panelOut.println("        <p>No send events found for this user.</p>");
                } else {
                    panelOut.println("        <table class=\"data-table\">");
                    panelOut.println("          <thead>");
                    panelOut.println("            <tr>");
                    panelOut.println("              <th>Event Time</th>");
                    panelOut.println("              <th>Type</th>");
                    panelOut.println("              <th>Magic ID</th>");
                    panelOut.println("              <th>Email</th>");
                    panelOut.println("              <th>Request ID</th>");
                    panelOut.println("              <th>Request IP</th>");
                    panelOut.println("              <th>SMTP Provider</th>");
                    panelOut.println("              <th>SMTP Message ID</th>");
                    panelOut.println("              <th>SMTP Reply</th>");
                    panelOut.println("              <th>Error</th>");
                    panelOut.println("            </tr>");
                    panelOut.println("          </thead>");
                    panelOut.println("          <tbody>");
                    for (MagicLinkSendEvent event : sendEvents) {
                        panelOut.println("            <tr>");
                        panelOut.println(
                                "              <td>" + escapeHtml(formatDateTime(event.getEventAt())) + "</td>");
                        panelOut.println("              <td>"
                                + escapeHtml(event.getEventType() == null ? "" : event.getEventType().name())
                                + "</td>");
                        panelOut.println("              <td>"
                                + escapeHtml(event.getMagicId() == null ? "" : String.valueOf(event.getMagicId()))
                                + "</td>");
                        panelOut.println(
                                "              <td>" + escapeHtml(orEmpty(event.getEmailNormalized())) + "</td>");
                        panelOut.println("              <td>" + escapeHtml(orEmpty(event.getRequestId())) + "</td>");
                        panelOut.println("              <td>" + escapeHtml(formatIp(event.getRequestIp())) + "</td>");
                        panelOut.println("              <td>" + escapeHtml(orEmpty(event.getSmtpProvider())) + "</td>");
                        panelOut.println(
                                "              <td>" + escapeHtml(orEmpty(event.getSmtpMessageId())) + "</td>");
                        panelOut.println(
                                "              <td>" + escapeHtml(orEmpty(event.getSmtpReplyCode())) + "</td>");
                        panelOut.println("              <td>" + escapeHtml(renderError(event)) + "</td>");
                        panelOut.println("            </tr>");
                    }
                    panelOut.println("          </tbody>");
                    panelOut.println("        </table>");
                }

                panelOut.println("      </section>");
            });
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
            AdminShellRenderer.render(out, "Access Denied - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Access Denied</h2>");
                panelOut.println("        <p>You must be an InteropHub admin to view this page.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderError(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "User Detail Error - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Could Not Load User</h2>");
                panelOut.println("        <p>" + escapeHtml(orEmpty(message)) + "</p>");
                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/users\">Back to Registered Users</a></p>");
                panelOut.println("      </section>");
            });
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
