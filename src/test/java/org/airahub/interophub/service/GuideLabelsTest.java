package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.Collection;
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
import org.airahub.interophub.service.GuideRows.AuditRow;
import org.airahub.interophub.service.GuideRows.BuildRow;
import org.airahub.interophub.service.GuideRows.CommentRow;
import org.airahub.interophub.service.GuideRows.Disposition;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.FlagRow;
import org.airahub.interophub.service.GuideRows.HostedVersion;
import org.airahub.interophub.service.GuideRows.LabelRow;
import org.airahub.interophub.service.GuideRows.Person;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.airahub.interophub.service.GuideRows.UrlBinding;
import org.airahub.interophub.service.TopicReferenceSectionData.Guide;
import org.airahub.interophub.service.TopicReferenceSectionData.LabelChoice;
import org.airahub.interophub.service.TopicReferenceSectionData.Link;
import org.airahub.interophub.servlet.GuideApiServlet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** Plan docs/tasks/topic-reference-version-display-and-comment-modes.md, Section 7. */
class GuideLabelsTest {
    private static final String HUB = "http://localhost:8080/hub";
    private static final String KEY = "guide-hosted-test";
    private static final LocalDateTime PUBLISHED = LocalDateTime.of(2026, 11, 10, 12, 0);

    private final User reader = user(2);
    private final User editor = user(9);
    private final User outsider = user(3);

    // ---- Label resolution (plan 3.3) ----

    @Test
    void hostedPathGivesItsLabelAndIgnoresQueryFragmentAndHostCase() {
        List<UrlBinding> urls = hostedUrls();
        assertEquals("v0.2.0", GuideLabels.resolve(HUB + "/guide/view/" + KEY + "/v0.2.0/?interophub=signin#comment-4",
                KEY, HUB, urls));
        assertEquals("main", GuideLabels.resolve(HUB + "/guide/view/" + KEY + "/main/", KEY, HUB + "/", urls));
        assertEquals("v1.0.0-rc1", GuideLabels.resolve("HTTP://LOCALHOST:8080/hub/guide/view/" + KEY + "/v1.0.0-rc1",
                KEY, HUB, urls));
        assertNull(GuideLabels.resolve(HUB + "/guide/view/" + KEY + "/not-a-label/", KEY, HUB, urls));
        assertNull(GuideLabels.resolve(HUB + "/guide/view/other-guide/latest/", KEY, HUB, urls));
        assertNull(GuideLabels.resolve(HUB + "/guide/view/" + KEY.toUpperCase() + "/latest/", KEY, HUB, urls));
    }

    @Test
    void externalPagesUseTheLongestRegisteredPrefix() {
        List<UrlBinding> urls = List.of(
                new UrlBinding("https://example.github.io/cc/", "Release"),
                new UrlBinding("https://example.github.io/cc/main/", "Preview"));
        assertEquals("Preview", GuideLabels.resolve("https://example.github.io/cc/main/section.html#a", "cc", HUB, urls));
        assertEquals("Release", GuideLabels.resolve("https://example.github.io/cc/index.html?x=1", "cc", HUB, urls));
        assertEquals("Release", GuideLabels.resolve("https://example.github.io/cc", "cc", HUB, urls));
        assertEquals("Release", GuideLabels.resolve("https://EXAMPLE.github.io/cc/mainly/", "cc", HUB, urls));
        assertNull(GuideLabels.resolve("https://example.github.io/other/", "cc", HUB, urls));
        assertNull(GuideLabels.resolve("https://example.github.io/CC/", "cc", HUB, urls));
    }

    @Test
    void unknownBlankAndMissingAddressesResolveToNothing() {
        assertNull(GuideLabels.resolve(null, KEY, HUB, hostedUrls()));
        assertNull(GuideLabels.resolve("   ", KEY, HUB, hostedUrls()));
        assertNull(GuideLabels.resolve("not a url", KEY, HUB, hostedUrls()));
        assertNull(GuideLabels.resolve(HUB + "/guide/view/" + KEY + "/latest/", KEY, null, List.of()));
    }

    // ---- State rules ----

    @Test
    void labelsWithoutARowAreUnlistedAndOpenAndUnknownLabelsHaveNoPolicy() {
        World world = world();
        GuideLabels.Facts facts = world.labelFacts(11);
        GuideLabels.Policy policy = facts.policy("latest");
        assertEquals(GuideLabels.UNLISTED, policy.display());
        assertEquals(GuideLabels.OPEN, policy.commentMode());
        assertNull(facts.policy("v9.9.9"));
        assertNull(facts.current());
        assertEquals(List.of("latest", "main", "v0.3.0", "v0.2.0", "v0.1.0"), facts.known());
    }

    @Test
    void makingALabelCurrentTurnsThePreviousCurrentIntoListed() {
        World world = world();
        JSONObject first = world.labels.patch(editor, 5L, "API", KEY, "v0.2.0",
                new JSONObject().put("display", "Current"));
        assertEquals("current", first.getString("display"));
        assertTrue(first.isNull("demoted"));

        JSONObject second = world.labels.patch(editor, 5L, "API", KEY, "v0.3.0",
                new JSONObject().put("display", "current").put("commentMode", "release"));
        assertEquals("current", second.getString("display"));
        assertEquals("release", second.getString("commentMode"));
        assertEquals("v0.2.0", second.getJSONObject("demoted").getString("label"));
        assertEquals("listed", second.getJSONObject("demoted").getString("display"));
        assertEquals(GuideLabels.LISTED, world.labelRows.get("v0.2.0").display);
        long currents = world.labelRows.values().stream().filter(row -> GuideLabels.CURRENT.equals(row.display)).count();
        assertEquals(1, currents);

        JSONObject change = new JSONObject(world.changes.get(1)).getJSONObject("label");
        assertEquals("v0.3.0", change.getString("label"));
        assertEquals("unlisted", change.getJSONObject("display").getString("from"));
        assertEquals("current", change.getJSONObject("display").getString("to"));
        assertEquals("v0.2.0", change.getJSONObject("demoted").getString("label"));
        assertEquals("API", world.changeSources.get(1));

        world.labels.patch(editor, 5L, "API", KEY, "v0.3.0", new JSONObject().put("display", "current"));
        assertEquals(2, world.changes.size(), "an unchanged PATCH writes no change record");
    }

    @Test
    void patchRefusesBadInputUnknownLabelsAndNonEditors() {
        World world = world();
        assertEquals("invalid_request", assertThrows(GuideApiException.class,
                () -> world.labels.patch(editor, 5L, "API", KEY, "main", new JSONObject())).getError());
        GuideApiException unknownField = assertThrows(GuideApiException.class,
                () -> world.labels.patch(editor, 5L, "API", KEY, "main", new JSONObject().put("state", "x")));
        assertEquals("invalid_field", unknownField.getError());
        assertEquals("state", unknownField.getField());
        assertEquals("display", assertThrows(GuideApiException.class, () -> world.labels.patch(editor, 5L, "API", KEY,
                "main", new JSONObject().put("display", "hidden"))).getField());
        assertEquals("commentMode", assertThrows(GuideApiException.class, () -> world.labels.patch(editor, 5L, "API",
                KEY, "main", new JSONObject().put("commentMode", JSONObject.NULL))).getField());
        GuideApiException missing = assertThrows(GuideApiException.class, () -> world.labels.patch(editor, 5L, "API",
                KEY, "v9.9.9", new JSONObject().put("display", "listed")));
        assertEquals(404, missing.getStatus());
        assertEquals("not_found", missing.getError());
        GuideApiException readerRefused = assertThrows(GuideApiException.class, () -> world.labels.patch(reader, 6L,
                "API", KEY, "main", new JSONObject().put("display", "listed")));
        assertEquals(403, readerRefused.getStatus());
        assertEquals("forbidden", readerRefused.getError());
        assertTrue(world.labelRows.isEmpty());
    }

    @Test
    void labelsListHidesAuthorsOnlyRetiredAndCountsFromReaders() {
        World world = world();
        world.setLabel("v0.1.0", GuideLabels.RETIRED, GuideLabels.OPEN);
        world.setLabel("v0.3.0", GuideLabels.AUTHORS_ONLY, GuideLabels.OPEN);
        world.setLabel("v0.2.0", GuideLabels.CURRENT, GuideLabels.RELEASE);
        world.comment(1, null, "OPEN", PUBLISHED.minusDays(1));

        JSONArray readerLabels = world.labels.list(reader, KEY).getJSONArray("labels");
        assertEquals(List.of("latest", "main", "v0.2.0"), names(readerLabels));
        assertFalse(readerLabels.getJSONObject(0).has("openInScope"));

        JSONArray editorLabels = world.labels.list(editor, KEY).getJSONArray("labels");
        assertEquals(List.of("latest", "main", "v0.3.0", "v0.2.0", "v0.1.0"), names(editorLabels));
        JSONObject current = editorLabels.getJSONObject(3);
        assertEquals("current", current.getString("display"));
        assertEquals("release", current.getString("commentMode"));
        assertEquals(ApiTime.format(PUBLISHED), current.getString("publishedAt"));
        assertEquals(1, current.getInt("openInScope"));

        assertEquals("forbidden", assertThrows(GuideApiException.class,
                () -> world.labels.list(outsider, KEY)).getError());
    }

    // ---- Comment scope (plan 3.2) ----

    @Test
    void releaseShowsReadersOnlyCommentsInScopeAndRepliesFollowTheirThread() {
        World world = world();
        world.setLabel("v0.2.0", GuideLabels.CURRENT, GuideLabels.RELEASE);
        long tagged = world.comment(1, "v0.2.0", "OPEN", PUBLISHED.plusDays(1)).id;
        long untaggedBefore = world.comment(1, null, "OPEN", PUBLISHED.minusDays(1)).id;
        long untaggedAfter = world.comment(1, null, "OPEN", PUBLISHED.plusDays(1)).id;
        long otherAfter = world.comment(1, "main", "DISPOSED", PUBLISHED.plusDays(2)).id;
        long otherBefore = world.comment(1, "main", "OPEN", PUBLISHED.minusHours(1)).id;
        world.reply(untaggedAfter, "v0.2.0", PUBLISHED.plusDays(3));
        long followingReply = world.reply(untaggedBefore, "main", PUBLISHED.plusDays(3)).id;
        world.flag("a-before", null, PUBLISHED.minusDays(2));
        world.flag("a-after", "main", PUBLISHED.plusDays(2));

        JSONObject asReader = world.comments.list(reader, KEY, "all", null, null, "v0.2.0", null);
        assertEquals(Set.of(tagged, untaggedBefore, otherBefore), ids(asReader));
        for (int i = 0; i < asReader.getJSONArray("comments").length(); i++) {
            assertTrue(asReader.getJSONArray("comments").getJSONObject(i).getBoolean("inScope"));
        }
        JSONObject policy = asReader.getJSONObject("labelPolicy");
        assertEquals("v0.2.0", policy.getString("label"));
        assertEquals("current", policy.getString("display"));
        assertEquals("release", policy.getString("commentMode"));
        assertEquals(ApiTime.format(PUBLISHED), policy.getString("publishedAt"));
        JSONObject withReply = find(asReader, untaggedBefore);
        assertEquals(followingReply, withReply.getJSONArray("replies").getJSONObject(0).getLong("id"));
        assertEquals("main", withReply.getJSONArray("replies").getJSONObject(0).getString("madeOnLabel"));
        assertEquals(List.of("a-before"), anchors(asReader.getJSONArray("reviewFlags")));

        JSONObject asEditor = world.comments.list(editor, KEY, "all", null, null, "v0.2.0", null);
        assertEquals(Set.of(tagged, untaggedBefore, untaggedAfter, otherAfter, otherBefore), ids(asEditor));
        assertFalse(find(asEditor, untaggedAfter).getBoolean("inScope"));
        assertFalse(find(asEditor, otherAfter).getBoolean("inScope"));
        assertTrue(find(asEditor, tagged).getBoolean("inScope"));
        assertEquals(2, asEditor.getJSONArray("reviewFlags").length());
        assertFalse(flagByAnchor(asEditor, "a-after").getBoolean("inScope"));
        assertTrue(flagByAnchor(asEditor, "a-before").getBoolean("inScope"));

        JSONObject byPageUrl = world.comments.list(reader, KEY, "all", null, null, null,
                HUB + "/guide/view/" + KEY + "/v0.2.0/#comment-1");
        assertEquals(ids(asReader), ids(byPageUrl));

        JSONObject sinceReader = world.comments.list(reader, KEY, null, null, "2026-01-01T00:00:00Z", "v0.2.0", null);
        assertEquals(Set.of(tagged, untaggedBefore, otherBefore), ids(sinceReader));
    }

    @Test
    void releaseWithoutUploadOrBuildCountsFromWhenReleaseWasSet() {
        World world = world();
        world.urls.add(new UrlBinding(HUB + "/guide/view/" + KEY + "/preview/", "preview"));
        world.labels.patch(editor, 5L, "API", KEY, "preview", new JSONObject().put("commentMode", "release"));
        LabelRow row = world.labelRows.get("preview");
        assertNotNull(row.commentModeSetAt);
        assertEquals(row.commentModeSetAt, world.labelFacts(11).publishedAt("preview"));
        world.labels.patch(editor, 5L, "API", KEY, "preview", new JSONObject().put("commentMode", "open"));
        assertNull(world.labelFacts(11).publishedAt("preview"));
    }

    // ---- Closed (V8, V9) ----

    @Test
    void closedShowsNothingRefusesWritesAndCanBeSetWithOpenComments() {
        World world = world();
        long open = world.comment(1, "v0.2.0", "OPEN", PUBLISHED.plusDays(1)).id;
        long other = world.comment(1, "main", "OPEN", PUBLISHED.plusDays(1)).id;
        world.flag("a-flag", "v0.2.0", PUBLISHED.plusDays(1));

        JSONObject closed = world.labels.patch(editor, 5L, "API", KEY, "v0.2.0",
                new JSONObject().put("commentMode", "closed"));
        assertEquals("closed", closed.getString("commentMode"));
        assertEquals(1, closed.getInt("openInScope"), "information only; closing still succeeds");

        for (User viewer : List.of(reader, editor)) {
            JSONObject listed = world.comments.list(viewer, KEY, "all", null, null, "v0.2.0", null);
            assertEquals(0, listed.getJSONArray("comments").length());
            assertEquals(0, listed.getJSONArray("reviewFlags").length());
            assertEquals("closed", listed.getJSONObject("labelPolicy").getString("commentMode"));
        }
        assertEquals(Set.of(open, other), ids(world.comments.list(reader, KEY, "all", null, null, "main", null)));
        JSONObject unfiltered = world.comments.list(editor, KEY, "all", null, null);
        assertEquals(Set.of(open, other), ids(unfiltered));
        assertEquals(1, unfiltered.getJSONArray("reviewFlags").length());
        assertEquals(Set.of(open), world.comments.threadsInScope(editor, KEY, "v0.2.0"));

        String closedPage = HUB + "/guide/view/" + KEY + "/v0.2.0/";
        GuideApiException post = assertThrows(GuideApiException.class, () -> world.comments.post(reader, 6L, KEY,
                comment("on a closed version").put("pageUrl", closedPage)));
        assertEquals(403, post.getStatus());
        assertEquals("comments_closed", post.getError());
        GuideApiException reply = assertThrows(GuideApiException.class, () -> world.comments.post(reader, 6L, KEY,
                new JSONObject().put("parentCommentId", other).put("text", "no").put("pageUrl", closedPage)));
        assertEquals("comments_closed", reply.getError());
        GuideApiException flag = assertThrows(GuideApiException.class, () -> world.comments.flag(editor, KEY,
                new JSONObject().put("anchor", "x").put("note", "look").put("pageUrl", closedPage)));
        assertEquals("comments_closed", flag.getError());

        JSONObject allowed = world.comments.post(reader, 6L, KEY, new JSONObject().put("parentCommentId", open)
                .put("text", "replying from main").put("pageUrl", HUB + "/guide/view/" + KEY + "/main/")).thread();
        assertEquals("main", allowed.getJSONArray("replies").getJSONObject(0).getString("madeOnLabel"));
    }

    // ---- API compatibility ----

    @Test
    void writesRecordTheLabelAndOlderGuidesStillWork() {
        World world = world();
        JSONObject older = world.comments.post(reader, 6L, KEY, comment("no pageUrl")).thread();
        assertTrue(older.has("madeOnLabel"));
        assertTrue(older.isNull("madeOnLabel"));
        assertFalse(older.has("inScope"));

        JSONObject tagged = world.comments.post(reader, 6L, KEY, comment("with pageUrl")
                .put("pageUrl", HUB + "/guide/view/" + KEY + "/latest/?interophub=signin")).thread();
        assertEquals("latest", tagged.getString("madeOnLabel"));
        JSONObject unrecognized = world.comments.post(reader, 6L, KEY, comment("elsewhere")
                .put("pageUrl", "https://elsewhere.example/page")).thread();
        assertTrue(unrecognized.isNull("madeOnLabel"));
        assertEquals("pageUrl", assertThrows(GuideApiException.class, () -> world.comments.post(reader, 6L, KEY,
                comment("long").put("pageUrl", "https://x.example/" + "a".repeat(2001)))).getField());

        JSONObject flag = world.comments.flag(editor, KEY, new JSONObject().put("anchor", "a-1").put("note", "n")
                .put("pageUrl", HUB + "/guide/view/" + KEY + "/main/")).flag();
        assertEquals("main", flag.getString("madeOnLabel"));

        JSONObject plain = world.comments.list(reader, KEY, null, null, null);
        assertFalse(plain.has("labelPolicy"));
        assertFalse(plain.getJSONArray("comments").getJSONObject(0).has("inScope"));
        assertFalse(plain.getJSONArray("reviewFlags").getJSONObject(0).has("inScope"));
        assertEquals(3, plain.getJSONArray("comments").length());

        JSONObject unknown = world.comments.list(reader, KEY, null, null, null, "v9.9.9", null);
        assertTrue(unknown.has("labelPolicy"));
        assertTrue(unknown.isNull("labelPolicy"));
        assertEquals(3, unknown.getJSONArray("comments").length());
        assertFalse(unknown.getJSONArray("comments").getJSONObject(0).has("inScope"));

        JSONObject openLabel = world.comments.list(reader, KEY, null, null, null, "main", null);
        assertEquals("open", openLabel.getJSONObject("labelPolicy").getString("commentMode"));
        assertEquals("unlisted", openLabel.getJSONObject("labelPolicy").getString("display"));
        assertTrue(openLabel.getJSONArray("comments").getJSONObject(0).getBoolean("inScope"));
    }

    // ---- Hosted viewer (plan 3.1) ----

    @Test
    void hostedViewerAppliesEachDisplayStateToReadersAndEditors() {
        World world = world();
        world.setLabel("v0.3.0", GuideLabels.CURRENT, GuideLabels.RELEASE);
        world.setLabel("v0.2.0", GuideLabels.LISTED, GuideLabels.OPEN);
        world.setLabel("main", GuideLabels.UNLISTED, GuideLabels.OPEN);
        world.setLabel("latest", GuideLabels.AUTHORS_ONLY, GuideLabels.OPEN);
        world.setLabel("v0.1.0", GuideLabels.RETIRED, GuideLabels.OPEN);
        for (String label : List.of("v0.3.0", "v0.2.0", "main")) {
            assertArrayEquals(html(label), world.hosting.open(reader, KEY, label), label);
            assertArrayEquals(html(label), world.hosting.open(editor, KEY, label), label);
        }
        assertNull(world.hosting.view(reader, KEY, "latest"));
        assertArrayEquals(html("latest"), world.hosting.open(editor, KEY, "latest"));

        GuideHostingService.Viewed retired = world.hosting.view(reader, KEY, "v0.1.0");
        assertTrue(retired.retired());
        assertNull(retired.html());
        assertEquals("v0.3.0", retired.currentLabel());
        assertNull(world.hosting.open(reader, KEY, "v0.1.0"));
        assertArrayEquals(html("v0.1.0"), world.hosting.open(editor, KEY, "v0.1.0"));

        world.setLabel("v0.3.0", GuideLabels.LISTED, GuideLabels.OPEN);
        assertNull(world.hosting.view(reader, KEY, "v0.1.0").currentLabel());
        assertNull(world.hosting.view(outsider, KEY, "v0.2.0"));
        assertNull(world.hosting.view(reader, KEY, "v9.9.9"));
    }

    // ---- Topic page (plan 3.1, Section 6) ----

    @Test
    void readersSeeTheCurrentLabelAndListedLabelsOnly() {
        World world = world();
        world.setLabel("v0.3.0", GuideLabels.CURRENT, GuideLabels.RELEASE);
        world.setLabel("v0.2.0", GuideLabels.LISTED, GuideLabels.OPEN);
        world.setLabel("latest", GuideLabels.AUTHORS_ONLY, GuideLabels.OPEN);
        world.setLabel("v0.1.0", GuideLabels.RETIRED, GuideLabels.OPEN);
        List<LabelChoice> choices = TopicReferenceSectionData.labelChoices("/hub", world.document,
                world.labelFacts(11), world.versionChoices());
        assertEquals(List.of("latest", "main", "v0.3.0", "v0.2.0", "v0.1.0"),
                choices.stream().map(LabelChoice::label).toList());
        assertEquals("/hub/guide/view/" + KEY + "/v0.3.0/", choices.get(2).href());

        Guide asReader = TopicReferenceSectionData.presentLabels("/hub", 189L, KEY, "Hosted test", "HUB", true,
                choices, TopicReferenceDigest.empty(), false, true);
        assertEquals(List.of("Read the guide"), asReader.links().stream().map(Link::label).toList());
        assertEquals("/hub/guide/open?doc=" + KEY + "&label=v0.3.0", asReader.links().get(0).href());
        assertEquals(List.of("v0.2.0"), asReader.otherVersions().stream().map(Link::label).toList());
        assertTrue(asReader.retired().isEmpty());
        assertTrue(asReader.reader());
        assertFalse(asReader.commentsHidden());
        assertEquals("/hub/guide/open?doc=" + KEY + "&label=v0.3.0", asReader.open().href());

        Guide asEditor = TopicReferenceSectionData.presentLabels("/hub", 189L, KEY, "Hosted test", "HUB", true,
                choices, TopicReferenceDigest.empty(), true, true);
        assertEquals("v0.3.0", asEditor.links().get(0).label());
        assertEquals("Current", asEditor.links().get(0).badge());
        assertEquals(List.of("latest", "main", "v0.2.0"), asEditor.otherVersions().stream().map(Link::label).toList());
        assertEquals("Authors only", asEditor.otherVersions().get(0).badge());
        assertEquals("Unlisted", asEditor.otherVersions().get(1).badge());
        assertNull(asEditor.otherVersions().get(2).badge());
        assertEquals(List.of("v0.1.0"), asEditor.retired().stream().map(Link::label).toList());
        assertTrue(asEditor.open().href().contains("/es/topic-guide-comments/189"));
    }

    @Test
    void aClosedCurrentLabelHidesCountsFromReaders() {
        List<LabelChoice> choices = List.of(
                new LabelChoice("v0.3.0", GuideLabels.CURRENT, GuideLabels.CLOSED, "/hub/guide/view/g/v0.3.0/", null));
        Guide asReader = TopicReferenceSectionData.presentLabels("/hub", 189L, "g", "G", "HUB", false, choices,
                TopicReferenceDigest.empty(), false, true);
        assertTrue(asReader.commentsHidden());
        Guide asEditor = TopicReferenceSectionData.presentLabels("/hub", 189L, "g", "G", "HUB", false, choices,
                TopicReferenceDigest.empty(), true, true);
        assertFalse(asEditor.commentsHidden());
    }

    @Test
    void readerDigestFollowsTheCurrentLabelsMode() {
        World world = world();
        world.comment(1, "v0.2.0", "OPEN", PUBLISHED.plusDays(1));
        world.comment(1, null, "OPEN", PUBLISHED.plusDays(1));
        world.comment(1, null, "OPEN", PUBLISHED.minusDays(1));
        GuideLabels.Facts facts = world.labelFacts(11);
        assertEquals(3, world.comments.digestFor(11, facts.policy("v0.2.0")).open());
        world.setLabel("v0.2.0", GuideLabels.CURRENT, GuideLabels.RELEASE);
        assertEquals(2, world.comments.digestFor(11, world.labelFacts(11).policy("v0.2.0")).open());
        world.setLabel("v0.2.0", GuideLabels.CURRENT, GuideLabels.CLOSED);
        assertEquals(0, world.comments.digestFor(11, world.labelFacts(11).policy("v0.2.0")).open());
        assertEquals(3, world.comments.digestFor(11, null).open());
    }

    // ---- HTTP: scopes and permissions on GET /labels and PATCH /labels/{label} ----

    @Test
    void patchNeedsTopicsWriteAndAnEditorAndGetLabelsNeedsAReadScope() throws Exception {
        World world = world();
        GuideApiServlet servlet = new GuideApiServlet(world.comments, null, null, world.hosting, null, world.labels);

        Http commentsOnly = call(servlet, "PATCH", "/api/guide/" + KEY + "/labels/v0.2.0", editor,
                Set.of(GuideScopes.COMMENTS), "{\"display\":\"current\"}");
        assertEquals(403, commentsOnly.status);
        assertEquals("insufficient_scope", commentsOnly.json().getString("error"));

        Http readerPatch = call(servlet, "PATCH", "/api/guide/" + KEY + "/labels/v0.2.0", reader,
                Set.of(GuideScopes.TOPICS_WRITE), "{\"display\":\"current\"}");
        assertEquals(403, readerPatch.status);
        assertEquals("forbidden", readerPatch.json().getString("error"));

        Http editorPatch = call(servlet, "PATCH", "/api/guide/" + KEY + "/labels/v0.2.0", editor,
                Set.of(GuideScopes.TOPICS_WRITE), "{\"display\":\"current\",\"commentMode\":\"release\"}");
        assertEquals(200, editorPatch.status);
        assertEquals("current", editorPatch.json().getString("display"));

        Http unknown = call(servlet, "PATCH", "/api/guide/" + KEY + "/labels/v9.9.9", editor,
                Set.of(GuideScopes.TOPICS_WRITE), "{\"display\":\"listed\"}");
        assertEquals(404, unknown.status);

        Http list = call(servlet, "GET", "/api/guide/" + KEY + "/labels", reader, Set.of(GuideScopes.COMMENTS), null);
        assertEquals(200, list.status);
        assertEquals(5, list.json().getJSONArray("labels").length());
        Http listRead = call(servlet, "GET", "/api/guide/" + KEY + "/labels", reader, Set.of(GuideScopes.TOPICS_READ),
                null);
        assertEquals(200, listRead.status);

        world.setLabel("v0.2.0", GuideLabels.CURRENT, GuideLabels.CLOSED);
        Http closedPost = call(servlet, "POST", "/api/guide/" + KEY + "/comments", reader, Set.of(GuideScopes.COMMENTS),
                comment("x").put("pageUrl", HUB + "/guide/view/" + KEY + "/v0.2.0/").toString());
        assertEquals(403, closedPost.status);
        assertEquals("comments_closed", closedPost.json().getString("error"));
    }

    // ---- helpers ----

    private World world() {
        World world = new World();
        world.view(2);
        world.view(9);
        world.editors.add(9L);
        return world;
    }

    private static List<UrlBinding> hostedUrls() {
        return new ArrayList<>(List.of(
                new UrlBinding(HUB + "/guide/view/" + KEY + "/latest/", "latest"),
                new UrlBinding(HUB + "/guide/view/" + KEY + "/main/", "main")));
    }

    private static byte[] html(String label) {
        return ("<!DOCTYPE html><title>" + label + "</title>").getBytes(StandardCharsets.UTF_8);
    }

    private static JSONObject comment(String text) {
        return new JSONObject().put("anchor", "secure-transport").put("blockIndex", 1).put("text", text);
    }

    private static Set<Long> ids(JSONObject listed) {
        Set<Long> ids = new HashSet<>();
        JSONArray comments = listed.getJSONArray("comments");
        for (int i = 0; i < comments.length(); i++) {
            ids.add(comments.getJSONObject(i).getLong("id"));
        }
        return ids;
    }

    private static JSONObject find(JSONObject listed, long id) {
        JSONArray comments = listed.getJSONArray("comments");
        for (int i = 0; i < comments.length(); i++) {
            if (comments.getJSONObject(i).getLong("id") == id) {
                return comments.getJSONObject(i);
            }
        }
        throw new AssertionError("missing " + id);
    }

    private static JSONObject flagByAnchor(JSONObject listed, String anchor) {
        JSONArray flags = listed.getJSONArray("reviewFlags");
        for (int i = 0; i < flags.length(); i++) {
            if (anchor.equals(flags.getJSONObject(i).getString("anchor"))) {
                return flags.getJSONObject(i);
            }
        }
        throw new AssertionError("missing flag " + anchor);
    }

    private static List<String> anchors(JSONArray flags) {
        List<String> anchors = new ArrayList<>();
        for (int i = 0; i < flags.length(); i++) {
            anchors.add(flags.getJSONObject(i).getString("anchor"));
        }
        return anchors;
    }

    private static List<String> names(JSONArray labels) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < labels.length(); i++) {
            names.add(labels.getJSONObject(i).getString("label"));
        }
        return names;
    }

    private static User user(long id) {
        User user = new User();
        user.setUserId(id);
        user.setIsAdmin(false);
        user.setDisplayName("Person " + id);
        user.setOrganization("Example");
        return user;
    }

    private static Http call(GuideApiServlet servlet, String method, String uri, User caller, Set<String> scopes,
            String body) throws Exception {
        Http http = new Http();
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        ServletInputStream stream = new ServletInputStream() {
            @Override
            public int read() {
                return input.read();
            }

            @Override
            public boolean isFinished() {
                return input.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
            }
        };
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(), new Class<?>[] { HttpServletRequest.class },
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getInputStream" -> stream;
                    case "getProtocol" -> "HTTP/1.1";
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
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(http.bytes, StandardCharsets.UTF_8), true);
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(), new Class<?>[] { HttpServletResponse.class },
                (proxy, m, args) -> switch (m.getName()) {
                    case "setStatus" -> {
                        http.status = (Integer) args[0];
                        yield null;
                    }
                    case "getWriter" -> writer;
                    default -> defaultValue(m.getReturnType());
                });
        servlet.service((jakarta.servlet.ServletRequest) request, (jakarta.servlet.ServletResponse) response);
        writer.flush();
        return http;
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

    private static final class Http {
        int status;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        JSONObject json() {
            return new JSONObject(bytes.toString(StandardCharsets.UTF_8));
        }
    }

    /** One hosted guide (document 11, topic 189) behind the comment, label, and hosting stores. */
    private static final class World implements GuideCommentService.Store, GuideLabelService.Store,
            GuideHostingService.Store, GuideHostingService.Files {
        final GuideCommentService comments = new GuideCommentService(this);
        final GuideLabelService labels = new GuideLabelService(this);
        final GuideHostingService hosting = new GuideHostingService(this, this);
        final DocumentRow document = new DocumentRow();
        final TopicRow topic = new TopicRow();
        final List<UrlBinding> urls = hostedUrls();
        final List<HostedVersion> versions = new ArrayList<>();
        final Map<Long, byte[]> files = new HashMap<>();
        final Map<String, LabelRow> labelRows = new java.util.LinkedHashMap<>();
        final List<CommentRow> rows = new ArrayList<>();
        final List<FlagRow> flagRows = new ArrayList<>();
        final List<String> changes = new ArrayList<>();
        final List<String> changeSources = new ArrayList<>();
        final Set<Long> viewers = new HashSet<>();
        final Set<Long> editors = new HashSet<>();
        long nextId = 100;

        World() {
            document.id = 11L;
            document.documentKey = KEY;
            document.topicId = 189;
            document.title = "Hosted test";
            document.active = true;
            document.hosting = GuideHtmlRules.HUB;
            document.urls = urls;
            topic.id = 189;
            topic.code = "GUIDE-HOSTED";
            topic.name = "Guide hosted";
            topic.visibility = "PRIVATE";
            upload("latest", PUBLISHED.plusDays(5));
            upload("main", PUBLISHED.plusDays(6));
            upload("v0.1.0", PUBLISHED.minusDays(30));
            upload("v0.2.0", PUBLISHED.minusDays(3));
            upload("v0.2.0", PUBLISHED);
            upload("v0.3.0", PUBLISHED.plusDays(4));
        }

        void view(long userId) {
            viewers.add(userId);
        }

        void upload(String label, LocalDateTime at) {
            HostedVersion version = new HostedVersion();
            version.id = nextId++;
            version.documentId = 11;
            version.label = label;
            version.storedFileId = version.id;
            version.uploadedAt = at;
            version.uploadedByName = "Uploader";
            versions.add(version);
            files.put(version.storedFileId, html(label));
        }

        List<TopicReferenceSectionData.VersionChoice> versionChoices() {
            return versions.stream()
                    .map(version -> new TopicReferenceSectionData.VersionChoice(version.label, version.uploadedAt))
                    .toList();
        }

        void setLabel(String label, String display, String mode) {
            LabelRow row = new LabelRow();
            row.documentId = 11;
            row.label = label;
            row.display = display;
            row.commentMode = mode;
            row.commentModeSetAt = PUBLISHED;
            labelRows.put(label, row);
        }

        CommentRow comment(long userId, String madeOn, String state, LocalDateTime at) {
            CommentRow row = new CommentRow();
            row.id = nextId++;
            row.documentId = 11;
            row.anchor = "secure-transport";
            row.blockIndex = 0;
            row.text = "comment " + row.id;
            row.state = state;
            row.userId = userId;
            row.createdAt = at;
            row.updatedAt = at;
            row.madeOnLabel = madeOn;
            rows.add(row);
            return row;
        }

        CommentRow reply(long parentId, String madeOn, LocalDateTime at) {
            CommentRow row = comment(2, madeOn, "OPEN", at);
            row.parentId = parentId;
            return row;
        }

        void flag(String anchor, String madeOn, LocalDateTime at) {
            FlagRow row = new FlagRow();
            row.id = nextId++;
            row.documentId = 11;
            row.anchor = anchor;
            row.note = "note " + anchor;
            row.createdByUserId = 9;
            row.createdAt = at;
            row.updatedAt = at;
            row.madeOnLabel = madeOn;
            flagRows.add(row);
        }

        // Shared by the three stores.

        @Override
        public DocumentRow documentByKey(String key) {
            return KEY.equals(key) ? document : null;
        }

        @Override
        public TopicRow topic(long topicId) {
            return topicId == 189 ? topic : null;
        }

        @Override
        public TopicRow topicById(long id) {
            return topic(id);
        }

        @Override
        public boolean canView(User user, TopicRow topic) {
            return user != null && viewers.contains(user.getUserId());
        }

        @Override
        public boolean canEdit(User user, TopicRow topic) {
            return user != null && editors.contains(user.getUserId());
        }

        @Override
        public String publicBaseUrl() {
            return HUB;
        }

        @Override
        public List<CommentRow> comments(long documentId) {
            return rows.stream().filter(row -> row.documentId == documentId).toList();
        }

        // GuideCommentService.Store

        @Override
        public DocumentRow documentById(long id) {
            return id == 11 ? document : null;
        }

        @Override
        public Person personOf(User user) {
            return GuidePeople.of(user);
        }

        @Override
        public Map<Long, Person> people(Collection<Long> userIds) {
            Map<Long, Person> people = new HashMap<>();
            for (Long id : userIds) {
                people.put(id, new Person(id, "Person " + id, "Example"));
            }
            return people;
        }

        @Override
        public List<Disposition> dispositions() {
            return List.of(new Disposition("PERSUASIVE", "Persuasive"));
        }

        @Override
        public TopicReferenceDigest digest(long documentId) {
            return TopicReferenceDigest.of(comments(documentId), activeFlags(documentId), Map.of());
        }

        @Override
        public List<FlagRow> activeFlags(long documentId) {
            return flags(documentId, false);
        }

        @Override
        public List<FlagRow> flags(long documentId, boolean includeCleared) {
            return flagRows.stream().filter(flag -> flag.documentId == documentId && (includeCleared || !flag.cleared()))
                    .toList();
        }

        @Override
        public FlagRow flag(long id) {
            return flagRows.stream().filter(flag -> flag.id == id).findFirst().orElse(null);
        }

        @Override
        public FlagRow activeFlag(long documentId, String anchor) {
            return flagRows.stream().filter(flag -> anchor.equals(flag.anchor) && !flag.cleared()).findFirst()
                    .orElse(null);
        }

        @Override
        public void insertFlag(FlagRow row) {
            row.id = nextId++;
            flagRows.add(row);
        }

        @Override
        public void saveFlag(FlagRow row) {
        }

        @Override
        public CommentRow comment(long id) {
            return rows.stream().filter(row -> row.id == id).findFirst().orElse(null);
        }

        @Override
        public void insertComment(CommentRow row, CommentRow parent, List<AuditRow> audits) {
            row.id = nextId++;
            rows.add(row);
        }

        @Override
        public void updateComment(CommentRow row, CommentRow parent, List<AuditRow> audits) {
        }

        @Override
        public void updateComments(List<CommentRow> rows, List<AuditRow> audits) {
        }

        @Override
        public String topicUrl(long topicId) {
            return HUB + "/es/topic/" + topicId;
        }

        @Override
        public GuideLabels.Facts labelFacts(long documentId) {
            GuideLabels.Facts facts = new GuideLabels.Facts();
            for (LabelRow row : labelRows.values()) {
                facts.settings.put(row.label, row);
            }
            for (HostedVersion version : versions) {
                facts.uploads.merge(version.label, version.uploadedAt,
                        (left, right) -> left.isAfter(right) ? left : right);
            }
            for (UrlBinding binding : urls) {
                facts.urlLabels.add(binding.label);
            }
            return facts;
        }

        // GuideLabelService.Store

        @Override
        public GuideLabels.Facts facts(long documentId) {
            return labelFacts(documentId);
        }

        @Override
        public void save(List<LabelRow> saved, String changesJson, long topicId, long userId, Long tokenId,
                String source) {
            for (LabelRow row : saved) {
                if (row.id == null) {
                    row.id = nextId++;
                }
                labelRows.put(row.label, row);
            }
            changes.add(changesJson);
            changeSources.add(source);
        }

        // GuideHostingService.Store and Files

        @Override
        public String displayName(long userId) {
            return "Person " + userId;
        }

        @Override
        public List<HostedVersion> versions(long documentId) {
            return versions;
        }

        @Override
        public void save(HostedVersion version, String changesJson, long topicId, long userId, Long tokenId,
                String source, BuildRow build) {
            version.id = nextId++;
            versions.add(version);
        }

        @Override
        public List<LabelRow> labels(long documentId) {
            return new ArrayList<>(labelRows.values());
        }

        @Override
        public long store(byte[] html, long userId) {
            long id = nextId++;
            files.put(id, html);
            return id;
        }

        @Override
        public byte[] read(long storedFileId) {
            return files.get(storedFileId);
        }
    }
}
