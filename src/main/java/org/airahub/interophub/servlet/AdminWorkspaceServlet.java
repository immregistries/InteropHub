package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.airahub.interophub.dao.ConnectWorkspaceDao;
import org.airahub.interophub.dao.IgTopicDao;
import org.airahub.interophub.model.ConnectWorkspace;
import org.airahub.interophub.model.IgTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminWorkspaceServlet extends HttpServlet {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter CREATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthFlowService authFlowService;
    private final ConnectWorkspaceDao connectWorkspaceDao;
    private final IgTopicDao igTopicDao;

    public AdminWorkspaceServlet() {
        this.authFlowService = new AuthFlowService();
        this.connectWorkspaceDao = new ConnectWorkspaceDao();
        this.igTopicDao = new IgTopicDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String mode = trimToNull(request.getParameter("mode"));
        String workspaceIdRaw = trimToNull(request.getParameter("workspaceId"));
        String topicIdRaw = trimToNull(request.getParameter("topicId"));

        if ("new".equalsIgnoreCase(mode)) {
            Long topicId = parseId(topicIdRaw);
            if (topicId == null) {
                renderError(response, contextPath, "A valid topicId is required to create a workspace.");
                return;
            }

            IgTopic topic = igTopicDao.findById(topicId).orElse(null);
            if (topic == null) {
                renderError(response, contextPath, "Topic entry was not found.");
                return;
            }

            ConnectWorkspace newWorkspace = new ConnectWorkspace();
            newWorkspace.setTopicId(topicId);
            newWorkspace.setStatus(ConnectWorkspace.WorkspaceStatus.ACTIVE);
            newWorkspace.setRequiresApproval(Boolean.TRUE);
            renderEditForm(response, contextPath, topic, newWorkspace, null, true);
            return;
        }

        if (workspaceIdRaw == null) {
            renderError(response, contextPath, "workspaceId is required.");
            return;
        }

        Long workspaceId = parseId(workspaceIdRaw);
        if (workspaceId == null) {
            renderError(response, contextPath, "Invalid workspace identifier.");
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

        if ("edit".equalsIgnoreCase(mode)) {
            renderEditForm(response, contextPath, topic, workspace, null, false);
            return;
        }

        renderDetails(response, contextPath, topic, workspace);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String workspaceIdRaw = trimToNull(request.getParameter("workspaceId"));
        String topicIdRaw = trimToNull(request.getParameter("topicId"));

        boolean createNew = workspaceIdRaw == null;
        ConnectWorkspace workspace;
        IgTopic topic;

        if (createNew) {
            Long topicId = parseId(topicIdRaw);
            if (topicId == null) {
                renderError(response, contextPath, "A valid topicId is required to create a workspace.");
                return;
            }
            topic = igTopicDao.findById(topicId).orElse(null);
            if (topic == null) {
                renderError(response, contextPath, "Topic entry was not found.");
                return;
            }

            workspace = new ConnectWorkspace();
            workspace.setTopicId(topicId);
            workspace.setCreatedByUserId(adminUser.get().getUserId());
            workspace.setRequiresApproval(Boolean.TRUE);
        } else {
            Long workspaceId = parseId(workspaceIdRaw);
            if (workspaceId == null) {
                renderError(response, contextPath, "Invalid workspace identifier.");
                return;
            }

            workspace = connectWorkspaceDao.findById(workspaceId).orElse(null);
            if (workspace == null) {
                renderError(response, contextPath, "Workspace entry was not found.");
                return;
            }

            topic = igTopicDao.findById(workspace.getTopicId()).orElse(null);
            if (topic == null) {
                renderError(response, contextPath, "Associated topic entry was not found.");
                return;
            }
        }

        String workspaceName = trimToNull(request.getParameter("workspaceName"));
        String description = trimToNull(request.getParameter("description"));
        String startDateRaw = trimToNull(request.getParameter("startDate"));
        String endDateRaw = trimToNull(request.getParameter("endDate"));
        String statusRaw = trimToNull(request.getParameter("status"));

        try {
            workspace.setWorkspaceName(required(workspaceName, "Workspace name"));
            workspace.setDescription(description);
            workspace.setStartDate(parseDate(startDateRaw, "Start date"));
            workspace.setEndDate(parseDate(endDateRaw, "End date"));
            workspace.setStatus(parseStatus(required(statusRaw, "Status")));
            validateDateOrder(workspace.getStartDate(), workspace.getEndDate());

            connectWorkspaceDao.saveOrUpdate(workspace);
            response.sendRedirect(contextPath + "/admin/topics?topicId=" + workspace.getTopicId());
        } catch (Exception ex) {
            workspace.setWorkspaceName(workspaceName);
            workspace.setDescription(description);
            workspace.setStartDate(safeParseDate(startDateRaw));
            workspace.setEndDate(safeParseDate(endDateRaw));
            if (statusRaw != null) {
                try {
                    workspace.setStatus(parseStatus(statusRaw));
                } catch (Exception ignored) {
                    // Keep existing status for redisplay.
                }
            }
            renderEditForm(response, contextPath, topic, workspace, ex.getMessage(), createNew);
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

    private void renderDetails(HttpServletResponse response, String contextPath, IgTopic topic,
            ConnectWorkspace workspace)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Workspace Details - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Workspace Details</h1>");
            out.println("    <p>This view is read-only. Use Edit to change this workspace.</p>");

            out.println("    <section class=\"panel\">");
            out.println("      <p><strong>Topic:</strong> " + escapeHtml(orEmpty(topic.getTopicName())) + "</p>");
            out.println("      <p><strong>Workspace Name:</strong> " + escapeHtml(orEmpty(workspace.getWorkspaceName()))
                    + "</p>");
            out.println("      <p><strong>Description:</strong> " + escapeHtml(orEmpty(workspace.getDescription()))
                    + "</p>");
            out.println("      <p><strong>Start Date:</strong> " + escapeHtml(formatDate(workspace.getStartDate()))
                    + "</p>");
            out.println(
                    "      <p><strong>End Date:</strong> " + escapeHtml(formatDate(workspace.getEndDate())) + "</p>");
            out.println("      <p><strong>Status:</strong> "
                    + escapeHtml(workspace.getStatus() == null ? "" : workspace.getStatus().name()) + "</p>");
            out.println("      <p><strong>Created By User ID:</strong> " + escapeHtml(
                    workspace.getCreatedByUserId() == null ? "" : String.valueOf(workspace.getCreatedByUserId()))
                    + "</p>");
            out.println("      <p><strong>Created At:</strong> " + escapeHtml(formatCreatedAt(workspace)) + "</p>");
            out.println("    </section>");

            out.println(
                    "    <p><a href=\"" + contextPath + "/admin/workspaces?workspaceId=" + workspace.getWorkspaceId()
                            + "&mode=edit\">Edit Workspace</a></p>");
            out.println("    <p><a href=\"" + contextPath + "/admin/topics?topicId=" + workspace.getTopicId()
                    + "\">Back to Topic</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderEditForm(HttpServletResponse response, String contextPath, IgTopic topic,
            ConnectWorkspace workspace,
            String errorMessage, boolean createNew) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>" + (createNew ? "Create Workspace" : "Edit Workspace") + " - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>" + (createNew ? "Create Workspace" : "Edit Workspace") + "</h1>");
            out.println("    <p><strong>Topic:</strong> " + escapeHtml(orEmpty(topic.getTopicName())) + "</p>");

            if (errorMessage != null && !errorMessage.isBlank()) {
                out.println("    <p><strong>Could not save:</strong> " + escapeHtml(errorMessage) + "</p>");
            }

            out.println(
                    "    <form class=\"login-form\" action=\"" + contextPath + "/admin/workspaces\" method=\"post\">");
            if (!createNew && workspace.getWorkspaceId() != null) {
                out.println("      <input type=\"hidden\" name=\"workspaceId\" value=\"" + workspace.getWorkspaceId()
                        + "\" />");
            }
            out.println("      <input type=\"hidden\" name=\"topicId\" value=\"" + workspace.getTopicId() + "\" />");

            out.println("      <label for=\"workspaceName\">Workspace Name (required)</label>");
            out.println("      <input id=\"workspaceName\" name=\"workspaceName\" type=\"text\" required value=\""
                    + escapeHtml(orEmpty(workspace.getWorkspaceName())) + "\" />");

            out.println("      <label for=\"description\">Description</label>");
            out.println("      <textarea id=\"description\" name=\"description\" rows=\"5\">"
                    + escapeHtml(orEmpty(workspace.getDescription())) + "</textarea>");

            out.println("      <label for=\"startDate\">Start Date</label>");
            out.println("      <input id=\"startDate\" name=\"startDate\" type=\"date\" value=\""
                    + escapeHtml(formatDateInput(workspace.getStartDate())) + "\" />");

            out.println("      <label for=\"endDate\">End Date</label>");
            out.println("      <input id=\"endDate\" name=\"endDate\" type=\"date\" value=\""
                    + escapeHtml(formatDateInput(workspace.getEndDate())) + "\" />");

            out.println("      <label for=\"status\">Status (required)</label>");
            out.println("      <select id=\"status\" name=\"status\" required>");
            out.println("        <option value=\"ACTIVE\""
                    + selectedStatus(workspace, ConnectWorkspace.WorkspaceStatus.ACTIVE)
                    + ">ACTIVE</option>");
            out.println("        <option value=\"CLOSED\""
                    + selectedStatus(workspace, ConnectWorkspace.WorkspaceStatus.CLOSED)
                    + ">CLOSED</option>");
            out.println("        <option value=\"ARCHIVED\""
                    + selectedStatus(workspace, ConnectWorkspace.WorkspaceStatus.ARCHIVED) + ">ARCHIVED</option>");
            out.println("      </select>");

            out.println("      <button type=\"submit\">Save</button>");
            out.println("    </form>");
            out.println("    <p><a href=\"" + contextPath + "/admin/topics?topicId=" + workspace.getTopicId()
                    + "\">Back to Topic</a></p>");
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
            out.println("  <title>Workspace Error - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Workspace Request Error</h1>");
            out.println("    <p>" + escapeHtml(message) + "</p>");
            out.println("    <p><a href=\"" + contextPath + "/admin/topics\">Back to Topics</a></p>");
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
            out.println("    <p>You must be an InteropHub admin to access workspace settings.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void validateDateOrder(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after start date.");
        }
    }

    private String selectedStatus(ConnectWorkspace workspace, ConnectWorkspace.WorkspaceStatus status) {
        return workspace.getStatus() == status ? " selected" : "";
    }

    private LocalDate parseDate(String value, String label) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException(label + " must use yyyy-MM-dd format.");
        }
    }

    private LocalDate safeParseDate(String value) {
        try {
            return parseDate(value, "Date");
        } catch (Exception ex) {
            return null;
        }
    }

    private ConnectWorkspace.WorkspaceStatus parseStatus(String value) {
        try {
            return ConnectWorkspace.WorkspaceStatus.valueOf(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Status must be ACTIVE, CLOSED, or ARCHIVED.");
        }
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String required(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatDate(LocalDate value) {
        if (value == null) {
            return "";
        }
        return DATE_FORMATTER.format(value);
    }

    private String formatDateInput(LocalDate value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    private String formatCreatedAt(ConnectWorkspace workspace) {
        if (workspace.getCreatedAt() == null) {
            return "";
        }
        return CREATED_AT_FORMATTER.format(workspace.getCreatedAt());
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
