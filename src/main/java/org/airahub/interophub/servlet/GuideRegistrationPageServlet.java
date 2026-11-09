package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.GuideApiException;
import org.airahub.interophub.service.GuideHostingService;
import org.airahub.interophub.service.GuideHtmlRules;
import org.airahub.interophub.service.GuideRegistrationService;
import org.airahub.interophub.service.GuideRegistrationService.UrlInput;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.airahub.interophub.service.GuideRows.UrlBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import org.immregistries.aira.web.AiraPage;

/** The same registration fields and rules as the API, for people who can edit the topic. */
public class GuideRegistrationPageServlet extends HttpServlet {
    private static final int URL_ROWS = 6;

    private final AuthFlowService authFlowService = new AuthFlowService();
    private final GuideRegistrationService registration = new GuideRegistrationService();
    private final GuideHostingService hosting = new GuideHostingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        show(request, response, null, null, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!CsrfTokenSupport.isValid(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Invalid request token. Reload the page and try again.");
            return;
        }
        if ("upload".equals(request.getParameter("intent"))) {
            upload(request, response);
            return;
        }
        Viewer viewer = viewer(request, response);
        if (viewer == null) {
            return;
        }
        boolean dryRun = "check".equals(request.getParameter("intent"));
        PostedForm posted = null;
        try {
            posted = readPosted(request);
            List<UrlInput> urls = postedUrls(posted);
            JSONObject result;
            boolean hub = GuideHtmlRules.HUB.equals(posted.hosting);
            if (posted.creating) {
                result = registration.create(viewer.user, null, "WEB", dryRun, viewer.topic.code,
                        posted.documentKey, posted.title, posted.repositoryUrl, hub ? List.of() : urls,
                        posted.hosting);
            } else {
                result = registration.update(viewer.user, null, "WEB", dryRun, posted.documentKey,
                        true, posted.title, true, posted.repositoryUrl, !hub, hub ? List.of() : urls,
                        true, posted.active, true, posted.hosting);
            }
            String notice = dryRun
                    ? "Check passed. Nothing was saved."
                    : "Saved. Allowlist added " + result.getJSONObject("allowlistChanges").getJSONArray("added").length()
                            + " and removed "
                            + result.getJSONObject("allowlistChanges").getJSONArray("removed").length() + ".";
            show(request, response, dryRun ? posted : null, notice, null);
        } catch (GuideApiException ex) {
            show(request, response, posted, null, ex.getMessage());
        }
    }

    private void show(HttpServletRequest request, HttpServletResponse response, PostedForm posted, String notice,
            String error) throws IOException {
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
        if (notice == null) {
            if ("new".equals(request.getParameter("uploaded"))) {
                notice = "Uploaded. The new label is ready to open.";
            } else if ("replaced".equals(request.getParameter("uploaded"))) {
                notice = "Replaced that label. The earlier upload is still listed.";
            }
        }
        render(request, response, viewer, documents, editing, posted, notice, error);
    }

    private void render(HttpServletRequest request, HttpServletResponse response, Viewer viewer,
            List<DocumentRow> documents, DocumentRow editing, PostedForm posted, String notice, String error)
            throws IOException {
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
            String shownHosting = posted != null ? posted.hosting
                    : (editing == null || editing.hosting == null ? GuideHtmlRules.EXTERNAL : editing.hosting);
            out.println("      <div class=\"aira-alert aira-alert--warning\"><p>" + warning(viewer.topic, shownHosting)
                    + "</p></div>");
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
            boolean creating = posted != null ? posted.creating : editing == null;
            String documentKey = posted != null ? posted.documentKey : (creating ? "" : editing.documentKey);
            String title = posted != null ? posted.title : (creating ? "" : editing.title);
            String repositoryUrl = posted != null ? posted.repositoryUrl
                    : (creating || editing.repositoryUrl == null ? "" : editing.repositoryUrl);
            out.println("      <form class=\"aira-stack\" method=\"post\" action=\"" + action + "\">");
            out.println("        <input type=\"hidden\" name=\"csrfToken\" value=\""
                    + escape(CsrfTokenSupport.getOrCreateToken(request)) + "\">");
            out.println("        <input type=\"hidden\" name=\"mode\" value=\"" + (creating ? "create" : "update") + "\">");
            field(out, "documentKey", "Document key", documentKey, !creating);
            field(out, "title", "Title", title, false);
            field(out, "repositoryUrl", "Repository URL", repositoryUrl, false);
            out.println("        <div class=\"aira-field\">");
            out.println("          <label class=\"aira-label\" for=\"hosting\">Hosting</label>");
            out.println("          <select class=\"aira-select\" id=\"hosting\" name=\"hosting\">");
            option(out, GuideHtmlRules.EXTERNAL, "External (published elsewhere)", shownHosting);
            option(out, GuideHtmlRules.HUB, "Hosted on InteropHub", shownHosting);
            out.println("          </select>");
            out.println("          <p class=\"aira-field-help\">Hosted replaces the published URLs with this hub's latest and main view URLs. Switching back to external removes those view URLs; fill in the published URLs in the same save if you have them.</p>");
            out.println("        </div>");
            out.println("        <fieldset class=\"aira-stack\">");
            out.println("          <legend class=\"aira-label\">Published URLs</legend>");
            out.println("          <p class=\"aira-field-help\">The first URL is the one /guide/open uses. Leave unused rows blank. These rows are ignored while hosting is Hosted on InteropHub.</p>");
            for (int i = 0; i < URL_ROWS; i++) {
                String url = "";
                String label = "";
                if (posted != null) {
                    url = posted.urls[i];
                    label = posted.labels[i];
                } else if (!creating && !GuideHtmlRules.HUB.equals(editing.hosting) && i < editing.urls.size()) {
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
                boolean active = posted != null ? posted.active : editing.active;
                out.println("          <label class=\"aira-label\" for=\"active\"><input id=\"active\" name=\"active\" type=\"checkbox\" value=\"true\""
                        + (active ? " checked" : "") + "> Active</label>");
                out.println("        </div>");
            }
            out.println("        <p>");
            out.println("          <button class=\"aira-button aira-button--secondary\" type=\"submit\" name=\"intent\" value=\"check\">Check without saving</button>");
            out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\" name=\"intent\" value=\"save\">Save</button>");
            out.println("        </p>");
            out.println("      </form>");
            if (!creating && editing != null && GuideHtmlRules.HUB.equals(editing.hosting)) {
                versions(out, request, action, editing.documentKey, viewer);
            }
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

    private static String warning(TopicRow topic, String hosting) {
        if (GuideHtmlRules.HUB.equals(hosting)) {
            return "This guide is hosted on InteropHub. Only signed-in people who can view the topic can read it.";
        }
        String which = "PRIVATE".equals(topic.visibility)
                ? "This topic is in a private Topic Space, so do not link it to a publicly published guide. Choose Hosted on InteropHub instead."
                : "This topic is in a public Topic Space, so do not link it to a guide that is kept private.";
        return escape(which) + " The reverse is also a setup mistake: a private topic should not be linked to a publicly published guide, and a public topic should not be linked to a private one. InteropHub cannot prevent either pairing.";
    }

    private static void option(PrintWriter out, String value, String label, String selected) {
        out.println("            <option value=\"" + escape(value) + "\""
                + (value.equals(selected) ? " selected" : "") + ">" + escape(label) + "</option>");
    }

    private void versions(PrintWriter out, HttpServletRequest request, String action, String documentKey, Viewer viewer) {
        out.println("      <h2 class=\"aira-section-title\">Hosted versions</h2>");
        out.println("      <form class=\"aira-stack\" method=\"post\" action=\"" + action
                + "\" enctype=\"multipart/form-data\">");
        out.println("        <input type=\"hidden\" name=\"csrfToken\" value=\""
                + escape(CsrfTokenSupport.getOrCreateToken(request)) + "\">");
        out.println("        <input type=\"hidden\" name=\"documentKey\" value=\"" + escape(documentKey) + "\">");
        out.println("        <div class=\"aira-field\">");
        out.println("          <label class=\"aira-label\" for=\"versionLabel\">Label</label>");
        out.println("          <input class=\"aira-input\" id=\"versionLabel\" name=\"versionLabel\" type=\"text\" value=\"latest\" maxlength=\"64\">");
        out.println("          <p class=\"aira-field-help\">latest, main, or a version such as v1.2.3. Uploading the same label again replaces what that label serves and keeps the earlier upload in the history.</p>");
        out.println("        </div>");
        out.println("        <div class=\"aira-field\">");
        out.println("          <label class=\"aira-label\" for=\"html\">HTML file</label>");
        out.println("          <input class=\"aira-input\" id=\"html\" name=\"html\" type=\"file\" accept=\"text/html,.html\" required>");
        out.println("        </div>");
        out.println("        <p><button class=\"aira-button aira-button--primary\" type=\"submit\" name=\"intent\" value=\"upload\">Upload</button></p>");
        out.println("      </form>");
        try {
            JSONArray rows = hosting.list(viewer.user, documentKey).getJSONArray("versions");
            if (rows.isEmpty()) {
                out.println("      <p class=\"aira-meta\">No versions uploaded yet.</p>");
                return;
            }
            out.println("      <ul class=\"aira-stack aira-stack--compact\">");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                int earlier = row.getJSONArray("earlier").length();
                out.println("        <li><a class=\"aira-inline-link\" href=\"" + escape(row.getString("viewUrl")) + "\">"
                        + escape(row.getString("label")) + "</a> · " + escape(row.optString("uploadedBy", ""))
                        + " · " + escape(row.optString("uploadedAt", ""))
                        + (earlier == 0 ? "" : " · " + earlier + (earlier == 1 ? " earlier upload" : " earlier uploads"))
                        + "</li>");
                JSONArray history = row.getJSONArray("earlier");
                for (int h = 0; h < history.length(); h++) {
                    JSONObject old = history.getJSONObject(h);
                    out.println("        <li class=\"aira-meta\">Earlier " + escape(row.getString("label")) + " · "
                            + escape(old.optString("uploadedBy", "")) + " · " + escape(old.optString("uploadedAt", ""))
                            + "</li>");
                }
            }
            out.println("      </ul>");
        } catch (GuideApiException ex) {
            out.println("      <div class=\"aira-alert aira-alert--error\"><p>" + escape(ex.getMessage()) + "</p></div>");
        }
    }

    private void upload(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Viewer viewer = viewer(request, response);
        if (viewer == null) {
            return;
        }
        String documentKey = trim(request.getParameter("documentKey"));
        String label = trim(request.getParameter("versionLabel"));
        try {
            Part part = request.getPart("html");
            if (part == null || part.getSize() <= 0) {
                show(request, response, null, null, "Choose an HTML file.");
                return;
            }
            if (part.getSize() > GuideHtmlRules.MAX_BYTES) {
                show(request, response, null, null, "The guide must be a non-empty HTML file of at most 20 MB.");
                return;
            }
            byte[] html;
            try (InputStream input = part.getInputStream()) {
                html = input.readNBytes((int) GuideHtmlRules.MAX_BYTES + 1);
            }
            String type = part.getContentType();
            String media = type == null ? "" : type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (media.isEmpty() || "application/octet-stream".equals(media)) {
                type = "text/html";
            }
            GuideHostingService.UploadResult uploaded = hosting.upload(viewer.user, null, "WEB", documentKey, label,
                    html, type);
            String uploadedFlag = uploaded.status() == 201 ? "new" : "replaced";
            response.sendRedirect(request.getContextPath() + "/es/topic-guide/" + viewer.topic.id + "?document="
                    + documentKey + "&uploaded=" + uploadedFlag);
        } catch (GuideApiException ex) {
            show(request, response, null, null, ex.getMessage());
        } catch (ServletException | IllegalStateException ex) {
            show(request, response, null, null, "The guide could not be uploaded.");
        }
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

    private static PostedForm readPosted(HttpServletRequest request) {
        PostedForm posted = new PostedForm();
        posted.creating = "create".equals(request.getParameter("mode"));
        posted.documentKey = emptyIfNull(trim(request.getParameter("documentKey")));
        posted.title = emptyIfNull(trim(request.getParameter("title")));
        posted.repositoryUrl = emptyIfNull(trim(request.getParameter("repositoryUrl")));
        posted.hosting = GuideHtmlRules.hosting(request.getParameter("hosting"));
        posted.active = request.getParameter("active") != null;
        for (int i = 0; i < URL_ROWS; i++) {
            posted.labels[i] = emptyIfNull(trim(request.getParameter("label" + i)));
            posted.urls[i] = emptyIfNull(trim(request.getParameter("url" + i)));
        }
        return posted;
    }

    private static List<UrlInput> postedUrls(PostedForm posted) {
        List<UrlInput> urls = new ArrayList<>();
        for (int i = 0; i < URL_ROWS; i++) {
            String url = trim(posted.urls[i]);
            String label = trim(posted.labels[i]);
            if (url == null && label == null) {
                continue;
            }
            urls.add(new UrlInput(url, label));
        }
        return urls;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
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

    private static final class PostedForm {
        boolean creating;
        String documentKey;
        String title;
        String repositoryUrl;
        String hosting = GuideHtmlRules.EXTERNAL;
        boolean active;
        final String[] labels = new String[URL_ROWS];
        final String[] urls = new String[URL_ROWS];
    }
}
