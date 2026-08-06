package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsInterestService;
import org.immregistries.aira.web.AiraPage;

public class MagicLinkServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(MagicLinkServlet.class.getName());

    private final AuthFlowService authFlowService;
    private final EsInterestService esInterestService;

    public MagicLinkServlet() {
        this.authFlowService = new AuthFlowService();
        this.esInterestService = new EsInterestService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = request.getParameter("token");

        try {
            if (!authFlowService.isMagicLinkTokenValid(token)) {
                throw new IllegalArgumentException("Magic link is invalid or expired.");
            }
            renderConfirmation(request, response, token);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Magic link sign-in failed.", ex);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            renderError(request, response, ex.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = request.getParameter("token");

        try {
            AuthFlowService.AuthenticatedSession authenticatedSession = authFlowService.consumeMagicLink(token,
                    request);
            response.addCookie(authFlowService.buildSessionCookie(authenticatedSession.getRawSessionToken(), request));
            esInterestService.linkAnonymousRecordsByEmail(
                    authenticatedSession.getUser().getUserId(),
                    authenticatedSession.getUser().getEmailNormalized());
            String redirectTarget = authenticatedSession.getExternalRedirectUrl()
                    .or(() -> authenticatedSession.getInternalRedirectUrl()
                            .map(value -> request.getContextPath() + value))
                    .orElse(request.getContextPath() + "/welcome");
            authFlowService.clearRememberedInternalRequestedUrl(request);
            response.sendRedirect(redirectTarget);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Magic link sign-in failed.", ex);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            renderError(request, response, ex.getMessage());
        }
    }

    private void renderConfirmation(HttpServletRequest request, HttpServletResponse response, String token)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String contextPath = request.getContextPath();

        AiraPage page = InteropAiraPageFactory.base(request, "Confirm Sign-In - InteropHub")
                .applicationSubtitle("Sign In")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.publicTopicSpacePickerContext())
                .build();

        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("      <div class=\"aira-container--narrow\">");
            out.println("        <div class=\"aira-page-header\">");
            out.println("          <div>");
            out.println("            <h1 class=\"aira-page-title\">Welcome to the Immunization InteropHub</h1>");
            out.println("            <p class=\"aira-page-intro\">Your access link is valid.</p>");
            out.println("          </div>");
            out.println("        </div>");
            out.println("        <section class=\"aira-panel\">");
            out.println("          <p>Please press OK to continue.</p>");
            out.println(
                    "          <form class=\"aira-form\" action=\"" + contextPath + "/magic-link\" method=\"post\">");
            out.println("            <input type=\"hidden\" name=\"token\" value=\"" + escapeHtml(token) + "\" />");
            out.println("            <div class=\"aira-action-group\">");
            out.println("              <button class=\"aira-button aira-button--primary\" type=\"submit\">OK</button>");
            out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                    + "/home\">Cancel and return to Home</a>");
            out.println("            </div>");
            out.println("          </form>");
            out.println("        </section>");
            out.println("      </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    private void renderError(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String contextPath = request.getContextPath();

        AiraPage page = InteropAiraPageFactory.base(request, "Magic Link Invalid - InteropHub")
                .applicationSubtitle("Sign In")
                .mainClass("aira-main")
                .context(InteropAiraPageFactory.publicTopicSpacePickerContext())
                .build();

        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("      <div class=\"aira-container--narrow\">");
            out.println("        <div class=\"aira-page-header\">");
            out.println("          <div>");
            out.println("            <h1 class=\"aira-page-title\">Magic Link Invalid</h1>");
            out.println("            <p class=\"aira-page-intro\">Could not sign you in with that link.</p>");
            out.println("          </div>");
            out.println("        </div>");
            out.println("        <div class=\"aira-alert aira-alert--error\">");
            out.println("          <p>Reason: " + escapeHtml(message) + "</p>");
            out.println("        </div>");
            out.println("        <div class=\"aira-action-group\">");
            out.println("          <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                    + "/home\">Return to Home</a>");
            out.println("        </div>");
            out.println("      </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
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
