package org.airahub.interophub.servlet;

/**
 * The set of topic-management views hosted at {@code /es/topic-manage/{topicId}/{slug}}.
 * Shared between {@link EsTopicManageServlet} (which renders them) and
 * {@link TopicManageNavRenderer} (which links to them from any page).
 */
enum TopicManageView {
    FOLLOWERS("followers", "Followers"),
    MEETINGS("meetings", "Meetings"),
    COMMENTS("comments", "Comments"),
    RELATIONSHIPS("relationships", "Relationships"),
    CURATED("curated", "Curated Topics"),
    SUPPORTERS("supporters", "Supporters");

    final String slug;
    final String label;

    TopicManageView(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    static TopicManageView fromSlug(String slug) {
        if (slug == null) {
            return null;
        }
        for (TopicManageView v : values()) {
            if (v.slug.equalsIgnoreCase(slug)) {
                return v;
            }
        }
        return null;
    }
}
