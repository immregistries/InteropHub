package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.EsTopicViewHistoryService;
import org.airahub.interophub.service.TopicSpaceAccessService;

/**
 * Fetches and renders the "Recently Viewed Topics" right-rail card. Shared by
 * the topic detail page, the topics browse page, and the topic-manage page so
 * all three present the same compact list. Pages that need special link
 * destinations (e.g. topic-manage keeping managers on the same view) supply
 * their own {@code hrefBuilder}; everything else about the card is identical.
 */
final class RecentlyViewedTopicsRenderer {

    private static final int DEFAULT_LIMIT = 10;

    private RecentlyViewedTopicsRenderer() {
    }

    static List<EsTopicViewHistoryService.RecentlyViewedTopic> fetchVisible(
            EsTopicViewHistoryService topicViewHistoryService, TopicSpaceAccessService topicSpaceAccessService,
            User viewer) {
        if (viewer == null || viewer.getUserId() == null) {
            return List.of();
        }
        Set<Long> visibleSpaceIds = topicSpaceAccessService.getVisibleSpaceIds(viewer);
        return topicViewHistoryService.findRecentAuthenticatedTopicViews(viewer.getUserId(), DEFAULT_LIMIT)
                .stream()
                .filter(item -> item.topicId() != null
                        && item.topicSpaceId() != null
                        && visibleSpaceIds.contains(item.topicSpaceId()))
                .limit(DEFAULT_LIMIT)
                .toList();
    }

    static void render(PrintWriter out, Long currentTopicId,
            List<EsTopicViewHistoryService.RecentlyViewedTopic> recentlyViewedTopics, boolean signedIn,
            Function<Long, String> hrefBuilder) {
        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Recently Viewed Topics</h2></div>");
        out.println("            <div class=\"aira-section-card__body aira-stack aira-stack--compact\">");
        if (!signedIn) {
            out.println(
                    "              <p class=\"aira-meta\">Sign in to keep a persistent recently viewed topic list.</p>");
        } else if (recentlyViewedTopics.isEmpty()) {
            out.println("              <p class=\"aira-meta\">No recent topic views yet.</p>");
        } else {
            for (EsTopicViewHistoryService.RecentlyViewedTopic viewedTopic : recentlyViewedTopics) {
                boolean isCurrentTopic = currentTopicId != null && currentTopicId.equals(viewedTopic.topicId());
                String ariaCurrent = isCurrentTopic ? " aria-current=\"page\"" : "";
                out.println("              <a class=\"aira-recent-topic\" href=\""
                        + hrefBuilder.apply(viewedTopic.topicId()) + "\"" + ariaCurrent + ">");
                out.println("                <span class=\"aira-recent-topic__title\">"
                        + escapeHtml(orEmpty(viewedTopic.topicName())) + "</span>");
                out.println("              </a>");
            }
        }
        out.println("            </div>");
        out.println("          </section>");
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
