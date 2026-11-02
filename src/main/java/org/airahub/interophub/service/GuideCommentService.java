package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Comment reads and writes for contract 5.1 and 5.3–5.8, and review flags in Section 6.
 */
public class GuideCommentService {
    static final int EDIT_WINDOW_DAYS = 14;
    public static final int PANEL_LIMIT = 5;
    private static final int MAX_TEXT = 4000;
    private static final int MAX_NOTE = 500;
    private static final Pattern ANCHOR = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]*$");
    private static final Set<String> STATES = Set.of("OPEN", "DISPOSED", "CLOSED", "WITHDRAWN");
    private static final Set<String> PATCH_FIELDS = Set.of(
            "text", "expectedUpdatedAt", "dispositionCode", "changeNote", "state", "anchor", "blockIndex", "snippet");
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

    public JSONObject patch(User user, Long tokenId, long id, JSONObject body) {
        refuseUnknown(body, PATCH_FIELDS);
        CommentRow row = live(store.comment(id));
        CommentRow parent = parentOf(row);
        CommentRow thread = parent == null ? row : parent;
        Loaded loaded = requireComment(user, thread);
        if (parent != null) {
            for (String field : AUTHOR_FIELDS) {
                if (body.has(field)) {
                    throw GuideApiException.invalidField(field, "That field is not accepted on a reply.");
                }
            }
        } else if (hasAuthorField(body) && !loaded.canEdit) {
            throw GuideApiException.forbidden("Only an author can change that.");
        }
        PatchPlan plan = readPatch(body, parent == null);
        validatePatch(plan, row, parent, user);
        checkExpected(body, thread, loaded, user);
        List<AuditRow> audits = applyPatch(plan, row, user, tokenId);
        if (audits.isEmpty()) {
            return threadJson(loaded, thread.id, user);
        }
        if (parent != null) {
            parent.updatedAt = row.updatedAt;
        }
        store.updateComment(row, parent, audits);
        return threadJson(loaded, thread.id, user);
    }

    public void delete(User user, Long tokenId, long id) {
        CommentRow row = live(store.comment(id));
        CommentRow parent = parentOf(row);
        CommentRow thread = parent == null ? row : parent;
        Loaded loaded = requireComment(user, thread);
        if (!loaded.canEdit) {
            throw GuideApiException.forbidden("Only an author can delete a comment.");
        }
        LocalDateTime now = LocalDateTime.now();
        List<CommentRow> targets = new ArrayList<>();
        targets.add(row);
        if (parent == null) {
            for (CommentRow reply : replies(store.comments(row.documentId), row.id)) {
                if (!reply.deleted()) {
                    targets.add(reply);
                }
            }
        }
        List<AuditRow> audits = new ArrayList<>();
        for (CommentRow target : targets) {
            target.deletedAt = now;
            target.deletedByUserId = user.getUserId();
            target.updatedAt = now;
            audits.add(auditFor(target.id, user, tokenId, now, "deleted", "false", "true"));
        }
        if (parent != null) {
            parent.updatedAt = now;
            targets.add(parent);
        }
        store.updateComments(targets, audits);
    }

    public JSONObject reviewFlags(User user, String documentKey, String includeCleared) {
        Loaded loaded = requireView(user, documentKey);
        boolean include = includeCleared(includeCleared);
        List<FlagRow> rows = store.flags(loaded.document.id, include);
        Map<Long, Person> people = store.people(userIds(List.of(), rows));
        JSONObject json = new JSONObject();
        json.put("serverTime", ApiTime.format(ApiTime.now()));
        json.put("reviewFlags", flags(rows, loaded.document.documentKey, people));
        return json;
    }

    public FlagResult flag(User user, String documentKey, JSONObject body) {
        refuseUnknown(body, Set.of("anchor", "note"));
        Loaded loaded = requireComment(user, documentKey);
        if (!loaded.canEdit) {
            throw GuideApiException.forbidden("Only an author can flag an area for review.");
        }
        String anchor = requiredAnchor(body);
        String note = requiredNote(body);
        LocalDateTime now = LocalDateTime.now();
        FlagRow existing = store.activeFlag(loaded.document.id, anchor);
        if (existing != null) {
            if (!note.equals(existing.note)) {
                existing.note = note;
                existing.updatedAt = now;
                store.saveFlag(existing);
            }
            return new FlagResult(200, flagJson(existing, loaded.document.documentKey, user));
        }
        FlagRow row = new FlagRow();
        row.documentId = loaded.document.id;
        row.anchor = anchor;
        row.note = note;
        row.createdByUserId = user.getUserId();
        row.createdAt = now;
        row.updatedAt = now;
        store.insertFlag(row);
        return new FlagResult(201, flagJson(row, loaded.document.documentKey, user));
    }

    public void clearFlag(User user, String documentKey, long id) {
        Loaded loaded = requireComment(user, documentKey);
        if (!loaded.canEdit) {
            throw GuideApiException.forbidden("Only an author can clear a review flag.");
        }
        FlagRow row = store.flag(id);
        if (row == null || row.documentId != loaded.document.id) {
            throw GuideApiException.notFound("That flag was not found.");
        }
        if (row.cleared()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        row.clearedAt = now;
        row.clearedByUserId = user.getUserId();
        row.updatedAt = now;
        store.saveFlag(row);
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
            json.put("clearedAt", time(flag.clearedAt));
            json.put("clearedBy", flag.clearedByUserId == null
                    ? JSONObject.NULL : GuideResponses.person(people.get(flag.clearedByUserId)));
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

    private PatchPlan readPatch(JSONObject body, boolean topLevel) {
        PatchPlan plan = new PatchPlan();
        if (body.has("text")) {
            plan.text = requiredText(body);
            plan.textSet = true;
        }
        if (topLevel && body.has("dispositionCode")) {
            plan.dispositionSet = true;
            plan.dispositionCode = dispositionCode(body);
        }
        if (topLevel && body.has("changeNote")) {
            plan.changeNoteSet = true;
            plan.changeNote = changeNoteValue(body);
        }
        if (topLevel && body.has("state")) {
            plan.stateSet = true;
            plan.requestedState = requestedState(body);
        }
        boolean reattach = topLevel && (body.has("anchor") || body.has("blockIndex") || body.has("snippet"));
        if (reattach) {
            if (!body.has("anchor") || body.isNull("anchor")) {
                throw GuideApiException.invalidField("anchor", "An anchor is required to re-attach a comment.");
            }
            plan.anchor = requiredAnchor(body);
            plan.anchorSet = true;
            if (body.has("blockIndex")) {
                plan.blockIndex = requiredBlockIndex(body);
                plan.blockIndexSet = true;
            }
            if (body.has("snippet")) {
                plan.snippet = optionalSnippet(body);
                plan.snippetSet = true;
            }
        }
        return plan;
    }

    private void validatePatch(PatchPlan plan, CommentRow row, CommentRow parent, User user) {
        if (plan.textSet) {
            if (!sameUser(row.userId, user)) {
                throw GuideApiException.forbidden("You cannot change another person's comment.");
            }
            if (parent == null && ("WITHDRAWN".equals(row.state) || "CLOSED".equals(row.state))) {
                throw GuideApiException.invalidState("This comment cannot be edited in its current state.");
            }
            if (parent != null && row.withdrawn()) {
                throw GuideApiException.invalidState("This reply cannot be edited after it is withdrawn.");
            }
            if (!withinWindow(row.createdAt, LocalDateTime.now())) {
                throw GuideApiException.editWindowClosed();
            }
        }
        if (parent != null) {
            return;
        }
        boolean closedOrWithdrawn = "CLOSED".equals(row.state) || "WITHDRAWN".equals(row.state);
        if (plan.dispositionSet && closedOrWithdrawn) {
            throw GuideApiException.invalidState("A disposition cannot be changed in this comment's current state.");
        }
        boolean setting = plan.dispositionSet && plan.dispositionCode != null;
        boolean clearing = plan.dispositionSet && plan.dispositionCode == null;
        boolean dispositionRemains = setting || (row.dispositionCode != null && !clearing);
        if (setting && plan.changeNote == null && !plan.changeNoteSet && (row.changeNote == null || row.changeNote.isBlank())) {
            throw GuideApiException.invalidField("changeNote", "A change note is required when setting a disposition.");
        }
        if (setting && plan.changeNoteSet && plan.changeNote == null) {
            throw GuideApiException.invalidField("changeNote", "A change note is required when setting a disposition.");
        }
        if (plan.changeNoteSet && plan.changeNote != null && !dispositionRemains) {
            throw GuideApiException.invalidField("changeNote", "A change note can only be set with a disposition.");
        }
        if (plan.changeNoteSet && plan.changeNote == null && dispositionRemains) {
            throw GuideApiException.invalidField("changeNote", "A change note is required when a disposition is set.");
        }
        if (plan.stateSet) {
            if ("CLOSED".equals(plan.requestedState)) {
                if (!"OPEN".equals(row.state) && !"DISPOSED".equals(row.state)) {
                    throw GuideApiException.invalidState("This comment cannot be closed in its current state.");
                }
            } else if (!"CLOSED".equals(row.state)) {
                throw GuideApiException.invalidState("Only a closed comment can be reopened.");
            }
        }
    }

    private List<AuditRow> applyPatch(PatchPlan plan, CommentRow row, User user, Long tokenId) {
        String newText = plan.textSet ? plan.text : row.text;
        String newAnchor = plan.anchorSet ? plan.anchor : row.anchor;
        Integer newBlock = plan.blockIndexSet ? plan.blockIndex : row.blockIndex;
        String newSnippet = plan.snippetSet ? plan.snippet : row.snippet;
        String newDisposition = row.dispositionCode;
        String newNote = row.changeNote;
        String newState = row.state;
        if (plan.dispositionSet) {
            if (plan.dispositionCode == null) {
                newDisposition = null;
                newNote = null;
                if ("DISPOSED".equals(newState)) {
                    newState = "OPEN";
                }
            } else {
                newDisposition = plan.dispositionCode;
                if (plan.changeNoteSet && plan.changeNote != null) {
                    newNote = plan.changeNote;
                }
                if ("OPEN".equals(newState)) {
                    newState = "DISPOSED";
                }
            }
        } else if (plan.changeNoteSet) {
            newNote = plan.changeNote;
        }
        if (plan.stateSet) {
            newState = "CLOSED".equals(plan.requestedState)
                    ? "CLOSED"
                    : (newDisposition != null ? "DISPOSED" : "OPEN");
        }
        boolean textChanged = plan.textSet && !Objects.equals(row.text, newText);
        boolean anchorChanged = plan.anchorSet && !Objects.equals(row.anchor, newAnchor);
        boolean blockChanged = plan.blockIndexSet && !Objects.equals(row.blockIndex, newBlock);
        boolean snippetChanged = plan.snippetSet && !Objects.equals(row.snippet, newSnippet);
        boolean dispositionChanged = !Objects.equals(row.dispositionCode, newDisposition);
        boolean noteChanged = !Objects.equals(row.changeNote, newNote);
        boolean stateChanged = !Objects.equals(row.state, newState);
        if (!textChanged && !anchorChanged && !blockChanged && !snippetChanged
                && !dispositionChanged && !noteChanged && !stateChanged) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<AuditRow> audits = new ArrayList<>();
        if (textChanged) {
            audits.add(auditFor(row.id, user, tokenId, now, "text", row.text, newText));
            row.text = newText;
            row.editedAt = now;
        }
        if (anchorChanged) {
            audits.add(auditFor(row.id, user, tokenId, now, "anchor", row.anchor, newAnchor));
            row.anchor = newAnchor;
        }
        if (blockChanged) {
            audits.add(auditFor(row.id, user, tokenId, now, "blockIndex",
                    row.blockIndex == null ? null : Integer.toString(row.blockIndex),
                    newBlock == null ? null : Integer.toString(newBlock)));
            row.blockIndex = newBlock;
        }
        if (snippetChanged) {
            audits.add(auditFor(row.id, user, tokenId, now, "snippet", row.snippet, newSnippet));
            row.snippet = newSnippet;
        }
        if (dispositionChanged) {
            audits.add(auditFor(row.id, user, tokenId, now, "dispositionCode", row.dispositionCode, newDisposition));
            row.dispositionCode = newDisposition;
            if (newDisposition == null) {
                row.disposedAt = null;
                row.disposedByUserId = null;
            } else {
                row.disposedAt = now;
                row.disposedByUserId = user.getUserId();
            }
        }
        if (noteChanged) {
            audits.add(auditFor(row.id, user, tokenId, now, "changeNote", row.changeNote, newNote));
            row.changeNote = newNote;
        }
        if (stateChanged) {
            audits.add(auditFor(row.id, user, tokenId, now, "state", row.state, newState));
            if ("CLOSED".equals(newState)) {
                row.closedAt = now;
                row.closedByUserId = user.getUserId();
            } else if ("CLOSED".equals(row.state)) {
                row.closedAt = null;
                row.closedByUserId = null;
            }
            row.state = newState;
        }
        row.updatedAt = now;
        return audits;
    }

    private JSONObject flagJson(FlagRow flag, String documentKey, User actor) {
        Map<Long, Person> people = store.people(userIds(List.of(), List.of(flag)));
        if (actor != null && actor.getUserId() != null && !people.containsKey(actor.getUserId())) {
            Person person = store.personOf(actor);
            if (person != null) {
                people.put(actor.getUserId(), person);
            }
        }
        return flags(List.of(flag), documentKey, people).getJSONObject(0);
    }

    private String dispositionCode(JSONObject body) {
        if (body.isNull("dispositionCode")) {
            return null;
        }
        if (!(body.get("dispositionCode") instanceof String raw)) {
            throw GuideApiException.invalidField("dispositionCode", "Disposition must be one of the guide's dispositions.");
        }
        String code = raw.trim();
        for (Disposition disposition : store.dispositions()) {
            if (code.equals(disposition.code)) {
                return code;
            }
        }
        throw GuideApiException.invalidField("dispositionCode", "Disposition must be one of the guide's dispositions.");
    }

    private static String changeNoteValue(JSONObject body) {
        if (body.isNull("changeNote")) {
            return null;
        }
        if (!(body.get("changeNote") instanceof String raw)) {
            throw GuideApiException.invalidField("changeNote", "Change note must be 1 to 500 characters.");
        }
        String note = raw.trim();
        if (note.isEmpty() || note.length() > MAX_NOTE) {
            throw GuideApiException.invalidField("changeNote", "Change note must be 1 to 500 characters.");
        }
        return note;
    }

    private static String requestedState(JSONObject body) {
        if (body.isNull("state") || !(body.get("state") instanceof String raw)) {
            throw GuideApiException.invalidField("state", "State must be CLOSED or OPEN.");
        }
        if (!"CLOSED".equals(raw) && !"OPEN".equals(raw)) {
            throw GuideApiException.invalidField("state", "State must be CLOSED or OPEN.");
        }
        return raw;
    }

    private static String requiredNote(JSONObject body) {
        if (!body.has("note") || body.isNull("note") || !(body.get("note") instanceof String raw)) {
            throw GuideApiException.invalidField("note", "Note must be 1 to 500 characters.");
        }
        String note = raw.trim();
        if (note.isEmpty() || note.length() > MAX_NOTE) {
            throw GuideApiException.invalidField("note", "Note must be 1 to 500 characters.");
        }
        return note;
    }

    private static boolean includeCleared(String value) {
        if (value == null || value.isBlank() || "false".equals(value)) {
            return false;
        }
        if ("true".equals(value)) {
            return true;
        }
        throw GuideApiException.invalidRequest("includeCleared must be true or false.");
    }

    private static boolean hasAuthorField(JSONObject body) {
        for (String field : AUTHOR_FIELDS) {
            if (body.has(field)) {
                return true;
            }
        }
        return false;
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
            if (flag.clearedByUserId != null) {
                ids.add(flag.clearedByUserId);
            }
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

    private static AuditRow auditFor(long commentId, User user, Long tokenId, LocalDateTime at,
            String field, String oldValue, String newValue) {
        AuditRow audit = audit(user, tokenId, at, field, oldValue, newValue);
        audit.commentId = commentId;
        return audit;
    }

    public record Posted(long id, JSONObject thread) {
    }

    public record FlagResult(int status, JSONObject flag) {
    }

    private static final class PatchPlan {
        boolean textSet;
        String text;
        boolean dispositionSet;
        String dispositionCode;
        boolean changeNoteSet;
        String changeNote;
        boolean stateSet;
        String requestedState;
        boolean anchorSet;
        String anchor;
        boolean blockIndexSet;
        Integer blockIndex;
        boolean snippetSet;
        String snippet;
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

        List<FlagRow> flags(long documentId, boolean includeCleared);

        FlagRow flag(long id);

        FlagRow activeFlag(long documentId, String anchor);

        void insertFlag(FlagRow row);

        void saveFlag(FlagRow row);

        CommentRow comment(long id);

        void insertComment(CommentRow row, CommentRow parent, List<AuditRow> audits);

        void updateComment(CommentRow row, CommentRow parent, List<AuditRow> audits);

        void updateComments(List<CommentRow> rows, List<AuditRow> audits);

        String topicUrl(long topicId);
    }
}
