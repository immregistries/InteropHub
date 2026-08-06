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

/**
 * JSON suggestion endpoint backing the header search-as-you-type combobox.
 * Mapped to {@code /search/suggest}. Uses the same {@link SearchService} as
 * {@link SearchServlet} so ranking and authorization stay consistent between
 * the suggestion panel and the full results page.
 */
public class SearchSuggestServlet extends HttpServlet {

    private static final int TOPIC_LIMIT = 4;
    private static final int UPCOMING_LIMIT = 3;
    private static final int PREVIOUS_LIMIT = 2;

    private final AuthFlowService authFlowService;
    private final TopicSpaceAccessService topicSpaceAccessService;
    private final SearchService searchService;

    public SearchSuggestServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
        this.searchService = new SearchService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();
        String query = trimToNull(request.getParameter("q"));

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        User viewer = authenticatedUser.orElse(null);
        Set<Long> visibleSpaceIds = topicSpaceAccessService.getVisibleSpaceIds(viewer);
        ZoneId viewerZone = MeetingTimeFormatter.resolveViewerZone(viewer);

        SearchService.Results results = query == null
                ? new SearchService.Results(List.of(), List.of(), List.of())
                : searchService.search(query, visibleSpaceIds);

        List<SearchService.TopicResult> topics = limit(results.topics(), TOPIC_LIMIT);
        List<SearchService.MeetingResult> upcoming = limit(results.upcomingMeetings(), UPCOMING_LIMIT);
        List<SearchService.MeetingResult> previous = limit(results.previousMeetings(), PREVIOUS_LIMIT);

        try (PrintWriter out = response.getWriter()) {
            out.print("{\"ok\":true,\"topics\":[");
            for (int i = 0; i < topics.size(); i++) {
                if (i > 0) {
                    out.print(',');
                }
                writeTopic(out, contextPath, topics.get(i));
            }
            out.print("],\"upcomingMeetings\":[");
            for (int i = 0; i < upcoming.size(); i++) {
                if (i > 0) {
                    out.print(',');
                }
                writeMeeting(out, contextPath, upcoming.get(i), viewerZone, true);
            }
            out.print("],\"previousMeetings\":[");
            for (int i = 0; i < previous.size(); i++) {
                if (i > 0) {
                    out.print(',');
                }
                writeMeeting(out, contextPath, previous.get(i), viewerZone, false);
            }
            out.print("]}");
        }
    }

    private void writeTopic(PrintWriter out, String contextPath, SearchService.TopicResult topic) {
        out.print("{\"url\":\"");
        out.print(escapeJson(contextPath + "/es/topic/" + topic.esTopicId()));
        out.print("\",\"titleHtml\":\"");
        out.print(escapeJson(topic.titleHtml()));
        out.print("\",\"spaceName\":\"");
        out.print(escapeJson(orEmpty(topic.spaceName())));
        out.print("\",\"stage\":\"");
        out.print(escapeJson(orEmpty(topic.stage())));
        out.print("\",\"summaryHtml\":\"");
        out.print(escapeJson(orEmpty(topic.summaryHtml())));
        out.print("\"}");
    }

    private void writeMeeting(PrintWriter out, String contextPath, SearchService.MeetingResult meeting,
            ZoneId viewerZone, boolean upcoming) {
        ZonedDateTime displayTime = MeetingTimeFormatter.toViewerZone(meeting.scheduledStart(), meeting.timezoneId(),
                viewerZone);
        String whenText = upcoming ? MeetingTimeFormatter.formatDateAndTime(displayTime)
                : MeetingTimeFormatter.formatDate(displayTime);

        out.print("{\"url\":\"");
        out.print(escapeJson(contextPath + "/es/agenda?meetingId=" + meeting.esMeetingId()));
        out.print("\",\"titleHtml\":\"");
        out.print(escapeJson(meeting.titleHtml()));
        out.print("\",\"whenText\":\"");
        out.print(escapeJson(whenText));
        out.print("\",\"spaceName\":\"");
        out.print(escapeJson(orEmpty(meeting.spaceName())));
        out.print("\",\"matchLabel\":");
        out.print(meeting.matchLabel() == null ? "null" : "\"" + escapeJson(meeting.matchLabel()) + "\"");
        out.print(",\"matchDetailHtml\":");
        out.print(meeting.matchDetailHtml() == null ? "null" : "\"" + escapeJson(meeting.matchDetailHtml()) + "\"");
        out.print("}");
    }

    private <T> List<T> limit(List<T> list, int max) {
        return list.size() <= max ? list : list.subList(0, max);
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

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
