package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.airahub.interophub.dao.EsTopicUserViewDao;
import org.airahub.interophub.model.EsTopicUserView;
import org.junit.jupiter.api.Test;

class EsTopicViewHistoryServiceTest {

    @Test
    void newUserTopicPairCreatesInitialVisit() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 10, 0);
        EsTopicViewHistoryService service = serviceAt(store, now);

        service.recordAuthenticatedTopicView(7L, 101L);

        EsTopicUserView saved = store.require(7L, 101L);
        assertEquals(1L, saved.getVisitCount());
        assertEquals(now, saved.getFirstViewedAt());
        assertEquals(now, saved.getLastViewedAt());
        assertEquals(now, saved.getLastCountedAt());
    }

    @Test
    void withinThirtyMinutesOnlyUpdatesLastViewed() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        LocalDateTime first = LocalDateTime.of(2026, 8, 1, 10, 0);
        serviceAt(store, first).recordAuthenticatedTopicView(9L, 202L);

        LocalDateTime second = first.plusMinutes(29);
        serviceAt(store, second).recordAuthenticatedTopicView(9L, 202L);

        EsTopicUserView saved = store.require(9L, 202L);
        assertEquals(1L, saved.getVisitCount());
        assertEquals(first, saved.getFirstViewedAt());
        assertEquals(second, saved.getLastViewedAt());
        assertEquals(first, saved.getLastCountedAt());
    }

    @Test
    void atThirtyMinutesBoundaryIncrementsVisitCount() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        LocalDateTime first = LocalDateTime.of(2026, 8, 1, 10, 0);
        serviceAt(store, first).recordAuthenticatedTopicView(10L, 303L);

        LocalDateTime second = first.plusMinutes(30);
        serviceAt(store, second).recordAuthenticatedTopicView(10L, 303L);

        EsTopicUserView saved = store.require(10L, 303L);
        assertEquals(2L, saved.getVisitCount());
        assertEquals(second, saved.getLastViewedAt());
        assertEquals(second, saved.getLastCountedAt());
    }

    @Test
    void afterThirtyMinutesIncrementsVisitCount() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        LocalDateTime first = LocalDateTime.of(2026, 8, 1, 10, 0);
        serviceAt(store, first).recordAuthenticatedTopicView(11L, 404L);

        LocalDateTime second = first.plusMinutes(45);
        serviceAt(store, second).recordAuthenticatedTopicView(11L, 404L);

        EsTopicUserView saved = store.require(11L, 404L);
        assertEquals(2L, saved.getVisitCount());
        assertEquals(second, saved.getLastViewedAt());
        assertEquals(second, saved.getLastCountedAt());
    }

    @Test
    void repeatedRapidLoadsDoNotIncrementMoreThanOnce() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        LocalDateTime first = LocalDateTime.of(2026, 8, 1, 10, 0);
        serviceAt(store, first).recordAuthenticatedTopicView(12L, 505L);
        serviceAt(store, first.plusMinutes(2)).recordAuthenticatedTopicView(12L, 505L);
        serviceAt(store, first.plusMinutes(10)).recordAuthenticatedTopicView(12L, 505L);

        EsTopicUserView saved = store.require(12L, 505L);
        assertEquals(1L, saved.getVisitCount());
        assertEquals(first.plusMinutes(10), saved.getLastViewedAt());
        assertEquals(first, saved.getLastCountedAt());
    }

    @Test
    void nullLastCountedAtStillIncrementsAndResetsCountedTime() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        LocalDateTime first = LocalDateTime.of(2026, 8, 1, 10, 0);
        serviceAt(store, first).recordAuthenticatedTopicView(13L, 606L);

        EsTopicUserView current = store.require(13L, 606L);
        current.setVisitCount(4L);
        current.setLastCountedAt(null);
        store.saveOrUpdate(current);

        LocalDateTime second = first.plusMinutes(5);
        serviceAt(store, second).recordAuthenticatedTopicView(13L, 606L);

        EsTopicUserView saved = store.require(13L, 606L);
        assertEquals(5L, saved.getVisitCount());
        assertEquals(second, saved.getLastViewedAt());
        assertEquals(second, saved.getLastCountedAt());
    }

    @Test
    void retriesWhenUniqueConstraintRaceOccurs() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        store.simulateUniqueConstraintRaceForNextInsert = true;

        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 10, 0);
        serviceAt(store, now).recordAuthenticatedTopicView(14L, 707L);

        EsTopicUserView saved = store.require(14L, 707L);
        assertNotNull(saved);
        assertEquals(1L, saved.getVisitCount());
    }

    @Test
    void invalidUserIdIsRejected() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        EsTopicViewHistoryService service = serviceAt(store, LocalDateTime.of(2026, 8, 1, 10, 0));

        assertThrows(IllegalArgumentException.class, () -> service.recordAuthenticatedTopicView(null, 808L));
        assertThrows(IllegalArgumentException.class, () -> service.recordAuthenticatedTopicView(0L, 808L));
    }

    @Test
    void invalidTopicIdIsRejected() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        EsTopicViewHistoryService service = serviceAt(store, LocalDateTime.of(2026, 8, 1, 10, 0));

        assertThrows(IllegalArgumentException.class, () -> service.recordAuthenticatedTopicView(15L, null));
        assertThrows(IllegalArgumentException.class, () -> service.recordAuthenticatedTopicView(15L, -1L));
    }

    @Test
    void recentlyViewedUsesProvidedLimit() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        store.recentRows = List.of(
                new EsTopicUserViewDao.RecentlyViewedTopicRow(1L, "One", 10L,
                        LocalDateTime.of(2026, 8, 1, 12, 0), "🔵"),
                new EsTopicUserViewDao.RecentlyViewedTopicRow(2L, "Two", 10L,
                        LocalDateTime.of(2026, 8, 1, 11, 0), null),
                new EsTopicUserViewDao.RecentlyViewedTopicRow(3L, "Three", 10L,
                        LocalDateTime.of(2026, 8, 1, 10, 0), null));

        EsTopicViewHistoryService service = serviceAt(store, LocalDateTime.of(2026, 8, 1, 12, 0));
        List<EsTopicViewHistoryService.RecentlyViewedTopic> recent = service.findRecentAuthenticatedTopicViews(7L, 2);

        assertEquals(2, store.lastRequestedLimit);
        assertEquals(2, recent.size());
        assertEquals(1L, recent.get(0).topicId());
    }

    @Test
    void recentlyViewedRoundTripsThroughRecordingInNewestFirstOrder() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        LocalDateTime base = LocalDateTime.of(2026, 8, 1, 9, 0);

        for (long topicId = 1; topicId <= 12; topicId++) {
            serviceAt(store, base.plusMinutes(topicId)).recordAuthenticatedTopicView(20L, topicId);
        }

        List<EsTopicViewHistoryService.RecentlyViewedTopic> recent = serviceAt(store, base.plusMinutes(30))
                .findRecentAuthenticatedTopicViews(20L);

        assertEquals(10, recent.size());
        assertEquals(12L, recent.get(0).topicId());
        assertEquals(3L, recent.get(9).topicId());
    }

    @Test
    void recentlyViewedReturnsEmptyForAnonymousOrUnknownUser() {
        InMemoryTopicViewStore store = new InMemoryTopicViewStore();
        EsTopicViewHistoryService service = serviceAt(store, LocalDateTime.of(2026, 8, 1, 12, 0));

        assertTrue(service.findRecentAuthenticatedTopicViews(null).isEmpty());
        assertTrue(service.findRecentAuthenticatedTopicViews(0L).isEmpty());
    }

    private EsTopicViewHistoryService serviceAt(InMemoryTopicViewStore store, LocalDateTime now) {
        Clock fixedClock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        return new EsTopicViewHistoryService(store, fixedClock);
    }

    private static final class InMemoryTopicViewStore implements EsTopicViewHistoryService.TopicViewStore {

        private final Map<String, EsTopicUserView> data = new HashMap<>();
        private long idSequence = 1L;
        private boolean simulateUniqueConstraintRaceForNextInsert = false;
        private int lastRequestedLimit = -1;
        private List<EsTopicUserViewDao.RecentlyViewedTopicRow> recentRows = List.of();

        @Override
        public Optional<EsTopicUserView> findByUserIdAndTopicId(Long userId, Long topicId) {
            return Optional.ofNullable(data.get(key(userId, topicId))).map(InMemoryTopicViewStore::copy);
        }

        @Override
        public EsTopicUserView saveOrUpdate(EsTopicUserView topicUserView) {
            String key = key(topicUserView.getUserId(), topicUserView.getEsTopicId());
            if (topicUserView.getEsTopicUserViewId() == null) {
                if (simulateUniqueConstraintRaceForNextInsert && !data.containsKey(key)) {
                    simulateUniqueConstraintRaceForNextInsert = false;
                    EsTopicUserView concurrent = copy(topicUserView);
                    concurrent.setEsTopicUserViewId(idSequence++);
                    data.put(key, concurrent);
                    throw new RuntimeException("uq_es_topic_user_view_user_topic");
                }
                topicUserView.setEsTopicUserViewId(idSequence++);
            }
            data.put(key, copy(topicUserView));
            return copy(topicUserView);
        }

        @Override
        public List<EsTopicUserViewDao.RecentlyViewedTopicRow> findRecentActiveTopicsByUserId(Long userId, int limit) {
            lastRequestedLimit = limit;
            if (!recentRows.isEmpty()) {
                return recentRows.stream().limit(limit).toList();
            }
            List<EsTopicUserViewDao.RecentlyViewedTopicRow> rows = new ArrayList<>();
            for (EsTopicUserView value : data.values()) {
                if (userId.equals(value.getUserId())) {
                    rows.add(new EsTopicUserViewDao.RecentlyViewedTopicRow(
                            value.getEsTopicId(),
                            "Topic " + value.getEsTopicId(),
                            1L,
                            value.getLastViewedAt(),
                            null));
                }
            }
            rows.sort(Comparator.comparing(EsTopicUserViewDao.RecentlyViewedTopicRow::lastViewedAt).reversed());
            return rows.stream().limit(limit).toList();
        }

        EsTopicUserView require(Long userId, Long topicId) {
            EsTopicUserView value = data.get(key(userId, topicId));
            assertNotNull(value);
            return copy(value);
        }

        private static String key(Long userId, Long topicId) {
            return userId + ":" + topicId;
        }

        private static EsTopicUserView copy(EsTopicUserView source) {
            EsTopicUserView copy = new EsTopicUserView();
            copy.setEsTopicUserViewId(source.getEsTopicUserViewId());
            copy.setUserId(source.getUserId());
            copy.setEsTopicId(source.getEsTopicId());
            copy.setFirstViewedAt(source.getFirstViewedAt());
            copy.setLastViewedAt(source.getLastViewedAt());
            copy.setVisitCount(source.getVisitCount());
            copy.setLastCountedAt(source.getLastCountedAt());
            return copy;
        }
    }
}
