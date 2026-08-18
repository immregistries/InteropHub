package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsTopicSupporterDao;
import org.airahub.interophub.dao.SupporterDao;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.Supporter;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.immregistries.aira.web.AiraPage;

/**
 * Public Emerging-Standards-specific presentation of the system-wide
 * Supporter model (see docs/add-supporters.md). Lists active Supporters
 * that support at least one visible Emerging Standards Topic, alphabetized,
 * each with the Emerging Standards Topics it supports. Not limited to
 * Topics currently shown on the main Topics board.
 */
public class EsSupportersServlet extends HttpServlet {

    private static final String SPACE_CODE = "emerging-standards";

    private final AuthFlowService authFlowService;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsTopicDao esTopicDao;
    private final SupporterDao supporterDao;
    private final EsTopicSupporterDao topicSupporterDao;
    private final TopicSpaceAccessService topicSpaceAccessService;

    public EsSupportersServlet() {
        this.authFlowService = new AuthFlowService();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.esTopicDao = new EsTopicDao();
        this.supporterDao = new SupporterDao();
        this.topicSupporterDao = new EsTopicSupporterDao();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
    }

    private record SupporterEntry(Supporter supporter, List<EsTopic> topics) {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        User viewer = authenticatedUser.orElse(null);

        EsTopicSpace topicSpace = topicSpaceDao.findBySpaceCode(SPACE_CODE).orElse(null);
        if (topicSpace == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        List<EsTopic> visibleTopics = topicSpaceAccessService.filterVisibleTopics(viewer,
                esTopicDao.findActiveBySpaceIdOrderByTopicName(topicSpace.getEsTopicSpaceId()));

        List<SupporterEntry> entries = new ArrayList<>();
        for (Supporter supporter : supporterDao.findAllActiveOrderByShortName()) {
            List<Long> supportedTopicIds = topicSupporterDao.findTopicIdsBySupporterId(supporter.getSupporterId());
            List<EsTopic> supporterTopics = visibleTopics.stream()
                    .filter(t -> supportedTopicIds.contains(t.getEsTopicId()))
                    .sorted((a, b) -> orEmpty(a.getTopicName()).compareToIgnoreCase(orEmpty(b.getTopicName())))
                    .collect(Collectors.toList());
            if (!supporterTopics.isEmpty()) {
                entries.add(new SupporterEntry(supporter, supporterTopics));
            }
        }

        response.setContentType("text/html;charset=UTF-8");
        AiraPage page = InteropAiraPageFactory
                .base(request, "Supporters - " + orEmpty(topicSpace.getSpaceName()) + " - InteropHub")
                .applicationSubtitle("Supporters")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.topicsMeetingsContext(
                        topicSpace.getSpaceName(), topicSpace.getSpaceCode(), false, false, true))
                .build();

        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            out.println("      <div class=\"aira-page-header\">");
            out.println("        <div>");
            out.println("          <h1 class=\"aira-page-title\">Supporters</h1>");
            out.println("        </div>");
            out.println("      </div>");

            out.println("      <div class=\"aira-prose\">");
            out.println(
                    "        <p>Emerging Standards tracks interoperability topics that may be important to the immunization community. Inclusion of a topic in Emerging Standards does not mean that AIRA, CDC, or another organization funds, endorses, or supports work related to that topic.</p>");
            out.println(
                    "        <p>Organizations listed here have explicitly agreed to be publicly identified as supporting efforts related to one or more topics. Support may take different forms, and InteropHub does not distinguish among funding, technical assistance, staffing, implementation, promotion, or other forms of support.</p>");
            out.println("      </div>");

            if (entries.isEmpty()) {
                out.println("      <div class=\"aira-empty-state\">");
                out.println(
                        "        <p class=\"aira-empty-state__title\">No organizations are currently listed as public supporters.</p>");
                out.println(
                        "        <p>Organizations interested in being publicly identified as supporting one or more Emerging Standards topics should contact AIRA.</p>");
                out.println("      </div>");
            } else {
                out.println("      <div class=\"aira-stack\">");
                for (SupporterEntry entry : entries) {
                    renderSupporterCard(out, contextPath, entry);
                }
                out.println("      </div>");
            }

            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    private void renderSupporterCard(PrintWriter out, String contextPath, SupporterEntry entry) {
        Supporter supporter = entry.supporter();
        String shortName = orEmpty(supporter.getShortName());
        out.println("        <section class=\"aira-section-card\">");
        out.println("          <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">"
                + escapeHtml(shortName) + "</h2></div>");
        out.println("          <div class=\"aira-section-card__body aira-stack aira-stack--compact\">");

        String fullName = trimToNull(supporter.getFullName());
        if (fullName != null && !fullName.equalsIgnoreCase(shortName)) {
            out.println("            <p class=\"aira-meta\">" + escapeHtml(fullName) + "</p>");
        }
        String description = trimToNull(supporter.getDescription());
        if (description != null) {
            out.println("            <p>" + escapeHtml(description) + "</p>");
        }
        String websiteUrl = trimToNull(supporter.getWebsiteUrl());
        if (websiteUrl != null) {
            out.println("            <p><a class=\"aira-inline-link\" href=\"" + escapeHtml(websiteUrl)
                    + "\" target=\"_blank\" rel=\"noopener\">" + escapeHtml(websiteUrl) + "</a></p>");
        }

        out.println("            <div class=\"aira-chip-list\">");
        for (EsTopic topic : entry.topics()) {
            out.println("              <a class=\"aira-chip\" href=\"" + contextPath + "/es/topic/"
                    + topic.getEsTopicId() + "\">" + escapeHtml(orEmpty(topic.getTopicName())) + "</a>");
        }
        out.println("            </div>");

        out.println("          </div>");
        out.println("        </section>");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
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
