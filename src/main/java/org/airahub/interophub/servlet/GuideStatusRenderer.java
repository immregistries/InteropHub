package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.service.GuideBuildService;
import org.airahub.interophub.service.GuideBuildService.GuideStatus;
import org.airahub.interophub.service.GuideBuildService.Impact;
import org.airahub.interophub.service.GuideBuildService.Release;
import org.airahub.interophub.service.GuideBuildService.State;
import org.airahub.interophub.service.GuideBuildService.StatusReport;
import org.airahub.interophub.service.GuideBuildService.Warning;
import org.airahub.interophub.service.GuideRows.BuildRow;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The Status view of {@code /admin/es/guides}: every registered, active guide and
 * what it last reported (plan section B5). Kept apart from the servlet so it can
 * be rendered in tests without a database or a signed-in admin.
 */
final class GuideStatusRenderer {
    static final String PATH = "/admin/es/guides";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static final Map<String, String> FILTERS = new java.util.LinkedHashMap<>();

    static {
        FILTERS.put("", "All guides");
        FILTERS.put("no-status", "No status reported");
        FILTERS.put("needs-upgrade", "Needs upgrade (any impact)");
        FILTERS.put("needs-rebuild", "Needs upgrade: rebuild");
        FILTERS.put("needs-config", "Needs upgrade: config");
        FILTERS.put("needs-content", "Needs upgrade: content");
        FILTERS.put("has-warnings", "Has warnings");
        FILTERS.put("stale", "Not published in " + GuideBuildService.STALE_DAYS + " days");
    }

    private GuideStatusRenderer() {
    }

    /** The guides a filter and warning code leave on the page, in report order. */
    static List<GuideStatus> filtered(StatusReport report, String filter, String code) {
        String chosen = filter == null ? "" : filter;
        String warningCode = code == null || code.isBlank() ? null : code;
        return report.guides.stream()
                .filter(guide -> switch (chosen) {
                    case "no-status" -> guide.state == State.NO_STATUS;
                    case "needs-upgrade" -> guide.needsUpgrade();
                    case "needs-rebuild" -> guide.needsUpgrade() && guide.impact == Impact.REBUILD;
                    case "needs-config" -> guide.needsUpgrade() && guide.impact == Impact.CONFIG;
                    case "needs-content" -> guide.needsUpgrade() && guide.impact == Impact.CONTENT;
                    case "has-warnings" -> guide.warningCount > 0;
                    case "stale" -> guide.stale;
                    default -> true;
                })
                .filter(guide -> warningCode == null || guide.warningCodes.contains(warningCode))
                .toList();
    }

    static void viewSwitch(PrintWriter out, String contextPath, boolean status) {
        out.println("          <nav class=\"aira-segmented-control\" aria-label=\"Guide admin views\">");
        out.println("            <a class=\"aira-segment" + (status ? "" : " is-selected") + "\" href=\""
                + contextPath + PATH + "\"" + (status ? "" : " aria-current=\"page\"") + ">Registrations</a>");
        out.println("            <a class=\"aira-segment" + (status ? " is-selected" : "") + "\" href=\""
                + contextPath + PATH + "?view=status\"" + (status ? " aria-current=\"page\"" : "") + ">Status</a>");
        out.println("          </nav>");
    }

    static void render(PrintWriter out, String contextPath, StatusReport report, String filter, String code) {
        String chosenFilter = filter != null && FILTERS.containsKey(filter) ? filter : "";
        String chosenCode = code != null && report.warningCodes.contains(code) ? code : "";
        out.println("          <h1 class=\"aira-page-title\">Guide status</h1>");
        viewSwitch(out, contextPath, true);
        out.println("          <p class=\"aira-muted\">What each registered guide last reported when it was published."
                + " InteropHub does not check guides itself.</p>");
        summary(out, report);

        out.println("          <form class=\"aira-toolbar\" method=\"get\" action=\"" + contextPath + PATH + "\">");
        out.println("            <input type=\"hidden\" name=\"view\" value=\"status\">");
        out.println("            <div class=\"aira-field-row\">");
        out.println("              <div class=\"aira-field\">");
        out.println("                <label class=\"aira-label\" for=\"statusFilter\">Show</label>");
        out.println("                <select class=\"aira-select\" id=\"statusFilter\" name=\"filter\">");
        for (Map.Entry<String, String> entry : FILTERS.entrySet()) {
            out.println("                  <option value=\"" + escape(entry.getKey()) + "\""
                    + (entry.getKey().equals(chosenFilter) ? " selected" : "") + ">" + escape(entry.getValue())
                    + "</option>");
        }
        out.println("                </select>");
        out.println("              </div>");
        out.println("              <div class=\"aira-field\">");
        out.println("                <label class=\"aira-label\" for=\"statusCode\">Warning code</label>");
        out.println("                <select class=\"aira-select\" id=\"statusCode\" name=\"code\">");
        out.println("                  <option value=\"\">Any</option>");
        for (String warningCode : report.warningCodes) {
            out.println("                  <option value=\"" + escape(warningCode) + "\""
                    + (warningCode.equals(chosenCode) ? " selected" : "") + ">" + escape(warningCode) + "</option>");
        }
        out.println("                </select>");
        out.println("              </div>");
        out.println("            </div>");
        out.println("            <div class=\"aira-cluster\">");
        out.println("              <button class=\"aira-button aira-button--primary\" type=\"submit\">Apply</button>");
        out.println("              <a class=\"aira-button aira-button--secondary\" href=\"" + contextPath + PATH
                + "?view=status\">Clear</a>");
        out.println("            </div>");
        out.println("          </form>");

        List<GuideStatus> guides = filtered(report, chosenFilter, chosenCode);
        out.println("          <section class=\"aira-section-card\">");
        out.println("            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">Guides ("
                + guides.size() + (guides.size() == report.guides.size() ? "" : " of " + report.guides.size())
                + ")</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        out.println("              <div class=\"aira-table-wrap\"><table class=\"aira-table aira-table--compact\""
                + " id=\"guide-status-table\">");
        out.println("                <thead><tr><th>Guide and topic</th><th>Hosting</th><th>Current</th><th>Process version</th>"
                + "<th>Upgrade impact</th><th>Document version</th><th>Last build</th><th>Last publish</th>"
                + "<th>Warnings</th><th>Open comments</th><th>Open review flags</th><th>Feedback markers</th>"
                + "</tr></thead><tbody>");
        if (guides.isEmpty()) {
            out.println("                  <tr><td class=\"aira-table__empty\" colspan=\"12\">No guides match.</td></tr>");
        }
        for (GuideStatus guide : guides) {
            row(out, contextPath, guide, report);
        }
        out.println("                </tbody></table></div>");
        out.println("            </div></section>");
        releases(out, report);
    }

    private static void summary(PrintWriter out, StatusReport report) {
        long behind = report.needsUpgrade();
        long rebuild = report.needsUpgrade(Impact.REBUILD);
        long config = report.needsUpgrade(Impact.CONFIG);
        long content = report.needsUpgrade(Impact.CONTENT);
        long unknownImpact = behind - rebuild - config - content;
        StringBuilder text = new StringBuilder();
        text.append("<strong>").append(report.guides.size()).append("</strong> registered guides · ");
        text.append("<strong>").append(report.count(State.CURRENT)).append("</strong> current · ");
        text.append("<strong>").append(behind).append("</strong> behind (")
                .append(rebuild).append(" rebuild, ").append(config).append(" config, ")
                .append(content).append(" content");
        if (unknownImpact > 0) {
            text.append(", ").append(unknownImpact).append(" impact unknown");
        }
        text.append(") · ");
        text.append("<strong>").append(report.count(State.NO_STATUS)).append("</strong> no status reported · ");
        text.append("<strong>").append(report.withWarnings()).append("</strong> with warnings");
        long unreadable = report.count(State.UNREADABLE);
        if (unreadable > 0) {
            text.append(" · <strong>").append(unreadable).append("</strong> status unreadable");
        }
        long unknown = report.count(State.UNKNOWN_VERSION);
        if (unknown > 0) {
            text.append(" · <strong>").append(unknown).append("</strong> on a version not in the release list");
        }
        out.println("          <p id=\"guide-status-summary\">" + text + "</p>");
        if (report.latestVersion == null) {
            out.println("          <p class=\"aira-muted\">No guide has reported a process release yet.</p>");
        } else {
            out.println("          <p class=\"aira-muted\">Latest process release: <strong>"
                    + escape(report.latestVersion) + "</strong>, as reported by "
                    + escape(report.releaseSourceKey) + ".</p>");
        }
    }

    private static void row(PrintWriter out, String contextPath, GuideStatus guide, StatusReport report) {
        String detailHref = contextPath + PATH + "?view=status&amp;guide=" + escape(guide.documentKey);
        StringBuilder html = new StringBuilder();
        html.append("                  <tr data-guide=\"").append(escape(guide.documentKey))
                .append("\" data-state=\"").append(guide.state.name()).append("\">");
        html.append("<td><strong>");
        if (guide.guideUrl != null) {
            html.append("<a href=\"").append(escape(href(contextPath, guide.guideUrl))).append("\">")
                    .append(escape(guide.title)).append("</a>");
        } else {
            html.append(escape(guide.title));
        }
        html.append("</strong>");
        html.append("<br><a class=\"aira-muted\" href=\"").append(detailHref).append("\">")
                .append(escape(guide.documentKey)).append("</a>");
        if (guide.topicId != null) {
            html.append("<br>Topic: <a href=\"").append(contextPath).append("/es/topic/").append(guide.topicId)
                    .append("\">").append(escape(guide.topicName)).append("</a>");
        }
        html.append("</td>");
        html.append("<td>").append(escape(guide.hosting)).append("</td>");
        html.append("<td>").append(guide.currentLabel == null
                ? "<span class=\"aira-muted\">None</span>" : escape(guide.currentLabel)).append("</td>");
        switch (guide.state) {
            case NO_STATUS -> html.append("<td colspan=\"4\"><span class=\"aira-badge aira-badge--subtle\">")
                    .append("No status reported</span></td>");
            case UNREADABLE -> html.append("<td colspan=\"4\"><span class=\"aira-badge aira-badge--danger\">")
                    .append("Status unreadable</span> <span class=\"aira-muted\">").append(escape(guide.error))
                    .append("</span></td>");
            default -> {
                html.append("<td class=\"aira-table__cell--nowrap\">").append(versionCell(guide, report)).append("</td>");
                html.append("<td>").append(guide.impact == null ? "—" : escape(guide.impact.code())).append("</td>");
                html.append("<td>").append(guide.documentVersion == null ? "—" : escape(guide.documentVersion));
                if (guide.documentStatus != null) {
                    html.append("<br><span class=\"aira-muted\">").append(escape(guide.documentStatus)).append("</span>");
                }
                html.append("</td>");
                html.append("<td class=\"aira-table__cell--date\">").append(date(guide.lastBuild)).append("</td>");
            }
        }
        html.append("<td class=\"aira-table__cell--date\">");
        if (guide.lastPublish == null) {
            html.append("<span class=\"aira-badge aira-badge--outline\">Never</span>");
        } else {
            html.append(date(guide.lastPublish));
        }
        if (guide.stale && guide.lastPublish != null) {
            html.append(" <span class=\"aira-badge aira-badge--outline\">Over ")
                    .append(GuideBuildService.STALE_DAYS).append(" days</span>");
        }
        html.append("</td>");
        html.append("<td class=\"aira-table__cell--numeric\">").append(warningsCell(guide)).append("</td>");
        html.append("<td class=\"aira-table__cell--numeric\">").append(guide.openComments).append("</td>");
        html.append("<td class=\"aira-table__cell--numeric\">").append(guide.openFlags).append("</td>");
        html.append("<td class=\"aira-table__cell--numeric\">")
                .append(guide.feedbackMarkers == null ? "—" : guide.feedbackMarkers).append("</td>");
        html.append("</tr>");
        out.println(html);
    }

    private static String versionCell(GuideStatus guide, StatusReport report) {
        StringBuilder html = new StringBuilder();
        switch (guide.state) {
            case NO_MANIFEST -> {
                String oldest = report.releases.isEmpty() ? null
                        : report.releases.get(report.releases.size() - 1).version();
                html.append(oldest == null ? "No manifest" : "Before " + escape(oldest));
            }
            default -> html.append(escape(guide.processVersion));
        }
        html.append(" ").append(badge(guide));
        return html.toString();
    }

    /** Green when current, amber when only rebuild or config upgrades are pending, red for content. */
    static String badge(GuideStatus guide) {
        if (guide.state == State.CURRENT) {
            return "<span class=\"aira-badge aira-badge--success\">Current</span>";
        }
        if (guide.state == State.UNKNOWN_VERSION) {
            return "<span class=\"aira-badge aira-badge--info\">Not in release list</span>";
        }
        if (guide.behind == null || guide.behind == 0) {
            return guide.state == State.NO_MANIFEST
                    ? "<span class=\"aira-badge aira-badge--warning\">No manifest</span>" : "";
        }
        String tone = guide.impact == Impact.CONTENT ? "danger" : "warning";
        return "<span class=\"aira-badge aira-badge--" + tone + "\">" + guide.behind + " behind</span>";
    }

    private static String warningsCell(GuideStatus guide) {
        if (guide.state == State.NO_STATUS || guide.state == State.UNREADABLE || guide.state == State.NO_MANIFEST) {
            return "—";
        }
        if (guide.warningCount == 0) {
            return "0";
        }
        StringBuilder html = new StringBuilder("<details><summary>").append(guide.warningCount).append("</summary><ul>");
        for (Warning warning : guide.warnings) {
            html.append("<li>").append(warningText(warning)).append("</li>");
        }
        html.append("</ul></details>");
        return html.toString();
    }

    private static String warningText(Warning warning) {
        StringBuilder html = new StringBuilder();
        if (warning.code() != null) {
            html.append("<code>").append(escape(warning.code())).append("</code> ");
        }
        html.append(escape(warning.message()));
        if (warning.file() != null) {
            html.append(" <span class=\"aira-muted\">(").append(escape(warning.file())).append(")</span>");
        }
        return html.toString();
    }

    private static void releases(PrintWriter out, StatusReport report) {
        if (report.releases.isEmpty()) {
            return;
        }
        out.println("          <section class=\"aira-section-card\">");
        out.println("            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">"
                + "Process releases known to InteropHub</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        out.println("              <p class=\"aira-muted\">From the manifest of " + escape(report.releaseSourceKey)
                + ", the guide on the highest process version. A release InteropHub has not seen"
                + " appears here once the first guide is published with it.</p>");
        out.println("              <div class=\"aira-table-wrap\"><table class=\"aira-table aira-table--compact\">");
        out.println("                <thead><tr><th>Version</th><th>Date</th><th>Upgrade impact</th><th>Summary</th>"
                + "</tr></thead><tbody>");
        for (Release release : report.releases) {
            out.println("                  <tr><td>" + escape(release.version()) + "</td><td>" + escape(release.date())
                    + "</td><td>" + (release.impact() == null ? "—" : escape(release.impact().code())) + "</td><td>"
                    + escape(release.summary()) + "</td></tr>");
        }
        out.println("                </tbody></table></div>");
        out.println("            </div></section>");
    }

    static void renderDetail(PrintWriter out, String contextPath, GuideBuildService.Detail detail) {
        out.println("          <p><a href=\"" + contextPath + PATH + "?view=status\">&larr; All guide status</a></p>");
        out.println("          <h1 class=\"aira-page-title\">" + escape(detail.title()) + "</h1>");
        GuideStatus guide = detail.status();
        StatusReport report = detail.report();
        StringBuilder meta = new StringBuilder("<p class=\"aira-muted\">").append(escape(detail.documentKey()));
        if (guide != null) {
            meta.append(" · ").append(escape(guide.hosting));
            if (guide.topicId != null) {
                meta.append(" · <a href=\"").append(contextPath).append("/es/topic/").append(guide.topicId)
                        .append("\">").append(escape(guide.topicName)).append("</a>");
            }
            if (guide.guideUrl != null) {
                meta.append(" · <a href=\"").append(escape(href(contextPath, guide.guideUrl)))
                        .append("\">Open guide</a>");
            }
        } else if (!detail.active()) {
            meta.append(" · inactive registration");
        }
        meta.append("</p>");
        out.println("          " + meta);
        if (guide != null) {
            String state = switch (guide.state) {
                case NO_STATUS -> "<span class=\"aira-badge aira-badge--subtle\">No status reported</span>";
                case UNREADABLE -> "<span class=\"aira-badge aira-badge--danger\">Status unreadable</span> "
                        + escape(guide.error);
                default -> versionCell(guide, report)
                        + (guide.impact == null ? "" : " · upgrade impact " + escape(guide.impact.code()));
            };
            out.println("          <p id=\"guide-status-state\">" + state + "</p>");
        }

        out.println("          <section class=\"aira-section-card\">");
        out.println("            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">"
                + "Warnings in the latest build</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        if (guide == null || guide.warnings.isEmpty()) {
            out.println("              <p class=\"aira-muted\">None reported.</p>");
        } else {
            out.println("              <ul>");
            for (Warning warning : guide.warnings) {
                out.println("                <li>" + warningText(warning) + "</li>");
            }
            out.println("              </ul>");
        }
        out.println("            </div></section>");

        out.println("          <section class=\"aira-section-card\">");
        out.println("            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">"
                + "Build history</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        out.println("              <div class=\"aira-table-wrap\"><table class=\"aira-table aira-table--compact\""
                + " id=\"guide-build-history\">");
        out.println("                <thead><tr><th>Received</th><th>Source</th><th>Label</th><th>Process version</th>"
                + "<th>Document version</th><th>Built</th><th>Commit</th><th>Warnings</th><th>Received by</th>"
                + "</tr></thead><tbody>");
        if (detail.builds().isEmpty()) {
            out.println("                  <tr><td class=\"aira-table__empty\" colspan=\"9\">No status reported."
                    + "</td></tr>");
        }
        for (BuildRow build : detail.builds()) {
            String version;
            if (build.manifestError != null) {
                version = "<span class=\"aira-badge aira-badge--danger\">Unreadable</span> "
                        + escape(build.manifestError);
            } else if (build.processVersion == null) {
                version = "No manifest";
            } else {
                version = escape(build.processVersion);
            }
            out.println("                  <tr><td class=\"aira-table__cell--nowrap\">" + dateTime(build.receivedAt)
                    + "</td><td>" + escape(build.source) + "</td><td>" + escape(build.label) + "</td><td>" + version
                    + "</td><td>" + escape(build.documentVersion)
                    + (build.documentStatus == null ? "" : " <span class=\"aira-muted\">"
                            + escape(build.documentStatus) + "</span>")
                    + "</td><td class=\"aira-table__cell--nowrap\">" + dateTime(build.builtAt)
                    + "</td><td class=\"aira-table__cell--code\">" + escape(build.sourceCommit)
                    + "</td><td class=\"aira-table__cell--numeric\">"
                    + (build.warningCount == null ? "—" : build.warningCount)
                    + "</td><td>" + escape(build.receivedByName) + "</td></tr>");
        }
        out.println("                </tbody></table></div>");
        out.println("            </div></section>");

        BuildRow latest = detail.builds().isEmpty() ? null : detail.builds().get(0);
        out.println("          <section class=\"aira-section-card\">");
        out.println("            <div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">"
                + "Raw manifest of the latest build</h2></div>");
        out.println("            <div class=\"aira-section-card__body\">");
        if (latest == null || latest.manifestJson == null) {
            out.println("              <p class=\"aira-muted\">"
                    + (latest == null ? "No build has been reported." : "The latest build has no readable manifest.")
                    + "</p>");
        } else {
            out.println("              <pre id=\"guide-build-manifest-raw\" class=\"aira-code-block aira-code-block--wrap\""
                    + " tabindex=\"0\" aria-label=\"Raw build manifest\">"
                    + escape(pretty(latest.manifestJson)) + "</pre>");
        }
        out.println("            </div></section>");
    }

    private static String pretty(String json) {
        try {
            return new JSONObject(json).toString(2);
        } catch (JSONException ex) {
            return json;
        }
    }

    private static String href(String contextPath, String url) {
        return url.startsWith("/") ? contextPath + url : url;
    }

    private static String date(LocalDateTime value) {
        return value == null ? "—" : DATE.format(value);
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? "—" : DATE_TIME.format(value);
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
