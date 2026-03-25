package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.AppApiDao;
import org.airahub.interophub.dao.AppApiSecretDao;
import org.airahub.interophub.dao.AppRegistryDao;
import org.airahub.interophub.model.AppApi;
import org.airahub.interophub.model.AppApiSecret;
import org.airahub.interophub.model.AppRegistry;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AppAccessServlet extends HttpServlet {
    private static final String SECRET_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int SECRET_RANDOM_LENGTH = 16;

    private final AuthFlowService authFlowService;
    private final AppRegistryDao appRegistryDao;
    private final AppApiDao appApiDao;
    private final AppApiSecretDao appApiSecretDao;
    private final SecureRandom secureRandom;

    public AppAccessServlet() {
        this.authFlowService = new AuthFlowService();
        this.appRegistryDao = new AppRegistryDao();
        this.appApiDao = new AppApiDao();
        this.appApiSecretDao = new AppApiSecretDao();
        this.secureRandom = new SecureRandom();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String appIdRaw = trimToNull(request.getParameter("appId"));
        Long appId = parseId(appIdRaw);
        if (appId == null) {
            renderNotFound(response, request.getContextPath(), "A valid application id is required.");
            return;
        }

        AppRegistry app = appRegistryDao.findById(appId).orElse(null);
        if (app == null || !Boolean.TRUE.equals(app.getEnabled()) || Boolean.TRUE.equals(app.getKillSwitch())) {
            renderNotFound(response, request.getContextPath(), "That application is not currently available.");
            return;
        }

        List<AppApi> appApis = appApiDao.findByAppId(appId);
        List<AppApiSecret> secrets = appApiSecretDao.findByUserIdAndAppId(authenticatedUser.get().getUserId(), appId);
        boolean generatedSecrets = false;

        if (secrets.isEmpty() && !appApis.isEmpty()) {
            for (AppApi appApi : appApis) {
                AppApiSecret secret = new AppApiSecret();
                secret.setApiId(appApi.getApiId());
                secret.setUserId(authenticatedUser.get().getUserId());
                secret.setLabel("Auto-generated");
                secret.setSecretValue(generateReadableSecret(app, appApi));
                appApiSecretDao.save(secret);
            }
            secrets = appApiSecretDao.findByUserIdAndAppId(authenticatedUser.get().getUserId(), appId);
            generatedSecrets = true;
        }

        Map<Long, AppApi> apiById = new HashMap<>();
        for (AppApi api : appApis) {
            apiById.put(api.getApiId(), api);
        }

        List<SecretDisplayRow> displayRows = new ArrayList<>();
        for (AppApiSecret secret : secrets) {
            AppApi purpose = apiById.get(secret.getApiId());
            displayRows.add(new SecretDisplayRow(secret, purpose));
        }

        renderPage(response, request.getContextPath(), app, displayRows, generatedSecrets);
    }

    private void renderPage(HttpServletResponse response, String contextPath, AppRegistry app,
            List<SecretDisplayRow> displayRows, boolean generatedSecrets) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Application Access - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <section class=\"panel\">");
            out.println("      <img class=\"banner\" src=\"" + contextPath
                    + "/image/Splashpage_connectathon.png\" alt=\"Developers collaborating on connectathon work\" />");
            out.println("    </section>");
            out.println("    <h1>Application Access</h1>");
            out.println("    <section class=\"panel\">");
            out.println("      <h2>Application Details</h2>");
            out.println("      <p><strong>Name:</strong> " + escapeHtml(orEmpty(app.getAppName())) + "</p>");
            out.println(
                    "      <p><strong>Description:</strong> " + escapeHtml(orEmpty(app.getAppDescription())) + "</p>");
            out.println("      <p><strong>Managed By:</strong> "
                    + escapeHtml(app.getManagedBy() == null ? "" : app.getManagedBy().name()) + "</p>");

            String redirectUrl = trimToNull(app.getDefaultRedirectUrl());
            if (redirectUrl != null) {
                out.println("      <p><a href=\"" + escapeHtml(redirectUrl)
                        + "\">Allow Access</a></p>");
            } else {
                out.println("      <p>Allow Access link is not configured for this application.</p>");
            }
            out.println("    </section>");

            out.println("    <section class=\"panel\">");
            out.println("      <h2>API Secrets</h2>");
            out.println(
                    "      <p>These test secrets are generated per user and per purpose for sandbox API tracking.</p>");
            if (generatedSecrets) {
                out.println("      <p><strong>New secrets were generated for this application.</strong></p>");
            }

            if (displayRows.isEmpty()) {
                out.println("      <p>No API secrets are available for this application.</p>");
            } else {
                out.println("      <table class=\"data-table\">");
                out.println("        <thead>");
                out.println("          <tr>");
                out.println("            <th>Purpose</th>");
                out.println("            <th>Enabled</th>");
                out.println("            <th>Secret</th>");
                out.println("          </tr>");
                out.println("        </thead>");
                out.println("        <tbody>");
                for (SecretDisplayRow row : displayRows) {
                    out.println("          <tr>");
                    out.println("            <td>" + escapeHtml(orEmpty(row.purposeLabel())) + "</td>");
                    out.println("            <td>" + enabledIcon(row.enabled()) + "</td>");
                    out.println("            <td><code>" + escapeHtml(orEmpty(row.secretValue())) + "</code></td>");
                    out.println("          </tr>");
                }
                out.println("        </tbody>");
                out.println("      </table>");
            }
            out.println("    </section>");

            out.println("    <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderNotFound(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Application Not Found - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Application Unavailable</h1>");
            out.println("    <p>" + escapeHtml(message) + "</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Back to Welcome</a></p>");
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

    private String generateReadableSecret(AppRegistry app, AppApi appApi) {
        String appPrefix = prefixFromText(app.getAppCode(), 4);
        String apiPrefix = prefixFromText(appApi.getApiCode(), 4);

        StringBuilder randomPart = new StringBuilder(SECRET_RANDOM_LENGTH);
        for (int i = 0; i < SECRET_RANDOM_LENGTH; i++) {
            int index = secureRandom.nextInt(SECRET_ALPHABET.length());
            randomPart.append(SECRET_ALPHABET.charAt(index));
        }

        return appPrefix + apiPrefix + randomPart;
    }

    private String prefixFromText(String value, int length) {
        StringBuilder normalized = new StringBuilder();
        if (value != null) {
            String upper = value.toUpperCase();
            for (int i = 0; i < upper.length(); i++) {
                char ch = upper.charAt(i);
                boolean alphaNum = (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9');
                if (alphaNum) {
                    normalized.append(ch);
                }
            }
        }

        while (normalized.length() < length) {
            normalized.append('X');
        }

        if (normalized.length() > length) {
            return normalized.substring(0, length);
        }
        return normalized.toString();
    }

    private Long parseId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
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

    private record SecretDisplayRow(AppApiSecret secret, AppApi appApi) {
        private String purposeLabel() {
            if (appApi != null && appApi.getPurposeLabel() != null && !appApi.getPurposeLabel().isBlank()) {
                return appApi.getPurposeLabel();
            }
            return "(Unknown Purpose)";
        }

        private boolean enabled() {
            return appApi != null && Boolean.TRUE.equals(appApi.getEnabled());
        }

        private String secretValue() {
            return secret.getSecretValue();
        }
    }
}