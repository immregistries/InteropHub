package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignRegistrationDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminEsCampaignRegistrationDisplayServlet extends HttpServlet {

    private static final int DEFAULT_NAME_LIMIT = 200;
    private static final int MAX_NAME_LIMIT = 1000;
    private static final long AUTO_REFRESH_DURATION_MS = 20L * 60L * 1000L;
    private static final int AUTO_REFRESH_SECONDS = 20;

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsCampaignRegistrationDao registrationDao;

    public AdminEsCampaignRegistrationDisplayServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
        this.registrationDao = new EsCampaignRegistrationDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String campaignCode = trimToNull(request.getParameter("campaignCode"));
        boolean autoRefreshRequested = "1".equals(request.getParameter("auto"));
        int nameLimit = parsePositiveInt(request.getParameter("nameLimit"), DEFAULT_NAME_LIMIT, MAX_NAME_LIMIT);

        if (campaignCode == null) {
            renderCampaignPicker(response, contextPath, campaignDao.findAllOrdered());
            return;
        }

        Optional<EsCampaign> campaign = campaignDao.findByCampaignCode(campaignCode);
        if (campaign.isEmpty()) {
            renderCampaignNotFound(response, contextPath, campaignCode);
            return;
        }

        String startedAtRaw = trimToNull(request.getParameter("startedAt"));
        if (autoRefreshRequested && startedAtRaw == null) {
            String redirectUrl = contextPath + "/admin/es/registrations?campaignCode="
                    + URLEncoder.encode(campaignCode, StandardCharsets.UTF_8)
                    + "&auto=1&startedAt=" + System.currentTimeMillis()
                    + "&nameLimit=" + nameLimit;
            response.sendRedirect(redirectUrl);
            return;
        }

        long count = registrationDao.countByCampaignId(campaign.get().getEsCampaignId());
        List<String> firstNames = registrationDao.findRecentFirstNamesByCampaignId(campaign.get().getEsCampaignId(),
                nameLimit);

        long now = System.currentTimeMillis();
        long startedAt = parseLongOrDefault(startedAtRaw, now);
        long elapsed = Math.max(0L, now - startedAt);
        long remaining = Math.max(0L, AUTO_REFRESH_DURATION_MS - elapsed);
        boolean autoRefreshActive = autoRefreshRequested && remaining > 0L;

        renderDisplay(response, contextPath, campaign.get(), count, firstNames, autoRefreshActive, startedAt, remaining,
                nameLimit);
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

    private void renderCampaignPicker(HttpServletResponse response, String contextPath, List<EsCampaign> campaigns)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Registration Display - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Campaign Registration Display</h1>");
            out.println("    <p>Select a campaign to view registration count and names.</p>");
            out.println("    <form class=\"login-form\" method=\"get\" action=\"" + contextPath
                    + "/admin/es/registrations\">");
            out.println("      <label for=\"campaignCode\">Campaign</label>");
            out.println("      <select id=\"campaignCode\" name=\"campaignCode\" required>");
            out.println("        <option value=\"\">Choose one...</option>");
            for (EsCampaign campaign : campaigns) {
                out.println("        <option value=\"" + escapeHtml(orEmpty(campaign.getCampaignCode())) + "\">"
                        + escapeHtml(orEmpty(campaign.getCampaignCode())) + " - "
                        + escapeHtml(orEmpty(campaign.getCampaignName())) + "</option>");
            }
            out.println("      </select>");
            out.println(
                    "      <label><input type=\"checkbox\" name=\"auto\" value=\"1\" /> Auto-refresh for 20 minutes</label>");
            out.println("      <label for=\"nameLimit\">Name list limit</label>");
            out.println(
                    "      <input id=\"nameLimit\" name=\"nameLimit\" type=\"number\" min=\"1\" max=\"1000\" value=\""
                            + DEFAULT_NAME_LIMIT + "\" />");
            out.println("      <button type=\"submit\">Open Display</button>");
            out.println("    </form>");
            out.println("    <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderCampaignNotFound(HttpServletResponse response, String contextPath, String campaignCode)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Campaign Not Found - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Campaign Not Found</h1>");
            out.println("    <p>No campaign was found for this code.</p>");
            out.println("    <p><strong>Code:</strong> " + escapeHtml(orEmpty(campaignCode)) + "</p>");
            out.println(
                    "    <p><a href=\"" + contextPath + "/admin/es/registrations\">Choose another campaign</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderDisplay(HttpServletResponse response, String contextPath, EsCampaign campaign, long count,
            List<String> firstNames, boolean autoRefreshActive, long startedAt, long remainingMs, int nameLimit)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            if (autoRefreshActive) {
                out.println("  <meta http-equiv=\"refresh\" content=\"" + AUTO_REFRESH_SECONDS + "\" />");
            }
            out.println("  <title>Registration Display - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("  <style>");
            out.println("    .room-count { font-size: 5rem; font-weight: 700; margin: 0.25rem 0 1rem; }");
            out.println("    .room-names { font-size: 1.4rem; line-height: 1.4; }");
            out.println("    .room-wrap { max-width: 1400px; margin: 0 auto; padding: 1rem; }");
            out.println("  </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"room-wrap\">");
            out.println("    <h1>Campaign Registration Display</h1>");
            out.println("    <p><strong>Campaign:</strong> " + escapeHtml(orEmpty(campaign.getCampaignName())) + " ("
                    + escapeHtml(orEmpty(campaign.getCampaignCode())) + ")</p>");
            out.println("    <p>Raw registrations</p>");
            out.println("    <p class=\"room-count\">" + count + "</p>");

            String namesCsv = firstNames.stream()
                    .map(this::trimToNull)
                    .filter(name -> name != null)
                    .map(this::escapeHtml)
                    .collect(Collectors.joining(", "));

            out.println("    <h2>Recent First Names</h2>");
            if (namesCsv.isEmpty()) {
                out.println("    <p class=\"room-names\">No names yet.</p>");
            } else {
                out.println("    <p class=\"room-names\">" + namesCsv + "</p>");
            }

            if (autoRefreshActive) {
                out.println("    <p>Auto-refresh is active every " + AUTO_REFRESH_SECONDS
                        + " seconds. Remaining: " + (remainingMs / 1000L) + " seconds.</p>");
            } else {
                out.println("    <p>Auto-refresh is off or completed.</p>");
            }

            String codeEncoded = URLEncoder.encode(orEmpty(campaign.getCampaignCode()), StandardCharsets.UTF_8);
            out.println("    <p><a href=\"" + contextPath + "/admin/es/registrations?campaignCode=" + codeEncoded
                    + "&nameLimit=" + nameLimit + "\">Manual refresh</a></p>");
            out.println("    <p><a href=\"" + contextPath + "/admin/es/registrations?campaignCode=" + codeEncoded
                    + "&auto=1&startedAt=" + startedAt + "&nameLimit=" + nameLimit + "\">Start auto-refresh</a></p>");
            out.println(
                    "    <p><a href=\"" + contextPath + "/admin/es/registrations\">Choose another campaign</a></p>");
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
            out.println("    <p>You must be an InteropHub admin to access this page.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private int parsePositiveInt(String raw, int defaultValue, int maxValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed <= 0) {
                return defaultValue;
            }
            return Math.min(parsed, maxValue);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private long parseLongOrDefault(String raw, long defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
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
