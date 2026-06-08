package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsTopicReviewService;

public class EsCdcReviewServlet extends HttpServlet {

    private static final List<SignalOption> SIGNAL_OPTIONS = List.of(
            new SignalOption(1, "No current activity observed"),
            new SignalOption(3, "Watching"),
            new SignalOption(4, "Seeing activity"));

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsTopicReviewService reviewService;

    public EsCdcReviewServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.reviewService = new EsTopicReviewService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> user = requireAuthenticatedUser(request, response);
        if (user.isEmpty()) {
            return;
        }
        if (!authFlowService.isCdcOrAdminUser(user.get())) {
            renderAccessDenied(response, request.getContextPath());
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

        List<EsCampaignTopicBrowseRow> rows = campaignTopicDao
                .findBrowseRowsByCampaignIdOrdered(campaign.get().getEsCampaignId());
        Map<Long, Integer> scoreByTopicId = reviewService.findUserScoresByTopicId(
                campaign.get().getEsCampaignId(), user.get().getUserId());

        renderPage(response, request.getContextPath(), campaign.get(), rows, scoreByTopicId);
    }

    private void renderPage(HttpServletResponse response, String contextPath, EsCampaign campaign,
            List<EsCampaignTopicBrowseRow> rows, Map<Long, Integer> scoreByTopicId) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>CDC Topic Signals - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"es-topics-page es-cdc-review-page\">");
            out.println("    <section class=\"es-topics-header\">");
            out.println("      <h1>CDC Topic Signals</h1>");
            out.println("      <div class=\"es-cdc-intro\">");
            out.println(
                    "        <p>Use this page to share informal individual observations about the topics currently selected for this Emerging Standards campaign. These responses are not official CDC policy positions, priorities, endorsements, or commitments. They are used only to help AIRA understand where additional community discussion may be useful. You may skip any topic.</p>");
            out.println("      </div>");
            out.println("      <label for=\"cdc-topic-search\" class=\"es-topics-search-label\">Search topics</label>");
            out.println("      <input id=\"cdc-topic-search\" class=\"es-topics-search\" type=\"search\""
                    + " placeholder=\"Search by topic name or description\" autocomplete=\"off\" />");
            out.println("    </section>");

            out.println("    <section id=\"cdc-topic-list\" class=\"es-topic-groups es-cdc-topic-list\">");
            if (rows.isEmpty()) {
                out.println("      <section class=\"es-stage-group\">");
                out.println("        <p>No topics are currently included in this campaign.</p>");
                out.println("      </section>");
            } else {
                for (EsCampaignTopicBrowseRow row : rows) {
                    renderTopicRow(out, contextPath, row, scoreByTopicId.get(row.getEsTopicId()));
                }
            }
            out.println("    </section>");
            out.println("  </main>");

            out.println("  <script>");
            out.println("    (function() {");
            out.println("      var campaignCode = " + quoteJs(campaign.getCampaignCode()) + ";");
            out.println("      var policyBlockedLabel = "
                    + quoteJs(EsTopicReviewService.CDC_POLICY_STATUS_NOT_SUPPORTED) + ";");
            out.println(
                    "      var rows = Array.prototype.slice.call(document.querySelectorAll('.es-cdc-topic-row')); ");
            out.println("      var searchInput = document.getElementById('cdc-topic-search');");
            out.println("      function applyFilter() {");
            out.println("        var term = (searchInput.value || '').toLowerCase();");
            out.println("        rows.forEach(function(row) {");
            out.println("          var text = row.getAttribute('data-search') || ''; ");
            out.println("          var hidden = term.length > 0 && text.indexOf(term) === -1;");
            out.println("          row.classList.toggle('is-filtered-out', hidden);");
            out.println("        });");
            out.println("      }");
            out.println("      function setRowStatus(row, message, isError) {");
            out.println("        var status = row.querySelector('.js-cdc-row-status');");
            out.println("        if (!status) { return; }");
            out.println("        status.hidden = false;");
            out.println("        status.textContent = message || '';");
            out.println("        status.classList.toggle('is-error', !!isError);");
            out.println("      }");
            out.println("      function setSignalSelection(row, score) {");
            out.println("        var buttons = Array.prototype.slice.call(row.querySelectorAll('.js-cdc-signal')); ");
            out.println("        buttons.forEach(function(button) {");
            out.println("          var buttonScore = parseInt(button.getAttribute('data-score') || '0', 10) || 0;");
            out.println("          button.classList.toggle('is-active', buttonScore === score);");
            out.println("        });");
            out.println("      }");
            out.println("      function syncPolicyState(row, policyStatus) {");
            out.println("        var normalized = (policyStatus || '').trim();");
            out.println("        var blocked = normalized === policyBlockedLabel;");
            out.println("        var badge = row.querySelector('.js-cdc-policy-badge');");
            out.println("        var note = row.querySelector('.js-cdc-policy-note');");
            out.println("        var action = row.querySelector('.js-cdc-policy-action');");
            out.println("        var commentInput = row.querySelector('.js-cdc-comment-input');");
            out.println("        var commentSave = row.querySelector('.js-cdc-comment-save');");
            out.println(
                    "        var signalButtons = Array.prototype.slice.call(row.querySelectorAll('.js-cdc-signal')); ");
            out.println("        row.classList.toggle('is-cdc-policy-blocked', blocked);");
            out.println("        if (badge) {");
            out.println("          badge.hidden = normalized.length === 0;");
            out.println(
                    "          badge.textContent = normalized.length === 0 ? '' : ('Policy status: ' + normalized);");
            out.println("        }");
            out.println("        if (note) {");
            out.println("          note.hidden = !blocked;");
            out.println(
                    "          note.textContent = blocked ? ('Feedback not requested because this topic is marked: ' + policyBlockedLabel + '.') : '';");
            out.println("        }");
            out.println("        signalButtons.forEach(function(button) { button.disabled = blocked; });");
            out.println("        if (commentInput) {");
            out.println("          commentInput.disabled = blocked;");
            out.println(
                    "          commentInput.placeholder = blocked ? 'Comments are disabled while this policy status is set.' : 'Add an optional observation about this topic';");
            out.println("        }");
            out.println("        if (commentSave) { commentSave.disabled = blocked; }");
            out.println("        if (action) {");
            out.println("          action.setAttribute('data-action', normalized.length === 0 ? 'set' : 'clear');");
            out.println(
                    "          action.textContent = normalized.length === 0 ? 'Mark as not currently supported by CDC policy' : 'Remove policy status';");
            out.println("        }");
            out.println("      }");
            out.println("      function postForm(url, params) {");
            out.println("        return fetch(url, {");
            out.println("          method: 'POST',");
            out.println("          headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },");
            out.println("          body: params.toString()");
            out.println("        }).then(function(res) { return res.json(); });");
            out.println("      }");
            out.println("      rows.forEach(function(row) {");
            out.println("        syncPolicyState(row, row.getAttribute('data-policy-status') || '');");
            out.println(
                    "        var selectedScore = parseInt(row.getAttribute('data-selected-score') || '0', 10) || 0;");
            out.println("        if (selectedScore > 0) {");
            out.println("          setSignalSelection(row, selectedScore);");
            out.println("        }");
            out.println("        var topicId = row.getAttribute('data-topic-id');");
            out.println(
                    "        Array.prototype.slice.call(row.querySelectorAll('.js-cdc-signal')).forEach(function(button) {");
            out.println("          button.addEventListener('click', function() {");
            out.println("            if (button.disabled) { return; }");
            out.println("            var score = parseInt(button.getAttribute('data-score') || '0', 10) || 0;");
            out.println("            var params = new URLSearchParams();");
            out.println("            params.set('campaignCode', campaignCode);");
            out.println("            params.set('topicId', topicId);");
            out.println("            params.set('score', String(score));");
            out.println("            postForm('" + contextPath
                    + "/es/cdc-review/save-signal', params).then(function(json) {");
            out.println("              if (!json || !json.ok) {");
            out.println(
                    "                setRowStatus(row, (json && json.error) ? json.error : 'Unable to save signal.', true);");
            out.println("                return;");
            out.println("              }");
            out.println("              row.setAttribute('data-selected-score', String(json.score || score));");
            out.println("              setSignalSelection(row, json.score || score);");
            out.println("              setRowStatus(row, 'Saved', false);");
            out.println("            }).catch(function() {");
            out.println("              setRowStatus(row, 'Unable to save signal.', true);");
            out.println("            });");
            out.println("          });");
            out.println("        });");
            out.println("        var commentSave = row.querySelector('.js-cdc-comment-save');");
            out.println("        if (commentSave) {");
            out.println("          commentSave.addEventListener('click', function() {");
            out.println("            if (commentSave.disabled) { return; }");
            out.println("            var commentInput = row.querySelector('.js-cdc-comment-input');");
            out.println("            var text = commentInput ? (commentInput.value || '').trim() : '';");
            out.println("            if (text.length === 0) {");
            out.println("              setRowStatus(row, 'Enter a comment before saving.', true);");
            out.println("              return;");
            out.println("            }");
            out.println("            var params = new URLSearchParams();");
            out.println("            params.set('campaignCode', campaignCode);");
            out.println("            params.set('topicId', topicId);");
            out.println("            params.set('commentText', text);");
            out.println("            postForm('" + contextPath
                    + "/es/cdc-review/save-comment', params).then(function(json) {");
            out.println("              if (!json || !json.ok) {");
            out.println(
                    "                setRowStatus(row, (json && json.error) ? json.error : 'Unable to save comment.', true);");
            out.println("                return;");
            out.println("              }");
            out.println("              if (commentInput) { commentInput.value = ''; }");
            out.println("              setRowStatus(row, 'Saved', false);");
            out.println("            }).catch(function() {");
            out.println("              setRowStatus(row, 'Unable to save comment.', true);");
            out.println("            });");
            out.println("          });");
            out.println("        }");
            out.println("        var policyAction = row.querySelector('.js-cdc-policy-action');");
            out.println("        if (policyAction) {");
            out.println("          policyAction.addEventListener('click', function() {");
            out.println("            var params = new URLSearchParams();");
            out.println("            params.set('campaignCode', campaignCode);");
            out.println("            params.set('topicId', topicId);");
            out.println(
                    "            params.set('policyStatusAction', policyAction.getAttribute('data-action') || 'set');");
            out.println("            postForm('" + contextPath
                    + "/es/cdc-review/policy-status', params).then(function(json) {");
            out.println("              if (!json || !json.ok) {");
            out.println(
                    "                setRowStatus(row, (json && json.error) ? json.error : 'Unable to update policy status.', true);");
            out.println("                return;");
            out.println("              }");
            out.println("              var policyStatus = json.policyStatus || '';");
            out.println("              row.setAttribute('data-policy-status', policyStatus);");
            out.println("              syncPolicyState(row, policyStatus);");
            out.println("              setRowStatus(row, 'Saved', false);");
            out.println("            }).catch(function() {");
            out.println("              setRowStatus(row, 'Unable to update policy status.', true);");
            out.println("            });");
            out.println("          });");
            out.println("        }");
            out.println("      });");
            out.println("      searchInput.addEventListener('input', applyFilter);");
            out.println("      applyFilter();");
            out.println("    })();");
            out.println("  </script>");

            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderTopicRow(PrintWriter out, String contextPath, EsCampaignTopicBrowseRow row,
            Integer selectedScore) {
        String topicName = orEmpty(row.getTopicName());
        String description = orEmpty(row.getDescription());
        String stage = trimToNull(row.getStage());
        String topicType = trimToNull(row.getTopicType());
        String policyStatus = orEmpty(row.getPolicyStatus());
        boolean blocked = EsTopicReviewService.CDC_POLICY_STATUS_NOT_SUPPORTED.equals(trimToNull(policyStatus));

        out.println("      <article class=\"es-topic-row es-cdc-topic-row" + (blocked ? " is-cdc-policy-blocked" : "")
                + "\" data-topic-id=\"" + row.getEsTopicId() + "\""
                + " data-policy-status=\"" + escapeHtml(policyStatus) + "\""
                + " data-selected-score=\"" + (selectedScore == null ? "" : selectedScore) + "\""
                + " data-search=\"" + escapeHtml((topicName + " " + description).toLowerCase()) + "\">");
        out.println("        <div class=\"es-cdc-topic-body\">");
        out.println("          <div class=\"es-topic-top\">");
        out.println("            <h3><a href=\"" + contextPath + "/es/topic/" + row.getEsTopicId()
                + "\">" + escapeHtml(topicName) + "</a></h3>");
        out.println("          </div>");
        if (!description.isEmpty()) {
            out.println("          <p class=\"es-topic-preview\">" + escapeHtml(description) + "</p>");
        }
        if (stage != null || topicType != null) {
            out.println("          <p class=\"es-cdc-topic-meta\">");
            if (stage != null) {
                out.println("            <span>Stage: " + escapeHtml(stage) + "</span>");
            }
            if (stage != null && topicType != null) {
                out.println("            <span class=\"es-cdc-meta-sep\">&bull;</span>");
            }
            if (topicType != null) {
                out.println("            <span>Type: " + escapeHtml(topicType) + "</span>");
            }
            out.println("          </p>");
        }
        out.println("          <p class=\"es-cdc-policy-badge js-cdc-policy-badge\""
                + (policyStatus.isEmpty() ? " hidden" : "") + ">"
                + (policyStatus.isEmpty() ? "" : "Policy status: " + escapeHtml(policyStatus)) + "</p>");
        out.println("          <p class=\"es-cdc-policy-note js-cdc-policy-note\"" + (blocked ? "" : " hidden") + ">"
                + (blocked
                        ? "Feedback not requested because this topic is marked: "
                                + escapeHtml(EsTopicReviewService.CDC_POLICY_STATUS_NOT_SUPPORTED) + "."
                        : "")
                + "</p>");
        out.println("          <section class=\"es-cdc-control-block\">");
        out.println("            <p class=\"es-cdc-control-label\">Signal</p>");
        out.println("            <div class=\"es-cdc-signal-buttons\">");
        for (SignalOption option : SIGNAL_OPTIONS) {
            boolean active = selectedScore != null && selectedScore.intValue() == option.score();
            out.println("              <button type=\"button\" class=\"es-cdc-signal-btn js-cdc-signal"
                    + (active ? " is-active" : "") + "\" data-score=\"" + option.score() + "\""
                    + (blocked ? " disabled" : "") + ">" + escapeHtml(option.label()) + "</button>");
        }
        out.println("            </div>");
        out.println("          </section>");
        out.println("          <section class=\"es-cdc-control-block\">");
        out.println("            <label class=\"es-cdc-control-label\" for=\"cdc-comment-" + row.getEsTopicId()
                + "\">Comment</label>");
        out.println("            <textarea id=\"cdc-comment-" + row.getEsTopicId()
                + "\" class=\"es-review-comment-input es-cdc-comment-input js-cdc-comment-input\""
                + (blocked ? " disabled" : "")
                + " placeholder=\""
                + escapeHtml(blocked
                        ? "Comments are disabled while this policy status is set."
                        : "Add an optional observation about this topic")
                + "\"></textarea>");
        out.println("            <div class=\"es-cdc-comment-actions\">");
        out.println("              <button type=\"button\" class=\"js-cdc-comment-save\""
                + (blocked ? " disabled" : "") + ">Save comment</button>");
        out.println("            </div>");
        out.println("          </section>");
        out.println("          <section class=\"es-cdc-control-block\">");
        out.println("            <p class=\"es-cdc-control-label\">Policy status</p>");
        out.println(
                "            <button type=\"button\" class=\"es-cdc-policy-action js-cdc-policy-action\" data-action=\""
                        + (policyStatus.isEmpty() ? "set" : "clear") + "\">"
                        + escapeHtml(policyStatus.isEmpty()
                                ? "Mark as not currently supported by CDC policy"
                                : "Remove policy status")
                        + "</button>");
        out.println("          </section>");
        out.println("          <p class=\"es-cdc-row-status js-cdc-row-status\" hidden></p>");
        out.println("        </div>");
        out.println("      </article>");
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

    private void renderAccessDenied(HttpServletResponse response, String contextPath) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Access Denied - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Access denied</h1>");
            out.println(
                    "    <p>This page is available only to authorized CDC users and InteropHub administrators.</p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
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

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String quoteJs(String value) {
        return "\"" + orEmpty(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"";
    }

    private record SignalOption(int score, String label) {
    }
}