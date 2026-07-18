package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicRelationshipDao;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopicRelationship;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicSpaceAccessService;

/**
 * Handles add/delete of es_topic_relationship rows.
 * Authorization: admin always allowed; otherwise caller must be a champion of
 * from_topic_id.
 * URL: POST /es/topics/relationship action=add|delete
 */
public class EsTopicRelationshipServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsTopicRelationshipDao relationshipDao;
    private final EsSubscriptionDao subscriptionDao;
    private final TopicSpaceAccessService topicSpaceAccessService;

    public EsTopicRelationshipServlet() {
        this.authFlowService = new AuthFlowService();
        this.relationshipDao = new EsTopicRelationshipDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        String contextPath = request.getContextPath();

        Optional<User> userOpt = authFlowService.findAuthenticatedUser(request);
        if (userOpt.isEmpty()) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }
        User user = userOpt.get();
        boolean isAdmin = authFlowService.isAdminUser(user);

        String action = trimToNull(request.getParameter("action"));

        if ("add".equals(action)) {
            Long fromTopicId = parseLong(request.getParameter("fromTopicId"));
            Long toTopicId = parseLong(request.getParameter("toTopicId"));
            String typeStr = trimToNull(request.getParameter("relationshipType"));

            if (fromTopicId == null || toTopicId == null) {
                response.sendRedirect(contextPath + "/es/topics");
                return;
            }
            if (!topicSpaceAccessService.canViewTopicId(user, fromTopicId)
                    || !topicSpaceAccessService.canViewTopicId(user, toTopicId)) {
                response.sendRedirect(contextPath + "/es/topics");
                return;
            }
            // Prevent self-links
            if (fromTopicId.equals(toTopicId)) {
                response.sendRedirect(contextPath + "/es/topic/" + fromTopicId);
                return;
            }
            if (!isAdmin && !isChampionOf(user, fromTopicId)) {
                response.sendRedirect(contextPath + "/es/topic/" + fromTopicId + "?error=not_authorized");
                return;
            }

            EsTopicRelationship rel = new EsTopicRelationship();
            rel.setFromTopicId(fromTopicId);
            rel.setToTopicId(toTopicId);
            rel.setRelationshipType(EsTopicRelationship.RelationshipType.fromString(typeStr));
            rel.setCreatedByUserId(user.getUserId());
            try {
                relationshipDao.save(rel);
            } catch (Exception ex) {
                // Unique constraint violation means the link already exists — not an error for
                // the user
            }
            response.sendRedirect(contextPath + "/es/topic/" + fromTopicId);

        } else if ("delete".equals(action)) {
            Long relationshipId = parseLong(request.getParameter("relationshipId"));
            Long fromTopicId = parseLong(request.getParameter("fromTopicId"));

            if (relationshipId == null) {
                response.sendRedirect(contextPath + "/es/topics");
                return;
            }
            Optional<EsTopicRelationship> relOpt = relationshipDao.findById(relationshipId);
            if (relOpt.isEmpty()) {
                String fallback = fromTopicId != null
                        ? contextPath + "/es/topic/" + fromTopicId
                        : contextPath + "/es/topics";
                response.sendRedirect(fallback);
                return;
            }
            EsTopicRelationship rel = relOpt.get();
            if (!topicSpaceAccessService.canViewTopicId(user, rel.getFromTopicId())
                    || !topicSpaceAccessService.canViewTopicId(user, rel.getToTopicId())) {
                response.sendRedirect(contextPath + "/es/topics");
                return;
            }
            if (!isAdmin && !isChampionOf(user, rel.getFromTopicId())) {
                response.sendRedirect(contextPath + "/es/topic/" + rel.getFromTopicId() + "?error=not_authorized");
                return;
            }
            relationshipDao.delete(relationshipId);
            response.sendRedirect(contextPath + "/es/topic/" + rel.getFromTopicId());

        } else {
            response.sendRedirect(contextPath + "/es/topics");
        }
    }

    private boolean isChampionOf(User user, Long topicId) {
        List<EsSubscription> subs = subscriptionDao.findActiveByTopicId(topicId);
        return subs.stream().anyMatch(s -> isChampionEquivalentStatus(s.getStatus())
                && (user.getUserId() != null && user.getUserId().equals(s.getUserId())
                        || (user.getEmailNormalized() != null
                                && user.getEmailNormalized().equals(s.getEmailNormalized()))));
    }

    private boolean isChampionEquivalentStatus(EsSubscription.SubscriptionStatus status) {
        return status == EsSubscription.SubscriptionStatus.CHAMPION
                || status == EsSubscription.SubscriptionStatus.SUPPORT;
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
