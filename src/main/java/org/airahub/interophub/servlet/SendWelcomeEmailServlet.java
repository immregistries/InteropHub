package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.AuthService;
import org.airahub.interophub.service.EmailService;

public class SendWelcomeEmailServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(SendWelcomeEmailServlet.class.getName());

    private final AuthFlowService authFlowService;
    private final AuthService authService;
    private final EmailService emailService;

    public SendWelcomeEmailServlet() {
        this.authFlowService = new AuthFlowService();
        this.authService = new AuthService();
        this.emailService = new EmailService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/home");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        String email = trimToNull(request.getParameter("email"));
        String normalizedEmail = normalizeEmail(email);
        String contextPath = request.getContextPath();
        String displayName = trimToNull(request.getParameter("displayName"));
        String organization = trimToNull(request.getParameter("organization"));
        String roleTitle = trimToNull(request.getParameter("roleTitle"));
        Optional<AuthFlowService.ExternalAuthRequest> externalAuthRequest = Optional.empty();
        String externalAuthError = null;
        try {
            externalAuthRequest = authFlowService.parseExternalAuthRequest(request);
        } catch (IllegalArgumentException ex) {
            externalAuthError = ex.getMessage();
        }

        boolean profileSubmission = request.getParameter("profileSubmission") != null;

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Email Sent - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");

            try {
                if (externalAuthError != null) {
                    throw new IllegalArgumentException(externalAuthError);
                }
                if (normalizedEmail == null) {
                    throw new IllegalArgumentException("A valid email address is required.");
                }

                Optional<User> existingUser = authService.findUserByEmail(normalizedEmail);
                User user;
                if (existingUser.isPresent()) {
                    user = existingUser.get();
                } else if (!profileSubmission) {
                    renderProfileForm(out, contextPath, email, displayName, organization, roleTitle, null,
                            externalAuthRequest.orElse(null));
                    out.println("  </main>");
                    PageFooterRenderer.render(out);
                    out.println("</body>");
                    out.println("</html>");
                    return;
                } else {
                    String requiredDisplayName = required(displayName, "Display Name");
                    String requiredOrganization = required(organization, "Organization");
                    String requiredRoleTitle = required(roleTitle, "Role Title");

                    user = authService.registerUser(
                            normalizedEmail,
                            requiredDisplayName,
                            requiredOrganization,
                            requiredRoleTitle);
                }

                String magicLinkUrl = authFlowService.issueMagicLink(user, request, externalAuthRequest.orElse(null));
                renderEmailSent(out, contextPath, email, emailService.sendWelcomeEmail(normalizedEmail, magicLinkUrl));
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to issue magic link or send welcome email.", ex);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                if (profileSubmission && normalizedEmail != null) {
                    renderProfileForm(out, contextPath, email, displayName, organization, roleTitle, ex.getMessage(),
                            externalAuthRequest.orElse(null));
                } else {
                    out.println("    <h1>Email Send Failed</h1>");
                    out.println("    <p>Could not send email right now.</p>");
                    out.println("    <p>Reason: " + escapeHtml(ex.getMessage()) + "</p>");
                    out.println("    <p><a href=\"" + contextPath + "/home\">Return to Home</a></p>");
                }
            }

            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderEmailSent(PrintWriter out, String contextPath, String email, String loginLink) {
        out.println("    <h1>Email Sent</h1>");
        out.println("    <p>We sent a welcome email to <strong>" + escapeHtml(email) + "</strong>.</p>");
        out.println("    <p>The message includes this temporary link:</p>");
        out.println("    <p><a href=\"" + escapeHtml(loginLink) + "\">" + escapeHtml(loginLink) + "</a></p>");
        out.println("    <p><a href=\"" + contextPath + "/home\">Back to Home</a></p>");
    }

    private void renderProfileForm(PrintWriter out, String contextPath, String email, String displayName,
            String organization, String roleTitle, String errorMessage,
            AuthFlowService.ExternalAuthRequest externalAuthRequest) {
        out.println("    <h1>Tell Us About You</h1>");
        out.println("    <p>Before sending your magic link, we need a few details for your InteropHub profile.</p>");
        out.println("    <p>Email: <strong>" + escapeHtml(email) + "</strong></p>");
        if (errorMessage != null && !errorMessage.isBlank()) {
            out.println("    <p><strong>Could not continue:</strong> " + escapeHtml(errorMessage) + "</p>");
        }
        out.println(
                "    <form class=\"login-form\" action=\"" + contextPath + "/send-welcome-email\" method=\"post\">");
        out.println("      <input type=\"hidden\" name=\"profileSubmission\" value=\"1\" />");
        out.println("      <input type=\"hidden\" name=\"email\" value=\"" + escapeHtml(orEmpty(email)) + "\" />");
        if (externalAuthRequest != null) {
            out.println("      <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_APP_CODE + "\" value=\""
                    + escapeHtml(externalAuthRequest.getAppCode()) + "\" />");
            out.println("      <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_RETURN_TO + "\" value=\""
                    + escapeHtml(externalAuthRequest.getReturnTo()) + "\" />");
            out.println("      <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_STATE + "\" value=\""
                    + escapeHtml(externalAuthRequest.getState()) + "\" />");
            out.println("      <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_REQUESTED_URL + "\" value=\""
                    + escapeHtml(externalAuthRequest.getRequestedUrl()) + "\" />");
        }

        out.println("      <label for=\"displayName\">Display Name</label>");
        out.println("      <input id=\"displayName\" name=\"displayName\" type=\"text\" required value=\""
                + escapeHtml(orEmpty(displayName)) + "\" />");

        out.println("      <label for=\"organization\">Organization</label>");
        out.println("      <input id=\"organization\" name=\"organization\" type=\"text\" required value=\""
                + escapeHtml(orEmpty(organization)) + "\" />");

        out.println("      <label for=\"roleTitle\">Role Title</label>");
        out.println("      <input id=\"roleTitle\" name=\"roleTitle\" type=\"text\" required value=\""
                + escapeHtml(orEmpty(roleTitle)) + "\" />");

        out.println("      <button type=\"submit\">Save And Send Magic Link</button>");
        out.println("    </form>");
        out.println("    <p><a href=\"" + contextPath + "/home\">Cancel</a></p>");
    }

    private String required(String rawValue, String label) {
        String value = trimToNull(rawValue);
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    private String normalizeEmail(String rawEmail) {
        String value = trimToNull(rawEmail);
        if (value == null || !value.contains("@")) {
            return null;
        }
        return value.toLowerCase();
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
