package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class OAuthArchitectureServlet extends HttpServlet {
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
            out.println("  <title>OAuth Architecture - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"doc-page\">");

            // ── Header ─────────────────────────────────────────────────────────────
            out.println("    <section class=\"panel doc-section\">");
            out.println("      <p class=\"doc-kicker\">InteropHub Architecture Reference</p>");
            out.println("      <h1>OAuth Architecture</h1>");
            out.println(
                    "      <p>InteropHub is the central portal for all Connectathon interoperability testing activities. It provides:</p>");
            out.println("      <ul>");
            out.println("        <li>Developer onboarding</li>");
            out.println("        <li>Identity verification via email</li>");
            out.println("        <li>Discovery of test APIs</li>");
            out.println("        <li>OAuth credential issuance</li>");
            out.println("        <li>Access tokens for API execution</li>");
            out.println("        <li>Usage tracking across systems</li>");
            out.println("      </ul>");
            out.println(
                    "      <p>The design intentionally mirrors real-world OAuth and FHIR authorization workflows while remaining simple enough for a Connectathon environment. Security is not the primary objective. The goal is developer convenience, realistic OAuth workflow practice, observability of system usage, and lightweight centralized coordination. InteropHub acts as a <strong>lightweight OAuth Authorization Server</strong> and developer portal.</p>");
            out.println("    </section>");

            // ── Core Conceptual Model ──────────────────────────────────────────────
            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>Core Conceptual Model</h2>");
            out.println("      <p>Three roles exist in the architecture.</p>");
            out.println("      <h3>1. Human Developer</h3>");
            out.println("      <p>A person participating in the Connectathon. They:</p>");
            out.println("      <ul>");
            out.println("        <li>register in InteropHub</li>");
            out.println("        <li>verify their email</li>");
            out.println("        <li>log into the portal</li>");
            out.println("        <li>view available test APIs</li>");
            out.println("        <li>generate OAuth client credentials</li>");
            out.println("      </ul>");
            out.println("      <h3>2. OAuth Client (Application)</h3>");
            out.println("      <p>An application or integration created by a developer. Examples:</p>");
            out.println("      <ul>");
            out.println("        <li>an EHR test harness</li>");
            out.println("        <li>a CDS engine</li>");
            out.println("        <li>a FHIR client script</li>");
            out.println("        <li>a Postman collection</li>");
            out.println("      </ul>");
            out.println("      <p>Each client receives credentials used to obtain OAuth access tokens.</p>");
            out.println("      <h3>3. Resource Server (Dependent APIs)</h3>");
            out.println("      <p>These are the testing APIs that developers interact with. Examples:</p>");
            out.println("      <ul>");
            out.println("        <li>Step Into CDSi</li>");
            out.println("        <li>FHIR sandbox</li>");
            out.println("        <li>forecasting engines</li>");
            out.println("        <li>Connectathon validation services</li>");
            out.println("      </ul>");
            out.println("      <p>Resource servers validate OAuth tokens issued by InteropHub.</p>");
            out.println("    </section>");

            // ── Architectural Roles ────────────────────────────────────────────────
            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>Architectural Roles</h2>");
            out.println("      <p>InteropHub provides the following capabilities:</p>");
            out.println("      <table>");
            out.println("        <thead><tr><th>Capability</th><th>Role</th></tr></thead>");
            out.println("        <tbody>");
            out.println("          <tr><td>User onboarding</td><td>Identity system</td></tr>");
            out.println("          <tr><td>Client registration</td><td>OAuth client registry</td></tr>");
            out.println("          <tr><td>Token issuance</td><td>OAuth Authorization Server</td></tr>");
            out.println("          <tr><td>Token signing</td><td>Trust anchor for APIs</td></tr>");
            out.println("          <tr><td>Usage tracking</td><td>Observability hub</td></tr>");
            out.println("        </tbody>");
            out.println("      </table>");
            out.println("      <p>Dependent APIs function as <strong>OAuth Resource Servers</strong>.</p>");
            out.println("    </section>");

            // ── Key Architectural Principles ───────────────────────────────────────
            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>Key Architectural Principles</h2>");
            out.println("      <h3>1. Simple developer workflow</h3>");
            out.println("      <p>The workflow requires minimal configuration. Typical flow:</p>");
            out.println("      <ol>");
            out.println("        <li>Developer registers in InteropHub</li>");
            out.println("        <li>Developer verifies email</li>");
            out.println("        <li>Developer logs in</li>");
            out.println("        <li>Developer creates an OAuth client</li>");
            out.println("        <li>Developer receives <code>client_id</code> and <code>client_secret</code></li>");
            out.println("        <li>Client requests an OAuth token</li>");
            out.println("        <li>Client calls the API using a bearer token</li>");
            out.println("      </ol>");
            out.println("      <h3>2. OAuth tokens represent applications, not users</h3>");
            out.println(
                    "      <p>OAuth tokens are issued to <strong>clients</strong>, not directly to users. InteropHub maintains the relationship:</p>");
            out.println("      <p>User &rarr; OAuth Client &rarr; Access Token</p>");
            out.println(
                    "      <p>This allows accurate usage reporting, multiple clients per user, and clearer API authorization semantics.</p>");
            out.println("      <h3>3. Short-lived access tokens</h3>");
            out.println(
                    "      <p>Tokens should be short-lived (example: 1 hour). This matches real OAuth behavior and prevents stale credentials.</p>");
            out.println("      <h3>4. JWT tokens validated locally</h3>");
            out.println(
                    "      <p>Resource servers validate tokens locally using InteropHub&#39;s public signing key. They do <strong>not</strong> call InteropHub on every request. Benefits: faster execution, fewer network dependencies, simpler architecture.</p>");
            out.println("    </section>");

            // ── Existing Core Tables ──────────────────────────────────────────────
            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>Existing Core Tables</h2>");
            out.println("      <h3><code>auth_user</code></h3>");
            out.println(
                    "      <p>Represents a registered developer. Important attributes: email, verification status, activity timestamps, lifecycle state. This table forms the basis of the developer identity model.</p>");
            out.println("      <h3><code>app_registry</code></h3>");
            out.println(
                    "      <p>Represents known APIs participating in the Connectathon environment. Example entries: <code>step-cdsi</code>, <code>forecasting-api</code>, <code>fhir-sandbox</code>. This registry is used to display APIs available to developers.</p>");
            out.println("    </section>");

            // ── Phase 1 ────────────────────────────────────────────────────────────
            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>Minimum Viable Build — Phase 1</h2>");
            out.println(
                    "      <p>Phase 1 delivers the minimal OAuth workflow necessary for Connectathon testing. This phase focuses on: developer onboarding, OAuth client creation, token issuance, resource server token validation, and usage logging.</p>");
            out.println("      <p>Phase 1 token flow:</p>");
            renderCodeBlock(out,
                    "Developer\n  |\n  v\nInteropHub\n  | (token request)\n  v\nAccess Token (JWT)\n  |\n  v\nDependent API\n  |\n  v\nUsage Event -> InteropHub");
            out.println("    </section>");

            // ── Phase 1 Components ─────────────────────────────────────────────────
            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>Phase 1 Components</h2>");

            out.println("      <h3>1. Developer Portal</h3>");
            out.println(
                    "      <p>Developers must be able to: register with email, verify email, log in, view APIs available for testing, and create OAuth clients. The existing <code>auth_user</code> table supports this functionality.</p>");

            out.println("      <h3>2. OAuth Client Registry</h3>");
            out.println(
                    "      <p>Developers create <strong>OAuth Clients</strong> within InteropHub. Each client receives a <code>client_id</code> and <code>client_secret</code>. Suggested table:</p>");
            renderCodeBlock(out,
                    "oauth_client\n  client_id         (pk)\n  user_id\n  client_name\n  client_secret_hash\n  status\n  created_at\n  last_used_at");
            out.println("      <p>Clients are associated with the developer who created them.</p>");

            out.println("      <h3>3. OAuth Token Endpoint</h3>");
            out.println("      <p>InteropHub must expose a token endpoint:</p>");
            renderCodeBlock(out,
                    "POST /oauth/token\n\nRequest:\n  grant_type=client_credentials\n  client_id=abc123\n  client_secret=xyz789\n\nResponse:\n  {\n    \"access_token\": \"...\",\n    \"token_type\": \"Bearer\",\n    \"expires_in\": 3600\n  }");

            out.println("      <h3>4. Access Token Format</h3>");
            out.println("      <p>Access tokens are <strong>JWTs signed by InteropHub</strong>. Suggested claims:</p>");
            out.println("      <table>");
            out.println("        <thead><tr><th>Claim</th><th>Description</th></tr></thead>");
            out.println("        <tbody>");
            out.println("          <tr><td><code>iss</code></td><td>Issuer (InteropHub)</td></tr>");
            out.println("          <tr><td><code>sub</code></td><td><code>client_id</code></td></tr>");
            out.println("          <tr><td><code>aud</code></td><td>Target API</td></tr>");
            out.println("          <tr><td><code>exp</code></td><td>Expiration</td></tr>");
            out.println("          <tr><td><code>iat</code></td><td>Issued time</td></tr>");
            out.println("          <tr><td><code>scope</code></td><td>Permissions</td></tr>");
            out.println("          <tr><td><code>jti</code></td><td>Token ID</td></tr>");
            out.println("          <tr><td><code>hub_user_id</code></td><td>Developer user ID</td></tr>");
            out.println("        </tbody>");
            out.println("      </table>");
            out.println("      <p>Example payload:</p>");
            renderCodeBlock(out,
                    "{\n  \"iss\": \"interop-hub\",\n  \"sub\": \"client_123\",\n  \"hub_user_id\": 55,\n  \"scope\": \"system/*.read\",\n  \"exp\": 1710000000,\n  \"iat\": 1709996400\n}");

            out.println("      <h3>5. Resource Server Token Validation</h3>");
            out.println("      <p>Dependent APIs must:</p>");
            out.println("      <ol>");
            out.println("        <li>Validate JWT signature</li>");
            out.println("        <li>Validate expiration</li>");
            out.println("        <li>Validate issuer</li>");
            out.println("        <li>Validate scope (optional)</li>");
            out.println("      </ol>");
            out.println("      <p>If valid, the request proceeds. Otherwise: <code>401 Unauthorized</code>.</p>");

            out.println("      <h3>6. Usage Event Reporting</h3>");
            out.println("      <p>Resource servers should report usage events to InteropHub:</p>");
            renderCodeBlock(out,
                    "POST /hub/events\n\n{\n  \"client_id\": \"abc123\",\n  \"app_code\": \"step-cdsi\",\n  \"operation\": \"$forecast\",\n  \"timestamp\": \"...\",\n  \"status\": \"success\"\n}");
            out.println("      <p>This allows the hub to build usage reports.</p>");

            out.println("      <h3>7. Reporting and Observability</h3>");
            out.println(
                    "      <p>InteropHub will generate dashboards showing: active developers, active clients, API usage frequency, operations executed, and unused endpoints. This information helps improve Connectathon design.</p>");
            out.println("    </section>");

            // ── Phase 1 Summary ────────────────────────────────────────────────────
            out.println("    <section class=\"panel doc-section\">");
            out.println("      <h2>Phase 1 Summary</h2>");
            out.println("      <table>");
            out.println("        <thead><tr><th>Feature</th><th>Required</th></tr></thead>");
            out.println("        <tbody>");
            out.println("          <tr><td>User registration</td><td>Yes</td></tr>");
            out.println("          <tr><td>Email verification</td><td>Yes</td></tr>");
            out.println("          <tr><td>OAuth client creation</td><td>Yes</td></tr>");
            out.println("          <tr><td>Token endpoint</td><td>Yes</td></tr>");
            out.println("          <tr><td>JWT token issuance</td><td>Yes</td></tr>");
            out.println("          <tr><td>Token validation by APIs</td><td>Yes</td></tr>");
            out.println("          <tr><td>Usage event logging</td><td>Yes</td></tr>");
            out.println("        </tbody>");
            out.println("      </table>");
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
