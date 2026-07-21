package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicPathDefinition;
import org.airahub.interophub.model.EsTopicStageDefinition;
import org.junit.jupiter.api.Test;

class TopicBoardRulesTest {

    @Test
    void stagesOrderedByBoardOrderThenName() {
        EsTopicStageDefinition beta = stage(2L, "Beta", true);
        EsTopicStageDefinition alpha = stage(1L, "Alpha", true);
        Map<Long, Integer> order = new LinkedHashMap<>();
        order.put(2L, 1);
        order.put(1L, 1);

        List<EsTopicStageDefinition> displayed = TopicBoardRules.resolveDisplayedStages(List.of(beta, alpha), order);

        assertEquals(List.of(1L, 2L),
                displayed.stream().map(EsTopicStageDefinition::getEsTopicStageDefinitionId).toList());
    }

    @Test
    void pathsOrderedByBoardOrderThenName() {
        EsTopicPathDefinition zeta = path(3L, "Zeta", true);
        EsTopicPathDefinition alpha = path(4L, "Alpha", true);
        Map<Long, Integer> order = new LinkedHashMap<>();
        order.put(3L, 5);
        order.put(4L, 2);

        List<EsTopicPathDefinition> displayed = TopicBoardRules.resolveDisplayedPaths(List.of(zeta, alpha), order);

        assertEquals(List.of(4L, 3L),
                displayed.stream().map(EsTopicPathDefinition::getEsTopicPathDefinitionId).toList());
    }

    @Test
    void unassignedStageTopicVisibleWhenEnabled() {
        EsTopic topic = topic(10L, 100L, null, 300L, EsTopic.EsTopicStatus.ACTIVE);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, 100L, Set.of(200L), Set.of(300L), true, false);

        assertTrue(visible);
    }

    @Test
    void unassignedPathTopicVisibleWhenEnabled() {
        EsTopic topic = topic(10L, 100L, 200L, null, EsTopic.EsTopicStatus.ACTIVE);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, 100L, Set.of(200L), Set.of(300L), false, true);

        assertTrue(visible);
    }

    @Test
    void unassignedStageTopicHiddenWhenDisabled() {
        EsTopic topic = topic(10L, 100L, null, 300L, EsTopic.EsTopicStatus.ACTIVE);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, 100L, Set.of(200L), Set.of(300L), false, false);

        assertFalse(visible);
    }

    @Test
    void unassignedPathTopicHiddenWhenDisabled() {
        EsTopic topic = topic(10L, 100L, 200L, null, EsTopic.EsTopicStatus.ACTIVE);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, 100L, Set.of(200L), Set.of(300L), true, false);

        assertFalse(visible);
    }

    @Test
    void inactiveTopicIsExcluded() {
        EsTopic topic = topic(10L, 100L, 200L, 300L, EsTopic.EsTopicStatus.RETIRED);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, 100L, Set.of(200L), Set.of(300L), true, true);

        assertFalse(visible);
    }

    @Test
    void topicFromDifferentSpaceIsExcluded() {
        EsTopic topic = topic(10L, 555L, 200L, 300L, EsTopic.EsTopicStatus.ACTIVE);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, 100L, Set.of(200L), Set.of(300L), true, true);

        assertFalse(visible);
    }

    @Test
    void topicInHiddenStageIsExcluded() {
        EsTopic topic = topic(10L, 100L, 999L, 300L, EsTopic.EsTopicStatus.ACTIVE);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, 100L, Set.of(200L), Set.of(300L), true, true);

        assertFalse(visible);
    }

    @Test
    void topicInHiddenPathIsExcluded() {
        EsTopic topic = topic(10L, 100L, 200L, 999L, EsTopic.EsTopicStatus.ACTIVE);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, 100L, Set.of(200L), Set.of(300L), true, true);

        assertFalse(visible);
    }

    @Test
    void filterCompatibleStageIdsKeepsOnlyCurrentSpaceIds() {
        Set<Long> selected = new LinkedHashSet<>(List.of(1L, 2L, 3L));
        Set<Long> activeInSpace = Set.of(2L, 3L, 4L);

        Set<Long> compatible = TopicBoardRules.filterCompatibleDefinitionIds(selected, activeInSpace);

        assertEquals(Set.of(2L, 3L), compatible);
    }

    @Test
    void normalizeDisplayOrderReindexesFromZero() {
        Map<Long, Integer> requested = new LinkedHashMap<>();
        requested.put(4L, 90);
        requested.put(2L, 5);

        Map<Long, Integer> normalized = TopicBoardRules.normalizeDisplayOrder(requested, Set.of(2L, 4L));

        assertEquals(0, normalized.get(2L));
        assertEquals(1, normalized.get(4L));
    }

    @Test
    void normalizeDisplayOrderDropsDisallowedIds() {
        Map<Long, Integer> requested = new LinkedHashMap<>();
        requested.put(4L, 90);
        requested.put(2L, 5);

        Map<Long, Integer> normalized = TopicBoardRules.normalizeDisplayOrder(requested, Set.of(2L));

        assertEquals(Set.of(2L), normalized.keySet());
    }

    @Test
    void resolveDisplayedStagesExcludesInactiveDefinitions() {
        EsTopicStageDefinition active = stage(1L, "Active", true);
        EsTopicStageDefinition inactive = stage(2L, "Inactive", false);
        Map<Long, Integer> order = Map.of(1L, 0, 2L, 1);

        List<EsTopicStageDefinition> displayed = TopicBoardRules.resolveDisplayedStages(List.of(active, inactive),
                order);

        assertEquals(List.of(1L), displayed.stream().map(EsTopicStageDefinition::getEsTopicStageDefinitionId).toList());
    }

    @Test
    void resolveDisplayedPathsExcludesInactiveDefinitions() {
        EsTopicPathDefinition active = path(1L, "Active", true);
        EsTopicPathDefinition inactive = path(2L, "Inactive", false);
        Map<Long, Integer> order = Map.of(1L, 0, 2L, 1);

        List<EsTopicPathDefinition> displayed = TopicBoardRules.resolveDisplayedPaths(List.of(active, inactive), order);

        assertEquals(List.of(1L), displayed.stream().map(EsTopicPathDefinition::getEsTopicPathDefinitionId).toList());
    }

    @Test
    void visibleWhenBothAssignedAndDisplayed() {
        EsTopic topic = topic(10L, 100L, 200L, 300L, EsTopic.EsTopicStatus.ACTIVE);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, 100L, Set.of(200L), Set.of(300L), false, false);

        assertTrue(visible);
    }

    @Test
    void hiddenWhenBoardSpaceMissing() {
        EsTopic topic = topic(10L, 100L, 200L, 300L, EsTopic.EsTopicStatus.ACTIVE);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, null, Set.of(200L), Set.of(300L), true, true);

        assertFalse(visible);
    }

    @Test
    void hiddenWhenTopicMissing() {
        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(null, 100L, Set.of(200L), Set.of(300L), true, true);

        assertFalse(visible);
    }

    @Test
    void topicVisibleWithNullStageAndDisplayedPathWhenUnassignedStageEnabled() {
        EsTopic topic = topic(10L, 100L, null, 300L, EsTopic.EsTopicStatus.ACTIVE);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, 100L, Set.of(), Set.of(300L), true, true);

        assertTrue(visible);
    }

    @Test
    void topicVisibleWithDisplayedStageAndNullPathWhenUnassignedPathEnabled() {
        EsTopic topic = topic(10L, 100L, 200L, null, EsTopic.EsTopicStatus.ACTIVE);

        boolean visible = TopicBoardRules.isTopicVisibleOnBoard(topic, 100L, Set.of(200L), Set.of(), true, true);

        assertTrue(visible);
    }

    private EsTopicStageDefinition stage(Long id, String name, boolean active) {
        EsTopicStageDefinition definition = new EsTopicStageDefinition();
        definition.setEsTopicStageDefinitionId(id);
        definition.setStageName(name);
        definition.setIsActive(active);
        definition.setEsTopicSpaceId(100L);
        return definition;
    }

    private EsTopicPathDefinition path(Long id, String name, boolean active) {
        EsTopicPathDefinition definition = new EsTopicPathDefinition();
        definition.setEsTopicPathDefinitionId(id);
        definition.setPathName(name);
        definition.setIsActive(active);
        definition.setEsTopicSpaceId(100L);
        return definition;
    }

    private EsTopic topic(Long id, Long spaceId, Long stageId, Long pathId, EsTopic.EsTopicStatus status) {
        EsTopic topic = new EsTopic();
        topic.setEsTopicId(id);
        topic.setEsTopicSpaceId(spaceId);
        topic.setEsTopicStageDefinitionId(stageId);
        topic.setEsTopicPathDefinitionId(pathId);
        topic.setStatus(status);
        topic.setTopicName("Topic " + id);
        return topic;
    }
}
