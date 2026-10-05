package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.immregistries.aira.web.AiraPage;

/**
 * Public, static "Why InteropHub?" page: the illustrated organizational
 * backstory explaining why InteropHub exists and why an organization might
 * use it. Available without signing in. Linked from both welcome page views
 * through {@link WhyInteropHubTeaserRenderer}.
 */
public class WhyInteropHubServlet extends HttpServlet {
    static final String PATH = "/why-interophub";
    static final String IMAGE_DIR = "/image/why-interophub/";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String contextPath = request.getContextPath();
        response.setContentType("text/html;charset=UTF-8");
        AiraPage page = InteropAiraPageFactory.base(request, "Why InteropHub? - InteropHub")
                .applicationSubtitle("Why InteropHub?")
                .mainClass("aira-main")
                .build();
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            renderContent(out, contextPath);
            out.println(InteropAiraPageFactory.headerSearchScriptTag(contextPath));
            page.writeEnd(out);
        }
    }

    private void renderContent(PrintWriter out, String contextPath) {
        out.println("      <div class=\"aira-container--narrow\">");
        out.println("        <div class=\"aira-page-header\">");
        out.println("          <div>");
        out.println("            <h1 class=\"aira-public-title\">InteropHub: From meetings to sustained community "
                + "progress</h1>");
        out.println("            <p class=\"aira-public-intro\">Interoperability advances when people can work "
                + "together over time. InteropHub connects topics, people, meetings, resources, and outcomes so "
                + "organizations can sustain more of that work&mdash;and help more people share responsibility for "
                + "moving it forward.</p>");
        out.println("          </div>");
        out.println("        </div>");

        out.println("        <div class=\"aira-stack aira-stack--loose\">");

        renderSection(out, contextPath, "Interoperability is work we do together",
                "visual-2-hl7-v2-and-fhir.webp",
                "IIS and EHR buildings exchange HL7 v2 messages, with FHIR appearing on the horizon.",
                "Behind every successful exchange of health information is a community that has agreed on how to "
                        + "make it work. Public health programs, jurisdictions, vendors, and standards experts bring "
                        + "different responsibilities and experience. Progress depends on their ability to "
                        + "understand one another, resolve questions, and develop a shared technical direction.",
                "At the American Immunization Registry Association (AIRA), this work includes supporting the "
                        + "community around immunization information systems. Established HL7 v2 exchange connects "
                        + "clinical and public health systems today. Exploring FHIR introduced additional "
                        + "possibilities&mdash;and a growing set of questions about where those possibilities could "
                        + "help.",
                "That exploration helped shape InteropHub. The need it addresses extends across interoperability: "
                        + "communities need a dependable way to organize their work, involve the right people, and "
                        + "carry progress from one conversation to the next.");

        renderSection(out, contextPath, "More possibilities require more capacity",
                "visual-3-scaling-problem.webp",
                "A growing community gathers across a river from a wide field of topics, with no bridge connecting "
                        + "them.",
                "What initially sounded like one transition opened into many possible directions. AIRA's Emerging "
                        + "Standards discussions grew to encompass more than a hundred topics and a widening "
                        + "community of interested people. Each topic could bring its own questions, stakeholders, "
                        + "meetings, and next steps.",
                "Modernization asks organizations to sustain this kind of breadth. Advancing a dozen related "
                        + "initiatives at once requires more than repeating the effort used to advance one. People "
                        + "need to find relevant work, facilitators need to connect expertise with questions, and "
                        + "each initiative needs enough continuity to keep moving.",
                "The community may already have the knowledge and interest. The organizational challenge is giving "
                        + "that distributed capacity a structure through which it can contribute.");

        renderSection(out, contextPath, "One person can hold a great deal together",
                "visual-4-default-approach.webp",
                "A concerned child holds four ropes connecting a wiki, spreadsheets, email, and meetings.",
                "Community work often succeeds because someone takes responsibility for making it happen. That "
                        + "person knows the history, remembers who should be involved, prepares the agenda, recruits "
                        + "presenters, and follows up afterward. Their relationships and judgment are essential.",
                "The information supporting that work is often spread across email, spreadsheets, wiki pages, "
                        + "documents, and meeting notes. Each tool serves a purpose. The coordinator supplies the "
                        + "connections between them.",
                "As the portfolio grows, more of that person's attention goes toward maintaining those connections. "
                        + "A pause can mean reconstructing the history. A handoff can require explaining what "
                        + "happened, why it happened, and what remains unresolved. Another promising initiative "
                        + "becomes another demand on the same limited capacity.",
                "The question is how to extend a capable person's leadership while making the work easier for "
                        + "others to carry forward.");

        renderSection(out, contextPath, "Give the work a shared, lasting home",
                "visual-5-shared-home.webp",
                "The same child comfortably holds one cord to InteropHub, which connects topics, followers, and "
                        + "meetings.",
                "InteropHub brings familiar activities into one connected structure. Topics connect to the people "
                        + "who follow them, the meetings where they are discussed, and the resources and outcomes "
                        + "that give the work context.",
                "The topic is the lasting home for the work. A meeting is one occasion for moving it forward. When "
                        + "a discussion produces a direction, an open question, an action, or a reason for a choice, "
                        + "that contribution belongs with the topic so the next discussion can build on it.",
                "This gives facilitators a repeatable way to prepare and continue the work: make the question "
                        + "visible, identify interested people, involve contributors early, hold a focused "
                        + "discussion, and preserve what changed. Followers provide a starting point for outreach, "
                        + "while facilitators continue to identify others whose perspectives are needed.",
                "The coordinator still exercises judgment and builds relationships. A shared record allows "
                        + "colleagues and community contributors to take on defined responsibilities with the "
                        + "context they need. If work pauses or leadership changes, the group has somewhere to "
                        + "resume.");

        renderSection(out, contextPath, "Build the capacity to move more work forward",
                "visual-6-bridge.webp",
                WhyInteropHubTeaserRenderer.VISUAL_6_ALT,
                "The purpose of InteropHub is to help organizations scale and replicate effective community work. "
                        + "A facilitator can support a broader portfolio when every initiative does not require them "
                        + "to personally maintain every connection. Colleagues can share meeting responsibilities. "
                        + "Contributors can develop into topic leaders. New efforts can begin with an established "
                        + "way of working.",
                "That capacity comes from software and practice together: clear responsibilities, thoughtful "
                        + "preparation, useful records, and people willing to carry the next step. InteropHub "
                        + "provides a structure for making those practices easier to repeat across related efforts.",
                "Over time, this approach can help promising ideas develop into clearer use cases, shared technical "
                        + "directions, and projects with a community prepared to act. Funding can then support "
                        + "concentrated development and implementation on an existing foundation of understanding "
                        + "and participation.",
                "The ambition is to help an organization sustain a dozen interoperability initiatives with the "
                        + "continuity and attention each deserves. The software should recede into the background, "
                        + "leaving the community's work visible: what matters, what has been learned, what remains "
                        + "open, and who can help move it forward.");

        out.println("          <section class=\"aira-panel\">");
        out.println("            <p class=\"aira-section-title\"><strong>Make community work easier to organize, "
                + "easier to share, easier to continue, and harder to lose.</strong></p>");
        out.println("          </section>");

        out.println("        </div>");
        out.println("      </div>");
    }

    private void renderSection(PrintWriter out, String contextPath, String heading, String imageFile,
            String altText, String... paragraphs) {
        out.println("          <section class=\"aira-panel\">");
        out.println("            <div class=\"aira-stack\">");
        out.println("              <h2 class=\"aira-section-title\">" + heading + "</h2>");
        out.println("              <figure class=\"aira-figure\">");
        out.println("                <div class=\"aira-figure__frame\">");
        out.println("                  <img class=\"aira-figure__image\" src=\"" + contextPath + IMAGE_DIR + imageFile
                + "\" alt=\"" + altText + "\" width=\"1280\" height=\"720\" loading=\"lazy\" />");
        out.println("                </div>");
        out.println("              </figure>");
        out.println("              <div class=\"aira-copy-block\">");
        for (String paragraph : paragraphs) {
            out.println("                <p>" + paragraph + "</p>");
        }
        out.println("              </div>");
        out.println("            </div>");
        out.println("          </section>");
    }
}
