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
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }
        byte[] html;
        try {
            html = hosting.open(authenticated.get(), segments[0], segments[1]);
        } catch (RuntimeException ex) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        if (html == null) {
            refuse(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html; charset=utf-8");
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Robots-Tag", "noindex");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setContentLength(html.length);
        response.getOutputStream().write(html);
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
