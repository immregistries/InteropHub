package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
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
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.DandelionSyncService;

public class AdminDandelionSyncServlet extends HttpServlet {
    private final AuthFlowService authFlowService;
    private final DandelionSyncService syncService;
    private final DandelionSyncQueueDao queueDao;

    public AdminDandelionSyncServlet() {
        this.authFlowService = new AuthFlowService();
        this.syncService = new DandelionSyncService();
        this.queueDao = new DandelionSyncQueueDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!ensureAdminAccess(request, response)) {
            return;
        }
        renderPage(response, request.getContextPath(), loadConfig(), null, null, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!ensureAdminAccess(request, response)) {
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

        renderPage(response, request.getContextPath(), config, message, errorMessage, processResult);
    }

    private boolean ensureAdminAccess(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return false;
        }
        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            renderForbidden(response, request.getContextPath());
            return false;
        }
        return true;
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Access Denied - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Access Denied</h2>");
                panelOut.println("        <p>You must be an InteropHub admin to access Dandelion sync.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
                panelOut.println("      </section>");
            });
        }
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

    private void renderPage(HttpServletResponse response, String contextPath, DandelionSyncConfig config,
            String message, String errorMessage, DandelionSyncService.ProcessResult processResult) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        Map<DandelionSyncQueueItem.QueueStatus, Long> counts = queueDao.countByStatus();
        List<DandelionSyncQueueItem> failures = queueDao.findRecentFailures(20);

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Dandelion Sync - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Dandelion Daily Sync</h2>");
                panelOut.println("        <p>Configure API access and manage the outbound sync queue.</p>");

                if (message != null) {
                    panelOut.println("        <p><strong>" + escapeHtml(message) + "</strong></p>");
                }
                if (errorMessage != null) {
                    panelOut.println("        <p><strong>Error:</strong> " + escapeHtml(errorMessage) + "</p>");
                }
                if (processResult != null && processResult.getMessage() == null) {
                    panelOut.println(
                            "        <p>Fetched " + processResult.getTotalFetched() + " item(s) this run.</p>");
                }

                panelOut.println("        <form class=\"login-form\" action=\"" + contextPath
                        + "/admin/es/dandelion-sync\" method=\"post\">");
                panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"save-config\" />");
                panelOut.println("          <label><input type=\"checkbox\" name=\"syncEnabled\""
                        + checked(config.getSyncEnabled()) + " /> Enable sync</label>");
                panelOut.println("          <label for=\"apiEndpoint\">API Endpoint</label>");
                panelOut.println("          <input id=\"apiEndpoint\" name=\"apiEndpoint\" type=\"text\" value=\""
                        + escapeHtml(orEmpty(config.getApiEndpoint())) + "\" />");
                panelOut.println("          <label for=\"apiKey\">API Key</label>");
                panelOut.println("          <input id=\"apiKey\" name=\"apiKey\" type=\"text\" value=\""
                        + escapeHtml(orEmpty(config.getApiKey())) + "\" />");
                panelOut.println("          <button type=\"submit\">Save Settings</button>");
                panelOut.println("        </form>");

                panelOut.println("        <section>");
                panelOut.println("          <h3>Queue Status</h3>");
                panelOut.println("          <table class=\"data-table\">");
                panelOut.println("            <thead><tr><th>Status</th><th>Count</th></tr></thead>");
                panelOut.println("            <tbody>");
                for (DandelionSyncQueueItem.QueueStatus status : DandelionSyncQueueItem.QueueStatus.values()) {
                    panelOut.println("              <tr><td>" + escapeHtml(status.name()) + "</td><td>"
                            + counts.getOrDefault(status, 0L) + "</td></tr>");
                }
                panelOut.println("            </tbody>");
                panelOut.println("          </table>");
                panelOut.println("        </section>");

                panelOut.println("        <div style=\"display:flex;gap:0.75rem;flex-wrap:wrap;margin:1rem 0;\">");
                panelOut.println("          <form method=\"post\" action=\"" + contextPath
                        + "/admin/es/dandelion-sync\"><input type=\"hidden\" name=\"action\" value=\"process-now\" />"
                        + "<button type=\"submit\">Process Pending Now</button></form>");
                panelOut.println("          <form method=\"post\" action=\"" + contextPath
                        + "/admin/es/dandelion-sync\"><input type=\"hidden\" name=\"action\" value=\"requeue-failures\" />"
                        + "<button type=\"submit\">Requeue Failed (Safe Order)</button></form>");
                panelOut.println("          <form method=\"post\" action=\"" + contextPath
                        + "/admin/es/dandelion-sync\"><input type=\"hidden\" name=\"action\" value=\"full-sync\" />"
                        + "<button type=\"submit\">Queue Full Sync</button></form>");
                panelOut.println("        </div>");

                panelOut.println("        <section>");
                panelOut.println("          <h3>Recent Failures</h3>");
                panelOut.println("          <table class=\"data-table\">");
                panelOut.println(
                        "            <thead><tr><th>ID</th><th>Entity</th><th>Operation</th><th>Attempts</th><th>Error</th></tr></thead>");
                panelOut.println("            <tbody>");
                if (failures.isEmpty()) {
                    panelOut.println("              <tr><td colspan=\"5\">No failed sync items.</td></tr>");
                } else {
                    for (DandelionSyncQueueItem item : failures) {
                        panelOut.println("              <tr><td>" + item.getSyncQueueId() + "</td><td>"
                                + escapeHtml(item.getEntityType().name()) + "</td><td>"
                                + escapeHtml(item.getOperation().name()) + "</td><td>"
                                + item.getAttemptCount() + "</td><td>"
                                + escapeHtml(orEmpty(item.getLastError())) + "</td></tr>");
                    }
                }
                panelOut.println("            </tbody>");
                panelOut.println("          </table>");
                panelOut.println("        </section>");

                panelOut.println("      </section>");
            });
        }
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