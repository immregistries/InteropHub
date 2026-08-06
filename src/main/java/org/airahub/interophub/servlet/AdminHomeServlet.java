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

public class AdminHomeServlet extends HttpServlet {
    private static final int QUICK_CAMPAIGN_LIMIT = 6;

    private final EsCampaignDao esCampaignDao;

    public AdminHomeServlet() {
        this.esCampaignDao = new EsCampaignDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        List<EsCampaign> quickCampaigns = loadQuickCampaigns();

        AdminShellRenderer.render(request, response, "Admin Home - InteropHub", AdminSection.PLATFORM, "/admin",
                out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Quick Campaign Access</h2>");
                    if (quickCampaigns.isEmpty()) {
                        out.println(
                                "            <p class=\"aira-meta\">No active or draft campaigns are currently available.</p>");
                    } else {
                        out.println("            <div class=\"aira-stack aira-stack--compact\">");
                        for (EsCampaign campaign : quickCampaigns) {
                            String campaignCode = orEmpty(campaign.getCampaignCode());
                            String campaignName = orEmpty(campaign.getCampaignName());
                            String status = campaign.getStatus() == null ? "UNKNOWN" : campaign.getStatus().name();
                            out.println("              <div class=\"aira-cluster aira-cluster--between\">");
                            out.println("                <a class=\"aira-inline-link\" href=\"" + contextPath
                                    + "/admin/es/campaigns/detail?campaignCode="
                                    + escapeUrlComponent(campaignCode) + "\">"
                                    + escapeHtml(campaignCode + " - " + campaignName) + "</a>");
                            out.println("                <span class=\"aira-badge aira-badge--subtle\">"
                                    + escapeHtml(status) + "</span>");
                            out.println("              </div>");
                        }
                        out.println("            </div>");
                        out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es/campaigns\">View all campaigns</a></p>");
                    }
                    out.println("          </section>");
                });
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

}