package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsTopicReviewService;

public class EsTopicReviewServlet extends HttpServlet {

    private static final List<String> STAGE_ORDER = List.of("Start", "Draft", "Gather", "Monitor", "Parked",
            "Pilot", "Rollout");
    private static final String OTHER_LABEL = "Other";

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsTopicDao topicDao;
    private final EsTopicReviewService reviewService;

    public EsTopicReviewServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
        this.topicDao = new EsTopicDao();
        this.reviewService = new EsTopicReviewService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> user = requireAuthenticatedUser(request, response);
        if (user.isEmpty()) {
            return;
        }

        String campaignCode = parseCampaignCode(request);
        if (campaignCode == null) {
            renderCampaignNotFound(response, request.getContextPath(), null);
            return;
        }

        Optional<EsCampaign> campaign = findCampaignExact(campaignCode);
        if (campaign.isEmpty()) {
            renderCampaignNotFound(response, request.getContextPath(), campaignCode);
            return;
        }

        List<EsCampaignTopicBrowseRow> rows = topicDao.findAllActiveBrowseRowsOrdered();
        Map<Long, Integer> scoreByTopicId = reviewService.findUserScoresByTopicId(
                campaign.get().getEsCampaignId(), user.get().getUserId());

        renderPage(response, request.getContextPath(), campaign.get(), rows, scoreByTopicId);
    }

    private void renderPage(HttpServletResponse response, String contextPath, EsCampaign campaign,
            List<EsCampaignTopicBrowseRow> rows, Map<Long, Integer> scoreByTopicId) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        Map<String, Map<String, List<EsCampaignTopicBrowseRow>>> grouped = groupByNeighborhoodThenStage(rows);
        int totalTopics = rows.size();
        int reviewedCount = scoreByTopicId.size();
        int leftCount = Math.max(0, totalTopics - reviewedCount);

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Emerging Standards Review - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"es-topics-page es-review-page\">");
            out.println("    <section class=\"es-topics-header\">");
            out.println("      <h1>Emerging Standards Topic Review</h1>");
            out.println("      <p>Indicate whether each topic is worth discussing as a community,</p>");
            out.println(
                    "      <p class=\"es-review-scale-hint\">Score guide: 0 no value, 1 very low, 2 low, 3 worth discussing, 4 high, 5 critical.</p>");
            out.println("      <label for=\"topic-search\" class=\"es-topics-search-label\">Search topics</label>");
            out.println("      <input id=\"topic-search\" class=\"es-topics-search\" type=\"search\""
                    + " placeholder=\"Search by topic name or description\" autocomplete=\"off\" />");
            out.println("    </section>");

            out.println("    <section id=\"topic-groups\" class=\"es-topic-groups\">");
            for (Map.Entry<String, Map<String, List<EsCampaignTopicBrowseRow>>> neighborhoodEntry : grouped
                    .entrySet()) {
                String neighborhood = neighborhoodEntry.getKey();
                Map<String, List<EsCampaignTopicBrowseRow>> stageGroups = neighborhoodEntry.getValue();
                out.println("      <section class=\"es-neighborhood-group\" data-neighborhood-name=\""
                        + escapeHtml(neighborhood) + "\">");
                out.println("        <h2 class=\"es-stage-title\">" + escapeHtml(neighborhood) + "</h2>");
                for (String stage : orderedStageLabels()) {
                    List<EsCampaignTopicBrowseRow> stageRows = stageGroups.getOrDefault(stage, List.of());
                    if (!stageRows.isEmpty()) {
                        renderStage(out, stage, stageRows, scoreByTopicId);
                    }
                }
                out.println("      </section>");
            }
            out.println("    </section>");

            out.println("    <div id=\"es-detail-overlay\" class=\"es-detail-overlay\" hidden></div>");
            out.println("    <aside id=\"es-detail-sheet\" class=\"es-detail-sheet\" hidden>");
            out.println("      <h2 id=\"es-detail-title\"></h2>");
            out.println("      <p id=\"es-detail-stage\" class=\"es-detail-stage\"></p>");
            out.println("      <p id=\"es-detail-topic-type\" class=\"es-detail-stage\" hidden></p>");
            out.println("      <p id=\"es-detail-policy-status\" class=\"es-detail-stage\" hidden></p>");
            out.println("      <p id=\"es-detail-description\" class=\"es-detail-description\"></p>");
            out.println(
                    "      <label for=\"es-detail-comment\" class=\"es-review-comment-label\">Optional comment or question</label>");
            out.println("      <textarea id=\"es-detail-comment\" class=\"es-review-comment-input\" maxlength=\"2000\""
                    + " placeholder=\"Add a comment/question for this topic\"></textarea>");
            out.println("      <p id=\"es-detail-comment-status\" class=\"es-review-comment-status\" hidden></p>");
            out.println("      <div class=\"es-detail-actions\">");
            out.println("        <button type=\"button\" id=\"es-detail-save-comment\">Save Comment</button>");
            out.println(
                    "        <button type=\"button\" id=\"es-detail-close\" class=\"es-secondary-button\">Close</button>");
            out.println("      </div>");
            out.println("    </aside>");

            out.println("    <div class=\"es-review-progress-footer\">");
            out.println("      <span id=\"es-review-progress\" data-total=\"" + totalTopics + "\" data-reviewed=\""
                    + reviewedCount + "\">" + reviewedCount + " reviewed - " + leftCount + " left</span>");
            out.println("    </div>");

            out.println("  </main>");

            out.println("  <script>");
            out.println("    (function() {");
            out.println("      var campaignCode = " + quoteJs(campaign.getCampaignCode()) + ";");
            out.println("      var rows = Array.prototype.slice.call(document.querySelectorAll('.es-topic-row'));");
            out.println(
                    "      var stageGroups = Array.prototype.slice.call(document.querySelectorAll('.es-stage-group'));");
            out.println(
                    "      var neighborhoodGroups = Array.prototype.slice.call(document.querySelectorAll('.es-neighborhood-group'));");
            out.println("      var progress = document.getElementById('es-review-progress');");
            out.println("      var searchInput = document.getElementById('topic-search');");
            out.println("      var detailOverlay = document.getElementById('es-detail-overlay');");
            out.println("      var detailSheet = document.getElementById('es-detail-sheet');");
            out.println("      var detailTitle = document.getElementById('es-detail-title');");
            out.println("      var detailStage = document.getElementById('es-detail-stage');");
            out.println("      var detailTopicType = document.getElementById('es-detail-topic-type');");
            out.println("      var detailPolicyStatus = document.getElementById('es-detail-policy-status');");
            out.println("      var detailDescription = document.getElementById('es-detail-description');");
            out.println("      var detailComment = document.getElementById('es-detail-comment');");
            out.println("      var detailCommentStatus = document.getElementById('es-detail-comment-status');");
            out.println("      var detailSaveComment = document.getElementById('es-detail-save-comment');");
            out.println("      var detailClose = document.getElementById('es-detail-close');");
            out.println("      var currentDetailTopicId = null;");
            out.println("      var currentDetailTopicName = null;");
            out.println("      var totalTopics = parseInt(progress.getAttribute('data-total') || '0', 10) || 0;");
            out.println("      var reviewedCount = parseInt(progress.getAttribute('data-reviewed') || '0', 10) || 0;");

            out.println("      function updateProgress(count) {");
            out.println("        reviewedCount = count;");
            out.println("        var left = Math.max(0, totalTopics - reviewedCount);");
            out.println("        progress.textContent = reviewedCount + ' reviewed - ' + left + ' left';");
            out.println("      }");

            out.println("      function applyFilter() {");
            out.println("        var term = (searchInput.value || '').toLowerCase();");
            out.println("        rows.forEach(function(row) {");
            out.println("          var text = row.getAttribute('data-search') || ''; ");
            out.println("          var hidden = term.length > 0 && text.indexOf(term) === -1;");
            out.println("          row.classList.toggle('is-filtered-out', hidden);");
            out.println("        });");
            out.println("        stageGroups.forEach(function(group) {");
            out.println("          var visible = group.querySelector('.es-topic-row:not(.is-filtered-out)');");
            out.println("          group.style.display = visible ? '' : 'none';");
            out.println("        });");
            out.println("        neighborhoodGroups.forEach(function(group) {");
            out.println("          var visible = group.querySelector('.es-topic-row:not(.is-filtered-out)');");
            out.println("          group.style.display = visible ? '' : 'none';");
            out.println("        });");
            out.println("      }");

            out.println("      function setRowReviewed(row, score) {");
            out.println("        row.classList.add('is-reviewed');");
            out.println("        var selectedWrap = row.querySelector('.es-review-selected-wrap');");
            out.println("        var selectedBtn = row.querySelector('.es-review-selected-value');");
            out.println("        var editWrap = row.querySelector('.es-review-edit-wrap');");
            out.println("        if (selectedWrap) { selectedWrap.hidden = false; }");
            out.println(
                    "        if (selectedBtn) { selectedBtn.textContent = String(score); selectedBtn.setAttribute('data-score', String(score)); }");
            out.println("        if (editWrap) { editWrap.hidden = true; }");
            out.println("      }");

            out.println("      function setRowEditing(row) {");
            out.println("        var selectedWrap = row.querySelector('.es-review-selected-wrap');");
            out.println("        var editWrap = row.querySelector('.es-review-edit-wrap');");
            out.println("        if (selectedWrap) { selectedWrap.hidden = true; }");
            out.println("        if (editWrap) { editWrap.hidden = false; }");
            out.println("      }");

            out.println("      function saveScore(row, topicId, score) {");
            out.println("        var params = new URLSearchParams();");
            out.println("        params.set('campaignCode', campaignCode);");
            out.println("        params.set('topicId', String(topicId));");
            out.println("        params.set('score', String(score));");
            out.println("        fetch('" + contextPath + "/es/review/save', {");
            out.println("          method: 'POST',");
            out.println("          headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
            out.println("          body: params.toString()");
            out.println("        }).then(function(res) { return res.json(); }).then(function(json) {");
            out.println("          if (!json || !json.ok) {");
            out.println("            window.alert((json && json.error) ? json.error : 'Unable to save score.');");
            out.println("            return;");
            out.println("          }");
            out.println("          setRowReviewed(row, json.score);");
            out.println(
                    "          if (typeof json.reviewedCount === 'number') { updateProgress(json.reviewedCount); }");
            out.println("        }).catch(function() {");
            out.println("          window.alert('Unable to save score.');");
            out.println("        });");
            out.println("      }");

            out.println("      function openDetail(row) {");
            out.println("        currentDetailTopicId = row.getAttribute('data-topic-id');");
            out.println("        currentDetailTopicName = row.getAttribute('data-topic-name') || ''; ");
            out.println("        detailTitle.textContent = currentDetailTopicName;");
            out.println(
                    "        detailStage.textContent = 'Stage: ' + (row.getAttribute('data-topic-stage') || 'Other');");
            out.println("        var topicType = (row.getAttribute('data-topic-type') || '').trim();");
            out.println("        var policyStatus = (row.getAttribute('data-policy-status') || '').trim();");
            out.println("        if (topicType) {");
            out.println("          detailTopicType.textContent = 'Topic type: ' + topicType;");
            out.println("          detailTopicType.hidden = false;");
            out.println("        } else {");
            out.println("          detailTopicType.textContent = '';");
            out.println("          detailTopicType.hidden = true;");
            out.println("        }");
            out.println("        if (policyStatus) {");
            out.println("          detailPolicyStatus.textContent = 'Policy status: ' + policyStatus;");
            out.println("          detailPolicyStatus.hidden = false;");
            out.println("        } else {");
            out.println("          detailPolicyStatus.textContent = '';");
            out.println("          detailPolicyStatus.hidden = true;");
            out.println("        }");
            out.println(
                    "        detailDescription.textContent = row.getAttribute('data-topic-description') || 'No description available.';");
            out.println("        detailCommentStatus.hidden = true;");
            out.println("        detailCommentStatus.textContent = '';");
            out.println("        detailComment.value = '';");
            out.println("        detailOverlay.hidden = false;");
            out.println("        detailSheet.hidden = false;");
            out.println("        document.body.classList.add('es-sheet-open');");
            out.println("      }");

            out.println("      function closeDetail() {");
            out.println("        currentDetailTopicId = null;");
            out.println("        currentDetailTopicName = null;");
            out.println("        detailOverlay.hidden = true;");
            out.println("        detailSheet.hidden = true;");
            out.println("        document.body.classList.remove('es-sheet-open');");
            out.println("      }");

            out.println("      rows.forEach(function(row) {");
            out.println("        var topicId = row.getAttribute('data-topic-id');");
            out.println("        var content = row.querySelector('.es-topic-content');");
            out.println("        var selectedBtn = row.querySelector('.es-review-selected-value');");
            out.println(
                    "        var scoreButtons = Array.prototype.slice.call(row.querySelectorAll('.es-review-score-btn')); ");
            out.println("        if (content) {");
            out.println("          content.addEventListener('click', function() { openDetail(row); });");
            out.println("        }");
            out.println("        if (selectedBtn) {");
            out.println("          selectedBtn.addEventListener('click', function(evt) {");
            out.println("            evt.stopPropagation();");
            out.println("            setRowEditing(row);");
            out.println("          });");
            out.println("        }");
            out.println("        scoreButtons.forEach(function(btn) {");
            out.println("          btn.addEventListener('click', function(evt) {");
            out.println("            evt.stopPropagation();");
            out.println("            var score = parseInt(btn.getAttribute('data-score') || '0', 10) || 0;");
            out.println("            if (score < 0 || score > 5) { return; }");
            out.println("            saveScore(row, topicId, score);");
            out.println("          });");
            out.println("        });");
            out.println("      });");

            out.println("      detailSaveComment.addEventListener('click', function() {");
            out.println("        if (!currentDetailTopicId) { return; }");
            out.println("        var text = (detailComment.value || '').trim();");
            out.println("        if (text.length === 0) {");
            out.println("          detailCommentStatus.hidden = false;");
            out.println("          detailCommentStatus.textContent = 'Enter a comment before saving.';");
            out.println("          return;");
            out.println("        }");
            out.println("        var params = new URLSearchParams();");
            out.println("        params.set('campaignCode', campaignCode);");
            out.println("        params.set('topicId', currentDetailTopicId);");
            out.println("        params.set('commentText', text);");
            out.println("        fetch('" + contextPath + "/es/review/comment', {");
            out.println("          method: 'POST',");
            out.println("          headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
            out.println("          body: params.toString()");
            out.println("        }).then(function(res) { return res.json(); }).then(function(json) {");
            out.println("          if (!json || !json.ok) {");
            out.println("            detailCommentStatus.hidden = false;");
            out.println(
                    "            detailCommentStatus.textContent = (json && json.error) ? json.error : 'Unable to save comment.';");
            out.println("            return;");
            out.println("          }");
            out.println("          detailComment.value = '';");
            out.println("          detailCommentStatus.hidden = false;");
            out.println("          detailCommentStatus.textContent = 'Comment saved.';");
            out.println("        }).catch(function() {");
            out.println("          detailCommentStatus.hidden = false;");
            out.println("          detailCommentStatus.textContent = 'Unable to save comment.';");
            out.println("        });");
            out.println("      });");

            out.println("      detailOverlay.addEventListener('click', closeDetail);");
            out.println("      detailClose.addEventListener('click', closeDetail);");
            out.println("      searchInput.addEventListener('input', applyFilter);");
            out.println("      applyFilter();");
            out.println("    })();");
            out.println("  </script>");

            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderStage(PrintWriter out, String stageName, List<EsCampaignTopicBrowseRow> rows,
            Map<Long, Integer> scoreByTopicId) {
        out.println("      <section class=\"es-stage-group\" data-stage-name=\"" + escapeHtml(stageName) + "\">");
        out.println("        <div class=\"es-topic-list\">");
        for (EsCampaignTopicBrowseRow row : rows) {
            String topicName = orEmpty(row.getTopicName());
            String description = orEmpty(row.getDescription());
            String preview = description.length() <= 130 ? description : description.substring(0, 127) + "...";
            Integer savedScore = scoreByTopicId.get(row.getEsTopicId());
            boolean reviewed = savedScore != null;

            out.println("          <article class=\"es-topic-row es-review-topic-row" + (reviewed ? " is-reviewed" : "")
                    + "\""
                    + " data-topic-id=\"" + row.getEsTopicId() + "\""
                    + " data-topic-name=\"" + escapeHtml(topicName) + "\""
                    + " data-topic-description=\"" + escapeHtml(description) + "\""
                    + " data-topic-type=\"" + escapeHtml(orEmpty(row.getTopicType())) + "\""
                    + " data-policy-status=\"" + escapeHtml(orEmpty(row.getPolicyStatus())) + "\""
                    + " data-topic-neighborhood=\"" + escapeHtml(normalizeNeighborhood(row.getNeighborhood())) + "\""
                    + " data-topic-stage=\"" + escapeHtml(orEmpty(normalizeStage(row.getStage()))) + "\""
                    + " data-search=\"" + escapeHtml((topicName + " " + description).toLowerCase()) + "\">");

            out.println("            <div class=\"es-review-score-wrap\">");
            out.println("              <div class=\"es-review-selected-wrap\"" + (reviewed ? "" : " hidden") + ">");
            out.println("                <button type=\"button\" class=\"es-review-selected-value\" data-score=\""
                    + (reviewed ? savedScore : "") + "\">" + (reviewed ? savedScore : "") + "</button>");
            out.println("              </div>");
            out.println("              <div class=\"es-review-edit-wrap\"" + (reviewed ? " hidden" : "") + ">");
            for (int score = 0; score <= 5; score++) {
                out.println(
                        "                <button type=\"button\" class=\"es-review-score-btn\" data-score=\"" + score
                                + "\">" + score + "</button>");
            }
            out.println("              </div>");
            out.println("            </div>");

            out.println("            <div class=\"es-topic-content\">");
            out.println("              <div class=\"es-topic-top\">");
            out.println("                <h3>" + escapeHtml(topicName) + "</h3>");
            out.println("                <span class=\"es-topic-state\"" + (reviewed ? "" : " hidden") + ">"
                    + (reviewed ? "Reviewed" : "") + "</span>");
            out.println("              </div>");
            out.println("              <p class=\"es-topic-preview\">" + escapeHtml(orEmpty(preview)) + "</p>");
            out.println("            </div>");
            out.println("          </article>");
        }
        out.println("        </div>");
        out.println("      </section>");
    }

    private Map<String, Map<String, List<EsCampaignTopicBrowseRow>>> groupByNeighborhoodThenStage(
            List<EsCampaignTopicBrowseRow> rows) {
        Map<String, Map<String, List<EsCampaignTopicBrowseRow>>> grouped = new LinkedHashMap<>();
        for (EsCampaignTopicBrowseRow row : rows) {
            String neighborhood = normalizeNeighborhood(row.getNeighborhood());
            String stage = normalizeStage(row.getStage());
            grouped
                    .computeIfAbsent(neighborhood, ignored -> createOrderedStageMap())
                    .computeIfAbsent(stage, ignored -> new ArrayList<>())
                    .add(row);
        }

        List<String> neighborhoodNames = new ArrayList<>(grouped.keySet());
        neighborhoodNames.sort((left, right) -> {
            boolean leftOther = OTHER_LABEL.equalsIgnoreCase(left);
            boolean rightOther = OTHER_LABEL.equalsIgnoreCase(right);
            if (leftOther != rightOther) {
                return leftOther ? 1 : -1;
            }
            return left.compareToIgnoreCase(right);
        });

        Map<String, Map<String, List<EsCampaignTopicBrowseRow>>> ordered = new LinkedHashMap<>();
        for (String neighborhood : neighborhoodNames) {
            ordered.put(neighborhood, grouped.get(neighborhood));
        }
        return ordered;
    }

    private Map<String, List<EsCampaignTopicBrowseRow>> createOrderedStageMap() {
        Map<String, List<EsCampaignTopicBrowseRow>> grouped = new LinkedHashMap<>();
        for (String stage : STAGE_ORDER) {
            grouped.put(stage, new ArrayList<>());
        }
        grouped.put(OTHER_LABEL, new ArrayList<>());
        return grouped;
    }

    private List<String> orderedStageLabels() {
        List<String> labels = new ArrayList<>(STAGE_ORDER);
        labels.add(OTHER_LABEL);
        return labels;
    }

    private String normalizeNeighborhood(String neighborhood) {
        if (neighborhood == null) {
            return OTHER_LABEL;
        }
        String trimmed = neighborhood.trim();
        return trimmed.isEmpty() ? OTHER_LABEL : trimmed;
    }

    private String normalizeStage(String stage) {
        if (stage == null) {
            return OTHER_LABEL;
        }
        String trimmed = stage.trim();
        for (String candidate : STAGE_ORDER) {
            if (candidate.equalsIgnoreCase(trimmed)) {
                return candidate;
            }
        }
        return OTHER_LABEL;
    }

    private Optional<User> requireAuthenticatedUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        return authenticatedUser;
    }

    private Optional<EsCampaign> findCampaignExact(String campaignCode) {
        return campaignDao.findByCampaignCode(campaignCode)
                .filter(campaign -> campaignCode.equals(campaign.getCampaignCode()));
    }

    private String parseCampaignCode(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            return null;
        }
        if (!pathInfo.startsWith("/")) {
            return null;
        }
        String trimmed = pathInfo.substring(1).trim();
        if (trimmed.isEmpty() || trimmed.contains("/")) {
            return null;
        }
        return trimmed;
    }

    private void renderCampaignNotFound(HttpServletResponse response, String contextPath, String campaignCode)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Campaign Not Found - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Campaign Not Found</h1>");
            out.println("    <p>The campaign code could not be resolved.</p>");
            if (campaignCode != null) {
                out.println("    <p><strong>Code:</strong> " + escapeHtml(campaignCode) + "</p>");
            }
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private String quoteJs(String value) {
        return "\"" + escapeHtml(orEmpty(value))
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
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
}
