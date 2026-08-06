package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.immregistries.aira.web.AiraPage;

/**
 * Shared shell for every admin page: the standard AIRA header, the dark
 * "Platform | People | Content | Topic Spaces" context-nav row, and the
 * right-rail admin menu (via {@link AdminSectionNavRenderer}) for whichever
 * {@link AdminSection} is active. Callers supply only their own main-content
 * markup - the menu is never duplicated per page.
 */
final class AdminShellRenderer {

    private AdminShellRenderer() {
    }

    static void render(HttpServletRequest request, HttpServletResponse response, String documentTitle,
            AdminSection section, String activeHref, ContentRenderer contentRenderer) throws IOException {
        String contextPath = request.getContextPath();
        AiraPage page = InteropAiraPageFactory.base(request, documentTitle)
                .applicationSubtitle("Admin")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.adminContext(section))
                .build();

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container--wide aira-stack\">");
            out.println("      <div class=\"aira-right-rail-layout\">");
            out.println("        <div class=\"aira-stack\">");
            contentRenderer.render(out);
            out.println("        </div>");
            AdminSectionNavRenderer.render(out, contextPath, section, contextPath + activeHref);
            out.println("      </div>");
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    @FunctionalInterface
    interface ContentRenderer {
        void render(PrintWriter out) throws IOException;
    }
}
