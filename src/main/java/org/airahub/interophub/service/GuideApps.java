package org.airahub.interophub.service;

/**
 * The two {@code app_registry} rows that guide sign-ins and personal access tokens use.
 * The bearer filter accepts tokens of these apps only.
 */
public final class GuideApps {
    public static final String TOPIC_REFERENCE = "topic-reference";
    public static final String INTEROPHUB_API = "interophub-api";

    public static final String GUIDE_PREFIX = "ihg_";
    public static final String PERSONAL_PREFIX = "ihp_";

    public static final String KIND_GUIDE = "guide";
    public static final String KIND_PERSONAL = "personal";

    /** 32 random bytes, base64url without padding. */
    public static final int RAW_TOKEN_BODY_LENGTH = 43;

    private GuideApps() {
    }

    /**
     * Guide tokens are {@code ihg_} on {@code topic-reference}. Personal access tokens are
     * {@code ihp_} on {@code interophub-api}. A prefix that does not match the app is refused.
     */
    public static String kind(String appCode, String rawToken) {
        if (appCode == null || rawToken == null) {
            return null;
        }
        if (TOPIC_REFERENCE.equals(appCode) && hasBody(rawToken, GUIDE_PREFIX)) {
            return KIND_GUIDE;
        }
        if (INTEROPHUB_API.equals(appCode) && hasBody(rawToken, PERSONAL_PREFIX)) {
            return KIND_PERSONAL;
        }
        return null;
    }

    private static boolean hasBody(String rawToken, String prefix) {
        if (!rawToken.startsWith(prefix) || rawToken.length() != prefix.length() + RAW_TOKEN_BODY_LENGTH) {
            return false;
        }
        for (int i = prefix.length(); i < rawToken.length(); i++) {
            char ch = rawToken.charAt(i);
            boolean ok = (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-'
                    || ch == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
