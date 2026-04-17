package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.model.EsCampaign;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class EsCampaignRegistrationThankYouServlet extends HttpServlet {

    private final EsCampaignDao campaignDao;

    public EsCampaignRegistrationThankYouServlet() {
        this.campaignDao = new EsCampaignDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String campaignCode = parseCampaignCode(request);
        String contextPath = request.getContextPath();
        Optional<EsCampaign> campaign = campaignCode == null
                ? Optional.empty()
                : campaignDao.findByCampaignCode(campaignCode);
        String campaignName = campaign.map(EsCampaign::getCampaignName).orElse(null);
        String browseHref = campaignCode == null
                ? contextPath + "/topics"
                : contextPath + "/topics/" + URLEncoder.encode(campaignCode, StandardCharsets.UTF_8);

        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Registration Submitted - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Thank You</h1>");
            out.println("    <p>Your registration was successfully recorded.</p>");
            if (campaignName != null && !campaignName.isBlank()) {
                out.println("    <p><strong>Event:</strong> " + escapeHtml(campaignName) + "</p>");
            }
            out.println(
                    "    <p>While you are here, if you would like to look at Emerging Standard topics and select ones you are interested in getting updates on, use the link below.</p>");
            out.println("    <p><a class=\"button-link\" href=\"" + browseHref
                    + "\">Browse Emerging Standard Topics</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private String parseCampaignCode(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            return null;
        }
        if (!pathInfo.startsWith("/")) {
            return null;
        }
        String trimmed = pathInfo.substring(1).trim();
        if (trimmed.isEmpty() || trimmed.contains("/")) {
            return null;
        }
        return trimmed;
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
