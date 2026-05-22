package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;

final class AdminShellRenderer {

    private AdminShellRenderer() {
    }

    static void render(PrintWriter out, String title, String contextPath, ContentRenderer contentRenderer)
            throws IOException {
        String pageHeading = extractPageHeading(title);
        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("  <meta charset=\"UTF-8\" />");
        out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
        out.println("  <title>" + escapeHtml(title) + "</title>");
        out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
        out.println("</head>");
        out.println("<body class=\"admin-page\">");
        out.println("  <main class=\"admin-shell\">");
        out.println("    <aside class=\"admin-rail\">");
        out.println("      <h1>Admin</h1>");
        out.println("      <p class=\"admin-rail-subtitle\">Navigation center</p>");
        AdminNavRenderer.renderPanel(out, contextPath);
        out.println("      <p class=\"admin-rail-footer-link\"><a href=\"" + contextPath
                + "/welcome\">Back to Welcome</a></p>");
        out.println("    </aside>");
        out.println("    <section class=\"admin-main\">");
        out.println("      <section class=\"panel admin-page-intro\">");
        out.println("        <h2>" + escapeHtml(pageHeading) + "</h2>");
        out.println(
                "        <p>This page helps administrators manage this area of InteropHub. Replace this placeholder text with page-specific guidance.</p>");
        out.println("      </section>");
        contentRenderer.render(out);
        out.println("    </section>");
        out.println("  </main>");
        PageFooterRenderer.render(out);
        out.println("</body>");
        out.println("</html>");
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

    private static String extractPageHeading(String title) {
        if (title == null || title.isBlank()) {
            return "Admin Page";
        }
        String normalized = title.trim();
        if (normalized.endsWith(" - InteropHub")) {
            normalized = normalized.substring(0, normalized.length() - " - InteropHub".length()).trim();
        }
        return normalized.isBlank() ? "Admin Page" : normalized;
    }

    @FunctionalInterface
    interface ContentRenderer {
        void render(PrintWriter out) throws IOException;
    }
}