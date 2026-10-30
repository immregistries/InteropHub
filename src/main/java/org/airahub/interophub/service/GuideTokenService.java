package org.airahub.interophub.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.dao.AppRegistryDao;
import org.airahub.interophub.dao.AuthLoginCodeDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.dao.UserTokenDao;
import org.airahub.interophub.model.AppRegistry;
import org.airahub.interophub.model.AuthLoginCode;
import org.airahub.interophub.model.User;
import org.airahub.interophub.model.UserToken;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GuideTokenService {
    private static final String ORIGIN_MESSAGE = "This origin is not allowed to call the guide API.";
    private static final String PAT_DELETE_MESSAGE = "Only a guide sign-in token can be revoked here.";

    private final AuthLoginCodeDao authLoginCodeDao;
    private final AppRegistryDao appRegistryDao;
    private final UserDao userDao;
    private final UserTokenDao userTokenDao;
    private final GuideAllowlistOrigins allowlistOrigins;
    private final SecureRandom secureRandom;

    public GuideTokenService() {
        this(new AuthLoginCodeDao(), new AppRegistryDao(), new UserDao(), new UserTokenDao(),
                new GuideAllowlistOrigins(), new SecureRandom());
    }

    GuideTokenService(AuthLoginCodeDao authLoginCodeDao, AppRegistryDao appRegistryDao, UserDao userDao,
            UserTokenDao userTokenDao, GuideAllowlistOrigins allowlistOrigins, SecureRandom secureRandom) {
        this.authLoginCodeDao = authLoginCodeDao;
        this.appRegistryDao = appRegistryDao;
        this.userDao = userDao;
        this.userTokenDao = userTokenDao;
        this.allowlistOrigins = allowlistOrigins;
        this.secureRandom = secureRandom;
    }

    public void exchange(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String origin = trimToNull(request.getHeader("Origin"));
        Set<String> allowed = allowlistOrigins.get();
        if (GuideCors.permission(origin, allowed) == GuideCors.Permission.REFUSED) {
            GuideApiErrors.forbidden(response, ORIGIN_MESSAGE);
            return;
        }

        JSONObject json;
        try {
            String body = request.getReader().lines().collect(Collectors.joining("\n"));
            json = new JSONObject(body);
        } catch (JSONException ex) {
            GuideApiErrors.invalidRequest(response, "The request body must be JSON.");
            return;
        }

        GuideTokenExchange.Facts facts = new GuideTokenExchange.Facts();
        facts.returnTo = jsonString(json, "returnTo");
        facts.codeVerifier = jsonString(json, "codeVerifier");
        facts.origin = origin;
        facts.originAllowed = origin == null || GuideCors.permission(origin, allowed) == GuideCors.Permission.ALLOWED;

        String code = jsonString(json, "code");
        AuthLoginCode loginCode = null;
        if (code != null) {
            loginCode = authLoginCodeDao.findValidByCodeHash(TokenHash.sha256(code)).orElse(null);
        }
        if (loginCode != null) {
            facts.found = true;
            AppRegistry app = appRegistryDao.findById(loginCode.getAppId()).orElse(null);
            facts.appCode = app == null ? null : app.getAppCode();
            facts.storedReturnTo = loginCode.getReturnTo();
            facts.storedChallenge = loginCode.getCodeChallenge();
        }

        GuideTokenExchange.Verdict verdict = GuideTokenExchange.evaluate(facts);
        if (verdict.status == GuideTokenExchange.Status.ORIGIN_FORBIDDEN) {
            GuideApiErrors.forbidden(response, ORIGIN_MESSAGE);
            return;
        }
        if (verdict.status != GuideTokenExchange.Status.OK || loginCode == null) {
            GuideApiErrors.invalidRequest(response, GuideTokenExchange.INVALID_MESSAGE);
            return;
        }

        User user = userDao.findById(loginCode.getUserId()).orElse(null);
        if (user == null || user.getStatus() != User.UserStatus.ACTIVE) {
            GuideApiErrors.invalidRequest(response, GuideTokenExchange.INVALID_MESSAGE);
            return;
        }
        if (!authLoginCodeDao.markConsumedIfValid(loginCode.getLoginCodeId())) {
            GuideApiErrors.invalidRequest(response, GuideTokenExchange.INVALID_MESSAGE);
            return;
        }

        String labelOrigin = origin == null ? GuideCors.originOf(facts.returnTo) : GuideCors.originOf(origin);
        if (labelOrigin == null) {
            labelOrigin = facts.returnTo == null ? "" : facts.returnTo;
        }
        IssuedToken issued = issue(user, GuideApps.TOPIC_REFERENCE, GuideApps.GUIDE_PREFIX, GuideScopes.GUIDE_TOKEN_SCOPES,
                30, GuideScopes.guideLabel(labelOrigin));
        writeTokenResponse(response, issued, user);
    }

    public void revokeCallingToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (GuideRequestAuth.requireUser(request, response) == null) {
            return;
        }
        Object kind = request.getAttribute(GuideRequestAuth.TOKEN_KIND);
        if (!GuideApps.KIND_GUIDE.equals(kind)) {
            GuideApiErrors.forbidden(response, PAT_DELETE_MESSAGE);
            return;
        }
        Object tokenId = request.getAttribute(GuideRequestAuth.TOKEN_ID);
        if (tokenId instanceof Long id) {
            userTokenDao.revoke(id);
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    public IssuedToken createPersonalToken(User user, String label, Collection<String> scopes, int lifetimeDays) {
        String trimmed = label == null ? null : label.trim();
        if (trimmed == null || trimmed.isEmpty() || trimmed.length() > 120) {
            throw new IllegalArgumentException("Enter a label of up to 120 characters.");
        }
        if (lifetimeDays != 30 && lifetimeDays != 90 && lifetimeDays != 365) {
            throw new IllegalArgumentException("Choose a lifetime of 30, 90, or 365 days.");
        }
        String stored = GuideScopes.joinPersonal(scopes);
        return issue(user, GuideApps.INTEROPHUB_API, GuideApps.PERSONAL_PREFIX, stored, lifetimeDays, trimmed);
    }

    public List<AccountToken> listForAccount(Long userId) {
        AppRegistry guideApp = appRegistryDao.findByAppCode(GuideApps.TOPIC_REFERENCE).orElse(null);
        AppRegistry personalApp = appRegistryDao.findByAppCode(GuideApps.INTEROPHUB_API).orElse(null);
        List<Long> appIds = new ArrayList<>();
        if (guideApp != null && guideApp.getAppId() != null) {
            appIds.add(guideApp.getAppId());
        }
        if (personalApp != null && personalApp.getAppId() != null) {
            appIds.add(personalApp.getAppId());
        }
        if (appIds.isEmpty()) {
            return List.of();
        }
        List<AccountToken> rows = new ArrayList<>();
        for (UserToken token : userTokenDao.findUnrevokedForUserAndApps(userId, appIds)) {
            String kind = kindForApp(token.getAppId(), guideApp, personalApp);
            if (kind == null) {
                continue;
            }
            rows.add(new AccountToken(token.getTokenId(), token.getLabel(), kind, token.getScopes(),
                    token.getIssuedAt(), token.getExpiresAt(), token.getLastUsedAt()));
        }
        return rows;
    }

    public boolean revokeOwned(Long userId, Long tokenId) {
        if (userId == null || tokenId == null) {
            return false;
        }
        UserToken token = userTokenDao.findById(tokenId).orElse(null);
        if (token == null || !userId.equals(token.getUserId()) || token.getRevokedAt() != null) {
            return false;
        }
        AppRegistry app = appRegistryDao.findById(token.getAppId()).orElse(null);
        if (app == null) {
            return false;
        }
        String appCode = app.getAppCode();
        if (!GuideApps.TOPIC_REFERENCE.equals(appCode) && !GuideApps.INTEROPHUB_API.equals(appCode)) {
            return false;
        }
        return userTokenDao.revoke(tokenId);
    }

    private IssuedToken issue(User user, String appCode, String prefix, String scopes, int days, String label) {
        AppRegistry app = appRegistryDao.findByAppCode(appCode)
                .orElseThrow(() -> new IllegalStateException("App is not registered: " + appCode));
        String raw = TokenHash.randomToken(prefix, secureRandom);
        UserToken token = new UserToken();
        token.setUserId(user.getUserId());
        token.setAppId(app.getAppId());
        token.setTokenHash(TokenHash.sha256(raw));
        token.setLabel(label);
        token.setScopes(scopes);
        token.setExpiresAt(LocalDateTime.now().plusDays(days));
        userTokenDao.save(token);
        return new IssuedToken(raw, token.getExpiresAt(), scopes);
    }

    private static void writeTokenResponse(HttpServletResponse response, IssuedToken issued, User user) throws IOException {
        String displayName = user.getFullName();
        if (displayName == null || displayName.isBlank()) {
            displayName = "";
        }
        JSONObject body = new JSONObject();
        body.put("accessToken", issued.rawToken());
        body.put("tokenType", "Bearer");
        body.put("expiresAt", ApiTime.format(issued.expiresAt()));
        body.put("scopes", new JSONArray(GuideScopes.parse(issued.scopes()).stream().toList()));
        JSONObject userJson = new JSONObject();
        userJson.put("userId", user.getUserId());
        userJson.put("displayName", displayName);
        if (user.getOrganization() == null || user.getOrganization().isBlank()) {
            userJson.put("organization", JSONObject.NULL);
        } else {
            userJson.put("organization", user.getOrganization());
        }
        body.put("user", userJson);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body.toString());
    }

    private static String kindForApp(Long appId, AppRegistry guideApp, AppRegistry personalApp) {
        if (guideApp != null && appId.equals(guideApp.getAppId())) {
            return GuideApps.KIND_GUIDE;
        }
        if (personalApp != null && appId.equals(personalApp.getAppId())) {
            return GuideApps.KIND_PERSONAL;
        }
        return null;
    }

    private static String jsonString(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.get(key);
        if (!(value instanceof String text)) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class IssuedToken {
        private final String rawToken;
        private final LocalDateTime expiresAt;
        private final String scopes;

        public IssuedToken(String rawToken, LocalDateTime expiresAt, String scopes) {
            this.rawToken = rawToken;
            this.expiresAt = expiresAt;
            this.scopes = scopes;
        }

        public String rawToken() {
            return rawToken;
        }

        public LocalDateTime expiresAt() {
            return expiresAt;
        }

        public String scopes() {
            return scopes;
        }
    }

    public static final class AccountToken {
        private final Long tokenId;
        private final String label;
        private final String kind;
        private final String scopes;
        private final LocalDateTime issuedAt;
        private final LocalDateTime expiresAt;
        private final LocalDateTime lastUsedAt;

        public AccountToken(Long tokenId, String label, String kind, String scopes, LocalDateTime issuedAt,
                LocalDateTime expiresAt, LocalDateTime lastUsedAt) {
            this.tokenId = tokenId;
            this.label = label;
            this.kind = kind;
            this.scopes = scopes;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.lastUsedAt = lastUsedAt;
        }

        public Long getTokenId() {
            return tokenId;
        }

        public String getLabel() {
            return label;
        }

        public String getKind() {
            return kind;
        }

        public String getScopes() {
            return scopes;
        }

        public LocalDateTime getIssuedAt() {
            return issuedAt;
        }

        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }

        public LocalDateTime getLastUsedAt() {
            return lastUsedAt;
        }
    }
}
