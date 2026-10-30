package org.airahub.interophub.service;

import java.util.Set;
import org.airahub.interophub.dao.AppRegistryDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.dao.UserTokenDao;
import org.airahub.interophub.model.AppRegistry;
import org.airahub.interophub.model.User;
import org.airahub.interophub.model.UserToken;

/** Resolves a raw bearer token to the user it acts as. The raw token is not returned. */
public final class GuideBearerAuthenticator implements GuideTokenLookup {
    private final UserTokenDao userTokenDao;
    private final UserDao userDao;
    private final AppRegistryDao appRegistryDao;

    public GuideBearerAuthenticator() {
        this(new UserTokenDao(), new UserDao(), new AppRegistryDao());
    }

    GuideBearerAuthenticator(UserTokenDao userTokenDao, UserDao userDao, AppRegistryDao appRegistryDao) {
        this.userTokenDao = userTokenDao;
        this.userDao = userDao;
        this.appRegistryDao = appRegistryDao;
    }

    @Override
    public BearerDecision authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return BearerDecision.reject();
        }
        UserToken token = userTokenDao.findActiveByTokenHash(TokenHash.sha256(rawToken)).orElse(null);
        if (token == null || token.getUserId() == null || token.getAppId() == null || token.getTokenId() == null) {
            return BearerDecision.reject();
        }
        AppRegistry app = appRegistryDao.findById(token.getAppId()).orElse(null);
        User user = userDao.findById(token.getUserId()).orElse(null);
        String appCode = app == null ? null : app.getAppCode();
        User.UserStatus status = user == null ? null : user.getStatus();
        if (!BearerTokenRules.accept(rawToken, appCode, status, false, false)) {
            return BearerDecision.reject();
        }
        userTokenDao.touchLastUsedAt(token.getTokenId());
        Set<String> scopes = GuideScopes.parse(token.getScopes());
        return BearerDecision.accept(user, token.getTokenId(), GuideApps.kind(appCode, rawToken), scopes);
    }
}
