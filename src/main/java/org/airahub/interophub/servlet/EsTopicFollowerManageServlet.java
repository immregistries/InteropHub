package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EmailReason;
import org.airahub.interophub.service.TopicFollowerManagementService;

/**
 * Handles the managed-follower workflow (docs/topic-managed-followers.md):
 * adding a follower on someone's behalf, editing their contact name/org, and
 * sending registration/verify invitations. Available to app admins,
 * Topic-Space admins for the topic's space, and champion/support contacts
 * for the topic — the same tier as role/unfollow changes in
 * {@link EsTopicSubscriptionRoleServlet}.
 *
 * URL: POST /es/topics/followers-manage
 * action=add|inviteRegistration|inviteVerify|updateContact
 */
public class EsTopicFollowerManageServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsTopicDao esTopicDao;
    private final EsSubscriptionDao subscriptionDao;
    private final TopicFollowerManagementService followerService;

    public EsTopicFollowerManageServlet() {
        this.authFlowService = new AuthFlowService();
        this.esTopicDao = new EsTopicDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.followerService = new TopicFollowerManagementService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        String contextPath = request.getContextPath();
        Long topicId = parseLong(request.getParameter("topicId"));
        String action = trimToNull(request.getParameter("action"));

        if (topicId == null) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }

        Optional<User> viewerOpt = requireFollowerManager(request, topicId);
        if (viewerOpt.isEmpty()) {
            response.sendRedirect(contextPath + "/es/topic/" + topicId);
            return;
        }
        User viewer = viewerOpt.get();
        boolean isAdmin = authFlowService.isAdminUser(viewer);

        if ("add".equals(action)) {
            handleAdd(request, response, contextPath, topicId, viewer);
            return;
        }

        if ("inviteRegistration".equals(action) || "inviteVerify".equals(action)) {
            handleInvite(request, response, contextPath, topicId, action, isAdmin);
            return;
        }

        if ("updateContact".equals(action)) {
            handleUpdateContact(request, response, contextPath, topicId);
            return;
        }

        response.sendRedirect(manageUrl(contextPath, topicId));
    }

    private void handleUpdateContact(HttpServletRequest request, HttpServletResponse response, String contextPath,
            Long topicId) throws IOException {
        Long subscriptionId = parseLong(request.getParameter("subscriptionId"));
        if (subscriptionId == null) {
            redirectWithError(response, contextPath, topicId, "Follower not found.");
            return;
        }
        String firstName = trimToNull(request.getParameter("firstName"));
        String lastName = trimToNull(request.getParameter("lastName"));
        String organization = trimToNull(request.getParameter("organization"));

        TopicFollowerManagementService.Outcome<EsSubscription> outcome = followerService.updateContactInfo(
                subscriptionId, topicId, firstName, lastName, organization);
        if (!outcome.isSuccess()) {
            redirectWithError(response, contextPath, topicId, outcome.getErrorMessage());
            return;
        }
        response.sendRedirect(manageUrl(contextPath, topicId));
    }

    private void handleAdd(HttpServletRequest request, HttpServletResponse response, String contextPath,
            Long topicId, User viewer) throws IOException {
        String email = trimToNull(request.getParameter("email"));
        String firstName = trimToNull(request.getParameter("firstName"));
        String lastName = trimToNull(request.getParameter("lastName"));
        String organization = trimToNull(request.getParameter("organization"));
        String reason = trimToNull(request.getParameter("reason"));
        boolean sendNotification = request.getParameter("sendNotification") != null;

        if (email == null) {
            redirectWithError(response, contextPath, topicId, "An email address is required.");
            return;
        }

        TopicFollowerManagementService.Outcome<EsSubscription> outcome = followerService.addManagedFollower(
                topicId, email, firstName, lastName, organization, reason, viewer.getUserId(), sendNotification);
        if (!outcome.isSuccess()) {
            redirectWithError(response, contextPath, topicId, outcome.getErrorMessage());
            return;
        }
        response.sendRedirect(manageUrl(contextPath, topicId));
    }

    private void handleInvite(HttpServletRequest request, HttpServletResponse response, String contextPath,
            Long topicId, String action, boolean isAdmin) throws IOException {
        Long subscriptionId = parseLong(request.getParameter("subscriptionId"));
        boolean confirm = "1".equals(request.getParameter("confirm"));

        if (subscriptionId == null) {
            redirectWithError(response, contextPath, topicId, "Follower not found.");
            return;
        }
        Optional<EsSubscription> subOpt = subscriptionDao.findById(subscriptionId);
        if (subOpt.isEmpty() || !topicId.equals(subOpt.get().getEsTopicId())) {
            redirectWithError(response, contextPath, topicId, "Follower not found.");
            return;
        }
        EsSubscription sub = subOpt.get();
        boolean isRegistrationInvite = "inviteRegistration".equals(action);
        String reasonCode = isRegistrationInvite
                ? EmailReason.TOPIC_FOLLOWER_REGISTRATION_INVITE
                : EmailReason.TOPIC_FOLLOWER_VERIFY_EMAIL;

        if (!isAdmin && !confirm) {
            Optional<LocalDateTime> lastSent = followerService.lastSentAt(sub.getEmailNormalized(), reasonCode);
            if (lastSent.isPresent() && TopicFollowerManagementService.isWithinCooldown(
                    lastSent.get(), LocalDateTime.now(), TopicFollowerManagementService.RESEND_COOLDOWN_DAYS)) {
                redirectWithError(response, contextPath, topicId,
                        "This invitation was sent recently. Use \"Send again\" to confirm.");
                return;
            }
        }

        TopicFollowerManagementService.Outcome<Void> outcome = isRegistrationInvite
                ? followerService.sendRegistrationInvite(subscriptionId)
                : followerService.sendVerifyEmailInvite(subscriptionId, request);
        if (!outcome.isSuccess()) {
            redirectWithError(response, contextPath, topicId, outcome.getErrorMessage());
            return;
        }
        response.sendRedirect(manageUrl(contextPath, topicId));
    }

    /**
     * App admins, Topic-Space admins for the topic's space, or champion/support
     * contacts for the topic itself.
     */
    private Optional<User> requireFollowerManager(HttpServletRequest request, Long topicId) {
        Optional<User> viewerOpt = authFlowService.findAuthenticatedUser(request);
        if (viewerOpt.isEmpty()) {
            return Optional.empty();
        }
        User viewer = viewerOpt.get();
        EsTopic topic = esTopicDao.findById(topicId).orElse(null);
        if (topic == null || !followerService.canManageFollowers(viewer, topic)) {
            return Optional.empty();
        }
        return Optional.of(viewer);
    }

    private void redirectWithError(HttpServletResponse response, String contextPath, Long topicId, String message)
            throws IOException {
        response.sendRedirect(manageUrl(contextPath, topicId) + "?error="
                + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    private String manageUrl(String contextPath, Long topicId) {
        return contextPath + "/es/topic-manage/" + topicId + "/" + TopicManageView.FOLLOWERS.slug;
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
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
}
