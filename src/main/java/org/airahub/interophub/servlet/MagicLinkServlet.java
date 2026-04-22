package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.service.AuthFlowService;

public class MagicLinkServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(MagicLinkServlet.class.getName());

    private final AuthFlowService authFlowService;

    public MagicLinkServlet() {
        this.authFlowService = new AuthFlowService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = request.getParameter("token");

        try {
            if (!authFlowService.isMagicLinkTokenValid(token)) {
                throw new IllegalArgumentException("Magic link is invalid or expired.");
            }
            renderConfirmation(response, request.getContextPath(), token);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Magic link sign-in failed.", ex);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            renderError(response, request.getContextPath(), ex.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = request.getParameter("token");

        try {
            AuthFlowService.AuthenticatedSession authenticatedSession = authFlowService.consumeMagicLink(token,
                    request);
            response.addCookie(authFlowService.buildSessionCookie(authenticatedSession.getRawSessionToken(), request));
            String redirectTarget = authenticatedSession.getExternalRedirectUrl()
                .or(() -> authenticatedSession.getInternalRedirectUrl()
                    .map(value -> request.getContextPath() + value))
                .orElse(request.getContextPath() + "/welcome");
            authFlowService.clearRememberedInternalRequestedUrl(request);
            response.sendRedirect(redirectTarget);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Magic link sign-in failed.", ex);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            renderError(response, request.getContextPath(), ex.getMessage());
        }
    }

    private void renderConfirmation(HttpServletResponse response, String contextPath, String token) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Confirm Sign-In - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Welcome to the Immunization InteropHub</h1>");
            out.println("    <p>Your access link is valid.</p>");
            out.println("    <p>Please press OK to continue.</p>");
            out.println("    <form class=\"login-form\" action=\"" + contextPath + "/magic-link\" method=\"post\">");
            out.println("      <input type=\"hidden\" name=\"token\" value=\"" + escapeHtml(token) + "\" />");
            out.println("      <button type=\"submit\">OK</button>");
            out.println("    </form>");
            out.println("    <p><a href=\"" + contextPath + "/home\">Cancel and return to Home</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderError(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Magic Link Invalid - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Magic Link Invalid</h1>");
            out.println("    <p>Could not sign you in with that link.</p>");
            out.println("    <p>Reason: " + escapeHtml(message) + "</p>");
            out.println("    <p><a href=\"" + contextPath + "/home\">Return to Home</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
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
