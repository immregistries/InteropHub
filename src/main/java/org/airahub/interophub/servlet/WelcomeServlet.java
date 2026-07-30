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
                renderAnonymousContent(out, contextPath);
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
            renderAuthenticatedContent(out, contextPath, name, user, availableApps, publicSpaces, privateSpaces,
                    adminUser);
            page.writeEnd(out);
        }
    }

    private void renderAnonymousContent(PrintWriter out, String contextPath) {
        out.println("      <div class=\"aira-container--standard\">");
        out.println("        <div class=\"aira-page-header\">");
        out.println("          <div>");
        out.println("            <h1 class=\"aira-public-title\">Immunization InteropHub</h1>");
        out.println(
                "            <p class=\"aira-public-intro\">Connect with other developers working on immunization interoperability.</p>");
        out.println("          </div>");
        out.println("          <div class=\"aira-action-group\">");
        out.println("            <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                + "/home\">Sign In</a>");
        out.println("          </div>");
        out.println("        </div>");
        out.println("      </div>");
    }

    private void renderAuthenticatedContent(PrintWriter out, String contextPath, String name, User user,
            List<AppRegistry> availableApps, List<EsTopicSpace> publicSpaces, List<EsTopicSpace> privateSpaces,
            boolean adminUser) {
        out.println("      <div class=\"aira-container--standard\">");
        out.println("        <div class=\"aira-page-header\">");
        out.println("          <div>");
        out.println("            <h1 class=\"aira-page-title\">Welcome, " + escapeHtml(name) + "</h1>");
        out.println("            <p class=\"aira-page-intro\">You are signed in as <strong>"
                + escapeHtml(orEmpty(user.getEmail())) + "</strong>.</p>");
        out.println("          </div>");
        out.println("          <div class=\"aira-action-group\">");
        out.println("            <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                + "/es/topics\">Emerging Standard Topics</a>");
        out.println("            <form class=\"aira-inline-form\" action=\"" + contextPath
                + "/logout\" method=\"post\">");
        out.println(
                "              <button type=\"submit\" class=\"aira-button aira-button--secondary\">Logout</button>");
        out.println("            </form>");
        out.println("          </div>");
        out.println("        </div>");

        out.println("        <div class=\"aira-stack\">");

        renderApplicationsSection(out, contextPath, availableApps);
        renderTopicSpacesSection(out, contextPath, publicSpaces, privateSpaces);

        if (adminUser) {
            renderAdminSection(out, contextPath);
        }

        out.println("        </div>");
        out.println("      </div>");
    }

    private void renderApplicationsSection(PrintWriter out, String contextPath, List<AppRegistry> availableApps) {
        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Applications</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        if (availableApps.isEmpty()) {
            out.println(
                    "              <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No applications are currently available.</p></div>");
        } else {
            out.println("              <div class=\"aira-resource-grid\">");
            for (AppRegistry app : availableApps) {
                String appName = orEmpty(app.getAppName());
                out.println("                <a class=\"aira-resource-link\" href=\"" + contextPath
                        + "/app-access?appId=" + app.getAppId()
                        + "\"><span class=\"aira-resource-link__icon\" aria-hidden=\"true\">"
                        + escapeHtml(initialFor(appName))
                        + "</span><span><span class=\"aira-resource-link__title\">" + escapeHtml(appName)
                        + "</span>" + renderResourceDescription(app.getAppDescription()) + "</span></a>");
            }
            out.println("              </div>");
        }
        out.println("            </div>");
        out.println("          </section>");
    }

    private void renderTopicSpacesSection(PrintWriter out, String contextPath, List<EsTopicSpace> publicSpaces,
            List<EsTopicSpace> privateSpaces) {
        out.println("          <section class=\"aira-section-card\">");
        out.println(
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Topic Spaces</h2></div>");
        out.println("            <div class=\"aira-section-card__body aira-stack\">");
        if (publicSpaces.isEmpty() && privateSpaces.isEmpty()) {
            out.println(
                    "              <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No Topic Spaces are currently available.</p></div>");
        } else {
            out.println("              <div>");
            out.println("                <h3 class=\"aira-subsection-title\">Public</h3>");
            renderTopicSpaceGrid(out, contextPath, publicSpaces);
            out.println("              </div>");
            out.println("              <div>");
            out.println("                <h3 class=\"aira-subsection-title\">Private</h3>");
            renderTopicSpaceGrid(out, contextPath, privateSpaces);
            out.println("              </div>");
        }
        out.println("            </div>");
        out.println("          </section>");
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
                "            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Admin</h2></div>");
        out.println("            <div class=\"aira-section-card__body aira-stack\">");
        for (AdminNavRenderer.NavGroup group : AdminNavRenderer.navGroups(contextPath)) {
            out.println("              <div>");
            out.println("                <h3 class=\"aira-subsection-title\">" + escapeHtml(group.title())
                    + "</h3>");
            out.println("                <div class=\"aira-resource-grid\">");
            for (AdminNavRenderer.NavItem item : group.items()) {
                String label = orEmpty(item.label());
                out.println("                  <a class=\"aira-resource-link\" href=\"" + item.href()
                        + "\"><span class=\"aira-resource-link__icon\" aria-hidden=\"true\">"
                        + escapeHtml(initialFor(label))
                        + "</span><span class=\"aira-resource-link__title\">" + escapeHtml(label)
                        + "</span></a>");
            }
            out.println("                </div>");
            out.println("              </div>");
        }
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
