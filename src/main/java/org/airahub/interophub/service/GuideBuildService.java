package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.BuildRow;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Guide build records: manifest-only reports, build history, and the admin
 * status of every registered guide. InteropHub never fetches anything to work
 * out status. What it knows about process releases comes only from the
 * manifests guides have reported (plan decision D11, section B4).
 */
public class GuideBuildService {
    public static final int STALE_DAYS = 60;
    private static final int MAX_LABEL = 64;
    private static final Pattern VERSION = Pattern.compile(
            "^v?(\\d{1,9})\\.(\\d{1,9})(?:\\.(\\d{1,9}))?(?:-([0-9A-Za-z.-]+))?$");

    private final Store store;

    public GuideBuildService() {
        this(new HibernateGuideBuildStore());
    }

    GuideBuildService(Store store) {
        this.store = store;
    }

    // ---------------------------------------------------------------- API

    /** {@code POST /api/guide/{documentKey}/builds}. Returns the 201 body. */
    public JSONObject report(User user, String documentKey, JSONObject body) {
        String key = GuideUrlRules.documentKey(documentKey);
        DocumentRow document = store.documentByKey(key);
        if (document == null || document.id == null) {
            throw GuideApiException.notFound("That guide was not found.");
        }
        TopicRow topic = store.topicById(document.topicId);
        if (topic == null || !store.canEdit(user, topic)) {
            throw GuideApiException.forbidden("You cannot edit this topic.");
        }
        if (!document.active) {
            throw GuideApiException.documentInactive();
        }
        String label = label(body);
        Object raw = body.opt("manifest");
        if (raw == null || raw == JSONObject.NULL) {
            throw GuideApiException.invalidRequest("A manifest is required.");
        }
        if (!(raw instanceof JSONObject manifest)) {
            throw GuideApiException.invalidRequest("The manifest must be a JSON object.");
        }
        GuideBuildManifest.Parsed parsed;
        try {
            parsed = GuideBuildManifest.parse(manifest);
        } catch (GuideBuildManifest.Invalid ex) {
            throw GuideApiException.invalidRequest(ex.getMessage());
        }
        BuildRow row = new BuildRow();
        row.documentId = document.id;
        row.source = "MANIFEST";
        row.label = label;
        GuideBuildManifest.fill(row, parsed);
        row.receivedByUserId = user.getUserId();
        row.receivedAt = ApiTime.now();
        store.save(row);
        JSONObject json = new JSONObject();
        json.put("buildId", row.id);
        json.put("documentKey", key);
        json.put("processVersion", row.processVersion);
        json.put("receivedAt", ApiTime.format(row.receivedAt));
        return json;
    }

    /** {@code GET /api/guide/{documentKey}/builds}: newest first, for topic editors. */
    public JSONObject history(User user, String documentKey) {
        String key = GuideUrlRules.documentKey(documentKey);
        DocumentRow document = store.documentByKey(key);
        if (document == null || document.id == null) {
            throw GuideApiException.notFound("That guide was not found.");
        }
        TopicRow topic = store.topicById(document.topicId);
        if (topic == null || !store.canEdit(user, topic)) {
            throw GuideApiException.forbidden("You cannot edit this topic.");
        }
        JSONArray builds = new JSONArray();
        for (BuildRow row : store.builds(document.id)) {
            builds.put(buildJson(row));
        }
        JSONObject json = new JSONObject();
        json.put("documentKey", key);
        json.put("builds", builds);
        return json;
    }

    static JSONObject buildJson(BuildRow row) {
        JSONObject json = new JSONObject();
        json.put("buildId", row.id);
        json.put("source", row.source);
        json.put("label", nullable(row.label));
        json.put("manifestSchema", nullable(row.manifestSchema));
        json.put("processVersion", nullable(row.processVersion));
        json.put("documentVersion", nullable(row.documentVersion));
        json.put("documentStatus", nullable(row.documentStatus));
        json.put("builtAt", nullable(ApiTime.format(row.builtAt)));
        json.put("sourceCommit", nullable(row.sourceCommit));
        json.put("warningCount", nullable(row.warningCount));
        json.put("manifestError", nullable(row.manifestError));
        json.put("receivedBy", row.receivedByName == null ? "" : row.receivedByName);
        json.put("receivedAt", ApiTime.format(row.receivedAt));
        JSONObject manifest = manifest(row);
        json.put("manifest", manifest == null ? JSONObject.NULL : manifest);
        return json;
    }

    private static String label(JSONObject body) {
        if (!body.has("label") || body.isNull("label")) {
            return null;
        }
        if (!(body.get("label") instanceof String text)) {
            throw GuideApiException.invalidRequest("The label must be text.");
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_LABEL) {
            throw GuideApiException.invalidRequest("The label is longer than 64 characters.");
        }
        return trimmed;
    }

    private static Object nullable(Object value) {
        return value == null ? JSONObject.NULL : value;
    }

    // ---------------------------------------------------------------- status

    public StatusReport status() {
        return status(store.statusInputs(), LocalDateTime.now());
    }

    /** Detail for one guide on the admin page, or null when it is not registered. */
    public Detail detail(String documentKey) {
        DocumentRow document;
        try {
            document = store.documentByKey(GuideUrlRules.documentKey(documentKey));
        } catch (GuideApiException ex) {
            return null;
        }
        if (document == null || document.id == null) {
            return null;
        }
        StatusReport report = status();
        GuideStatus status = report.guides.stream()
                .filter(guide -> guide.documentKey.equals(document.documentKey))
                .findFirst().orElse(null);
        return new Detail(document.documentKey, document.title, document.active, status, report,
                store.builds(document.id));
    }

    static StatusReport status(StatusInputs inputs, LocalDateTime now) {
        StatusReport report = new StatusReport();
        BuildRow reference = null;
        Version referenceVersion = null;
        for (BuildRow build : inputs.latestBuilds.values()) {
            if (build.manifestError != null) {
                continue;
            }
            Version version = Version.parse(build.processVersion);
            if (version != null && (referenceVersion == null || version.compareTo(referenceVersion) > 0)) {
                reference = build;
                referenceVersion = version;
            }
        }
        if (reference != null) {
            report.latestVersion = reference.processVersion;
            report.releases = releases(manifest(reference));
            report.releaseSourceKey = inputs.documentKeys.get(reference.documentId);
        }
        TreeSet<String> codes = new TreeSet<>();
        LocalDateTime staleBefore = now.minusDays(STALE_DAYS);
        for (DocumentRow document : inputs.documents) {
            if (!document.active || document.id == null) {
                continue;
            }
            GuideStatus guide = new GuideStatus();
            guide.documentKey = document.documentKey;
            guide.title = document.title;
            guide.hosting = document.hosting;
            guide.guideUrl = inputs.guideUrls.get(document.id);
            TopicRow topic = inputs.topics.get(document.topicId);
            if (topic != null) {
                guide.topicId = topic.id;
                guide.topicName = topic.name;
                guide.topicCode = topic.code;
            }
            guide.openComments = inputs.openComments.getOrDefault(document.id, 0L);
            guide.openFlags = inputs.openFlags.getOrDefault(document.id, 0L);
            guide.currentLabel = inputs.currentLabels.get(document.id);
            BuildRow build = inputs.latestBuilds.get(document.id);
            guide.latestBuild = build;
            LocalDateTime hosted = inputs.lastHostedUploads.get(document.id);
            guide.lastPublish = later(hosted, build == null ? null : build.receivedAt);
            guide.stale = guide.lastPublish == null || guide.lastPublish.isBefore(staleBefore);
            classify(guide, build, report);
            codes.addAll(guide.warningCodes);
            report.guides.add(guide);
        }
        report.guides.sort(SORT);
        report.warningCodes = new ArrayList<>(codes);
        return report;
    }

    private static void classify(GuideStatus guide, BuildRow build, StatusReport report) {
        if (build == null) {
            guide.state = State.NO_STATUS;
            return;
        }
        guide.lastBuild = build.builtAt;
        guide.processVersion = build.processVersion;
        guide.documentVersion = build.documentVersion;
        guide.documentStatus = build.documentStatus;
        if (build.manifestError != null) {
            guide.state = State.UNREADABLE;
            guide.error = build.manifestError;
            return;
        }
        if (build.processVersion == null) {
            // Uploaded without a manifest: built before manifests existed, so it is
            // behind every release InteropHub knows about.
            guide.state = State.NO_MANIFEST;
            guide.behind = report.releases.size();
            guide.impact = mostDemanding(report.releases);
            return;
        }
        JSONObject manifest = manifest(build);
        guide.warningCount = build.warningCount == null ? 0 : build.warningCount;
        guide.warnings = warnings(manifest);
        for (Warning warning : guide.warnings) {
            if (warning.code() != null && !warning.code().isBlank()) {
                guide.warningCodes.add(warning.code());
            }
        }
        guide.feedbackMarkers = feedbackMarkers(manifest);
        Version version = Version.parse(build.processVersion);
        boolean listed = version != null && report.releases.stream()
                .anyMatch(release -> version.equals(Version.parse(release.version())));
        if (!listed) {
            guide.state = State.UNKNOWN_VERSION;
            return;
        }
        List<Release> newer = report.releases.stream()
                .filter(release -> {
                    Version other = Version.parse(release.version());
                    return other != null && other.compareTo(version) > 0;
                })
                .toList();
        guide.behind = newer.size();
        guide.impact = mostDemanding(newer);
        guide.state = newer.isEmpty() ? State.CURRENT : State.BEHIND;
    }

    private static final Comparator<GuideStatus> SORT = Comparator
            .comparingInt((GuideStatus guide) -> switch (guide.state) {
                case NO_STATUS -> 0;
                case UNREADABLE -> 1;
                default -> 2;
            })
            .thenComparing(guide -> guide.behind == null ? 0 : guide.behind, Comparator.reverseOrder())
            .thenComparing(guide -> guide.warningCount, Comparator.reverseOrder())
            .thenComparing(guide -> guide.title == null ? "" : guide.title.toLowerCase())
            .thenComparing(guide -> guide.documentKey);

    static List<Release> releases(JSONObject manifest) {
        List<Release> releases = new ArrayList<>();
        if (manifest == null || !(manifest.opt("processReleases") instanceof JSONArray array)) {
            return releases;
        }
        for (int i = 0; i < array.length(); i++) {
            if (!(array.opt(i) instanceof JSONObject item)) {
                continue;
            }
            String version = item.optString("version", "").trim();
            if (version.isEmpty()) {
                continue;
            }
            String impact = item.optString("upgradeImpact", "").trim();
            releases.add(new Release(version, item.optString("date", ""),
                    Impact.parse(impact), item.optString("summary", "")));
        }
        releases.sort((left, right) -> {
            Version a = Version.parse(left.version());
            Version b = Version.parse(right.version());
            if (a == null || b == null) {
                return a == null ? (b == null ? 0 : 1) : -1;
            }
            return b.compareTo(a);
        });
        return releases;
    }

    /** The most demanding impact, or null when none of the releases names a known one. */
    static Impact mostDemanding(List<Release> releases) {
        Impact most = null;
        for (Release release : releases) {
            if (release.impact() != null && (most == null || release.impact().ordinal() > most.ordinal())) {
                most = release.impact();
            }
        }
        return most;
    }

    static List<Warning> warnings(JSONObject manifest) {
        List<Warning> warnings = new ArrayList<>();
        JSONObject checks = manifest == null ? null : manifest.optJSONObject("checks");
        JSONArray array = checks == null ? null : checks.optJSONArray("warnings");
        if (array == null) {
            return warnings;
        }
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof JSONObject warning) {
                warnings.add(new Warning(blankToNull(warning.optString("code", "")),
                        warning.optString("message", ""), blankToNull(warning.optString("file", ""))));
            } else if (item instanceof String text) {
                warnings.add(new Warning(null, text, null));
            }
        }
        return warnings;
    }

    private static Integer feedbackMarkers(JSONObject manifest) {
        JSONObject structure = manifest == null ? null : manifest.optJSONObject("structure");
        if (structure == null || !(structure.opt("feedbackMarkers") instanceof Number number)) {
            return null;
        }
        return number.intValue();
    }

    static JSONObject manifest(BuildRow row) {
        if (row == null || row.manifestJson == null || row.manifestJson.isBlank()) {
            return null;
        }
        try {
            return new JSONObject(row.manifestJson);
        } catch (JSONException ex) {
            return null;
        }
    }

    private static LocalDateTime later(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ---------------------------------------------------------------- shapes

    public enum State {
        NO_STATUS, UNREADABLE, NO_MANIFEST, UNKNOWN_VERSION, BEHIND, CURRENT
    }

    /** Ordered from least to most demanding (plan D10). */
    public enum Impact {
        REBUILD, CONFIG, CONTENT;

        static Impact parse(String raw) {
            if (raw == null) {
                return null;
            }
            return switch (raw.trim().toLowerCase()) {
                case "rebuild" -> REBUILD;
                case "config" -> CONFIG;
                case "content" -> CONTENT;
                default -> null;
            };
        }

        public String code() {
            return name().toLowerCase();
        }
    }

    public record Release(String version, String date, Impact impact, String summary) {
    }

    public record Warning(String code, String message, String file) {
    }

    public static final class GuideStatus {
        public String documentKey;
        public String title;
        public String hosting;
        public String guideUrl;
        public Long topicId;
        public String topicName;
        public String topicCode;
        public State state;
        public String processVersion;
        /** Releases newer than the guide's version; null when that cannot be worked out. */
        public Integer behind;
        public Impact impact;
        public String documentVersion;
        public String documentStatus;
        public LocalDateTime lastBuild;
        public LocalDateTime lastPublish;
        public boolean stale;
        public int warningCount;
        public List<Warning> warnings = new ArrayList<>();
        public TreeSet<String> warningCodes = new TreeSet<>();
        public long openComments;
        public long openFlags;
        /** The guide's Current label, or null (plan Section 6). */
        public String currentLabel;
        public Integer feedbackMarkers;
        public String error;
        public BuildRow latestBuild;

        public boolean needsUpgrade() {
            return state == State.BEHIND || state == State.NO_MANIFEST;
        }
    }

    public static final class StatusReport {
        public String latestVersion;
        public String releaseSourceKey;
        public List<Release> releases = new ArrayList<>();
        public List<GuideStatus> guides = new ArrayList<>();
        public List<String> warningCodes = new ArrayList<>();

        public long count(State state) {
            return guides.stream().filter(guide -> guide.state == state).count();
        }

        public long needsUpgrade(Impact impact) {
            return guides.stream().filter(guide -> guide.needsUpgrade() && guide.impact == impact).count();
        }

        public long needsUpgrade() {
            return guides.stream().filter(GuideStatus::needsUpgrade).count();
        }

        public long withWarnings() {
            return guides.stream().filter(guide -> guide.warningCount > 0).count();
        }
    }

    public record Detail(String documentKey, String title, boolean active, GuideStatus status, StatusReport report,
            List<BuildRow> builds) {
    }

    /** Everything the status page needs, read in a handful of queries. */
    public static final class StatusInputs {
        public List<DocumentRow> documents = new ArrayList<>();
        public Map<Long, String> documentKeys = new LinkedHashMap<>();
        public Map<Long, TopicRow> topics = new LinkedHashMap<>();
        public Map<Long, BuildRow> latestBuilds = new LinkedHashMap<>();
        public Map<Long, Long> openComments = new LinkedHashMap<>();
        public Map<Long, Long> openFlags = new LinkedHashMap<>();
        public Map<Long, LocalDateTime> lastHostedUploads = new LinkedHashMap<>();
        public Map<Long, String> guideUrls = new LinkedHashMap<>();
        public Map<Long, String> currentLabels = new LinkedHashMap<>();
    }

    /** A process version label such as {@code v0.21.0} or {@code v1.2.0-rc1}. */
    record Version(int major, int minor, int patch, String suffix) implements Comparable<Version> {
        static Version parse(String raw) {
            if (raw == null) {
                return null;
            }
            Matcher matcher = VERSION.matcher(raw.trim());
            if (!matcher.matches()) {
                return null;
            }
            return new Version(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                    matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3)), matcher.group(4));
        }

        @Override
        public int compareTo(Version other) {
            int compared = Integer.compare(major, other.major);
            if (compared == 0) {
                compared = Integer.compare(minor, other.minor);
            }
            if (compared == 0) {
                compared = Integer.compare(patch, other.patch);
            }
            if (compared != 0) {
                return compared;
            }
            if (suffix == null || other.suffix == null) {
                // A pre-release sorts before its release.
                return suffix == null ? (other.suffix == null ? 0 : 1) : -1;
            }
            return suffix.compareTo(other.suffix);
        }
    }

    interface Store {
        DocumentRow documentByKey(String key);

        TopicRow topicById(long id);

        boolean canEdit(User user, TopicRow topic);

        /** Persists the row and sets {@code row.id}. */
        void save(BuildRow row);

        /** Newest received first, with {@code receivedByName} filled. */
        List<BuildRow> builds(long documentId);

        StatusInputs statusInputs();
    }
}
