package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
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
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsTopicReviewService;

public class AdminEsTopicReviewResultsServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsTopicReviewService reviewService;
    private final EsCommentDao commentDao;

    public AdminEsTopicReviewResultsServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
        this.reviewService = new EsTopicReviewService();
        this.commentDao = new EsCommentDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
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

        Optional<EsCampaign> campaign = findCampaignExact(campaignCode);
        if (campaign.isEmpty()) {
            renderCampaignNotFound(response, contextPath, campaignCode);
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

        renderPage(response, contextPath, campaign.get(), responders, summaryRows, commentCountByTopicId);
    }

    private void renderPage(HttpServletResponse response, String contextPath, EsCampaign campaign,
            List<EsTopicReviewDao.ResponderRow> responders,
            List<EsTopicReviewDao.TopicSummaryRow> summaryRows,
            Map<Long, Long> commentCountByTopicId) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        DecimalFormat scoreFormat = new DecimalFormat("0.00");

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "ES Topic Review Results - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>ES Topic Review Results</h2>");
                panelOut.println(
                        "        <p><strong>Campaign:</strong> " + escapeHtml(orEmpty(campaign.getCampaignName()))
                                + " (" + escapeHtml(orEmpty(campaign.getCampaignCode())) + ")</p>");
                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/es/review/"
                                + escapeHtml(orEmpty(campaign.getCampaignCode()))
                                + "\">Open Review Instrument</a></p>");

                panelOut.println("        <h2>Summary</h2>");
                panelOut.println("        <table class=\"admin-table\">");
                panelOut.println("          <tbody>");
                panelOut.println("            <tr><th>Responders</th><td>" + responders.size() + "</td></tr>");
                panelOut.println("            <tr><th>Topics Considered</th><td>" + summaryRows.size() + "</td></tr>");
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");

                panelOut.println("        <h3>Who Has Responded</h3>");
                if (responders.isEmpty()) {
                    panelOut.println("        <p>No responses yet.</p>");
                } else {
                    String responderText = responders.stream()
                            .map(this::responderLabel)
                            .collect(Collectors.joining(", "));
                    panelOut.println("        <p>" + escapeHtml(responderText) + "</p>");
                }

                panelOut.println("        <h2>Ranked Topics</h2>");
                panelOut.println("        <table class=\"admin-table\">");
                panelOut.println("          <thead>");
                panelOut.println("            <tr>");
                panelOut.println("              <th>Rank</th>");
                panelOut.println("              <th>Topic</th>");
                panelOut.println("              <th>Average Score</th>");
                panelOut.println("              <th>Reviews</th>");
                panelOut.println("              <th>3+</th>");
                panelOut.println("              <th>4+</th>");
                panelOut.println("              <th>Comments</th>");
                panelOut.println("            </tr>");
                panelOut.println("          </thead>");
                panelOut.println("          <tbody>");
                if (summaryRows.isEmpty()) {
                    panelOut.println("            <tr><td colspan=\"7\">No topics available.</td></tr>");
                } else {
                    int rank = 1;
                    for (EsTopicReviewDao.TopicSummaryRow row : summaryRows) {
                        String avgText = row.getAverageScore() == null ? "--"
                                : scoreFormat.format(row.getAverageScore());
                        long commentCount = commentCountByTopicId.getOrDefault(row.getEsTopicId(), 0L);
                        panelOut.println("            <tr>");
                        panelOut.println("              <td>" + rank + "</td>");
                        panelOut.println("              <td>" + escapeHtml(orEmpty(row.getTopicName())) + "</td>");
                        panelOut.println("              <td>" + avgText + "</td>");
                        panelOut.println("              <td>" + row.getReviewCount() + "</td>");
                        panelOut.println("              <td>" + row.getCountScore3Plus() + "</td>");
                        panelOut.println("              <td>" + row.getCountScore4Plus() + "</td>");
                        panelOut.println("              <td>" + commentCount + "</td>");
                        panelOut.println("            </tr>");
                        rank++;
                    }
                }
                panelOut.println("          </tbody>");
                panelOut.println("        </table>");

                panelOut.println("        <p style=\"margin-top:1.5rem\"><a href=\"" + contextPath
                        + "/admin/es/campaigns\">Back to Campaigns</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private String responderLabel(EsTopicReviewDao.ResponderRow responder) {
        String display = trimToNull(responder.getDisplayName());
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

    private Optional<EsCampaign> findCampaignExact(String campaignCode) {
        return campaignDao.findByCampaignCode(campaignCode)
                .filter(campaign -> campaignCode.equals(campaign.getCampaignCode()));
    }

    private void renderCampaignNotFound(HttpServletResponse response, String contextPath, String campaignCode)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\"><head><meta charset=\"UTF-8\" />");
            out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("<title>Campaign Not Found - InteropHub</title>");
            out.println("<link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" /></head>");
            out.println("<body><main class=\"container\"><h1>Campaign Not Found</h1>");
            out.println(
                    "<p>No campaign found for code <strong>" + escapeHtml(orEmpty(campaignCode)) + "</strong>.</p>");
            out.println("<p><a href=\"" + contextPath + "/admin/es/campaigns\">Back to Campaigns</a></p>");
            out.println("</main></body></html>");
        }
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\"><head><meta charset=\"UTF-8\" />");
            out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("<title>Access Denied - InteropHub</title>");
            out.println("<link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" /></head>");
            out.println("<body><main class=\"container\"><h1>Access Denied</h1>");
            out.println("<p>You must be an InteropHub admin to access this page.</p>");
            out.println("<p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("</main></body></html>");
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
