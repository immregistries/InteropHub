package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;

final class CsrfTokenSupport {

    private static final String SESSION_KEY = CsrfTokenSupport.class.getName() + ".token";

    private CsrfTokenSupport() {
    }

    static String getOrCreateToken(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof String token && !token.isBlank()) {
            return token;
        }
        String token = UUID.randomUUID().toString();
        session.setAttribute(SESSION_KEY, token);
        return token;
    }

    static boolean isValid(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_KEY);
        if (!(expected instanceof String expectedToken) || expectedToken.isBlank()) {
            return false;
        }
        String provided = trimToNull(request.getHeader("X-CSRF-Token"));
        if (provided == null) {
            provided = trimToNull(request.getParameter("csrfToken"));
        }
        return expectedToken.equals(provided);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}