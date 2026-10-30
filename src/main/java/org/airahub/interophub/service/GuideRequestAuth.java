package org.airahub.interophub.service;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;

/**
 * Shared guards for servlets behind the bearer filter. Scope checks live here, not in the filter.
 */
public final class GuideRequestAuth {
    public static final String USER = "interophub.guide.user";
    public static final String TOKEN_ID = "interophub.guide.tokenId";
    public static final String TOKEN_KIND = "interophub.guide.tokenKind";
    public static final String SCOPES = "interophub.guide.scopes";

    private GuideRequestAuth() {
    }

    public static User requireUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object value = request.getAttribute(USER);
        if (!(value instanceof User user) || user.getUserId() == null) {
            GuideApiErrors.invalidToken(response);
            return null;
        }
        return user;
    }

    /**
     * Accepts the token when it has any one of {@code anyOf}. {@code topics:write} satisfies
     * {@code topics:read}. On failure the response is {@code 403 insufficient_scope} and the
     * {@code requiredScope} value is the first listed scope.
     */
    public static boolean requireScope(HttpServletRequest request, HttpServletResponse response, String... anyOf)
            throws IOException {
        if (requireUser(request, response) == null) {
            return false;
        }
        if (anyOf == null || anyOf.length == 0) {
            GuideApiErrors.insufficientScope(response, "");
            return false;
        }
        Set<String> have = scopes(request);
        for (String required : anyOf) {
            if (GuideScopes.satisfied(have, required)) {
                return true;
            }
        }
        GuideApiErrors.insufficientScope(response, anyOf[0]);
        return false;
    }

    @SuppressWarnings("unchecked")
    public static Set<String> scopes(HttpServletRequest request) {
        Object value = request.getAttribute(SCOPES);
        if (value instanceof Set<?> set) {
            return (Set<String>) set;
        }
        return Set.of();
    }
}
