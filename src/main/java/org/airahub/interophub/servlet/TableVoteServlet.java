package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.service.TableVoteService;

public class TableVoteServlet extends HttpServlet {

    private static final String ATTR_SESSION_KEY = "interophub.es.registration.sessionKey";
    private static final String ATTR_CAMPAIGN_CODE = "interophub.es.registration.campaignCode";
    private static final String ATTR_CAMPAIGN_REGISTRATION_ID = "interophub.es.registration.campaignRegistrationId";

    private final TableVoteService tableVoteService;

    public TableVoteServlet() {
        this.tableVoteService = new TableVoteService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PathParts parts = parsePath(request);
        if (parts == null) {
            renderNotFound(response, request.getContextPath(), "Route must include campaign code and table number.");
            return;
        }

        Optional<EsCampaign> campaign = tableVoteService.findCampaignExact(parts.campaignCode());
        if (campaign.isEmpty()) {
            renderNotFound(response, request.getContextPath(), "Campaign was not found.");
            return;
        }

        HttpSession session = request.getSession(true);
        String sessionKey = ensureSessionKey(session);
        List<EsCampaignTopicBrowseRow> tableTopics = tableVoteService.findTableTopics(campaign.get().getEsCampaignId(),
                parts.tableNo());
        if (tableTopics.isEmpty()) {
            renderNotFound(response, request.getContextPath(), "No topics are assigned to this table.");
            return;
        }

        int currentRoundNo = tableVoteService.resolveCurrentRound(campaign.get());
        Set<Long> sessionSelections = tableVoteService.findSessionSelections(campaign.get().getEsCampaignId(),
                parts.tableNo(), currentRoundNo, sessionKey);
        boolean forceVoteMode = "vote".equalsIgnoreCase(trimToNull(request.getParameter("view")));
        boolean showVoteMode = forceVoteMode || sessionSelections.isEmpty();

        Map<Long, Long> totals = showVoteMode
                ? Map.of()
                : tableVoteService.findVoteTotals(campaign.get().getEsCampaignId(), parts.tableNo(), currentRoundNo);

        String status = trimToNull(request.getParameter("status"));
        String statusMessage = null;
        if ("saved".equals(status)) {
            statusMessage = "Your selections were saved for Round " + currentRoundNo + ".";
        }

        renderPage(response, request.getContextPath(), campaign.get(), parts.tableNo(), currentRoundNo, tableTopics,
                sessionSelections, totals, showVoteMode, statusMessage, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        PathParts parts = parsePath(request);
        if (parts == null) {
            renderNotFound(response, request.getContextPath(), "Route must include campaign code and table number.");
            return;
        }

        Optional<EsCampaign> campaign = tableVoteService.findCampaignExact(parts.campaignCode());
        if (campaign.isEmpty()) {
            renderNotFound(response, request.getContextPath(), "Campaign was not found.");
            return;
        }

        HttpSession session = request.getSession(true);
        String sessionKey = ensureSessionKey(session);
        List<EsCampaignTopicBrowseRow> tableTopics = tableVoteService.findTableTopics(campaign.get().getEsCampaignId(),
                parts.tableNo());
        if (tableTopics.isEmpty()) {
            renderNotFound(response, request.getContextPath(), "No topics are assigned to this table.");
            return;
        }

        List<Long> selectedTopicIds = parseTopicIdsCsv(request.getParameter("selectedTopicIds"));
        String sessionCampaignCode = trimToNull((String) session.getAttribute(ATTR_CAMPAIGN_CODE));
        Long sessionCampaignRegistrationId = sessionAttributeLong(session, ATTR_CAMPAIGN_REGISTRATION_ID);

        try {
            tableVoteService.submitVote(campaign.get(), parts.tableNo(), sessionKey, selectedTopicIds,
                    sessionCampaignCode, sessionCampaignRegistrationId);

            response.sendRedirect(buildTableUrl(request.getContextPath(), parts.campaignCode(), parts.tableNo())
                    + "?status=saved&view=results");
            return;
        } catch (IllegalArgumentException ex) {
            int currentRoundNo = tableVoteService.resolveCurrentRound(campaign.get());
            Set<Long> sessionSelections = tableVoteService.findSessionSelections(campaign.get().getEsCampaignId(),
                    parts.tableNo(), currentRoundNo, sessionKey);
            renderPage(response, request.getContextPath(), campaign.get(), parts.tableNo(), currentRoundNo, tableTopics,
                    sessionSelections, Map.of(), true, null, ex.getMessage());
            return;
        }
    }

    private void renderPage(HttpServletResponse response, String contextPath, EsCampaign campaign, int tableNo,
            int roundNo, List<EsCampaignTopicBrowseRow> tableTopics, Set<Long> sessionSelections,
            Map<Long, Long> totalsByTopicId, boolean voteMode, String statusMessage, String errorMessage)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        String pageTitle = "Topics for Table " + tableNo;
        String instruction = voteMode
                ? "Select up to three topics you are interested in discussing today"
                : "Thank you for making your selections, please now discuss with your group";

        String selectedCsv = sessionSelections.stream().map(String::valueOf).collect(Collectors.joining(","));
        String tableUrl = buildTableUrl(contextPath, campaign.getCampaignCode(), tableNo);

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>" + escapeHtml(pageTitle) + " - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"es-topics-page\">");
            out.println("    <section class=\"es-topics-header\">");
            out.println("      <h1>" + escapeHtml(pageTitle) + "</h1>");
            out.println("      <p class=\"es-topics-campaign\">" + escapeHtml(instruction) + "</p>");
            out.println("      <p class=\"es-topics-campaign\"><strong>Campaign:</strong> "
                    + escapeHtml(orEmpty(campaign.getCampaignName()))
                    + " (" + escapeHtml(orEmpty(campaign.getCampaignCode())) + ")"
                    + " | <strong>Round:</strong> " + roundNo + "</p>");
            out.println("    </section>");

            if (statusMessage != null) {
                out.println("    <p class=\"es-status-message\">" + escapeHtml(statusMessage) + "</p>");
            }
            if (errorMessage != null) {
                out.println("    <p class=\"es-status-message es-vote-error\">" + escapeHtml(errorMessage) + "</p>");
            }

            out.println("    <section class=\"es-topic-groups\">");
            out.println("      <section class=\"es-stage-group\">");
            out.println("        <h2 class=\"es-stage-title\">Table Topics</h2>");
            out.println("        <div class=\"es-topic-list\">");

            for (EsCampaignTopicBrowseRow row : tableTopics) {
                boolean selectedBySession = sessionSelections.contains(row.getEsTopicId());
                long total = totalsByTopicId.getOrDefault(row.getEsTopicId(), 0L);
                String description = orEmpty(row.getDescription());
                String preview = description.length() <= 130 ? description : description.substring(0, 127) + "...";

                out.println("          <article class=\"es-topic-row" + (selectedBySession ? " is-selected" : "") + "\""
                        + " data-topic-id=\"" + row.getEsTopicId() + "\""
                        + " data-topic-name=\"" + escapeHtml(orEmpty(row.getTopicName())) + "\""
                        + " data-topic-description=\"" + escapeHtml(description) + "\""
                        + " data-topic-type=\"" + escapeHtml(orEmpty(row.getTopicType())) + "\""
                        + " data-policy-status=\"" + escapeHtml(orEmpty(row.getPolicyStatus())) + "\">");

                out.println("            <div class=\"es-topic-checkbox-wrap\">");
                if (voteMode) {
                    out.println("              <input class=\"es-topic-checkbox\" type=\"checkbox\""
                            + " data-topic-id=\"" + row.getEsTopicId() + "\""
                            + (selectedBySession ? " checked" : "")
                            + " aria-label=\"Select " + escapeHtml(orEmpty(row.getTopicName())) + "\" />");
                } else {
                    out.println("              <span class=\"es-vote-total\">" + total + "</span>");
                }
                out.println("            </div>");

                out.println("            <div class=\"es-topic-content\">");
                out.println("              <div class=\"es-topic-top\">");
                out.println("                <h3>" + escapeHtml(orEmpty(row.getTopicName())) + "</h3>");
                if (!voteMode && selectedBySession) {
                    out.println("                <span class=\"es-topic-state\">Your selection</span>");
                }
                out.println("              </div>");
                out.println("              <p class=\"es-topic-preview\">" + escapeHtml(preview) + "</p>");
                out.println("            </div>");
                out.println("          </article>");
            }

            out.println("        </div>");
            out.println("      </section>");
            out.println("    </section>");

            out.println("    <div id=\"es-detail-overlay\" class=\"es-detail-overlay\" hidden></div>");
            out.println("    <aside id=\"es-detail-sheet\" class=\"es-detail-sheet\" hidden>");
            out.println("      <h2 id=\"es-detail-title\"></h2>");
            out.println("      <p id=\"es-detail-description\" class=\"es-detail-description\"></p>");
            out.println("      <p id=\"es-detail-topic-type\" class=\"es-detail-stage\" hidden></p>");
            out.println("      <p id=\"es-detail-policy-status\" class=\"es-detail-stage\" hidden></p>");
            out.println("      <div class=\"es-detail-actions\">");
            out.println(
                    "        <button type=\"button\" id=\"es-detail-close\" class=\"es-secondary-button\">Close</button>");
            out.println("      </div>");
            out.println("    </aside>");

            if (voteMode) {
                out.println("    <form id=\"table-vote-form\" method=\"post\" action=\"" + tableUrl + "\">");
                out.println(
                        "      <input type=\"hidden\" name=\"selectedTopicIds\" id=\"table-selected-topic-ids\" value=\""
                                + escapeHtml(selectedCsv) + "\" />");
                out.println("      <div class=\"es-sticky-action\">");
                out.println("        <button id=\"table-submit-button\" type=\"submit\" disabled>Select (0)</button>");
                out.println("      </div>");
                out.println("    </form>");
            } else {
                out.println("    <section class=\"es-table-results-actions\">");
                out.println("      <a class=\"es-secondary-button\" href=\"" + tableUrl
                        + "?view=vote\">Revise selections</a>");
                out.println("      <a class=\"es-secondary-button\" href=\"" + tableUrl
                        + "?view=results\">Refresh totals</a>");
                out.println("    </section>");
            }

            out.println("  </main>");

            out.println("  <script>");
            out.println("    (function() {");
            out.println(
                    "      var rows = Array.prototype.slice.call(document.querySelectorAll('.es-topic-row'));\n"
                            + "      var overlay = document.getElementById('es-detail-overlay');\n"
                            + "      var sheet = document.getElementById('es-detail-sheet');\n"
                            + "      var title = document.getElementById('es-detail-title');\n"
                            + "      var description = document.getElementById('es-detail-description');\n"
                            + "      var topicType = document.getElementById('es-detail-topic-type');\n"
                            + "      var policyStatus = document.getElementById('es-detail-policy-status');\n"
                            + "      var close = document.getElementById('es-detail-close');");

            out.println("      function openDetail(row) {");
            out.println("        title.textContent = row.getAttribute('data-topic-name') || ''; ");
            out.println(
                    "        description.textContent = row.getAttribute('data-topic-description') || 'No description available.';");
            out.println("        var topicTypeValue = (row.getAttribute('data-topic-type') || '').trim();");
            out.println("        var policyStatusValue = (row.getAttribute('data-policy-status') || '').trim();");
            out.println("        if (topicTypeValue) {");
            out.println("          topicType.textContent = 'Topic type: ' + topicTypeValue;");
            out.println("          topicType.hidden = false;");
            out.println("        } else {");
            out.println("          topicType.textContent = '';");
            out.println("          topicType.hidden = true;");
            out.println("        }");
            out.println("        if (policyStatusValue) {");
            out.println("          policyStatus.textContent = 'Policy status: ' + policyStatusValue;");
            out.println("          policyStatus.hidden = false;");
            out.println("        } else {");
            out.println("          policyStatus.textContent = '';");
            out.println("          policyStatus.hidden = true;");
            out.println("        }");
            out.println("        overlay.hidden = false;");
            out.println("        sheet.hidden = false;");
            out.println("        document.body.classList.add('es-sheet-open');");
            out.println("      }");

            out.println("      function closeDetail() {");
            out.println("        overlay.hidden = true;");
            out.println("        sheet.hidden = true;");
            out.println("        document.body.classList.remove('es-sheet-open');");
            out.println("      }");

            out.println("      rows.forEach(function(row) {");
            out.println("        var content = row.querySelector('.es-topic-content');");
            out.println("        if (content) { content.addEventListener('click', function() { openDetail(row); }); }");
            out.println("      });");
            out.println("      if (overlay) { overlay.addEventListener('click', closeDetail); }");
            out.println("      if (close) { close.addEventListener('click', closeDetail); }");

            if (voteMode) {
                out.println(
                        "      var selectedInput = document.getElementById('table-selected-topic-ids');\n"
                                + "      var submitButton = document.getElementById('table-submit-button');\n"
                                + "      var checkboxes = Array.prototype.slice.call(document.querySelectorAll('.es-topic-checkbox'));\n"
                                + "      var selected = new Set();");

                out.println("      checkboxes.forEach(function(checkbox) {");
                out.println(
                        "        if (checkbox.checked) { selected.add(checkbox.getAttribute('data-topic-id')); }");
                out.println("      });");

                out.println("      function sync() {");
                out.println("        selectedInput.value = Array.from(selected).join(',');");
                out.println("        submitButton.textContent = 'Select (' + selected.size + ')';");
                out.println("        submitButton.disabled = selected.size === 0;");
                out.println("      }");

                out.println("      checkboxes.forEach(function(checkbox) {");
                out.println("        checkbox.addEventListener('change', function() {");
                out.println("          var topicId = checkbox.getAttribute('data-topic-id');");
                out.println("          if (checkbox.checked) {");
                out.println("            selected.add(topicId);");
                out.println("            if (selected.size > 3) {");
                out.println("              checkbox.checked = false;");
                out.println("              selected.delete(topicId);");
                out.println("              window.alert('Select no more than three topics.');");
                out.println("            }");
                out.println("          } else {");
                out.println("            selected.delete(topicId);");
                out.println("          }");
                out.println("          sync();");
                out.println("        });");
                out.println("      });");
                out.println("      sync();");
            }

            out.println("    })();");
            out.println("  </script>");

            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private String ensureSessionKey(HttpSession session) {
        String sessionKey = trimToNull((String) session.getAttribute(ATTR_SESSION_KEY));
        if (sessionKey == null) {
            sessionKey = session.getId();
            session.setAttribute(ATTR_SESSION_KEY, sessionKey);
        }
        return sessionKey;
    }

    private String buildTableUrl(String contextPath, String campaignCode, int tableNo) {
        return contextPath + "/table/" + URLEncoder.encode(orEmpty(campaignCode), StandardCharsets.UTF_8)
                + "/" + tableNo;
    }

    private PathParts parsePath(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || !pathInfo.startsWith("/")) {
            return null;
        }
        String[] raw = pathInfo.substring(1).split("/");
        if (raw.length != 2) {
            return null;
        }
        String campaignCode = trimToNull(raw[0]);
        Integer tableNo = parsePositiveInt(raw[1]);
        if (campaignCode == null || tableNo == null) {
            return null;
        }
        return new PathParts(campaignCode, tableNo);
    }

    private List<Long> parseTopicIdsCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            Long parsed = parseLong(part);
            if (parsed != null && parsed > 0L) {
                ids.add(parsed);
            }
        }
        return new ArrayList<>(ids);
    }

    private void renderNotFound(HttpServletResponse response, String contextPath, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Table Topics Not Found - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Table Topics Not Found</h1>");
            out.println("    <p>" + escapeHtml(orEmpty(message)) + "</p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private Long sessionAttributeLong(HttpSession session, String name) {
        Object value = session.getAttribute(name);
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value instanceof String stringValue) {
            return parseLong(stringValue);
        }
        return null;
    }

    private Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
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

    private record PathParts(String campaignCode, int tableNo) {
    }
}
