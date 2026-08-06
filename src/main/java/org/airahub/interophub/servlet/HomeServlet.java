package org.airahub.interophub.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.logging.Logger;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.immregistries.aira.web.AiraPage;

public class HomeServlet extends HttpServlet {
        private static final Logger LOGGER = Logger.getLogger(HomeServlet.class.getName());
        private final AuthFlowService authFlowService;

        public HomeServlet() {
                this.authFlowService = new AuthFlowService();
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                        throws ServletException, IOException {
                LOGGER.info("Handling request for home page.");

                Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);

                String contextPath = request.getContextPath();
                String emailValidationError = request.getParameter("emailError");
                String submittedEmail = trimToNull(request.getParameter("email"));
                Optional<AuthFlowService.ExternalAuthRequest> externalAuthRequest = Optional.empty();
                String externalAuthError = null;
                try {
                        externalAuthRequest = authFlowService.parseExternalAuthRequest(request);
                        externalAuthRequest.ifPresent(
                                        value -> authFlowService.rememberExternalAuthRequest(request, value));
                } catch (IllegalArgumentException ex) {
                        externalAuthRequest = authFlowService.recallExternalAuthRequest(request);
                        if (externalAuthRequest.isEmpty()) {
                                externalAuthError = ex.getMessage();
                        }
                }

                if (authenticatedUser.isPresent()) {
                        if (externalAuthRequest.isPresent()) {
                                String redirectTarget = authFlowService.issueExternalLoginCodeRedirect(
                                                authenticatedUser.get(),
                                                externalAuthRequest.get());
                                authFlowService.clearRememberedExternalAuthRequest(request);
                                response.sendRedirect(redirectTarget);
                                return;
                        }
                        Optional<String> internalRedirect = authFlowService.recallInternalRequestedUrl(request);
                        if (internalRedirect.isPresent()) {
                                authFlowService.clearRememberedInternalRequestedUrl(request);
                                response.sendRedirect(request.getContextPath() + internalRedirect.get());
                                return;
                        }
                        response.sendRedirect(request.getContextPath() + "/welcome");
                        return;
                }

                response.setContentType("text/html;charset=UTF-8");

                AiraPage page = InteropAiraPageFactory.base(request, "Sign In - InteropHub")
                                .applicationSubtitle("Sign In")
                                .mainClass("aira-main")
                                .context(InteropAiraPageFactory.publicTopicSpacePickerContext())
                                .build();

                try (PrintWriter out = response.getWriter()) {
                        page.writeStart(out);
                        renderPageContent(out, contextPath, emailValidationError, submittedEmail,
                                        externalAuthRequest, externalAuthError);
                        page.writeEnd(out);
                }
        }

        private void renderPageContent(PrintWriter out, String contextPath, String emailValidationError,
                        String submittedEmail, Optional<AuthFlowService.ExternalAuthRequest> externalAuthRequest,
                        String externalAuthError) {
                out.println("      <div class=\"aira-container--standard\">");
                out.println("        <div class=\"aira-page-header\">");
                out.println("          <div>");
                out.println("            <h1 class=\"aira-public-title\">Immunization InteropHub</h1>");
                SignInInfoRenderer.renderIntroParagraphs(out);
                out.println("          </div>");
                out.println("          <div class=\"aira-action-group\">");
                out.println("            <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath
                                + "/welcome\">Browse public topics</a>");
                out.println("          </div>");
                out.println("        </div>");

                out.println("        <div class=\"aira-stack\">");

                out.println("          <div class=\"aira-grid\">");

                out.println("            <section class=\"aira-panel\">");
                out.println("              <h2 class=\"aira-section-title\">Sign in with your email</h2>");
                out.println(
                                "              <p>Enter your email address and we will send you a sign-in link. No password is required.</p>");
                if (externalAuthRequest.isPresent()) {
                        out.println("              <div class=\"aira-alert aira-alert--info\">");
                        out.println("                <p>You are signing in for external application <strong>"
                                        + escapeHtml(externalAuthRequest.get().getAppCode())
                                        + "</strong>.</p>");
                        out.println("              </div>");
                }
                if (externalAuthError != null) {
                        out.println("              <div class=\"aira-alert aira-alert--error\">");
                        out.println("                <p><strong>External login request is invalid:</strong> "
                                        + escapeHtml(externalAuthError) + "</p>");
                        out.println("              </div>");
                }
                out.println("              <form class=\"aira-form\" action=\"" + contextPath
                                + "/send-welcome-email\" method=\"post\">");
                out.println("                <div class=\"aira-field\">");
                out.println("                  <label for=\"email\">Email Address</label>");
                boolean hasEmailError = emailValidationError != null;
                if (hasEmailError) {
                        out.println(
                                        "                  <div class=\"aira-field-error\" id=\"email-error\">Enter a valid email address using standard format (for example, you@example.org).</div>");
                }
                out.println(
                                "                  <input class=\"aira-input\" id=\"email\" name=\"email\" type=\"email\" placeholder=\"you@example.org\" autocomplete=\"email\" required maxlength=\"254\" value=\""
                                                + escapeHtml(orEmpty(submittedEmail))
                                                + "\" pattern=\"[A-Za-z0-9.!#$%&amp;'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+\" title=\"Enter a valid email address like you@example.org\""
                                                + (hasEmailError ? " aria-invalid=\"true\" aria-describedby=\"email-error\"" : "")
                                                + " />");
                out.println("                </div>");
                if (externalAuthRequest.isPresent()) {
                        renderExternalAuthHiddenInputs(out, externalAuthRequest.get());
                }
                out.println("                <div class=\"aira-action-group\">");
                out.println(
                                "                  <button class=\"aira-button aira-button--primary\" type=\"submit\">Send sign-in link</button>");
                out.println("                </div>");
                out.println("              </form>");
                out.println("            </section>");

                out.println("            <section class=\"aira-panel\">");
                out.println("              <h2 class=\"aira-section-title\">What happens next</h2>");
                out.println(
                                "              <p>If you already have an account, we will email you a link that signs you in. If you are new to InteropHub, you will first provide a few account details and accept the applicable terms; we will then email your sign-in link.</p>");
                out.println(
                                "              <p>After you sign in, InteropHub will keep you signed in for up to 30 days. Continued activity renews that period, so people who use InteropHub regularly should rarely need to sign in again. You may still be asked to sign in again for security or account-related reasons.</p>");
                out.println("              <div class=\"aira-alert aira-alert--info\">");
                out.println("                <p class=\"aira-alert__title\">You can browse without an account</p>");
                out.println(
                                "                <p>Public Topic Spaces, topics, meeting information, notes, presentations, and other public resources remain available without signing in.</p>");
                out.println("              </div>");
                out.println("            </section>");

                out.println("          </div>");

                SignInInfoRenderer.renderCapabilitiesSection(out);
                SignInInfoRenderer.renderDetailsSection(out);

                out.println("        </div>");
                out.println("      </div>");
        }

        private void renderExternalAuthHiddenInputs(PrintWriter out,
                        AuthFlowService.ExternalAuthRequest externalAuthRequest) {
                out.println("        <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_APP_CODE + "\" value=\""
                                + escapeHtml(externalAuthRequest.getAppCode()) + "\" />");
                out.println("        <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_RETURN_TO + "\" value=\""
                                + escapeHtml(externalAuthRequest.getReturnTo()) + "\" />");
                out.println("        <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_STATE + "\" value=\""
                                + escapeHtml(externalAuthRequest.getState()) + "\" />");
                out.println("        <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_REQUESTED_URL
                                + "\" value=\""
                                + escapeHtml(externalAuthRequest.getRequestedUrl()) + "\" />");
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
}
