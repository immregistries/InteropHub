package org.airahub.interophub.servlet;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EmailSendLogDao;
import org.airahub.interophub.model.EmailSendLog;
import org.airahub.interophub.model.User;

public class AdminEmailLogServlet extends HttpServlet {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_LIMIT = 20;
    private static final int SEARCH_LIMIT = 100;

    private final EmailSendLogDao emailSendLogDao;

    public AdminEmailLogServlet() {
        this.emailSendLogDao = new EmailSendLogDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

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

        renderPage(request, response, searchEmail, rows);
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response,
            String searchEmail, List<EmailSendLog> rows) throws IOException {
        String contextPath = request.getContextPath();

        AdminShellRenderer.render(request, response, "Email Log - InteropHub", AdminSection.PEOPLE,
                "/admin/emails", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Email Log</h2>");

                    // Search form
                    out.println("            <form class=\"aira-form\" method=\"get\" action=\"" + contextPath + "/admin/emails\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"email\">Search by email address:</label>");
                    out.println("                <input class=\"aira-input\" id=\"email\" name=\"email\" type=\"email\" value=\""
                            + escapeHtml(orEmpty(searchEmail)) + "\" placeholder=\"user@example.com\" />");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Search</button>");
                    if (searchEmail != null) {
                        out.println("                <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                                + "/admin/emails\">Clear</a>");
                    }
                    out.println("              </div>");
                    out.println("            </form>");

                    // Result header
                    if (searchEmail != null) {
                        out.println("            <p class=\"aira-meta\">Showing up to " + SEARCH_LIMIT
                                + " emails sent to <strong>" + escapeHtml(searchEmail) + "</strong>.</p>");
                    } else {
                        out.println("            <p class=\"aira-meta\">Showing last " + DEFAULT_LIMIT + " emails sent.</p>");
                    }

                    // Table
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead>");
                    out.println("                <tr>");
                    out.println("                  <th>Sent At</th>");
                    out.println("                  <th>Reason</th>");
                    out.println("                  <th>To</th>");
                    out.println("                  <th>Subject</th>");
                    out.println("                  <th>SMTP Provider</th>");
                    out.println("                  <th>Message ID</th>");
                    out.println("                  <th>Body</th>");
                    out.println("                </tr>");
                    out.println("              </thead>");
                    out.println("              <tbody>");

                    if (rows.isEmpty()) {
                        out.println("                <tr><td colspan=\"7\">No emails found.</td></tr>");
                    }

                    for (EmailSendLog row : rows) {
                        String sentAt = row.getSentAt() != null ? DATETIME_FMT.format(row.getSentAt()) : "";
                        String userLink = row.getUserId() != null
                                ? "<a class=\"aira-inline-link\" href=\"" + contextPath + "/admin/users/" + row.getUserId() + "\">"
                                        + escapeHtml(row.getRecipientEmail()) + "</a>"
                                : escapeHtml(row.getRecipientEmail());

                        out.println("                <tr>");
                        out.println("                  <td>" + escapeHtml(sentAt) + "</td>");
                        out.println("                  <td>" + escapeHtml(orEmpty(row.getEmailReason())) + "</td>");
                        out.println("                  <td>" + userLink + "</td>");
                        out.println("                  <td>" + escapeHtml(orEmpty(row.getSubject())) + "</td>");
                        out.println("                  <td>" + escapeHtml(orEmpty(row.getSmtpProvider())) + "</td>");
                        out.println("                  <td>" + escapeHtml(orEmpty(row.getSmtpMessageId())) + "</td>");
                        out.println("                  <td>");
                        if (row.getBodyText() != null && !row.getBodyText().isBlank()) {
                            out.println("                    <details>");
                            out.println("                      <summary>View body</summary>");
                            out.println("                      <pre style=\"white-space:pre-wrap;\">"
                                    + escapeHtml(row.getBodyText()) + "</pre>");
                            out.println("                    </details>");
                        }
                        out.println("                  </td>");
                        out.println("                </tr>");
                    }

                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");
                    out.println("          </section>");
                });
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
