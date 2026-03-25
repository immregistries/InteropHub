package org.airahub.interophub.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.airahub.interophub.dao.AppRedirectAllowlistDao;
import org.airahub.interophub.dao.AppRegistryDao;
import org.airahub.interophub.dao.AuthLoginCodeDao;
import org.airahub.interophub.dao.HubSettingDao;
import org.airahub.interophub.dao.MagicLinkDao;
import org.airahub.interophub.dao.SessionDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.AppRedirectAllowlist;
import org.airahub.interophub.model.AppRegistry;
import org.airahub.interophub.model.AuthLoginCode;
import org.airahub.interophub.model.HubSetting;
import org.airahub.interophub.model.MagicLink;
import org.airahub.interophub.model.Session;
import org.airahub.interophub.model.User;

public class AuthFlowService {
    public static final String SESSION_COOKIE_NAME = "interophub_session";
    private static final String ADMIN_EMAIL_DOMAIN = "@immregistries.org";
    public static final String PARAM_APP_CODE = "app_code";
    public static final String PARAM_RETURN_TO = "return_to";
    public static final String PARAM_STATE = "state";
    public static final String PARAM_REQUESTED_URL = "requested_url";

    private static final int MAGIC_LINK_DAYS = 7;
    private static final int SESSION_DAYS = 30;
    private static final int LOGIN_CODE_MINUTES = 5;
    private static final int MIN_STATE_LENGTH = 8;
    private static final int MAX_STATE_LENGTH = 255;
    private static final int MAX_URL_LENGTH = 500;
    private static final int MAX_APP_CODE_LENGTH = 60;

    private final MagicLinkDao magicLinkDao;
    private final SessionDao sessionDao;
    private final UserDao userDao;
    private final HubSettingDao hubSettingDao;
    private final AppRegistryDao appRegistryDao;
    private final AppRedirectAllowlistDao appRedirectAllowlistDao;
    private final AuthLoginCodeDao authLoginCodeDao;
    private final SecureRandom secureRandom;

    public AuthFlowService() {
        this.magicLinkDao = new MagicLinkDao();
        this.sessionDao = new SessionDao();
        this.userDao = new UserDao();
        this.hubSettingDao = new HubSettingDao();
        this.appRegistryDao = new AppRegistryDao();
        this.appRedirectAllowlistDao = new AppRedirectAllowlistDao();
        this.authLoginCodeDao = new AuthLoginCodeDao();
        this.secureRandom = new SecureRandom();
    }

    public String issueMagicLink(User user, HttpServletRequest request) {
        return issueMagicLink(user, request, null);
    }

    public String issueMagicLink(User user, HttpServletRequest request, ExternalAuthRequest externalAuthRequest) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User is required before issuing a magic link.");
        }

        String rawToken = generateToken();
        MagicLink link = new MagicLink();
        link.setUserId(user.getUserId());
        link.setTokenHash(sha256(rawToken));
        link.setExpiresAt(LocalDateTime.now().plusDays(MAGIC_LINK_DAYS));
        link.setRequestIp(resolveIp(request.getRemoteAddr()));
        link.setUserAgent(trimToMax(request.getHeader("User-Agent"), 300));
        if (externalAuthRequest != null) {
            link.setAppId(externalAuthRequest.getAppId());
            link.setReturnTo(externalAuthRequest.getReturnTo());
            link.setStateNonce(externalAuthRequest.getState());
            link.setRequestedUrl(externalAuthRequest.getRequestedUrl());
        }
        magicLinkDao.save(link);

        return buildMagicLinkUrl(rawToken);
    }

    public AuthenticatedSession consumeMagicLink(String rawToken, HttpServletRequest request) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Magic link token is required.");
        }

        byte[] tokenHash = sha256(rawToken.trim());
        MagicLink magicLink = magicLinkDao.findValidUnconsumedByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Magic link is invalid or expired."));

        magicLinkDao.markConsumed(magicLink.getMagicId());

        User user = userDao.findById(magicLink.getUserId())
                .orElseThrow(() -> new IllegalStateException("User for magic link was not found."));

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(Boolean.TRUE);
            userDao.saveOrUpdate(user);
        }

        String rawSessionToken = generateToken();
        Session session = new Session();
        session.setUserId(user.getUserId());
        session.setSessionTokenHash(sha256(rawSessionToken));
        session.setExpiresAt(LocalDateTime.now().plusDays(SESSION_DAYS));
        session.setLastIp(resolveIp(request.getRemoteAddr()));
        session.setLastUserAgent(trimToMax(request.getHeader("User-Agent"), 300));
        sessionDao.save(session);

        String externalRedirectUrl = null;
        ExternalAuthRequest externalAuthRequest = resolveExternalAuthRequestForMagicLink(magicLink);
        if (externalAuthRequest != null) {
            externalRedirectUrl = issueExternalLoginCodeRedirect(user, externalAuthRequest);
        }

        return new AuthenticatedSession(user, rawSessionToken, externalRedirectUrl);
    }

    public String issueExternalLoginCodeRedirect(User user, ExternalAuthRequest externalAuthRequest) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }
        if (externalAuthRequest == null || externalAuthRequest.getAppId() == null) {
            throw new IllegalArgumentException("External auth request is required.");
        }

        String rawLoginCode = generateToken();
        AuthLoginCode loginCode = new AuthLoginCode();
        loginCode.setUserId(user.getUserId());
        loginCode.setAppId(externalAuthRequest.getAppId());
        loginCode.setCodeHash(sha256(rawLoginCode));
        loginCode.setExpiresAt(LocalDateTime.now().plusMinutes(LOGIN_CODE_MINUTES));
        loginCode.setReturnTo(externalAuthRequest.getReturnTo());
        loginCode.setStateNonce(externalAuthRequest.getState());
        loginCode.setRequestedUrl(externalAuthRequest.getRequestedUrl());
        authLoginCodeDao.save(loginCode);

        return buildExternalRedirectUrl(
                externalAuthRequest.getReturnTo(),
                rawLoginCode,
                externalAuthRequest.getState());
    }

    public Optional<ExternalAuthRequest> parseExternalAuthRequest(HttpServletRequest request) {
        String appCode = trimToNull(request.getParameter(PARAM_APP_CODE));
        String returnTo = trimToNull(request.getParameter(PARAM_RETURN_TO));
        String state = trimToNull(request.getParameter(PARAM_STATE));
        String requestedUrl = trimToNull(request.getParameter(PARAM_REQUESTED_URL));

        if (appCode == null && returnTo == null && state == null && requestedUrl == null) {
            return Optional.empty();
        }

        AppRegistry appRegistry = appRegistryDao.findByAppCode(required(appCode, "app_code"))
                .orElseThrow(() -> new IllegalArgumentException("app_code is not registered."));

        return Optional.of(validateExternalAuthRequest(
                appRegistry,
                required(returnTo, "return_to"),
                required(state, "state"),
                required(requestedUrl, "requested_url")));
    }

    private ExternalAuthRequest validateExternalAuthRequest(AppRegistry appRegistry, String returnTo, String state,
            String requestedUrl) {
        if (!Boolean.TRUE.equals(appRegistry.getEnabled()) || Boolean.TRUE.equals(appRegistry.getKillSwitch())) {
            throw new IllegalArgumentException("The selected application is not available for login.");
        }

        String appCode = trimToMax(required(trimToNull(appRegistry.getAppCode()), "app_code"), MAX_APP_CODE_LENGTH);
        String normalizedReturnTo = validateAbsoluteHttpUrl(required(returnTo, "return_to"), "return_to");
        String normalizedRequestedUrl = validateAbsoluteHttpUrl(required(requestedUrl, "requested_url"),
                "requested_url");
        String normalizedState = required(state, "state");
        if (normalizedState.length() < MIN_STATE_LENGTH || normalizedState.length() > MAX_STATE_LENGTH) {
            throw new IllegalArgumentException(
                    "state must be between " + MIN_STATE_LENGTH + " and " + MAX_STATE_LENGTH + " characters.");
        }

        List<AppRedirectAllowlist> allowlistEntries = appRedirectAllowlistDao
                .findEnabledByAppId(appRegistry.getAppId());
        boolean allowed = allowlistEntries.stream()
                .map(AppRedirectAllowlist::getBaseUrl)
                .map(this::trimToNull)
                .filter(baseUrl -> baseUrl != null)
                .anyMatch(baseUrl -> matchesAllowedBase(baseUrl, normalizedReturnTo));
        if (!allowed) {
            throw new IllegalArgumentException("return_to is not in the configured allowlist for this app_code.");
        }

        return new ExternalAuthRequest(appRegistry.getAppId(), appCode, normalizedReturnTo, normalizedState,
                normalizedRequestedUrl);
    }

    public Optional<User> findAuthenticatedUser(HttpServletRequest request) {
        return extractSessionToken(request)
                .flatMap(token -> sessionDao.findValidByTokenHash(sha256(token)))
                .flatMap(session -> userDao.findById(session.getUserId()));
    }

    public Optional<User> findAuthenticatedAdminUser(HttpServletRequest request) {
        return findAuthenticatedUser(request)
                .filter(this::isAdminUser);
    }

    public boolean isAdminUser(User user) {
        if (user == null) {
            return false;
        }

        String email = user.getEmailNormalized();
        if (email == null || email.isBlank()) {
            email = user.getEmail();
        }
        if (email == null || email.isBlank()) {
            return false;
        }

        return email.trim().toLowerCase().endsWith(ADMIN_EMAIL_DOMAIN);
    }

    public void logout(HttpServletRequest request) {
        extractSessionToken(request)
                .ifPresent(token -> sessionDao.revokeByTokenHash(sha256(token)));
    }

    public Cookie buildSessionCookie(String rawSessionToken, HttpServletRequest request) {
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, rawSessionToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath(resolveCookiePath(request));
        cookie.setMaxAge(SESSION_DAYS * 24 * 60 * 60);
        return cookie;
    }

    public Cookie buildClearedSessionCookie(HttpServletRequest request) {
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath(resolveCookiePath(request));
        cookie.setMaxAge(0);
        return cookie;
    }

    private Optional<String> extractSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }

        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return Optional.of(value);
                }
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private String buildMagicLinkUrl(String rawToken) {
        String baseUrl = hubSettingDao.findActive()
                .or(() -> hubSettingDao.findFirst())
                .map(HubSetting::getExternalBaseUrl)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .orElse("http://localhost:8080/hub");

        if (baseUrl.endsWith("/")) {
            return baseUrl + "magic-link?token=" + rawToken;
        }
        return baseUrl + "/magic-link?token=" + rawToken;
    }

    private String resolveCookiePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isBlank()) {
            return "/";
        }
        return contextPath;
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

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash token.", ex);
        }
    }

    private String trimToMax(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String required(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }

    private String validateAbsoluteHttpUrl(String value, String label) {
        String normalized = trimToMax(value, MAX_URL_LENGTH);
        if (!value.equals(normalized)) {
            throw new IllegalArgumentException(label + " exceeds maximum length of " + MAX_URL_LENGTH + " characters.");
        }
        URI uri = parseUri(normalized, label);
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException(label + " must be an absolute URL.");
        }
        String scheme = uri.getScheme();
        if (scheme == null ||
                !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException(label + " must use http or https.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(label + " must include a host.");
        }
        return uri.toString();
    }

    private boolean matchesAllowedBase(String baseUrl, String returnToUrl) {
        URI base = parseUri(baseUrl, "allowlist base");
        URI target = parseUri(returnToUrl, "return_to");

        if (!equalsIgnoreCase(base.getScheme(), target.getScheme())) {
            return false;
        }
        if (!equalsIgnoreCase(base.getHost(), target.getHost())) {
            return false;
        }
        if (normalizePort(base) != normalizePort(target)) {
            return false;
        }

        String basePath = normalizePath(base.getPath());
        String targetPath = normalizePath(target.getPath());
        if ("/".equals(basePath)) {
            return true;
        }
        return targetPath.equals(basePath) || targetPath.startsWith(basePath + "/");
    }

    private URI parseUri(String raw, String label) {
        try {
            return new URI(raw);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(label + " is not a valid URL.");
        }
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.equalsIgnoreCase(right);
    }

    private int normalizePort(URI uri) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        if (!path.startsWith("/")) {
            return "/" + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String buildExternalRedirectUrl(String returnTo, String code, String state) {
        String encodedCode = URLEncoder.encode(code, java.nio.charset.StandardCharsets.UTF_8);
        String encodedState = URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8);

        String separator = returnTo.contains("?") ? "&" : "?";
        return returnTo + separator + "code=" + encodedCode + "&state=" + encodedState;
    }

    private ExternalAuthRequest resolveExternalAuthRequestForMagicLink(MagicLink magicLink) {
        String returnTo = trimToNull(magicLink.getReturnTo());
        String state = trimToNull(magicLink.getStateNonce());
        String requestedUrl = trimToNull(magicLink.getRequestedUrl());

        if (returnTo == null && state == null && requestedUrl == null && magicLink.getAppId() == null) {
            return null;
        }

        if (magicLink.getAppId() != null) {
            return validateExternalAuthRequest(
                    appRegistryDao.findById(magicLink.getAppId())
                            .orElseThrow(() -> new IllegalStateException("App for login request was not found.")),
                    required(returnTo, "return_to"),
                    required(state, "state"),
                    required(requestedUrl, "requested_url"));
        }

        if (returnTo == null || state == null || requestedUrl == null) {
            return null;
        }

        Optional<AppRegistry> resolvedApp = findEnabledAppByReturnTo(returnTo);
        if (resolvedApp.isEmpty()) {
            return null;
        }

        return validateExternalAuthRequest(resolvedApp.get(), returnTo, state, requestedUrl);
    }

    private Optional<AppRegistry> findEnabledAppByReturnTo(String returnTo) {
        List<AppRegistry> candidateApps = appRegistryDao.findAllOrdered().stream()
                .filter(app -> app.getAppId() != null)
                .filter(app -> Boolean.TRUE.equals(app.getEnabled()))
                .filter(app -> !Boolean.TRUE.equals(app.getKillSwitch()))
                .toList();

        for (AppRegistry app : candidateApps) {
            List<AppRedirectAllowlist> allowlistEntries = appRedirectAllowlistDao.findEnabledByAppId(app.getAppId());
            boolean matches = allowlistEntries.stream()
                    .map(AppRedirectAllowlist::getBaseUrl)
                    .map(this::trimToNull)
                    .filter(baseUrl -> baseUrl != null)
                    .anyMatch(baseUrl -> matchesAllowedBase(baseUrl, returnTo));
            if (matches) {
                return Optional.of(app);
            }
        }
        return Optional.empty();
    }

    public static class AuthenticatedSession {
        private final User user;
        private final String rawSessionToken;
        private final String externalRedirectUrl;

        public AuthenticatedSession(User user, String rawSessionToken, String externalRedirectUrl) {
            this.user = user;
            this.rawSessionToken = rawSessionToken;
            this.externalRedirectUrl = externalRedirectUrl;
        }

        public User getUser() {
            return user;
        }

        public String getRawSessionToken() {
            return rawSessionToken;
        }

        public Optional<String> getExternalRedirectUrl() {
            return Optional.ofNullable(externalRedirectUrl);
        }
    }

    public static class ExternalAuthRequest {
        private final Long appId;
        private final String appCode;
        private final String returnTo;
        private final String state;
        private final String requestedUrl;

        public ExternalAuthRequest(Long appId, String appCode, String returnTo, String state, String requestedUrl) {
            this.appId = appId;
            this.appCode = appCode;
            this.returnTo = returnTo;
            this.state = state;
            this.requestedUrl = requestedUrl;
        }

        public Long getAppId() {
            return appId;
        }

        public String getAppCode() {
            return appCode;
        }

        public String getReturnTo() {
            return returnTo;
        }

        public String getState() {
            return state;
        }

        public String getRequestedUrl() {
            return requestedUrl;
        }
    }
}
