package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsCampaignRegistrationDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.model.EsCampaign;
import org.airahub.interophub.model.EsCampaignRegistration;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.EsInterestService;
import org.airahub.interophub.service.EsNormalizer;

public class EsRegisterForMeetingServlet extends HttpServlet {

    private static final String ATTR_FIRST_NAME = "interophub.es.registration.firstName";
    private static final String ATTR_LAST_NAME = "interophub.es.registration.lastName";
    private static final String ATTR_EMAIL = "interophub.es.registration.email";
    private static final String ATTR_EMAIL_NORMALIZED = "interophub.es.registration.emailNormalized";
    private static final String ATTR_CAMPAIGN_CODE = "interophub.es.registration.campaignCode";
    private static final String ATTR_SESSION_KEY = "interophub.es.registration.sessionKey";
    private static final String ATTR_CAMPAIGN_REGISTRATION_ID = "interophub.es.registration.campaignRegistrationId";

    private final EsCampaignDao campaignDao;
    private final EsTopicDao topicDao;
    private final EsTopicMeetingDao topicMeetingDao;
    private final EsTopicMeetingMemberDao topicMeetingMemberDao;
    private final EsCampaignRegistrationDao registrationDao;
    private final EsInterestService esInterestService;
    private final AuthFlowService authFlowService;

    public EsRegisterForMeetingServlet() {
        this.campaignDao = new EsCampaignDao();
        this.topicDao = new EsTopicDao();
        this.topicMeetingDao = new EsTopicMeetingDao();
        this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
        this.registrationDao = new EsCampaignRegistrationDao();
        this.esInterestService = new EsInterestService();
        this.authFlowService = new AuthFlowService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PathParts parts = parsePath(request);
        if (parts == null) {
            renderNotFound(response, request.getContextPath(), "Registration link is incomplete.");
            return;
        }

        Resolution resolution = resolvePath(parts);
        if (!resolution.valid()) {
            renderNotFound(response, request.getContextPath(), resolution.errorMessage());
            return;
        }

        renderForm(response, request.getContextPath(), resolution,
                null, null, null, null, false);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");

        PathParts parts = parsePath(request);
        if (parts == null) {
            renderNotFound(response, request.getContextPath(), "Registration link is incomplete.");
            return;
        }

        Resolution resolution = resolvePath(parts);
        if (!resolution.valid()) {
            renderNotFound(response, request.getContextPath(), resolution.errorMessage());
            return;
        }

        String firstName = trimToNull(request.getParameter("firstName"));
        String lastName = trimToNull(request.getParameter("lastName"));
        String email = trimToNull(request.getParameter("email"));
        String emailNormalized = EsNormalizer.normalizeEmail(email);
        boolean generalUpdatesOptIn = request.getParameter("generalUpdatesOptIn") != null;

        if (firstName == null || lastName == null || emailNormalized == null) {
            renderForm(response, request.getContextPath(), resolution,
                    "First name, last name, and email are required to request a meeting.",
                    firstName, lastName, email, generalUpdatesOptIn);
            return;
        }

        HttpSession session = request.getSession(true);
        String sessionKey = session.getId();

        EsCampaignRegistration registration = new EsCampaignRegistration();
        registration.setEsCampaignId(resolution.campaign().getEsCampaignId());
        registration.setFirstName(firstName);
        registration.setLastName(lastName);
        registration.setEmail(email);
        registration.setEmailNormalized(emailNormalized);
        registration.setGeneralUpdatesOptIn(generalUpdatesOptIn);
        registration.setSessionKey(sessionKey);
        registrationDao.save(registration);

        upsertMeetingRequest(resolution.meeting(), resolution.campaign(), email, emailNormalized);
        upsertTopicSubscription(resolution.topic().getEsTopicId(), resolution.campaign().getEsCampaignId(),
                email, emailNormalized, findAuthenticatedUserId(request));

        if (generalUpdatesOptIn) {
            EsSubscription subscription = new EsSubscription();
            subscription.setEmail(email);
            subscription.setEmailNormalized(emailNormalized);
            subscription.setUserId(null);
            subscription.setEsTopicId(null);
            subscription.setSubscriptionType(EsSubscription.SubscriptionType.GENERAL_ES);
            subscription.setStatus(EsSubscription.SubscriptionStatus.SUBSCRIBED);
            subscription.setSourceCampaignId(resolution.campaign().getEsCampaignId());
            esInterestService.subscribeOrUpdate(subscription);
        }

        setSessionValue(session, ATTR_FIRST_NAME, firstName);
        setSessionValue(session, ATTR_LAST_NAME, lastName);
        setSessionValue(session, ATTR_EMAIL, email);
        setSessionValue(session, ATTR_EMAIL_NORMALIZED, emailNormalized);
        setSessionValue(session, ATTR_CAMPAIGN_CODE, resolution.campaign().getCampaignCode());
        setSessionValue(session, ATTR_SESSION_KEY, sessionKey);
        session.setAttribute(ATTR_CAMPAIGN_REGISTRATION_ID, registration.getEsCampaignRegistrationId());

        response.sendRedirect(request.getContextPath() + "/register/complete/"
                + URLEncoder.encode(resolution.campaign().getCampaignCode(), StandardCharsets.UTF_8));
    }

    private void upsertMeetingRequest(EsTopicMeeting meeting, EsCampaign campaign, String email,
            String emailNormalized) {
        Optional<EsTopicMeetingMember> existing = topicMeetingMemberDao.findByMeetingIdAndEmailNormalized(
                meeting.getEsTopicMeetingId(), emailNormalized);

        if (existing.isPresent()) {
            EsTopicMeetingMember member = existing.get();
            member.setEmail(email);
            member.setEmailNormalized(emailNormalized);
            member.setSourceCampaignId(campaign.getEsCampaignId());

            EsTopicMeetingMember.MembershipStatus status = member.getMembershipStatus();
            if (status == EsTopicMeetingMember.MembershipStatus.DECLINED
                    || status == EsTopicMeetingMember.MembershipStatus.REMOVED) {
                member.setMembershipStatus(EsTopicMeetingMember.MembershipStatus.REQUESTED);
                member.setApprovedAt(null);
                member.setApprovedByUserId(null);
            }
            topicMeetingMemberDao.saveOrUpdate(member);
            return;
        }

        EsTopicMeetingMember member = new EsTopicMeetingMember();
        member.setEsTopicMeetingId(meeting.getEsTopicMeetingId());
        member.setEmail(email);
        member.setEmailNormalized(emailNormalized);
        member.setMembershipStatus(EsTopicMeetingMember.MembershipStatus.REQUESTED);
        member.setSourceCampaignId(campaign.getEsCampaignId());
        topicMeetingMemberDao.saveOrUpdate(member);
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

    private void renderForm(HttpServletResponse response, String contextPath, Resolution resolution,
            String errorMessage,
            String firstName, String lastName, String email, boolean generalUpdatesOptIn)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            String campaignCodeEncoded = URLEncoder.encode(resolution.campaign().getCampaignCode(),
                    StandardCharsets.UTF_8);
            String topicCodeEncoded = URLEncoder.encode(resolution.topic().getTopicCode(), StandardCharsets.UTF_8);

            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>Join - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Join the " + escapeHtml(orEmpty(resolution.meeting().getMeetingName())) + "</h1>");
            if (resolution.meeting().getMeetingDescription() != null
                    && !resolution.meeting().getMeetingDescription().isBlank()) {
                out.println("    <p>" + escapeHtml(resolution.meeting().getMeetingDescription()) + "</p>");
            }
            if (errorMessage != null) {
                out.println("    <p><strong>Could not submit:</strong> " + escapeHtml(errorMessage) + "</p>");
            }

            out.println("    <form class=\"login-form\" method=\"post\" action=\"" + contextPath
                    + "/registerForMeeting/" + campaignCodeEncoded + "/" + topicCodeEncoded + "\">");
            out.println("      <label for=\"firstName\">First Name (required)</label>");
            out.println("      <input id=\"firstName\" name=\"firstName\" type=\"text\" required value=\""
                    + escapeHtml(orEmpty(firstName)) + "\" />");

            out.println("      <label for=\"lastName\">Last Name (required)</label>");
            out.println("      <input id=\"lastName\" name=\"lastName\" type=\"text\" required value=\""
                    + escapeHtml(orEmpty(lastName)) + "\" />");

            out.println("      <label for=\"email\">Email (required)</label>");
            out.println("      <input id=\"email\" name=\"email\" type=\"email\" required value=\""
                    + escapeHtml(orEmpty(email)) + "\" />");

            out.println("      <label>");
            out.println("        <input id=\"generalUpdatesOptIn\" name=\"generalUpdatesOptIn\" type=\"checkbox\""
                    + (generalUpdatesOptIn ? " checked" : "") + " />");
            out.println("        Subscribe for general Emerging Standards updates");
            out.println("      </label>");

            out.println("      <button type=\"submit\">Request to Join</button>");
            out.println("    </form>");

            out.println("    <p><a class=\"button-link\" href=\"" + contextPath + "/topics/"
                    + campaignCodeEncoded + "\">Continue to topics instead</a></p>");

            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
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
            out.println("  <title>Meeting Registration Not Found - InteropHub</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");
            out.println("    <h1>Meeting Registration Not Found</h1>");
            out.println("    <p>" + escapeHtml(orEmpty(message)) + "</p>");
            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private Resolution resolvePath(PathParts parts) {
        Optional<EsCampaign> campaignOpt = campaignDao.findByCampaignCode(parts.campaignCode())
                .filter(c -> parts.campaignCode().equals(c.getCampaignCode()));
        if (campaignOpt.isEmpty()) {
            return Resolution.invalid("Campaign was not found.");
        }

        Optional<EsTopic> topicOpt = topicDao.findByTopicCode(parts.topicCode())
                .filter(t -> parts.topicCode().equals(t.getTopicCode()));
        if (topicOpt.isEmpty()) {
            return Resolution.invalid("Topic was not found.");
        }

        EsCampaign campaign = campaignOpt.get();
        EsTopic topic = topicOpt.get();
        Optional<EsTopicMeeting> meetingOpt = topicMeetingDao.findByTopicId(topic.getEsTopicId())
                .filter(m -> m.getDisabledAt() == null);
        if (meetingOpt.isEmpty()) {
            return Resolution.invalid("No active meeting is configured for this topic.");
        }

        return Resolution.valid(campaign, topic, meetingOpt.get());
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
        String topicCode = trimToNull(raw[1]);
        if (campaignCode == null || topicCode == null) {
            return null;
        }
        return new PathParts(campaignCode, topicCode);
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

    private record PathParts(String campaignCode, String topicCode) {
    }

    private record Resolution(EsCampaign campaign, EsTopic topic, EsTopicMeeting meeting, String errorMessage) {
        private static Resolution valid(EsCampaign campaign, EsTopic topic, EsTopicMeeting meeting) {
            return new Resolution(campaign, topic, meeting, null);
        }

        private static Resolution invalid(String errorMessage) {
            return new Resolution(null, null, null, errorMessage);
        }

        private boolean valid() {
            return campaign != null && topic != null && meeting != null;
        }
    }
}