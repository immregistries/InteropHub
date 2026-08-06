package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignMeetingBrowseRow;
import org.airahub.interophub.dao.EsCampaignRegistrationDao;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.PublicUrlService;
import org.immregistries.aira.web.AiraPage;

/**
 * Admin detail page for a single ES campaign: shows campaign info and a table
 * of all tables with links to vote and view results.
 * Route: /admin/es/campaigns/detail?campaignCode={code}
 */
public class AdminEsCampaignDetailServlet extends HttpServlet {

    private static final String ACTIVE_HREF = "/admin/es/campaigns";

    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsCampaignRegistrationDao registrationDao;
    private final PublicUrlService publicUrlService;

    public AdminEsCampaignDetailServlet() {
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.registrationDao = new EsCampaignRegistrationDao();
        this.publicUrlService = new PublicUrlService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
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
            renderCampaignNotFound(request, response, campaignCode);
            return;
        }

        EsCampaign campaign = campaignOpt.get();
        Long campaignId = campaign.getEsCampaignId();
        long topicCount = campaignTopicDao.countByCampaignId(campaignId);
        long regCount = registrationDao.countByCampaignId(campaignId);
        List<Integer> tableNos = campaignTopicDao.findDistinctTableNosByCampaignId(campaignId);
        String encodedCampaignCode = encodePathSegment(campaign.getCampaignCode());
        String detailPath = "/admin/es/campaigns/detail?campaignCode="
                + encodeQueryComponent(campaign.getCampaignCode());
        String registrationPath = "/register/" + encodedCampaignCode;
        String hubPath = "/register/complete/" + encodedCampaignCode;
        String registrationAbsoluteUrl = publicUrlService.resolveExternalUrl(registrationPath);
        String hubAbsoluteUrl = publicUrlService.resolveExternalUrl(hubPath);
        String registrationQrUrl = buildQrPageUrl(contextPath, registrationPath, "Registration page", detailPath);
        String hubQrUrl = buildQrPageUrl(contextPath, hubPath, "Registration complete hub", detailPath);
        List<EsCampaignMeetingBrowseRow> meetingRows = campaignTopicDao
                .findActiveMeetingRowsByCampaignIdOrdered(campaignId);

        AdminShellRenderer.render(request, response, campaign.getCampaignName() + " - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    if (saved) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--success\"><p>Campaign changes saved.</p></div>");
                    }
                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <tbody>");
                    out.println(
                            "                <tr><th>Campaign Code</th><td>" + escapeHtml(campaign.getCampaignCode())
                                    + "</td></tr>");
                    out.println(
                            "                <tr><th>Status</th><td>" + escapeHtml(String.valueOf(campaign.getStatus()))
                                    + "</td></tr>");
                    out.println(
                            "                <tr><th>Current Round</th><td>" + campaign.getCurrentRoundNo() + "</td></tr>");
                    out.println("                <tr><th>Total Topics</th><td>" + topicCount + "</td></tr>");
                    out.println("                <tr><th>Registrations</th><td>" + regCount + "</td></tr>");
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println(
                            "            <p><strong>Registration URL:</strong> <a class=\"aira-inline-link\" href=\""
                                    + escapeHtml(registrationAbsoluteUrl)
                                    + "\">" + escapeHtml(registrationAbsoluteUrl) + "</a> (<a class=\"aira-inline-link\" href=\""
                                    + escapeHtml(registrationQrUrl) + "\">qr code</a>)</p>");
                    out.println(
                            "            <p><strong>Engagement Hub URL:</strong> <a class=\"aira-inline-link\" href=\""
                                    + escapeHtml(hubAbsoluteUrl)
                                    + "\">" + escapeHtml(hubAbsoluteUrl) + "</a> (<a class=\"aira-inline-link\" href=\""
                                    + escapeHtml(hubQrUrl) + "\">qr code</a>)</p>");

                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/registrations?campaignCode="
                            + escapeHtml(campaign.getCampaignCode()) + "\">Registration Display</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/es/review/"
                            + escapeHtml(campaign.getCampaignCode()) + "\">Open Review Instrument</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/es/cdc-review/"
                            + escapeHtml(campaign.getCampaignCode()) + "\">Open CDC Signal Instrument</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/review-results?campaignCode="
                            + escapeHtml(campaign.getCampaignCode()) + "\">Review Results</a>");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/campaigns/edit?campaignCode="
                            + escapeHtml(campaign.getCampaignCode()) + "\">Edit Campaign</a>");
                    out.println("            </div>");

                    if (tableNos.isEmpty()) {
                        out.println(
                                "            <p class=\"aira-meta\">No tables assigned yet. Import topics with a Tables per Set value &gt; 0.</p>");
                    } else {
                        out.println("            <h3 class=\"aira-subsection-title\">Tables</h3>");
                        out.println("            <div class=\"aira-table-wrap\">");
                        out.println("            <table class=\"aira-table\">");
                        out.println("              <thead>");
                        out.println("                <tr>");
                        out.println("                  <th>Table</th>");
                        out.println("                  <th>Topics</th>");
                        out.println("                  <th>Vote</th>");
                        out.println("                  <th>Results</th>");
                        out.println("                </tr>");
                        out.println("              </thead>");
                        out.println("              <tbody>");
                        for (Integer tableNo : tableNos) {
                            long tableTopicCount = campaignTopicDao.countByCampaignIdAndTableNo(campaignId, tableNo);
                            String votePath = "/table/" + encodedCampaignCode + "/" + tableNo + "?view=vote";
                            String resultsPath = "/table/" + encodedCampaignCode + "/" + tableNo + "?view=results";
                            String voteUrl = contextPath + votePath;
                            String resultsUrl = contextPath + resultsPath;
                            String voteQrUrl = buildQrPageUrl(contextPath, votePath, "Table " + tableNo + " Vote",
                                    detailPath);
                            String resultsQrUrl = buildQrPageUrl(contextPath, resultsPath,
                                    "Table " + tableNo + " Results", detailPath);
                            out.println("                <tr>");
                            out.println("                  <td>Table " + tableNo + "</td>");
                            out.println("                  <td>" + tableTopicCount + "</td>");
                            out.println("                  <td><a class=\"aira-inline-link\" href=\"" + escapeHtml(voteUrl)
                                    + "\">Vote</a> (<a class=\"aira-inline-link\" href=\"" + escapeHtml(voteQrUrl)
                                    + "\">qr code</a>)</td>");
                            out.println("                  <td><a class=\"aira-inline-link\" href=\"" + escapeHtml(resultsUrl)
                                    + "\">Results</a> (<a class=\"aira-inline-link\" href=\"" + escapeHtml(resultsQrUrl)
                                    + "\">qr code</a>)</td>");
                            out.println("                </tr>");
                        }
                        out.println("              </tbody>");
                        out.println("            </table>");
                        out.println("            </div>");
                    }

                    out.println("            <h3 class=\"aira-subsection-title\">Meeting Registration Links</h3>");
                    if (meetingRows.isEmpty()) {
                        out.println(
                                "            <p class=\"aira-meta\">No active meetings are configured for this campaign.</p>");
                    } else {
                        out.println("            <div class=\"aira-table-wrap\">");
                        out.println("            <table class=\"aira-table\">");
                        out.println("              <thead>");
                        out.println("                <tr>");
                        out.println("                  <th>Meeting</th>");
                        out.println("                  <th>Register for Meeting</th>");
                        out.println("                </tr>");
                        out.println("              </thead>");
                        out.println("              <tbody>");
                        for (EsCampaignMeetingBrowseRow row : meetingRows) {
                            if (row.getTopicCode() == null || row.getTopicCode().isBlank()) {
                                continue;
                            }
                            String meetingPath = "/registerForMeeting/" + encodedCampaignCode + "/"
                                    + encodePathSegment(row.getTopicCode());
                            String meetingAbsoluteUrl = publicUrlService.resolveExternalUrl(meetingPath);
                            String meetingLabel = row.getMeetingName() == null || row.getMeetingName().isBlank()
                                    ? row.getTopicName()
                                    : row.getMeetingName();
                            String meetingQrUrl = buildQrPageUrl(contextPath, meetingPath,
                                    "Meeting registration: " + meetingLabel, detailPath);

                            out.println("                <tr>");
                            out.println("                  <td>" + escapeHtml(meetingLabel) + "</td>");
                            out.println("                  <td><a class=\"aira-inline-link\" href=\""
                                    + escapeHtml(meetingAbsoluteUrl)
                                    + "\">Register for Meeting</a> (<a class=\"aira-inline-link\" href=\""
                                    + escapeHtml(meetingQrUrl)
                                    + "\">qr code</a>)</td>");
                            out.println("                </tr>");
                        }
                        out.println("              </tbody>");
                        out.println("            </table>");
                        out.println("            </div>");
                    }

                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/campaigns\">Back to Campaigns</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderCampaignNotFound(HttpServletRequest request, HttpServletResponse response, String campaignCode)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        String contextPath = request.getContextPath();
        AiraPage page = InteropAiraPageFactory.base(request, "Campaign Not Found - InteropHub").build();
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <div class=\"aira-page-header\">");
            out.println("        <div>");
            out.println("          <h1 class=\"aira-page-title\">Campaign Not Found</h1>");
            out.println("        </div>");
            out.println("      </div>");
            out.println("      <p>No campaign found with code: <strong>" + escapeHtml(campaignCode) + "</strong></p>");
            out.println("      <p><a class=\"aira-inline-link\" href=\"" + contextPath
                    + "/admin/es/campaigns\">Back to Campaigns</a></p>");
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildQrPageUrl(String contextPath, String targetPath, String label, String backPath) {
        return contextPath + "/admin/qr?target=" + encodeQueryComponent(targetPath)
                + "&label=" + encodeQueryComponent(label)
                + "&back=" + encodeQueryComponent(backPath);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodeQueryComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
