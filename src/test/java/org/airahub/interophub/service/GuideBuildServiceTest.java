package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideBuildService.Impact;
import org.airahub.interophub.service.GuideBuildService.State;
import org.airahub.interophub.service.GuideBuildService.StatusInputs;
import org.airahub.interophub.service.GuideBuildService.StatusReport;
import org.airahub.interophub.service.GuideRows.BuildRow;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.airahub.interophub.servlet.GuideApiServlet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class GuideBuildServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 12, 0);

    // ------------------------------------------------------------ manifest-only endpoint

    @Test
    void reportStoresTheManifestForExternalAndHubGuides() {
        Memory memory = memory();
        JSONObject created = memory.service.report(user(5), "external-guide", body("v0.21.0", "v0.4.0"));
        assertEquals(Set.of("buildId", "documentKey", "processVersion", "receivedAt"), created.keySet());
        assertEquals(1L, created.getLong("buildId"));
        assertEquals("external-guide", created.getString("documentKey"));
        assertEquals("v0.21.0", created.getString("processVersion"));
        assertTrue(created.getString("receivedAt").endsWith("Z"));
        BuildRow row = memory.saved.get(0);
        assertEquals("MANIFEST", row.source);
        assertEquals("v0.4.0", row.label);
        assertNull(row.hostedVersionId);
        assertEquals("v0.4.0", row.documentVersion);
        assertEquals(1, row.warningCount);
        assertEquals(5L, row.receivedByUserId);

        JSONObject hub = memory.service.report(user(5), "hub-guide", body("v0.21.0", null));
        assertEquals("hub-guide", hub.getString("documentKey"));
        assertNull(memory.saved.get(1).label);
    }

    @Test
    void reportPermissionsMatchTheUpload() {
        Memory memory = memory();
        assertError("not_found", () -> memory.service.report(user(5), "no-such-guide", body("v0.21.0", null)));
        memory.viewers.add(2L);
        assertError("forbidden", () -> memory.service.report(user(2), "external-guide", body("v0.21.0", null)));
        memory.documents.get("external-guide").active = false;
        assertError("document_inactive", () -> memory.service.report(user(5), "external-guide", body("v0.21.0", null)));
        // Permission is checked before the body, so a reader learns nothing from a bad manifest.
        assertError("forbidden", () -> memory.service.report(user(2), "external-guide", new JSONObject()));
        assertTrue(memory.saved.isEmpty());
    }

    @Test
    void reportRefusesAMissingUnknownOrOversizedManifest() {
        Memory memory = memory();
        assertError("invalid_request", () -> memory.service.report(user(5), "external-guide", new JSONObject()));
        assertError("invalid_request", () -> memory.service.report(user(5), "external-guide",
                new JSONObject().put("manifest", "not an object")));
        assertError("invalid_request", () -> memory.service.report(user(5), "external-guide",
                new JSONObject().put("manifest", new JSONObject().put("schema", 2).put("processVersion", "v1.0.0"))));
        assertError("invalid_request", () -> memory.service.report(user(5), "external-guide",
                new JSONObject().put("manifest", manifest("v0.21.0").put("pad", "x".repeat(GuideBuildManifest.MAX_BYTES)))));
        assertError("invalid_request", () -> memory.service.report(user(5), "external-guide",
                body("v0.21.0", "x".repeat(65))));
        assertTrue(memory.saved.isEmpty());
    }

    @Test
    void endpointUsesTheUploadScopeAndTheContractErrorShape() throws Exception {
        Memory memory = memory();
        GuideApiServlet servlet = new GuideApiServlet(null, null, null, null, memory.service);

        Captured wrongScope = exchange(servlet, "POST", "/api/guide/external-guide/builds",
                Set.of(GuideScopes.COMMENTS, GuideScopes.TOPICS_READ), user(5), body("v0.21.0", null).toString());
        assertEquals(403, wrongScope.status);
        assertTrue(wrongScope.body().contains("insufficient_scope"));

        Captured badJson = exchange(servlet, "POST", "/api/guide/external-guide/builds",
                Set.of(GuideScopes.TOPICS_WRITE), user(5), "{not json");
        assertEquals(400, badJson.status);
        assertEquals("invalid_request", new JSONObject(badJson.body()).getString("error"));

        Captured created = exchange(servlet, "POST", "/api/guide/external-guide/builds",
                Set.of(GuideScopes.TOPICS_WRITE), user(5), body("v0.21.0", "v0.4.0").toString());
        assertEquals(201, created.status);
        assertEquals("v0.21.0", new JSONObject(created.body()).getString("processVersion"));

        Captured forbidden = exchange(servlet, "GET", "/api/guide/external-guide/builds",
                Set.of(GuideScopes.TOPICS_READ), user(2), "");
        assertEquals(403, forbidden.status);
        assertEquals("forbidden", new JSONObject(forbidden.body()).getString("error"));

        Captured missing = exchange(servlet, "GET", "/api/guide/no-such-guide/builds",
                Set.of(GuideScopes.TOPICS_READ), user(5), "");
        assertEquals(404, missing.status);
    }

    @Test
    void historyIsNewestFirstForTopicEditorsOnly() {
        Memory memory = memory();
        memory.service.report(user(5), "external-guide", body("v0.20.0", "v0.3.0"));
        memory.service.report(user(5), "external-guide", body("v0.21.0", "v0.4.0"));
        JSONArray builds = memory.service.history(user(5), "external-guide").getJSONArray("builds");
        assertEquals(2, builds.length());
        assertEquals("v0.21.0", builds.getJSONObject(0).getString("processVersion"));
        assertEquals("v0.20.0", builds.getJSONObject(1).getString("processVersion"));
        assertEquals("MANIFEST", builds.getJSONObject(0).getString("source"));
        assertEquals("v0.21.0", builds.getJSONObject(0).getJSONObject("manifest").getString("processVersion"));
        memory.viewers.add(2L);
        assertError("forbidden", () -> memory.service.history(user(2), "external-guide"));
    }

    // ------------------------------------------------------------ status

    @Test
    void latestReleaseAndReleaseListComeFromTheHighestReportedManifest() {
        StatusInputs inputs = inputs("alpha", "beta", "gamma");
        inputs.latestBuilds.put(1L, build(1, "v0.21.0", releases(r("v0.21.0", "rebuild"))));
        inputs.latestBuilds.put(2L, build(2, "v0.22.0",
                releases(r("v0.22.0", "content"), r("v0.21.0", "rebuild"))));
        // An unreadable build never sets the latest release, whatever it claims.
        BuildRow broken = build(3, "v9.0.0", releases(r("v9.0.0", "rebuild")));
        broken.manifestError = "Unknown build manifest schema: 2.";
        inputs.latestBuilds.put(3L, broken);

        StatusReport report = GuideBuildService.status(inputs, NOW);
        assertEquals("v0.22.0", report.latestVersion);
        assertEquals("beta", report.releaseSourceKey);
        assertEquals(List.of("v0.22.0", "v0.21.0"), report.releases.stream().map(GuideBuildService.Release::version).toList());
    }

    @Test
    void behindCountAndMostDemandingImpact() {
        StatusInputs inputs = inputs("current", "one-behind", "content-behind", "config-behind");
        String list = releases(r("v0.24.0", "rebuild"), r("v0.23.0", "config"), r("v0.22.0", "content"),
                r("v0.21.0", "rebuild"));
        inputs.latestBuilds.put(1L, build(1, "v0.24.0", list));
        inputs.latestBuilds.put(2L, build(2, "v0.23.0", list));
        inputs.latestBuilds.put(3L, build(3, "v0.21.0", list));
        inputs.latestBuilds.put(4L, build(4, "v0.22.0", list));

        Map<String, GuideBuildService.GuideStatus> byKey = byKey(GuideBuildService.status(inputs, NOW));
        assertEquals(State.CURRENT, byKey.get("current").state);
        assertEquals(0, byKey.get("current").behind);
        assertNull(byKey.get("current").impact);

        assertEquals(State.BEHIND, byKey.get("one-behind").state);
        assertEquals(1, byKey.get("one-behind").behind);
        assertEquals(Impact.REBUILD, byKey.get("one-behind").impact);

        assertEquals(3, byKey.get("content-behind").behind);
        assertEquals(Impact.CONTENT, byKey.get("content-behind").impact);

        assertEquals(2, byKey.get("config-behind").behind);
        assertEquals(Impact.CONFIG, byKey.get("config-behind").impact);
    }

    @Test
    void aVersionMissingFromTheReleaseListShowsNoBehindCount() {
        StatusInputs inputs = inputs("listed", "unlisted");
        String list = releases(r("v0.22.0", "content"), r("v0.21.0", "rebuild"));
        inputs.latestBuilds.put(1L, build(1, "v0.22.0", list));
        inputs.latestBuilds.put(2L, build(2, "v0.20.5", releases(r("v0.20.5", "rebuild"))));

        GuideBuildService.GuideStatus unlisted = byKey(GuideBuildService.status(inputs, NOW)).get("unlisted");
        assertEquals(State.UNKNOWN_VERSION, unlisted.state);
        assertEquals("v0.20.5", unlisted.processVersion);
        assertNull(unlisted.behind);
        assertNull(unlisted.impact);
        assertFalse(unlisted.needsUpgrade());
    }

    @Test
    void registeredGuidesWithNoBuildsShowNoStatusReported() {
        StatusInputs inputs = inputs("never", "also-never", "inactive");
        inputs.documents.get(2).active = false;
        StatusReport report = GuideBuildService.status(inputs, NOW);
        assertEquals(2, report.guides.size());
        assertTrue(report.guides.stream().allMatch(guide -> guide.state == State.NO_STATUS));
        assertNull(report.latestVersion);
        assertTrue(report.releases.isEmpty());
        assertEquals(2, report.count(State.NO_STATUS));
        assertTrue(report.guides.stream().allMatch(guide -> guide.stale));
    }

    @Test
    void uploadsWithoutAManifestAreBehindEveryKnownRelease() {
        StatusInputs inputs = inputs("reported", "old-upload");
        inputs.latestBuilds.put(1L, build(1, "v0.22.0", releases(r("v0.22.0", "content"), r("v0.21.0", "rebuild"))));
        BuildRow noManifest = new BuildRow();
        noManifest.documentId = 2;
        noManifest.source = "UPLOAD";
        noManifest.receivedAt = NOW.minusDays(3);
        inputs.latestBuilds.put(2L, noManifest);
        GuideBuildService.GuideStatus old = byKey(GuideBuildService.status(inputs, NOW)).get("old-upload");
        assertEquals(State.NO_MANIFEST, old.state);
        assertEquals(2, old.behind);
        assertEquals(Impact.CONTENT, old.impact);
        assertTrue(old.needsUpgrade());
    }

    @Test
    void sortOrderAndCounts() {
        StatusInputs inputs = inputs("current-many-warnings", "behind-two", "never", "unreadable", "behind-one",
                "current-no-warnings");
        String list = releases(r("v0.23.0", "rebuild"), r("v0.22.0", "content"), r("v0.21.0", "rebuild"));
        inputs.latestBuilds.put(1L, warned(build(1, "v0.23.0", list), 3));
        inputs.latestBuilds.put(2L, build(2, "v0.21.0", list));
        BuildRow unreadable = build(4, null, null);
        unreadable.manifestJson = null;
        unreadable.manifestError = "The build manifest is not valid JSON.";
        inputs.latestBuilds.put(4L, unreadable);
        inputs.latestBuilds.put(5L, build(5, "v0.22.0", list));
        inputs.latestBuilds.put(6L, build(6, "v0.23.0", list));
        inputs.lastHostedUploads.put(6L, NOW.minusDays(90));
        inputs.latestBuilds.get(6L).receivedAt = NOW.minusDays(61);

        StatusReport report = GuideBuildService.status(inputs, NOW);
        assertEquals(List.of("never", "unreadable", "behind-two", "behind-one", "current-many-warnings",
                "current-no-warnings"), report.guides.stream().map(guide -> guide.documentKey).toList());
        assertEquals(2, report.count(State.CURRENT));
        assertEquals(2, report.needsUpgrade());
        assertEquals(1, report.needsUpgrade(Impact.CONTENT));
        assertEquals(1, report.needsUpgrade(Impact.REBUILD));
        assertEquals(1, report.withWarnings());
        assertEquals(List.of("CHAPTER_COUNT"), report.warningCodes);
        assertEquals("The build manifest is not valid JSON.", byKey(report).get("unreadable").error);
        assertTrue(byKey(report).get("current-no-warnings").stale);
        assertFalse(byKey(report).get("current-many-warnings").stale);
    }

    @Test
    void versionsCompareNumericallyAndPreReleasesSortFirst() {
        assertTrue(GuideBuildService.Version.parse("v0.10.0").compareTo(GuideBuildService.Version.parse("v0.9.0")) > 0);
        assertTrue(GuideBuildService.Version.parse("v1.0.0-rc1").compareTo(GuideBuildService.Version.parse("v1.0.0")) < 0);
        assertEquals(0, GuideBuildService.Version.parse("v0.21").compareTo(GuideBuildService.Version.parse("v0.21.0")));
        assertNull(GuideBuildService.Version.parse("main"));
    }

    // ------------------------------------------------------------ helpers

    static String r(String version, String impact) {
        return new JSONObject().put("version", version).put("date", "2026-11-24").put("upgradeImpact", impact)
                .put("summary", "Release " + version).toString();
    }

    static String releases(String... items) {
        return "[" + String.join(",", items) + "]";
    }

    static BuildRow build(long documentId, String processVersion, String releasesJson) {
        BuildRow row = new BuildRow();
        row.id = documentId * 10;
        row.documentId = documentId;
        row.source = "MANIFEST";
        row.manifestSchema = 1;
        row.processVersion = processVersion;
        row.documentVersion = "v0.1.0";
        row.documentStatus = "Draft";
        row.builtAt = NOW.minusDays(1);
        row.receivedAt = NOW.minusDays(1);
        row.warningCount = 0;
        JSONObject manifest = new JSONObject().put("schema", 1).put("processVersion", processVersion == null ? "" : processVersion);
        if (releasesJson != null) {
            manifest.put("processReleases", new JSONArray(releasesJson));
        }
        manifest.put("checks", new JSONObject().put("warnings", new JSONArray()));
        row.manifestJson = manifest.toString();
        return row;
    }

    static BuildRow warned(BuildRow row, int count) {
        JSONObject manifest = new JSONObject(row.manifestJson);
        JSONArray warnings = new JSONArray();
        for (int i = 0; i < count; i++) {
            warnings.put(new JSONObject().put("code", "CHAPTER_COUNT").put("message", "Warning " + i));
        }
        manifest.getJSONObject("checks").put("warnings", warnings);
        row.manifestJson = manifest.toString();
        row.warningCount = count;
        return row;
    }

    static StatusInputs inputs(String... keys) {
        StatusInputs inputs = new StatusInputs();
        TopicRow topic = new TopicRow();
        topic.id = 8;
        topic.code = "TOPIC";
        topic.name = "Topic";
        inputs.topics.put(8L, topic);
        long id = 1;
        for (String key : keys) {
            DocumentRow document = new DocumentRow();
            document.id = id;
            document.documentKey = key;
            document.title = key;
            document.topicId = 8;
            document.active = true;
            document.hosting = "EXTERNAL";
            inputs.documents.add(document);
            inputs.documentKeys.put(id, key);
            id++;
        }
        return inputs;
    }

    private static Map<String, GuideBuildService.GuideStatus> byKey(StatusReport report) {
        Map<String, GuideBuildService.GuideStatus> map = new HashMap<>();
        for (GuideBuildService.GuideStatus guide : report.guides) {
            map.put(guide.documentKey, guide);
        }
        return map;
    }

    static JSONObject manifest(String processVersion) {
        return new JSONObject()
                .put("schema", 1)
                .put("generator", "content-publishing-templates")
                .put("processVersion", processVersion)
                .put("processReleases", new JSONArray(releases(r(processVersion, "rebuild"))))
                .put("document", new JSONObject().put("id", "external-guide").put("version", "v0.4.0").put("status", "Draft"))
                .put("build", new JSONObject().put("builtAt", "2026-11-24T18:03:11Z"))
                .put("checks", new JSONObject().put("warnings", new JSONArray()
                        .put(new JSONObject().put("code", "CHAPTER_COUNT").put("message", "8 chapters"))));
    }

    private static JSONObject body(String processVersion, String label) {
        JSONObject body = new JSONObject().put("manifest", manifest(processVersion));
        if (label != null) {
            body.put("label", label);
        }
        return body;
    }

    private static void assertError(String error, org.junit.jupiter.api.function.Executable action) {
        GuideApiException ex = assertThrows(GuideApiException.class, action);
        assertEquals(error, ex.getError());
    }

    private static User user(long id) {
        User user = new User();
        user.setUserId(id);
        return user;
    }

    private static Memory memory() {
        Memory memory = new Memory();
        TopicRow topic = new TopicRow();
        topic.id = 8;
        memory.topics.put(8L, topic);
        memory.add(1L, "external-guide", "EXTERNAL");
        memory.add(2L, "hub-guide", "HUB");
        memory.editors.add(5L);
        return memory;
    }

    private static final class Memory implements GuideBuildService.Store {
        final GuideBuildService service = new GuideBuildService(this);
        final Map<String, DocumentRow> documents = new HashMap<>();
        final Map<Long, TopicRow> topics = new HashMap<>();
        final Set<Long> editors = new HashSet<>();
        final Set<Long> viewers = new HashSet<>();
        final List<BuildRow> saved = new ArrayList<>();

        void add(long id, String key, String hosting) {
            DocumentRow document = new DocumentRow();
            document.id = id;
            document.documentKey = key;
            document.topicId = 8;
            document.active = true;
            document.hosting = hosting;
            documents.put(key, document);
        }

        @Override
        public DocumentRow documentByKey(String key) {
            return documents.get(key);
        }

        @Override
        public TopicRow topicById(long id) {
            return topics.get(id);
        }

        @Override
        public boolean canEdit(User user, TopicRow topic) {
            return user != null && editors.contains(user.getUserId());
        }

        @Override
        public void save(BuildRow row) {
            row.id = (long) saved.size() + 1;
            row.receivedAt = LocalDateTime.of(2026, 11, 24, 10, 0).plusMinutes(saved.size());
            saved.add(row);
        }

        @Override
        public List<BuildRow> builds(long documentId) {
            return saved.stream().filter(row -> row.documentId == documentId)
                    .sorted(Comparator.comparing((BuildRow row) -> row.receivedAt).reversed()).toList();
        }

        @Override
        public StatusInputs statusInputs() {
            return new StatusInputs();
        }
    }

    // ------------------------------------------------------------ servlet plumbing

    private static Captured exchange(GuideApiServlet servlet, String method, String uri, Set<String> scopes, User caller,
            String body) throws Exception {
        Captured captured = new Captured();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getContentType" -> "application/json";
                    case "getInputStream" -> new ServletInputStream() {
                        private final ByteArrayInputStream input = new ByteArrayInputStream(bytes);
                        public int read() { return input.read(); }
                        public boolean isFinished() { return input.available() == 0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) { }
                    };
                    case "getAttribute" -> {
                        String name = (String) args[0];
                        if (GuideRequestAuth.USER.equals(name)) {
                            yield caller;
                        }
                        if (GuideRequestAuth.SCOPES.equals(name)) {
                            yield scopes;
                        }
                        yield null;
                    }
                    default -> defaultValue(m.getReturnType());
                });
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(), new Class<?>[] { HttpServletResponse.class },
                (proxy, m, args) -> switch (m.getName()) {
                    case "setStatus", "sendError" -> {
                        captured.status = (Integer) args[0];
                        yield null;
                    }
                    case "getWriter" -> {
                        if (captured.writer == null) {
                            captured.writer = new PrintWriter(new OutputStreamWriter(captured.bytes, StandardCharsets.UTF_8), true);
                        }
                        yield captured.writer;
                    }
                    default -> defaultValue(m.getReturnType());
                });
        servlet.service(request, response);
        return captured;
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
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintWriter writer;

        String body() {
            if (writer != null) {
                writer.flush();
            }
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
