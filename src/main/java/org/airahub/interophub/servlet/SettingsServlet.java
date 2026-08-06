package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.HubSettingDao;
import org.airahub.interophub.model.HubSetting;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.EmailService;

public class SettingsServlet extends HttpServlet {
    private final HubSettingDao hubSettingDao;
    private final EmailService emailService;

    public SettingsServlet() {
        this.hubSettingDao = new HubSettingDao();
        this.emailService = new EmailService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (AdminAccessGuard.requireAdmin(request, response).isEmpty()) {
            return;
        }

        HubSetting settings = loadSettings();
        renderForm(request, response, settings, null, false, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Optional<User> adminUser = AdminAccessGuard.requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        User currentUser = adminUser.get();

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

        renderForm(request, response, settings, errorMessage, saved, testEmailMessage);
    }

    private HubSetting loadSettings() {
        return hubSettingDao.findActive()
                .or(() -> hubSettingDao.findFirst())
                .orElseGet(this::createDefaultSettings);
    }

    private HubSetting createDefaultSettings() {
        HubSetting settings = new HubSetting();
        settings.setActive(Boolean.TRUE);
        settings.setEmailEnabled(Boolean.TRUE);
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
        settings.setEmailEnabled(isChecked(request, "emailEnabled"));
        settings.setExternalBaseUrl(required(request.getParameter("externalBaseUrl"), "External base URL"));
        settings.setSmtpHost(required(request.getParameter("smtpHost"), "SMTP host"));
        settings.setSmtpPort(parsePort(request.getParameter("smtpPort")));
        settings.setSmtpUsername(trimOrEmpty(request.getParameter("smtpUsername")));
        settings.setSmtpPassword(trimOrEmpty(request.getParameter("smtpPassword")));
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

    private String trimOrEmpty(String rawValue) {
        return rawValue == null ? "" : rawValue.trim();
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

    private void renderForm(HttpServletRequest request, HttpServletResponse response, HubSetting settings,
            String errorMessage, boolean saved, String testEmailMessage) throws IOException {
        String contextPath = request.getContextPath();
        AdminShellRenderer.render(request, response, "InteropHub Settings", AdminSection.PLATFORM, "/admin/settings",
                out -> {
                    out.println("          <section class=\"aira-panel\">");
                    out.println("            <h2 class=\"aira-section-title\">Hub Settings</h2>");
                    out.println(
                            "            <p class=\"aira-meta\">Edit SMTP and external URL values for email sending.</p>");

                    if (saved) {
                        out.println(
                                "            <div class=\"aira-alert aira-alert--success\"><p>Settings saved.</p></div>");
                    }
                    if (errorMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--danger\"><p><strong>Save failed:</strong> "
                                + escapeHtml(errorMessage) + "</p></div>");
                    }
                    if (testEmailMessage != null) {
                        out.println("            <div class=\"aira-alert aira-alert--info\"><p>"
                                + escapeHtml(testEmailMessage) + "</p></div>");
                    }

                    out.println("            <form class=\"aira-form\" action=\"" + contextPath
                            + "/admin/settings\" method=\"post\">");
                    out.println("              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"active\""
                            + checked(settings.getActive()) + " /> Active</label>");
                    out.println(
                            "              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"emailEnabled\""
                                    + checked(settings.getEmailEnabled()) + " /> Email Sending Enabled</label>");
                    out.println(
                            "              <p class=\"aira-meta\">When unchecked, all outbound email is suppressed silently. No SMTP connection is attempted.</p>");
                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"externalBaseUrl\">External Base URL</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"externalBaseUrl\" name=\"externalBaseUrl\" type=\"text\" value=\""
                                    + escapeHtml(orEmpty(settings.getExternalBaseUrl())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"smtpHost\">SMTP Host</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"smtpHost\" name=\"smtpHost\" type=\"text\" value=\""
                                    + escapeHtml(orEmpty(settings.getSmtpHost())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"smtpPort\">SMTP Port</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"smtpPort\" name=\"smtpPort\" type=\"number\" min=\"1\" max=\"65535\" value=\""
                                    + escapeHtml(String.valueOf(
                                            settings.getSmtpPort() == null ? 2525 : settings.getSmtpPort()))
                                    + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"smtpUsername\">SMTP Username</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"smtpUsername\" name=\"smtpUsername\" type=\"text\" value=\""
                                    + escapeHtml(orEmpty(settings.getSmtpUsername())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"smtpPassword\">SMTP Password</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"smtpPassword\" name=\"smtpPassword\" type=\"text\" value=\""
                                    + escapeHtml(orEmpty(settings.getSmtpPassword())) + "\" />");
                    out.println("              </div>");

                    out.println(
                            "              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"smtpAuth\""
                                    + checked(settings.getSmtpAuth()) + " /> SMTP Auth</label>");
                    out.println(
                            "              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"smtpStarttls\""
                                    + checked(settings.getSmtpStarttls()) + " /> SMTP STARTTLS</label>");
                    out.println(
                            "              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"smtpSsl\""
                                    + checked(settings.getSmtpSsl()) + " /> SMTP SSL</label>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"smtpFromEmail\">From Email</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"smtpFromEmail\" name=\"smtpFromEmail\" type=\"email\" value=\""
                                    + escapeHtml(orEmpty(settings.getSmtpFromEmail())) + "\" />");
                    out.println("              </div>");

                    out.println("              <div class=\"aira-field\">");
                    out.println("                <label for=\"smtpFromName\">From Name</label>");
                    out.println(
                            "                <input class=\"aira-input\" id=\"smtpFromName\" name=\"smtpFromName\" type=\"text\" value=\""
                                    + escapeHtml(orEmpty(settings.getSmtpFromName())) + "\" />");
                    out.println("              </div>");

                    out.println(
                            "              <label class=\"aira-radio\"><input type=\"checkbox\" name=\"sendTestEmail\" />"
                                    + " Send test email to my address after saving</label>");
                    out.println("              <div class=\"aira-action-group\">");
                    out.println(
                            "                <button class=\"aira-button aira-button--primary\" type=\"submit\">Save Settings</button>");
                    out.println("              </div>");
                    out.println("            </form>");
                    out.println("          </section>");
                });
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
