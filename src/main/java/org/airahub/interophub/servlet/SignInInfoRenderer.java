package org.airahub.interophub.servlet;

import java.io.PrintWriter;

/**
 * Shared "what InteropHub is and what signing in gets you" content, used by
 * both the anonymous {@code WelcomeServlet} view and {@code HomeServlet} so
 * the two pages stay in sync instead of carrying separate copies of the
 * same explanatory copy.
 */
final class SignInInfoRenderer {
    private SignInInfoRenderer() {
    }

    static void renderIntroParagraphs(PrintWriter out) {
        out.println(
                "            <p class=\"aira-public-intro\"><strong>Discover and participate in immunization interoperability work.</strong></p>");
        out.println(
                "            <p class=\"aira-public-intro\">InteropHub connects topics, meetings, people, resources, and testing systems used by the immunization interoperability community. Public topics and materials are available without signing in. Sign in when you want to follow work, participate in meetings, connect with other participants, or use demonstration and testing systems.</p>");
    }

    static void renderCapabilitiesSection(PrintWriter out) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">What signing in lets you do</h2>");
        out.println("            <div class=\"aira-grid\">");
        renderCapabilityCard(out, "Follow the work that matters to you",
                "Follow individual topics or recurring meeting series and receive relevant communications when that work is discussed or advanced.");
        renderCapabilityCard(out, "Participate in meetings and community work",
                "Indicate that you plan to attend, record attendance, contribute where participation is enabled, and remain connected to the topics discussed in a meeting.");
        renderCapabilityCard(out, "Find and connect with participants",
                "See relevant participants in the same community activities and allow others to recognize your involvement. This helps people find collaborators without depending entirely on informal networks.");
        renderCapabilityCard(out, "Use demonstration and testing systems",
                "Access AIRA demonstration applications and interoperability testing resources. Some systems may require additional authorization, terms, or setup.");
        renderCapabilityCard(out, "Access testing credentials and technical collaboration",
                "Where available, obtain credentials or API access for non-production testing and participate in organized interoperability activities, including future Interoperability Labs.");
        out.println("            </div>");
        out.println("          </section>");
    }

    private static void renderCapabilityCard(PrintWriter out, String title, String description) {
        out.println("              <article class=\"aira-card\">");
        out.println("                <div class=\"aira-card__body\">");
        out.println("                  <h3 class=\"aira-card__title\">" + escapeHtml(title) + "</h3>");
        out.println(
                "                  <p class=\"aira-card__description\">" + escapeHtml(description) + "</p>");
        out.println("                </div>");
        out.println("              </article>");
    }

    static void renderDetailsSection(PrintWriter out) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <h2 class=\"aira-section-title\">More about signing in</h2>");
        out.println("            <div class=\"aira-stack aira-stack--compact\">");
        out.println("              <details>");
        out.println("                <summary>How passwordless sign-in works</summary>");
        out.println(
                "                <p>InteropHub uses email links instead of passwords. Enter your email address, complete registration if this is your first visit, and use the link we send to sign in. Once signed in, your session can remain active for 30 days and is renewed as you continue to use InteropHub.</p>");
        out.println("              </details>");
        out.println("              <details>");
        out.println("                <summary>What information other participants may see</summary>");
        out.println(
                "                <p>Your name, organization, role, and participation in relevant community activities may be visible to other signed-in participants. Depending on the activity, this may include following a topic, attending a meeting, or participating in an interoperability collaboration. This visibility helps the community identify who is interested and involved.</p>");
        out.println("              </details>");
        out.println("              <details>");
        out.println("                <summary>Testing use and activity information</summary>");
        out.println(
                "                <p>InteropHub and connected demonstration systems are for interoperability development, experimentation, and testing&mdash;not production use. Users must not submit protected health information, production data, real credentials, confidential information, or other sensitive or regulated data.</p>");
        out.println(
                "                <p>AIRA records account, participation, and system-usage information needed to operate the service, assist users, improve systems, analyze activity, troubleshoot problems, prevent misuse, communicate with participants, and report aggregate community value.</p>");
        out.println("              </details>");
        out.println("            </div>");
        out.println("          </section>");
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
