package org.airahub.interophub.servlet;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsTopicReviewDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.EsTopicReviewService;

public class AdminEsTopicReviewResultsServlet extends HttpServlet {

    private final EsCampaignDao campaignDao;
    private final EsTopicReviewService reviewService;
    private final EsCommentDao commentDao;

    public AdminEsTopicReviewResultsServlet() {
        this.campaignDao = new EsCampaignDao();
        this.reviewService = new EsTopicReviewService();
        this.commentDao = new EsCommentDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
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

        Optional<EsCampaign> campaign = findCampaignExact(campaignCode);
        if (campaign.isEmpty()) {
            renderCampaignNotFound(request, response, campaignCode);
            return;
        }

        List<EsTopicReviewDao.ResponderRow> responders = reviewService.findResponders(campaign.get().getEsCampaignId());
        List<EsTopicReviewDao.TopicSummaryRow> summaryRows = reviewService
                .findTopicSummary(campaign.get().getEsCampaignId());
        Map<Long, Long> commentCountByTopicId = new HashMap<>();
        for (EsCommentDao.TopicCommentCountRow row : commentDao
                .findTopicCommentCountsByCampaignId(campaign.get().getEsCampaignId())) {
            if (row.getEsTopicId() != null) {
                commentCountByTopicId.put(row.getEsTopicId(), row.getCommentCount());
            }
        }

        renderPage(request, response, campaign.get(), responders, summaryRows, commentCountByTopicId);
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response, EsCampaign campaign,
            List<EsTopicReviewDao.ResponderRow> responders,
            List<EsTopicReviewDao.TopicSummaryRow> summaryRows,
            Map<Long, Long> commentCountByTopicId) throws IOException {
        String contextPath = request.getContextPath();
        DecimalFormat scoreFormat = new DecimalFormat("0.00");

        AdminShellRenderer.render(request, response, "ES Topic Review Results - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/review-results", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">ES Topic Review Results</h2>");
                    out.println(
                            "            <p class=\"aira-meta\"><strong>Campaign:</strong> "
                                    + escapeHtml(orEmpty(campaign.getCampaignName()))
                                    + " (" + escapeHtml(orEmpty(campaign.getCampaignCode())) + ")</p>");
                    out.println(
                            "            <p><a class=\"aira-inline-link\" href=\"" + contextPath + "/es/review/"
                                    + escapeHtml(orEmpty(campaign.getCampaignCode()))
                                    + "\">Open Review Instrument</a></p>");

                    out.println("            <h3 class=\"aira-subsection-title\">Summary</h3>");
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <tbody>");
                    out.println("                <tr><th>Responders</th><td>" + responders.size() + "</td></tr>");
                    out.println(
                            "                <tr><th>Topics Considered</th><td>" + summaryRows.size() + "</td></tr>");
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println("            <h3 class=\"aira-subsection-title\">Who Has Responded</h3>");
                    if (responders.isEmpty()) {
                        out.println("            <p class=\"aira-meta\">No responses yet.</p>");
                    } else {
                        String responderText = responders.stream()
                                .map(this::responderLabel)
                                .collect(Collectors.joining(", "));
                        out.println("            <p>" + escapeHtml(responderText) + "</p>");
                    }

                    out.println("            <h3 class=\"aira-subsection-title\">Ranked Topics</h3>");
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead>");
                    out.println("                <tr>");
                    out.println("                  <th>Rank</th>");
                    out.println("                  <th>Topic</th>");
                    out.println("                  <th>Average Score</th>");
                    out.println("                  <th>Reviews</th>");
                    out.println("                  <th>3+</th>");
                    out.println("                  <th>4+</th>");
                    out.println("                  <th>Comments</th>");
                    out.println("                </tr>");
                    out.println("              </thead>");
                    out.println("              <tbody>");
                    if (summaryRows.isEmpty()) {
                        out.println("                <tr><td colspan=\"7\">No topics available.</td></tr>");
                    } else {
                        int rank = 1;
                        for (EsTopicReviewDao.TopicSummaryRow row : summaryRows) {
                            String avgText = row.getAverageScore() == null ? "--"
                                    : scoreFormat.format(row.getAverageScore());
                            long commentCount = commentCountByTopicId.getOrDefault(row.getEsTopicId(), 0L);
                            String topicUrl = contextPath + "/es/topic/" + row.getEsTopicId();
                            out.println("                <tr>");
                            out.println("                  <td>" + rank + "</td>");
                            out.println("                  <td><a class=\"aira-inline-link\" href=\"" + topicUrl
                                    + "\">" + escapeHtml(orEmpty(row.getTopicName())) + "</a></td>");
                            out.println("                  <td>" + avgText + "</td>");
                            out.println("                  <td>" + row.getReviewCount() + "</td>");
                            out.println("                  <td>" + row.getCountScore3Plus() + "</td>");
                            out.println("                  <td>" + row.getCountScore4Plus() + "</td>");
                            out.println("                  <td>" + commentCount + "</td>");
                            out.println("                </tr>");
                            rank++;
                        }
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/campaigns\">Back to Campaigns</a></p>");
                    out.println("          </section>");
                });
    }

    private String responderLabel(EsTopicReviewDao.ResponderRow responder) {
        String display = trimToNull(responder.getFullName());
        if (display == null) {
            display = trimToNull(responder.getEmailNormalized());
        }
        if (display == null) {
            display = trimToNull(responder.getEmail());
        }
        if (display == null) {
            display = "User " + responder.getUserId();
        }
        return display + " (" + responder.getReviewCount() + ")";
    }

    private Optional<EsCampaign> findCampaignExact(String campaignCode) {
        return campaignDao.findByCampaignCode(campaignCode)
                .filter(campaign -> campaignCode.equals(campaign.getCampaignCode()));
    }

    private void renderCampaignNotFound(HttpServletRequest request, HttpServletResponse response, String campaignCode)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "Campaign Not Found - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/review-results", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Campaign Not Found</h2>");
                    out.println("            <p>No campaign found for code <strong>"
                            + escapeHtml(orEmpty(campaignCode)) + "</strong>.</p>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/campaigns\">Back to Campaigns</a></p>");
                    out.println("          </section>");
                });
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
