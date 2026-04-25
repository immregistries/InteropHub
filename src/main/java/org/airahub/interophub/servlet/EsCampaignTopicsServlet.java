package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.service.EsInterestService;
import org.airahub.interophub.service.EsNormalizer;

public class EsCampaignTopicsServlet extends HttpServlet {

    private static final List<String> STAGE_ORDER = List.of("Start", "Draft", "Gather", "Monitor", "Parked",
            "Pilot", "Rollout");
    private static final String OTHER_LABEL = "Other";

    private static final String ATTR_FIRST_NAME = "interophub.es.registration.firstName";
    private static final String ATTR_LAST_NAME = "interophub.es.registration.lastName";
    private static final String ATTR_EMAIL = "interophub.es.registration.email";
    private static final String ATTR_EMAIL_NORMALIZED = "interophub.es.registration.emailNormalized";
    private static final String ATTR_SELECTION_PREFIX = "interophub.es.topicSelection.";

    private final EsCampaignDao campaignDao;
    private final EsTopicDao topicDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsInterestService esInterestService;

    public EsCampaignTopicsServlet() {
        this.campaignDao = new EsCampaignDao();
        this.topicDao = new EsTopicDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.esInterestService = new EsInterestService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
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

        HttpSession session = request.getSession(true);
        String search = trimToNull(request.getParameter("q"));
        Integer subscribedCount = parseIntOrNull(request.getParameter("subscribedCount"));
        boolean generalSubscribedNow = "1".equals(request.getParameter("generalSubscribed"));

        BrowseState browseState = loadBrowseState(campaign.get(), session);
        String selectionKey = selectionSessionKey(campaignCode);
        Set<Long> selectedTopicIds = sanitizeSelectedIds(
                readSelectedTopicIdsFromSession(session, selectionKey),
                browseState.allowedTopicIds,
                browseState.subscribedTopicIds);
        writeSelectedTopicIdsToSession(session, selectionKey, selectedTopicIds);

        renderBrowsePage(response, request.getContextPath(), campaign.get(), search, browseState.rows,
                selectedTopicIds, browseState.subscribedTopicIds, false, null, subscribedCount, generalSubscribedNow,
                browseState.firstName, browseState.lastName, browseState.email, browseState.emailNormalized,
                browseState.generalAlreadySubscribed);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

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

        HttpSession session = request.getSession(true);
        String search = trimToNull(request.getParameter("q"));
        String action = trimToNull(request.getParameter("action"));
        if (action == null) {
            action = "preview";
        }

        BrowseState browseState = loadBrowseState(campaign.get(), session);
        String selectionKey = selectionSessionKey(campaignCode);
        Set<Long> requestSelectedIds = parseTopicIdsCsv(request.getParameter("selectedTopicIds"));
        Set<Long> selectedTopicIds = sanitizeSelectedIds(
                requestSelectedIds,
                browseState.allowedTopicIds,
                browseState.subscribedTopicIds);
        writeSelectedTopicIdsToSession(session, selectionKey, selectedTopicIds);

        if ("confirm".equalsIgnoreCase(action)) {
            handleConfirm(request, response, campaign.get(), search, browseState, selectedTopicIds, selectionKey);
            return;
        }

        renderBrowsePage(response, request.getContextPath(), campaign.get(), search, browseState.rows,
                selectedTopicIds, browseState.subscribedTopicIds, true, null, null, false,
                browseState.firstName, browseState.lastName, browseState.email, browseState.emailNormalized,
                browseState.generalAlreadySubscribed);
    }

    private void handleConfirm(HttpServletRequest request, HttpServletResponse response, EsCampaign campaign,
            String search,
            BrowseState browseState, Set<Long> selectedTopicIds, String selectionKey) throws IOException {
        if (selectedTopicIds.isEmpty()) {
            response.sendRedirect(
                    buildBrowseRedirect(request.getContextPath(), campaign.getCampaignCode(), search, 0, false));
            return;
        }

        String postedEmail = trimToNull(request.getParameter("confirmEmail"));
        String postedFirstName = trimToNull(request.getParameter("confirmFirstName"));
        String postedLastName = trimToNull(request.getParameter("confirmLastName"));
        String effectiveEmail = postedEmail != null ? postedEmail : browseState.email;
        String effectiveFirstName = postedFirstName != null ? postedFirstName : browseState.firstName;
        String effectiveLastName = postedLastName != null ? postedLastName : browseState.lastName;
        String effectiveEmailNormalized = EsNormalizer.normalizeEmail(effectiveEmail);
        if (effectiveEmailNormalized == null) {
            renderBrowsePage(response, request.getContextPath(), campaign, search, browseState.rows,
                    selectedTopicIds, browseState.subscribedTopicIds, true,
                    "Email is required to save followed topics.", null, false,
                    effectiveFirstName, effectiveLastName, effectiveEmail, effectiveEmailNormalized,
                    browseState.generalAlreadySubscribed);
            return;
        }

        int subscribedCount = 0;
        for (Long topicId : selectedTopicIds) {
            if (browseState.subscribedTopicIds.contains(topicId)) {
                continue;
            }
            EsSubscription subscription = new EsSubscription();
            subscription.setEmail(effectiveEmail);
            subscription.setEmailNormalized(effectiveEmailNormalized);
            subscription.setUserId(null);
            subscription.setEsTopicId(topicId);
            subscription.setSubscriptionType(EsSubscription.SubscriptionType.TOPIC);
            subscription.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
            subscription.setSourceCampaignId(campaign.getEsCampaignId());
            esInterestService.subscribeOrUpdate(subscription);
            subscribedCount++;
        }

        boolean generalSubscribedNow = false;
        boolean generalAlreadySubscribed = isGeneralSubscribed(effectiveEmailNormalized);
        boolean wantsGeneral = request.getParameter("generalEsOptIn") != null;
        if (wantsGeneral && !generalAlreadySubscribed) {
            EsSubscription general = new EsSubscription();
            general.setEmail(effectiveEmail);
            general.setEmailNormalized(effectiveEmailNormalized);
            general.setUserId(null);
            general.setEsTopicId(null);
            general.setSubscriptionType(EsSubscription.SubscriptionType.GENERAL_ES);
            general.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
            general.setSourceCampaignId(campaign.getEsCampaignId());
            esInterestService.subscribeOrUpdate(general);
            generalSubscribedNow = true;
        }

        HttpSession session = request.getSession(true);
        writeSelectedTopicIdsToSession(session, selectionKey, Set.of());
        setSessionValue(session, ATTR_FIRST_NAME, effectiveFirstName);
        setSessionValue(session, ATTR_LAST_NAME, effectiveLastName);
        setSessionValue(session, ATTR_EMAIL, effectiveEmail);
        setSessionValue(session, ATTR_EMAIL_NORMALIZED, effectiveEmailNormalized);

        response.sendRedirect(buildBrowseRedirect(
                request.getContextPath(),
                campaign.getCampaignCode(),
                search,
                subscribedCount,
                generalSubscribedNow));
    }

    private String buildBrowseRedirect(String contextPath, String campaignCode, String search, int subscribedCount,
            boolean generalSubscribedNow) {
        String encodedCampaignCode = URLEncoder.encode(campaignCode, StandardCharsets.UTF_8);
        StringBuilder target = new StringBuilder();
        target.append(contextPath).append("/topics/").append(encodedCampaignCode);
        target.append("?subscribedCount=").append(Math.max(subscribedCount, 0));
        if (search != null) {
            target.append("&q=").append(URLEncoder.encode(search, StandardCharsets.UTF_8));
        }
        if (generalSubscribedNow) {
            target.append("&generalSubscribed=1");
        }
        return target.toString();
    }

    private BrowseState loadBrowseState(EsCampaign campaign, HttpSession session) {
        List<EsCampaignTopicBrowseRow> rows = topicDao.findAllActiveBrowseRowsOrdered();
        List<Long> topicIds = rows.stream()
                .map(EsCampaignTopicBrowseRow::getEsTopicId)
                .collect(Collectors.toList());

        String firstName = trimToNull((String) session.getAttribute(ATTR_FIRST_NAME));
        String lastName = trimToNull((String) session.getAttribute(ATTR_LAST_NAME));
        String email = trimToNull((String) session.getAttribute(ATTR_EMAIL));
        String emailNormalized = trimToNull((String) session.getAttribute(ATTR_EMAIL_NORMALIZED));
        if (emailNormalized == null) {
            emailNormalized = EsNormalizer.normalizeEmail(email);
        }

        Set<Long> subscribedTopicIds = emailNormalized == null
                ? Set.of()
                : subscriptionDao.findActiveTopicIdsByEmailAndTopicIds(emailNormalized, topicIds);

        boolean generalAlreadySubscribed = emailNormalized != null && isGeneralSubscribed(emailNormalized);

        return new BrowseState(
                rows,
                new LinkedHashSet<>(topicIds),
                subscribedTopicIds,
                firstName,
                lastName,
                email,
                emailNormalized,
                generalAlreadySubscribed);
    }

    private boolean isGeneralSubscribed(String emailNormalized) {
        if (emailNormalized == null) {
            return false;
        }
        return subscriptionDao.findGeneralByEmailNormalized(emailNormalized)
                .map(sub -> sub.getStatus() == EsSubscription.SubscriptionStatus.SUBSCRIBED)
                .orElse(false);
    }

    private void renderBrowsePage(HttpServletResponse response, String contextPath, EsCampaign campaign, String search,
            List<EsCampaignTopicBrowseRow> rows, Set<Long> selectedTopicIds, Set<Long> subscribedTopicIds,
            boolean showConfirmation, String confirmationError, Integer subscribedCount, boolean generalSubscribedNow,
            String firstName, String lastName, String email, String emailNormalized, boolean generalAlreadySubscribed)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        String searchValue = orEmpty(search);
        String selectedCsv = toCsv(selectedTopicIds);
        String browseHref = contextPath + "/topics/" + escapeHtml(campaign.getCampaignCode())
                + (search == null ? "" : "?q=" + URLEncoder.encode(search, StandardCharsets.UTF_8));
        Map<Long, EsCampaignTopicBrowseRow> rowByTopicId = rows.stream()
                .collect(Collectors.toMap(EsCampaignTopicBrowseRow::getEsTopicId, row -> row, (a, b) -> a,
                        LinkedHashMap::new));

        Map<String, Map<String, List<EsCampaignTopicBrowseRow>>> grouped = groupByNeighborhoodThenStage(rows);
        boolean canOfferGeneral = emailNormalized != null && !generalAlreadySubscribed;

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Emerging Standards Topics - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body" + (showConfirmation ? " class=\"es-confirm-open\"" : "") + ">");
            out.println("  <main class=\"es-topics-page\">");
            out.println("    <section class=\"es-topics-header\">");
            out.println("      <h1>Emerging Standards Topics</h1>");
            out.println(
                    "      <p class=\"es-topics-campaign\">Review and select topics you are interested in then click follow to indicate you would like to receive updates on these topics in the future.</p>");
            out.println("      <label for=\"topic-search\" class=\"es-topics-search-label\">Search topics</label>");
            out.println("      <input id=\"topic-search\" class=\"es-topics-search\" type=\"search\""
                    + " placeholder=\"Search by topic name or description\""
                    + " value=\"" + escapeHtml(searchValue) + "\""
                    + " autocomplete=\"off\" />");
            out.println("    </section>");

            if (subscribedCount != null) {
                out.println("    <p class=\"es-status-message\">Following " + subscribedCount + " topic"
                        + (subscribedCount == 1 ? "" : "s") + ".</p>");
            }
            if (generalSubscribedNow) {
                out.println("    <p class=\"es-status-message\">General Emerging Standards updates enabled.</p>");
            }

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
                        renderStage(out, stage, stageRows, selectedTopicIds, subscribedTopicIds);
                    }
                }
                out.println("      </section>");
            }
            out.println("    </section>");

            out.println("    <form id=\"es-subscribe-form\" method=\"post\" action=\"" + contextPath + "/topics/"
                    + escapeHtml(campaign.getCampaignCode()) + "\">");
            out.println("      <input type=\"hidden\" name=\"action\" value=\"preview\" />");
            out.println("      <input type=\"hidden\" name=\"q\" id=\"es-search-hidden\" value=\""
                    + escapeHtml(searchValue) + "\" />");
            out.println("      <input type=\"hidden\" name=\"selectedTopicIds\" id=\"es-selected-topic-ids\" value=\""
                    + escapeHtml(selectedCsv) + "\" />");
            out.println("      <div class=\"es-sticky-action\">\n"
                    + "        <button id=\"es-subscribe-button\" type=\"submit\" disabled>Follow (0)</button>\n"
                    + "      </div>");
            out.println("    </form>");

            out.println("    <div id=\"es-detail-overlay\" class=\"es-detail-overlay\" hidden></div>");
            out.println("    <aside id=\"es-detail-sheet\" class=\"es-detail-sheet\" hidden>");
            out.println("      <h2 id=\"es-detail-title\"></h2>");
            out.println("      <p id=\"es-detail-stage\" class=\"es-detail-stage\"></p>");
            out.println("      <p id=\"es-detail-state\" class=\"es-detail-state\"></p>");
            out.println("      <p id=\"es-detail-description\" class=\"es-detail-description\"></p>");
            out.println("      <p id=\"es-detail-topic-type\" class=\"es-detail-stage\" hidden></p>");
            out.println("      <p id=\"es-detail-policy-status\" class=\"es-detail-stage\" hidden></p>");
            out.println("      <div class=\"es-detail-actions\">\n"
                    + "        <button type=\"button\" id=\"es-detail-select\">Interested</button>\n"
                    + "        <button type=\"button\" id=\"es-detail-close\" class=\"es-secondary-button\">Close</button>\n"
                    + "      </div>");
            out.println("    </aside>");

            if (showConfirmation) {
                out.println("    <a id=\"es-confirm-overlay\" class=\"es-confirm-overlay\" href=\"" + browseHref
                        + "\" aria-label=\"Close confirmation\"></a>");
                out.println("    <section id=\"es-confirm-sheet\" class=\"es-confirm-sheet\">");
                out.println("      <h2>Confirm Following</h2>");
                out.println(
                        "      <p class=\"es-topics-help\">When a topic you follow becomes active because a meeting is scheduled, a discussion opens, or progress is made, we'll send you an email so you don't miss it. You can opt out at any time.</p>");
                if (confirmationError != null) {
                    out.println("      <p class=\"es-confirm-error\">" + escapeHtml(confirmationError) + "</p>");
                }
                out.println("      <form method=\"post\" action=\"" + contextPath + "/topics/"
                        + escapeHtml(campaign.getCampaignCode()) + "\">");
                out.println("        <input type=\"hidden\" name=\"action\" value=\"confirm\" />");
                out.println("        <input type=\"hidden\" name=\"q\" value=\"" + escapeHtml(searchValue) + "\" />");
                out.println("        <input type=\"hidden\" name=\"selectedTopicIds\" value=\""
                        + escapeHtml(selectedCsv) + "\" />");
                out.println("        <label>Name:</label>");
                out.println("        <div class=\"es-name-row\">\n"
                        + "          <input id=\"confirmFirstName\" name=\"confirmFirstName\" type=\"text\" placeholder=\"First\" value=\""
                        + escapeHtml(orEmpty(firstName)) + "\" />\n"
                        + "          <input id=\"confirmLastName\" name=\"confirmLastName\" type=\"text\" placeholder=\"Last\" value=\""
                        + escapeHtml(orEmpty(lastName)) + "\" />\n"
                        + "        </div>");
                out.println("        <label for=\"confirmEmail\">Email</label>");
                out.println("        <input id=\"confirmEmail\" name=\"confirmEmail\" type=\"email\" required value=\""
                        + escapeHtml(orEmpty(email)) + "\" />");

                if (canOfferGeneral) {
                    out.println("        <label class=\"es-checkbox-row\">\n"
                            + "          <input type=\"checkbox\" name=\"generalEsOptIn\" value=\"1\" />\n"
                            + "          Also follow me for general Emerging Standards updates\n"
                            + "        </label>");
                }

                out.println("        <h3>Selected Topics</h3>");
                out.println("        <ul class=\"es-confirm-topic-list\">");
                for (Long topicId : selectedTopicIds) {
                    EsCampaignTopicBrowseRow row = rowByTopicId.get(topicId);
                    if (row == null) {
                        continue;
                    }
                    out.println("          <li>" + escapeHtml(orEmpty(row.getTopicName())) + "</li>");
                }
                out.println("        </ul>");

                out.println("        <div class=\"es-confirm-actions\">\n"
                        + "          <a class=\"es-secondary-button\" href=\"" + browseHref + "\">Close</a>\n"
                        + "          <a class=\"es-secondary-button\" href=\"" + contextPath + "/topics/"
                        + escapeHtml(campaign.getCampaignCode())
                        + (search == null ? "" : "?q=" + URLEncoder.encode(search, StandardCharsets.UTF_8))
                        + "\">Keep Browsing</a>\n"
                        + "          <button id=\"es-confirm-submit\" type=\"submit\">Confirm Follow</button>\n"
                        + "        </div>");
                out.println("      </form>");
                out.println("    </section>");
            }

            out.println("  </main>");

            out.println("  <script>");
            out.println("    (function() {");
            out.println(
                    "      var stageGroups = Array.prototype.slice.call(document.querySelectorAll('.es-stage-group'));\n"
                            + "      var neighborhoodGroups = Array.prototype.slice.call(document.querySelectorAll('.es-neighborhood-group'));\n"
                            + "      var rows = Array.prototype.slice.call(document.querySelectorAll('.es-topic-row'));\n"
                            + "      var searchInput = document.getElementById('topic-search');\n"
                            + "      var searchHidden = document.getElementById('es-search-hidden');\n"
                            + "      var selectedInput = document.getElementById('es-selected-topic-ids');\n"
                            + "      var subscribeButton = document.getElementById('es-subscribe-button');\n"
                            + "      var detailOverlay = document.getElementById('es-detail-overlay');\n"
                            + "      var detailSheet = document.getElementById('es-detail-sheet');\n"
                            + "      var detailTitle = document.getElementById('es-detail-title');\n"
                            + "      var detailStage = document.getElementById('es-detail-stage');\n"
                            + "      var detailState = document.getElementById('es-detail-state');\n"
                            + "      var detailDescription = document.getElementById('es-detail-description');\n"
                            + "      var detailTopicType = document.getElementById('es-detail-topic-type');\n"
                            + "      var detailPolicyStatus = document.getElementById('es-detail-policy-status');\n"
                            + "      var detailSelect = document.getElementById('es-detail-select');\n"
                            + "      var detailClose = document.getElementById('es-detail-close');");
            out.println("      var scrollKey = 'es-topics-scroll-' + " + quoteJs(campaign.getCampaignCode()) + ";");
            out.println("      var selected = new Set();");
            out.println("      var currentDetailTopicId = null;");

            out.println("      rows.forEach(function(row) {\n"
                    + "        var topicId = row.getAttribute('data-topic-id');\n"
                    + "        if (row.classList.contains('is-selected') && !row.classList.contains('is-subscribed')) { selected.add(topicId); }\n"
                    + "      });");

            out.println("      function selectedCsv() { return Array.from(selected).join(','); }");
            out.println("      function syncSelectedInputs() {\n"
                    + "        selectedInput.value = selectedCsv();\n"
                    + "        var count = selected.size;\n"
                    + "        subscribeButton.textContent = 'Follow (' + count + ')';\n"
                    + "        subscribeButton.disabled = count === 0;\n"
                    + "      }");

            out.println("      function applyFilter() {\n"
                    + "        var term = (searchInput.value || '').toLowerCase();\n"
                    + "        if (searchHidden) { searchHidden.value = searchInput.value || ''; }\n"
                    + "        rows.forEach(function(row) {\n"
                    + "          var text = row.getAttribute('data-search') || '';\n"
                    + "          var isFilteredOut = term.length > 0 && text.indexOf(term) === -1;\n"
                    + "          row.classList.toggle('is-filtered-out', isFilteredOut);\n"
                    + "        });\n"
                    + "        stageGroups.forEach(function(group) {\n"
                    + "          var visible = group.querySelector('.es-topic-row:not(.is-filtered-out)');\n"
                    + "          group.style.display = visible ? '' : 'none';\n"
                    + "        });\n"
                    + "        neighborhoodGroups.forEach(function(group) {\n"
                    + "          var visible = group.querySelector('.es-topic-row:not(.is-filtered-out)');\n"
                    + "          group.style.display = visible ? '' : 'none';\n"
                    + "        });\n"
                    + "      }");

            out.println("      function updateRowState(row, isSelected) {\n"
                    + "        row.classList.toggle('is-selected', isSelected);\n"
                    + "        var state = row.querySelector('.es-topic-state');\n"
                    + "        if (!state) { return; }\n"
                    + "        if (row.classList.contains('is-subscribed')) {\n"
                    + "          state.textContent = 'Following';\n"
                    + "          state.hidden = false;\n"
                    + "          return;\n"
                    + "        }\n"
                    + "        if (isSelected) {\n"
                    + "          state.textContent = 'Selected';\n"
                    + "          state.hidden = false;\n"
                    + "          return;\n"
                    + "        }\n"
                    + "        state.textContent = '';\n"
                    + "        state.hidden = true;\n"
                    + "      }");

            out.println("      function setTopicSelected(topicId, isSelected) {\n"
                    + "        var row = document.querySelector('.es-topic-row[data-topic-id=\\\"' + topicId + '\\\"]');\n"
                    + "        if (!row || row.classList.contains('is-subscribed')) { return; }\n"
                    + "        var checkbox = row.querySelector('.es-topic-checkbox');\n"
                    + "        if (checkbox) { checkbox.checked = isSelected; }\n"
                    + "        if (isSelected) { selected.add(topicId); } else { selected.delete(topicId); }\n"
                    + "        updateRowState(row, isSelected);\n"
                    + "        syncSelectedInputs();\n"
                    + "      }");

            out.println("      function openDetail(row) {\n"
                    + "        currentDetailTopicId = row.getAttribute('data-topic-id');\n"
                    + "        var isSubscribed = row.classList.contains('is-subscribed');\n"
                    + "        var isSelected = selected.has(currentDetailTopicId);\n"
                    + "        detailTitle.textContent = row.getAttribute('data-topic-name') || '';\n"
                    + "        detailStage.textContent = 'Stage: ' + (row.getAttribute('data-topic-stage') || 'Unknown');\n"
                    + "        detailDescription.textContent = row.getAttribute('data-topic-description') || 'No description available.';\n"
                    + "        var topicType = (row.getAttribute('data-topic-type') || '').trim();\n"
                    + "        var policyStatus = (row.getAttribute('data-policy-status') || '').trim();\n"
                    + "        if (topicType) {\n"
                    + "          detailTopicType.textContent = 'Topic type: ' + topicType;\n"
                    + "          detailTopicType.hidden = false;\n"
                    + "        } else {\n"
                    + "          detailTopicType.textContent = '';\n"
                    + "          detailTopicType.hidden = true;\n"
                    + "        }\n"
                    + "        if (policyStatus) {\n"
                    + "          detailPolicyStatus.textContent = 'Policy status: ' + policyStatus;\n"
                    + "          detailPolicyStatus.hidden = false;\n"
                    + "        } else {\n"
                    + "          detailPolicyStatus.textContent = '';\n"
                    + "          detailPolicyStatus.hidden = true;\n"
                    + "        }\n"
                    + "        if (isSubscribed) {\n"
                    + "          detailState.textContent = 'Current state: Following';\n"
                    + "          detailSelect.hidden = true;\n"
                    + "        } else if (isSelected) {\n"
                    + "          detailState.textContent = 'Current state: Selected (pending)';\n"
                    + "          detailSelect.hidden = false;\n"
                    + "          detailSelect.textContent = 'Interested';\n"
                    + "          detailSelect.disabled = false;\n"
                    + "        } else {\n"
                    + "          detailState.textContent = 'Current state: Not selected';\n"
                    + "          detailSelect.hidden = false;\n"
                    + "          detailSelect.textContent = 'Interested';\n"
                    + "          detailSelect.disabled = false;\n"
                    + "        }\n"
                    + "        detailOverlay.hidden = false;\n"
                    + "        detailSheet.hidden = false;\n"
                    + "        document.body.classList.add('es-sheet-open');\n"
                    + "      }");

            out.println("      function closeDetail() {\n"
                    + "        currentDetailTopicId = null;\n"
                    + "        detailOverlay.hidden = true;\n"
                    + "        detailSheet.hidden = true;\n"
                    + "        document.body.classList.remove('es-sheet-open');\n"
                    + "      }");

            out.println("      rows.forEach(function(row) {\n"
                    + "        var checkbox = row.querySelector('.es-topic-checkbox');\n"
                    + "        var content = row.querySelector('.es-topic-content');\n"
                    + "        if (checkbox) {\n"
                    + "          checkbox.addEventListener('click', function(evt) { evt.stopPropagation(); });\n"
                    + "          checkbox.addEventListener('change', function() {\n"
                    + "            setTopicSelected(row.getAttribute('data-topic-id'), checkbox.checked);\n"
                    + "          });\n"
                    + "        }\n"
                    + "        if (content) {\n"
                    + "          content.addEventListener('click', function() {\n"
                    + "            openDetail(row);\n"
                    + "          });\n"
                    + "        }\n"
                    + "      });");

            out.println("      if (detailOverlay) { detailOverlay.addEventListener('click', closeDetail); }");
            out.println("      if (detailClose) { detailClose.addEventListener('click', closeDetail); }");
            out.println("      if (detailSelect) {\n"
                    + "        detailSelect.addEventListener('click', function() {\n"
                    + "          if (!currentDetailTopicId) { return; }\n"
                    + "          setTopicSelected(currentDetailTopicId, true);\n"
                    + "          detailSelect.textContent = 'Interested';\n"
                    + "          detailSelect.disabled = true;\n"
                    + "          detailState.textContent = 'Current state: Selected (pending)';\n"
                    + "          closeDetail();\n"
                    + "        });\n"
                    + "      }");

            out.println("      if (searchInput) {\n"
                    + "        searchInput.addEventListener('input', applyFilter);\n"
                    + "      }");

            out.println("      var confirmSubmit = document.getElementById('es-confirm-submit');\n"
                    + "      if (confirmSubmit) {\n"
                    + "        confirmSubmit.addEventListener('click', function() {\n"
                    + "          try { sessionStorage.setItem(scrollKey, String(window.scrollY || 0)); } catch (err) {}\n"
                    + "        });\n"
                    + "      }");

            out.println("      try {\n"
                    + "        var scrollValue = sessionStorage.getItem(scrollKey);\n"
                    + "        if (scrollValue !== null) {\n"
                    + "          sessionStorage.removeItem(scrollKey);\n"
                    + "          window.scrollTo(0, parseInt(scrollValue, 10) || 0);\n"
                    + "        }\n"
                    + "      } catch (err) {}");

            out.println("      syncSelectedInputs();");
            out.println("      applyFilter();");
            out.println("    })();");
            out.println("  </script>");

            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderStage(PrintWriter out, String stageName, List<EsCampaignTopicBrowseRow> rows,
            Set<Long> selectedTopicIds, Set<Long> subscribedTopicIds) {
        out.println("      <section class=\"es-stage-group\" data-stage-name=\"" + escapeHtml(stageName) + "\">");
        out.println("        <div class=\"es-topic-list\">");
        for (EsCampaignTopicBrowseRow row : rows) {
            String topicName = orEmpty(row.getTopicName());
            String description = orEmpty(row.getDescription());
            String preview = description.length() <= 130 ? description : description.substring(0, 127) + "...";
            boolean subscribed = subscribedTopicIds.contains(row.getEsTopicId());
            boolean selected = !subscribed && selectedTopicIds.contains(row.getEsTopicId());

            String stateText = subscribed ? "Following" : (selected ? "Selected" : "");
            out.println("          <article class=\"es-topic-row" + (subscribed ? " is-subscribed" : "")
                    + (selected ? " is-selected" : "") + "\""
                    + " data-topic-id=\"" + row.getEsTopicId() + "\""
                    + " data-topic-name=\"" + escapeHtml(topicName) + "\""
                    + " data-topic-description=\"" + escapeHtml(description) + "\""
                    + " data-topic-type=\"" + escapeHtml(orEmpty(row.getTopicType())) + "\""
                    + " data-policy-status=\"" + escapeHtml(orEmpty(row.getPolicyStatus())) + "\""
                    + " data-topic-neighborhood=\"" + escapeHtml(normalizeNeighborhood(row.getNeighborhood())) + "\""
                    + " data-topic-stage=\"" + escapeHtml(orEmpty(normalizeStage(row.getStage()))) + "\""
                    + " data-search=\"" + escapeHtml((topicName + " " + description).toLowerCase()) + "\">");

            out.println("            <div class=\"es-topic-checkbox-wrap\">");
            out.println("              <input class=\"es-topic-checkbox\" type=\"checkbox\""
                    + " data-topic-id=\"" + row.getEsTopicId() + "\""
                    + (subscribed ? " disabled" : "")
                    + (selected ? " checked" : "")
                    + " aria-label=\"Select " + escapeHtml(topicName) + "\" />");
            out.println("            </div>");

            out.println("            <div class=\"es-topic-content\">");
            out.println("              <div class=\"es-topic-top\">");
            out.println("                <h3>" + escapeHtml(topicName) + "</h3>");
            out.println("                <span class=\"es-topic-state\"" + (stateText.isEmpty() ? " hidden" : "") + ">"
                    + escapeHtml(stateText) + "</span>");
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

    private Set<Long> sanitizeSelectedIds(Set<Long> candidate, Set<Long> allowedTopicIds,
            Set<Long> subscribedTopicIds) {
        if (candidate == null || candidate.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return candidate.stream()
                .filter(allowedTopicIds::contains)
                .filter(topicId -> !subscribedTopicIds.contains(topicId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> readSelectedTopicIdsFromSession(HttpSession session, String selectionKey) {
        Object value = session.getAttribute(selectionKey);
        if (value instanceof Set<?> valueSet) {
            Set<Long> ids = new LinkedHashSet<>();
            for (Object item : valueSet) {
                if (item instanceof Long itemLong) {
                    ids.add(itemLong);
                } else if (item instanceof String itemString) {
                    Long parsed = parseId(itemString);
                    if (parsed != null) {
                        ids.add(parsed);
                    }
                }
            }
            return ids;
        }
        return new LinkedHashSet<>();
    }

    private void writeSelectedTopicIdsToSession(HttpSession session, String selectionKey, Set<Long> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            session.removeAttribute(selectionKey);
            return;
        }
        session.setAttribute(selectionKey, new LinkedHashSet<>(selectedIds));
    }

    private String selectionSessionKey(String campaignCode) {
        return ATTR_SELECTION_PREFIX + campaignCode;
    }

    private Set<Long> parseTopicIdsCsv(String csv) {
        Set<Long> ids = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }
        String[] parts = csv.split(",");
        for (String part : parts) {
            Long id = parseId(part);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String toCsv(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
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

    private Integer parseIntOrNull(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseId(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void setSessionValue(HttpSession session, String attributeName, String value) {
        if (value == null) {
            session.removeAttribute(attributeName);
            return;
        }
        session.setAttribute(attributeName, value);
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

    private String quoteJs(String value) {
        String safe = value == null ? ""
                : value
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "");
        return "\"" + safe + "\"";
    }

    private static final class BrowseState {
        private final List<EsCampaignTopicBrowseRow> rows;
        private final Set<Long> allowedTopicIds;
        private final Set<Long> subscribedTopicIds;
        private final String firstName;
        private final String lastName;
        private final String email;
        private final String emailNormalized;
        private final boolean generalAlreadySubscribed;

        private BrowseState(List<EsCampaignTopicBrowseRow> rows, Set<Long> allowedTopicIds,
                Set<Long> subscribedTopicIds,
                String firstName, String lastName, String email, String emailNormalized,
                boolean generalAlreadySubscribed) {
            this.rows = rows;
            this.allowedTopicIds = allowedTopicIds;
            this.subscribedTopicIds = subscribedTopicIds;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.emailNormalized = emailNormalized;
            this.generalAlreadySubscribed = generalAlreadySubscribed;
        }
    }
}