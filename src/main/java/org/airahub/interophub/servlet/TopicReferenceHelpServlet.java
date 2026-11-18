package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideHelp;
import org.immregistries.aira.web.AiraPage;

/**
 * Public help for Topic Reference authors and their AI agents: {@code /help/topic-reference-authors}.
 * No sign-in. Every other breadcrumb (API error {@code help} links, {@code GET /api/token}, the
 * account page, the guide settings page, the templates project) links to its anchors, which are
 * part of the API contract ({@link GuideHelp}). It is written to be read as text by an agent, and
 * it holds nothing secret: no token, no internal address beyond the public hub, and no names.
 */
public class TopicReferenceHelpServlet extends HttpServlet {
    static final String TITLE = "Topic Reference authors: tokens and agents";
    static final String TEMPLATES_URL = "https://github.com/immregistries/content-publishing-templates";

    @Override
    public void init() {
        GuideHelp.useHubSettings();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AiraPage page = InteropAiraPageFactory.base(request, TITLE + " - InteropHub")
                .applicationSubtitle("Help")
                .mainClass("aira-main")
                .build();
        render(request, response, page);
    }

    /** Writes the page in the given shell. Needs no session and no database beyond the hub base URL. */
    static void render(HttpServletRequest request, HttpServletResponse response, AiraPage page) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "public, max-age=300");
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            writeContent(out, GuideHelp.base());
            out.println(InteropAiraPageFactory.headerSearchScriptTag(request.getContextPath()));
            page.writeEnd(out);
        }
    }

    /** For tests: the shell without a signed-in user. */
    static AiraPage signedOutPage(HttpServletRequest request) {
        return InteropAiraPageFactory.base(request, TITLE + " - InteropHub", Optional.<User>empty(), false)
                .applicationSubtitle("Help")
                .mainClass("aira-main")
                .build();
    }

    /** The page body. {@code hub} is the public base URL, such as {@code https://example.org/hub}. */
    static void writeContent(PrintWriter out, String hub) {
        out.println("<div class=\"aira-container--narrow aira-stack aira-stack--loose aira-prose\">");
        out.println("<div class=\"aira-page-header\"><div>");
        out.println("<h1 class=\"aira-page-title\">" + escape(TITLE) + "</h1>");
        out.println("<p class=\"aira-page-intro\">A Topic Reference is a guide, a web page built with the"
                + " content-publishing-templates, that is registered to a topic in InteropHub. Readers comment on it."
                + " Its authors publish versions and answer the comments, often through an AI agent. This page says"
                + " what the author must do by hand, what an agent can do, and how to read an error. It is written"
                + " so that an agent can read it as text.</p>");
        out.println("</div></div>");

        out.println("<nav aria-label=\"On this page\"><ul>");
        out.println("<li><a href=\"#" + GuideHelp.WHO_CAN_AUTHOR + "\">Who can author</a></li>");
        out.println("<li><a href=\"#" + GuideHelp.CREATE_A_TOKEN + "\">Create a token</a></li>");
        out.println("<li><a href=\"#" + GuideHelp.WHERE_THE_TOKEN_GOES + "\">Where the token goes</a></li>");
        out.println("<li><a href=\"#" + GuideHelp.WHAT_AN_AGENT_CAN_DO + "\">What an agent can do</a></li>");
        out.println("<li><a href=\"#" + GuideHelp.IF_YOU_ARE_AN_AGENT + "\">If you are an agent</a></li>");
        out.println("<li><a href=\"#" + GuideHelp.TROUBLESHOOTING + "\">Troubleshooting</a></li>");
        out.println("</ul></nav>");

        whoCanAuthor(out, hub);
        createToken(out, hub);
        whereTheTokenGoes(out);
        whatAnAgentCanDo(out, hub);
        ifYouAreAnAgent(out, hub);
        troubleshooting(out, hub);
        out.println("</div>");
    }

    private static void whoCanAuthor(PrintWriter out, String hub) {
        String h = escape(hub);
        section(out, GuideHelp.WHO_CAN_AUTHOR, "Who can author");
        out.println("<p>An author is anyone who can edit the guide's topic in InteropHub:</p>");
        out.println("<ul>");
        out.println("<li>InteropHub administrators;</li>");
        out.println("<li>admins of the Topic Space the topic belongs to;</li>");
        out.println("<li>the topic's active champions and support subscribers.</li>");
        out.println("</ul>");
        out.println("<p>Anyone who can view the topic can read the guide and comment on it. Only authors can publish,"
                + " change a guide's settings, set review flags, and dispose of or close comments.</p>");
        out.println("<p><strong>To become an author,</strong> open the topic's page in InteropHub"
                + " (<code>" + h + "/es/topic/&lt;topicId&gt;</code>), select <strong>Send info / question</strong>,"
                + " and ask the topic's champions to add you. You can also ask an admin of the topic's Topic Space.</p>");
        relay(out, "Your InteropHub account is not an editor of this topic yet. Open the topic's page in InteropHub,"
                + " select Send info / question, and ask the topic's champions to add you as an editor. Tell me when"
                + " that is done.");
        out.println("</section>");
    }

    private static void createToken(PrintWriter out, String hub) {
        String h = escape(hub);
        section(out, GuideHelp.CREATE_A_TOKEN, "Create a token");
        out.println("<p>A personal access token lets a script or an agent act as you. Only you can create one,"
                + " because it needs your InteropHub sign-in.</p>");
        out.println("<ol>");
        out.println("<li>Sign in at <code>" + h + "/home</code>.</li>");
        out.println("<li>Open <strong>Account</strong> (<code>" + h + "/account</code>). Under <strong>Access"
                + " tokens</strong>, find <strong>Create a personal access token</strong>.</li>");
        out.println("<li>Enter a label that says where the token will live, such as <em>laptop agent</em> or"
                + " <em>GitHub publishing</em>.</li>");
        out.println("<li>Choose <strong>Author (publish guides and manage comments)</strong>. It ticks"
                + " <code>comments</code> and <code>topics:write</code>. Or tick the scopes yourself (table below).</li>");
        out.println("<li>Choose a lifetime: 30, 90, or 365 days. When it ends, create a new token.</li>");
        out.println("<li>Select <strong>Create token</strong>. The token is shown <strong>once</strong>. Put it"
                + " straight where it goes (next section). InteropHub cannot show it again. If you lose it, revoke"
                + " it and create another.</li>");
        out.println("</ol>");
        out.println("<div class=\"aira-table-wrap\"><table class=\"aira-table\">");
        out.println("<thead><tr><th scope=\"col\">Scope</th><th scope=\"col\">Allows</th></tr></thead><tbody>");
        out.println("<tr><td><code>comments</code></td><td>Read and answer comments on guides, and (for authors)"
                + " dispose of comments and set review flags.</td></tr>");
        out.println("<tr><td><code>topics:read</code></td><td>Read topic and guide information.</td></tr>");
        out.println("<tr><td><code>topics:write</code></td><td>Publish and manage guides on topics you edit."
                + " Includes <code>topics:read</code>.</td></tr>");
        out.println("</tbody></table></div>");
        out.println("<p>A token can never do more than your account can. Revoking it on the Account page stops it"
                + " at once. Personal access tokens start with <code>ihp_</code>. Tokens that start with"
                + " <code>ihg_</code> are made by a guide page when a reader signs in to comment; they allow only"
                + " <code>comments</code> and last 30 days.</p>");
        out.println("</section>");
    }

    private static void whereTheTokenGoes(PrintWriter out) {
        section(out, GuideHelp.WHERE_THE_TOKEN_GOES, "Where the token goes");
        out.println("<p>Tools read the token from an environment variable named <code>INTEROPHUB_TOKEN</code>."
                + " Set it yourself. Do not give the token to an agent in a chat.</p>");
        out.println("<h3>On your computer</h3>");
        out.println("<p>PowerShell, for this window:</p>");
        code(out, "PowerShell, this window", "$env:INTEROPHUB_TOKEN = \"<paste your token here>\"");
        out.println("<p>PowerShell, saved for windows you open later (Windows):</p>");
        code(out, "PowerShell, saved", "setx INTEROPHUB_TOKEN \"<paste your token here>\"");
        out.println("<p>bash or zsh, for this shell:</p>");
        code(out, "bash", "export INTEROPHUB_TOKEN=\"<paste your token here>\"");
        out.println("<p>To keep the token out of your shell history, let the shell prompt for it instead:</p>");
        code(out, "Prompt for the token",
                "# PowerShell 7\n$env:INTEROPHUB_TOKEN = Read-Host -MaskInput \"InteropHub token\"\n"
                        + "# bash or zsh\nread -rs INTEROPHUB_TOKEN && export INTEROPHUB_TOKEN");
        out.println("<p>A variable set with <code>setx</code>, or in another window, reaches only programs started"
                + " after it was set. Restart your agent from a new terminal so it sees the variable.</p>");
        out.println("<h3>For publishing from GitHub</h3>");
        out.println("<p>Store the token as the repository secret <code>INTEROPHUB_TOKEN</code>. The GitHub CLI asks"
                + " for the value, so it never appears on the command line:</p>");
        code(out, "GitHub secret", "gh secret set INTEROPHUB_TOKEN --repo <owner>/<repo>");
        out.println("<p>Or, on GitHub: the repository's <strong>Settings</strong>, <strong>Secrets and"
                + " variables</strong>, <strong>Actions</strong>, <strong>New repository secret</strong>.</p>");
        out.println("<h3>Never</h3>");
        out.println("<ul>");
        out.println("<li>Never paste a token into a chat, an issue, an email, or a guide.</li>");
        out.println("<li>Never commit a token, or put it in <code>guide.json</code>, a script, or any file in a"
                + " repository.</li>");
        out.println("<li>Never put a token in a URL. It goes only in the <code>Authorization: Bearer</code>"
                + " header.</li>");
        out.println("<li>Agents: never print, echo, or log the token or the variable. To check it, call"
                + " <code>GET /api/token</code>.</li>");
        out.println("</ul>");
        out.println("<p>If a token may have been exposed, revoke it on the Account page and create a new one.</p>");
        out.println("</section>");
    }

    private static void whatAnAgentCanDo(PrintWriter out, String hub) {
        String h = escape(hub);
        section(out, GuideHelp.WHAT_AN_AGENT_CAN_DO, "What an agent can do");
        out.println("<p>With the author's token, an agent can do everything below. Every request sends"
                + " <code>Authorization: Bearer</code> with the token from <code>INTEROPHUB_TOKEN</code>. JSON"
                + " bodies use <code>Content-Type: application/json</code>. Paths are under <code>" + h + "</code>."
                + " <code>{documentKey}</code> is the guide's <code>id</code> in <code>guide.json</code>.</p>");
        out.println("<div class=\"aira-table-wrap\"><table class=\"aira-table\">");
        out.println("<thead><tr><th scope=\"col\">Action</th><th scope=\"col\">Request</th>"
                + "<th scope=\"col\">Scope</th></tr></thead><tbody>");
        row(out, "Check the token: who it acts as, its scopes, expiry, and the guides it can publish",
                "GET /api/token", "any");
        row(out, "Register a guide on a topic", "POST /api/guides", "topics:write");
        row(out, "Change a registration (title, URLs, hosting, active)", "PATCH /api/guide/{documentKey}",
                "topics:write");
        row(out, "Upload a hosted version (the body is the built HTML)",
                "POST /api/guide/{documentKey}/versions/{label}", "topics:write");
        row(out, "Report a build of a guide published elsewhere", "POST /api/guide/{documentKey}/builds",
                "topics:write");
        row(out, "List versions and labels",
                "GET /api/guide/{documentKey}/versions<br>GET /api/guide/{documentKey}/labels",
                "comments or topics:read");
        row(out, "Set a label's display state and comment mode",
                "PATCH /api/guide/{documentKey}/labels/{label}", "topics:write");
        row(out, "Read comments", "GET /api/guide/{documentKey}/comments", "comments");
        row(out, "Answer a comment (send <code>parentCommentId</code>)", "POST /api/guide/{documentKey}/comments",
                "comments");
        row(out, "Dispose of, close, or reopen a comment (authors)", "PATCH /api/guide/comments/{id}", "comments");
        row(out, "Set a review flag on an anchor (authors)", "POST /api/guide/{documentKey}/review-flags",
                "comments");
        row(out, "Clear a review flag (authors)", "DELETE /api/guide/{documentKey}/review-flags/{id}", "comments");
        out.println("</tbody></table></div>");
        out.println("<p>Label display states are <code>current</code> (at most one, the version readers are sent to),"
                + " <code>listed</code>, <code>unlisted</code> (the default), <code>authors_only</code>, and"
                + " <code>retired</code>. Comment modes are <code>open</code> (the default), <code>release</code>"
                + " (readers see only the comments that belong to that version), and <code>closed</code>. To make"
                + " a release Current:</p>");
        code(out, "Make a release Current",
                "curl -X PATCH \"" + hub + "/api/guide/<documentKey>/labels/v1.2.0\" \\\n"
                        + "  -H \"Authorization: Bearer $INTEROPHUB_TOKEN\" \\\n"
                        + "  -H \"Content-Type: application/json\" \\\n"
                        + "  -d '{\"display\": \"current\", \"commentMode\": \"release\"}'");
        out.println("<h3>Tools</h3>");
        out.println("<p>The <a href=\"" + TEMPLATES_URL + "\">content-publishing-templates</a> project wraps these"
                + " requests. In a guide project, <code>process/tools/publish_guide_to_hub.py</code> uploads a hosted"
                + " guide, or reports the build of a guide published elsewhere. It reads the hub and the document key"
                + " from <code>guide.json</code> and the token from <code>INTEROPHUB_TOKEN</code>:</p>");
        code(out, "Publish a hosted guide",
                "python process/tools/publish_guide_to_hub.py output/guide/index.html --label v1.2.0 --label latest");
        code(out, "Report a build of a guide published elsewhere",
                "python process/tools/publish_guide_to_hub.py output/guide/index.html --manifest-only --label v1.2.0");
        out.println("<p>In InteropHub, each guide's settings page (Topic Reference, under the topic's management"
                + " pages) shows the exact commands for that guide.</p>");
        out.println("</section>");
    }

    private static void ifYouAreAnAgent(PrintWriter out, String hub) {
        String h = escape(hub);
        section(out, GuideHelp.IF_YOU_ARE_AN_AGENT, "If you are an agent");
        out.println("<p>Work in this order.</p>");
        out.println("<ol>");
        out.println("<li><strong>Call <code>GET /api/token</code> first.</strong> Read the token from"
                + " <code>INTEROPHUB_TOKEN</code>; do not print it.");
        code(out, "Check the token", "curl -s -H \"Authorization: Bearer $INTEROPHUB_TOKEN\" "
                + hub + "/api/token");
        out.println("The answer names the user, the token's <code>kind</code>, <code>scopes</code>,"
                + " <code>expiresAt</code>, <code>expiresInDays</code>, and <code>guides</code>: the active guides"
                + " on topics this account edits, each with <code>canPublish</code>.</li>");
        out.println("<li><strong>If something is missing, stop and tell the author, in these words.</strong>");
        out.println("<ul>");
        out.println("<li>No <code>INTEROPHUB_TOKEN</code>, or <code>401 invalid_token</code> (missing, wrong,"
                + " expired, or revoked):");
        relay(out, "I need an InteropHub personal access token to do this. Please sign in to InteropHub, open"
                + " Account, and under Access tokens create a token with the Author preset. Then set it as the"
                + " INTEROPHUB_TOKEN environment variable yourself; the page shows how. Please don't paste the token"
                + " into this chat. Tell me when it's done.");
        out.println("</li>");
        out.println("<li>The scope you need is not in <code>scopes</code> (<code>topics:write</code> to publish or"
                + " change settings, <code>comments</code> for comments):");
        relay(out, "Your InteropHub token can't do this: it lacks the <scope> scope. Please create a new token with"
                + " the Author preset on your InteropHub Account page, set it as INTEROPHUB_TOKEN, and tell me when"
                + " it's done.");
        out.println("</li>");
        out.println("<li>The guide is not in <code>guides</code>: the account is not an editor of its topic, or the"
                + " guide is not registered or not active.");
        relay(out, "Your InteropHub account isn't an editor of the topic for this guide. Open the topic's page in"
                + " InteropHub, select Send info / question, and ask the topic's champions to add you as an editor."
                + " Tell me when that is done.");
        out.println("If the guide is simply not registered yet, an editor's token can register it with"
                + " <code>POST /api/guides</code>.</li>");
        out.println("<li><code>expiresInDays</code> is 7 or less:");
        relay(out, "Your InteropHub token expires in <n> days. When it does, create a new one on your InteropHub"
                + " Account page and set it as INTEROPHUB_TOKEN.");
        out.println("</li>");
        out.println("</ul></li>");
        out.println("<li><strong>Never ask the author to paste the token into the conversation.</strong> Ask them to"
                + " set the variable, or the repository secret, themselves.</li>");
        out.println("<li><strong>When a request fails,</strong> read <code>error</code> and <code>help</code> in the"
                + " JSON body and follow the matching entry under <a href=\"#" + GuideHelp.TROUBLESHOOTING
                + "\">Troubleshooting</a>. If the answer is HTML instead of JSON, see"
                + " <a href=\"#" + HTML_ANCHOR + "\">An HTML page instead of JSON</a>.</li>");
        out.println("</ol>");
        out.println("</section>");
    }

    static final String HTML_ANCHOR = "troubleshooting-html-response";

    private static void troubleshooting(PrintWriter out, String hub) {
        String h = escape(hub);
        section(out, GuideHelp.TROUBLESHOOTING, "Troubleshooting");
        out.println("<p>Every error from the API is JSON, like this:</p>");
        code(out, "Error body", "{\"error\": \"insufficient_scope\", \"requiredScope\": \"topics:write\",\n"
                + " \"message\": \"…\", \"help\": \"" + hub + GuideHelp.PATH
                + "#troubleshooting-insufficient_scope\"}");
        out.println("<p>Branch on <code>error</code>. <code>message</code> is for people. <code>help</code> links to"
                + " the entry below. Some errors add <code>field</code>, <code>requiredScope</code>, or"
                + " <code>current</code>.</p>");

        entry(out, "invalid_token", "401",
                "The token is missing, mistyped, expired, or revoked. The answer is the same for each, so it does not"
                        + " say which. Check that <code>INTEROPHUB_TOKEN</code> is set where you run, without printing"
                        + " it, and that the header is exactly <code>Authorization: Bearer &lt;token&gt;</code>.",
                "I need a new InteropHub personal access token. Please create one with the Author preset on your"
                        + " InteropHub Account page, set it as INTEROPHUB_TOKEN yourself, and tell me when it's done."
                        + " Please don't paste it into this chat.");
        entry(out, "insufficient_scope", "403",
                "The token works, but lacks the scope this request needs. <code>requiredScope</code> names it.",
                "Your InteropHub token lacks the <requiredScope> scope. Please create a new token with the Author"
                        + " preset on your InteropHub Account page, set it as INTEROPHUB_TOKEN, and tell me when it's"
                        + " done.");
        entry(out, "forbidden", "403",
                "The account may not do this. Usually it is not an editor of the guide's topic; the"
                        + " <code>message</code> names the topic when the account can see it. It is also returned when"
                        + " the account cannot view the topic, when changing another person's comment, and when a web"
                        + " page on an address that is not registered for guide sign-in calls the API.",
                "Your InteropHub account isn't an editor of the topic for this guide. Open the topic's page in"
                        + " InteropHub, select Send info / question, and ask the topic's champions to add you as an"
                        + " editor. Tell me when that is done.");
        entry(out, "not_found", "404",
                "No guide is registered under this document key, or the comment, flag, or label does not exist. Check"
                        + " the key (<code>comments.document</code>, then <code>id</code>, in"
                        + " <code>guide.json</code>). An unregistered guide can be registered with"
                        + " <code>POST /api/guides</code>. A label becomes known once it has an upload or a build"
                        + " report.",
                "InteropHub has no guide registered as <documentKey>. Should I register it on topic <topic>, or is"
                        + " the key different?");
        entry(out, "document_inactive", "409",
                "The guide's registration is switched off, so it takes no uploads, builds, or comments.",
                "The guide <documentKey> is marked inactive in InteropHub. If it should be live, please tick Active"
                        + " on its guide settings page in InteropHub, or tell me to do it.");
        entry(out, "comments_closed", "403",
                "The comment mode of the version this page belongs to is Closed, so nobody can comment there. An"
                        + " author can change it with <code>PATCH /api/guide/{documentKey}/labels/{label}</code>.",
                "Comments are closed on <label> of this guide. Should I open them (commentMode open or release)?");
        entry(out, "invalid_request", "400",
                "The request is malformed: bad JSON, a missing body, an unknown query value, or an unsupported"
                        + " method. Fix the request; asking the author will not help.", null);
        entry(out, "invalid_field", "400",
                "A field failed validation. <code>field</code> names it and <code>message</code> says why. Fix the"
                        + " value and send again.", null);
        entry(out, "invalid_state", "409",
                "The action does not fit the current state: for example replying to a closed thread, disposing of a"
                        + " withdrawn comment, or uploading a hosted version to a guide whose hosting is"
                        + " <code>EXTERNAL</code>. For the last, use <code>--manifest-only</code>, or change the"
                        + " hosting to <code>HUB</code> first.", null);
        entry(out, "edit_window_closed", "403",
                "Commenters can edit or withdraw their own comments for 14 days. That window has passed.", null);
        entry(out, "conflict", "409",
                "Someone changed the thread after you read it. <code>current</code> holds the latest version. Read it,"
                        + " decide again, and resend with the new <code>expectedUpdatedAt</code>.", null);
        entry(out, "duplicate", "409",
                "The document key or a URL is already registered. Change the existing registration with"
                        + " <code>PATCH /api/guide/{documentKey}</code>, or choose another key.", null);
        entry(out, "url_not_approved", "400",
                "A guide URL is outside the prefixes an InteropHub administrator has approved. Use hosting"
                        + " <code>HUB</code>, or ask for the prefix to be approved.",
                "InteropHub doesn't accept guide URLs under that address yet. Please ask an InteropHub administrator"
                        + " to approve it, or I can host the guide on InteropHub instead.");
        entry(out, "server_error", "500",
                "Something failed inside InteropHub. Try once more a little later. If it fails again, report it as in"
                        + " the next entry.", null);

        out.println("<h3 id=\"" + HTML_ANCHOR + "\">An HTML page instead of JSON</h3>");
        out.println("<p><strong>Cause:</strong> a firewall or proxy in front of InteropHub stopped the request before"
                + " it reached InteropHub. The answer is an HTML page, often with status 403 or 406, rather than the"
                + " JSON above. It has nothing to do with the token. Do not retry in a loop.</p>");
        out.println("<p><strong>What to report:</strong> the HTTP status, the time in UTC, and the endpoint (method"
                + " and path). Never include the token or the request body.</p>");
        relay(out, "A firewall in front of InteropHub blocked my request. Please report it to the InteropHub"
                + " administrators with these details: status <status>, time <time in UTC>, endpoint <method and"
                + " path>.");
        out.println("</section>");
    }

    private static void entry(PrintWriter out, String code, String status, String cause, String relay) {
        out.println("<h3 id=\"" + GuideHelp.troubleshootingAnchor(code) + "\"><code>" + code + "</code> (" + status
                + ")</h3>");
        out.println("<p><strong>Cause:</strong> " + cause + "</p>");
        if (relay != null) {
            relay(out, relay);
        }
    }

    private static void section(PrintWriter out, String id, String title) {
        out.println("<section aria-labelledby=\"" + id + "\">");
        out.println("<h2 class=\"aira-section-title\" id=\"" + id + "\">" + escape(title) + "</h2>");
    }

    /** Exact words for an agent to pass on to the author. Angle-bracket parts are filled in. */
    private static void relay(PrintWriter out, String words) {
        out.println("<blockquote class=\"aira-alert aira-alert--info\"><p><strong>Tell the author:</strong> &ldquo;"
                + escape(words) + "&rdquo;</p></blockquote>");
    }

    private static void row(PrintWriter out, String action, String request, String scope) {
        out.println("<tr><td>" + action + "</td><td><code>" + request + "</code></td><td><code>" + escape(scope)
                + "</code></td></tr>");
    }

    private static void code(PrintWriter out, String label, String text) {
        out.println("<pre class=\"aira-code-block aira-code-block--wrap\" tabindex=\"0\" aria-label=\""
                + escape(label) + "\"><code>" + escape(text) + "</code></pre>");
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
