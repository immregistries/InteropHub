package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EmailSendLogDao;
import org.airahub.interophub.model.EmailSendLog;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminEmailLogServlet extends HttpServlet {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_LIMIT = 20;
    private static final int SEARCH_LIMIT = 100;

    private final AuthFlowService authFlowService;
    private final EmailSendLogDao emailSendLogDao;

    public AdminEmailLogServlet() {
        this.authFlowService = new AuthFlowService();
        this.emailSendLogDao = new EmailSendLogDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String searchEmail = trimToNull(request.getParameter("email"));

        List<EmailSendLog> rows;
        if (searchEmail != null) {
            String normalized = normalizeEmail(searchEmail);
            rows = (normalized != null)
                    ? emailSendLogDao.findByEmailNormalized(normalized, SEARCH_LIMIT)
                    : List.of();
        } else {
            rows = emailSendLogDao.findRecentSent(DEFAULT_LIMIT);
        }

        renderPage(response, contextPath, searchEmail, rows);
    }

    private void renderPage(HttpServletResponse response, String contextPath,
            String searchEmail, List<EmailSendLog> rows) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Email Log - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Email Log</h2>");

                // Search form
                panelOut.println("        <form method=\"get\" action=\"" + contextPath + "/admin/emails\">");
                panelOut.println("          <label for=\"email\">Search by email address:</label>");
                panelOut.println("          <input id=\"email\" name=\"email\" type=\"email\" value=\""
                        + escapeHtml(orEmpty(searchEmail)) + "\" placeholder=\"user@example.com\" />");
                panelOut.println("          <button type=\"submit\">Search</button>");
                if (searchEmail != null) {
                    panelOut.println("          &nbsp; <a href=\"" + contextPath
                            + "/admin/emails\">Clear</a>");
                }
                panelOut.println("        </form>");

                // Result header
                if (searchEmail != null) {
                    panelOut.println("        <p>Showing up to " + SEARCH_LIMIT
                            + " emails sent to <strong>" + escapeHtml(searchEmail) + "</strong>.</p>");
                } else {
                    panelOut.println("        <p>Showing last " + DEFAULT_LIMIT + " emails sent.</p>");
                }

                // Table
                panelOut.println("        <table class=\"data-table\">");
                panelOut.println("          <thead>");
                panelOut.println("            <tr>");
                panelOut.println("              <th>Sent At</th>");
                panelOut.println("              <th>Reason</th>");
                panelOut.println("              <th>To</th>");
                panelOut.println("              <th>Subject</th>");
                panelOut.println("              <th>SMTP Provider</th>");
                panelOut.println("              <th>Message ID</th>");
                panelOut.println("              <th>Body</th>");
                panelOut.println("            </tr>");
                panelOut.println("          </thead>");
                panelOut.println("          <tbody>");

                if (rows.isEmpty()) {
                    panelOut.println("            <tr><td colspan=\"7\">No emails found.</td></tr>");
                }

                for (EmailSendLog row : rows) {
                    String sentAt = row.getSentAt() != null ? DATETIME_FMT.format(row.getSentAt()) : "";
                    String userLink = row.getUserId() != null
                            ? "<a href=\"" + contextPath + "/admin/users/" + row.getUserId() + "\">"
                                    + escapeHtml(row.getRecipientEmail()) + "</a>"
                            : escapeHtml(row.getRecipientEmail());

                    panelOut.println("            <tr>");
                    panelOut.println("              <td>" + escapeHtml(sentAt) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(orEmpty(row.getEmailReason())) + "</td>");
                    panelOut.println("              <td>" + userLink + "</td>");
                    panelOut.println("              <td>" + escapeHtml(orEmpty(row.getSubject())) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(orEmpty(row.getSmtpProvider())) + "</td>");
                    panelOut.println("              <td>" + escapeHtml(orEmpty(row.getSmtpMessageId())) + "</td>");
                    panelOut.println("              <td>");
                    if (row.getBodyText() != null && !row.getBodyText().isBlank()) {
                        panelOut.println("                <details>");
                        panelOut.println("                  <summary>View body</summary>");
                        panelOut.println("                  <pre style=\"white-space:pre-wrap;\">"
                                + escapeHtml(row.getBodyText()) + "</pre>");
                        panelOut.println("                </details>");
                    }
                    panelOut.println("              </td>");
                    panelOut.println("            </tr>");
                }

                panelOut.println("          </tbody>");
                panelOut.println("        </table>");
                panelOut.println("      </section>");
            });
        }
    }

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(user.get())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                AdminShellRenderer.render(out, "Access Denied - InteropHub", request.getContextPath(), panelOut -> {
                    panelOut.println("      <section class=\"panel\">");
                    panelOut.println("        <h2>Access Denied</h2>");
                    panelOut.println("        <p>You must be an InteropHub admin to access this page.</p>");
                    panelOut.println("        <p><a href=\"" + request.getContextPath()
                            + "/welcome\">Return to Welcome</a></p>");
                    panelOut.println("      </section>");
                });
            }
            return Optional.empty();
        }
        return user;
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim().toLowerCase(java.util.Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeHtml(String value) {
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
