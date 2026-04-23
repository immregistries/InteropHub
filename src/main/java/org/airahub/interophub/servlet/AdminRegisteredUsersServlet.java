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
            AdminShellRenderer.render(out, "Registered Users - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Registered Users</h2>");
                panelOut.println("        <p><a href=\"" + contextPath + "/welcome\">&larr; Back to Welcome</a></p>");

                panelOut.println("        <form method=\"get\" action=\"" + contextPath + "/admin/users\">");
                panelOut.println(
                        "          <label for=\"search\">Search by email, display name, or organization:</label>");
                panelOut.println("          <input type=\"text\" id=\"search\" name=\"search\" value=\""
                        + escapeHtml(search == null ? "" : search) + "\" />");
                panelOut.println("          <button type=\"submit\">Search</button>");
                if (isSearch) {
                    panelOut.println("          <a href=\"" + contextPath + "/admin/users\">Clear</a>");
                }
                panelOut.println("        </form>");

                if (isSearch) {
                    panelOut.println("        <h2>Search Results (" + users.size() + ")</h2>");
                } else {
                    panelOut.println("        <h2>Last 10 Registrations</h2>");
                }

                if (users.isEmpty()) {
                    panelOut.println("        <p>No users found.</p>");
                } else {
                    panelOut.println("        <table class=\"data-table\">");
                    panelOut.println("          <thead>");
                    panelOut.println("            <tr>");
                    panelOut.println("              <th>Display Name</th>");
                    panelOut.println("              <th>Organization</th>");
                    panelOut.println("              <th>Status</th>");
                    panelOut.println("              <th>Last Login</th>");
                    panelOut.println("            </tr>");
                    panelOut.println("          </thead>");
                    panelOut.println("          <tbody>");
                    for (User user : users) {
                        String userDetailLink = contextPath + "/admin/users/detail?userId=" + user.getUserId();
                        String displayText = trimToNull(user.getDisplayName());
                        if (displayText == null) {
                            displayText = orEmpty(user.getEmail());
                        }
                        panelOut.println("            <tr>");
                        panelOut.println("              <td><a href=\"" + userDetailLink + "\">"
                                + escapeHtml(displayText)
                                + "</a></td>");
                        panelOut.println("              <td>" + escapeHtml(orEmpty(user.getOrganization())) + "</td>");
                        panelOut.println(
                                "              <td>"
                                        + escapeHtml(user.getStatus() == null ? "" : user.getStatus().name())
                                        + "</td>");
                        panelOut.println("              <td>" + formatDateTime(user) + "</td>");
                        panelOut.println("            </tr>");
                    }
                    panelOut.println("          </tbody>");
                    panelOut.println("        </table>");
                }

                panelOut.println("      </section>");
            });
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
            AdminShellRenderer.render(out, "Access Denied - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Access Denied</h2>");
                panelOut.println(
                        "        <p>You must be an InteropHub admin to access registered user information.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
                panelOut.println("      </section>");
            });
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
