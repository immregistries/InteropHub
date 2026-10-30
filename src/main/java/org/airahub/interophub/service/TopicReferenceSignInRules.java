package org.airahub.interophub.service;

import java.util.regex.Pattern;

/**
 * Extra rules for the shared {@code topic-reference} public client. Other apps do not use this.
 */
public final class TopicReferenceSignInRules {
    private static final Pattern CHALLENGE = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private TopicReferenceSignInRules() {
    }

    public static void check(String returnTo, String codeChallenge, String codeChallengeMethod, String responseMode) {
        if (returnTo != null && returnTo.indexOf('#') >= 0) {
            throw new IllegalArgumentException("return_to must not include a fragment.");
        }
        if (codeChallenge == null || codeChallenge.isBlank()) {
            throw new IllegalArgumentException("code_challenge is required.");
        }
        if (!"S256".equals(codeChallengeMethod)) {
            throw new IllegalArgumentException("code_challenge_method must be S256.");
        }
        if (!CHALLENGE.matcher(codeChallenge).matches()) {
            throw new IllegalArgumentException("code_challenge must be a 43-character S256 value.");
        }
        if (!"fragment".equals(responseMode)) {
            throw new IllegalArgumentException("response_mode must be fragment.");
        }
    }
}
