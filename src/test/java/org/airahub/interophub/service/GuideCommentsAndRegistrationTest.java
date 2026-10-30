package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRegistrationService.UrlInput;
import org.airahub.interophub.service.GuideRows.AllowInsert;
import org.airahub.interophub.service.GuideRows.AllowRow;
import org.airahub.interophub.service.GuideRows.AllowToggle;
import org.airahub.interophub.service.GuideRows.AuditRow;
import org.airahub.interophub.service.GuideRows.CommentRow;
import org.airahub.interophub.service.GuideRows.Disposition;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.FlagRow;
import org.airahub.interophub.service.GuideRows.Person;
import org.airahub.interophub.service.GuideRows.RegistrationPlan;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.airahub.interophub.servlet.GuideApiServlet;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class GuideCommentsAndRegistrationTest {

    @Test
    void apiTimeAcceptsAnyIsoInstant() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Denver"));
            LocalDateTime stored = LocalDateTime.of(2026, 10, 30, 12, 0, 0);
            assertEquals(stored, ApiTime.parse("2026-10-30T18:00:00Z"));
            assertEquals(stored, ApiTime.parse("2026-10-30T18:00:00.000000Z"));
            assertEquals(stored, ApiTime.parse("2026-10-30T18:00:00+00:00"));
            assertThrows(DateTimeParseException.class, () -> ApiTime.parse("yesterday"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void visibilityHidesAPrivateTopicAndReportsAnUnknownGuide() {
        MemoryComments comments = new MemoryComments();
        comments.addTopic(7, "CONTEXTUAL-CONDITIONS", "PUBLIC");
        comments.addTopic(8, "GUIDE-PRIVATE-TEST", "PRIVATE");
        comments.addDocument("contextual-conditions", 7, true);
        comments.addDocument("guide-private-test", 8, true);
        User reader = user(2, false);
        User outsider = user(3, false);
        comments.view(2, 7);
        JSONObject publicGuide = comments.service.document(reader, "contextual-conditions");
        assertTrue(publicGuide.getJSONObject("permissions").getBoolean("canView"));
        assertEquals("CONTEXTUAL-CONDITIONS", publicGuide.getJSONObject("topic").getString("topicCode"));

        JSONObject hidden = comments.service.document(outsider, "guide-private-test");
        assertFalse(hidden.getJSONObject("permissions").getBoolean("canView"));
        assertFalse(hidden.getJSONObject("permissions").getBoolean("canComment"));
        assertTrue(hidden.isNull("topic"));

        GuideApiException missing = assertThrows(GuideApiException.class,
                () -> comments.service.document(reader, "missing-guide"));
        assertEquals("not_found", missing.getError());
    }

    @Test
    void commentRulesCoverRepliesEditsWindowAndSomeoneElsesText() {
        MemoryComments comments = new MemoryComments();
        comments.addTopic(7, "CONTEXTUAL-CONDITIONS", "PUBLIC");
        comments.addDocument("contextual-conditions", 7, true);
        User reader = user(2, false);
        User other = user(4, false);
        comments.view(2, 7);
        comments.view(4, 7);
        JSONObject thread = comments.service.post(reader, 9L, "contextual-conditions", comment("hello")).thread();
        long id = thread.getLong("id");
        JSONObject reply = comments.service.post(reader, 9L, "contextual-conditions",
                new JSONObject().put("parentCommentId", id).put("text", "a reply")).thread();
        long replyId = reply.getJSONArray("replies").getJSONObject(0).getLong("id");

        GuideApiException nested = assertThrows(GuideApiException.class, () -> comments.service.post(reader, 9L,
                "contextual-conditions", new JSONObject().put("parentCommentId", replyId).put("text", "no")));
        assertEquals("invalid_field", nested.getError());
        assertEquals("parentCommentId", nested.getField());

        comments.row(id).state = "CLOSED";
        GuideApiException closed = assertThrows(GuideApiException.class, () -> comments.service.post(reader, 9L,
                "contextual-conditions", new JSONObject().put("parentCommentId", id).put("text", "later")));
        assertEquals("invalid_state", closed.getError());

        comments.row(id).state = "WITHDRAWN";
        comments.row(id).withdrawnAt = LocalDateTime.now();
        GuideApiException withdrawn = assertThrows(GuideApiException.class, () -> comments.service.post(reader, 9L,
                "contextual-conditions", new JSONObject().put("parentCommentId", id).put("text", "later")));
        assertEquals("invalid_state", withdrawn.getError());

        comments.row(id).state = "OPEN";
        comments.row(id).withdrawnAt = null;
        GuideApiException someoneElse = assertThrows(GuideApiException.class,
                () -> comments.service.patchText(other, 10L, id, new JSONObject().put("text", "nope")));
        assertEquals("forbidden", someoneElse.getError());

        comments.row(id).createdAt = LocalDateTime.now().minusDays(15);
        GuideApiException window = assertThrows(GuideApiException.class,
                () -> comments.service.patchText(reader, 9L, id, new JSONObject().put("text", "too late")));
        assertEquals("edit_window_closed", window.getError());

        GuideApiException authorField = assertThrows(GuideApiException.class, () -> comments.service.patchText(reader, 9L,
                id, new JSONObject().put("dispositionCode", "PERSUASIVE").put("text", "no")));
        assertEquals("invalid_field", authorField.getError());
        assertEquals("dispositionCode", authorField.getField());
        assertEquals("hello", comments.row(id).text);
    }

    @Test
    void sinceIncludesEditsRepliesWithdrawalsAndTombstones() {
        MemoryComments comments = new MemoryComments();
        comments.addTopic(7, "CONTEXTUAL-CONDITIONS", "PUBLIC");
        comments.addDocument("contextual-conditions", 7, true);
        User reader = user(2, false);
        comments.view(2, 7);
        long id = comments.service.post(reader, 9L, "contextual-conditions", comment("first")).id();
        LocalDateTime start = comments.row(id).updatedAt.minusMinutes(1);
        comments.service.patchText(reader, 9L, id, new JSONObject().put("text", "edited"));
        comments.service.post(reader, 9L, "contextual-conditions",
                new JSONObject().put("parentCommentId", id).put("text", "reply"));
        comments.service.withdraw(reader, 9L, id);

        long other = comments.service.post(reader, 9L, "contextual-conditions", comment("stays")).id();
        comments.row(other).updatedAt = start.minusHours(2);
        comments.row(other).deletedAt = LocalDateTime.now();
        comments.row(other).updatedAt = LocalDateTime.now();

        JSONObject listed = comments.service.list(reader, "contextual-conditions", "OPEN", null,
                ApiTime.format(start));
        JSONObject withdrawn = find(listed, id);
        assertEquals("WITHDRAWN", withdrawn.getString("state"));
        assertTrue(withdrawn.isNull("text"));
        assertEquals("reply", withdrawn.getJSONArray("replies").getJSONObject(0).getString("text"));
        JSONObject tombstone = find(listed, other);
        assertTrue(tombstone.getBoolean("deleted"));
        assertFalse(tombstone.has("text"));

        long fresh = comments.service.post(reader, 9L, "contextual-conditions", comment("fresh")).id();
        comments.row(fresh).updatedAt = start.minusHours(3);
        comments.service.post(reader, 9L, "contextual-conditions",
                new JSONObject().put("parentCommentId", fresh).put("text", "only a reply"));
        JSONObject replyOnly = comments.service.list(reader, "contextual-conditions", null, null, ApiTime.format(start));
        assertEquals("only a reply", find(replyOnly, fresh).getJSONArray("replies").getJSONObject(0).getString("text"));
        assertFalse(comments.audits.isEmpty());
    }

    @Test
    void registrationUrlRulesAllowlistAndDryRun() {
        MemoryRegistration registration = new MemoryRegistration();
        registration.prefixes.add("https://immregistries.github.io/");
        registration.localhostMode = true;
        registration.topic(7, "CONTEXTUAL-CONDITIONS");
        User author = user(5, false);
        User admin = user(1, true);
        registration.globalAdmin = false;

        GuideApiException refused = assertThrows(GuideApiException.class, () -> registration.service.create(author, 3L,
                "API", false, "CONTEXTUAL-CONDITIONS", "other-guide", "Other", null,
                List.of(new UrlInput("https://evil.example/guide", "Release"))));
        assertEquals("url_not_approved", refused.getError());
        assertEquals(0, registration.persistCount);

        registration.globalAdmin = true;
        registration.service.create(admin, 4L, "API", false, "CONTEXTUAL-CONDITIONS", "admin-guide", "Admin", null,
                List.of(new UrlInput("https://evil.example/guide", "Release")));
        assertEquals(1, registration.documents.size());

        registration.globalAdmin = false;
        JSONObject dry = registration.service.create(author, 3L, "API", true, "CONTEXTUAL-CONDITIONS",
                "checked-guide", "Checked", null,
                List.of(new UrlInput("https://immregistries.github.io/checked/", "Release")));
        assertTrue(dry.getBoolean("dryRun"));
        assertEquals(1, dry.getJSONObject("allowlistChanges").getJSONArray("added").length());
        assertEquals(1, registration.persistCount);
        assertNull(registration.document("checked-guide"));

        JSONObject created = registration.service.create(author, 3L, "API", false, "CONTEXTUAL-CONDITIONS",
                "checked-guide", "Checked", "https://github.com/example/checked",
                List.of(new UrlInput("https://immregistries.github.io/checked/", "Release")));
        assertFalse(created.getBoolean("dryRun"));
        assertEquals("API", registration.changes.get(registration.changes.size() - 1).source);
        assertEquals(3L, registration.changes.get(registration.changes.size() - 1).tokenId);
        assertTrue(registration.changes.get(registration.changes.size() - 1).changesJson.contains("documentKey"));
        assertTrue(registration.allowEnabled("https://immregistries.github.io/checked/"));

        registration.allow("http://localhost:8001/", true);
        registration.service.create(author, 3L, "API", false, "CONTEXTUAL-CONDITIONS", "local-guide", "Local", null,
                List.of(new UrlInput("http://localhost:8001/guide/", "Local")));
        registration.service.update(author, 3L, "API", false, "checked-guide", false, null, false, null, true, List.of(),
                false, null);
        assertFalse(registration.allowEnabled("https://immregistries.github.io/checked/"));
        assertTrue(registration.allowEnabled("http://localhost:8001/"));

        GuideApiException duplicate = assertThrows(GuideApiException.class, () -> registration.service.create(author, 3L,
                "API", false, "CONTEXTUAL-CONDITIONS", "copy-guide", "Copy", null,
                List.of(new UrlInput("https://evil.example/guide", "Release"))));
        assertEquals("duplicate", duplicate.getError());

        registration.localhostMode = false;
        GuideApiException devOff = assertThrows(GuideApiException.class, () -> registration.service.create(author, 3L,
                "API", false, "CONTEXTUAL-CONDITIONS", "dev-off", "Dev", null,
                List.of(new UrlInput("http://localhost:9/guide", "Local"))));
        assertEquals("invalid_field", devOff.getError());
    }

    @Test
    void postedFieldsAreRejectedByName() {
        MemoryComments comments = new MemoryComments();
        comments.addTopic(7, "CONTEXTUAL-CONDITIONS", "PUBLIC");
        comments.addDocument("contextual-conditions", 7, true);
        User reader = user(2, false);
        comments.view(2, 7);

        GuideApiException empty = assertThrows(GuideApiException.class, () -> comments.service.post(reader, 9L,
                "contextual-conditions", comment("   ")));
        assertEquals("invalid_field", empty.getError());
        assertEquals("text", empty.getField());

        GuideApiException longText = assertThrows(GuideApiException.class, () -> comments.service.post(reader, 9L,
                "contextual-conditions", comment("x".repeat(4001))));
        assertEquals("text", longText.getField());

        JSONObject longSnippet = comment("ok").put("snippet", "s".repeat(121));
        GuideApiException snippet = assertThrows(GuideApiException.class,
                () -> comments.service.post(reader, 9L, "contextual-conditions", longSnippet));
        assertEquals("snippet", snippet.getField());

        JSONObject badAnchor = comment("ok").put("anchor", "not an anchor");
        GuideApiException anchor = assertThrows(GuideApiException.class,
                () -> comments.service.post(reader, 9L, "contextual-conditions", badAnchor));
        assertEquals("anchor", anchor.getField());
        assertTrue(comments.rows.isEmpty());
    }

    @Test
    void staleExpectedUpdatedAtConflictsWithoutSaving() {
        MemoryComments comments = new MemoryComments();
        comments.addTopic(7, "CONTEXTUAL-CONDITIONS", "PUBLIC");
        comments.addDocument("contextual-conditions", 7, true);
        User reader = user(2, false);
        comments.view(2, 7);
        long id = comments.service.post(reader, 9L, "contextual-conditions", comment("hello")).id();
        int audits = comments.audits.size();
        GuideApiException conflict = assertThrows(GuideApiException.class, () -> comments.service.patchText(reader, 9L,
                id, new JSONObject().put("text", "changed").put("expectedUpdatedAt", "2020-01-01T00:00:00Z")));
        assertEquals("conflict", conflict.getError());
        assertTrue(conflict.getCurrentJson().contains("\"text\":\"hello\""));
        assertEquals("hello", comments.row(id).text);
        assertEquals(audits, comments.audits.size());
    }

    @Test
    void inactiveDocumentRefusesCommentWrites() {
        MemoryComments comments = new MemoryComments();
        comments.addTopic(7, "CONTEXTUAL-CONDITIONS", "PUBLIC");
        comments.addDocument("contextual-conditions", 7, false);
        User reader = user(2, false);
        comments.view(2, 7);
        GuideApiException inactive = assertThrows(GuideApiException.class,
                () -> comments.service.post(reader, 9L, "contextual-conditions", comment("no")));
        assertEquals("document_inactive", inactive.getError());
    }

    @Test
    void guideTokenCannotRegisterAGuide() throws Exception {
        MemoryRegistration registration = new MemoryRegistration();
        GuideApiServlet servlet = new GuideApiServlet(new GuideCommentService(new MemoryComments().store()),
                registration.service, null);
        Captured captured = new Captured();
        HttpServletRequest request = request(captured);
        HttpServletResponse response = response(captured);
        servlet.service(request, response);
        captured.writer.flush();
        String body = captured.bytes.toString(StandardCharsets.UTF_8);
        assertEquals(403, captured.status);
        assertTrue(body.contains("insufficient_scope"));
        assertTrue(body.contains("topics:write"));
        assertEquals(0, registration.persistCount);
    }

    private static JSONObject comment(String text) {
        return new JSONObject().put("anchor", "secure-transport").put("blockIndex", 1).put("text", text);
    }

    private static JSONObject find(JSONObject listed, long id) {
        for (int i = 0; i < listed.getJSONArray("comments").length(); i++) {
            JSONObject row = listed.getJSONArray("comments").getJSONObject(i);
            if (row.getLong("id") == id) {
                return row;
            }
        }
        throw new AssertionError("missing " + id + " in " + listed);
    }

    private static User user(long id, boolean admin) {
        User user = new User();
        user.setUserId(id);
        user.setIsAdmin(admin);
        user.setDisplayName("Person " + id);
        user.setOrganization("Example");
        return user;
    }

    private static HttpServletRequest request(Captured captured) {
        User caller = user(2, false);
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "POST";
                    case "getRequestURI" -> "/api/guides";
                    case "getContextPath" -> "";
                    case "getAttribute" -> {
                        String name = (String) args[0];
                        if (GuideRequestAuth.USER.equals(name)) {
                            yield caller;
                        }
                        if (GuideRequestAuth.SCOPES.equals(name)) {
                            yield Set.of(GuideScopes.COMMENTS);
                        }
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static HttpServletResponse response(Captured captured) {
        captured.writer = new PrintWriter(new OutputStreamWriter(captured.bytes, StandardCharsets.UTF_8), true);
        return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "setStatus" -> {
                        captured.status = (Integer) args[0];
                        yield null;
                    }
                    case "setHeader", "addHeader", "setContentType", "setCharacterEncoding" -> null;
                    case "getWriter" -> captured.writer;
                    default -> defaultValue(method.getReturnType());
                });
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

    private static final class Captured {
        int status;
        final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        PrintWriter writer;
    }

    private static final class MemoryComments implements GuideCommentService.Store {
        final GuideCommentService service = new GuideCommentService(this);
        final List<DocumentRow> documents = new ArrayList<>();
        final List<TopicRow> topics = new ArrayList<>();
        final List<CommentRow> rows = new ArrayList<>();
        final List<AuditRow> audits = new ArrayList<>();
        final Set<String> viewable = new java.util.HashSet<>();
        long nextId = 1;
        GuideCommentService.Store store() {
            return this;
        }

        void addTopic(long id, String code, String visibility) {
            TopicRow topic = new TopicRow();
            topic.id = id;
            topic.code = code;
            topic.name = code;
            topic.visibility = visibility;
            topics.add(topic);
        }

        void addDocument(String key, long topicId, boolean active) {
            DocumentRow document = new DocumentRow();
            document.id = topicId;
            document.documentKey = key;
            document.topicId = topicId;
            document.title = key;
            document.active = active;
            documents.add(document);
        }

        void view(long userId, long topicId) {
            viewable.add(userId + ":" + topicId);
        }

        CommentRow row(long id) {
            return comment(id);
        }

        @Override
        public DocumentRow documentByKey(String key) {
            return documents.stream().filter(document -> key.equals(document.documentKey)).findFirst().orElse(null);
        }

        @Override
        public DocumentRow documentById(long id) {
            return documents.stream().filter(document -> document.id != null && document.id == id).findFirst().orElse(null);
        }

        @Override
        public TopicRow topic(long topicId) {
            return topics.stream().filter(topic -> topic.id == topicId).findFirst().orElse(null);
        }

        @Override
        public boolean canView(User user, TopicRow topic) {
            return user != null && viewable.contains(user.getUserId() + ":" + topic.id);
        }

        @Override
        public boolean canEdit(User user, TopicRow topic) {
            return user != null && Boolean.TRUE.equals(user.getIsAdmin());
        }

        @Override
        public Person personOf(User user) {
            return GuidePeople.of(user);
        }

        @Override
        public Map<Long, Person> people(java.util.Collection<Long> userIds) {
            Map<Long, Person> people = new HashMap<>();
            for (Long userId : userIds) {
                people.put(userId, new Person(userId, "Person " + userId, "Example"));
            }
            return people;
        }

        @Override
        public List<Disposition> dispositions() {
            return List.of(new Disposition("PERSUASIVE", "Persuasive"));
        }

        @Override
        public List<CommentRow> comments(long documentId) {
            return rows.stream().filter(row -> row.documentId == documentId).toList();
        }

        @Override
        public List<FlagRow> activeFlags(long documentId) {
            return List.of();
        }

        @Override
        public CommentRow comment(long id) {
            return rows.stream().filter(row -> row.id != null && row.id == id).findFirst().orElse(null);
        }

        @Override
        public void insertComment(CommentRow row, CommentRow parent, List<AuditRow> audits) {
            row.id = nextId++;
            rows.add(row);
            this.audits.addAll(audits);
        }

        @Override
        public void updateComment(CommentRow row, CommentRow parent, List<AuditRow> audits) {
            this.audits.addAll(audits);
        }

        @Override
        public String topicUrl(long topicId) {
            return "http://localhost:8080/hub/es/topic/" + topicId;
        }
    }

    private static final class MemoryRegistration implements GuideRegistrationService.Store {
        final GuideRegistrationService service = new GuideRegistrationService(this);
        final Map<String, DocumentRow> documents = new HashMap<>();
        final Map<String, TopicRow> topics = new HashMap<>();
        final List<AllowRow> allowlist = new ArrayList<>();
        final List<String> prefixes = new ArrayList<>();
        final List<RegistrationPlan> changes = new ArrayList<>();
        boolean localhostMode;
        boolean globalAdmin;
        int persistCount;
        long nextId = 1;
        long nextAllow = 1;

        void topic(long id, String code) {
            TopicRow topic = new TopicRow();
            topic.id = id;
            topic.code = code;
            topic.name = code;
            topic.visibility = "PUBLIC";
            topics.put(code, topic);
        }

        void allow(String url, boolean enabled) {
            AllowRow row = new AllowRow();
            row.allowId = nextAllow++;
            row.baseUrl = url;
            row.enabled = enabled;
            allowlist.add(row);
        }

        boolean allowEnabled(String url) {
            return allowlist.stream().anyMatch(row -> url.equals(row.baseUrl) && row.enabled);
        }

        DocumentRow document(String key) {
            return documents.get(key);
        }

        @Override
        public TopicRow topicByCode(String code) {
            return topics.get(code);
        }

        @Override
        public TopicRow topicById(long id) {
            return topics.values().stream().filter(topic -> topic.id == id).findFirst().orElse(null);
        }

        @Override
        public DocumentRow documentByKey(String key) {
            return documents.get(key);
        }

        @Override
        public List<DocumentRow> documentsByTopic(long topicId) {
            return documents.values().stream().filter(document -> document.topicId == topicId).toList();
        }

        @Override
        public Long documentIdForUrl(String url) {
            for (DocumentRow document : documents.values()) {
                for (var binding : document.urls) {
                    if (url.equals(binding.url)) {
                        return document.id;
                    }
                }
            }
            return null;
        }

        @Override
        public List<String> enabledPrefixes() {
            return prefixes;
        }

        @Override
        public List<AllowRow> allowlist() {
            return allowlist;
        }

        @Override
        public boolean canEdit(User user, TopicRow topic) {
            return true;
        }

        @Override
        public boolean canView(User user, TopicRow topic) {
            return true;
        }

        @Override
        public boolean globalAdmin(User user) {
            return globalAdmin && user != null && Boolean.TRUE.equals(user.getIsAdmin());
        }

        @Override
        public boolean localhostMode() {
            return localhostMode;
        }

        @Override
        public Person personOf(User user) {
            return GuidePeople.of(user);
        }

        @Override
        public List<Disposition> dispositions() {
            return List.of(new Disposition("PERSUASIVE", "Persuasive"));
        }

        @Override
        public String topicUrl(long topicId) {
            return "http://localhost:8080/hub/es/topic/" + topicId;
        }

        @Override
        public void persist(RegistrationPlan plan) {
            persistCount++;
            if (plan.document.id == null) {
                plan.document.id = nextId++;
            }
            for (AllowInsert insert : plan.inserts) {
                AllowRow row = new AllowRow();
                row.allowId = nextAllow++;
                row.baseUrl = insert.url;
                row.enabled = insert.enabled;
                allowlist.add(row);
                for (var binding : plan.urls) {
                    if (binding.insertNew && insert.url.equals(binding.url)) {
                        binding.allowId = row.allowId;
                    }
                }
            }
            for (AllowToggle toggle : plan.toggles) {
                for (AllowRow row : allowlist) {
                    if (toggle.allowId == row.allowId) {
                        row.enabled = toggle.enabled;
                    }
                }
            }
            plan.document.urls = plan.urls;
            documents.put(plan.document.documentKey, plan.document);
            if (plan.changesJson != null) {
                changes.add(plan);
            }
        }
    }
}
