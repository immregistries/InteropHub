package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.GuideRows.AllowInsert;
import org.airahub.interophub.service.GuideRows.AllowRow;
import org.airahub.interophub.service.GuideRows.AllowToggle;
import org.airahub.interophub.service.GuideRows.Disposition;
import org.airahub.interophub.service.GuideRows.DocumentRow;
import org.airahub.interophub.service.GuideRows.Person;
import org.airahub.interophub.service.GuideRows.RegistrationPlan;
import org.airahub.interophub.service.GuideRows.TopicRow;
import org.airahub.interophub.service.GuideRows.UrlBinding;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Guide registration. The same checks serve {@code POST /api/guides},
 * {@code PATCH /api/guide/{documentKey}}, and the topic registration page.
 */
public class GuideRegistrationService {
    private final Store store;

    public GuideRegistrationService() {
        this(new HibernateGuideRegistrationStore());
    }

    GuideRegistrationService(Store store) {
        this.store = store;
    }

    public JSONObject create(User user, Long tokenId, String source, boolean dryRun, String topicCode,
            String documentKey, String title, String repositoryUrl, List<UrlInput> urls) {
        requireSource(source);
        String key = GuideUrlRules.documentKey(documentKey);
        if (store.documentByKey(key) != null) {
            throw GuideApiException.duplicate("documentKey", "That document key is already registered.");
        }
        TopicRow topic = store.topicByCode(topicCode == null ? "" : topicCode.trim());
        if (topic == null) {
            throw GuideApiException.invalidField("topicCode", "Topic was not found.");
        }
        if (!store.canEdit(user, topic)) {
            throw GuideApiException.forbidden("You cannot edit this topic.");
        }
        String normalizedTitle = GuideUrlRules.title(title);
        String repository = GuideUrlRules.repositoryUrl(repositoryUrl);
        if (urls == null || urls.isEmpty()) {
            throw GuideApiException.invalidField("urls", "Add at least one URL.");
        }
        DocumentRow document = new DocumentRow();
        document.documentKey = key;
        document.topicId = topic.id;
        document.title = normalizedTitle;
        document.repositoryUrl = repository;
        document.active = true;
        document.createdByUserId = user.getUserId();
        LocalDateTime now = LocalDateTime.now();
        document.createdAt = now;
        document.updatedAt = now;
        RegistrationPlan plan = new RegistrationPlan();
        plan.creating = true;
        plan.document = document;
        plan.topicId = topic.id;
        plan.userId = user.getUserId();
        plan.tokenId = tokenId;
        plan.source = source;
        plan.urls = bindUrls(urls, null, true, plan, user);
        document.urls = plan.urls;
        plan.changesJson = createChanges(document).toString();
        if (!dryRun) {
            store.persist(plan);
        }
        return response(user, topic, document, plan, dryRun);
    }

    public JSONObject update(User user, Long tokenId, String source, boolean dryRun, String documentKey,
            boolean hasTitle, String title, boolean hasRepository, String repositoryUrl, boolean hasUrls,
            List<UrlInput> urls, boolean hasActive, Boolean active) {
        requireSource(source);
        String key = GuideUrlRules.documentKey(documentKey);
        DocumentRow current = store.documentByKey(key);
        if (current == null) {
            throw GuideApiException.notFound("That guide was not found.");
        }
        TopicRow topic = store.topicById(current.topicId);
        if (topic == null || !store.canEdit(user, topic)) {
            throw GuideApiException.forbidden("You cannot edit this topic.");
        }
        DocumentRow next = copy(current);
        if (hasTitle) {
            next.title = GuideUrlRules.title(title);
        }
        if (hasRepository) {
            next.repositoryUrl = GuideUrlRules.repositoryUrl(repositoryUrl);
        }
        if (hasActive) {
            if (active == null) {
                throw GuideApiException.invalidField("isActive", "isActive must be true or false.");
            }
            next.active = active;
        }
        RegistrationPlan plan = new RegistrationPlan();
        plan.creating = false;
        plan.document = next;
        plan.topicId = topic.id;
        plan.userId = user.getUserId();
        plan.tokenId = tokenId;
        plan.source = source;
        plan.urls = hasUrls ? bindUrls(urls, current, next.active, plan, user) : retoggle(current, next.active, plan);
        next.urls = plan.urls;
        next.updatedAt = LocalDateTime.now();
        JSONObject changes = updateChanges(current, next, hasUrls);
        plan.changesJson = changes.isEmpty() ? null : changes.toString();
        boolean writes = plan.changesJson != null || !plan.inserts.isEmpty() || !plan.toggles.isEmpty();
        if (!dryRun && writes) {
            store.persist(plan);
        }
        return response(user, topic, next, plan, dryRun);
    }

    public TopicRow editableTopic(User user, long topicId) {
        TopicRow topic = store.topicById(topicId);
        if (topic == null || !store.canView(user, topic)) {
            return null;
        }
        if (!store.canEdit(user, topic)) {
            return null;
        }
        return topic;
    }

    public TopicRow viewableTopic(User user, long topicId) {
        TopicRow topic = store.topicById(topicId);
        if (topic == null || !store.canView(user, topic)) {
            return null;
        }
        return topic;
    }

    public boolean canEdit(User user, TopicRow topic) {
        return store.canEdit(user, topic);
    }

    public List<DocumentRow> documentsForTopic(long topicId) {
        return store.documentsByTopic(topicId);
    }

    public DocumentRow document(String documentKey) {
        try {
            return store.documentByKey(GuideUrlRules.documentKey(documentKey));
        } catch (GuideApiException ex) {
            return null;
        }
    }

    private JSONObject response(User user, TopicRow topic, DocumentRow document, RegistrationPlan plan, boolean dryRun) {
        boolean canView = store.canView(user, topic);
        boolean canEdit = store.canEdit(user, topic);
        return GuideResponses.document(document, topic, canView, canEdit, store.personOf(user), store.dispositions(),
                ApiTime.format(LocalDateTime.now()), store.topicUrl(topic.id), plan.added, plan.removed, dryRun);
    }

    /** Keeps the current URLs and enables or disables the rows this registration owns. */
    private List<UrlBinding> retoggle(DocumentRow current, boolean documentActive, RegistrationPlan plan) {
        List<UrlBinding> kept = new ArrayList<>();
        Map<String, AllowRow> allowByUrl = new LinkedHashMap<>();
        for (AllowRow row : store.allowlist()) {
            allowByUrl.put(row.baseUrl, row);
        }
        int sort = 0;
        for (UrlBinding previous : current.urls) {
            UrlBinding copy = copyBinding(previous);
            copy.sortOrder = sort++;
            AllowRow allow = allowByUrl.get(previous.url);
            if (previous.ownsAllowlist && previous.allowId != null && allow != null && allow.enabled != documentActive) {
                plan.toggles.add(new AllowToggle(previous.allowId, documentActive));
                if (documentActive) {
                    plan.added.add(previous.url);
                } else {
                    plan.removed.add(previous.url);
                }
            }
            kept.add(copy);
        }
        return kept;
    }

    private UrlBinding bindOne(String url, String label, int sort, UrlBinding previous, AllowRow existing,
            boolean documentActive, RegistrationPlan plan) {
        UrlBinding binding = new UrlBinding(url, label);
        binding.sortOrder = sort;
        if (previous != null && previous.ownsAllowlist && previous.allowId != null) {
            binding.ownsAllowlist = true;
            binding.allowId = previous.allowId;
            if (existing != null && existing.enabled != documentActive) {
                plan.toggles.add(new AllowToggle(previous.allowId, documentActive));
                if (documentActive) {
                    plan.added.add(url);
                } else {
                    plan.removed.add(url);
                }
            }
            return binding;
        }
        if (existing == null) {
            binding.ownsAllowlist = true;
            binding.insertNew = true;
            plan.inserts.add(new AllowInsert(url, documentActive));
            if (documentActive) {
                plan.added.add(url);
            }
            return binding;
        }
        if (!existing.enabled && documentActive) {
            binding.ownsAllowlist = true;
            binding.allowId = existing.allowId;
            plan.toggles.add(new AllowToggle(existing.allowId, true));
            plan.added.add(url);
            return binding;
        }
        binding.ownsAllowlist = false;
        binding.allowId = existing.allowId;
        return binding;
    }

    private static void disableRemoved(Map<String, UrlBinding> previous, Set<String> kept,
            Map<String, AllowRow> allowByUrl, RegistrationPlan plan) {
        for (UrlBinding old : previous.values()) {
            if (kept.contains(old.url) || !old.ownsAllowlist || old.allowId == null) {
                continue;
            }
            AllowRow allow = allowByUrl.get(old.url);
            if (allow != null && allow.enabled) {
                plan.toggles.add(new AllowToggle(old.allowId, false));
                plan.removed.add(old.url);
            }
        }
    }

    private static JSONObject createChanges(DocumentRow document) {
        JSONObject changes = new JSONObject();
        diff(changes, "documentKey", null, document.documentKey);
        diff(changes, "title", null, document.title);
        diff(changes, "repositoryUrl", null, document.repositoryUrl);
        diff(changes, "isActive", null, document.active);
        diff(changes, "urls", null, urlArray(document.urls));
        return changes;
    }

    private static JSONObject updateChanges(DocumentRow before, DocumentRow after, boolean urlsReplaced) {
        JSONObject changes = new JSONObject();
        diff(changes, "title", before.title, after.title);
        diff(changes, "repositoryUrl", before.repositoryUrl, after.repositoryUrl);
        diff(changes, "isActive", before.active, after.active);
        if (urlsReplaced && !sameUrls(before.urls, after.urls)) {
            diff(changes, "urls", urlArray(before.urls), urlArray(after.urls));
        }
        return changes;
    }

    private static boolean sameUrls(List<UrlBinding> left, List<UrlBinding> right) {
        return Objects.equals(urlArray(left).toString(), urlArray(right).toString());
    }

    private static JSONArray urlArray(List<UrlBinding> urls) {
        JSONArray array = new JSONArray();
        for (UrlBinding binding : urls) {
            JSONObject row = new JSONObject();
            row.put("url", binding.url);
            row.put("label", binding.label);
            array.put(row);
        }
        return array;
    }

    private static void diff(JSONObject changes, String field, Object oldValue, Object newValue) {
        if (Objects.equals(jsonValue(oldValue), jsonValue(newValue))) {
            return;
        }
        JSONObject diff = new JSONObject();
        diff.put("old", jsonValue(oldValue));
        diff.put("new", jsonValue(newValue));
        changes.put(field, diff);
    }

    private static Object jsonValue(Object value) {
        if (value == null) {
            return JSONObject.NULL;
        }
        return value;
    }

    private static DocumentRow copy(DocumentRow current) {
        DocumentRow copy = new DocumentRow();
        copy.id = current.id;
        copy.documentKey = current.documentKey;
        copy.topicId = current.topicId;
        copy.title = current.title;
        copy.repositoryUrl = current.repositoryUrl;
        copy.active = current.active;
        copy.createdByUserId = current.createdByUserId;
        copy.createdAt = current.createdAt;
        copy.updatedAt = current.updatedAt;
        copy.urls = new ArrayList<>();
        for (UrlBinding binding : current.urls) {
            copy.urls.add(copyBinding(binding));
        }
        return copy;
    }

    private static UrlBinding copyBinding(UrlBinding binding) {
        UrlBinding copy = new UrlBinding(binding.url, binding.label);
        copy.sortOrder = binding.sortOrder;
        copy.allowId = binding.allowId;
        copy.ownsAllowlist = binding.ownsAllowlist;
        return copy;
    }

    private static void requireSource(String source) {
        if (!"API".equals(source) && !"WEB".equals(source)) {
            throw GuideApiException.invalidRequest("The change source is invalid.");
        }
    }

    private List<UrlBinding> bindUrls(List<UrlInput> inputs, DocumentRow current, boolean documentActive,
            RegistrationPlan plan, User user) {
        boolean admin = store.globalAdmin(user);
        List<AllowRow> allowlist = store.allowlist();
        Map<String, AllowRow> allowByUrl = new LinkedHashMap<>();
        for (AllowRow row : allowlist) {
            allowByUrl.put(row.baseUrl, row);
        }
        Map<String, UrlBinding> previous = new LinkedHashMap<>();
        if (current != null) {
            for (UrlBinding binding : current.urls) {
                previous.put(binding.url, binding);
            }
        }
        boolean localhostMode = store.localhostMode();
        List<String> prefixes = store.enabledPrefixes();
        List<UrlBinding> bindings = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        Set<String> seenLabels = new LinkedHashSet<>();
        int sort = 0;
        for (UrlInput input : inputs == null ? List.<UrlInput>of() : inputs) {
            GuideUrlRules.Normalized normalized = GuideUrlRules.normalize(input.url(), localhostMode);
            String label = GuideUrlRules.label(input.label());
            if (!seenUrls.add(normalized.url())) {
                throw GuideApiException.invalidField("urls", "Each guide URL may be listed once.");
            }
            if (!seenLabels.add(label)) {
                throw GuideApiException.invalidField("urls", "Each URL label must be unique.");
            }
            Long owner = store.documentIdForUrl(normalized.url());
            if (owner != null && (current == null || current.id == null || !owner.equals(current.id))) {
                throw GuideApiException.duplicate("urls", "That URL is already registered.");
            }
            if (!GuideUrlRules.approved(normalized.url(), normalized.devApproved(), admin, prefixes)) {
                throw GuideApiException.urlNotApproved();
            }
            bindings.add(bindOne(normalized.url(), label, sort++, previous.get(normalized.url()),
                    allowByUrl.get(normalized.url()), documentActive, plan));
        }
        disableRemoved(previous, seenUrls, allowByUrl, plan);
        return bindings;
    }

    public record UrlInput(String url, String label) {
    }

    interface Store {
        TopicRow topicByCode(String code);

        TopicRow topicById(long id);

        DocumentRow documentByKey(String key);

        List<DocumentRow> documentsByTopic(long topicId);

        Long documentIdForUrl(String url);

        List<String> enabledPrefixes();

        List<AllowRow> allowlist();

        boolean canEdit(User user, TopicRow topic);

        boolean canView(User user, TopicRow topic);

        boolean globalAdmin(User user);

        boolean localhostMode();

        Person personOf(User user);

        List<Disposition> dispositions();

        String topicUrl(long topicId);

        void persist(RegistrationPlan plan);
    }
}
