package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DocsIndexServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        String contextPath = request.getContextPath();

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Implementor Docs - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"doc-page\">");

            out.println("    <section class=\"panel doc-section\">");
            out.println("      <p class=\"doc-kicker\">InteropHub</p>");
            out.println("      <h1>Implementor Docs</h1>");
            out.println("      <p>Technical reference guides for teams integrating with InteropHub.</p>");
            out.println("    </section>");

            out.println("    <div class=\"doc-index-grid\">");

            out.println("      <a class=\"doc-card panel\" href=\"" + contextPath
                    + "/docs/external-application-login-flow.html\">");
            out.println("        <p class=\"doc-kicker\">Implementor Guide</p>");
            out.println("        <h2>External Application Login Flow</h2>");
            out.println(
                    "        <p>How an external application redirects users to InteropHub for authentication and receives user identity back via a one-time code exchange.</p>");
            out.println("      </a>");

            out.println("      <a class=\"doc-card panel\" href=\"" + contextPath + "/docs/oauth-architecture.html\">");
            out.println("        <p class=\"doc-kicker\">Architecture Reference</p>");
            out.println("        <h2>OAuth Architecture</h2>");
            out.println(
                    "        <p>Overview of InteropHub&#39;s OAuth design: roles, token flow, JWT issuance, resource server validation, and usage tracking for the Connectathon environment.</p>");
            out.println("      </a>");

            out.println("    </div>");

            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }
}
