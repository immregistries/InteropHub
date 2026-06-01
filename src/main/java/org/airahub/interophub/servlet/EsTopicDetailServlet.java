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
import org.airahub.interophub.dao.EsTopicNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsTopicCurationDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicRelationshipDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsComment;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicCuration;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicRelationship;

public class EsTopicDetailServlet extends HttpServlet {

        private static final DateTimeFormatter MEETING_DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

        private final AuthFlowService authFlowService;
        private final EsTopicDao esTopicDao;
        private final EsTopicNeighborhoodDao topicNeighborhoodDao;
        private final EsCampaignDao campaignDao;
        private final EsCampaignTopicDao campaignTopicDao;
        private final EsSubscriptionDao subscriptionDao;
        private final EsTopicMeetingMemberDao topicMeetingMemberDao;
        private final EsCommentDao commentDao;
        private final UserDao userDao;
        private final EsTopicMeetingDao esTopicMeetingDao;
        private final EsMeetingAgendaItemDao agendaItemDao;
        private final EsMeetingDao esMeetingDao;
        private final EsTopicRelationshipDao relationshipDao;
        private final EsTopicCurationDao curationDao;

        public EsTopicDetailServlet() {
                this.authFlowService = new AuthFlowService();
                this.esTopicDao = new EsTopicDao();
                this.topicNeighborhoodDao = new EsTopicNeighborhoodDao();
                this.campaignDao = new EsCampaignDao();
                this.campaignTopicDao = new EsCampaignTopicDao();
                this.subscriptionDao = new EsSubscriptionDao();
                this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
                this.commentDao = new EsCommentDao();
                this.userDao = new UserDao();
                this.esTopicMeetingDao = new EsTopicMeetingDao();
                this.agendaItemDao = new EsMeetingAgendaItemDao();
                this.esMeetingDao = new EsMeetingDao();
                this.relationshipDao = new EsTopicRelationshipDao();
                this.curationDao = new EsTopicCurationDao();
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

                // Curator navigation context — set when arriving from a curated-topics table
                // link
                Long curatorTopicId = null;
                String curatorParamStr = request.getParameter("curator");
                if (curatorParamStr != null && !curatorParamStr.isBlank()) {
                        try {
                                curatorTopicId = Long.parseLong(curatorParamStr.trim());
                        } catch (NumberFormatException ignored) {
                        }
                }

                // Inline edit mode for a specific curated list entry
                Long editCurationId = null;
                String editCurationStr = request.getParameter("editCuration");
                if (editCurationStr != null && !editCurationStr.isBlank()) {
                        try {
                                editCurationId = Long.parseLong(editCurationStr.trim());
                        } catch (NumberFormatException ignored) {
                        }
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
                        List<EsCampaignMeetingBrowseRow> meetingRows = campaignTopicDao
                                        .findAllActiveMeetingRowsOrdered();
                        for (EsCampaignMeetingBrowseRow row : meetingRows) {
                                if (topicId.equals(row.getEsTopicId())) {
                                        meeting = row;
                                        break;
                                }
                        }

                        // Meeting membership
                        if (meeting != null) {
                                List<EsTopicMeetingMember> members = topicMeetingMemberDao
                                                .findByMeetingIdsAndUserOrEmail(
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
                                                                        || (curEmail != null && curEmail.equals(
                                                                                        s.getEmailNormalized()))));
                        if (!showChampionView) {
                                topicSubscriptions = List.of();
                        }
                }

                Map<Long, User> subscriberUsers = Map.of();
                EsTopicMeeting topicMeetingSeries = null;
                List<EsMeeting> topicAgendaMeetings = List.of();
                List<EsMeeting> upcomingMeetings = List.of();
                List<EsComment> topicComments = List.of();

                // Load meeting appearances for all visitors (upcoming for public section;
                // all non-cancelled for champion section)
                {
                        List<EsMeetingAgendaItem> agendaItems = agendaItemDao.findByTopicId(topicId);
                        if (!agendaItems.isEmpty()) {
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
                                LocalDateTime now = LocalDateTime.now();
                                upcomingMeetings = tempMeetings.stream()
                                                .filter(m -> m.getScheduledStart() != null
                                                                && m.getScheduledStart().isAfter(now))
                                                .collect(Collectors.toList());
                        }
                }

                // Load relationship and curation data (needed for public display and champion
                // management)
                List<EsTopicRelationship> outboundRels = relationshipDao.findByFromTopicId(topicId);
                List<EsTopicRelationship> inboundRels = relationshipDao.findByToTopicId(topicId);
                List<EsTopicCuration> curatedEntries = curationDao.findByCuratorTopicId(topicId);
                List<EsTopicCuration> curatedByEntries = curationDao.findByCuratedTopicId(topicId);

                boolean needsTopicData = !outboundRels.isEmpty() || !inboundRels.isEmpty()
                                || !curatedEntries.isEmpty() || !curatedByEntries.isEmpty() || showChampionView
                                || curatorTopicId != null;
                List<EsTopic> allTopics = List.of();
                Map<Long, String> topicNameMap = Map.of();
                if (needsTopicData) {
                        allTopics = esTopicDao.findAllOrderByTopicName();
                        Map<Long, String> nameMap = new HashMap<>();
                        for (EsTopic t : allTopics) {
                                nameMap.put(t.getEsTopicId(), t.getTopicName());
                        }
                        topicNameMap = nameMap;
                }

                // Build curator nav context if the visitor arrived via a curated-topics link
                CuratorNavContext curatorNav = null;
                if (curatorTopicId != null) {
                        List<EsTopicCuration> curatorList = curationDao.findByCuratorTopicId(curatorTopicId);
                        int pos = -1;
                        for (int i = 0; i < curatorList.size(); i++) {
                                if (topicId.equals(curatorList.get(i).getCuratedTopicId())) {
                                        pos = i;
                                        break;
                                }
                        }
                        if (pos >= 0) {
                                String curatorName = topicNameMap.getOrDefault(curatorTopicId, "#" + curatorTopicId);
                                EsTopicCuration currentEntry = curatorList.get(pos);
                                String currentDisplayName = (currentEntry.getTopicAlias() != null
                                                && !currentEntry.getTopicAlias().isBlank())
                                                                ? currentEntry.getTopicAlias()
                                                                : topicNameMap.getOrDefault(topicId, "#" + topicId);
                                EsTopicCuration prevEntry = pos > 0 ? curatorList.get(pos - 1) : null;
                                EsTopicCuration nextEntry = pos < curatorList.size() - 1 ? curatorList.get(pos + 1)
                                                : null;
                                curatorNav = new CuratorNavContext(curatorTopicId, curatorName,
                                                prevEntry, currentDisplayName, nextEntry);
                        }
                }

                List<String> existingCurationStatuses = List.of();

                if (showChampionView) {
                        if (topicSubscriptions.isEmpty()) {
                                topicSubscriptions = subscriptionDao.findActiveByTopicId(topicId);
                        }
                        if (!curatedEntries.isEmpty()) {
                                existingCurationStatuses = curationDao.findDistinctCurationStatuses(topicId);
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
                        topicComments = commentDao.findByTopicId(topicId);
                }

                String topicName = orEmpty(topic.getTopicName());
                String description = orEmpty(topic.getDescription());
                String normalizedStage = orEmpty(topic.getStage());
                String normalizedNeighborhood = String.join(", ",
                                topicNeighborhoodDao.findNeighborhoodNamesByTopicId(topic.getEsTopicId()));

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
                        LocalEnvBannerRenderer.renderIfLocalhost(out);
                        out.println("  <div class=\"estd-shell\">");
                        out.println("    <a href=\"" + contextPath
                                        + "/es/topics\" class=\"estd-back\">\u2190 All Topics</a>");

                        if (curatorNav != null) {
                                renderCuratorNavWidget(out, contextPath, curatorNav, topicNameMap);
                        }

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
                                        + " data-confluence-url=\"" + escapeHtml(orEmpty(topic.getConfluenceUrl()))
                                        + "\""
                                        + " data-user-comments=\"" + escapeHtml(toJsonStringArray(userComments)) + "\""
                                        + " data-is-followed=\"" + (followed ? "1" : "0") + "\""
                                        + " data-meeting-id=\"" + (meetingId == null ? "" : meetingId) + "\""
                                        + " data-meeting-name=\""
                                        + escapeHtml(meeting == null ? "" : orEmpty(meeting.getMeetingName()))
                                        + "\""
                                        + " data-meeting-description=\""
                                        + escapeHtml(meeting == null ? "" : orEmpty(meeting.getMeetingDescription()))
                                        + "\""
                                        + " data-meeting-status=\"" + escapeHtml(membershipStatus) + "\""
                                        + " data-agenda-meetings=\""
                                        + escapeHtml(toJsonAgendaMeetings(upcomingMeetings)) + "\""
                                        + "></article>");

                        // Render the shared detail sheet HTML (no overlay in page mode)
                        EsTopicDetailRenderer.renderDetailSheetHtml(out, canInteract, canReview, false);

                        // Related topics display (public — visible to all visitors)
                        if (!outboundRels.isEmpty() || !inboundRels.isEmpty()) {
                                renderRelationshipsSection(out, contextPath, outboundRels, inboundRels, topicNameMap);
                        }

                        // Curated list display (public — visible to all visitors)
                        if (!curatedEntries.isEmpty() || !curatedByEntries.isEmpty()) {
                                renderCurationSection(out, contextPath, topicId, curatedEntries, curatedByEntries,
                                                topicNameMap);
                        }

                        if (showChampionView) {
                                renderChampionSection(out, contextPath, topicId, topicSubscriptions, subscriberUsers,
                                                topicMeetingSeries, topicAgendaMeetings, topicComments,
                                                outboundRels, curatedEntries, allTopics, existingCurationStatuses,
                                                topicNameMap,
                                                editCurationId);
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
                        LocalEnvBannerRenderer.renderIfLocalhost(out);
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

        private static final DateTimeFormatter AGENDA_DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

        private String toJsonAgendaMeetings(List<EsMeeting> meetings) {
                if (meetings == null || meetings.isEmpty()) {
                        return "[]";
                }
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < meetings.size(); i++) {
                        EsMeeting m = meetings.get(i);
                        if (i > 0)
                                sb.append(',');
                        String date = m.getScheduledStart() != null ? m.getScheduledStart().format(AGENDA_DATE_FMT)
                                        : "";
                        sb.append("{\"id\":").append(m.getEsMeetingId());
                        sb.append(",\"name\":\"").append(escapeJson(orEmpty(m.getMeetingName()))).append("\"");
                        sb.append(",\"date\":\"").append(escapeJson(date)).append("\"");
                        sb.append('}');
                }
                sb.append(']');
                return sb.toString();
        }

        private void renderRelationshipsSection(PrintWriter out, String contextPath,
                        List<EsTopicRelationship> outboundRels, List<EsTopicRelationship> inboundRels,
                        Map<Long, String> topicNameMap) {
                // Build label → list-of-links map preserving insertion order
                Map<String, List<String>> grouped = new LinkedHashMap<>();
                for (EsTopicRelationship rel : outboundRels) {
                        String label = rel.getRelationshipType() != null
                                        ? rel.getRelationshipType().getLabel()
                                        : "related to";
                        String name = topicNameMap.getOrDefault(rel.getToTopicId(), "#" + rel.getToTopicId());
                        grouped.computeIfAbsent(label, k -> new ArrayList<>())
                                        .add("<a href=\"" + contextPath + "/es/topic/" + rel.getToTopicId()
                                                        + "\" style=\"color:#0b6fb8;\">" + escapeHtml(name) + "</a>");
                }
                for (EsTopicRelationship rel : inboundRels) {
                        String label = rel.getRelationshipType() != null
                                        ? rel.getRelationshipType().getInverseLabel()
                                        : "related to";
                        String name = topicNameMap.getOrDefault(rel.getFromTopicId(), "#" + rel.getFromTopicId());
                        grouped.computeIfAbsent(label, k -> new ArrayList<>())
                                        .add("<a href=\"" + contextPath + "/es/topic/" + rel.getFromTopicId()
                                                        + "\" style=\"color:#0b6fb8;\">" + escapeHtml(name) + "</a>");
                }
                if (grouped.isEmpty()) {
                        return;
                }
                out.println("    <div style=\"margin-top:1.5rem; background:#fff; border:1px solid #d5dde5;"
                                + " border-radius:8px; padding:1.25rem 1.5rem;\">");
                out.println("      <h2 style=\"font-size:1rem; color:#0b6fb8; margin:0 0 1rem;"
                                + " font-weight:600; letter-spacing:0.01em; border-bottom:1px solid #d5dde5;"
                                + " padding-bottom:0.6rem;\">Related Topics</h2>");
                for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
                        out.println("      <p style=\"margin:0.3rem 0; font-size:0.9rem;\">");
                        out.println("        <span style=\"color:#5b6673; font-size:0.82rem;"
                                        + " min-width:10rem; display:inline-block;\">"
                                        + escapeHtml(capitalize(entry.getKey()))
                                        + ":</span> ");
                        out.println("        " + String.join(", ", entry.getValue()));
                        out.println("      </p>");
                }
                out.println("    </div>");
        }

        private void renderCurationSection(PrintWriter out, String contextPath, Long pageTopicId,
                        List<EsTopicCuration> curatedEntries, List<EsTopicCuration> curatedByEntries,
                        Map<Long, String> topicNameMap) {
                String sectionStyle = "margin-top:1.5rem; background:#fff; border:1px solid #d5dde5;"
                                + " border-radius:8px; padding:1.25rem 1.5rem;";
                String thStyle = "text-align:left; padding:0.45rem 0.75rem; border-bottom:1px solid #d5dde5;"
                                + " background:#eef2f7; font-size:0.82rem; font-weight:600; color:#5b6673;";
                String cellStyle = "padding:0.4rem 0.75rem; border-bottom:1px solid #eef1f4; font-size:0.87rem;";

                if (!curatedEntries.isEmpty()) {
                        // Group by category; blank/null → "" (uncategorized) rendered first
                        Map<String, List<EsTopicCuration>> byCategory = new LinkedHashMap<>();
                        for (EsTopicCuration entry : curatedEntries) {
                                String cat = (entry.getCategoryLabel() != null && !entry.getCategoryLabel().isBlank())
                                                ? entry.getCategoryLabel().trim()
                                                : "";
                                byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(entry);
                        }
                        // Uncategorized first, then categories in order of first appearance
                        List<String> categoryOrder = new ArrayList<>();
                        if (byCategory.containsKey("")) {
                                categoryOrder.add("");
                        }
                        for (String cat : byCategory.keySet()) {
                                if (!cat.isEmpty()) {
                                        categoryOrder.add(cat);
                                }
                        }
                        for (String cat : categoryOrder) {
                                List<EsTopicCuration> group = byCategory.get(cat);
                                if (group == null || group.isEmpty()) {
                                        continue;
                                }
                                String sectionTitle = cat.isEmpty() ? "Curated Topics" : (cat + " Topics");
                                out.println("    <div style=\"" + sectionStyle + "\">");
                                out.println("      <h2 style=\"font-size:1rem; color:#0b6fb8; margin:0 0 1rem;"
                                                + " font-weight:600; letter-spacing:0.01em; border-bottom:1px solid #d5dde5;"
                                                + " padding-bottom:0.6rem;\">" + escapeHtml(sectionTitle) + "</h2>");
                                out.println("      <table style=\"width:100%; border-collapse:collapse;\">");
                                out.println("        <thead><tr>");
                                out.println("          <th style=\"" + thStyle + "\">Topic</th>");
                                out.println("          <th style=\"" + thStyle + "\">Status</th>");
                                out.println("          <th style=\"" + thStyle + "\">Note</th>");
                                out.println("        </tr></thead><tbody>");
                                for (EsTopicCuration entry : group) {
                                        String canonicalName = topicNameMap.getOrDefault(entry.getCuratedTopicId(),
                                                        "#" + entry.getCuratedTopicId());
                                        boolean hasAlias = entry.getTopicAlias() != null
                                                        && !entry.getTopicAlias().isBlank();
                                        String displayName = hasAlias ? entry.getTopicAlias() : canonicalName;
                                        out.println("        <tr>");
                                        out.print("          <td style=\"" + cellStyle + "\">");
                                        out.print("<a href=\"" + contextPath + "/es/topic/" + entry.getCuratedTopicId()
                                                        + "?curator=" + pageTopicId
                                                        + "\" style=\"color:#0b6fb8;\">" + escapeHtml(displayName)
                                                        + "</a>");
                                        if (hasAlias) {
                                                out.print(" <span style=\"color:#5b6673; font-size:0.82rem;\">("
                                                                + escapeHtml(canonicalName) + ")</span>");
                                        }
                                        out.println("</td>");
                                        out.println("          <td style=\"" + cellStyle + "\">"
                                                        + escapeHtml(orEmpty(entry.getCurationStatus())) + "</td>");
                                        out.println("          <td style=\"" + cellStyle
                                                        + " color:#5b6673; white-space:pre-wrap;\">"
                                                        + escapeHtml(orEmpty(entry.getEditorialNote())) + "</td>");
                                        out.println("        </tr>");
                                }
                                out.println("        </tbody></table>");
                                out.println("    </div>");
                        }
                }

                if (!curatedByEntries.isEmpty()) {
                        out.println("    <div style=\"" + sectionStyle + "\">");
                        out.println("      <h2 style=\"font-size:1rem; color:#0b6fb8; margin:0 0 0.75rem;"
                                        + " font-weight:600; letter-spacing:0.01em; border-bottom:1px solid #d5dde5;"
                                        + " padding-bottom:0.6rem;\">Included In</h2>");
                        out.println(
                                        "      <p style=\"font-size:0.88rem; color:#5b6673; margin:0 0 0.5rem;\">This topic appears in the following curated lists:</p>");
                        out.println("      <ul style=\"margin:0; padding-left:1.25rem;\">");
                        for (EsTopicCuration entry : curatedByEntries) {
                                String curatorName = topicNameMap.getOrDefault(entry.getCuratorTopicId(),
                                                "#" + entry.getCuratorTopicId());
                                out.println("        <li style=\"font-size:0.9rem; margin:0.25rem 0;\"><a href=\""
                                                + contextPath + "/es/topic/" + entry.getCuratorTopicId()
                                                + "\" style=\"color:#0b6fb8;\">" + escapeHtml(curatorName)
                                                + "</a></li>");
                        }
                        out.println("      </ul>");
                        out.println("    </div>");
                }
        }

        private void renderCuratorNavWidget(PrintWriter out, String contextPath,
                        CuratorNavContext nav, Map<Long, String> topicNameMap) {
                out.println("    <div style=\"margin-bottom:1rem; background:#eef4fb;"
                                + " border:1px solid #b8d4ee; border-radius:8px; padding:0.65rem 1rem;"
                                + " display:flex; align-items:center; gap:0.6rem; flex-wrap:wrap;\">");
                out.println("      <span style=\"font-size:0.75rem; color:#5b6673; font-weight:600;"
                                + " text-transform:uppercase; letter-spacing:0.04em;"
                                + " white-space:nowrap;\">Curated list:</span>");
                out.println("      <a href=\"" + contextPath + "/es/topic/" + nav.curatorTopicId + "\""
                                + " style=\"font-size:0.88rem; color:#0b6fb8; font-weight:600;"
                                + " text-decoration:none;\">" + escapeHtml(nav.curatorTopicName) + "</a>");
                out.println("      <span style=\"color:#c5d0da; margin:0 0.2rem;\">|</span>");
                if (nav.prevEntry != null) {
                        String prevName = (nav.prevEntry.getTopicAlias() != null
                                        && !nav.prevEntry.getTopicAlias().isBlank())
                                                        ? nav.prevEntry.getTopicAlias()
                                                        : topicNameMap.getOrDefault(nav.prevEntry.getCuratedTopicId(),
                                                                        "#" + nav.prevEntry.getCuratedTopicId());
                        out.println("      <a href=\"" + contextPath + "/es/topic/"
                                        + nav.prevEntry.getCuratedTopicId() + "?curator=" + nav.curatorTopicId
                                        + "\" style=\"font-size:0.87rem; color:#0b6fb8; text-decoration:none;\""
                                        + " title=\"" + escapeHtml(prevName) + "\">\u2190 "
                                        + escapeHtml(prevName) + "</a>");
                } else {
                        out.println("      <span style=\"font-size:0.87rem; color:#c5d0da;\">\u2190</span>");
                }
                out.println("      <span style=\"font-size:0.87rem; font-weight:600; color:#0f1720;"
                                + " padding:0.15rem 0.5rem; background:#dbe9f5; border-radius:4px;\">"
                                + escapeHtml(nav.currentDisplayName) + "</span>");
                if (nav.nextEntry != null) {
                        String nextName = (nav.nextEntry.getTopicAlias() != null
                                        && !nav.nextEntry.getTopicAlias().isBlank())
                                                        ? nav.nextEntry.getTopicAlias()
                                                        : topicNameMap.getOrDefault(nav.nextEntry.getCuratedTopicId(),
                                                                        "#" + nav.nextEntry.getCuratedTopicId());
                        out.println("      <a href=\"" + contextPath + "/es/topic/"
                                        + nav.nextEntry.getCuratedTopicId() + "?curator=" + nav.curatorTopicId
                                        + "\" style=\"font-size:0.87rem; color:#0b6fb8; text-decoration:none;\""
                                        + " title=\"" + escapeHtml(nextName) + "\">" + escapeHtml(nextName)
                                        + " \u2192</a>");
                } else {
                        out.println("      <span style=\"font-size:0.87rem; color:#c5d0da;\">\u2192</span>");
                }
                out.println("    </div>");
        }

        private static final class CuratorNavContext {
                final Long curatorTopicId;
                final String curatorTopicName;
                final EsTopicCuration prevEntry;
                final String currentDisplayName;
                final EsTopicCuration nextEntry;

                CuratorNavContext(Long curatorTopicId, String curatorTopicName,
                                EsTopicCuration prevEntry, String currentDisplayName, EsTopicCuration nextEntry) {
                        this.curatorTopicId = curatorTopicId;
                        this.curatorTopicName = curatorTopicName;
                        this.prevEntry = prevEntry;
                        this.currentDisplayName = currentDisplayName;
                        this.nextEntry = nextEntry;
                }
        }

        private void renderChampionSection(PrintWriter out, String contextPath, Long topicId,
                        List<EsSubscription> subscriptions, Map<Long, User> userMap,
                        EsTopicMeeting series, List<EsMeeting> agendaMeetings,
                        List<EsComment> comments,
                        List<EsTopicRelationship> outboundRels,
                        List<EsTopicCuration> curatedEntries,
                        List<EsTopic> allTopics,
                        List<String> existingCurationStatuses,
                        Map<Long, String> topicNameMap,
                        Long editCurationId) {
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
                                out.println("            <td style=\"" + cellStyle + "\">" + escapeHtml(name)
                                                + "</td>");
                                out.println("            <td style=\"" + cellStyle + " color:#5b6673;\">"
                                                + escapeHtml(org) + "</td>");
                                out.println("            <td style=\"" + cellStyle + "\">" + escapeHtml(email)
                                                + "</td>");
                                out.println("            <td style=\"" + cellStyle
                                                + (isChamp ? " font-weight:600; color:#0b6fb8;" : "") + "\">"
                                                + escapeHtml(role) + "</td>");
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
                                out.println("            <td style=\"" + cellStyle
                                                + " white-space:nowrap; color:#5b6673;\">"
                                                + escapeHtml(dateStr) + "</td>");
                                out.println("            <td style=\"" + cellStyle + "\"><a href=\"" + contextPath
                                                + "/es/agenda?meetingId=" + m.getEsMeetingId()
                                                + "\" style=\"color:#0b6fb8;\">"
                                                + escapeHtml(orEmpty(m.getMeetingName())) + "</a></td>");
                                out.println("            <td style=\"" + cellStyle + "\">" + escapeHtml(statusStr)
                                                + "</td>");
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
                                String dateStr = c.getCreatedAt() != null ? c.getCreatedAt().toLocalDate().toString()
                                                : "";
                                String name = (orEmpty(c.getFirstName()) + " " + orEmpty(c.getLastName())).trim();
                                String commentText = orEmpty(c.getCommentText());
                                out.println("          <tr style=\"vertical-align:top;\">");
                                out.println("            <td style=\"" + cellStyle + " white-space:nowrap;\">"
                                                + escapeHtml(dateStr) + "</td>");
                                out.println("            <td style=\"" + cellStyle + " white-space:nowrap;\">"
                                                + escapeHtml(name) + "</td>");
                                out.println("            <td style=\"" + cellStyle + "\">" + escapeHtml(commentText)
                                                + "</td>");
                                out.println("          </tr>");
                        }
                        out.println("          </tbody></table>");
                }
                out.println("      </section>");

                // --- Manage Relationships ---
                out.println("      <section style=\"margin-bottom:1.5rem; padding-top:0.25rem;\">");
                out.println("        <h3 style=\"font-size:0.9rem; font-weight:600; color:#0f1720;"
                                + " margin:0 0 0.5rem;\">Manage Relationships</h3>");
                if (outboundRels.isEmpty()) {
                        out.println(
                                        "        <p style=\"color:#5b6673; font-size:0.88rem; margin:0 0 1rem;\">No outgoing relationships defined yet.</p>");
                } else {
                        out.println(
                                        "        <table style=\"width:100%; border-collapse:collapse; font-size:0.87rem; margin-bottom:1rem;\">");
                        out.println("          <thead><tr>");
                        out.println("            <th style=\"" + thStyle + "\">Type</th>");
                        out.println("            <th style=\"" + thStyle + "\">Topic</th>");
                        out.println("            <th style=\"" + thStyle + "\"></th>");
                        out.println("          </tr></thead><tbody>");
                        for (EsTopicRelationship rel : outboundRels) {
                                String label = rel.getRelationshipType() != null
                                                ? rel.getRelationshipType().getLabel()
                                                : "related to";
                                String name = topicNameMap.getOrDefault(rel.getToTopicId(), "#" + rel.getToTopicId());
                                out.println("          <tr>");
                                out.println("            <td style=\"" + cellStyle + "\">" + escapeHtml(label)
                                                + "</td>");
                                out.println("            <td style=\"" + cellStyle + "\"><a href=\"" + contextPath
                                                + "/es/topic/" + rel.getToTopicId() + "\" style=\"color:#0b6fb8;\">"
                                                + escapeHtml(name)
                                                + "</a></td>");
                                out.println("            <td style=\"" + cellStyle + "\">");
                                out.println("              <form method=\"post\" action=\"" + contextPath
                                                + "/es/topics/relationship\" style=\"display:inline;\">");
                                out.println("                <input type=\"hidden\" name=\"action\" value=\"delete\"/>");
                                out.println("                <input type=\"hidden\" name=\"relationshipId\" value=\""
                                                + rel.getEsTopicRelationshipId() + "\"/>");
                                out.println("                <input type=\"hidden\" name=\"fromTopicId\" value=\""
                                                + topicId + "\"/>");
                                out.println("                <button type=\"submit\" style=\"background:none; border:none;"
                                                + " color:#c0392b; cursor:pointer; font-size:0.82rem;\">Remove</button>");
                                out.println("              </form>");
                                out.println("            </td>");
                                out.println("          </tr>");
                        }
                        out.println("          </tbody></table>");
                }
                // Add relationship form
                out.println("        <form method=\"post\" action=\"" + contextPath
                                + "/es/topics/relationship\" style=\"display:flex; gap:0.5rem; flex-wrap:wrap; align-items:flex-end; margin-top:0.25rem;\">");
                out.println("          <input type=\"hidden\" name=\"action\" value=\"add\"/>");
                out.println("          <input type=\"hidden\" name=\"fromTopicId\" value=\"" + topicId + "\"/>");
                out.println("          <div>");
                out.println("            <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Relationship</label>");
                out.println("            <select name=\"relationshipType\" style=\"font-size:0.87rem;"
                                + " padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px;\">");
                for (EsTopicRelationship.RelationshipType type : EsTopicRelationship.RelationshipType.values()) {
                        boolean isDefault = type == EsTopicRelationship.RelationshipType.RELATED_TO;
                        out.println("              <option value=\"" + type.name() + "\""
                                        + (isDefault ? " selected" : "") + ">" + escapeHtml(type.getLabel())
                                        + "</option>");
                }
                out.println("            </select>");
                out.println("          </div>");
                out.println("          <div>");
                out.println("            <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Topic</label>");
                out.println("            <select name=\"toTopicId\" style=\"font-size:0.87rem;"
                                + " padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px; max-width:300px;\">");
                for (EsTopic t : allTopics) {
                        if (!t.getEsTopicId().equals(topicId)) {
                                out.println("              <option value=\"" + t.getEsTopicId() + "\">"
                                                + escapeHtml(t.getTopicName())
                                                + "</option>");
                        }
                }
                out.println("            </select>");
                out.println("          </div>");
                out.println("          <button type=\"submit\" style=\"padding:0.38rem 0.85rem;"
                                + " background:#0b6fb8; color:#fff; border:none; border-radius:4px;"
                                + " font-size:0.87rem; cursor:pointer;\">Add Link</button>");
                out.println("        </form>");
                out.println("      </section>");

                // --- Manage Curated List ---
                out.println("      <section>");
                out.println("        <h3 style=\"font-size:0.9rem; font-weight:600; color:#0f1720;"
                                + " margin:0 0 0.5rem;\">Manage Curated List</h3>");
                if (curatedEntries.isEmpty()) {
                        out.println(
                                        "        <p style=\"color:#5b6673; font-size:0.88rem; margin:0 0 1rem;\">No topics in curated list yet.</p>");
                } else {
                        out.println(
                                        "        <table style=\"width:100%; border-collapse:collapse; font-size:0.87rem; margin-bottom:1rem;\">");
                        out.println("          <thead><tr>");
                        out.println("            <th style=\"" + thStyle + "\">Topic</th>");
                        out.println("            <th style=\"" + thStyle + "\">Alias</th>");
                        out.println("            <th style=\"" + thStyle + "\">Category</th>");
                        out.println("            <th style=\"" + thStyle + "\">Status</th>");
                        out.println("            <th style=\"" + thStyle + "\">Order</th>");
                        out.println("            <th style=\"" + thStyle + "\"></th>");
                        out.println("          </tr></thead><tbody>");
                        for (EsTopicCuration entry : curatedEntries) {
                                String name = topicNameMap.getOrDefault(entry.getCuratedTopicId(),
                                                "#" + entry.getCuratedTopicId());
                                boolean isEditing = entry.getEsTopicCurationId().equals(editCurationId);
                                if (isEditing) {
                                        out.println("          <tr id='edit-curation' style='background:#f0f6ff;'>");
                                        out.println("            <td colspan='6' style='padding:0.75rem 1rem; border-bottom:1px solid #d5dde5;'>");
                                        out.println("              <div style='font-size:0.82rem; color:#5b6673; margin-bottom:0.5rem;'>Editing: <strong>"
                                                        + escapeHtml(name) + "</strong></div>");
                                        out.println("              <form method='post' action='" + contextPath
                                                        + "/es/topics/curation' style='display:grid; gap:0.5rem;'>");
                                        out.println("                <input type='hidden' name='action' value='update'/>");
                                        out.println("                <input type='hidden' name='curationId' value='"
                                                        + entry.getEsTopicCurationId() + "'/>");
                                        out.println("                <input type='hidden' name='curatorTopicId' value='"
                                                        + topicId + "'/>");
                                        out.println("                <div style='display:flex; gap:0.5rem; flex-wrap:wrap; align-items:flex-end;'>");
                                        out.println("                  <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;"
                                                        + " display:block; margin-bottom:0.2rem;'>Alias</label>"
                                                        + "<input type='text' name='topicAlias' maxlength='140'"
                                                        + " value='" + escapeHtml(orEmpty(entry.getTopicAlias())) + "'"
                                                        + " style='font-size:0.87rem; padding:0.3rem 0.5rem; border:1px solid #d5dde5;"
                                                        + " border-radius:4px; width:200px;'/></div>");
                                        out.println("                  <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;"
                                                        + " display:block; margin-bottom:0.2rem;'>Category</label>"
                                                        + "<input type='text' name='categoryLabel' maxlength='80'"
                                                        + " value='" + escapeHtml(orEmpty(entry.getCategoryLabel()))
                                                        + "'"
                                                        + " style='font-size:0.87rem; padding:0.3rem 0.5rem; border:1px solid #d5dde5;"
                                                        + " border-radius:4px; width:160px;'/></div>");
                                        out.println("                  <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;"
                                                        + " display:block; margin-bottom:0.2rem;'>Status</label>"
                                                        + "<input type='text' name='curationStatus' maxlength='80'"
                                                        + " list='curation-status-list'"
                                                        + " value='" + escapeHtml(orEmpty(entry.getCurationStatus()))
                                                        + "'"
                                                        + " style='font-size:0.87rem; padding:0.3rem 0.5rem; border:1px solid #d5dde5;"
                                                        + " border-radius:4px; width:160px;'/></div>");
                                        out.println("                  <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;"
                                                        + " display:block; margin-bottom:0.2rem;'>Order</label>"
                                                        + "<input type='number' name='displayOrder'"
                                                        + " value='"
                                                        + (entry.getDisplayOrder() == null ? 0
                                                                        : entry.getDisplayOrder())
                                                        + "'"
                                                        + " min='0' style='font-size:0.87rem; padding:0.3rem 0.5rem; border:1px solid #d5dde5;"
                                                        + " border-radius:4px; width:70px;'/></div>");
                                        out.println("                  <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;"
                                                        + " display:block; margin-bottom:0.2rem;'>Agenda Cadence (days)</label>"
                                                        + "<input type='number' name='agendaCadenceDays'"
                                                        + " value='"
                                                        + (entry.getAgendaCadenceDays() == null ? ""
                                                                        : entry.getAgendaCadenceDays())
                                                        + "'"
                                                        + " min='0' style='font-size:0.87rem; padding:0.3rem 0.5rem; border:1px solid #d5dde5;"
                                                        + " border-radius:4px; width:90px;'/></div>");
                                        out.println("                </div>");
                                        out.println("                <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;"
                                                        + " display:block; margin-bottom:0.2rem;'>Editorial Note</label>"
                                                        + "<textarea name='editorialNote' rows='2'"
                                                        + " style='font-size:0.87rem; padding:0.4rem 0.5rem; border:1px solid #d5dde5;"
                                                        + " border-radius:4px; width:100%; resize:vertical;'>"
                                                        + escapeHtml(orEmpty(entry.getEditorialNote()))
                                                        + "</textarea></div>");
                                        out.println("                <div style='display:flex; gap:0.75rem; align-items:center;'>"
                                                        + "<button type='submit' style='padding:0.35rem 0.85rem;"
                                                        + " background:#0b6fb8; color:#fff; border:none; border-radius:4px;"
                                                        + " font-size:0.87rem; cursor:pointer;'>Save Changes</button>"
                                                        + " <a href='" + contextPath + "/es/topic/" + topicId
                                                        + "' style='font-size:0.87rem; color:#5b6673;'>Cancel</a></div>");
                                        out.println("              </form>");
                                        out.println("            </td>");
                                        out.println("          </tr>");
                                } else {
                                        out.println("          <tr>");
                                        out.println("            <td style=\"" + cellStyle + "\"><a href=\""
                                                        + contextPath
                                                        + "/es/topic/" + topicId + "?editCuration="
                                                        + entry.getEsTopicCurationId() + "#edit-curation"
                                                        + "\" style=\"color:#0b6fb8;\">" + escapeHtml(name)
                                                        + "</a></td>");
                                        out.println("            <td style=\"" + cellStyle + "\">"
                                                        + escapeHtml(orEmpty(entry.getTopicAlias()))
                                                        + "</td>");
                                        out.println("            <td style=\"" + cellStyle + "\">"
                                                        + escapeHtml(orEmpty(entry.getCategoryLabel())) + "</td>");
                                        out.println("            <td style=\"" + cellStyle + "\">"
                                                        + escapeHtml(orEmpty(entry.getCurationStatus())) + "</td>");
                                        out.println("            <td style=\"" + cellStyle + " text-align:center;\">"
                                                        + orEmpty(entry.getDisplayOrder() == null ? null
                                                                        : String.valueOf(entry.getDisplayOrder()))
                                                        + "</td>");
                                        out.println("            <td style=\"" + cellStyle + "\">");
                                        out.println("              <form method=\"post\" action=\"" + contextPath
                                                        + "/es/topics/curation\" style=\"display:inline;\">");
                                        out.println("                <input type=\"hidden\" name=\"action\" value=\"delete\"/>");
                                        out.println("                <input type=\"hidden\" name=\"curationId\" value=\""
                                                        + entry.getEsTopicCurationId() + "\"/>");
                                        out.println(
                                                        "                <input type=\"hidden\" name=\"curatorTopicId\" value=\""
                                                                        + topicId + "\"/>");
                                        out.println("                <button type=\"submit\" style=\"background:none; border:none;"
                                                        + " color:#c0392b; cursor:pointer; font-size:0.82rem;\">Remove</button>");
                                        out.println("              </form>");
                                        out.println("            </td>");
                                        out.println("          </tr>");
                                }
                        }
                        out.println("          </tbody></table>");
                }
                // Add curation entry form (collapsed by default)
                out.println("        <details style=\"margin-top:0.5rem;\">");
                out.println("          <summary style=\"font-size:0.87rem; font-weight:600;"
                                + " cursor:pointer; color:#0b6fb8;\">+ Add to curated list</summary>");
                out.println("          <form method=\"post\" action=\"" + contextPath
                                + "/es/topics/curation\" style=\"margin-top:0.75rem; display:grid; gap:0.6rem;\">");
                out.println("            <input type=\"hidden\" name=\"action\" value=\"add\"/>");
                out.println("            <input type=\"hidden\" name=\"curatorTopicId\" value=\"" + topicId + "\"/>");
                out.println("            <div style=\"display:flex; gap:0.5rem; flex-wrap:wrap; align-items:flex-end;\">");
                // topic select
                out.println("              <div>");
                out.println("                <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Topic *</label>");
                out.println("                <select name=\"curatedTopicId\" style=\"font-size:0.87rem;"
                                + " padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px; max-width:300px;\">");
                for (EsTopic t : allTopics) {
                        if (!t.getEsTopicId().equals(topicId)) {
                                out.println("                  <option value=\"" + t.getEsTopicId() + "\">"
                                                + escapeHtml(t.getTopicName()) + "</option>");
                        }
                }
                out.println("                </select>");
                out.println("              </div>");
                // alias
                out.println("              <div>");
                out.println("                <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Alias</label>");
                out.println("                <input type=\"text\" name=\"topicAlias\" maxlength=\"140\""
                                + " placeholder=\"Custom display name\""
                                + " style=\"font-size:0.87rem; padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px; width:200px;\"/>");
                out.println("              </div>");
                // category
                out.println("              <div>");
                out.println("                <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Category</label>");
                out.println("                <input type=\"text\" name=\"categoryLabel\" maxlength=\"80\""
                                + " placeholder=\"e.g. Core\""
                                + " style=\"font-size:0.87rem; padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px; width:160px;\"/>");
                out.println("              </div>");
                // curation status with datalist
                out.println("              <div>");
                out.println("                <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Status</label>");
                out.println("                <input type=\"text\" name=\"curationStatus\" maxlength=\"80\""
                                + " list=\"curation-status-list\" placeholder=\"e.g. Active\""
                                + " style=\"font-size:0.87rem; padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px; width:160px;\"/>");
                out.println("                <datalist id=\"curation-status-list\">");
                for (String status : existingCurationStatuses) {
                        out.println("                  <option value=\"" + escapeHtml(status) + "\"/>");
                }
                out.println("                </datalist>");
                out.println("              </div>");
                // display order
                out.println("              <div>");
                out.println("                <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Order</label>");
                out.println("                <input type=\"number\" name=\"displayOrder\" value=\"0\" min=\"0\""
                                + " style=\"font-size:0.87rem; padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px; width:70px;\"/>");
                out.println("              </div>");
                // agenda cadence days
                out.println("              <div>");
                out.println("                <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Agenda Cadence (days)</label>");
                out.println("                <input type=\"number\" name=\"agendaCadenceDays\" value=\"\" min=\"0\""
                                + " style=\"font-size:0.87rem; padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px; width:90px;\"/>");
                out.println("              </div>");
                out.println("            </div>");
                // editorial note full-width
                out.println("            <div>");
                out.println("              <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Editorial Note</label>");
                out.println("              <textarea name=\"editorialNote\" rows=\"2\""
                                + " style=\"font-size:0.87rem; padding:0.4rem 0.5rem; border:1px solid #d5dde5;"
                                + " border-radius:4px; width:100%; resize:vertical;\"></textarea>");
                out.println("            </div>");
                out.println("            <div>");
                out.println("              <button type=\"submit\" style=\"padding:0.38rem 0.85rem;"
                                + " background:#0b6fb8; color:#fff; border:none; border-radius:4px;"
                                + " font-size:0.87rem; cursor:pointer;\">Add to Curated List</button>");
                out.println("            </div>");
                out.println("          </form>");
                out.println("        </details>");
                out.println("      </section>");
                out.println("    </div>");
        }

        private String capitalize(String s) {
                if (s == null || s.isEmpty()) {
                        return s;
                }
                return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
