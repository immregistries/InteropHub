package org.airahub.interophub.servlet;

import java.io.IOException;
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

/**
 * Admin page to create a new campaign and one-time import curated topics from a
 * selected source topic.
 * Route: /admin/es/campaigns/create
 */
public class AdminEsCampaignCreateServlet extends HttpServlet {

    private static final String ACTIVE_HREF = "/admin/es/campaigns";

    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsTopicDao topicDao;
    private final EsTopicCurationDao topicCurationDao;

    public AdminEsCampaignCreateServlet() {
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.topicDao = new EsTopicDao();
        this.topicCurationDao = new EsTopicCurationDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        renderForm(request, response, null, null, null, null, topicDao.findAllActive());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String campaignCode = trimToNull(request.getParameter("newCampaignCode"));
        String campaignName = trimToNull(request.getParameter("newCampaignName"));
        Long sourceTopicId = parseId(trimToNull(request.getParameter("sourceTopicId")));
        List<EsTopic> sourceTopics = topicDao.findAllActive();

        if (campaignCode == null) {
            renderForm(request, response, "New Campaign Code is required.", campaignCode, campaignName,
                    sourceTopicId, sourceTopics);
            return;
        }
        if (campaignName == null) {
            renderForm(request, response, "New Campaign Name is required.", campaignCode, campaignName,
                    sourceTopicId, sourceTopics);
            return;
        }
        if (sourceTopicId == null) {
            renderForm(request, response, "Select Project is required.", campaignCode, campaignName,
                    null, sourceTopics);
            return;
        }

        if (campaignDao.findByCampaignCode(campaignCode).isPresent()) {
            renderForm(request, response,
                    "A campaign with that code already exists.", campaignCode, campaignName,
                    sourceTopicId, sourceTopics);
            return;
        }

        Optional<EsTopic> sourceTopicOpt = topicDao.findById(sourceTopicId);
        if (sourceTopicOpt.isEmpty() || sourceTopicOpt.get().getStatus() != EsTopic.EsTopicStatus.ACTIVE) {
            renderForm(request, response,
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

        renderResult(request, response, campaign, sourceTopicOpt.get(), curatedEntries.size(), topicsImported,
                topicsUpdated, skippedSourceTopic, duplicateCuratedTopicRows);
    }

    private void renderForm(HttpServletRequest request, HttpServletResponse response,
            String errorMessage, String campaignCode, String campaignName,
            Long selectedSourceTopicId, List<EsTopic> sourceTopics) throws IOException {
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "Create Campaign - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Create New Campaign</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Create a new campaign and do a one-time import of curated topics from a selected project.</p>");

                    if (errorMessage != null) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--danger\"><p><strong>Error:</strong> "
                                        + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" method=\"post\" action=\""
                            + contextPath + "/admin/es/campaigns/create\">");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"newCampaignCode\">New Campaign Code</label>");
                    out.println("                <input class=\"aira-input\" id=\"newCampaignCode\" name=\"newCampaignCode\" type=\"text\""
                            + " value=\"" + escapeHtml(orEmpty(campaignCode)) + "\" required />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"newCampaignName\">New Campaign Name</label>");
                    out.println("                <input class=\"aira-input\" id=\"newCampaignName\" name=\"newCampaignName\" type=\"text\""
                            + " value=\"" + escapeHtml(orEmpty(campaignName)) + "\" required />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"sourceTopicId\">Select Project</label>");
                    out.println("                <select class=\"aira-select\" id=\"sourceTopicId\" name=\"sourceTopicId\" required>");
                    out.println("                  <option value=\"\">(select a project)</option>");
                    for (EsTopic topic : sourceTopics) {
                        boolean selected = selectedSourceTopicId != null
                                && selectedSourceTopicId.equals(topic.getEsTopicId());
                        out.println("                  <option value=\"" + topic.getEsTopicId() + "\""
                                + (selected ? " selected" : "") + ">"
                                + escapeHtml(topic.getTopicCode() + " — " + topic.getTopicName()) + "</option>");
                    }
                    out.println("                </select>");
                    out.println("              </div>");

                    out.println("            <p class=\"aira-meta\">"
                            + "This is a one-time import. Curated child topics are copied into <code>es_campaign_topic</code>"
                            + " with <code>table_no=1</code> and <code>topic_set_no=1</code>."
                            + " The selected project itself is not imported."
                            + "</p>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Create Campaign</button>");
                    out.println("                <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/campaigns\">Cancel</a>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("          </section>");
                });
    }

    private void renderResult(HttpServletRequest request, HttpServletResponse response,
            EsCampaign campaign, EsTopic sourceTopic, int curatedRowsFound,
            int topicsImported, int topicsUpdated,
            int skippedSourceTopic, int duplicateCuratedTopicRows) throws IOException {
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "Campaign Created - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Campaign Created</h2>");
                    out.println("            <div class=\"aira-alert aira-alert--success\"><p><strong>"
                            + escapeHtml(campaign.getCampaignCode()) + "</strong> &mdash; "
                            + escapeHtml(campaign.getCampaignName()) + "</p></div>");
                    out.println("            <p>Source project: <strong>" + escapeHtml(sourceTopic.getTopicCode())
                            + "</strong> &mdash; "
                            + escapeHtml(sourceTopic.getTopicName()) + "</p>");
                    out.println("            <p>Curation rows found: <strong>" + curatedRowsFound + "</strong></p>");
                    out.println("            <p>Campaign topics imported: <strong>" + topicsImported + "</strong></p>");
                    if (topicsUpdated > 0) {
                        out.println("            <p>Campaign topics updated: <strong>" + topicsUpdated + "</strong></p>");
                    }
                    if (skippedSourceTopic > 0) {
                        out.println(
                                "            <p>Skipped source-topic rows: <strong>" + skippedSourceTopic + "</strong></p>");
                    }
                    if (duplicateCuratedTopicRows > 0) {
                        out.println(
                                "            <p>Duplicate curated-topic rows merged: <strong>" + duplicateCuratedTopicRows
                                        + "</strong></p>");
                    }
                    if (topicsImported == 0 && topicsUpdated == 0) {
                        out.println(
                                "            <p class=\"aira-meta\">No curated child topics were imported. The campaign was created successfully.</p>");
                    }

                    String detailUrl = contextPath + "/admin/es/campaigns/detail?campaignCode="
                            + URLEncoder.encode(campaign.getCampaignCode(), StandardCharsets.UTF_8);
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + detailUrl
                            + "\">View Campaign Details</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/campaigns\">Back to Campaigns</a>");
                    out.println("            </div>");
                    out.println("          </section>");
                });
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
