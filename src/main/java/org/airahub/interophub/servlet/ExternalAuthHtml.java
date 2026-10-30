package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import org.airahub.interophub.service.AuthFlowService;

/** Hidden fields and query parameters that carry an external sign-in through registration. */
public final class ExternalAuthHtml {
    private ExternalAuthHtml() {
    }

    public static void writeHiddenInputs(PrintWriter out, AuthFlowService.ExternalAuthRequest request) {
        if (request == null) {
            return;
        }
        hidden(out, AuthFlowService.PARAM_APP_CODE, request.getAppCode());
        hidden(out, AuthFlowService.PARAM_RETURN_TO, request.getReturnTo());
        hidden(out, AuthFlowService.PARAM_STATE, request.getState());
        hidden(out, AuthFlowService.PARAM_REQUESTED_URL, request.getRequestedUrl());
        if (request.getCodeChallenge() != null) {
            hidden(out, AuthFlowService.PARAM_CODE_CHALLENGE, request.getCodeChallenge());
            hidden(out, AuthFlowService.PARAM_CODE_CHALLENGE_METHOD, request.getCodeChallengeMethod());
            hidden(out, AuthFlowService.PARAM_RESPONSE_MODE, "fragment");
        }
    }

    public static void appendRequestParams(StringBuilder url, HttpServletRequest request) {
        append(url, AuthFlowService.PARAM_APP_CODE, request);
        append(url, AuthFlowService.PARAM_RETURN_TO, request);
        append(url, AuthFlowService.PARAM_STATE, request);
        append(url, AuthFlowService.PARAM_REQUESTED_URL, request);
        append(url, AuthFlowService.PARAM_CODE_CHALLENGE, request);
        append(url, AuthFlowService.PARAM_CODE_CHALLENGE_METHOD, request);
        append(url, AuthFlowService.PARAM_RESPONSE_MODE, request);
    }

    private static void append(StringBuilder url, String key, HttpServletRequest request) {
        String value = request.getParameter(key);
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        url.append('&')
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(trimmed, StandardCharsets.UTF_8));
    }

    private static void hidden(PrintWriter out, String name, String value) {
        out.println("                <input type=\"hidden\" name=\"" + name + "\" value=\"" + escapeHtml(value) + "\" />");
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
