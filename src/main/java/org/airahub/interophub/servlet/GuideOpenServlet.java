package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    GuideOpenServlet(GuideRegistrationService registration) {
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
        if (label != null) {
            for (UrlBinding binding : document.urls) {
                if (label.equals(binding.label)) {
                    chosen = binding;
                    break;
                }
            }
        }
        String comment = trim(request.getParameter("comment"));
        String anchor = trim(request.getParameter("anchor"));
        String target = chosen.url + "?interophub=signin";
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

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
