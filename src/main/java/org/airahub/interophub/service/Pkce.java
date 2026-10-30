package org.airahub.interophub.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** S256 PKCE: base64url(SHA-256(verifier)), no padding. */
public final class Pkce {
    private Pkce() {
    }

    public static String s256(String verifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(TokenHash.sha256(verifier));
    }

    public static boolean matches(String verifier, String challenge) {
        if (verifier == null || verifier.isEmpty() || challenge == null || challenge.isEmpty()) {
            return false;
        }
        byte[] actual = s256(verifier).getBytes(StandardCharsets.UTF_8);
        byte[] expected = challenge.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }
}
