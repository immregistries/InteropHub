package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsTopicReviewService;

public class EsTopicReviewSaveServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsTopicDao topicDao;
    private final EsTopicReviewService reviewService;

    public EsTopicReviewSaveServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
        this.topicDao = new EsTopicDao();
        this.reviewService = new EsTopicReviewService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeJsonError(response, "Authentication is required.");
            return;
        }

        String campaignCode = trimToNull(request.getParameter("campaignCode"));
        Long topicId = parseLong(request.getParameter("topicId"));
        Integer score = parseInt(request.getParameter("score"));

        if (campaignCode == null || topicId == null || score == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJsonError(response, "campaignCode, topicId, and score are required.");
            return;
        }

        Optional<EsCampaign> campaign = findCampaignExact(campaignCode);
        if (campaign.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            writeJsonError(response, "Campaign was not found.");
            return;
        }

        try {
            EsTopicReviewService.SaveResult saveResult = reviewService.saveScore(
                    campaign.get().getEsCampaignId(), topicId, user.get().getUserId(), score);
            int totalTopics = topicDao.findAllActiveBrowseRowsOrdered().size();
            long reviewedCount = saveResult.reviewedCount();
            long leftCount = Math.max(0L, totalTopics - reviewedCount);

            try (PrintWriter out = response.getWriter()) {
                out.print("{\"ok\":true");
                out.print(",\"score\":" + saveResult.review().getCommunityValueScore());
                out.print(",\"reviewedCount\":" + reviewedCount);
                out.print(",\"leftCount\":" + leftCount);
                out.print("}");
            }
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJsonError(response, ex.getMessage());
        }
    }

    private Optional<EsCampaign> findCampaignExact(String campaignCode) {
        return campaignDao.findByCampaignCode(campaignCode)
                .filter(campaign -> campaignCode.equals(campaign.getCampaignCode()));
    }

    private void writeJsonError(HttpServletResponse response, String message) throws IOException {
        try (PrintWriter out = response.getWriter()) {
            out.print("{\"ok\":false,\"error\":\"");
            out.print(escapeJson(message));
            out.print("\"}");
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
