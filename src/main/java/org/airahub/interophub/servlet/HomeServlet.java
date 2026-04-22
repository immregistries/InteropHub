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

                try (PrintWriter out = response.getWriter()) {
                        out.println("<!DOCTYPE html>");
                        out.println("<html lang=\"en\">");
                        out.println("<head>");
                        out.println("  <meta charset=\"UTF-8\" />");
                        out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
                        out.println("  <title>InteropHub Login</title>");
                        out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
                        out.println("</head>");
                        out.println("<body>");
                        out.println("  <main class=\"login-page\">");
                        out.println("    <section class=\"panel\">");
                        out.println("      <img class=\"banner\" src=\"" + contextPath
                                        + "/image/Splashpage_connectathon.png\" alt=\"Developers collaborating on connectathon work\" />");
                        out.println("      <h1>Immunization InteropHub</h1>");
                        out.println(
                                        "      <p class=\"tagline\">Connect with other developers working on immunization interoperability</p>");
                        out.println("    </section>");

                        out.println("    <section class=\"panel\">");
                        out.println("      <p class=\"section-title\">Enter your email to continue</p>");
                        if (externalAuthRequest.isPresent()) {
                                out.println(
                                                "      <p>You are signing in for external application <strong>"
                                                                + escapeHtml(externalAuthRequest.get().getAppCode())
                                                                + "</strong>.</p>");
                        }
                        if (externalAuthError != null) {
                                out.println("      <p><strong>External login request is invalid:</strong> "
                                                + escapeHtml(externalAuthError) + "</p>");
                        }
                        out.println("      <form class=\"login-form\" action=\"" + contextPath
                                        + "/send-welcome-email\" method=\"post\">");
                        out.println("        <label for=\"email\">Email Address</label>");
                        if (emailValidationError != null) {
                                out.println(
                                                "        <div class=\"field-error field-error-block\">Enter a valid email address using standard format (for example, you@example.org).</div>");
                        }
                        out.println(
                                        "        <input id=\"email\" name=\"email\" type=\"email\" placeholder=\"you@example.org\" autocomplete=\"email\" required maxlength=\"254\" value=\""
                                                        + escapeHtml(orEmpty(submittedEmail))
                                                        + "\" pattern=\"[A-Za-z0-9.!#$%&amp;'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+\" title=\"Enter a valid email address like you@example.org\" />");
                        if (externalAuthRequest.isPresent()) {
                                renderExternalAuthHiddenInputs(out, externalAuthRequest.get());
                        }
                        out.println("        <button type=\"submit\">Send Email Link</button>");
                        out.println("      </form>");
                        out.println("    </section>");

                        out.println("    <section class=\"panel\">");
                        out.println(
                                        "      <p>New here? We\'ll ask for a few details after you confirm your email so we can connect you with other participants.");
                        out.println(
                                        "      Your information is only shared with people who join the same connectathon workspace.</p>");
                        out.println("    </section>");

                        out.println("    <section class=\"panel\">");
                        out.println("      <h2>About this project</h2>");
                        out.println(
                                        "      <p>InteropHub is operated by the American Immunization Registry Association (AIRA) to support the development of standards and interoperability for immunization information systems.</p>");
                        out.println("      <p>By registering you can:</p>");
                        out.println("      <ul>");
                        out.println("        <li>Access demonstration systems</li>");
                        out.println("        <li>Generate API secrets</li>");
                        out.println("        <li>Connect with other developers</li>");
                        out.println("        <li>Participate in connectathon workspaces</li>");
                        out.println("      </ul>");
                        out.println("      <p>Come join the community.</p>");
                        out.println("    </section>");
                        out.println("  </main>");
                        PageFooterRenderer.render(out);
                        out.println("</body>");
                        out.println("</html>");
                }
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
