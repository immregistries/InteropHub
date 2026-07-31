package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.dao.EsNeighborhoodDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicBoardDefinitionDao;
import org.airahub.interophub.dao.EsTopicNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicPathDefinitionDao;
import org.airahub.interophub.dao.EsTopicStageDefinitionDao;
import org.airahub.interophub.model.EsNeighborhood;
import org.airahub.interophub.model.EsTopicBoardDefinition;
import org.airahub.interophub.model.EsTopicPathDefinition;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicStageDefinition;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicBoardService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.immregistries.aira.web.AiraPage;

public class EsTopicsServlet extends HttpServlet {

    private static final String VIEW_ALL = "all";
    private static final String VIEW_MY_TOPICS = "my-topics";
    private static final String DEFAULT_SPACE_CODE = "emerging-standards";
    private static final String UNCATEGORIZED_LABEL = "Uncategorized";

    private final AuthFlowService authFlowService;
    private final EsTopicDao esTopicDao;
    private final EsNeighborhoodDao esNeighborhoodDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicNeighborhoodDao topicNeighborhoodDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsTopicStageDefinitionDao topicStageDefinitionDao;
    private final EsTopicPathDefinitionDao topicPathDefinitionDao;
    private final EsTopicBoardDefinitionDao topicBoardDefinitionDao;
    private final TopicBoardService topicBoardService;
    private final TopicSpaceAccessService topicSpaceAccessService;

    public EsTopicsServlet() {
        this.authFlowService = new AuthFlowService();
        this.esTopicDao = new EsTopicDao();
        this.esNeighborhoodDao = new EsNeighborhoodDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicNeighborhoodDao = new EsTopicNeighborhoodDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.topicStageDefinitionDao = new EsTopicStageDefinitionDao();
        this.topicPathDefinitionDao = new EsTopicPathDefinitionDao();
        this.topicBoardDefinitionDao = new EsTopicBoardDefinitionDao();
        this.topicBoardService = new TopicBoardService();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();
        String view = trimToNull(request.getParameter("view"));
        String requestedSpaceCode = trimToNull(request.getParameter("space"));
        String neighborhoodParam = trimToNull(request.getParameter("n"));
        Long stageDefinitionIdParam = parseLong(request.getParameter("s"));
        Long pathDefinitionIdParam = parseLong(request.getParameter("p"));
        String query = trimToNull(request.getParameter("q"));

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        User viewer = authenticatedUser.orElse(null);

        List<EsTopicSpace> visibleSpaces = topicSpaceAccessService
                .filterVisibleSpaces(viewer, topicSpaceDao.findAllActiveOrdered());
        EsTopicSpace selectedSpace = findSelectedSpace(visibleSpaces, requestedSpaceCode);
        if (selectedSpace == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.println("<!doctype html>");
                out.println("<html lang=\"en\"><head><meta charset=\"utf-8\" />");
                out.println("<title>Topic Spaces Not Found - InteropHub</title></head>");
                out.println("<body><p>No visible Topic Spaces are available.</p></body></html>");
            }
            return;
        }

        String selectedSpaceCode = selectedSpace.getSpaceCode();
        Long selectedSpaceId = selectedSpace.getEsTopicSpaceId();

        Long authenticatedUserId = authenticatedUser.map(User::getUserId).orElse(null);
        String authenticatedEmailNormalized = authenticatedUser
                .map(User::getEmailNormalized)
                .map(this::trimToNull)
                .orElse(null);

        List<EsNeighborhood> neighborhoods = topicSpaceAccessService
                .filterVisibleNeighborhoods(viewer, esNeighborhoodDao.findAllActive()).stream()
                .filter(n -> selectedSpaceId.equals(n.getEsTopicSpaceId()))
                .collect(Collectors.toList());
        List<EsTopicStageDefinition> stageDefinitions = topicStageDefinitionDao
                .findAllOrderedBySpaceId(selectedSpaceId).stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .collect(Collectors.toList());
        List<EsTopicPathDefinition> pathDefinitions = topicPathDefinitionDao
                .findAllOrderedBySpaceId(selectedSpaceId).stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                .collect(Collectors.toList());

        List<EsCampaignTopicBrowseRow> allRows = topicSpaceAccessService
                .filterVisibleTopicRows(viewer, esTopicDao.findAllActiveBrowseRowsOrdered());
        Map<Long, Long> topicSpaceIdsByTopicId = esTopicDao.findSpaceIdsByTopicIds(allRows.stream()
                .map(EsCampaignTopicBrowseRow::getEsTopicId)
                .collect(Collectors.toList()));
        allRows = allRows.stream()
                .filter(row -> selectedSpaceId.equals(topicSpaceIdsByTopicId.get(row.getEsTopicId())))
                .collect(Collectors.toList());
        applyCanonicalNeighborhoods(allRows);
        List<Long> allTopicIds = allRows.stream().map(EsCampaignTopicBrowseRow::getEsTopicId)
                .collect(Collectors.toList());

        Set<Long> followedTopicIds = Set.of();
        if (authenticatedUser.isPresent()) {
            followedTopicIds = subscriptionDao.findActiveTopicIdsByUserOrEmailAndTopicIds(
                    authenticatedUserId,
                    authenticatedEmailNormalized,
                    allTopicIds);
        }

        List<EsCampaignTopicBrowseRow> filteredRows = applySearchFilter(allRows, query);
        boolean searchActive = query != null;

        Map<String, String> activeNeighborhoodLookup = buildNeighborhoodLookup(neighborhoods);
        String selectedNeighborhood = findMatchingNeighborhoodName(neighborhoodParam, neighborhoods,
                activeNeighborhoodLookup);

        Map<Long, Integer> stageCounts = buildDefinitionCounts(filteredRows,
                EsCampaignTopicBrowseRow::getEsTopicStageDefinitionId);
        Map<Long, Integer> pathCounts = buildDefinitionCounts(filteredRows,
                EsCampaignTopicBrowseRow::getEsTopicPathDefinitionId);
        Map<String, Integer> neighborhoodCounts = buildNeighborhoodCounts(filteredRows, neighborhoods,
                activeNeighborhoodLookup);

        boolean hasActiveFilter = stageDefinitionIdParam != null || pathDefinitionIdParam != null
                || selectedNeighborhood != null;
        boolean myTopicsView = VIEW_MY_TOPICS.equalsIgnoreCase(view);
        boolean showOverview = !myTopicsView && !VIEW_ALL.equalsIgnoreCase(view)
                && query == null && !hasActiveFilter;

        List<EsCampaignTopicBrowseRow> pageRows = List.of();
        if (!showOverview) {
            pageRows = resolvePageRows(filteredRows, myTopicsView, selectedNeighborhood, stageDefinitionIdParam,
                    pathDefinitionIdParam, activeNeighborhoodLookup, followedTopicIds);
            pageRows = dedupeAndSortRows(pageRows);
        }

        String stageName = findStageName(stageDefinitions, stageDefinitionIdParam);
        String pathName = findPathName(pathDefinitions, pathDefinitionIdParam);

        Optional<TopicBoardService.BoardView> primaryBoard = selectedSpace.getPrimaryEsTopicBoardDefinitionId() == null
                ? Optional.empty()
                : topicBoardDefinitionDao.findById(selectedSpace.getPrimaryEsTopicBoardDefinitionId())
                        .map(EsTopicBoardDefinition::getBoardCode)
                        .flatMap(boardCode -> topicBoardService.loadBoardByCodeForDisplay(boardCode, viewer));

        response.setContentType("text/html;charset=UTF-8");
        AiraPage page = InteropAiraPageFactory.base(request, selectedSpace.getSpaceName() + " Topics - InteropHub")
                .applicationSubtitle("Topics")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.topicsMeetingsContext(
                        selectedSpace.getSpaceName(),
                        selectedSpaceCode,
                        true,
                        false))
                .build();

        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container--wide aira-stack\">");

            renderPageHeader(out, selectedSpace, myTopicsView, selectedNeighborhood, stageName, pathName,
                    pageRows.size(), showOverview, query);

            out.println("      <div class=\"aira-right-rail-layout\">");
            out.println("        <div class=\"aira-stack\">");

            if (showOverview && primaryBoard.isPresent()) {
                renderBoard(out, contextPath, primaryBoard.get(), authenticatedUser.isPresent());
            }

            if (showOverview) {
                renderOverview(out, selectedSpace, stageDefinitions, pathDefinitions, neighborhoods);
            } else if (myTopicsView && authenticatedUser.isEmpty()) {
                out.println("          <p class=\"aira-alert aira-alert--info\"><a href=\"" + contextPath
                        + "/home\">Log in</a> to view topics you follow.</p>");
            } else if (pageRows.isEmpty()) {
                out.println(
                        "          <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No active topics found for this view.</p></div>");
            } else {
                renderTopicList(out, contextPath, pageRows, followedTopicIds);
            }

            out.println("        </div>");

            renderFilterRail(out, contextPath, selectedSpaceCode, query, view, stageDefinitions, pathDefinitions,
                    neighborhoods, stageDefinitionIdParam, pathDefinitionIdParam, selectedNeighborhood,
                    activeNeighborhoodLookup, stageCounts, pathCounts, neighborhoodCounts, searchActive,
                    authenticatedUser.isPresent(), myTopicsView, followedTopicIds.size());

            out.println("      </div>");
            out.println("    </div>");
            page.writeEnd(out);
        }
    }

    private void renderPageHeader(PrintWriter out, EsTopicSpace selectedSpace,
            boolean myTopicsView, String selectedNeighborhood, String stageName, String pathName, int topicCount,
            boolean showOverview, String query) {
        if (showOverview) {
            return;
        }

        String selectedSpaceName = orEmpty(selectedSpace.getSpaceName());

        String title;
        if (myTopicsView) {
            title = "My Topics";
        } else if (stageName != null) {
            title = "Stage: " + stageName;
        } else if (pathName != null) {
            title = "Advancement Path: " + pathName;
        } else if (selectedNeighborhood != null) {
            title = "Neighborhood: " + selectedNeighborhood;
        } else {
            title = selectedSpaceName + " Topics";
        }

        out.println("      <div class=\"aira-page-header\">");
        out.println("        <div>");
        out.println("          <h1 class=\"aira-page-title\">" + escapeHtml(title) + "</h1>");
        String intro = topicCount + " active topic" + (topicCount == 1 ? "" : "s") + (query != null
                ? " matching \"" + escapeHtml(query) + "\""
                : "");
        out.println("          <p class=\"aira-page-intro\">" + intro + "</p>");
        out.println("        </div>");
        out.println("      </div>");
    }

    private void renderOverview(PrintWriter out, EsTopicSpace selectedSpace,
            List<EsTopicStageDefinition> stageDefinitions,
            List<EsTopicPathDefinition> pathDefinitions, List<EsNeighborhood> neighborhoods) {
        String stageConceptDescription = trimToNull(selectedSpace.getStageConceptDescription());
        String pathConceptDescription = trimToNull(selectedSpace.getPathConceptDescription());

        out.println("          <div class=\"aira-card-grid\">");

        out.println("            <article class=\"aira-panel\">");
        out.println(
                "              <div class=\"aira-panel__header\"><h2 class=\"aira-panel__title\">Stages</h2></div>");
        out.println("              <div class=\"aira-panel__body aira-stack aira-stack--compact\">");
        out.println("                <p class=\"aira-meta\">" + escapeHtml(stageConceptDescription == null
                ? EsTopicSpace.DEFAULT_STAGE_CONCEPT_DESCRIPTION
                : stageConceptDescription) + "</p>");
        if (stageDefinitions.isEmpty()) {
            out.println("                <p class=\"aira-meta\">No stages configured for this Topic Space yet.</p>");
        } else {
            for (EsTopicStageDefinition stage : stageDefinitions) {
                out.println("                <div><strong>" + escapeHtml(orEmpty(stage.getStageName()))
                        + "</strong><p class=\"aira-meta\">"
                        + escapeHtml(orEmpty(stage.getStageDescription())) + "</p></div>");
            }
        }
        out.println("              </div>");
        out.println("            </article>");

        out.println("            <article class=\"aira-panel\">");
        out.println(
                "              <div class=\"aira-panel__header\"><h2 class=\"aira-panel__title\">Advancement Paths</h2></div>");
        out.println("              <div class=\"aira-panel__body aira-stack aira-stack--compact\">");
        out.println("                <p class=\"aira-meta\">" + escapeHtml(pathConceptDescription == null
                ? EsTopicSpace.DEFAULT_PATH_CONCEPT_DESCRIPTION
                : pathConceptDescription) + "</p>");
        if (pathDefinitions.isEmpty()) {
            out.println(
                    "                <p class=\"aira-meta\">No advancement paths configured for this Topic Space yet.</p>");
        } else {
            for (EsTopicPathDefinition path : pathDefinitions) {
                out.println("                <div><strong>" + escapeHtml(orEmpty(path.getPathName()))
                        + "</strong><p class=\"aira-meta\">"
                        + escapeHtml(orEmpty(path.getPathDescription())) + "</p></div>");
            }
        }
        out.println("              </div>");
        out.println("            </article>");

        out.println("            <article class=\"aira-panel\">");
        out.println(
                "              <div class=\"aira-panel__header\"><h2 class=\"aira-panel__title\">Neighborhoods</h2></div>");
        out.println("              <div class=\"aira-panel__body aira-stack aira-stack--compact\">");
        if (neighborhoods.isEmpty()) {
            out.println("                <p class=\"aira-meta\">No active neighborhoods configured yet.</p>");
        } else {
            for (EsNeighborhood neighborhood : neighborhoods) {
                String name = trimToNull(neighborhood.getNeighborhoodName());
                if (name == null) {
                    continue;
                }
                String description = trimToNull(neighborhood.getDescription());
                out.println("                <div><strong>" + escapeHtml(name) + "</strong><p class=\"aira-meta\">"
                        + escapeHtml(description == null ? "No description provided." : description)
                        + "</p></div>");
            }
        }
        out.println("              </div>");
        out.println("            </article>");

        out.println("          </div>");
    }

    private void renderBoard(PrintWriter out, String contextPath, TopicBoardService.BoardView boardView,
            boolean canEdit) {
        List<EsTopicStageDefinition> stages = boardView.displayedStages();
        List<EsTopicPathDefinition> paths = boardView.displayedPaths();
        Map<TopicBoardService.CellKey, List<TopicBoardService.TopicCard>> cardsByCell = boardView.cardsByCell();

        out.println("          <section class=\"aira-section-card\">");
        out.println("            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">"
                + escapeHtml(orEmpty(boardView.board().getBoardName())) + "</h2>");
        if (canEdit) {
            out.println("              <a class=\"aira-section-card__action\" href=\"" + contextPath
                    + "/es/board/" + escapeHtml(orEmpty(boardView.board().getBoardCode())) + "\">Edit board</a>");
        }
        out.println("            </div>");
        out.println("            <div class=\"aira-section-card__body\">");

        if (stages.isEmpty()) {
            out.println(
                    "              <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">This board has no stages configured yet.</p></div>");
            out.println("            </div>");
            out.println("          </section>");
            return;
        }

        boolean hasUnassignedPathCards = stages.stream().anyMatch(stage -> {
            List<TopicBoardService.TopicCard> cards = cardsByCell
                    .get(new TopicBoardService.CellKey(stage.getEsTopicStageDefinitionId(), null));
            return cards != null && !cards.isEmpty();
        });

        out.println("              <div class=\"aira-matrix-table-wrap\">");
        out.println("                <table class=\"aira-matrix-table\">");
        out.println("                  <thead>");
        out.println(
                "                    <tr><th class=\"aira-matrix-table__corner\" scope=\"col\"><div class=\"aira-matrix-table__header-inner\"><span class=\"aira-matrix-table__label\">Advancement Path</span></div></th>");
        for (EsTopicStageDefinition stage : stages) {
            out.println("<th class=\"aira-matrix-table__col-header\" scope=\"col\"><div class=\"aira-matrix-table__header-inner\"><span class=\"aira-matrix-table__label\">"
                    + escapeHtml(orEmpty(stage.getStageName())) + "</span></div></th>");
        }
        out.println("</tr>");
        out.println("                  </thead>");
        out.println("                  <tbody>");
        for (EsTopicPathDefinition path : paths) {
            out.println(
                    "                    <tr><th class=\"aira-matrix-table__row-header\" scope=\"row\"><div class=\"aira-matrix-table__header-inner\"><span class=\"aira-matrix-table__label\">"
                            + escapeHtml(orEmpty(path.getPathName())) + "</span></div></th>");
            for (EsTopicStageDefinition stage : stages) {
                renderBoardCell(out, contextPath, cardsByCell.get(new TopicBoardService.CellKey(
                        stage.getEsTopicStageDefinitionId(), path.getEsTopicPathDefinitionId())));
            }
            out.println("</tr>");
        }
        if (hasUnassignedPathCards) {
            out.println(
                    "                    <tr><th class=\"aira-matrix-table__row-header\" scope=\"row\"><div class=\"aira-matrix-table__header-inner\"><span class=\"aira-matrix-table__label\">No Advancement Path</span></div></th>");
            for (EsTopicStageDefinition stage : stages) {
                renderBoardCell(out, contextPath,
                        cardsByCell.get(new TopicBoardService.CellKey(stage.getEsTopicStageDefinitionId(), null)));
            }
            out.println("</tr>");
        }
        out.println("                  </tbody>");
        out.println("                </table>");
        out.println("              </div>");
        out.println("            </div>");
        out.println("          </section>");
    }

    private void renderBoardCell(PrintWriter out, String contextPath,
            List<TopicBoardService.TopicCard> cards) {
        if (cards == null || cards.isEmpty()) {
            out.println("<td class=\"aira-matrix-table__cell\"></td>");
            return;
        }
        out.println("<td class=\"aira-matrix-table__cell\"><div class=\"aira-stack aira-stack--compact\">");
        for (TopicBoardService.TopicCard card : cards) {
            out.println("<article class=\"aira-entity-card\"><a class=\"aira-entity-card__title\" href=\""
                    + contextPath + "/es/topic/" + card.topicId() + "\">"
                    + escapeHtml(card.topicName()) + "</a></article>");
        }
        out.println("</div></td>");
    }

    private void renderTopicList(PrintWriter out, String contextPath, List<EsCampaignTopicBrowseRow> rows,
            Set<Long> followedTopicIds) {
        out.println("          <div class=\"aira-choice-list\">");
        for (EsCampaignTopicBrowseRow row : rows) {
            String topicName = orEmpty(row.getTopicName());
            String description = orEmpty(row.getDescription());
            String preview = description.length() <= 160 ? description : description.substring(0, 157) + "...";
            boolean followed = followedTopicIds.contains(row.getEsTopicId());
            List<String> neighborhoodNames = splitNeighborhoods(row.getNeighborhood());

            out.println("            <a class=\"aira-choice-row aira-interactive-card"
                    + (followed ? " is-followed" : "") + "\" href=\"" + contextPath + "/es/topic/"
                    + row.getEsTopicId() + "\">");
            out.println("              <div class=\"aira-choice-row__control\">"
                    + "<span class=\"aira-resource-link__icon\" aria-hidden=\"true\">"
                    + escapeHtml(initialFor(topicName)) + "</span></div>");
            out.println("              <div>");
            out.println("                <p class=\"aira-choice-row__title\">" + escapeHtml(topicName) + "</p>");
            out.println(
                    "                <p class=\"aira-choice-row__description\">" + escapeHtml(preview) + "</p>");
            out.println("                <div class=\"aira-tag-list\">");
            if (followed) {
                out.println("                  <span class=\"aira-tag aira-tag--info\">Following</span>");
            }
            for (String neighborhoodName : neighborhoodNames) {
                out.println("                  <span class=\"aira-tag\">" + escapeHtml(neighborhoodName)
                        + "</span>");
            }
            out.println("                </div>");
            out.println("              </div>");
            out.println("            </a>");
        }
        out.println("          </div>");
    }

    private void renderFilterRail(PrintWriter out, String contextPath, String selectedSpaceCode, String query,
            String view, List<EsTopicStageDefinition> stageDefinitions, List<EsTopicPathDefinition> pathDefinitions,
            List<EsNeighborhood> neighborhoods, Long selectedStageId, Long selectedPathId,
            String selectedNeighborhood, Map<String, String> activeNeighborhoodLookup,
            Map<Long, Integer> stageCounts, Map<Long, Integer> pathCounts, Map<String, Integer> neighborhoodCounts,
            boolean searchActive, boolean authenticated, boolean myTopicsView, int followedTopicCount) {
        out.println("        <aside class=\"aira-right-rail\" aria-label=\"Topic filters\">");

        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Search</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        out.println(
                "              <form class=\"aira-inline-form\" method=\"get\" action=\"" + contextPath + "/es/topics\">");
        out.println("                <input type=\"hidden\" name=\"space\" value=\"" + escapeHtml(selectedSpaceCode)
                + "\" />");
        if (trimToNull(view) != null) {
            out.println("                <input type=\"hidden\" name=\"view\" value=\"" + escapeHtml(view) + "\" />");
        }
        if (selectedStageId != null) {
            out.println("                <input type=\"hidden\" name=\"s\" value=\"" + selectedStageId + "\" />");
        }
        if (selectedPathId != null) {
            out.println("                <input type=\"hidden\" name=\"p\" value=\"" + selectedPathId + "\" />");
        }
        if (selectedNeighborhood != null) {
            out.println("                <input type=\"hidden\" name=\"n\" value=\"" + escapeHtml(selectedNeighborhood)
                    + "\" />");
        }
        out.println("                <input class=\"aira-input\" type=\"search\" name=\"q\" value=\""
                + escapeHtml(orEmpty(query))
                + "\" placeholder=\"Search by topic name or description\" autocomplete=\"off\" />");
        out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Search</button>");
        out.println("              </form>");
        out.println("            </div>");
        out.println("          </section>");

        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Explore</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        out.println("              <nav class=\"aira-sidebar-nav\" aria-label=\"Explore topics\">");
        out.println("                <a class=\"aira-sidebar-link\" href=\""
                + buildTopicsUrl(contextPath, selectedSpaceCode, VIEW_ALL, null, null, null, query) + "\""
                + (!myTopicsView ? " aria-current=\"page\"" : "") + ">All Topics</a>");
        String myTopicsLabel = authenticated ? "My Topics (" + followedTopicCount + ")" : "My Topics";
        out.println("                <a class=\"aira-sidebar-link\" href=\""
                + buildTopicsUrl(contextPath, selectedSpaceCode, VIEW_MY_TOPICS, null, null, null, query) + "\""
                + (myTopicsView ? " aria-current=\"page\"" : "") + ">" + escapeHtml(myTopicsLabel) + "</a>");
        out.println("              </nav>");
        out.println("            </div>");
        out.println("          </section>");

        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Stage</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        out.println("              <nav class=\"aira-sidebar-nav\" aria-label=\"Filter by stage\">");
        for (EsTopicStageDefinition stage : stageDefinitions) {
            int count = stageCounts.getOrDefault(stage.getEsTopicStageDefinitionId(), 0);
            if (searchActive && count <= 0) {
                continue;
            }
            boolean active = stage.getEsTopicStageDefinitionId().equals(selectedStageId);
            out.println("                <a class=\"aira-sidebar-link\" href=\""
                    + buildTopicsUrl(contextPath, selectedSpaceCode, null, stage.getEsTopicStageDefinitionId(), null,
                            null, query)
                    + "\"" + (active ? " aria-current=\"page\"" : "") + ">"
                    + escapeHtml(orEmpty(stage.getStageName())) + " (" + count + ")</a>");
        }
        out.println("              </nav>");
        out.println("            </div>");
        out.println("          </section>");

        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Advancement Path</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        out.println("              <nav class=\"aira-sidebar-nav\" aria-label=\"Filter by path\">");
        for (EsTopicPathDefinition path : pathDefinitions) {
            int count = pathCounts.getOrDefault(path.getEsTopicPathDefinitionId(), 0);
            if (searchActive && count <= 0) {
                continue;
            }
            boolean active = path.getEsTopicPathDefinitionId().equals(selectedPathId);
            out.println("                <a class=\"aira-sidebar-link\" href=\""
                    + buildTopicsUrl(contextPath, selectedSpaceCode, null, null, path.getEsTopicPathDefinitionId(),
                            null, query)
                    + "\"" + (active ? " aria-current=\"page\"" : "") + ">"
                    + escapeHtml(orEmpty(path.getPathName())) + " (" + count + ")</a>");
        }
        out.println("              </nav>");
        out.println("            </div>");
        out.println("          </section>");

        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Neighborhood</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        out.println("              <nav class=\"aira-sidebar-nav\" aria-label=\"Filter by neighborhood\">");
        for (EsNeighborhood neighborhood : neighborhoods) {
            String neighborhoodName = trimToNull(neighborhood.getNeighborhoodName());
            if (neighborhoodName == null) {
                continue;
            }
            int count = neighborhoodCounts.getOrDefault(neighborhoodName, 0);
            if (searchActive && count <= 0) {
                continue;
            }
            boolean active = equalsIgnoreCaseTrimmed(selectedNeighborhood, neighborhoodName);
            out.println("                <a class=\"aira-sidebar-link\" href=\""
                    + buildTopicsUrl(contextPath, selectedSpaceCode, null, null, null, neighborhoodName, query)
                    + "\"" + (active ? " aria-current=\"page\"" : "") + ">"
                    + escapeHtml(neighborhoodName) + " (" + count + ")</a>");
        }
        int uncategorizedCount = neighborhoodCounts.getOrDefault(UNCATEGORIZED_LABEL, 0);
        if (!searchActive || uncategorizedCount > 0) {
            boolean active = equalsIgnoreCaseTrimmed(selectedNeighborhood, UNCATEGORIZED_LABEL);
            out.println("                <a class=\"aira-sidebar-link\" href=\""
                    + buildTopicsUrl(contextPath, selectedSpaceCode, null, null, null, UNCATEGORIZED_LABEL, query)
                    + "\"" + (active ? " aria-current=\"page\"" : "") + ">"
                    + UNCATEGORIZED_LABEL + " (" + uncategorizedCount + ")</a>");
        }
        out.println("              </nav>");
        out.println("            </div>");
        out.println("          </section>");

        out.println("        </aside>");
    }

    private List<EsCampaignTopicBrowseRow> applySearchFilter(List<EsCampaignTopicBrowseRow> rows, String query) {
        if (query == null) {
            return rows;
        }
        String search = query.toLowerCase(Locale.ROOT);
        return rows.stream()
                .filter(row -> ((orEmpty(row.getTopicName()) + " " + orEmpty(row.getDescription()))
                        .toLowerCase(Locale.ROOT)
                        .contains(search)))
                .collect(Collectors.toList());
    }

    private List<EsCampaignTopicBrowseRow> resolvePageRows(List<EsCampaignTopicBrowseRow> rows, boolean myTopicsView,
            String selectedNeighborhood, Long selectedStageId, Long selectedPathId,
            Map<String, String> activeNeighborhoodLookup, Set<Long> followedTopicIds) {
        List<EsCampaignTopicBrowseRow> result = rows;
        if (myTopicsView) {
            result = result.stream()
                    .filter(row -> followedTopicIds.contains(row.getEsTopicId()))
                    .collect(Collectors.toList());
        }
        if (selectedStageId != null) {
            result = result.stream()
                    .filter(row -> selectedStageId.equals(row.getEsTopicStageDefinitionId()))
                    .collect(Collectors.toList());
        }
        if (selectedPathId != null) {
            result = result.stream()
                    .filter(row -> selectedPathId.equals(row.getEsTopicPathDefinitionId()))
                    .collect(Collectors.toList());
        }
        if (selectedNeighborhood != null) {
            result = result.stream()
                    .filter(row -> topicMatchesNeighborhood(row, selectedNeighborhood, activeNeighborhoodLookup))
                    .collect(Collectors.toList());
        }
        return result;
    }

    private List<EsCampaignTopicBrowseRow> dedupeAndSortRows(List<EsCampaignTopicBrowseRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<Long, EsCampaignTopicBrowseRow> uniqueByTopicId = new LinkedHashMap<>();
        for (EsCampaignTopicBrowseRow row : rows) {
            if (row == null || row.getEsTopicId() == null) {
                continue;
            }
            uniqueByTopicId.putIfAbsent(row.getEsTopicId(), row);
        }
        List<EsCampaignTopicBrowseRow> uniqueRows = new ArrayList<>(uniqueByTopicId.values());
        uniqueRows.sort((left, right) -> {
            String leftName = orEmpty(left.getTopicName()).trim();
            String rightName = orEmpty(right.getTopicName()).trim();
            int byName = leftName.compareToIgnoreCase(rightName);
            if (byName != 0) {
                return byName;
            }
            return Long.compare(left.getEsTopicId(), right.getEsTopicId());
        });
        return uniqueRows;
    }

    private Map<Long, Integer> buildDefinitionCounts(List<EsCampaignTopicBrowseRow> rows,
            java.util.function.Function<EsCampaignTopicBrowseRow, Long> idExtractor) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (EsCampaignTopicBrowseRow row : rows) {
            Long id = idExtractor.apply(row);
            if (id == null) {
                continue;
            }
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Integer> buildNeighborhoodCounts(List<EsCampaignTopicBrowseRow> rows,
            List<EsNeighborhood> neighborhoods, Map<String, String> activeNeighborhoodLookup) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (EsNeighborhood neighborhood : neighborhoods) {
            String name = trimToNull(neighborhood.getNeighborhoodName());
            if (name != null) {
                counts.put(name, 0);
            }
        }
        counts.put(UNCATEGORIZED_LABEL, 0);
        for (EsCampaignTopicBrowseRow row : rows) {
            List<String> names = getMatchedActiveNeighborhoods(row.getNeighborhood(), activeNeighborhoodLookup);
            if (names.isEmpty()) {
                counts.merge(UNCATEGORIZED_LABEL, 1, Integer::sum);
            } else {
                for (String name : names) {
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private String findStageName(List<EsTopicStageDefinition> stageDefinitions, Long stageDefinitionId) {
        if (stageDefinitionId == null) {
            return null;
        }
        for (EsTopicStageDefinition stage : stageDefinitions) {
            if (stageDefinitionId.equals(stage.getEsTopicStageDefinitionId())) {
                return orEmpty(stage.getStageName());
            }
        }
        return null;
    }

    private String findPathName(List<EsTopicPathDefinition> pathDefinitions, Long pathDefinitionId) {
        if (pathDefinitionId == null) {
            return null;
        }
        for (EsTopicPathDefinition path : pathDefinitions) {
            if (pathDefinitionId.equals(path.getEsTopicPathDefinitionId())) {
                return orEmpty(path.getPathName());
            }
        }
        return null;
    }

    private EsTopicSpace findSelectedSpace(List<EsTopicSpace> visibleSpaces, String requestedSpaceCode) {
        if (visibleSpaces == null || visibleSpaces.isEmpty()) {
            return null;
        }
        String normalizedRequested = trimToNull(requestedSpaceCode);
        if (normalizedRequested != null) {
            for (EsTopicSpace topicSpace : visibleSpaces) {
                if (equalsIgnoreCaseTrimmed(topicSpace.getSpaceCode(), normalizedRequested)) {
                    return topicSpace;
                }
            }
        }
        for (EsTopicSpace topicSpace : visibleSpaces) {
            if (equalsIgnoreCaseTrimmed(DEFAULT_SPACE_CODE, topicSpace.getSpaceCode())) {
                return topicSpace;
            }
        }
        return visibleSpaces.get(0);
    }

    private String buildTopicsUrl(String contextPath, String spaceCode, String view, Long stageDefinitionId,
            Long pathDefinitionId, String neighborhood, String query) {
        List<String> params = new ArrayList<>();
        if (trimToNull(spaceCode) != null) {
            params.add("space=" + urlEncode(spaceCode));
        }
        if (trimToNull(view) != null) {
            params.add("view=" + urlEncode(view));
        }
        if (stageDefinitionId != null) {
            params.add("s=" + stageDefinitionId);
        }
        if (pathDefinitionId != null) {
            params.add("p=" + pathDefinitionId);
        }
        if (trimToNull(neighborhood) != null) {
            params.add("n=" + urlEncode(neighborhood));
        }
        if (trimToNull(query) != null) {
            params.add("q=" + urlEncode(query));
        }
        if (params.isEmpty()) {
            return contextPath + "/es/topics";
        }
        return contextPath + "/es/topics?" + String.join("&", params);
    }

    private List<String> splitNeighborhoods(String neighborhoodRaw) {
        String normalized = trimToNull(neighborhoodRaw);
        if (normalized == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String part : normalized.split(",")) {
            String token = trimToNull(part);
            if (token != null) {
                names.add(token);
            }
        }
        return names;
    }

    private Map<String, String> buildNeighborhoodLookup(List<EsNeighborhood> neighborhoods) {
        Map<String, String> lookup = new LinkedHashMap<>();
        for (EsNeighborhood neighborhood : neighborhoods) {
            String name = trimToNull(neighborhood.getNeighborhoodName());
            if (name != null) {
                lookup.put(name.toLowerCase(Locale.ROOT), name);
            }
        }
        return lookup;
    }

    private List<String> getMatchedActiveNeighborhoods(String neighborhoodRaw,
            Map<String, String> activeNeighborhoodLookup) {
        List<String> matched = new ArrayList<>();
        for (String token : splitNeighborhoods(neighborhoodRaw)) {
            String canonical = activeNeighborhoodLookup.get(token.toLowerCase(Locale.ROOT));
            if (canonical != null
                    && matched.stream().noneMatch(existing -> equalsIgnoreCaseTrimmed(existing, canonical))) {
                matched.add(canonical);
            }
        }
        return matched;
    }

    private String findMatchingNeighborhoodName(String requested, List<EsNeighborhood> neighborhoods,
            Map<String, String> activeNeighborhoodLookup) {
        String normalizedRequested = trimToNull(requested);
        if (normalizedRequested == null) {
            return null;
        }
        if (equalsIgnoreCaseTrimmed(normalizedRequested, UNCATEGORIZED_LABEL)) {
            return UNCATEGORIZED_LABEL;
        }
        String canonical = activeNeighborhoodLookup.get(normalizedRequested.toLowerCase(Locale.ROOT));
        return canonical;
    }

    private boolean topicMatchesNeighborhood(EsCampaignTopicBrowseRow row, String selectedNeighborhood,
            Map<String, String> activeNeighborhoodLookup) {
        if (equalsIgnoreCaseTrimmed(selectedNeighborhood, UNCATEGORIZED_LABEL)) {
            return getMatchedActiveNeighborhoods(row.getNeighborhood(), activeNeighborhoodLookup).isEmpty();
        }
        return getMatchedActiveNeighborhoods(row.getNeighborhood(), activeNeighborhoodLookup).stream()
                .anyMatch(name -> equalsIgnoreCaseTrimmed(name, selectedNeighborhood));
    }

    private void applyCanonicalNeighborhoods(List<EsCampaignTopicBrowseRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> topicIds = rows.stream()
                .map(EsCampaignTopicBrowseRow::getEsTopicId)
                .collect(Collectors.toList());
        Map<Long, List<String>> namesByTopicId = topicNeighborhoodDao.findNeighborhoodNamesByTopicIds(topicIds);
        for (EsCampaignTopicBrowseRow row : rows) {
            List<String> names = namesByTopicId.get(row.getEsTopicId());
            row.setNeighborhood(names == null || names.isEmpty() ? null : String.join(", ", names));
        }
    }

    private String initialFor(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            return "?";
        }
        return trimmed.substring(0, 1).toUpperCase();
    }

    private boolean equalsIgnoreCaseTrimmed(String a, String b) {
        String normalizedA = trimToNull(a);
        String normalizedB = trimToNull(b);
        if (normalizedA == null || normalizedB == null) {
            return false;
        }
        return normalizedA.equalsIgnoreCase(normalizedB);
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

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
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
