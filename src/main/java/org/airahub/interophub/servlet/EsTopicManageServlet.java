package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsTopicSupporterDao;
import org.airahub.interophub.dao.SupporterDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsComment;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicCuration;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicRelationship;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicSupporter;
import org.airahub.interophub.model.Supporter;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EmailReason;
import org.airahub.interophub.service.EsTopicViewHistoryService;
import org.airahub.interophub.service.TopicFollowerManagementService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.immregistries.aira.web.AiraPage;

public class EsTopicManageServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(EsTopicManageServlet.class.getName());
    private static final DateTimeFormatter MEETING_DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    private static final DateTimeFormatter INVITE_DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final AuthFlowService authFlowService;
    private final EsTopicDao esTopicDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsSubscriptionDao subscriptionDao;
    private final UserDao userDao;
    private final EsTopicMeetingDao esTopicMeetingDao;
    private final EsMeetingAgendaItemDao agendaItemDao;
    private final EsMeetingDao esMeetingDao;
    private final EsTopicRelationshipDao relationshipDao;
    private final EsTopicCurationDao curationDao;
    private final EsCommentDao commentDao;
    private final EsTopicSupporterDao topicSupporterDao;
    private final SupporterDao supporterDao;
    private final TopicSpaceAccessService topicSpaceAccessService;
    private final EsTopicViewHistoryService topicViewHistoryService;
    private final TopicFollowerManagementService topicFollowerManagementService;

    public EsTopicManageServlet() {
        this.authFlowService = new AuthFlowService();
        this.esTopicDao = new EsTopicDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.userDao = new UserDao();
        this.esTopicMeetingDao = new EsTopicMeetingDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.esMeetingDao = new EsMeetingDao();
        this.relationshipDao = new EsTopicRelationshipDao();
        this.curationDao = new EsTopicCurationDao();
        this.commentDao = new EsCommentDao();
        this.topicSupporterDao = new EsTopicSupporterDao();
        this.supporterDao = new SupporterDao();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
        this.topicViewHistoryService = new EsTopicViewHistoryService();
        this.topicFollowerManagementService = new TopicFollowerManagementService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();

        ParsedPath parsed = parseTopicIdAndView(request.getPathInfo());
        if (parsed == null) {
            response.sendRedirect(contextPath + "/es/topics");
            return;
        }
        Long topicId = parsed.topicId();

        Optional<EsCampaignTopicBrowseRow> topicOpt = esTopicDao.findActiveById(topicId);
        if (topicOpt.isEmpty()) {
            renderNotFound(request, response, contextPath, topicId);
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
            renderNotFound(request, response, contextPath, topicId);
            return;
        }

        boolean isAdmin = authFlowService.isAdminUser(viewer);
        boolean isSpaceAdmin = topicSpaceAccessService.canAdministerSpace(viewer, topicEntity.getEsTopicSpaceId());
        List<EsSubscription> topicSubscriptions = subscriptionDao.findActiveByTopicId(topicId);
        boolean isChampionOrSupport = TopicFollowerManagementService.isChampionOrSupportForTopic(viewer,
                topicSubscriptions);
        boolean canManage = TopicFollowerManagementService.canManageFollowers(isAdmin, isSpaceAdmin,
                isChampionOrSupport);
        if (!canManage) {
            response.sendRedirect(contextPath + "/es/topic/" + topicId);
            return;
        }

        TopicManageView view = TopicManageView.fromSlug(parsed.viewSegment());
        if (view == null || (view == TopicManageView.SUPPORTERS && !isAdmin)) {
            response.sendRedirect(contextPath + "/es/topic-manage/" + topicId + "/" + TopicManageView.FOLLOWERS.slug);
            return;
        }

        try {
            topicViewHistoryService.recordAuthenticatedTopicView(viewer.getUserId(), topicId);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Unable to record topic view for user/topic", ex);
        }

        EsTopicSpace topicSpace = topicEntity.getEsTopicSpaceId() != null
                ? topicSpaceDao.findById(topicEntity.getEsTopicSpaceId()).orElse(null)
                : null;

        EsTopicMeeting topicMeetingSeries = esTopicMeetingDao.findByTopicId(topicId).orElse(null);

        Long editCurationId = null;
        String editCurationStr = request.getParameter("editCuration");
        if (editCurationStr != null && !editCurationStr.isBlank()) {
            try {
                editCurationId = Long.parseLong(editCurationStr.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        List<EsTopicViewHistoryService.RecentlyViewedTopic> recentlyViewedTopics = RecentlyViewedTopicsRenderer
                .fetchVisible(topicViewHistoryService, topicSpaceAccessService, viewer);

        // Topics this viewer can manage (champion/support anywhere, or admin
        // everywhere) so the "Recently Viewed Topics" rail can keep them inside
        // the manage flow, on the same view, instead of dropping to the topic page.
        Set<Long> manageableTopicIds = subscriptionDao
                .findByUserIdAndType(viewer.getUserId(), EsSubscription.SubscriptionType.TOPIC)
                .stream()
                .filter(s -> isChampionEquivalentStatus(s.getStatus()))
                .map(EsSubscription::getEsTopicId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        AiraPage page = InteropAiraPageFactory.base(request, "Manage " + orEmpty(topic.getTopicName()) + " - InteropHub")
                .applicationSubtitle("Topic management")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.topicsMeetingsContext(
                        topicSpace != null ? topicSpace.getSpaceName() : "InteropHub",
                        topicSpace != null ? topicSpace.getSpaceCode() : null,
                        true,
                        false))
                .build();

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container--wide aira-stack\">");

            out.println("      <div class=\"aira-right-rail-layout\">");
            out.println("        <div class=\"aira-stack\">");

            out.println("          <h1 class=\"aira-page-title\">Manage: " + escapeHtml(orEmpty(topic.getTopicName()))
                    + "</h1>");

            switch (view) {
                case FOLLOWERS -> renderFollowersView(out, contextPath, request, topicId, topicSubscriptions,
                        isAdmin);
                case MEETINGS -> renderMeetingsView(out, contextPath, topicId, viewer, topicMeetingSeries);
                case COMMENTS -> renderCommentsView(out, topicId);
                case RELATIONSHIPS -> renderRelationshipsView(out, contextPath, topicId, viewer);
                case CURATED -> renderCuratedView(out, contextPath, topicId, viewer, editCurationId);
                case SUPPORTERS -> renderSupportersView(out, contextPath, request, topicId);
            }

            out.println("        </div>"); // end main content aira-stack

            TopicManageNavRenderer.TopicManageCounts counts = TopicManageNavRenderer.computeCounts(topicId, viewer,
                    subscriptionDao, agendaItemDao, esMeetingDao, commentDao, relationshipDao, curationDao,
                    topicSupporterDao, topicSpaceAccessService);
            renderRightRail(out, contextPath, topicId, view, isAdmin, isSpaceAdmin, topicMeetingSeries, recentlyViewedTopics,
                    manageableTopicIds, counts, isAdmin || isChampionOrSupport);

            out.println("      </div>"); // end aira-right-rail-layout
            out.println("    </div>"); // end aira-container--wide aira-stack
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    // -------------------------------------------------------------------------
    // Right rail: recently viewed topics + management nav menu
    // -------------------------------------------------------------------------

    private void renderRightRail(PrintWriter out, String contextPath, Long topicId, TopicManageView activeView,
            boolean isAdmin, boolean isSpaceAdmin, EsTopicMeeting topicMeetingSeries,
            List<EsTopicViewHistoryService.RecentlyViewedTopic> recentlyViewedTopics,
            Set<Long> manageableTopicIds, TopicManageNavRenderer.TopicManageCounts counts, boolean canManageStarterPackets) {
        out.println("        <aside class=\"aira-right-rail\" aria-label=\"Topic management\">");

        // Keep managers inside the manage flow, on the same view, when jumping
        // between topics they can manage; regular followers still land on the
        // plain topic page.
        RecentlyViewedTopicsRenderer.render(out, topicId, recentlyViewedTopics, true,
                otherTopicId -> (isAdmin || manageableTopicIds.contains(otherTopicId))
                        ? contextPath + "/es/topic-manage/" + otherTopicId + "/" + activeView.slug
                        : contextPath + "/es/topic/" + otherTopicId);

        TopicManageNavRenderer.render(out, contextPath, topicId, activeView, isAdmin,
                topicMeetingSeries != null ? topicMeetingSeries.getEsTopicMeetingId() : null, true, counts,
                isSpaceAdmin, canManageStarterPackets);

        out.println("        </aside>");
    }

    // -------------------------------------------------------------------------
    // View: Followers
    // -------------------------------------------------------------------------

    private void renderFollowersView(PrintWriter out, String contextPath, HttpServletRequest request, Long topicId,
            List<EsSubscription> subscriptions, boolean isAdmin) {
        List<TopicFollowerManagementService.FollowerRow> rows = topicFollowerManagementService
                .listFollowerRows(subscriptions);

        String error = trimToNull(request.getParameter("error"));

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Followers (" + rows.size() + ")</h2>");
        if (error != null) {
            out.println(
                    "            <div class=\"aira-alert aira-alert--danger\"><p>" + escapeHtml(error) + "</p></div>");
        }
        if (rows.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No followers yet.</p>");
        } else {
            out.println("            <div class=\"aira-table-wrap\">");
            out.println("            <table class=\"aira-table\">");
            out.println("              <thead><tr>");
            out.println("                <th>Name</th>");
            out.println("                <th>Organization</th>");
            out.println("                <th>Email</th>");
            out.println("                <th>Status</th>");
            out.println("                <th>Role</th>");
            out.println("              </tr></thead>");
            out.println("              <tbody>");
            for (TopicFollowerManagementService.FollowerRow row : rows) {
                EsSubscription s = row.subscription();
                String org = row.organization();
                String email = orEmpty(s.getEmail());
                TopicFollowerManagementService.FollowerStatus status = row.status();

                out.println("                <tr>");
                out.println("                  <td>");
                if (row.hasDisplayName()) {
                    out.println("                    " + escapeHtml(row.displayName()));
                } else {
                    renderAddNameForm(out, contextPath, topicId, s.getEsSubscriptionId());
                }
                out.println("                  </td>");
                out.println("                  <td>" + escapeHtml(org) + "</td>");
                out.println("                  <td>" + escapeHtml(email) + "</td>");
                out.println("                  <td>");
                if (status == TopicFollowerManagementService.FollowerStatus.REGISTERED) {
                    out.println("                    <span class=\"aira-badge aira-badge--success\">Registered</span>");
                } else {
                    renderInviteAction(out, contextPath, topicId, s, status, isAdmin);
                }
                out.println("                  </td>");
                out.println("                  <td>");
                renderRoleSelect(out, contextPath, topicId, s, email);
                out.println("                  </td>");
                out.println("                </tr>");
            }
            out.println("              </tbody>");
            out.println("            </table>");
            out.println("            </div>");
        }

        renderAddFollowerForm(out, contextPath, topicId);

        out.println("          </section>");

        renderFollowerAdminTools(out, contextPath, topicId, rows.size());
    }

    /**
     * "Admin Tools" section below the Followers panel: a grid of cards, one
     * per tool. Add future tools (e.g. upload a list) as another card method
     * here plus a matching action in {@link EsTopicFollowerManageServlet}.
     */
    private void renderFollowerAdminTools(PrintWriter out, String contextPath, Long topicId, int followerCount) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Admin Tools</h2>");
        out.println("            <div class=\"aira-card-grid\">");
        renderDownloadFollowersCard(out, contextPath, topicId, followerCount);
        out.println("            </div>");
        out.println("          </section>");
    }

    private void renderDownloadFollowersCard(PrintWriter out, String contextPath, Long topicId, int followerCount) {
        out.println("              <div class=\"aira-card\">");
        out.println("                <div class=\"aira-card__body aira-stack\">");
        out.println("                  <h3 class=\"aira-section-title\">Download followers</h3>");
        out.println("                  <p class=\"aira-meta\">A CSV file of everyone listed above, with names,"
                + " organizations, emails, status, and role. People who have opted out of InteropHub email"
                + " are included and flagged in an &ldquo;Opted Out of Email&rdquo; column.</p>");
        out.println("                  <div class=\"aira-action-group\">");
        if (followerCount == 0) {
            out.println("                    <span class=\"aira-meta\">No followers to download yet.</span>");
        } else {
            out.println("                    <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                    + "/es/topics/followers-manage?action=exportCsv&amp;topicId=" + topicId
                    + "\" download>Download CSV (" + followerCount + ")</a>");
        }
        out.println("                  </div>");
        out.println("                </div>");
        out.println("              </div>");
    }

    private void renderRoleSelect(PrintWriter out, String contextPath, Long topicId, EsSubscription s,
            String email) {
        out.println("                    <form class=\"aira-inline-form\" method=\"post\" action=\""
                + contextPath + "/es/topics/subscription-role\">");
        out.println("                      <input type=\"hidden\" name=\"subscriptionId\" value=\""
                + s.getEsSubscriptionId() + "\" />");
        out.println("                      <input type=\"hidden\" name=\"topicId\" value=\"" + topicId + "\" />");
        out.println("                      <label class=\"aira-visually-hidden\" for=\"follower-role-"
                + s.getEsSubscriptionId() + "\">Role for " + escapeHtml(email) + "</label>");
        out.println("                      <select class=\"aira-select\" id=\"follower-role-"
                + s.getEsSubscriptionId() + "\" name=\"status\" onchange=\"this.form.submit()\">");
        out.println("                        <option value=\"SUBSCRIBED\""
                + (s.getStatus() == EsSubscription.SubscriptionStatus.SUBSCRIBED ? " selected" : "")
                + ">Follower</option>");
        out.println("                        <option value=\"CHAMPION\""
                + (s.getStatus() == EsSubscription.SubscriptionStatus.CHAMPION ? " selected" : "")
                + ">Champion</option>");
        out.println("                        <option value=\"SUPPORT\""
                + (s.getStatus() == EsSubscription.SubscriptionStatus.SUPPORT ? " selected" : "")
                + ">Support</option>");
        out.println("                        <option value=\"UNSUBSCRIBED\">Unfollow</option>");
        out.println("                      </select>");
        out.println(
                "                      <noscript><button class=\"aira-button aira-button--small aira-button--secondary\" type=\"submit\">Save</button></noscript>");
        out.println("                    </form>");
    }

    private void renderInviteAction(PrintWriter out, String contextPath, Long topicId, EsSubscription s,
            TopicFollowerManagementService.FollowerStatus status, boolean isAdmin) {
        boolean isVerify = status == TopicFollowerManagementService.FollowerStatus.UNVERIFIED;
        String action = isVerify ? "inviteVerify" : "inviteRegistration";
        String reasonCode = isVerify ? EmailReason.TOPIC_FOLLOWER_VERIFY_EMAIL
                : EmailReason.TOPIC_FOLLOWER_REGISTRATION_INVITE;
        String title = isVerify ? "Send a verification email" : "Send a registration invite";

        LocalDateTime lastSent = topicFollowerManagementService
                .lastSentAt(s.getEmailNormalized(), reasonCode)
                .orElse(null);
        boolean cooldown = !isAdmin && TopicFollowerManagementService.isWithinCooldown(
                lastSent, LocalDateTime.now(), TopicFollowerManagementService.RESEND_COOLDOWN_DAYS);

        if (lastSent != null) {
            out.println("                    <div class=\"aira-meta\">Last sent "
                    + escapeHtml(INVITE_DATE_FMT.format(lastSent)) + "</div>");
        }
        out.println("                    <form method=\"post\" action=\"" + contextPath
                + "/es/topics/followers-manage\" style=\"display:inline\">");
        out.println("                      <input type=\"hidden\" name=\"action\" value=\"" + action + "\" />");
        out.println("                      <input type=\"hidden\" name=\"subscriptionId\" value=\""
                + s.getEsSubscriptionId() + "\" />");
        out.println("                      <input type=\"hidden\" name=\"topicId\" value=\"" + topicId + "\" />");
        if (cooldown) {
            out.println("                      <input type=\"hidden\" name=\"confirm\" value=\"1\" />");
        }
        out.println("                      <button class=\"aira-button aira-button--secondary aira-button--small\" type=\"submit\" title=\""
                + escapeHtml(title) + "\">" + escapeHtml(cooldown ? "Send Again" : "Send Invite") + "</button>");
        out.println("                    </form>");
    }

    private void renderAddNameForm(PrintWriter out, String contextPath, Long topicId, Long subscriptionId) {
        out.println("                    <details>");
        out.println("                      <summary>+ Add name</summary>");
        out.println("                      <form class=\"aira-inline-form\" method=\"post\" action=\""
                + contextPath + "/es/topics/followers-manage\">");
        out.println("                        <input type=\"hidden\" name=\"action\" value=\"updateContact\" />");
        out.println("                        <input type=\"hidden\" name=\"subscriptionId\" value=\""
                + subscriptionId + "\" />");
        out.println("                        <input type=\"hidden\" name=\"topicId\" value=\"" + topicId + "\" />");
        out.println(
                "                        <input class=\"aira-input\" type=\"text\" name=\"firstName\" maxlength=\"100\" placeholder=\"First name\" />");
        out.println(
                "                        <input class=\"aira-input\" type=\"text\" name=\"lastName\" maxlength=\"100\" placeholder=\"Last name\" />");
        out.println(
                "                        <input class=\"aira-input\" type=\"text\" name=\"organization\" maxlength=\"200\" placeholder=\"Organization\" />");
        out.println(
                "                        <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"submit\">Save</button>");
        out.println("                      </form>");
        out.println("                    </details>");
    }

    private void renderAddFollowerForm(PrintWriter out, String contextPath, Long topicId) {
        out.println("            <details>");
        out.println("              <summary>+ Add a follower</summary>");
        out.println("              <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                + "/es/topics/followers-manage\">");
        out.println("                <input type=\"hidden\" name=\"action\" value=\"add\">");
        out.println("                <input type=\"hidden\" name=\"topicId\" value=\"" + topicId + "\">");
        out.println("                <div class=\"aira-field-row\">");
        out.println("                  <div class=\"aira-field\">");
        out.println("                    <label>Email *</label>");
        out.println(
                "                    <input class=\"aira-input\" type=\"email\" name=\"email\" maxlength=\"254\" required>");
        out.println("                  </div>");
        out.println("                  <div class=\"aira-field\">");
        out.println("                    <label>First Name</label>");
        out.println("                    <input class=\"aira-input\" type=\"text\" name=\"firstName\" maxlength=\"100\">");
        out.println("                  </div>");
        out.println("                  <div class=\"aira-field\">");
        out.println("                    <label>Last Name</label>");
        out.println("                    <input class=\"aira-input\" type=\"text\" name=\"lastName\" maxlength=\"100\">");
        out.println("                  </div>");
        out.println("                  <div class=\"aira-field\">");
        out.println("                    <label>Organization</label>");
        out.println(
                "                    <input class=\"aira-input\" type=\"text\" name=\"organization\" maxlength=\"200\">");
        out.println("                  </div>");
        out.println("                </div>");
        out.println("                <div class=\"aira-field\">");
        out.println("                  <label>Reason / context</label>");
        out.println(
                "                  <textarea class=\"aira-textarea\" name=\"reason\" rows=\"2\" placeholder=\"e.g. requested by email on 9/12\"></textarea>");
        out.println("                </div>");
        out.println(
                "                <label class=\"aira-radio\"><input type=\"checkbox\" name=\"sendNotification\" checked> Send them an email now</label>");
        out.println("                <div class=\"aira-action-group\">");
        out.println(
                "                  <button class=\"aira-button aira-button--primary\" type=\"submit\">Add Follower</button>");
        out.println("                </div>");
        out.println("              </form>");
        out.println("            </details>");
    }

    // -------------------------------------------------------------------------
    // View: Meetings
    // -------------------------------------------------------------------------

    private void renderMeetingsView(PrintWriter out, String contextPath, Long topicId, User viewer,
            EsTopicMeeting series) {
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

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Meetings</h2>");

        if (series != null) {
            out.println("            <div class=\"aira-action-group\">");
            out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                    + "/es/meeting-series?seriesId=" + series.getEsTopicMeetingId() + "\">View meeting series: "
                    + escapeHtml(orEmpty(series.getMeetingName())) + " &rarr;</a>");
            out.println("            </div>");
        }

        out.println("            <h3 class=\"aira-section-title\">Meeting Appearances (" + topicAgendaMeetings.size()
                + ")</h3>");
        if (topicAgendaMeetings.isEmpty()) {
            out.println(
                    "            <p class=\"aira-meta\">This topic has not appeared on any meeting agendas yet.</p>");
        } else {
            out.println("            <div class=\"aira-table-wrap\">");
            out.println("            <table class=\"aira-table\">");
            out.println("              <thead><tr>");
            out.println("                <th>Date</th>");
            out.println("                <th>Meeting</th>");
            out.println("                <th>Status</th>");
            out.println("              </tr></thead>");
            out.println("              <tbody>");
            for (EsMeeting m : topicAgendaMeetings) {
                String dateStr = m.getScheduledStart() != null
                        ? m.getScheduledStart().format(MEETING_DATE_FMT)
                        : "";
                String statusStr = m.getStatus() != null ? m.getStatus().name() : "";
                out.println("                <tr>");
                out.println("                  <td>" + escapeHtml(dateStr) + "</td>");
                out.println("                  <td><a href=\"" + contextPath + "/es/agenda?meetingId="
                        + m.getEsMeetingId() + "\">" + escapeHtml(orEmpty(m.getMeetingName())) + "</a></td>");
                out.println("                  <td>" + escapeHtml(statusStr) + "</td>");
                out.println("                </tr>");
            }
            out.println("              </tbody>");
            out.println("            </table>");
            out.println("            </div>");
        }
        out.println("          </section>");
    }

    // -------------------------------------------------------------------------
    // View: Comments
    // -------------------------------------------------------------------------

    private void renderCommentsView(PrintWriter out, Long topicId) {
        List<EsComment> topicComments = commentDao.findByTopicId(topicId);

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Campaign Comments (" + topicComments.size()
                + ")</h2>");
        if (topicComments.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No campaign comments yet.</p>");
        } else {
            out.println("            <div class=\"aira-table-wrap\">");
            out.println("            <table class=\"aira-table\">");
            out.println("              <thead><tr>");
            out.println("                <th>Date</th>");
            out.println("                <th>Submitted by</th>");
            out.println("                <th>Comment</th>");
            out.println("              </tr></thead>");
            out.println("              <tbody>");
            for (EsComment c : topicComments) {
                String dateStr = c.getCreatedAt() != null ? c.getCreatedAt().toLocalDate().toString() : "";
                String name = (orEmpty(c.getFirstName()) + " " + orEmpty(c.getLastName())).trim();
                out.println("                <tr>");
                out.println("                  <td>" + escapeHtml(dateStr) + "</td>");
                out.println("                  <td>" + escapeHtml(name) + "</td>");
                out.println("                  <td>" + escapeHtml(orEmpty(c.getCommentText())) + "</td>");
                out.println("                </tr>");
            }
            out.println("              </tbody>");
            out.println("            </table>");
            out.println("            </div>");
        }
        out.println("          </section>");
    }

    // -------------------------------------------------------------------------
    // View: Relationships
    // -------------------------------------------------------------------------

    private void renderRelationshipsView(PrintWriter out, String contextPath, Long topicId, User viewer) {
        List<EsTopicRelationship> outboundRels = topicSpaceAccessService
                .filterVisibleRelationships(viewer, relationshipDao.findByFromTopicId(topicId));
        List<EsTopic> allTopics = topicSpaceAccessService.filterVisibleTopics(viewer,
                esTopicDao.findAllOrderByTopicName());
        Map<Long, String> topicNameMap = buildTopicNameMap(allTopics);

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Relationships</h2>");

        if (outboundRels.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No outgoing relationships defined yet.</p>");
        } else {
            out.println("            <div class=\"aira-table-wrap\">");
            out.println("            <table class=\"aira-table\">");
            out.println("              <thead><tr>");
            out.println("                <th>Type</th>");
            out.println("                <th>Topic</th>");
            out.println("                <th></th>");
            out.println("              </tr></thead>");
            out.println("              <tbody>");
            for (EsTopicRelationship rel : outboundRels) {
                String label = rel.getRelationshipType() != null ? rel.getRelationshipType().getLabel()
                        : "related to";
                String name = topicNameMap.getOrDefault(rel.getToTopicId(), "#" + rel.getToTopicId());
                out.println("                <tr>");
                out.println("                  <td>" + escapeHtml(label) + "</td>");
                out.println("                  <td><a href=\"" + contextPath + "/es/topic/" + rel.getToTopicId()
                        + "\">" + escapeHtml(name) + "</a></td>");
                out.println("                  <td>");
                out.println("                    <form method=\"post\" action=\"" + contextPath
                        + "/es/topics/relationship\" style=\"display:inline\">");
                out.println("                      <input type=\"hidden\" name=\"action\" value=\"delete\">");
                out.println("                      <input type=\"hidden\" name=\"relationshipId\" value=\""
                        + rel.getEsTopicRelationshipId() + "\">");
                out.println("                      <input type=\"hidden\" name=\"fromTopicId\" value=\"" + topicId
                        + "\">");
                out.println("                      <input type=\"hidden\" name=\"returnTo\" value=\"manage\">");
                out.println(
                        "                      <button class=\"aira-button aira-button--danger aira-button--small\" type=\"submit\">Remove</button>");
                out.println("                    </form>");
                out.println("                  </td>");
                out.println("                </tr>");
            }
            out.println("              </tbody>");
            out.println("            </table>");
            out.println("            </div>");
        }

        out.println("            <form class=\"aira-inline-form\" method=\"post\" action=\"" + contextPath
                + "/es/topics/relationship\">");
        out.println("              <input type=\"hidden\" name=\"action\" value=\"add\">");
        out.println("              <input type=\"hidden\" name=\"fromTopicId\" value=\"" + topicId + "\">");
        out.println("              <input type=\"hidden\" name=\"returnTo\" value=\"manage\">");
        out.println("              <div class=\"aira-field\">");
        out.println("                <label>Relationship</label>");
        out.println("                <select class=\"aira-select\" name=\"relationshipType\">");
        for (EsTopicRelationship.RelationshipType type : EsTopicRelationship.RelationshipType.values()) {
            boolean isDefault = type == EsTopicRelationship.RelationshipType.RELATED_TO;
            out.println("                  <option value=\"" + type.name() + "\"" + (isDefault ? " selected" : "")
                    + ">" + escapeHtml(type.getLabel()) + "</option>");
        }
        out.println("                </select>");
        out.println("              </div>");
        out.println("              <div class=\"aira-field\">");
        out.println("                <label>Topic</label>");
        out.println("                <select class=\"aira-select\" name=\"toTopicId\">");
        for (EsTopic t : allTopics) {
            if (!t.getEsTopicId().equals(topicId)) {
                out.println("                  <option value=\"" + t.getEsTopicId() + "\">"
                        + escapeHtml(t.getTopicName()) + "</option>");
            }
        }
        out.println("                </select>");
        out.println("              </div>");
        out.println(
                "              <button class=\"aira-button aira-button--primary\" type=\"submit\">Add Link</button>");
        out.println("            </form>");
        out.println("          </section>");
    }

    // -------------------------------------------------------------------------
    // View: Curated Topics
    // -------------------------------------------------------------------------

    private void renderCuratedView(PrintWriter out, String contextPath, Long topicId, User viewer,
            Long editCurationId) {
        List<EsTopicCuration> curatedEntries = topicSpaceAccessService
                .filterVisibleCurations(viewer, curationDao.findByCuratorTopicId(topicId));
        List<EsTopic> allTopics = topicSpaceAccessService.filterVisibleTopics(viewer,
                esTopicDao.findAllOrderByTopicName());
        Map<Long, String> topicNameMap = buildTopicNameMap(allTopics);
        List<String> existingCurationStatuses = curatedEntries.isEmpty() ? List.of()
                : curationDao.findDistinctCurationStatuses(topicId);
        String selfPageUrl = contextPath + "/es/topic-manage/" + topicId + "/" + TopicManageView.CURATED.slug;

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Curated Topics</h2>");

        if (curatedEntries.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No topics in curated list yet.</p>");
        } else {
            out.println("            <div class=\"aira-table-wrap\">");
            out.println("            <table class=\"aira-table\">");
            out.println("              <thead><tr>");
            out.println("                <th>Topic</th>");
            out.println("                <th>Alias</th>");
            out.println("                <th>Category</th>");
            out.println("                <th>Status</th>");
            out.println("                <th>Order</th>");
            out.println("                <th></th>");
            out.println("              </tr></thead>");
            out.println("              <tbody>");
            for (EsTopicCuration entry : curatedEntries) {
                String name = topicNameMap.getOrDefault(entry.getCuratedTopicId(),
                        "#" + entry.getCuratedTopicId());
                boolean isEditing = entry.getEsTopicCurationId().equals(editCurationId);
                if (isEditing) {
                    renderCuratedEditRow(out, contextPath, topicId, name, entry, selfPageUrl);
                } else {
                    out.println("                <tr>");
                    out.println("                  <td><a href=\"" + selfPageUrl + "?editCuration="
                            + entry.getEsTopicCurationId() + "#edit-curation\">" + escapeHtml(name) + "</a></td>");
                    out.println("                  <td>" + escapeHtml(orEmpty(entry.getTopicAlias())) + "</td>");
                    out.println("                  <td>" + escapeHtml(orEmpty(entry.getCategoryLabel())) + "</td>");
                    out.println("                  <td>" + escapeHtml(orEmpty(entry.getCurationStatus())) + "</td>");
                    out.println("                  <td>" + orEmpty(entry.getDisplayOrder() == null ? null
                            : String.valueOf(entry.getDisplayOrder())) + "</td>");
                    out.println("                  <td>");
                    out.println("                    <form method=\"post\" action=\"" + contextPath
                            + "/es/topics/curation\" style=\"display:inline\">");
                    out.println("                      <input type=\"hidden\" name=\"action\" value=\"delete\">");
                    out.println("                      <input type=\"hidden\" name=\"curationId\" value=\""
                            + entry.getEsTopicCurationId() + "\">");
                    out.println("                      <input type=\"hidden\" name=\"curatorTopicId\" value=\""
                            + topicId + "\">");
                    out.println("                      <input type=\"hidden\" name=\"returnTo\" value=\"manage\">");
                    out.println(
                            "                      <button class=\"aira-button aira-button--danger aira-button--small\" type=\"submit\">Remove</button>");
                    out.println("                    </form>");
                    out.println("                  </td>");
                    out.println("                </tr>");
                }
            }
            out.println("              </tbody>");
            out.println("            </table>");
            out.println("            </div>");
        }

        out.println("            <details>");
        out.println("              <summary>+ Add to curated list</summary>");
        out.println("              <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                + "/es/topics/curation\">");
        out.println("                <input type=\"hidden\" name=\"action\" value=\"add\">");
        out.println("                <input type=\"hidden\" name=\"curatorTopicId\" value=\"" + topicId + "\">");
        out.println("                <input type=\"hidden\" name=\"returnTo\" value=\"manage\">");
        out.println("                <div class=\"aira-field-row\">");
        out.println("                  <div class=\"aira-field\">");
        out.println("                    <label>Topic *</label>");
        out.println("                    <select class=\"aira-select\" name=\"curatedTopicId\">");
        for (EsTopic t : allTopics) {
            if (!t.getEsTopicId().equals(topicId)) {
                out.println("                      <option value=\"" + t.getEsTopicId() + "\">"
                        + escapeHtml(t.getTopicName()) + "</option>");
            }
        }
        out.println("                    </select>");
        out.println("                  </div>");
        out.println("                  <div class=\"aira-field\">");
        out.println("                    <label>Alias</label>");
        out.println(
                "                    <input class=\"aira-input\" type=\"text\" name=\"topicAlias\" maxlength=\"140\" placeholder=\"Custom display name\">");
        out.println("                  </div>");
        out.println("                  <div class=\"aira-field\">");
        out.println("                    <label>Category</label>");
        out.println(
                "                    <input class=\"aira-input\" type=\"text\" name=\"categoryLabel\" maxlength=\"80\" placeholder=\"e.g. Core\">");
        out.println("                  </div>");
        out.println("                  <div class=\"aira-field\">");
        out.println("                    <label>Status</label>");
        out.println(
                "                    <input class=\"aira-input\" type=\"text\" name=\"curationStatus\" maxlength=\"80\" list=\"curation-status-list\" placeholder=\"e.g. Active\">");
        out.println("                    <datalist id=\"curation-status-list\">");
        for (String status : existingCurationStatuses) {
            out.println("                      <option value=\"" + escapeHtml(status) + "\">");
        }
        out.println("                    </datalist>");
        out.println("                  </div>");
        out.println("                  <div class=\"aira-field\">");
        out.println("                    <label>Order</label>");
        out.println(
                "                    <input class=\"aira-input\" type=\"number\" name=\"displayOrder\" value=\"0\" min=\"0\">");
        out.println("                  </div>");
        out.println("                  <div class=\"aira-field\">");
        out.println("                    <label>Agenda Cadence (days)</label>");
        out.println(
                "                    <input class=\"aira-input\" type=\"number\" name=\"agendaCadenceDays\" min=\"0\">");
        out.println("                  </div>");
        out.println("                </div>");
        out.println("                <div class=\"aira-field\">");
        out.println("                  <label>Editorial Note</label>");
        out.println("                  <textarea class=\"aira-textarea\" name=\"editorialNote\" rows=\"2\"></textarea>");
        out.println("                </div>");
        out.println("                <div class=\"aira-action-group\">");
        out.println(
                "                  <button class=\"aira-button aira-button--primary\" type=\"submit\">Add to Curated List</button>");
        out.println("                </div>");
        out.println("              </form>");
        out.println("            </details>");
        out.println("          </section>");
    }

    private void renderCuratedEditRow(PrintWriter out, String contextPath, Long topicId, String name,
            EsTopicCuration entry, String selfPageUrl) {
        out.println("                <tr id=\"edit-curation\">");
        out.println("                  <td colspan=\"6\">");
        out.println("                    <div class=\"aira-meta\">Editing: <strong>" + escapeHtml(name)
                + "</strong></div>");
        out.println("                    <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                + "/es/topics/curation\">");
        out.println("                      <input type=\"hidden\" name=\"action\" value=\"update\">");
        out.println("                      <input type=\"hidden\" name=\"curationId\" value=\""
                + entry.getEsTopicCurationId() + "\">");
        out.println("                      <input type=\"hidden\" name=\"curatorTopicId\" value=\"" + topicId
                + "\">");
        out.println("                      <input type=\"hidden\" name=\"returnTo\" value=\"manage\">");
        out.println("                      <div class=\"aira-field-row\">");
        out.println("                        <div class=\"aira-field\">");
        out.println("                          <label>Alias</label>");
        out.println("                          <input class=\"aira-input\" type=\"text\" name=\"topicAlias\" maxlength=\"140\" value=\""
                + escapeHtml(orEmpty(entry.getTopicAlias())) + "\">");
        out.println("                        </div>");
        out.println("                        <div class=\"aira-field\">");
        out.println("                          <label>Category</label>");
        out.println("                          <input class=\"aira-input\" type=\"text\" name=\"categoryLabel\" maxlength=\"80\" value=\""
                + escapeHtml(orEmpty(entry.getCategoryLabel())) + "\">");
        out.println("                        </div>");
        out.println("                        <div class=\"aira-field\">");
        out.println("                          <label>Status</label>");
        out.println(
                "                          <input class=\"aira-input\" type=\"text\" name=\"curationStatus\" maxlength=\"80\" list=\"curation-status-list\" value=\""
                        + escapeHtml(orEmpty(entry.getCurationStatus())) + "\">");
        out.println("                        </div>");
        out.println("                        <div class=\"aira-field\">");
        out.println("                          <label>Order</label>");
        out.println("                          <input class=\"aira-input\" type=\"number\" name=\"displayOrder\" min=\"0\" value=\""
                + (entry.getDisplayOrder() == null ? 0 : entry.getDisplayOrder()) + "\">");
        out.println("                        </div>");
        out.println("                        <div class=\"aira-field\">");
        out.println("                          <label>Agenda Cadence (days)</label>");
        out.println("                          <input class=\"aira-input\" type=\"number\" name=\"agendaCadenceDays\" min=\"0\" value=\""
                + (entry.getAgendaCadenceDays() == null ? "" : entry.getAgendaCadenceDays()) + "\">");
        out.println("                        </div>");
        out.println("                      </div>");
        out.println("                      <div class=\"aira-field\">");
        out.println("                        <label>Editorial Note</label>");
        out.println("                        <textarea class=\"aira-textarea\" name=\"editorialNote\" rows=\"2\">"
                + escapeHtml(orEmpty(entry.getEditorialNote())) + "</textarea>");
        out.println("                      </div>");
        out.println("                      <div class=\"aira-action-group\">");
        out.println(
                "                        <button class=\"aira-button aira-button--primary\" type=\"submit\">Save Changes</button>");
        out.println("                        <a class=\"aira-button aira-button--secondary\" href=\""
                + escapeHtml(selfPageUrl) + "\">Cancel</a>");
        out.println("                      </div>");
        out.println("                    </form>");
        out.println("                  </td>");
        out.println("                </tr>");
    }

    // -------------------------------------------------------------------------
    // View: Supporters (admin-only)
    // -------------------------------------------------------------------------

    private void renderSupportersView(PrintWriter out, String contextPath, HttpServletRequest request, Long topicId) {
        List<EsTopicSupporter> relationships = topicSupporterDao.findByTopicId(topicId);
        List<Long> supporterIds = relationships.stream()
                .map(EsTopicSupporter::getSupporterId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Supporter> supporterMap = supporterDao.findByIds(supporterIds).stream()
                .collect(Collectors.toMap(Supporter::getSupporterId, s -> s));

        List<EsTopicSupporter> sorted = new ArrayList<>(relationships);
        sorted.sort((a, b) -> {
            Supporter sa = supporterMap.get(a.getSupporterId());
            Supporter sb = supporterMap.get(b.getSupporterId());
            String nameA = sa != null ? orEmpty(sa.getShortName()) : "";
            String nameB = sb != null ? orEmpty(sb.getShortName()) : "";
            return nameA.compareToIgnoreCase(nameB);
        });

        String error = trimToNull(request.getParameter("error"));

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Supporters (" + sorted.size() + ")</h2>");
        if (error != null) {
            out.println(
                    "            <div class=\"aira-alert aira-alert--danger\"><p>" + escapeHtml(error) + "</p></div>");
        }
        if (sorted.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No Supporters associated with this topic yet.</p>");
        } else {
            out.println("            <div class=\"aira-table-wrap\">");
            out.println("            <table class=\"aira-table\">");
            out.println(
                    "              <thead><tr><th>Supporter</th><th>Status</th><th class=\"aira-table__cell--actions\">Actions</th></tr></thead>");
            out.println("              <tbody>");
            for (EsTopicSupporter rel : sorted) {
                Supporter supporter = supporterMap.get(rel.getSupporterId());
                String name = supporter != null ? orEmpty(supporter.getShortName()) : ("#" + rel.getSupporterId());
                boolean active = supporter != null && Boolean.TRUE.equals(supporter.getActive());
                out.println("                <tr>");
                out.println("                  <td>" + escapeHtml(name) + "</td>");
                out.println("                  <td>" + (active
                        ? "<span class=\"aira-badge aira-badge--success\">Active</span>"
                        : "<span class=\"aira-badge aira-badge--subtle\">Inactive</span>") + "</td>");
                out.println("                  <td class=\"aira-table__cell--actions\">");
                out.println("                    <form method=\"post\" action=\"" + contextPath
                        + "/es/topics/supporter\" style=\"display:inline\">");
                out.println("                      <input type=\"hidden\" name=\"action\" value=\"remove\">");
                out.println("                      <input type=\"hidden\" name=\"esTopicSupporterId\" value=\""
                        + rel.getEsTopicSupporterId() + "\">");
                out.println("                      <input type=\"hidden\" name=\"topicId\" value=\"" + topicId
                        + "\">");
                out.println(
                        "                      <button class=\"aira-button aira-button--danger aira-button--small\" type=\"submit\">Remove</button>");
                out.println("                    </form>");
                out.println("                  </td>");
                out.println("                </tr>");
            }
            out.println("              </tbody>");
            out.println("            </table>");
            out.println("            </div>");
        }

        List<Supporter> availableSupporters = supporterDao.findActiveExcludingTopic(topicId);
        out.println("            <h3 class=\"aira-subsection-title\">Add Existing Supporter</h3>");
        if (availableSupporters.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No other active Supporters are available to add.</p>");
        } else {
            out.println("            <form class=\"aira-inline-form\" method=\"post\" action=\"" + contextPath
                    + "/es/topics/supporter\">");
            out.println("              <input type=\"hidden\" name=\"action\" value=\"add\">");
            out.println("              <input type=\"hidden\" name=\"topicId\" value=\"" + topicId + "\">");
            out.println("              <div class=\"aira-field\">");
            out.println("                <label>Supporter</label>");
            out.println("                <select class=\"aira-select\" name=\"supporterId\">");
            for (Supporter s : availableSupporters) {
                out.println("                  <option value=\"" + s.getSupporterId() + "\">"
                        + escapeHtml(orEmpty(s.getShortName())) + "</option>");
            }
            out.println("                </select>");
            out.println("              </div>");
            out.println(
                    "              <button class=\"aira-button aira-button--primary\" type=\"submit\">Add Supporter</button>");
            out.println("            </form>");
        }

        out.println("            <details>");
        out.println("              <summary>+ Add New Supporter</summary>");
        out.println("              <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                + "/es/topics/supporter\">");
        out.println("                <input type=\"hidden\" name=\"action\" value=\"createAndAdd\">");
        out.println("                <input type=\"hidden\" name=\"topicId\" value=\"" + topicId + "\">");
        out.println("                <div class=\"aira-field\">");
        out.println("                  <label>Short Name *</label>");
        out.println(
                "                  <input class=\"aira-input\" type=\"text\" name=\"shortName\" maxlength=\"120\" required>");
        out.println("                </div>");
        out.println("                <div class=\"aira-field\">");
        out.println("                  <label>Full Name *</label>");
        out.println(
                "                  <input class=\"aira-input\" type=\"text\" name=\"fullName\" maxlength=\"200\" required>");
        out.println("                </div>");
        out.println("                <div class=\"aira-field\">");
        out.println("                  <label>Description</label>");
        out.println("                  <textarea class=\"aira-textarea\" name=\"description\" rows=\"2\"></textarea>");
        out.println("                </div>");
        out.println("                <div class=\"aira-field\">");
        out.println("                  <label>Website URL</label>");
        out.println(
                "                  <input class=\"aira-input\" type=\"url\" name=\"websiteUrl\" maxlength=\"500\">");
        out.println("                </div>");
        out.println(
                "                <label class=\"aira-radio\"><input type=\"checkbox\" name=\"active\" checked> Active</label>");
        out.println("                <div class=\"aira-action-group\">");
        out.println(
                "                  <button class=\"aira-button aira-button--primary\" type=\"submit\">Create and Add Supporter</button>");
        out.println("                </div>");
        out.println("              </form>");
        out.println("            </details>");
        out.println("          </section>");
    }

    private Map<Long, String> buildTopicNameMap(List<EsTopic> allTopics) {
        Map<Long, String> topicNameMap = new HashMap<>();
        for (EsTopic t : allTopics) {
            topicNameMap.put(t.getEsTopicId(), t.getTopicName());
        }
        return topicNameMap;
    }

    // -------------------------------------------------------------------------
    // Not found
    // -------------------------------------------------------------------------

    private void renderNotFound(HttpServletRequest request, HttpServletResponse response, String contextPath,
            Long topicId) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        AiraPage page = InteropAiraPageFactory.base(request, "Topic Not Found - InteropHub").build();
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <div class=\"aira-page-header\">");
            out.println("        <div>");
            out.println("          <h1 class=\"aira-page-title\">Topic Not Found</h1>");
            out.println("        </div>");
            out.println("      </div>");
            out.println(
                    "      <div class=\"aira-alert aira-alert--danger\"><p>This topic could not be found.</p></div>");
            out.println("      <p><a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                    + "/es/topics\">Back to Topics</a></p>");
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    // -------------------------------------------------------------------------
    // Path parsing
    // -------------------------------------------------------------------------

    private ParsedPath parseTopicIdAndView(String pathInfo) {
        if (pathInfo == null || pathInfo.length() <= 1) {
            return null;
        }
        String segment = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] parts = segment.split("/", -1);
        Long topicId;
        try {
            topicId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (topicId <= 0) {
            return null;
        }
        String viewSegment = parts.length > 1 ? trimToNull(parts[1]) : null;
        return new ParsedPath(topicId, viewSegment);
    }

    private record ParsedPath(Long topicId, String viewSegment) {
    }

    // -------------------------------------------------------------------------
    // Small utilities
    // -------------------------------------------------------------------------

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
