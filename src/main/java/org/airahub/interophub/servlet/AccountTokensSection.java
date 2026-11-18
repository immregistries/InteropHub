package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.airahub.interophub.service.GuideApps;
import org.airahub.interophub.service.GuideHelp;
import org.airahub.interophub.service.GuideScopes;
import org.airahub.interophub.service.GuideTokenInfoService.GuideEntry;
import org.airahub.interophub.service.GuideTokenService.AccountToken;

/**
 * The "Access tokens" part of {@code /account}: the token list with expiry warnings, the
 * one-time reveal of a new personal access token with a "How to use this token" panel, and the
 * create form with scope descriptions and the Author preset. Plain rendering, so tests need no
 * database.
 */
final class AccountTokensSection {
    static final String AUTHOR_PRESET = "author";
    static final String AUTHOR_PRESET_LABEL = "Author (publish guides and manage comments)";
    static final List<String> AUTHOR_SCOPES = List.of(GuideScopes.COMMENTS, GuideScopes.TOPICS_WRITE);
    static final int EXPIRY_WARNING_DAYS = 14;
    static final String TOKEN_PLACEHOLDER = "<paste your token here>";
    static final String AGENT_SENTENCE = "If an agent asked you to make this token, set it as INTEROPHUB_TOKEN"
            + " yourself; don't paste it into the chat.";

    private static final DateTimeFormatter TOKEN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private AccountTokensSection() {
    }

    /** What the account page shows once, right after a personal access token is created. */
    static final class Revealed implements Serializable {
        private static final long serialVersionUID = 1L;
        final String rawToken;
        final LocalDateTime expiresAt;
        final String scopes;

        Revealed(String rawToken, LocalDateTime expiresAt, String scopes) {
            this.rawToken = rawToken;
            this.expiresAt = expiresAt;
            this.scopes = scopes;
        }
    }

    static String scopeDescription(String scope) {
        if (GuideScopes.COMMENTS.equals(scope)) {
            return "Read and answer comments on guides.";
        }
        if (GuideScopes.TOPICS_READ.equals(scope)) {
            return "Read topic and guide information.";
        }
        if (GuideScopes.TOPICS_WRITE.equals(scope)) {
            return "Publish and manage guides on topics you edit.";
        }
        return "";
    }

    /** The posted scopes, plus the Author preset's scopes when it is chosen (so it works without script). */
    static List<String> chosenScopes(String preset, Collection<String> posted) {
        Set<String> chosen = new LinkedHashSet<>();
        if (posted != null) {
            chosen.addAll(posted);
        }
        if (AUTHOR_PRESET.equals(preset)) {
            chosen.addAll(AUTHOR_SCOPES);
        }
        return new ArrayList<>(chosen);
    }

    static boolean expiresSoon(LocalDateTime expiresAt, LocalDateTime now) {
        return expiresAt != null && !expiresAt.isBefore(now) && expiresAt.isBefore(now.plusDays(EXPIRY_WARNING_DAYS));
    }

    static void render(PrintWriter out, String contextPath, Revealed revealed, List<AccountToken> tokens,
            List<GuideEntry> guides, LocalDateTime now) {
        out.println("          <section class=\"aira-panel\" id=\"access-tokens\">");
        out.println("            <h2 class=\"aira-section-title\">Access tokens</h2>");
        out.println("              <p>Guide sign-ins are created when you comment from a Topic Reference guide. Personal access tokens are for scripts and other tools, such as an AI agent that publishes your guide. Revoking a token stops it immediately. Your InteropHub sign-in is separate. <a class=\"aira-inline-link\" href=\""
                + escapeHtml(GuideHelp.page()) + "\">Help for Topic Reference authors</a></p>");
        if (revealed != null) {
            out.println("            <div class=\"aira-alert aira-alert--warning\">");
            out.println("              <p class=\"aira-alert__title\">Copy this personal access token now</p>");
            out.println("              <p>It will not be shown again.</p>");
            out.println("              <p><input class=\"aira-input\" readonly=\"readonly\" aria-label=\"New personal access token\" value=\""
                    + escapeHtml(revealed.rawToken) + "\" /></p>");
            out.println("            </div>");
            howToUse(out, revealed, guides == null ? List.of() : guides);
        }
        if (tokens == null || tokens.isEmpty()) {
            out.println("            <p>You have no guide sign-ins or personal access tokens.</p>");
        } else {
            tokenTable(out, contextPath, tokens, now);
        }
        createForm(out, contextPath);
        out.println("          </section>");
    }

    private static void howToUse(PrintWriter out, Revealed revealed, List<GuideEntry> guides) {
        boolean canPublish = GuideScopes.satisfied(GuideScopes.parse(revealed.scopes), GuideScopes.TOPICS_WRITE);
        out.println("            <div class=\"aira-panel aira-stack\" id=\"how-to-use-this-token\">");
        out.println("              <h3 class=\"aira-section-title\">How to use this token</h3>");
        out.println("              <p><strong>" + escapeHtml(AGENT_SENTENCE) + "</strong></p>");
        out.println("              <p>Tools read the token from the environment variable <code>INTEROPHUB_TOKEN</code>."
                + " Copy the token from the box above and put it in place of <code>" + escapeHtml(TOKEN_PLACEHOLDER)
                + "</code>.</p>");
        out.println("              <p>PowerShell, for this window:</p>");
        code(out, "$env:INTEROPHUB_TOKEN = \"" + TOKEN_PLACEHOLDER + "\"");
        out.println("              <p>PowerShell, saved for windows you open later:</p>");
        code(out, "setx INTEROPHUB_TOKEN \"" + TOKEN_PLACEHOLDER + "\"");
        out.println("              <p>bash or zsh:</p>");
        code(out, "export INTEROPHUB_TOKEN=\"" + TOKEN_PLACEHOLDER + "\"");
        out.println("              <p>For publishing from GitHub, store it as a repository secret (the command asks for the value):</p>");
        code(out, "gh secret set INTEROPHUB_TOKEN --repo <owner>/<repo>");
        out.println("              <p>Restart your agent from a new terminal after setting the variable, so it can see it.</p>");
        out.println("              <p><strong>Expires:</strong> " + escapeHtml(format(revealed.expiresAt)) + ".</p>");
        if (guides.isEmpty()) {
            out.println("              <p><strong>Guides this account can publish to:</strong> none yet. You need to be"
                    + " an editor of a topic with a registered guide.</p>");
        } else {
            out.println("              <p><strong>Guides this account can publish to</strong>"
                    + (canPublish ? ":" : " (this token cannot publish: it does not have <code>topics:write</code>):")
                    + "</p>");
            out.println("              <ul>");
            for (GuideEntry guide : guides) {
                out.println("                <li><code>" + escapeHtml(guide.documentKey()) + "</code>"
                        + (guide.title() == null || guide.title().isBlank() ? "" : " &ndash; " + escapeHtml(guide.title()))
                        + " (" + escapeHtml(guide.topicName()) + ", " + escapeHtml(hostingLabel(guide.hosting()))
                        + ")</li>");
            }
            out.println("              </ul>");
        }
        out.println("              <p><a class=\"aira-inline-link\" href=\"" + escapeHtml(GuideHelp.url(GuideHelp.WHERE_THE_TOKEN_GOES))
                + "\">More about where the token goes, and what an agent can do with it</a></p>");
        out.println("            </div>");
    }

    private static void tokenTable(PrintWriter out, String contextPath, List<AccountToken> tokens, LocalDateTime now) {
        out.println("            <div class=\"aira-table-wrap\">");
        out.println("            <table class=\"aira-table\">");
        out.println("              <thead><tr><th>Label</th><th>Kind</th><th>Scopes</th><th>Issued</th><th>Expires</th><th>Last used</th><th></th></tr></thead>");
        out.println("              <tbody>");
        for (AccountToken token : tokens) {
            out.println("                <tr>");
            out.println("                  <td>" + escapeHtml(orEmpty(token.getLabel())) + "</td>");
            out.println("                  <td>" + escapeHtml(kindLabel(token.getKind())) + "</td>");
            out.println("                  <td>" + escapeHtml(displayScopes(token.getScopes())) + "</td>");
            out.println("                  <td>" + escapeHtml(format(token.getIssuedAt())) + "</td>");
            out.println("                  <td>" + expiryCell(token.getExpiresAt(), now) + "</td>");
            out.println("                  <td>" + escapeHtml(token.getLastUsedAt() == null ? "Not yet" : format(token.getLastUsedAt())) + "</td>");
            out.println("                  <td>");
            out.println("                    <form method=\"post\" action=\"" + contextPath + "/account\">");
            out.println("                      <input type=\"hidden\" name=\"action\" value=\"revoke-token\" />");
            out.println("                      <input type=\"hidden\" name=\"tokenId\" value=\"" + token.getTokenId() + "\" />");
            out.println("                      <button class=\"aira-button aira-button--secondary\" type=\"submit\">Revoke</button>");
            out.println("                    </form>");
            out.println("                  </td>");
            out.println("                </tr>");
        }
        out.println("              </tbody>");
        out.println("            </table>");
        out.println("            </div>");
    }

    static String expiryCell(LocalDateTime expiresAt, LocalDateTime now) {
        if (expiresAt == null) {
            return "";
        }
        String formatted = escapeHtml(format(expiresAt));
        if (expiresAt.isBefore(now)) {
            return formatted + " <span class=\"aira-badge aira-badge--danger\">Expired</span>";
        }
        if (expiresSoon(expiresAt, now)) {
            return formatted + " <span class=\"aira-badge aira-badge--warning\">Expires soon</span>";
        }
        return formatted;
    }

    private static void createForm(PrintWriter out, String contextPath) {
        out.println("            <h3 class=\"aira-section-title\">Create a personal access token</h3>");
        out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + contextPath + "/account\">");
        out.println("              <input type=\"hidden\" name=\"action\" value=\"create-personal-token\" />");
        out.println("              <div class=\"aira-field\">");
        out.println("                <label for=\"tokenLabel\">Label</label>");
        out.println("                <input class=\"aira-input\" id=\"tokenLabel\" name=\"label\" type=\"text\" maxlength=\"120\" required />");
        out.println("                <p class=\"aira-field-help\">Where the token will live, such as \"laptop agent\" or \"GitHub publishing\".</p>");
        out.println("              </div>");
        out.println("              <fieldset class=\"aira-field\">");
        out.println("                <legend>Scopes</legend>");
        out.println("                <label><input type=\"checkbox\" id=\"tokenPresetAuthor\" name=\"preset\" value=\"" + AUTHOR_PRESET
                + "\" data-scopes=\"" + escapeHtml(String.join(" ", AUTHOR_SCOPES)) + "\" /> <strong>"
                + escapeHtml(AUTHOR_PRESET_LABEL) + "</strong></label>");
        out.println("                <p class=\"aira-field-help\">Ticks comments and topics:write. Choose this for an agent that publishes a Topic Reference.</p>");
        for (String scope : GuideScopes.PERSONAL_CHOICES) {
            out.println("                <label><input type=\"checkbox\" name=\"scope\" value=\"" + escapeHtml(scope) + "\" /> "
                    + escapeHtml(scope) + "</label>");
            out.println("                <p class=\"aira-field-help\">" + escapeHtml(scopeDescription(scope)) + "</p>");
        }
        out.println("              </fieldset>");
        out.println("              <div class=\"aira-field\">");
        out.println("                <label for=\"lifetimeDays\">Lifetime</label>");
        out.println("                <select class=\"aira-select\" id=\"lifetimeDays\" name=\"lifetimeDays\">");
        out.println("                  <option value=\"30\">30 days</option>");
        out.println("                  <option value=\"90\" selected>90 days</option>");
        out.println("                  <option value=\"365\">365 days</option>");
        out.println("                </select>");
        out.println("              </div>");
        out.println("              <div class=\"aira-action-group\">");
        out.println("                <button class=\"aira-button aira-button--primary\" type=\"submit\">Create token</button>");
        out.println("              </div>");
        out.println("            </form>");
        out.println("            <script>");
        out.println("              (function () {");
        out.println("                var preset = document.getElementById('tokenPresetAuthor');");
        out.println("                if (!preset) { return; }");
        out.println("                var wanted = preset.getAttribute('data-scopes').split(' ');");
        out.println("                preset.addEventListener('change', function () {");
        out.println("                  var boxes = preset.form.querySelectorAll('input[name=\"scope\"]');");
        out.println("                  for (var i = 0; i < boxes.length; i++) {");
        out.println("                    boxes[i].checked = preset.checked ? wanted.indexOf(boxes[i].value) >= 0 : false;");
        out.println("                  }");
        out.println("                });");
        out.println("              })();");
        out.println("            </script>");
    }

    private static void code(PrintWriter out, String text) {
        out.println("              <pre class=\"aira-code-block aira-code-block--wrap\" tabindex=\"0\"><code>"
                + escapeHtml(text) + "</code></pre>");
    }

    private static String hostingLabel(String hosting) {
        return "HUB".equals(hosting) ? "hosted on InteropHub" : "published elsewhere";
    }

    static String kindLabel(String kind) {
        if (GuideApps.KIND_GUIDE.equals(kind)) {
            return "Guide sign-in";
        }
        if (GuideApps.KIND_PERSONAL.equals(kind)) {
            return "Personal access token";
        }
        return kind == null ? "" : kind;
    }

    private static String displayScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return "";
        }
        return scopes.replace(",", ", ");
    }

    private static String format(LocalDateTime value) {
        return value == null ? "" : TOKEN_TIME.format(value);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
