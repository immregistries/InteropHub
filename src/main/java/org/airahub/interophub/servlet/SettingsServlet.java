package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.HubSettingDao;
import org.airahub.interophub.model.HubSetting;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EmailService;

public class SettingsServlet extends HttpServlet {
    private final AuthFlowService authFlowService;
    private final HubSettingDao hubSettingDao;
    private final EmailService emailService;

    public SettingsServlet() {
        this.authFlowService = new AuthFlowService();
        this.hubSettingDao = new HubSettingDao();
        this.emailService = new EmailService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!ensureAdminAccess(request, response)) {
            return;
        }

        HubSetting settings = loadSettings();
        renderForm(response, request.getContextPath(), settings, null, false, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!ensureAdminAccess(request, response)) {
            return;
        }

        User currentUser = authFlowService.findAuthenticatedUser(request).orElseThrow();

        HubSetting settings = loadSettings();
        String errorMessage = null;
        boolean saved = false;
        String testEmailMessage = null;

        try {
            populateFromRequest(settings, request);
            settings = hubSettingDao.saveOrUpdate(settings);
            saved = true;
        } catch (Exception ex) {
            errorMessage = ex.getMessage();
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "Could not save settings.";
            }
        }

        if (saved && isChecked(request, "sendTestEmail")) {
            String targetEmail = currentUser.getEmailNormalized() != null
                    ? currentUser.getEmailNormalized()
                    : currentUser.getEmail();
            try {
                emailService.sendTestEmail(targetEmail);
                testEmailMessage = "Test email sent successfully to " + targetEmail + ".";
            } catch (Exception ex) {
                testEmailMessage = "Settings saved, but test email failed: " + ex.getMessage();
            }
        }

        renderForm(response, request.getContextPath(), settings, errorMessage, saved, testEmailMessage);
    }

    private boolean ensureAdminAccess(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return false;
        }

        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            renderForbidden(response, request.getContextPath());
            return false;
        }

        return true;
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
            out.println("    <p>You must be an InteropHub admin to access settings.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private HubSetting loadSettings() {
        return hubSettingDao.findActive()
                .or(() -> hubSettingDao.findFirst())
                .orElseGet(this::createDefaultSettings);
    }

    private HubSetting createDefaultSettings() {
        HubSetting settings = new HubSetting();
        settings.setActive(Boolean.TRUE);
        settings.setExternalBaseUrl("http://localhost:8080/hub");
        settings.setSmtpHost("sandbox.smtp.mailtrap.io");
        settings.setSmtpPort(2525);
        settings.setSmtpUsername("");
        settings.setSmtpPassword("");
        settings.setSmtpAuth(Boolean.TRUE);
        settings.setSmtpStarttls(Boolean.TRUE);
        settings.setSmtpSsl(Boolean.FALSE);
        settings.setSmtpFromEmail("no-reply@interophub.local");
        settings.setSmtpFromName("InteropHub");
        return settings;
    }

    private void populateFromRequest(HubSetting settings, HttpServletRequest request) {
        settings.setActive(isChecked(request, "active"));
        settings.setExternalBaseUrl(required(request.getParameter("externalBaseUrl"), "External base URL"));
        settings.setSmtpHost(required(request.getParameter("smtpHost"), "SMTP host"));
        settings.setSmtpPort(parsePort(request.getParameter("smtpPort")));
        settings.setSmtpUsername(required(request.getParameter("smtpUsername"), "SMTP username"));
        settings.setSmtpPassword(required(request.getParameter("smtpPassword"), "SMTP password"));
        settings.setSmtpAuth(isChecked(request, "smtpAuth"));
        settings.setSmtpStarttls(isChecked(request, "smtpStarttls"));
        settings.setSmtpSsl(isChecked(request, "smtpSsl"));
        settings.setSmtpFromEmail(required(request.getParameter("smtpFromEmail"), "SMTP from email"));
        settings.setSmtpFromName(required(request.getParameter("smtpFromName"), "SMTP from name"));
    }

    private Integer parsePort(String rawPort) {
        String value = required(rawPort, "SMTP port");
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("SMTP port must be between 1 and 65535.");
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("SMTP port must be a number.");
        }
    }

    private boolean isChecked(HttpServletRequest request, String fieldName) {
        return request.getParameter(fieldName) != null;
    }

    private String required(String rawValue, String label) {
        if (rawValue == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    private void renderForm(HttpServletResponse response, String contextPath, HubSetting settings, String errorMessage,
            boolean saved, String testEmailMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>InteropHub Settings</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Hub Settings</h1>");
            out.println("    <p>Edit SMTP and external URL values for email sending.</p>");

            if (saved) {
                out.println("    <p><strong>Settings saved.</strong></p>");
            }
            if (errorMessage != null) {
                out.println("    <p><strong>Save failed:</strong> " + escapeHtml(errorMessage) + "</p>");
            }
            if (testEmailMessage != null) {
                out.println("    <p><strong>Test email:</strong> " + escapeHtml(testEmailMessage) + "</p>");
            }

            out.println("    <form class=\"login-form\" action=\"" + contextPath + "/settings\" method=\"post\">");
            out.println("      <label><input type=\"checkbox\" name=\"active\""
                    + checked(settings.getActive()) + " /> Active</label>");
            out.println("      <label for=\"externalBaseUrl\">External Base URL</label>");
            out.println("      <input id=\"externalBaseUrl\" name=\"externalBaseUrl\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(settings.getExternalBaseUrl())) + "\" />");

            out.println("      <label for=\"smtpHost\">SMTP Host</label>");
            out.println("      <input id=\"smtpHost\" name=\"smtpHost\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(settings.getSmtpHost())) + "\" />");

            out.println("      <label for=\"smtpPort\">SMTP Port</label>");
            out.println(
                    "      <input id=\"smtpPort\" name=\"smtpPort\" type=\"number\" min=\"1\" max=\"65535\" value=\""
                            + escapeHtml(String.valueOf(settings.getSmtpPort() == null ? 2525 : settings.getSmtpPort()))
                            + "\" />");

            out.println("      <label for=\"smtpUsername\">SMTP Username</label>");
            out.println("      <input id=\"smtpUsername\" name=\"smtpUsername\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(settings.getSmtpUsername())) + "\" />");

            out.println("      <label for=\"smtpPassword\">SMTP Password</label>");
            out.println("      <input id=\"smtpPassword\" name=\"smtpPassword\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(settings.getSmtpPassword())) + "\" />");

            out.println("      <label><input type=\"checkbox\" name=\"smtpAuth\""
                    + checked(settings.getSmtpAuth()) + " /> SMTP Auth</label>");
            out.println("      <label><input type=\"checkbox\" name=\"smtpStarttls\""
                    + checked(settings.getSmtpStarttls()) + " /> SMTP STARTTLS</label>");
            out.println("      <label><input type=\"checkbox\" name=\"smtpSsl\""
                    + checked(settings.getSmtpSsl()) + " /> SMTP SSL</label>");

            out.println("      <label for=\"smtpFromEmail\">From Email</label>");
            out.println("      <input id=\"smtpFromEmail\" name=\"smtpFromEmail\" type=\"email\" value=\""
                    + escapeHtml(orEmpty(settings.getSmtpFromEmail())) + "\" />");

            out.println("      <label for=\"smtpFromName\">From Name</label>");
            out.println("      <input id=\"smtpFromName\" name=\"smtpFromName\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(settings.getSmtpFromName())) + "\" />");

            out.println("      <label><input type=\"checkbox\" name=\"sendTestEmail\" />"
                    + " Send test email to my address after saving</label>");
            out.println("      <button type=\"submit\">Save Settings</button>");
            out.println("    </form>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private String checked(Boolean value) {
        return Boolean.TRUE.equals(value) ? " checked" : "";
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
