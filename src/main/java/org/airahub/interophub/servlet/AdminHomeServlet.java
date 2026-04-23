package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminHomeServlet extends HttpServlet {
    private static final int QUICK_CAMPAIGN_LIMIT = 6;

    private final AuthFlowService authFlowService;
    private final EsCampaignDao esCampaignDao;

    public AdminHomeServlet() {
        this.authFlowService = new AuthFlowService();
        this.esCampaignDao = new EsCampaignDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        List<EsCampaign> quickCampaigns = loadQuickCampaigns();

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Admin Home - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Quick Campaign Access</h2>");
                if (quickCampaigns.isEmpty()) {
                    panelOut.println("        <p>No active or draft campaigns are currently available.</p>");
                } else {
                    panelOut.println("        <ul class=\"admin-quick-links\">");
                    for (EsCampaign campaign : quickCampaigns) {
                        String campaignCode = orEmpty(campaign.getCampaignCode());
                        String campaignName = orEmpty(campaign.getCampaignName());
                        String status = campaign.getStatus() == null ? "UNKNOWN" : campaign.getStatus().name();
                        panelOut.println("          <li><a href=\"" + contextPath
                                + "/admin/es/campaigns/detail?campaignCode="
                                + escapeUrlComponent(campaignCode)
                                + "\">"
                                + escapeHtml(campaignCode + " - " + campaignName)
                                + "</a><span class=\"admin-pill\">"
                                + escapeHtml(status)
                                + "</span></li>");
                    }
                    panelOut.println("        </ul>");
                    panelOut.println(
                            "        <p><a href=\"" + contextPath + "/admin/es/campaigns\">View all campaigns</a></p>");
                }
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

    private List<EsCampaign> loadQuickCampaigns() {
        List<EsCampaign> quick = new ArrayList<>(esCampaignDao.findAllActive());
        if (quick.size() >= QUICK_CAMPAIGN_LIMIT) {
            return quick.subList(0, QUICK_CAMPAIGN_LIMIT);
        }

        for (EsCampaign campaign : esCampaignDao.findAllOrdered()) {
            if (campaign.getStatus() != EsCampaign.CampaignStatus.DRAFT) {
                continue;
            }
            if (containsCampaign(quick, campaign)) {
                continue;
            }
            quick.add(campaign);
            if (quick.size() >= QUICK_CAMPAIGN_LIMIT) {
                break;
            }
        }
        return quick;
    }

    private boolean containsCampaign(List<EsCampaign> campaigns, EsCampaign candidate) {
        if (candidate == null || candidate.getEsCampaignId() == null) {
            return false;
        }
        for (EsCampaign campaign : campaigns) {
            if (campaign.getEsCampaignId() != null && campaign.getEsCampaignId().equals(candidate.getEsCampaignId())) {
                return true;
            }
        }
        return false;
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

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escapeUrlComponent(String value) {
        if (value == null) {
            return "";
        }
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Access Denied - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Access Denied</h1>");
            out.println("    <p>You must be an InteropHub admin to access this page.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

}