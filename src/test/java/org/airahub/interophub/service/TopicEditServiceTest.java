package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class TopicEditServiceTest {

    @Test
    void dryRunCreateResolvesNamesAndWritesNothing() {
        Fixture fixture = new Fixture();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("topicCode", " test.1 ");
        fields.put("topicName", "New topic");
        fields.put("stage", "  sTaGe a ");
        fields.put("path", " pAtH a ");
        fields.put("neighborhoods", List.of(" north "));

        TopicEditService.TopicEditResult result = fixture.service.create(
                fixture.user, fixture.space, fields, "WEB", true);

        assertTrue(result.dryRun());
        assertEquals("test.1", result.topic().getTopicCode());
        assertEquals(fixture.stage.getEsTopicStageDefinitionId(), result.topic().getEsTopicStageDefinitionId());
        assertEquals(fixture.path.getEsTopicPathDefinitionId(), result.topic().getEsTopicPathDefinitionId());
        assertEquals("North", result.topic().getNeighborhood());
        assertEquals(Set.of(fixture.neighborhood.getEsNeighborhoodId()), fixture.neighborhoodIds);
        assertEquals(0, fixture.topicWrites);
        assertEquals(0, fixture.neighborhoodWrites);
        assertEquals(0, fixture.changeWrites);
    }

    @Test
    void dryRunUpdateDoesNotPersistAndReturnsOnlyDifferences() {
        Fixture fixture = new Fixture();
        EsTopic current = new EsTopic();
        current.setEsTopicId(71L);
        current.setTopicCode("EXISTING");
        current.setTopicName("Before");
        current.setEsTopicSpaceId(fixture.space.getEsTopicSpaceId());
        current.setCreatedByUserId(fixture.user.getUserId());
        current.setStatus(EsTopic.EsTopicStatus.ACTIVE);
        current.setUpdatedAt(LocalDateTime.of(2026, 10, 30, 8, 0));
        fixture.topicDao.current = current;

        TopicEditService.TopicEditResult result = fixture.service.update(
                fixture.user, current, current.getUpdatedAt(), Map.of("topicName", "After"), "WEB", true);

        assertTrue(result.dryRun());
        assertEquals("After", result.topic().getTopicName());
        assertEquals(Set.of("topicName"), result.changes().keySet());
        assertEquals(0, fixture.topicWrites);
        assertEquals(0, fixture.neighborhoodWrites);
        assertEquals(0, fixture.changeWrites);
    }

    @Test
    void invalidResolvedNameCarriesTheFieldName() {
        Fixture fixture = new Fixture();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("topicCode", "TEST-1");
        fields.put("topicName", "New topic");
        fields.put("stage", "Not in this space");

        TopicEditService.ValidationException ex = assertThrows(TopicEditService.ValidationException.class,
                () -> fixture.service.create(fixture.user, fixture.space, fields, "WEB", true));

        assertEquals("stage", ex.getField());
        assertTrue(ex.getMessage().contains("No stage named"));
        assertEquals(0, fixture.topicWrites);
    }

    @Test
    void duplicateTopicCodeIsRejectedAcrossSpaces() {
        Fixture fixture = new Fixture();
        EsTopic otherSpaceTopic = new EsTopic();
        otherSpaceTopic.setEsTopicId(99L);
        otherSpaceTopic.setEsTopicSpaceId(999L);
        otherSpaceTopic.setTopicCode("TEST-1");
        fixture.topicDao.codeMatch = otherSpaceTopic;

        TopicEditService.ValidationException ex = assertThrows(TopicEditService.ValidationException.class,
                () -> fixture.service.create(fixture.user, fixture.space,
                        Map.of("topicCode", "TEST-1", "topicName", "New topic"), "WEB", true));

        assertEquals("topicCode", ex.getField());
        assertEquals("Topic code is already in use.", ex.getMessage());
        assertEquals(0, fixture.topicWrites);
    }

    @Test
    void topicMoveRequiresDestinationSpaceCreationPermission() {
        Fixture fixture = new Fixture();
        EsTopic current = new EsTopic();
        current.setEsTopicId(71L);
        current.setTopicCode("EXISTING");
        current.setTopicName("Topic");
        current.setEsTopicSpaceId(fixture.space.getEsTopicSpaceId());
        current.setUpdatedAt(LocalDateTime.of(2026, 10, 30, 8, 0));
        fixture.topicDao.current = current;
        fixture.allowDestinationSpace = false;

        SecurityException ex = assertThrows(SecurityException.class,
                () -> fixture.service.update(fixture.user, current, current.getUpdatedAt(),
                        Map.of("topicSpaceId", fixture.destinationSpace.getEsTopicSpaceId()), "WEB", true));

        assertTrue(ex.getMessage().contains("Topic Space you administer"));
        assertEquals(fixture.space.getEsTopicSpaceId(), current.getEsTopicSpaceId());
    }

    @Test
    void changePayloadContainsOldAndNewValues() {
        Map<String, Object> changes = Map.of(
                "topicName", Map.of("old", "Before", "new", "After"),
                "topicSummary", Map.of("old", JSONObject.NULL, "new", "Summary"));

        JSONObject json = new JSONObject(TopicEditService.serializeChanges(changes));

        assertEquals("Before", json.getJSONObject("topicName").getString("old"));
        assertEquals("After", json.getJSONObject("topicName").getString("new"));
        assertTrue(json.getJSONObject("topicSummary").isNull("old"));
        assertEquals("Summary", json.getJSONObject("topicSummary").getString("new"));
    }

    private static class Fixture {
        private final EsTopicSpace space = new EsTopicSpace();
        private final EsTopicSpace destinationSpace = new EsTopicSpace();
        private final User user = new User();
        private final EsTopicStageDefinition stage = new EsTopicStageDefinition();
        private final EsTopicPathDefinition path = new EsTopicPathDefinition();
        private final EsNeighborhood neighborhood = new EsNeighborhood();
        private final Set<Long> neighborhoodIds = Set.of(81L);
        private int topicWrites;
        private int neighborhoodWrites;
        private int changeWrites;
        private boolean allowDestinationSpace = true;
        private final FakeTopicDao topicDao = new FakeTopicDao();
        private final TopicEditService service;

        private Fixture() {
            space.setEsTopicSpaceId(15L);
            space.setSpaceCode("test-space");
            space.setIsActive(true);
            destinationSpace.setEsTopicSpaceId(16L);
            destinationSpace.setSpaceCode("destination");
            destinationSpace.setIsActive(true);
            user.setUserId(22L);

            stage.setEsTopicStageDefinitionId(61L);
            stage.setEsTopicSpaceId(space.getEsTopicSpaceId());
            stage.setStageName("Stage A");
            path.setEsTopicPathDefinitionId(71L);
            path.setEsTopicSpaceId(space.getEsTopicSpaceId());
            path.setPathName("Path A");
            neighborhood.setEsNeighborhoodId(81L);
            neighborhood.setEsTopicSpaceId(space.getEsTopicSpaceId());
            neighborhood.setNeighborhoodName("North");
            neighborhood.setIsActive(true);

            EsTopicSpaceDao spaces = new EsTopicSpaceDao() {
                @Override
                public Optional<EsTopicSpace> findById(Long id) {
                    if (id.equals(space.getEsTopicSpaceId())) {
                        return Optional.of(space);
                    }
                    return id.equals(destinationSpace.getEsTopicSpaceId())
                            ? Optional.of(destinationSpace) : Optional.empty();
                }
            };
            EsNeighborhoodDao neighborhoods = new EsNeighborhoodDao() {
                @Override
                public List<EsNeighborhood> findAllActiveBySpaceId(Long spaceId) {
                    return spaceId.equals(space.getEsTopicSpaceId()) ? List.of(neighborhood) : List.of();
                }

                @Override
                public List<EsNeighborhood> findAllOrdered() {
                    return List.of(neighborhood);
                }
            };
            EsTopicStageDefinitionDao stages = new EsTopicStageDefinitionDao() {
                @Override
                public Optional<EsTopicStageDefinition> findByNameInSpace(String name, Long spaceId) {
                    return spaceId.equals(space.getEsTopicSpaceId())
                                    && name.trim().equalsIgnoreCase(stage.getStageName())
                            ? Optional.of(stage) : Optional.empty();
                }

                @Override
                public Optional<EsTopicStageDefinition> findById(Long id) {
                    return id.equals(stage.getEsTopicStageDefinitionId()) ? Optional.of(stage) : Optional.empty();
                }
            };
            EsTopicPathDefinitionDao paths = new EsTopicPathDefinitionDao() {
                @Override
                public Optional<EsTopicPathDefinition> findByNameInSpace(String name, Long spaceId) {
                    return spaceId.equals(space.getEsTopicSpaceId())
                                    && name.trim().equalsIgnoreCase(path.getPathName())
                            ? Optional.of(path) : Optional.empty();
                }

                @Override
                public Optional<EsTopicPathDefinition> findById(Long id) {
                    return id.equals(path.getEsTopicPathDefinitionId()) ? Optional.of(path) : Optional.empty();
                }
            };
            EsTopicNeighborhoodDao topicNeighborhoods = new EsTopicNeighborhoodDao() {
                @Override
                public Set<Long> findNeighborhoodIdsByTopicId(Long topicId) {
                    return Fixture.this.topicDao.current == null ? Set.of() : neighborhoodIds;
                }

                @Override
                public void replaceTopicNeighborhoods(Session session, Long topicId, Set<Long> ids) {
                    neighborhoodWrites++;
                }
            };
            EsTopicChangeDao changes = new EsTopicChangeDao() {
                @Override
                public void save(Session session, EsTopicChange change) {
                    changeWrites++;
                }
            };
            TopicSpaceAccessService access = new TopicSpaceAccessService() {
                @Override
                public boolean canCreateTopicInSpace(User actor, Long spaceId) {
                    return spaceId.equals(space.getEsTopicSpaceId()) || allowDestinationSpace;
                }

                @Override
                public boolean canEditTopic(User actor, EsTopic topic) {
                    return true;
                }
            };
            topicDao.writeCounter = () -> topicWrites++;
            topicDao.topicNeighborhoodIds = neighborhoodIds;
            service = new TopicEditService(topicDao, topicNeighborhoods, changes, spaces, neighborhoods, stages,
                    paths, access);
        }
    }

    private static class FakeTopicDao extends EsTopicDao {
        private EsTopic codeMatch;
        private EsTopic current;
        private Set<Long> topicNeighborhoodIds = Set.of();
        private Runnable writeCounter = () -> {
        };

        @Override
        public Optional<EsTopic> findByTopicCode(String code) {
            return Optional.ofNullable(codeMatch != null && codeMatch.getTopicCode().equalsIgnoreCase(code)
                    ? codeMatch : null);
        }

        @Override
        public Optional<EsTopic> findById(Long topicId) {
            return Optional.ofNullable(current != null && current.getEsTopicId().equals(topicId) ? current : null);
        }

        @Override
        public EsTopic saveOrUpdate(Session session, EsTopic topic) {
            writeCounter.run();
            return topic;
        }

        @Override
        public Optional<EsTopic> findById(Session session, Long topicId) {
            return findById(topicId);
        }
    }
}
