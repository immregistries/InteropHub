package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicCurationDao;
import org.airahub.interophub.dao.EsTopicDao;
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
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicSpaceAccessService;

public class EsTopicManageServlet extends HttpServlet {

        private static final DateTimeFormatter MEETING_DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

        private final AuthFlowService authFlowService;
        private final EsTopicDao esTopicDao;
        private final EsSubscriptionDao subscriptionDao;
        private final UserDao userDao;
        private final EsTopicMeetingDao esTopicMeetingDao;
        private final EsMeetingAgendaItemDao agendaItemDao;
        private final EsMeetingDao esMeetingDao;
        private final EsTopicRelationshipDao relationshipDao;
        private final EsTopicCurationDao curationDao;
        private final EsCommentDao commentDao;
        private final TopicSpaceAccessService topicSpaceAccessService;

        public EsTopicManageServlet() {
                this.authFlowService = new AuthFlowService();
                this.esTopicDao = new EsTopicDao();
                this.subscriptionDao = new EsSubscriptionDao();
                this.userDao = new UserDao();
                this.esTopicMeetingDao = new EsTopicMeetingDao();
                this.agendaItemDao = new EsMeetingAgendaItemDao();
                this.esMeetingDao = new EsMeetingDao();
                this.relationshipDao = new EsTopicRelationshipDao();
                this.curationDao = new EsTopicCurationDao();
                this.commentDao = new EsCommentDao();
                this.topicSpaceAccessService = new TopicSpaceAccessService();
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                String contextPath = request.getContextPath();

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
                if (authenticatedUser.isEmpty()) {
                        response.sendRedirect(contextPath + "/es/topic/" + topicId);
                        return;
                }

                User viewer = authenticatedUser.get();
                EsTopic topicEntity = esTopicDao.findById(topicId).orElse(null);
                if (topicEntity == null || !topicSpaceAccessService.canViewTopic(viewer, topicEntity)) {
                        renderNotFound(response, contextPath, topicId);
                        return;
                }

                boolean isAdmin = authFlowService.isAdminUser(viewer);
                String viewerEmail = trimToNull(viewer.getEmailNormalized());
                List<EsSubscription> topicSubscriptions = subscriptionDao.findActiveByTopicId(topicId);
                boolean canManage = isAdmin || topicSubscriptions.stream().anyMatch(s -> isChampionEquivalentStatus(
                                s.getStatus())
                                && ((s.getUserId() != null && s.getUserId().equals(viewer.getUserId()))
                                                || (viewerEmail != null
                                                                && viewerEmail.equals(s.getEmailNormalized()))));
                if (!canManage) {
                        response.sendRedirect(contextPath + "/es/topic/" + topicId);
                        return;
                }

                Long editCurationId = null;
                String editCurationStr = request.getParameter("editCuration");
                if (editCurationStr != null && !editCurationStr.isBlank()) {
                        try {
                                editCurationId = Long.parseLong(editCurationStr.trim());
                        } catch (NumberFormatException ignored) {
                        }
                }

                List<Long> subUserIds = topicSubscriptions.stream()
                                .map(EsSubscription::getUserId)
                                .filter(id -> id != null)
                                .distinct()
                                .collect(Collectors.toList());

                Map<Long, User> subscriberUsers = Map.of();
                if (!subUserIds.isEmpty()) {
                        subscriberUsers = userDao.findByIds(subUserIds).stream()
                                        .collect(Collectors.toMap(User::getUserId, u -> u));
                }

                EsTopicMeeting topicMeetingSeries = esTopicMeetingDao.findByTopicId(topicId).orElse(null);

                List<EsMeeting> topicAgendaMeetings = List.of();
                List<EsMeetingAgendaItem> agendaItems = agendaItemDao.findByTopicId(topicId);
                if (!agendaItems.isEmpty()) {
                        List<Long> agendaMeetingIds = agendaItems.stream()
                                        .map(EsMeetingAgendaItem::getEsMeetingId)
                                        .distinct()
                                        .collect(Collectors.toList());
                        List<EsMeeting> meetings = new ArrayList<>();
                        for (Long meetingId : agendaMeetingIds) {
                                EsMeeting meeting = esMeetingDao.findById(meetingId).orElse(null);
                                if (meeting != null && meeting.getStatus() != EsMeeting.MeetingStatus.CANCELLED) {
                                        meetings.add(meeting);
                                }
                        }
                        meetings = topicSpaceAccessService.filterVisibleMeetings(viewer, meetings);
                        meetings.sort(Comparator.comparing(EsMeeting::getScheduledStart,
                                        Comparator.nullsLast(Comparator.naturalOrder())));
                        topicAgendaMeetings = meetings;
                }

                List<EsComment> topicComments = commentDao.findByTopicId(topicId);

                List<EsTopicRelationship> outboundRels = topicSpaceAccessService
                                .filterVisibleRelationships(viewer, relationshipDao.findByFromTopicId(topicId));
                List<EsTopicCuration> curatedEntries = topicSpaceAccessService
                                .filterVisibleCurations(viewer, curationDao.findByCuratorTopicId(topicId));

                List<EsTopic> allTopics = topicSpaceAccessService.filterVisibleTopics(viewer,
                                esTopicDao.findAllOrderByTopicName());

                Map<Long, String> topicNameMap = new HashMap<>();
                for (EsTopic t : allTopics) {
                        topicNameMap.put(t.getEsTopicId(), t.getTopicName());
                }

                List<String> existingCurationStatuses = List.of();
                if (!curatedEntries.isEmpty()) {
                        existingCurationStatuses = curationDao.findDistinctCurationStatuses(topicId);
                }

                response.setContentType("text/html;charset=UTF-8");
                try (PrintWriter out = response.getWriter()) {
                        out.println("<!doctype html>");
                        out.println("<html lang=\"en\">");
                        out.println("<head>");
                        out.println("  <meta charset=\"utf-8\" />");
                        out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />");
                        out.println("  <title>Manage " + escapeHtml(orEmpty(topic.getTopicName()))
                                        + " - InteropHub</title>");
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
                        out.println("  </style>");
                        out.println("</head>");
                        out.println("<body>");
                        LocalEnvBannerRenderer.renderIfLocalhost(out);
                        out.println("  <div class=\"estd-shell\">");
                        out.println("    <a href=\"" + contextPath
                                        + "/es/topics\" class=\"estd-back\">\u2190 All Topics</a>");
                        out.println("    <div style=\"margin-bottom:1rem; background:#fff; border:1px solid #d5dde5;"
                                        + " border-radius:8px; padding:0.75rem 1rem;\">");
                        out.println("      <p style=\"margin:0; font-size:0.9rem;\">"
                                        + "<a href=\"" + contextPath + "/es/topic/" + topicId
                                        + "\" style=\"color:#0b6fb8;\">\u2190 Back to Topic Details</a></p>");
                        out.println("    </div>");

                        renderChampionSection(out, contextPath, topicId, topicSubscriptions, subscriberUsers,
                                        topicMeetingSeries, topicAgendaMeetings, topicComments,
                                        outboundRels, curatedEntries, allTopics, existingCurationStatuses, topicNameMap,
                                        editCurationId, contextPath + "/es/topic-manage/" + topicId);

                        out.println("  </div>");
                        PageFooterRenderer.render(out);
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
                        PageFooterRenderer.render(out);
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

        private void renderChampionSection(PrintWriter out, String contextPath, Long topicId,
                        List<EsSubscription> subscriptions, Map<Long, User> userMap,
                        EsTopicMeeting series, List<EsMeeting> agendaMeetings,
                        List<EsComment> comments,
                        List<EsTopicRelationship> outboundRels,
                        List<EsTopicCuration> curatedEntries,
                        List<EsTopic> allTopics,
                        List<String> existingCurationStatuses,
                        Map<Long, String> topicNameMap,
                        Long editCurationId,
                        String selfPageUrl) {
                String thStyle = "text-align:left; padding:0.45rem 0.75rem; border-bottom:1px solid #d5dde5;"
                                + " background:#eef2f7; font-size:0.82rem; font-weight:600; color:#5b6673;";
                String cellStyle = "padding:0.4rem 0.75rem; border-bottom:1px solid #eef1f4;";

                out.println("    <div style=\"margin-top:1.5rem; background:#fff; border:1px solid #d5dde5;"
                                + " border-radius:8px; padding:1.25rem 1.5rem;\">");
                out.println("      <h2 style=\"font-size:1rem; color:#0b6fb8; margin:0 0 1.25rem;"
                                + " font-weight:600; letter-spacing:0.01em; border-bottom:1px solid #d5dde5;"
                                + " padding-bottom:0.6rem;\">Champion/Support &amp; Admin View</h2>");

                List<EsSubscription> sortedSubs = new ArrayList<>(subscriptions);
                sortedSubs.sort((a, b) -> {
                        boolean aIsChamp = isChampionEquivalentStatus(a.getStatus());
                        boolean bIsChamp = isChampionEquivalentStatus(b.getStatus());
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
                                boolean isChamp = isChampionEquivalentStatus(s.getStatus());
                                String role = switch (s.getStatus()) {
                                        case CHAMPION -> "Champion";
                                        case SUPPORT -> "Support";
                                        default -> "Follower";
                                };
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

                if (series != null) {
                        out.println("      <section style=\"margin-bottom:1.5rem;\">");
                        out.println("        <h3 style=\"font-size:0.9rem; font-weight:600; color:#0f1720;"
                                        + " margin:0 0 0.4rem;\">Working Group Meeting Series</h3>");
                        out.println("        <p style=\"margin:0;\"><a href=\"" + contextPath + "/es/meetings?seriesId="
                                        + series.getEsTopicMeetingId() + "\" style=\"color:#0b6fb8;\">"
                                        + escapeHtml(orEmpty(series.getMeetingName())) + " \u2192</a></p>");
                        out.println("      </section>");
                }

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
                                out.println("                <input type=\"hidden\" name=\"returnTo\" value=\"manage\"/>");
                                out.println("                <button type=\"submit\" style=\"background:none; border:none;"
                                                + " color:#c0392b; cursor:pointer; font-size:0.82rem;\">Remove</button>");
                                out.println("              </form>");
                                out.println("            </td>");
                                out.println("          </tr>");
                        }
                        out.println("          </tbody></table>");
                }

                out.println("        <form method=\"post\" action=\"" + contextPath
                                + "/es/topics/relationship\" style=\"display:flex; gap:0.5rem; flex-wrap:wrap; align-items:flex-end; margin-top:0.25rem;\">");
                out.println("          <input type=\"hidden\" name=\"action\" value=\"add\"/>");
                out.println("          <input type=\"hidden\" name=\"fromTopicId\" value=\"" + topicId + "\"/>");
                out.println("          <input type=\"hidden\" name=\"returnTo\" value=\"manage\"/>");
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
                                        out.println("                <input type='hidden' name='returnTo' value='manage'/>");
                                        out.println("                <div style='display:flex; gap:0.5rem; flex-wrap:wrap; align-items:flex-end;'>");
                                        out.println("                  <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;"
                                                        + " display:block; margin-bottom:0.2rem;'>Alias</label>"
                                                        + "<input type='text' name='topicAlias' maxlength='140'"
                                                        + " value='" + escapeHtml(orEmpty(entry.getTopicAlias())) + "'"
                                                        + " style='font-size:0.87rem; padding:0.3rem 0.5rem; border:1px solid #d5dde5;'"
                                                        + " border-radius:4px; width:200px;'/></div>");
                                        out.println("                  <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;"
                                                        + " display:block; margin-bottom:0.2rem;'>Category</label>"
                                                        + "<input type='text' name='categoryLabel' maxlength='80'"
                                                        + " value='" + escapeHtml(orEmpty(entry.getCategoryLabel()))
                                                        + "'"
                                                        + " style='font-size:0.87rem; padding:0.3rem 0.5rem; border:1px solid #d5dde5;'"
                                                        + " border-radius:4px; width:160px;'/></div>");
                                        out.println("                  <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;"
                                                        + " display:block; margin-bottom:0.2rem;'>Status</label>"
                                                        + "<input type='text' name='curationStatus' maxlength='80'"
                                                        + " list='curation-status-list'"
                                                        + " value='" + escapeHtml(orEmpty(entry.getCurationStatus()))
                                                        + "'"
                                                        + " style='font-size:0.87rem; padding:0.3rem 0.5rem; border:1px solid #d5dde5;'"
                                                        + " border-radius:4px; width:160px;'/></div>");
                                        out.println("                  <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;"
                                                        + " display:block; margin-bottom:0.2rem;'>Order</label>"
                                                        + "<input type='number' name='displayOrder'"
                                                        + " value='"
                                                        + (entry.getDisplayOrder() == null ? 0
                                                                        : entry.getDisplayOrder())
                                                        + "'"
                                                        + " min='0' style='font-size:0.87rem; padding:0.3rem 0.5rem; border:1px solid #d5dde5;'"
                                                        + " border-radius:4px; width:70px;'/></div>");
                                        out.println("                  <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;"
                                                        + " display:block; margin-bottom:0.2rem;'>Agenda Cadence (days)</label>"
                                                        + "<input type='number' name='agendaCadenceDays'"
                                                        + " value='"
                                                        + (entry.getAgendaCadenceDays() == null ? ""
                                                                        : entry.getAgendaCadenceDays())
                                                        + "'"
                                                        + " min='0' style='font-size:0.87rem; padding:0.3rem 0.5rem; border:1px solid #d5dde5;'"
                                                        + " border-radius:4px; width:90px;'/></div>");
                                        out.println("                </div>");
                                        out.println("                <div><label style='font-size:0.82rem; font-weight:600; color:#5b6673;'"
                                                        + " display:block; margin-bottom:0.2rem;'>Editorial Note</label>"
                                                        + "<textarea name='editorialNote' rows='2'"
                                                        + " style='font-size:0.87rem; padding:0.4rem 0.5rem; border:1px solid #d5dde5;'"
                                                        + " border-radius:4px; width:100%; resize:vertical;'>"
                                                        + escapeHtml(orEmpty(entry.getEditorialNote()))
                                                        + "</textarea></div>");
                                        out.println("                <div style='display:flex; gap:0.75rem; align-items:center;'>"
                                                        + "<button type='submit' style='padding:0.35rem 0.85rem;'"
                                                        + " background:#0b6fb8; color:#fff; border:none; border-radius:4px;'"
                                                        + " font-size:0.87rem; cursor:pointer;'>Save Changes</button>"
                                                        + " <a href='" + escapeHtml(selfPageUrl)
                                                        + "' style='font-size:0.87rem; color:#5b6673;'>Cancel</a></div>");
                                        out.println("              </form>");
                                        out.println("            </td>");
                                        out.println("          </tr>");
                                } else {
                                        out.println("          <tr>");
                                        out.println("            <td style=\"" + cellStyle + "\"><a href=\""
                                                        + escapeHtml(selfPageUrl) + "?editCuration="
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
                                        out.println("                <input type=\"hidden\" name=\"curatorTopicId\" value=\""
                                                        + topicId + "\"/>");
                                        out.println("                <input type=\"hidden\" name=\"returnTo\" value=\"manage\"/>");
                                        out.println("                <button type=\"submit\" style=\"background:none; border:none;"
                                                        + " color:#c0392b; cursor:pointer; font-size:0.82rem;\">Remove</button>");
                                        out.println("              </form>");
                                        out.println("            </td>");
                                        out.println("          </tr>");
                                }
                        }
                        out.println("          </tbody></table>");
                }

                out.println("        <details style=\"margin-top:0.5rem;\">");
                out.println("          <summary style=\"font-size:0.87rem; font-weight:600;"
                                + " cursor:pointer; color:#0b6fb8;\">+ Add to curated list</summary>");
                out.println("          <form method=\"post\" action=\"" + contextPath
                                + "/es/topics/curation\" style=\"margin-top:0.75rem; display:grid; gap:0.6rem;\">");
                out.println("            <input type=\"hidden\" name=\"action\" value=\"add\"/>");
                out.println("            <input type=\"hidden\" name=\"curatorTopicId\" value=\"" + topicId + "\"/>");
                out.println("            <input type=\"hidden\" name=\"returnTo\" value=\"manage\"/>");
                out.println("            <div style=\"display:flex; gap:0.5rem; flex-wrap:wrap; align-items:flex-end;\">");
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
                out.println("              <div>");
                out.println("                <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Alias</label>");
                out.println("                <input type=\"text\" name=\"topicAlias\" maxlength=\"140\""
                                + " placeholder=\"Custom display name\""
                                + " style=\"font-size:0.87rem; padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px; width:200px;\"/>");
                out.println("              </div>");
                out.println("              <div>");
                out.println("                <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Category</label>");
                out.println("                <input type=\"text\" name=\"categoryLabel\" maxlength=\"80\""
                                + " placeholder=\"e.g. Core\""
                                + " style=\"font-size:0.87rem; padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px; width:160px;\"/>");
                out.println("              </div>");
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
                out.println("              <div>");
                out.println("                <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Order</label>");
                out.println("                <input type=\"number\" name=\"displayOrder\" value=\"0\" min=\"0\""
                                + " style=\"font-size:0.87rem; padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px; width:70px;\"/>");
                out.println("              </div>");
                out.println("              <div>");
                out.println("                <label style=\"font-size:0.82rem; font-weight:600; color:#5b6673;"
                                + " display:block; margin-bottom:0.25rem;\">Agenda Cadence (days)</label>");
                out.println("                <input type=\"number\" name=\"agendaCadenceDays\" value=\"\" min=\"0\""
                                + " style=\"font-size:0.87rem; padding:0.35rem 0.5rem; border:1px solid #d5dde5; border-radius:4px; width:90px;\"/>");
                out.println("              </div>");
                out.println("            </div>");
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
