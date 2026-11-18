package org.airahub.interophub.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.BearerDecision;
import org.airahub.interophub.service.GuideApiErrors;
import org.airahub.interophub.service.GuideApiException;
import org.airahub.interophub.service.GuideApps;
import org.airahub.interophub.service.GuideHelp;
import org.airahub.interophub.service.GuideRequestAuth;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideScopes;
import org.airahub.interophub.service.GuideTokenInfoService;
import org.airahub.interophub.service.GuideTokenInfoService.DocumentFacts;
import org.airahub.interophub.service.GuideTokenInfoService.GuideEntry;
import org.airahub.interophub.service.GuideTokenInfoService.TokenFacts;
import org.airahub.interophub.service.GuideTokenInfoService.TopicFacts;
import org.airahub.interophub.service.GuideTokenService.AccountToken;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** Plan docs/tasks/topic-reference-author-onboarding.md, Section 5. */
class TopicReferenceAuthorOnboardingTest {
    private static final String HUB = "http://localhost:8080/hub";
    private static final String RAW_PERSONAL = "ihp_" + "A".repeat(20) + "b".repeat(23);
    private static final Pattern TOKEN_LIKE = Pattern.compile("ih[gp]_[A-Za-z0-9_-]{8,}");
    private static final Pattern ID = Pattern.compile("id=\"([^\"]+)\"");
    private static final List<String> PLAN_CODES = List.of("invalid_token", "insufficient_scope", "forbidden",
            "not_found", "document_inactive", "comments_closed", "invalid_request", "invalid_field", "invalid_state");

    // ------------------------------------------------------------------ help page

    @Test
    void helpPageRendersWithoutSignIn() throws Exception {
        HttpServletRequest request = request("GET", "/help/topic-reference-authors", Map.of(), List.of());
        Captured captured = new Captured();
        TopicReferenceHelpServlet.render(request, captured.response(),
                TopicReferenceHelpServlet.signedOutPage(request));
        String html = captured.body();
        assertEquals(0, captured.status, "no redirect to sign-in");
        assertNull(captured.headers.get("Location"));
        assertTrue(html.contains(TopicReferenceHelpServlet.TITLE));
        assertTrue(html.contains("<html") || html.contains("<!DOCTYPE") || html.contains("<!doctype"));
    }

    @Test
    void helpPageHasEveryContractAnchor() {
        Set<String> ids = ids(helpContent());
        for (String section : GuideHelp.SECTIONS) {
            assertTrue(ids.contains(section), section);
        }
        for (String code : PLAN_CODES) {
            assertTrue(ids.contains("troubleshooting-" + code), code);
        }
        for (String code : GuideHelp.ERROR_CODES) {
            assertTrue(ids.contains(GuideHelp.troubleshootingAnchor(code)), code);
        }
        assertTrue(ids.contains(TopicReferenceHelpServlet.HTML_ANCHOR));
        assertEquals(List.of("who-can-author", "create-a-token", "where-the-token-goes", "what-an-agent-can-do",
                "if-you-are-an-agent", "troubleshooting"), GuideHelp.SECTIONS);
    }

    @Test
    void helpPageHoldsNoTokenOrPersonalDetail() {
        String html = helpContent();
        assertFalse(TOKEN_LIKE.matcher(html).find(), "token-like string on the help page");
        assertFalse(html.contains("@"), "no email addresses");
        assertTrue(html.contains("INTEROPHUB_TOKEN"));
        assertTrue(html.contains("gh secret set INTEROPHUB_TOKEN"));
        assertTrue(html.contains("GET /api/token"));
        assertTrue(html.contains("PATCH /api/guide/{documentKey}/labels/{label}"));
        assertTrue(html.contains("publish_guide_to_hub.py"));
        assertTrue(html.contains("Send info / question"));
    }

    // ------------------------------------------------------------------ GET /api/token

    @Test
    void personalTokenOfAnEditorOfTwoTopics() {
        FakeStore store = new FakeStore();
        store.editable.addAll(List.of(1L, 2L));
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0);
        store.token = new TokenFacts("laptop agent", now.plusDays(90));
        JSONObject json = new GuideTokenInfoService(store).describe(user(), 41L, GuideApps.KIND_PERSONAL,
                Set.of(GuideScopes.TOPICS_WRITE, GuideScopes.COMMENTS), now);

        assertEquals(7L, json.getJSONObject("user").getLong("userId"));
        assertEquals("Pat Author", json.getJSONObject("user").getString("displayName"));
        assertEquals("personal", json.getString("kind"));
        assertEquals("laptop agent", json.getString("label"));
        assertEquals(List.of("comments", "topics:write"), strings(json.getJSONArray("scopes")));
        assertTrue(json.getString("expiresAt").endsWith("Z"));
        assertEquals(90, json.getInt("expiresInDays"));
        assertEquals(HUB + "/help/topic-reference-authors#if-you-are-an-agent", json.getString("help"));

        JSONArray guides = json.getJSONArray("guides");
        assertEquals(List.of("alpha-guide", "beta-guide"), keys(guides), "two topics; not topic 3; not inactive");
        JSONObject alpha = guides.getJSONObject(0);
        assertEquals("Alpha", alpha.getString("title"));
        assertEquals(1L, alpha.getLong("topicId"));
        assertEquals("Topic One", alpha.getString("topicName"));
        assertEquals("HUB", alpha.getString("hosting"));
        assertTrue(alpha.getBoolean("active"));
        assertTrue(alpha.getBoolean("canPublish"));
        assertEquals("EXTERNAL", guides.getJSONObject(1).getString("hosting"));
        assertEquals(Set.of("user", "kind", "label", "scopes", "expiresAt", "expiresInDays", "guides", "help"),
                json.keySet());
    }

    @Test
    void canPublishFollowsTopicsWriteAndGuideTokensCannotPublish() {
        FakeStore store = new FakeStore();
        store.editable.add(1L);
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0);
        store.token = new TokenFacts("Guide sign-in: https://example.github.io", now.plusDays(30));
        JSONObject guide = new GuideTokenInfoService(store).describe(user(), 9L, GuideApps.KIND_GUIDE,
                Set.of(GuideScopes.COMMENTS), now);
        assertEquals("guide", guide.getString("kind"));
        assertEquals(30, guide.getInt("expiresInDays"));
        assertFalse(guide.getJSONArray("guides").getJSONObject(0).getBoolean("canPublish"));

        JSONObject readOnly = new GuideTokenInfoService(store).describe(user(), 9L, GuideApps.KIND_PERSONAL,
                Set.of(GuideScopes.TOPICS_READ), now);
        assertFalse(readOnly.getJSONArray("guides").getJSONObject(0).getBoolean("canPublish"));
    }

    @Test
    void readerGetsAnEmptyGuidesList() {
        FakeStore store = new FakeStore();
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0);
        store.token = new TokenFacts("reader", now.plusDays(1).minusSeconds(5));
        JSONObject json = new GuideTokenInfoService(store).describe(user(), 3L, GuideApps.KIND_PERSONAL,
                Set.of(GuideScopes.COMMENTS), now);
        assertTrue(json.has("guides"));
        assertEquals(0, json.getJSONArray("guides").length());
        assertEquals(1, json.getInt("expiresInDays"));
    }

    @Test
    void responseNeverCarriesEmailOrToken() throws Exception {
        FakeStore store = new FakeStore();
        store.editable.add(1L);
        store.token = new TokenFacts("laptop agent", LocalDateTime.now().plusDays(90));
        HttpServletRequest request = request("GET", "/api/token", Map.of(
                GuideRequestAuth.USER, user(),
                GuideRequestAuth.TOKEN_ID, 41L,
                GuideRequestAuth.TOKEN_KIND, GuideApps.KIND_PERSONAL,
                GuideRequestAuth.SCOPES, Set.of(GuideScopes.TOPICS_WRITE)), List.of("Bearer " + RAW_PERSONAL));
        Captured captured = new Captured();
        new GuideTokenInfoServlet(new GuideTokenInfoService(store)).service(request, captured.response());
        String body = captured.body();
        assertEquals(200, captured.status);
        assertFalse(body.contains("pat.author@example.invalid"));
        assertFalse(body.toLowerCase().contains("email"));
        assertFalse(TOKEN_LIKE.matcher(body).find());
        assertFalse(body.contains("hash"));
        assertEquals("no-store", captured.headers.get("Cache-Control"));
    }

    @Test
    void invalidExpiredAndRevokedTokensGet401WithHelp() throws Exception {
        // Expired, revoked, unknown, and disabled-user tokens are all a rejected lookup (BearerTokenRules).
        GuideBearerTokenFilter filter = new GuideBearerTokenFilter(raw -> BearerDecision.reject(), Set::of);
        for (List<String> header : List.of(List.<String>of(), List.of("Bearer " + RAW_PERSONAL), List.of("Basic x"))) {
            Captured captured = new Captured();
            boolean[] passed = { false };
            FilterChain chain = (req, res) -> passed[0] = true;
            filter.doFilter(request("GET", "/api/token", Map.of(), header), captured.response(), chain);
            assertFalse(passed[0]);
            assertEquals(401, captured.status);
            JSONObject json = new JSONObject(captured.body());
            assertEquals("invalid_token", json.getString("error"));
            assertEquals(HUB + "/help/topic-reference-authors#troubleshooting-invalid_token", json.getString("help"));
            assertEquals(GuideApiErrors.AUTHENTICATE_INVALID, captured.headers.get("WWW-Authenticate"));
        }

        Captured noUser = new Captured();
        new GuideTokenInfoServlet(new GuideTokenInfoService(new FakeStore()))
                .service(request("GET", "/api/token", Map.of(), List.of()), noUser.response());
        assertEquals(401, noUser.status);
        assertEquals(GuideApiErrors.invalidTokenBody(), noUser.body());
    }

    @Test
    void tokenEndpointRefusesOtherMethodsAsJson() throws Exception {
        Captured captured = new Captured();
        new GuideTokenInfoServlet(new GuideTokenInfoService(new FakeStore()))
                .service(request("POST", "/api/token", Map.of(GuideRequestAuth.USER, user()), List.of()),
                        captured.response());
        assertEquals(405, captured.status);
        assertEquals("GET", captured.headers.get("Allow"));
        JSONObject json = new JSONObject(captured.body());
        assertEquals("invalid_request", json.getString("error"));
        assertTrue(json.getString("help").endsWith("#troubleshooting-invalid_request"));
    }

    // ------------------------------------------------------------------ help in every error

    @Test
    void everyGuideApiExceptionCarriesHelpWithTheRightAnchor() throws Exception {
        Set<String> anchors = ids(helpContent());
        List<GuideApiException> all = List.of(
                GuideApiException.invalidRequest("Bad."),
                GuideApiException.invalidField("label", "Bad label."),
                GuideApiException.forbidden("No."),
                GuideApiException.notEditor("Topic One"),
                GuideApiException.editWindowClosed(),
                GuideApiException.commentsClosed(),
                GuideApiException.notFound("Missing."),
                GuideApiException.conflict("Changed.", "{\"id\":1}"),
                GuideApiException.invalidState("Closed."),
                GuideApiException.documentInactive(),
                GuideApiException.duplicate("documentKey", "Taken."),
                GuideApiException.urlNotApproved());
        Set<String> codes = new java.util.HashSet<>();
        for (GuideApiException ex : all) {
            Captured captured = new Captured();
            GuideApiErrors.write(captured.response(), ex);
            JSONObject json = new JSONObject(captured.body());
            codes.add(ex.getError());
            assertEquals(ex.getStatus(), captured.status);
            assertEquals(ex.getError(), json.getString("error"));
            assertEquals(ex.getMessage(), json.getString("message"));
            assertEquals(ex.getField(), json.optString("field", null));
            if (ex.getCurrentJson() != null) {
                assertEquals(1, json.getJSONObject("current").getInt("id"));
            }
            String anchor = "troubleshooting-" + ex.getError();
            assertEquals(HUB + "/help/topic-reference-authors#" + anchor, json.getString("help"));
            assertTrue(anchors.contains(anchor), anchor);
        }
        assertEquals(Set.of("invalid_request", "invalid_field", "forbidden", "edit_window_closed", "comments_closed",
                "not_found", "conflict", "invalid_state", "document_inactive", "duplicate", "url_not_approved"), codes);
    }

    @Test
    void responseHelpersCarryHelpAndKeepTheirFields() throws Exception {
        Captured scope = new Captured();
        GuideApiErrors.insufficientScope(scope.response(), GuideScopes.TOPICS_WRITE);
        JSONObject json = new JSONObject(scope.body());
        assertEquals(403, scope.status);
        assertEquals("insufficient_scope", json.getString("error"));
        assertEquals("topics:write", json.getString("requiredScope"));
        assertTrue(json.getString("message").contains("topics:write"));
        assertTrue(json.getString("help").endsWith("#troubleshooting-insufficient_scope"));

        Captured forbidden = new Captured();
        GuideApiErrors.forbidden(forbidden.response(), "This origin is not allowed to call the guide API.");
        assertTrue(new JSONObject(forbidden.body()).getString("help").endsWith("#troubleshooting-forbidden"));

        Captured bad = new Captured();
        GuideApiErrors.invalidRequest(bad.response(), "Bad.");
        assertEquals(400, bad.status);
        assertTrue(new JSONObject(bad.body()).getString("help").endsWith("#troubleshooting-invalid_request"));

        Captured server = new Captured();
        GuideApiErrors.serverError(server.response());
        assertTrue(new JSONObject(server.body()).getString("help").endsWith("#troubleshooting-server_error"));

        assertEquals(HUB + "/help/topic-reference-authors#troubleshooting", GuideHelp.forError("teapot"));
    }

    @Test
    void forbiddenNamesTheTopicOnlyWhenTheCallerMaySeeIt() {
        GuideApiException named = GuideApiException.notEditor("Only an author can delete a comment.", "Topic One");
        assertEquals("forbidden", named.getError());
        assertEquals(403, named.getStatus());
        assertTrue(named.getMessage().startsWith("Only an author can delete a comment. "));
        assertTrue(named.getMessage().contains("editor of the topic \"Topic One\""));
        GuideApiException hidden = GuideApiException.notEditor(null);
        assertTrue(hidden.getMessage().contains("editor of this guide's topic"));
        assertFalse(hidden.getMessage().contains("\"\""));
    }

    // ------------------------------------------------------------------ token page

    @Test
    void authorPresetTicksCommentsAndTopicsWrite() {
        assertEquals(List.of("comments", "topics:write"), AccountTokensSection.chosenScopes("author", List.of()));
        assertEquals(List.of("topics:read", "comments", "topics:write"),
                AccountTokensSection.chosenScopes("author", List.of("topics:read")));
        assertEquals(List.of("comments"), AccountTokensSection.chosenScopes(null, List.of("comments")));

        String html = tokenSection(null, List.of(), LocalDateTime.now());
        assertTrue(html.contains("data-scopes=\"comments topics:write\""));
        assertTrue(html.contains("Author (publish guides and manage comments)"));
        assertTrue(html.contains("Read and answer comments on guides."));
        assertTrue(html.contains("Read topic and guide information."));
        assertTrue(html.contains("Publish and manage guides on topics you edit."));
    }

    @Test
    void howToUsePanelAppearsOnlyAfterCreationAndNeverRepeatsTheToken() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0);
        String before = tokenSection(null, List.of(), now);
        assertFalse(before.contains("How to use this token"));
        assertFalse(before.contains("Copy this personal access token now"));

        AccountTokensSection.Revealed revealed = new AccountTokensSection.Revealed(RAW_PERSONAL, now.plusDays(90),
                "comments,topics:write");
        StringWriter writer = new StringWriter();
        AccountTokensSection.render(new PrintWriter(writer), "/hub", revealed, List.of(),
                List.of(new GuideEntry("alpha-guide", "Alpha", 1L, "Topic One", "HUB", true, true)), now);
        String after = writer.toString();
        assertTrue(after.contains("How to use this token"));
        assertEquals(1, count(after, RAW_PERSONAL), "the token is shown once, in the copy box only");
        assertTrue(after.contains("$env:INTEROPHUB_TOKEN = &quot;&lt;paste your token here&gt;&quot;"));
        assertTrue(after.contains("setx INTEROPHUB_TOKEN"));
        assertTrue(after.contains("export INTEROPHUB_TOKEN="));
        assertTrue(after.contains("gh secret set INTEROPHUB_TOKEN --repo &lt;owner&gt;/&lt;repo&gt;"));
        assertTrue(after.contains("2027-02-16"));
        assertTrue(after.contains("alpha-guide"));
        assertTrue(after.contains(HUB + "/help/topic-reference-authors"));
        assertTrue(after.contains("If an agent asked you to make this token, set it as INTEROPHUB_TOKEN yourself;"
                + " don&#39;t paste it into the chat."));
    }

    @Test
    void expiryBadgeWithinFourteenDays() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0);
        assertTrue(AccountTokensSection.expiryCell(now.plusDays(13), now).contains("aira-badge--warning"));
        assertTrue(AccountTokensSection.expiryCell(now.plusDays(13), now).contains("Expires soon"));
        assertFalse(AccountTokensSection.expiryCell(now.plusDays(15), now).contains("aira-badge"));
        assertTrue(AccountTokensSection.expiryCell(now.minusMinutes(1), now).contains("Expired"));

        String html = tokenSection(null, List.of(
                new AccountToken(1L, "soon", GuideApps.KIND_PERSONAL, "comments", now.minusDays(80), now.plusDays(10), null),
                new AccountToken(2L, "later", GuideApps.KIND_PERSONAL, "comments", now, now.plusDays(90), null)), now);
        assertEquals(1, count(html, "Expires soon"));
    }

    // ------------------------------------------------------------------ guide settings page

    @Test
    void publishBoxShowsTheCommandForEachHosting() {
        DocumentRow hub = document("hosted-guide", "HUB");
        DocumentRow external = document("external-guide", "EXTERNAL");
        StringWriter writer = new StringWriter();
        GuidePublishBox.render(new PrintWriter(writer), List.of(hub, external), HUB);
        String html = writer.toString();
        assertTrue(html.contains("Publish from your agent"));

        String hubBox = box(html, "hosted-guide");
        assertTrue(hubBox.contains("<code>hosted-guide</code>"));
        assertTrue(hubBox.contains("<code>" + HUB + "</code>"));
        assertTrue(hubBox.contains("<code>HUB</code>"));
        assertTrue(hubBox.contains("python process/tools/publish_guide_to_hub.py output/guide/index.html --label vX.Y.Z --label latest"));
        assertFalse(hubBox.contains("--manifest-only"));
        assertTrue(hubBox.contains("PATCH &quot;" + HUB + "/api/guide/hosted-guide/labels/vX.Y.Z&quot;"));

        String externalBox = box(html, "external-guide");
        assertTrue(externalBox.contains("<code>EXTERNAL</code>"));
        assertTrue(externalBox.contains("python process/tools/publish_guide_to_hub.py output/guide/index.html --manifest-only --label vX.Y.Z"));
        assertFalse(externalBox.contains("--label latest"));
        assertTrue(externalBox.contains("/api/guide/external-guide/labels/vX.Y.Z"));
        assertTrue(html.contains(HUB + "/help/topic-reference-authors"));

        StringWriter none = new StringWriter();
        GuidePublishBox.render(new PrintWriter(none), List.of(), HUB);
        assertEquals("", none.toString());
    }

    // ------------------------------------------------------------------ helpers

    private static String helpContent() {
        StringWriter writer = new StringWriter();
        TopicReferenceHelpServlet.writeContent(new PrintWriter(writer), HUB);
        return writer.toString();
    }

    private static String tokenSection(AccountTokensSection.Revealed revealed, List<AccountToken> tokens,
            LocalDateTime now) {
        StringWriter writer = new StringWriter();
        AccountTokensSection.render(new PrintWriter(writer), "/hub", revealed, tokens, List.of(), now);
        return writer.toString();
    }

    private static Set<String> ids(String html) {
        Set<String> ids = new java.util.HashSet<>();
        Matcher matcher = ID.matcher(html);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private static String box(String html, String key) {
        int start = html.indexOf("id=\"" + GuidePublishBox.ANCHOR + "-" + key + "\"");
        assertTrue(start >= 0, key);
        int end = html.indexOf("</div>", start);
        return html.substring(start, end);
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }

    private static DocumentRow document(String key, String hosting) {
        DocumentRow row = new DocumentRow();
        row.documentKey = key;
        row.hosting = hosting;
        row.active = true;
        return row;
    }

    private static User user() {
        User user = new User();
        user.setUserId(7L);
        user.setFirstName("Pat");
        user.setLastName("Author");
        user.setEmail("pat.author@example.invalid");
        return user;
    }

    private static List<String> strings(JSONArray array) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            values.add(array.getString(i));
        }
        return values;
    }

    private static List<String> keys(JSONArray guides) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < guides.length(); i++) {
            keys.add(guides.getJSONObject(i).getString("documentKey"));
        }
        return keys;
    }

    private static final class FakeStore implements GuideTokenInfoService.Store {
        final Set<Long> editable = new java.util.HashSet<>();
        TokenFacts token;

        @Override
        public TokenFacts token(long tokenId) {
            return token;
        }

        @Override
        public List<DocumentFacts> documents() {
            return List.of(
                    new DocumentFacts("beta-guide", "Beta", 2L, "EXTERNAL", true),
                    new DocumentFacts("alpha-guide", "Alpha", 1L, "HUB", true),
                    new DocumentFacts("old-guide", "Old", 1L, "EXTERNAL", false),
                    new DocumentFacts("gamma-guide", "Gamma", 3L, "EXTERNAL", true));
        }

        @Override
        public TopicFacts topic(long topicId) {
            return new TopicFacts(topicId, topicId == 1L ? "Topic One" : topicId == 2L ? "Topic Two" : "Topic Three");
        }

        @Override
        public boolean canEdit(User user, long topicId) {
            return editable.contains(topicId);
        }
    }

    private static HttpServletRequest request(String method, String path, Map<String, Object> attributes,
            List<String> authorization) {
        Map<String, Object> attrs = new HashMap<>(attributes);
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class[] { HttpServletRequest.class }, (proxy, m, args) -> {
                    switch (m.getName()) {
                        case "getMethod":
                            return method;
                        case "getContextPath":
                            return "/hub";
                        case "getServletPath":
                            return path;
                        case "getRequestURI":
                            return "/hub" + path;
                        case "getHeader":
                            return "Authorization".equalsIgnoreCase((String) args[0]) && !authorization.isEmpty()
                                    ? authorization.get(0) : null;
                        case "getHeaders":
                            return Collections.enumeration("Authorization".equalsIgnoreCase((String) args[0])
                                    ? authorization : List.<String>of());
                        case "getAttribute":
                            return attrs.get((String) args[0]);
                        case "setAttribute":
                            attrs.put((String) args[0], args[1]);
                            return null;
                        default:
                            Class<?> type = m.getReturnType();
                            if (type == boolean.class) {
                                return false;
                            }
                            if (type == int.class) {
                                return 0;
                            }
                            if (type == long.class) {
                                return 0L;
                            }
                            return null;
                    }
                });
    }

    private static final class Captured {
        int status;
        final Map<String, String> headers = new HashMap<>();
        final StringWriter writer = new StringWriter();
        final PrintWriter printer = new PrintWriter(writer);

        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                    new Class[] { HttpServletResponse.class }, (proxy, m, args) -> {
                        switch (m.getName()) {
                            case "setStatus":
                                status = (Integer) args[0];
                                return null;
                            case "setHeader":
                            case "addHeader":
                                headers.put((String) args[0], (String) args[1]);
                                return null;
                            case "getWriter":
                                return printer;
                            default:
                                Class<?> type = m.getReturnType();
                                if (type == boolean.class) {
                                    return false;
                                }
                                if (type == int.class) {
                                    return 0;
                                }
                                return null;
                        }
                    });
        }

        String body() {
            printer.flush();
            return writer.toString();
        }
    }
}
