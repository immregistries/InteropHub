package org.airahub.interophub.servlet;

/**
 * The 4 top-level areas of the admin experience, shown as a dark context-nav
 * row (mirroring the "Topics | Meetings" pattern used elsewhere) on every
 * admin page. {@link AdminSectionNavRenderer} renders the right-rail menu for
 * whichever section is active.
 */
enum AdminSection {
    PLATFORM("Platform", "/admin"),
    PEOPLE("People", "/admin/users"),
    CONTENT("Content", "/admin/topics"),
    TOPIC_SPACES("Topic Spaces", "/admin/es");

    final String label;
    final String homeHref;

    AdminSection(String label, String homeHref) {
        this.label = label;
        this.homeHref = homeHref;
    }
}
