package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.GuideApiException;
import org.airahub.interophub.service.GuideRegistrationService;
import org.airahub.interophub.service.GuideRegistrationService.UrlInput;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.airahub.interophub.service.GuideRows.UrlBinding;
import org.json.JSONObject;
import org.immregistries.aira.web.AiraPage;

/** The same registration fields and rules as the API, for people who can edit the topic. */
public class GuideRegistrationPageServlet extends HttpServlet {
    private static final int URL_ROWS = 6;

    private final AuthFlowService authFlowService = new AuthFlowService();
    private final GuideRegistrationService registration = new GuideRegistrationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        show(request, response, null, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Viewer viewer = viewer(request, response);
        if (viewer == null) {
            return;
        }
        boolean dryRun = "check".equals(request.getParameter("intent"));
        List<UrlInput> urls = postedUrls(request);
        try {
            JSONObject result;
            if ("create".equals(request.getParameter("mode"))) {
                result = registration.create(viewer.user, null, "WEB", dryRun, viewer.topic.code,
                        request.getParameter("documentKey"), request.getParameter("title"),
                        request.getParameter("repositoryUrl"), urls);
            } else {
                boolean active = request.getParameter("active") != null;
                result = registration.update(viewer.user, null, "WEB", dryRun, request.getParameter("documentKey"),
                        true, request.getParameter("title"), true, request.getParameter("repositoryUrl"), true, urls,
                        true, active);
            }
            String notice = dryRun
                    ? "Check passed. Nothing was saved."
                    : "Saved. Allowlist added " + result.getJSONObject("allowlistChanges").getJSONArray("added").length()
                            + " and removed "
                            + result.getJSONObject("allowlistChanges").getJSONArray("removed").length() + ".";
            show(request, response, notice, null);
        } catch (GuideApiException ex) {
            show(request, response, null, ex.getMessage());
        }
    }

    private void show(HttpServletRequest request, HttpServletResponse response, String notice, String error)
            throws IOException {
        Viewer viewer = viewer(request, response);
        if (viewer == null) {
            return;
        }
        List<DocumentRow> documents = registration.documentsForTopic(viewer.topic.id);
        String requestedKey = trim(request.getParameter("document"));
        DocumentRow editing = null;
        for (DocumentRow document : documents) {
            if (requestedKey != null && requestedKey.equals(document.documentKey)) {
                editing = document;
            }
        }
        if (editing == null && !documents.isEmpty() && !"new".equals(request.getParameter("mode"))) {
            editing = documents.get(0);
        }
        render(request, response, viewer, documents, editing, notice, error);
    }

    private void render(HttpServletRequest request, HttpServletResponse response, Viewer viewer,
            List<DocumentRow> documents, DocumentRow editing, String notice, String error) throws IOException {
        String contextPath = request.getContextPath();
        String action = contextPath + "/es/topic-guide/" + viewer.topic.id;
        AiraPage page = InteropAiraPageFactory.base(request, "Topic Reference - InteropHub")
                .applicationSubtitle("Topic management")
                .mainClass("aira-main")
                .build();
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <h1 class=\"aira-page-title\">Topic Reference</h1>");
            out.println("      <p class=\"aira-meta\">" + escape(viewer.topic.name) + " (" + escape(viewer.topic.code)
                    + ")</p>");
            out.println("      <div class=\"aira-alert aira-alert--warning\"><p>" + warning(viewer.topic) + "</p></div>");
            if (notice != null) {
                out.println("      <div class=\"aira-alert aira-alert--success\"><p>" + escape(notice) + "</p></div>");
            }
            if (error != null) {
                out.println("      <div class=\"aira-alert aira-alert--error\"><p>" + escape(error) + "</p></div>");
            }
            if (documents.size() > 1 || (editing != null && documents.size() == 1)) {
                out.println("      <ul class=\"aira-stack aira-stack--compact\">");
                for (DocumentRow document : documents) {
                    out.println("        <li><a class=\"aira-inline-link\" href=\"" + action + "?document="
                            + escape(document.documentKey) + "\">" + escape(document.documentKey) + "</a>"
                            + (document.active ? "" : " (inactive)") + "</li>");
                }
                out.println("        <li><a class=\"aira-inline-link\" href=\"" + action + "?mode=new\">Register another guide</a></li>");
                out.println("      </ul>");
            }
            boolean creating = editing == null;
            out.println("      <form class=\"aira-stack\" method=\"post\" action=\"" + action + "\">");
            out.println("        <input type=\"hidden\" name=\"mode\" value=\"" + (creating ? "create" : "update") + "\">");
            field(out, "documentKey", "Document key", creating ? "" : editing.documentKey, !creating);
            field(out, "title", "Title", creating ? "" : editing.title, false);
            field(out, "repositoryUrl", "Repository URL", creating || editing.repositoryUrl == null ? "" : editing.repositoryUrl, false);
            out.println("        <fieldset class=\"aira-stack\">");
            out.println("          <legend class=\"aira-label\">Published URLs</legend>");
            out.println("          <p class=\"aira-field-help\">The first URL is the one /guide/open uses. Leave unused rows blank.</p>");
            for (int i = 0; i < URL_ROWS; i++) {
                String url = "";
                String label = "";
                if (!creating && i < editing.urls.size()) {
                    UrlBinding binding = editing.urls.get(i);
                    url = binding.url;
                    label = binding.label;
                }
                out.println("          <div class=\"aira-field\">");
                out.println("            <label class=\"aira-label\" for=\"label" + i + "\">Label " + (i + 1) + "</label>");
                out.println("            <input class=\"aira-input\" id=\"label" + i + "\" name=\"label" + i
                        + "\" type=\"text\" maxlength=\"40\" value=\"" + escape(label) + "\">");
                out.println("          </div>");
                out.println("          <div class=\"aira-field\">");
                out.println("            <label class=\"aira-label\" for=\"url" + i + "\">URL " + (i + 1) + "</label>");
                out.println("            <input class=\"aira-input\" id=\"url" + i + "\" name=\"url" + i
                        + "\" type=\"url\" value=\"" + escape(url) + "\">");
                out.println("          </div>");
            }
            out.println("        </fieldset>");
            if (!creating) {
                out.println("        <div class=\"aira-field\">");
                out.println("          <label class=\"aira-label\" for=\"active\"><input id=\"active\" name=\"active\" type=\"checkbox\" value=\"true\""
                        + (editing.active ? " checked" : "") + "> Active</label>");
                out.println("        </div>");
            }
            out.println("        <p>");
            out.println("          <button class=\"aira-button aira-button--secondary\" type=\"submit\" name=\"intent\" value=\"check\">Check without saving</button>");
            out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\" name=\"intent\" value=\"save\">Save</button>");
            out.println("        </p>");
            out.println("      </form>");
            out.println("      <p><a class=\"aira-inline-link\" href=\"" + contextPath + "/es/topic-manage/"
                    + viewer.topic.id + "/followers\">Back to topic management</a></p>");
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    private static void field(PrintWriter out, String name, String label, String value, boolean readOnly) {
        out.println("        <div class=\"aira-field\">");
        out.println("          <label class=\"aira-label\" for=\"" + name + "\">" + escape(label) + "</label>");
        out.println("          <input class=\"aira-input\" id=\"" + name + "\" name=\"" + name + "\" type=\"text\" value=\""
                + escape(value) + "\"" + (readOnly ? " readonly" : "") + ">");
        out.println("        </div>");
    }

    private static String warning(TopicRow topic) {
        String which = "PRIVATE".equals(topic.visibility)
                ? "This topic is in a private Topic Space, so do not link it to a publicly published guide."
                : "This topic is in a public Topic Space, so do not link it to a guide that is kept private.";
        return escape(which) + " The reverse is also a setup mistake: a private topic should not be linked to a publicly published guide, and a public topic should not be linked to a private one. InteropHub cannot prevent either pairing.";
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
        TopicRow viewable = registration.viewableTopic(user, topicId);
        if (viewable == null) {
            notFound(request, response);
            return null;
        }
        if (!registration.canEdit(user, viewable)) {
            response.sendRedirect(contextPath + "/es/topic/" + topicId);
            return null;
        }
        return new Viewer(user, viewable);
    }

    private static List<UrlInput> postedUrls(HttpServletRequest request) {
        List<UrlInput> urls = new ArrayList<>();
        for (int i = 0; i < URL_ROWS; i++) {
            String url = trim(request.getParameter("url" + i));
            String label = trim(request.getParameter("label" + i));
            if (url == null && label == null) {
                continue;
            }
            urls.add(new UrlInput(url, label));
        }
        return urls;
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

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private record Viewer(User user, TopicRow topic) {
    }
}
