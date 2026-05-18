package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

/**
 * Admin edit page for a single auth_user account.
 * Route: /admin/users/edit?userId={id}
 * Editable fields: email, firstName, lastName, displayName, organization,
 * roleTitle.
 */
public class AdminUserEditServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final UserDao userDao;

    public AdminUserEditServlet() {
        this.authFlowService = new AuthFlowService();
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
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
            renderUserNotFound(response, request.getContextPath(), userId);
            return;
        }

        renderForm(response, request.getContextPath(), targetUser.get(), null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
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
            renderUserNotFound(response, contextPath, userId);
            return;
        }

        User user = targetUserOpt.get();

        String email = trimToNull(request.getParameter("email"));
        String firstName = trimToNull(request.getParameter("firstName"));
        String lastName = trimToNull(request.getParameter("lastName"));
        String displayName = trimToNull(request.getParameter("displayName"));
        String organization = trimToNull(request.getParameter("organization"));
        String roleTitle = trimToNull(request.getParameter("roleTitle"));

        if (email == null) {
            renderForm(response, contextPath, user, "Email is required.");
            return;
        }
        if (email.length() > 254) {
            renderForm(response, contextPath, user, "Email must be 254 characters or fewer.");
            return;
        }

        String emailNormalized = email.toLowerCase(java.util.Locale.ROOT).trim();

        // Uniqueness check: reject if another user already owns this normalised email
        Optional<User> existingOwner = userDao.findByEmailNormalized(emailNormalized);
        if (existingOwner.isPresent() && !existingOwner.get().getUserId().equals(userId)) {
            renderForm(response, contextPath, user, "That email address is already in use by another account.");
            return;
        }

        if (firstName != null && firstName.length() > 100) {
            renderForm(response, contextPath, user, "First name must be 100 characters or fewer.");
            return;
        }
        if (lastName != null && lastName.length() > 100) {
            renderForm(response, contextPath, user, "Last name must be 100 characters or fewer.");
            return;
        }
        if (displayName != null && displayName.length() > 160) {
            renderForm(response, contextPath, user, "Display name must be 160 characters or fewer.");
            return;
        }
        if (organization != null && organization.length() > 200) {
            renderForm(response, contextPath, user, "Organization must be 200 characters or fewer.");
            return;
        }
        if (roleTitle != null && roleTitle.length() > 200) {
            renderForm(response, contextPath, user, "Role title must be 200 characters or fewer.");
            return;
        }

        user.setEmail(email);
        user.setEmailNormalized(emailNormalized);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setDisplayName(displayName);
        user.setOrganization(organization);
        user.setRoleTitle(roleTitle);
        userDao.saveOrUpdate(user);

        response.sendRedirect(contextPath + "/admin/users/detail?userId=" + userId + "&saved=1");
    }

    private void renderForm(HttpServletResponse response, String contextPath,
            User user, String errorMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Edit User - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Edit User</h2>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/users/detail?userId=" + user.getUserId()
                        + "\">&larr; Back to User Detail</a></p>");

                if (errorMessage != null) {
                    panelOut.println("        <p class=\"error\"><strong>Error:</strong> "
                            + escapeHtml(errorMessage) + "</p>");
                }

                panelOut.println("        <form class=\"login-form\" method=\"post\" action=\""
                        + contextPath + "/admin/users/edit\">");
                panelOut.println("          <input type=\"hidden\" name=\"userId\" value=\""
                        + escapeHtml(String.valueOf(user.getUserId())) + "\" />");

                panelOut.println("          <label for=\"userId\">User ID</label>");
                panelOut.println("          <input id=\"userId\" type=\"text\" value=\""
                        + escapeHtml(String.valueOf(user.getUserId())) + "\" disabled />");

                panelOut.println("          <label for=\"email\">Email</label>");
                panelOut.println("          <input id=\"email\" name=\"email\" type=\"email\" required"
                        + " maxlength=\"254\" value=\""
                        + escapeHtml(orEmpty(user.getEmail())) + "\" />");

                panelOut.println("          <label for=\"firstName\">First Name</label>");
                panelOut.println("          <input id=\"firstName\" name=\"firstName\" type=\"text\""
                        + " maxlength=\"100\" value=\""
                        + escapeHtml(orEmpty(user.getFirstName())) + "\" />");

                panelOut.println("          <label for=\"lastName\">Last Name</label>");
                panelOut.println("          <input id=\"lastName\" name=\"lastName\" type=\"text\""
                        + " maxlength=\"100\" value=\""
                        + escapeHtml(orEmpty(user.getLastName())) + "\" />");

                panelOut.println("          <label for=\"displayName\">Display Name Override</label>");
                panelOut.println("          <input id=\"displayName\" name=\"displayName\" type=\"text\""
                        + " maxlength=\"160\" value=\""
                        + escapeHtml(orEmpty(user.getDisplayName())) + "\" />");
                panelOut.println("          <small>When set, this overrides first + last name in the UI."
                        + " Leave blank to use first/last name.</small>");

                panelOut.println("          <label for=\"organization\">Organization</label>");
                panelOut.println("          <input id=\"organization\" name=\"organization\" type=\"text\""
                        + " maxlength=\"200\" value=\""
                        + escapeHtml(orEmpty(user.getOrganization())) + "\" />");

                panelOut.println("          <label for=\"roleTitle\">Role Title</label>");
                panelOut.println("          <input id=\"roleTitle\" name=\"roleTitle\" type=\"text\""
                        + " maxlength=\"200\" value=\""
                        + escapeHtml(orEmpty(user.getRoleTitle())) + "\" />");

                panelOut.println("          <div class=\"form-actions\">");
                panelOut.println("            <button type=\"submit\">Save Changes</button>");
                panelOut.println("            <a class=\"button-link\" href=\"" + contextPath
                        + "/admin/users/detail?userId=" + user.getUserId() + "\">Cancel</a>");
                panelOut.println("          </div>");
                panelOut.println("        </form>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderUserNotFound(HttpServletResponse response, String contextPath, Long userId)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "User Not Found - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>User Not Found</h2>");
                panelOut.println("        <p>No user found with ID: <strong>"
                        + escapeHtml(String.valueOf(userId)) + "</strong></p>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/users\">Back to Registered Users</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
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
        return authenticatedUser;
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
