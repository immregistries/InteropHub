package org.airahub.interophub.service;

import java.util.List;
import java.util.function.Supplier;

/**
 * The public Topic Reference author help page and its section anchors. The path and the
 * anchors are part of the API contract: error bodies, {@code GET /api/token}, the account
 * page, and the templates project all link to them.
 */
public final class GuideHelp {
    public static final String PATH = "/help/topic-reference-authors";

    public static final String WHO_CAN_AUTHOR = "who-can-author";
    public static final String CREATE_A_TOKEN = "create-a-token";
    public static final String WHERE_THE_TOKEN_GOES = "where-the-token-goes";
    public static final String WHAT_AN_AGENT_CAN_DO = "what-an-agent-can-do";
    public static final String IF_YOU_ARE_AN_AGENT = "if-you-are-an-agent";
    public static final String TROUBLESHOOTING = "troubleshooting";

    /** The section anchors, in page order. */
    public static final List<String> SECTIONS = List.of(WHO_CAN_AUTHOR, CREATE_A_TOKEN, WHERE_THE_TOKEN_GOES,
            WHAT_AN_AGENT_CAN_DO, IF_YOU_ARE_AN_AGENT, TROUBLESHOOTING);

    /**
     * Every contract Section 2 error code. Each has its own {@code #troubleshooting-<code>} entry on
     * the help page; any other code links to {@code #troubleshooting}.
     */
    public static final List<String> ERROR_CODES = List.of("invalid_token", "insufficient_scope", "forbidden",
            "not_found", "document_inactive", "comments_closed", "invalid_request", "invalid_field",
            "invalid_state", "edit_window_closed", "conflict", "duplicate", "url_not_approved", "server_error");

    private static final String DEFAULT_BASE = "http://localhost:8080/hub";

    private static volatile Supplier<String> resolver;
    private static volatile String cachedBase;

    private GuideHelp() {
    }

    /**
     * Sets where the hub base URL comes from ({@code hub_settings.external_base_url} in the running
     * application). Called once from servlet and filter {@code init}; unit tests that never call it
     * get {@code http://localhost:8080/hub}. A successful lookup is kept for the life of the process,
     * as {@link PublicUrlService#isLocalhostMode()} does; a failed one is retried on the next use.
     */
    public static void useBaseUrl(Supplier<String> baseUrl) {
        resolver = baseUrl;
        cachedBase = null;
    }

    /** Wires {@link #useBaseUrl} to {@link PublicUrlService}, unless something already did. */
    public static void useHubSettings() {
        if (resolver == null) {
            useBaseUrl(() -> new PublicUrlService().resolveExternalBaseUrl());
        }
    }

    public static String base() {
        String cached = cachedBase;
        if (cached != null) {
            return cached;
        }
        Supplier<String> source = resolver;
        if (source == null) {
            return DEFAULT_BASE;
        }
        try {
            String value = source.get();
            if (value != null && !value.isBlank()) {
                String trimmed = value.trim();
                while (trimmed.endsWith("/")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                cachedBase = trimmed;
                return trimmed;
            }
        } catch (RuntimeException ex) {
            // Fall through; the next call tries again.
        }
        return DEFAULT_BASE;
    }

    /** {@code {hub}/help/topic-reference-authors}. */
    public static String page() {
        return base() + PATH;
    }

    /** {@code {hub}/help/topic-reference-authors#<anchor>}. */
    public static String url(String anchor) {
        return page() + "#" + anchor;
    }

    /**
     * The anchor of an error code's troubleshooting entry: {@code troubleshooting-} followed by the
     * code exactly as the API writes it (for example {@code troubleshooting-invalid_token}), or
     * {@code troubleshooting} for an unknown code.
     */
    public static String troubleshootingAnchor(String errorCode) {
        if (errorCode != null && ERROR_CODES.contains(errorCode)) {
            return TROUBLESHOOTING + "-" + errorCode;
        }
        return TROUBLESHOOTING;
    }

    /** The {@code help} value of an error body. */
    public static String forError(String errorCode) {
        return url(troubleshootingAnchor(errorCode));
    }
}
