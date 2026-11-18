package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.GuideHostingService;
import org.immregistries.aira.web.AiraPage;

/**
 * Serves a hosted guide from InteropHub. A missing slash is redirected onto
 * the canonical URL. Anything after the label is not a guide. Signed-out
 * readers go to sign-in and come back; every other refusal uses the private
 * topic not-found page so the response does not reveal whether the document
 * exists.
 */
public class GuideViewServlet extends HttpServlet {
    private final GuideHostingService hosting;
    private final AuthFlowService authFlowService;

    public GuideViewServlet() {
        this(new GuideHostingService(), new AuthFlowService());
    }

    public GuideViewServlet(GuideHostingService hosting, AuthFlowService authFlowService) {
        this.hosting = hosting;
        this.authFlowService = authFlowService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        if (path == null || path.isBlank() || "/".equals(path)) {
            refuse(request, response);
            return;
        }
        String raw = path.startsWith("/") ? path.substring(1) : path;
        boolean slash = raw.endsWith("/");
        if (slash) {
            raw = raw.substring(0, raw.length() - 1);
        }
        if (raw.isEmpty()) {
            refuse(request, response);
            return;
        }
        String[] segments = raw.split("/", -1);
        if (segments.length != 2 || segments[0].isEmpty() || segments[1].isEmpty()) {
            refuse(request, response);
            return;
        }
        if (!slash) {
            String query = request.getQueryString();
            String target = request.getContextPath() + "/guide/view/" + segments[0] + "/" + segments[1] + "/";
            if (query != null && !query.isBlank()) {
                target = target + "?" + query;
            }
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", target);
            return;
        }
        Optional<User> authenticated = authFlowService.findAuthenticatedUser(request);
        if (authenticated.isEmpty()) {
            String guidePath = "/guide/view/" + segments[0] + "/" + segments[1] + "/";
            String query = request.getQueryString();
            if (query != null && !query.isBlank()) {
                guidePath = guidePath + "?" + query;
            }
            authFlowService.rememberGuideViewReturn(request, guidePath);
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }
        GuideHostingService.Viewed viewed;
        try {
            viewed = hosting.view(authenticated.get(), segments[0], segments[1]);
        } catch (RuntimeException ex) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        if (viewed == null) {
            refuse(request, response);
            return;
        }
        if (viewed.retired()) {
            retired(request, response, viewed);
            return;
        }
        byte[] html = viewed.html();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html; charset=utf-8");
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Robots-Tag", "noindex");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setContentLength(html.length);
        response.getOutputStream().write(html);
    }

    /** Plan 3.1: a reader opening a Retired label. Links to the Current label when there is one. */
    private static void retired(HttpServletRequest request, HttpServletResponse response,
            GuideHostingService.Viewed viewed) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Robots-Tag", "noindex");
        AiraPage page = InteropAiraPageFactory.base(request, "Version Retired - InteropHub")
                .applicationSubtitle("Topic Reference")
                .pageHeading("Version Retired")
                .mainClass("aira-main")
                .build();
        String contextPath = request.getContextPath();
        try (var out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container--narrow aira-stack aira-stack--compact\">");
            out.println("      <section class=\"aira-alert aira-alert--info\" role=\"status\" aria-live=\"polite\">");
            out.println("        <p class=\"aira-alert__title\">This version has been retired</p>");
            out.println("        <p>The authors no longer offer version " + escape(viewed.label()) + " of this guide.</p>");
            if (viewed.currentLabel() != null) {
                out.println("        <p><a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                        + "/guide/view/" + escape(encode(viewed.documentKey())) + "/"
                        + escape(encode(viewed.currentLabel())) + "/\">Read the current version ("
                        + escape(viewed.currentLabel()) + ")</a></p>");
            }
            out.println("      </section>");
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** The same page a private topic shows. It does not name the guide. */
    private static void refuse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        AiraPage page = InteropAiraPageFactory.base(request, "Topic Not Found - InteropHub")
                .applicationSubtitle("Topic display")
                .pageHeading("Topic Not Found")
                .mainClass("aira-main")
                .build();
        String contextPath = request.getContextPath();
        try (var out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container--narrow aira-stack aira-stack--compact\">");
            out.println("      <section class=\"aira-alert aira-alert--warning\" role=\"status\" aria-live=\"polite\">");
            out.println("        <p class=\"aira-alert__title\">Topic not found</p>");
            out.println("        <p>The requested topic could not be found.</p>");
            out.println("        <p><a class=\"aira-inline-link\" href=\"" + contextPath
                    + "/es/topics\">Back to Topics</a></p>");
            out.println("      </section>");
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }
}
