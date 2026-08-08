package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

/**
 * Sets the follower role (SUBSCRIBED/CHAMPION/SUPPORT) or removes
 * (UNSUBSCRIBED) a topic follower. Admin only — mirrors the "Change Status"
 * action that used to live on the standalone admin subscription detail page.
 * URL: POST /es/topics/subscription-role
 */
public class EsTopicSubscriptionRoleServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsSubscriptionDao subscriptionDao;

    public EsTopicSubscriptionRoleServlet() {
        this.authFlowService = new AuthFlowService();
        this.subscriptionDao = new EsSubscriptionDao();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();

        Optional<User> userOpt = authFlowService.findAuthenticatedUser(request);
        if (userOpt.isEmpty() || !authFlowService.isAdminUser(userOpt.get())) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }

        Long subscriptionId = parseLong(request.getParameter("subscriptionId"));
        Long topicId = parseLong(request.getParameter("topicId"));
        EsSubscription.SubscriptionStatus targetStatus = parseStatus(request.getParameter("status"));

        if (subscriptionId == null || topicId == null) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }
        String returnUrl = contextPath + "/es/topic-manage/" + topicId + "/followers";

        Optional<EsSubscription> subOpt = subscriptionDao.findById(subscriptionId);
        if (subOpt.isEmpty()) {
            response.sendRedirect(returnUrl);
            return;
        }
        EsSubscription sub = subOpt.get();
        boolean isTopicSubscription = sub.getSubscriptionType() == EsSubscription.SubscriptionType.TOPIC
                && topicId.equals(sub.getEsTopicId());
        if (!isTopicSubscription || targetStatus == null || targetStatus == sub.getStatus()) {
            response.sendRedirect(returnUrl);
            return;
        }

        subscriptionDao.setTopicSubscriptionStatus(
                subscriptionId,
                targetStatus,
                targetStatus == EsSubscription.SubscriptionStatus.UNSUBSCRIBED ? LocalDateTime.now() : null);
        response.sendRedirect(returnUrl);
    }

    private EsSubscription.SubscriptionStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return EsSubscription.SubscriptionStatus.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
