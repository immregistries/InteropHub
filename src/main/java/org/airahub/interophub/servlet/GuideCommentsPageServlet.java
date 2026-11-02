package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.ApiTime;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.GuideApiException;
import org.airahub.interophub.service.GuideCommentService;
import org.airahub.interophub.service.GuideRegistrationService;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.immregistries.aira.web.AiraPage;
import org.json.JSONArray;
import org.json.JSONObject;

/** Author comment management for one topic's guide. Uses {@link GuideCommentService}. */
public class GuideCommentsPageServlet extends HttpServlet {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final AuthFlowService authFlowService = new AuthFlowService();
    private final GuideRegistrationService registration = new GuideRegistrationService();
    private final GuideCommentService comments = new GuideCommentService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        show(request, response, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Viewer viewer = viewer(request, response);
        if (viewer == null) {
            return;
        }
        String intent = request.getParameter("intent");
        long id;
        try {
            id = Long.parseLong(request.getParameter("id"));
        } catch (NumberFormatException ex) {
            show(request, response, "That comment was not found.");
            return;
        }
        try {
            if ("delete".equals(intent)) {
                comments.delete(viewer.user, null, id);
                redirect(request, response, viewer, "deleted");
                return;
            }
            JSONObject body = new JSONObject();
            String expected = trim(request.getParameter("expectedUpdatedAt"));
            if (expected != null) {
                body.put("expectedUpdatedAt", expected);
            }
            if ("dispose".equals(intent)) {
                body.put("dispositionCode", request.getParameter("dispositionCode"));
                body.put("changeNote", request.getParameter("changeNote"));
            } else if ("clear".equals(intent)) {
                body.put("dispositionCode", JSONObject.NULL);
            } else if ("close".equals(intent)) {
                body.put("state", "CLOSED");
            } else if ("reopen".equals(intent)) {
                body.put("state", "OPEN");
            } else {
                show(request, response, "That action is not available.");
                return;
            }
            comments.patch(viewer.user, null, id, body);
            redirect(request, response, viewer, noticeFor(intent));
        } catch (GuideApiException ex) {
            show(request, response, ex.getMessage());
        }
    }

    private void show(HttpServletRequest request, HttpServletResponse response, String error) throws IOException {
        Viewer viewer = viewer(request, response);
        if (viewer == null) {
            return;
        }
        render(request, response, viewer, error);
    }

    private void render(HttpServletRequest request, HttpServletResponse response, Viewer viewer, String error)
            throws IOException {
        String contextPath = request.getContextPath();
        String action = contextPath + "/es/topic-guide-comments/" + viewer.topic.id;
        String show = showFilter(request.getParameter("show"));
        DocumentRow document = selected(viewer.documents, trim(request.getParameter("document")));
        AiraPage page = InteropAiraPageFactory.base(request, "Topic Reference comments - InteropHub")
                .applicationSubtitle("Topic management")
                .mainClass("aira-main")
                .build();
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <h1 class=\"aira-page-title\">Topic Reference comments</h1>");
            out.println("      <p class=\"aira-meta\">" + escape(viewer.topic.name) + " (" + escape(viewer.topic.code)
                    + ")</p>");
            String notice = noticeText(request.getParameter("notice"));
            if (notice != null) {
                out.println("      <div class=\"aira-alert aira-alert--success\"><p>" + escape(notice) + "</p></div>");
            }
            if (error != null) {
                out.println("      <div class=\"aira-alert aira-alert--error\"><p>" + escape(error) + "</p></div>");
            }
            if (document == null) {
                out.println("      <p>No Topic Reference is registered for this topic.</p>");
                out.println("      <p><a class=\"aira-inline-link\" href=\"" + contextPath + "/es/topic-guide/"
                        + viewer.topic.id + "\">Register a guide</a></p>");
            } else {
                if (viewer.documents.size() > 1) {
                    out.println("      <ul class=\"aira-stack aira-stack--compact\">");
                    for (DocumentRow candidate : viewer.documents) {
                        out.println("        <li><a class=\"aira-inline-link\" href=\"" + action + "?document="
                                + encode(candidate.documentKey) + "&amp;show=" + encode(show) + "\">"
                                + escape(candidate.documentKey) + "</a>"
                                + (candidate.active ? "" : " (inactive)") + "</li>");
                    }
                    out.println("      </ul>");
                }
                if (!document.active) {
                    out.println("      <div class=\"aira-alert aira-alert--warning\"><p>This guide registration is inactive. Comments can be read here, and changes are refused until it is active again.</p></div>");
                }
                out.println("      <form class=\"aira-cluster\" method=\"get\" action=\"" + action + "\">");
                out.println("        <input type=\"hidden\" name=\"document\" value=\"" + escape(document.documentKey) + "\">");
                out.println("        <label class=\"aira-label\" for=\"show\">Show</label>");
                out.println("        <select class=\"aira-select\" id=\"show\" name=\"show\">");
                option(out, "current", "Everything except closed", show);
                option(out, "all", "All states", show);
                option(out, "OPEN", "Open", show);
                option(out, "DISPOSED", "Disposed", show);
                option(out, "CLOSED", "Closed", show);
                option(out, "WITHDRAWN", "Withdrawn", show);
                out.println("        </select>");
                out.println("        <button class=\"aira-button aira-button--secondary\" type=\"submit\">Apply</button>");
                out.println("      </form>");
                out.println("      <p><a class=\"aira-inline-link\" href=\"" + contextPath + "/guide/open?doc="
                        + encode(document.documentKey) + "\">Open the guide</a></p>");
                JSONObject catalog = comments.document(viewer.user, document.documentKey);
                JSONObject listed = comments.list(viewer.user, document.documentKey, stateQuery(show), null, null);
                JSONArray threads = listed.getJSONArray("comments");
                if (threads.isEmpty()) {
                    out.println("      <p class=\"aira-meta\">No comments in this view.</p>");
                }
                for (int i = 0; i < threads.length(); i++) {
                    thread(out, action, document.documentKey, show, catalog.getJSONArray("dispositions"),
                            threads.getJSONObject(i));
                }
            }
            out.println("      <p><a class=\"aira-inline-link\" href=\"" + contextPath + "/es/topic-manage/"
                    + viewer.topic.id + "/followers\">Back to topic management</a></p>");
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    private static void thread(PrintWriter out, String action, String documentKey, String show, JSONArray dispositions,
            JSONObject thread) {
        String state = thread.optString("state", "");
        String updatedAt = thread.optString("updatedAt", "");
        long id = thread.getLong("id");
        out.println("      <article class=\"aira-section-card\">");
        out.println("        <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">"
                + escape(thread.isNull("text") ? "Withdrawn by the commenter." : thread.optString("text", ""))
                + "</h2></div>");
        out.println("        <div class=\"aira-section-card__body aira-stack\">");
        out.println("          <p class=\"aira-meta\">" + escape(thread.getJSONObject("author").optString("displayName", ""))
                + " · " + escape(date(thread.optString("createdAt", null)))
                + " · " + escape(state)
                + (thread.isNull("anchor") ? "" : " · " + escape(thread.optString("anchor", "")))
                + "</p>");
        if (!thread.isNull("disposition")) {
            String note = thread.isNull("changeNote") ? "" : thread.optString("changeNote", "");
            out.println("          <p><span class=\"aira-badge aira-badge--subtle\">"
                    + escape(thread.getJSONObject("disposition").optString("label", "")) + "</span> "
                    + escape(note) + "</p>");
        }
        out.println("          <p><a class=\"aira-inline-link\" href=\"" + guideLink(action, documentKey, id)
                + "\">Open in the guide</a></p>");
        if ("OPEN".equals(state) || "DISPOSED".equals(state)) {
            dispositionForm(out, action, documentKey, show, dispositions, thread, id, updatedAt);
            actionForm(out, action, documentKey, show, id, updatedAt, "close", "Close",
                    "Closing hides this comment in the guide until it is reopened. Replies are refused while it is closed.");
        } else if ("CLOSED".equals(state)) {
            actionForm(out, action, documentKey, show, id, updatedAt, "reopen", "Reopen", null);
        }
        deleteForm(out, action, documentKey, show, id, true);
        JSONArray replies = thread.optJSONArray("replies");
        if (replies != null) {
            for (int i = 0; i < replies.length(); i++) {
                JSONObject reply = replies.getJSONObject(i);
                if (reply.optBoolean("deleted")) {
                    continue;
                }
                out.println("          <article class=\"aira-stack aira-stack--compact\">");
                out.println("            <p>" + escape(reply.isNull("text") ? "Withdrawn by the commenter."
                        : reply.optString("text", "")) + "</p>");
                out.println("            <p class=\"aira-meta\">" + escape(reply.getJSONObject("author").optString("displayName", ""))
                        + " · " + escape(date(reply.optString("createdAt", null))) + "</p>");
                deleteForm(out, action, documentKey, show, reply.getLong("id"), false);
                out.println("          </article>");
            }
        }
        out.println("        </div>");
        out.println("      </article>");
    }

    private static void dispositionForm(PrintWriter out, String action, String documentKey, String show,
            JSONArray dispositions, JSONObject thread, long id, String updatedAt) {
        String current = thread.isNull("disposition") ? "" : thread.getJSONObject("disposition").optString("code", "");
        String note = thread.isNull("changeNote") ? "" : thread.optString("changeNote", "");
        out.println("          <form class=\"aira-stack\" method=\"post\" action=\"" + action + "\">");
        hidden(out, documentKey, show, id, updatedAt);
        out.println("            <div class=\"aira-field\">");
        out.println("              <label class=\"aira-label\" for=\"disposition-" + id + "\">Disposition</label>");
        out.println("              <select class=\"aira-select\" id=\"disposition-" + id + "\" name=\"dispositionCode\">");
        out.println("                <option value=\"\">Choose a disposition</option>");
        for (int i = 0; i < dispositions.length(); i++) {
            JSONObject disposition = dispositions.getJSONObject(i);
            String code = disposition.optString("code", "");
            out.println("                <option value=\"" + escape(code) + "\""
                    + (code.equals(current) ? " selected" : "") + ">" + escape(disposition.optString("label", code))
                    + "</option>");
        }
        out.println("              </select>");
        out.println("            </div>");
        out.println("            <div class=\"aira-field\">");
        out.println("              <label class=\"aira-label\" for=\"note-" + id + "\">Change note</label>");
        out.println("              <textarea class=\"aira-textarea\" id=\"note-" + id + "\" name=\"changeNote\" maxlength=\"500\" rows=\"3\">"
                + escape(note) + "</textarea>");
        out.println("            </div>");
        out.println("            <p>");
        out.println("              <button class=\"aira-button aira-button--primary\" type=\"submit\" name=\"intent\" value=\"dispose\">Save disposition</button>");
        out.println("            </p>");
        out.println("          </form>");
        if (!current.isEmpty()) {
            actionForm(out, action, documentKey, show, id, updatedAt, "clear", "Clear disposition", null);
        }
    }

    private static void actionForm(PrintWriter out, String action, String documentKey, String show, long id,
            String updatedAt, String intent, String label, String help) {
        out.println("          <form method=\"post\" action=\"" + action + "\">");
        hidden(out, documentKey, show, id, updatedAt);
        if (help != null) {
            out.println("            <p class=\"aira-field-help\">" + escape(help) + "</p>");
        }
        out.println("            <button class=\"aira-button aira-button--secondary\" type=\"submit\" name=\"intent\" value=\""
                + intent + "\">" + escape(label) + "</button>");
        out.println("          </form>");
    }

    private static void deleteForm(PrintWriter out, String action, String documentKey, String show, long id,
            boolean thread) {
        out.println("          <details>");
        out.println("            <summary>Delete</summary>");
        out.println("            <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\"" + action + "\">");
        hidden(out, documentKey, show, id, null);
        out.println("              <p>" + (thread
                ? "Delete this comment and its replies? They stay in the audit record and disappear from the guide."
                : "Delete this reply? It stays in the audit record and disappears from the guide.") + "</p>");
        out.println("              <button class=\"aira-button aira-button--secondary\" type=\"submit\" name=\"intent\" value=\"delete\">Delete</button>");
        out.println("            </form>");
        out.println("          </details>");
    }

    private static void hidden(PrintWriter out, String documentKey, String show, long id, String updatedAt) {
        out.println("            <input type=\"hidden\" name=\"document\" value=\"" + escape(documentKey) + "\">");
        out.println("            <input type=\"hidden\" name=\"show\" value=\"" + escape(show) + "\">");
        out.println("            <input type=\"hidden\" name=\"id\" value=\"" + id + "\">");
        if (updatedAt != null && !updatedAt.isBlank()) {
            out.println("            <input type=\"hidden\" name=\"expectedUpdatedAt\" value=\"" + escape(updatedAt) + "\">");
        }
    }

    private static String guideLink(String action, String documentKey, long id) {
        String context = action.substring(0, action.indexOf("/es/topic-guide-comments/"));
        return context + "/guide/open?doc=" + encode(documentKey) + "&amp;comment=" + id;
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, Viewer viewer, String notice)
            throws IOException {
        String document = trim(request.getParameter("document"));
        String show = showFilter(request.getParameter("show"));
        String target = request.getContextPath() + "/es/topic-guide-comments/" + viewer.topic.id
                + "?show=" + encode(show) + "&notice=" + encode(notice);
        if (document != null) {
            target += "&document=" + encode(document);
        }
        response.sendRedirect(target);
    }

    private Viewer viewer(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();
        Long topicId = topicId(request.getPathInfo());
        if (topicId == null) {
            response.sendRedirect(contextPath + "/es/topics");
            return null;
        }
        Optional<User> authenticated = authFlowService.findAuthenticatedUser(request);
        if (authenticated.isEmpty()) {
            response.sendRedirect(contextPath + "/es/topic/" + topicId);
            return null;
        }
        User user = authenticated.get();
        TopicRow topic = registration.viewableTopic(user, topicId);
        if (topic == null) {
            notFound(request, response);
            return null;
        }
        if (!registration.canEdit(user, topic)) {
            response.sendRedirect(contextPath + "/es/topic/" + topicId);
            return null;
        }
        return new Viewer(user, topic, registration.documentsForTopic(topicId));
    }

    private static DocumentRow selected(List<DocumentRow> documents, String requested) {
        DocumentRow firstActive = null;
        for (DocumentRow document : documents) {
            if (requested != null && requested.equals(document.documentKey)) {
                return document;
            }
            if (firstActive == null && document.active) {
                firstActive = document;
            }
        }
        if (firstActive != null) {
            return firstActive;
        }
        return documents.isEmpty() ? null : documents.get(0);
    }

    static String stateQuery(String show) {
        return switch (showFilter(show)) {
            case "all" -> "all";
            case "OPEN", "DISPOSED", "CLOSED", "WITHDRAWN" -> showFilter(show);
            default -> "OPEN,DISPOSED,WITHDRAWN";
        };
    }

    private static String showFilter(String show) {
        if (show == null || show.isBlank() || "current".equals(show)) {
            return "current";
        }
        if ("all".equals(show) || "OPEN".equals(show) || "DISPOSED".equals(show) || "CLOSED".equals(show)
                || "WITHDRAWN".equals(show)) {
            return show;
        }
        return "current";
    }

    private static String noticeFor(String intent) {
        return switch (intent) {
            case "dispose" -> "saved";
            case "clear" -> "cleared";
            case "close" -> "closed";
            case "reopen" -> "reopened";
            default -> "saved";
        };
    }

    private static String noticeText(String code) {
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "saved" -> "Disposition saved.";
            case "cleared" -> "Disposition cleared. The comment is open again.";
            case "closed" -> "Comment closed.";
            case "reopened" -> "Comment reopened.";
            case "deleted" -> "Comment deleted.";
            default -> null;
        };
    }

    private static void option(PrintWriter out, String value, String label, String selected) {
        out.println("          <option value=\"" + value + "\"" + (value.equals(selected) ? " selected" : "") + ">"
                + escape(label) + "</option>");
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

    private static Long topicId(String pathInfo) {
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            return null;
        }
        String raw = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        int slash = raw.indexOf('/');
        if (slash >= 0) {
            raw = raw.substring(0, slash);
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void notFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        AiraPage page = InteropAiraPageFactory.base(request, "Topic Not Found - InteropHub").build();
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <h1 class=\"aira-page-title\">Topic Not Found</h1>");
            out.println("      <div class=\"aira-alert aira-alert--error\"><p>This topic could not be found.</p></div>");
            out.println("    </div>");
            page.writeEnd(out);
        }
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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

    private record Viewer(User user, TopicRow topic, List<DocumentRow> documents) {
    }
}
