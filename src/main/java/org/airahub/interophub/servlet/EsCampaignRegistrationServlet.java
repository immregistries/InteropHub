package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignRegistrationDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsCampaignRegistration;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.service.EsInterestService;
import org.airahub.interophub.service.EsNormalizer;

public class EsCampaignRegistrationServlet extends HttpServlet {

    private static final String ATTR_FIRST_NAME = "interophub.es.registration.firstName";
    private static final String ATTR_LAST_NAME = "interophub.es.registration.lastName";
    private static final String ATTR_EMAIL = "interophub.es.registration.email";
    private static final String ATTR_EMAIL_NORMALIZED = "interophub.es.registration.emailNormalized";
    private static final String ATTR_CAMPAIGN_CODE = "interophub.es.registration.campaignCode";
    private static final String ATTR_SESSION_KEY = "interophub.es.registration.sessionKey";
    private static final String ATTR_CAMPAIGN_REGISTRATION_ID = "interophub.es.registration.campaignRegistrationId";

    private final EsCampaignDao campaignDao;
    private final EsCampaignRegistrationDao registrationDao;
    private final EsInterestService esInterestService;

    public EsCampaignRegistrationServlet() {
        this.campaignDao = new EsCampaignDao();
        this.registrationDao = new EsCampaignRegistrationDao();
        this.esInterestService = new EsInterestService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String campaignCode = parseCampaignCode(request);
        if (campaignCode == null) {
            renderCampaignNotFound(response, request.getContextPath(), null);
            return;
        }

        Optional<EsCampaign> campaign = campaignDao.findByCampaignCode(campaignCode);
        if (campaign.isEmpty()) {
            renderCampaignNotFound(response, request.getContextPath(), campaignCode);
            return;
        }

        renderForm(response, request.getContextPath(), campaign.get(), null, null, null, null, false);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        String campaignCode = parseCampaignCode(request);
        if (campaignCode == null) {
            renderCampaignNotFound(response, request.getContextPath(), null);
            return;
        }

        Optional<EsCampaign> campaign = campaignDao.findByCampaignCode(campaignCode);
        if (campaign.isEmpty()) {
            renderCampaignNotFound(response, request.getContextPath(), campaignCode);
            return;
        }

        String firstName = trimToNull(request.getParameter("firstName"));
        String lastName = trimToNull(request.getParameter("lastName"));
        String email = trimToNull(request.getParameter("email"));
        String emailNormalized = EsNormalizer.normalizeEmail(email);
        boolean generalUpdatesOptIn = request.getParameter("generalUpdatesOptIn") != null;

        if (firstName == null) {
            renderForm(response, request.getContextPath(), campaign.get(), "First name is required.", null,
                    lastName, email, generalUpdatesOptIn);
            return;
        }

        HttpSession session = request.getSession(true);
        String sessionKey = session.getId();

        EsCampaignRegistration registration = new EsCampaignRegistration();
        registration.setEsCampaignId(campaign.get().getEsCampaignId());
        registration.setFirstName(firstName);
        registration.setLastName(lastName);
        registration.setEmail(email);
        registration.setEmailNormalized(emailNormalized);
        registration.setGeneralUpdatesOptIn(generalUpdatesOptIn);
        registration.setSessionKey(sessionKey);
        registrationDao.save(registration);

        if (emailNormalized != null && generalUpdatesOptIn) {
            EsSubscription subscription = new EsSubscription();
            subscription.setEmail(email);
            subscription.setEmailNormalized(emailNormalized);
            subscription.setUserId(null);
            subscription.setEsTopicId(null);
            subscription.setSubscriptionType(EsSubscription.SubscriptionType.GENERAL_ES);
            subscription.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
            subscription.setSourceCampaignId(campaign.get().getEsCampaignId());
            esInterestService.subscribeOrUpdate(subscription);
        }

        setSessionValue(session, ATTR_FIRST_NAME, firstName);
        setSessionValue(session, ATTR_LAST_NAME, lastName);
        setSessionValue(session, ATTR_EMAIL, email);
        setSessionValue(session, ATTR_EMAIL_NORMALIZED, emailNormalized);
        setSessionValue(session, ATTR_CAMPAIGN_CODE, campaignCode);
        setSessionValue(session, ATTR_SESSION_KEY, sessionKey);
        session.setAttribute(ATTR_CAMPAIGN_REGISTRATION_ID, registration.getEsCampaignRegistrationId());

        response.sendRedirect(request.getContextPath() + "/register/complete/"
                + URLEncoder.encode(campaignCode, StandardCharsets.UTF_8));
    }

    private void renderForm(HttpServletResponse response, String contextPath, EsCampaign campaign, String errorMessage,
            String firstName, String lastName, String email, boolean generalUpdatesOptIn)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Register - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>" + escapeHtml(orEmpty(campaign.getCampaignName())) + "</h1>");
            out.println("    <p>Register to participate and share your input.</p>");
            if (errorMessage != null) {
                out.println("    <p><strong>Could not submit:</strong> " + escapeHtml(errorMessage) + "</p>");
            }
            out.println("    <form class=\"login-form\" method=\"post\" action=\"" + contextPath + "/register/"
                    + escapeHtml(campaign.getCampaignCode()) + "\">");
            out.println("      <label for=\"firstName\">First Name (required)</label>");
            out.println("      <input id=\"firstName\" name=\"firstName\" type=\"text\" required value=\""
                    + escapeHtml(orEmpty(firstName)) + "\" />");

            out.println("      <label for=\"lastName\">Last Name</label>");
            out.println("      <input id=\"lastName\" name=\"lastName\" type=\"text\" value=\""
                    + escapeHtml(orEmpty(lastName)) + "\" />");

            out.println("      <label for=\"email\">Email</label>");
            out.println("      <input id=\"email\" name=\"email\" type=\"email\" value=\""
                    + escapeHtml(orEmpty(email)) + "\" />");

            String checkboxDisabled = trimToNull(email) == null ? " disabled" : "";
            String checkboxChecked = generalUpdatesOptIn ? " checked" : "";
            out.println("      <label>");
            out.println("        <input id=\"generalUpdatesOptIn\" name=\"generalUpdatesOptIn\" type=\"checkbox\""
                    + checkboxChecked + checkboxDisabled + " />");
            out.println("        Subscribe for general Emerging Standards updates");
            out.println("      </label>");

            out.println("      <button type=\"submit\">Submit Registration</button>");
            out.println("    </form>");
            out.println("  </main>");
            out.println("  <script>");
            out.println("    (function() {");
            out.println("      var email = document.getElementById('email');");
            out.println("      var checkbox = document.getElementById('generalUpdatesOptIn');");
            out.println("      function syncCheckbox() {");
            out.println("        var hasEmail = email && email.value && email.value.trim().length > 0;");
            out.println("        checkbox.disabled = !hasEmail;");
            out.println("        if (!hasEmail) { checkbox.checked = false; }");
            out.println("      }");
            out.println("      if (email && checkbox) {");
            out.println("        email.addEventListener('input', syncCheckbox);");
            out.println("        syncCheckbox();");
            out.println("      }");
            out.println("    })();");
            out.println("  </script>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderCampaignNotFound(HttpServletResponse response, String contextPath, String campaignCode)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Campaign Not Found - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Campaign Not Found</h1>");
            out.println("    <p>The campaign code could not be resolved.</p>");
            if (campaignCode != null) {
                out.println("    <p><strong>Code:</strong> " + escapeHtml(campaignCode) + "</p>");
            }
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private String parseCampaignCode(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            return null;
        }
        if (!pathInfo.startsWith("/")) {
            return null;
        }
        String trimmed = pathInfo.substring(1).trim();
        if (trimmed.isEmpty() || trimmed.contains("/")) {
            return null;
        }
        return trimmed;
    }

    private void setSessionValue(HttpSession session, String attributeName, String value) {
        if (value == null) {
            session.removeAttribute(attributeName);
            return;
        }
        session.setAttribute(attributeName, value);
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
