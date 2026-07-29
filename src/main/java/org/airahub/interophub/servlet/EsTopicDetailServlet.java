package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.airahub.interophub.dao.EsTopicNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import java.time.LocalDate;
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
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicCuration;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicRelationship;
import org.airahub.interophub.model.EsTopicSpace;
import org.immregistries.aira.web.AiraPage;
import org.airahub.interophub.service.EsTopicViewHistoryService;

public class EsTopicDetailServlet extends HttpServlet {

        private static final Logger LOGGER = Logger.getLogger(EsTopicDetailServlet.class.getName());

        private final AuthFlowService authFlowService;
        private final EsTopicDao esTopicDao;
        private final EsTopicNeighborhoodDao topicNeighborhoodDao;
        private final EsCampaignDao campaignDao;
        private final EsCampaignTopicDao campaignTopicDao;
        private final EsSubscriptionDao subscriptionDao;
        private final EsTopicMeetingDao esTopicMeetingDao;
        private final EsMeetingAgendaItemDao agendaItemDao;
        private final EsMeetingDao esMeetingDao;
        private final EsTopicSpaceDao topicSpaceDao;
        private final EsTopicRelationshipDao relationshipDao;
        private final EsTopicCurationDao curationDao;
        private final TopicSpaceAccessService topicSpaceAccessService;
        private final EsTopicViewHistoryService topicViewHistoryService;

        public EsTopicDetailServlet() {
                this.authFlowService = new AuthFlowService();
                this.esTopicDao = new EsTopicDao();
                this.topicNeighborhoodDao = new EsTopicNeighborhoodDao();
                this.campaignDao = new EsCampaignDao();
                this.campaignTopicDao = new EsCampaignTopicDao();
                this.subscriptionDao = new EsSubscriptionDao();
                this.esTopicMeetingDao = new EsTopicMeetingDao();
                this.agendaItemDao = new EsMeetingAgendaItemDao();
                this.esMeetingDao = new EsMeetingDao();
                this.topicSpaceDao = new EsTopicSpaceDao();
                this.relationshipDao = new EsTopicRelationshipDao();
                this.curationDao = new EsTopicCurationDao();
                this.topicSpaceAccessService = new TopicSpaceAccessService();
                this.topicViewHistoryService = new EsTopicViewHistoryService();
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

                String servletPath = trimToNull(request.getServletPath());
                boolean isLegacyTopicUrl = "/es/topic".equals(servletPath);
                if (isLegacyTopicUrl && !"emerging-standards".equalsIgnoreCase(topicSpace.getSpaceCode())) {
                        response.sendRedirect(buildSpaceTopicUrl(contextPath, topicSpace.getSpaceCode(), topicId,
                                        request.getQueryString()));
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
                String normalizedStage = orEmpty(topic.getStage());
                String normalizedNeighborhood = String.join(", ",
                                topicNeighborhoodDao.findNeighborhoodNamesByTopicId(topic.getEsTopicId()));
                List<EsTopicViewHistoryService.RecentlyViewedTopic> recentlyViewedTopics = List.of();

                if (authenticatedUser.isPresent()) {
                        Long viewerUserId = authenticatedUser.get().getUserId();
                        try {
                                topicViewHistoryService.recordAuthenticatedTopicView(viewerUserId, topicId);
                        } catch (RuntimeException ex) {
                                LOGGER.log(Level.WARNING, "Unable to record topic view for user/topic", ex);
                        }

                        Set<Long> visibleSpaceIds = topicSpaceAccessService.getVisibleSpaceIds(viewer);
                        recentlyViewedTopics = topicViewHistoryService
                                        .findRecentAuthenticatedTopicViews(viewerUserId, 10)
                                        .stream()
                                        .filter(item -> item.topicId() != null
                                                        && item.topicSpaceId() != null
                                                        && visibleSpaceIds.contains(item.topicSpaceId()))
                                        .limit(10)
                                        .toList();
                }

                List<EsMeeting> upcomingMeetings = List.of();
                List<EsMeetingAgendaItem> agendaItems = agendaItemDao.findByTopicId(topicId);
                if (!agendaItems.isEmpty()) {
                        List<Long> agendaMeetingIds = agendaItems.stream()
                                        .map(EsMeetingAgendaItem::getEsMeetingId)
                                        .distinct()
                                        .collect(Collectors.toList());
                        List<EsMeeting> tempMeetings = new ArrayList<>();
                        for (Long meetingIdValue : agendaMeetingIds) {
                                EsMeeting meetingRow = esMeetingDao.findById(meetingIdValue).orElse(null);
                                if (meetingRow != null && meetingRow.getStatus() != EsMeeting.MeetingStatus.CANCELLED) {
                                        tempMeetings.add(meetingRow);
                                }
                        }
                        tempMeetings = topicSpaceAccessService.filterVisibleMeetings(viewer, tempMeetings);
                        tempMeetings.sort(Comparator.comparing(EsMeeting::getScheduledStart,
                                        Comparator.nullsLast(Comparator.naturalOrder())));
                        upcomingMeetings = tempMeetings.stream()
                                        .filter(m -> m.getScheduledStart() != null
                                                        && !m.getScheduledStart().toLocalDate()
                                                                        .isBefore(LocalDate.now()))
                                        .collect(Collectors.toList());
                }

                Optional<EsTopicMeeting> topicMeetingSeriesOpt = esTopicMeetingDao.findByTopicId(topicId);
                if (topicMeetingSeriesOpt.isPresent()) {
                        List<EsMeeting> directMeetings = esMeetingDao
                                        .findByEsTopicMeetingId(topicMeetingSeriesOpt.get().getEsTopicMeetingId())
                                        .stream()
                                        .filter(m -> m.getStatus() != EsMeeting.MeetingStatus.CANCELLED)
                                        .filter(m -> m.getScheduledStart() != null
                                                        && !m.getScheduledStart().toLocalDate()
                                                                        .isBefore(LocalDate.now()))
                                        .collect(Collectors.toList());
                        directMeetings = topicSpaceAccessService.filterVisibleMeetings(viewer, directMeetings);

                        LinkedHashMap<Long, EsMeeting> mergedByMeetingId = new LinkedHashMap<>();
                        for (EsMeeting m : upcomingMeetings) {
                                mergedByMeetingId.putIfAbsent(m.getEsMeetingId(), m);
                        }
                        for (EsMeeting m : directMeetings) {
                                mergedByMeetingId.putIfAbsent(m.getEsMeetingId(), m);
                        }
                        upcomingMeetings = mergedByMeetingId.values().stream()
                                        .sorted(Comparator.comparing(EsMeeting::getScheduledStart,
                                                        Comparator.nullsLast(Comparator.naturalOrder())))
                                        .collect(Collectors.toList());
                }

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
                        out.println("                <a class=\"aira-inline-link\" href=\"" + contextPath
                                        + "/es/topics\">All Topics</a>");
                        out.println("                <h1 class=\"aira-topic-title\">" + escapeHtml(topicName)
                                        + "</h1>");
                        out.println("                <p class=\"aira-topic-summary\">"
                                        + escapeHtml(description.isBlank() ? buildTopicSummary(topic) : description)
                                        + "</p>");
                        out.println("              </div>");
                        out.println("              <div class=\"aira-topic-actions\">");
                        if (canInteract) {
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
                        if (canManageTopic) {
                                out.println("                <a class=\"aira-button aira-button--secondary\" href=\""
                                                + contextPath + "/es/topic-manage/" + topicId
                                                + "\">Champion/Support &amp; Admin View</a>");
                        }
                        out.println("              </div>");
                        out.println("            </div>");
                        out.println("            <div class=\"aira-topic-meta\" aria-label=\"Topic metadata\">");
                        out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Stage</span><span class=\"aira-meta-chip__value\">"
                                        + escapeHtml(normalizedStage.isBlank() ? "Other" : normalizedStage)
                                        + "</span></span>");
                        out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Path</span><span class=\"aira-meta-chip__value\">"
                                        + escapeHtml(trimToNull(topic.getPolicyStatus()) == null ? "To be defined"
                                                        : topic.getPolicyStatus())
                                        + "</span></span>");
                        out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Topic type</span><span class=\"aira-meta-chip__value\">"
                                        + escapeHtml(trimToNull(topic.getTopicType()) == null ? "Capability"
                                                        : topic.getTopicType())
                                        + "</span></span>");
                        out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Visibility</span><span class=\"aira-meta-chip__value\">"
                                        + escapeHtml(topicSpace.getVisibility() == null ? "Unknown"
                                                        : capitalize(topicSpace.getVisibility().name().toLowerCase()))
                                        + "</span></span>");
                        out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Neighborhood</span><span class=\"aira-meta-chip__value\">"
                                        + escapeHtml(normalizedNeighborhood.isBlank() ? "Not set"
                                                        : normalizedNeighborhood)
                                        + "</span></span>");
                        out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Champion</span><span class=\"aira-meta-chip__value\">TBD</span></span>");
                        out.println("              <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">Last updated</span><span class=\"aira-meta-chip__value\">Pending</span></span>");
                        out.println("            </div>");
                        out.println("          </header>");

                        out.println("          <section aria-labelledby=\"stage-path-title\" class=\"aira-stack\">");
                        out.println("            <h2 class=\"aira-section-title\" id=\"stage-path-title\">Stage and Path</h2>");
                        out.println("            <div class=\"aira-topic-status-grid\">");
                        out.println("              <article class=\"aira-topic-status-card aira-topic-status-card--stage\"><span class=\"aira-topic-status-card__icon\" aria-hidden=\"true\">S</span><div><p class=\"aira-topic-status-card__label\">Stage</p><p class=\"aira-topic-status-card__value\">"
                                        + escapeHtml(normalizedStage.isBlank() ? "Other" : normalizedStage)
                                        + "</p><p class=\"aira-topic-status-card__description\">"
                                        + escapeHtml(description.isBlank()
                                                        ? "Active concept development and refinement."
                                                        : description)
                                        + "</p></div></article>");
                        out.println("              <article class=\"aira-topic-status-card aira-topic-status-card--path\"><span class=\"aira-topic-status-card__icon\" aria-hidden=\"true\">P</span><div><p class=\"aira-topic-status-card__label\">Path</p><p class=\"aira-topic-status-card__value\">"
                                        + escapeHtml(trimToNull(topic.getPolicyStatus()) == null ? "Leadership Review"
                                                        : topic.getPolicyStatus())
                                        + "</p><p class=\"aira-topic-status-card__description\">Planning and sponsorship details can be added here.</p></div></article>");
                        out.println("            </div>");
                        out.println("          </section>");

                        out.println("          <section class=\"aira-section-card\" aria-labelledby=\"overview-title\">");
                        out.println(
                                        "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"overview-title\">Overview</h2>");
                        if (!trimToNull(topic.getConfluenceUrl()).isEmpty()) {
                                out.println("              <a class=\"aira-section-card__action\" href=\""
                                                + escapeHtml(topic.getConfluenceUrl())
                                                + "\" target=\"_blank\" rel=\"noopener\">Open full background</a>");
                        }
                        out.println("            </div>");
                        out.println("            <div class=\"aira-section-card__body aira-topic-overview\">");
                        out.println("              <div class=\"aira-topic-overview__text aira-prose\">");
                        if (description.isBlank()) {
                                out.println("                <p>Overview content will be added here.</p>");
                        } else {
                                out.println("                <p>" + escapeHtml(description) + "</p>");
                        }
                        out.println("              </div>");
                        out.println("              <div class=\"aira-alert aira-alert--info\" role=\"status\" aria-live=\"polite\">");
                        out.println("                <p class=\"aira-alert__title\">Background placeholder</p>");
                        out.println("                <p>Use this area later for source material, diagrams, or supporting notes.</p>");
                        out.println("              </div>");
                        out.println("            </div>");
                        out.println("          </section>");

                        out.println("          <section class=\"aira-section-card\" aria-labelledby=\"outcomes-title\">");
                        out.println(
                                        "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"outcomes-title\">Recent Outcomes</h2></div>");
                        out.println("            <div class=\"aira-section-card__body\"><div class=\"aira-outcome-list\">");
                        out.println(
                                        "              <article class=\"aira-outcome-row\"><span class=\"aira-outcome-row__type aira-badge aira-badge--info\">Decision</span><span class=\"aira-outcome-row__summary\">Topic layout now follows the AIRA demo structure.</span><span class=\"aira-outcome-row__source\">Layout</span><span class=\"aira-outcome-row__date\">Today</span><a class=\"aira-outcome-row__action\" href=\"#meeting-summary\">View topic layout</a></article>");
                        out.println(
                                        "              <article class=\"aira-outcome-row\"><span class=\"aira-outcome-row__type aira-badge aira-badge--success\">Next Action</span><span class=\"aira-outcome-row__summary\">Populate the placeholder sections with topic content.</span><span class=\"aira-outcome-row__source\">Implementation</span><span class=\"aira-outcome-row__date\">Pending</span><a class=\"aira-outcome-row__action\" href=\"#meeting-summary\">Open topic page</a></article>");
                        out.println(
                                        "              <article class=\"aira-outcome-row\"><span class=\"aira-outcome-row__type aira-badge aira-badge--subtle\">Key Point</span><span class=\"aira-outcome-row__summary\">Keep the shell on AIRA Web styles and leave local CSS behind.</span><span class=\"aira-outcome-row__source\">Design</span><span class=\"aira-outcome-row__date\">Pending</span><a class=\"aira-outcome-row__action\" href=\"#meeting-summary\">Review shell</a></article>");
                        out.println(
                                        "              <article class=\"aira-outcome-row\"><span class=\"aira-outcome-row__type aira-badge aira-badge--warning\">Direction</span><span class=\"aira-outcome-row__summary\">Add functionality later without changing the section structure.</span><span class=\"aira-outcome-row__source\">Planning</span><span class=\"aira-outcome-row__date\">Pending</span><a class=\"aira-outcome-row__action\" href=\"#meeting-summary\">Review roadmap</a></article>");
                        out.println("            </div></div>");
                        out.println("          </section>");

                        out.println("          <section class=\"aira-section-card\" aria-labelledby=\"meetings-summary-title\">");
                        out.println(
                                        "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"meetings-summary-title\">Meetings</h2></div>");
                        out.println("            <div class=\"aira-section-card__body aira-stack\">");
                        out.println("              <section><h3 class=\"aira-subsection-title\">Upcoming</h3><div class=\"aira-meeting-list\">");
                        if (upcomingMeetings.isEmpty()) {
                                out.println(
                                                "                <article class=\"aira-meeting-row\"><span class=\"aira-meeting-row__title\">Upcoming meetings will appear here.</span><span class=\"aira-meeting-row__meta\">Placeholder</span><a class=\"aira-meeting-row__action\" href=\"#meeting\">Open meeting</a></article>");
                        } else {
                                for (EsMeeting upcoming : upcomingMeetings) {
                                        String meetingName = orEmpty(upcoming.getMeetingName());
                                        String meetingDate = upcoming.getScheduledStart() == null ? "Pending"
                                                        : upcoming.getScheduledStart().format(AGENDA_DATE_FMT);
                                        out.println("                <article class=\"aira-meeting-row\"><span class=\"aira-meeting-row__title\">"
                                                        + escapeHtml(meetingName)
                                                        + "</span><span class=\"aira-meeting-row__meta\">"
                                                        + escapeHtml(meetingDate)
                                                        + "</span><a class=\"aira-meeting-row__action\" href=\""
                                                        + contextPath
                                                        + "/es/agenda?meetingId=" + upcoming.getEsMeetingId()
                                                        + "\">Open meeting</a></article>");
                                }
                        }
                        out.println("              </div></section>");
                        out.println("              <section><h3 class=\"aira-subsection-title\">Recent</h3><div class=\"aira-meeting-list\">");
                        out.println(
                                        "                <article class=\"aira-meeting-row\"><span class=\"aira-meeting-row__title\">Recent meeting summaries are not yet connected.</span><span class=\"aira-meeting-row__meta\">Placeholder</span><a class=\"aira-meeting-row__action\" href=\"#meeting\">View summary</a></article>");
                        out.println(
                                        "                <article class=\"aira-meeting-row\"><span class=\"aira-meeting-row__title\">Use this area later for archived notes and decisions.</span><span class=\"aira-meeting-row__meta\">Placeholder</span><a class=\"aira-meeting-row__action\" href=\"#meeting\">View summary</a></article>");
                        out.println("              </div></section>");
                        out.println("            </div>");
                        out.println("          </section>");

                        out.println("          <section class=\"aira-section-card\" aria-labelledby=\"related-title\">");
                        out.println(
                                        "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"related-title\">Related Topics</h2></div>");
                        out.println("            <div class=\"aira-section-card__body\"><div class=\"aira-relationship-list\">");
                        if (outboundRels.isEmpty() && inboundRels.isEmpty()) {
                                out.println(
                                                "              <article class=\"aira-relationship-row\"><span class=\"aira-relationship-row__title\">No related topics have been connected yet.</span><span class=\"aira-relationship-row__verb\">Placeholder</span><a class=\"aira-relationship-row__action\" href=\"#related-topic\">Open</a></article>");
                        } else {
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
                        }
                        out.println("            </div></div>");
                        out.println("          </section>");

                        out.println("          <section class=\"aira-section-card\" aria-labelledby=\"included-title\">");
                        out.println(
                                        "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"included-title\">Included In</h2></div>");
                        out.println("            <div class=\"aira-section-card__body\"><div class=\"aira-tag-list\">");
                        if (curatedByEntries.isEmpty()) {
                                out.println("              <span class=\"aira-tag aira-tag--outline\">Not yet connected</span>");
                        } else {
                                for (EsTopicCuration entry : curatedByEntries) {
                                        String curatorName = topicNameMap.getOrDefault(entry.getCuratorTopicId(),
                                                        "#" + entry.getCuratorTopicId());
                                        out.println("              <a class=\"aira-tag aira-tag--outline\" href=\""
                                                        + contextPath
                                                        + "/es/topic/" + entry.getCuratorTopicId() + "\">"
                                                        + escapeHtml(curatorName) + "</a>");
                                }
                        }
                        out.println("            </div></div>");
                        out.println("          </section>");

                        out.println("          <section class=\"aira-section-card\" aria-labelledby=\"resources-title\">");
                        out.println(
                                        "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"resources-title\">Resources</h2></div>");
                        out.println("            <div class=\"aira-section-card__body\"><div class=\"aira-resource-grid\">");
                        out.println(
                                        "              <a class=\"aira-resource-link\" href=\""
                                                        + (trimToNull(topic.getConfluenceUrl()) == null
                                                                        ? contextPath + "/es/topic/" + topicId
                                                                        : escapeHtml(topic.getConfluenceUrl()))
                                                        + "\"><span class=\"aira-resource-link__icon\" aria-hidden=\"true\">1</span><span><span class=\"aira-resource-link__title\">One-page summary</span><span class=\"aira-resource-link__description\">Current topic brief</span></span></a>");
                        out.println(
                                        "              <a class=\"aira-resource-link\" href=\"" + contextPath
                                                        + "/es/topic-manage/"
                                                        + topicId
                                                        + "\"><span class=\"aira-resource-link__icon\" aria-hidden=\"true\">D</span><span><span class=\"aira-resource-link__title\">Background notes</span><span class=\"aira-resource-link__description\">Topic setup and follow-up notes</span></span></a>");
                        out.println(
                                        "              <a class=\"aira-resource-link\" href=\"" + contextPath
                                                        + "/es/topics\"><span class=\"aira-resource-link__icon\" aria-hidden=\"true\">R</span><span><span class=\"aira-resource-link__title\">Topic list</span><span class=\"aira-resource-link__description\">Return to the topic index</span></span></a>");
                        out.println(
                                        "              <a class=\"aira-resource-link\" href=\"" + contextPath
                                                        + "/workspace\"><span class=\"aira-resource-link__icon\" aria-hidden=\"true\">S</span><span><span class=\"aira-resource-link__title\">Workspace</span><span class=\"aira-resource-link__description\">General collaboration workspace</span></span></a>");
                        out.println("            </div></div>");
                        out.println("          </section>");

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
                        out.println(
                                        "          <section class=\"aira-section-card\"><div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Recently viewed</h2></div><div class=\"aira-section-card__body aira-stack aira-stack--compact\">");
                        if (!canInteract) {
                                out.println("            <p class=\"aira-meta\">Sign in to keep a persistent recently viewed topic list.</p>");
                        } else if (recentlyViewedTopics.isEmpty()) {
                                out.println("            <p class=\"aira-meta\">No recent topic views yet.</p>");
                        } else {
                                for (EsTopicViewHistoryService.RecentlyViewedTopic viewedTopic : recentlyViewedTopics) {
                                        String viewedTopicName = orEmpty(viewedTopic.topicName());
                                        boolean isCurrentTopic = topicId.equals(viewedTopic.topicId());
                                        String recentMeta = isCurrentTopic
                                                        ? "Current topic"
                                                        : "Viewed " + formatRecentViewedAt(viewedTopic.lastViewedAt());
                                        String ariaCurrent = isCurrentTopic ? " aria-current=\"page\"" : "";
                                        out.println("            <a class=\"aira-recent-topic\" href=\""
                                                        + contextPath
                                                        + "/es/topic/" + viewedTopic.topicId() + "\""
                                                        + ariaCurrent
                                                        + "><span class=\"aira-recent-topic__icon\" aria-hidden=\"true\">"
                                                        + escapeHtml(initialForTopic(viewedTopicName))
                                                        + "</span><span><span class=\"aira-recent-topic__title\">"
                                                        + escapeHtml(viewedTopicName)
                                                        + "</span><span class=\"aira-recent-topic__meta\">"
                                                        + escapeHtml(recentMeta)
                                                        + "</span></span></a>");
                                }
                        }
                        out.println("          </div></section>");
                        out.println(
                                        "          <section class=\"aira-section-card\" id=\"private-intake\"><div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Next discussion</h2></div><div class=\"aira-section-card__body aira-stack aira-stack--compact\">");
                        if (meeting != null) {
                                out.println("            <p><strong>" + escapeHtml(orEmpty(meeting.getMeetingName()))
                                                + "</strong></p>");
                                String meetingSummary = trimToNull(meeting.getMeetingDescription());
                                out.println("            <p class=\"aira-meta\">"
                                                + escapeHtml(meetingSummary == null
                                                                ? "Private intake routed to topic champions"
                                                                : meetingSummary)
                                                + "</p>");
                        } else {
                                out.println("            <p><strong>Leadership Review</strong></p>");
                                out.println("            <p class=\"aira-meta\">Placeholder discussion route for future topic intake</p>");
                        }
                        out.println("            <a class=\"aira-button aira-button--small aira-button--secondary\" href=\""
                                        + contextPath + "/es/meetings\">Open meeting</a>");
                        out.println("          </div></section>");
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

                                out.println("        function setFollowButtonState(isFollowed) {");
                                out.println("          if (!followButton) { return; }");
                                out.println("          followButton.setAttribute('data-followed', isFollowed ? '1' : '0');");
                                out.println("          followButton.textContent = isFollowed ? 'Unfollow' : 'Follow';");
                                out.println("          followButton.classList.toggle('aira-button--primary', !isFollowed);");
                                out.println("          followButton.classList.toggle('aira-button--tertiary', isFollowed);");
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

        private String buildTopicSummary(EsCampaignTopicBrowseRow topic) {
                List<String> parts = new ArrayList<>();
                String stage = trimToNull(topic.getStage());
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

        private String buildSpaceTopicUrl(String contextPath, String spaceCode, Long topicId, String queryString) {
                StringBuilder url = new StringBuilder(contextPath)
                                .append("/spaces/")
                                .append(urlEncodePathSegment(orEmpty(spaceCode)))
                                .append("/topic/")
                                .append(topicId);
                String normalizedQuery = trimToNull(queryString);
                if (normalizedQuery != null) {
                        url.append('?').append(normalizedQuery);
                }
                return url.toString();
        }

        private String urlEncodePathSegment(String value) {
                return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
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
        private static final DateTimeFormatter RECENT_VIEW_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

        private String formatRecentViewedAt(java.time.LocalDateTime viewedAt) {
                if (viewedAt == null) {
                        return "recently";
                }
                return viewedAt.format(RECENT_VIEW_FMT);
        }

        private String initialForTopic(String topicName) {
                String normalized = trimToNull(topicName);
                if (normalized == null) {
                        return "T";
                }
                return normalized.substring(0, 1).toUpperCase();
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
