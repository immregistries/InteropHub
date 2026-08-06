package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.immregistries.aira.web.AiraPage;

/**
 * Shared "must be signed in and an admin" check for admin servlets, plus the
 * access-denied page shown when someone signed in but not an admin reaches
 * one. Anonymous visitors are redirected to sign in instead.
 */
final class AdminAccessGuard {

    private static final AuthFlowService AUTH_FLOW_SERVICE = new AuthFlowService();

    private AdminAccessGuard() {
    }

    static Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = AUTH_FLOW_SERVICE.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!AUTH_FLOW_SERVICE.isAdminUser(authenticatedUser.get())) {
            renderForbidden(request, response);
            return Optional.empty();
        }
        return authenticatedUser;
    }

    private static void renderForbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        String contextPath = request.getContextPath();
        AiraPage page = InteropAiraPageFactory.base(request, "Access Denied - InteropHub").build();
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <div class=\"aira-page-header\">");
            out.println("        <div>");
            out.println("          <h1 class=\"aira-page-title\">Access Denied</h1>");
            out.println("        </div>");
            out.println("      </div>");
            out.println(
                    "      <div class=\"aira-alert aira-alert--danger\"><p>You must be an InteropHub admin to access this page.</p></div>");
            out.println("      <p><a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                    + "/welcome\">Return to Welcome</a></p>");
            out.println("    </div>");
            page.writeEnd(out);
        }
    }
}
