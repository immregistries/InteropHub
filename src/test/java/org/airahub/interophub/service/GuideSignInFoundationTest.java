package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.time.LocalDateTime;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.servlet.GuideBearerTokenFilter;
import org.junit.jupiter.api.Test;

class GuideSignInFoundationTest {

    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String RETURN_TO = "http://localhost:8001/guide-signin-test.html";

    @Test
    void apiTimeUsesUtcWhenTheJvmIsNotUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Denver"));
            LocalDateTime stored = LocalDateTime.of(2026, 10, 30, 12, 0, 0);
            assertEquals("2026-10-30T18:00:00.000Z", ApiTime.format(stored));
            assertEquals(stored, ApiTime.parse("2026-10-30T18:00:00.000Z"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void pkceS256MatchesTheRfcVectorAndRejectsAWrongVerifier() {
        assertEquals(CHALLENGE, Pkce.s256(VERIFIER));
        assertTrue(Pkce.matches(VERIFIER, CHALLENGE));
        assertFalse(Pkce.matches(VERIFIER + "x", CHALLENGE));
        assertFalse(Pkce.matches("plain-verifier-value", "plain-verifier-value"));
        assertNotEqualToPlain(VERIFIER);
    }

    @Test
    void topicReferenceRefusesPlainMissingChallengeFragmentAndQueryMode() {
        TopicReferenceSignInRules.check(RETURN_TO, CHALLENGE, "S256", "fragment");

        IllegalArgumentException plain = assertThrows(IllegalArgumentException.class,
                () -> TopicReferenceSignInRules.check(RETURN_TO, CHALLENGE, "plain", "fragment"));
        assertTrue(plain.getMessage().contains("S256"));

        assertThrows(IllegalArgumentException.class,
                () -> TopicReferenceSignInRules.check(RETURN_TO, null, "S256", "fragment"));
        assertThrows(IllegalArgumentException.class,
                () -> TopicReferenceSignInRules.check(RETURN_TO + "#anchor", CHALLENGE, "S256", "fragment"));
        assertThrows(IllegalArgumentException.class,
                () -> TopicReferenceSignInRules.check(RETURN_TO, CHALLENGE, "S256", "query"));
    }

    @Test
    void codeFailuresShareOneMessageAndDoNotLookLikeSuccess() {
        List<GuideTokenExchange.Facts> failures = List.of(
                facts(false, GuideApps.TOPIC_REFERENCE, false, false, RETURN_TO, CHALLENGE, RETURN_TO, VERIFIER, null, true),
                facts(true, GuideApps.TOPIC_REFERENCE, true, false, RETURN_TO, CHALLENGE, RETURN_TO, VERIFIER, null, true),
                facts(true, GuideApps.TOPIC_REFERENCE, false, true, RETURN_TO, CHALLENGE, RETURN_TO, VERIFIER, null, true),
                facts(true, "clear", false, false, RETURN_TO, CHALLENGE, RETURN_TO, VERIFIER, null, true),
                facts(true, GuideApps.TOPIC_REFERENCE, false, false, RETURN_TO, CHALLENGE, "http://localhost:8001/other", VERIFIER, null, true),
                facts(true, GuideApps.TOPIC_REFERENCE, false, false, RETURN_TO, CHALLENGE, RETURN_TO, "wrong-verifier", null, true));

        String message = null;
        for (GuideTokenExchange.Facts facts : failures) {
            GuideTokenExchange.Verdict verdict = GuideTokenExchange.evaluate(facts);
            assertEquals(GuideTokenExchange.Status.INVALID, verdict.status);
            String next = GuideTokenExchange.clientMessage(verdict);
            if (message == null) {
                message = next;
            }
            assertEquals(message, next);
        }
        assertEquals(GuideTokenExchange.INVALID_MESSAGE, message);
    }

    @Test
    void validCodeWithRefusedOriginIsForbiddenNotTheSharedMessage() {
        GuideTokenExchange.Facts facts = facts(true, GuideApps.TOPIC_REFERENCE, false, false, RETURN_TO, CHALLENGE,
                RETURN_TO, VERIFIER, "http://localhost:8002", false);
        GuideTokenExchange.Verdict verdict = GuideTokenExchange.evaluate(facts);
        assertEquals(GuideTokenExchange.Status.ORIGIN_FORBIDDEN, verdict.status);
        assertNull(GuideTokenExchange.clientMessage(verdict));
    }

    @Test
    void goodVerifierMissingOriginAndAllowedOriginSucceed() {
        assertEquals(GuideTokenExchange.Status.OK, GuideTokenExchange.evaluate(
                facts(true, GuideApps.TOPIC_REFERENCE, false, false, RETURN_TO, CHALLENGE, RETURN_TO, VERIFIER, null, false)).status);
        assertEquals(GuideTokenExchange.Status.OK, GuideTokenExchange.evaluate(
                facts(true, GuideApps.TOPIC_REFERENCE, false, false, RETURN_TO, CHALLENGE, RETURN_TO, VERIFIER,
                        "http://localhost:8001", true)).status);
    }

    @Test
    void fragmentRedirectIsOnlyForPkceClients() {
        assertEquals(RETURN_TO + "#code=abc&state=xyz", ExternalRedirect.build(RETURN_TO, "abc", "xyz", true));
        assertEquals(RETURN_TO + "?code=abc&state=xyz", ExternalRedirect.build(RETURN_TO, "abc", "xyz", false));
        assertEquals(RETURN_TO + "?already=1&code=abc&state=xyz",
                ExternalRedirect.build(RETURN_TO + "?already=1", "abc", "xyz", false));
    }

    @Test
    void promptLoginKeepsTheReaderOnTheForm() {
        assertEquals(SignInPrompt.Next.SHOW_FORM, SignInPrompt.decide(true, true, true, false));
        assertEquals(SignInPrompt.Next.ISSUE_EXTERNAL_CODE, SignInPrompt.decide(false, true, true, false));
        assertEquals(SignInPrompt.Next.SHOW_FORM, SignInPrompt.decide(true, true, false, true));
        assertTrue(AuthFlowService.EXTERNAL_AUTH_ATTRIBUTE_PREFIX.startsWith("interophub.externalAuth."));
        assertTrue(SignInPrompt.isLoginPrompt(" login "));
        assertFalse(SignInPrompt.isLoginPrompt("none"));
    }

    @Test
    void corsOriginsComeFromAllowlistBases() {
        assertEquals("http://localhost:8001", GuideCors.originOf("http://localhost:8001/"));
        assertEquals("http://localhost:8001", GuideCors.originOf("http://localhost:8001/contextual-conditions/"));
        assertEquals("https://example.github.io", GuideCors.originOf("https://example.github.io/guide/"));
        Set<String> allowed = Set.of(GuideCors.originOf("http://localhost:8001/"));
        assertEquals(GuideCors.Permission.ALLOWED, GuideCors.permission("http://localhost:8001", allowed));
        assertEquals(GuideCors.Permission.REFUSED, GuideCors.permission("http://localhost:8002", allowed));
        assertEquals(GuideCors.Permission.ABSENT, GuideCors.permission(null, allowed));
        assertEquals(GuideCors.Permission.ABSENT, GuideCors.permission("  ", allowed));
    }

    @Test
    void bearerRejectionsAreAllRejectionsAndPrefixesMustMatch() {
        assertFalse(BearerTokenRules.accept(null, GuideApps.TOPIC_REFERENCE, User.UserStatus.ACTIVE, false, false));
        assertFalse(BearerTokenRules.accept(guideToken(), GuideApps.TOPIC_REFERENCE, User.UserStatus.ACTIVE, true, false));
        assertFalse(BearerTokenRules.accept(guideToken(), GuideApps.TOPIC_REFERENCE, User.UserStatus.ACTIVE, false, true));
        assertFalse(BearerTokenRules.accept(guideToken(), "clear", User.UserStatus.ACTIVE, false, false));
        assertFalse(BearerTokenRules.accept(guideToken(), GuideApps.TOPIC_REFERENCE, User.UserStatus.DISABLED, false, false));
        assertFalse(BearerTokenRules.accept(guideToken(), GuideApps.INTEROPHUB_API, User.UserStatus.ACTIVE, false, false));
        assertFalse(BearerTokenRules.accept(personalToken(), GuideApps.TOPIC_REFERENCE, User.UserStatus.ACTIVE, false, false));
        assertTrue(BearerTokenRules.accept(guideToken(), GuideApps.TOPIC_REFERENCE, User.UserStatus.ACTIVE, false, false));
        assertTrue(BearerTokenRules.accept(personalToken(), GuideApps.INTEROPHUB_API, User.UserStatus.ACTIVE, false, false));
        assertNull(BearerTokenRules.singleBearerToken(List.of()));
        assertNull(BearerTokenRules.singleBearerToken(List.of("Basic abc")));
        assertNull(BearerTokenRules.singleBearerToken(List.of("Bearer one", "Bearer two")));
        assertEquals("ihg_tok", BearerTokenRules.singleBearerToken(List.of("bearer ihg_tok")));
    }

    @Test
    void filterFailuresShareOneBody() throws Exception {
        String missing = filter(exchange("GET", "/api/guide/comments", null, null, null)).body;
        String basic = filter(exchange("GET", "/api/guide/comments", null, List.of("Basic abc"), null)).body;
        String two = filter(exchange("GET", "/api/guide/comments", null, List.of("Bearer one", "Bearer two"), null)).body;
        String rejected = filter(exchange("GET", "/api/guide/comments", null, List.of("Bearer ihg_tok"),
                raw -> BearerDecision.reject())).body;
        assertEquals(GuideApiErrors.INVALID_TOKEN_BODY, missing);
        assertEquals(missing, basic);
        assertEquals(missing, two);
        assertEquals(missing, rejected);
        Captured rejectedCapture = filter(exchange("GET", "/api/guide/comments", null, List.of("Bearer ihg_tok"),
                raw -> BearerDecision.reject()));
        assertEquals(401, rejectedCapture.status);
        assertEquals(GuideApiErrors.AUTHENTICATE_INVALID, rejectedCapture.headers.get("WWW-Authenticate"));
    }

    @Test
    void scopeRefusalAndMissingUserGuard() throws Exception {
        User user = new User();
        user.setUserId(7L);
        HttpResult missingUser = call((request, response) -> {
            assertNull(GuideRequestAuth.requireUser(request, response));
        }, Map.of());
        assertEquals(401, missingUser.status);
        assertEquals(GuideApiErrors.INVALID_TOKEN_BODY, missingUser.body);

        HttpResult refused = call((request, response) -> {
            request.setAttribute(GuideRequestAuth.USER, user);
            request.setAttribute(GuideRequestAuth.SCOPES, Set.of(GuideScopes.COMMENTS));
            assertFalse(GuideRequestAuth.requireScope(request, response, GuideScopes.TOPICS_WRITE));
        }, Map.of());
        assertEquals(403, refused.status);
        assertTrue(refused.body.contains("\"error\":\"insufficient_scope\""));
        assertTrue(refused.body.contains("\"requiredScope\":\"topics:write\""));
        assertTrue(refused.headers.get("WWW-Authenticate").contains("insufficient_scope"));

        HttpResult implied = call((request, response) -> {
            request.setAttribute(GuideRequestAuth.USER, user);
            request.setAttribute(GuideRequestAuth.SCOPES, Set.of(GuideScopes.TOPICS_WRITE));
            assertTrue(GuideRequestAuth.requireScope(request, response, GuideScopes.TOPICS_READ));
        }, Map.of());
        assertEquals("", implied.body);
    }

    @Test
    void corsPreflightAndRefusedOriginDoNoTokenWork() throws Exception {
        Set<String> allowed = Set.of("http://localhost:8001");
        AtomicInteger lookups = new AtomicInteger();
        GuideTokenLookup lookup = raw -> {
            lookups.incrementAndGet();
            return BearerDecision.reject();
        };

        Captured allowedPreflight = filter(exchange("OPTIONS", "/api/guide/token", "http://localhost:8001", null, lookup), allowed);
        assertEquals(204, allowedPreflight.status);
        assertEquals("http://localhost:8001", allowedPreflight.headers.get("Access-Control-Allow-Origin"));
        assertEquals("Origin", allowedPreflight.headers.get("Vary"));
        assertEquals("WWW-Authenticate", allowedPreflight.headers.get("Access-Control-Expose-Headers"));
        assertEquals("GET, POST, PATCH, DELETE", allowedPreflight.headers.get("Access-Control-Allow-Methods"));
        assertEquals("Authorization, Content-Type", allowedPreflight.headers.get("Access-Control-Allow-Headers"));
        assertEquals("600", allowedPreflight.headers.get("Access-Control-Max-Age"));
        assertFalse(allowedPreflight.headers.containsKey("Access-Control-Allow-Credentials"));
        assertEquals(0, lookups.get());

        Captured refusedPreflight = filter(exchange("OPTIONS", "/api/guide/token", "http://localhost:8002", null, lookup), allowed);
        assertEquals(403, refusedPreflight.status);
        assertFalse(refusedPreflight.headers.containsKey("Access-Control-Allow-Origin"));
        assertEquals(0, lookups.get());

        Captured refusedPost = filter(exchange("POST", "/api/guide/token", "http://localhost:8002", null, lookup), allowed);
        assertEquals(403, refusedPost.status);
        assertTrue(refusedPost.body.contains("\"error\":\"forbidden\""));
        assertFalse(refusedPost.headers.containsKey("Access-Control-Allow-Origin"));
        assertFalse(refusedPost.chainCalled);
        assertEquals(0, lookups.get());

        Captured tokenPost = filter(exchange("POST", "/api/guide/token", "http://localhost:8001", null, lookup), allowed);
        assertTrue(tokenPost.chainCalled);
        assertEquals("http://localhost:8001", tokenPost.headers.get("Access-Control-Allow-Origin"));
        assertEquals(0, lookups.get());

        Captured noOrigin = filter(exchange("POST", "/api/guide/token", null, null, lookup), allowed);
        assertTrue(noOrigin.chainCalled);
        assertFalse(noOrigin.headers.containsKey("Access-Control-Allow-Origin"));
    }

    @Test
    void issuedTokensUseTheRightPrefix() {
        String guide = TokenHash.randomToken(GuideApps.GUIDE_PREFIX, new java.security.SecureRandom());
        String personal = TokenHash.randomToken(GuideApps.PERSONAL_PREFIX, new java.security.SecureRandom());
        assertEquals(GuideApps.KIND_GUIDE, GuideApps.kind(GuideApps.TOPIC_REFERENCE, guide));
        assertEquals(GuideApps.KIND_PERSONAL, GuideApps.kind(GuideApps.INTEROPHUB_API, personal));
        assertEquals("comments", GuideScopes.GUIDE_TOKEN_SCOPES);
        assertEquals("comments,topics:write", GuideScopes.joinPersonal(List.of("topics:write", "comments")));
        assertTrue(GuideScopes.guideLabel("http://localhost:8001").startsWith("Guide sign-in: http://localhost:8001"));
    }

    private static void assertNotEqualToPlain(String verifier) {
        assertFalse(verifier.equals(Pkce.s256(verifier)));
    }

    private static String guideToken() {
        return GuideApps.GUIDE_PREFIX + "a".repeat(GuideApps.RAW_TOKEN_BODY_LENGTH);
    }

    private static String personalToken() {
        return GuideApps.PERSONAL_PREFIX + "b".repeat(GuideApps.RAW_TOKEN_BODY_LENGTH);
    }

    private static GuideTokenExchange.Facts facts(boolean found, String appCode, boolean expired, boolean consumed,
            String storedReturnTo, String storedChallenge, String returnTo, String verifier, String origin,
            boolean originAllowed) {
        GuideTokenExchange.Facts facts = new GuideTokenExchange.Facts();
        facts.found = found;
        facts.appCode = appCode;
        facts.expired = expired;
        facts.consumed = consumed;
        facts.storedReturnTo = storedReturnTo;
        facts.storedChallenge = storedChallenge;
        facts.returnTo = returnTo;
        facts.codeVerifier = verifier;
        facts.origin = origin;
        facts.originAllowed = originAllowed;
        return facts;
    }

    private static Captured filter(Exchange exchange) throws Exception {
        return filter(exchange, Set.of("http://localhost:8001"));
    }

    private static Captured filter(Exchange exchange, Set<String> allowed) throws Exception {
        GuideBearerTokenFilter filter = new GuideBearerTokenFilter(
                exchange.lookup == null ? raw -> BearerDecision.reject() : exchange.lookup,
                () -> allowed);
        Captured captured = new Captured();
        HttpServletRequest request = request(exchange, captured.attributes);
        HttpServletResponse response = response(captured);
        FilterChain chain = (req, res) -> captured.chainCalled = true;
        filter.doFilter(request, response, chain);
        captured.writer.flush();
        captured.body = captured.bytes.toString(StandardCharsets.UTF_8);
        return captured;
    }

    private static HttpResult call(ServletCall call, Map<String, Object> attributes) throws Exception {
        Captured captured = new Captured();
        HttpServletRequest request = request(exchange("GET", "/api/guide/comments", null, null, null), captured.attributes);
        captured.attributes.putAll(attributes);
        HttpServletResponse response = response(captured);
        call.run(request, response);
        captured.writer.flush();
        return new HttpResult(captured.status, captured.bytes.toString(StandardCharsets.UTF_8), captured.headers);
    }

    private static HttpServletRequest request(Exchange exchange, Map<String, Object> attributes) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getMethod" -> exchange.method;
                        case "getHeader" -> "Origin".equals(args[0]) ? exchange.origin : null;
                        case "getHeaders" -> enumeration(exchange.authorization);
                        case "getServletPath" -> exchange.path;
                        case "getRequestURI" -> exchange.path;
                        case "getContextPath" -> "";
                        case "setAttribute" -> {
                            attributes.put((String) args[0], args[1]);
                            yield null;
                        }
                        case "getAttribute" -> attributes.get(args[0]);
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static HttpServletResponse response(Captured captured) {
        captured.writer = new PrintWriter(new OutputStreamWriter(captured.bytes, StandardCharsets.UTF_8), true);
        return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "setStatus" -> {
                            captured.status = (Integer) args[0];
                            yield null;
                        }
                        case "setHeader", "addHeader" -> {
                            captured.headers.put((String) args[0], (String) args[1]);
                            yield null;
                        }
                        case "setContentType", "setCharacterEncoding" -> null;
                        case "getWriter" -> captured.writer;
                        case "getStatus" -> captured.status;
                        default -> defaultValue(method.getReturnType());
                    };
                });
    }

    private static Enumeration<String> enumeration(List<String> values) {
        return new Vector<>(values == null ? List.of() : values).elements();
    }

    private static Object defaultValue(Class<?> type) {
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(int.class)) {
            return 0;
        }
        if (type.equals(long.class)) {
            return 0L;
        }
        return null;
    }

    private static Exchange exchange(String method, String path, String origin, List<String> authorization,
            GuideTokenLookup lookup) {
        return new Exchange(method, path, origin, authorization, lookup);
    }

    private record Exchange(String method, String path, String origin, List<String> authorization, GuideTokenLookup lookup) {
    }

    private static final class Captured {
        private int status = 200;
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, Object> attributes = new HashMap<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private PrintWriter writer;
        private String body = "";
        private boolean chainCalled;
    }

    private record HttpResult(int status, String body, Map<String, String> headers) {
    }

    @FunctionalInterface
    private interface ServletCall {
        void run(HttpServletRequest request, HttpServletResponse response) throws Exception;
    }
}
