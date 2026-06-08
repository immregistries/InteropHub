package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsTopicReviewService;

public class EsCdcReviewPolicyStatusServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsTopicReviewService reviewService;

    public EsCdcReviewPolicyStatusServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
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
        if (!authFlowService.isCdcOrAdminUser(user.get())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeJsonError(response, "Access is restricted to authorized CDC users and administrators.");
            return;
        }

        String campaignCode = trimToNull(request.getParameter("campaignCode"));
        Long topicId = parseLong(request.getParameter("topicId"));
        String action = trimToNull(request.getParameter("policyStatusAction"));
        if (campaignCode == null || topicId == null || action == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJsonError(response, "campaignCode, topicId, and policyStatusAction are required.");
            return;
        }

        Optional<EsCampaign> campaign = findCampaignExact(campaignCode);
        if (campaign.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            writeJsonError(response, "Campaign was not found.");
            return;
        }

        try {
            EsTopic topic = reviewService.updateCdcPolicyStatus(campaign.get().getEsCampaignId(), topicId, action);
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"ok\":true,\"policyStatus\":\"");
                out.print(escapeJson(topic.getPolicyStatus()));
                out.print("\"}");
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
}