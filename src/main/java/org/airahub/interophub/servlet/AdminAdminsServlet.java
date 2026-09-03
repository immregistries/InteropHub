package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.User;

/**
 * Dedicated admin-management page: lists current admins with a one-click
 * remove, and a search box to grant admin access to an existing registered
 * user. Route: /admin/users/admins
 */
public class AdminAdminsServlet extends HttpServlet {

    private static final String ACTIVE_HREF = "/admin/users/admins";

    private final UserDao userDao;

    public AdminAdminsServlet() {
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String search = trimToNull(request.getParameter("search"));
        List<User> searchResults = search == null ? List.of() : userDao.searchUsers(search);
        List<User> currentAdmins = userDao.findAllAdmins();

        String message = request.getParameter("added") != null ? "Admin access granted."
                : request.getParameter("removed") != null ? "Admin access removed."
                        : null;

        renderPage(request, response, adminUser.get(), currentAdmins, search, searchResults, message, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String action = trimToNull(request.getParameter("action"));
        Long userId = parseId(request.getParameter("userId"));

        if (userId == null) {
            response.sendRedirect(contextPath + "/admin/users/admins");
            return;
        }

        Optional<User> targetUser = userDao.findById(userId);
        if (targetUser.isEmpty()) {
            response.sendRedirect(contextPath + "/admin/users/admins");
            return;
        }

        if ("removeAdmin".equals(action)) {
            if (userId.equals(adminUser.get().getUserId())) {
                List<User> currentAdmins = userDao.findAllAdmins();
                renderPage(request, response, adminUser.get(), currentAdmins, null, List.of(), null,
                        "You cannot remove your own admin access.");
                return;
            }
            User user = targetUser.get();
            user.setIsAdmin(false);
            userDao.saveOrUpdate(user);
            response.sendRedirect(contextPath + "/admin/users/admins?removed=1");
        } else if ("makeAdmin".equals(action)) {
            User user = targetUser.get();
            user.setIsAdmin(true);
            userDao.saveOrUpdate(user);
            response.sendRedirect(contextPath + "/admin/users/admins?added=1");
        } else {
            response.sendRedirect(contextPath + "/admin/users/admins");
        }
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response, User viewer,
            List<User> currentAdmins, String search, List<User> searchResults, String message, String errorMessage)
            throws IOException {
        String contextPath = request.getContextPath();
        boolean isSearch = search != null;

        AdminShellRenderer.render(request, response, "Admins - InteropHub", AdminSection.PEOPLE, ACTIVE_HREF,
                out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Admins</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Admins have full access to every admin screen in InteropHub. To grant access, the person must first sign in at least once to create their account, then you can search for them below.</p>");

                    if (message != null) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }
                    if (errorMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--danger\"><p>"
                                + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println(
                            "            <h3 class=\"aira-subsection-title\">Current Admins (" + currentAdmins.size()
                                    + ")</h3>");
                    renderAdminsTable(out, contextPath, currentAdmins, viewer);

                    out.println("            <h3 class=\"aira-subsection-title\">Add Admin</h3>");
                    out.println("            <form class=\"aira-form\" method=\"get\" action=\"" + contextPath
                            + "/admin/users/admins\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println(
                            "                <label for=\"search\">Search registered users by email, name, or organization:</label>");
                    out.println(
                            "                <input class=\"aira-input\" type=\"text\" id=\"search\" name=\"search\" value=\""
                                    + escapeHtml(search == null ? "" : search) + "\" />");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Search</button>");
                    if (isSearch) {
                        out.println(
                                "                <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                                        + "/admin/users/admins\">Clear</a>");
                    }
                    out.println("              </div>");
                    out.println("            </form>");

                    if (isSearch) {
                        renderSearchResultsTable(out, contextPath, searchResults);
                    }

                    out.println("          </section>");
                });
    }

    private void renderAdminsTable(java.io.PrintWriter out, String contextPath, List<User> admins, User viewer) {
        if (admins.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No admins found.</p>");
            return;
        }
        out.println("            <div class=\"aira-table-wrap\">");
        out.println("            <table class=\"aira-table\">");
        out.println("              <thead><tr><th>Name</th><th>Email</th><th>Organization</th><th></th></tr></thead>");
        out.println("              <tbody>");
        for (User user : admins) {
            String name = trimToNull(user.getFullName());
            if (name == null) {
                name = orEmpty(user.getEmail());
            }
            out.println("                <tr>");
            out.println("                  <td>" + escapeHtml(name) + "</td>");
            out.println("                  <td>" + escapeHtml(orEmpty(user.getEmail())) + "</td>");
            out.println("                  <td>" + escapeHtml(orEmpty(user.getOrganization())) + "</td>");
            out.println("                  <td>");
            if (user.getUserId().equals(viewer.getUserId())) {
                out.println("                    <span class=\"aira-meta\">(you)</span>");
            } else {
                out.println("                    <form class=\"aira-inline-form\" method=\"post\" action=\""
                        + contextPath + "/admin/users/admins\">");
                out.println("                      <input type=\"hidden\" name=\"action\" value=\"removeAdmin\" />");
                out.println("                      <input type=\"hidden\" name=\"userId\" value=\""
                        + user.getUserId() + "\" />");
                out.println(
                        "                      <button class=\"aira-button aira-button--danger aira-button--small\" type=\"submit\" onclick=\"return confirm('Remove admin access for "
                                + escapeHtml(name).replace("'", "\\'") + "?')\">Remove Admin</button>");
                out.println("                    </form>");
            }
            out.println("                  </td>");
            out.println("                </tr>");
        }
        out.println("              </tbody></table>");
        out.println("            </div>");
    }

    private void renderSearchResultsTable(java.io.PrintWriter out, String contextPath, List<User> results) {
        out.println("            <h4 class=\"aira-subsection-title\">Search Results (" + results.size() + ")</h4>");
        if (results.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No matching registered users found.</p>");
            return;
        }
        out.println("            <div class=\"aira-table-wrap\">");
        out.println("            <table class=\"aira-table\">");
        out.println("              <thead><tr><th>Name</th><th>Email</th><th>Organization</th><th></th></tr></thead>");
        out.println("              <tbody>");
        for (User user : results) {
            String name = trimToNull(user.getFullName());
            if (name == null) {
                name = orEmpty(user.getEmail());
            }
            out.println("                <tr>");
            out.println("                  <td>" + escapeHtml(name) + "</td>");
            out.println("                  <td>" + escapeHtml(orEmpty(user.getEmail())) + "</td>");
            out.println("                  <td>" + escapeHtml(orEmpty(user.getOrganization())) + "</td>");
            out.println("                  <td>");
            if (Boolean.TRUE.equals(user.getIsAdmin())) {
                out.println("                    <span class=\"aira-meta\">Already an admin</span>");
            } else {
                out.println("                    <form class=\"aira-inline-form\" method=\"post\" action=\""
                        + contextPath + "/admin/users/admins\">");
                out.println("                      <input type=\"hidden\" name=\"action\" value=\"makeAdmin\" />");
                out.println("                      <input type=\"hidden\" name=\"userId\" value=\""
                        + user.getUserId() + "\" />");
                out.println(
                        "                      <button class=\"aira-button aira-button--primary aira-button--small\" type=\"submit\">Make Admin</button>");
                out.println("                    </form>");
            }
            out.println("                  </td>");
            out.println("                </tr>");
        }
        out.println("              </tbody></table>");
        out.println("            </div>");
    }

    private Long parseId(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return null;
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
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
