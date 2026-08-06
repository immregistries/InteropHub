package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.User;

/**
 * Admin edit page for a single auth_user account.
 * Route: /admin/users/edit?userId={id}
 * Editable fields: email, firstName, lastName, displayName, organization,
 * roleTitle, isAdmin.
 */
public class AdminUserEditServlet extends HttpServlet {

    private final UserDao userDao;

    public AdminUserEditServlet() {
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        Long userId = parseUserId(request.getParameter("userId"));
        if (userId == null) {
            response.sendRedirect(request.getContextPath() + "/admin/users");
            return;
        }

        Optional<User> targetUser = userDao.findById(userId);
        if (targetUser.isEmpty()) {
            renderUserNotFound(request, response, userId);
            return;
        }

        renderForm(request, response, targetUser.get(), null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();

        Long userId = parseUserId(request.getParameter("userId"));
        if (userId == null) {
            response.sendRedirect(contextPath + "/admin/users");
            return;
        }

        Optional<User> targetUserOpt = userDao.findById(userId);
        if (targetUserOpt.isEmpty()) {
            renderUserNotFound(request, response, userId);
            return;
        }

        User user = targetUserOpt.get();

        String email = trimToNull(request.getParameter("email"));
        String firstName = trimToNull(request.getParameter("firstName"));
        String lastName = trimToNull(request.getParameter("lastName"));
        String displayName = trimToNull(request.getParameter("displayName"));
        String organization = trimToNull(request.getParameter("organization"));
        String roleTitle = trimToNull(request.getParameter("roleTitle"));
        boolean isAdmin = "on".equalsIgnoreCase(request.getParameter("isAdmin"));

        if (email == null) {
            renderForm(request, response, user, "Email is required.");
            return;
        }
        if (email.length() > 254) {
            renderForm(request, response, user, "Email must be 254 characters or fewer.");
            return;
        }

        String emailNormalized = email.toLowerCase(java.util.Locale.ROOT).trim();

        // Uniqueness check: reject if another user already owns this normalised email
        Optional<User> existingOwner = userDao.findByEmailNormalized(emailNormalized);
        if (existingOwner.isPresent() && !existingOwner.get().getUserId().equals(userId)) {
            renderForm(request, response, user, "That email address is already in use by another account.");
            return;
        }

        if (firstName != null && firstName.length() > 100) {
            renderForm(request, response, user, "First name must be 100 characters or fewer.");
            return;
        }
        if (lastName != null && lastName.length() > 100) {
            renderForm(request, response, user, "Last name must be 100 characters or fewer.");
            return;
        }
        if (displayName != null && displayName.length() > 160) {
            renderForm(request, response, user, "Display name must be 160 characters or fewer.");
            return;
        }
        if (organization != null && organization.length() > 200) {
            renderForm(request, response, user, "Organization must be 200 characters or fewer.");
            return;
        }
        if (roleTitle != null && roleTitle.length() > 200) {
            renderForm(request, response, user, "Role title must be 200 characters or fewer.");
            return;
        }

        user.setEmail(email);
        user.setEmailNormalized(emailNormalized);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setDisplayName(displayName);
        user.setOrganization(organization);
        user.setRoleTitle(roleTitle);
        user.setIsAdmin(isAdmin);
        userDao.saveOrUpdate(user);

        response.sendRedirect(contextPath + "/admin/users/detail?userId=" + userId + "&saved=1");
    }

    private void renderForm(HttpServletRequest request, HttpServletResponse response,
            User user, String errorMessage) throws IOException {
        String contextPath = request.getContextPath();

        AdminShellRenderer.render(request, response, "Edit User - InteropHub", AdminSection.PEOPLE,
                "/admin/users", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Edit User</h2>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/users/detail?userId=" + user.getUserId()
                            + "\">&larr; Back to User Detail</a></p>");

                    if (errorMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--danger\"><p><strong>Error:</strong> "
                                + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" method=\"post\" action=\""
                            + contextPath + "/admin/users/edit\">");
                    out.println("              <input type=\"hidden\" name=\"userId\" value=\""
                            + escapeHtml(String.valueOf(user.getUserId())) + "\" />");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"userId\">User ID</label>");
                    out.println("                <input class=\"aira-input\" id=\"userId\" type=\"text\" value=\""
                            + escapeHtml(String.valueOf(user.getUserId())) + "\" disabled />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"email\">Email</label>");
                    out.println("                <input class=\"aira-input\" id=\"email\" name=\"email\" type=\"email\" required"
                            + " maxlength=\"254\" value=\""
                            + escapeHtml(orEmpty(user.getEmail())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"firstName\">First Name</label>");
                    out.println("                <input class=\"aira-input\" id=\"firstName\" name=\"firstName\" type=\"text\""
                            + " maxlength=\"100\" value=\""
                            + escapeHtml(orEmpty(user.getFirstName())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"lastName\">Last Name</label>");
                    out.println("                <input class=\"aira-input\" id=\"lastName\" name=\"lastName\" type=\"text\""
                            + " maxlength=\"100\" value=\""
                            + escapeHtml(orEmpty(user.getLastName())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"displayName\">Display Name Override</label>");
                    out.println("                <input class=\"aira-input\" id=\"displayName\" name=\"displayName\" type=\"text\""
                            + " maxlength=\"160\" value=\""
                            + escapeHtml(orEmpty(user.getDisplayName())) + "\" />");
                    out.println("                <p class=\"aira-field-help\">When set, this overrides first + last name in the UI."
                            + " Leave blank to use first/last name.</p>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"organization\">Organization</label>");
                    out.println("                <input class=\"aira-input\" id=\"organization\" name=\"organization\" type=\"text\""
                            + " maxlength=\"200\" value=\""
                            + escapeHtml(orEmpty(user.getOrganization())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"roleTitle\">Role Title</label>");
                    out.println("                <input class=\"aira-input\" id=\"roleTitle\" name=\"roleTitle\" type=\"text\""
                            + " maxlength=\"200\" value=\""
                            + escapeHtml(orEmpty(user.getRoleTitle())) + "\" />");
                    out.println("              </div>");

                    out.println("              <label class=\"aira-radio\"><input id=\"isAdmin\" name=\"isAdmin\" type=\"checkbox\""
                            + (Boolean.TRUE.equals(user.getIsAdmin()) ? " checked" : "") + " /> Admin Access</label>");
                    out.println("              <p class=\"aira-field-help\">Enable to grant InteropHub admin permissions.</p>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save Changes</button>");
                    out.println("                <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/users/detail?userId=" + user.getUserId() + "\">Cancel</a>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("          </section>");
                });
    }

    private void renderUserNotFound(HttpServletRequest request, HttpServletResponse response, Long userId)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        String contextPath = request.getContextPath();

        AdminShellRenderer.render(request, response, "User Not Found - InteropHub", AdminSection.PEOPLE,
                "/admin/users", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">User Not Found</h2>");
                    out.println("            <p>No user found with ID: <strong>"
                            + escapeHtml(String.valueOf(userId)) + "</strong></p>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/users\">Back to Registered Users</a></p>");
                    out.println("          </section>");
                });
    }

    private Long parseUserId(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(rawValue.trim());
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
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
