package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.MeetingTimeFormatter;
import org.airahub.interophub.service.SearchService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.immregistries.aira.web.AiraPage;

/**
 * InteropHub global search full results page. Mapped to {@code /search}.
 * Groups results into Topics, Upcoming meetings, and Previous meetings, each
 * initially showing up to {@link #INITIAL_GROUP_LIMIT} rows with the rest
 * revealed in place via a native {@code <details>} disclosure. See
 * {@code docs/InteropHub_Global_Search_Design.md}.
 */
public class SearchServlet extends HttpServlet {

    private static final int INITIAL_GROUP_LIMIT = 10;

    private final AuthFlowService authFlowService;
    private final TopicSpaceAccessService topicSpaceAccessService;
    private final SearchService searchService;

    public SearchServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
        this.searchService = new SearchService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();
        String query = trimToNull(request.getParameter("q"));

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        User viewer = authenticatedUser.orElse(null);
        Set<Long> visibleSpaceIds = topicSpaceAccessService.getVisibleSpaceIds(viewer);
        ZoneId viewerZone = MeetingTimeFormatter.resolveViewerZone(viewer);

        SearchService.Results results = query == null
                ? new SearchService.Results(List.of(), List.of(), List.of())
                : searchService.search(query, visibleSpaceIds);

        response.setContentType("text/html;charset=UTF-8");
        AiraPage.Builder pageBuilder = InteropAiraPageFactory.base(request,
                (query == null ? "Search" : "Search results for \"" + query + "\"") + " - InteropHub")
                .applicationSubtitle("Search")
                .mainClass("aira-main")
                .addLocalStylesheet("/css/search.css");
        if (query != null) {
            pageBuilder.pageHeading("Search results for “" + query + "”");
        } else {
            pageBuilder.pageHeading("Search");
        }
        AiraPage page = pageBuilder.build();

        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container--wide aira-stack\">");

            if (query == null) {
                out.println("      <p class=\"aira-page-intro\">Enter a search term to find topics and meetings.</p>");
            } else if (results.topics().isEmpty() && results.upcomingMeetings().isEmpty()
                    && results.previousMeetings().isEmpty()) {
                out.println("      <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No topics or meetings matched &ldquo;"
                        + escapeHtml(query) + "&rdquo;. Try a different name, abbreviation, or phrase.</p></div>");
            } else {
                renderTopicGroup(out, contextPath, results.topics());
                renderMeetingGroup(out, contextPath, "Upcoming meetings", results.upcomingMeetings(), viewerZone,
                        true);
                renderMeetingGroup(out, contextPath, "Previous meetings", results.previousMeetings(), viewerZone,
                        false);
            }

            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    private void renderTopicGroup(PrintWriter out, String contextPath, List<SearchService.TopicResult> topics) {
        if (topics.isEmpty()) {
            return;
        }
        out.println("      <section class=\"aira-section-card\">");
        out.println("        <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Topics</h2></div>");
        out.println("        <div class=\"aira-section-card__body\">");
        out.println("          <div class=\"aira-choice-list\">");
        int visibleCount = Math.min(INITIAL_GROUP_LIMIT, topics.size());
        for (int i = 0; i < visibleCount; i++) {
            renderTopicRow(out, contextPath, topics.get(i));
        }
        out.println("          </div>");
        if (topics.size() > INITIAL_GROUP_LIMIT) {
            int remaining = topics.size() - INITIAL_GROUP_LIMIT;
            out.println("          <details class=\"es-search-more\">");
            out.println("            <summary>Show " + remaining + " more topic" + (remaining == 1 ? "" : "s")
                    + "</summary>");
            out.println("            <div class=\"aira-choice-list\">");
            for (int i = INITIAL_GROUP_LIMIT; i < topics.size(); i++) {
                renderTopicRow(out, contextPath, topics.get(i));
            }
            out.println("            </div>");
            out.println("          </details>");
        }
        out.println("        </div>");
        out.println("      </section>");
    }

    private void renderTopicRow(PrintWriter out, String contextPath, SearchService.TopicResult topic) {
        out.println("            <a class=\"aira-choice-row aira-interactive-card\" href=\"" + contextPath
                + "/es/topic/" + topic.esTopicId() + "\">");
        out.println("              <div class=\"aira-choice-row__control\"><span class=\"aira-resource-link__icon\" aria-hidden=\"true\">"
                + escapeHtml(initialFor(topic.topicName())) + "</span></div>");
        out.println("              <div>");
        out.println("                <p class=\"aira-choice-row__title\">" + topic.titleHtml() + "</p>");
        out.println("                <p class=\"aira-choice-row__meta\">" + escapeHtml(orEmpty(topic.spaceName()))
                + (isBlank(topic.stage()) ? "" : " · " + escapeHtml(topic.stage())) + "</p>");
        if (!isBlank(topic.summaryHtml())) {
            out.println("                <p class=\"aira-choice-row__description\">" + topic.summaryHtml() + "</p>");
        }
        out.println("              </div>");
        out.println("            </a>");
    }

    private void renderMeetingGroup(PrintWriter out, String contextPath, String title,
            List<SearchService.MeetingResult> meetings, ZoneId viewerZone, boolean upcoming) {
        if (meetings.isEmpty()) {
            return;
        }
        out.println("      <section class=\"aira-section-card\">");
        out.println("        <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">"
                + escapeHtml(title) + "</h2></div>");
        out.println("        <div class=\"aira-section-card__body\">");
        out.println("          <div class=\"aira-choice-list\">");
        int visibleCount = Math.min(INITIAL_GROUP_LIMIT, meetings.size());
        for (int i = 0; i < visibleCount; i++) {
            renderMeetingRow(out, contextPath, meetings.get(i), viewerZone, upcoming);
        }
        out.println("          </div>");
        if (meetings.size() > INITIAL_GROUP_LIMIT) {
            int remaining = meetings.size() - INITIAL_GROUP_LIMIT;
            String noun = title.toLowerCase(java.util.Locale.ROOT);
            out.println("          <details class=\"es-search-more\">");
            out.println("            <summary>Show " + remaining + " more " + escapeHtml(noun) + "</summary>");
            out.println("            <div class=\"aira-choice-list\">");
            for (int i = INITIAL_GROUP_LIMIT; i < meetings.size(); i++) {
                renderMeetingRow(out, contextPath, meetings.get(i), viewerZone, upcoming);
            }
            out.println("            </div>");
            out.println("          </details>");
        }
        out.println("        </div>");
        out.println("      </section>");
    }

    private void renderMeetingRow(PrintWriter out, String contextPath, SearchService.MeetingResult meeting,
            ZoneId viewerZone, boolean upcoming) {
        ZonedDateTime displayTime = MeetingTimeFormatter.toViewerZone(meeting.scheduledStart(), meeting.timezoneId(),
                viewerZone);
        String whenText = upcoming ? MeetingTimeFormatter.formatDateAndTime(displayTime)
                : MeetingTimeFormatter.formatDate(displayTime);

        out.println("            <a class=\"aira-choice-row aira-interactive-card\" href=\"" + contextPath
                + "/es/agenda?meetingId=" + meeting.esMeetingId() + "\">");
        out.println("              <div class=\"aira-choice-row__control\"><span class=\"aira-resource-link__icon\" aria-hidden=\"true\">"
                + escapeHtml(initialFor(meeting.meetingName())) + "</span></div>");
        out.println("              <div>");
        out.println("                <p class=\"aira-choice-row__title\">" + meeting.titleHtml() + "</p>");
        out.println("                <p class=\"aira-choice-row__meta\">" + escapeHtml(whenText) + " · "
                + escapeHtml(orEmpty(meeting.spaceName())) + "</p>");
        if (meeting.matchLabel() != null) {
            out.println("                <p class=\"aira-choice-row__description\"><strong>"
                    + escapeHtml(meeting.matchLabel()) + ":</strong> " + meeting.matchDetailHtml() + "</p>");
        }
        if (meeting.additionalMatchSummary() != null) {
            out.println("                <p class=\"aira-choice-row__meta\">"
                    + escapeHtml(meeting.additionalMatchSummary()) + "</p>");
        }
        out.println("              </div>");
        out.println("            </a>");
    }

    private String initialFor(String name) {
        String trimmed = trimToNull(name);
        return trimmed == null ? "?" : trimmed.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
