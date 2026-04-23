package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
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

public class EsTopicMeetingToggleServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsTopicMeetingMemberDao topicMeetingMemberDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsInterestService esInterestService;

    public EsTopicMeetingToggleServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
        this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
        this.subscriptionDao = new EsSubscriptionDao();
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
        Long meetingId = parseLong(request.getParameter("meetingId"));
        String action = trimToNull(request.getParameter("action"));
        if (topicId == null || meetingId == null || action == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJsonError(response, "topicId, meetingId, and action are required.");
            return;
        }

        boolean requestJoin;
        if ("request".equalsIgnoreCase(action)) {
            requestJoin = true;
        } else if ("unrequest".equalsIgnoreCase(action)) {
            requestJoin = false;
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJsonError(response, "Unsupported action.");
            return;
        }

        User currentUser = user.get();
        String email = currentUser.getEmail();
        String emailNormalized = currentUser.getEmailNormalized();
        Long userId = currentUser.getUserId();
        Long sourceCampaignId = campaignDao.findMostRecentActive()
                .map(EsCampaign::getEsCampaignId)
                .orElse(null);

        try {
            if (requestJoin) {
                upsertTopicSubscription(topicId, sourceCampaignId, email, emailNormalized, userId);
                Optional<EsTopicMeetingMember> existing = topicMeetingMemberDao.findByMeetingIdAndUserOrEmail(
                        meetingId, userId, emailNormalized);
                if (existing.isPresent()) {
                    EsTopicMeetingMember member = existing.get();
                    EsTopicMeetingMember.MembershipStatus status = member.getMembershipStatus();
                    if (status != EsTopicMeetingMember.MembershipStatus.REQUESTED
                            && status != EsTopicMeetingMember.MembershipStatus.APPROVED) {
                        member.setMembershipStatus(EsTopicMeetingMember.MembershipStatus.REQUESTED);
                        member.setApprovedAt(null);
                        member.setApprovedByUserId(null);
                    }
                    member.setUserId(userId);
                    member.setEmail(email);
                    member.setEmailNormalized(emailNormalized);
                    member.setSourceCampaignId(sourceCampaignId);
                    topicMeetingMemberDao.saveOrUpdate(member);
                } else {
                    EsTopicMeetingMember member = new EsTopicMeetingMember();
                    member.setEsTopicMeetingId(meetingId);
                    member.setUserId(userId);
                    member.setEmail(email);
                    member.setEmailNormalized(emailNormalized);
                    member.setMembershipStatus(EsTopicMeetingMember.MembershipStatus.REQUESTED);
                    member.setSourceCampaignId(sourceCampaignId);
                    topicMeetingMemberDao.saveOrUpdate(member);
                }
            } else {
                Optional<EsTopicMeetingMember> existing = topicMeetingMemberDao.findByMeetingIdAndUserOrEmail(
                        meetingId, userId, emailNormalized);
                if (existing.isPresent()) {
                    EsTopicMeetingMember member = existing.get();
                    member.setMembershipStatus(EsTopicMeetingMember.MembershipStatus.REMOVED);
                    member.setApprovedAt(null);
                    member.setApprovedByUserId(null);
                    topicMeetingMemberDao.saveOrUpdate(member);
                }
            }

            String membershipStatus = requestJoin ? "REQUESTED" : "REMOVED";
            try (PrintWriter out = response.getWriter()) {
                out.print("{\"ok\":true,\"requested\":" + requestJoin
                        + ",\"membershipStatus\":\"" + membershipStatus + "\"}");
            }
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJsonError(response, ex.getMessage());
        }
    }

    private void upsertTopicSubscription(Long topicId, Long sourceCampaignId, String email, String emailNormalized,
            Long userId) {
        Optional<EsSubscription> existing = subscriptionDao.findByUserOrEmailAndTopic(userId, emailNormalized, topicId);
        if (existing.isPresent()) {
            EsSubscription subscription = existing.get();
            subscription.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
            subscription.setUnsubscribedAt(null);
            subscription.setUserId(userId);
            if (subscription.getSourceCampaignId() == null) {
                subscription.setSourceCampaignId(sourceCampaignId);
            }
            subscriptionDao.saveOrUpdate(subscription);
            return;
        }

        EsSubscription subscription = new EsSubscription();
        subscription.setEmail(email);
        subscription.setEmailNormalized(emailNormalized);
        subscription.setUserId(userId);
        subscription.setEsTopicId(topicId);
        subscription.setSubscriptionType(EsSubscription.SubscriptionType.TOPIC);
        subscription.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
        subscription.setSourceCampaignId(sourceCampaignId);
        esInterestService.subscribeOrUpdate(subscription);
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
