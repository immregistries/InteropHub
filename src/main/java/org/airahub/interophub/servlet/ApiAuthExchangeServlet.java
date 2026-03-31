package org.airahub.interophub.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.airahub.interophub.dao.AppLoginEventDao;
import org.airahub.interophub.dao.AppRegistryDao;
import org.airahub.interophub.dao.AuthLoginCodeDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.AppLoginEvent;
import org.airahub.interophub.model.AppRegistry;
import org.airahub.interophub.model.AuthLoginCode;
import org.airahub.interophub.model.User;

public class ApiAuthExchangeServlet extends HttpServlet {
    private static final long RESPONSE_EXPIRES_IN_SECONDS = 3600L;

    private final AppLoginEventDao appLoginEventDao;
    private final AppRegistryDao appRegistryDao;
    private final AuthLoginCodeDao authLoginCodeDao;
    private final UserDao userDao;

    public ApiAuthExchangeServlet() {
        this.appLoginEventDao = new AppLoginEventDao();
        this.appRegistryDao = new AppRegistryDao();
        this.authLoginCodeDao = new AuthLoginCodeDao();
        this.userDao = new UserDao();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        ExchangeRequest exchangeRequest;
        try {
            exchangeRequest = parseRequest(request);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, ex.getMessage());
            return;
        }

        AppRegistry app = appRegistryDao.findByAppCode(exchangeRequest.appCode)
                .orElse(null);
        if (app == null || app.getAppId() == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "Invalid app_code or code.");
            return;
        }

        byte[] codeHash = sha256(exchangeRequest.code);
        AuthLoginCode loginCode = authLoginCodeDao.findValidByCodeHash(codeHash)
                .orElse(null);
        if (loginCode == null || !app.getAppId().equals(loginCode.getAppId())) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "Invalid app_code or code.");
            return;
        }

        boolean consumed = authLoginCodeDao.markConsumedIfValid(loginCode.getLoginCodeId());
        if (!consumed) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(response, "Code has already been used or expired.");
            return;
        }

        User user = userDao.findById(loginCode.getUserId()).orElse(null);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            writeError(response, "User not found for code.");
            return;
        }

        String issuedAtIso = formatUtc(loginCode.getIssuedAt() == null ? LocalDateTime.now() : loginCode.getIssuedAt());

        AppLoginEvent loginEvent = new AppLoginEvent();
        loginEvent.setUserId(user.getUserId());
        loginEvent.setAppId(app.getAppId());
        loginEvent.setLoginCodeId(loginCode.getLoginCodeId());
        loginEvent.setServerIp(trimToNull(request.getRemoteAddr()));
        loginEvent.setUserIp(exchangeRequest.userIp);
        appLoginEventDao.log(loginEvent);

        // Update user's last login timestamp
        user.setLastLoginAt(LocalDateTime.now());
        userDao.saveOrUpdate(user);

        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("{"
                + "\"hub_user_id\":" + user.getUserId() + ","
                + "\"email\":\"" + escapeJson(orEmpty(user.getEmail())) + "\","
                + "\"name\":\"" + escapeJson(orEmpty(user.getDisplayName())) + "\","
                + "\"organization\":\"" + escapeJson(orEmpty(user.getOrganization())) + "\","
                + "\"title\":\"" + escapeJson(orEmpty(user.getRoleTitle())) + "\","
                + "\"requested_url\":\"" + escapeJson(orEmpty(loginCode.getRequestedUrl())) + "\","
                + "\"issued_at\":\"" + escapeJson(issuedAtIso) + "\","
                + "\"expires_in_seconds\":" + RESPONSE_EXPIRES_IN_SECONDS
                + "}");
    }

    private ExchangeRequest parseRequest(HttpServletRequest request) throws IOException {
        String body = request.getReader().lines().collect(Collectors.joining("\n"));
        String appCode = extractJsonString(body, "app_code");
        String code = extractJsonString(body, "code");
        String userIp = extractJsonString(body, "user_ip");

        if (appCode == null || appCode.isBlank()) {
            throw new IllegalArgumentException("app_code is required.");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required.");
        }

        // Validate user_ip if provided: allow only characters valid in IPv4/IPv6
        // addresses.
        // Reject anything that doesn't look like an IP to avoid storing garbage.
        if (userIp != null && !userIp.isBlank()) {
            String trimmedIp = userIp.trim();
            if (!trimmedIp.matches("[0-9a-fA-F.:]+") || trimmedIp.length() > 45) {
                throw new IllegalArgumentException("user_ip must be a valid IPv4 or IPv6 address.");
            }
            userIp = trimmedIp;
        } else {
            userIp = null;
        }

        return new ExchangeRequest(appCode.trim(), code.trim(), userIp);
    }

    private String extractJsonString(String body, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\\\"])*)\"");
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        return unescapeJsonString(matcher.group(1));
    }

    private String unescapeJsonString(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case '\\':
                    case '"':
                    case '/':
                        out.append(next);
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        if (i + 4 < value.length()) {
                            String hex = value.substring(i + 1, i + 5);
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                        break;
                    default:
                        out.append(next);
                        break;
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash code.", ex);
        }
    }

    private void writeError(HttpServletResponse response, String message) throws IOException {
        response.getWriter().write("{\"error\":\"" + escapeJson(orEmpty(message)) + "\"}");
    }

    private String formatUtc(LocalDateTime value) {
        return value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static class ExchangeRequest {
        private final String appCode;
        private final String code;
        private final String userIp;

        private ExchangeRequest(String appCode, String code, String userIp) {
            this.appCode = appCode;
            this.code = code;
            this.userIp = userIp;
        }
    }
}
