package org.airahub.interophub.servlet;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.EsTopicImportService;
import org.airahub.interophub.service.EsTopicImportService.ImportResult;

/**
 * Temporary admin tool for one-time ES topic imports and campaign assignment.
 * Route: /admin/es-topic-import
 */
public class AdminEsTopicImportServlet extends HttpServlet {

    private static final String ACTIVE_HREF = "/admin/es-topic-import";

    private final EsCampaignDao campaignDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsTopicImportService importService;

    public AdminEsTopicImportServlet() {
        this.campaignDao = new EsCampaignDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.importService = new EsTopicImportService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }
        renderForm(request, response, null, campaignDao.findAllOrdered(),
                topicSpaceDao.findAllActiveOrdered());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        Long selectedCampaignId = parseId(trimToNull(request.getParameter("campaignId")));
        String newCampaignCode = trimToNull(request.getParameter("newCampaignCode"));
        String newCampaignName = trimToNull(request.getParameter("newCampaignName"));
        String topicSpaceCode = trimToNull(request.getParameter("topicSpaceCode"));
        int tablesPerSet = parsePositiveIntOrDefault(trimToNull(request.getParameter("tablesPerSet")), 1);
        String jsonLines = request.getParameter("jsonLines");

        if (jsonLines == null || jsonLines.isBlank()) {
            renderForm(request, response, "JSON input is required.", campaignDao.findAllOrdered(),
                    topicSpaceDao.findAllActiveOrdered());
            return;
        }
        if (topicSpaceCode == null) {
            renderForm(request, response, "Topic Space code is required.", campaignDao.findAllOrdered(),
                    topicSpaceDao.findAllActiveOrdered());
            return;
        }

        try {
            ImportResult result = importService.importLines(
                    jsonLines, selectedCampaignId, newCampaignCode, newCampaignName,
                    adminUser.get().getUserId(), tablesPerSet, topicSpaceCode);
            renderResult(request, response, result);
        } catch (IllegalArgumentException ex) {
            renderForm(request, response, ex.getMessage(), campaignDao.findAllOrdered(),
                    topicSpaceDao.findAllActiveOrdered());
        }
    }

    // ── Page renderers
    // ────────────────────────────────────────────────────────────────────────────

    private void renderForm(HttpServletRequest request, HttpServletResponse response,
            String errorMessage, List<EsCampaign> campaigns, List<EsTopicSpace> topicSpaces) throws IOException {
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "ES Topic Import - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">ES Topic Import</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Paste one JSON object per line. Topics are upserted; nothing is deleted.</p>");

                    if (errorMessage != null) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--danger\"><p><strong>Error:</strong> "
                                        + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/es-topic-import\" method=\"post\">");
                    out.println("              <h3 class=\"aira-subsection-title\">Campaign Assignment</h3>");
                    out.println(
                            "              <p class=\"aira-meta\">Select an existing campaign, or enter a new campaign code and name to create one. If both are provided, the new campaign wins.</p>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"campaignId\">Existing Campaign</label>");
                    out.println("                <select class=\"aira-select\" id=\"campaignId\" name=\"campaignId\">");
                    out.println("                  <option value=\"\">(none)</option>");
                    for (EsCampaign c : campaigns) {
                        out.println("                  <option value=\"" + c.getEsCampaignId() + "\">"
                                + escapeHtml(c.getCampaignCode() + " — " + c.getCampaignName())
                                + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"newCampaignCode\">New Campaign Code</label>");
                    out.println("                <input class=\"aira-input\" id=\"newCampaignCode\" name=\"newCampaignCode\" type=\"text\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"newCampaignName\">New Campaign Name</label>");
                    out.println("                <input class=\"aira-input\" id=\"newCampaignName\" name=\"newCampaignName\" type=\"text\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"tablesPerSet\">Tables per Set</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"tablesPerSet\" name=\"tablesPerSet\" type=\"number\" min=\"1\" value=\"1\" style=\"width:6em\" />");
                    out.println(
                            "                <p class=\"aira-field-help\">One <code>es_campaign_topic</code> row is created per table (1 through this number). Default 1.</p>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"topicSpaceCode\">Topic Space Code (required)</label>");
                    out.println("                <select class=\"aira-select\" id=\"topicSpaceCode\" name=\"topicSpaceCode\" required>");
                    out.println("                  <option value=\"\" selected disabled>Choose a Topic Space</option>");
                    for (EsTopicSpace topicSpace : topicSpaces) {
                        out.println("                  <option value=\"" + escapeHtml(topicSpace.getSpaceCode()) + "\">"
                                + escapeHtml(topicSpace.getSpaceName() + " (" + topicSpace.getSpaceCode() + ")")
                                + "</option>");
                    }
                    out.println("                </select>");
                    out.println(
                            "                <p class=\"aira-field-help\">Select the Topic Space that owns this batch. Import only updates topics already in that Topic Space and will not move topics between spaces.</p>");
                    out.println("              </div>");

                    out.println("              <h3 class=\"aira-subsection-title\">JSON Lines</h3>");
                    out.println(
                            "              <p class=\"aira-meta\">Required fields per line: <code>topicCode</code>, <code>topicName</code>. Optional: <code>description</code>, <code>neighborhood</code>, <code>priorityIis</code>, <code>priorityEhr</code>, <code>priorityCdc</code>, <code>stage</code>, <code>policyStatus</code>, <code>topicType</code>, <code>confluenceUrl</code>, <code>displayOrder</code>, <code>set</code>.</p>");
                    out.println(
                            "              <p class=\"aira-meta\"><code>neighborhood</code> should contain one active neighborhood name or a comma-separated list of active neighborhood names in the selected Topic Space. The import updates the canonical topic-to-neighborhood mapping.</p>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"jsonLines\">One JSON object per line</label>");
                    out.println(
                            "                <textarea class=\"aira-textarea\" id=\"jsonLines\" name=\"jsonLines\" rows=\"20\" style=\"width:100%;font-family:monospace\"></textarea>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Import</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println(
                            "            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                    + "/admin/es\">Back to Emerging Standards</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderResult(HttpServletRequest request, HttpServletResponse response,
            ImportResult result) throws IOException {
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "ES Topic Import Result - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Import Result</h2>");

                    if (result.getErrorMessage() != null) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--danger\"><p><strong>Stopped at line "
                                        + result.getErrorLine()
                                        + ":</strong> " + escapeHtml(result.getErrorMessage()) + "</p></div>");
                    } else {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--success\"><p>Import completed successfully.</p></div>");
                    }

                    out.println("            <p>Campaign: <strong>" + escapeHtml(orEmpty(result.getCampaignCode()))
                            + "</strong> &mdash; " + escapeHtml(orEmpty(result.getCampaignName())) + "</p>");
                    out.println("            <p>Lines processed: <strong>" + result.getLinesProcessed() + "</strong></p>");
                    out.println("            <p>Topics &mdash; inserted: <strong>" + result.getTopicsInserted()
                            + "</strong> | updated: <strong>" + result.getTopicsUpdated() + "</strong></p>");
                    out.println(
                            "            <p>Campaign topics &mdash; inserted: <strong>" + result.getCampaignTopicsInserted()
                                    + "</strong> | updated: <strong>" + result.getCampaignTopicsUpdated()
                                    + "</strong></p>");
                    if (result.getDuplicateTopicCodes() > 0) {
                        out.println("            <p>Duplicate topic codes in paste (last write wins): <strong>"
                                + result.getDuplicateTopicCodes() + "</strong></p>");
                    }

                    out.println("            <div class=\"aira-action-group\">");
                    if (result.getCampaignCode() != null && !result.getCampaignCode().isBlank()) {
                        String detailUrl = contextPath + "/admin/es/campaigns/detail?campaignCode="
                                + URLEncoder.encode(result.getCampaignCode(), StandardCharsets.UTF_8);
                        out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + detailUrl
                                + "\">View Campaign Details</a>");
                    }
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es-topic-import\">Import Another Batch</a>");
                    out.println("            </div>");
                    out.println(
                            "            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                    + "/admin/es\">Back to Emerging Standards</a></p>");
                    out.println("          </section>");
                });
    }

    // ── Utility helpers
    // ───────────────────────────────────────────────────────────────────────────

    private Long parseId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int parsePositiveIntOrDefault(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 1 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
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
