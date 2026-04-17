package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignRegistrationDao;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

/**
 * Admin page listing all ES campaigns with topic and registration counts.
 * Route: /admin/es/campaigns
 */
public class AdminEsCampaignsServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsCampaignRegistrationDao registrationDao;

    public AdminEsCampaignsServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.registrationDao = new EsCampaignRegistrationDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        List<EsCampaign> campaigns = campaignDao.findAllOrdered();

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>ES Campaigns - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>ES Campaigns</h1>");

            if (campaigns.isEmpty()) {
                out.println("    <p>No campaigns found.</p>");
            } else {
                out.println("    <table class=\"admin-table\">");
                out.println("      <thead>");
                out.println("        <tr>");
                out.println("          <th>Campaign Code</th>");
                out.println("          <th>Campaign Name</th>");
                out.println("          <th>Status</th>");
                out.println("          <th>Topics</th>");
                out.println("          <th>Registrations</th>");
                out.println("        </tr>");
                out.println("      </thead>");
                out.println("      <tbody>");
                for (EsCampaign c : campaigns) {
                    long topicCount = campaignTopicDao.countByCampaignId(c.getEsCampaignId());
                    long regCount = registrationDao.countByCampaignId(c.getEsCampaignId());
                    String detailUrl = contextPath + "/admin/es/campaigns/detail?campaignCode="
                            + escapeHtml(c.getCampaignCode());
                    out.println("        <tr>");
                    out.println("          <td>" + escapeHtml(c.getCampaignCode()) + "</td>");
                    out.println("          <td><a href=\"" + detailUrl + "\">"
                            + escapeHtml(c.getCampaignName()) + "</a></td>");
                    out.println("          <td>" + escapeHtml(String.valueOf(c.getStatus())) + "</td>");
                    out.println("          <td>" + topicCount + "</td>");
                    out.println("          <td>" + regCount + "</td>");
                    out.println("        </tr>");
                }
                out.println("      </tbody>");
                out.println("    </table>");
            }

            out.println("    <p style=\"margin-top:1.5rem\">");
            out.println("      <a href=\"" + contextPath + "/admin/es-topic-import\">Import Topics</a>");
            out.println("    </p>");
            out.println("    <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.println("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\" />");
                out.println("<title>Access Denied - InteropHub</title>");
                out.println(
                        "<link rel=\"stylesheet\" href=\"" + request.getContextPath() + "/css/main.css\" /></head>");
                out.println("<body><main class=\"container\"><h1>Access Denied</h1>");
                out.println("<p>You must be an InteropHub admin to access this page.</p>");
                out.println("<p><a href=\"" + request.getContextPath() + "/welcome\">Return to Welcome</a></p>");
                out.println("</main></body></html>");
            }
            return Optional.empty();
        }
        return authenticatedUser;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
