package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EmailProspectBrowseRow;
import org.airahub.interophub.dao.EmailProspectDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.EsInterestService;

public class AdminRegisteredUsersServlet extends HttpServlet {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int DEFAULT_LIMIT = 20;

    private final UserDao userDao;
    private final EmailProspectDao emailProspectDao;
    private final EsInterestService esInterestService;

    public AdminRegisteredUsersServlet() {
        this.userDao = new UserDao();
        this.emailProspectDao = new EmailProspectDao();
        this.esInterestService = new EsInterestService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String search = trimToNull(request.getParameter("search"));
        String linkedParam = trimToNull(request.getParameter("linked"));
        int linkedCount = 0;
        if (linkedParam != null) {
            try {
                linkedCount = Integer.parseInt(linkedParam);
            } catch (NumberFormatException ignored) {
            }
        }
        String dedupedParam = trimToNull(request.getParameter("deduped"));
        int dedupedCount = 0;
        if (dedupedParam != null) {
            try {
                dedupedCount = Integer.parseInt(dedupedParam);
            } catch (NumberFormatException ignored) {
            }
        }

        // Stat card counts — always totals, never filtered by search
        long countRegistered = userDao.countActiveUsers();
        long countActiveLogins = userDao.countRecentLogins();
        long countProspects = emailProspectDao.countProspects();

        // Table data — filtered by search when present
        List<User> registrations;
        List<User> activeLogins;
        List<EmailProspectBrowseRow> prospects;
        if (search != null) {
            registrations = userDao.searchUsers(search);
            activeLogins = userDao.searchRecentLogins(search);
            prospects = emailProspectDao.searchProspects(search);
        } else {
            registrations = userDao.findRecentRegistrations(DEFAULT_LIMIT);
            activeLogins = userDao.findRecentLogins(DEFAULT_LIMIT);
            prospects = emailProspectDao.findRecentProspects(DEFAULT_LIMIT);
        }

        renderPage(request, response, search, linkedCount, dedupedCount,
                countRegistered, countActiveLogins, countProspects,
                registrations, activeLogins, prospects);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }
        String action = trimToNull(request.getParameter("action"));
        if ("linkAllProspects".equals(action)) {
            EsInterestService.LinkResult result = esInterestService.linkAllProspects();
            response.sendRedirect(request.getContextPath() + "/admin/users?linked=" + result.getUsersProcessed()
                    + "&deduped=" + result.getDuplicateSubscriptionsRemoved());
        } else {
            response.sendRedirect(request.getContextPath() + "/admin/users");
        }
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response, String search,
            int linkedCount, int dedupedCount,
            long countRegistered, long countActiveLogins, long countProspects,
            List<User> registrations, List<User> activeLogins,
            List<EmailProspectBrowseRow> prospects) throws IOException {
        String contextPath = request.getContextPath();
        boolean isSearch = search != null;

        AdminShellRenderer.render(request, response, "Registered Users - InteropHub", AdminSection.PEOPLE,
                "/admin/users", out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Users &amp; Prospects</h2>");

                    // --- Search bar ---
                    out.println("            <form class=\"aira-form\" method=\"get\" action=\"" + contextPath + "/admin/users\">");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"search\">Search by email, name, or organization:</label>");
                    out.println("                <input class=\"aira-input\" type=\"text\" id=\"search\" name=\"search\" value=\""
                            + escapeHtml(search == null ? "" : search) + "\" />");
                    out.println("              </div>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Search</button>");
                    if (isSearch) {
                        out.println("                <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath + "/admin/users\">Clear</a>");
                    }
                    out.println("              </div>");
                    out.println("            </form>");

                    // --- Stat cards (always totals) ---
                    out.println("            <div class=\"aira-cluster\">");
                    renderStatCard(out, String.valueOf(countRegistered), "Registered Users");
                    renderStatCard(out, String.valueOf(countActiveLogins), "Active (last 30 days)");
                    renderStatCard(out, String.valueOf(countProspects), "Prospects (email only)");
                    out.println("            </div>");

                    // --- Success banner after link action ---
                    if (linkedCount > 0) {
                        String bannerMsg = linkedCount + " user" + (linkedCount == 1 ? "" : "s")
                                + " processed — anonymous records have been linked.";
                        if (dedupedCount > 0) {
                            bannerMsg += " " + dedupedCount + " duplicate subscription"
                                    + (dedupedCount == 1 ? "" : "s") + " merged.";
                        }
                        out.println("            <div class=\"aira-alert aira-alert--success\"><p><strong>"
                                + escapeHtml(bannerMsg)
                                + "</strong></p></div>");
                    }

                    // --- Table 1: Registrations ---
                    String regHeading = isSearch
                            ? "Registrations matching &ldquo;" + escapeHtml(search) + "&rdquo; (" + registrations.size()
                                    + ")"
                            : "Last " + DEFAULT_LIMIT + " Registrations";
                    renderRegistrationsTable(out, contextPath, regHeading, registrations);

                    // --- Table 2: Active logins (last 30 days) ---
                    String loginHeading = isSearch
                            ? "Active Users matching &ldquo;" + escapeHtml(search) + "&rdquo; (" + activeLogins.size() + ")"
                            : "Last " + DEFAULT_LIMIT + " Active Users (30 days)";
                    renderActiveLoginsTable(out, contextPath, loginHeading, activeLogins);

                    // --- Table 3: Prospects ---
                    String prospectHeading = isSearch
                            ? "Prospects matching &ldquo;" + escapeHtml(search) + "&rdquo; (" + prospects.size() + ")"
                            : "Last " + DEFAULT_LIMIT + " Prospects (not yet registered)";

                    // --- Link All Prospects button ---
                    out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath
                            + "/admin/users\">");
                    out.println("              <input type=\"hidden\" name=\"action\" value=\"linkAllProspects\" />");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println("                <button class=\"aira-button aira-button--secondary\" type=\"submit\">Link All Prospects to Registered Users</button>");
                    out.println("              </div>");
                    out.println("            </form>");

                    renderProspectsTable(out, prospectHeading, prospects);

                    out.println("          </section>");
                });
    }

    private void renderStatCard(PrintWriter out, String value, String label) {
        out.println("              <div class=\"aira-meta-chip\">");
        out.println("                <span class=\"aira-meta-chip__value\">" + escapeHtml(value) + "</span>");
        out.println("                <span class=\"aira-meta-chip__label\">" + escapeHtml(label) + "</span>");
        out.println("              </div>");
    }

    private void renderRegistrationsTable(PrintWriter out, String contextPath, String heading, List<User> users) {
        out.println("            <h3 class=\"aira-subsection-title\">" + heading + "</h3>");
        if (users.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No registrations found.</p>");
            return;
        }
        out.println("            <div class=\"aira-table-wrap\">");
        out.println("            <table class=\"aira-table\">");
        out.println("              <thead><tr>");
        out.println("                <th>Name</th><th>Email</th><th>Organization</th><th>Status</th><th>Registered</th>");
        out.println("              </tr></thead>");
        out.println("              <tbody>");
        for (User user : users) {
            String link = contextPath + "/admin/users/detail?userId=" + user.getUserId();
            String name = trimToNull(user.getFullName());
            if (name == null)
                name = orEmpty(user.getEmail());
            out.println("                <tr>");
            out.println("                  <td><a class=\"aira-inline-link\" href=\"" + link + "\">" + escapeHtml(name) + "</a></td>");
            out.println("                  <td>" + escapeHtml(orEmpty(user.getEmail())) + "</td>");
            out.println("                  <td>" + escapeHtml(orEmpty(user.getOrganization())) + "</td>");
            out.println("                  <td>" + escapeHtml(user.getStatus() == null ? "" : user.getStatus().name())
                    + "</td>");
            out.println("                  <td>" + formatDateTime(user.getCreatedAt()) + "</td>");
            out.println("                </tr>");
        }
        out.println("              </tbody></table>");
        out.println("            </div>");
    }

    private void renderActiveLoginsTable(PrintWriter out, String contextPath, String heading, List<User> users) {
        out.println("            <h3 class=\"aira-subsection-title\">" + heading + "</h3>");
        if (users.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No active users found.</p>");
            return;
        }
        out.println("            <div class=\"aira-table-wrap\">");
        out.println("            <table class=\"aira-table\">");
        out.println("              <thead><tr>");
        out.println("                <th>Name</th><th>Email</th><th>Organization</th><th>Last Login</th>");
        out.println("              </tr></thead>");
        out.println("              <tbody>");
        for (User user : users) {
            String link = contextPath + "/admin/users/detail?userId=" + user.getUserId();
            String name = trimToNull(user.getFullName());
            if (name == null)
                name = orEmpty(user.getEmail());
            out.println("                <tr>");
            out.println("                  <td><a class=\"aira-inline-link\" href=\"" + link + "\">" + escapeHtml(name) + "</a></td>");
            out.println("                  <td>" + escapeHtml(orEmpty(user.getEmail())) + "</td>");
            out.println("                  <td>" + escapeHtml(orEmpty(user.getOrganization())) + "</td>");
            out.println("                  <td>" + formatDateTime(user.getLastLoginAt()) + "</td>");
            out.println("                </tr>");
        }
        out.println("              </tbody></table>");
        out.println("            </div>");
    }

    private void renderProspectsTable(PrintWriter out, String heading, List<EmailProspectBrowseRow> prospects) {
        out.println("            <h3 class=\"aira-subsection-title\">" + heading + "</h3>");
        if (prospects.isEmpty()) {
            out.println("            <p class=\"aira-meta\">No prospects found.</p>");
            return;
        }
        out.println("            <div class=\"aira-table-wrap\">");
        out.println("            <table class=\"aira-table\">");
        out.println("              <thead><tr>");
        out.println("                <th>Email</th><th>First Contact</th><th>Last Contact</th><th>Sources</th>");
        out.println("              </tr></thead>");
        out.println("              <tbody>");
        for (EmailProspectBrowseRow row : prospects) {
            out.println("                <tr>");
            out.println("                  <td>" + escapeHtml(row.getEmailNormalized()) + "</td>");
            out.println("                  <td>" + formatDateTime(row.getFirstContactAt()) + "</td>");
            out.println("                  <td>" + formatDateTime(row.getLastContactAt()) + "</td>");
            out.println("                  <td>" + buildSourcesLabel(row) + "</td>");
            out.println("                </tr>");
        }
        out.println("              </tbody></table>");
        out.println("            </div>");
    }

    private String buildSourcesLabel(EmailProspectBrowseRow row) {
        StringBuilder sb = new StringBuilder();
        if (row.getCampaignRegistrationCount() > 0) {
            sb.append("Campaign Reg (").append(row.getCampaignRegistrationCount()).append(") ");
        }
        if (row.getCommentCount() > 0) {
            sb.append("Comment (").append(row.getCommentCount()).append(") ");
        }
        if (row.getSubscriptionCount() > 0) {
            sb.append("Subscription (").append(row.getSubscriptionCount()).append(") ");
        }
        if (row.getMeetingMemberCount() > 0) {
            sb.append("Meeting (").append(row.getMeetingMemberCount()).append(")");
        }
        return escapeHtml(sb.toString().trim());
    }

    private String formatDateTime(LocalDateTime dt) {
        if (dt == null) {
            return "";
        }
        return escapeHtml(DATETIME_FORMATTER.format(dt));
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
