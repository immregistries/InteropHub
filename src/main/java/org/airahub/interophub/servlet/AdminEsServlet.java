package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminEsServlet extends HttpServlet {

    private final AuthFlowService authFlowService;

    public AdminEsServlet() {
        this.authFlowService = new AuthFlowService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Emerging Standards Admin - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Emerging Standards</h2>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/campaigns\">Campaigns</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin/es/topics\">ES Topics</a></p>");
                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es/neighborhoods\">Neighborhoods</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/registrations\">Campaign Registration Display</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath
                        + "/admin/es/review-results\">Review Results</a></p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin\">Back to Admin Home</a></p>");
                panelOut.println("      </section>");
            });
        }
    }

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            renderForbidden(response, request.getContextPath());
            return Optional.empty();
        }
        return authenticatedUser;
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Access Denied - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Access Denied</h2>");
                panelOut.println("        <p>You must be an InteropHub admin to access this page.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/admin\">Return to Admin Home</a></p>");
                panelOut.println("      </section>");
            });
        }
    }
}
