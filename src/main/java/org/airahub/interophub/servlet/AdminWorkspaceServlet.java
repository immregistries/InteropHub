package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.airahub.interophub.dao.ConnectWorkspaceDao;
import org.airahub.interophub.dao.IgTopicDao;
import org.airahub.interophub.dao.WorkspaceStepDao;
import org.airahub.interophub.model.ConnectWorkspace;
import org.airahub.interophub.model.IgTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.model.WorkspaceStep;
import org.airahub.interophub.service.AuthFlowService;

public class AdminWorkspaceServlet extends HttpServlet {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter CREATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthFlowService authFlowService;
    private final ConnectWorkspaceDao connectWorkspaceDao;
    private final IgTopicDao igTopicDao;
    private final WorkspaceStepDao workspaceStepDao;

    public AdminWorkspaceServlet() {
        this.authFlowService = new AuthFlowService();
        this.connectWorkspaceDao = new ConnectWorkspaceDao();
        this.igTopicDao = new IgTopicDao();
        this.workspaceStepDao = new WorkspaceStepDao();
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
            renderEditForm(response, contextPath, topic, newWorkspace, List.of(), null, true);
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
            String message = request.getParameter("saved") != null ? "Workspace settings saved." : null;
            List<WorkspaceStep> steps = workspaceStepDao.findByWorkspaceOrdered(workspace.getWorkspaceId());
            renderEditForm(response, contextPath, topic, workspace, steps, message, false);
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
        String stepAction = trimToNull(request.getParameter("stepAction"));
        String[] stepIdValues = request.getParameterValues("stepId");
        String[] stepNameValues = request.getParameterValues("stepName");
        String[] stepAppliesToValues = request.getParameterValues("stepAppliesTo");
        String newStepName = trimToNull(request.getParameter("newStepName"));
        String newStepAppliesToRaw = trimToNull(request.getParameter("newStepAppliesTo"));

        try {
            workspace.setWorkspaceName(required(workspaceName, "Workspace name"));
            workspace.setDescription(description);
            workspace.setStartDate(parseDate(startDateRaw, "Start date"));
            workspace.setEndDate(parseDate(endDateRaw, "End date"));
            workspace.setStatus(parseStatus(required(statusRaw, "Status")));
            validateDateOrder(workspace.getStartDate(), workspace.getEndDate());

            workspace = connectWorkspaceDao.saveOrUpdate(workspace);
            processWorkspaceStepUpdates(workspace.getWorkspaceId(), stepIdValues, stepNameValues, stepAppliesToValues,
                    stepAction, newStepName, newStepAppliesToRaw);
            response.sendRedirect(contextPath + "/admin/workspaces?workspaceId=" + workspace.getWorkspaceId()
                    + "&mode=edit&saved=1");
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
            List<WorkspaceStep> steps = workspace.getWorkspaceId() == null
                    ? List.of()
                    : workspaceStepDao.findByWorkspaceOrdered(workspace.getWorkspaceId());
            renderEditForm(response, contextPath, topic, workspace, steps,
                    "Could not save: " + ex.getMessage(), createNew);
        }
    }

    private void processWorkspaceStepUpdates(Long workspaceId, String[] stepIdValues, String[] stepNameValues,
            String[] stepAppliesToValues, String stepAction, String newStepName, String newStepAppliesToRaw) {
        if (workspaceId == null) {
            return;
        }

        List<WorkspaceStep> existingSteps = workspaceStepDao.findByWorkspaceOrdered(workspaceId);
        Map<Long, WorkspaceStep> existingById = new HashMap<>();
        int maxSortOrder = -1;
        for (WorkspaceStep step : existingSteps) {
            existingById.put(step.getStepId(), step);
            if (step.getSortOrder() != null && step.getSortOrder() > maxSortOrder) {
                maxSortOrder = step.getSortOrder();
            }
        }

        if (stepIdValues != null) {
            for (int i = 0; i < stepIdValues.length; i++) {
                Long stepId = parseId(stepIdValues[i]);
                if (stepId == null) {
                    continue;
                }
                WorkspaceStep step = existingById.get(stepId);
                if (step == null) {
                    continue;
                }

                String nameAtIndex = trimToNull(paramAt(stepNameValues, i));
                String appliesToAtIndex = trimToNull(paramAt(stepAppliesToValues, i));
                step.setStepName(required(nameAtIndex, "Step name"));
                step.setAppliesTo(parseAppliesTo(required(appliesToAtIndex, "Applies to")));
                workspaceStepDao.saveOrUpdate(step);
            }
        }

        if ("add".equalsIgnoreCase(stepAction)) {
            WorkspaceStep newStep = new WorkspaceStep();
            newStep.setWorkspaceId(workspaceId);
            newStep.setStepName(required(newStepName, "New step name"));
            newStep.setAppliesTo(parseAppliesTo(required(newStepAppliesToRaw, "New step applies to")));
            newStep.setSortOrder(maxSortOrder + 1);
            workspaceStepDao.save(newStep);
            return;
        }

        if (stepAction != null && (stepAction.startsWith("move-up:") || stepAction.startsWith("move-down:"))) {
            List<WorkspaceStep> ordered = workspaceStepDao.findByWorkspaceOrdered(workspaceId);
            Long moveStepId = parseId(stepAction.substring(stepAction.indexOf(':') + 1));
            if (moveStepId == null) {
                return;
            }

            int index = -1;
            for (int i = 0; i < ordered.size(); i++) {
                if (moveStepId.equals(ordered.get(i).getStepId())) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                return;
            }

            if (stepAction.startsWith("move-up:") && index > 0) {
                swapStepOrder(ordered.get(index - 1), ordered.get(index));
            } else if (stepAction.startsWith("move-down:") && index < ordered.size() - 1) {
                swapStepOrder(ordered.get(index), ordered.get(index + 1));
            }
        }
    }

    private void swapStepOrder(WorkspaceStep first, WorkspaceStep second) {
        int firstOrder = first.getSortOrder() == null ? 0 : first.getSortOrder();
        int secondOrder = second.getSortOrder() == null ? 0 : second.getSortOrder();
        first.setSortOrder(secondOrder);
        second.setSortOrder(firstOrder);
        workspaceStepDao.saveOrUpdate(first);
        workspaceStepDao.saveOrUpdate(second);
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
            ConnectWorkspace workspace, List<WorkspaceStep> workspaceSteps,
            String message, boolean createNew) throws IOException {
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

            if (message != null && !message.isBlank()) {
                out.println("    <p><strong>" + escapeHtml(message) + "</strong></p>");
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

            if (!createNew && workspace.getWorkspaceId() != null) {
                out.println("      <h2>Workspace Steps</h2>");
                out.println("      <table class=\"data-table\">");
                out.println("        <thead>");
                out.println("          <tr>");
                out.println("            <th>Step Name</th>");
                out.println("            <th>Applies To</th>");
                out.println("            <th>Order</th>");
                out.println("          </tr>");
                out.println("        </thead>");
                out.println("        <tbody>");
                for (int i = 0; i < workspaceSteps.size(); i++) {
                    WorkspaceStep step = workspaceSteps.get(i);
                    boolean canMoveUp = i > 0;
                    boolean canMoveDown = i < workspaceSteps.size() - 1;

                    out.println("          <tr>");
                    out.println("            <td>");
                    out.println("              <input type=\"hidden\" name=\"stepId\" value=\"" + step.getStepId()
                            + "\" />");
                    out.println("              <input name=\"stepName\" type=\"text\" required value=\""
                            + escapeHtml(orEmpty(step.getStepName())) + "\" />");
                    out.println("            </td>");
                    out.println("            <td>");
                    out.println("              <select name=\"stepAppliesTo\" required>");
                    out.println(appliesToOption(step.getAppliesTo(), WorkspaceStep.AppliesTo.CLIENT_TO_SERVER));
                    out.println(appliesToOption(step.getAppliesTo(), WorkspaceStep.AppliesTo.CLIENT_ONLY));
                    out.println(appliesToOption(step.getAppliesTo(), WorkspaceStep.AppliesTo.SERVER_ONLY));
                    out.println(appliesToOption(step.getAppliesTo(), WorkspaceStep.AppliesTo.BOTH));
                    out.println("              </select>");
                    out.println("            </td>");
                    out.println("            <td>");
                    out.println("              <button type=\"submit\" name=\"stepAction\" value=\"move-up:"
                            + step.getStepId() + "\"" + (canMoveUp ? "" : " disabled") + ">↑</button>");
                    out.println("              <button type=\"submit\" name=\"stepAction\" value=\"move-down:"
                            + step.getStepId() + "\"" + (canMoveDown ? "" : " disabled") + ">↓</button>");
                    out.println("            </td>");
                    out.println("          </tr>");
                }
                if (workspaceSteps.isEmpty()) {
                    out.println("          <tr>");
                    out.println("            <td colspan=\"3\">No steps yet.</td>");
                    out.println("          </tr>");
                }

                out.println("          <tr>");
                out.println(
                        "            <td><input name=\"newStepName\" type=\"text\" placeholder=\"New step name\" /></td>");
                out.println("            <td>");
                out.println("              <select name=\"newStepAppliesTo\">");
                out.println(appliesToOption(WorkspaceStep.AppliesTo.CLIENT_TO_SERVER,
                        WorkspaceStep.AppliesTo.CLIENT_TO_SERVER));
                out.println(
                        appliesToOption(WorkspaceStep.AppliesTo.CLIENT_TO_SERVER, WorkspaceStep.AppliesTo.CLIENT_ONLY));
                out.println(
                        appliesToOption(WorkspaceStep.AppliesTo.CLIENT_TO_SERVER, WorkspaceStep.AppliesTo.SERVER_ONLY));
                out.println(appliesToOption(WorkspaceStep.AppliesTo.CLIENT_TO_SERVER, WorkspaceStep.AppliesTo.BOTH));
                out.println("              </select>");
                out.println("            </td>");
                out.println("            <td>");
                out.println(
                        "              <button type=\"submit\" name=\"stepAction\" value=\"add\">Add Step</button>");
                out.println("            </td>");
                out.println("          </tr>");
                out.println("        </tbody>");
                out.println("      </table>");
                out.println("      <p>Existing steps can be renamed and reordered. Steps cannot be deleted.</p>");
            } else {
                out.println("      <p>Save the workspace first, then add and manage workspace steps.</p>");
            }

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

    private WorkspaceStep.AppliesTo parseAppliesTo(String value) {
        try {
            return WorkspaceStep.AppliesTo.valueOf(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Applies to must be CLIENT_TO_SERVER, CLIENT_ONLY, SERVER_ONLY, or BOTH.");
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

    private String paramAt(String[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return null;
        }
        return values[index];
    }

    private String appliesToOption(WorkspaceStep.AppliesTo selected, WorkspaceStep.AppliesTo option) {
        String isSelected = selected == option ? " selected" : "";
        return "                <option value=\"" + option.name() + "\"" + isSelected + ">"
                + option.name() + "</option>";
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
