package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.DandelionSyncConfigDao;
import org.airahub.interophub.dao.DandelionSyncQueueDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.DandelionSyncConfig;
import org.airahub.interophub.model.DandelionSyncQueueItem;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.DandelionSyncService;

public class AdminDandelionSyncServlet extends HttpServlet {
    private static final String ACTIVE_HREF = "/admin/es/dandelion-sync";

    private final DandelionSyncService syncService;
    private final DandelionSyncConfigDao configDao;
    private final DandelionSyncQueueDao queueDao;
    private final EsTopicSpaceDao topicSpaceDao;

    public AdminDandelionSyncServlet() {
        this.syncService = new DandelionSyncService();
        this.configDao = new DandelionSyncConfigDao();
        this.queueDao = new DandelionSyncQueueDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        Long spaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        if (spaceId == null) {
            renderSpaceList(request, response, null);
            return;
        }

        EsTopicSpace topicSpace = topicSpaceDao.findById(spaceId).orElse(null);
        if (topicSpace == null) {
            renderSpaceList(request, response, "Topic Space was not found.");
            return;
        }

        renderSpacePage(request, response, topicSpace, loadConfig(spaceId), null, null, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        Long spaceId = parseId(trimToNull(request.getParameter("esTopicSpaceId")));
        if (spaceId == null) {
            renderSpaceList(request, response, "Invalid Topic Space identifier.");
            return;
        }
        EsTopicSpace topicSpace = topicSpaceDao.findById(spaceId).orElse(null);
        if (topicSpace == null) {
            renderSpaceList(request, response, "Topic Space was not found.");
            return;
        }

        String action = trimToNull(request.getParameter("action"));
        DandelionSyncConfig config = loadConfig(spaceId);
        String message = null;
        String errorMessage = null;
        DandelionSyncService.ProcessResult processResult = null;

        try {
            if ("save-config".equals(action)) {
                populateConfig(config, spaceId, request);
                config = syncService.saveConfig(config);
                message = "Dandelion sync settings saved.";
            } else if ("full-sync".equals(action)) {
                int enqueued = syncService.enqueueFullSync(spaceId);
                message = "Queued full sync items: " + enqueued + ".";
            } else if ("process-now".equals(action)) {
                processResult = syncService.processPendingQueue(spaceId);
                message = summarizeProcess(processResult);
            } else if ("requeue-projects".equals(action)) {
                int requeuedProjects = syncService.requeueAllProjects(spaceId);
                message = "Requeued all project items for replay: " + requeuedProjects + ".";
            } else if ("requeue-failures".equals(action)) {
                DandelionSyncService.RequeueResult result = syncService.requeueFailuresInDependencyOrder(spaceId);
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

        renderSpacePage(request, response, topicSpace, config, message, errorMessage, processResult);
    }

    private DandelionSyncConfig loadConfig(Long spaceId) {
        return syncService.findActiveConfigForSpace(spaceId).orElseGet(() -> createDefaultConfig(spaceId));
    }

    private DandelionSyncConfig createDefaultConfig(Long spaceId) {
        DandelionSyncConfig config = new DandelionSyncConfig();
        config.setEsTopicSpaceId(spaceId);
        config.setActive(Boolean.TRUE);
        config.setSyncEnabled(Boolean.FALSE);
        config.setApiEndpoint("http://localhost:8080/api/v1/sync");
        config.setApiKey("");
        return config;
    }

    private void populateConfig(DandelionSyncConfig config, Long spaceId, HttpServletRequest request) {
        config.setEsTopicSpaceId(spaceId);
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

    private void renderSpaceList(HttpServletRequest request, HttpServletResponse response, String errorMessage)
            throws IOException {
        String contextPath = request.getContextPath();
        List<EsTopicSpace> spaces = topicSpaceDao.findAllOrdered();

        AdminShellRenderer.render(request, response, "Dandelion Sync - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Dandelion Sync</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Each Topic Space syncs to its own Dandelion workspace with its own API key. Choose a Topic Space to configure.</p>");
                    if (errorMessage != null && !errorMessage.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--danger\"><p>"
                                + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println(
                            "              <thead><tr><th>Topic Space</th><th>Sync Enabled</th><th>Pending</th><th>Failed</th></tr></thead>");
                    out.println("              <tbody>");
                    for (EsTopicSpace space : spaces) {
                        Optional<DandelionSyncConfig> config = configDao.findActiveForSpace(space.getEsTopicSpaceId());
                        Map<DandelionSyncQueueItem.QueueStatus, Long> counts =
                                queueDao.countByStatus(space.getEsTopicSpaceId());
                        boolean enabled = config.isPresent() && Boolean.TRUE.equals(config.get().getSyncEnabled());
                        out.println("                <tr>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es/dandelion-sync?esTopicSpaceId=" + space.getEsTopicSpaceId() + "\">"
                                + escapeHtml(orEmpty(space.getSpaceName())) + "</a></td>");
                        out.println("                  <td>" + (enabled
                                ? "<span class=\"aira-badge aira-badge--success\">Yes</span>"
                                : "<span class=\"aira-badge aira-badge--subtle\">No</span>") + "</td>");
                        out.println("                  <td>"
                                + counts.getOrDefault(DandelionSyncQueueItem.QueueStatus.PENDING, 0L) + "</td>");
                        out.println("                  <td>"
                                + counts.getOrDefault(DandelionSyncQueueItem.QueueStatus.FAILED, 0L) + "</td>");
                        out.println("                </tr>");
                    }
                    if (spaces.isEmpty()) {
                        out.println("                <tr><td colspan=\"4\">No Topic Spaces found.</td></tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");
                    out.println("          </section>");
                });
    }

    private void renderSpacePage(HttpServletRequest request, HttpServletResponse response, EsTopicSpace topicSpace,
            DandelionSyncConfig config, String message, String errorMessage,
            DandelionSyncService.ProcessResult processResult) throws IOException {
        String contextPath = request.getContextPath();
        Long spaceId = topicSpace.getEsTopicSpaceId();
        Map<DandelionSyncQueueItem.QueueStatus, Long> counts = queueDao.countByStatus(spaceId);
        List<DandelionSyncQueueItem> failures = queueDao.findRecentFailures(spaceId, 20);

        AdminShellRenderer.render(request, response,
                "Dandelion Sync - " + topicSpace.getSpaceName() + " - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Dandelion Daily Sync - "
                            + escapeHtml(orEmpty(topicSpace.getSpaceName())) + "</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Configure API access and manage the outbound sync queue for this Topic Space's Dandelion workspace.</p>");

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
                    out.println("              <input type=\"hidden\" name=\"esTopicSpaceId\" value=\"" + spaceId
                            + "\" />");
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
                            + "/admin/es/dandelion-sync\"><input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                            + spaceId + "\" /><input type=\"hidden\" name=\"action\" value=\"process-now\" />"
                            + "<button class=\"aira-button aira-button--primary\" type=\"submit\">Process Pending Now</button></form>");
                    out.println("              <form method=\"post\" action=\"" + contextPath
                            + "/admin/es/dandelion-sync\"><input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                            + spaceId + "\" /><input type=\"hidden\" name=\"action\" value=\"requeue-projects\" />"
                            + "<button class=\"aira-button aira-button--secondary\" type=\"submit\">Requeue All Projects</button></form>");
                    out.println("              <form method=\"post\" action=\"" + contextPath
                            + "/admin/es/dandelion-sync\"><input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                            + spaceId + "\" /><input type=\"hidden\" name=\"action\" value=\"requeue-failures\" />"
                            + "<button class=\"aira-button aira-button--secondary\" type=\"submit\">Requeue Failed (Safe Order)</button></form>");
                    out.println("              <form method=\"post\" action=\"" + contextPath
                            + "/admin/es/dandelion-sync\"><input type=\"hidden\" name=\"esTopicSpaceId\" value=\""
                            + spaceId + "\" /><input type=\"hidden\" name=\"action\" value=\"full-sync\" />"
                            + "<button class=\"aira-button aira-button--secondary\" type=\"submit\">Queue Full Sync</button></form>");
                    out.println("            </div>");
                    out.println(
                            "            <p class=\"aira-meta\">Project replay resets all project queue rows in this Topic Space to pending so project details and project tags can be resent.</p>");

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

                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/dandelion-sync\">Back to all Topic Spaces</a></p>");
                    out.println("          </section>");
                });
    }

    private Long parseId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
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
