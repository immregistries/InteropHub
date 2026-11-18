package org.airahub.interophub.service;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Section 2 error bodies. Every body carries {@code help}, a link to the error's entry on the
 * public author help page ({@link GuideHelp}). The invalid-token body is the same for every cause.
 */
public final class GuideApiErrors {
    public static final String AUTHENTICATE_INVALID = "Bearer realm=\"interophub\", error=\"invalid_token\"";
    static final String INVALID_TOKEN_MESSAGE = "The access token is missing, invalid, expired, or revoked.";

    private GuideApiErrors() {
    }

    /** The one {@code 401 invalid_token} body, whatever the cause. */
    public static String invalidTokenBody() {
        return "{\"error\":\"invalid_token\",\"message\":\"" + escapeJson(INVALID_TOKEN_MESSAGE) + "\""
                + help("invalid_token") + "}";
    }

    public static void invalidToken(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", AUTHENTICATE_INVALID);
        write(response, invalidTokenBody());
    }

    public static void insufficientScope(HttpServletResponse response, String requiredScope) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setHeader("WWW-Authenticate",
                "Bearer realm=\"interophub\", error=\"insufficient_scope\", scope=\"" + escapeHeader(requiredScope) + "\"");
        write(response, "{\"error\":\"insufficient_scope\",\"requiredScope\":\"" + escapeJson(requiredScope)
                + "\",\"message\":\"" + escapeJson(insufficientScopeMessage(requiredScope)) + "\""
                + help("insufficient_scope") + "}");
    }

    static String insufficientScopeMessage(String requiredScope) {
        if (requiredScope == null || requiredScope.isBlank()) {
            return "The access token does not include the required scope.";
        }
        return "The access token does not include the " + requiredScope + " scope, which this request needs."
                + " Ask the author to create a personal access token with " + requiredScope
                + " on their InteropHub account page.";
    }

    public static void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        write(response, "{\"error\":\"forbidden\",\"message\":\"" + escapeJson(message) + "\"" + help("forbidden") + "}");
    }

    public static void invalidRequest(HttpServletResponse response, String message) throws IOException {
        invalidRequest(response, HttpServletResponse.SC_BAD_REQUEST, message);
    }

    public static void invalidRequest(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        write(response, "{\"error\":\"invalid_request\",\"message\":\"" + escapeJson(message) + "\""
                + help("invalid_request") + "}");
    }

    public static void serverError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        write(response, "{\"error\":\"server_error\",\"message\":\"Something went wrong.\"" + help("server_error") + "}");
    }

    public static void write(HttpServletResponse response, GuideApiException ex) throws IOException {
        response.setStatus(ex.getStatus());
        StringBuilder body = new StringBuilder();
        body.append("{\"error\":\"").append(escapeJson(ex.getError())).append("\"");
        if (ex.getField() != null) {
            body.append(",\"field\":\"").append(escapeJson(ex.getField())).append("\"");
        }
        body.append(",\"message\":\"").append(escapeJson(ex.getMessage())).append("\"");
        if (ex.getCurrentJson() != null) {
            body.append(",\"current\":").append(ex.getCurrentJson());
        }
        body.append(help(ex.getError()));
        body.append("}");
        write(response, body.toString());
    }

    static void write(HttpServletResponse response, String body) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
    }

    private static String help(String errorCode) {
        return ",\"help\":\"" + escapeJson(GuideHelp.forError(errorCode)) + "\"";
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String escapeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "");
    }
}
