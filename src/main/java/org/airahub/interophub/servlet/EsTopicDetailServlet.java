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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsComment;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopicMeeting;

public class EsTopicDetailServlet extends HttpServlet {

    private static final DateTimeFormatter MEETING_DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final AuthFlowService authFlowService;
    private final EsTopicDao esTopicDao;
    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicMeetingMemberDao topicMeetingMemberDao;
    private final EsCommentDao commentDao;
    private final UserDao userDao;
    private final EsTopicMeetingDao esTopicMeetingDao;
    private final EsMeetingAgendaItemDao agendaItemDao;
    private final EsMeetingDao esMeetingDao;

    public EsTopicDetailServlet() {
        this.authFlowService = new AuthFlowService();
        this.esTopicDao = new EsTopicDao();
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
        this.commentDao = new EsCommentDao();
        this.userDao = new UserDao();
        this.esTopicMeetingDao = new EsTopicMeetingDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.esMeetingDao = new EsMeetingDao();
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

        // Champion / admin intelligence view
        boolean isAdmin = authenticatedUser.isPresent() && authFlowService.isAdminUser(authenticatedUser.get());
        List<EsSubscription> topicSubscriptions = List.of();
        boolean showChampionView = isAdmin;

        if (!showChampionView && authenticatedUser.isPresent()) {
            topicSubscriptions = subscriptionDao.findActiveByTopicId(topicId);
            final Long curUserId = authenticatedUser.get().getUserId();
            final String curEmail = authenticatedEmailNormalized;
            showChampionView = topicSubscriptions.stream()
                    .anyMatch(s -> EsSubscription.SubscriptionStatus.CHAMPION.equals(s.getStatus())
                            && (s.getUserId() != null && curUserId.equals(s.getUserId())
                                    || (curEmail != null && curEmail.equals(s.getEmailNormalized()))));
            if (!showChampionView) {
                topicSubscriptions = List.of();
            }
        }

        Map<Long, User> subscriberUsers = Map.of();
        EsTopicMeeting topicMeetingSeries = null;
        List<EsMeeting> topicAgendaMeetings = List.of();
        List<EsComment> topicComments = List.of();

        if (showChampionView) {
            if (topicSubscriptions.isEmpty()) {
                topicSubscriptions = subscriptionDao.findActiveByTopicId(topicId);
            }
            List<Long> subUserIds = topicSubscriptions.stream()
                    .map(EsSubscription::getUserId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());
            if (!subUserIds.isEmpty()) {
                subscriberUsers = userDao.findByIds(subUserIds).stream()
                        .collect(Collectors.toMap(User::getUserId, u -> u));
            }
            topicMeetingSeries = esTopicMeetingDao.findByTopicId(topicId).orElse(null);
            List<EsMeetingAgendaItem> agendaItems = agendaItemDao.findByTopicId(topicId);
            List<Long> agendaMeetingIds = agendaItems.stream()
                    .map(EsMeetingAgendaItem::getEsMeetingId)
                    .distinct()
                    .collect(Collectors.toList());
            List<EsMeeting> tempMeetings = new ArrayList<>();
            for (Long mid : agendaMeetingIds) {
                EsMeeting m = esMeetingDao.findById(mid).orElse(null);
                if (m != null && m.getStatus() != EsMeeting.MeetingStatus.CANCELLED) {
                    tempMeetings.add(m);
                }
            }
            tempMeetings.sort(Comparator.comparing(EsMeeting::getScheduledStart));
            topicAgendaMeetings = tempMeetings;
            topicComments = commentDao.findByTopicId(topicId);
        }

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

            if (showChampionView) {
                renderChampionSection(out, contextPath, topicSubscriptions, subscriberUsers,
                        topicMeetingSeries, topicAgendaMeetings, topicComments);
            }

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

    private void renderChampionSection(PrintWriter out, String contextPath,
            List<EsSubscription> subscriptions, Map<Long, User> userMap,
            EsTopicMeeting series, List<EsMeeting> agendaMeetings,
            List<EsComment> comments) {
        String thStyle = "text-align:left; padding:0.45rem 0.75rem; border-bottom:1px solid #d5dde5;"
                + " background:#eef2f7; font-size:0.82rem; font-weight:600; color:#5b6673;";
        String cellStyle = "padding:0.4rem 0.75rem; border-bottom:1px solid #eef1f4;";

        out.println("    <div style=\"margin-top:1.5rem; background:#fff; border:1px solid #d5dde5;"
                + " border-radius:8px; padding:1.25rem 1.5rem;\">");
        out.println("      <h2 style=\"font-size:1rem; color:#0b6fb8; margin:0 0 1.25rem;"
                + " font-weight:600; letter-spacing:0.01em; border-bottom:1px solid #d5dde5;"
                + " padding-bottom:0.6rem;\">Champion &amp; Admin View</h2>");

        // --- Followers ---
        List<EsSubscription> sortedSubs = new ArrayList<>(subscriptions);
        sortedSubs.sort((a, b) -> {
            boolean aIsChamp = EsSubscription.SubscriptionStatus.CHAMPION.equals(a.getStatus());
            boolean bIsChamp = EsSubscription.SubscriptionStatus.CHAMPION.equals(b.getStatus());
            if (aIsChamp != bIsChamp) {
                return aIsChamp ? -1 : 1;
            }
            User uA = a.getUserId() != null ? userMap.get(a.getUserId()) : null;
            User uB = b.getUserId() != null ? userMap.get(b.getUserId()) : null;
            String nameA = uA != null
                    ? (orEmpty(uA.getFirstName()) + " " + orEmpty(uA.getLastName())).trim()
                    : orEmpty(a.getEmail());
            String nameB = uB != null
                    ? (orEmpty(uB.getFirstName()) + " " + orEmpty(uB.getLastName())).trim()
                    : orEmpty(b.getEmail());
            return nameA.compareToIgnoreCase(nameB);
        });

        out.println("      <section style=\"margin-bottom:1.5rem;\">");
        out.println("        <h3 style=\"font-size:0.9rem; font-weight:600; color:#0f1720;"
                + " margin:0 0 0.5rem;\">Followers (" + sortedSubs.size() + ")</h3>");
        if (sortedSubs.isEmpty()) {
            out.println("        <p style=\"color:#5b6673; font-size:0.88rem; margin:0;\">No followers yet.</p>");
        } else {
            out.println("        <table style=\"width:100%; border-collapse:collapse; font-size:0.87rem;\">");
            out.println("          <thead><tr>");
            out.println("            <th style=\"" + thStyle + "\">Name</th>");
            out.println("            <th style=\"" + thStyle + "\">Organization</th>");
            out.println("            <th style=\"" + thStyle + "\">Email</th>");
            out.println("            <th style=\"" + thStyle + "\">Role</th>");
            out.println("          </tr></thead><tbody>");
            for (EsSubscription s : sortedSubs) {
                User u = s.getUserId() != null ? userMap.get(s.getUserId()) : null;
                boolean isChamp = EsSubscription.SubscriptionStatus.CHAMPION.equals(s.getStatus());
                String role = isChamp ? "Champion" : "Follower";
                String name = u != null
                        ? (orEmpty(u.getFirstName()) + " " + orEmpty(u.getLastName())).trim()
                        : "";
                String org = u != null ? orEmpty(u.getOrganization()) : "";
                String email = orEmpty(s.getEmail());
                out.println("          <tr>");
                out.println("            <td style=\"" + cellStyle + "\">" + escapeHtml(name) + "</td>");
                out.println("            <td style=\"" + cellStyle + " color:#5b6673;\">" + escapeHtml(org) + "</td>");
                out.println("            <td style=\"" + cellStyle + "\">" + escapeHtml(email) + "</td>");
                out.println("            <td style=\"" + cellStyle
                        + (isChamp ? " font-weight:600; color:#0b6fb8;" : "") + "\">" + escapeHtml(role) + "</td>");
                out.println("          </tr>");
            }
            out.println("          </tbody></table>");
        }
        out.println("      </section>");

        // --- Series Meeting Link ---
        if (series != null) {
            out.println("      <section style=\"margin-bottom:1.5rem;\">");
            out.println("        <h3 style=\"font-size:0.9rem; font-weight:600; color:#0f1720;"
                    + " margin:0 0 0.4rem;\">Working Group Meeting Series</h3>");
            out.println("        <p style=\"margin:0;\"><a href=\"" + contextPath + "/es/meetings?seriesId="
                    + series.getEsTopicMeetingId() + "\" style=\"color:#0b6fb8;\">"
                    + escapeHtml(orEmpty(series.getMeetingName())) + " \u2192</a></p>");
            out.println("      </section>");
        }

        // --- Agenda Appearances ---
        out.println("      <section style=\"margin-bottom:1.5rem;\">");
        out.println("        <h3 style=\"font-size:0.9rem; font-weight:600; color:#0f1720;"
                + " margin:0 0 0.5rem;\">Meeting Appearances (" + agendaMeetings.size() + ")</h3>");
        if (agendaMeetings.isEmpty()) {
            out.println(
                    "        <p style=\"color:#5b6673; font-size:0.88rem; margin:0;\">This topic has not appeared on any meeting agendas yet.</p>");
        } else {
            out.println("        <table style=\"width:100%; border-collapse:collapse; font-size:0.87rem;\">");
            out.println("          <thead><tr>");
            out.println("            <th style=\"" + thStyle + "\">Date</th>");
            out.println("            <th style=\"" + thStyle + "\">Meeting</th>");
            out.println("            <th style=\"" + thStyle + "\">Status</th>");
            out.println("          </tr></thead><tbody>");
            for (EsMeeting m : agendaMeetings) {
                String dateStr = m.getScheduledStart() != null
                        ? m.getScheduledStart().format(MEETING_DATE_FMT)
                        : "";
                String statusStr = m.getStatus() != null ? m.getStatus().name() : "";
                out.println("          <tr>");
                out.println("            <td style=\"" + cellStyle + " white-space:nowrap; color:#5b6673;\">"
                        + escapeHtml(dateStr) + "</td>");
                out.println("            <td style=\"" + cellStyle + "\"><a href=\"" + contextPath
                        + "/es/agenda?meetingId=" + m.getEsMeetingId() + "\" style=\"color:#0b6fb8;\">"
                        + escapeHtml(orEmpty(m.getMeetingName())) + "</a></td>");
                out.println("            <td style=\"" + cellStyle + "\">" + escapeHtml(statusStr) + "</td>");
                out.println("          </tr>");
            }
            out.println("          </tbody></table>");
        }
        out.println("      </section>");

        // --- Campaign Comments ---
        out.println("      <section>");
        out.println("        <h3 style=\"font-size:0.9rem; font-weight:600; color:#0f1720;"
                + " margin:0 0 0.5rem;\">Campaign Comments (" + comments.size() + ")</h3>");
        if (comments.isEmpty()) {
            out.println(
                    "        <p style=\"color:#5b6673; font-size:0.88rem; margin:0;\">No campaign comments yet.</p>");
        } else {
            out.println("        <table style=\"width:100%; border-collapse:collapse; font-size:0.87rem;\">");
            out.println("          <thead><tr>");
            out.println("            <th style=\"" + thStyle + "\">Date</th>");
            out.println("            <th style=\"" + thStyle + "\">Submitted by</th>");
            out.println("            <th style=\"" + thStyle + "\">Comment</th>");
            out.println("          </tr></thead><tbody>");
            for (EsComment c : comments) {
                String dateStr = c.getCreatedAt() != null ? c.getCreatedAt().toLocalDate().toString() : "";
                String name = (orEmpty(c.getFirstName()) + " " + orEmpty(c.getLastName())).trim();
                String commentText = orEmpty(c.getCommentText());
                out.println("          <tr style=\"vertical-align:top;\">");
                out.println("            <td style=\"" + cellStyle + " white-space:nowrap;\">"
                        + escapeHtml(dateStr) + "</td>");
                out.println("            <td style=\"" + cellStyle + " white-space:nowrap;\">"
                        + escapeHtml(name) + "</td>");
                out.println("            <td style=\"" + cellStyle + "\">" + escapeHtml(commentText) + "</td>");
                out.println("          </tr>");
            }
            out.println("          </tbody></table>");
        }
        out.println("      </section>");
        out.println("    </div>");
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
