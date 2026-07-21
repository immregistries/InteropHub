package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicPathDefinition;
import org.airahub.interophub.model.EsTopicStageDefinition;

public final class TopicBoardRules {

    private TopicBoardRules() {
    }

    public static List<EsTopicStageDefinition> resolveDisplayedStages(
            List<EsTopicStageDefinition> allDefinitions,
            Map<Long, Integer> configuredStageOrderById) {
        if (allDefinitions == null || allDefinitions.isEmpty() || configuredStageOrderById == null
                || configuredStageOrderById.isEmpty()) {
            return List.of();
        }
        List<EsTopicStageDefinition> displayed = new ArrayList<>();
        for (EsTopicStageDefinition definition : allDefinitions) {
            if (definition == null || definition.getEsTopicStageDefinitionId() == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(definition.getIsActive())) {
                continue;
            }
            if (configuredStageOrderById.containsKey(definition.getEsTopicStageDefinitionId())) {
                displayed.add(definition);
            }
        }
        displayed.sort(Comparator
                .comparingInt((EsTopicStageDefinition d) -> configuredStageOrderById
                        .getOrDefault(d.getEsTopicStageDefinitionId(), Integer.MAX_VALUE))
                .thenComparing(d -> lower(d.getStageName()))
                .thenComparing(EsTopicStageDefinition::getEsTopicStageDefinitionId));
        return displayed;
    }

    public static List<EsTopicPathDefinition> resolveDisplayedPaths(
            List<EsTopicPathDefinition> allDefinitions,
            Map<Long, Integer> configuredPathOrderById) {
        if (allDefinitions == null || allDefinitions.isEmpty() || configuredPathOrderById == null
                || configuredPathOrderById.isEmpty()) {
            return List.of();
        }
        List<EsTopicPathDefinition> displayed = new ArrayList<>();
        for (EsTopicPathDefinition definition : allDefinitions) {
            if (definition == null || definition.getEsTopicPathDefinitionId() == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(definition.getIsActive())) {
                continue;
            }
            if (configuredPathOrderById.containsKey(definition.getEsTopicPathDefinitionId())) {
                displayed.add(definition);
            }
        }
        displayed.sort(Comparator
                .comparingInt((EsTopicPathDefinition d) -> configuredPathOrderById
                        .getOrDefault(d.getEsTopicPathDefinitionId(), Integer.MAX_VALUE))
                .thenComparing(d -> lower(d.getPathName()))
                .thenComparing(EsTopicPathDefinition::getEsTopicPathDefinitionId));
        return displayed;
    }

    public static boolean isTopicVisibleOnBoard(
            EsTopic topic,
            Long boardSpaceId,
            Set<Long> displayedStageIds,
            Set<Long> displayedPathIds,
            boolean showUnassignedStage,
            boolean showUnassignedPath) {
        if (topic == null || topic.getEsTopicId() == null || boardSpaceId == null) {
            return false;
        }
        if (!boardSpaceId.equals(topic.getEsTopicSpaceId())) {
            return false;
        }
        if (topic.getStatus() != EsTopic.EsTopicStatus.ACTIVE) {
            return false;
        }

        boolean stageVisible;
        if (topic.getEsTopicStageDefinitionId() == null) {
            stageVisible = showUnassignedStage;
        } else {
            stageVisible = displayedStageIds.contains(topic.getEsTopicStageDefinitionId());
        }
        if (!stageVisible) {
            return false;
        }

        if (topic.getEsTopicPathDefinitionId() == null) {
            return showUnassignedPath;
        }
        return displayedPathIds.contains(topic.getEsTopicPathDefinitionId());
    }

    public static Set<Long> filterCompatibleDefinitionIds(
            Set<Long> selectedIds,
            Set<Long> activeIdsInSpace) {
        if (selectedIds == null || selectedIds.isEmpty() || activeIdsInSpace == null || activeIdsInSpace.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (Long id : selectedIds) {
            if (id != null && activeIdsInSpace.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    public static Map<Long, Integer> normalizeDisplayOrder(Map<Long, Integer> requestedOrderById,
            Set<Long> allowedIds) {
        if (requestedOrderById == null || requestedOrderById.isEmpty() || allowedIds == null || allowedIds.isEmpty()) {
            return Map.of();
        }
        List<Map.Entry<Long, Integer>> entries = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : requestedOrderById.entrySet()) {
            if (entry.getKey() == null || !allowedIds.contains(entry.getKey())) {
                continue;
            }
            int value = entry.getValue() == null ? 0 : entry.getValue();
            entries.add(Map.entry(entry.getKey(), value));
        }
        entries.sort(Comparator
                .comparingInt((Map.Entry<Long, Integer> entry) -> entry.getValue())
                .thenComparing(Map.Entry::getKey));
        Map<Long, Integer> normalized = new LinkedHashMap<>();
        int idx = 0;
        for (Map.Entry<Long, Integer> entry : entries) {
            normalized.put(entry.getKey(), idx++);
        }
        return normalized;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
