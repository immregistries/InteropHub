package org.airahub.interophub.servlet;

import java.io.PrintWriter;

/**
 * Visual 6 (the InteropHub bridge) plus a "Why InteropHub?" link, shown on
 * both the anonymous and signed-in {@code WelcomeServlet} views so they point
 * to {@link WhyInteropHubServlet} the same way.
 */
final class WhyInteropHubTeaserRenderer {
    static final String VISUAL_6_ALT = "Children cross a modest bridge labeled InteropHub between the community "
            + "and an organized landscape of topics.";

    private WhyInteropHubTeaserRenderer() {
    }

    static void render(PrintWriter out, String contextPath, String indent) {
        out.println(indent + "<div class=\"aira-stack\">");
        out.println(indent + "  <figure class=\"aira-figure\">");
        out.println(indent + "    <div class=\"aira-figure__frame\">");
        out.println(indent + "      <img class=\"aira-figure__image\" src=\"" + contextPath
                + WhyInteropHubServlet.IMAGE_DIR + "visual-6-bridge.webp\" alt=\"" + VISUAL_6_ALT
                + "\" width=\"1280\" height=\"720\" loading=\"lazy\" />");
        out.println(indent + "    </div>");
        out.println(indent + "  </figure>");
        out.println(indent + "  <div class=\"aira-action-group\">");
        out.println(indent + "    <a class=\"aira-button aira-button--primary\" href=\"" + contextPath
                + WhyInteropHubServlet.PATH + "\">Why InteropHub?</a>");
        out.println(indent + "    <p class=\"aira-meta\">How InteropHub helps organizations sustain more "
                + "interoperability work and share responsibility for moving it forward.</p>");
        out.println(indent + "  </div>");
        out.println(indent + "</div>");
    }
}
