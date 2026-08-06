package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.DandelionSyncQueueDao;
import org.airahub.interophub.model.DandelionSyncConfig;
import org.airahub.interophub.model.DandelionSyncQueueItem;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.DandelionSyncService;

public class AdminDandelionSyncServlet extends HttpServlet {
    private final DandelionSyncService syncService;
    private final DandelionSyncQueueDao queueDao;

    public AdminDandelionSyncServlet() {
        this.syncService = new DandelionSyncService();
        this.queueDao = new DandelionSyncQueueDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }
        renderPage(request, response, loadConfig(), null, null, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String action = trimToNull(request.getParameter("action"));
        DandelionSyncConfig config = loadConfig();
        String message = null;
        String errorMessage = null;
        DandelionSyncService.ProcessResult processResult = null;

        try {
            if ("save-config".equals(action)) {
                populateConfig(config, request);
                config = syncService.saveConfig(config);
                message = "Dandelion sync settings saved.";
            } else if ("full-sync".equals(action)) {
                int enqueued = syncService.enqueueFullSync();
                message = "Queued full sync items: " + enqueued + ".";
            } else if ("process-now".equals(action)) {
                processResult = syncService.processPendingQueue();
                message = summarizeProcess(processResult);
            } else if ("requeue-projects".equals(action)) {
                int requeuedProjects = syncService.requeueAllProjects();
                message = "Requeued all project items for replay: " + requeuedProjects + ".";
            } else if ("requeue-failures".equals(action)) {
                DandelionSyncService.RequeueResult result = syncService.requeueFailuresInDependencyOrder();
                message = "Requeued failed items: "
                        + result.getTotalRequeued()
                        + " (projects=" + result.getTopicsRequeued()
                        + ", contacts=" + result.getContactsRequeued()
                        + ", assignments=" + result.getAssignmentsRequeued() + ").";
            }
        } catch (Exception ex) {
            errorMessage = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "Dandelion sync action failed."
                    : ex.getMessage();
        }

        renderPage(request, response, config, message, errorMessage, processResult);
    }

    private DandelionSyncConfig loadConfig() {
        return syncService.findActiveConfig().orElseGet(this::createDefaultConfig);
    }

    private DandelionSyncConfig createDefaultConfig() {
        DandelionSyncConfig config = new DandelionSyncConfig();
        config.setActive(Boolean.TRUE);
        config.setSyncEnabled(Boolean.FALSE);
        config.setApiEndpoint("http://localhost:8080/api/v1/sync");
        config.setApiKey("");
        return config;
    }

    private void populateConfig(DandelionSyncConfig config, HttpServletRequest request) {
        config.setActive(Boolean.TRUE);
        config.setSyncEnabled(request.getParameter("syncEnabled") != null);
        config.setApiEndpoint(required(request.getParameter("apiEndpoint"), "API endpoint"));
        config.setApiKey(required(request.getParameter("apiKey"), "API key"));
    }

    private String summarizeProcess(DandelionSyncService.ProcessResult result) {
        if (result == null) {
            return null;
        }
        if (result.getMessage() != null) {
            return result.getMessage();
        }
        return "Processed " + result.getTotalFetched() + " item(s): "
                + result.getSentCount() + " sent, "
                + result.getFailedCount() + " failed.";
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response, DandelionSyncConfig config,
            String message, String errorMessage, DandelionSyncService.ProcessResult processResult) throws IOException {
        String contextPath = request.getContextPath();
        Map<DandelionSyncQueueItem.QueueStatus, Long> counts = queueDao.countByStatus();
        List<DandelionSyncQueueItem> failures = queueDao.findRecentFailures(20);

        AdminShellRenderer.render(request, response, "Dandelion Sync - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/dandelion-sync", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Dandelion Daily Sync</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Configure API access and manage the outbound sync queue.</p>");

                    if (message != null) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }
                    if (errorMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--danger\"><p><strong>Error:</strong> "
                                + escapeHtml(errorMessage) + "</p></div>");
                    }
                    if (processResult != null && processResult.getMessage() == null) {
                        out.println("            <p class=\"aira-meta\">Fetched " + processResult.getTotalFetched()
                                + " item(s) this run.</p>");
                    }

                    out.println("            <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/es/dandelion-sync\" method=\"post\">");
                    out.println("              <input type=\"hidden\" name=\"action\" value=\"save-config\" />");
                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"syncEnabled\""
                            + checked(config.getSyncEnabled()) + " /> Enable sync</label>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"apiEndpoint\">API Endpoint</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"apiEndpoint\" name=\"apiEndpoint\" type=\"text\" value=\""
                                    + escapeHtml(orEmpty(config.getApiEndpoint())) + "\" />");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"apiKey\">API Key</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"apiKey\" name=\"apiKey\" type=\"text\" value=\""
                                    + escapeHtml(orEmpty(config.getApiKey())) + "\" />");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save Settings</button>");
                    out.println("              </div>");
                    out.println("            </form>");

                    out.println("            <h3 class=\"aira-subsection-title\">Queue Status</h3>");
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead><tr><th>Status</th><th>Count</th></tr></thead>");
                    out.println("              <tbody>");
                    for (DandelionSyncQueueItem.QueueStatus status : DandelionSyncQueueItem.QueueStatus.values()) {
                        out.println("                <tr><td>" + escapeHtml(status.name()) + "</td><td>"
                                + counts.getOrDefault(status, 0L) + "</td></tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <form method=\"post\" action=\"" + contextPath
                            + "/admin/es/dandelion-sync\"><input type=\"hidden\" name=\"action\" value=\"process-now\" />"
                            + "<button class=\"aira-button aira-button--primary\" type=\"submit\">Process Pending Now</button></form>");
                    out.println("              <form method=\"post\" action=\"" + contextPath
                            + "/admin/es/dandelion-sync\"><input type=\"hidden\" name=\"action\" value=\"requeue-projects\" />"
                            + "<button class=\"aira-button aira-button--secondary\" type=\"submit\">Requeue All Projects</button></form>");
                    out.println("              <form method=\"post\" action=\"" + contextPath
                            + "/admin/es/dandelion-sync\"><input type=\"hidden\" name=\"action\" value=\"requeue-failures\" />"
                            + "<button class=\"aira-button aira-button--secondary\" type=\"submit\">Requeue Failed (Safe Order)</button></form>");
                    out.println("              <form method=\"post\" action=\"" + contextPath
                            + "/admin/es/dandelion-sync\"><input type=\"hidden\" name=\"action\" value=\"full-sync\" />"
                            + "<button class=\"aira-button aira-button--secondary\" type=\"submit\">Queue Full Sync</button></form>");
                    out.println("            </div>");
                    out.println(
                            "            <p class=\"aira-meta\">Project replay resets all project queue rows to pending so project details and project tags can be resent.</p>");

                    out.println("            <h3 class=\"aira-subsection-title\">Recent Failures</h3>");
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println(
                            "              <thead><tr><th>ID</th><th>Entity</th><th>Operation</th><th>Attempts</th><th>Error</th></tr></thead>");
                    out.println("              <tbody>");
                    if (failures.isEmpty()) {
                        out.println("                <tr><td colspan=\"5\">No failed sync items.</td></tr>");
                    } else {
                        for (DandelionSyncQueueItem item : failures) {
                            out.println("                <tr><td>" + item.getSyncQueueId() + "</td><td>"
                                    + escapeHtml(item.getEntityType().name()) + "</td><td>"
                                    + escapeHtml(item.getOperation().name()) + "</td><td>"
                                    + item.getAttemptCount() + "</td><td>"
                                    + escapeHtml(orEmpty(item.getLastError())) + "</td></tr>");
                        }
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");
                    out.println("          </section>");
                });
    }

    private String required(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return trimmed;
    }

    private String checked(Boolean value) {
        return Boolean.TRUE.equals(value) ? " checked" : "";
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
