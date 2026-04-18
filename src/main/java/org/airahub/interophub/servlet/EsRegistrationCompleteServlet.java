package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import org.airahub.interophub.dao.EsCampaignMeetingBrowseRow;
import org.airahub.interophub.dao.EsCampaignTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsInterestService;
import org.airahub.interophub.service.EsNormalizer;

public class EsRegistrationCompleteServlet extends HttpServlet {

    private static final String ATTR_FIRST_NAME = "interophub.es.registration.firstName";
    private static final String ATTR_LAST_NAME = "interophub.es.registration.lastName";
    private static final String ATTR_EMAIL = "interophub.es.registration.email";
    private static final String ATTR_EMAIL_NORMALIZED = "interophub.es.registration.emailNormalized";
    private static final String ATTR_CAMPAIGN_CODE = "interophub.es.registration.campaignCode";
    private static final String ATTR_SESSION_KEY = "interophub.es.registration.sessionKey";

    private final EsCampaignDao campaignDao;
    private final EsCampaignTopicDao campaignTopicDao;
    private final EsTopicMeetingMemberDao topicMeetingMemberDao;
    private final EsInterestService esInterestService;
    private final AuthFlowService authFlowService;

    public EsRegistrationCompleteServlet() {
        this.campaignDao = new EsCampaignDao();
        this.campaignTopicDao = new EsCampaignTopicDao();
        this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
        this.esInterestService = new EsInterestService();
        this.authFlowService = new AuthFlowService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String campaignCode = parseCampaignCode(request);
        if (campaignCode == null) {
            renderCampaignNotFound(response, request.getContextPath(), null);
            return;
        }

        Optional<EsCampaign> campaign = campaignDao.findByCampaignCode(campaignCode)
                .filter(c -> campaignCode.equals(c.getCampaignCode()));
        if (campaign.isEmpty()) {
            renderCampaignNotFound(response, request.getContextPath(), campaignCode);
            return;
        }

        HttpSession session = request.getSession(true);
        Identity identity = loadIdentity(session);
        int requestedCount = parseIntOrZero(request.getParameter("requestedCount"));
        boolean identitySaved = "1".equals(request.getParameter("identitySaved"));

        String statusMessage = null;
        if (requestedCount > 0) {
            statusMessage = "Requested to join " + requestedCount + " meeting"
                    + (requestedCount == 1 ? "" : "s") + ".";
        } else if (identitySaved) {
            statusMessage = "Your details were saved. You can now request to join meetings.";
        }

        renderHub(response, request.getContextPath(), campaign.get(), identity, null, statusMessage);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        String campaignCode = parseCampaignCode(request);
        if (campaignCode == null) {
            renderCampaignNotFound(response, request.getContextPath(), null);
            return;
        }

        Optional<EsCampaign> campaign = campaignDao.findByCampaignCode(campaignCode)
                .filter(c -> campaignCode.equals(c.getCampaignCode()));
        if (campaign.isEmpty()) {
            renderCampaignNotFound(response, request.getContextPath(), campaignCode);
            return;
        }

        HttpSession session = request.getSession(true);
        String action = trimToNull(request.getParameter("action"));
        if ("saveIdentity".equals(action)) {
            handleSaveIdentity(request, response, campaign.get(), session);
            return;
        }
        if ("requestMeetings".equals(action)) {
            handleRequestMeetings(request, response, campaign.get(), session);
            return;
        }

        Identity identity = loadIdentity(session);
        renderHub(response, request.getContextPath(), campaign.get(), identity,
                "The submitted action was not recognized.", null);
    }

    private void handleSaveIdentity(HttpServletRequest request, HttpServletResponse response, EsCampaign campaign,
            HttpSession session) throws IOException {
        String firstName = trimToNull(request.getParameter("firstName"));
        String lastName = trimToNull(request.getParameter("lastName"));
        String email = trimToNull(request.getParameter("email"));
        String emailNormalized = EsNormalizer.normalizeEmail(email);

        if (firstName == null || lastName == null || emailNormalized == null) {
            Identity identity = new Identity(firstName, lastName, email, emailNormalized);
            renderHub(response, request.getContextPath(), campaign, identity,
                    "First name, last name, and email are required to request meetings.", null);
            return;
        }

        setSessionValue(session, ATTR_FIRST_NAME, firstName);
        setSessionValue(session, ATTR_LAST_NAME, lastName);
        setSessionValue(session, ATTR_EMAIL, email);
        setSessionValue(session, ATTR_EMAIL_NORMALIZED, emailNormalized);
        setSessionValue(session, ATTR_CAMPAIGN_CODE, campaign.getCampaignCode());
        ensureSessionKey(session);

        response.sendRedirect(request.getContextPath() + "/register/complete/"
                + URLEncoder.encode(campaign.getCampaignCode(), StandardCharsets.UTF_8)
                + "?identitySaved=1");
    }

    private void handleRequestMeetings(HttpServletRequest request, HttpServletResponse response, EsCampaign campaign,
            HttpSession session) throws IOException {
        Identity identity = loadIdentity(session);
        if (!identity.complete()) {
            renderHub(response, request.getContextPath(), campaign, identity,
                    "Please provide your first name, last name, and email before requesting meetings.", null);
            return;
        }

        List<EsCampaignMeetingBrowseRow> meetingRows = campaignTopicDao
                .findActiveMeetingRowsByCampaignIdOrdered(campaign.getEsCampaignId());
        Map<Long, EsTopicMeetingMember> existingByMeetingId = findExistingMembershipByMeetingId(meetingRows,
                identity.emailNormalized());
        Set<Long> selectedMeetingIds = parseMeetingIdsCsv(request.getParameter("selectedMeetingIds"));
        Long userId = findAuthenticatedUserId(request);

        int requestedCount = 0;
        for (EsCampaignMeetingBrowseRow row : meetingRows) {
            Long meetingId = row.getEsTopicMeetingId();
            if (!selectedMeetingIds.contains(meetingId)) {
                continue;
            }

            upsertTopicSubscription(row.getEsTopicId(), campaign.getEsCampaignId(),
                    identity.email(), identity.emailNormalized(), userId);

            EsTopicMeetingMember existing = existingByMeetingId.get(meetingId);
            if (existing != null && (existing.getMembershipStatus() == EsTopicMeetingMember.MembershipStatus.REQUESTED
                    || existing.getMembershipStatus() == EsTopicMeetingMember.MembershipStatus.APPROVED)) {
                continue;
            }

            if (existing != null) {
                existing.setMembershipStatus(EsTopicMeetingMember.MembershipStatus.REQUESTED);
                existing.setEmail(identity.email());
                existing.setEmailNormalized(identity.emailNormalized());
                existing.setSourceCampaignId(campaign.getEsCampaignId());
                existing.setApprovedAt(null);
                existing.setApprovedByUserId(null);
                topicMeetingMemberDao.saveOrUpdate(existing);
                requestedCount++;
                continue;
            }

            EsTopicMeetingMember member = new EsTopicMeetingMember();
            member.setEsTopicMeetingId(meetingId);
            member.setEmail(identity.email());
            member.setEmailNormalized(identity.emailNormalized());
            member.setMembershipStatus(EsTopicMeetingMember.MembershipStatus.REQUESTED);
            member.setSourceCampaignId(campaign.getEsCampaignId());
            topicMeetingMemberDao.saveOrUpdate(member);
            requestedCount++;
        }

        response.sendRedirect(request.getContextPath() + "/register/complete/"
                + URLEncoder.encode(campaign.getCampaignCode(), StandardCharsets.UTF_8)
                + "?requestedCount=" + Math.max(requestedCount, 0));
    }

    private void upsertTopicSubscription(Long esTopicId, Long sourceCampaignId, String email, String emailNormalized,
            Long userId) {
        if (esTopicId == null || emailNormalized == null) {
            return;
        }
        EsSubscription subscription = new EsSubscription();
        subscription.setEmail(email);
        subscription.setEmailNormalized(emailNormalized);
        subscription.setUserId(userId);
        subscription.setEsTopicId(esTopicId);
        subscription.setSubscriptionType(EsSubscription.SubscriptionType.TOPIC);
        subscription.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
        subscription.setSourceCampaignId(sourceCampaignId);
        esInterestService.subscribeOrUpdate(subscription);
    }

    private Long findAuthenticatedUserId(HttpServletRequest request) {
        Optional<User> authenticatedUser = authFlowService.findAuthenticatedUser(request);
        return authenticatedUser.map(User::getUserId).orElse(null);
    }

    private void renderHub(HttpServletResponse response, String contextPath, EsCampaign campaign, Identity identity,
            String errorMessage, String statusMessage) throws IOException {
        response.setContentType("text/html;charset=UTF-8");

        List<EsCampaignMeetingBrowseRow> meetingRows = campaignTopicDao
                .findActiveMeetingRowsByCampaignIdOrdered(campaign.getEsCampaignId());
        Map<Long, EsTopicMeetingMember> existingByMeetingId = findExistingMembershipByMeetingId(meetingRows,
                identity.emailNormalized());

        long alreadyRequestedCount = meetingRows.stream().filter(row -> {
            EsTopicMeetingMember member = existingByMeetingId.get(row.getEsTopicMeetingId());
            if (member == null) {
                return false;
            }
            return member.getMembershipStatus() == EsTopicMeetingMember.MembershipStatus.REQUESTED
                    || member.getMembershipStatus() == EsTopicMeetingMember.MembershipStatus.APPROVED;
        }).count();
        boolean allRequested = !meetingRows.isEmpty() && alreadyRequestedCount == meetingRows.size();
        boolean showIdentityInline = !identity.complete() && !meetingRows.isEmpty();

        String encodedCampaignCode = URLEncoder.encode(campaign.getCampaignCode(), StandardCharsets.UTF_8);

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Registration Complete - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"es-topics-page\">");
            out.println("    <section class=\"es-topics-header\">");
            out.println("      <h1>Registration Complete</h1>");
            out.println(
                    "      <p class=\"es-topics-campaign\">Your registration is confirmed. Next, you can optionally request to join meetings, then continue to topics.</p>");
            out.println("    </section>");

            if (statusMessage != null) {
                out.println("    <p class=\"es-status-message\">" + escapeHtml(statusMessage) + "</p>");
            }
            if (errorMessage != null) {
                out.println("    <p class=\"es-status-message es-vote-error\">" + escapeHtml(errorMessage) + "</p>");
            }

            out.println("    <section class=\"es-topic-groups\">");
            out.println("      <section class=\"es-stage-group\">");
            out.println("        <h2 class=\"es-stage-title\">Meetings and Groups</h2>");

            if (meetingRows.isEmpty()) {
                out.println(
                        "        <p>No active meetings are currently available. You can continue to topics below.</p>");
            } else if (allRequested) {
                out.println("        <p>You have already requested all currently active meetings.</p>");
            } else if (alreadyRequestedCount > 0) {
                out.println("        <p>Are you interested in these other meetings?</p>");
            } else {
                out.println("        <p>Select any meetings you would like to request to join.</p>");
            }

            if (showIdentityInline) {
                out.println("        <form class=\"login-form\" method=\"post\" action=\"" + contextPath
                        + "/register/complete/" + encodedCampaignCode + "\">");
                out.println("          <input type=\"hidden\" name=\"action\" value=\"saveIdentity\" />");
                out.println("          <label for=\"firstName\">First Name (required for meeting requests)</label>");
                out.println("          <input id=\"firstName\" name=\"firstName\" type=\"text\" required value=\""
                        + escapeHtml(orEmpty(identity.firstName())) + "\" />");
                out.println("          <label for=\"lastName\">Last Name (required for meeting requests)</label>");
                out.println("          <input id=\"lastName\" name=\"lastName\" type=\"text\" required value=\""
                        + escapeHtml(orEmpty(identity.lastName())) + "\" />");
                out.println("          <label for=\"email\">Email (required for meeting requests)</label>");
                out.println("          <input id=\"email\" name=\"email\" type=\"email\" required value=\""
                        + escapeHtml(orEmpty(identity.email())) + "\" />");
                out.println("          <button type=\"submit\">Save details to request meetings</button>");
                out.println("        </form>");
                out.println(
                        "        <p>You can skip this and continue directly to topics if you prefer not to request meetings.</p>");
            }

            out.println("        <form id=\"es-meeting-request-form\" method=\"post\" action=\"" + contextPath
                    + "/register/complete/" + encodedCampaignCode + "\">");
            out.println("          <input type=\"hidden\" name=\"action\" value=\"requestMeetings\" />");
            out.println(
                    "          <input type=\"hidden\" id=\"es-selected-meeting-ids\" name=\"selectedMeetingIds\" value=\"\" />");
            out.println("          <div class=\"es-topic-list\">");

            boolean hasRequestable = false;
            for (EsCampaignMeetingBrowseRow row : meetingRows) {
                EsTopicMeetingMember member = existingByMeetingId.get(row.getEsTopicMeetingId());
                EsTopicMeetingMember.MembershipStatus status = member == null ? null : member.getMembershipStatus();
                boolean alreadyRequested = status == EsTopicMeetingMember.MembershipStatus.REQUESTED
                        || status == EsTopicMeetingMember.MembershipStatus.APPROVED;
                boolean requestable = identity.complete() && !alreadyRequested;
                if (requestable) {
                    hasRequestable = true;
                }

                String stateText;
                if (status == EsTopicMeetingMember.MembershipStatus.APPROVED) {
                    stateText = "Joined";
                } else if (status == EsTopicMeetingMember.MembershipStatus.REQUESTED) {
                    stateText = "Requested";
                } else if (status == EsTopicMeetingMember.MembershipStatus.DECLINED
                        || status == EsTopicMeetingMember.MembershipStatus.REMOVED) {
                    stateText = "Can request again";
                } else {
                    stateText = "";
                }

                String displayName = trimToNull(row.getMeetingName()) == null
                        ? orEmpty(row.getTopicName())
                        : row.getMeetingName();
                String description = trimToNull(row.getMeetingDescription()) == null
                        ? orEmpty(row.getTopicName())
                        : row.getMeetingDescription();

                out.println("            <article class=\"es-topic-row"
                        + (alreadyRequested ? " is-subscribed" : "") + "\">");
                out.println("              <div class=\"es-topic-checkbox-wrap\">");
                out.println("                <input class=\"es-topic-checkbox es-meeting-checkbox\" type=\"checkbox\""
                        + " data-meeting-id=\"" + row.getEsTopicMeetingId() + "\""
                        + (requestable ? "" : " disabled")
                        + " aria-label=\"Select " + escapeHtml(orEmpty(displayName)) + "\" />");
                out.println("              </div>");
                out.println("              <div class=\"es-topic-content\">");
                out.println("                <div class=\"es-topic-top\">");
                out.println("                  <h3>" + escapeHtml(orEmpty(displayName)) + "</h3>");
                out.println("                  <span class=\"es-topic-state\"" + (stateText.isEmpty() ? " hidden" : "")
                        + ">"
                        + escapeHtml(stateText) + "</span>");
                out.println("                </div>");
                out.println(
                        "                <p class=\"es-topic-preview\">" + escapeHtml(orEmpty(description)) + "</p>");
                out.println(
                        "                <p class=\"es-detail-stage\">Topic: " + escapeHtml(orEmpty(row.getTopicName()))
                                + " (" + escapeHtml(orEmpty(row.getTopicCode())) + ")"
                                + (trimToNull(row.getStage()) == null ? "" : " | Stage: " + escapeHtml(row.getStage()))
                                + "</p>");
                if (Boolean.TRUE.equals(row.getJoinRequiresApproval())) {
                    out.println("                <p class=\"es-detail-stage\">Approval required before joining.</p>");
                }
                out.println("              </div>");
                out.println("            </article>");
            }

            out.println("          </div>");
            if (identity.complete() && hasRequestable) {
                out.println("          <div class=\"es-sticky-action\">\n"
                        + "            <button id=\"es-meeting-request-button\" type=\"submit\" disabled>Request to Join (0)</button>\n"
                        + "          </div>");
            }
            out.println("        </form>");
            out.println("      </section>");

            out.println("      <section class=\"es-stage-group\">\n"
                    + "        <h2 class=\"es-stage-title\">Topics</h2>\n"
                    + "        <p>Follow topics you care about and get updates.</p>\n"
                    + "        <p><a class=\"button-link\" href=\"" + contextPath + "/topics/" + encodedCampaignCode
                    + "\">Explore topics</a></p>\n"
                    + "      </section>");
            out.println("    </section>");
            out.println("  </main>");

            out.println("  <script>");
            out.println("    (function() {");
            out.println("      var selectedInput = document.getElementById('es-selected-meeting-ids');");
            out.println("      var submitButton = document.getElementById('es-meeting-request-button');");
            out.println(
                    "      var checkboxes = Array.prototype.slice.call(document.querySelectorAll('.es-meeting-checkbox'));");
            out.println("      if (!selectedInput || !submitButton || checkboxes.length === 0) { return; }");
            out.println("      var selected = new Set();");
            out.println("      function sync() {");
            out.println("        selectedInput.value = Array.from(selected).join(',');");
            out.println("        submitButton.textContent = 'Request to Join (' + selected.size + ')';");
            out.println("        submitButton.disabled = selected.size === 0;");
            out.println("      }");
            out.println("      checkboxes.forEach(function(checkbox) {");
            out.println("        checkbox.addEventListener('change', function() {");
            out.println("          var meetingId = checkbox.getAttribute('data-meeting-id');");
            out.println(
                    "          if (checkbox.checked) { selected.add(meetingId); } else { selected.delete(meetingId); }");
            out.println("          sync();");
            out.println("        });");
            out.println("      });");
            out.println("      sync();");
            out.println("    })();");
            out.println("  </script>");

            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private Map<Long, EsTopicMeetingMember> findExistingMembershipByMeetingId(List<EsCampaignMeetingBrowseRow> rows,
            String emailNormalized) {
        if (rows == null || rows.isEmpty() || emailNormalized == null) {
            return Map.of();
        }
        List<Long> meetingIds = rows.stream()
                .map(EsCampaignMeetingBrowseRow::getEsTopicMeetingId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (meetingIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, EsTopicMeetingMember> byMeetingId = new LinkedHashMap<>();
        for (EsTopicMeetingMember member : topicMeetingMemberDao.findByMeetingIdsAndEmailNormalized(meetingIds,
                emailNormalized)) {
            byMeetingId.put(member.getEsTopicMeetingId(), member);
        }
        return byMeetingId;
    }

    private Set<Long> parseMeetingIdsCsv(String csv) {
        Set<Long> ids = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }
        for (String part : csv.split(",")) {
            Long parsed = parseLong(part);
            if (parsed != null && parsed > 0L) {
                ids.add(parsed);
            }
        }
        return ids;
    }

    private Identity loadIdentity(HttpSession session) {
        String firstName = trimToNull((String) session.getAttribute(ATTR_FIRST_NAME));
        String lastName = trimToNull((String) session.getAttribute(ATTR_LAST_NAME));
        String email = trimToNull((String) session.getAttribute(ATTR_EMAIL));
        String emailNormalized = trimToNull((String) session.getAttribute(ATTR_EMAIL_NORMALIZED));
        if (emailNormalized == null) {
            emailNormalized = EsNormalizer.normalizeEmail(email);
        }
        return new Identity(firstName, lastName, email, emailNormalized);
    }

    private String ensureSessionKey(HttpSession session) {
        String sessionKey = trimToNull((String) session.getAttribute(ATTR_SESSION_KEY));
        if (sessionKey == null) {
            sessionKey = session.getId();
            session.setAttribute(ATTR_SESSION_KEY, sessionKey);
        }
        return sessionKey;
    }

    private void setSessionValue(HttpSession session, String attributeName, String value) {
        if (value == null) {
            session.removeAttribute(attributeName);
            return;
        }
        session.setAttribute(attributeName, value);
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

    private Integer parseIntOrZero(String value) {
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            return 0;
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

    private record Identity(String firstName, String lastName, String email, String emailNormalized) {
        private boolean complete() {
            return firstName != null && lastName != null && email != null && emailNormalized != null;
        }
    }
}