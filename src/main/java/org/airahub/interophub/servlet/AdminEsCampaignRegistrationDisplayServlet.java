package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignRegistrationDao;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.dao.EsCampaignTopicBrowseRow;
import org.airahub.interophub.dao.EsInterestDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsCampaignTopic;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;

public class AdminEsCampaignRegistrationDisplayServlet extends HttpServlet {

    private static final int DEFAULT_NAME_LIMIT = 200;
    private static final int MAX_NAME_LIMIT = 1000;
    private static final long AUTO_REFRESH_DURATION_MS = 60L * 60L * 1000L;
    private static final int AUTO_REFRESH_SECONDS = 10;
    private static final int DISPLAY_TABLE_CAPACITY = 9;

    private final AuthFlowService authFlowService;
    private final EsCampaignDao campaignDao;
    private final EsCampaignRegistrationDao registrationDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsInterestDao interestDao;
    private final EsSubscriptionDao subscriptionDao;

    public AdminEsCampaignRegistrationDisplayServlet() {
        this.authFlowService = new AuthFlowService();
        this.campaignDao = new EsCampaignDao();
        this.registrationDao = new EsCampaignRegistrationDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.interestDao = new EsInterestDao();
        this.subscriptionDao = new EsSubscriptionDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String campaignCode = trimToNull(request.getParameter("campaignCode"));
        boolean autoRefreshRequested = "1".equals(request.getParameter("auto"));
        int nameLimit = parsePositiveInt(request.getParameter("nameLimit"), DEFAULT_NAME_LIMIT, MAX_NAME_LIMIT);

        if (campaignCode == null) {
            renderCampaignPicker(response, contextPath, campaignDao.findAllOrdered());
            return;
        }

        Optional<EsCampaign> campaign = campaignDao.findByCampaignCode(campaignCode);
        if (campaign.isEmpty()) {
            renderCampaignNotFound(response, contextPath, campaignCode);
            return;
        }

        String startedAtRaw = trimToNull(request.getParameter("startedAt"));
        if (autoRefreshRequested && startedAtRaw == null) {
            String redirectUrl = contextPath + "/admin/es/registrations?campaignCode="
                    + URLEncoder.encode(campaignCode, StandardCharsets.UTF_8)
                    + "&auto=1&startedAt=" + System.currentTimeMillis()
                    + "&nameLimit=" + nameLimit;
            response.sendRedirect(redirectUrl);
            return;
        }

        long count = registrationDao.countByCampaignId(campaign.get().getEsCampaignId());
        List<String> firstNames = registrationDao.findRecentFirstNamesByCampaignId(campaign.get().getEsCampaignId(),
                nameLimit);

        long now = System.currentTimeMillis();
        long startedAt = parseLongOrDefault(startedAtRaw, now);
        long elapsed = Math.max(0L, now - startedAt);
        long remaining = Math.max(0L, AUTO_REFRESH_DURATION_MS - elapsed);
        boolean autoRefreshActive = autoRefreshRequested && remaining > 0L;

        List<EsCampaignTopicBrowseRow> topicRows = campaignTopicDao
                .findDistinctTopicBrowseRowsByCampaignIdOrdered(campaign.get().getEsCampaignId());
        List<EsCampaignTopic> campaignTopics = campaignTopicDao.findByCampaignId(campaign.get().getEsCampaignId());

        int maxSetNo = campaignTopics.stream()
                .map(EsCampaignTopic::getTopicSetNo)
                .filter(v -> v != null && v > 0)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        int maxTableNo = campaignTopics.stream()
                .map(EsCampaignTopic::getTableNo)
                .filter(v -> v != null && v > 0)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        int inferredSetCount = Math.max(1, maxSetNo);
        int inferredTablesPerSet;
        if (maxSetNo > 0 && maxTableNo > 0) {
            inferredTablesPerSet = Math.max(1, (int) Math.ceil((double) maxTableNo / (double) maxSetNo));
        } else if (maxTableNo > 0) {
            inferredTablesPerSet = maxTableNo;
        } else {
            inferredTablesPerSet = 1;
        }

        List<EsInterestDao.CampaignTopicRoundVoteRow> voteRows = interestDao
                .findVoteTotalsByCampaignTopicAndRound(campaign.get().getEsCampaignId());
        List<EsSubscriptionDao.CampaignTopicSubscriptionCountRow> subscriptionRows = subscriptionDao
                .findTopicSubscriptionCountsBySourceCampaignId(campaign.get().getEsCampaignId(), 25);

        renderDisplay(response, contextPath, campaign.get(), count, firstNames, autoRefreshActive, startedAt, remaining,
                nameLimit, topicRows, voteRows, subscriptionRows, inferredSetCount, inferredTablesPerSet);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> adminUser = requireAdmin(request, response);
        if (adminUser.isEmpty()) {
            return;
        }

        String contextPath = request.getContextPath();
        String campaignCode = trimToNull(request.getParameter("campaignCode"));
        if (campaignCode == null) {
            response.sendRedirect(contextPath + "/admin/es/registrations");
            return;
        }

        Optional<EsCampaign> campaign = campaignDao.findByCampaignCode(campaignCode);
        if (campaign.isEmpty()) {
            renderCampaignNotFound(response, contextPath, campaignCode);
            return;
        }

        String action = trimToNull(request.getParameter("roundAction"));
        String cleanupAction = trimToNull(request.getParameter("cleanupAction"));
        boolean cleanupConfirmed = "1".equals(request.getParameter("confirmCleanup"));

        if ("cleanup".equalsIgnoreCase(cleanupAction)
                && campaign.get().getStatus() == EsCampaign.CampaignStatus.DRAFT
                && cleanupConfirmed) {
            Long campaignId = campaign.get().getEsCampaignId();
            interestDao.deleteByCampaignId(campaignId);
            subscriptionDao.deleteBySourceCampaignId(campaignId);
            registrationDao.deleteByCampaignId(campaignId);
        }

        if ("next".equalsIgnoreCase(action)) {
            campaignDao.changeRoundByDelta(campaign.get().getEsCampaignId(), 1);
        } else if ("previous".equalsIgnoreCase(action)) {
            campaignDao.changeRoundByDelta(campaign.get().getEsCampaignId(), -1);
        }

        boolean autoRefreshRequested = "1".equals(request.getParameter("auto"));
        int nameLimit = parsePositiveInt(request.getParameter("nameLimit"), DEFAULT_NAME_LIMIT, MAX_NAME_LIMIT);
        String startedAt = trimToNull(request.getParameter("startedAt"));

        StringBuilder redirect = new StringBuilder();
        redirect.append(contextPath)
                .append("/admin/es/registrations?campaignCode=")
                .append(URLEncoder.encode(campaignCode, StandardCharsets.UTF_8))
                .append("&nameLimit=")
                .append(nameLimit);
        if (autoRefreshRequested) {
            redirect.append("&auto=1");
            if (startedAt != null) {
                redirect.append("&startedAt=")
                        .append(URLEncoder.encode(startedAt, StandardCharsets.UTF_8));
            }
        }
        response.sendRedirect(redirect.toString());
    }

    private Optional<User> requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        if (authenticatedUser.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return Optional.empty();
        }
        if (!authFlowService.isAdminUser(authenticatedUser.get())) {
            renderForbidden(response, request.getContextPath());
            return Optional.empty();
        }
        return authenticatedUser;
    }

    private void renderCampaignPicker(HttpServletResponse response, String contextPath, List<EsCampaign> campaigns)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Registration Display - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Campaign Registration Display</h1>");
            out.println("    <p>Select a campaign to view registration count and names.</p>");
            out.println("    <form class=\"login-form\" method=\"get\" action=\"" + contextPath
                    + "/admin/es/registrations\">");
            out.println("      <label for=\"campaignCode\">Campaign</label>");
            out.println("      <select id=\"campaignCode\" name=\"campaignCode\" required>");
            out.println("        <option value=\"\">Choose one...</option>");
            for (EsCampaign campaign : campaigns) {
                out.println("        <option value=\"" + escapeHtml(orEmpty(campaign.getCampaignCode())) + "\">"
                        + escapeHtml(orEmpty(campaign.getCampaignCode())) + " - "
                        + escapeHtml(orEmpty(campaign.getCampaignName())) + "</option>");
            }
            out.println("      </select>");
            out.println("      <label for=\"nameLimit\">Name list limit</label>");
            out.println(
                    "      <input id=\"nameLimit\" name=\"nameLimit\" type=\"number\" min=\"1\" max=\"1000\" value=\""
                            + DEFAULT_NAME_LIMIT + "\" />");
            out.println("      <button type=\"submit\">Open Display</button>");
            out.println("    </form>");
            out.println("    <p><a href=\"" + contextPath + "/admin/es\">Back to Emerging Standards</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
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
            out.println("    <p>No campaign was found for this code.</p>");
            out.println("    <p><strong>Code:</strong> " + escapeHtml(orEmpty(campaignCode)) + "</p>");
            out.println(
                    "    <p><a href=\"" + contextPath + "/admin/es/registrations\">Choose another campaign</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderDisplay(HttpServletResponse response, String contextPath, EsCampaign campaign, long count,
            List<String> firstNames, boolean autoRefreshActive, long startedAt, long remainingMs, int nameLimit,
            List<EsCampaignTopicBrowseRow> topicRows,
            List<EsInterestDao.CampaignTopicRoundVoteRow> voteRows,
            List<EsSubscriptionDao.CampaignTopicSubscriptionCountRow> subscriptionRows,
            int setCount,
            int tablesPerSet)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            if (autoRefreshActive) {
                out.println("  <meta http-equiv=\"refresh\" content=\"" + AUTO_REFRESH_SECONDS + "\" />");
            }
            out.println("  <title>" + escapeHtml(orEmpty(campaign.getCampaignName())) + " - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"es-reg-wrap\">");
            out.println("    <h1>" + escapeHtml(orEmpty(campaign.getCampaignName())) + "</h1>");
            int currentRoundNo = (campaign.getCurrentRoundNo() == null || campaign.getCurrentRoundNo() < 1)
                    ? 1
                    : campaign.getCurrentRoundNo();
            out.println("    <div class=\"es-reg-grid\">");
            out.println("      <section class=\"es-reg-left\">");

            if (campaign.getStatus() != EsCampaign.CampaignStatus.ACTIVE) {
                out.println("        <p class=\"es-reg-mode-badge\">Mode: "
                        + escapeHtml(String.valueOf(campaign.getStatus())) + "</p>");
            }

            out.println("        <p class=\"es-reg-count\">" + count + "</p>");

            String namesCsv = firstNames.stream()
                    .map(this::trimToNull)
                    .filter(name -> name != null)
                    .map(this::escapeHtml)
                    .collect(Collectors.joining(", "));

            out.println("        <h2>Registered</h2>");
            if (namesCsv.isEmpty()) {
                out.println("        <p class=\"es-reg-names\">No names yet.</p>");
            } else {
                out.println("        <p class=\"es-reg-names\">" + namesCsv + "</p>");
            }

            out.println("        <h2>Topics Followed</h2>");
            out.println("        <table class=\"admin-table\">");
            out.println("          <thead><tr><th>Topic</th><th>Count</th></tr></thead>");
            out.println("          <tbody>");
            if (subscriptionRows == null || subscriptionRows.isEmpty()) {
                out.println("            <tr><td colspan=\"2\">none</td></tr>");
            } else {
                for (EsSubscriptionDao.CampaignTopicSubscriptionCountRow row : subscriptionRows) {
                    out.println("            <tr><td>" + escapeHtml(orEmpty(row.getTopicName())) + "</td><td>"
                            + row.getSubscriptionCount() + "</td></tr>");
                }
            }
            out.println("          </tbody>");
            out.println("        </table>");

            if (campaign.getStatus() == EsCampaign.CampaignStatus.DRAFT) {
                out.println("        <section class=\"es-reg-cleanup\">");
                out.println("          <h2>Cleanup</h2>");
                out.println("          <form method=\"post\" action=\"" + contextPath + "/admin/es/registrations\">");
                out.println("            <input type=\"hidden\" name=\"campaignCode\" value=\""
                        + escapeHtml(orEmpty(campaign.getCampaignCode())) + "\" />");
                out.println("            <input type=\"hidden\" name=\"nameLimit\" value=\"" + nameLimit + "\" />");
                if (autoRefreshActive) {
                    out.println("            <input type=\"hidden\" name=\"auto\" value=\"1\" />");
                    out.println("            <input type=\"hidden\" name=\"startedAt\" value=\"" + startedAt
                            + "\" />");
                }
                out.println("            <input type=\"hidden\" name=\"cleanupAction\" value=\"cleanup\" />");
                out.println("            <label class=\"es-reg-cleanup-confirm\">");
                out.println("              <input type=\"checkbox\" name=\"confirmCleanup\" value=\"1\" />");
                out.println("              delete all registrations, topics followed, and table votes");
                out.println("            </label>");
                out.println("            <button type=\"submit\">Cleanup</button>");
                out.println("          </form>");
                out.println("        </section>");
            }

            out.println("      </section>");

            long toggleStartedAt = System.currentTimeMillis();
            out.println("      <section class=\"es-reg-right\">");
            out.println("        <div class=\"es-round-header\">");
            out.println("          <p class=\"es-round-current\"><strong>Current round:</strong> " + currentRoundNo
                    + "</p>");
            out.println("          <form class=\"es-round-controls\" method=\"post\" action=\"" + contextPath
                    + "/admin/es/registrations\">");
            out.println("            <input type=\"hidden\" name=\"campaignCode\" value=\""
                    + escapeHtml(orEmpty(campaign.getCampaignCode())) + "\" />");
            out.println("            <input type=\"hidden\" name=\"nameLimit\" value=\"" + nameLimit + "\" />");
            if (autoRefreshActive) {
                out.println("            <input type=\"hidden\" name=\"auto\" value=\"1\" />");
                out.println("            <input type=\"hidden\" name=\"startedAt\" value=\"" + startedAt + "\" />");
            }
            if (currentRoundNo > 1) {
                out.println(
                        "            <button type=\"submit\" name=\"roundAction\" value=\"previous\">Previous Round</button>");
            }
            out.println("            <button type=\"submit\" name=\"roundAction\" value=\"next\">Next Round</button>");
            out.println("          </form>");
            out.println("        </div>");

            Map<Integer, Long> tableVoterCounts = new HashMap<>();
            for (EsInterestDao.TableVoterCountRow row : interestDao
                    .findDistinctVoterCountsByCampaignAndRound(campaign.getEsCampaignId(), currentRoundNo)) {
                if (row.getTableNo() != null) {
                    tableVoterCounts.put(row.getTableNo(), row.getVoterCount());
                }
            }

            out.println("        <div class=\"es-table-health-wrap\">");
            out.println("          <table class=\"admin-table es-table-health-table\">");
            out.println("            <thead><tr><th>Table</th>");
            for (int setNo = 1; setNo <= setCount; setNo++) {
                out.println("              <th>Set " + setNo + "</th>");
            }
            out.println("            </tr></thead>");
            out.println("            <tbody>");
            for (int tableSlot = 1; tableSlot <= tablesPerSet; tableSlot++) {
                out.println("              <tr>");
                out.println("                <th>Table " + tableSlot + "</th>");
                for (int setNo = 1; setNo <= setCount; setNo++) {
                    int concreteTableNo = (setNo - 1) * tablesPerSet + tableSlot;
                    long voters = tableVoterCounts.getOrDefault(concreteTableNo, 0L);
                    int pct = (int) Math.min(100L, Math.round((voters * 100.0) / DISPLAY_TABLE_CAPACITY));
                    out.println("                <td>");
                    out.println("                  <div class=\"es-table-health-cell\">");
                    out.println("                    <span class=\"es-table-health-dot\" style=\"--fill:" + pct
                            + "%\"></span>");
                    out.println("                    <span class=\"es-table-health-value\">" + voters + "/"
                            + DISPLAY_TABLE_CAPACITY
                            + "</span>");
                    out.println("                  </div>");
                    out.println("                </td>");
                }
                out.println("              </tr>");
            }
            out.println("            </tbody>");
            out.println("          </table>");
            out.println("        </div>");

            Map<Long, String> topicNameById = new HashMap<>();
            for (EsCampaignTopicBrowseRow topic : topicRows) {
                topicNameById.put(topic.getEsTopicId(), topic.getTopicName());
            }

            Map<Long, Map<Integer, Long>> votesByTopicByRound = new HashMap<>();
            int maxDataRound = 0;
            for (EsInterestDao.CampaignTopicRoundVoteRow row : voteRows) {
                if (row.getEsTopicId() == null || row.getRoundNo() == null || row.getRoundNo() < 1) {
                    continue;
                }
                maxDataRound = Math.max(maxDataRound, row.getRoundNo());
                votesByTopicByRound
                        .computeIfAbsent(row.getEsTopicId(), ignored -> new HashMap<>())
                        .merge(row.getRoundNo(), row.getVoteCount(), Long::sum);
            }

            int maxRoundToShow = Math.max(3, Math.max(currentRoundNo, maxDataRound));
            List<Integer> rounds = IntStream.rangeClosed(1, maxRoundToShow).boxed().collect(Collectors.toList());

            List<TopicVoteMatrixRow> matrixRows = new ArrayList<>();
            for (EsCampaignTopicBrowseRow topic : topicRows) {
                Long topicId = topic.getEsTopicId();
                Map<Integer, Long> roundVotes = votesByTopicByRound.getOrDefault(topicId, Map.of());
                long totalVotes = roundVotes.values().stream().mapToLong(Long::longValue).sum();
                matrixRows.add(new TopicVoteMatrixRow(topic.getTopicName(), roundVotes, totalVotes));
            }
            matrixRows.sort((a, b) -> {
                int byVotes = Long.compare(b.totalVotes, a.totalVotes);
                if (byVotes != 0) {
                    return byVotes;
                }
                return String.CASE_INSENSITIVE_ORDER.compare(a.topicName, b.topicName);
            });

            List<TopicVoteMatrixRow> visibleRows = matrixRows.size() > 10 ? matrixRows.subList(0, 10) : matrixRows;

            out.println("        <div class=\"es-round-table-wrap\">");
            out.println("          <table class=\"admin-table es-round-table\">");
            out.println("            <thead>");
            out.println("              <tr>");
            out.println("                <th>Topic</th>");
            for (Integer round : rounds) {
                boolean selected = round == currentRoundNo;
                out.println("                <th" + (selected ? " class=\"es-current-round\"" : "") + ">Round "
                        + round + "</th>");
            }
            out.println("                <th>Total</th>");
            out.println("              </tr>");
            out.println("            </thead>");
            out.println("            <tbody>");
            for (TopicVoteMatrixRow row : visibleRows) {
                out.println("              <tr>");
                out.println("                <td>" + escapeHtml(orEmpty(row.topicName)) + "</td>");
                for (Integer round : rounds) {
                    long voteCount = row.roundVotes.getOrDefault(round, 0L);
                    boolean selected = round == currentRoundNo;
                    out.println("                <td" + (selected ? " class=\"es-current-round\"" : "") + ">"
                            + voteCount + "</td>");
                }
                out.println("                <td><strong>" + row.totalVotes + "</strong></td>");
                out.println("              </tr>");
            }
            if (visibleRows.isEmpty()) {
                out.println("              <tr><td colspan=\"" + (rounds.size() + 2)
                        + "\">No campaign topics assigned.</td></tr>");
            }
            out.println("            </tbody>");
            out.println("          </table>");
            if (matrixRows.size() > 10) {
                out.println("          <p class=\"es-refresh-hint\">Showing top 10 topics by total votes.</p>");
            }
            out.println("        </div>");
            out.println("      </section>");
            out.println("    </div>");

            out.println("    <form id=\"es-refresh-form\" class=\"es-refresh-floating\" method=\"get\" action=\""
                    + contextPath + "/admin/es/registrations\">");
            out.println("      <input type=\"hidden\" name=\"campaignCode\" value=\""
                    + escapeHtml(orEmpty(campaign.getCampaignCode())) + "\" />");
            out.println("      <input type=\"hidden\" name=\"nameLimit\" value=\"" + nameLimit + "\" />");
            out.println("      <input type=\"hidden\" name=\"startedAt\" value=\"" + toggleStartedAt + "\" />");
            out.println("      <label class=\"es-refresh-switch\">Refresh");
            out.println("        <input id=\"es-refresh-input\" type=\"checkbox\" name=\"auto\" value=\"1\""
                    + (autoRefreshActive ? " checked" : "") + " />");
            out.println("        <span class=\"es-refresh-slider\"></span>");
            out.println("      </label>");
            out.println("    </form>");
            out.println("    <script>");
            out.println("      (function(){");
            out.println("        var input = document.getElementById('es-refresh-input');");
            out.println("        if (input) {");
            out.println("          input.addEventListener('change', function(){");
            out.println("            document.getElementById('es-refresh-form').submit();");
            out.println("          });");
            out.println("        }");
            out.println("      })();");
            out.println("    </script>");

            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private static final class TopicVoteMatrixRow {
        private final String topicName;
        private final Map<Integer, Long> roundVotes;
        private final long totalVotes;

        private TopicVoteMatrixRow(String topicName, Map<Integer, Long> roundVotes, long totalVotes) {
            this.topicName = topicName;
            this.roundVotes = roundVotes;
            this.totalVotes = totalVotes;
        }
    }

    private void renderForbidden(HttpServletResponse response, String contextPath) throws IOException {
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
            out.println("    <h1>Access Denied</h1>");
            out.println("    <p>You must be an InteropHub admin to access this page.</p>");
            out.println("    <p><a href=\"" + contextPath + "/welcome\">Return to Welcome</a></p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private int parsePositiveInt(String raw, int defaultValue, int maxValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed <= 0) {
                return defaultValue;
            }
            return Math.min(parsed, maxValue);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private long parseLongOrDefault(String raw, long defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
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
}
