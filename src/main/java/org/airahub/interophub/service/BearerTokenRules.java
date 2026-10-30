package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.airahub.interophub.model.User;

/**
 * Bearer-token rules shared by the filter. Every failure is a rejection; the filter writes
 * one body for all of them.
 */
public final class BearerTokenRules {
    private BearerTokenRules() {
    }

    /**
     * The raw token from exactly one {@code Authorization} header, or null when the header is
     * missing, repeated, or not a single bearer token. The scheme match ignores case.
     */
    public static String singleBearerToken(Collection<String> authorizationHeaders) {
        if (authorizationHeaders == null || authorizationHeaders.size() != 1) {
            return null;
        }
        String header = authorizationHeaders.iterator().next();
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        String scheme = "Bearer ";
        if (trimmed.length() < scheme.length() || !trimmed.regionMatches(true, 0, scheme, 0, scheme.length())) {
            return null;
        }
        String token = trimmed.substring(scheme.length()).trim();
        if (token.isEmpty() || token.indexOf(' ') >= 0) {
            return null;
        }
        return token;
    }

    public static List<String> headerList(Iterable<String> headers) {
        List<String> values = new ArrayList<>();
        if (headers == null) {
            return values;
        }
        for (String header : headers) {
            values.add(header);
        }
        return values;
    }

    /**
     * True only for an active user and a token whose prefix matches {@code topic-reference}
     * or {@code interophub-api}. Revoked, expired, unknown, and other apps are all false.
     */
    public static boolean accept(String rawToken, String appCode, User.UserStatus status, boolean revoked, boolean expired) {
        if (revoked || expired || status != User.UserStatus.ACTIVE) {
            return false;
        }
        return GuideApps.kind(appCode, rawToken) != null;
    }
}
