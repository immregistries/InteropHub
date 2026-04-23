package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

/**
 * Admin edit page for a single ES campaign.
 * Route: /admin/es/campaigns/edit?campaignCode={code}
 */
public class AdminEsCampaignEditServlet extends HttpServlet {

    private static final DateTimeFormatter INPUT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;

    public AdminEsCampaignEditServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String campaignCode = trimToNull(request.getParameter("campaignCode"));
        if (campaignCode == null) {
            response.sendRedirect(request.getContextPath() + "/admin/es/campaigns");
            return;
        }

        Optional<EsCampaign> campaignOpt = campaignDao.findByCampaignCode(campaignCode);
        if (campaignOpt.isEmpty()) {
            renderCampaignNotFound(response, request.getContextPath(), campaignCode);
            return;
        }

        renderForm(response, request.getContextPath(), campaignOpt.get(), null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String campaignCode = trimToNull(request.getParameter("campaignCode"));
        if (campaignCode == null) {
            response.sendRedirect(contextPath + "/admin/es/campaigns");
            return;
        }

        Optional<EsCampaign> campaignOpt = campaignDao.findByCampaignCode(campaignCode);
        if (campaignOpt.isEmpty()) {
            renderCampaignNotFound(response, contextPath, campaignCode);
            return;
        }

        EsCampaign campaign = campaignOpt.get();

        String campaignName = trimToNull(request.getParameter("campaignName"));
        String description = trimToNull(request.getParameter("description"));
        String campaignType = trimToNull(request.getParameter("campaignType"));
        String statusRaw = trimToNull(request.getParameter("status"));
        String allowTopicCommentsRaw = trimToNull(request.getParameter("allowTopicComments"));
        String allowGeneralCommentsRaw = trimToNull(request.getParameter("allowGeneralComments"));
        String startAtRaw = trimToNull(request.getParameter("startAt"));
        String endAtRaw = trimToNull(request.getParameter("endAt"));

        if (campaignName == null) {
            renderForm(response, contextPath, campaign, "Campaign name is required.");
            return;
        }
        if (campaignType == null) {
            renderForm(response, contextPath, campaign, "Campaign type is required.");
            return;
        }

        EsCampaign.CampaignStatus status;
        try {
            status = EsCampaign.CampaignStatus.valueOf(statusRaw == null ? "" : statusRaw);
        } catch (IllegalArgumentException ex) {
            renderForm(response, contextPath, campaign, "Invalid status value.");
            return;
        }

        Boolean allowTopicComments = parseBooleanSelect(allowTopicCommentsRaw);
        if (allowTopicComments == null) {
            renderForm(response, contextPath, campaign, "Invalid Allow Topic Comments value.");
            return;
        }

        Boolean allowGeneralComments = parseBooleanSelect(allowGeneralCommentsRaw);
        if (allowGeneralComments == null) {
            renderForm(response, contextPath, campaign, "Invalid Allow General Comments value.");
            return;
        }

        LocalDateTime startAt;
        try {
            startAt = parseDateTimeInput(startAtRaw);
        } catch (DateTimeParseException ex) {
            renderForm(response, contextPath, campaign, "Start At must be a valid date/time.");
            return;
        }

        LocalDateTime endAt;
        try {
            endAt = parseDateTimeInput(endAtRaw);
        } catch (DateTimeParseException ex) {
            renderForm(response, contextPath, campaign, "End At must be a valid date/time.");
            return;
        }

        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            renderForm(response, contextPath, campaign, "End At must be after Start At.");
            return;
        }

        campaign.setCampaignName(campaignName);
        campaign.setDescription(description);
        campaign.setCampaignType(campaignType);
        campaign.setStatus(status);
        campaign.setAllowTopicComments(allowTopicComments);
        campaign.setAllowGeneralComments(allowGeneralComments);
        campaign.setStartAt(startAt);
        campaign.setEndAt(endAt);
        campaignDao.saveOrUpdate(campaign);

        response.sendRedirect(
                contextPath + "/admin/es/campaigns/detail?campaignCode=" + escapeUrlComponent(campaignCode)
                        + "&saved=1");
    }

    private void renderForm(HttpServletResponse response, String contextPath,
            EsCampaign campaign, String errorMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Edit Campaign - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Edit Campaign</h2>");

                if (errorMessage != null) {
                    panelOut.println(
                            "        <p class=\"error\"><strong>Error:</strong> " + escapeHtml(errorMessage)
                                    + "</p>");
                }

                panelOut.println("        <form class=\"login-form\" method=\"post\" action=\"" + contextPath
                        + "/admin/es/campaigns/edit\">");
                panelOut.println("          <input type=\"hidden\" name=\"campaignCode\" value=\""
                        + escapeHtml(campaign.getCampaignCode()) + "\" />");

                panelOut.println("          <label for=\"campaignCode\">Campaign Code</label>");
                panelOut.println("          <input id=\"campaignCode\" type=\"text\" value=\""
                        + escapeHtml(campaign.getCampaignCode()) + "\" disabled />");

                panelOut.println("          <label for=\"campaignName\">Campaign Name</label>");
                panelOut.println(
                        "          <input id=\"campaignName\" name=\"campaignName\" type=\"text\" required value=\""
                                + escapeHtml(orEmpty(campaign.getCampaignName())) + "\" />");

                panelOut.println("          <label for=\"description\">Description</label>");
                panelOut.println("          <textarea id=\"description\" name=\"description\" rows=\"4\">"
                        + escapeHtml(orEmpty(campaign.getDescription())) + "</textarea>");

                panelOut.println("          <label for=\"campaignType\">Campaign Type</label>");
                panelOut.println(
                        "          <input id=\"campaignType\" name=\"campaignType\" type=\"text\" required value=\""
                                + escapeHtml(orEmpty(campaign.getCampaignType())) + "\" />");

                panelOut.println("          <label for=\"status\">Status</label>");
                panelOut.println("          <select id=\"status\" name=\"status\" required>");
                for (EsCampaign.CampaignStatus status : EsCampaign.CampaignStatus.values()) {
                    boolean selected = status == campaign.getStatus();
                    panelOut.println("            <option value=\"" + status.name() + "\""
                            + (selected ? " selected" : "") + ">"
                            + status.name() + "</option>");
                }
                panelOut.println("          </select>");

                panelOut.println("          <label for=\"allowTopicComments\">Allow Topic Comments</label>");
                panelOut.println(
                        "          <select id=\"allowTopicComments\" name=\"allowTopicComments\" required>");
                panelOut.println("            <option value=\"true\""
                        + (Boolean.TRUE.equals(campaign.getAllowTopicComments()) ? " selected" : "")
                        + ">Yes</option>");
                panelOut.println("            <option value=\"false\""
                        + (Boolean.FALSE.equals(campaign.getAllowTopicComments()) ? " selected" : "")
                        + ">No</option>");
                panelOut.println("          </select>");

                panelOut.println("          <label for=\"allowGeneralComments\">Allow General Comments</label>");
                panelOut.println(
                        "          <select id=\"allowGeneralComments\" name=\"allowGeneralComments\" required>");
                panelOut.println("            <option value=\"true\""
                        + (Boolean.TRUE.equals(campaign.getAllowGeneralComments()) ? " selected" : "")
                        + ">Yes</option>");
                panelOut.println("            <option value=\"false\""
                        + (Boolean.FALSE.equals(campaign.getAllowGeneralComments()) ? " selected" : "")
                        + ">No</option>");
                panelOut.println("          </select>");

                panelOut.println("          <label for=\"startAt\">Start At</label>");
                panelOut.println(
                        "          <input id=\"startAt\" name=\"startAt\" type=\"datetime-local\" value=\""
                                + escapeHtml(formatDateTimeForInput(campaign.getStartAt())) + "\" />");

                panelOut.println("          <label for=\"endAt\">End At</label>");
                panelOut.println(
                        "          <input id=\"endAt\" name=\"endAt\" type=\"datetime-local\" value=\""
                                + escapeHtml(formatDateTimeForInput(campaign.getEndAt())) + "\" />");

                panelOut.println("          <div class=\"form-actions\">");
                panelOut.println("            <button type=\"submit\">Save Campaign</button>");
                panelOut.println("            <a class=\"button-link\" href=\"" + contextPath
                        + "/admin/es/campaigns/detail?campaignCode="
                        + escapeUrlComponent(campaign.getCampaignCode())
                        + "\">Cancel</a>");
                panelOut.println("          </div>");
                panelOut.println("        </form>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderCampaignNotFound(HttpServletResponse response, String contextPath, String campaignCode)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Campaign Not Found - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Campaign Not Found</h2>");
                panelOut.println(
                        "        <p>No campaign found with code: <strong>" + escapeHtml(campaignCode)
                                + "</strong></p>");
                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es/campaigns\">Back to Campaigns</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.println("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\" />");
                out.println("<title>Access Denied - InteropHub</title>");
                out.println(
                        "<link rel=\"stylesheet\" href=\"" + request.getContextPath() + "/css/main.css\" /></head>");
                out.println("<body><main class=\"container\"><h1>Access Denied</h1>");
                out.println("<p>You must be an InteropHub admin to access this page.</p>");
                out.println("<p><a href=\"" + request.getContextPath() + "/welcome\">Return to Welcome</a></p>");
                out.println("</main></body></html>");
            }
            return Optional.empty();
        }
        return authenticatedUser;
    }

    private LocalDateTime parseDateTimeInput(String value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.parse(value, INPUT_DATE_TIME_FORMAT);
    }

    private String formatDateTimeForInput(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return value.format(INPUT_DATE_TIME_FORMAT);
    }

    private Boolean parseBooleanSelect(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
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

    private String escapeUrlComponent(String value) {
        if (value == null) {
            return "";
        }
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
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
