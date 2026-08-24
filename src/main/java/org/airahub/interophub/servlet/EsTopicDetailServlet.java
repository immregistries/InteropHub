package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignMeetingBrowseRow;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsTopicPathDefinitionDao;
import org.airahub.interophub.dao.EsTopicStageDefinitionDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.airahub.interophub.dao.EsAgendaItemPresenterDao;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsRecordedOutcomeDao;
import org.airahub.interophub.dao.EsTopicCurationDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.dao.EsTopicNoteDao;
import org.airahub.interophub.dao.EsTopicRelationshipDao;
import org.airahub.interophub.dao.EsTopicSupporterDao;
import org.airahub.interophub.dao.SupporterDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsAgendaItemPresenter;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsRecordedOutcome;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicCuration;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.EsTopicRelationship;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicPathDefinition;
import org.airahub.interophub.model.EsTopicStageDefinition;
import org.airahub.interophub.model.Supporter;
import org.immregistries.aira.web.AiraPage;
import org.airahub.interophub.service.EsTopicViewHistoryService;
import org.airahub.interophub.service.TopicNoteDocumentSupport;
import java.time.LocalDateTime;

public class EsTopicDetailServlet extends HttpServlet {

        private static final Logger LOGGER = Logger.getLogger(EsTopicDetailServlet.class.getName());

        private final AuthFlowService authFlowService;
        private final EsTopicDao esTopicDao;
        private final EsCampaignDao campaignDao;
        private final EsCampaignTopicDao campaignTopicDao;
        private final EsSubscriptionDao subscriptionDao;
        private final EsTopicMeetingDao esTopicMeetingDao;
        private final EsTopicMeetingMemberDao topicMeetingMemberDao;
        private final EsMeetingAgendaItemDao agendaItemDao;
        private final EsMeetingDao esMeetingDao;
        private final EsTopicSpaceDao topicSpaceDao;
        private final EsTopicStageDefinitionDao topicStageDefinitionDao;
        private final EsTopicPathDefinitionDao topicPathDefinitionDao;
        private final EsTopicRelationshipDao relationshipDao;
        private final EsTopicCurationDao curationDao;
        private final EsCommentDao commentDao;
        private final EsTopicSupporterDao topicSupporterDao;
        private final SupporterDao supporterDao;
        private final TopicSpaceAccessService topicSpaceAccessService;
        private final EsTopicViewHistoryService topicViewHistoryService;
        private final EsAgendaItemPresenterDao presenterDao;
        private final EsTopicNoteDao topicNoteDao;
        private final EsRecordedOutcomeDao recordedOutcomeDao;
        private final UserDao userDao;
        private final TopicNoteDocumentSupport topicNoteDocumentSupport;

        public EsTopicDetailServlet() {
                this.authFlowService = new AuthFlowService();
                this.esTopicDao = new EsTopicDao();
                this.campaignDao = new EsCampaignDao();
                this.campaignTopicDao = new EsCampaignTopicDao();
                this.subscriptionDao = new EsSubscriptionDao();
                this.esTopicMeetingDao = new EsTopicMeetingDao();
                this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
                this.agendaItemDao = new EsMeetingAgendaItemDao();
                this.esMeetingDao = new EsMeetingDao();
                this.topicSpaceDao = new EsTopicSpaceDao();
                this.topicStageDefinitionDao = new EsTopicStageDefinitionDao();
                this.topicPathDefinitionDao = new EsTopicPathDefinitionDao();
                this.relationshipDao = new EsTopicRelationshipDao();
                this.curationDao = new EsTopicCurationDao();
                this.commentDao = new EsCommentDao();
                this.topicSupporterDao = new EsTopicSupporterDao();
                this.supporterDao = new SupporterDao();
                this.topicSpaceAccessService = new TopicSpaceAccessService();
                this.topicViewHistoryService = new EsTopicViewHistoryService();
                this.presenterDao = new EsAgendaItemPresenterDao();
                this.topicNoteDao = new EsTopicNoteDao();
                this.recordedOutcomeDao = new EsRecordedOutcomeDao();
                this.userDao = new UserDao();
                this.topicNoteDocumentSupport = new TopicNoteDocumentSupport();
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

                Optional<EsCampaignTopicBrowseRow> topicOpt = esTopicDao.findActiveById(topicId);
                if (topicOpt.isEmpty()) {
                        renderNotFound(request, response, contextPath, topicId);
                        return;
                }
                EsCampaignTopicBrowseRow topic = topicOpt.get();

                Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
                User viewer = authenticatedUser.orElse(null);
                EsTopic topicEntity = esTopicDao.findById(topicId).orElse(null);
                if (topicEntity == null || !topicSpaceAccessService.canViewTopic(viewer, topicEntity)) {
                        renderNotFound(request, response, contextPath, topicId);
                        return;
                }
                EsTopicSpace topicSpace = topicSpaceDao.findById(topicEntity.getEsTopicSpaceId()).orElse(null);
                if (topicSpace == null || trimToNull(topicSpace.getSpaceCode()) == null) {
                        renderNotFound(request, response, contextPath, topicId);
                        return;
                }

                String authenticatedEmailNormalized = authenticatedUser
                                .map(User::getEmailNormalized)
                                .map(this::trimToNull)
                                .orElse(null);
                boolean canInteract = authenticatedUser.isPresent();
                Optional<EsCampaign> campaign = campaignDao.findMostRecentActive();
                boolean canReview = canInteract && campaign.isPresent();
                String campaignCode = campaign.map(EsCampaign::getCampaignCode).orElse(null);
                boolean followed = false;
                if (canInteract) {
                        Long userId = authenticatedUser.get().getUserId();
                        followed = subscriptionDao.findActiveTopicIdsByUserOrEmailAndTopicIds(
                                        userId,
                                        authenticatedEmailNormalized,
                                        List.of(topicId)).contains(topicId);
                }

                EsCampaignMeetingBrowseRow meeting = null;

                if (canInteract) {
                        // Meeting for this topic
                        List<EsCampaignMeetingBrowseRow> meetingRows = campaignTopicDao
                                        .findAllActiveMeetingRowsOrdered();
                        for (EsCampaignMeetingBrowseRow row : meetingRows) {
                                if (topicId.equals(row.getEsTopicId())) {
                                        meeting = row;
                                        break;
                                }
                        }

                }

                boolean isAdmin = authenticatedUser.isPresent() && authFlowService.isAdminUser(authenticatedUser.get());
                boolean canManageTopic = isAdmin;
                if (!canManageTopic && authenticatedUser.isPresent()) {
                        final Long curUserId = authenticatedUser.get().getUserId();
                        final String curEmail = authenticatedEmailNormalized;
                        canManageTopic = subscriptionDao.findActiveByTopicId(topicId).stream()
                                        .anyMatch(s -> isChampionEquivalentStatus(s.getStatus())
                                                        && ((s.getUserId() != null && curUserId.equals(s.getUserId()))
                                                                        || (curEmail != null
                                                                                        && curEmail.equals(s
                                                                                                        .getEmailNormalized()))));
                }

                String topicName = orEmpty(topic.getTopicName());
                String description = orEmpty(topic.getDescription());
                Long stageDefinitionId = topicEntity.getEsTopicStageDefinitionId();
                Optional<EsTopicStageDefinition> stageDefinition = stageDefinitionId == null
                                ? Optional.empty()
                                : topicStageDefinitionDao.findById(stageDefinitionId);
                String normalizedStage = orEmpty(stageDefinition.map(EsTopicStageDefinition::getStageName)
                                .map(this::trimToNull).orElse(null));
                String stageDescription = stageDefinition.map(EsTopicStageDefinition::getStageDescription)
                                .map(this::trimToNull)
                                .orElse(null);
                Long pathDefinitionId = topicEntity.getEsTopicPathDefinitionId();
                Optional<EsTopicPathDefinition> pathDefinition = pathDefinitionId == null
                                ? Optional.empty()
                                : topicPathDefinitionDao.findById(pathDefinitionId);
                String normalizedPath = orEmpty(pathDefinition.map(EsTopicPathDefinition::getPathName)
                                .map(this::trimToNull).orElse(null));
                String pathDescription = pathDefinition.map(EsTopicPathDefinition::getPathDescription)
                                .map(this::trimToNull)
                                .orElse(null);
                if (authenticatedUser.isPresent()) {
                        try {
                                topicViewHistoryService.recordAuthenticatedTopicView(authenticatedUser.get().getUserId(), topicId);
                        } catch (RuntimeException ex) {
                                LOGGER.log(Level.WARNING, "Unable to record topic view for user/topic", ex);
                        }
                }
                List<EsTopicViewHistoryService.RecentlyViewedTopic> recentlyViewedTopics = RecentlyViewedTopicsRenderer
                                .fetchVisible(topicViewHistoryService, topicSpaceAccessService, viewer);

                List<TopicMeetingAgendaRow> topicMeetingRows = buildTopicMeetingRows(topicId, viewer);
                Optional<EsTopicMeeting> ownMeetingSeriesOpt = esTopicMeetingDao.findByTopicId(topicId);
                boolean viewerRegisteredForOwnMeeting = false;
                if (canInteract && ownMeetingSeriesOpt.isPresent()) {
                        Optional<EsTopicMeetingMember> membership = topicMeetingMemberDao.findByMeetingIdAndUserOrEmail(
                                        ownMeetingSeriesOpt.get().getEsTopicMeetingId(), authenticatedUser.get().getUserId(),
                                        authenticatedEmailNormalized);
                        viewerRegisteredForOwnMeeting = membership.isPresent()
                                        && (membership.get().getMembershipStatus() == EsTopicMeetingMember.MembershipStatus.REQUESTED
                                                        || membership.get()
                                                                        .getMembershipStatus() == EsTopicMeetingMember.MembershipStatus.APPROVED);
                }
                List<RecentOutcomeRow> recentOutcomeRows = buildRecentOutcomeRows(topicId, viewer);

                List<EsTopicRelationship> outboundRels = topicSpaceAccessService
                                .filterVisibleRelationships(viewer, relationshipDao.findByFromTopicId(topicId));
                List<EsTopicRelationship> inboundRels = topicSpaceAccessService
                                .filterVisibleRelationships(viewer, relationshipDao.findByToTopicId(topicId));
                List<EsTopicCuration> curatedEntries = topicSpaceAccessService
                                .filterVisibleCurations(viewer, curationDao.findByCuratorTopicId(topicId));
                List<EsTopicCuration> curatedByEntries = topicSpaceAccessService
                                .filterVisibleCurations(viewer, curationDao.findByCuratedTopicId(topicId));

                boolean needsTopicData = !outboundRels.isEmpty() || !inboundRels.isEmpty()
                                || !curatedEntries.isEmpty() || !curatedByEntries.isEmpty()
                                || curatorTopicId != null;
                List<EsTopic> allTopics = List.of();
                Map<Long, String> topicNameMap = Map.of();
                if (needsTopicData) {
                        allTopics = topicSpaceAccessService.filterVisibleTopics(viewer,
                                        esTopicDao.findAllOrderByTopicName());
                        Map<Long, String> nameMap = new HashMap<>();
                        for (EsTopic t : allTopics) {
                                nameMap.put(t.getEsTopicId(), t.getTopicName());
                        }
                        topicNameMap = nameMap;
                }
                List<CuratedTopicRow> curatedTopicRows = buildCuratedTopicRows(topicId, curatedEntries, topicNameMap);

                CuratorNavContext curatorNav = null;
                if (curatorTopicId != null && !topicSpaceAccessService.canViewTopicId(viewer, curatorTopicId)) {
                        curatorTopicId = null;
                }
                if (curatorTopicId != null) {
                        List<EsTopicCuration> curatorList = topicSpaceAccessService
                                        .filterVisibleCurations(viewer,
                                                        curationDao.findByCuratorTopicId(curatorTopicId));
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
                                curatorNav = new CuratorNavContext(curatorTopicId, curatorName, prevEntry,
                                                currentDisplayName, nextEntry);
                        }
                }

                AiraPage page = InteropAiraPageFactory.base(request, topicName + " - InteropHub")
                                .applicationSubtitle("Topic display")
                                .mainClass("aira-main")
                                .addLocalStylesheet("/css/agenda.css")
                                .context(InteropAiraPageFactory.topicsMeetingsContext(
                                                topicSpace.getSpaceName(),
                                                topicSpace.getSpaceCode(),
                                                true,
                                                false))
                                .build();

                response.setContentType("text/html;charset=UTF-8");
                try (PrintWriter out = response.getWriter()) {
                        page.writeStart(out);
                        out.println("    <div class=\"aira-container--wide aira-stack\">");
                        out.println("      <div class=\"aira-right-rail-layout\">");
                        out.println("        <div class=\"aira-stack\">");

                        if (curatorNav != null) {
                                out.println("          <section class=\"aira-section-card\" aria-labelledby=\"curator-nav-title\">");
                                out.println(
                                                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"curator-nav-title\">Curated list navigation</h2></div>");
                                out.println("            <div class=\"aira-section-card__body aira-stack aira-stack--compact\">");
                                out.println("              <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                                + "/es/topic/"
                                                + curatorNav.curatorTopicId + "\">"
                                                + escapeHtml(curatorNav.curatorTopicName) + "</a></p>");
                                out.println("              <div class=\"aira-cluster aira-cluster--between\">");
                                if (curatorNav.prevEntry != null) {
                                        String prevName = (curatorNav.prevEntry.getTopicAlias() != null
                                                        && !curatorNav.prevEntry.getTopicAlias().isBlank())
                                                                        ? curatorNav.prevEntry.getTopicAlias()
                                                                        : topicNameMap.getOrDefault(
                                                                                        curatorNav.prevEntry
                                                                                                        .getCuratedTopicId(),
                                                                                        "#" + curatorNav.prevEntry
                                                                                                        .getCuratedTopicId());
                                        out.println(
                                                        "                <a class=\"aira-button aira-button--tertiary aira-button--small\" href=\""
                                                                        + contextPath + "/es/topic/"
                                                                        + curatorNav.prevEntry.getCuratedTopicId()
                                                                        + "?curator=" + curatorNav.curatorTopicId
                                                                        + "\">\u2190 "
                                                                        + escapeHtml(prevName) + "</a>");
                                } else {
                                        out.println("                <span class=\"aira-meta\">No previous topic</span>");
                                }
                                out.println("                <span class=\"aira-badge aira-badge--subtle\">"
                                                + escapeHtml(curatorNav.currentDisplayName) + "</span>");
                                if (curatorNav.nextEntry != null) {
                                        String nextName = (curatorNav.nextEntry.getTopicAlias() != null
                                                        && !curatorNav.nextEntry.getTopicAlias().isBlank())
                                                                        ? curatorNav.nextEntry.getTopicAlias()
                                                                        : topicNameMap.getOrDefault(
                                                                                        curatorNav.nextEntry
                                                                                                        .getCuratedTopicId(),
                                                                                        "#" + curatorNav.nextEntry
                                                                                                        .getCuratedTopicId());
                                        out.println(
                                                        "                <a class=\"aira-button aira-button--tertiary aira-button--small\" href=\""
                                                                        + contextPath + "/es/topic/"
                                                                        + curatorNav.nextEntry.getCuratedTopicId()
                                                                        + "?curator="
                                                                        + curatorNav.curatorTopicId + "\">"
                                                                        + escapeHtml(nextName) + " \u2192</a>");
                                } else {
                                        out.println("                <span class=\"aira-meta\">No next topic</span>");
                                }
                                out.println("              </div>");
                                out.println("            </div>");
                                out.println("          </section>");
                        }

                        out.println("          <header class=\"aira-topic-header\">");
                        out.println("            <div class=\"aira-topic-header__top\">");
                        out.println("              <div>");
                        out.println("                <h1 class=\"aira-topic-title\">" + escapeHtml(topicName)
                                        + "</h1>");
                        String topicSummaryText = trimToNull(topic.getTopicSummary());
                        if (topicSummaryText == null) {
                                topicSummaryText = description.isBlank() ? buildTopicSummary(topic, normalizedStage)
                                                : description;
                        }
                        out.println("                <p class=\"aira-topic-summary\">"
                                        + escapeHtml(topicSummaryText)
                                        + "</p>");
                        out.println("              </div>");
                        out.println("              <div class=\"aira-topic-actions\">");
                        if (canInteract) {
                                out.println(
                                                "                <span class=\"aira-badge aira-badge--success\" id=\"topic-follow-status\" style=\""
                                                                + (followed ? "" : "display:none") + "\">&#10003; Following</span>");
                                String followClass = followed ? "aira-button aira-button--tertiary"
                                                : "aira-button aira-button--primary";
                                String followLabel = followed ? "Unfollow" : "Follow";
                                out.println("                <button type=\"button\" id=\"topic-follow-toggle\" class=\""
                                                + followClass + "\" data-followed=\""
                                                + (followed ? "1" : "0") + "\">"
                                                + followLabel + "</button>");
                                out.println(
                                                "                <button type=\"button\" id=\"topic-question-open\" class=\"aira-button aira-button--tertiary\">Send info / question</button>");
                        } else {
                                out.println("                <a class=\"aira-button aira-button--primary\" href=\""
                                                + contextPath + "/home\">Follow</a>");
                                out.println(
                                                "                <a class=\"aira-button aira-button--tertiary\" href=\""
                                                                + contextPath
                                                                + "/home\">Send info / question</a>");
                        }
                        out.println("              </div>");
                        out.println("            </div>");
                        List<EsSubscription> championSubscriptions = subscriptionDao.findChampionsByTopicId(topicId)
                                        .stream()
                                        .filter(sub -> sub.getStatus() == EsSubscription.SubscriptionStatus.CHAMPION)
                                        .collect(Collectors.toList());
                        List<String> championNames = new ArrayList<>();
                        for (EsSubscription championSub : championSubscriptions) {
                                String championName = championSub.getUserId() != null
                                                ? userDao.findById(championSub.getUserId())
                                                                .map(User::getFullName)
                                                                .map(this::trimToNull)
                                                                .orElse(null)
                                                : null;
                                if (championName == null) {
                                        championName = trimToNull(championSub.getEmail());
                                }
                                if (championName != null) {
                                        championNames.add(championName);
                                }
                        }
                        int followerCount = subscriptionDao.findActiveByTopicId(topicId).size();
                        List<Supporter> activeSupporters = topicSupporterDao.findActiveSupportersByTopicId(topicId);

                        out.println("            <div class=\"aira-topic-meta\" aria-label=\"Topic metadata\">");
                        out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Stage</span><span class=\"aira-meta-chip__value\">"
                                        + escapeHtml(normalizedStage.isBlank() ? "Other" : normalizedStage)
                                        + "</span></span>");
                        if (!normalizedPath.isBlank()) {
                                out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Path</span><span class=\"aira-meta-chip__value\">"
                                                + escapeHtml(normalizedPath)
                                                + "</span></span>");
                        }
                        String normalizedPolicyStatus = trimToNull(topic.getPolicyStatus());
                        if (normalizedPolicyStatus != null) {
                                out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Policy</span><span class=\"aira-meta-chip__value\">"
                                                + escapeHtml(normalizedPolicyStatus)
                                                + "</span></span>");
                        }
                        out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Topic type</span><span class=\"aira-meta-chip__value\">"
                                        + escapeHtml(trimToNull(topic.getTopicType()) == null ? "Capability"
                                                        : topic.getTopicType())
                                        + "</span></span>");
                        if (!championNames.isEmpty()) {
                                out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Champion</span><span class=\"aira-meta-chip__value\">"
                                                + escapeHtml(String.join(", ", championNames))
                                                + "</span></span>");
                        }
                        if (!activeSupporters.isEmpty()) {
                                String supporterNames = activeSupporters.stream()
                                                .map(Supporter::getShortName)
                                                .collect(Collectors.joining(", "));
                                out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Supporters</span><span class=\"aira-meta-chip__value\">"
                                                + escapeHtml(supporterNames)
                                                + "</span></span>");
                        }
                        out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Followers</span><span class=\"aira-meta-chip__value\">"
                                        + followerCount
                                        + "</span></span>");
                        if (!curatedEntries.isEmpty()) {
                                out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Topics</span><span class=\"aira-meta-chip__value\">"
                                                + curatedEntries.size()
                                                + "</span></span>");
                        }
                        out.println("            </div>");
                        out.println("          </header>");

                        out.println("          <section aria-labelledby=\"stage-path-title\" class=\"aira-stack\">");
                        out.println("            <h2 class=\"aira-section-title\" id=\"stage-path-title\">Stage and Path</h2>");
                        out.println("            <div class=\"aira-topic-status-grid\">");
                        out.println("              <article class=\"aira-topic-status-card aira-topic-status-card--stage\"><span class=\"aira-topic-status-card__icon\" aria-hidden=\"true\">S</span><div><p class=\"aira-topic-status-card__label\">Stage</p><p class=\"aira-topic-status-card__value\">"
                                        + escapeHtml(normalizedStage.isBlank() ? "Other" : normalizedStage)
                                        + "</p><p class=\"aira-topic-status-card__description\">"
                                        + escapeHtml(stageDescription == null
                                                        ? "No description set."
                                                        : stageDescription)
                                        + "</p></div></article>");
                        out.println("              <article class=\"aira-topic-status-card aira-topic-status-card--path\"><span class=\"aira-topic-status-card__icon\" aria-hidden=\"true\">P</span><div><p class=\"aira-topic-status-card__label\">Path</p><p class=\"aira-topic-status-card__value\">"
                                        + escapeHtml(normalizedPath.isBlank() ? "Not set" : normalizedPath)
                                        + "</p><p class=\"aira-topic-status-card__description\">"
                                        + escapeHtml(pathDescription == null
                                                        ? "No description set."
                                                        : pathDescription)
                                        + "</p></div></article>");
                        out.println("            </div>");
                        out.println("          </section>");

                        out.println("          <section class=\"aira-section-card\" aria-labelledby=\"overview-title\">");
                        out.println(
                                        "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"overview-title\">Overview</h2></div>");
                        out.println("            <div class=\"aira-section-card__body aira-topic-overview\">");
                        out.println("              <div class=\"aira-topic-overview__text\">");
                        out.println("                <div class=\"aira-prose\">");
                        if (description.isBlank()) {
                                out.println("                  <p>Overview content will be added here.</p>");
                        } else {
                                out.println("                  <p>" + escapeHtml(description) + "</p>");
                        }
                        out.println("                </div>");
                        if (trimToNull(topic.getConfluenceUrl()) != null) {
                                out.println("                <div class=\"aira-chip-list\">");
                                out.println("                  <a class=\"aira-chip\" href=\""
                                                + escapeHtml(topic.getConfluenceUrl())
                                                + "\" target=\"_blank\" rel=\"noopener\">📄 Confluence</a>");
                                out.println("                </div>");
                        }
                        out.println("              </div>");
                        out.println("              <div class=\"aira-alert aira-alert--info\" role=\"status\" aria-live=\"polite\">");
                        out.println("                <p class=\"aira-alert__title\">Coming soon</p>");
                        out.println(
                                        "                <p>Support for source material, diagrams, and supporting notes is coming in a future update.</p>");
                        out.println("              </div>");
                        out.println("            </div>");
                        out.println("          </section>");

                        if (!activeSupporters.isEmpty()) {
                                out.println("          <section class=\"aira-section-card\" aria-labelledby=\"supporters-title\">");
                                out.println(
                                                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"supporters-title\">Supporters</h2></div>");
                                out.println("            <div class=\"aira-section-card__body aira-stack\">");
                                for (Supporter supporter : activeSupporters) {
                                        String shortName = orEmpty(supporter.getShortName());
                                        String fullName = trimToNull(supporter.getFullName());
                                        out.println("              <article class=\"aira-relationship-row\">");
                                        out.println("                <span class=\"aira-relationship-row__title\">"
                                                        + escapeHtml(shortName)
                                                        + (fullName != null && !fullName.equalsIgnoreCase(shortName)
                                                                        ? " <span class=\"aira-meta\">(" + escapeHtml(fullName) + ")</span>"
                                                                        : "")
                                                        + "</span>");
                                        String supporterDescription = trimToNull(supporter.getDescription());
                                        if (supporterDescription != null) {
                                                out.println("                <span class=\"aira-relationship-row__verb\">"
                                                                + escapeHtml(supporterDescription) + "</span>");
                                        }
                                        String websiteUrl = trimToNull(supporter.getWebsiteUrl());
                                        if (websiteUrl != null) {
                                                out.println("                <a class=\"aira-relationship-row__action\" href=\""
                                                                + escapeHtml(websiteUrl)
                                                                + "\" target=\"_blank\" rel=\"noopener\">Website</a>");
                                        }
                                        out.println("              </article>");
                                }
                                out.println("            </div>");
                                out.println("          </section>");
                        }

                        if (!recentOutcomeRows.isEmpty()) {
                                out.println("          <section class=\"aira-section-card\" aria-labelledby=\"outcomes-title\">");
                                out.println(
                                                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"outcomes-title\">Recent Outcomes</h2></div>");
                                out.println("            <div class=\"aira-section-card__body\"><div class=\"aira-outcome-list\">");
                                for (RecentOutcomeRow row : recentOutcomeRows) {
                                        EsRecordedOutcome outcome = row.outcome();
                                        String typeLabel = outcome.getOutcomeType() == null ? ""
                                                        : humanizeEnumName(outcome.getOutcomeType().name());
                                        String dateLabel = outcome.getCreatedAt() == null ? ""
                                                        : outcome.getCreatedAt().format(AGENDA_DATE_FMT);
                                        out.println("              <article class=\"aira-outcome-row\">");
                                        out.println("                <span class=\"aira-outcome-row__type aira-badge aira-badge--info\">"
                                                        + escapeHtml(typeLabel) + "</span>");
                                        out.println("                <span class=\"aira-outcome-row__summary\">"
                                                        + escapeHtml(orEmpty(outcome.getOutcomeText())) + "</span>");
                                        out.println("                <span class=\"aira-outcome-row__source\">"
                                                        + escapeHtml(row.meeting() == null ? "" : orEmpty(row.meeting().getMeetingName()))
                                                        + "</span>");
                                        out.println(
                                                        "                <span class=\"aira-outcome-row__date\">" + escapeHtml(dateLabel)
                                                                        + "</span>");
                                        if (row.meeting() != null) {
                                                out.println("                <a class=\"aira-outcome-row__action\" href=\"" + contextPath
                                                                + "/es/agenda?meetingId=" + row.meeting().getEsMeetingId()
                                                                + "\">View in Agenda</a>");
                                        }
                                        out.println("              </article>");
                                }
                                out.println("            </div></div>");
                                out.println("          </section>");
                        }

                        if (!topicMeetingRows.isEmpty() || ownMeetingSeriesOpt.isPresent()) {
                                out.println(
                                                "          <section class=\"aira-section-card\" aria-labelledby=\"meetings-summary-title\">");
                                out.println("            <div class=\"aira-section-card__header\">");
                                out.println(
                                                "              <h2 class=\"aira-section-card__title\" id=\"meetings-summary-title\">Meetings</h2>");
                                if (ownMeetingSeriesOpt.isPresent()) {
                                        if (canInteract) {
                                                String registerClass = viewerRegisteredForOwnMeeting
                                                                ? "aira-button aira-button--tertiary aira-button--small"
                                                                : "aira-button aira-button--primary aira-button--small";
                                                String registerLabel = viewerRegisteredForOwnMeeting ? "Unregister"
                                                                : "Register for Meeting";
                                                out.println("              <div class=\"aira-cluster\">");
                                                out.println(
                                                                "                <span class=\"aira-badge aira-badge--success\" id=\"meeting-register-status\" style=\""
                                                                                + (viewerRegisteredForOwnMeeting ? "" : "display:none")
                                                                                + "\">&#10003; Registered</span>");
                                                out.println("                <button type=\"button\" id=\"meeting-register-toggle\" class=\""
                                                                + registerClass + "\" data-registered=\""
                                                                + (viewerRegisteredForOwnMeeting ? "1" : "0") + "\">"
                                                                + registerLabel + "</button>");
                                                out.println("              </div>");
                                        } else {
                                                out.println(
                                                                "              <a class=\"aira-button aira-button--primary aira-button--small\" href=\""
                                                                                + contextPath + "/home\">Register for Meeting</a>");
                                        }
                                }
                                out.println("            </div>");
                                out.println("            <div class=\"aira-section-card__body\">");
                                if (topicMeetingRows.isEmpty()) {
                                        out.println("              <p class=\"aira-meta\">No meetings scheduled yet.</p>");
                                } else {
                                out.println("              <div class=\"aira-table-wrap\">");
                                out.println(
                                                "              <table class=\"aira-table agenda-main-table agenda-main-table--readonly\">");
                                out.println(
                                                "                <thead><tr><th>Meeting</th><th>Agenda</th><th>Presenter(s)</th></tr></thead>");
                                out.println("                <tbody>");
                                LocalDateTime nowForRender = LocalDateTime.now();
                                for (TopicMeetingAgendaRow row : topicMeetingRows) {
                                        EsMeeting rowMeeting = row.meeting();
                                        EsMeetingAgendaItem rowItem = row.agendaItem();
                                        boolean isUpcoming = rowMeeting.getScheduledStart() != null
                                                        && !rowMeeting.getScheduledStart().isBefore(nowForRender);
                                        out.println("                  <tr>");
                                        out.println("                    <td>");
                                        out.println("                      <div class=\"agenda-item-title\"><a href=\"" + contextPath
                                                        + "/es/agenda?meetingId=" + rowMeeting.getEsMeetingId()
                                                        + "\" class=\"agenda-topic-link\">"
                                                        + escapeHtml(orEmpty(rowMeeting.getMeetingName())) + "</a></div>");
                                        if (rowMeeting.getScheduledStart() != null) {
                                                out.println("                      <div class=\"agenda-item-time\">"
                                                                + escapeHtml(rowMeeting.getScheduledStart().format(AGENDA_DATE_FMT))
                                                                + (isUpcoming ? " (Upcoming)" : "") + "</div>");
                                        }
                                        out.println("                    </td>");
                                        out.println("                    <td>");
                                        if (row.summary()) {
                                                if (row.summaryItems().isEmpty()) {
                                                        out.println("                      <span class=\"aira-meta\">No agenda items recorded</span>");
                                                } else {
                                                        out.println("                      <ul>");
                                                        for (SummaryAgendaItem summaryItem : row.summaryItems()) {
                                                                out.println("                        <li>" + escapeHtml(summaryItem.title()));
                                                                if (!summaryItem.decisions().isEmpty()) {
                                                                        out.println("                          <ul>");
                                                                        for (String decision : summaryItem.decisions()) {
                                                                                out.println(
                                                                                                "                            <li>" + escapeHtml(decision) + "</li>");
                                                                        }
                                                                        out.println("                          </ul>");
                                                                }
                                                                out.println("                        </li>");
                                                        }
                                                        out.println("                      </ul>");
                                                }
                                        } else {
                                                if (rowItem.getAgendaMarkdown() != null && !rowItem.getAgendaMarkdown().isBlank()) {
                                                        out.println("                      <div class=\"agenda-item-text\">"
                                                                        + renderPlainText(rowItem.getAgendaMarkdown()) + "</div>");
                                                }
                                                String notesHtml = row.note() != null
                                                                ? topicNoteDocumentSupport.renderNotesHtml(row.note().getDocumentJson())
                                                                : "";
                                                if (!notesHtml.isEmpty()) {
                                                        out.println("                      <div class=\"agenda-notes\">");
                                                        out.println("                        <div class=\"agenda-notes-heading\">Notes</div>");
                                                        out.println(notesHtml);
                                                        out.println("                      </div>");
                                                }
                                                if (!row.outcomes().isEmpty()) {
                                                        out.println("                      <div class=\"agenda-outcomes\">");
                                                        out.println(
                                                                        "                        <div class=\"agenda-outcomes-heading\">Outcomes</div>");
                                                        out.println("                        <ul>");
                                                        for (EsRecordedOutcome outcome : row.outcomes()) {
                                                                out.println("                          <li>"
                                                                                + escapeHtml(orEmpty(outcome.getOutcomeText())) + "</li>");
                                                        }
                                                        out.println("                        </ul>");
                                                        out.println("                      </div>");
                                                }
                                        }
                                        out.println("                    </td>");
                                        out.println("                    <td>");
                                        if (row.presenters().isEmpty()) {
                                                out.println("                      <span class=\"aira-meta\">No presenters listed</span>");
                                        } else {
                                                for (EsAgendaItemPresenter presenter : row.presenters()) {
                                                        out.println("                      <div class=\"agenda-presenter\">"
                                                                        + escapeHtml(presenterDisplayName(presenter, row.presenterUsers()))
                                                                        + "</div>");
                                                }
                                        }
                                        out.println("                    </td>");
                                        out.println("                  </tr>");
                                }
                                out.println("                </tbody>");
                                out.println("              </table>");
                                out.println("              </div>");
                                }
                                Long linkedSeriesId = !topicMeetingRows.isEmpty()
                                                ? topicMeetingRows.get(0).meeting().getEsTopicMeetingId()
                                                : ownMeetingSeriesOpt.map(EsTopicMeeting::getEsTopicMeetingId).orElse(null);
                                if (linkedSeriesId != null) {
                                        out.println("              <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                                        + "/es/meeting-series?seriesId=" + linkedSeriesId
                                                        + "\">See All Meetings</a></p>");
                                }
                                out.println("            </div>");
                                out.println("          </section>");
                        }

                        if (!outboundRels.isEmpty() || !inboundRels.isEmpty()) {
                        out.println("          <section class=\"aira-section-card\" aria-labelledby=\"related-title\">");
                        out.println(
                                        "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"related-title\">Related Topics</h2></div>");
                        out.println("            <div class=\"aira-section-card__body\"><div class=\"aira-relationship-list\">");
                                for (EsTopicRelationship rel : outboundRels) {
                                        String label = rel.getRelationshipType() != null
                                                        ? rel.getRelationshipType().getLabel()
                                                        : "Related topic";
                                        String name = topicNameMap.getOrDefault(rel.getToTopicId(),
                                                        "#" + rel.getToTopicId());
                                        out.println(
                                                        "              <article class=\"aira-relationship-row\"><span class=\"aira-relationship-row__title\">"
                                                                        + escapeHtml(name)
                                                                        + "</span><span class=\"aira-relationship-row__verb\">"
                                                                        + escapeHtml(label)
                                                                        + "</span><a class=\"aira-relationship-row__action\" href=\""
                                                                        + contextPath
                                                                        + "/es/topic/" + rel.getToTopicId()
                                                                        + "\">Open</a></article>");
                                }
                                for (EsTopicRelationship rel : inboundRels) {
                                        String label = rel.getRelationshipType() != null
                                                        ? rel.getRelationshipType().getInverseLabel()
                                                        : "Related topic";
                                        String name = topicNameMap.getOrDefault(rel.getFromTopicId(),
                                                        "#" + rel.getFromTopicId());
                                        out.println(
                                                        "              <article class=\"aira-relationship-row\"><span class=\"aira-relationship-row__title\">"
                                                                        + escapeHtml(name)
                                                                        + "</span><span class=\"aira-relationship-row__verb\">"
                                                                        + escapeHtml(label)
                                                                        + "</span><a class=\"aira-relationship-row__action\" href=\""
                                                                        + contextPath
                                                                        + "/es/topic/" + rel.getFromTopicId()
                                                                        + "\">Open</a></article>");
                                }
                        out.println("            </div></div>");
                        out.println("          </section>");
                        }

                        if (!curatedTopicRows.isEmpty()) {
                                out.println("          <section class=\"aira-section-card\" aria-labelledby=\"topics-panel-title\">");
                                out.println(
                                                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"topics-panel-title\">Topics</h2></div>");
                                out.println("            <div class=\"aira-section-card__body aira-stack\">");

                                Map<String, List<CuratedTopicRow>> curatedByCategory = new LinkedHashMap<>();
                                List<CuratedTopicRow> curatedUncategorized = new ArrayList<>();
                                for (CuratedTopicRow row : curatedTopicRows) {
                                        String category = trimToNull(row.curation().getCategoryLabel());
                                        if (category == null) {
                                                curatedUncategorized.add(row);
                                        } else {
                                                curatedByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(row);
                                        }
                                }

                                if (curatedByCategory.isEmpty()) {
                                        renderCuratedTopicTable(out, contextPath, curatedTopicRows, null);
                                } else {
                                        List<String> categoryNames = new ArrayList<>(curatedByCategory.keySet());
                                        categoryNames.sort(String.CASE_INSENSITIVE_ORDER);
                                        for (String category : categoryNames) {
                                                renderCuratedTopicTable(out, contextPath, curatedByCategory.get(category), category);
                                        }
                                        if (!curatedUncategorized.isEmpty()) {
                                                renderCuratedTopicTable(out, contextPath, curatedUncategorized, "No Category");
                                        }
                                }

                                out.println("            </div>");
                                out.println("          </section>");
                        }

                        if (!curatedByEntries.isEmpty()) {
                        out.println("          <section class=\"aira-section-card\" aria-labelledby=\"included-title\">");
                        out.println(
                                        "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"included-title\">Included In</h2></div>");
                        out.println("            <div class=\"aira-section-card__body\"><div class=\"aira-tag-list\">");
                                for (EsTopicCuration entry : curatedByEntries) {
                                        String curatorName = topicNameMap.getOrDefault(entry.getCuratorTopicId(),
                                                        "#" + entry.getCuratorTopicId());
                                        out.println("              <a class=\"aira-tag aira-tag--outline\" href=\""
                                                        + contextPath
                                                        + "/es/topic/" + entry.getCuratorTopicId() + "\">"
                                                        + escapeHtml(curatorName) + "</a>");
                                }
                        out.println("            </div></div>");
                        out.println("          </section>");
                        }

                        out.println("          <div class=\"aira-topic-details-strip\" aria-label=\"Topic details\">");
                        out.println("            <span><strong>Topic ID:</strong> " + topicId + "</span>");
                        out.println("            <div class=\"aira-topic-details-strip__end\">");
                        out.println("              <span class=\"aira-tag aira-tag--outline\">"
                                        + escapeHtml(trimToNull(topic.getTopicType()) == null ? "capability"
                                                        : topic.getTopicType())
                                        + "</span>");
                        out.println("              <span class=\"aira-tag aira-tag--outline\">"
                                        + escapeHtml(trimToNull(topic.getPolicyStatus()) == null ? "planning"
                                                        : topic.getPolicyStatus())
                                        + "</span>");
                        out.println("              <span class=\"aira-badge aira-badge--subtle\">"
                                        + escapeHtml(topicSpace.getVisibility() == null ? "Unknown"
                                                        : capitalize(topicSpace.getVisibility().name().toLowerCase()))
                                        + "</span>");
                        out.println("              <span class=\"aira-badge aira-badge--warning\">"
                                        + (canManageTopic ? "Management available" : "Read-only view") + "</span>");
                        out.println("            </div>");
                        out.println("          </div>");

                        out.println("        </div>");
                        out.println("        <aside class=\"aira-right-rail\" aria-label=\"Topic activity\">");
                        RecentlyViewedTopicsRenderer.render(out, topicId, recentlyViewedTopics, canInteract,
                                        otherTopicId -> contextPath + "/es/topic/" + otherTopicId);
                        if (canManageTopic) {
                                TopicManageNavRenderer.TopicManageCounts manageCounts = TopicManageNavRenderer.computeCounts(
                                                topicId, viewer, subscriptionDao, agendaItemDao, esMeetingDao, commentDao,
                                                relationshipDao, curationDao, topicSupporterDao, topicSpaceAccessService);
                                TopicManageNavRenderer.render(out, contextPath, topicId, null, isAdmin,
                                                meeting != null ? meeting.getEsTopicMeetingId() : null, false, manageCounts);
                        }
                        out.println("        </aside>");
                        out.println("      </div>");
                        if (canInteract) {
                                out.println("      <dialog id=\"topic-question-modal\">");
                                out.println("        <form method=\"dialog\" class=\"aira-stack\" style=\"min-width:22rem; max-width:36rem;\">");
                                out.println("          <h2>Send info / question</h2>");
                                if (canReview) {
                                        out.println("          <p class=\"aira-meta\">Your note will be saved to this topic's review comments.</p>");
                                } else {
                                        out.println("          <p class=\"aira-meta\">Questions are not available right now because no active campaign is open.</p>");
                                }
                                out.println("          <label for=\"topic-question-input\">Question</label>");
                                out.println(
                                                "          <textarea id=\"topic-question-input\" rows=\"5\" maxlength=\"2000\" placeholder=\"Add a question or context for this topic\"></textarea>");
                                out.println("          <p id=\"topic-question-status\" class=\"aira-meta\" hidden></p>");
                                out.println("          <div class=\"aira-cluster\">");
                                out.println("            <button type=\"button\" id=\"topic-question-submit\" class=\"aira-button aira-button--primary\""
                                                + (canReview ? "" : " disabled") + ">Send</button>");
                                out.println("            <button type=\"button\" id=\"topic-question-cancel\" class=\"aira-button aira-button--tertiary\">Cancel</button>");
                                out.println("          </div>");
                                out.println("        </form>");
                                out.println("      </dialog>");
                        }
                        out.println("    </div>");

                        if (canInteract) {
                                out.println("    <script>");
                                out.println("      (function() {");
                                out.println("        var topicId = " + topicId + ";");
                                out.println("        var canReview = " + canReview + ";");
                                out.println("        var campaignCode = " + quoteJs(campaignCode) + ";");
                                out.println("        var followButton = document.getElementById('topic-follow-toggle');");
                                out.println("        var questionOpen = document.getElementById('topic-question-open');");
                                out.println("        var questionModal = document.getElementById('topic-question-modal');");
                                out.println("        var questionInput = document.getElementById('topic-question-input');");
                                out.println("        var questionSubmit = document.getElementById('topic-question-submit');");
                                out.println("        var questionCancel = document.getElementById('topic-question-cancel');");
                                out.println("        var questionStatus = document.getElementById('topic-question-status');");

                                out.println("        var followStatusBadge = document.getElementById('topic-follow-status');");
                                out.println("        function setFollowButtonState(isFollowed) {");
                                out.println("          if (!followButton) { return; }");
                                out.println("          followButton.setAttribute('data-followed', isFollowed ? '1' : '0');");
                                out.println("          followButton.textContent = isFollowed ? 'Unfollow' : 'Follow';");
                                out.println("          followButton.classList.toggle('aira-button--primary', !isFollowed);");
                                out.println("          followButton.classList.toggle('aira-button--tertiary', isFollowed);");
                                out.println("          if (followStatusBadge) { followStatusBadge.style.display = isFollowed ? '' : 'none'; }");
                                out.println("        }");

                                out.println("        if (followButton) {");
                                out.println("          followButton.addEventListener('click', function() {");
                                out.println("            var isFollowed = followButton.getAttribute('data-followed') === '1';");
                                out.println("            var params = new URLSearchParams();");
                                out.println("            params.set('topicId', String(topicId));");
                                out.println("            params.set('action', isFollowed ? 'unfollow' : 'follow');");
                                out.println("            followButton.disabled = true;");
                                out.println("            fetch('" + contextPath + "/es/topics/follow-toggle', {");
                                out.println("              method: 'POST',");
                                out.println("              headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
                                out.println("              body: params.toString()");
                                out.println("            }).then(function(res) { return res.json(); }).then(function(json) {");
                                out.println("              if (!json || !json.ok) {");
                                out.println("                window.alert((json && json.error) ? json.error : 'Unable to update follow status.');");
                                out.println("                return;");
                                out.println("              }");
                                out.println("              setFollowButtonState(!!json.followed);");
                                out.println("            }).catch(function() {");
                                out.println("              window.alert('Unable to update follow status.');");
                                out.println("            }).finally(function() {");
                                out.println("              followButton.disabled = false;");
                                out.println("            });");
                                out.println("          });");
                                out.println("        }");

                                out.println("        var registerButton = document.getElementById('meeting-register-toggle');");
                                out.println("        var registerStatusBadge = document.getElementById('meeting-register-status');");
                                out.println("        function setRegisterButtonState(isRegistered) {");
                                out.println("          if (!registerButton) { return; }");
                                out.println("          registerButton.setAttribute('data-registered', isRegistered ? '1' : '0');");
                                out.println(
                                                "          registerButton.textContent = isRegistered ? 'Unregister' : 'Register for Meeting';");
                                out.println("          registerButton.classList.toggle('aira-button--primary', !isRegistered);");
                                out.println("          registerButton.classList.toggle('aira-button--tertiary', isRegistered);");
                                out.println(
                                                "          if (registerStatusBadge) { registerStatusBadge.style.display = isRegistered ? '' : 'none'; }");
                                out.println("        }");

                                out.println("        if (registerButton) {");
                                out.println("          registerButton.addEventListener('click', function() {");
                                out.println(
                                                "            var isRegistered = registerButton.getAttribute('data-registered') === '1';");
                                out.println("            var params = new URLSearchParams();");
                                out.println("            params.set('topicId', String(topicId));");
                                out.println("            params.set('meetingId', String(" + ownMeetingSeriesOpt
                                                .map(EsTopicMeeting::getEsTopicMeetingId).map(String::valueOf).orElse("null") + "));");
                                out.println("            params.set('action', isRegistered ? 'unrequest' : 'request');");
                                out.println("            registerButton.disabled = true;");
                                out.println("            fetch('" + contextPath + "/es/topics/meeting-toggle', {");
                                out.println("              method: 'POST',");
                                out.println("              headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
                                out.println("              body: params.toString()");
                                out.println("            }).then(function(res) { return res.json(); }).then(function(json) {");
                                out.println("              if (!json || !json.ok) {");
                                out.println(
                                                "                window.alert((json && json.error) ? json.error : 'Unable to update meeting registration.');");
                                out.println("                return;");
                                out.println("              }");
                                out.println("              setRegisterButtonState(!!json.requested);");
                                out.println("            }).catch(function() {");
                                out.println("              window.alert('Unable to update meeting registration.');");
                                out.println("            }).finally(function() {");
                                out.println("              registerButton.disabled = false;");
                                out.println("            });");
                                out.println("          });");
                                out.println("        }");

                                out.println("        if (questionOpen && questionModal) {");
                                out.println("          questionOpen.addEventListener('click', function() {");
                                out.println("            if (questionStatus) {");
                                out.println("              questionStatus.hidden = true;");
                                out.println("              questionStatus.textContent = ''; ");
                                out.println("            }");
                                out.println("            if (questionInput) { questionInput.value = ''; }");
                                out.println("            questionModal.showModal();");
                                out.println("          });");
                                out.println("        }");

                                out.println("        if (questionCancel && questionModal) {");
                                out.println("          questionCancel.addEventListener('click', function() { questionModal.close(); });");
                                out.println("        }");

                                out.println("        if (questionSubmit) {");
                                out.println("          questionSubmit.addEventListener('click', function() {");
                                out.println("            if (!canReview || !campaignCode) {");
                                out.println("              if (questionStatus) {");
                                out.println("                questionStatus.hidden = false;");
                                out.println("                questionStatus.textContent = 'Questions are unavailable right now.';");
                                out.println("              }");
                                out.println("              return;");
                                out.println("            }");
                                out.println("            var text = (questionInput && questionInput.value ? questionInput.value.trim() : '');");
                                out.println("            if (!text) {");
                                out.println("              if (questionStatus) {");
                                out.println("                questionStatus.hidden = false;");
                                out.println("                questionStatus.textContent = 'Enter a question before sending.';");
                                out.println("              }");
                                out.println("              return;");
                                out.println("            }");
                                out.println("            var params = new URLSearchParams();");
                                out.println("            params.set('campaignCode', campaignCode);");
                                out.println("            params.set('topicId', String(topicId));");
                                out.println("            params.set('commentText', text);");
                                out.println("            questionSubmit.disabled = true;");
                                out.println("            fetch('" + contextPath + "/es/review/comment', {");
                                out.println("              method: 'POST',");
                                out.println("              headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
                                out.println("              body: params.toString()");
                                out.println("            }).then(function(res) { return res.json(); }).then(function(json) {");
                                out.println("              if (!json || !json.ok) {");
                                out.println("                if (questionStatus) {");
                                out.println("                  questionStatus.hidden = false;");
                                out.println("                  questionStatus.textContent = (json && json.error) ? json.error : 'Unable to send question.';");
                                out.println("                }");
                                out.println("                return;");
                                out.println("              }");
                                out.println("              if (questionStatus) {");
                                out.println("                questionStatus.hidden = false;");
                                out.println("                questionStatus.textContent = 'Question sent.';");
                                out.println("              }");
                                out.println("              if (questionInput) { questionInput.value = ''; }");
                                out.println("            }).catch(function() {");
                                out.println("              if (questionStatus) {");
                                out.println("                questionStatus.hidden = false;");
                                out.println("                questionStatus.textContent = 'Unable to send question.';");
                                out.println("              }");
                                out.println("            }).finally(function() {");
                                out.println("              questionSubmit.disabled = false;");
                                out.println("            });");
                                out.println("          });");
                                out.println("        }");
                                out.println("      })();");
                                out.println("    </script>");
                        }

                        out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
                        page.writeEnd(out);
                }
        }

        private void renderNotFound(HttpServletRequest request, HttpServletResponse response, String contextPath,
                        Long topicId) throws IOException {
                response.setContentType("text/html;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                try (PrintWriter out = response.getWriter()) {
                        AiraPage page = InteropAiraPageFactory.base(request, "Topic Not Found - InteropHub")
                                        .applicationSubtitle("Topic display")
                                        .pageHeading("Topic Not Found")
                                        .mainClass("aira-main")
                                        .build();
                        page.writeStart(out);
                        out.println("    <div class=\"aira-container--narrow aira-stack aira-stack--compact\">");
                        out.println("      <section class=\"aira-alert aira-alert--warning\" role=\"status\" aria-live=\"polite\">");
                        out.println("        <p class=\"aira-alert__title\">Topic not found</p>");
                        out.println("        <p>The requested topic could not be found.</p>");
                        if (topicId != null) {
                                out.println("        <p>Topic ID: " + topicId + "</p>");
                        }
                        out.println("        <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                        + "/es/topics\">Back to Topics</a></p>");
                        out.println("      </section>");
                        out.println("    </div>");
                        out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
                        page.writeEnd(out);
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

        private String buildTopicSummary(EsCampaignTopicBrowseRow topic, String stageName) {
                List<String> parts = new ArrayList<>();
                String stage = trimToNull(stageName);
                String policyStatus = trimToNull(topic.getPolicyStatus());
                String type = trimToNull(topic.getTopicType());
                String neighborhood = trimToNull(topic.getNeighborhood());
                if (stage != null) {
                        parts.add(stage);
                }
                if (type != null) {
                        parts.add(type);
                }
                if (policyStatus != null) {
                        parts.add(policyStatus);
                }
                if (neighborhood != null) {
                        parts.add(neighborhood);
                }
                if (parts.isEmpty()) {
                        return "Topic details will be added here.";
                }
                return String.join(" · ", parts);
        }

        private String quoteJs(String value) {
                if (value == null) {
                        return "null";
                }
                return "\"" + value
                                .replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\r", "")
                                .replace("\n", "\\n")
                                + "\"";
        }

        private static final DateTimeFormatter AGENDA_DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

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

        private boolean isChampionEquivalentStatus(EsSubscription.SubscriptionStatus status) {
                return status == EsSubscription.SubscriptionStatus.CHAMPION
                                || status == EsSubscription.SubscriptionStatus.SUPPORT;
        }

        private String capitalize(String s) {
                if (s == null || s.isEmpty()) {
                        return s;
                }
                return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }

        private String humanizeEnumName(String enumName) {
                if (enumName == null || enumName.isBlank()) {
                        return "";
                }
                String[] words = enumName.split("_");
                StringBuilder result = new StringBuilder();
                for (String word : words) {
                        if (word.isEmpty()) {
                                continue;
                        }
                        if (result.length() > 0) {
                                result.append(' ');
                        }
                        result.append(word.charAt(0)).append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
                }
                return result.toString();
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

        private String renderPlainText(String text) {
                if (text == null) {
                        return "";
                }
                return escapeHtml(text).replace("\n", "<br>");
        }

        private String presenterDisplayName(EsAgendaItemPresenter presenter, Map<Long, User> presenterUsers) {
                if (trimToNull(presenter.getDisplayName()) != null) {
                        return presenter.getDisplayName();
                }
                if (presenter.getUserId() != null) {
                        User user = presenterUsers.get(presenter.getUserId());
                        if (user != null && trimToNull(user.getFullName()) != null) {
                                return user.getFullName();
                        }
                }
                return orEmpty(presenter.getEmail());
        }

        /**
         * Builds the rows for the topic-page "Meetings" panel: the single soonest
         * upcoming meeting (if any), followed by up to the 3 most recent past
         * meetings. A topic can appear in this panel two ways, which are merged
         * into one timeline:
         * <ul>
         * <li><b>Detail rows</b> — meetings where this topic has its own agenda
         * item (e.g. occasionally covered in a recurring meeting like IFG). Shows
         * that item's planned agenda, notes, and outcomes, same as es/agenda.</li>
         * <li><b>Summary rows</b> — meetings that belong to this topic's own
         * meeting series (e.g. IFG's own topic page, or a topic with a dedicated
         * meeting alongside occasional coverage elsewhere). Too much happens in
         * these to show full notes, so instead this lists each agenda item's
         * title with its recorded outcomes (decisions) nested underneath, and a
         * deduplicated list of everyone who presented across the whole meeting.
         * </li>
         * </ul>
         * If a meeting somehow qualifies both ways (a topic's own agenda item
         * inside its own meeting series), the summary row wins since that's the
         * meeting's dedicated topic and the individual item would be redundant.
         * Cancelled meetings and cancelled/postponed agenda items are never
         * included. Topic notes that are not linked to a meeting are out of scope
         * for this panel (not yet supported) and are simply not picked up by
         * either agenda-item-based lookup.
         */
        private List<TopicMeetingAgendaRow> buildTopicMeetingRows(Long topicId, User viewer) {
                // Detail candidates: meetings where this topic has its own agenda item.
                Map<Long, EsMeetingAgendaItem> itemByMeetingId = new LinkedHashMap<>();
                for (EsMeetingAgendaItem item : agendaItemDao.findByTopicId(topicId)) {
                        if (item.getStatus() == EsMeetingAgendaItem.AgendaItemStatus.CANCELLED
                                        || item.getStatus() == EsMeetingAgendaItem.AgendaItemStatus.POSTPONED) {
                                continue;
                        }
                        itemByMeetingId.putIfAbsent(item.getEsMeetingId(), item);
                }

                // Summary candidates: meetings belonging to this topic's own meeting series.
                Set<Long> ownSeriesMeetingIds = new HashSet<>();
                Optional<EsTopicMeeting> ownSeriesOpt = esTopicMeetingDao.findByTopicId(topicId);
                if (ownSeriesOpt.isPresent()) {
                        for (EsMeeting m : esMeetingDao.findByEsTopicMeetingId(ownSeriesOpt.get().getEsTopicMeetingId())) {
                                if (m.getStatus() != EsMeeting.MeetingStatus.CANCELLED) {
                                        ownSeriesMeetingIds.add(m.getEsMeetingId());
                                }
                        }
                }

                if (itemByMeetingId.isEmpty() && ownSeriesMeetingIds.isEmpty()) {
                        return List.of();
                }

                Map<Long, EsMeeting> candidateMeetingsById = new LinkedHashMap<>();
                for (Long meetingId : ownSeriesMeetingIds) {
                        esMeetingDao.findById(meetingId).ifPresent(m -> candidateMeetingsById.put(meetingId, m));
                }
                for (Long meetingId : itemByMeetingId.keySet()) {
                        if (!candidateMeetingsById.containsKey(meetingId)) {
                                esMeetingDao.findById(meetingId)
                                                .filter(m -> m.getStatus() != EsMeeting.MeetingStatus.CANCELLED)
                                                .ifPresent(m -> candidateMeetingsById.put(meetingId, m));
                        }
                }
                List<EsMeeting> candidateMeetings = topicSpaceAccessService.filterVisibleMeetings(viewer,
                                new ArrayList<>(candidateMeetingsById.values()));
                if (candidateMeetings.isEmpty()) {
                        return List.of();
                }

                LocalDateTime now = LocalDateTime.now();
                List<EsMeeting> orderedMeetings = new ArrayList<>();
                candidateMeetings.stream()
                                .filter(m -> m.getScheduledStart() != null && !m.getScheduledStart().isBefore(now))
                                .min(Comparator.comparing(EsMeeting::getScheduledStart))
                                .ifPresent(orderedMeetings::add);
                candidateMeetings.stream()
                                .filter(m -> m.getScheduledStart() != null && m.getScheduledStart().isBefore(now))
                                .sorted(Comparator.comparing(EsMeeting::getScheduledStart).reversed())
                                .limit(3)
                                .forEach(orderedMeetings::add);

                // Shared, mutated-in-place across rows: by the time rendering reads a row's
                // presenterUsers map, every row below it has already contributed its
                // presenters' resolved User records too, since all rows hold the same map.
                Map<Long, User> presenterUsers = new HashMap<>();
                List<TopicMeetingAgendaRow> rows = new ArrayList<>();
                for (EsMeeting meeting : orderedMeetings) {
                        if (ownSeriesMeetingIds.contains(meeting.getEsMeetingId())) {
                                rows.add(buildSummaryRow(meeting, presenterUsers));
                        } else {
                                rows.add(buildDetailRow(meeting, itemByMeetingId.get(meeting.getEsMeetingId()), presenterUsers));
                        }
                }
                return rows;
        }

        private TopicMeetingAgendaRow buildDetailRow(EsMeeting meeting, EsMeetingAgendaItem item,
                        Map<Long, User> presenterUsers) {
                List<EsAgendaItemPresenter> presenters = presenterDao
                                .findActiveByAgendaItemId(item.getEsMeetingAgendaItemId());
                resolvePresenterUsers(presenters, presenterUsers);
                EsTopicNote note = topicNoteDao.findByAgendaItemId(item.getEsMeetingAgendaItemId()).orElse(null);
                List<EsRecordedOutcome> outcomes = note != null
                                ? recordedOutcomeDao.findByNoteIdOrdered(note.getEsTopicNoteId())
                                : List.of();
                return new TopicMeetingAgendaRow(meeting, false, item, note, outcomes, List.of(), presenters,
                                presenterUsers);
        }

        private TopicMeetingAgendaRow buildSummaryRow(EsMeeting meeting, Map<Long, User> presenterUsers) {
                List<EsMeetingAgendaItem> meetingItems = agendaItemDao.findByMeetingIdOrdered(meeting.getEsMeetingId())
                                .stream()
                                .filter(i -> i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.CANCELLED
                                                && i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.POSTPONED)
                                .collect(Collectors.toList());

                List<SummaryAgendaItem> summaryItems = new ArrayList<>();
                Map<String, EsAgendaItemPresenter> presentersByIdentity = new LinkedHashMap<>();
                for (EsMeetingAgendaItem item : meetingItems) {
                        EsTopicNote note = topicNoteDao.findByAgendaItemId(item.getEsMeetingAgendaItemId()).orElse(null);
                        List<String> decisions = note == null ? List.of()
                                        : recordedOutcomeDao.findByNoteIdOrdered(note.getEsTopicNoteId()).stream()
                                                        .map(EsRecordedOutcome::getOutcomeText)
                                                        .map(this::orEmpty)
                                                        .collect(Collectors.toList());
                        summaryItems.add(new SummaryAgendaItem(orEmpty(item.getTitle()), decisions));

                        for (EsAgendaItemPresenter presenter : presenterDao
                                        .findActiveByAgendaItemId(item.getEsMeetingAgendaItemId())) {
                                String identity = presenter.getUserId() != null ? "u:" + presenter.getUserId()
                                                : trimToNull(presenter.getEmailNormalized()) != null
                                                                ? "e:" + presenter.getEmailNormalized()
                                                                : "n:" + orEmpty(presenter.getDisplayName());
                                presentersByIdentity.putIfAbsent(identity, presenter);
                        }
                }
                List<EsAgendaItemPresenter> presenters = new ArrayList<>(presentersByIdentity.values());
                resolvePresenterUsers(presenters, presenterUsers);

                return new TopicMeetingAgendaRow(meeting, true, null, null, List.of(), summaryItems, presenters,
                                presenterUsers);
        }

        private void resolvePresenterUsers(List<EsAgendaItemPresenter> presenters, Map<Long, User> presenterUsers) {
                for (EsAgendaItemPresenter presenter : presenters) {
                        if (presenter.getUserId() != null && !presenterUsers.containsKey(presenter.getUserId())) {
                                userDao.findById(presenter.getUserId()).ifPresent(u -> presenterUsers.put(u.getUserId(), u));
                        }
                }
        }

        private record TopicMeetingAgendaRow(EsMeeting meeting, boolean summary, EsMeetingAgendaItem agendaItem,
                        EsTopicNote note, List<EsRecordedOutcome> outcomes, List<SummaryAgendaItem> summaryItems,
                        List<EsAgendaItemPresenter> presenters, Map<Long, User> presenterUsers) {
        }

        private record SummaryAgendaItem(String title, List<String> decisions) {
        }

        /**
         * Builds the rows for the topic-page "Recent Outcomes" panel: up to the 10
         * most recently recorded outcomes for this topic, across all of its
         * meetings, newest first. Each carries the meeting it was decided in when
         * one exists and is visible to the viewer, so the row can link to that
         * meeting's agenda; otherwise it's left unlinked.
         */
        private List<RecentOutcomeRow> buildRecentOutcomeRows(Long topicId, User viewer) {
                List<EsRecordedOutcome> outcomes = recordedOutcomeDao.findRecentByTopicId(topicId, 10);
                List<RecentOutcomeRow> rows = new ArrayList<>();
                for (EsRecordedOutcome outcome : outcomes) {
                        EsTopicNote note = topicNoteDao.findById(outcome.getEsTopicNoteId()).orElse(null);
                        EsMeeting meeting = null;
                        if (note != null && note.getEsMeetingId() != null) {
                                EsMeeting candidate = esMeetingDao.findById(note.getEsMeetingId()).orElse(null);
                                if (candidate != null && topicSpaceAccessService.canViewMeeting(viewer, candidate)) {
                                        meeting = candidate;
                                }
                        }
                        rows.add(new RecentOutcomeRow(outcome, meeting));
                }
                return rows;
        }

        private record RecentOutcomeRow(EsRecordedOutcome outcome, EsMeeting meeting) {
        }

        /**
         * Builds the rows for the topic-page "Topics" panel: one row per topic this
         * topic curates, in curation display order, each carrying the most recent
         * past meeting (if any) where that curated topic had a non-cancelled,
         * non-postponed agenda item within THIS topic's own meeting series — the
         * "Last Appeared" indicator. Topics with no own meeting series (or no such
         * appearance yet) simply show no Last Appeared link. Category-based
         * grouping is applied at render time from each row's own
         * {@code curation().getCategoryLabel()}.
         */
        private List<CuratedTopicRow> buildCuratedTopicRows(Long topicId, List<EsTopicCuration> curatedEntries,
                        Map<Long, String> topicNameMap) {
                if (curatedEntries.isEmpty()) {
                        return List.of();
                }
                Map<Long, LocalDateTime> lastAppearedAt = new HashMap<>();
                Map<Long, Long> lastAppearedMeetingId = new HashMap<>();
                Optional<EsTopicMeeting> ownSeriesOpt = esTopicMeetingDao.findByTopicId(topicId);
                if (ownSeriesOpt.isPresent()) {
                        List<EsMeeting> seriesMeetings = esMeetingDao
                                        .findByEsTopicMeetingId(ownSeriesOpt.get().getEsTopicMeetingId());
                        Map<Long, EsMeeting> meetingById = new HashMap<>();
                        for (EsMeeting m : seriesMeetings) {
                                meetingById.put(m.getEsMeetingId(), m);
                        }
                        LocalDateTime now = LocalDateTime.now();
                        if (!meetingById.isEmpty()) {
                                for (EsMeetingAgendaItem item : agendaItemDao
                                                .findByMeetingIds(new ArrayList<>(meetingById.keySet()))) {
                                        if (item.getEsTopicId() == null
                                                        || item.getStatus() == EsMeetingAgendaItem.AgendaItemStatus.CANCELLED
                                                        || item.getStatus() == EsMeetingAgendaItem.AgendaItemStatus.POSTPONED) {
                                                continue;
                                        }
                                        EsMeeting sourceMeeting = meetingById.get(item.getEsMeetingId());
                                        if (sourceMeeting == null || sourceMeeting.getScheduledStart() == null
                                                        || sourceMeeting.getScheduledStart().isAfter(now)) {
                                                continue;
                                        }
                                        LocalDateTime existing = lastAppearedAt.get(item.getEsTopicId());
                                        if (existing == null || sourceMeeting.getScheduledStart().isAfter(existing)) {
                                                lastAppearedAt.put(item.getEsTopicId(), sourceMeeting.getScheduledStart());
                                                lastAppearedMeetingId.put(item.getEsTopicId(), sourceMeeting.getEsMeetingId());
                                        }
                                }
                        }
                }

                List<CuratedTopicRow> rows = new ArrayList<>();
                for (EsTopicCuration curation : curatedEntries) {
                        Long curatedTopicId = curation.getCuratedTopicId();
                        String name = topicNameMap.getOrDefault(curatedTopicId, "#" + curatedTopicId);
                        rows.add(new CuratedTopicRow(curation, name, lastAppearedAt.get(curatedTopicId),
                                        lastAppearedMeetingId.get(curatedTopicId)));
                }
                return rows;
        }

        private record CuratedTopicRow(EsTopicCuration curation, String topicName, LocalDateTime lastAppearedAt,
                        Long lastAppearedMeetingId) {
        }

        /**
         * Renders one Topic/Last Appeared table for the "Topics" panel, optionally
         * preceded by a category heading. Rows are rendered in the order given
         * (curation display order).
         */
        private void renderCuratedTopicTable(PrintWriter out, String contextPath, List<CuratedTopicRow> rows,
                        String categoryHeading) {
                out.println("              <section>");
                if (categoryHeading != null) {
                        out.println("                <h3 class=\"aira-subsection-title\">" + escapeHtml(categoryHeading)
                                        + "</h3>");
                }
                out.println("                <div class=\"aira-table-wrap\">");
                out.println("                  <table class=\"aira-table\">");
                out.println("                    <thead><tr><th>Topic</th><th>Last Appeared</th></tr></thead>");
                out.println("                    <tbody>");
                for (CuratedTopicRow row : rows) {
                        out.println("                      <tr>");
                        out.println("                        <td><a class=\"agenda-topic-link\" href=\"" + contextPath
                                        + "/es/topic/" + row.curation().getCuratedTopicId() + "\">"
                                        + escapeHtml(row.topicName()) + "</a></td>");
                        if (row.lastAppearedAt() != null && row.lastAppearedMeetingId() != null) {
                                out.println("                        <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                                + "/es/agenda?meetingId=" + row.lastAppearedMeetingId() + "\">"
                                                + escapeHtml(row.lastAppearedAt().toLocalDate().format(AGENDA_DATE_FMT))
                                                + "</a></td>");
                        } else {
                                out.println("                        <td><span class=\"aira-meta\">Never</span></td>");
                        }
                        out.println("                      </tr>");
                }
                out.println("                    </tbody>");
                out.println("                  </table>");
                out.println("                </div>");
                out.println("              </section>");
        }
}
