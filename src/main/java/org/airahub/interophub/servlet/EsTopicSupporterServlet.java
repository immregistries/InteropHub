package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.airahub.interophub.dao.EsTopicSupporterDao;
import org.airahub.interophub.dao.SupporterDao;
import org.airahub.interophub.model.Supporter;
import org.airahub.interophub.model.User;

/**
 * Handles admin-only add/remove/create-and-add of Topic &lt;-&gt; Supporter
 * relationships. Unlike es_topic_relationship, Supporter management is
 * restricted to admins (see docs/add-supporters.md, section 10) - Champions
 * do not get an equivalent path here.
 * URL: POST /es/topics/supporter action=add|remove|createAndAdd
 */
public class EsTopicSupporterServlet extends HttpServlet {

    private final EsTopicSupporterDao topicSupporterDao;
    private final SupporterDao supporterDao;

    public EsTopicSupporterServlet() {
        this.topicSupporterDao = new EsTopicSupporterDao();
        this.supporterDao = new SupporterDao();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        request.setCharacterEncoding("UTF-8");
        String contextPath = request.getContextPath();
        Long topicId = parseLong(request.getParameter("topicId"));
        String action = trimToNull(request.getParameter("action"));

        if (topicId == null) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }

        if ("add".equals(action)) {
            Long supporterId = parseLong(request.getParameter("supporterId"));
            if (supporterId == null) {
                redirectWithError(response, contextPath, topicId, "Choose a Supporter to add.");
                return;
            }
            Supporter supporter = supporterDao.findById(supporterId).orElse(null);
            if (supporter == null || !Boolean.TRUE.equals(supporter.getActive())) {
                redirectWithError(response, contextPath, topicId, "That Supporter is not available to add.");
                return;
            }
            topicSupporterDao.add(topicId, supporterId);
            response.sendRedirect(manageUrl(contextPath, topicId));

        } else if ("remove".equals(action)) {
            Long esTopicSupporterId = parseLong(request.getParameter("esTopicSupporterId"));
            if (esTopicSupporterId != null) {
                topicSupporterDao.delete(esTopicSupporterId);
            }
            response.sendRedirect(manageUrl(contextPath, topicId));

        } else if ("createAndAdd".equals(action)) {
            String shortName = trimToNull(request.getParameter("shortName"));
            String fullName = trimToNull(request.getParameter("fullName"));
            String description = trimToNull(request.getParameter("description"));
            String websiteUrl = trimToNull(request.getParameter("websiteUrl"));
            boolean active = request.getParameter("active") != null;

            if (shortName == null || fullName == null) {
                redirectWithError(response, contextPath, topicId, "Short Name and Full Name are required.");
                return;
            }

            Optional<Supporter> existing = supporterDao.findByFullNameIgnoreCase(fullName);
            if (existing.isPresent()) {
                Supporter match = existing.get();
                if (!Boolean.TRUE.equals(match.getActive())) {
                    redirectWithError(response, contextPath, topicId,
                            "A Supporter named \"" + match.getFullName()
                                    + "\" already exists but is inactive. Reactivate it from Supporters admin"
                                    + " before assigning it to a Topic.");
                    return;
                }
                topicSupporterDao.add(topicId, match.getSupporterId());
            } else {
                Supporter supporter = new Supporter();
                supporter.setShortName(shortName);
                supporter.setFullName(fullName);
                supporter.setDescription(description);
                supporter.setWebsiteUrl(websiteUrl);
                supporter.setActive(active);
                supporter = supporterDao.saveOrUpdate(supporter);
                topicSupporterDao.add(topicId, supporter.getSupporterId());
            }
            response.sendRedirect(manageUrl(contextPath, topicId));

        } else {
            response.sendRedirect(contextPath + "/es/topics");
        }
    }

    private void redirectWithError(HttpServletResponse response, String contextPath, Long topicId, String message)
            throws IOException {
        response.sendRedirect(manageUrl(contextPath, topicId) + "?error="
                + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    private String manageUrl(String contextPath, Long topicId) {
        return contextPath + "/es/topic-manage/" + topicId + "/" + TopicManageView.SUPPORTERS.slug;
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
