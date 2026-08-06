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
import org.immregistries.aira.web.AiraPage;

/**
 * Admin edit page for a single ES campaign.
 * Route: /admin/es/campaigns/edit?campaignCode={code}
 */
public class AdminEsCampaignEditServlet extends HttpServlet {

    private static final DateTimeFormatter INPUT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final String ACTIVE_HREF = "/admin/es/campaigns";

    private final EsCampaignDao campaignDao;

    public AdminEsCampaignEditServlet() {
        this.campaignDao = new EsCampaignDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
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
            renderCampaignNotFound(request, response, campaignCode);
            return;
        }

        renderForm(request, response, campaignOpt.get(), null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
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
            renderCampaignNotFound(request, response, campaignCode);
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
            renderForm(request, response, campaign, "Campaign name is required.");
            return;
        }
        if (campaignType == null) {
            renderForm(request, response, campaign, "Campaign type is required.");
            return;
        }

        EsCampaign.CampaignStatus status;
        try {
            status = EsCampaign.CampaignStatus.valueOf(statusRaw == null ? "" : statusRaw);
        } catch (IllegalArgumentException ex) {
            renderForm(request, response, campaign, "Invalid status value.");
            return;
        }

        Boolean allowTopicComments = parseBooleanSelect(allowTopicCommentsRaw);
        if (allowTopicComments == null) {
            renderForm(request, response, campaign, "Invalid Allow Topic Comments value.");
            return;
        }

        Boolean allowGeneralComments = parseBooleanSelect(allowGeneralCommentsRaw);
        if (allowGeneralComments == null) {
            renderForm(request, response, campaign, "Invalid Allow General Comments value.");
            return;
        }

        LocalDateTime startAt;
        try {
            startAt = parseDateTimeInput(startAtRaw);
        } catch (DateTimeParseException ex) {
            renderForm(request, response, campaign, "Start At must be a valid date/time.");
            return;
        }

        LocalDateTime endAt;
        try {
            endAt = parseDateTimeInput(endAtRaw);
        } catch (DateTimeParseException ex) {
            renderForm(request, response, campaign, "End At must be a valid date/time.");
            return;
        }

        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            renderForm(request, response, campaign, "End At must be after Start At.");
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

    private void renderForm(HttpServletRequest request, HttpServletResponse response,
            EsCampaign campaign, String errorMessage) throws IOException {
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "Edit Campaign - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Edit Campaign</h2>");

                    if (errorMessage != null) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--danger\"><p><strong>Error:</strong> "
                                        + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                            + "/admin/es/campaigns/edit\">");
                    out.println("              <input type=\"hidden\" name=\"campaignCode\" value=\""
                            + escapeHtml(campaign.getCampaignCode()) + "\" />");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"campaignCode\">Campaign Code</label>");
                    out.println("                <input class=\"aira-input\" id=\"campaignCode\" type=\"text\" value=\""
                            + escapeHtml(campaign.getCampaignCode()) + "\" disabled />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"campaignName\">Campaign Name</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"campaignName\" name=\"campaignName\" type=\"text\" required value=\""
                                    + escapeHtml(orEmpty(campaign.getCampaignName())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"description\">Description</label>");
                    out.println("                <textarea class=\"aira-textarea\" id=\"description\" name=\"description\" rows=\"4\">"
                            + escapeHtml(orEmpty(campaign.getDescription())) + "</textarea>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"campaignType\">Campaign Type</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"campaignType\" name=\"campaignType\" type=\"text\" required value=\""
                                    + escapeHtml(orEmpty(campaign.getCampaignType())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"status\">Status</label>");
                    out.println("                <select class=\"aira-select\" id=\"status\" name=\"status\" required>");
                    for (EsCampaign.CampaignStatus status : EsCampaign.CampaignStatus.values()) {
                        boolean selected = status == campaign.getStatus();
                        out.println("                  <option value=\"" + status.name() + "\""
                                + (selected ? " selected" : "") + ">"
                                + status.name() + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"allowTopicComments\">Allow Topic Comments</label>");
                    out.println(
                            "                <select class=\"aira-select\" id=\"allowTopicComments\" name=\"allowTopicComments\" required>");
                    out.println("                  <option value=\"true\""
                            + (Boolean.TRUE.equals(campaign.getAllowTopicComments()) ? " selected" : "")
                            + ">Yes</option>");
                    out.println("                  <option value=\"false\""
                            + (Boolean.FALSE.equals(campaign.getAllowTopicComments()) ? " selected" : "")
                            + ">No</option>");
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"allowGeneralComments\">Allow General Comments</label>");
                    out.println(
                            "                <select class=\"aira-select\" id=\"allowGeneralComments\" name=\"allowGeneralComments\" required>");
                    out.println("                  <option value=\"true\""
                            + (Boolean.TRUE.equals(campaign.getAllowGeneralComments()) ? " selected" : "")
                            + ">Yes</option>");
                    out.println("                  <option value=\"false\""
                            + (Boolean.FALSE.equals(campaign.getAllowGeneralComments()) ? " selected" : "")
                            + ">No</option>");
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"startAt\">Start At</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"startAt\" name=\"startAt\" type=\"datetime-local\" value=\""
                                    + escapeHtml(formatDateTimeForInput(campaign.getStartAt())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"endAt\">End At</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"endAt\" name=\"endAt\" type=\"datetime-local\" value=\""
                                    + escapeHtml(formatDateTimeForInput(campaign.getEndAt())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save Campaign</button>");
                    out.println("                <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/campaigns/detail?campaignCode="
                            + escapeUrlComponent(campaign.getCampaignCode())
                            + "\">Cancel</a>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("          </section>");
                });
    }

    private void renderCampaignNotFound(HttpServletRequest request, HttpServletResponse response, String campaignCode)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        String contextPath = request.getContextPath();
        AiraPage page = InteropAiraPageFactory.base(request, "Campaign Not Found - InteropHub").build();
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <div class=\"aira-page-header\">");
            out.println("        <div>");
            out.println("          <h1 class=\"aira-page-title\">Campaign Not Found</h1>");
            out.println("        </div>");
            out.println("      </div>");
            out.println(
                    "      <p>No campaign found with code: <strong>" + escapeHtml(campaignCode)
                            + "</strong></p>");
            out.println(
                    "      <p><a class=\"aira-inline-link\" href=\"" + contextPath + "/admin/es/campaigns\">Back to Campaigns</a></p>");
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
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
