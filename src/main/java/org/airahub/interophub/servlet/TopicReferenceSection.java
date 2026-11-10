package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.TopicReferenceSectionData;
import org.airahub.interophub.service.TopicReferenceSectionData.Attention;
import org.airahub.interophub.service.TopicReferenceSectionData.Count;
import org.airahub.interophub.service.TopicReferenceSectionData.FlagLine;
import org.airahub.interophub.service.TopicReferenceSectionData.Guide;
import org.airahub.interophub.service.TopicReferenceSectionData.Link;
import org.airahub.interophub.service.TopicReferenceSectionData.View;

/** Topic Reference in the topic page's main column. The right rail does not render this. */
final class TopicReferenceSection {
    private static final Logger LOGGER = Logger.getLogger(TopicReferenceSection.class.getName());

    private TopicReferenceSection() {
    }

    static void render(PrintWriter out, String contextPath, User viewer, EsTopic topic, EsTopicSpace space) {
        render(out, contextPath, viewer, topic, space, TopicReferenceSectionData::load);
    }

    static void render(PrintWriter out, String contextPath, User viewer, EsTopic topic, EsTopicSpace space, Loader loader) {
        if (out == null || loader == null || viewer == null || topic == null || topic.getEsTopicId() == null) {
            return;
        }
        try {
            View view = loader.load(viewer, topic, space, contextPath);
            if (view == null || view.guides().isEmpty()) {
                return;
            }
            write(out, view);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Topic Reference section failed for topic "
                    + (topic == null ? "?" : topic.getEsTopicId()), ex);
        }
    }

    static void write(PrintWriter out, View view) {
        if (out == null || view == null || view.guides().isEmpty()) {
            return;
        }
        out.println("          <section class=\"aira-section-card\" aria-labelledby=\"topic-reference-title\">");
        out.println("            <div class=\"aira-section-card__header\">");
        out.println("              <div class=\"aira-cluster\">");
        out.println("                <h2 class=\"aira-section-card__title\" id=\"topic-reference-title\">Topic Reference</h2>");
        out.println("                <span class=\"aira-badge aira-badge--subtle\">Editors only</span>");
        out.println("              </div>");
        if (view.editor()) {
            out.println("              <div class=\"aira-cluster\">");
            out.println("                <a class=\"aira-section-card__action\" href=\"" + escape(view.commentsHref())
                    + "\">Manage comments</a>");
            out.println("                <a class=\"aira-section-card__action\" href=\"" + escape(view.settingsHref())
                    + "\">Guide settings</a>");
            out.println("              </div>");
        }
        out.println("            </div>");
        out.println("            <div class=\"aira-section-card__body aira-stack\">");
        if (view.extraGuides() > 0) {
            String more = view.extraGuides() == 1 ? "1 more guide" : view.extraGuides() + " more guides";
            out.println("              <p><a class=\"aira-inline-link\" href=\"" + escape(view.settingsHref()) + "\">"
                    + escape(more) + "</a></p>");
        }
        for (Guide guide : view.guides()) {
            guide(out, guide);
        }
        out.println("            </div>");
        out.println("          </section>");
    }

    private static void guide(PrintWriter out, Guide guide) {
        out.println("              <div class=\"aira-stack aira-stack--compact topic-reference-guide\">");
        out.println("                <div class=\"aira-cluster aira-cluster--between\">");
        out.println("                  <div class=\"aira-stack aira-stack--compact\">");
        out.println("                    <div class=\"aira-cluster\">");
        out.println("                      <h3 class=\"aira-subsection-title\">" + escape(guide.title()) + "</h3>");
        out.println("                      <span class=\"aira-badge aira-badge--subtle\">" + escape(guide.hostingBadge())
                + "</span>");
        out.println("                    </div>");
        out.println("                    <div class=\"aira-chip-list\">");
        for (Link link : guide.links()) {
            String css = link.primary()
                    ? "aira-button aira-button--primary aira-button--small"
                    : "aira-chip";
            out.println("                      <a class=\"" + css + "\" href=\"" + escape(link.href()) + "\">"
                    + escape(link.label()) + "</a>");
        }
        out.println("                    </div>");
        if (guide.externalOnPrivate()) {
            out.println("                    <p class=\"aira-meta\">This topic is private and this guide is published externally. Host it on InteropHub instead.</p>");
        }
        out.println("                  </div>");
        out.println("                  <div class=\"aira-stack aira-stack--compact topic-reference-stats\">");
        out.println("                    <div class=\"aira-cluster topic-reference-stats__row\">");
        stat(out, guide.open());
        stat(out, guide.disposed());
        stat(out, guide.closed());
        stat(out, guide.flags());
        out.println("                    </div>");
        if (guide.activity() != null) {
            out.println("                    <p class=\"aira-meta\">" + escape(guide.activity()) + "</p>");
        }
        out.println("                  </div>");
        out.println("                </div>");
        if (!guide.needsAttention().isEmpty()) {
        out.println("                <div class=\"aira-stack aira-stack--compact\">");
        out.println("                  <h3 class=\"aira-subsection-title\">Needs attention</h3>");
        for (Attention attention : guide.needsAttention()) {
            out.println("                  <a class=\"aira-recent-topic\" href=\"" + escape(attention.href()) + "\">");
            out.println("                    <span class=\"aira-recent-topic__title\">" + escape(attention.excerpt())
                    + "</span>");
            out.println("                    <span class=\"aira-recent-topic__meta\">" + escape(attention.meta())
                    + "</span>");
            out.println("                  </a>");
        }
        if (guide.openTotal() > guide.needsAttention().size()) {
            out.println("                  <a class=\"aira-inline-link\" href=\"" + escape(guide.open().href())
                    + "\">View all " + guide.openTotal() + " open comments</a>");
        }
        out.println("                </div>");
        }
        if (guide.flagTotal() > 0) {
            out.println("                <div class=\"aira-stack aira-stack--compact\">");
            out.println("                  <h3 class=\"aira-subsection-title\">Flagged for review</h3>");
            for (FlagLine flag : guide.flagged()) {
                out.println("                  <a class=\"aira-recent-topic\" href=\"" + escape(flag.href()) + "\">");
                out.println("                    <span class=\"aira-recent-topic__title\">" + escape(flag.note())
                        + "</span>");
                out.println("                    <span class=\"aira-recent-topic__meta\">" + escape(flag.meta())
                        + "</span>");
                out.println("                  </a>");
            }
            int moreFlags = guide.flagTotal() - guide.flagged().size();
            if (moreFlags > 0) {
                out.println("                  <a class=\"aira-inline-link\" href=\"" + escape(guide.manageHref())
                        + "\">+" + moreFlags + " more</a>");
            }
            out.println("                </div>");
        }
        out.println("              </div>");
    }

    private static void stat(PrintWriter out, Count count) {
        out.println("                      <a class=\"topic-reference-stat\" href=\"" + escape(count.href()) + "\">");
        out.println("                        <span class=\"topic-reference-stat__value\">" + count.value() + "</span>");
        out.println("                        <span class=\"topic-reference-stat__label\">" + escape(count.label())
                + "</span>");
        out.println("                      </a>");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    interface Loader {
        View load(User viewer, EsTopic topic, EsTopicSpace space, String contextPath);
    }
}
