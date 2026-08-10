package org.airahub.interophub.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.dao.DandelionSyncConfigDao;
import org.airahub.interophub.dao.DandelionSyncQueueDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.DandelionSyncConfig;
import org.airahub.interophub.model.DandelionSyncQueueItem;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.User;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Dandelion sync is scoped per Topic Space: each space that syncs has its own
 * API endpoint/key (its own Dandelion workspace), its own config row, and its
 * own outbound queue. A user active in more than one space is upserted as a
 * contact separately into each relevant space's workspace.
 */
public class DandelionSyncService {
    private static final Logger LOGGER = Logger.getLogger(DandelionSyncService.class.getName());

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;

    private final DandelionSyncConfigDao configDao;
    private final DandelionSyncQueueDao queueDao;
    private final EsTopicNeighborhoodDao topicNeighborhoodDao;
    private final EsTopicDao topicDao;
    private final UserDao userDao;
    private final EsSubscriptionDao subscriptionDao;
    private final HttpClient httpClient;

    public DandelionSyncService() {
        this.configDao = new DandelionSyncConfigDao();
        this.queueDao = new DandelionSyncQueueDao();
        this.topicNeighborhoodDao = new EsTopicNeighborhoodDao();
        this.topicDao = new EsTopicDao();
        this.userDao = new UserDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Optional<DandelionSyncConfig> findActiveConfigForSpace(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return Optional.empty();
        }
        return configDao.findActiveForSpace(esTopicSpaceId).or(() -> configDao.findFirstForSpace(esTopicSpaceId));
    }

    public DandelionSyncConfig saveConfig(DandelionSyncConfig config) {
        return configDao.saveOrUpdate(config);
    }

    public void enqueueTopicUpsert(Long topicId) {
        if (topicId == null) {
            return;
        }
        Optional<EsTopic> topicOpt = topicDao.findById(topicId);
        if (topicOpt.isEmpty() || topicOpt.get().getEsTopicSpaceId() == null) {
            return;
        }
        queueDao.replacePending(
                topicOpt.get().getEsTopicSpaceId(),
                DandelionSyncQueueItem.EntityType.TOPIC,
                topicId,
                null,
                DandelionSyncQueueItem.OperationType.UPSERT);
    }

    /**
     * Fans out to every Topic Space where this user currently has an active
     * topic subscription/follow/champion/support role.
     */
    public void enqueueContactUpsert(Long userId) {
        if (userId == null) {
            return;
        }
        for (Long spaceId : subscriptionDao.findActiveSubscriptionTopicSpaceIdsByUserId(userId)) {
            enqueueContactUpsertForSpace(userId, spaceId);
        }
    }

    private void enqueueContactUpsertForSpace(Long userId, Long esTopicSpaceId) {
        if (userId == null || esTopicSpaceId == null) {
            return;
        }
        Optional<User> userOpt = userDao.findById(userId);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        if (isBlank(user.getFirstName()) || isBlank(user.getLastName())) {
            return;
        }
        queueDao.replacePending(
                esTopicSpaceId,
                DandelionSyncQueueItem.EntityType.CONTACT,
                userId,
                null,
                DandelionSyncQueueItem.OperationType.UPSERT);
    }

    public void enqueueSubscriptionState(Long esSubscriptionId) {
        if (esSubscriptionId == null) {
            return;
        }
        Optional<EsSubscription> subscriptionOpt = subscriptionDao.findById(esSubscriptionId);
        if (subscriptionOpt.isEmpty()) {
            return;
        }

        EsSubscription subscription = subscriptionOpt.get();
        if (subscription.getSubscriptionType() != EsSubscription.SubscriptionType.TOPIC
                || subscription.getEsTopicId() == null
                || subscription.getUserId() == null) {
            return;
        }

        Optional<EsTopic> topicOpt = topicDao.findById(subscription.getEsTopicId());
        if (topicOpt.isEmpty() || topicOpt.get().getEsTopicSpaceId() == null) {
            return;
        }
        Long spaceId = topicOpt.get().getEsTopicSpaceId();

        enqueueTopicUpsert(subscription.getEsTopicId());
        enqueueContactUpsertForSpace(subscription.getUserId(), spaceId);

        DandelionSyncQueueItem.OperationType operation = isActiveAssignment(subscription)
                ? DandelionSyncQueueItem.OperationType.ASSIGN_ADD
                : DandelionSyncQueueItem.OperationType.ASSIGN_REMOVE;
        enqueueAssignment(spaceId, subscription.getEsTopicId(), subscription.getUserId(), operation);
    }

    public int enqueueFullSync(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return 0;
        }
        int enqueued = 0;
        for (EsTopic topic : topicDao.findAllBySpaceIdOrderByTopicName(esTopicSpaceId)) {
            enqueueTopicUpsert(topic.getEsTopicId());
            enqueued++;
        }

        Set<Long> contactedUserIds = new LinkedHashSet<>();
        for (EsSubscription subscription : subscriptionDao.findAllTopicSubscriptionsBySpaceId(esTopicSpaceId)) {
            if (subscription.getEsTopicId() == null || subscription.getUserId() == null) {
                continue;
            }
            if (contactedUserIds.add(subscription.getUserId())) {
                enqueueContactUpsertForSpace(subscription.getUserId(), esTopicSpaceId);
                enqueued++;
            }
            DandelionSyncQueueItem.OperationType operation = isActiveAssignment(subscription)
                    ? DandelionSyncQueueItem.OperationType.ASSIGN_ADD
                    : DandelionSyncQueueItem.OperationType.ASSIGN_REMOVE;
            enqueueAssignment(esTopicSpaceId, subscription.getEsTopicId(), subscription.getUserId(), operation);
            enqueued++;
        }
        return enqueued;
    }

    public RequeueResult requeueFailuresInDependencyOrder(Long esTopicSpaceId) {
        int topics = queueDao.requeueFailedByEntityType(esTopicSpaceId, DandelionSyncQueueItem.EntityType.TOPIC);
        int contacts = queueDao.requeueFailedByEntityType(esTopicSpaceId, DandelionSyncQueueItem.EntityType.CONTACT);
        int assignments = queueDao.requeueFailedByEntityType(esTopicSpaceId, DandelionSyncQueueItem.EntityType.ASSIGNMENT);
        return new RequeueResult(topics, contacts, assignments);
    }

    public int requeueAllProjects(Long esTopicSpaceId) {
        return queueDao.requeueAllByEntityType(esTopicSpaceId, DandelionSyncQueueItem.EntityType.TOPIC);
    }

    public ProcessResult processPendingQueue(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return new ProcessResult(false, 0, 0, 0, "No Topic Space specified.");
        }

        Optional<DandelionSyncConfig> configOpt = findActiveConfigForSpace(esTopicSpaceId);
        if (configOpt.isEmpty() || !Boolean.TRUE.equals(configOpt.get().getSyncEnabled())) {
            return new ProcessResult(false, 0, 0, 0, "Sync disabled.");
        }

        DandelionSyncConfig config = configOpt.get();
        if (isBlank(config.getApiEndpoint()) || isBlank(config.getApiKey())) {
            return new ProcessResult(true, 0, 0, 0, "Sync config is incomplete.");
        }

        Outcome outcome = new Outcome();

        List<DandelionSyncQueueItem> topicItems = queueDao.findPendingByEntityType(
                esTopicSpaceId,
                DandelionSyncQueueItem.EntityType.TOPIC,
                MAX_BATCH_SIZE);
        if (!topicItems.isEmpty()) {
            outcome.totalFetched += topicItems.size();
            processEntityGroup(config, topicItems, outcome);
        }

        List<DandelionSyncQueueItem> contactItems = queueDao.findPendingByEntityType(
                esTopicSpaceId,
                DandelionSyncQueueItem.EntityType.CONTACT,
                MAX_BATCH_SIZE);
        if (!contactItems.isEmpty()) {
            outcome.totalFetched += contactItems.size();
            processEntityGroup(config, contactItems, outcome);
        }

        boolean hasPendingDependencies = queueDao.hasPendingByEntityType(esTopicSpaceId, DandelionSyncQueueItem.EntityType.TOPIC)
                || queueDao.hasPendingByEntityType(esTopicSpaceId, DandelionSyncQueueItem.EntityType.CONTACT);

        List<DandelionSyncQueueItem> assignmentItems = List.of();
        if (!hasPendingDependencies) {
            assignmentItems = queueDao.findPendingByEntityType(
                    esTopicSpaceId,
                    DandelionSyncQueueItem.EntityType.ASSIGNMENT,
                    MAX_BATCH_SIZE);
            if (!assignmentItems.isEmpty()) {
                outcome.totalFetched += assignmentItems.size();
                processEntityGroup(config, assignmentItems, outcome);
            }
        }

        if (outcome.totalFetched == 0) {
            return new ProcessResult(true, 0, 0, 0, "No pending items.");
        }

        String message = null;
        if (hasPendingDependencies) {
            message = "Processed projects/contacts first; assignment processing deferred until dependency queue is empty.";
        }
        return new ProcessResult(true, outcome.totalFetched, outcome.sentCount, outcome.failedCount, message);
    }

    /**
     * Processes every Topic Space that currently has sync enabled. Used by the
     * background scheduler, which runs independent of any one admin page.
     */
    public Map<Long, ProcessResult> processAllEnabledSpaces() {
        Map<Long, ProcessResult> results = new LinkedHashMap<>();
        for (DandelionSyncConfig config : configDao.findAllEnabled()) {
            results.put(config.getEsTopicSpaceId(), processPendingQueue(config.getEsTopicSpaceId()));
        }
        return results;
    }

    private void processEntityGroup(DandelionSyncConfig config, List<DandelionSyncQueueItem> items, Outcome outcome) {
        if (items.isEmpty()) {
            return;
        }

        try {
            switch (items.get(0).getEntityType()) {
                case TOPIC:
                    sendTopicUpserts(config, items, outcome);
                    break;
                case CONTACT:
                    sendContactUpserts(config, items, outcome);
                    break;
                case ASSIGNMENT:
                    sendAssignments(config, items, outcome);
                    break;
                default:
                    for (DandelionSyncQueueItem item : items) {
                        failItem(item, outcome, "Unsupported entity type.");
                    }
                    break;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to process Dandelion sync batch.", ex);
            for (DandelionSyncQueueItem item : items) {
                failItem(item, outcome, ex.getMessage());
            }
        }
    }

    private void sendTopicUpserts(DandelionSyncConfig config, List<DandelionSyncQueueItem> items, Outcome outcome)
            throws Exception {
        JSONArray payloadItems = new JSONArray();
        List<BatchItem> batchItems = new ArrayList<>();
        for (DandelionSyncQueueItem item : items) {
            Optional<EsTopic> topicOpt = topicDao.findById(item.getEntityId());
            if (topicOpt.isEmpty()) {
                failItem(item, outcome, "Topic not found.");
                continue;
            }
            EsTopic topic = topicOpt.get();
            String projectName = trimToNull(topic.getTopicName());
            String projectStatus = mapProjectStatus(topic.getStatus());
            if (projectName == null) {
                failItem(item, outcome, "Topic name is required for project sync.");
                continue;
            }

            JSONObject json = new JSONObject();
            json.put("externalProjectId", externalProjectId(topic.getEsTopicId()));
            json.put("projectName", projectName);
            json.put("description", nullableJson(topic.getDescription()));
            json.put("projectStatus", projectStatus);
            json.put("projectTags", toJsonArray(resolveProjectTags(topic.getEsTopicId())));
            payloadItems.put(json);
            batchItems.add(new BatchItem(item, externalProjectId(topic.getEsTopicId())));
        }

        if (!batchItems.isEmpty()) {
            applyBatchResponse(
                    postJson(config, "/projects/upsert", new JSONObject().put("items", payloadItems)),
                    batchItems,
                    outcome);
        }
    }

    private void sendContactUpserts(DandelionSyncConfig config, List<DandelionSyncQueueItem> items, Outcome outcome)
            throws Exception {
        JSONArray payloadItems = new JSONArray();
        List<BatchItem> batchItems = new ArrayList<>();
        for (DandelionSyncQueueItem item : items) {
            Optional<User> userOpt = userDao.findById(item.getEntityId());
            if (userOpt.isEmpty()) {
                failItem(item, outcome, "User not found.");
                continue;
            }
            User user = userOpt.get();
            String firstName = trimToNull(user.getFirstName());
            String lastName = trimToNull(user.getLastName());
            if (firstName == null || lastName == null) {
                failItem(item, outcome, "User first and last name are required for contact sync.");
                continue;
            }

            JSONObject json = new JSONObject();
            json.put("externalContactId", externalContactId(user.getUserId()));
            json.put("nameLast", lastName);
            json.put("nameFirst", firstName);
            json.put("nameTitle", nullableJson(trimToNull(user.getRoleTitle())));
            json.put("organizationName", nullableJson(trimToNull(user.getOrganization())));
            json.put("emailAddress", nullableJson(trimToNull(user.getEmail())));
            json.put("timeZone", nullableJson(trimToNull(user.getTimezoneId())));
            json.put("contactStatus", mapContactStatus(user.getStatus()));
            payloadItems.put(json);
            batchItems.add(new BatchItem(item, externalContactId(user.getUserId())));
        }

        if (!batchItems.isEmpty()) {
            applyBatchResponse(
                    postJson(config, "/contacts/upsert", new JSONObject().put("items", payloadItems)),
                    batchItems,
                    outcome);
        }
    }

    private void sendAssignments(DandelionSyncConfig config, List<DandelionSyncQueueItem> items, Outcome outcome)
            throws Exception {
        JSONArray payloadItems = new JSONArray();
        List<BatchItem> batchItems = new ArrayList<>();
        for (DandelionSyncQueueItem item : items) {
            Optional<EsTopic> topicOpt = topicDao.findById(item.getEntityId());
            Optional<User> userOpt = userDao.findById(item.getSecondaryEntityId());
            if (topicOpt.isEmpty()) {
                failItem(item, outcome, "Topic not found for assignment.");
                continue;
            }
            if (userOpt.isEmpty()) {
                failItem(item, outcome, "User not found for assignment.");
                continue;
            }
            User user = userOpt.get();
            if (isBlank(user.getFirstName()) || isBlank(user.getLastName())) {
                failItem(item, outcome, "Assignment contact is missing required first or last name.");
                continue;
            }

            JSONObject json = new JSONObject();
            String projectId = externalProjectId(item.getEntityId());
            String contactId = externalContactId(item.getSecondaryEntityId());
            json.put("externalProjectId", projectId);
            json.put("externalContactId", contactId);
            json.put("operation", item.getOperation() == DandelionSyncQueueItem.OperationType.ASSIGN_REMOVE
                    ? "remove"
                    : "add");
            payloadItems.put(json);
            batchItems.add(new BatchItem(item, assignmentKey(projectId, contactId)));
        }

        if (!batchItems.isEmpty()) {
            applyBatchResponse(
                    postJson(config, "/assignments/apply", new JSONObject().put("items", payloadItems)),
                    batchItems,
                    outcome);
        }
    }

    private HttpResponse<String> postJson(DandelionSyncConfig config, String path, JSONObject payload)
            throws Exception {
        String base = config.getApiEndpoint().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-Api-Key", config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void applyBatchResponse(HttpResponse<String> response, List<BatchItem> batchItems, Outcome outcome) {
        boolean appliedFromBody = applyItemizedResultsFromBody(response.body(), batchItems, outcome);
        if (appliedFromBody) {
            return;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = "HTTP " + response.statusCode() + ": " + trimToNull(response.body());
            for (BatchItem item : batchItems) {
                failItem(item.queueItem(), outcome, message);
            }
            return;
        }

        if (isBlank(response.body())) {
            for (BatchItem item : batchItems) {
                sentItem(item.queueItem(), outcome);
            }
            return;
        }

        for (BatchItem item : batchItems) {
            sentItem(item.queueItem(), outcome);
        }
    }

    private boolean applyItemizedResultsFromBody(String responseBody, List<BatchItem> batchItems, Outcome outcome) {
        if (isBlank(responseBody)) {
            return false;
        }
        JSONObject json;
        try {
            json = new JSONObject(responseBody);
        } catch (Exception ex) {
            return false;
        }

        JSONArray results = json.optJSONArray("results");
        if (results == null || results.isEmpty()) {
            return false;
        }

        Map<String, BatchItem> byKey = new LinkedHashMap<>();
        for (BatchItem batchItem : batchItems) {
            byKey.put(batchItem.key(), batchItem);
        }
        List<BatchItem> unresolved = new ArrayList<>(batchItems);

        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.optJSONObject(i);
            if (result == null) {
                continue;
            }
            BatchItem batchItem = null;
            String key = trimToNull(result.optString("key", null));
            if (key != null) {
                batchItem = byKey.get(key);
            }
            if (batchItem == null && i < batchItems.size()) {
                batchItem = batchItems.get(i);
            }
            if (batchItem == null) {
                continue;
            }
            unresolved.remove(batchItem);
            String status = trimToNull(result.optString("status", null));
            String message = trimToNull(result.optString("message", null));
            if ("error".equalsIgnoreCase(status)) {
                failItem(batchItem.queueItem(), outcome, message == null ? "Remote API returned an error." : message);
            } else {
                sentItem(batchItem.queueItem(), outcome);
            }
        }

        for (BatchItem batchItem : unresolved) {
            sentItem(batchItem.queueItem(), outcome);
        }
        return true;
    }

    private void enqueueAssignment(
            Long esTopicSpaceId,
            Long topicId,
            Long userId,
            DandelionSyncQueueItem.OperationType operation) {
        if (esTopicSpaceId == null || topicId == null || userId == null || operation == null) {
            return;
        }
        queueDao.replacePending(
                esTopicSpaceId,
                DandelionSyncQueueItem.EntityType.ASSIGNMENT,
                topicId,
                userId,
                operation);
    }

    private void sentItem(DandelionSyncQueueItem item, Outcome outcome) {
        queueDao.markSent(item.getSyncQueueId(), LocalDateTime.now());
        outcome.sentCount++;
    }

    private void failItem(DandelionSyncQueueItem item, Outcome outcome, String message) {
        queueDao.markFailed(item.getSyncQueueId(), message, MAX_ATTEMPTS);
        outcome.failedCount++;
    }

    private boolean isActiveAssignment(EsSubscription subscription) {
        return subscription.getStatus() == EsSubscription.SubscriptionStatus.SUBSCRIBED
                || subscription.getStatus() == EsSubscription.SubscriptionStatus.CHAMPION
                || subscription.getStatus() == EsSubscription.SubscriptionStatus.SUPPORT;
    }

    private String mapProjectStatus(EsTopic.EsTopicStatus status) {
        if (status == null) {
            return "Active";
        }
        switch (status) {
            case ACTIVE:
                return "Active";
            case RETIRED:
                return "Paused";
            case ARCHIVED:
                return "Closed";
            default:
                return "Active";
        }
    }

    private String mapContactStatus(User.UserStatus status) {
        return status == User.UserStatus.ACTIVE ? "ACTIVE" : "INACTIVE";
    }

    private Object nullableJson(String value) {
        return value == null ? JSONObject.NULL : value;
    }

    private List<String> resolveProjectTags(Long topicId) {
        List<String> tags = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String canonical : topicNeighborhoodDao.findNeighborhoodNamesByTopicId(topicId)) {
            String canonicalKey = canonical.toLowerCase(Locale.ROOT);
            if (seen.add(canonicalKey)) {
                tags.add(canonical);
            }
        }
        return tags;
    }

    private JSONArray toJsonArray(List<String> values) {
        JSONArray jsonArray = new JSONArray();
        for (String value : values) {
            jsonArray.put(value);
        }
        return jsonArray;
    }

    private String externalProjectId(Long topicId) {
        return "PRJ-" + topicId;
    }

    private String externalContactId(Long userId) {
        return "CNT-" + userId;
    }

    private String assignmentKey(String projectId, String contactId) {
        return projectId + "|" + contactId;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record BatchItem(DandelionSyncQueueItem queueItem, String key) {
        private BatchItem {
            Objects.requireNonNull(queueItem);
            Objects.requireNonNull(key);
        }
    }

    private static final class Outcome {
        private int totalFetched;
        private int sentCount;
        private int failedCount;
    }

    public static final class ProcessResult {
        private final boolean enabled;
        private final int totalFetched;
        private final int sentCount;
        private final int failedCount;
        private final String message;

        public ProcessResult(boolean enabled, int totalFetched, int sentCount, int failedCount, String message) {
            this.enabled = enabled;
            this.totalFetched = totalFetched;
            this.sentCount = sentCount;
            this.failedCount = failedCount;
            this.message = message;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getTotalFetched() {
            return totalFetched;
        }

        public int getSentCount() {
            return sentCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class RequeueResult {
        private final int topicsRequeued;
        private final int contactsRequeued;
        private final int assignmentsRequeued;

        public RequeueResult(int topicsRequeued, int contactsRequeued, int assignmentsRequeued) {
            this.topicsRequeued = topicsRequeued;
            this.contactsRequeued = contactsRequeued;
            this.assignmentsRequeued = assignmentsRequeued;
        }

        public int getTopicsRequeued() {
            return topicsRequeued;
        }

        public int getContactsRequeued() {
            return contactsRequeued;
        }

        public int getAssignmentsRequeued() {
            return assignmentsRequeued;
        }

        public int getTotalRequeued() {
            return topicsRequeued + contactsRequeued + assignmentsRequeued;
        }
    }
}
