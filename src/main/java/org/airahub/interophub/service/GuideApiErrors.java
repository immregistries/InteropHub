package org.airahub.interophub.service;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

/** Section 2 error bodies. The invalid-token body is one constant for every cause. */
public final class GuideApiErrors {
    public static final String INVALID_TOKEN_BODY = "{\"error\":\"invalid_token\",\"message\":\"The access token is invalid.\"}";
    public static final String AUTHENTICATE_INVALID = "Bearer realm=\"interophub\", error=\"invalid_token\"";

    private GuideApiErrors() {
    }

    public static void invalidToken(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", AUTHENTICATE_INVALID);
        write(response, INVALID_TOKEN_BODY);
    }

    public static void insufficientScope(HttpServletResponse response, String requiredScope) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setHeader("WWW-Authenticate",
                "Bearer realm=\"interophub\", error=\"insufficient_scope\", scope=\"" + escapeHeader(requiredScope) + "\"");
        write(response, "{\"error\":\"insufficient_scope\",\"requiredScope\":\"" + escapeJson(requiredScope)
                + "\",\"message\":\"The access token does not include the required scope.\"}");
    }

    public static void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        write(response, "{\"error\":\"forbidden\",\"message\":\"" + escapeJson(message) + "\"}");
    }

    public static void invalidRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        write(response, "{\"error\":\"invalid_request\",\"message\":\"" + escapeJson(message) + "\"}");
    }

    public static void serverError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        write(response, "{\"error\":\"server_error\",\"message\":\"Something went wrong.\"}");
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
        body.append("}");
        write(response, body.toString());
    }

    static void write(HttpServletResponse response, String body) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
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
