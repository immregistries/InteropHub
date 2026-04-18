package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsTopicImportService;
import org.airahub.interophub.service.EsTopicImportService.ImportResult;

/**
 * Temporary admin tool for one-time ES topic imports and campaign assignment.
 * Route: /admin/es-topic-import
 */
public class AdminEsTopicImportServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsTopicImportService importService;

    public AdminEsTopicImportServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
        this.importService = new EsTopicImportService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }
        renderForm(response, request.getContextPath(), null, campaignDao.findAllOrdered());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        Long selectedCampaignId = parseId(trimToNull(request.getParameter("campaignId")));
        String newCampaignCode = trimToNull(request.getParameter("newCampaignCode"));
        String newCampaignName = trimToNull(request.getParameter("newCampaignName"));
        int tablesPerSet = parsePositiveIntOrDefault(trimToNull(request.getParameter("tablesPerSet")), 1);
        String jsonLines = request.getParameter("jsonLines");

        if (jsonLines == null || jsonLines.isBlank()) {
            renderForm(response, contextPath, "JSON input is required.", campaignDao.findAllOrdered());
            return;
        }

        try {
            ImportResult result = importService.importLines(
                    jsonLines, selectedCampaignId, newCampaignCode, newCampaignName,
                    adminUser.get().getUserId(), tablesPerSet);
            renderResult(response, contextPath, result);
        } catch (IllegalArgumentException ex) {
            renderForm(response, contextPath, ex.getMessage(), campaignDao.findAllOrdered());
        }
    }

    // ── Page renderers
    // ────────────────────────────────────────────────────────────────────────────

    private void renderForm(HttpServletResponse response, String contextPath,
            String errorMessage, List<EsCampaign> campaigns) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>ES Topic Import - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>ES Topic Import</h1>");
            out.println("    <p>Paste one JSON object per line. Topics are upserted; nothing is deleted.</p>");

            if (errorMessage != null) {
                out.println("    <p class=\"error\"><strong>Error:</strong> " + escapeHtml(errorMessage) + "</p>");
            }

            out.println("    <form class=\"login-form\" action=\"" + contextPath
                    + "/admin/es-topic-import\" method=\"post\">");
            out.println("      <h2>Campaign Assignment</h2>");
            out.println("      <p>Select an existing campaign, or enter a new campaign code and name to create one."
                    + " If both are provided, the new campaign wins.</p>");

            out.println("      <label for=\"campaignId\">Existing Campaign</label>");
            out.println("      <select id=\"campaignId\" name=\"campaignId\">");
            out.println("        <option value=\"\">(none)</option>");
            for (EsCampaign c : campaigns) {
                out.println("        <option value=\"" + c.getEsCampaignId() + "\">"
                        + escapeHtml(c.getCampaignCode() + " \u2014 " + c.getCampaignName())
                        + "</option>");
            }
            out.println("      </select>");

            out.println("      <label for=\"newCampaignCode\">New Campaign Code</label>");
            out.println("      <input id=\"newCampaignCode\" name=\"newCampaignCode\" type=\"text\" />");
            out.println("      <label for=\"newCampaignName\">New Campaign Name</label>");
            out.println("      <input id=\"newCampaignName\" name=\"newCampaignName\" type=\"text\" />");

            out.println("      <label for=\"tablesPerSet\">Tables per Set</label>");
            out.println(
                    "      <input id=\"tablesPerSet\" name=\"tablesPerSet\" type=\"number\" min=\"1\" value=\"1\" style=\"width:6em\" />");
            out.println(
                    "      <p style=\"margin-top:0;font-size:.85em;color:#555\">One <code>es_campaign_topic</code> row is created per table (1 through this number). Default 1.</p>");

            out.println("      <h2>JSON Lines</h2>");
            out.println("      <p>Required fields per line: <code>topicCode</code>, <code>topicName</code>. "
                    + "Optional: <code>description</code>, <code>neighborhood</code>, "
                    + "<code>priorityIis</code>, <code>priorityEhr</code>, <code>priorityCdc</code>, "
                    + "<code>stage</code>, <code>policyStatus</code>, <code>topicType</code>, "
                    + "<code>displayOrder</code>, <code>set</code>.</p>");
            out.println("      <label for=\"jsonLines\">One JSON object per line</label>");
            out.println("      <textarea id=\"jsonLines\" name=\"jsonLines\" rows=\"20\""
                    + " style=\"width:100%;font-family:monospace\"></textarea>");

            out.println("      <button type=\"submit\">Import</button>");
            out.println("    </form>");
            out.println("    <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderResult(HttpServletResponse response, String contextPath,
            ImportResult result) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>ES Topic Import Result - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Import Result</h1>");

            if (result.getErrorMessage() != null) {
                out.println("    <p class=\"error\"><strong>Stopped at line " + result.getErrorLine()
                        + ":</strong> " + escapeHtml(result.getErrorMessage()) + "</p>");
            } else {
                out.println("    <p><strong>Import completed successfully.</strong></p>");
            }

            out.println("    <p>Campaign: <strong>" + escapeHtml(orEmpty(result.getCampaignCode()))
                    + "</strong> &mdash; " + escapeHtml(orEmpty(result.getCampaignName())) + "</p>");
            out.println("    <p>Lines processed: <strong>" + result.getLinesProcessed() + "</strong></p>");
            out.println("    <p>Topics &mdash; inserted: <strong>" + result.getTopicsInserted()
                    + "</strong> | updated: <strong>" + result.getTopicsUpdated() + "</strong></p>");
            out.println("    <p>Campaign topics &mdash; inserted: <strong>" + result.getCampaignTopicsInserted()
                    + "</strong> | updated: <strong>" + result.getCampaignTopicsUpdated() + "</strong></p>");
            if (result.getDuplicateTopicCodes() > 0) {
                out.println("    <p>Duplicate topic codes in paste (last write wins): <strong>"
                        + result.getDuplicateTopicCodes() + "</strong></p>");
            }

            if (result.getCampaignCode() != null && !result.getCampaignCode().isBlank()) {
                String detailUrl = contextPath + "/admin/es/campaigns/detail?campaignCode="
                        + URLEncoder.encode(result.getCampaignCode(), StandardCharsets.UTF_8);
                out.println("    <p><a href=\"" + detailUrl + "\">View Campaign Details</a></p>");
            }

            out.println("    <p><a href=\"" + contextPath + "/admin/es-topic-import\">Import Another Batch</a></p>");
            out.println("    <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    // ── Auth helpers
    // ──────────────────────────────────────────────────────────────────────────────

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
            out.println("    <p>You must be an InteropHub admin to access this page.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
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
