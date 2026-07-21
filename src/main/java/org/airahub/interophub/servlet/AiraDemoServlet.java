package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Properties;
import org.immregistries.aira.web.AiraDefaults;
import org.immregistries.aira.web.AiraEnvironmentConfig;
import org.immregistries.aira.web.AiraLogo;
import org.immregistries.aira.web.AiraPage;

public class AiraDemoServlet extends HttpServlet {
    private static final String APPLICATION_NAME = "InteropHub";
    private static final String APPLICATION_VERSION = resolveVersion();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        AiraPage page = AiraPage.builder()
                .applicationName(APPLICATION_NAME)
                .applicationSubtitle("AIRA Web integration preview")
                .applicationVersion(APPLICATION_VERSION)
                .documentTitle("AIRA Web Integration Demo - InteropHub")
                .pageHeading("AIRA Web Integration Demo")
                .pageIntro(
                        "This page verifies InteropHub can render the shared AIRA Web shell and styles from a local Maven dependency.")
                .mainClass("aira-main interophub-demo-main")
                .contextPath(request.getContextPath())
                .identityHref("/home")
                .logo(new AiraLogo(AiraDefaults.DEFAULT_LOGO_PATH, AiraDefaults.DEFAULT_LOGO_ALT_TEXT))
                .addGlobalAction("InteropHub Home", "/home", "secondary")
                .environment(new AiraEnvironmentConfig("Local", "Unauthenticated local integration demo"))
                .addLocalStylesheet("/css/aira-demo.css")
                .build();

        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);

            out.println("    <div class=\"aira-container--wide aira-stack aira-stack--loose\">");
            out.println("      <section class=\"aira-panel\" aria-labelledby=\"demo-purpose\">");
            out.println("        <h2 class=\"aira-section-title\" id=\"demo-purpose\">Purpose</h2>");
            out.println(
                    "        <p class=\"interophub-demo-note\">This is an unauthenticated AIRA Web integration demo. It does not require login, session state, or application data.</p>");
            out.println("      </section>");

            out.println("      <section aria-labelledby=\"demo-components\" class=\"aira-stack\">");
            out.println("        <h2 class=\"aira-section-title\" id=\"demo-components\">Component verification</h2>");
            out.println("        <div class=\"aira-card-grid\">");
            out.println("          <article class=\"aira-card interophub-demo-card\">");
            out.println("            <div class=\"aira-card__body aira-stack aira-stack--compact\">");
            out.println("              <div class=\"aira-cluster aira-cluster--between\">");
            out.println("                <h3 class=\"aira-card__title\">Hello world from InteropHub</h3>");
            out.println("                <span class=\"aira-badge aira-badge--success\">AIRA Web loaded</span>");
            out.println("              </div>");
            out.println(
                    "              <p>This card uses AIRA Web shared styles to confirm components render correctly inside InteropHub.</p>");
            out.println("            </div>");
            out.println("            <div class=\"aira-card__footer interophub-demo-actions\">");
            out.println(
                    "              <a class=\"aira-button aira-button--primary\" href=\"#demo-components\">Primary action</a>");
            out.println(
                    "              <a class=\"aira-button aira-button--secondary\" href=\"#demo-purpose\">Secondary action</a>");
            out.println("            </div>");
            out.println("          </article>");
            out.println("        </div>");
            out.println("      </section>");

            out.println(
                    "      <section class=\"aira-alert aira-alert--info\" role=\"status\" aria-live=\"polite\" aria-label=\"Integration status\">");
            out.println("        <p class=\"aira-alert__title\">Integration status</p>");
            out.println("        <p>InteropHub is serving the shared stylesheet from <code>"
                    + escapeHtml(request.getContextPath())
                    + "/aira/css/aira.css</code> and loading a local stylesheet afterward for scoped demo tweaks.</p>");
            out.println("      </section>");

            out.println("    </div>");

            page.writeEnd(out);
        }
    }

    private static String resolveVersion() {
        String appVersion = readVersionFromProperties("/interophub-version.properties", "software.version");
        if (appVersion != null && !appVersion.startsWith("${")) {
            return appVersion;
        }

        String pomVersion = readVersionFromProperties("/META-INF/maven/org.airahub/interophub/pom.properties",
                "version");
        if (pomVersion != null) {
            return pomVersion;
        }

        return "development";
    }

    private static String readVersionFromProperties(String path, String key) {
        Properties properties = new Properties();
        try (InputStream in = AiraDemoServlet.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            properties.load(in);
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } catch (IOException ex) {
            return null;
        }
    }

    private static String escapeHtml(String value) {
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