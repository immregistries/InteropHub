package org.airahub.interophub.servlet;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.SupporterDao;
import org.airahub.interophub.model.Supporter;
import org.airahub.interophub.model.User;

/**
 * Admin management of Supporter records themselves (short/full name,
 * description, website, active flag) - not their relationships to
 * individual Topics, which are managed from a Topic's own management page
 * (see EsTopicSupporterServlet / TopicManageView.SUPPORTERS). Supporters are
 * system-wide (see docs/add-supporters.md); this is simply their first
 * administration entry point, under Emerging Standards. There is
 * deliberately no delete option - only activate/deactivate.
 */
public class AdminEsSupporterServlet extends HttpServlet {

    private static final String ACTIVE_HREF = "/admin/es/supporters";

    private final SupporterDao supporterDao;

    public AdminEsSupporterServlet() {
        this.supporterDao = new SupporterDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String mode = trimToNull(request.getParameter("mode"));
        String idRaw = trimToNull(request.getParameter("supporterId"));

        if ("new".equalsIgnoreCase(mode)) {
            renderEditForm(request, response, new Supporter(), null, true);
            return;
        }

        if (idRaw != null) {
            Long supporterId = parseId(idRaw);
            Supporter supporter = supporterId == null ? null : supporterDao.findById(supporterId).orElse(null);
            if (supporter == null) {
                renderList(request, response, "Supporter was not found.");
                return;
            }
            if ("edit".equalsIgnoreCase(mode)) {
                renderEditForm(request, response, supporter, null, false);
                return;
            }
            renderDetails(request, response, supporter);
            return;
        }

        String message = request.getParameter("saved") != null ? "Supporter saved." : null;
        renderList(request, response, message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String idRaw = trimToNull(request.getParameter("supporterId"));
        boolean creating = idRaw == null;

        Supporter supporter;
        if (creating) {
            supporter = new Supporter();
        } else {
            Long supporterId = parseId(idRaw);
            supporter = supporterId == null ? null : supporterDao.findById(supporterId).orElse(null);
            if (supporter == null) {
                renderList(request, response, "Supporter was not found.");
                return;
            }
        }

        String shortName = trimToNull(request.getParameter("shortName"));
        String fullName = trimToNull(request.getParameter("fullName"));
        String description = trimToNull(request.getParameter("description"));
        String websiteUrl = trimToNull(request.getParameter("websiteUrl"));
        boolean active = request.getParameter("active") != null;

        try {
            supporter.setShortName(required(shortName, "Short Name"));
            supporter.setFullName(required(fullName, "Full Name"));
            supporter.setDescription(description);
            supporter.setWebsiteUrl(websiteUrl);
            supporter.setActive(active);

            supporterDao.saveOrUpdate(supporter);
            response.sendRedirect(contextPath + "/admin/es/supporters?saved=1");
        } catch (Exception ex) {
            supporter.setShortName(shortName);
            supporter.setFullName(fullName);
            supporter.setDescription(description);
            supporter.setWebsiteUrl(websiteUrl);
            supporter.setActive(active);
            renderEditForm(request, response, supporter, ex.getMessage(), creating);
        }
    }

    private void renderList(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        String contextPath = request.getContextPath();
        List<Supporter> supporters = supporterDao.findAllOrderByShortName();

        AdminShellRenderer.render(request, response, "Supporters Admin - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Supporters</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Organizations that have agreed to be publicly identified as supporting efforts related to one or more Topics. Supporters are system-wide and are assigned to Topics from each Topic's own management page.</p>");
                    if (message != null && !message.isBlank()) {
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p>"
                                + escapeHtml(message) + "</p></div>");
                    }

                    out.println("            <div class=\"aira-table-wrap\">");
                    out.println("            <table class=\"aira-table\">");
                    out.println("              <thead><tr>");
                    out.println("                <th>Short Name</th>");
                    out.println("                <th>Full Name</th>");
                    out.println("                <th>Website</th>");
                    out.println("                <th>Status</th>");
                    out.println("              </tr></thead>");
                    out.println("              <tbody>");
                    for (Supporter supporter : supporters) {
                        out.println("                <tr>");
                        out.println("                  <td><a class=\"aira-inline-link\" href=\"" + contextPath
                                + "/admin/es/supporters?supporterId=" + supporter.getSupporterId() + "&mode=edit\">"
                                + escapeHtml(orEmpty(supporter.getShortName())) + "</a></td>");
                        out.println("                  <td>" + escapeHtml(orEmpty(supporter.getFullName()))
                                + "</td>");
                        String websiteUrl = trimToNull(supporter.getWebsiteUrl());
                        out.println("                  <td>" + (websiteUrl == null ? "" : "<a href=\""
                                + escapeHtml(websiteUrl) + "\" target=\"_blank\" rel=\"noopener\">"
                                + escapeHtml(websiteUrl) + "</a>") + "</td>");
                        out.println("                  <td>" + activeBadge(Boolean.TRUE.equals(supporter.getActive()))
                                + "</td>");
                        out.println("                </tr>");
                    }
                    if (supporters.isEmpty()) {
                        out.println("                <tr><td colspan=\"4\">No Supporters have been created yet.</td></tr>");
                    }
                    out.println("              </tbody>");
                    out.println("            </table>");
                    out.println("            </div>");

                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                            + "/admin/es/supporters?mode=new\">Add Supporter</a>");
                    out.println("            </div>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es\">Back to Emerging Standards</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderDetails(HttpServletRequest request, HttpServletResponse response, Supporter supporter)
            throws IOException {
        String contextPath = request.getContextPath();

        AdminShellRenderer.render(request, response, "Supporter Details - InteropHub", AdminSection.TOPIC_SPACES,
                ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Supporter Details</h2>");
                    out.println("            <section class=\"aira-panel\">");
                    out.println("              <p><strong>Short Name:</strong> "
                            + escapeHtml(orEmpty(supporter.getShortName())) + "</p>");
                    out.println("              <p><strong>Full Name:</strong> "
                            + escapeHtml(orEmpty(supporter.getFullName())) + "</p>");
                    out.println("              <p><strong>Description:</strong> "
                            + escapeHtml(orEmpty(supporter.getDescription())) + "</p>");
                    out.println("              <p><strong>Website URL:</strong> "
                            + escapeHtml(orEmpty(supporter.getWebsiteUrl())) + "</p>");
                    out.println("              <p><strong>Status:</strong> "
                            + activeBadge(Boolean.TRUE.equals(supporter.getActive())) + "</p>");
                    out.println("            </section>");
                    out.println("            <div class=\"aira-action-group\">");
                    out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                            + "/admin/es/supporters?supporterId=" + supporter.getSupporterId()
                            + "&mode=edit\">Edit Supporter</a>");
                    out.println("            </div>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/supporters\">Back to Supporters</a></p>");
                    out.println("          </section>");
                });
    }

    private void renderEditForm(HttpServletRequest request, HttpServletResponse response, Supporter supporter,
            String errorMessage, boolean creating) throws IOException {
        String contextPath = request.getContextPath();

        AdminShellRenderer.render(request, response, (creating ? "Create" : "Edit") + " Supporter - InteropHub",
                AdminSection.TOPIC_SPACES, ACTIVE_HREF, out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">" + (creating ? "Create" : "Edit")
                            + " Supporter</h2>");

                    if (errorMessage != null && !errorMessage.isBlank()) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--danger\"><p><strong>Could not save:</strong> "
                                        + escapeHtml(errorMessage) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/es/supporters\" method=\"post\">");
                    if (!creating && supporter.getSupporterId() != null) {
                        out.println("              <input type=\"hidden\" name=\"supporterId\" value=\""
                                + supporter.getSupporterId() + "\" />");
                    }

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"shortName\">Short Name (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"shortName\" name=\"shortName\" type=\"text\" maxlength=\"120\" required value=\""
                                    + escapeHtml(orEmpty(supporter.getShortName())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"fullName\">Full Name (required)</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"fullName\" name=\"fullName\" type=\"text\" maxlength=\"200\" required value=\""
                                    + escapeHtml(orEmpty(supporter.getFullName())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"description\">Description</label>");
                    out.println(
                            "                <textarea class=\"aira-textarea\" id=\"description\" name=\"description\" rows=\"3\">"
                                    + escapeHtml(orEmpty(supporter.getDescription())) + "</textarea>");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"websiteUrl\">Website URL</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"websiteUrl\" name=\"websiteUrl\" type=\"url\" maxlength=\"500\" value=\""
                                    + escapeHtml(orEmpty(supporter.getWebsiteUrl())) + "\" />");
                    out.println("              </div>");

                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"active\""
                            + (Boolean.TRUE.equals(supporter.getActive()) || creating ? " checked" : "")
                            + " /> Active</label>");
                    out.println(
                            "              <p class=\"aira-meta\">Inactive Supporters stay in the database and keep their existing Topic relationships, but stop appearing publicly and can't be newly assigned to Topics.</p>");

                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("            <p><a class=\"aira-inline-link\" href=\"" + contextPath
                            + "/admin/es/supporters\">Back to Supporters</a></p>");
                    out.println("          </section>");
                });
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String required(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
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

    private String activeBadge(boolean active) {
        if (active) {
            return "<span class=\"aira-badge aira-badge--success\">Active</span>";
        }
        return "<span class=\"aira-badge aira-badge--subtle\">Inactive</span>";
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
