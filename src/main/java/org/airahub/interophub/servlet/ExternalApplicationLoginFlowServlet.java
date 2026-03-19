package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ExternalApplicationLoginFlowServlet extends HttpServlet {
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
            out.println("  <title>External Application Login Flow - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"doc-page\">");
            out.println("    <section class=\"panel doc-section\">");
            out.println("      <p class=\"doc-kicker\">InteropHub Implementor Guide</p>");
            out.println("      <h1>External Application Login Flow</h1>");
            out.println(
                    "      <p>This guide describes how an external application initiates login in InteropHub and receives user identity data back.</p>");
            out.println("    </section>");

            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>Summary</h2>");
            out.println("      <ol>");
            out.println(
                    "        <li>External app redirects the browser to InteropHub <code>/home</code> with required query parameters.</li>");
            out.println("        <li>User authenticates in InteropHub, or is already authenticated.</li>");
            out.println(
                    "        <li>InteropHub redirects the browser back to the external app <code>return_to</code> URL with a one-time <code>code</code> and the original <code>state</code>.</li>");
            out.println(
                    "        <li>External app server calls InteropHub <code>POST /api/auth/exchange</code> with <code>app_code</code> and <code>code</code>.</li>");
            out.println("        <li>InteropHub returns user profile data as JSON.</li>");
            out.println("      </ol>");
            out.println("    </section>");

            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>Base URLs</h2>");
            out.println("      <p>Typical local base URL is:</p>");
            renderCodeBlock(out, "http://localhost:8080/hub");
            out.println("      <p>Key endpoints:</p>");
            out.println("      <ul>");
            out.println("        <li>Login entry: <code>GET /home</code></li>");
            out.println("        <li>Code exchange: <code>POST /api/auth/exchange</code></li>");
            out.println("      </ul>");
            out.println("    </section>");

            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>1. Redirect User To InteropHub</h2>");
            out.println("      <p>Your app starts authentication by redirecting the browser to:</p>");
            out.println("      <ul>");
            out.println("        <li><code>GET {hubBaseUrl}/home</code></li>");
            out.println("      </ul>");
            out.println("      <p>Required query parameters:</p>");
            out.println("      <ul>");
            out.println("        <li><code>app_code</code>: Registered app code in InteropHub app registry.</li>");
            out.println(
                    "        <li><code>return_to</code>: Your app callback URL. Must match an allowlisted base URL for this app.</li>");
            out.println("        <li><code>state</code>: Opaque CSRF or correlation value, 8 to 255 chars.</li>");
            out.println(
                    "        <li><code>requested_url</code>: Original URL user wanted in your app, as an absolute HTTP or HTTPS URL.</li>");
            out.println("      </ul>");
            out.println("      <p>Example:</p>");
            renderCodeBlock(out,
                    "http://localhost:8080/hub/home?app_code=my-app&return_to=https%3A%2F%2Fapp.example.org%2Fauth%2Fcallback&state=7ecf5f8a9b43d1aa&requested_url=https%3A%2F%2Fapp.example.org%2Fworkspace%2F42");
            out.println("      <p>Notes:</p>");
            out.println("      <ul>");
            out.println("        <li>All values should be URL-encoded.</li>");
            out.println(
                    "        <li><code>return_to</code> must be absolute <code>http</code> or <code>https</code>.</li>");
            out.println(
                    "        <li><code>requested_url</code> must also be absolute <code>http</code> or <code>https</code>.</li>");
            out.println("        <li>If parameters are invalid, InteropHub will not issue a callback code.</li>");
            out.println("      </ul>");
            out.println("    </section>");

            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>2. User Authentication Behavior</h2>");
            out.println("      <p>InteropHub behavior depends on user session state:</p>");
            out.println("      <ul>");
            out.println(
                    "        <li>Already signed in to InteropHub: InteropHub immediately creates a one-time login code and redirects to your callback URL.</li>");
            out.println(
                    "        <li>Not signed in: InteropHub prompts for email, may collect profile for new users, issues a magic link, then continues after link click.</li>");
            out.println(
                    "        <li>After successful magic-link sign-in, InteropHub creates a one-time login code and redirects to your callback URL.</li>");
            out.println("      </ul>");
            out.println("    </section>");

            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>3. Callback To External App</h2>");
            out.println(
                    "      <p>On success, browser is redirected to your <code>return_to</code> URL with query parameters:</p>");
            out.println("      <ul>");
            out.println("        <li><code>code</code>: One-time code to exchange server-to-server.</li>");
            out.println("        <li><code>state</code>: The same state value you originally sent.</li>");
            out.println("      </ul>");
            out.println("      <p>Example callback URL:</p>");
            renderCodeBlock(out, "https://app.example.org/auth/callback?code=Z8Lh8L2tX5xYV...&state=7ecf5f8a9b43d1aa");
            out.println("      <p>What your app should do on callback:</p>");
            out.println("      <ol>");
            out.println("        <li>Verify <code>state</code> matches the value stored in the user session.</li>");
            out.println(
                    "        <li>Send <code>code</code> and <code>app_code</code> from your backend to the InteropHub exchange API.</li>");
            out.println("      </ol>");
            out.println("    </section>");

            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>4. Exchange Code For User Identity</h2>");
            out.println("      <p>Call the InteropHub exchange endpoint from your server:</p>");
            out.println("      <ul>");
            out.println("        <li><code>POST {hubBaseUrl}/api/auth/exchange</code></li>");
            out.println("        <li><code>Content-Type: application/json</code></li>");
            out.println("      </ul>");
            out.println("      <p>Request body:</p>");
            renderCodeBlock(out, "{\n  \"app_code\": \"my-app\",\n  \"code\": \"<code-from-callback>\"\n}");
            out.println("      <p>Example cURL:</p>");
            renderCodeBlock(out,
                    "curl -X POST \"http://localhost:8080/hub/api/auth/exchange\" \\\n  -H \"Content-Type: application/json\" \\\n  -d '{\"app_code\":\"my-app\",\"code\":\"<code-from-callback>\"}'");
            out.println("      <p>Successful response, <code>200 OK</code>:</p>");
            renderCodeBlock(out,
                    "{\n  \"hub_user_id\": 123,\n  \"email\": \"user@example.org\",\n  \"name\": \"Jane Doe\",\n  \"organization\": \"AIRA\",\n  \"title\": \"Developer\",\n  \"requested_url\": \"https://app.example.org/workspace/42\",\n  \"issued_at\": \"2026-03-05T20:34:12Z\",\n  \"expires_in_seconds\": 3600\n}");
            out.println("      <p>Error response, <code>400</code> or <code>404</code>:</p>");
            renderCodeBlock(out, "{\n  \"error\": \"Invalid app_code or code.\"\n}");
            out.println("    </section>");

            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>5. Code Rules And Security Expectations</h2>");
            out.println("      <ul>");
            out.println("        <li>Codes are one-time use.</li>");
            out.println("        <li>Codes expire quickly, currently 5 minutes in InteropHub.</li>");
            out.println(
                    "        <li>Exchange fails if the <code>app_code</code> is unknown, the <code>code</code> is unknown, the <code>code</code> is expired, the <code>code</code> has already been consumed, or the <code>code</code> was issued for a different <code>app_code</code>.</li>");
            out.println("        <li>Always validate <code>state</code> before exchange.</li>");
            out.println(
                    "        <li>Perform exchange server-to-server; do not expose exchange credentials or logic in browser code.</li>");
            out.println("      </ul>");
            out.println("    </section>");

            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>6. Recommended External App Implementation Pattern</h2>");
            out.println("      <ol>");
            out.println("        <li>Generate and store a random <code>state</code> in the user session.</li>");
            out.println("        <li>Build the InteropHub <code>/home</code> URL with required parameters.</li>");
            out.println("        <li>Redirect user to InteropHub.</li>");
            out.println("        <li>Handle callback at the <code>return_to</code> route.</li>");
            out.println("        <li>Validate <code>state</code>.</li>");
            out.println(
                    "        <li>Exchange <code>code</code> via backend <code>POST /api/auth/exchange</code>.</li>");
            out.println("        <li>Create your own local session or token for the user.</li>");
            out.println(
                    "        <li>Redirect user to <code>requested_url</code> if present and allowed by your app rules.</li>");
            out.println("      </ol>");
            out.println("    </section>");

            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>7. Operational Notes</h2>");
            out.println("      <ul>");
            out.println(
                    "        <li>For local or development, InteropHub email sending may be disabled by runtime property: <code>-Dinterophub.email.send.enabled=true</code> to enable SMTP sending.</li>");
            out.println(
                    "        <li>In disabled mode, the magic link is still generated and shown in the InteropHub UI, which is useful for testing.</li>");
            out.println("      </ul>");
            out.println("    </section>");

            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>8. Integration Checklist</h2>");
            out.println("      <ul class=\"checklist\">");
            out.println("        <li><code>app_code</code> exists and is enabled in InteropHub.</li>");
            out.println("        <li><code>return_to</code> base URL is allowlisted for the app.</li>");
            out.println("        <li><code>state</code> generation and verification are implemented.</li>");
            out.println("        <li>Callback handler reads <code>code</code> and <code>state</code>.</li>");
            out.println("        <li>Backend exchange call is implemented.</li>");
            out.println("        <li>Exchange errors are handled and shown to users.</li>");
            out.println("        <li>Local app session creation after successful exchange is implemented.</li>");
            out.println("      </ul>");
            out.println("    </section>");

            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderCodeBlock(PrintWriter out, String code) {
        out.println("      <pre class=\"doc-code\"><code>" + escapeHtml(code) + "</code></pre>");
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