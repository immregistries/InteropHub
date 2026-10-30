package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.AuditRow;
import org.airahub.interophub.service.GuideRows.CommentRow;
import org.airahub.interophub.service.GuideRows.Disposition;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.FlagRow;
import org.airahub.interophub.service.GuideRows.Person;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Comment reads and the commenter actions in contract 5.1 and 5.3–5.7.
 * Author disposition, close, re-attach, and delete stay refused until H3.
 */
public class GuideCommentService {
    static final int EDIT_WINDOW_DAYS = 14;
    private static final int MAX_TEXT = 4000;
    private static final Pattern ANCHOR = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]*$");
    private static final Set<String> STATES = Set.of("OPEN", "DISPOSED", "CLOSED", "WITHDRAWN");
    private static final List<String> AUTHOR_FIELDS = List.of(
            "dispositionCode", "changeNote", "state", "anchor", "blockIndex", "snippet");

    private final Store store;

    public GuideCommentService() {
        this(new HibernateGuideCommentStore());
    }

    GuideCommentService(Store store) {
        this.store = store;
    }

    public JSONObject document(User user, String documentKey) {
        Loaded loaded = load(user, documentKey);
        String topicUrl = loaded.topic == null ? null : store.topicUrl(loaded.topic.id);
        return GuideResponses.document(loaded.document, loaded.topic, loaded.canView, loaded.canEdit,
                store.personOf(user), store.dispositions(), ApiTime.format(LocalDateTime.now()),
                topicUrl, null, null, null);
    }

    public JSONObject list(User user, String documentKey, String state, String anchor, String since) {
        Loaded loaded = requireView(user, documentKey);
        LocalDateTime now = ApiTime.now();
        boolean sinceMode = since != null;
        if (sinceMode) {
            if (since.isBlank()) {
                throw GuideApiException.invalidField("since", "since must be an ISO-8601 timestamp.");
            }
            parseTime(since, "since");
        }
        Set<String> states = sinceMode ? STATES : parseStates(state);
        String anchorFilter = anchor == null || anchor.isBlank() ? null : anchor.trim();
        List<CommentRow> rows = store.comments(loaded.document.id);
        Map<Long, Person> people = store.people(userIds(rows, store.activeFlags(loaded.document.id)));
        JSONArray comments = new JSONArray();
        for (CommentRow parent : parents(rows)) {
            if (anchorFilter != null && !anchorFilter.equals(parent.anchor)) {
                continue;
            }
            if (sinceMode) {
                if (!ApiTime.atOrAfter(parent.updatedAt, since)) {
                    continue;
                }
                comments.put(parent.deleted()
                        ? tombstone(parent)
                        : thread(parent, replies(rows, parent.id), user, loaded, people, true));
            } else if (!parent.deleted() && states.contains(parent.state)) {
                comments.put(thread(parent, replies(rows, parent.id), user, loaded, people, false));
            }
        }
        JSONObject json = new JSONObject();
        json.put("serverTime", ApiTime.format(now));
        json.put("comments", comments);
        json.put("reviewFlags", flags(store.activeFlags(loaded.document.id), loaded.document.documentKey, people));
        return json;
    }

    public Posted post(User user, Long tokenId, String documentKey, JSONObject body) {
        Loaded loaded = requireComment(user, documentKey);
        LocalDateTime now = LocalDateTime.now();
        if (body.has("parentCommentId") && !body.isNull("parentCommentId")) {
            return reply(user, tokenId, loaded, body, now);
        }
        String anchor = requiredAnchor(body);
        int blockIndex = requiredBlockIndex(body);
        String text = requiredText(body);
        String snippet = optionalSnippet(body);
        String sourceNote = optionalSourceNote(body);
        CommentRow row = new CommentRow();
        row.documentId = loaded.document.id;
        row.anchor = anchor;
        row.blockIndex = blockIndex;
        row.snippet = snippet;
        row.text = text;
        row.state = "OPEN";
        row.userId = user.getUserId();
        row.sourceNote = sourceNote;
        row.createdAt = now;
        row.updatedAt = now;
        List<AuditRow> audits = new ArrayList<>();
        audits.add(audit(user, tokenId, now, "anchor", null, anchor));
        audits.add(audit(user, tokenId, now, "blockIndex", null, Integer.toString(blockIndex)));
        if (snippet != null) {
            audits.add(audit(user, tokenId, now, "snippet", null, snippet));
        }
        audits.add(audit(user, tokenId, now, "text", null, text));
        if (sourceNote != null) {
            audits.add(audit(user, tokenId, now, "sourceNote", null, sourceNote));
        }
        store.insertComment(row, null, audits);
        return new Posted(row.id, threadJson(loaded, row.id, user));
    }

    public JSONObject comment(User user, long id) {
        CommentRow row = live(store.comment(id));
        CommentRow parent = parentOf(row);
        Loaded loaded = requireView(user, parent == null ? row : parent);
        return threadJson(loaded, parent == null ? row.id : parent.id, user);
    }

    public JSONObject patchText(User user, Long tokenId, long id, JSONObject body) {
        refuseAuthorFields(body);
        refuseUnknown(body, Set.of("text", "expectedUpdatedAt"));
        CommentRow row = live(store.comment(id));
        CommentRow parent = parentOf(row);
        CommentRow thread = parent == null ? row : parent;
        Loaded loaded = requireComment(user, thread);
        LocalDateTime now = LocalDateTime.now();
        String replacement = null;
        if (body.has("text")) {
            replacement = requiredText(body);
        }
        checkExpected(body, thread, loaded, user);
        if (replacement == null) {
            return threadJson(loaded, thread.id, user);
        }
        if (!sameUser(row.userId, user)) {
            throw GuideApiException.forbidden("You cannot change another person's comment.");
        }
        if (parent == null && ("WITHDRAWN".equals(row.state) || "CLOSED".equals(row.state))) {
            throw GuideApiException.invalidState("This comment cannot be edited in its current state.");
        }
        if (parent != null && row.withdrawn()) {
            throw GuideApiException.invalidState("This reply cannot be edited after it is withdrawn.");
        }
        if (!withinWindow(row.createdAt, now)) {
            throw GuideApiException.editWindowClosed();
        }
        String text = replacement;
        String old = row.text;
        row.text = text;
        row.editedAt = now;
        row.updatedAt = now;
        if (parent != null) {
            parent.updatedAt = now;
        }
        store.updateComment(row, parent, List.of(audit(user, tokenId, now, "text", old, text)));
        return threadJson(loaded, thread.id, user);
    }

    public JSONObject withdraw(User user, Long tokenId, long id) {
        CommentRow row = live(store.comment(id));
        CommentRow parent = parentOf(row);
        CommentRow thread = parent == null ? row : parent;
        Loaded loaded = requireComment(user, thread);
        if (row.withdrawn()) {
            return threadJson(loaded, thread.id, user);
        }
        if (!sameUser(row.userId, user)) {
            throw GuideApiException.forbidden("You cannot withdraw another person's comment.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!withinWindow(row.createdAt, now)) {
            throw GuideApiException.editWindowClosed();
        }
        List<AuditRow> audits = new ArrayList<>();
        if (parent == null) {
            audits.add(audit(user, tokenId, now, "state", row.state, "WITHDRAWN"));
            row.state = "WITHDRAWN";
        } else {
            audits.add(audit(user, tokenId, now, "withdrawn", "false", "true"));
        }
        row.withdrawnAt = now;
        row.updatedAt = now;
        if (parent != null) {
            parent.updatedAt = now;
        }
        store.updateComment(row, parent, audits);
        return threadJson(loaded, thread.id, user);
    }

    private Posted reply(User user, Long tokenId, Loaded loaded, JSONObject body, LocalDateTime now) {
        long parentId = requiredLong(body, "parentCommentId");
        CommentRow parent = store.comment(parentId);
        if (parent == null || parent.documentId != loaded.document.id) {
            throw GuideApiException.notFound("That comment was not found.");
        }
        if (parent.parentId != null) {
            throw GuideApiException.invalidField("parentCommentId", "A reply cannot be added to a reply.");
        }
        if (parent.deleted() || "CLOSED".equals(parent.state) || "WITHDRAWN".equals(parent.state)) {
            throw GuideApiException.invalidState("Replies are closed on this comment.");
        }
        String text = requiredText(body);
        CommentRow row = new CommentRow();
        row.documentId = loaded.document.id;
        row.parentId = parent.id;
        row.text = text;
        row.state = "OPEN";
        row.userId = user.getUserId();
        row.createdAt = now;
        row.updatedAt = now;
        parent.updatedAt = now;
        store.insertComment(row, parent, List.of(audit(user, tokenId, now, "text", null, text)));
        return new Posted(row.id, threadJson(loaded, parent.id, user));
    }

    private JSONObject threadJson(Loaded loaded, long parentId, User user) {
        List<CommentRow> rows = store.comments(loaded.document.id);
        CommentRow parent = null;
        for (CommentRow row : rows) {
            if (row.id != null && row.id == parentId) {
                parent = row;
                break;
            }
        }
        if (parent == null || parent.deleted()) {
            throw GuideApiException.notFound("That comment was not found.");
        }
        Map<Long, Person> people = store.people(userIds(rows, List.of()));
        return thread(parent, replies(rows, parent.id), user, loaded, people, false);
    }

    private JSONObject thread(CommentRow parent, List<CommentRow> replies, User user, Loaded loaded,
            Map<Long, Person> people, boolean sinceMode) {
        JSONObject json = new JSONObject();
        json.put("id", parent.id);
        json.put("documentKey", loaded.document.documentKey);
        json.put("anchor", parent.anchor);
        json.put("blockIndex", parent.blockIndex == null ? JSONObject.NULL : parent.blockIndex);
        json.put("snippet", parent.snippet == null ? JSONObject.NULL : parent.snippet);
        json.put("text", parent.withdrawn() ? JSONObject.NULL : parent.text);
        json.put("state", parent.state);
        if (parent.dispositionCode == null) {
            json.put("disposition", JSONObject.NULL);
            json.put("changeNote", JSONObject.NULL);
        } else {
            JSONObject disposition = new JSONObject();
            disposition.put("code", parent.dispositionCode);
            disposition.put("label", dispositionLabel(parent.dispositionCode));
            json.put("disposition", disposition);
            json.put("changeNote", parent.changeNote == null ? JSONObject.NULL : parent.changeNote);
        }
        json.put("sourceNote", parent.sourceNote == null ? JSONObject.NULL : parent.sourceNote);
        json.put("author", GuideResponses.person(people.get(parent.userId)));
        json.put("createdAt", ApiTime.format(parent.createdAt));
        json.put("editedAt", time(parent.editedAt));
        json.put("editableUntil", time(parent.createdAt.plusDays(EDIT_WINDOW_DAYS)));
        json.put("updatedAt", time(parent.updatedAt));
        json.put("disposedAt", time(parent.disposedAt));
        json.put("disposedBy", parent.disposedByUserId == null
                ? JSONObject.NULL : GuideResponses.person(people.get(parent.disposedByUserId)));
        json.put("closedAt", time(parent.closedAt));
        json.put("closedBy", parent.closedByUserId == null
                ? JSONObject.NULL : GuideResponses.person(people.get(parent.closedByUserId)));
        json.put("deleted", false);
        json.put("permissions", threadPermissions(parent, user, loaded));
        JSONArray replyJson = new JSONArray();
        for (CommentRow reply : replies) {
            if (reply.deleted()) {
                if (sinceMode) {
                    replyJson.put(tombstone(reply));
                }
                continue;
            }
            replyJson.put(reply(reply, user, loaded, people));
        }
        json.put("replies", replyJson);
        return json;
    }

    private JSONObject reply(CommentRow row, User user, Loaded loaded, Map<Long, Person> people) {
        JSONObject json = new JSONObject();
        json.put("id", row.id);
        json.put("parentId", row.parentId);
        json.put("text", row.withdrawn() ? JSONObject.NULL : row.text);
        json.put("author", GuideResponses.person(people.get(row.userId)));
        json.put("createdAt", ApiTime.format(row.createdAt));
        json.put("editedAt", time(row.editedAt));
        json.put("editableUntil", time(row.createdAt.plusDays(EDIT_WINDOW_DAYS)));
        json.put("updatedAt", time(row.updatedAt));
        json.put("withdrawn", row.withdrawn());
        json.put("deleted", false);
        JSONObject permissions = new JSONObject();
        boolean ownWindow = sameUser(row.userId, user) && withinWindow(row.createdAt, LocalDateTime.now())
                && !row.withdrawn();
        permissions.put("canEdit", ownWindow);
        permissions.put("canWithdraw", ownWindow);
        permissions.put("canDelete", loaded.canEdit && !row.deleted());
        json.put("permissions", permissions);
        return json;
    }

    private JSONObject threadPermissions(CommentRow parent, User user, Loaded loaded) {
        boolean ownWindow = sameUser(parent.userId, user)
                && withinWindow(parent.createdAt, LocalDateTime.now())
                && !parent.withdrawn();
        boolean openForReply = "OPEN".equals(parent.state) || "DISPOSED".equals(parent.state);
        JSONObject permissions = new JSONObject();
        permissions.put("canEdit", ownWindow && !"CLOSED".equals(parent.state));
        permissions.put("canWithdraw", ownWindow);
        permissions.put("canReply", loaded.canView && loaded.document.active && openForReply);
        permissions.put("canDispose", loaded.canEdit && openForReply);
        permissions.put("canClose", loaded.canEdit && openForReply);
        permissions.put("canDelete", loaded.canEdit);
        permissions.put("canReattach", loaded.canEdit);
        return permissions;
    }

    private static JSONObject tombstone(CommentRow row) {
        JSONObject json = new JSONObject();
        json.put("id", row.id);
        json.put("deleted", true);
        json.put("updatedAt", time(row.updatedAt));
        return json;
    }

    private JSONArray flags(List<FlagRow> flags, String documentKey, Map<Long, Person> people) {
        JSONArray array = new JSONArray();
        for (FlagRow flag : flags) {
            JSONObject json = new JSONObject();
            json.put("id", flag.id);
            json.put("documentKey", documentKey);
            json.put("anchor", flag.anchor);
            json.put("note", flag.note);
            json.put("createdBy", GuideResponses.person(people.get(flag.createdByUserId)));
            json.put("createdAt", ApiTime.format(flag.createdAt));
            json.put("updatedAt", ApiTime.format(flag.updatedAt));
            json.put("clearedAt", JSONObject.NULL);
            json.put("clearedBy", JSONObject.NULL);
            array.put(json);
        }
        return array;
    }

    private String dispositionLabel(String code) {
        for (Disposition disposition : store.dispositions()) {
            if (code.equals(disposition.code)) {
                return disposition.label;
            }
        }
        return code;
    }

    private void checkExpected(JSONObject body, CommentRow thread, Loaded loaded, User user) {
        if (!body.has("expectedUpdatedAt") || body.isNull("expectedUpdatedAt")) {
            return;
        }
        Object raw = body.get("expectedUpdatedAt");
        if (!(raw instanceof String text)) {
            throw GuideApiException.invalidField("expectedUpdatedAt", "expectedUpdatedAt must be an ISO-8601 timestamp.");
        }
        parseTime(text, "expectedUpdatedAt");
        if (!ApiTime.sameInstant(thread.updatedAt, text)) {
            throw GuideApiException.conflict("The comment was updated. Refresh and try again.",
                    threadJson(loaded, thread.id, user).toString());
        }
    }

    private static void refuseAuthorFields(JSONObject body) {
        for (String field : AUTHOR_FIELDS) {
            if (body.has(field)) {
                throw GuideApiException.invalidField(field, "That field is not accepted on this request.");
            }
        }
    }

    private static void refuseUnknown(JSONObject body, Set<String> allowed) {
        for (String key : body.keySet()) {
            if (!allowed.contains(key) && !AUTHOR_FIELDS.contains(key)) {
                throw GuideApiException.invalidField(key, "That field is not accepted on this request.");
            }
        }
    }

    private Loaded requireComment(User user, String documentKey) {
        Loaded loaded = requireView(user, documentKey);
        if (!loaded.document.active) {
            throw GuideApiException.documentInactive();
        }
        return loaded;
    }

    private Loaded requireComment(User user, CommentRow thread) {
        DocumentRow document = store.documentById(thread.documentId);
        if (document == null) {
            throw GuideApiException.notFound("That guide was not found.");
        }
        Loaded loaded = access(user, document);
        if (!loaded.canView) {
            throw GuideApiException.forbidden("You cannot view comments on this guide.");
        }
        if (!document.active) {
            throw GuideApiException.documentInactive();
        }
        return loaded;
    }

    private Loaded requireView(User user, String documentKey) {
        Loaded loaded = load(user, documentKey);
        if (!loaded.canView) {
            throw GuideApiException.forbidden("You cannot view comments on this guide.");
        }
        return loaded;
    }

    private Loaded requireView(User user, CommentRow thread) {
        DocumentRow document = store.documentById(thread.documentId);
        if (document == null) {
            throw GuideApiException.notFound("That guide was not found.");
        }
        Loaded loaded = access(user, document);
        if (!loaded.canView) {
            throw GuideApiException.forbidden("You cannot view comments on this guide.");
        }
        return loaded;
    }

    private Loaded access(User user, DocumentRow document) {
        TopicRow topic = store.topic(document.topicId);
        boolean canView = topic != null && store.canView(user, topic);
        boolean canEdit = topic != null && store.canEdit(user, topic);
        return new Loaded(document, topic, canView, canEdit);
    }

    private Loaded load(User user, String documentKey) {
        String key;
        try {
            key = GuideUrlRules.documentKey(documentKey);
        } catch (GuideApiException ex) {
            throw GuideApiException.notFound("That guide was not found.");
        }
        DocumentRow document = store.documentByKey(key);
        if (document == null) {
            throw GuideApiException.notFound("That guide was not found.");
        }
        return access(user, document);
    }

    private CommentRow live(CommentRow row) {
        if (row == null || row.deleted()) {
            throw GuideApiException.notFound("That comment was not found.");
        }
        return row;
    }

    private CommentRow parentOf(CommentRow row) {
        if (row.parentId == null) {
            return null;
        }
        CommentRow parent = store.comment(row.parentId);
        if (parent == null || parent.deleted()) {
            throw GuideApiException.notFound("That comment was not found.");
        }
        return parent;
    }

    private static List<CommentRow> parents(List<CommentRow> rows) {
        List<CommentRow> parents = new ArrayList<>();
        for (CommentRow row : rows) {
            if (row.parentId == null) {
                parents.add(row);
            }
        }
        parents.sort(Comparator.comparing((CommentRow row) -> row.createdAt).thenComparing(row -> row.id));
        return parents;
    }

    private static List<CommentRow> replies(List<CommentRow> rows, Long parentId) {
        List<CommentRow> replies = new ArrayList<>();
        for (CommentRow row : rows) {
            if (parentId != null && parentId.equals(row.parentId)) {
                replies.add(row);
            }
        }
        replies.sort(Comparator.comparing((CommentRow row) -> row.createdAt).thenComparing(row -> row.id));
        return replies;
    }

    private static Set<Long> userIds(List<CommentRow> rows, List<FlagRow> flags) {
        Set<Long> ids = new LinkedHashSet<>();
        for (CommentRow row : rows) {
            ids.add(row.userId);
            if (row.disposedByUserId != null) {
                ids.add(row.disposedByUserId);
            }
            if (row.closedByUserId != null) {
                ids.add(row.closedByUserId);
            }
        }
        for (FlagRow flag : flags) {
            ids.add(flag.createdByUserId);
        }
        return ids;
    }

    private static Set<String> parseStates(String state) {
        if (state == null || state.isBlank()) {
            return Set.of("OPEN", "DISPOSED");
        }
        if ("all".equals(state.trim())) {
            return STATES;
        }
        Set<String> parsed = new LinkedHashSet<>();
        for (String part : state.split(",")) {
            String token = part.trim();
            if (!STATES.contains(token)) {
                throw GuideApiException.invalidField("state", "State must be a known comment state, or all.");
            }
            parsed.add(token);
        }
        if (parsed.isEmpty()) {
            throw GuideApiException.invalidField("state", "State must be a known comment state, or all.");
        }
        return parsed;
    }

    private static void parseTime(String value, String field) {
        try {
            ApiTime.parseInstant(value);
        } catch (DateTimeParseException ex) {
            throw GuideApiException.invalidField(field, field + " must be an ISO-8601 timestamp.");
        }
    }

    private static String requiredAnchor(JSONObject body) {
        if (!body.has("anchor") || body.isNull("anchor") || !(body.get("anchor") instanceof String raw)) {
            throw GuideApiException.invalidField("anchor", "An anchor is required.");
        }
        String anchor = raw.trim();
        if (anchor.isEmpty() || anchor.length() > 100 || !ANCHOR.matcher(anchor).matches()) {
            throw GuideApiException.invalidField("anchor", "Anchor must start with a letter or digit and stay within 100 characters.");
        }
        return anchor;
    }

    private static int requiredBlockIndex(JSONObject body) {
        if (!body.has("blockIndex") || body.isNull("blockIndex")) {
            throw GuideApiException.invalidField("blockIndex", "A block index is required.");
        }
        Object raw = body.get("blockIndex");
        if (!(raw instanceof Number number) || number.doubleValue() != number.longValue()) {
            throw GuideApiException.invalidField("blockIndex", "Block index must be a whole number.");
        }
        int value = number.intValue();
        if (value < 0) {
            throw GuideApiException.invalidField("blockIndex", "Block index must be zero or greater.");
        }
        return value;
    }

    private static String requiredText(JSONObject body) {
        if (!body.has("text") || body.isNull("text") || !(body.get("text") instanceof String raw)) {
            throw GuideApiException.invalidField("text", "Text must be 1 to 4,000 characters.");
        }
        String text = raw.trim();
        if (text.isEmpty() || text.length() > MAX_TEXT) {
            throw GuideApiException.invalidField("text", "Text must be 1 to 4,000 characters.");
        }
        return text;
    }

    private static String optionalSnippet(JSONObject body) {
        if (!body.has("snippet") || body.isNull("snippet")) {
            return null;
        }
        if (!(body.get("snippet") instanceof String raw)) {
            throw GuideApiException.invalidField("snippet", "Snippet must be at most 120 characters.");
        }
        if (raw.isBlank()) {
            return null;
        }
        if (raw.length() > 120) {
            throw GuideApiException.invalidField("snippet", "Snippet must be at most 120 characters.");
        }
        return raw;
    }

    private static String optionalSourceNote(JSONObject body) {
        if (!body.has("sourceNote") || body.isNull("sourceNote")) {
            return null;
        }
        if (!(body.get("sourceNote") instanceof String raw)) {
            throw GuideApiException.invalidField("sourceNote", "Source note must be at most 200 characters.");
        }
        String note = raw.trim();
        if (note.isEmpty()) {
            return null;
        }
        if (note.length() > 200) {
            throw GuideApiException.invalidField("sourceNote", "Source note must be at most 200 characters.");
        }
        return note;
    }

    private static long requiredLong(JSONObject body, String field) {
        Object raw = body.get(field);
        if (!(raw instanceof Number number) || number.doubleValue() != number.longValue() || number.longValue() <= 0) {
            throw GuideApiException.invalidField(field, "That comment was not found.");
        }
        return number.longValue();
    }

    private static boolean sameUser(long rowUserId, User user) {
        return user.getUserId() != null && user.getUserId().longValue() == rowUserId;
    }

    private static Object time(LocalDateTime value) {
        String formatted = ApiTime.format(value);
        return formatted == null ? JSONObject.NULL : formatted;
    }

    static boolean withinWindow(LocalDateTime createdAt, LocalDateTime now) {
        return createdAt != null && !now.isAfter(createdAt.plusDays(EDIT_WINDOW_DAYS));
    }

    private static AuditRow audit(User user, Long tokenId, LocalDateTime at, String field, String oldValue, String newValue) {
        AuditRow audit = new AuditRow();
        audit.userId = user.getUserId();
        audit.tokenId = tokenId;
        audit.at = at;
        audit.field = field;
        audit.oldValue = oldValue;
        audit.newValue = newValue;
        return audit;
    }

    public record Posted(long id, JSONObject thread) {
    }

    private record Loaded(DocumentRow document, TopicRow topic, boolean canView, boolean canEdit) {
    }

    interface Store {
        DocumentRow documentByKey(String key);

        DocumentRow documentById(long id);

        TopicRow topic(long topicId);

        boolean canView(User user, TopicRow topic);

        boolean canEdit(User user, TopicRow topic);

        Person personOf(User user);

        Map<Long, Person> people(Collection<Long> userIds);

        List<Disposition> dispositions();

        List<CommentRow> comments(long documentId);

        List<FlagRow> activeFlags(long documentId);

        CommentRow comment(long id);

        void insertComment(CommentRow row, CommentRow parent, List<AuditRow> audits);

        void updateComment(CommentRow row, CommentRow parent, List<AuditRow> audits);

        String topicUrl(long topicId);
    }
}
