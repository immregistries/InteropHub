package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.CommentRow;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.LabelRow;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A guide's labels: display state and comment mode (plan Sections 3 and 5.2 to 5.3).
 * {@code GET /api/guide/{key}/labels} and {@code PATCH /api/guide/{key}/labels/{label}},
 * and the label table on the guide settings page.
 */
public class GuideLabelService {
    private static final Set<String> PATCH_FIELDS = Set.of("display", "commentMode");

    private final Store store;

    public GuideLabelService() {
        this(new HibernateGuideLabelStore());
    }

    GuideLabelService(Store store) {
        this.store = store;
    }

    /** Every known label. Readers do not see Authors only or Retired labels, nor {@code openInScope}. */
    public JSONObject list(User user, String documentKey) {
        Loaded loaded = load(user, documentKey);
        if (!loaded.canView) {
            throw GuideApiException.forbidden("You cannot view this guide.");
        }
        GuideLabels.Facts facts = store.facts(loaded.document.id);
        List<CommentRow> comments = loaded.canEdit ? store.comments(loaded.document.id) : List.of();
        JSONArray labels = new JSONArray();
        for (String label : facts.known()) {
            if (!loaded.canEdit && !GuideLabels.readerKnown(facts.display(label))) {
                continue;
            }
            labels.put(labelJson(facts, label, loaded.canEdit, comments));
        }
        JSONObject json = new JSONObject();
        json.put("documentKey", loaded.document.documentKey);
        json.put("labels", labels);
        return json;
    }

    /**
     * Applies a PATCH body. Only {@code display} and {@code commentMode} are accepted;
     * values are case-insensitive ({@code current}, {@code release}, ...).
     */
    public JSONObject patch(User user, Long tokenId, String source, String documentKey, String label,
            JSONObject body) {
        if (body == null || body.isEmpty()) {
            throw GuideApiException.invalidRequest("Send display, commentMode, or both.");
        }
        for (String key : body.keySet()) {
            if (!PATCH_FIELDS.contains(key)) {
                throw GuideApiException.invalidField(key, "That field is not accepted on this request.");
            }
        }
        String display = null;
        if (body.has("display")) {
            if (body.isNull("display") || !(body.get("display") instanceof String raw)) {
                throw GuideApiException.invalidField("display",
                        "Display must be current, listed, unlisted, authors_only, or retired.");
            }
            display = GuideLabels.display(raw);
        }
        String mode = null;
        if (body.has("commentMode")) {
            if (body.isNull("commentMode") || !(body.get("commentMode") instanceof String raw)) {
                throw GuideApiException.invalidField("commentMode", "Comment mode must be open, release, or closed.");
            }
            mode = GuideLabels.commentMode(raw);
        }
        return set(user, tokenId, source, documentKey, label, display, mode);
    }

    /**
     * Sets a label's display state and/or comment mode (stored spellings; null leaves
     * a value unchanged). Making a label CURRENT turns the previous CURRENT label into
     * LISTED in the same transaction. Closing always succeeds (decision V9).
     * Returns the label object, with {@code demoted} holding the previous CURRENT label or null.
     */
    public JSONObject set(User user, Long tokenId, String source, String documentKey, String label,
            String display, String mode) {
        if (!"API".equals(source) && !"WEB".equals(source)) {
            throw GuideApiException.invalidRequest("The change source is invalid.");
        }
        Loaded loaded = load(user, documentKey);
        if (!loaded.canView || !loaded.canEdit) {
            throw GuideApiException.forbidden("You cannot edit this topic.");
        }
        if (display != null && !GuideLabels.DISPLAY_STATES.contains(display)) {
            throw GuideApiException.invalidField("display",
                    "Display must be current, listed, unlisted, authors_only, or retired.");
        }
        if (mode != null && !GuideLabels.COMMENT_MODES.contains(mode)) {
            throw GuideApiException.invalidField("commentMode", "Comment mode must be open, release, or closed.");
        }
        String name = label == null ? "" : label.trim();
        GuideLabels.Facts facts = store.facts(loaded.document.id);
        if (name.isEmpty() || name.length() > GuideLabels.MAX_LABEL || !facts.isKnown(name)) {
            throw GuideApiException.notFound("That label was not found.");
        }
        LocalDateTime now = ApiTime.now();
        LabelRow before = facts.setting(name);
        LabelRow row = copy(before, loaded.document.id, name);
        JSONObject change = new JSONObject();
        change.put("documentKey", loaded.document.documentKey);
        change.put("label", name);
        if (display != null && !display.equals(before.display)) {
            change.put("display", fromTo(before.display, display));
            row.display = display;
        }
        if (mode != null && !mode.equals(before.commentMode)) {
            change.put("commentMode", fromTo(before.commentMode, mode));
            row.commentMode = mode;
            row.commentModeSetAt = now;
        }
        List<LabelRow> changed = new ArrayList<>();
        LabelRow demoted = null;
        if (GuideLabels.CURRENT.equals(row.display) && change.has("display")) {
            for (LabelRow other : facts.settings.values()) {
                if (!name.equals(other.label) && GuideLabels.CURRENT.equals(other.display)) {
                    demoted = copy(other, loaded.document.id, other.label);
                    demoted.display = GuideLabels.LISTED;
                    demoted.updatedByUserId = user.getUserId();
                    demoted.updatedAt = now;
                    changed.add(demoted);
                    JSONObject note = new JSONObject();
                    note.put("label", other.label);
                    note.put("display", fromTo(GuideLabels.CURRENT, GuideLabels.LISTED));
                    change.put("demoted", note);
                }
            }
        }
        if (change.has("display") || change.has("commentMode")) {
            row.updatedByUserId = user.getUserId();
            row.updatedAt = now;
            changed.add(0, row);
            JSONObject changes = new JSONObject();
            changes.put("label", change);
            store.save(changed, changes.toString(), loaded.topic.id, user.getUserId(), tokenId, source);
            for (LabelRow saved : changed) {
                facts.settings.put(saved.label, saved);
            }
        }
        List<CommentRow> comments = store.comments(loaded.document.id);
        JSONObject json = labelJson(facts, name, true, comments);
        json.put("demoted", demoted == null ? JSONObject.NULL : labelJson(facts, demoted.label, true, comments));
        return json;
    }

    /** Open, live, top-level comments a reader would see on the label under its scope rule. */
    static int openInScope(GuideLabels.Facts facts, String label, List<CommentRow> comments) {
        GuideLabels.Policy policy = facts.policy(label);
        int count = 0;
        for (CommentRow row : comments) {
            if (row.parentId != null || row.deleted() || row.withdrawn() || !"OPEN".equals(row.state)) {
                continue;
            }
            if (policy == null || GuideLabels.OPEN.equals(policy.commentMode())
                    || policy.inScope(row.madeOnLabel, row.createdAt)) {
                count++;
            }
        }
        return count;
    }

    static JSONObject labelJson(GuideLabels.Facts facts, String label, boolean editor, List<CommentRow> comments) {
        LabelRow row = facts.setting(label);
        JSONObject json = new JSONObject();
        json.put("label", label);
        json.put("display", GuideLabels.api(row.display));
        json.put("commentMode", GuideLabels.api(row.commentMode));
        String published = ApiTime.format(facts.publishedAt(label));
        json.put("publishedAt", published == null ? JSONObject.NULL : published);
        if (editor) {
            json.put("openInScope", openInScope(facts, label, comments));
        }
        return json;
    }

    private static JSONObject fromTo(String from, String to) {
        JSONObject json = new JSONObject();
        json.put("from", GuideLabels.api(from));
        json.put("to", GuideLabels.api(to));
        return json;
    }

    private static LabelRow copy(LabelRow source, long documentId, String label) {
        LabelRow row = new LabelRow();
        row.id = source.id;
        row.documentId = documentId;
        row.label = label;
        row.display = Objects.requireNonNullElse(source.display, GuideLabels.UNLISTED);
        row.commentMode = Objects.requireNonNullElse(source.commentMode, GuideLabels.OPEN);
        row.commentModeSetAt = source.commentModeSetAt;
        row.updatedByUserId = source.updatedByUserId;
        row.updatedAt = source.updatedAt;
        return row;
    }

    private Loaded load(User user, String documentKey) {
        String key;
        try {
            key = GuideUrlRules.documentKey(documentKey);
        } catch (GuideApiException ex) {
            throw GuideApiException.notFound("That guide was not found.");
        }
        DocumentRow document = store.documentByKey(key);
        if (document == null || document.id == null) {
            throw GuideApiException.notFound("That guide was not found.");
        }
        TopicRow topic = store.topic(document.topicId);
        boolean canView = topic != null && store.canView(user, topic);
        boolean canEdit = canView && store.canEdit(user, topic);
        return new Loaded(document, topic, canView, canEdit);
    }

    private record Loaded(DocumentRow document, TopicRow topic, boolean canView, boolean canEdit) {
    }

    interface Store {
        DocumentRow documentByKey(String key);

        TopicRow topic(long topicId);

        boolean canView(User user, TopicRow topic);

        boolean canEdit(User user, TopicRow topic);

        GuideLabels.Facts facts(long documentId);

        List<CommentRow> comments(long documentId);

        /** Saves the rows and one es_topic_change row (GUIDE) in one transaction; sets new row ids. */
        void save(List<LabelRow> rows, String changesJson, long topicId, long userId, Long tokenId, String source);
    }
}
