package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Optional;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.ConnectWorkspaceDao;
import org.airahub.interophub.dao.IgTopicDao;
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

    public WorkspaceCenterServlet() {
        this.authFlowService = new AuthFlowService();
        this.connectWorkspaceDao = new ConnectWorkspaceDao();
        this.igTopicDao = new IgTopicDao();
        this.workspaceEnrollmentDao = new WorkspaceEnrollmentDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
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
