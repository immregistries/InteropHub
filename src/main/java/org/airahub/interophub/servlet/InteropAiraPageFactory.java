package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.PublicUrlService;
import org.immregistries.aira.web.AiraAccountConfig;
import org.immregistries.aira.web.AiraDefaults;
import org.immregistries.aira.web.AiraEnvironmentConfig;
import org.immregistries.aira.web.AiraLogo;
import org.immregistries.aira.web.AiraPage;
import org.immregistries.aira.web.AiraSearchConfig;

final class InteropAiraPageFactory {
    private static final String APPLICATION_NAME = "InteropHub";
    private static final String HOME_HREF = "/home";
    private static final String ACCOUNT_HREF = "/workspace";
    private static final String SEARCH_ACTION = "/es/topics";
    private static final String SEARCH_PARAMETER = "q";
    private static final String SEARCH_LABEL = "Search InteropHub";
    private static final String SEARCH_PLACEHOLDER = "Search topics or meetings";
    private static final String LOCAL_ENV_LABEL = "Local";
    private static final String LOCAL_ENV_DESCRIPTION = "Running in localhost mode";
    private static final String SIGNED_IN_USER_FALLBACK = "Signed in user";

    private static final AuthFlowService AUTH_FLOW_SERVICE = new AuthFlowService();
    private static final PublicUrlService PUBLIC_URL_SERVICE = new PublicUrlService();

    private InteropAiraPageFactory() {
    }

    static AiraPage.Builder base(HttpServletRequest request, String documentTitle) {
        Optional<User> authenticatedUser = AUTH_FLOW_SERVICE.findAuthenticatedUser(request);
        return base(request, documentTitle, authenticatedUser, PUBLIC_URL_SERVICE.isLocalhostMode());
    }

    static AiraPage.Builder base(HttpServletRequest request, String documentTitle, Optional<User> authenticatedUser,
            boolean localhostMode) {
        AiraPage.Builder builder = AiraPage.builder()
                .applicationName(APPLICATION_NAME)
                .applicationVersion(InteropVersionResolver.getApplicationVersion())
                .documentTitle(documentTitle)
                .contextPath(request.getContextPath())
                .identityHref(HOME_HREF)
                .logo(new AiraLogo(AiraDefaults.DEFAULT_LOGO_PATH, AiraDefaults.DEFAULT_LOGO_ALT_TEXT))
                .account(buildAccountConfig(authenticatedUser == null ? Optional.empty() : authenticatedUser))
                .search(new AiraSearchConfig(SEARCH_ACTION, SEARCH_PARAMETER, SEARCH_LABEL, SEARCH_PLACEHOLDER));

        if (localhostMode) {
            builder.environment(new AiraEnvironmentConfig(LOCAL_ENV_LABEL, LOCAL_ENV_DESCRIPTION));
        }

        return builder;
    }

    private static AiraAccountConfig buildAccountConfig(Optional<User> authenticatedUser) {
        if (authenticatedUser.isPresent()) {
            return new AiraAccountConfig(resolveUserDisplayName(authenticatedUser.get()), "Account", ACCOUNT_HREF);
        }
        return new AiraAccountConfig("", "Sign in", HOME_HREF);
    }

    private static String resolveUserDisplayName(User user) {
        if (user == null) {
            return SIGNED_IN_USER_FALLBACK;
        }

        String firstName = trimToNull(user.getFirstName());
        String lastName = trimToNull(user.getLastName());
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }

        String email = trimToNull(user.getEmail());
        if (email != null) {
            return email;
        }

        return SIGNED_IN_USER_FALLBACK;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
