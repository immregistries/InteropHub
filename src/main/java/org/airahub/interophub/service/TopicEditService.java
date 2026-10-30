package org.airahub.interophub.service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicChangeDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicNeighborhoodDao;
import org.airahub.interophub.dao.EsTopicPathDefinitionDao;
import org.airahub.interophub.dao.EsTopicSpaceDao;
import org.airahub.interophub.dao.EsTopicStageDefinitionDao;
import org.airahub.interophub.model.EsNeighborhood;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicChange;
import org.airahub.interophub.model.EsTopicPathDefinition;
import org.airahub.interophub.model.EsTopicSpace;
import org.airahub.interophub.model.EsTopicStageDefinition;
import org.airahub.interophub.model.User;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.json.JSONObject;

public class TopicEditService {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "topicCode", "topicName", "topicSummary", "description", "searchKeywords", "topicEmoji",
            "neighborhoods", "neighborhood", "stage", "path", "status", "policyStatus", "topicType",
            "confluenceUrl", "priorityIis", "priorityEhr", "priorityCdc", "topicSpaceId");

    private final EsTopicDao topicDao;
    private final EsTopicNeighborhoodDao topicNeighborhoodDao;
    private final EsTopicChangeDao changeDao;
    private final EsTopicSpaceDao topicSpaceDao;
    private final EsNeighborhoodDao neighborhoodDao;
    private final EsTopicStageDefinitionDao stageDao;
    private final EsTopicPathDefinitionDao pathDao;
    private final TopicSpaceAccessService accessService;

    public TopicEditService() {
        this(new EsTopicDao(), new EsTopicNeighborhoodDao(), new EsTopicChangeDao(), new EsTopicSpaceDao(),
                new EsNeighborhoodDao(), new EsTopicStageDefinitionDao(), new EsTopicPathDefinitionDao(),
                new TopicSpaceAccessService());
    }

    TopicEditService(EsTopicDao topicDao, EsTopicNeighborhoodDao topicNeighborhoodDao,
            EsTopicChangeDao changeDao, EsTopicSpaceDao topicSpaceDao, EsNeighborhoodDao neighborhoodDao,
            EsTopicStageDefinitionDao stageDao, EsTopicPathDefinitionDao pathDao,
            TopicSpaceAccessService accessService) {
        this.topicDao = topicDao;
        this.topicNeighborhoodDao = topicNeighborhoodDao;
        this.changeDao = changeDao;
        this.topicSpaceDao = topicSpaceDao;
        this.neighborhoodDao = neighborhoodDao;
        this.stageDao = stageDao;
        this.pathDao = pathDao;
        this.accessService = accessService;
    }

    public TopicEditResult create(User user, EsTopicSpace space, Map<String, ?> fields, String source) {
        return create(user, space, fields, source, false);
    }

    public TopicEditResult create(User user, EsTopicSpace space, Map<String, ?> fields, String source,
            boolean dryRun) {
        requireActor(user);
        requireSource(source);
        validateFields(fields);
        if (fields != null && fields.containsKey("topicSpaceId")) {
            throw new ValidationException("topicSpaceId", "Topic Space cannot be set when creating a topic.");
        }
        if (space == null || space.getEsTopicSpaceId() == null) {
            throw new ValidationException("space", "Topic Space is invalid.");
        }
        EsTopicSpace currentSpace = topicSpaceDao.findById(space.getEsTopicSpaceId())
                .orElseThrow(() -> new ValidationException("space", "Topic Space is invalid."));
        requireActiveSpace(currentSpace, "Only active Topic Spaces may receive topics.");
        if (!accessService.canCreateTopicInSpace(user, currentSpace.getEsTopicSpaceId())) {
            throw new SecurityException("You may not create topics in this Topic Space.");
        }

        EsTopic topic = new EsTopic();
        topic.setEsTopicSpaceId(currentSpace.getEsTopicSpaceId());
        topic.setCreatedByUserId(user.getUserId());
        topic.setPriorityIis(0);
        topic.setPriorityEhr(0);
        topic.setPriorityCdc(0);
        topic.setStatus(EsTopic.EsTopicStatus.ACTIVE);

        String topicCode = trimToNull(stringValue(value(fields, "topicCode"), "topicCode"));
        if (topicCode == null) {
            throw new ValidationException("topicCode", "Topic code is required.");
        }
        if (topicCode.length() > 80) {
            throw new ValidationException("topicCode", "Topic code must be at most 80 characters.");
        }
        if (topicDao.findByTopicCode(topicCode).isPresent()) {
            throw new ValidationException("topicCode", "Topic code is already in use.");
        }
        topic.setTopicCode(topicCode);

        Set<Long> neighborhoodIds = applyFields(user, topic, currentSpace, fields, true);
        Map<String, Object> changes = snapshot(topic, neighborhoodIds);
        Map<String, Object> createdChanges = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            createdChanges.put(entry.getKey(), Map.of("old", JSONObject.NULL, "new", jsonValue(entry.getValue())));
        }

        if (dryRun) {
            return new TopicEditResult(topic, createdChanges, true);
        }

        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            if (topicDao.findByTopicCode(session, topicCode).isPresent()) {
                throw new ValidationException("topicCode", "Topic code is already in use.");
            }
            EsTopic saved = topicDao.saveOrUpdate(session, topic);
            topicNeighborhoodDao.replaceTopicNeighborhoods(session, saved.getEsTopicId(), neighborhoodIds);
            changeDao.save(session, change(user, source, "CREATE", saved.getEsTopicId(), createdChanges));
            transaction.commit();
            new DandelionSyncService().enqueueTopicUpsert(saved.getEsTopicId());
            return new TopicEditResult(saved, createdChanges, false);
        } catch (RuntimeException ex) {
            rollback(transaction);
            throw ex;
        }
    }

    public TopicEditResult update(User user, EsTopic topic, LocalDateTime expectedUpdatedAt,
            Map<String, ?> fields, String source) {
        return update(user, topic, expectedUpdatedAt, fields, source, false);
    }

    public TopicEditResult update(User user, EsTopic topic, LocalDateTime expectedUpdatedAt,
            Map<String, ?> fields, String source, boolean dryRun) {
        requireActor(user);
        requireSource(source);
        validateFields(fields);
        if (fields != null && fields.containsKey("topicCode")) {
            throw new ValidationException("topicCode", "Topic code cannot be changed.");
        }
        if (topic == null || topic.getEsTopicId() == null) {
            throw new ValidationException("topic", "Topic was not found.");
        }
        if (expectedUpdatedAt == null) {
            throw new ValidationException("updatedAt", "Expected updatedAt is required.");
        }

        EsTopic current = topicDao.findById(topic.getEsTopicId())
                .orElseThrow(() -> new ValidationException("topic", "Topic was not found."));
        if (!accessService.canEditTopic(user, current)) {
            throw new SecurityException("You may not edit this topic.");
        }
        if (!expectedUpdatedAt.equals(current.getUpdatedAt())) {
            throw new TopicConflictException(current);
        }
        Map<String, Object> oldValues = snapshot(current,
                neighborhoodIds(current.getEsTopicId()));
        Set<Long> neighborhoodIds = applyFields(user, current, currentSpace(current), fields, false);
        Map<String, Object> newValues = snapshot(current, neighborhoodIds);
        Map<String, Object> changes = differences(oldValues, newValues);
        if (dryRun) {
            return new TopicEditResult(current, changes, true);
        }

        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            EsTopic persisted = topicDao.findById(session, topic.getEsTopicId())
                    .orElseThrow(() -> new ValidationException("topic", "Topic was not found."));
            if (!accessService.canEditTopic(user, persisted)) {
                throw new SecurityException("You may not edit this topic.");
            }
            if (!expectedUpdatedAt.equals(persisted.getUpdatedAt())) {
                throw new TopicConflictException(persisted);
            }
            Map<String, Object> transactionOldValues = snapshot(
                    persisted, neighborhoodIds(persisted.getEsTopicId()));
            Set<Long> persistedNeighborhoodIds = applyFields(
                    user, persisted, currentSpace(persisted), fields, false);
            Map<String, Object> transactionNewValues = snapshot(persisted, persistedNeighborhoodIds);
            Map<String, Object> transactionChanges = differences(transactionOldValues, transactionNewValues);

            EsTopic saved = topicDao.saveOrUpdate(session, persisted);
            topicNeighborhoodDao.replaceTopicNeighborhoods(session, saved.getEsTopicId(), persistedNeighborhoodIds);
            changeDao.save(session, change(user, source, "UPDATE", saved.getEsTopicId(), transactionChanges));
            transaction.commit();
            new DandelionSyncService().enqueueTopicUpsert(saved.getEsTopicId());
            return new TopicEditResult(saved, transactionChanges, false);
        } catch (RuntimeException ex) {
            rollback(transaction);
            throw ex;
        }
    }

    private Set<Long> applyFields(User user, EsTopic topic, EsTopicSpace space,
            Map<String, ?> fields, boolean creating) {
        if (fields == null) {
            fields = Map.of();
        }
        if (fields.containsKey("topicSpaceId")) {
            Long requestedSpaceId = longValue(fields.get("topicSpaceId"), "topicSpaceId");
            EsTopicSpace requestedSpace = topicSpaceDao.findById(requestedSpaceId)
                    .orElseThrow(() -> new ValidationException("topicSpaceId", "Topic Space is invalid."));
            requireActiveSpace(requestedSpace, "Only active Topic Spaces may receive moved topics.");
            boolean movingTopic = !requestedSpaceId.equals(topic.getEsTopicSpaceId());
            if (movingTopic && !accessService.canCreateTopicInSpace(user, requestedSpaceId)) {
                throw new SecurityException("You may only move a topic into a Topic Space you administer.");
            }
            topic.setEsTopicSpaceId(requestedSpaceId);
            space = requestedSpace;
        }
        if (fields.containsKey("topicName") || creating) {
            String name = requiredString(value(fields, "topicName"), "topicName", "Topic name");
            if (name.length() > 140) {
                throw new ValidationException("topicName", "Topic name must be at most 140 characters.");
            }
            topic.setTopicName(name);
        }
        setNullableString(fields, topic, "topicSummary", 300);
        setNullableString(fields, topic, "description", Integer.MAX_VALUE);
        setNullableString(fields, topic, "searchKeywords", Integer.MAX_VALUE);
        setNullableString(fields, topic, "topicEmoji", 64);
        setNullableString(fields, topic, "policyStatus", 120);
        setNullableString(fields, topic, "topicType", 120);
        if (fields.containsKey("confluenceUrl")) {
            topic.setConfluenceUrl(validateUrl(stringValue(fields.get("confluenceUrl"), "confluenceUrl")));
        }
        if (fields.containsKey("status")) {
            if (creating && fields.get("status") == null) {
                topic.setStatus(EsTopic.EsTopicStatus.ACTIVE);
            } else {
                topic.setStatus(parseStatus(fields.get("status")));
            }
        } else if (creating) {
            topic.setStatus(EsTopic.EsTopicStatus.ACTIVE);
        }
        applyPriority(fields, topic, "priorityIis", creating);
        applyPriority(fields, topic, "priorityEhr", creating);
        applyPriority(fields, topic, "priorityCdc", creating);

        if (fields.containsKey("stage") || creating) {
            String name = stringValue(fields.get("stage"), "stage");
            topic.setEsTopicStageDefinitionId(resolveStage(name, space));
        }
        if (fields.containsKey("path") || creating) {
            String name = stringValue(fields.get("path"), "path");
            topic.setEsTopicPathDefinitionId(resolvePath(name, space));
        }

        boolean neighborhoodsProvided = fields.containsKey("neighborhoods") || fields.containsKey("neighborhood");
        if (creating || neighborhoodsProvided) {
            Object raw = fields.containsKey("neighborhoods") ? fields.get("neighborhoods") : fields.get("neighborhood");
            List<String> names = neighborhoodNames(raw);
            Set<Long> ids = resolveNeighborhoods(names, space);
            topic.setNeighborhood(joinNeighborhoodNames(names, space));
            return ids;
        }
        Set<Long> currentIds = neighborhoodIds(topic.getEsTopicId());
        topic.setNeighborhood(joinNeighborhoodNames(neighborhoodNamesByIds(currentIds), space));
        return currentIds;
    }

    private void setNullableString(Map<String, ?> fields, EsTopic topic, String field, int maximum) {
        if (!fields.containsKey(field)) {
            return;
        }
        String value = stringValue(fields.get(field), field);
        if (value != null && value.codePointCount(0, value.length()) > maximum) {
            throw new ValidationException(field,
                    "Field '" + field + "' is " + value.codePointCount(0, value.length())
                            + " characters; the maximum is " + maximum + ".");
        }
        switch (field) {
            case "topicSummary" -> topic.setTopicSummary(value);
            case "description" -> topic.setDescription(value);
            case "searchKeywords" -> topic.setSearchKeywords(value);
            case "topicEmoji" -> topic.setTopicEmoji(value);
            case "policyStatus" -> topic.setPolicyStatus(value);
            case "topicType" -> topic.setTopicType(value);
            default -> throw new IllegalStateException("Unsupported topic field: " + field);
        }
    }

    private void applyPriority(Map<String, ?> fields, EsTopic topic, String field, boolean creating) {
        if (!fields.containsKey(field) && !creating) {
            return;
        }
        Object raw = fields.get(field);
        int value = raw == null || raw.toString().isBlank() ? 0 : integerValue(raw, field);
        switch (field) {
            case "priorityIis" -> topic.setPriorityIis(value);
            case "priorityEhr" -> topic.setPriorityEhr(value);
            case "priorityCdc" -> topic.setPriorityCdc(value);
            default -> throw new IllegalStateException("Unsupported priority: " + field);
        }
    }

    private Long resolveStage(String name, EsTopicSpace space) {
        String normalized = trimToNull(name);
        if (normalized == null) {
            return null;
        }
        return stageDao.findByNameInSpace(normalized, space.getEsTopicSpaceId())
                .map(EsTopicStageDefinition::getEsTopicStageDefinitionId)
                .orElseThrow(() -> new ValidationException("stage",
                        "No stage named '" + normalized + "' in Topic Space " + space.getSpaceCode() + "."));
    }

    private Long resolvePath(String name, EsTopicSpace space) {
        String normalized = trimToNull(name);
        if (normalized == null) {
            return null;
        }
        return pathDao.findByNameInSpace(normalized, space.getEsTopicSpaceId())
                .map(EsTopicPathDefinition::getEsTopicPathDefinitionId)
                .orElseThrow(() -> new ValidationException("path",
                        "No path named '" + normalized + "' in Topic Space " + space.getSpaceCode() + "."));
    }

    private Set<Long> resolveNeighborhoods(List<String> names, EsTopicSpace space) {
        Map<String, EsNeighborhood> byName = neighborhoodDao.findAllActiveBySpaceId(space.getEsTopicSpaceId())
                .stream()
                .collect(Collectors.toMap(n -> normalizeName(n.getNeighborhoodName()), n -> n,
                        (first, ignored) -> first, LinkedHashMap::new));
        Set<Long> ids = new LinkedHashSet<>();
        for (String name : names) {
            EsNeighborhood neighborhood = byName.get(normalizeName(name));
            if (neighborhood == null) {
                throw new ValidationException("neighborhoods",
                        "Unknown neighborhood: " + name
                                + ". Use an active neighborhood name exactly as configured in the selected Topic Space.");
            }
            ids.add(neighborhood.getEsNeighborhoodId());
        }
        return ids;
    }

    private String joinNeighborhoodNames(List<String> names, EsTopicSpace space) {
        if (names.isEmpty()) {
            return null;
        }
        Map<String, EsNeighborhood> byName = neighborhoodDao.findAllActiveBySpaceId(space.getEsTopicSpaceId())
                .stream()
                .collect(Collectors.toMap(n -> normalizeName(n.getNeighborhoodName()), n -> n,
                        (first, ignored) -> first, LinkedHashMap::new));
        return names.stream()
                .map(name -> byName.get(normalizeName(name)))
                .filter(Objects::nonNull)
                .map(EsNeighborhood::getNeighborhoodName)
                .collect(Collectors.joining(", "));
    }

    private List<String> neighborhoodNames(Object raw) {
        if (raw == null) {
            return List.of();
        }
        Collection<?> values;
        if (raw instanceof Collection<?> collection) {
            values = collection;
        } else if (raw instanceof String[] array) {
            values = List.of(array);
        } else {
            values = List.of(raw.toString().split(","));
        }
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (Object value : values) {
            String name = trimToNull(value == null ? null : value.toString());
            if (name != null) {
                unique.putIfAbsent(normalizeName(name), name);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private Map<String, Object> snapshot(EsTopic topic, Set<Long> neighborhoodIds) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("topicCode", topic.getTopicCode());
        values.put("topicName", topic.getTopicName());
        values.put("topicSummary", topic.getTopicSummary());
        values.put("description", topic.getDescription());
        values.put("searchKeywords", topic.getSearchKeywords());
        values.put("topicEmoji", topic.getTopicEmoji());
        values.put("neighborhoods", neighborhoodNamesByIds(neighborhoodIds));
        values.put("stage", stageName(topic.getEsTopicStageDefinitionId()));
        values.put("path", pathName(topic.getEsTopicPathDefinitionId()));
        values.put("status", topic.getStatus() == null ? null : topic.getStatus().name());
        values.put("policyStatus", topic.getPolicyStatus());
        values.put("topicType", topic.getTopicType());
        values.put("confluenceUrl", topic.getConfluenceUrl());
        values.put("priorityIis", topic.getPriorityIis());
        values.put("priorityEhr", topic.getPriorityEhr());
        values.put("priorityCdc", topic.getPriorityCdc());
        values.put("topicSpaceId", topic.getEsTopicSpaceId());
        return values;
    }

    private List<String> neighborhoodNamesByIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return neighborhoodDao.findAllOrdered().stream()
                .filter(n -> ids.contains(n.getEsNeighborhoodId()))
                .map(EsNeighborhood::getNeighborhoodName)
                .toList();
    }

    private String stageName(Long id) {
        return id == null ? null : stageDao.findById(id).map(EsTopicStageDefinition::getStageName).orElse(null);
    }

    private String pathName(Long id) {
        return id == null ? null : pathDao.findById(id).map(EsTopicPathDefinition::getPathName).orElse(null);
    }

    private Set<Long> neighborhoodIds(Long topicId) {
        return topicId == null ? Set.of() : topicNeighborhoodDao.findNeighborhoodIdsByTopicId(topicId);
    }

    private Map<String, Object> differences(Map<String, Object> oldValues, Map<String, Object> newValues) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String field : oldValues.keySet()) {
            if (!Objects.equals(oldValues.get(field), newValues.get(field))) {
                Map<String, Object> diff = new LinkedHashMap<>();
                diff.put("old", jsonValue(oldValues.get(field)));
                diff.put("new", jsonValue(newValues.get(field)));
                changes.put(field, diff);
            }
        }
        return changes;
    }

    private Object jsonValue(Object value) {
        return value == null ? JSONObject.NULL : value;
    }

    private EsTopicChange change(User user, String source, String type, Long topicId,
            Map<String, Object> changes) {
        EsTopicChange change = new EsTopicChange();
        change.setEsTopicId(topicId);
        change.setChangedByUserId(user.getUserId());
        change.setSource(source);
        change.setChangeType(type);
        change.setChangesJson(serializeChanges(changes));
        return change;
    }

    static String serializeChanges(Map<String, Object> changes) {
        JSONObject json = new JSONObject();
        changes.forEach((field, value) -> json.put(field, new JSONObject((Map<?, ?>) value)));
        return json.toString();
    }

    private EsTopicSpace currentSpace(EsTopic topic) {
        return topicSpaceDao.findById(topic.getEsTopicSpaceId())
                .orElseThrow(() -> new ValidationException("space", "Topic Space is invalid."));
    }

    private void requireActiveSpace(EsTopicSpace space, String message) {
        if (!Boolean.TRUE.equals(space.getIsActive())) {
            throw new ValidationException("space", message);
        }
    }

    private void validateFields(Map<String, ?> fields) {
        if (fields == null) {
            return;
        }
        for (String field : fields.keySet()) {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new ValidationException(field, "Field '" + field + "' is not editable.");
            }
        }
    }

    private String validateUrl(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new ValidationException("confluenceUrl",
                        "Confluence URL must start with http:// or https://");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ValidationException("confluenceUrl", "Confluence URL must include a host.");
            }
            if (normalized.length() > 500) {
                throw new ValidationException("confluenceUrl", "Confluence URL must be at most 500 characters.");
            }
            return normalized;
        } catch (ValidationException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("confluenceUrl", "Confluence URL is invalid.");
        }
    }

    private EsTopic.EsTopicStatus parseStatus(Object raw) {
        String status = trimToNull(raw == null ? null : raw.toString());
        if (status != null) {
            try {
                return EsTopic.EsTopicStatus.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Return the same validation message for unsupported status values.
            }
        }
        throw new ValidationException("status", "Status must be ACTIVE, RETIRED, or ARCHIVED.");
    }

    private int integerValue(Object raw, String field) {
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException(field, field + " must be a valid number.");
        }
    }

    private Long longValue(Object raw, String field) {
        try {
            return Long.valueOf(raw.toString());
        } catch (RuntimeException ex) {
            throw new ValidationException(field, "Topic Space is invalid.");
        }
    }

    private String requiredString(Object raw, String field, String label) {
        String value = stringValue(raw, field);
        if (value == null) {
            throw new ValidationException(field, label + " is required.");
        }
        return value;
    }

    private String stringValue(Object raw, String field) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String)) {
            throw new ValidationException(field, "Field '" + field + "' must be text.");
        }
        return trimToNull((String) raw);
    }

    private Object value(Map<String, ?> fields, String key) {
        return fields == null ? null : fields.get(key);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void requireActor(User user) {
        if (user == null || user.getUserId() == null) {
            throw new ValidationException("user", "A signed-in user is required.");
        }
    }

    private void requireSource(String source) {
        if (source == null || !Set.of("WEB", "IMPORT", "API").contains(source)) {
            throw new ValidationException("source", "Source must be WEB, IMPORT, or API.");
        }
    }

    private void rollback(Transaction transaction) {
        if (transaction != null && transaction.isActive()) {
            transaction.rollback();
        }
    }

    public record TopicEditResult(EsTopic topic, Map<String, Object> changes, boolean dryRun) {
        public TopicEditResult {
            changes = Map.copyOf(changes);
        }
    }

    public static class ValidationException extends IllegalArgumentException {
        private final String field;

        public ValidationException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }

    public static class TopicConflictException extends IllegalStateException {
        private final EsTopic currentTopic;

        public TopicConflictException(EsTopic currentTopic) {
            super("Topic changed after it was loaded. Reload the topic and try again.");
            this.currentTopic = currentTopic;
        }

        public EsTopic getCurrentTopic() {
            return currentTopic;
        }
    }
}
