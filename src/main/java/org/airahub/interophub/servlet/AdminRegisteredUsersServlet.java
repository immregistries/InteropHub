package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminRegisteredUsersServlet extends HttpServlet {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthFlowService authFlowService;
    private final UserDao userDao;

    public AdminRegisteredUsersServlet() {
        this.authFlowService = new AuthFlowService();
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String search = trimToNull(request.getParameter("search"));

        List<User> users;
        boolean isSearch;
        if (search != null) {
            users = userDao.searchUsers(search);
            isSearch = true;
        } else {
            users = userDao.findRecentRegistrations(10);
            isSearch = false;
        }

        renderPage(response, contextPath, users, search, isSearch);
    }

    private void renderPage(HttpServletResponse response, String contextPath,
            List<User> users, String search, boolean isSearch) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Registered Users - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Registered Users</h1>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">&larr; Back to Welcome</a></p>");

            out.println("    <form method=\"get\" action=\"" + contextPath + "/admin/users\">");
            out.println("      <label for=\"search\">Search by email, display name, or organization:</label>");
            out.println("      <input type=\"text\" id=\"search\" name=\"search\" value=\""
                    + escapeHtml(search == null ? "" : search) + "\" />");
            out.println("      <button type=\"submit\">Search</button>");
            if (isSearch) {
                out.println("      <a href=\"" + contextPath + "/admin/users\">Clear</a>");
            }
            out.println("    </form>");

            if (isSearch) {
                out.println("    <h2>Search Results (" + users.size() + ")</h2>");
            } else {
                out.println("    <h2>Last 10 Registrations</h2>");
            }

            if (users.isEmpty()) {
                out.println("    <p>No users found.</p>");
            } else {
                out.println("    <table class=\"data-table\">");
                out.println("      <thead>");
                out.println("        <tr>");
                out.println("          <th>Email</th>");
                out.println("          <th>Display Name</th>");
                out.println("          <th>Organization</th>");
                out.println("          <th>Status</th>");
                out.println("          <th>Last Login</th>");
                out.println("        </tr>");
                out.println("      </thead>");
                out.println("      <tbody>");
                for (User user : users) {
                    out.println("        <tr>");
                    out.println("          <td>" + escapeHtml(orEmpty(user.getEmail())) + "</td>");
                    out.println("          <td>" + escapeHtml(orEmpty(user.getDisplayName())) + "</td>");
                    out.println("          <td>" + escapeHtml(orEmpty(user.getOrganization())) + "</td>");
                    out.println("          <td>" + escapeHtml(user.getStatus() == null ? "" : user.getStatus().name()) + "</td>");
                    out.println("          <td>" + formatDateTime(user) + "</td>");
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

    private String formatDateTime(User user) {
        if (user.getLastLoginAt() == null) {
            return "";
        }
        return escapeHtml(DATETIME_FORMATTER.format(user.getLastLoginAt()));
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
            out.println("    <p>You must be an InteropHub admin to access registered user information.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

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
