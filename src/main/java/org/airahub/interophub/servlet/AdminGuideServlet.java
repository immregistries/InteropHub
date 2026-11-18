package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideAdminService;
import org.airahub.interophub.service.GuideApiException;
import org.airahub.interophub.service.GuideBuildService;

/**
 * Approved guide URL prefixes and every guide registration; with {@code view=status},
 * the status of every registered guide, and with {@code guide=key} one guide's build detail.
 */
public class AdminGuideServlet extends HttpServlet {
    private static final String ACTIVE_HREF = "/admin/es/guides";
    private final GuideAdminService adminService = new GuideAdminService();
    private final GuideBuildService buildService = new GuideBuildService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> admin = AdminAccessGuard.requireAdmin(request, response);
        if (admin.isEmpty()) {
            return;
        }
        if ("status".equals(request.getParameter("view"))) {
            renderStatus(request, response);
            return;
        }
        String notice = request.getParameter("saved") != null ? "Saved." : null;
        render(request, response, notice, null);
    }

    private void renderStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();
        String guideKey = request.getParameter("guide");
        if (guideKey != null && !guideKey.isBlank()) {
            GuideBuildService.Detail detail = buildService.detail(guideKey);
            if (detail == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
            AdminShellRenderer.render(request, response, "Guide Status - InteropHub", AdminSection.TOPIC_SPACES,
                    ACTIVE_HREF, out -> {
                        if (detail == null) {
                            GuideStatusRenderer.viewSwitch(out, contextPath, true);
                            out.println("          <div class=\"aira-alert aira-alert--error\"><p>"
                                    + "That guide is not registered.</p></div>");
                        } else {
                            GuideStatusRenderer.renderDetail(out, contextPath, detail);
                        }
                    });
            return;
        }
        GuideBuildService.StatusReport report = buildService.status();
        AdminShellRenderer.render(request, response, "Guide Status - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> GuideStatusRenderer.render(out, contextPath, report,
                        request.getParameter("filter"), request.getParameter("code")));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> admin = AdminAccessGuard.requireAdmin(request, response);
        if (admin.isEmpty()) {
            return;
        }
        String action = request.getParameter("action");
        try {
            if ("disable".equals(action)) {
                adminService.disablePrefix(admin.get(), Long.parseLong(request.getParameter("prefixId")));
            } else {
                adminService.addPrefix(admin.get(), request.getParameter("urlPrefix"));
            }
            response.sendRedirect(request.getContextPath() + ACTIVE_HREF + "?saved=1");
        } catch (GuideApiException ex) {
            render(request, response, null, ex.getMessage());
        } catch (NumberFormatException ex) {
            render(request, response, null, "That prefix was not found.");
        }
    }

    private void render(HttpServletRequest request, HttpServletResponse response, String notice, String error)
            throws IOException {
        AdminShellRenderer.render(request, response, "Guide Registrations - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <h1 class=\"aira-page-title\">Guide registrations</h1>");
                    GuideStatusRenderer.viewSwitch(out, request.getContextPath(), false);
                    if (notice != null) {
                        out.println("          <div class=\"aira-alert aira-alert--success\"><p>" + escape(notice) + "</p></div>");
                    }
                    if (error != null) {
                        out.println("          <div class=\"aira-alert aira-alert--error\"><p>" + escape(error) + "</p></div>");
                    }
                    out.println("          <section class=\"aira-section-card\">");
                    out.println("            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Approved URL prefixes</h2></div>");
                    out.println("            <div class=\"aira-section-card__body aira-stack\">");
                    out.println("              <form class=\"aira-stack\" method=\"post\" action=\""
                            + request.getContextPath() + ACTIVE_HREF + "\">");
                    out.println("                <input type=\"hidden\" name=\"action\" value=\"add\">");
                    out.println("                <div class=\"aira-field\">");
                    out.println("                  <label class=\"aira-label\" for=\"urlPrefix\">Prefix</label>");
                    out.println("                  <input class=\"aira-input\" id=\"urlPrefix\" name=\"urlPrefix\" type=\"url\" required>");
                    out.println("                  <p class=\"aira-field-help\">An https URL prefix, for example https://immregistries.github.io/</p>");
                    out.println("                </div>");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Add prefix</button>");
                    out.println("              </form>");
                    out.println("              <div class=\"aira-table-wrap\"><table class=\"aira-table\">");
                    out.println("                <thead><tr><th>Prefix</th><th>Status</th><th></th></tr></thead><tbody>");
                    for (GuideAdminService.PrefixItem prefix : adminService.prefixes()) {
                        out.println("                  <tr><td>" + escape(prefix.prefix()) + "</td><td>"
                                + (prefix.enabled() ? "Approved" : "Disabled") + "</td><td>");
                        if (prefix.enabled()) {
                            out.println("                    <form method=\"post\" action=\"" + request.getContextPath()
                                    + ACTIVE_HREF + "\">");
                            out.println("                      <input type=\"hidden\" name=\"action\" value=\"disable\">");
                            out.println("                      <input type=\"hidden\" name=\"prefixId\" value=\"" + prefix.id() + "\">");
                            out.println("                      <button class=\"aira-button aira-button--secondary\" type=\"submit\">Disable</button>");
                            out.println("                    </form>");
                        }
                        out.println("                  </td></tr>");
                    }
                    out.println("                </tbody></table></div>");
                    out.println("            </div></section>");
                    out.println("          <section class=\"aira-section-card\">");
                    out.println("            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Registrations</h2></div>");
                    out.println("            <div class=\"aira-section-card__body\">");
                    out.println("              <div class=\"aira-table-wrap\"><table class=\"aira-table\">");
                    out.println("                <thead><tr><th>Document</th><th>Title</th><th>Topic</th><th>Status</th><th>URLs</th></tr></thead><tbody>");
                    for (GuideAdminService.RegistrationItem item : adminService.registrations()) {
                        out.println("                  <tr><td>" + escape(item.documentKey()) + "</td><td>"
                                + escape(item.title()) + "</td><td>" + escape(item.topicName()) + " ("
                                + escape(item.topicCode()) + ")</td><td>" + (item.active() ? "Active" : "Inactive")
                                + "</td><td>" + escape(String.join("; ", item.urls())) + "</td></tr>");
                    }
                    out.println("                </tbody></table></div>");
                    out.println("            </div></section>");
                });
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
