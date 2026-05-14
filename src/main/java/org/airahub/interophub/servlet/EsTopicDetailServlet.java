package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignMeetingBrowseRow;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class EsTopicDetailServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsTopicDao esTopicDao;
    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicMeetingMemberDao topicMeetingMemberDao;
    private final EsCommentDao commentDao;

    public EsTopicDetailServlet() {
        this.authFlowService = new AuthFlowService();
        this.esTopicDao = new EsTopicDao();
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
        this.commentDao = new EsCommentDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();

        // Parse topic ID from path info (e.g. /123)
        Long topicId = parseTopicId(request.getPathInfo());
        if (topicId == null) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }

        Optional<EsCampaignTopicBrowseRow> topicOpt = esTopicDao.findActiveById(topicId);
        if (topicOpt.isEmpty()) {
            renderNotFound(response, contextPath, topicId);
            return;
        }
        EsCampaignTopicBrowseRow topic = topicOpt.get();

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        Optional<EsCampaign> campaign = campaignDao.findMostRecentActive();
        boolean canInteract = authenticatedUser.isPresent();
        boolean canReview = authenticatedUser.isPresent() && campaign.isPresent();
        String campaignCode = campaign.map(EsCampaign::getCampaignCode).orElse(null);

        Long authenticatedUserId = authenticatedUser.map(User::getUserId).orElse(null);
        String authenticatedEmailNormalized = authenticatedUser
                .map(User::getEmailNormalized)
                .map(this::trimToNull)
                .orElse(null);

        boolean followed = false;
        EsCampaignMeetingBrowseRow meeting = null;
        EsTopicMeetingMember membership = null;
        List<String> userComments = List.of();

        if (authenticatedUser.isPresent()) {
            // Follow status
            followed = subscriptionDao.findActiveTopicIdsByUserOrEmailAndTopicIds(
                    authenticatedUserId,
                    authenticatedEmailNormalized,
                    List.of(topicId)).contains(topicId);

            // Meeting for this topic
            List<EsCampaignMeetingBrowseRow> meetingRows = campaignTopicDao.findAllActiveMeetingRowsOrdered();
            for (EsCampaignMeetingBrowseRow row : meetingRows) {
                if (topicId.equals(row.getEsTopicId())) {
                    meeting = row;
                    break;
                }
            }

            // Meeting membership
            if (meeting != null) {
                List<EsTopicMeetingMember> members = topicMeetingMemberDao.findByMeetingIdsAndUserOrEmail(
                        List.of(meeting.getEsTopicMeetingId()),
                        authenticatedUserId,
                        authenticatedEmailNormalized);
                if (!members.isEmpty()) {
                    membership = members.get(0);
                }
            }

            // User comments
            if (canReview) {
                Long campaignId = campaign.get().getEsCampaignId();
                Long userId = authenticatedUser.get().getUserId();
                userComments = commentDao.findByUserAndCampaign(campaignId, userId).stream()
                        .filter(c -> topicId.equals(c.getEsTopicId()))
                        .map(c -> c.getCommentText() == null ? "" : c.getCommentText())
                        .filter(s -> !s.isBlank())
                        .toList();
            }
        }

        String membershipStatus = membership == null || membership.getMembershipStatus() == null
                ? ""
                : membership.getMembershipStatus().name();
        Long meetingId = meeting == null ? null : meeting.getEsTopicMeetingId();

        String topicName = orEmpty(topic.getTopicName());
        String description = orEmpty(topic.getDescription());
        String normalizedStage = orEmpty(topic.getStage());
        String normalizedNeighborhood = orEmpty(topic.getNeighborhood());

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!doctype html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"utf-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />");
            out.println("  <title>" + escapeHtml(topicName) + " - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("  <style>");
            out.println(
                    "    :root { --bg:#f6f7f8; --panel:#ffffff; --text:#0f1720; --muted:#5b6673; --border:#d5dde5; --accent:#0b6fb8; --accent-soft:#e6f1fb; }");
            out.println("    * { box-sizing:border-box; }");
            out.println(
                    "    body { margin:0; background:radial-gradient(circle at top left, #eef4f8 0, #f6f7f8 55%); color:var(--text); font-family:\"Segoe UI\", Tahoma, Geneva, Verdana, sans-serif; }");
            out.println("    .estd-shell { max-width:900px; margin:0 auto; padding:1.5rem 1.25rem; }");
            out.println(
                    "    .estd-back { display:inline-block; margin-bottom:1rem; color:var(--accent); text-decoration:none; font-size:0.95rem; }");
            out.println("    .estd-back:hover { text-decoration:underline; }");
            out.println("    /* Override popup styles so the sheet renders as normal page content */");
            out.println("    .es-detail-sheet {");
            out.println("      position: static !important;");
            out.println("      display: block !important;");
            out.println("      box-shadow: none !important;");
            out.println("      border-radius: 12px !important;");
            out.println("      border: 1px solid var(--border) !important;");
            out.println("      max-height: none !important;");
            out.println("      max-width: 760px !important;");
            out.println("      margin: 0 auto !important;");
            out.println("      transform: none !important;");
            out.println("      left: auto !important;");
            out.println("      right: auto !important;");
            out.println("      bottom: auto !important;");
            out.println("      z-index: auto !important;");
            out.println("    }");
            out.println("  </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("  <div class=\"estd-shell\">");
            out.println("    <a href=\"" + contextPath + "/es/topics\" class=\"estd-back\">\u2190 All Topics</a>");

            // Hidden article row with all data-* attributes — JS reads these to populate
            // the sheet
            out.println("    <article class=\"es-topic-row\" hidden"
                    + " data-topic-id=\"" + topic.getEsTopicId() + "\""
                    + " data-topic-name=\"" + escapeHtml(topicName) + "\""
                    + " data-topic-description=\"" + escapeHtml(description) + "\""
                    + " data-topic-type=\"" + escapeHtml(orEmpty(topic.getTopicType())) + "\""
                    + " data-policy-status=\"" + escapeHtml(orEmpty(topic.getPolicyStatus())) + "\""
                    + " data-topic-neighborhood=\"" + escapeHtml(normalizedNeighborhood) + "\""
                    + " data-topic-stage=\"" + escapeHtml(normalizedStage) + "\""
                    + " data-confluence-url=\"" + escapeHtml(orEmpty(topic.getConfluenceUrl())) + "\""
                    + " data-user-comments=\"" + escapeHtml(toJsonStringArray(userComments)) + "\""
                    + " data-is-followed=\"" + (followed ? "1" : "0") + "\""
                    + " data-meeting-id=\"" + (meetingId == null ? "" : meetingId) + "\""
                    + " data-meeting-name=\"" + escapeHtml(meeting == null ? "" : orEmpty(meeting.getMeetingName()))
                    + "\""
                    + " data-meeting-description=\""
                    + escapeHtml(meeting == null ? "" : orEmpty(meeting.getMeetingDescription())) + "\""
                    + " data-meeting-status=\"" + escapeHtml(membershipStatus) + "\""
                    + "></article>");

            // Render the shared detail sheet HTML (no overlay in page mode)
            EsTopicDetailRenderer.renderDetailSheetHtml(out, canInteract, canReview, false);

            out.println("  </div>");

            // Render the shared interactive script (closeBackUrl set → close navigates
            // back)
            EsTopicDetailRenderer.renderDetailInteractScript(out, contextPath, campaignCode,
                    canInteract, canReview, contextPath + "/es/topics");

            // Auto-open the detail panel on page load
            out.println("  <script>");
            out.println("    document.addEventListener('DOMContentLoaded', function() {");
            out.println("      var row = document.querySelector('.es-topic-row');");
            out.println("      if (row && window.openDetail) { window.openDetail(row); }");
            out.println("    });");
            out.println("  </script>");

            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderNotFound(HttpServletResponse response, String contextPath, Long topicId) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        try (PrintWriter out = response.getWriter()) {
            out.println("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"/>");
            out.println("<title>Topic Not Found - InteropHub</title></head><body>");
            out.println("<p>Topic not found.</p>");
            out.println("<p><a href=\"" + contextPath + "/es/topics\">\u2190 Back to Topics</a></p>");
            out.println("</body></html>");
        }
    }

    private Long parseTopicId(String pathInfo) {
        if (pathInfo == null || pathInfo.length() <= 1) {
            return null;
        }
        String segment = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        int slash = segment.indexOf('/');
        if (slash >= 0) {
            segment = segment.substring(0, slash);
        }
        try {
            long id = Long.parseLong(segment);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJsonStringArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(escapeJson(values.get(i))).append('"');
        }
        json.append(']');
        return json.toString();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
