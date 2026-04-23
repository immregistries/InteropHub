package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsInterestService;

public class EsTopicFollowToggleServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicMeetingMemberDao topicMeetingMemberDao;
    private final EsInterestService esInterestService;

    public EsTopicFollowToggleServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
        this.esInterestService = new EsInterestService();
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

        Long topicId = parseLong(request.getParameter("topicId"));
        String action = trimToNull(request.getParameter("action"));
        if (topicId == null || action == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJsonError(response, "topicId and action are required.");
            return;
        }

        boolean follow;
        if ("follow".equalsIgnoreCase(action)) {
            follow = true;
        } else if ("unfollow".equalsIgnoreCase(action)) {
            follow = false;
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJsonError(response, "Unsupported action.");
            return;
        }

        User currentUser = user.get();
        String emailNormalized = currentUser.getEmailNormalized();
        Long userId = currentUser.getUserId();

        try {
            if (follow) {
                EsSubscription subscription = new EsSubscription();
                subscription.setEmail(currentUser.getEmail());
                subscription.setEmailNormalized(emailNormalized);
                subscription.setUserId(userId);
                subscription.setEsTopicId(topicId);
                subscription.setSubscriptionType(EsSubscription.SubscriptionType.TOPIC);
                subscription.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
                subscription.setSourceCampaignId(campaignDao.findMostRecentActive()
                        .map(EsCampaign::getEsCampaignId)
                        .orElse(null));
                esInterestService.subscribeOrUpdate(subscription);
            } else {
                Optional<EsSubscription> existing = subscriptionDao.findByUserOrEmailAndTopic(
                        userId, emailNormalized, topicId);
                if (existing.isPresent()) {
                    EsSubscription subscription = existing.get();
                    subscription.setStatus(EsSubscription.SubscriptionStatus.UNSUBSCRIBED);
                    subscription.setUnsubscribedAt(LocalDateTime.now());
                    subscriptionDao.saveOrUpdate(subscription);
                }

                List<EsTopicMeetingMember> members = topicMeetingMemberDao.findByTopicIdAndUserOrEmail(
                        topicId, userId, emailNormalized);
                for (EsTopicMeetingMember member : members) {
                    member.setMembershipStatus(EsTopicMeetingMember.MembershipStatus.REMOVED);
                    member.setApprovedAt(null);
                    member.setApprovedByUserId(null);
                    topicMeetingMemberDao.saveOrUpdate(member);
                }
            }

            try (PrintWriter out = response.getWriter()) {
                out.print("{\"ok\":true,\"followed\":" + follow + "}");
            }
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJsonError(response, ex.getMessage());
        }
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
