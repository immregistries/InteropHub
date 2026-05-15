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
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsInterestService;

public class AdminRegisteredUsersServlet extends HttpServlet {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int DEFAULT_LIMIT = 20;

    private final AuthFlowService authFlowService;
    private final UserDao userDao;
    private final EmailProspectDao emailProspectDao;
    private final EsInterestService esInterestService;

    public AdminRegisteredUsersServlet() {
        this.authFlowService = new AuthFlowService();
        this.userDao = new UserDao();
        this.emailProspectDao = new EmailProspectDao();
        this.esInterestService = new EsInterestService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String search = trimToNull(request.getParameter("search"));
        String linkedParam = trimToNull(request.getParameter("linked"));
        int linkedCount = 0;
        if (linkedParam != null) {
            try {
                linkedCount = Integer.parseInt(linkedParam);
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

        renderPage(response, contextPath, search, linkedCount,
                countRegistered, countActiveLogins, countProspects,
                registrations, activeLogins, prospects);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }
        String action = trimToNull(request.getParameter("action"));
        if ("linkAllProspects".equals(action)) {
            int count = esInterestService.linkAllProspects();
            response.sendRedirect(request.getContextPath() + "/admin/users?linked=" + count);
        } else {
            response.sendRedirect(request.getContextPath() + "/admin/users");
        }
    }

    private void renderPage(HttpServletResponse response, String contextPath, String search, int linkedCount,
            long countRegistered, long countActiveLogins, long countProspects,
            List<User> registrations, List<User> activeLogins,
            List<EmailProspectBrowseRow> prospects) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        boolean isSearch = search != null;

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Registered Users - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Users &amp; Prospects</h2>");

                // --- Search bar ---
                panelOut.println("        <form method=\"get\" action=\"" + contextPath + "/admin/users\">");
                panelOut.println("          <label for=\"search\">Search by email, name, or organization:</label>");
                panelOut.println("          <input type=\"text\" id=\"search\" name=\"search\" value=\""
                        + escapeHtml(search == null ? "" : search) + "\" />");
                panelOut.println("          <button type=\"submit\">Search</button>");
                if (isSearch) {
                    panelOut.println("          <a href=\"" + contextPath + "/admin/users\">Clear</a>");
                }
                panelOut.println("        </form>");

                // --- Stat cards (always totals) ---
                panelOut.println("        <div class=\"stat-cards\">");
                renderStatCard(panelOut, String.valueOf(countRegistered), "Registered Users");
                renderStatCard(panelOut, String.valueOf(countActiveLogins), "Active (last 30 days)");
                renderStatCard(panelOut, String.valueOf(countProspects), "Prospects (email only)");
                panelOut.println("        </div>");

                // --- Success banner after link action ---
                if (linkedCount > 0) {
                    panelOut.println("        <p class=\"success-banner\"><strong>"
                            + escapeHtml(linkedCount + " user" + (linkedCount == 1 ? "" : "s")
                                    + " processed — anonymous records have been linked.")
                            + "</strong></p>");
                }

                // --- Table 1: Registrations ---
                String regHeading = isSearch
                        ? "Registrations matching &ldquo;" + escapeHtml(search) + "&rdquo; (" + registrations.size()
                                + ")"
                        : "Last " + DEFAULT_LIMIT + " Registrations";
                renderRegistrationsTable(panelOut, contextPath, regHeading, registrations);

                // --- Table 2: Active logins (last 30 days) ---
                String loginHeading = isSearch
                        ? "Active Users matching &ldquo;" + escapeHtml(search) + "&rdquo; (" + activeLogins.size() + ")"
                        : "Last " + DEFAULT_LIMIT + " Active Users (30 days)";
                renderActiveLoginsTable(panelOut, contextPath, loginHeading, activeLogins);

                // --- Table 3: Prospects ---
                String prospectHeading = isSearch
                        ? "Prospects matching &ldquo;" + escapeHtml(search) + "&rdquo; (" + prospects.size() + ")"
                        : "Last " + DEFAULT_LIMIT + " Prospects (not yet registered)";

                // --- Link All Prospects button ---
                panelOut.println("        <form method=\"post\" action=\"" + contextPath
                        + "/admin/users\" style=\"margin: 1rem 0;\">");
                panelOut.println("          <input type=\"hidden\" name=\"action\" value=\"linkAllProspects\" />");
                panelOut.println("          <button type=\"submit\">Link All Prospects to Registered Users</button>");
                panelOut.println("        </form>");

                renderProspectsTable(panelOut, prospectHeading, prospects);

                panelOut.println("      </section>");
            });
        }
    }

    private void renderStatCard(PrintWriter out, String value, String label) {
        out.println("          <div class=\"stat-card\">");
        out.println("            <span class=\"stat-card-value\">" + escapeHtml(value) + "</span>");
        out.println("            <span class=\"stat-card-label\">" + escapeHtml(label) + "</span>");
        out.println("          </div>");
    }

    private void renderRegistrationsTable(PrintWriter out, String contextPath, String heading, List<User> users) {
        out.println("        <h3>" + heading + "</h3>");
        if (users.isEmpty()) {
            out.println("        <p>No registrations found.</p>");
            return;
        }
        out.println("        <table class=\"data-table\">");
        out.println("          <thead><tr>");
        out.println("            <th>Name</th><th>Email</th><th>Organization</th><th>Status</th><th>Registered</th>");
        out.println("          </tr></thead>");
        out.println("          <tbody>");
        for (User user : users) {
            String link = contextPath + "/admin/users/detail?userId=" + user.getUserId();
            String name = trimToNull(user.getFullName());
            if (name == null)
                name = orEmpty(user.getEmail());
            out.println("            <tr>");
            out.println("              <td><a href=\"" + link + "\">" + escapeHtml(name) + "</a></td>");
            out.println("              <td>" + escapeHtml(orEmpty(user.getEmail())) + "</td>");
            out.println("              <td>" + escapeHtml(orEmpty(user.getOrganization())) + "</td>");
            out.println("              <td>" + escapeHtml(user.getStatus() == null ? "" : user.getStatus().name())
                    + "</td>");
            out.println("              <td>" + formatDateTime(user.getCreatedAt()) + "</td>");
            out.println("            </tr>");
        }
        out.println("          </tbody></table>");
    }

    private void renderActiveLoginsTable(PrintWriter out, String contextPath, String heading, List<User> users) {
        out.println("        <h3>" + heading + "</h3>");
        if (users.isEmpty()) {
            out.println("        <p>No active users found.</p>");
            return;
        }
        out.println("        <table class=\"data-table\">");
        out.println("          <thead><tr>");
        out.println("            <th>Name</th><th>Email</th><th>Organization</th><th>Last Login</th>");
        out.println("          </tr></thead>");
        out.println("          <tbody>");
        for (User user : users) {
            String link = contextPath + "/admin/users/detail?userId=" + user.getUserId();
            String name = trimToNull(user.getFullName());
            if (name == null)
                name = orEmpty(user.getEmail());
            out.println("            <tr>");
            out.println("              <td><a href=\"" + link + "\">" + escapeHtml(name) + "</a></td>");
            out.println("              <td>" + escapeHtml(orEmpty(user.getEmail())) + "</td>");
            out.println("              <td>" + escapeHtml(orEmpty(user.getOrganization())) + "</td>");
            out.println("              <td>" + formatDateTime(user.getLastLoginAt()) + "</td>");
            out.println("            </tr>");
        }
        out.println("          </tbody></table>");
    }

    private void renderProspectsTable(PrintWriter out, String heading, List<EmailProspectBrowseRow> prospects) {
        out.println("        <h3>" + heading + "</h3>");
        if (prospects.isEmpty()) {
            out.println("        <p>No prospects found.</p>");
            return;
        }
        out.println("        <table class=\"data-table\">");
        out.println("          <thead><tr>");
        out.println("            <th>Email</th><th>First Contact</th><th>Last Contact</th><th>Sources</th>");
        out.println("          </tr></thead>");
        out.println("          <tbody>");
        for (EmailProspectBrowseRow row : prospects) {
            out.println("            <tr>");
            out.println("              <td>" + escapeHtml(row.getEmailNormalized()) + "</td>");
            out.println("              <td>" + formatDateTime(row.getFirstContactAt()) + "</td>");
            out.println("              <td>" + formatDateTime(row.getLastContactAt()) + "</td>");
            out.println("              <td>" + buildSourcesLabel(row) + "</td>");
            out.println("            </tr>");
        }
        out.println("          </tbody></table>");
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

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }

        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            renderForbidden(response, request.getContextPath());
            return Optional.empty();
        }

        return authenticatedUser;
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            AdminShellRenderer.render(out, "Access Denied - InteropHub", contextPath, panelOut -> {
                panelOut.println("      <section class=\"panel\">");
                panelOut.println("        <h2>Access Denied</h2>");
                panelOut.println(
                        "        <p>You must be an InteropHub admin to access registered user information.</p>");
                panelOut.println("        <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
                panelOut.println("      </section>");
            });
        }
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
