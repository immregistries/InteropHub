package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.ApiTime;
import org.airahub.interophub.service.GuideApiException;
import org.airahub.interophub.service.GuideCommentService;
import org.airahub.interophub.service.GuideRegistrationService;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Recent Topic Reference comments and review flags on the public topic page.
 * Shown only for a signed-in viewer who can view the topic, and only when the
 * topic has an active guide. Design 6.2 keeps comments to signed-in viewers,
 * so an anonymous visitor of a public topic does not see names or excerpts.
 */
final class TopicReferenceCommentsPanel {
    private static final Logger LOGGER = Logger.getLogger(TopicReferenceCommentsPanel.class.getName());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private TopicReferenceCommentsPanel() {
    }

    static void render(PrintWriter out, String contextPath, User viewer, EsTopic topic) {
        if (viewer == null || topic == null || topic.getEsTopicId() == null) {
            return;
        }
        try {
            PanelView view = load(viewer, topic.getEsTopicId());
            if (view != null) {
                write(out, contextPath, topic.getEsTopicId(), view);
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Topic Reference comments panel failed for topic " + topic.getEsTopicId(), ex);
        }
    }

    private static PanelView load(User viewer, long topicId) {
        GuideRegistrationService registration = new GuideRegistrationService();
        TopicRow topic = registration.viewableTopic(viewer, topicId);
        if (topic == null) {
            return null;
        }
        DocumentRow active = null;
        for (DocumentRow document : registration.documentsForTopic(topicId)) {
            if (document.active) {
                active = document;
                break;
            }
        }
        if (active == null) {
            return null;
        }
        JSONObject listed;
        try {
            listed = new GuideCommentService().list(viewer, active.documentKey, "OPEN,DISPOSED", null, null);
        } catch (GuideApiException ex) {
            return null;
        }
        List<CommentItem> comments = new ArrayList<>();
        JSONArray threads = listed.getJSONArray("comments");
        for (int i = 0; i < threads.length(); i++) {
            JSONObject thread = threads.getJSONObject(i);
            if (thread.optBoolean("deleted") || thread.isNull("text")) {
                continue;
            }
            String disposition = thread.isNull("disposition") ? null : thread.getJSONObject("disposition").optString("label", null);
            comments.add(new CommentItem(thread.getLong("id"), excerpt(thread.optString("text", "")),
                    thread.getJSONObject("author").optString("displayName", ""),
                    date(thread.optString("createdAt", null)), disposition,
                    thread.optString("updatedAt", "")));
        }
        comments.sort(Comparator.comparing((CommentItem item) -> item.updatedAt).reversed());
        if (comments.size() > GuideCommentService.PANEL_LIMIT) {
            comments = new ArrayList<>(comments.subList(0, GuideCommentService.PANEL_LIMIT));
        }
        List<FlagItem> flags = new ArrayList<>();
        JSONArray flagRows = listed.getJSONArray("reviewFlags");
        for (int i = 0; i < flagRows.length(); i++) {
            JSONObject flag = flagRows.getJSONObject(i);
            flags.add(new FlagItem(flag.optString("anchor", ""), excerpt(flag.optString("note", "")),
                    date(flag.optString("createdAt", null)), flag.optString("updatedAt", "")));
        }
        flags.sort(Comparator.comparing((FlagItem item) -> item.updatedAt).reversed());
        if (flags.size() > GuideCommentService.PANEL_LIMIT) {
            flags = new ArrayList<>(flags.subList(0, GuideCommentService.PANEL_LIMIT));
        }
        return new PanelView(active.documentKey, registration.canEdit(viewer, topic), comments, flags);
    }

    static void write(PrintWriter out, String contextPath, long topicId, PanelView view) {
        String open = contextPath + "/guide/open?doc=" + encode(view.documentKey);
        out.println("          <section class=\"aira-section-card\" aria-labelledby=\"topic-reference-comments-title\">");
        out.println("            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\" id=\"topic-reference-comments-title\">Topic Reference comments</h2></div>");
        out.println("            <div class=\"aira-section-card__body aira-stack aira-stack--compact\">");
        if (view.comments.isEmpty()) {
            out.println("              <p class=\"aira-meta\">No open comments.</p>");
        }
        for (CommentItem comment : view.comments) {
            String href = open + "&amp;comment=" + comment.id;
            out.println("              <a class=\"aira-recent-topic\" href=\"" + href + "\">");
            out.println("                <span class=\"aira-recent-topic__title\">" + escape(comment.excerpt) + "</span>");
            out.println("                <span class=\"aira-recent-topic__meta\">" + escape(comment.displayName)
                    + " · " + escape(comment.date)
                    + (comment.disposition == null ? "" : " · " + escape(comment.disposition)) + "</span>");
            out.println("              </a>");
        }
        for (FlagItem flag : view.flags) {
            String href = open + "&amp;anchor=" + encode(flag.anchor);
            out.println("              <a class=\"aira-recent-topic\" href=\"" + href + "\">");
            out.println("                <span class=\"aira-recent-topic__title\">Flagged for review</span>");
            out.println("                <span class=\"aira-recent-topic__meta\">" + escape(flag.note) + " · "
                    + escape(flag.date) + "</span>");
            out.println("              </a>");
        }
        out.println("              <a class=\"aira-inline-link\" href=\"" + open + "\">Open the guide</a>");
        if (view.author) {
            out.println("              <a class=\"aira-inline-link\" href=\"" + contextPath + "/es/topic-guide-comments/"
                    + topicId + "\">Manage comments</a>");
        }
        out.println("            </div>");
        out.println("          </section>");
    }

    private static String excerpt(String text) {
        String collapsed = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= 120) {
            return collapsed;
        }
        return collapsed.substring(0, 117) + "...";
    }

    private static String date(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return "";
        }
        try {
            return DATE.format(ApiTime.parse(timestamp));
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    record PanelView(String documentKey, boolean author, List<CommentItem> comments, List<FlagItem> flags) {
    }

    record CommentItem(long id, String excerpt, String displayName, String date, String disposition, String updatedAt) {
    }

    record FlagItem(String anchor, String note, String date, String updatedAt) {
    }
}
