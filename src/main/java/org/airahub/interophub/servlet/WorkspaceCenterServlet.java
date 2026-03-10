package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.ConnectWorkspaceDao;
import org.airahub.interophub.dao.IgTopicDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.dao.WorkspaceEnrollmentDao;
import org.airahub.interophub.model.ConnectWorkspace;
import org.airahub.interophub.model.IgTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.model.WorkspaceEnrollment;
import org.airahub.interophub.service.AuthFlowService;

public class WorkspaceCenterServlet extends HttpServlet {
    private final AuthFlowService authFlowService;
    private final ConnectWorkspaceDao connectWorkspaceDao;
    private final IgTopicDao igTopicDao;
    private final WorkspaceEnrollmentDao workspaceEnrollmentDao;
    private final UserDao userDao;

    public WorkspaceCenterServlet() {
        this.authFlowService = new AuthFlowService();
        this.connectWorkspaceDao = new ConnectWorkspaceDao();
        this.igTopicDao = new IgTopicDao();
        this.workspaceEnrollmentDao = new WorkspaceEnrollmentDao();
        this.userDao = new UserDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String mode = trimToNull(request.getParameter("mode"));
        if ("admin-enrollments".equalsIgnoreCase(mode)) {
            if (!authFlowService.isAdminUser(authenticatedUser.get())) {
                renderForbidden(response, request.getContextPath());
                return;
            }
            renderAdminEnrollmentPage(response, request.getContextPath(), null);
            return;
        }

        renderWorkspaceCenter(response, request.getContextPath(), authenticatedUser.get(),
                trimToNull(request.getParameter("workspaceId")), null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String mode = trimToNull(request.getParameter("mode"));
        if ("admin-enrollments".equalsIgnoreCase(mode)) {
            if (!authFlowService.isAdminUser(authenticatedUser.get())) {
                renderForbidden(response, request.getContextPath());
                return;
            }
            handleAdminEnrollmentAction(request, response, request.getContextPath(), authenticatedUser.get());
            return;
        }

        String workspaceIdRaw = trimToNull(request.getParameter("workspaceId"));
        String action = trimToNull(request.getParameter("action"));
        String message = null;

        Long workspaceId = parseId(workspaceIdRaw);
        if (workspaceId != null && "join".equalsIgnoreCase(action)) {
            WorkspaceEnrollment existing = workspaceEnrollmentDao
                    .findByWorkspaceAndUser(workspaceId, authenticatedUser.get().getUserId())
                    .orElse(null);
            if (existing == null) {
                WorkspaceEnrollment enrollment = new WorkspaceEnrollment();
                enrollment.setWorkspaceId(workspaceId);
                enrollment.setUserId(authenticatedUser.get().getUserId());
                enrollment.setState(WorkspaceEnrollment.EnrollmentState.PENDING);
                enrollment.setConsentAt(LocalDateTime.now());
                workspaceEnrollmentDao.save(enrollment);
                message = "Your join request has been submitted and is pending approval.";
            }
        }

        renderWorkspaceCenter(response, request.getContextPath(), authenticatedUser.get(), workspaceIdRaw, message);
    }

    private void handleAdminEnrollmentAction(HttpServletRequest request, HttpServletResponse response,
            String contextPath,
            User adminUser) throws IOException {
        String action = trimToNull(request.getParameter("action"));
        Long enrollmentId = parseId(trimToNull(request.getParameter("enrollmentId")));
        String message;

        if (enrollmentId == null) {
            renderAdminEnrollmentPage(response, contextPath, "A valid enrollmentId is required.");
            return;
        }

        WorkspaceEnrollment enrollment = workspaceEnrollmentDao.findById(enrollmentId).orElse(null);
        if (enrollment == null) {
            renderAdminEnrollmentPage(response, contextPath, "Enrollment record was not found.");
            return;
        }

        if ("approve".equalsIgnoreCase(action)) {
            if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.PENDING) {
                enrollment.setState(WorkspaceEnrollment.EnrollmentState.APPROVED);
                enrollment.setApprovedByUserId(adminUser.getUserId());
                enrollment.setApprovedAt(LocalDateTime.now());
                workspaceEnrollmentDao.saveOrUpdate(enrollment);
                message = "Enrollment approved.";
            } else {
                message = "Enrollment is no longer pending and was not changed.";
            }
        } else if ("reject".equalsIgnoreCase(action)) {
            if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.PENDING) {
                enrollment.setState(WorkspaceEnrollment.EnrollmentState.REJECTED);
                enrollment.setApprovedByUserId(adminUser.getUserId());
                enrollment.setApprovedAt(LocalDateTime.now());
                workspaceEnrollmentDao.saveOrUpdate(enrollment);
                message = "Enrollment rejected.";
            } else {
                message = "Enrollment is no longer pending and was not changed.";
            }
        } else {
            message = "Unsupported action.";
        }

        renderAdminEnrollmentPage(response, contextPath, message);
    }

    private void renderAdminEnrollmentPage(HttpServletResponse response, String contextPath, String message)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        List<ConnectWorkspace> activeWorkspaces = connectWorkspaceDao.findActiveOrderedByStartDate();
        Map<Long, String> topicNameById = new HashMap<>();
        for (IgTopic topic : igTopicDao.findAllOrdered()) {
            topicNameById.put(topic.getTopicId(), topic.getTopicName());
        }

        // Load all enrollments and related users in batches to avoid per-row queries.
        Map<Long, List<WorkspaceEnrollment>> enrollmentsByWorkspaceId = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        for (ConnectWorkspace workspace : activeWorkspaces) {
            List<WorkspaceEnrollment> enrollments = workspaceEnrollmentDao
                    .findByWorkspaceId(workspace.getWorkspaceId());
            enrollmentsByWorkspaceId.put(workspace.getWorkspaceId(), enrollments);
            for (WorkspaceEnrollment enrollment : enrollments) {
                if (enrollment.getUserId() != null) {
                    userIds.add(enrollment.getUserId());
                }
            }
        }

        Map<Long, User> userById = new HashMap<>();
        for (User user : userDao.findByIds(new ArrayList<>(userIds))) {
            userById.put(user.getUserId(), user);
        }

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Workspace Registrations - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Workspace Registrations</h1>");
            out.println("    <p>Approve or reject pending workspace registrations.</p>");
            if (message != null) {
                out.println("    <p><strong>" + escapeHtml(message) + "</strong></p>");
            }

            if (activeWorkspaces.isEmpty()) {
                out.println("    <p>No active workspaces are currently available.</p>");
            }

            for (ConnectWorkspace workspace : activeWorkspaces) {
                String topicName = topicNameById.get(workspace.getTopicId());
                if (topicName == null || topicName.isBlank()) {
                    topicName = "Unknown Topic";
                }
                String workspaceName = workspace.getWorkspaceName();
                if (workspaceName == null || workspaceName.isBlank()) {
                    workspaceName = "(Unnamed Workspace)";
                }

                out.println("    <h2>" + escapeHtml(topicName + ": " + workspaceName) + "</h2>");

                List<WorkspaceEnrollment> enrollments = new ArrayList<>(
                        enrollmentsByWorkspaceId.getOrDefault(workspace.getWorkspaceId(), List.of()));
                enrollments.sort(Comparator.comparing(enrollment -> toSortKey(userById.get(enrollment.getUserId()))));

                if (enrollments.isEmpty()) {
                    out.println("    <p>No registrations yet.</p>");
                    continue;
                }

                out.println("    <table class=\"data-table\">");
                out.println("      <thead>");
                out.println("        <tr>");
                out.println("          <th>Display Name</th>");
                out.println("          <th>Organization</th>");
                out.println("          <th>Email</th>");
                out.println("          <th>Action</th>");
                out.println("        </tr>");
                out.println("      </thead>");
                out.println("      <tbody>");
                for (WorkspaceEnrollment enrollment : enrollments) {
                    User user = userById.get(enrollment.getUserId());
                    String displayName = user == null ? "" : trimToNull(user.getDisplayName());
                    if (displayName == null) {
                        displayName = user == null ? "(Unknown User)" : orEmpty(user.getEmail());
                    }
                    String organization = user == null ? "" : orEmpty(user.getOrganization());
                    String email = user == null ? "" : orEmpty(user.getEmail());

                    out.println("        <tr>");
                    out.println("          <td>" + escapeHtml(displayName) + "</td>");
                    out.println("          <td>" + escapeHtml(organization) + "</td>");
                    out.println("          <td>" + escapeHtml(email) + "</td>");
                    out.println("          <td>");
                    if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.PENDING) {
                        out.println("            <form style=\"display:inline\" action=\"" + contextPath
                                + "/workspace\" method=\"post\">");
                        out.println(
                                "              <input type=\"hidden\" name=\"mode\" value=\"admin-enrollments\" />");
                        out.println("              <input type=\"hidden\" name=\"enrollmentId\" value=\""
                                + enrollment.getEnrollmentId() + "\" />");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"approve\" />");
                        out.println("              <button type=\"submit\">Approve</button>");
                        out.println("            </form>");

                        out.println("            <form style=\"display:inline\" action=\"" + contextPath
                                + "/workspace\" method=\"post\">");
                        out.println(
                                "              <input type=\"hidden\" name=\"mode\" value=\"admin-enrollments\" />");
                        out.println("              <input type=\"hidden\" name=\"enrollmentId\" value=\""
                                + enrollment.getEnrollmentId() + "\" />");
                        out.println("              <input type=\"hidden\" name=\"action\" value=\"reject\" />");
                        out.println("              <button type=\"submit\">Reject</button>");
                        out.println("            </form>");
                    } else {
                        out.println("            " + escapeHtml(enrollment.getState().name()));
                    }
                    out.println("          </td>");
                    out.println("        </tr>");
                }
                out.println("      </tbody>");
                out.println("    </table>");
            }

            out.println("    <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderWorkspaceCenter(HttpServletResponse response, String contextPath, User user,
            String workspaceIdRaw,
            String submittedMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        Long workspaceId = parseId(workspaceIdRaw);
        if (workspaceId == null) {
            renderError(response, contextPath, "A valid workspaceId is required.");
            return;
        }

        ConnectWorkspace workspace = connectWorkspaceDao.findById(workspaceId).orElse(null);
        if (workspace == null) {
            renderError(response, contextPath, "Workspace entry was not found.");
            return;
        }

        IgTopic topic = igTopicDao.findById(workspace.getTopicId()).orElse(null);
        if (topic == null) {
            renderError(response, contextPath, "Associated topic entry was not found.");
            return;
        }

        WorkspaceEnrollment enrollment = workspaceEnrollmentDao
                .findByWorkspaceAndUser(workspaceId, user.getUserId())
                .orElse(null);

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Workspace Center - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Workspace Center</h1>");

            if (submittedMessage != null) {
                out.println("    <p><strong>" + escapeHtml(submittedMessage) + "</strong></p>");
            }

            if (enrollment == null) {
                out.println("    <p>You are not currently enrolled in this workspace.</p>");
                out.println("    <form action=\"" + contextPath + "/workspace\" method=\"post\">");
                out.println("      <input type=\"hidden\" name=\"workspaceId\" value=\"" + workspace.getWorkspaceId()
                        + "\" />");
                out.println("      <input type=\"hidden\" name=\"action\" value=\"join\" />");
                out.println("      <button type=\"submit\">Request To Join</button>");
                out.println("    </form>");
            } else if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.APPROVED) {
                out.println("    <h2>"
                        + escapeHtml(orEmpty(topic.getTopicName()) + ": " + orEmpty(workspace.getWorkspaceName()))
                        + "</h2>");
                out.println("    <p>" + escapeHtml(orEmpty(workspace.getDescription())) + "</p>");
            } else if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.PENDING) {
                out.println("    <p>Your enrollment status is <strong>PENDING</strong>. Please wait for approval.</p>");
            } else if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.REJECTED) {
                out.println("    <p>Your enrollment status is <strong>REJECTED</strong>.</p>");
            } else if (enrollment.getState() == WorkspaceEnrollment.EnrollmentState.SUSPENDED) {
                out.println("    <p>Your enrollment status is <strong>SUSPENDED</strong>.</p>");
            } else {
                out.println("    <p>Your enrollment status is <strong>" + escapeHtml(enrollment.getState().name())
                        + "</strong>.</p>");
            }

            out.println("    <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderError(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Workspace Center - Error</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Workspace Request Error</h1>");
            out.println("    <p>" + escapeHtml(message) + "</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
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
            out.println("    <p>You must be an InteropHub admin to manage workspace registrations.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private String toSortKey(User user) {
        if (user == null) {
            return "";
        }
        String displayName = trimToNull(user.getDisplayName());
        if (displayName != null) {
            return displayName.toLowerCase();
        }
        return orEmpty(user.getEmail()).toLowerCase();
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
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
