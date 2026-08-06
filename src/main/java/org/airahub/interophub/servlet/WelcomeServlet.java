package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.AppRegistryDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.model.AppRegistry;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsInterestService;
import org.airahub.interophub.service.TopicSpaceAccessService;
import org.immregistries.aira.web.AiraPage;

public class WelcomeServlet extends HttpServlet {
    private final AuthFlowService authFlowService;
    private final AppRegistryDao appRegistryDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsInterestService esInterestService;
    private final TopicSpaceAccessService topicSpaceAccessService;

    public WelcomeServlet() {
        this.authFlowService = new AuthFlowService();
        this.appRegistryDao = new AppRegistryDao();
        this.topicSpaceDao = new EsTopicSpaceDao();
        this.esInterestService = new EsInterestService();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User user = authFlowService.findAuthenticatedUser(request).orElse(null);
        String contextPath = request.getContextPath();

        List<EsTopicSpace> visibleTopicSpaces = topicSpaceAccessService
                .filterVisibleSpaces(user, topicSpaceDao.findAllActiveOrdered());
        List<EsTopicSpace> publicSpaces = new ArrayList<>();
        List<EsTopicSpace> privateSpaces = new ArrayList<>();
        for (EsTopicSpace topicSpace : visibleTopicSpaces) {
            if (topicSpace.getVisibility() == EsTopicSpace.Visibility.PRIVATE) {
                privateSpaces.add(topicSpace);
            } else {
                publicSpaces.add(topicSpace);
            }
        }
        List<EsTopicSpace> spacePickerOrder = new ArrayList<>(publicSpaces);
        spacePickerOrder.addAll(privateSpaces);

        if (user == null) {
            response.setContentType("text/html;charset=UTF-8");
            AiraPage page = InteropAiraPageFactory.base(request, "Welcome - InteropHub")
                    .applicationSubtitle("Welcome")
                    .mainClass("aira-main")
                    .context(InteropAiraPageFactory.topicSpacePickerContext(spacePickerOrder))
                    .build();
            try (PrintWriter out = response.getWriter()) {
                page.writeStart(out);
                renderAnonymousContent(out, contextPath, publicSpaces);
                page.writeEnd(out);
            }
            return;
        }

        esInterestService.linkAnonymousRecordsByEmail(user.getUserId(), user.getEmailNormalized());

        boolean adminUser = authFlowService.isAdminUser(user);
        String name = user.getFullName() == null || user.getFullName().isBlank()
                ? user.getEmail()
                : user.getFullName();
        List<AppRegistry> availableApps = appRegistryDao.findAllOrdered().stream()
                .filter(app -> Boolean.TRUE.equals(app.getEnabled()))
                .filter(app -> Boolean.TRUE.equals(app.getVisible()))
                .filter(app -> !Boolean.TRUE.equals(app.getKillSwitch()))
                .filter(app -> app.getAppName() != null && !app.getAppName().isBlank())
                .filter(app -> app.getDefaultRedirectUrl() != null && !app.getDefaultRedirectUrl().isBlank())
                .toList();

        response.setContentType("text/html;charset=UTF-8");
        AiraPage page = InteropAiraPageFactory.base(request, "Welcome - InteropHub")
                .applicationSubtitle("Welcome")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.topicSpacePickerContext(spacePickerOrder))
                .build();
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            renderAuthenticatedContent(out, contextPath, name, availableApps, publicSpaces, privateSpaces,
                    adminUser);
            page.writeEnd(out);
        }
    }

    private void renderAnonymousContent(PrintWriter out, String contextPath, List<EsTopicSpace> publicSpaces) {
        out.println("      <div class=\"aira-container--standard\">");
        out.println("        <div class=\"aira-page-header\">");
        out.println("          <div>");
        out.println("            <h1 class=\"aira-public-title\">Immunization InteropHub</h1>");
        SignInInfoRenderer.renderIntroParagraphs(out);
        out.println("          </div>");
        out.println("          <div class=\"aira-action-group\">");
        out.println("            <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                + "/home\">Sign In</a>");
        out.println("          </div>");
        out.println("        </div>");

        out.println("        <div class=\"aira-stack\">");

        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Public Topic Spaces</h2>");
        renderTopicSpaceGrid(out, contextPath, publicSpaces);
        out.println("          </section>");

        SignInInfoRenderer.renderCapabilitiesSection(out);
        SignInInfoRenderer.renderDetailsSection(out);

        out.println("        </div>");
        out.println("      </div>");
    }

    private void renderAuthenticatedContent(PrintWriter out, String contextPath, String name,
            List<AppRegistry> availableApps, List<EsTopicSpace> publicSpaces, List<EsTopicSpace> privateSpaces,
            boolean adminUser) {
        out.println("      <div class=\"aira-container--standard\">");
        out.println("        <div class=\"aira-page-header\">");
        out.println("          <div>");
        out.println("            <h1 class=\"aira-page-title\">Welcome, " + escapeHtml(name) + "</h1>");
        out.println(
                "            <p class=\"aira-page-intro\"><strong>Discover, participate in, and advance immunization interoperability work.</strong></p>");
        out.println("            <p class=\"aira-page-intro\">InteropHub connects topics, meetings, people, "
                + "resources, and testing systems used by the immunization interoperability community. Start "
                + "with a Topic Space to explore a related body of work, or open an application when you need "
                + "a demonstration or testing system.</p>");
        out.println("          </div>");
        out.println("          <div class=\"aira-action-group\">");
        out.println("            <form class=\"aira-inline-form\" action=\"" + contextPath
                + "/logout\" method=\"post\">");
        out.println(
                "              <button type=\"submit\" class=\"aira-button aira-button--tertiary aira-button--small\">Logout</button>");
        out.println("            </form>");
        out.println("          </div>");
        out.println("        </div>");

        out.println("        <div class=\"aira-stack\">");

        renderTopicSpacesSection(out, contextPath, publicSpaces, privateSpaces);
        renderApplicationsSection(out, contextPath, availableApps);
        renderHowInteropHubSupportsSection(out);

        if (adminUser) {
            renderAdminSection(out, contextPath);
        }

        out.println("        </div>");
        out.println("      </div>");
    }

    private void renderTopicSpacesSection(PrintWriter out, String contextPath, List<EsTopicSpace> publicSpaces,
            List<EsTopicSpace> privateSpaces) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Explore work through Topic Spaces</h2>");
        out.println("            <p class=\"aira-meta\">Topic Spaces organize related topics, meetings, resources, "
                + "participants, and outcomes. Select a Topic Space from the navigation above to explore the work "
                + "available to you.</p>");
        List<EsTopicSpace> allSpaces = new ArrayList<>(publicSpaces);
        allSpaces.addAll(privateSpaces);
        if (allSpaces.isEmpty()) {
            out.println(
                    "            <p class=\"aira-meta\">No Topic Spaces are currently available.</p>");
        } else {
            out.println("            <div class=\"aira-stack aira-stack--compact\">");
            for (EsTopicSpace topicSpace : allSpaces) {
                boolean isPrivate = topicSpace.getVisibility() == EsTopicSpace.Visibility.PRIVATE;
                String spaceName = orEmpty(topicSpace.getSpaceName());
                out.println("              <div>");
                out.println("                <p><a class=\"aira-inline-link\" href=\""
                        + buildTopicSpaceUrl(contextPath, topicSpace.getSpaceCode()) + "\">" + escapeHtml(spaceName)
                        + "</a>"
                        + (isPrivate ? " <span class=\"aira-badge aira-badge--subtle\">Private</span>" : "")
                        + "</p>");
                String description = trimToNull(topicSpace.getDescription());
                if (description != null) {
                    out.println("                <p class=\"aira-meta\">" + escapeHtml(description) + "</p>");
                }
                out.println("              </div>");
            }
            out.println("            </div>");
        }
        out.println("          </section>");
    }

    private void renderApplicationsSection(PrintWriter out, String contextPath, List<AppRegistry> availableApps) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">Use demonstration and testing applications</h2>");
        out.println("            <p class=\"aira-meta\">InteropHub provides access to applications that support "
                + "standards development, demonstration, and interoperability testing. Applications may relate to "
                + "work found in one or more Topic Spaces.</p>");
        if (availableApps.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No applications are currently available.</p>");
        } else {
            out.println("            <div class=\"aira-grid\">");
            for (AppRegistry app : availableApps) {
                String appName = orEmpty(app.getAppName());
                out.println("              <div>");
                out.println("                <h3 class=\"aira-subsection-title\">" + escapeHtml(appName) + "</h3>");
                String description = trimToNull(app.getAppDescription());
                if (description != null) {
                    out.println("                <p class=\"aira-meta\">" + escapeHtml(description) + "</p>");
                }
                out.println("                <a class=\"aira-button aira-button--secondary aira-button--small\" href=\""
                        + contextPath + "/app-access?appId=" + app.getAppId() + "\">Open application</a>");
                out.println("              </div>");
            }
            out.println("            </div>");
        }
        out.println("          </section>");
    }

    private void renderHowInteropHubSupportsSection(PrintWriter out) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">One place to follow interoperability work</h2>");
        out.println("            <p class=\"aira-meta\">InteropHub connects the activities that move "
                + "interoperability work from initial discovery through participation, testing, and durable "
                + "results.</p>");
        out.println("            <div class=\"aira-grid\">");
        renderSupportItem(out, "Discover topics",
                "Find emerging issues, understand why they matter, and see how they relate to other work.");
        renderSupportItem(out, "Follow work",
                "Stay connected to the topics and recurring meeting series that matter to you.");
        renderSupportItem(out, "Join meetings",
                "Find upcoming discussions, review agendas, and remain connected to the topics discussed.");
        renderSupportItem(out, "Use testing systems",
                "Access demonstration applications, technical resources, and organized interoperability activities.");
        renderSupportItem(out, "Preserve outcomes",
                "Return to meeting notes, decisions, presentations, resources, and prior work without "
                        + "reconstructing the history from separate systems.");
        out.println("            </div>");
        out.println("          </section>");
    }

    private void renderSupportItem(PrintWriter out, String title, String description) {
        out.println("              <div>");
        out.println("                <h3 class=\"aira-subsection-title\">" + escapeHtml(title) + "</h3>");
        out.println("                <p class=\"aira-meta\">" + escapeHtml(description) + "</p>");
        out.println("              </div>");
    }

    private void renderTopicSpaceGrid(PrintWriter out, String contextPath, List<EsTopicSpace> spaces) {
        if (spaces.isEmpty()) {
            out.println(
                    "                <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">None available.</p></div>");
            return;
        }
        out.println("                <div class=\"aira-resource-grid\">");
        for (EsTopicSpace topicSpace : spaces) {
            String spaceName = orEmpty(topicSpace.getSpaceName());
            out.println("                  <a class=\"aira-resource-link\" href=\""
                    + buildTopicSpaceUrl(contextPath, topicSpace.getSpaceCode())
                    + "\"><span class=\"aira-resource-link__icon\" aria-hidden=\"true\">"
                    + escapeHtml(initialFor(spaceName))
                    + "</span><span><span class=\"aira-resource-link__title\">" + escapeHtml(spaceName)
                    + "</span>" + renderResourceDescription(topicSpace.getDescription()) + "</span></a>");
        }
        out.println("                </div>");
    }

    private void renderAdminSection(PrintWriter out, String contextPath) {
        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Administration</h2></div>");
        out.println("            <div class=\"aira-section-card__body aira-stack aira-stack--compact\">");
        out.println(
                "              <p class=\"aira-meta\">Visible because you have admin access. Not part of the general participant experience.</p>");
        out.println("              <a class=\"aira-button aira-button--secondary aira-button--small\" href=\""
                + contextPath + "/admin\">Open Admin Home</a>");
        out.println("            </div>");
        out.println("          </section>");
    }

    private String renderResourceDescription(String description) {
        String trimmed = trimToNull(description);
        if (trimmed == null) {
            return "";
        }
        String preview = trimmed.length() <= 110 ? trimmed : trimmed.substring(0, 107) + "...";
        return "<span class=\"aira-resource-link__description\">" + escapeHtml(preview) + "</span>";
    }

    private String initialFor(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            return "?";
        }
        return trimmed.substring(0, 1).toUpperCase();
    }

    private String buildTopicSpaceUrl(String contextPath, String spaceCode) {
        return contextPath + "/spaces/"
                + java.net.URLEncoder.encode(orEmpty(spaceCode), java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20")
                + "/topics";
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
