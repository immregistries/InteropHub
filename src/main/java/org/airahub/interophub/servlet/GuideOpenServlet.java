package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.service.GuideApiException;
import org.airahub.interophub.service.GuideHtmlRules;
import org.airahub.interophub.service.GuideRegistrationService;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.UrlBinding;
import org.immregistries.aira.web.AiraPage;

/** Contract 3.6. No sign-in is required to follow the link. */
public class GuideOpenServlet extends HttpServlet {
    private static final Pattern ANCHOR = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]*$");
    private final GuideRegistrationService registration;

    public GuideOpenServlet() {
        this(new GuideRegistrationService());
    }

    public GuideOpenServlet(GuideRegistrationService registration) {
        this.registration = registration;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String documentKey = request.getParameter("doc");
        DocumentRow document = documentKey == null ? null : registration.document(documentKey);
        if (document == null || !document.active || document.urls.isEmpty()) {
            notFound(request, response);
            return;
        }
        String label = trim(request.getParameter("label"));
        UrlBinding chosen = document.urls.get(0);
        String chosenUrl = chosen.url;
        if (label != null) {
            boolean matched = false;
            for (UrlBinding binding : document.urls) {
                if (label.equals(binding.label)) {
                    chosenUrl = binding.url;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                String hosted = hostedLabelUrl(document, label);
                if (hosted != null) {
                    chosenUrl = hosted;
                }
            }
        }
        String comment = trim(request.getParameter("comment"));
        String anchor = trim(request.getParameter("anchor"));
        String target = chosenUrl + "?interophub=signin";
        if (comment != null) {
            if (!comment.matches("[1-9][0-9]*")) {
                notFound(request, response);
                return;
            }
            target += "#comment-" + comment;
        } else if (anchor != null) {
            if (anchor.length() > 100 || !ANCHOR.matcher(anchor).matches()) {
                notFound(request, response);
                return;
            }
            target += "#" + anchor;
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", target);
    }

    private static void notFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        AiraPage page = InteropAiraPageFactory.base(request, "Not Found - InteropHub").build();
        String contextPath = request.getContextPath();
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <h1 class=\"aira-page-title\">Page not found</h1>");
            out.println("      <div class=\"aira-alert aira-alert--error\"><p>That page could not be found.</p></div>");
            out.println("      <p><a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                    + "/home\">Back to Home</a></p>");
            out.println("    </div>");
            page.writeEnd(out);
        }
    }

    /**
     * A hosted guide registers only its latest and main view URLs, but every
     * uploaded label has a view URL (for example a Current {@code v0.2.0}). Built
     * from the registered view URL so it stays on the same hub base. The viewer
     * itself decides whether the reader may open that label.
     */
    static String hostedLabelUrl(DocumentRow document, String label) {
        if (!GuideHtmlRules.HUB.equals(document.hosting)) {
            return null;
        }
        try {
            GuideHtmlRules.label(label);
        } catch (GuideApiException ex) {
            return null;
        }
        String marker = "/guide/view/" + document.documentKey + "/";
        for (UrlBinding binding : document.urls) {
            int at = binding.url == null ? -1 : binding.url.indexOf(marker);
            if (at >= 0) {
                return binding.url.substring(0, at + marker.length()) + label + "/";
            }
        }
        return null;
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
