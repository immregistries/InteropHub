package org.airahub.interophub.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.BuildRow;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.HostedVersion;
import org.airahub.interophub.service.GuideRows.LabelRow;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Uploads and serves a guide hosted on InteropHub. HTML is checked here and
 * stored through {@link GuideHtmlFiles}, which uses {@link StoredFileService}.
 */
public class GuideHostingService {
    private static final Logger LOGGER = Logger.getLogger(GuideHostingService.class.getName());

    private final Store store;
    private final Files files;

    public GuideHostingService() {
        this(new HibernateGuideHostingStore(), new GuideHtmlFiles());
    }

    GuideHostingService(Store store, Files files) {
        this.store = store;
        this.files = files;
    }

    public UploadResult upload(User user, Long tokenId, String source, String documentKey, String label,
            byte[] html, String contentType) {
        if (!"API".equals(source) && !"WEB".equals(source)) {
            throw GuideApiException.invalidRequest("The change source is invalid.");
        }
        String key = GuideUrlRules.documentKey(documentKey);
        String versionLabel = GuideHtmlRules.label(label);
        GuideHtmlRules.html(html, contentType);
        DocumentRow document = store.documentByKey(key);
        if (document == null || document.id == null) {
            throw GuideApiException.notFound("That guide was not found.");
        }
        TopicRow topic = store.topicById(document.topicId);
        if (topic == null || !store.canEdit(user, topic)) {
            throw GuideApiException.notEditor(topic != null && store.canView(user, topic) ? topic.name : null);
        }
        if (!document.active) {
            throw GuideApiException.documentInactive();
        }
        if (!GuideHtmlRules.HUB.equals(document.hosting)) {
            throw GuideApiException.invalidState("Upload a guide only after its hosting is HUB.");
        }
        boolean replaced = false;
        for (HostedVersion existing : store.versions(document.id)) {
            if (versionLabel.equals(existing.label)) {
                replaced = true;
                break;
            }
        }
        long storedFileId;
        try {
            storedFileId = files.store(html, user.getUserId());
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Hosted guide upload failed", ex);
            throw new IllegalStateException("The guide could not be stored.");
        }
        HostedVersion version = new HostedVersion();
        version.documentId = document.id;
        version.label = versionLabel;
        version.storedFileId = storedFileId;
        version.sizeBytes = html.length;
        version.uploadedByUserId = user.getUserId();
        version.uploadedByName = store.displayName(user.getUserId());
        version.uploadedAt = LocalDateTime.now();
        JSONObject changes = new JSONObject();
        JSONObject upload = new JSONObject();
        upload.put("documentKey", key);
        upload.put("label", versionLabel);
        upload.put("sizeBytes", html.length);
        upload.put("replaced", replaced);
        changes.put("upload", upload);
        BuildRow build = GuideBuildManifest.uploadRow(html);
        build.documentId = document.id;
        build.label = versionLabel;
        build.receivedByUserId = user.getUserId();
        build.receivedAt = version.uploadedAt;
        if (build.processVersion != null) {
            upload.put("processVersion", build.processVersion);
        }
        if (build.manifestError != null) {
            LOGGER.info("Guide " + key + " uploaded with an unreadable build manifest: " + build.manifestError);
        }
        store.save(version, changes.toString(), topic.id, user.getUserId(), tokenId, source, build);
        return new UploadResult(replaced ? 200 : 201,
                versionJson(version, GuideUrlRules.viewUrl(store.publicBaseUrl(), key, versionLabel), false));
    }

    public JSONObject list(User user, String documentKey) {
        String key = GuideUrlRules.documentKey(documentKey);
        DocumentRow document = store.documentByKey(key);
        if (document == null || document.id == null) {
            throw GuideApiException.notFound("That guide was not found.");
        }
        TopicRow topic = store.topicById(document.topicId);
        if (topic == null || !store.canView(user, topic)) {
            throw GuideApiException.forbidden("You cannot view this guide.");
        }
        JSONObject json = new JSONObject();
        json.put("versions", versionList(key, store.versions(document.id)));
        return json;
    }

    /**
     * The HTML to serve, or null when the caller must see the same refusal as a
     * private topic. Every failure looks the same: no access, inactive, not
     * hosted, unknown document, or unknown label.
     */
    public byte[] open(User user, String documentKey, String label) {
        Viewed viewed = view(user, documentKey, label);
        return viewed == null ? null : viewed.html();
    }

    /**
     * What the viewer serves, or null for the private-topic refusal. Display states
     * (plan 3.1): an Authors only label is refused to non-editors; a Retired label
     * gives non-editors the retired page ({@code html} null, {@code retired} true),
     * with the Current label when it has an upload. Editors open every label.
     */
    public Viewed view(User user, String documentKey, String label) {
        String key;
        String versionLabel;
        try {
            key = GuideUrlRules.documentKey(documentKey);
            versionLabel = GuideHtmlRules.label(label);
        } catch (GuideApiException ex) {
            return null;
        }
        DocumentRow document = store.documentByKey(key);
        if (document == null || document.id == null || !document.active || !GuideHtmlRules.HUB.equals(document.hosting)) {
            return null;
        }
        TopicRow topic = store.topicById(document.topicId);
        if (topic == null || !store.canView(user, topic)) {
            return null;
        }
        List<HostedVersion> versions = store.versions(document.id);
        HostedVersion current = newest(versions, versionLabel);
        if (current == null) {
            return null;
        }
        List<LabelRow> settings = store.labels(document.id);
        String display = GuideLabels.UNLISTED;
        String currentLabel = null;
        for (LabelRow row : settings) {
            if (versionLabel.equals(row.label) && row.display != null) {
                display = row.display;
            }
            if (GuideLabels.CURRENT.equals(row.display) && newest(versions, row.label) != null) {
                currentLabel = row.label;
            }
        }
        boolean restricted = GuideLabels.AUTHORS_ONLY.equals(display) || GuideLabels.RETIRED.equals(display);
        if (restricted && !store.canEdit(user, topic)) {
            if (GuideLabels.AUTHORS_ONLY.equals(display)) {
                return null;
            }
            return new Viewed(null, true, key, versionLabel, versionLabel.equals(currentLabel) ? null : currentLabel);
        }
        try {
            return new Viewed(files.read(current.storedFileId), false, key, versionLabel, currentLabel);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Hosted guide could not be read", ex);
            throw new IllegalStateException("The guide could not be read.");
        }
    }

    private static HostedVersion newest(List<HostedVersion> versions, String label) {
        HostedVersion current = null;
        for (HostedVersion version : versions) {
            if (label.equals(version.label) && (current == null || newer(version, current))) {
                current = version;
            }
        }
        return current;
    }

    /** {@code html} is null when {@code retired} is true. */
    public record Viewed(byte[] html, boolean retired, String documentKey, String label, String currentLabel) {
    }

    private JSONArray versionList(String documentKey, List<HostedVersion> versions) {
        Map<String, List<HostedVersion>> byLabel = new LinkedHashMap<>();
        for (HostedVersion version : versions) {
            byLabel.computeIfAbsent(version.label, key -> new ArrayList<>()).add(version);
        }
        List<String> labels = new ArrayList<>(byLabel.keySet());
        labels.sort((left, right) -> {
            int rank = Integer.compare(rank(left), rank(right));
            return rank != 0 ? rank : left.compareTo(right);
        });
        JSONArray array = new JSONArray();
        for (String label : labels) {
            List<HostedVersion> rows = byLabel.get(label);
            rows.sort((left, right) -> newer(left, right) ? -1 : 1);
            HostedVersion newest = rows.get(0);
            JSONObject json = versionJson(newest,
                    GuideUrlRules.viewUrl(store.publicBaseUrl(), documentKey, label), true);
            json.put("historyCount", rows.size());
            JSONArray earlier = new JSONArray();
            for (int i = 1; i < rows.size(); i++) {
                HostedVersion old = rows.get(i);
                JSONObject row = new JSONObject();
                row.put("sizeBytes", old.sizeBytes);
                row.put("uploadedBy", old.uploadedByName == null ? "" : old.uploadedByName);
                row.put("uploadedAt", ApiTime.format(old.uploadedAt));
                earlier.put(row);
            }
            json.put("earlier", earlier);
            array.put(json);
        }
        return array;
    }

    private static JSONObject versionJson(HostedVersion version, String viewUrl, boolean list) {
        JSONObject json = new JSONObject();
        json.put("label", version.label);
        json.put("sizeBytes", version.sizeBytes);
        json.put("uploadedBy", version.uploadedByName == null ? "" : version.uploadedByName);
        json.put("uploadedAt", ApiTime.format(version.uploadedAt));
        json.put("viewUrl", viewUrl);
        if (!list) {
            return json;
        }
        return json;
    }

    private static boolean newer(HostedVersion left, HostedVersion right) {
        if (left.uploadedAt == null || right.uploadedAt == null) {
            return left.id != null && (right.id == null || left.id > right.id);
        }
        int compared = left.uploadedAt.compareTo(right.uploadedAt);
        if (compared != 0) {
            return compared > 0;
        }
        long leftId = left.id == null ? 0 : left.id;
        long rightId = right.id == null ? 0 : right.id;
        return leftId > rightId;
    }

    private static int rank(String label) {
        if ("latest".equals(label)) {
            return 0;
        }
        if ("main".equals(label)) {
            return 1;
        }
        return 2;
    }

    public record UploadResult(int status, JSONObject version) {
    }

    /** Stores validated HTML. Implementations must not return a public id or Blob URL. */
    interface Files {
        long store(byte[] html, long userId) throws IOException;

        byte[] read(long storedFileId) throws IOException;
    }

    interface Store {
        DocumentRow documentByKey(String key);

        TopicRow topicById(long id);

        boolean canView(User user, TopicRow topic);

        boolean canEdit(User user, TopicRow topic);

        String displayName(long userId);

        String publicBaseUrl();

        List<HostedVersion> versions(long documentId);

        /**
         * Saves the upload, its change record, and its build row in one
         * transaction. Sets {@code version.id}, {@code build.hostedVersionId}, and {@code build.id}.
         */
        void save(HostedVersion version, String changesJson, long topicId, long userId, Long tokenId, String source,
                BuildRow build);

        /** Author label settings. A store without them reports none (every label Unlisted). */
        default List<LabelRow> labels(long documentId) {
            return List.of();
        }
    }
}
