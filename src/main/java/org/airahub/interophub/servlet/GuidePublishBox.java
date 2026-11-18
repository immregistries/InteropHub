package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.util.List;
import org.airahub.interophub.service.GuideHelp;
import org.airahub.interophub.service.GuideHtmlRules;
import org.airahub.interophub.service.GuideRows.DocumentRow;

/**
 * "Publish from your agent" on the guide settings page ({@code /es/topic-guide/{topicId}}): for each
 * registered guide, the document key, the hub address, the hosting, and the exact commands an agent
 * runs to publish it. Editors only, like the rest of that page.
 */
final class GuidePublishBox {
    static final String ANCHOR = "publish-from-your-agent";
    static final String TOOL = "python process/tools/publish_guide_to_hub.py output/guide/index.html";

    private GuidePublishBox() {
    }

    static void render(PrintWriter out, List<DocumentRow> documents, String hub) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        out.println("      <section class=\"aira-stack\" aria-labelledby=\"" + ANCHOR + "\">");
        out.println("      <h2 class=\"aira-section-title\" id=\"" + ANCHOR + "\">Publish from your agent</h2>");
        out.println("      <p>An agent with your personal access token in <code>INTEROPHUB_TOKEN</code> runs these"
                + " commands from the guide's project folder. Replace <code>vX.Y.Z</code> with the release. "
                + "<a class=\"aira-inline-link\" href=\"" + escape(GuideHelp.page()) + "\">Help for authors and"
                + " agents</a></p>");
        for (DocumentRow document : documents) {
            box(out, document, hub);
        }
        out.println("      </section>");
    }

    static void box(PrintWriter out, DocumentRow document, String hub) {
        String hosting = hosting(document);
        out.println("      <div class=\"aira-panel aira-stack aira-stack--compact\" id=\"" + ANCHOR + "-"
                + escape(document.documentKey) + "\">");
        out.println("        <p><strong>Document key:</strong> <code>" + escape(document.documentKey)
                + "</code><br><strong>Hub:</strong> <code>" + escape(hub) + "</code><br><strong>Hosting:</strong> <code>"
                + escape(hosting) + "</code>" + (document.active ? "" : " <span class=\"aira-badge aira-badge--warning\">Inactive</span>")
                + "</p>");
        for (String command : commands(document.documentKey, hosting, hub)) {
            out.println("        <pre class=\"aira-code-block aira-code-block--wrap\" tabindex=\"0\"><code>"
                    + escape(command) + "</code></pre>");
        }
        out.println("        <p class=\"aira-field-help\">The tool reads the hub and document key from <code>guide.json</code>"
                + " (<code>comments.hub</code>, and <code>comments.document</code> or <code>id</code>). If they are not"
                + " there, add <code>--hub " + escape(hub) + " --document " + escape(document.documentKey)
                + "</code>. The last command makes the release the version readers are sent to.</p>");
        out.println("      </div>");
    }

    /** The commands for one guide: the publish command for its hosting, then making a release Current. */
    static List<String> commands(String documentKey, String hosting, String hub) {
        String publish = GuideHtmlRules.HUB.equals(hosting)
                ? TOOL + " --label vX.Y.Z --label latest"
                : TOOL + " --manifest-only --label vX.Y.Z";
        String current = "curl -X PATCH \"" + hub + "/api/guide/" + documentKey + "/labels/vX.Y.Z\" \\\n"
                + "  -H \"Authorization: Bearer $INTEROPHUB_TOKEN\" \\\n"
                + "  -H \"Content-Type: application/json\" \\\n"
                + "  -d '{\"display\": \"current\", \"commentMode\": \"release\"}'";
        return List.of(publish, current);
    }

    private static String hosting(DocumentRow document) {
        return GuideHtmlRules.HUB.equals(document.hosting) ? GuideHtmlRules.HUB : GuideHtmlRules.EXTERNAL;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
