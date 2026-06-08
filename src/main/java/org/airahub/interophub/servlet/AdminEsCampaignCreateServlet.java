package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.dao.EsTopicCurationDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsCampaignTopic;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicCuration;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

/**
 * Admin page to create a new campaign and one-time import curated topics from a
 * selected source topic.
 * Route: /admin/es/campaigns/create
 */
public class AdminEsCampaignCreateServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsTopicDao topicDao;
    private final EsTopicCurationDao topicCurationDao;

    public AdminEsCampaignCreateServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.topicDao = new EsTopicDao();
        this.topicCurationDao = new EsTopicCurationDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        renderForm(response, request.getContextPath(), null, null, null, null, topicDao.findAllActive());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String campaignCode = trimToNull(request.getParameter("newCampaignCode"));
        String campaignName = trimToNull(request.getParameter("newCampaignName"));
        Long sourceTopicId = parseId(trimToNull(request.getParameter("sourceTopicId")));
        List<EsTopic> sourceTopics = topicDao.findAllActive();

        if (campaignCode == null) {
            renderForm(response, contextPath, "New Campaign Code is required.", campaignCode, campaignName,
                    sourceTopicId, sourceTopics);
            return;
        }
        if (campaignName == null) {
            renderForm(response, contextPath, "New Campaign Name is required.", campaignCode, campaignName,
                    sourceTopicId, sourceTopics);
            return;
        }
        if (sourceTopicId == null) {
            renderForm(response, contextPath, "Select Project is required.", campaignCode, campaignName,
                    null, sourceTopics);
            return;
        }

        if (campaignDao.findByCampaignCode(campaignCode).isPresent()) {
            renderForm(response, contextPath,
                    "A campaign with that code already exists.", campaignCode, campaignName,
                    sourceTopicId, sourceTopics);
            return;
        }

        Optional<EsTopic> sourceTopicOpt = topicDao.findById(sourceTopicId);
        if (sourceTopicOpt.isEmpty() || sourceTopicOpt.get().getStatus() != EsTopic.EsTopicStatus.ACTIVE) {
            renderForm(response, contextPath,
                    "Selected project was not found or is not active.", campaignCode, campaignName,
                    sourceTopicId, sourceTopics);
            return;
        }

        EsCampaign campaign = new EsCampaign();
        campaign.setCampaignCode(campaignCode);
        campaign.setCampaignName(campaignName);
        campaign.setCreatedByUserId(adminUser.get().getUserId());
        campaign = campaignDao.saveOrUpdate(campaign);

        List<EsTopicCuration> curatedEntries = topicCurationDao.findByCuratorTopicId(sourceTopicId);
        Map<Long, Integer> curatedTopicDisplayOrder = new LinkedHashMap<>();

        int skippedSourceTopic = 0;
        int duplicateCuratedTopicRows = 0;

        for (EsTopicCuration entry : curatedEntries) {
            Long curatedTopicId = entry.getCuratedTopicId();
            if (curatedTopicId == null) {
                continue;
            }
            if (curatedTopicId.equals(sourceTopicId)) {
                skippedSourceTopic++;
                continue;
            }

            Integer displayOrder = entry.getDisplayOrder() == null ? 0 : entry.getDisplayOrder();
            Integer existingDisplayOrder = curatedTopicDisplayOrder.get(curatedTopicId);
            if (existingDisplayOrder == null) {
                curatedTopicDisplayOrder.put(curatedTopicId, displayOrder);
            } else {
                duplicateCuratedTopicRows++;
                curatedTopicDisplayOrder.put(curatedTopicId, Math.min(existingDisplayOrder, displayOrder));
            }
        }

        int topicsImported = 0;
        int topicsUpdated = 0;

        for (Map.Entry<Long, Integer> curatedTopic : curatedTopicDisplayOrder.entrySet()) {
            Long curatedTopicId = curatedTopic.getKey();
            Integer displayOrder = curatedTopic.getValue();

            Optional<EsCampaignTopic> existingCampaignTopic = campaignTopicDao
                    .findByCampaignIdAndTopicIdAndTableNo(campaign.getEsCampaignId(), curatedTopicId, 1);

            EsCampaignTopic campaignTopic;
            boolean isNew = existingCampaignTopic.isEmpty();
            if (isNew) {
                campaignTopic = new EsCampaignTopic();
                campaignTopic.setEsCampaignId(campaign.getEsCampaignId());
                campaignTopic.setEsTopicId(curatedTopicId);
                campaignTopic.setTableNo(1);
            } else {
                campaignTopic = existingCampaignTopic.get();
            }

            campaignTopic.setTopicSetNo(1);
            campaignTopic.setDisplayOrder(displayOrder == null ? 0 : displayOrder);
            campaignTopicDao.saveOrUpdate(campaignTopic);

            if (isNew) {
                topicsImported++;
            } else {
                topicsUpdated++;
            }
        }

        renderResult(response, contextPath, campaign, sourceTopicOpt.get(), curatedEntries.size(), topicsImported,
                topicsUpdated, skippedSourceTopic, duplicateCuratedTopicRows);
    }

    private void renderForm(HttpServletResponse response, String contextPath,
            String errorMessage, String campaignCode, String campaignName,
            Long selectedSourceTopicId, List<EsTopic> sourceTopics) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Create Campaign - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Create New Campaign</h2>");
                panelOut.println(
                        "        <p>Create a new campaign and do a one-time import of curated topics from a selected project.</p>");

                if (errorMessage != null) {
                    panelOut.println("        <p class=\"error\"><strong>Error:</strong> " + escapeHtml(errorMessage)
                            + "</p>");
                }

                panelOut.println("        <form class=\"login-form\" method=\"post\" action=\""
                        + contextPath + "/admin/es/campaigns/create\">");

                panelOut.println("          <label for=\"newCampaignCode\">New Campaign Code</label>");
                panelOut.println("          <input id=\"newCampaignCode\" name=\"newCampaignCode\" type=\"text\""
                        + " value=\"" + escapeHtml(orEmpty(campaignCode)) + "\" required />");

                panelOut.println("          <label for=\"newCampaignName\">New Campaign Name</label>");
                panelOut.println("          <input id=\"newCampaignName\" name=\"newCampaignName\" type=\"text\""
                        + " value=\"" + escapeHtml(orEmpty(campaignName)) + "\" required />");

                panelOut.println("          <label for=\"sourceTopicId\">Select Project</label>");
                panelOut.println("          <select id=\"sourceTopicId\" name=\"sourceTopicId\" required>");
                panelOut.println("            <option value=\"\">(select a project)</option>");
                for (EsTopic topic : sourceTopics) {
                    boolean selected = selectedSourceTopicId != null
                            && selectedSourceTopicId.equals(topic.getEsTopicId());
                    panelOut.println("            <option value=\"" + topic.getEsTopicId() + "\""
                            + (selected ? " selected" : "") + ">"
                            + escapeHtml(topic.getTopicCode() + " \u2014 " + topic.getTopicName()) + "</option>");
                }
                panelOut.println("          </select>");

                panelOut.println("          <p style=\"margin-top:0;font-size:.85em;color:#555\">"
                        + "This is a one-time import. Curated child topics are copied into <code>es_campaign_topic</code>"
                        + " with <code>table_no=1</code> and <code>topic_set_no=1</code>."
                        + " The selected project itself is not imported."
                        + "</p>");

                panelOut.println("          <div class=\"form-actions\">");
                panelOut.println("            <button type=\"submit\">Create Campaign</button>");
                panelOut.println("            <a class=\"button-link\" href=\"" + contextPath
                        + "/admin/es/campaigns\">Cancel</a>");
                panelOut.println("          </div>");
                panelOut.println("        </form>");
                panelOut.println("      </section>");
            });
        }
    }

    private void renderResult(HttpServletResponse response, String contextPath,
            EsCampaign campaign, EsTopic sourceTopic, int curatedRowsFound,
            int topicsImported, int topicsUpdated,
            int skippedSourceTopic, int duplicateCuratedTopicRows) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Campaign Created - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Campaign Created</h2>");
                panelOut.println("        <p><strong>" + escapeHtml(campaign.getCampaignCode()) + "</strong> &mdash; "
                        + escapeHtml(campaign.getCampaignName()) + "</p>");
                panelOut.println("        <p>Source project: <strong>" + escapeHtml(sourceTopic.getTopicCode())
                        + "</strong> &mdash; "
                        + escapeHtml(sourceTopic.getTopicName()) + "</p>");
                panelOut.println("        <p>Curation rows found: <strong>" + curatedRowsFound + "</strong></p>");
                panelOut.println("        <p>Campaign topics imported: <strong>" + topicsImported + "</strong></p>");
                if (topicsUpdated > 0) {
                    panelOut.println("        <p>Campaign topics updated: <strong>" + topicsUpdated + "</strong></p>");
                }
                if (skippedSourceTopic > 0) {
                    panelOut.println(
                            "        <p>Skipped source-topic rows: <strong>" + skippedSourceTopic + "</strong></p>");
                }
                if (duplicateCuratedTopicRows > 0) {
                    panelOut.println(
                            "        <p>Duplicate curated-topic rows merged: <strong>" + duplicateCuratedTopicRows
                                    + "</strong></p>");
                }
                if (topicsImported == 0 && topicsUpdated == 0) {
                    panelOut.println(
                            "        <p>No curated child topics were imported. The campaign was created successfully.</p>");
                }

                String detailUrl = contextPath + "/admin/es/campaigns/detail?campaignCode="
                        + URLEncoder.encode(campaign.getCampaignCode(), StandardCharsets.UTF_8);
                panelOut.println("        <p><a href=\"" + detailUrl + "\">View Campaign Details</a></p>");
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
            renderForbidden(response, request.getContextPath());
            return Optional.empty();
        }
        return authenticatedUser;
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Access Denied - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Access Denied</h2>");
                panelOut.println("        <p>You must be an InteropHub admin to access this page.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

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