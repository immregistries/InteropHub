package org.airahub.interophub.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ExternalRedirect {
    private ExternalRedirect() {
    }

    /**
     * Query-string return is the existing external-app behavior. Fragment return is used for
     * {@code topic-reference} so the code stays out of server logs and Referer headers.
     */
    public static String build(String returnTo, String code, String state, boolean fragment) {
        String encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8);
        String encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8);
        if (fragment) {
            return returnTo + "#code=" + encodedCode + "&state=" + encodedState;
        }
        String separator = returnTo.contains("?") ? "&" : "?";
        return returnTo + separator + "code=" + encodedCode + "&state=" + encodedState;
    }
}
