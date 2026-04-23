package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.util.List;

public final class AdminNavRenderer {

    private AdminNavRenderer() {
    }

    public static void renderPanel(PrintWriter out, String contextPath) {
        out.println("    <h2>Admin Navigation</h2>");
        out.println("    <div class=\"admin-nav-panel\">");

        renderGroup(out, "Platform", List.of(
                navItem(contextPath + "/admin", "Admin Home"),
                navItem(contextPath + "/admin/settings", "Hub Settings"),
                navItem(contextPath + "/admin/apps", "App Registry"),
                navItem(contextPath + "/admin/app-login-stats", "App Login Statistics")));

        renderGroup(out, "People and Collaboration", List.of(
                navItem(contextPath + "/admin/users", "Registered Users"),
                navItem(contextPath + "/admin/workspaces", "Workspaces")));

        renderGroup(out, "Content", List.of(
                navItem(contextPath + "/admin/topics", "IG Topics")));

        renderGroup(out, "Emerging Standards", List.of(
                navItem(contextPath + "/admin/es", "ES Home"),
                navItem(contextPath + "/admin/es/campaigns", "Campaigns"),
                navItem(contextPath + "/admin/es/topics", "ES Topics"),
                navItem(contextPath + "/admin/es/registrations", "Registrations Display"),
                navItem(contextPath + "/admin/es/review-results", "Review Results")));

        renderGroup(out, "Utilities", List.of(
                navItem(contextPath + "/admin/es-topic-import", "ES Topic Import"),
                navItem(contextPath + "/admin/qr", "QR Generator")));

        out.println("    </div>");
    }

    private static void renderGroup(PrintWriter out, String title, List<NavItem> links) {
        out.println("      <section class=\"admin-nav-group\">");
        out.println("        <h2>" + escapeHtml(title) + "</h2>");
        out.println("        <ul>");
        for (NavItem item : links) {
            out.println("          <li><a href=\"" + item.href + "\">" + escapeHtml(item.label) + "</a></li>");
        }
        out.println("        </ul>");
        out.println("      </section>");
    }

    private static NavItem navItem(String href, String label) {
        return new NavItem(href, label);
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

    private static class NavItem {
        private final String href;
        private final String label;

        private NavItem(String href, String label) {
            this.href = href;
            this.label = label;
        }
    }
}
