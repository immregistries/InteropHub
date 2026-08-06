package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.User;

public class AdminEsServlet extends HttpServlet {

    private final EsTopicSpaceDao topicSpaceDao;
    private final EsCampaignDao campaignDao;

    public AdminEsServlet() {
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.campaignDao = new EsCampaignDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        int activeTopicSpaceCount = topicSpaceDao.findAllActiveOrdered().size();
        int activeCampaignCount = campaignDao.findAllActive().size();

        AdminShellRenderer.render(request, response, "Topic Spaces - InteropHub", AdminSection.TOPIC_SPACES,
                "/admin/es", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Topic Spaces</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Topic Spaces organize Emerging Standards content into structured workspaces, each with its own Neighborhoods, Stages, Advancement Paths, and Topic Boards. Use the menu on the right to manage structure and taxonomy, topics and campaigns, meetings, and feedback and sync.</p>");
                    out.println("            <div class=\"aira-cluster\">");
                    out.println("              <span class=\"aira-meta-chip\">");
                    out.println("                <span class=\"aira-meta-chip__label\">Active Topic Spaces</span>");
                    out.println("                <span class=\"aira-meta-chip__value\">" + activeTopicSpaceCount
                            + "</span>");
                    out.println("              </span>");
                    out.println("              <span class=\"aira-meta-chip\">");
                    out.println("                <span class=\"aira-meta-chip__label\">Active Campaigns</span>");
                    out.println("                <span class=\"aira-meta-chip__value\">" + activeCampaignCount
                            + "</span>");
                    out.println("              </span>");
                    out.println("            </div>");
                    out.println("          </section>");
                });
    }
}
