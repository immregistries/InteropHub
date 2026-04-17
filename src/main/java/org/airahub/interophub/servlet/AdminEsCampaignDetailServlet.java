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
 * Admin detail page for a single ES campaign: shows campaign info and a table
 * of all tables with links to vote and view results.
 * Route: /admin/es/campaigns/detail?campaignCode={code}
 */
public class AdminEsCampaignDetailServlet extends HttpServlet {

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsCampaignRegistrationDao registrationDao;

    public AdminEsCampaignDetailServlet() {
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
        String campaignCode = trimToNull(request.getParameter("campaignCode"));
        boolean saved = "1".equals(request.getParameter("saved"));

        if (campaignCode == null) {
            response.sendRedirect(contextPath + "/admin/es/campaigns");
            return;
        }

        Optional<EsCampaign> campaignOpt = campaignDao.findByCampaignCode(campaignCode);
        if (campaignOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.println("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\" />");
                out.println("<title>Campaign Not Found - InteropHub</title>");
                out.println("<link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" /></head>");
                out.println("<body><main class=\"container\"><h1>Campaign Not Found</h1>");
                out.println("<p>No campaign found with code: <strong>" + escapeHtml(campaignCode) + "</strong></p>");
                out.println("<p><a href=\"" + contextPath + "/admin/es/campaigns\">Back to Campaigns</a></p>");
                out.println("</main></body></html>");
            }
            return;
        }

        EsCampaign campaign = campaignOpt.get();
        Long campaignId = campaign.getEsCampaignId();
        long topicCount = campaignTopicDao.countByCampaignId(campaignId);
        long regCount = registrationDao.countByCampaignId(campaignId);
        List<Integer> tableNos = campaignTopicDao.findDistinctTableNosByCampaignId(campaignId);

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>" + escapeHtml(campaign.getCampaignName()) + " - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>" + escapeHtml(campaign.getCampaignName()) + "</h1>");
            if (saved) {
                out.println("    <p><strong>Campaign changes saved.</strong></p>");
            }

            // Campaign summary
            out.println("    <table class=\"admin-table\" style=\"margin-bottom:1.5rem\">");
            out.println("      <tbody>");
            out.println(
                    "        <tr><th>Campaign Code</th><td>" + escapeHtml(campaign.getCampaignCode()) + "</td></tr>");
            out.println("        <tr><th>Status</th><td>" + escapeHtml(String.valueOf(campaign.getStatus()))
                    + "</td></tr>");
            out.println("        <tr><th>Current Round</th><td>" + campaign.getCurrentRoundNo() + "</td></tr>");
            out.println("        <tr><th>Total Topics</th><td>" + topicCount + "</td></tr>");
            out.println("        <tr><th>Registrations</th><td>" + regCount + "</td></tr>");
            out.println("      </tbody>");
            out.println("    </table>");

            // Registration display link
            out.println("    <p><a href=\"" + contextPath + "/admin/es/registrations?campaignCode="
                    + escapeHtml(campaign.getCampaignCode()) + "\">Registration Display</a></p>");
            out.println("    <p><a href=\"" + contextPath + "/admin/es/campaigns/edit?campaignCode="
                    + escapeHtml(campaign.getCampaignCode()) + "\">Edit Campaign</a></p>");

            // Tables
            if (tableNos.isEmpty()) {
                out.println("    <p>No tables assigned yet. Import topics with a Tables per Set value &gt; 0.</p>");
            } else {
                out.println("    <h2>Tables</h2>");
                out.println("    <table class=\"admin-table\">");
                out.println("      <thead>");
                out.println("        <tr>");
                out.println("          <th>Table</th>");
                out.println("          <th>Topics</th>");
                out.println("          <th>Vote</th>");
                out.println("          <th>Results</th>");
                out.println("        </tr>");
                out.println("      </thead>");
                out.println("      <tbody>");
                for (Integer tableNo : tableNos) {
                    long tableTopicCount = campaignTopicDao.countByCampaignIdAndTableNo(campaignId, tableNo);
                    String voteUrl = contextPath + "/table/" + escapeHtml(campaign.getCampaignCode())
                            + "/" + tableNo + "?view=vote";
                    String resultsUrl = contextPath + "/table/" + escapeHtml(campaign.getCampaignCode())
                            + "/" + tableNo + "?view=results";
                    out.println("        <tr>");
                    out.println("          <td>Table " + tableNo + "</td>");
                    out.println("          <td>" + tableTopicCount + "</td>");
                    out.println("          <td><a href=\"" + voteUrl + "\">Vote</a></td>");
                    out.println("          <td><a href=\"" + resultsUrl + "\">Results</a></td>");
                    out.println("        </tr>");
                }
                out.println("      </tbody>");
                out.println("    </table>");
            }

            out.println("    <p style=\"margin-top:1.5rem\"><a href=\"" + contextPath
                    + "/admin/es/campaigns\">Back to Campaigns</a></p>");
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
