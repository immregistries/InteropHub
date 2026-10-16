package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.airahub.interophub.service.PublicFormGuard.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicFormGuardTest {

    private static final String FORM = "topics:DD26";

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private PublicFormGuard guard;

    @BeforeEach
    void setUp() {
        guard = new PublicFormGuard(now::get);
    }

    @Test
    void allowsNormalSubmitAfterRender() {
        HttpSession session = newSession();
        guard.markRendered(session, FORM);
        now.addAndGet(5_000L);
        assertEquals(Verdict.OK, guard.check(request(session, "10.0.0.1", null), FORM, "a@example.org"));
    }

    @Test
    void rejectsFilledHoneypot() {
        HttpSession session = newSession();
        guard.markRendered(session, FORM);
        now.addAndGet(5_000L);
        assertEquals(Verdict.HONEYPOT,
                guard.check(request(session, "10.0.0.1", "http://spam"), FORM, "a@example.org"));
    }

    @Test
    void rejectsSubmitWithoutRenderThenAllowsRetry() {
        HttpSession session = newSession();
        assertEquals(Verdict.TOO_FAST, guard.check(request(session, "10.0.0.1", null), FORM, "a@example.org"));
        now.addAndGet(PublicFormGuard.MIN_FILL_MILLIS);
        assertEquals(Verdict.OK, guard.check(request(session, "10.0.0.1", null), FORM, "a@example.org"));
    }

    @Test
    void rejectsSubmitTooSoonAfterRender() {
        HttpSession session = newSession();
        guard.markRendered(session, FORM);
        now.addAndGet(500L);
        assertEquals(Verdict.TOO_FAST, guard.check(request(session, "10.0.0.1", null), FORM, "a@example.org"));
    }

    @Test
    void blocksConcurrentBurstForSameEmail() {
        HttpSession session = newSession();
        guard.markRendered(session, FORM);
        now.addAndGet(5_000L);
        assertEquals(Verdict.OK, guard.check(request(session, "10.0.0.1", null), FORM, "bot@example.org"));
        assertEquals(Verdict.RATE_LIMITED,
                guard.check(request(session, "10.0.0.1", null), FORM, "bot@example.org"));
        now.addAndGet(PublicFormGuard.MIN_GAP_PER_EMAIL_MILLIS);
        assertEquals(Verdict.OK, guard.check(request(session, "10.0.0.1", null), FORM, "bot@example.org"));
    }

    @Test
    void capsSubmissionsPerEmailWithinWindow() {
        HttpSession session = newSession();
        guard.markRendered(session, FORM);
        for (int i = 0; i < PublicFormGuard.MAX_PER_EMAIL; i++) {
            now.addAndGet(PublicFormGuard.MIN_GAP_PER_EMAIL_MILLIS);
            assertEquals(Verdict.OK, guard.check(request(session, "10.0.0.1", null), FORM, "a@example.org"));
        }
        now.addAndGet(PublicFormGuard.MIN_GAP_PER_EMAIL_MILLIS);
        assertEquals(Verdict.RATE_LIMITED,
                guard.check(request(session, "10.0.0.1", null), FORM, "a@example.org"));
        now.addAndGet(PublicFormGuard.WINDOW_MILLIS);
        assertEquals(Verdict.OK, guard.check(request(session, "10.0.0.1", null), FORM, "a@example.org"));
    }

    @Test
    void sharedConferenceIpAllowsManyDistinctPeople() {
        HttpSession session = newSession();
        guard.markRendered(session, FORM);
        now.addAndGet(5_000L);
        for (int i = 0; i < PublicFormGuard.MAX_PER_IP; i++) {
            assertEquals(Verdict.OK,
                    guard.check(request(session, "10.0.0.1", null), FORM, "person" + i + "@example.org"));
        }
        assertEquals(Verdict.RATE_LIMITED,
                guard.check(request(session, "10.0.0.1", null), FORM, "one-too-many@example.org"));
        assertEquals(Verdict.OK,
                guard.check(request(session, "10.0.0.2", null), FORM, "elsewhere@example.org"));
    }

    private static HttpSession newSession() {
        Map<String, Object> attributes = new HashMap<>();
        return (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
                new Class<?>[] { HttpSession.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getAttribute":
                            return attributes.get(args[0]);
                        case "setAttribute":
                            attributes.put((String) args[0], args[1]);
                            return null;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static HttpServletRequest request(HttpSession session, String ip, String honeypot) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRemoteAddr":
                            return ip;
                        case "getParameter":
                            return PublicFormGuard.HONEYPOT_FIELD.equals(args[0]) ? honeypot : null;
                        case "getSession":
                            return session;
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                });
    }
}
