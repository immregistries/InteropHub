package org.airahub.interophub.servlet;

import java.io.IOException;
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

/**
 * Admin page listing all ES campaigns with topic and registration counts.
 * Route: /admin/es/campaigns
 */
public class AdminEsCampaignsServlet extends HttpServlet {

    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsCampaignRegistrationDao registrationDao;

    public AdminEsCampaignsServlet() {
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.registrationDao = new EsCampaignRegistrationDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        List<EsCampaign> campaigns = campaignDao.findAllOrdered();

        AdminShellRenderer.render(request, response, "ES Campaigns - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es/campaigns", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">ES Campaigns</h2>");

                    if (campaigns.isEmpty()) {
                        out.println("            <p class=\"aira-meta\">No campaigns found.</p>");
                    } else {
                        out.println("            <div class=\"aira-table-wrap\">");
                        out.println("            <table class=\"aira-table\">");
                        out.println("              <thead>");
                        out.println("                <tr>");
                        out.println("                  <th>Campaign Code</th>");
                        out.println("                  <th>Campaign Name</th>");
                        out.println("                  <th>Status</th>");
                        out.println("                  <th>Topics</th>");
                        out.println("                  <th>Registrations</th>");
                        out.println("                  <th>Review</th>");
                        out.println("                  <th>Results</th>");
                        out.println("                </tr>");
                        out.println("              </thead>");
                        out.println("              <tbody>");
                        for (EsCampaign c : campaigns) {
                            long topicCount = campaignTopicDao.countByCampaignId(c.getEsCampaignId());
                            long regCount = registrationDao.countByCampaignId(c.getEsCampaignId());
                            String detailUrl = contextPath + "/admin/es/campaigns/detail?campaignCode="
                                    + escapeHtml(c.getCampaignCode());
                            String reviewUrl = contextPath + "/es/review/" + escapeHtml(c.getCampaignCode());
                            String resultsUrl = contextPath + "/admin/es/review-results?campaignCode="
                                    + escapeHtml(c.getCampaignCode());
                            out.println("                <tr>");
                            out.println("                  <td>" + escapeHtml(c.getCampaignCode()) + "</td>");
                            out.println("                  <td><a class=\"aira-inline-link\" href=\"" + detailUrl + "\">"
                                    + escapeHtml(c.getCampaignName()) + "</a></td>");
                            out.println("                  <td>" + escapeHtml(String.valueOf(c.getStatus())) + "</td>");
                            out.println("                  <td>" + topicCount + "</td>");
                            out.println("                  <td>" + regCount + "</td>");
                            out.println("                  <td><a class=\"aira-inline-link\" href=\"" + reviewUrl
                                    + "\">Open Review</a></td>");
                            out.println("                  <td><a class=\"aira-inline-link\" href=\"" + resultsUrl
                                    + "\">View Results</a></td>");
                            out.println("                </tr>");
                        }
                        out.println("              </tbody>");
                        out.println("            </table>");
                        out.println("            </div>");
                    }

                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                            + "/admin/es/campaigns/create\">Create New Campaign</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es-topic-import\">Import Topics</a>");
                    out.println("            </div>");
                    out.println(
                            "            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                    + "/admin/es\">Back to Emerging Standards</a></p>");
                    out.println("          </section>");
                });
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
