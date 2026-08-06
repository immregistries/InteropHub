package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicCurationDao;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopicCuration;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicSpaceAccessService;

/**
 * Handles add/update/delete of es_topic_curation rows.
 * Authorization: admin always allowed; otherwise caller must be a champion of
 * curator_topic_id.
 * URL: POST /es/topics/curation action=add|update|delete
 */
public class EsTopicCurationServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsTopicCurationDao curationDao;
    private final EsSubscriptionDao subscriptionDao;
    private final TopicSpaceAccessService topicSpaceAccessService;

    public EsTopicCurationServlet() {
        this.authFlowService = new AuthFlowService();
        this.curationDao = new EsTopicCurationDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        String contextPath = request.getContextPath();
        boolean returnToManage = "manage".equalsIgnoreCase(trimToNull(request.getParameter("returnTo")));

        Optional<User> userOpt = authFlowService.findAuthenticatedUser(request);
        if (userOpt.isEmpty()) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }
        User user = userOpt.get();
        boolean isAdmin = authFlowService.isAdminUser(user);

        String action = trimToNull(request.getParameter("action"));

        if ("add".equals(action)) {
            Long curatorTopicId = parseLong(request.getParameter("curatorTopicId"));
            Long curatedTopicId = parseLong(request.getParameter("curatedTopicId"));

            if (curatorTopicId == null || curatedTopicId == null) {
                response.sendRedirect(contextPath + "/es/topics");
                return;
            }
            if (!topicSpaceAccessService.canViewTopicId(user, curatorTopicId)
                    || !topicSpaceAccessService.canViewTopicId(user, curatedTopicId)) {
                response.sendRedirect(contextPath + "/es/topics");
                return;
            }
            if (curatorTopicId.equals(curatedTopicId)) {
                response.sendRedirect(topicReturnUrl(contextPath, curatorTopicId, returnToManage));
                return;
            }
            if (!isAdmin && !isChampionOf(user, curatorTopicId)) {
                response.sendRedirect(
                        topicReturnUrl(contextPath, curatorTopicId, returnToManage) + "?error=not_authorized");
                return;
            }

            EsTopicCuration entry = new EsTopicCuration();
            entry.setCuratorTopicId(curatorTopicId);
            entry.setCuratedTopicId(curatedTopicId);
            entry.setTopicAlias(trimToNull(request.getParameter("topicAlias")));
            entry.setCategoryLabel(trimToNull(request.getParameter("categoryLabel")));
            entry.setEditorialNote(trimToNull(request.getParameter("editorialNote")));
            entry.setCurationStatus(trimToNull(request.getParameter("curationStatus")));
            entry.setDisplayOrder(parseIntOrDefault(request.getParameter("displayOrder"), 0));
            entry.setAgendaCadenceDays(parseIntOrZeroToNull(request.getParameter("agendaCadenceDays")));
            entry.setCreatedByUserId(user.getUserId());
            try {
                curationDao.save(entry);
            } catch (Exception ex) {
                // Unique constraint violation means the topic is already in this curated list
            }
            response.sendRedirect(topicReturnUrl(contextPath, curatorTopicId, returnToManage));

        } else if ("update".equals(action)) {
            Long curationId = parseLong(request.getParameter("curationId"));
            Long curatorTopicId = parseLong(request.getParameter("curatorTopicId"));

            if (curationId == null || curatorTopicId == null) {
                response.sendRedirect(contextPath + "/es/topics");
                return;
            }
            Optional<EsTopicCuration> entryOpt = curationDao.findById(curationId);
            if (entryOpt.isEmpty()) {
                response.sendRedirect(topicReturnUrl(contextPath, curatorTopicId, returnToManage));
                return;
            }
            EsTopicCuration entry = entryOpt.get();
            if (!topicSpaceAccessService.canViewTopicId(user, entry.getCuratorTopicId())
                    || !topicSpaceAccessService.canViewTopicId(user, entry.getCuratedTopicId())) {
                response.sendRedirect(contextPath + "/es/topics");
                return;
            }
            if (!isAdmin && !isChampionOf(user, entry.getCuratorTopicId())) {
                response.sendRedirect(topicReturnUrl(contextPath, entry.getCuratorTopicId(), returnToManage)
                        + "?error=not_authorized");
                return;
            }

            entry.setTopicAlias(trimToNull(request.getParameter("topicAlias")));
            entry.setCategoryLabel(trimToNull(request.getParameter("categoryLabel")));
            entry.setEditorialNote(trimToNull(request.getParameter("editorialNote")));
            entry.setCurationStatus(trimToNull(request.getParameter("curationStatus")));
            entry.setDisplayOrder(parseIntOrDefault(request.getParameter("displayOrder"), entry.getDisplayOrder()));
            entry.setAgendaCadenceDays(parseIntOrZeroToNull(request.getParameter("agendaCadenceDays")));
            curationDao.saveOrUpdate(entry);
            response.sendRedirect(topicReturnUrl(contextPath, entry.getCuratorTopicId(), returnToManage));

        } else if ("delete".equals(action)) {
            Long curationId = parseLong(request.getParameter("curationId"));
            Long curatorTopicId = parseLong(request.getParameter("curatorTopicId"));

            if (curationId == null) {
                response.sendRedirect(contextPath + "/es/topics");
                return;
            }
            Optional<EsTopicCuration> entryOpt = curationDao.findById(curationId);
            if (entryOpt.isEmpty()) {
                String fallback = curatorTopicId != null
                        ? topicReturnUrl(contextPath, curatorTopicId, returnToManage)
                        : contextPath + "/es/topics";
                response.sendRedirect(fallback);
                return;
            }
            EsTopicCuration entry = entryOpt.get();
            if (!topicSpaceAccessService.canViewTopicId(user, entry.getCuratorTopicId())
                    || !topicSpaceAccessService.canViewTopicId(user, entry.getCuratedTopicId())) {
                response.sendRedirect(contextPath + "/es/topics");
                return;
            }
            if (!isAdmin && !isChampionOf(user, entry.getCuratorTopicId())) {
                response.sendRedirect(topicReturnUrl(contextPath, entry.getCuratorTopicId(), returnToManage)
                        + "?error=not_authorized");
                return;
            }
            curationDao.delete(curationId);
            response.sendRedirect(topicReturnUrl(contextPath, entry.getCuratorTopicId(), returnToManage));

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

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return value == null ? defaultValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Integer parseIntOrZeroToNull(String value) {
        try {
            if (value == null || value.isBlank())
                return null;
            int v = Integer.parseInt(value.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String topicReturnUrl(String contextPath, Long topicId, boolean returnToManage) {
        if (returnToManage) {
            return contextPath + "/es/topic-manage/" + topicId + "/curated";
        }
        return contextPath + "/es/topic/" + topicId;
    }
}
