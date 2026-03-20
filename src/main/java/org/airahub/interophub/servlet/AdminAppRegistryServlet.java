package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.AppApiDao;
import org.airahub.interophub.dao.AppRedirectAllowlistDao;
import org.airahub.interophub.dao.AppRegistryDao;
import org.airahub.interophub.model.AppApi;
import org.airahub.interophub.model.AppRedirectAllowlist;
import org.airahub.interophub.model.AppRegistry;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminAppRegistryServlet extends HttpServlet {
    private final AuthFlowService authFlowService;
    private final AppApiDao appApiDao;
    private final AppRedirectAllowlistDao appRedirectAllowlistDao;
    private final AppRegistryDao appRegistryDao;

    public AdminAppRegistryServlet() {
        this.authFlowService = new AuthFlowService();
        this.appApiDao = new AppApiDao();
        this.appRedirectAllowlistDao = new AppRedirectAllowlistDao();
        this.appRegistryDao = new AppRegistryDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String appIdRaw = trimToNull(request.getParameter("appId"));
        boolean createNew = "new".equalsIgnoreCase(request.getParameter("mode"));

        if (createNew) {
            renderForm(response, request.getContextPath(), new AppRegistry(), new ArrayList<>(), new ArrayList<>(),
                    null,
                    true, null, null, true);
            return;
        }

        if (appIdRaw != null) {
            Long appId = parseId(appIdRaw);
            if (appId == null) {
                renderList(response, request.getContextPath(), "Invalid app identifier.");
                return;
            }

            AppRegistry appRegistry = appRegistryDao.findById(appId).orElse(null);
            if (appRegistry == null) {
                renderList(response, request.getContextPath(), "App entry was not found.");
                return;
            }

            renderForm(
                    response,
                    request.getContextPath(),
                    appRegistry,
                    appRedirectAllowlistDao.findByAppId(appRegistry.getAppId()),
                    appApiDao.findByAppId(appRegistry.getAppId()),
                    null,
                    false,
                    null,
                    null,
                    true);
            return;
        }

        String message = request.getParameter("saved") != null ? "App settings saved." : null;
        renderList(response, request.getContextPath(), message);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String appIdRaw = trimToNull(request.getParameter("appId"));

        boolean createNew = appIdRaw == null;
        AppRegistry appRegistry;
        if (createNew) {
            appRegistry = new AppRegistry();
            appRegistry.setKillSwitch(Boolean.FALSE);
        } else {
            Long appId = parseId(appIdRaw);
            if (appId == null) {
                renderList(response, contextPath, "Invalid app identifier.");
                return;
            }
            appRegistry = appRegistryDao.findById(appId).orElse(null);
            if (appRegistry == null) {
                renderList(response, contextPath, "App entry was not found.");
                return;
            }
        }

        String appCode = trimToNull(request.getParameter("appCode"));
        String appName = trimToNull(request.getParameter("appName"));
        String managedBy = trimToNull(request.getParameter("managedBy"));
        String defaultRedirectUrl = trimToNull(request.getParameter("defaultRedirectUrl"));
        String appDescription = trimToNull(request.getParameter("appDescription"));
        String newAllowBaseUrl = trimToNull(request.getParameter("newAllowBaseUrl"));
        String newPurposeLabel = trimToNull(request.getParameter("newPurposeLabel"));
        boolean newPurposeEnabled = request.getParameter("newPurposeEnabled") != null;
        boolean enabled = request.getParameter("enabled") != null;

        try {
            appRegistry.setAppCode(required(appCode, "App code"));
            appRegistry.setAppName(required(appName, "App name"));
            appRegistry.setDefaultRedirectUrl(required(defaultRedirectUrl, "Default redirect URL"));
            appRegistry.setManagedBy(parseManagedBy(required(managedBy, "Managed By")));
            appRegistry.setAppDescription(appDescription);
            appRegistry.setEnabled(enabled);

            appRegistry = appRegistryDao.saveOrUpdate(appRegistry);
            upsertAllowlistEntries(request, appRegistry.getAppId());
            upsertAppApiEntries(request, appRegistry.getAppId());

            if (newAllowBaseUrl != null) {
                AppRedirectAllowlist newEntry = new AppRedirectAllowlist();
                newEntry.setAppId(appRegistry.getAppId());
                newEntry.setBaseUrl(newAllowBaseUrl);
                newEntry.setEnabled(Boolean.TRUE);
                appRedirectAllowlistDao.save(newEntry);
            }

            if (newPurposeLabel != null) {
                AppApi newPurpose = new AppApi();
                newPurpose.setAppId(appRegistry.getAppId());
                newPurpose.setPurposeLabel(newPurposeLabel);
                newPurpose.setApiCode(generateUniqueApiCode(appRegistry.getAppId(), newPurposeLabel));
                newPurpose.setEnabled(newPurposeEnabled);
                appApiDao.save(newPurpose);
            }

            response.sendRedirect(contextPath + "/admin/apps?saved=1");
        } catch (Exception ex) {
            renderForm(
                    response,
                    contextPath,
                    appRegistry,
                    appRegistry.getAppId() == null ? new ArrayList<>()
                            : appRedirectAllowlistDao.findByAppId(appRegistry.getAppId()),
                    appRegistry.getAppId() == null ? new ArrayList<>() : appApiDao.findByAppId(appRegistry.getAppId()),
                    ex.getMessage(),
                    createNew,
                    newAllowBaseUrl,
                    newPurposeLabel,
                    newPurposeEnabled);
        }
    }

    private void upsertAllowlistEntries(HttpServletRequest request, Long appId) {
        String[] allowIds = request.getParameterValues("allowId");
        if (allowIds == null) {
            return;
        }

        for (String allowIdRaw : allowIds) {
            Long allowId = parseId(allowIdRaw);
            if (allowId == null) {
                throw new IllegalArgumentException("Invalid allowlist identifier.");
            }

            AppRedirectAllowlist existing = appRedirectAllowlistDao.findById(allowId)
                    .orElseThrow(() -> new IllegalArgumentException("Allowlist entry not found."));
            if (!appId.equals(existing.getAppId())) {
                throw new IllegalArgumentException("Allowlist entry does not match the selected app.");
            }

            String baseUrl = required(trimToNull(request.getParameter("allowBaseUrl_" + allowId)), "Base URL");
            boolean entryEnabled = request.getParameter("allowEnabled_" + allowId) != null;

            existing.setBaseUrl(baseUrl);
            existing.setEnabled(entryEnabled);
            appRedirectAllowlistDao.saveOrUpdate(existing);
        }
    }

    private void upsertAppApiEntries(HttpServletRequest request, Long appId) {
        String[] apiIds = request.getParameterValues("apiId");
        if (apiIds == null) {
            return;
        }

        for (String apiIdRaw : apiIds) {
            Long apiId = parseId(apiIdRaw);
            if (apiId == null) {
                throw new IllegalArgumentException("Invalid API purpose identifier.");
            }

            AppApi existing = appApiDao.findById(apiId)
                    .orElseThrow(() -> new IllegalArgumentException("API purpose entry not found."));
            if (!appId.equals(existing.getAppId())) {
                throw new IllegalArgumentException("API purpose entry does not match the selected app.");
            }

            String purposeLabel = required(trimToNull(request.getParameter("purposeLabel_" + apiId)), "Purpose");
            boolean entryEnabled = request.getParameter("purposeEnabled_" + apiId) != null;

            existing.setPurposeLabel(purposeLabel);
            existing.setEnabled(entryEnabled);
            appApiDao.saveOrUpdate(existing);
        }
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

    private void renderList(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        List<AppRegistry> apps = appRegistryDao.findAllOrdered();

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>App Registry Admin - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>App Registry</h1>");
            out.println("    <p>Manage central app definitions and enable or disable access.</p>");
            if (message != null && !message.isBlank()) {
                out.println("    <p><strong>" + escapeHtml(message) + "</strong></p>");
            }
            out.println("    <table class=\"data-table\">");
            out.println("      <thead>");
            out.println("        <tr>");
            out.println("          <th>App Code</th>");
            out.println("          <th>App Name</th>");
            out.println("          <th>Managed By</th>");
            out.println("          <th>Enabled</th>");
            out.println("          <th>Action</th>");
            out.println("        </tr>");
            out.println("      </thead>");
            out.println("      <tbody>");
            for (AppRegistry app : apps) {
                out.println("        <tr>");
                out.println("          <td>" + escapeHtml(orEmpty(app.getAppCode())) + "</td>");
                out.println("          <td>" + escapeHtml(orEmpty(app.getAppName())) + "</td>");
                out.println("          <td>" + escapeHtml(app.getManagedBy() == null ? "" : app.getManagedBy().name())
                        + "</td>");
                out.println("          <td>" + enabledIcon(Boolean.TRUE.equals(app.getEnabled())) + "</td>");
                out.println("          <td><a href=\"" + contextPath + "/admin/apps?appId=" + app.getAppId()
                        + "\">Edit</a></td>");
                out.println("        </tr>");
            }
            if (apps.isEmpty()) {
                out.println("        <tr>");
                out.println("          <td colspan=\"5\">No app entries yet.</td>");
                out.println("        </tr>");
            }
            out.println("      </tbody>");
            out.println("    </table>");

            out.println("    <section class=\"panel\">");
            out.println("      <h2>Create New App</h2>");
            out.println(
                    "      <p><a href=\"" + contextPath + "/admin/apps?mode=new\">Add New App Registry Entry</a></p>");
            out.println("    </section>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderForm(HttpServletResponse response, String contextPath, AppRegistry appRegistry,
            List<AppRedirectAllowlist> allowlistEntries, List<AppApi> apiEntries, String errorMessage,
            boolean createNew, String newAllowBaseUrl, String newPurposeLabel, boolean newPurposeEnabled)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>" + (createNew ? "Create App" : "Edit App") + " - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>" + (createNew ? "Create App Registry Entry" : "Edit App Registry Entry") + "</h1>");
            out.println("    <p>Fields marked required must be provided before saving.</p>");

            if (errorMessage != null && !errorMessage.isBlank()) {
                out.println("    <p><strong>Could not save:</strong> " + escapeHtml(errorMessage) + "</p>");
            }

            out.println("    <form class=\"login-form\" action=\"" + contextPath + "/admin/apps\" method=\"post\">");
            if (!createNew && appRegistry.getAppId() != null) {
                out.println("      <input type=\"hidden\" name=\"appId\" value=\"" + appRegistry.getAppId() + "\" />");
            }

            out.println("      <label for=\"appCode\">App Code (required)</label>");
            out.println("      <input id=\"appCode\" name=\"appCode\" type=\"text\" required value=\""
                    + escapeHtml(orEmpty(appRegistry.getAppCode())) + "\" />");

            out.println("      <label for=\"appName\">App Name (required)</label>");
            out.println("      <input id=\"appName\" name=\"appName\" type=\"text\" required value=\""
                    + escapeHtml(orEmpty(appRegistry.getAppName())) + "\" />");

            out.println("      <label for=\"managedBy\">Managed By (required)</label>");
            out.println("      <select id=\"managedBy\" name=\"managedBy\" required>");
            out.println("        <option value=\"\">Choose one...</option>");
            out.println("        <option value=\"AIRA\"" + selectedManagedBy(appRegistry, AppRegistry.ManagedBy.AIRA)
                    + ">AIRA</option>");
            out.println("        <option value=\"THIRD_PARTY\""
                    + selectedManagedBy(appRegistry, AppRegistry.ManagedBy.THIRD_PARTY) + ">THIRD_PARTY</option>");
            out.println("      </select>");

            out.println("      <label for=\"defaultRedirectUrl\">Default Redirect URL (required)</label>");
            out.println(
                    "      <input id=\"defaultRedirectUrl\" name=\"defaultRedirectUrl\" type=\"url\" required value=\""
                            + escapeHtml(orEmpty(appRegistry.getDefaultRedirectUrl())) + "\" />");

            out.println("      <label for=\"appDescription\">App Description</label>");
            out.println("      <textarea id=\"appDescription\" name=\"appDescription\" rows=\"5\">"
                    + escapeHtml(orEmpty(appRegistry.getAppDescription())) + "</textarea>");

            out.println("      <label><input type=\"checkbox\" name=\"enabled\""
                    + (Boolean.TRUE.equals(appRegistry.getEnabled()) || createNew ? " checked" : "")
                    + " /> Enabled</label>");

            out.println("      <h2>Allowed Redirect Base URLs</h2>");
            out.println("      <p>Edit existing entries or disable them. Deletion is not supported.</p>");

            if (allowlistEntries.isEmpty()) {
                out.println("      <p>No allowlist URLs yet.</p>");
            } else {
                for (AppRedirectAllowlist entry : allowlistEntries) {
                    out.println(
                            "      <input type=\"hidden\" name=\"allowId\" value=\"" + entry.getAllowId() + "\" />");

                    out.println("      <label for=\"allowBaseUrl_" + entry.getAllowId() + "\">Base URL</label>");
                    out.println("      <input id=\"allowBaseUrl_" + entry.getAllowId() + "\" name=\"allowBaseUrl_"
                            + entry.getAllowId() + "\" type=\"url\" required value=\""
                            + escapeHtml(orEmpty(entry.getBaseUrl())) + "\" />");

                    out.println("      <label><input type=\"checkbox\" name=\"allowEnabled_" + entry.getAllowId() + "\""
                            + (Boolean.TRUE.equals(entry.getEnabled()) ? " checked" : "")
                            + " /> Enabled</label>");
                }
            }

            out.println("      <label for=\"newAllowBaseUrl\">Add New Base URL (optional)</label>");
            out.println("      <input id=\"newAllowBaseUrl\" name=\"newAllowBaseUrl\" type=\"url\" value=\""
                    + escapeHtml(orEmpty(newAllowBaseUrl)) + "\" />");
            out.println("      <p>Any new URL is added as enabled by default.</p>");

            out.println("      <h2>API Access</h2>");
            out.println("      <p>Edit existing purposes or disable them. Deletion is not supported.</p>");

            if (apiEntries.isEmpty()) {
                out.println("      <p>No API purposes yet.</p>");
            } else {
                for (AppApi entry : apiEntries) {
                    out.println("      <input type=\"hidden\" name=\"apiId\" value=\"" + entry.getApiId() + "\" />");

                    out.println("      <label for=\"purposeLabel_" + entry.getApiId() + "\">Purpose</label>");
                    out.println("      <input id=\"purposeLabel_" + entry.getApiId() + "\" name=\"purposeLabel_"
                            + entry.getApiId() + "\" type=\"text\" required value=\""
                            + escapeHtml(orEmpty(entry.getPurposeLabel())) + "\" />");

                    out.println("      <label><input type=\"checkbox\" name=\"purposeEnabled_" + entry.getApiId()
                            + "\"" + (Boolean.TRUE.equals(entry.getEnabled()) ? " checked" : "")
                            + " /> Enabled</label>");
                }
            }

            out.println("      <label for=\"newPurposeLabel\">Purpose</label>");
            out.println("      <input id=\"newPurposeLabel\" name=\"newPurposeLabel\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(newPurposeLabel)) + "\" />");
            out.println("      <label><input type=\"checkbox\" name=\"newPurposeEnabled\""
                    + (newPurposeEnabled ? " checked" : "") + " /> Enabled</label>");
            out.println("      <p>If Purpose is blank, no new API purpose is added.</p>");

            out.println("      <button type=\"submit\">Save</button>");
            out.println("    </form>");
            out.println("    <p><a href=\"" + contextPath + "/admin/apps\">Back to App Registry</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Access Denied - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Access Denied</h1>");
            out.println("    <p>You must be an InteropHub admin to access app registry settings.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private String enabledIcon(boolean enabled) {
        if (enabled) {
            return "<span class=\"status-icon status-enabled\" title=\"Enabled\">&#10004; Enabled</span>";
        }
        return "<span class=\"status-icon status-disabled\" title=\"Disabled\">&#10008; Disabled</span>";
    }

    private String generateUniqueApiCode(Long appId, String purposeLabel) {
        String baseCode = slugifyPurposeLabel(purposeLabel);
        String candidate = baseCode;
        int suffix = 2;

        while (appApiDao.findByAppIdAndApiCode(appId, candidate).isPresent()) {
            candidate = baseCode + "-" + suffix;
            suffix++;
        }

        return candidate;
    }

    private String slugifyPurposeLabel(String purposeLabel) {
        if (purposeLabel == null) {
            return "purpose";
        }

        String lower = purposeLabel.trim().toLowerCase();
        StringBuilder out = new StringBuilder();
        boolean previousDash = false;
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            boolean alphaNum = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
            if (alphaNum) {
                out.append(ch);
                previousDash = false;
            } else if (!previousDash) {
                out.append('-');
                previousDash = true;
            }
        }

        String normalized = out.toString().replaceAll("^-+", "").replaceAll("-+$", "");
        if (normalized.isEmpty()) {
            return "purpose";
        }
        if (normalized.length() > 80) {
            return normalized.substring(0, 80);
        }
        return normalized;
    }

    private String selectedManagedBy(AppRegistry appRegistry, AppRegistry.ManagedBy managedBy) {
        return appRegistry.getManagedBy() == managedBy ? " selected" : "";
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AppRegistry.ManagedBy parseManagedBy(String value) {
        try {
            return AppRegistry.ManagedBy.valueOf(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Managed By must be either AIRA or THIRD_PARTY.");
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
