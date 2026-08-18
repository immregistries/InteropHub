package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.util.List;

/**
 * Renders the right-rail "menu" card for the active {@link AdminSection}.
 * This is the single shared source of the admin sub-navigation - every admin
 * servlet calls this instead of building or duplicating its own copy of the
 * menu, so the item list only ever needs updating in one place.
 */
final class AdminSectionNavRenderer {

    private AdminSectionNavRenderer() {
    }

    record NavItem(String label, String href) {
    }

    record NavGroup(String title, List<NavItem> items) {
    }

    static void render(PrintWriter out, String contextPath, AdminSection section, String activeHref) {
        out.println("        <aside class=\"aira-right-rail\" aria-label=\"" + section.label + " admin menu\">");
        out.println("          <section class=\"aira-section-card\">");
        out.println("            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">"
                + escapeHtml(section.label) + "</h2></div>");
        out.println("            <div class=\"aira-section-card__body aira-stack aira-stack--compact\">");
        for (NavGroup group : groupsFor(section)) {
            if (group.title() != null) {
                out.println("              <h3 class=\"aira-subsection-title\">" + escapeHtml(group.title())
                        + "</h3>");
            }
            for (NavItem item : group.items()) {
                String href = contextPath + item.href();
                String ariaCurrent = href.equals(activeHref) ? " aria-current=\"page\"" : "";
                out.println("              <a class=\"aira-recent-topic\" href=\"" + href + "\"" + ariaCurrent
                        + ">");
                out.println("                <span class=\"aira-recent-topic__title\">" + escapeHtml(item.label())
                        + "</span>");
                out.println("              </a>");
            }
        }
        out.println("            </div>");
        out.println("          </section>");
        out.println("        </aside>");
    }

    private static List<NavGroup> groupsFor(AdminSection section) {
        return switch (section) {
            case PLATFORM -> List.of(new NavGroup(null, List.of(
                    new NavItem("Admin Home", "/admin"),
                    new NavItem("Hub Settings", "/admin/settings"),
                    new NavItem("App Registry", "/admin/apps"),
                    new NavItem("App Login Statistics", "/admin/app-login-stats"),
                    new NavItem("QR Generator", "/admin/qr"))));
            case PEOPLE -> List.of(new NavGroup(null, List.of(
                    new NavItem("Registered Users", "/admin/users"),
                    new NavItem("Emails", "/admin/emails"),
                    new NavItem("Workspaces", "/admin/workspaces"))));
            case CONTENT -> List.of(new NavGroup(null, List.of(
                    new NavItem("IG Topics", "/admin/topics"))));
            case TOPIC_SPACES -> List.of(
                    new NavGroup("Structure & Taxonomy", List.of(
                            new NavItem("Topic Spaces", "/admin/es/topic-spaces"),
                            new NavItem("Neighborhoods", "/admin/es/neighborhoods"),
                            new NavItem("Stages", "/admin/es/stages"),
                            new NavItem("Advancement Paths", "/admin/es/paths"),
                            new NavItem("Topic Boards", "/admin/es/topic-boards"))),
                    new NavGroup("Topics & Campaigns", List.of(
                            new NavItem("ES Topics", "/admin/es/topics"),
                            new NavItem("Campaigns", "/admin/es/campaigns"),
                            new NavItem("ES Topic Import", "/admin/es-topic-import"),
                            new NavItem("Supporters", "/admin/es/supporters"))),
                    new NavGroup("Meetings", List.of(
                            new NavItem("Meetings", "/admin/es/meetings"),
                            new NavItem("Meeting Polls", "/admin/es/meeting-polls"),
                            new NavItem("Meeting Surveys", "/admin/es/meeting-survey"))),
                    new NavGroup("Feedback & Sync", List.of(
                            new NavItem("Surveys", "/admin/es/surveys"),
                            new NavItem("Review Results", "/admin/es/review-results"),
                            new NavItem("Registrations Display", "/admin/es/registrations"),
                            new NavItem("Dandelion Sync", "/admin/es/dandelion-sync"))));
        };
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
