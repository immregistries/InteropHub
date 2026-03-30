package org.airahub.interophub.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.LegalTermAcceptanceDao;
import org.airahub.interophub.dao.LegalTermDao;
import org.airahub.interophub.dao.MagicLinkSendEventDao;
import org.airahub.interophub.model.LegalTerm;
import org.airahub.interophub.model.LegalTermAcceptance;
import org.airahub.interophub.model.MagicLinkSendEvent;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.AuthService;
import org.airahub.interophub.service.EmailService;

public class SendWelcomeEmailServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(SendWelcomeEmailServlet.class.getName());
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_EMAIL_LOCAL_PART_LENGTH = 64;
    private static final int MAX_DISPLAY_NAME_LENGTH = 60;
    private static final int MAX_ORGANIZATION_LENGTH = 120;
    private static final int MAX_ROLE_TITLE_LENGTH = 120;

    private final AuthFlowService authFlowService;
    private final AuthService authService;
    private final EmailService emailService;
    private final LegalTermDao legalTermDao;
    private final LegalTermAcceptanceDao legalTermAcceptanceDao;
    private final MagicLinkSendEventDao magicLinkSendEventDao;

    public SendWelcomeEmailServlet() {
        this.authFlowService = new AuthFlowService();
        this.authService = new AuthService();
        this.emailService = new EmailService();
        this.legalTermDao = new LegalTermDao();
        this.legalTermAcceptanceDao = new LegalTermAcceptanceDao();
        this.magicLinkSendEventDao = new MagicLinkSendEventDao();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(request.getContextPath() + "/home");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        String email = trimToNull(request.getParameter("email"));
        String normalizedEmail = normalizeEmail(email);
        String contextPath = request.getContextPath();
        String displayName = trimToNull(request.getParameter("displayName"));
        String organization = trimToNull(request.getParameter("organization"));
        String roleTitle = trimToNull(request.getParameter("roleTitle"));
        Set<Long> selectedLegalTermIds = parseSelectedRegistrationTermIds(request);
        Optional<AuthFlowService.ExternalAuthRequest> externalAuthRequest = Optional.empty();
        String externalAuthError = null;
        try {
            externalAuthRequest = authFlowService.parseExternalAuthRequest(request);
            externalAuthRequest.ifPresent(value -> authFlowService.rememberExternalAuthRequest(request, value));
        } catch (IllegalArgumentException ex) {
            externalAuthRequest = authFlowService.recallExternalAuthRequest(request);
            if (externalAuthRequest.isEmpty()) {
                externalAuthError = ex.getMessage();
            }
        }

        boolean profileSubmission = request.getParameter("profileSubmission") != null;
        String requestId = UUID.randomUUID().toString();

        if (!profileSubmission && normalizedEmail == null) {
            redirectToHomeWithEmailError(request, response, email);
            return;
        }

        response.setContentType("text/html;charset=UTF-8");
        User auditUser = null;
        Long issuedMagicId = null;

        try (PrintWriter out = response.getWriter()) {
            String pageTitle = profileSubmission ? "Register - InteropHub" : "Register - InteropHub";
            out.println("<!DOCTYPE html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("  <meta charset=\"UTF-8\" />");
            out.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
            out.println("  <title>" + escapeHtml(pageTitle) + "</title>");
            out.println("  <link rel=\"stylesheet\" href=\"" + contextPath + "/css/main.css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("  <main class=\"container\">");

            try {
                if (externalAuthError != null) {
                    throw new IllegalArgumentException(externalAuthError);
                }
                if (normalizedEmail == null) {
                    throw new IllegalArgumentException("A valid email address is required.");
                }

                Optional<User> existingUser = authService.findUserByEmail(normalizedEmail);
                User user;
                if (existingUser.isPresent()) {
                    user = existingUser.get();
                    auditUser = user;
                } else if (!profileSubmission) {
                    renderProfileForm(out, contextPath, email, displayName, organization, roleTitle,
                            selectedLegalTermIds,
                            Map.of(),
                            null,
                            externalAuthRequest.orElse(null));
                    out.println("  </main>");
                    PageFooterRenderer.render(out);
                    out.println("</body>");
                    out.println("</html>");
                    return;
                } else {
                    Map<String, String> fieldErrors = validateRegistrationFields(displayName, organization, roleTitle);
                    List<LegalTerm> registrationTerms = loadRegistrationTerms();
                    if (registrationTerms.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Registration terms are not configured. Please contact support.");
                    }
                    if (!fieldErrors.isEmpty()) {
                        renderProfileForm(out, contextPath, email, displayName, organization, roleTitle,
                                selectedLegalTermIds,
                                fieldErrors,
                                null,
                                externalAuthRequest.orElse(null));
                        out.println("  </main>");
                        PageFooterRenderer.render(out);
                        out.println("</body>");
                        out.println("</html>");
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        return;
                    }
                    if (!allRequiredRegistrationTermsAccepted(registrationTerms, selectedLegalTermIds)) {
                        throw new IllegalArgumentException(
                                "Please complete all profile fields and accept all legal terms to continue.");
                    }

                    user = authService.registerUser(
                            normalizedEmail,
                            displayName,
                            organization,
                            roleTitle);
                    auditUser = user;

                    saveTermAcceptancesForRegistration(user, registrationTerms, request);
                }

                AuthFlowService.IssuedMagicLink issuedMagicLink = authFlowService.issueMagicLinkWithMetadata(
                        user,
                        request,
                        externalAuthRequest.orElse(null));
                issuedMagicId = issuedMagicLink.getMagicId();
                authFlowService.clearRememberedExternalAuthRequest(request);

                logMagicLinkSendEvent(
                        MagicLinkSendEvent.EventType.SEND_REQUESTED,
                        requestId,
                        issuedMagicLink.getMagicId(),
                        user,
                        normalizedEmail,
                        request,
                        externalAuthRequest.orElse(null),
                        null,
                        null,
                        null,
                        null);

                logMagicLinkSendEvent(
                        MagicLinkSendEvent.EventType.SMTP_SEND_STARTED,
                        requestId,
                        issuedMagicLink.getMagicId(),
                        user,
                        normalizedEmail,
                        request,
                        externalAuthRequest.orElse(null),
                        null,
                        null,
                        null,
                        null);

                EmailService.SendWelcomeEmailResult sendResult = emailService.sendWelcomeEmailWithResult(
                        normalizedEmail,
                        issuedMagicLink.getMagicLinkUrl());

                logMagicLinkSendEvent(
                        MagicLinkSendEvent.EventType.SMTP_SEND_SUCCEEDED,
                        requestId,
                        issuedMagicLink.getMagicId(),
                        user,
                        normalizedEmail,
                        request,
                        externalAuthRequest.orElse(null),
                        sendResult.getSmtpMessageId(),
                        sendResult.getSmtpProvider(),
                        null,
                        null);

                LOGGER.info("requestId=" + requestId + " sent welcome email for userId=" + user.getUserId()
                        + " magicId=" + issuedMagicLink.getMagicId()
                        + " email=" + normalizedEmail);
                renderEmailSent(out, contextPath, email);
            } catch (IllegalArgumentException ex) {
                LOGGER.log(Level.INFO, "Could not complete registration request: {0}", ex.getMessage());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                if (profileSubmission && normalizedEmail != null) {
                    renderProfileForm(out, contextPath, email, displayName, organization, roleTitle,
                            selectedLegalTermIds,
                            Map.of(),
                            ex.getMessage(),
                            externalAuthRequest.orElse(null));
                } else {
                    out.println("    <h1>Email Send Failed</h1>");
                    out.println("    <p>Could not continue with this request.</p>");
                    out.println("    <p>Reason: " + escapeHtml(ex.getMessage()) + "</p>");
                    out.println("    <p><a href=\"" + contextPath + "/home\">Return to Home</a></p>");
                }
            } catch (Exception ex) {
                if (normalizedEmail != null && issuedMagicId != null) {
                    String smtpReplyCode = null;
                    String smtpProvider = null;
                    if (ex instanceof EmailService.EmailSendException emailSendException) {
                        smtpReplyCode = emailSendException.getSmtpReplyCode();
                        smtpProvider = emailSendException.getSmtpProvider();
                    }
                    logMagicLinkSendEvent(
                            MagicLinkSendEvent.EventType.SMTP_SEND_FAILED,
                            requestId,
                            issuedMagicId,
                            auditUser,
                            normalizedEmail,
                            request,
                            externalAuthRequest.orElse(null),
                            null,
                            smtpProvider,
                            smtpReplyCode,
                            ex);
                }
                LOGGER.log(Level.SEVERE, "Failed to issue magic link or send welcome email.", ex);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                if (profileSubmission && normalizedEmail != null) {
                    renderProfileForm(out, contextPath, email, displayName, organization, roleTitle,
                            selectedLegalTermIds,
                            Map.of(),
                            ex.getMessage(),
                            externalAuthRequest.orElse(null));
                } else {
                    out.println("    <h1>Email Send Failed</h1>");
                    out.println("    <p>Could not send email right now.</p>");
                    out.println("    <p>Reason: " + escapeHtml(ex.getMessage()) + "</p>");
                    out.println("    <p><a href=\"" + contextPath + "/home\">Return to Home</a></p>");
                }
            }

            out.println("  </main>");
            PageFooterRenderer.render(out);
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void renderEmailSent(PrintWriter out, String contextPath, String email) {
        out.println("    <h1>Email Sent</h1>");
        out.println("    <p>We sent a welcome email to <strong>" + escapeHtml(email) + "</strong>.</p>");
        out.println("    <p>Check your inbox and use the sign-in link in that message to continue.</p>");
        out.println("    <p><a href=\"" + contextPath + "/home\">Back to Home</a></p>");
    }

    private void renderProfileForm(PrintWriter out, String contextPath, String email, String displayName,
            String organization, String roleTitle, Set<Long> selectedLegalTermIds, Map<String, String> fieldErrors,
            String errorMessage,
            AuthFlowService.ExternalAuthRequest externalAuthRequest) {
        List<LegalTerm> registrationTerms = loadRegistrationTerms();

        out.println("    <h1>Register</h1>");
        out.println(
                "    <p>Use of these testing resources is free. We need a little information about you, and you must review and agree to the terms and limitations for using these tools.</p>");
        out.println("    <p>Email: <strong>" + escapeHtml(email) + "</strong></p>");
        if (errorMessage != null && !errorMessage.isBlank()) {
            out.println("    <p><strong>Could not continue:</strong> " + escapeHtml(errorMessage) + "</p>");
        }
        out.println(
                "    <form class=\"login-form\" action=\"" + contextPath + "/send-welcome-email\" method=\"post\">");
        out.println("      <input type=\"hidden\" name=\"profileSubmission\" value=\"1\" />");
        out.println("      <input type=\"hidden\" name=\"email\" value=\"" + escapeHtml(orEmpty(email)) + "\" />");
        if (externalAuthRequest != null) {
            out.println("      <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_APP_CODE + "\" value=\""
                    + escapeHtml(externalAuthRequest.getAppCode()) + "\" />");
            out.println("      <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_RETURN_TO + "\" value=\""
                    + escapeHtml(externalAuthRequest.getReturnTo()) + "\" />");
            out.println("      <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_STATE + "\" value=\""
                    + escapeHtml(externalAuthRequest.getState()) + "\" />");
            out.println("      <input type=\"hidden\" name=\"" + AuthFlowService.PARAM_REQUESTED_URL + "\" value=\""
                    + escapeHtml(externalAuthRequest.getRequestedUrl()) + "\" />");
        }

        out.println("      <label for=\"displayName\">Display Name" + renderFieldError(fieldErrors, "displayName")
                + "</label>");
        out.println("      <div class=\"field-hint\">Your first and last name for others to see</div>");
        out.println("      <input id=\"displayName\" name=\"displayName\" type=\"text\" required maxlength=\""
                + MAX_DISPLAY_NAME_LENGTH + "\" value=\""
                + escapeHtml(orEmpty(displayName)) + "\" />");

        out.println("      <label for=\"organization\">Organization" + renderFieldError(fieldErrors, "organization")
                + "</label>");
        out.println("      <div class=\"field-hint\">Full name of organization you are associated with</div>");
        out.println("      <input id=\"organization\" name=\"organization\" type=\"text\" required maxlength=\""
                + MAX_ORGANIZATION_LENGTH + "\" value=\""
                + escapeHtml(orEmpty(organization)) + "\" />");

        out.println("      <label for=\"roleTitle\">Role Title" + renderFieldError(fieldErrors, "roleTitle")
                + "</label>");
        out.println("      <input id=\"roleTitle\" name=\"roleTitle\" type=\"text\" required maxlength=\""
                + MAX_ROLE_TITLE_LENGTH + "\" value=\""
                + escapeHtml(orEmpty(roleTitle)) + "\" />");

        if (!registrationTerms.isEmpty()) {
            LegalTermsUiRenderer.renderTermsSection(out, registrationTerms, selectedLegalTermIds, "Legal Terms",
                    "legalTerm_");
        }

        out.println("      <div class=\"form-actions\">");
        out.println("        <button type=\"submit\">Register</button>");
        out.println("        <a class=\"button-link\" href=\"" + contextPath + "/home\">Cancel</a>");
        out.println("      </div>");
        out.println("    </form>");

        LegalTermsUiRenderer.renderTermsScript(out);
    }

    private List<LegalTerm> loadRegistrationTerms() {
        try {
            return legalTermDao.findActiveForScope(LegalTerm.ScopeType.REGISTRATION);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not load legal terms for registration form.", ex);
            return List.of();
        }
    }

    private Set<Long> parseSelectedRegistrationTermIds(HttpServletRequest request) {
        Set<Long> selectedIds = new HashSet<>();
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            if (paramName == null || !paramName.startsWith("legalTerm_")) {
                continue;
            }
            if (request.getParameter(paramName) == null) {
                continue;
            }
            String suffix = paramName.substring("legalTerm_".length());
            try {
                selectedIds.add(Long.valueOf(suffix));
            } catch (Exception ignored) {
                // Ignore malformed term id suffixes.
            }
        }
        return selectedIds;
    }

    private boolean allRequiredRegistrationTermsAccepted(List<LegalTerm> terms, Set<Long> selectedIds) {
        for (LegalTerm term : terms) {
            if (term.getTermId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(term.getRequired()) && !selectedIds.contains(term.getTermId())) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> validateRegistrationFields(String displayName, String organization, String roleTitle) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        if (!isValidDisplayName(displayName)) {
            fieldErrors.put("displayName",
                    "Enter your full first and last name (2+ characters each, max " + MAX_DISPLAY_NAME_LENGTH
                            + ").");
        }
        if (!isValidOrganization(organization)) {
            fieldErrors.put("organization",
                    "Use 3-" + MAX_ORGANIZATION_LENGTH
                            + " characters with letters, numbers, spaces, and common punctuation.");
        }
        if (!isValidRoleTitle(roleTitle)) {
            fieldErrors.put("roleTitle",
                    "Use 3-" + MAX_ROLE_TITLE_LENGTH
                            + " characters with letters, numbers, spaces, and common punctuation.");
        }

        return fieldErrors;
    }

    private boolean isValidDisplayName(String displayName) {
        if (displayName == null || displayName.length() < 5 || displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            return false;
        }
        if (!containsOnlySafeDisplayNameChars(displayName)) {
            return false;
        }

        String[] nameParts = displayName.split("\\s+");
        if (nameParts.length < 2) {
            return false;
        }

        String firstName = nameParts[0];
        String lastName = nameParts[nameParts.length - 1];
        return countLetters(firstName) >= 2 && countLetters(lastName) >= 2;
    }

    private boolean isValidOrganization(String organization) {
        return organization != null
                && organization.length() >= 3
                && organization.length() <= MAX_ORGANIZATION_LENGTH
                && containsOnlySafeTextChars(organization, false);
    }

    private boolean isValidRoleTitle(String roleTitle) {
        return roleTitle != null
                && roleTitle.length() >= 3
                && roleTitle.length() <= MAX_ROLE_TITLE_LENGTH
                && containsOnlySafeTextChars(roleTitle, false);
    }

    private boolean containsOnlySafeTextChars(String value, boolean requireLetter) {
        boolean hasLetter = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch)) {
                return false;
            }
            if (Character.isLetter(ch)) {
                hasLetter = true;
            }
            boolean allowed = Character.isLetterOrDigit(ch)
                    || Character.isWhitespace(ch)
                    || ch == '.'
                    || ch == ','
                    || ch == '\''
                    || ch == '-'
                    || ch == '&'
                    || ch == '/'
                    || ch == '(' || ch == ')';
            if (!allowed) {
                return false;
            }
        }
        return !requireLetter || hasLetter;
    }

    private boolean containsOnlySafeDisplayNameChars(String value) {
        boolean hasLetter = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch)) {
                return false;
            }

            int type = Character.getType(ch);
            boolean isUnicodeNamePunctuation = type == Character.DASH_PUNCTUATION
                    || type == Character.CONNECTOR_PUNCTUATION
                    || type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK;

            if (Character.isLetter(ch)) {
                hasLetter = true;
            }

            boolean allowed = Character.isLetterOrDigit(ch)
                    || Character.isWhitespace(ch)
                    || ch == '\''
                    || ch == '’'
                    || ch == '.'
                    || isUnicodeNamePunctuation;
            if (!allowed) {
                return false;
            }
        }
        return hasLetter;
    }

    private int countLetters(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int letterCount = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                letterCount++;
            }
        }
        return letterCount;
    }

    private void redirectToHomeWithEmailError(HttpServletRequest request, HttpServletResponse response, String email)
            throws IOException {
        StringBuilder url = new StringBuilder(request.getContextPath()).append("/home?emailError=1");
        appendQueryParam(url, "email", trimToNull(email));
        appendQueryParam(url, AuthFlowService.PARAM_APP_CODE,
                trimToNull(request.getParameter(AuthFlowService.PARAM_APP_CODE)));
        appendQueryParam(url, AuthFlowService.PARAM_RETURN_TO,
                trimToNull(request.getParameter(AuthFlowService.PARAM_RETURN_TO)));
        appendQueryParam(url, AuthFlowService.PARAM_STATE,
                trimToNull(request.getParameter(AuthFlowService.PARAM_STATE)));
        appendQueryParam(url, AuthFlowService.PARAM_REQUESTED_URL,
                trimToNull(request.getParameter(AuthFlowService.PARAM_REQUESTED_URL)));
        response.sendRedirect(url.toString());
    }

    private void appendQueryParam(StringBuilder url, String key, String value) {
        if (value == null) {
            return;
        }
        url.append('&')
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private String renderFieldError(Map<String, String> fieldErrors, String fieldName) {
        if (fieldErrors == null) {
            return "";
        }
        String message = fieldErrors.get(fieldName);
        if (message == null || message.isBlank()) {
            return "";
        }
        return " <span class=\"field-error\">" + escapeHtml(message) + "</span>";
    }

    private void saveTermAcceptancesForRegistration(User user, List<LegalTerm> terms, HttpServletRequest request) {
        if (user == null || user.getUserId() == null || terms == null || terms.isEmpty()) {
            return;
        }
        String ipAddress = trimToNull(request.getRemoteAddr());
        String userAgent = trimToNull(request.getHeader("User-Agent"));
        for (LegalTerm term : terms) {
            if (term.getTermId() == null) {
                continue;
            }
            LegalTermAcceptance acceptance = legalTermAcceptanceDao
                    .findByTermUserWorkspace(term.getTermId(), user.getUserId(), null)
                    .orElseGet(LegalTermAcceptance::new);
            acceptance.setTermId(term.getTermId());
            acceptance.setUserId(user.getUserId());
            acceptance.setWorkspaceId(null);
            acceptance.setAcceptedValue(Boolean.TRUE);
            acceptance.setAcceptedAt(LocalDateTime.now());
            acceptance.setIpAddress(ipAddress);
            acceptance.setUserAgent(userAgent);
            legalTermAcceptanceDao.saveOrUpdate(acceptance);
        }
    }

    private void logMagicLinkSendEvent(
            MagicLinkSendEvent.EventType eventType,
            String requestId,
            Long magicId,
            User user,
            String normalizedEmail,
            HttpServletRequest request,
            AuthFlowService.ExternalAuthRequest externalAuthRequest,
            String smtpMessageId,
            String smtpProvider,
            String smtpReplyCode,
            Exception error) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return;
        }
        if (user == null || user.getUserId() == null) {
            return;
        }

        MagicLinkSendEvent event = new MagicLinkSendEvent();
        event.setEventType(eventType);
        event.setRequestId(trimToNull(requestId));
        event.setMagicId(magicId);
        event.setUserId(user.getUserId());
        event.setAppId(externalAuthRequest == null ? null : externalAuthRequest.getAppId());
        event.setEmailNormalized(normalizedEmail);
        event.setRequestIp(resolveIp(request.getRemoteAddr()));
        event.setUserAgent(trimToMax(request.getHeader("User-Agent"), 300));
        event.setSmtpMessageId(trimToMax(trimToNull(smtpMessageId), 255));
        event.setSmtpProvider(trimToMax(trimToNull(smtpProvider), 80));
        event.setSmtpReplyCode(trimToMax(trimToNull(smtpReplyCode), 32));
        event.setServerNode(trimToMax(resolveServerNode(), 120));

        if (error != null) {
            event.setErrorClass(trimToMax(error.getClass().getName(), 120));
            event.setErrorMessage(trimToMax(trimToNull(error.getMessage()), 1000));
        }

        MagicLinkSendEvent persisted = magicLinkSendEventDao.log(event);
        LOGGER.info("magicLinkSendEventId=" + persisted.getSendEventId()
                + " requestId=" + orEmpty(requestId)
                + " type=" + eventType.name()
                + " userId=" + (event.getUserId() == null ? "" : event.getUserId())
                + " magicId=" + (event.getMagicId() == null ? "" : event.getMagicId())
                + " email=" + normalizedEmail);
    }

    private byte[] resolveIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(rawIp).getAddress();
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveServerNode() {
        try {
            return trimToNull(InetAddress.getLocalHost().getHostName());
        } catch (Exception ex) {
            return null;
        }
    }

    private String trimToMax(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeEmail(String rawEmail) {
        String value = trimToNull(rawEmail);
        if (!isLikelyValidEmail(value)) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private boolean isLikelyValidEmail(String value) {
        if (value == null || value.length() > MAX_EMAIL_LENGTH) {
            return false;
        }

        int atIndex = value.indexOf('@');
        if (atIndex <= 0 || atIndex != value.lastIndexOf('@') || atIndex >= value.length() - 1) {
            return false;
        }

        String localPart = value.substring(0, atIndex);
        String domainPart = value.substring(atIndex + 1);
        if (localPart.length() > MAX_EMAIL_LOCAL_PART_LENGTH || localPart.startsWith(".") || localPart.endsWith(".")
                || localPart.contains("..")) {
            return false;
        }

        for (int i = 0; i < localPart.length(); i++) {
            char ch = localPart.charAt(i);
            boolean allowed = Character.isLetterOrDigit(ch)
                    || ch == '.'
                    || ch == '!'
                    || ch == '#'
                    || ch == '$'
                    || ch == '%'
                    || ch == '&'
                    || ch == '\''
                    || ch == '*'
                    || ch == '+'
                    || ch == '/'
                    || ch == '='
                    || ch == '?'
                    || ch == '^'
                    || ch == '_'
                    || ch == '`'
                    || ch == '{'
                    || ch == '|'
                    || ch == '}'
                    || ch == '~'
                    || ch == '-';
            if (!allowed) {
                return false;
            }
        }

        if (domainPart.length() > 253 || domainPart.startsWith(".") || domainPart.endsWith(".")) {
            return false;
        }

        String[] labels = domainPart.split("\\.");
        if (labels.length < 2) {
            return false;
        }

        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
                return false;
            }
            for (int i = 0; i < label.length(); i++) {
                char ch = label.charAt(i);
                if (!Character.isLetterOrDigit(ch) && ch != '-') {
                    return false;
                }
            }
        }

        String tld = labels[labels.length - 1];
        if (tld.length() < 2) {
            return false;
        }
        for (int i = 0; i < tld.length(); i++) {
            if (!Character.isLetter(tld.charAt(i))) {
                return false;
            }
        }

        return true;
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
