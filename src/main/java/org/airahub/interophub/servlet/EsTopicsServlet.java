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
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignMeetingBrowseRow;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.dao.EsCommentDao;
import org.airahub.interophub.dao.EsNeighborhoodDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsComment;
import org.airahub.interophub.model.EsNeighborhood;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsTopicReviewService;

public class EsTopicsServlet extends HttpServlet {

    private static final String VIEW_OVERVIEW = "overview";
    private static final String VIEW_ALL = "all";
    private static final String VIEW_NEIGHBORHOOD = "neighborhood";
    private static final String VIEW_STAGE = "stage";
    private static final String VIEW_REVIEW = "review";
    private static final String VIEW_MY_TOPICS = "my-topics";
    private static final String VIEW_MEETINGS = "meetings";
    private static final String UNCATEGORIZED_LABEL = "Uncategorized";

    private static final List<String> STAGE_ORDER = List.of("Parked", "Monitor", "Gather", "Start", "Draft", "Pilot",
            "Rollout");

    private static final Map<String, String> STAGE_DESCRIPTIONS = Map.of(
            "Draft", "Draft topics are early-stage ideas gathering initial interest and problem framing.",
            "Gather", "Gather topics are collecting broader input from implementers and stakeholders.",
            "Monitor", "Monitor topics are active efforts being tracked for readiness and real-world momentum.",
            "Start", "Start topics are beginning active development work toward practical implementation.",
            "Pilot", "Pilot topics are in trial implementations to validate feasibility and workflow impact.",
            "Rollout", "Rollout topics are ready for broader adoption and implementation support.",
            "Parked", "Parked topics are intentionally paused while dependencies or timing constraints are addressed.");

    private final AuthFlowService authFlowService;
    private final EsTopicDao esTopicDao;
    private final EsNeighborhoodDao esNeighborhoodDao;
    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicMeetingMemberDao topicMeetingMemberDao;
    private final EsTopicReviewService reviewService;
    private final EsCommentDao commentDao;

    public EsTopicsServlet() {
        this.authFlowService = new AuthFlowService();
        this.esTopicDao = new EsTopicDao();
        this.esNeighborhoodDao = new EsNeighborhoodDao();
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
        this.reviewService = new EsTopicReviewService();
        this.commentDao = new EsCommentDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        String contextPath = request.getContextPath();
        String view = trimToNull(request.getParameter("view"));
        String neighborhoodParam = trimToNull(request.getParameter("n"));
        String stageParam = trimToNull(request.getParameter("s"));
        String reviewParam = trimToNull(request.getParameter("r"));
        String query = trimToNull(request.getParameter("q"));
        if (view == null) {
            view = (query == null) ? VIEW_OVERVIEW : VIEW_ALL;
        }

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        Optional<EsCampaign> campaign = campaignDao.findMostRecentActive();
        boolean canReview = authenticatedUser.isPresent() && campaign.isPresent();
        boolean requiresAuthView = VIEW_MY_TOPICS.equalsIgnoreCase(view) || VIEW_MEETINGS.equalsIgnoreCase(view);

        Long authenticatedUserId = authenticatedUser.map(User::getUserId).orElse(null);
        String authenticatedEmailNormalized = authenticatedUser
                .map(User::getEmailNormalized)
                .map(this::trimToNull)
                .orElse(null);

        List<EsNeighborhood> neighborhoods = esNeighborhoodDao.findAllActive();
        List<EsCampaignTopicBrowseRow> allRows = esTopicDao.findAllActiveBrowseRowsOrdered();
        List<Long> allTopicIds = allRows.stream().map(EsCampaignTopicBrowseRow::getEsTopicId)
                .collect(Collectors.toList());

        Set<Long> followedTopicIds = Set.of();
        Map<Long, EsCampaignMeetingBrowseRow> meetingByTopicId = Map.of();
        Map<Long, EsTopicMeetingMember> membershipByMeetingId = Map.of();
        if (authenticatedUser.isPresent()) {
            followedTopicIds = subscriptionDao.findActiveTopicIdsByUserOrEmailAndTopicIds(
                    authenticatedUserId,
                    authenticatedEmailNormalized,
                    allTopicIds);

            List<EsCampaignMeetingBrowseRow> meetingRows = campaignTopicDao.findAllActiveMeetingRowsOrdered();
            Map<Long, EsCampaignMeetingBrowseRow> meetingLookup = new LinkedHashMap<>();
            for (EsCampaignMeetingBrowseRow row : meetingRows) {
                if (!meetingLookup.containsKey(row.getEsTopicId())) {
                    meetingLookup.put(row.getEsTopicId(), row);
                }
            }
            meetingByTopicId = meetingLookup;

            if (!meetingRows.isEmpty()) {
                List<Long> meetingIds = meetingRows.stream().map(EsCampaignMeetingBrowseRow::getEsTopicMeetingId)
                        .collect(Collectors.toList());
                List<EsTopicMeetingMember> members = topicMeetingMemberDao.findByMeetingIdsAndUserOrEmail(
                        meetingIds,
                        authenticatedUserId,
                        authenticatedEmailNormalized);
                Map<Long, EsTopicMeetingMember> membershipLookup = new LinkedHashMap<>();
                for (EsTopicMeetingMember member : members) {
                    if (!membershipLookup.containsKey(member.getEsTopicMeetingId())) {
                        membershipLookup.put(member.getEsTopicMeetingId(), member);
                    }
                }
                membershipByMeetingId = membershipLookup;
            }
        }

        List<EsCampaignTopicBrowseRow> filteredRows = applySearchFilter(allRows, query);
        boolean searchActive = query != null;

        Map<Long, Integer> scoreByTopicId = Map.of();
        Map<Long, List<String>> userCommentsByTopicId = Map.of();
        if (canReview) {
            Long campaignId = campaign.get().getEsCampaignId();
            Long userId = authenticatedUser.get().getUserId();
            scoreByTopicId = reviewService.findUserScoresByTopicId(campaignId, userId);
            userCommentsByTopicId = buildUserCommentsByTopicId(commentDao.findByUserAndCampaign(campaignId, userId));
        }

        Map<String, String> activeNeighborhoodLookup = buildNeighborhoodLookup(neighborhoods);
        Map<String, Integer> stageCounts = buildStageCounts(filteredRows);
        Map<String, Integer> neighborhoodCounts = buildNeighborhoodCounts(filteredRows, neighborhoods,
                activeNeighborhoodLookup);

        String selectedNeighborhood = findMatchingNeighborhoodName(neighborhoodParam, neighborhoods,
                activeNeighborhoodLookup);
        String selectedStage = normalizeToCanonicalStage(stageParam);
        Integer selectedReviewScore = parseReviewScore(reviewParam);

        Map<Integer, Integer> reviewScoreCounts = buildReviewScoreCounts(filteredRows, scoreByTopicId);
        int notReviewedCount = countNotReviewed(filteredRows, scoreByTopicId);

        try (PrintWriter out = response.getWriter()) {
            out.println("<!doctype html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"utf-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />");
            out.println("  <title>Emerging Standards Topics - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("  <style>");
            out.println(
                    "    :root { --bg:#f6f7f8; --panel:#ffffff; --text:#0f1720; --muted:#5b6673; --border:#d5dde5; --accent:#0b6fb8; --accent-soft:#e6f1fb; }");
            out.println("    * { box-sizing:border-box; }");
            out.println(
                    "    body { margin:0; background:radial-gradient(circle at top left, #eef4f8 0, #f6f7f8 55%); color:var(--text); font-family:\"Segoe UI\", Tahoma, Geneva, Verdana, sans-serif; }");
            out.println("    .estp-shell { max-width:1300px; margin:0 auto; padding:1.25rem; }");
            out.println(
                    "    .estp-layout { display:grid; grid-template-columns:300px minmax(0,1fr); gap:1rem; align-items:start; }");
            out.println(
                    "    .estp-sidebar, .estp-main { background:var(--panel); border:1px solid var(--border); border-radius:14px; box-shadow:0 3px 10px rgba(15,23,32,0.05); }");
            out.println(
                    "    .estp-sidebar { position:sticky; top:1rem; overflow:hidden; max-height:calc(100vh - 2rem); display:flex; flex-direction:column; }");
            out.println(
                    "    .estp-sidebar-home { display:block; margin:0.8rem 0.8rem 0.25rem 0.8rem; padding:0.58rem 0.65rem; border:1px solid var(--border); border-radius:10px; background:#f7fbff; color:#084a79; text-decoration:none; font-weight:600; }");
            out.println("    .estp-sidebar-home:hover { background:#edf6ff; }");
            out.println("    .estp-sidebar h2 { margin:0; padding:1rem 1rem 0.5rem 1rem; font-size:1rem; }");
            out.println("    .estp-nav-group { padding:0 0.75rem 0.75rem 0.75rem; }");
            out.println("    .estp-nav-scroll { overflow-y:auto; padding-bottom:1rem; }");
            out.println("    .estp-nav-scroll::-webkit-scrollbar { width:10px; }");
            out.println(
                    "    .estp-nav-scroll::-webkit-scrollbar-thumb { background:#c8d6e3; border-radius:999px; border:2px solid #fff; }");
            out.println(
                    "    .estp-nav-title { margin:0.75rem 0.25rem 0.5rem 0.25rem; color:var(--muted); font-size:0.83rem; text-transform:uppercase; letter-spacing:0.04em; }");
            out.println(
                    "    .estp-nav-section { margin:0.6rem 0; border:1px solid var(--border); border-radius:10px; background:#fbfcfd; }");
            out.println(
                    "    .estp-nav-summary { list-style:none; cursor:pointer; padding:0.58rem 0.65rem; margin:0; display:flex; justify-content:space-between; align-items:center; }");
            out.println("    .estp-nav-summary::-webkit-details-marker { display:none; }");
            out.println("    .estp-nav-summary::after { content:'+'; font-weight:700; color:var(--muted); }");
            out.println("    .estp-nav-section[open] > .estp-nav-summary::after { content:'-'; }");
            out.println("    .estp-nav-links-wrap { padding:0 0.2rem 0.4rem 0.2rem; }");
            out.println(
                    "    .estp-nav-link { display:block; padding:0.58rem 0.65rem; border-radius:9px; color:var(--text); text-decoration:none; font-size:0.93rem; }");
            out.println("    .estp-nav-link:hover { background:#f2f5f8; }");
            out.println(
                    "    .estp-nav-link.is-active { background:var(--accent-soft); color:#084a79; font-weight:600; }");
            out.println("    .estp-main { padding:1.1rem 1.1rem 1.2rem 1.1rem; }");
            out.println("    .estp-title { margin:0 0 0.35rem 0; font-size:1.5rem; }");
            out.println("    .estp-subtitle { margin:0 0 1rem 0; color:var(--muted); }");
            out.println(
                    "    .estp-overview-card { border:1px solid var(--border); background:#fbfcfd; border-radius:12px; padding:0.8rem 0.9rem; margin:0 0 1rem 0; }");
            out.println("    .estp-overview-card p { margin:0; color:var(--muted); }");
            out.println(
                    "    .estp-overview-grid { display:grid; gap:0.9rem; grid-template-columns:repeat(auto-fit,minmax(260px,1fr)); margin:0 0 1rem 0; }");
            out.println(
                    "    .estp-overview-panel { border:1px solid var(--border); background:#fff; border-radius:12px; padding:0.85rem 0.95rem; }");
            out.println("    .estp-overview-panel h3 { margin:0 0 0.7rem 0; font-size:1rem; }");
            out.println("    .estp-overview-list { margin:0; padding-left:1.1rem; display:grid; gap:0.45rem; }");
            out.println("    .estp-overview-list li { color:var(--text); }");
            out.println("    .estp-overview-list p { margin:0.15rem 0 0 0; color:var(--muted); }");
            out.println(
                    "    .estp-overview-stage-list { list-style:none; margin:0; padding:0; display:grid; gap:0.7rem; }");
            out.println("    .estp-overview-stage-item { padding:0.35rem 0; min-height:96px; }");
            out.println(
                    "    .estp-overview-stage-icon { float:left; width:84px; height:84px; object-fit:cover; border-radius:8px; margin:0 0.65rem 0.1rem 0; border:1px solid var(--border); background:#eef2f5; }");
            out.println("    .estp-overview-stage-item p { margin:0.18rem 0 0 0; color:var(--muted); }");
            out.println("    .estp-context-inline { margin:0 0 1rem 0; }");
            out.println("    .estp-context-inline p { margin:0 0 0.55rem 0; color:var(--muted); }");
            out.println(
                    "    .estp-context-points { margin:0 0 0.75rem 0; padding-left:1.2rem; display:grid; gap:0.35rem; }");
            out.println(
                    "    .estp-context-process { display:block; width:100%; max-width:760px; border:1px solid var(--border); border-radius:10px; background:#fff; }");
            out.println("    .estp-search-form { margin:0 0 0.7rem 0; display:flex; gap:0.5rem; align-items:center; }");
            out.println(
                    "    .estp-search-input { flex:1; min-width:0; border:1px solid var(--border); border-radius:10px; padding:0.55rem 0.7rem; font:inherit; }");
            out.println(
                    "    .estp-search-btn { border:1px solid #1b5f93; background:#0b6fb8; color:#fff; border-radius:10px; padding:0.55rem 0.9rem; cursor:pointer; font:inherit; }");
            out.println("    .estp-search-meta { margin:0 0 0.8rem 0; color:var(--muted); font-size:0.9rem; }");
            out.println(
                    "    .estp-auth-banner { border:1px solid #bcd6ea; background:#eef6fd; color:#0b3f67; border-radius:10px; padding:0.65rem 0.8rem; margin:0 0 1rem 0; }");
            out.println("    .estp-auth-banner a { color:#0b6fb8; }");
            out.println("    .es-stage-title { margin:1.1rem 0 0.45rem 0; }");
            out.println("    .es-neighborhood-group:first-child .es-stage-title { margin-top:0.4rem; }");
            out.println("    .es-topic-list { display:grid; gap:0.6rem; }");
            out.println("    .es-review-topic-row { cursor:pointer; }");
            out.println("    .es-review-topic-row .es-topic-content { min-width:0; }");
            out.println("    .es-topic-preview { margin-bottom:0; }");
            out.println(
                    "    .es-review-sidebar-count { color:var(--muted); font-size:0.88rem; padding:0.2rem 0.65rem 0.5rem 0.65rem; }");
            out.println(
                    "    .es-review-score-wrap.es-review-score-wrap-disabled { visibility:hidden; width:0; margin:0; padding:0; overflow:hidden; }");
            out.println("    .es-detail-comments-wrap { margin:0.7rem 0 0.8rem 0; }");
            out.println("    .es-detail-comments-list { margin:0; padding-left:1.1rem; display:grid; gap:0.3rem; }");
            out.println(
                    "    @media (max-width: 960px) { .estp-layout { grid-template-columns:1fr; } .estp-sidebar { position:static; } .estp-search-form { flex-wrap:wrap; } .estp-search-btn { width:100%; } }");
            out.println("  </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("  <div class=\"estp-shell\">");
            out.println("    <div class=\"estp-layout\">");

            renderSidebar(out, contextPath, neighborhoods, view, selectedNeighborhood, selectedStage,
                    selectedReviewScore, stageCounts, neighborhoodCounts, query, searchActive,
                    campaign.isPresent(), reviewScoreCounts, notReviewedCount,
                    authenticatedUser.isPresent(),
                    followedTopicIds.size(),
                    meetingByTopicId.size());

            out.println("      <main class=\"estp-main\">");
            renderMainHeader(out, contextPath, view, selectedNeighborhood, selectedStage, selectedReviewScore,
                    filteredRows.size(), query, authenticatedUser);

            boolean showOverview = VIEW_OVERVIEW.equalsIgnoreCase(view) && query == null;
            if (showOverview) {
                renderOverviewBlurb(out, contextPath, neighborhoods);
            } else {
                List<EsCampaignTopicBrowseRow> pageRows = resolvePageRows(filteredRows, view, selectedNeighborhood,
                        selectedStage, selectedReviewScore, activeNeighborhoodLookup, scoreByTopicId,
                        followedTopicIds, meetingByTopicId.keySet());
                pageRows = dedupeAndSortRows(pageRows);
                if (requiresAuthView && authenticatedUser.isEmpty()) {
                    out.println("        <p class=\"estp-auth-banner\">"
                            + "<a href=\"" + contextPath + "/home\">Log in</a> to view this section.</p>");
                } else if (pageRows.isEmpty()) {
                    out.println("        <p>No active topics found for this view.</p>");
                } else {
                    renderTopicList(out, pageRows, scoreByTopicId, userCommentsByTopicId,
                            canReview && !VIEW_MEETINGS.equalsIgnoreCase(view),
                            followedTopicIds, meetingByTopicId, membershipByMeetingId);
                }
            }

            renderDetailSheet(out, authenticatedUser.isPresent(), canReview);
            out.println("      </main>");
            out.println("    </div>");
            out.println("  </div>");

            renderPageScript(out, contextPath,
                    campaign.map(EsCampaign::getCampaignCode).orElse(null),
                    authenticatedUser.isPresent(),
                    canReview,
                    filteredRows.size(),
                    countReviewed(filteredRows, scoreByTopicId),
                    reviewScoreCounts,
                    notReviewedCount);

            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderSidebar(PrintWriter out, String contextPath, List<EsNeighborhood> neighborhoods, String view,
            String selectedNeighborhood, String selectedStage, Integer selectedReviewScore,
            Map<String, Integer> stageCounts,
            Map<String, Integer> neighborhoodCounts, String query, boolean searchActive, boolean showReviewSection,
            Map<Integer, Integer> reviewScoreCounts, int notReviewedCount,
            boolean authenticated, int followedTopicCount, int meetingCount) {
        out.println("      <aside class=\"estp-sidebar\">");
        out.println("        <a class=\"estp-sidebar-home\" href=\"" + contextPath
                + "/es/topics?view=" + VIEW_OVERVIEW + "\">Emerging Standards</a>");
        out.println("        <h2>Explore Topics</h2>");
        out.println("        <div class=\"estp-nav-group estp-nav-scroll\">");

        String allTopicsActive = VIEW_ALL.equalsIgnoreCase(view)
                ? " is-active"
                : "";
        out.println("          <a class=\"estp-nav-link" + allTopicsActive + "\" href=\""
                + buildTopicsUrl(contextPath, VIEW_ALL, null, null, query) + "\">All Topics</a>");

        String myTopicsActive = VIEW_MY_TOPICS.equalsIgnoreCase(view) ? " is-active" : "";
        String meetingsActive = VIEW_MEETINGS.equalsIgnoreCase(view) ? " is-active" : "";
        String myTopicsLabel = authenticated
                ? "My Topics (" + followedTopicCount + ")"
                : "My Topics";
        String meetingsLabel = authenticated
                ? "Meetings (" + meetingCount + ")"
                : "Meetings";
        out.println("          <a class=\"estp-nav-link" + myTopicsActive + "\" href=\""
                + buildTopicsUrl(contextPath, VIEW_MY_TOPICS, null, null, query) + "\">"
                + escapeHtml(myTopicsLabel) + "</a>");
        out.println("          <a class=\"estp-nav-link" + meetingsActive + "\" href=\""
                + buildTopicsUrl(contextPath, VIEW_MEETINGS, null, null, query) + "\">"
                + escapeHtml(meetingsLabel) + "</a>");

        out.println("          <details class=\"estp-nav-section\""
                + (VIEW_STAGE.equalsIgnoreCase(view) ? " open" : "") + ">");
        out.println("            <summary class=\"estp-nav-title estp-nav-summary\">By Stage</summary>");
        out.println("            <div class=\"estp-nav-links-wrap\">");
        for (String stage : STAGE_ORDER) {
            int count = stageCounts.getOrDefault(stage, 0);
            if (searchActive && count <= 0) {
                continue;
            }
            String active = VIEW_STAGE.equalsIgnoreCase(view) && equalsIgnoreCaseTrimmed(stage, selectedStage)
                    ? " is-active"
                    : "";
            out.println("              <a class=\"estp-nav-link" + active + "\" href=\""
                    + buildTopicsUrl(contextPath, VIEW_STAGE, null, stage, query) + "\">"
                    + escapeHtml(stage) + " (" + count + ")</a>");
        }
        out.println("            </div>");
        out.println("          </details>");

        out.println("          <details class=\"estp-nav-section\""
                + (VIEW_NEIGHBORHOOD.equalsIgnoreCase(view) ? " open" : "") + ">");
        out.println("            <summary class=\"estp-nav-title estp-nav-summary\">By Neighborhood</summary>");
        out.println("            <div class=\"estp-nav-links-wrap\">");
        for (EsNeighborhood neighborhood : neighborhoods) {
            String neighborhoodName = trimToNull(neighborhood.getNeighborhoodName());
            if (neighborhoodName == null) {
                continue;
            }
            int count = neighborhoodCounts.getOrDefault(neighborhoodName, 0);
            if (searchActive && count <= 0) {
                continue;
            }
            String active = VIEW_NEIGHBORHOOD.equalsIgnoreCase(view)
                    && equalsIgnoreCaseTrimmed(selectedNeighborhood, neighborhoodName) ? " is-active" : "";
            out.println("              <a class=\"estp-nav-link" + active + "\" href=\""
                    + buildTopicsUrl(contextPath, VIEW_NEIGHBORHOOD, neighborhoodName, null, query) + "\">"
                    + escapeHtml(neighborhoodName) + " (" + count + ")</a>");
        }

        int uncategorizedCount = neighborhoodCounts.getOrDefault(UNCATEGORIZED_LABEL, 0);
        if (!searchActive || uncategorizedCount > 0) {
            String uncategorizedActive = VIEW_NEIGHBORHOOD.equalsIgnoreCase(view)
                    && equalsIgnoreCaseTrimmed(selectedNeighborhood, UNCATEGORIZED_LABEL)
                            ? " is-active"
                            : "";
            out.println("              <a class=\"estp-nav-link" + uncategorizedActive + "\" href=\""
                    + buildTopicsUrl(contextPath, VIEW_NEIGHBORHOOD, UNCATEGORIZED_LABEL, null, query) + "\">"
                    + UNCATEGORIZED_LABEL + " (" + uncategorizedCount + ")</a>");
        }
        out.println("            </div>");
        out.println("          </details>");

        if (showReviewSection) {
            out.println("          <details class=\"estp-nav-section\""
                    + (VIEW_REVIEW.equalsIgnoreCase(view) ? " open" : "") + ">");
            out.println("            <summary class=\"estp-nav-title estp-nav-summary\">Review</summary>");
            out.println("            <div class=\"estp-nav-links-wrap\">");
            for (int score = 5; score >= 0; score--) {
                String active = VIEW_REVIEW.equalsIgnoreCase(view)
                        && selectedReviewScore != null
                        && selectedReviewScore.intValue() == score ? " is-active" : "";
                out.println("              <a id=\"es-review-level-" + score + "\" class=\"estp-nav-link" + active
                        + "\" href=\"" + buildReviewTopicsUrl(contextPath, score, query) + "\">"
                        + escapeHtml(reviewLevelLabel(score)) + " (<span id=\"es-review-count-" + score
                        + "\">" + reviewScoreCounts.getOrDefault(score, 0) + "</span>)</a>");
            }
            out.println(
                    "              <p class=\"es-review-sidebar-count\">Not Reviewed (<span id=\"es-review-not-reviewed\">"
                            + notReviewedCount + "</span></p>");
            out.println("            </div>");
            out.println("          </details>");
        }

        out.println("        </div>");
        out.println("      </aside>");
    }

    private void renderMainHeader(PrintWriter out, String contextPath, String view, String selectedNeighborhood,
            String selectedStage, Integer selectedReviewScore, int topicCount, String query,
            Optional<User> authenticatedUser) {
        if (VIEW_OVERVIEW.equalsIgnoreCase(view) && query == null) {
            out.println("        <h1 class=\"estp-title\">Emerging Standards</h1>");
            renderOverviewContext(out, contextPath);
        } else if (VIEW_NEIGHBORHOOD.equalsIgnoreCase(view) && selectedNeighborhood != null) {
            out.println("        <h1 class=\"estp-title\">Neighborhood: " + escapeHtml(selectedNeighborhood) + "</h1>");
        } else if (VIEW_STAGE.equalsIgnoreCase(view) && selectedStage != null) {
            out.println("        <h1 class=\"estp-title\">Stage: " + escapeHtml(selectedStage) + "</h1>");
            out.println(
                    "        <p class=\"estp-subtitle\">" + escapeHtml(orEmpty(STAGE_DESCRIPTIONS.get(selectedStage)))
                            + "</p>");
        } else if (VIEW_REVIEW.equalsIgnoreCase(view) && selectedReviewScore != null) {
            out.println("        <h1 class=\"estp-title\">Reviewed: "
                    + escapeHtml(reviewLevelLabel(selectedReviewScore)) + "</h1>");
            out.println("        <p class=\"estp-subtitle\">Topics currently scored at this level.</p>");
        } else if (VIEW_MY_TOPICS.equalsIgnoreCase(view)) {
            out.println("        <h1 class=\"estp-title\">My Topics</h1>");
            out.println("        <p class=\"estp-subtitle\">Topics you currently follow.</p>");
        } else if (VIEW_MEETINGS.equalsIgnoreCase(view)) {
            out.println("        <h1 class=\"estp-title\">Meetings</h1>");
            out.println("        <p class=\"estp-subtitle\">Topics with active meetings you can request to join.</p>");
        } else {
            out.println("        <h1 class=\"estp-title\">Emerging Standards Topics</h1>");
        }

        out.println("        <p class=\"estp-subtitle\"><strong>Active topics:</strong> " + topicCount + "</p>");

        out.println(
                "        <form class=\"estp-search-form\" method=\"get\" action=\"" + contextPath + "/es/topics\">");
        out.println("          <input class=\"estp-search-input\" type=\"search\" name=\"q\" value=\""
                + escapeHtml(orEmpty(query))
                + "\" placeholder=\"Search by topic name or description\" autocomplete=\"off\" />");
        out.println("          <button class=\"estp-search-btn\" type=\"submit\">Search</button>");
        out.println("        </form>");
        if (query != null) {
            out.println("        <p class=\"estp-search-meta\">Showing filtered results for \""
                    + escapeHtml(query) + "\".</p>");
        }

        if (authenticatedUser.isPresent()) {
            String identity = trimToNull(authenticatedUser.get().getDisplayName());
            if (identity == null) {
                identity = orEmpty(authenticatedUser.get().getEmail());
            }
            out.println("        <p class=\"estp-auth-banner\">Logged in as " + escapeHtml(identity)
                    + " - you can rate and comment on topics.</p>");
        } else {
            out.println("        <p class=\"estp-auth-banner\">"
                    + "<a href=\"" + contextPath + "/home\">Log in</a> to rate and comment on topics.</p>");
        }
    }

    private void renderOverviewContext(PrintWriter out, String contextPath) {
        out.println("        <div class=\"estp-context-inline\">");
        out.println(
                "          <p>Emerging Standards is a working pipeline for ideas that may become broadly adopted immunization standards. It helps the community share early concepts, evaluate maturity, and coordinate implementation as topics move toward practical rollout.</p>");
        out.println("          <ul class=\"estp-context-points\">");
        out.println(
                "            <li><strong>Emerging Topics and Standards:</strong> actively evolving ideas and implementation approaches that are gathering community input and early adoption evidence.</li>");
        out.println(
                "            <li><strong>Emerged Standards:</strong> topics that have reached stable guidance and are ready for wider implementation and operational use.</li>");
        out.println(
                "            <li><strong>Parked:</strong> topics intentionally paused while dependencies, timing, or priorities are resolved.</li>");
        out.println("          </ul>");
        out.println("          <img class=\"estp-context-process\" src=\"" + contextPath + "/image/"
                + urlEncodePathSegment("Emerging Standards Process")
                + ".png\" alt=\"Emerging Standards process lifecycle\" />");
        out.println("        </div>");
    }

    private void renderOverviewBlurb(PrintWriter out, String contextPath, List<EsNeighborhood> neighborhoods) {
        out.println("        <section class=\"estp-overview-grid\">");
        out.println("          <article class=\"estp-overview-panel\">");
        out.println("            <h3>Stages</h3>");
        out.println("            <ul class=\"estp-overview-stage-list\">");
        for (String stage : STAGE_ORDER) {
            out.println("              <li class=\"estp-overview-stage-item\">"
                    + "<img class=\"estp-overview-stage-icon\" src=\"" + contextPath + "/image/"
                    + urlEncodePathSegment(stage) + ".png\" alt=\"" + escapeHtml(stage) + "\" />"
                    + "<strong>" + escapeHtml(stage) + "</strong><p>"
                    + escapeHtml(orEmpty(STAGE_DESCRIPTIONS.get(stage))) + "</p></li>");
        }
        out.println("            </ul>");
        out.println("          </article>");

        out.println("          <article class=\"estp-overview-panel\">");
        out.println("            <h3>Neighborhoods</h3>");
        out.println("            <ul class=\"estp-overview-list\">");
        if (neighborhoods.isEmpty()) {
            out.println("              <li>No active neighborhoods configured yet.</li>");
        } else {
            for (EsNeighborhood neighborhood : neighborhoods) {
                String name = trimToNull(neighborhood.getNeighborhoodName());
                if (name == null) {
                    continue;
                }
                String description = trimToNull(neighborhood.getDescription());
                out.println("              <li><strong>" + escapeHtml(name) + "</strong><p>"
                        + escapeHtml(description == null ? "No description provided." : description)
                        + "</p></li>");
            }
        }
        out.println("            </ul>");
        out.println("          </article>");
        out.println("        </section>");
    }

    private void renderTopicList(PrintWriter out, List<EsCampaignTopicBrowseRow> rows,
            Map<Long, Integer> scoreByTopicId, Map<Long, List<String>> userCommentsByTopicId,
            boolean showReviewControls, Set<Long> followedTopicIds,
            Map<Long, EsCampaignMeetingBrowseRow> meetingByTopicId,
            Map<Long, EsTopicMeetingMember> membershipByMeetingId) {
        out.println("      <section class=\"es-stage-group\">");
        out.println("        <div class=\"es-topic-list\">");
        for (EsCampaignTopicBrowseRow row : rows) {
            String topicName = orEmpty(row.getTopicName());
            String description = orEmpty(row.getDescription());
            String preview = description.length() <= 130 ? description : description.substring(0, 127) + "...";
            String normalizedStage = orEmpty(normalizeToCanonicalStage(row.getStage()));
            String normalizedNeighborhood = normalizeNeighborhoodForDisplay(row.getNeighborhood());
            Integer savedScore = scoreByTopicId.get(row.getEsTopicId());
            boolean reviewed = savedScore != null;
            List<String> userComments = userCommentsByTopicId.getOrDefault(row.getEsTopicId(), List.of());
            boolean followed = followedTopicIds.contains(row.getEsTopicId());
            EsCampaignMeetingBrowseRow meeting = meetingByTopicId.get(row.getEsTopicId());
            Long meetingId = meeting == null ? null : meeting.getEsTopicMeetingId();
            EsTopicMeetingMember membership = meetingId == null ? null : membershipByMeetingId.get(meetingId);
            String membershipStatus = membership == null || membership.getMembershipStatus() == null
                    ? ""
                    : membership.getMembershipStatus().name();
            boolean meetingRegistered = "REQUESTED".equals(membershipStatus) || "APPROVED".equals(membershipStatus);

            out.println("          <article class=\"es-topic-row es-review-topic-row" + (reviewed ? " is-reviewed" : "")
                    + (meetingRegistered ? " is-meeting-registered" : "") + "\""
                    + " data-topic-id=\"" + row.getEsTopicId() + "\""
                    + " data-topic-name=\"" + escapeHtml(topicName) + "\""
                    + " data-topic-description=\"" + escapeHtml(description) + "\""
                    + " data-topic-type=\"" + escapeHtml(orEmpty(row.getTopicType())) + "\""
                    + " data-policy-status=\"" + escapeHtml(orEmpty(row.getPolicyStatus())) + "\""
                    + " data-topic-neighborhood=\"" + escapeHtml(normalizedNeighborhood) + "\""
                    + " data-topic-stage=\"" + escapeHtml(normalizedStage) + "\""
                    + " data-confluence-url=\"" + escapeHtml(orEmpty(row.getConfluenceUrl())) + "\""
                    + " data-user-comments=\"" + escapeHtml(toJsonStringArray(userComments)) + "\""
                    + " data-is-followed=\"" + (followed ? "1" : "0") + "\""
                    + " data-meeting-id=\"" + (meetingId == null ? "" : meetingId) + "\""
                    + " data-meeting-name=\"" + escapeHtml(meeting == null ? "" : orEmpty(meeting.getMeetingName()))
                    + "\""
                    + " data-meeting-description=\""
                    + escapeHtml(meeting == null ? "" : orEmpty(meeting.getMeetingDescription())) + "\""
                    + " data-meeting-status=\"" + escapeHtml(membershipStatus) + "\""
                    + " data-current-score=\"" + (reviewed ? savedScore : "") + "\"> ");

            if (showReviewControls) {
                out.println("            <div class=\"es-review-score-wrap\">");
                out.println("              <div class=\"es-review-selected-wrap\"" + (reviewed ? "" : " hidden") + ">");
                out.println("                <button type=\"button\" class=\"es-review-selected-value\" data-score=\""
                        + (reviewed ? savedScore : "") + "\">" + (reviewed ? savedScore : "") + "</button>");
                out.println("              </div>");
                out.println("              <div class=\"es-review-edit-wrap\"" + (reviewed ? " hidden" : "") + ">");
                for (int score = 0; score <= 5; score++) {
                    out.println("                <button type=\"button\" class=\"es-review-score-btn\" data-score=\""
                            + score
                            + "\">" + score + "</button>");
                }
                out.println("              </div>");
                out.println("            </div>");
            } else {
                out.println(
                        "            <div class=\"es-review-score-wrap es-review-score-wrap-disabled\" aria-hidden=\"true\"></div>");
            }

            String stateLabel = meetingRegistered ? "Registered" : (reviewed ? "Reviewed" : "");
            out.println("            <div class=\"es-topic-content\">");
            out.println("              <div class=\"es-topic-top\">");
            out.println("                <h3>" + escapeHtml(topicName) + "</h3>");
            out.println("                <span class=\"es-topic-state\"" + (stateLabel.isEmpty() ? " hidden" : "") + ">"
                    + stateLabel + "</span>");
            out.println("              </div>");
            out.println("              <p class=\"es-topic-preview\">" + escapeHtml(orEmpty(preview)) + "</p>");
            out.println("            </div>");
            out.println("          </article>");
        }
        out.println("        </div>");
        out.println("      </section>");
    }

    private void renderDetailSheet(PrintWriter out, boolean canInteract, boolean canReview) {
        out.println("    <div id=\"es-detail-overlay\" class=\"es-detail-overlay\" hidden></div>");
        out.println("    <aside id=\"es-detail-sheet\" class=\"es-detail-sheet\" hidden>");
        out.println("      <h2 id=\"es-detail-title\"></h2>");
        out.println("      <p id=\"es-detail-stage\" class=\"es-detail-stage\"></p>");
        out.println("      <p id=\"es-detail-neighborhood\" class=\"es-detail-stage\" hidden></p>");
        out.println("      <p id=\"es-detail-topic-type\" class=\"es-detail-stage\" hidden></p>");
        out.println("      <p id=\"es-detail-policy-status\" class=\"es-detail-stage\" hidden></p>");
        out.println("      <p id=\"es-detail-description\" class=\"es-detail-description\"></p>");
        out.println(
                "      <p><a id=\"es-detail-confluence-url\" href=\"#\" target=\"_blank\" rel=\"noopener\" hidden>View in Confluence</a></p>");
        if (canInteract) {
            out.println("      <div id=\"es-detail-follow-wrap\" class=\"es-detail-comments-wrap\" hidden>");
            out.println("        <div class=\"es-detail-actions\">");
            out.println("          <button type=\"button\" id=\"es-detail-follow-toggle\"></button>");
            out.println("        </div>");
            out.println("      </div>");

            out.println("      <div id=\"es-detail-meeting-wrap\" class=\"es-detail-comments-wrap\" hidden>");
            out.println("        <p id=\"es-detail-meeting-name\" class=\"es-topic-meta\" style=\"margin-top:0\"></p>");
            out.println("        <p id=\"es-detail-meeting-description\" class=\"es-topic-meta\"></p>");
            out.println("        <div class=\"es-detail-actions\">");
            out.println("          <button type=\"button\" id=\"es-detail-meeting-toggle\"></button>");
            out.println("        </div>");
            out.println("      </div>");
        }
        out.println("      <div id=\"es-detail-comments-wrap\" class=\"es-detail-comments-wrap\" hidden>");
        out.println("        <ul id=\"es-detail-comments-list\" class=\"es-detail-comments-list\" hidden></ul>");
        out.println("      </div>");
        if (canReview) {
            out.println(
                    "      <label for=\"es-detail-comment\" class=\"es-review-comment-label\">Optional comment or question</label>");
            out.println("      <textarea id=\"es-detail-comment\" class=\"es-review-comment-input\" maxlength=\"2000\""
                    + " placeholder=\"Add a comment/question for this topic\"></textarea>");
            out.println("      <p id=\"es-detail-comment-status\" class=\"es-review-comment-status\" hidden></p>");
            out.println("      <div class=\"es-detail-actions\">");
            out.println("        <button type=\"button\" id=\"es-detail-save-comment\">Save Comment</button>");
            out.println(
                    "        <button type=\"button\" id=\"es-detail-close\" class=\"es-secondary-button\">Close</button>");
            out.println("      </div>");
        } else {
            out.println("      <div class=\"es-detail-actions\">");
            out.println(
                    "        <button type=\"button\" id=\"es-detail-close\" class=\"es-secondary-button\">Close</button>");
            out.println("      </div>");
        }
        out.println("    </aside>");
    }

    private void renderPageScript(PrintWriter out, String contextPath, String campaignCode,
            boolean canInteract, boolean canReview,
            int totalTopics, int reviewedCount, Map<Integer, Integer> reviewScoreCounts, int notReviewedCount) {
        out.println("  <script>");
        out.println("    (function() {");
        out.println("      var canInteract = " + canInteract + ";");
        out.println("      var canReview = " + canReview + ";");
        out.println("      var campaignCode = " + quoteJs(campaignCode) + ";");
        out.println("      var rows = Array.prototype.slice.call(document.querySelectorAll('.es-topic-row')); ");
        out.println("      var detailOverlay = document.getElementById('es-detail-overlay');");
        out.println("      var detailSheet = document.getElementById('es-detail-sheet');");
        out.println("      var detailTitle = document.getElementById('es-detail-title');");
        out.println("      var detailStage = document.getElementById('es-detail-stage');");
        out.println("      var detailNeighborhood = document.getElementById('es-detail-neighborhood');");
        out.println("      var detailTopicType = document.getElementById('es-detail-topic-type');");
        out.println("      var detailPolicyStatus = document.getElementById('es-detail-policy-status');");
        out.println("      var detailDescription = document.getElementById('es-detail-description');");
        out.println("      var detailConfluence = document.getElementById('es-detail-confluence-url');");
        out.println("      var detailCommentsWrap = document.getElementById('es-detail-comments-wrap');");
        out.println("      var detailCommentsList = document.getElementById('es-detail-comments-list');");
        out.println("      var detailFollowWrap = document.getElementById('es-detail-follow-wrap');");
        out.println("      var detailFollowToggle = document.getElementById('es-detail-follow-toggle');");
        out.println("      var detailMeetingWrap = document.getElementById('es-detail-meeting-wrap');");
        out.println("      var detailMeetingName = document.getElementById('es-detail-meeting-name');");
        out.println("      var detailMeetingDescription = document.getElementById('es-detail-meeting-description');");
        out.println("      var detailMeetingToggle = document.getElementById('es-detail-meeting-toggle');");
        out.println("      var detailComment = document.getElementById('es-detail-comment');");
        out.println("      var detailCommentStatus = document.getElementById('es-detail-comment-status');");
        out.println("      var detailSaveComment = document.getElementById('es-detail-save-comment');");
        out.println("      var detailClose = document.getElementById('es-detail-close');");
        out.println("      var currentDetailTopicId = null;");
        out.println("      var currentMeetingId = null;");
        out.println("      var currentFollowed = false;");
        out.println("      var currentMeetingStatus = '';");
        out.println("      var sessionComments = {}; ");
        out.println("      var total = " + totalTopics + ";");
        out.println("      var reviewed = " + reviewedCount + ";");
        out.println("      var scoreCounts = " + toJsScoreCounts(reviewScoreCounts) + ";");
        out.println("      var notReviewed = " + notReviewedCount + ";");

        out.println("      function parseScore(value) {");
        out.println("        var n = parseInt(value || '', 10);");
        out.println("        return Number.isFinite(n) ? n : null;");
        out.println("      }");

        out.println("      function renderReviewSidebar() {");
        out.println("        for (var s = 0; s <= 5; s++) {");
        out.println("          var count = scoreCounts[s] || 0;");
        out.println("          var countNode = document.getElementById('es-review-count-' + s);");
        out.println("          if (countNode) { countNode.textContent = String(count); }");
        out.println("        }");
        out.println("        var notReviewedNode = document.getElementById('es-review-not-reviewed');");
        out.println("        if (notReviewedNode) { notReviewedNode.textContent = String(Math.max(0, notReviewed)); }");
        out.println("      }");

        out.println("      function updateProgress() {");
        out.println("        var node = document.getElementById('es-review-progress-inline');");
        out.println("        if (!node) { return; }");
        out.println("        var left = Math.max(0, total - reviewed);");
        out.println("        node.textContent = reviewed + ' reviewed - ' + left + ' left';");
        out.println("      }");

        out.println("      function updateReviewSidebar(oldScore, newScore) {");
        out.println("        if (oldScore === null) {");
        out.println("          reviewed += 1;");
        out.println("          notReviewed -= 1;");
        out.println("        } else {");
        out.println("          scoreCounts[oldScore] = Math.max(0, (scoreCounts[oldScore] || 0) - 1);");
        out.println("        }");
        out.println("        scoreCounts[newScore] = (scoreCounts[newScore] || 0) + 1;");
        out.println("        renderReviewSidebar();");
        out.println("        updateProgress();");
        out.println("      }");

        out.println("      function setRowReviewed(row, score) {");
        out.println("        row.classList.add('is-reviewed');");
        out.println("        row.setAttribute('data-current-score', String(score));");
        out.println("        var selectedWrap = row.querySelector('.es-review-selected-wrap');");
        out.println("        var selectedBtn = row.querySelector('.es-review-selected-value');");
        out.println("        var editWrap = row.querySelector('.es-review-edit-wrap');");
        out.println("        var state = row.querySelector('.es-topic-state');");
        out.println("        if (selectedWrap) { selectedWrap.hidden = false; }");
        out.println(
                "        if (selectedBtn) { selectedBtn.textContent = String(score); selectedBtn.setAttribute('data-score', String(score)); }");
        out.println("        if (editWrap) { editWrap.hidden = true; }");
        out.println("        if (state) { state.hidden = false; state.textContent = 'Reviewed'; }");
        out.println("      }");

        out.println("      function setRowEditing(row) {");
        out.println("        var selectedWrap = row.querySelector('.es-review-selected-wrap');");
        out.println("        var editWrap = row.querySelector('.es-review-edit-wrap');");
        out.println("        if (selectedWrap) { selectedWrap.hidden = true; }");
        out.println("        if (editWrap) { editWrap.hidden = false; }");
        out.println("      }");

        out.println("      function renderComments(existing, topicId) {");
        out.println("        var merged = existing.slice();");
        out.println("        if (sessionComments[topicId]) { merged = merged.concat(sessionComments[topicId]); }");
        out.println("        detailCommentsList.innerHTML = ''; ");
        out.println("        if (merged.length === 0) {");
        out.println("          if (detailCommentsWrap) { detailCommentsWrap.hidden = true; }");
        out.println("          detailCommentsList.hidden = true;");
        out.println("          return;");
        out.println("        }");
        out.println("        if (detailCommentsWrap) { detailCommentsWrap.hidden = false; }");
        out.println("        detailCommentsList.hidden = false;");
        out.println("        merged.forEach(function(text) {");
        out.println("          var li = document.createElement('li');");
        out.println("          li.textContent = text;");
        out.println("          detailCommentsList.appendChild(li);");
        out.println("        });");
        out.println("      }");

        out.println("      function openDetail(row) {");
        out.println("        currentDetailTopicId = row.getAttribute('data-topic-id');");
        out.println("        currentMeetingId = row.getAttribute('data-meeting-id') || null;");
        out.println("        currentFollowed = row.getAttribute('data-is-followed') === '1';");
        out.println("        currentMeetingStatus = (row.getAttribute('data-meeting-status') || '').toUpperCase();");
        out.println("        detailTitle.textContent = row.getAttribute('data-topic-name') || ''; ");
        out.println(
                "        detailStage.textContent = 'Stage: ' + ((row.getAttribute('data-topic-stage') || '').trim() || 'Other');");

        out.println("        var neighborhood = (row.getAttribute('data-topic-neighborhood') || '').trim();");
        out.println(
                "        if (neighborhood) { detailNeighborhood.hidden = false; detailNeighborhood.textContent = 'Neighborhood: ' + neighborhood; } else { detailNeighborhood.hidden = true; detailNeighborhood.textContent = ''; }");

        out.println("        var topicType = (row.getAttribute('data-topic-type') || '').trim();");
        out.println(
                "        if (topicType) { detailTopicType.hidden = false; detailTopicType.textContent = 'Topic type: ' + topicType; } else { detailTopicType.hidden = true; detailTopicType.textContent = ''; }");

        out.println("        var policy = (row.getAttribute('data-policy-status') || '').trim();");
        out.println(
                "        if (policy) { detailPolicyStatus.hidden = false; detailPolicyStatus.textContent = 'Policy status: ' + policy; } else { detailPolicyStatus.hidden = true; detailPolicyStatus.textContent = ''; }");

        out.println(
                "        detailDescription.textContent = row.getAttribute('data-topic-description') || 'No description available.';");

        out.println("        var confluenceUrl = (row.getAttribute('data-confluence-url') || '').trim();");
        out.println(
                "        if (confluenceUrl) { detailConfluence.hidden = false; detailConfluence.href = confluenceUrl; } else { detailConfluence.hidden = true; detailConfluence.removeAttribute('href'); }");

        out.println("        if (canInteract && detailFollowWrap && detailFollowToggle) {");
        out.println("          detailFollowWrap.hidden = false;");
        out.println("          detailFollowToggle.textContent = currentFollowed ? 'Unfollow Topic' : 'Follow Topic';");
        out.println("          detailFollowToggle.setAttribute('data-followed', currentFollowed ? '1' : '0');");
        out.println("          detailFollowToggle.classList.toggle('es-secondary-button', currentFollowed);");
        out.println("        }");

        out.println("        if (canInteract && detailMeetingWrap) {");
        out.println("          var meetingName = (row.getAttribute('data-meeting-name') || '').trim();");
        out.println("          var meetingDescription = (row.getAttribute('data-meeting-description') || '').trim();");
        out.println("          var hasMeeting = !!currentMeetingId;");
        out.println("          detailMeetingWrap.hidden = !hasMeeting;");
        out.println("          if (hasMeeting) {");
        out.println(
                "            if (detailMeetingName) { detailMeetingName.textContent = meetingName ? ('Meeting: ' + meetingName) : 'Meeting available for this topic.'; }");
        out.println(
                "            if (detailMeetingDescription) { detailMeetingDescription.textContent = meetingDescription; detailMeetingDescription.hidden = meetingDescription.length === 0; }");
        out.println(
                "            var requested = currentMeetingStatus === 'REQUESTED' || currentMeetingStatus === 'APPROVED';");
        out.println("            if (detailMeetingToggle) {");
        out.println(
                "              detailMeetingToggle.textContent = requested ? 'Unrequest Meeting' : 'Request to Join Meeting';");
        out.println("              detailMeetingToggle.classList.toggle('es-secondary-button', requested);");
        out.println("            }");
        out.println("          }");
        out.println("        }");

        out.println("        var parsedComments = []; ");
        out.println(
                "        try { parsedComments = JSON.parse(row.getAttribute('data-user-comments') || '[]'); } catch (e) { parsedComments = []; }");
        out.println(
                "        renderComments(Array.isArray(parsedComments) ? parsedComments : [], currentDetailTopicId);");

        out.println(
                "        if (detailCommentStatus) { detailCommentStatus.hidden = true; detailCommentStatus.textContent = ''; }");
        out.println("        if (detailComment) { detailComment.value = ''; }");
        out.println("        detailOverlay.hidden = false;");
        out.println("        detailSheet.hidden = false;");
        out.println("        document.body.classList.add('es-sheet-open');");
        out.println("      }");

        out.println("      function closeDetail() {");
        out.println("        currentDetailTopicId = null;");
        out.println("        currentMeetingId = null;");
        out.println("        detailOverlay.hidden = true;");
        out.println("        detailSheet.hidden = true;");
        out.println("        document.body.classList.remove('es-sheet-open');");
        out.println("      }");

        out.println("      function saveScore(row, topicId, score) {");
        out.println("        var oldScore = parseScore(row.getAttribute('data-current-score')); ");
        out.println("        var params = new URLSearchParams();");
        out.println("        params.set('campaignCode', campaignCode);");
        out.println("        params.set('topicId', String(topicId));");
        out.println("        params.set('score', String(score));");
        out.println("        fetch('" + contextPath + "/es/review/save', {");
        out.println("          method: 'POST',");
        out.println("          headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
        out.println("          body: params.toString()");
        out.println("        }).then(function(res) { return res.json(); }).then(function(json) {");
        out.println("          if (!json || !json.ok) {");
        out.println("            window.alert((json && json.error) ? json.error : 'Unable to save score.');");
        out.println("            return;");
        out.println("          }");
        out.println("          setRowReviewed(row, json.score);");
        out.println("          updateReviewSidebar(oldScore, json.score);");
        out.println("        }).catch(function() {");
        out.println("          window.alert('Unable to save score.');");
        out.println("        });");
        out.println("      }");

        out.println("      rows.forEach(function(row) {");
        out.println("        var topicId = row.getAttribute('data-topic-id');");
        out.println("        var content = row.querySelector('.es-topic-content');");
        out.println("        var selectedBtn = row.querySelector('.es-review-selected-value');");
        out.println(
                "        var scoreButtons = Array.prototype.slice.call(row.querySelectorAll('.es-review-score-btn')); ");
        out.println("        if (content) { content.addEventListener('click', function() { openDetail(row); }); }");
        out.println("        if (selectedBtn) {");
        out.println("          selectedBtn.addEventListener('click', function(evt) {");
        out.println("            evt.stopPropagation();");
        out.println("            if (!canReview) { return; }");
        out.println("            setRowEditing(row);");
        out.println("          });");
        out.println("        }");
        out.println("        if (!canReview) { return; }");
        out.println("        scoreButtons.forEach(function(btn) {");
        out.println("          btn.addEventListener('click', function(evt) {");
        out.println("            evt.stopPropagation();");
        out.println("            var score = parseInt(btn.getAttribute('data-score') || '0', 10) || 0;");
        out.println("            if (score < 0 || score > 5 || !campaignCode) { return; }");
        out.println("            saveScore(row, topicId, score);");
        out.println("          });");
        out.println("        });");
        out.println("      });");

        out.println("      if (canInteract && detailFollowToggle) {");
        out.println("        detailFollowToggle.addEventListener('click', function() {");
        out.println("          if (!currentDetailTopicId) { return; }");
        out.println("          var shouldFollow = !currentFollowed;");
        out.println("          var params = new URLSearchParams();");
        out.println("          params.set('topicId', String(currentDetailTopicId));");
        out.println("          params.set('action', shouldFollow ? 'follow' : 'unfollow');");
        out.println("          fetch('" + contextPath + "/es/topics/follow-toggle', {");
        out.println("            method: 'POST',");
        out.println("            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
        out.println("            body: params.toString()");
        out.println("          }).then(function(res) { return res.json(); }).then(function(json) {");
        out.println("            if (!json || !json.ok) {");
        out.println(
                "              window.alert((json && json.error) ? json.error : 'Unable to update follow status.');");
        out.println("              return;");
        out.println("            }");
        out.println("            closeDetail();");
        out.println("            window.location.reload();");
        out.println("          }).catch(function() {");
        out.println("            window.alert('Unable to update follow status.');");
        out.println("          });");
        out.println("        });");
        out.println("      }");

        out.println("      if (canInteract && detailMeetingToggle) {");
        out.println("        detailMeetingToggle.addEventListener('click', function() {");
        out.println("          if (!currentDetailTopicId || !currentMeetingId) { return; }");
        out.println(
                "          var requested = currentMeetingStatus === 'REQUESTED' || currentMeetingStatus === 'APPROVED';");
        out.println("          var params = new URLSearchParams();");
        out.println("          params.set('topicId', String(currentDetailTopicId));");
        out.println("          params.set('meetingId', String(currentMeetingId));");
        out.println("          params.set('action', requested ? 'unrequest' : 'request');");
        out.println("          fetch('" + contextPath + "/es/topics/meeting-toggle', {");
        out.println("            method: 'POST',");
        out.println("            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
        out.println("            body: params.toString()");
        out.println("          }).then(function(res) { return res.json(); }).then(function(json) {");
        out.println("            if (!json || !json.ok) {");
        out.println(
                "              window.alert((json && json.error) ? json.error : 'Unable to update meeting request.');");
        out.println("              return;");
        out.println("            }");
        out.println("            if (json.requested) {");
        out.println("              closeDetail();");
        out.println("              window.location.reload();");
        out.println("              return;");
        out.println("            }");
        out.println("            currentMeetingStatus = (json.membershipStatus || '').toUpperCase();");
        out.println(
                "            var nowRequested = currentMeetingStatus === 'REQUESTED' || currentMeetingStatus === 'APPROVED';");
        out.println(
                "            detailMeetingToggle.textContent = nowRequested ? 'Unrequest Meeting' : 'Request to Join Meeting';");
        out.println("            detailMeetingToggle.classList.toggle('es-secondary-button', nowRequested);");
        out.println(
                "            var row = document.querySelector('.es-topic-row[data-topic-id=\"' + currentDetailTopicId + '\"]');");
        out.println("            if (row) {");
        out.println("              row.setAttribute('data-meeting-status', currentMeetingStatus);");
        out.println("              row.classList.remove('is-meeting-registered');");
        out.println("            }");
        out.println("          }).catch(function() {");
        out.println("            window.alert('Unable to update meeting request.');");
        out.println("          });");
        out.println("        });");
        out.println("      }");

        out.println("      if (canReview && detailSaveComment) {");
        out.println("        detailSaveComment.addEventListener('click', function() {");
        out.println("          if (!currentDetailTopicId || !campaignCode) { return; }");
        out.println("          var text = (detailComment.value || '').trim();");
        out.println("          if (text.length === 0) {");
        out.println("            detailCommentStatus.hidden = false;");
        out.println("            detailCommentStatus.textContent = 'Enter a comment before saving.';");
        out.println("            return;");
        out.println("          }");
        out.println("          var params = new URLSearchParams();");
        out.println("          params.set('campaignCode', campaignCode);");
        out.println("          params.set('topicId', currentDetailTopicId);");
        out.println("          params.set('commentText', text);");
        out.println("          fetch('" + contextPath + "/es/review/comment', {");
        out.println("            method: 'POST',");
        out.println("            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
        out.println("            body: params.toString()");
        out.println("          }).then(function(res) { return res.json(); }).then(function(json) {");
        out.println("            if (!json || !json.ok) {");
        out.println("              detailCommentStatus.hidden = false;");
        out.println(
                "              detailCommentStatus.textContent = (json && json.error) ? json.error : 'Unable to save comment.';");
        out.println("              return;");
        out.println("            }");
        out.println(
                "            if (!sessionComments[currentDetailTopicId]) { sessionComments[currentDetailTopicId] = []; }");
        out.println("            sessionComments[currentDetailTopicId].push(text);");
        out.println("            var existing = []; ");
        out.println("            try {");
        out.println(
                "              var row = document.querySelector('.es-topic-row[data-topic-id=\"' + currentDetailTopicId + '\"]');");
        out.println("              existing = JSON.parse((row && row.getAttribute('data-user-comments')) || '[]');");
        out.println("            } catch (e) { existing = []; }");
        out.println("            renderComments(Array.isArray(existing) ? existing : [], currentDetailTopicId);");
        out.println("            detailComment.value = ''; ");
        out.println("            detailCommentStatus.hidden = false;");
        out.println("            detailCommentStatus.textContent = 'Comment saved.';");
        out.println("          }).catch(function() {");
        out.println("            detailCommentStatus.hidden = false;");
        out.println("            detailCommentStatus.textContent = 'Unable to save comment.';");
        out.println("          });");
        out.println("        });");
        out.println("      }");

        out.println("      if (detailOverlay) { detailOverlay.addEventListener('click', closeDetail); }");
        out.println("      if (detailClose) { detailClose.addEventListener('click', closeDetail); }");
        out.println("      renderReviewSidebar();");
        out.println("      updateProgress();");
        out.println("    })();");
        out.println("  </script>");

        out.println("  <div style=\"display:none\" id=\"es-review-progress-inline\"></div>");
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

    private List<EsCampaignTopicBrowseRow> resolvePageRows(List<EsCampaignTopicBrowseRow> rows, String view,
            String selectedNeighborhood, String selectedStage, Integer selectedReviewScore,
            Map<String, String> activeNeighborhoodLookup, Map<Long, Integer> scoreByTopicId,
            Set<Long> followedTopicIds, Set<Long> meetingTopicIds) {
        if (VIEW_NEIGHBORHOOD.equalsIgnoreCase(view) && selectedNeighborhood != null) {
            return rows.stream()
                    .filter(row -> topicMatchesNeighborhood(row, selectedNeighborhood, activeNeighborhoodLookup))
                    .collect(Collectors.toList());
        }
        if (VIEW_STAGE.equalsIgnoreCase(view) && selectedStage != null) {
            return rows.stream()
                    .filter(row -> equalsIgnoreCaseTrimmed(normalizeToCanonicalStage(row.getStage()), selectedStage))
                    .collect(Collectors.toList());
        }
        if (VIEW_REVIEW.equalsIgnoreCase(view) && selectedReviewScore != null) {
            int score = selectedReviewScore;
            return rows.stream()
                    .filter(row -> scoreByTopicId.containsKey(row.getEsTopicId())
                            && scoreByTopicId.get(row.getEsTopicId()) != null
                            && scoreByTopicId.get(row.getEsTopicId()) == score)
                    .collect(Collectors.toList());
        }
        if (VIEW_MY_TOPICS.equalsIgnoreCase(view)) {
            return rows.stream()
                    .filter(row -> followedTopicIds.contains(row.getEsTopicId()))
                    .collect(Collectors.toList());
        }
        if (VIEW_MEETINGS.equalsIgnoreCase(view)) {
            return rows.stream()
                    .filter(row -> meetingTopicIds.contains(row.getEsTopicId()))
                    .collect(Collectors.toList());
        }
        return rows;
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

    private Map<Long, List<String>> buildUserCommentsByTopicId(List<EsComment> comments) {
        Map<Long, List<String>> byTopic = new LinkedHashMap<>();
        for (EsComment comment : comments) {
            if (comment.getEsTopicId() == null || trimToNull(comment.getCommentText()) == null) {
                continue;
            }
            byTopic.computeIfAbsent(comment.getEsTopicId(), ignored -> new ArrayList<>())
                    .add(comment.getCommentText().trim());
        }
        return byTopic;
    }

    private Map<Integer, Integer> buildReviewScoreCounts(List<EsCampaignTopicBrowseRow> rows,
            Map<Long, Integer> scoreByTopicId) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i <= 5; i++) {
            counts.put(i, 0);
        }
        for (EsCampaignTopicBrowseRow row : rows) {
            Integer score = scoreByTopicId.get(row.getEsTopicId());
            if (score != null && score >= 0 && score <= 5) {
                counts.put(score, counts.getOrDefault(score, 0) + 1);
            }
        }
        return counts;
    }

    private int countNotReviewed(List<EsCampaignTopicBrowseRow> rows, Map<Long, Integer> scoreByTopicId) {
        int count = 0;
        for (EsCampaignTopicBrowseRow row : rows) {
            if (!scoreByTopicId.containsKey(row.getEsTopicId())) {
                count++;
            }
        }
        return count;
    }

    private int countReviewed(List<EsCampaignTopicBrowseRow> rows, Map<Long, Integer> scoreByTopicId) {
        int count = 0;
        for (EsCampaignTopicBrowseRow row : rows) {
            if (scoreByTopicId.containsKey(row.getEsTopicId())) {
                count++;
            }
        }
        return count;
    }

    private String buildTopicsUrl(String contextPath, String view, String neighborhood, String stage, String query) {
        List<String> params = new ArrayList<>();
        if (trimToNull(view) != null) {
            params.add("view=" + urlEncode(view));
        }
        if (trimToNull(neighborhood) != null) {
            params.add("n=" + urlEncode(neighborhood));
        }
        if (trimToNull(stage) != null) {
            params.add("s=" + urlEncode(stage));
        }
        if (trimToNull(query) != null) {
            params.add("q=" + urlEncode(query));
        }
        if (params.isEmpty()) {
            return contextPath + "/es/topics";
        }
        return contextPath + "/es/topics?" + String.join("&", params);
    }

    private String normalizeNeighborhoodForDisplay(String neighborhoodRaw) {
        String trimmed = trimToNull(neighborhoodRaw);
        return trimmed == null ? "" : trimmed;
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
        for (String token : parseNeighborhoodTokens(neighborhoodRaw)) {
            String canonical = activeNeighborhoodLookup.get(token.toLowerCase(Locale.ROOT));
            if (canonical != null
                    && matched.stream().noneMatch(existing -> equalsIgnoreCaseTrimmed(existing, canonical))) {
                matched.add(canonical);
            }
        }
        return matched;
    }

    private List<String> parseNeighborhoodTokens(String neighborhoodRaw) {
        String normalized = trimToNull(neighborhoodRaw);
        if (normalized == null) {
            return List.of();
        }
        String[] parts = normalized.split(",");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            String token = trimToNull(part);
            if (token != null) {
                tokens.add(token);
            }
        }
        return tokens;
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
        for (EsNeighborhood neighborhood : neighborhoods) {
            String neighborhoodName = neighborhood.getNeighborhoodName();
            if (equalsIgnoreCaseTrimmed(normalizedRequested, neighborhoodName)) {
                return neighborhoodName.trim();
            }
        }
        String normalizedKey = normalizedRequested.toLowerCase(Locale.ROOT);
        if (activeNeighborhoodLookup.containsKey(normalizedKey)) {
            return activeNeighborhoodLookup.get(normalizedKey);
        }
        return null;
    }

    private boolean topicMatchesNeighborhood(EsCampaignTopicBrowseRow row, String selectedNeighborhood,
            Map<String, String> activeNeighborhoodLookup) {
        if (equalsIgnoreCaseTrimmed(selectedNeighborhood, UNCATEGORIZED_LABEL)) {
            return getMatchedActiveNeighborhoods(row.getNeighborhood(), activeNeighborhoodLookup).isEmpty();
        }
        return getMatchedActiveNeighborhoods(row.getNeighborhood(), activeNeighborhoodLookup).stream()
                .anyMatch(name -> equalsIgnoreCaseTrimmed(name, selectedNeighborhood));
    }

    private Map<String, Integer> buildStageCounts(List<EsCampaignTopicBrowseRow> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String stage : STAGE_ORDER) {
            counts.put(stage, 0);
        }
        for (EsCampaignTopicBrowseRow row : rows) {
            String stage = normalizeToCanonicalStage(row.getStage());
            if (stage != null) {
                counts.put(stage, counts.getOrDefault(stage, 0) + 1);
            }
        }
        return counts;
    }

    private Map<String, Integer> buildNeighborhoodCounts(List<EsCampaignTopicBrowseRow> rows,
            List<EsNeighborhood> neighborhoods,
            Map<String, String> activeNeighborhoodLookup) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (EsNeighborhood neighborhood : neighborhoods) {
            String name = trimToNull(neighborhood.getNeighborhoodName());
            if (name != null) {
                counts.put(name, 0);
            }
        }
        counts.put(UNCATEGORIZED_LABEL, 0);

        for (EsCampaignTopicBrowseRow row : rows) {
            List<String> matched = getMatchedActiveNeighborhoods(row.getNeighborhood(), activeNeighborhoodLookup);
            if (matched.isEmpty()) {
                counts.put(UNCATEGORIZED_LABEL, counts.getOrDefault(UNCATEGORIZED_LABEL, 0) + 1);
            } else {
                for (String name : matched) {
                    counts.put(name, counts.getOrDefault(name, 0) + 1);
                }
            }
        }

        return counts;
    }

    private String normalizeToCanonicalStage(String stage) {
        String normalized = trimToNull(stage);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if ("monnitor".equals(lowered)) {
            lowered = "monitor";
        }
        for (String candidate : STAGE_ORDER) {
            if (candidate.toLowerCase(Locale.ROOT).equals(lowered)) {
                return candidate;
            }
        }
        return null;
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

    private String toJsScoreCounts(Map<Integer, Integer> counts) {
        StringBuilder js = new StringBuilder("{");
        for (int i = 0; i <= 5; i++) {
            if (i > 0) {
                js.append(',');
            }
            js.append(i).append(':').append(counts.getOrDefault(i, 0));
        }
        js.append('}');
        return js.toString();
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

    private Integer parseReviewScore(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            int score = Integer.parseInt(normalized);
            return score >= 0 && score <= 5 ? score : null;
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private String reviewLevelLabel(int score) {
        return switch (score) {
            case 5 -> "Critical";
            case 4 -> "High";
            case 3 -> "Worth Discussing";
            case 2 -> "Low";
            case 1 -> "Very Low";
            default -> "No Value";
        };
    }

    private String buildReviewTopicsUrl(String contextPath, int score, String query) {
        List<String> params = new ArrayList<>();
        params.add("view=" + urlEncode(VIEW_REVIEW));
        params.add("r=" + score);
        if (trimToNull(query) != null) {
            params.add("q=" + urlEncode(query));
        }
        return contextPath + "/es/topics?" + String.join("&", params);
    }

    private boolean equalsIgnoreCaseTrimmed(String a, String b) {
        String normalizedA = trimToNull(a);
        String normalizedB = trimToNull(b);
        if (normalizedA == null || normalizedB == null) {
            return normalizedA == normalizedB;
        }
        return normalizedA.equalsIgnoreCase(normalizedB);
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
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String urlEncodePathSegment(String value) {
        return urlEncode(value).replace("+", "%20");
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