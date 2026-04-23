package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsNeighborhood;
import org.airahub.interophub.model.EsTopic;

public class EsTopicsServlet extends HttpServlet {

    private static final String VIEW_NEIGHBORHOOD = "neighborhood";
    private static final String VIEW_STAGE = "stage";
    private static final String UNCATEGORIZED_LABEL = "Uncategorized";

    private static final List<String> STAGE_ORDER = List.of("Draft", "Gather", "Monitor", "Pilot", "Rollout", "Parked");

    private static final Map<String, String> STAGE_DESCRIPTIONS = Map.of(
            "Draft", "Draft topics are early-stage ideas gathering initial interest and problem framing.",
            "Gather", "Gather topics are collecting broader input from implementers and stakeholders.",
            "Monitor", "Monitor topics are active efforts being tracked for readiness and real-world momentum.",
            "Pilot", "Pilot topics are in trial implementations to validate feasibility and workflow impact.",
            "Rollout", "Rollout topics are ready for broader adoption and implementation support.",
            "Parked", "Parked topics are intentionally paused while dependencies or timing constraints are addressed.");

    private final EsTopicDao esTopicDao;
    private final EsNeighborhoodDao esNeighborhoodDao;

    public EsTopicsServlet() {
        this.esTopicDao = new EsTopicDao();
        this.esNeighborhoodDao = new EsNeighborhoodDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        String contextPath = request.getContextPath();
        String view = trimToNull(request.getParameter("view"));
        String neighborhoodParam = trimToNull(request.getParameter("n"));
        String stageParam = trimToNull(request.getParameter("s"));

        List<EsNeighborhood> neighborhoods = esNeighborhoodDao.findAllActive();
        List<EsTopic> allTopics = esTopicDao.findAllActiveForPublicPage();
        Map<String, String> activeNeighborhoodLookup = buildNeighborhoodLookup(neighborhoods);

        String selectedNeighborhood = findMatchingNeighborhoodName(neighborhoodParam, neighborhoods,
                activeNeighborhoodLookup);
        String selectedStage = normalizeToCanonicalStage(stageParam);

        try (PrintWriter out = response.getWriter()) {
            out.println("<!doctype html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"utf-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />");
            out.println("  <title>Emerging Standards Topics - InteropHub</title>");
            out.println("  <style>");
            out.println(
                    "    :root { --bg:#f6f7f8; --panel:#ffffff; --text:#0f1720; --muted:#5b6673; --border:#d5dde5; --accent:#0b6fb8; --accent-soft:#e6f1fb; --tag:#edf4e8; --tag-text:#2d5a27; }");
            out.println("    * { box-sizing:border-box; }");
            out.println(
                    "    body { margin:0; background:radial-gradient(circle at top left, #eef4f8 0, #f6f7f8 55%); color:var(--text); font-family:\"Segoe UI\", Tahoma, Geneva, Verdana, sans-serif; }");
            out.println("    .estp-shell { max-width:1200px; margin:0 auto; padding:1.25rem; }");
            out.println(
                    "    .estp-layout { display:grid; grid-template-columns:280px minmax(0,1fr); gap:1rem; align-items:start; }");
            out.println(
                    "    .estp-sidebar, .estp-main { background:var(--panel); border:1px solid var(--border); border-radius:14px; box-shadow:0 3px 10px rgba(15,23,32,0.05); }");
            out.println("    .estp-sidebar { position:sticky; top:1rem; overflow:hidden; }");
            out.println("    .estp-sidebar h2 { margin:0; padding:1rem 1rem 0.5rem 1rem; font-size:1rem; }");
            out.println("    .estp-nav-group { padding:0 0.75rem 0.75rem 0.75rem; }");
            out.println(
                    "    .estp-nav-title { margin:0.75rem 0.25rem 0.5rem 0.25rem; color:var(--muted); font-size:0.83rem; text-transform:uppercase; letter-spacing:0.04em; }");
            out.println(
                    "    .estp-nav-link { display:block; padding:0.58rem 0.65rem; border-radius:9px; color:var(--text); text-decoration:none; font-size:0.93rem; }");
            out.println("    .estp-nav-link:hover { background:#f2f5f8; }");
            out.println(
                    "    .estp-nav-link.is-active { background:var(--accent-soft); color:#084a79; font-weight:600; }");
            out.println("    .estp-main { padding:1.1rem 1.1rem 1.2rem 1.1rem; }");
            out.println("    .estp-title { margin:0 0 0.35rem 0; font-size:1.5rem; }");
            out.println("    .estp-subtitle { margin:0 0 1rem 0; color:var(--muted); }");
            out.println(
                    "    .estp-about { background:#fbfcfd; border:1px dashed var(--border); border-radius:10px; padding:0.85rem 0.95rem; }");
            out.println(
                    "    .estp-stage-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr)); gap:0.8rem; margin-top:1rem; }");
            out.println(
                    "    .estp-stage-card { border:1px solid var(--border); border-radius:10px; padding:0.7rem 0.8rem; background:#fff; }");
            out.println("    .estp-stage-card h3 { margin:0 0 0.4rem 0; font-size:1rem; }");
            out.println("    .estp-topic-group { margin-top:1.1rem; }");
            out.println("    .estp-topic-group h3 { margin:0 0 0.5rem 0; font-size:1.1rem; }");
            out.println("    .estp-topic-list { display:grid; gap:0.65rem; }");
            out.println(
                    "    .estp-topic-card { border:1px solid var(--border); border-radius:10px; padding:0.75rem 0.85rem; background:#fff; }");
            out.println(
                    "    .estp-topic-head { display:flex; gap:0.6rem; align-items:center; justify-content:space-between; flex-wrap:wrap; }");
            out.println("    .estp-topic-name { margin:0; font-size:1rem; }");
            out.println(
                    "    .estp-badge { background:var(--tag); color:var(--tag-text); border-radius:999px; padding:0.18rem 0.5rem; font-size:0.78rem; font-weight:600; }");
            out.println("    .estp-topic-desc { margin:0.45rem 0 0.4rem 0; color:var(--text); }");
            out.println("    .estp-topic-meta { margin:0; color:var(--muted); font-size:0.86rem; }");
            out.println("    .estp-link { color:var(--accent); text-decoration:none; }");
            out.println("    .estp-link:hover { text-decoration:underline; }");
            out.println(
                    "    @media (max-width: 930px) { .estp-layout { grid-template-columns:1fr; } .estp-sidebar { position:static; } }");
            out.println("  </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("  <div class=\"estp-shell\">");
            out.println("    <div class=\"estp-layout\">");
            renderSidebar(out, contextPath, neighborhoods, view, selectedNeighborhood, selectedStage);
            out.println("      <main class=\"estp-main\">");

            if (VIEW_NEIGHBORHOOD.equalsIgnoreCase(view) && selectedNeighborhood != null) {
                renderNeighborhoodView(out, selectedNeighborhood, neighborhoods, allTopics, activeNeighborhoodLookup);
            } else if (VIEW_STAGE.equalsIgnoreCase(view) && selectedStage != null) {
                renderStageView(out, selectedStage, allTopics, activeNeighborhoodLookup);
            } else {
                renderDefaultAboutView(out, allTopics.size());
            }

            out.println("      </main>");
            out.println("    </div>");
            out.println("  </div>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderSidebar(PrintWriter out, String contextPath, List<EsNeighborhood> neighborhoods, String view,
            String selectedNeighborhood, String selectedStage) {
        out.println("      <aside class=\"estp-sidebar\">");
        out.println("        <h2>Explore Topics</h2>");
        out.println("        <div class=\"estp-nav-group\">");

        String overviewActive = (!VIEW_NEIGHBORHOOD.equalsIgnoreCase(view) && !VIEW_STAGE.equalsIgnoreCase(view))
                ? " is-active"
                : "";
        out.println("          <a class=\"estp-nav-link" + overviewActive + "\" href=\"" + contextPath
                + "/es/topics\">About Emerging Standards</a>");

        out.println("          <p class=\"estp-nav-title\">By Neighborhood</p>");
        for (EsNeighborhood neighborhood : neighborhoods) {
            String neighborhoodName = orEmpty(neighborhood.getNeighborhoodName());
            String active = VIEW_NEIGHBORHOOD.equalsIgnoreCase(view)
                    && equalsIgnoreCaseTrimmed(selectedNeighborhood, neighborhoodName) ? " is-active" : "";
            out.println("          <a class=\"estp-nav-link" + active + "\" href=\"" + contextPath
                    + "/es/topics?view=neighborhood&n=" + urlEncode(neighborhoodName) + "\">"
                    + escapeHtml(neighborhoodName) + "</a>");
        }

        String uncategorizedActive = VIEW_NEIGHBORHOOD.equalsIgnoreCase(view)
                && equalsIgnoreCaseTrimmed(selectedNeighborhood, UNCATEGORIZED_LABEL)
                        ? " is-active"
                        : "";
        out.println("          <a class=\"estp-nav-link" + uncategorizedActive + "\" href=\"" + contextPath
                + "/es/topics?view=neighborhood&n=" + urlEncode(UNCATEGORIZED_LABEL) + "\">"
                + UNCATEGORIZED_LABEL + "</a>");

        Set<String> discoveredNeighborhoods = neighborhoods.stream()
                .map(EsNeighborhood::getNeighborhoodName)
                .filter(name -> trimToNull(name) != null)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        out.println("          <p class=\"estp-nav-title\">By Stage</p>");
        for (String stage : STAGE_ORDER) {
            String active = VIEW_STAGE.equalsIgnoreCase(view) && equalsIgnoreCaseTrimmed(stage, selectedStage)
                    ? " is-active"
                    : "";
            out.println("          <a class=\"estp-nav-link" + active + "\" href=\"" + contextPath
                    + "/es/topics?view=stage&s=" + urlEncode(stage) + "\">" + escapeHtml(stage) + "</a>");
        }

        if (discoveredNeighborhoods.isEmpty()) {
            out.println("          <p class=\"estp-topic-meta\">No neighborhoods are configured yet.</p>");
        }

        out.println("        </div>");
        out.println("      </aside>");
    }

    private void renderDefaultAboutView(PrintWriter out, int topicCount) {
        out.println("        <h1 class=\"estp-title\">Emerging Standards Topics</h1>");
        out.println(
                "        <p class=\"estp-subtitle\">Public view of active topics currently tracked by InteropHub.</p>");
        out.println("        <section class=\"estp-about\">");
        out.println(
                "          <p>Emerging Standards topics represent active work that needs input and visibility across the immunization ecosystem. Use the left panel to browse by neighborhood or by lifecycle stage.</p>");
        out.println("          <p><strong>Active topics currently listed:</strong> " + topicCount + "</p>");
        out.println("        </section>");

        out.println("        <section class=\"estp-topic-group\">");
        out.println("          <h2>About The Stages</h2>");
        out.println("          <div class=\"estp-stage-grid\">");
        for (String stage : STAGE_ORDER) {
            out.println("            <article class=\"estp-stage-card\">");
            out.println("              <h3>" + escapeHtml(stage) + "</h3>");
            out.println("              <p class=\"estp-topic-meta\">"
                    + escapeHtml(orEmpty(STAGE_DESCRIPTIONS.get(stage))) + "</p>");
            out.println("            </article>");
        }
        out.println("          </div>");
        out.println("        </section>");
    }

    private void renderNeighborhoodView(PrintWriter out, String selectedNeighborhood,
            List<EsNeighborhood> neighborhoods,
            List<EsTopic> allTopics, Map<String, String> activeNeighborhoodLookup) {
        out.println("        <h1 class=\"estp-title\">Neighborhood: " + escapeHtml(selectedNeighborhood) + "</h1>");

        if (equalsIgnoreCaseTrimmed(selectedNeighborhood, UNCATEGORIZED_LABEL)) {
            out.println("        <p class=\"estp-subtitle\">Topics with no matching active neighborhood.</p>");
        } else {
            EsNeighborhood match = neighborhoods.stream()
                    .filter(neighborhood -> equalsIgnoreCaseTrimmed(neighborhood.getNeighborhoodName(),
                            selectedNeighborhood))
                    .findFirst()
                    .orElse(null);

            if (match != null && trimToNull(match.getDescription()) != null) {
                out.println("        <p class=\"estp-subtitle\">" + escapeHtml(match.getDescription()) + "</p>");
            } else {
                out.println(
                        "        <p class=\"estp-subtitle\">Topics currently classified under this neighborhood.</p>");
            }
        }

        List<EsTopic> filtered = allTopics.stream()
                .filter(topic -> topicMatchesNeighborhood(topic, selectedNeighborhood, activeNeighborhoodLookup))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            out.println("        <p>No active topics were found for this neighborhood.</p>");
            return;
        }

        Map<String, List<EsTopic>> byStage = groupByStage(filtered);
        for (Map.Entry<String, List<EsTopic>> entry : byStage.entrySet()) {
            renderTopicGroup(out, entry.getKey(), entry.getValue());
        }
    }

    private void renderStageView(PrintWriter out, String selectedStage, List<EsTopic> allTopics,
            Map<String, String> activeNeighborhoodLookup) {
        out.println("        <h1 class=\"estp-title\">Stage: " + escapeHtml(selectedStage) + "</h1>");
        out.println("        <p class=\"estp-subtitle\">" + escapeHtml(orEmpty(STAGE_DESCRIPTIONS.get(selectedStage)))
                + "</p>");

        List<EsTopic> filtered = allTopics.stream()
                .filter(topic -> equalsIgnoreCaseTrimmed(topic.getStage(), selectedStage))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            out.println("        <p>No active topics were found in this stage.</p>");
            return;
        }

        Map<String, List<EsTopic>> byNeighborhood = new LinkedHashMap<>();
        for (EsTopic topic : filtered) {
            List<String> matchedNeighborhoods = getMatchedActiveNeighborhoods(topic, activeNeighborhoodLookup);
            if (matchedNeighborhoods.isEmpty()) {
                byNeighborhood.computeIfAbsent(UNCATEGORIZED_LABEL, ignored -> new ArrayList<>()).add(topic);
            } else {
                for (String neighborhood : matchedNeighborhoods) {
                    byNeighborhood.computeIfAbsent(neighborhood, ignored -> new ArrayList<>()).add(topic);
                }
            }
        }

        for (Map.Entry<String, List<EsTopic>> entry : byNeighborhood.entrySet()) {
            renderTopicGroup(out, entry.getKey(), entry.getValue());
        }
    }

    private void renderTopicGroup(PrintWriter out, String groupName, List<EsTopic> topics) {
        out.println("        <section class=\"estp-topic-group\">");
        out.println("          <h3>" + escapeHtml(groupName) + "</h3>");
        out.println("          <div class=\"estp-topic-list\">");
        for (EsTopic topic : topics) {
            out.println("            <article class=\"estp-topic-card\">");
            out.println("              <div class=\"estp-topic-head\">");
            out.println("                <h4 class=\"estp-topic-name\">" + escapeHtml(orEmpty(topic.getTopicName()))
                    + "</h4>");
            if (trimToNull(topic.getStage()) != null) {
                out.println("                <span class=\"estp-badge\">" + escapeHtml(topic.getStage().trim())
                        + "</span>");
            }
            out.println("              </div>");
            if (trimToNull(topic.getDescription()) != null) {
                out.println(
                        "              <p class=\"estp-topic-desc\">" + escapeHtml(topic.getDescription()) + "</p>");
            }

            List<String> metaItems = new ArrayList<>();
            if (trimToNull(topic.getNeighborhood()) != null) {
                metaItems.add("Neighborhood: " + escapeHtml(topic.getNeighborhood().trim()));
            }
            if (trimToNull(topic.getPolicyStatus()) != null) {
                metaItems.add("Policy Status: " + escapeHtml(topic.getPolicyStatus().trim()));
            }
            if (trimToNull(topic.getTopicType()) != null) {
                metaItems.add("Type: " + escapeHtml(topic.getTopicType().trim()));
            }
            if (!metaItems.isEmpty()) {
                out.println("              <p class=\"estp-topic-meta\">" + String.join(" | ", metaItems) + "</p>");
            }

            if (trimToNull(topic.getConfluenceUrl()) != null) {
                out.println("              <p class=\"estp-topic-meta\"><a class=\"estp-link\" href=\""
                        + escapeHtml(topic.getConfluenceUrl())
                        + "\" target=\"_blank\" rel=\"noopener\">Learn more in Confluence</a></p>");
            }
            out.println("            </article>");
        }
        out.println("          </div>");
        out.println("        </section>");
    }

    private Map<String, List<EsTopic>> groupByStage(List<EsTopic> topics) {
        Map<String, List<EsTopic>> grouped = new LinkedHashMap<>();
        for (String stage : STAGE_ORDER) {
            grouped.put(stage, new ArrayList<>());
        }
        grouped.put("Other", new ArrayList<>());

        for (EsTopic topic : topics) {
            String canonicalStage = normalizeToCanonicalStage(topic.getStage());
            String key = canonicalStage == null ? "Other" : canonicalStage;
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(topic);
        }

        return grouped.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private String findMatchingNeighborhoodName(String requested, List<EsNeighborhood> neighborhoods,
            Map<String, String> activeNeighborhoodLookup) {
        String normalizedRequested = trimToNull(requested);
        if (normalizedRequested == null) {
            return null;
        }
        if (equalsIgnoreCaseTrimmed(normalizedRequested, UNCATEGORIZED_LABEL)) {
            return UNCATEGORIZED_LABEL;
        }
        for (EsNeighborhood neighborhood : neighborhoods) {
            String neighborhoodName = neighborhood.getNeighborhoodName();
            if (equalsIgnoreCaseTrimmed(normalizedRequested, neighborhoodName)) {
                return neighborhoodName.trim();
            }
        }
        String normalizedKey = normalizedRequested.toLowerCase(Locale.ROOT);
        if (activeNeighborhoodLookup.containsKey(normalizedKey)) {
            return activeNeighborhoodLookup.get(normalizedKey);
        }
        return null;
    }

    private boolean topicMatchesNeighborhood(EsTopic topic, String selectedNeighborhood,
            Map<String, String> activeNeighborhoodLookup) {
        if (equalsIgnoreCaseTrimmed(selectedNeighborhood, UNCATEGORIZED_LABEL)) {
            return getMatchedActiveNeighborhoods(topic, activeNeighborhoodLookup).isEmpty();
        }
        return getMatchedActiveNeighborhoods(topic, activeNeighborhoodLookup).stream()
                .anyMatch(name -> equalsIgnoreCaseTrimmed(name, selectedNeighborhood));
    }

    private List<String> getMatchedActiveNeighborhoods(EsTopic topic, Map<String, String> activeNeighborhoodLookup) {
        List<String> matched = new ArrayList<>();
        for (String token : parseNeighborhoodTokens(topic.getNeighborhood())) {
            String canonical = activeNeighborhoodLookup.get(token.toLowerCase(Locale.ROOT));
            if (canonical != null
                    && matched.stream().noneMatch(existing -> equalsIgnoreCaseTrimmed(existing, canonical))) {
                matched.add(canonical);
            }
        }
        return matched;
    }

    private List<String> parseNeighborhoodTokens(String neighborhoodRaw) {
        String normalized = trimToNull(neighborhoodRaw);
        if (normalized == null) {
            return List.of();
        }
        String[] parts = normalized.split(",");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            String token = trimToNull(part);
            if (token != null) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Map<String, String> buildNeighborhoodLookup(List<EsNeighborhood> neighborhoods) {
        Map<String, String> lookup = new LinkedHashMap<>();
        for (EsNeighborhood neighborhood : neighborhoods) {
            String name = trimToNull(neighborhood.getNeighborhoodName());
            if (name != null) {
                lookup.put(name.toLowerCase(Locale.ROOT), name);
            }
        }
        return lookup;
    }

    private String normalizeToCanonicalStage(String stage) {
        String normalized = trimToNull(stage);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        for (String candidate : STAGE_ORDER) {
            if (candidate.toLowerCase(Locale.ROOT).equals(lowered)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean equalsIgnoreCaseTrimmed(String a, String b) {
        String normalizedA = trimToNull(a);
        String normalizedB = trimToNull(b);
        if (normalizedA == null || normalizedB == null) {
            return normalizedA == normalizedB;
        }
        return normalizedA.equalsIgnoreCase(normalizedB);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String escapeHtml(String value) {
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
