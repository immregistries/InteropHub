package org.airahub.interophub.servlet;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicSpaceAccessService;

/**
 * Canonical Topic Space routes.
 *
 * Supported paths:
 * /spaces/{space-code}/topics
 * /spaces/{space-code}/topic/{topic-id}
 * /spaces/{space-code}/meetings
 * /spaces/{space-code}/meeting/{meeting-id}
 */
public class TopicSpaceRouteServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final TopicSpaceAccessService topicSpaceAccessService;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsTopicDao topicDao;
    private final EsMeetingDao meetingDao;

    public TopicSpaceRouteServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.topicDao = new EsTopicDao();
        this.meetingDao = new EsMeetingDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = trimToNull(request.getPathInfo());
        if (pathInfo == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String[] parts = pathInfo.split("/");
        if (parts.length < 3) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String spaceCode = trimToNull(parts[1]);
        String route = trimToNull(parts[2]);
        if (spaceCode == null || route == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        User viewer = authenticatedUser.orElse(null);

        EsTopicSpace requestedSpace = topicSpaceDao.findBySpaceCode(spaceCode)
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .orElse(null);
        if (requestedSpace == null || !topicSpaceAccessService.canViewSpace(viewer, requestedSpace)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String contextPath = request.getContextPath();
        if ("topics".equalsIgnoreCase(route)) {
            redirectPreservingTopicFilters(response, contextPath, spaceCode, request);
            return;
        }

        if ("topic".equalsIgnoreCase(route) && parts.length >= 4) {
            Long topicId = parseLong(parts[3]);
            if (topicId == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            EsTopic topic = topicDao.findById(topicId).orElse(null);
            if (topic == null || !topicSpaceAccessService.canViewTopic(viewer, topic)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            EsTopicSpace actualSpace = topicSpaceDao.findById(topic.getEsTopicSpaceId()).orElse(null);
            if (actualSpace == null || !topicSpaceAccessService.canViewSpace(viewer, actualSpace)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            String targetCode = actualSpace.getSpaceCode();
            String target = contextPath + "/es/topic/" + topicId + "?space=" + urlEncode(targetCode);
            String query = trimToNull(request.getQueryString());
            if (query != null) {
                target = target + "&" + query;
            }
            response.sendRedirect(target);
            return;
        }

        if ("meetings".equalsIgnoreCase(route)) {
            String seriesId = trimToNull(request.getParameter("seriesId"));
            if (seriesId != null) {
                response.sendRedirect(contextPath + "/es/meetings?seriesId=" + urlEncode(seriesId)
                        + "&space=" + urlEncode(spaceCode));
                return;
            }
            response.sendRedirect(contextPath + "/es/topics?space=" + urlEncode(spaceCode) + "&view=meetings");
            return;
        }

        if ("meeting".equalsIgnoreCase(route) && parts.length >= 4) {
            Long meetingId = parseLong(parts[3]);
            if (meetingId == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            EsMeeting meeting = meetingDao.findById(meetingId).orElse(null);
            if (meeting == null || !topicSpaceAccessService.canViewMeeting(viewer, meeting)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            EsTopicSpace actualSpace = topicSpaceDao.findById(meeting.getEsTopicSpaceId()).orElse(null);
            if (actualSpace == null || !topicSpaceAccessService.canViewSpace(viewer, actualSpace)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.sendRedirect(contextPath + "/es/agenda?meetingId=" + meetingId
                    + "&space=" + urlEncode(actualSpace.getSpaceCode()));
            return;
        }

        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private void redirectPreservingTopicFilters(HttpServletResponse response, String contextPath, String spaceCode,
            HttpServletRequest request) throws IOException {
        StringBuilder target = new StringBuilder(contextPath)
                .append("/es/topics?space=")
                .append(urlEncode(spaceCode));

        appendIfPresent(target, "view", request.getParameter("view"));
        appendIfPresent(target, "n", request.getParameter("n"));
        appendIfPresent(target, "s", request.getParameter("s"));
        appendIfPresent(target, "r", request.getParameter("r"));
        appendIfPresent(target, "q", request.getParameter("q"));

        response.sendRedirect(target.toString());
    }

    private void appendIfPresent(StringBuilder target, String key, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return;
        }
        target.append('&').append(key).append('=').append(urlEncode(normalized));
    }

    private Long parseLong(String raw) {
        String normalized = trimToNull(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException nfe) {
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

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
