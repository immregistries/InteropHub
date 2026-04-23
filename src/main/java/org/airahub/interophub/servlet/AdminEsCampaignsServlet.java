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
            AdminShellRenderer.render(out, "ES Campaigns - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>ES Campaigns</h2>");

                if (campaigns.isEmpty()) {
                    panelOut.println("        <p>No campaigns found.</p>");
                } else {
                    panelOut.println("        <table class=\"admin-table\">");
                    panelOut.println("          <thead>");
                    panelOut.println("            <tr>");
                    panelOut.println("              <th>Campaign Code</th>");
                    panelOut.println("              <th>Campaign Name</th>");
                    panelOut.println("              <th>Status</th>");
                    panelOut.println("              <th>Topics</th>");
                    panelOut.println("              <th>Registrations</th>");
                    panelOut.println("              <th>Review</th>");
                    panelOut.println("              <th>Results</th>");
                    panelOut.println("            </tr>");
                    panelOut.println("          </thead>");
                    panelOut.println("          <tbody>");
                    for (EsCampaign c : campaigns) {
                        long topicCount = campaignTopicDao.countByCampaignId(c.getEsCampaignId());
                        long regCount = registrationDao.countByCampaignId(c.getEsCampaignId());
                        String detailUrl = contextPath + "/admin/es/campaigns/detail?campaignCode="
                                + escapeHtml(c.getCampaignCode());
                        String reviewUrl = contextPath + "/es/review/" + escapeHtml(c.getCampaignCode());
                        String resultsUrl = contextPath + "/admin/es/review-results?campaignCode="
                                + escapeHtml(c.getCampaignCode());
                        panelOut.println("            <tr>");
                        panelOut.println("              <td>" + escapeHtml(c.getCampaignCode()) + "</td>");
                        panelOut.println("              <td><a href=\"" + detailUrl + "\">"
                                + escapeHtml(c.getCampaignName()) + "</a></td>");
                        panelOut.println("              <td>" + escapeHtml(String.valueOf(c.getStatus())) + "</td>");
                        panelOut.println("              <td>" + topicCount + "</td>");
                        panelOut.println("              <td>" + regCount + "</td>");
                        panelOut.println("              <td><a href=\"" + reviewUrl + "\">Open Review</a></td>");
                        panelOut.println("              <td><a href=\"" + resultsUrl + "\">View Results</a></td>");
                        panelOut.println("            </tr>");
                    }
                    panelOut.println("          </tbody>");
                    panelOut.println("        </table>");
                }

                panelOut.println("        <p style=\"margin-top:1.5rem\">");
                panelOut.println("          <a href=\"" + contextPath + "/admin/es-topic-import\">Import Topics</a>");
                panelOut.println("        </p>");
                panelOut.println(
                        "        <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
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
