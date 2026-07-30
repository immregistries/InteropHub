package org.airahub.interophub.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.dao.EsTopicUserViewDao;
import org.airahub.interophub.model.EsTopicUserView;
import org.hibernate.exception.ConstraintViolationException;

public class EsTopicViewHistoryService {

    private static final Duration VISIT_COUNT_WINDOW = Duration.ofMinutes(30);
    private static final int DEFAULT_RECENT_LIMIT = 10;

    private final TopicViewStore topicViewStore;
    private final Clock clock;

    public EsTopicViewHistoryService() {
        this(new DaoTopicViewStore(new EsTopicUserViewDao()), Clock.systemDefaultZone());
    }

    EsTopicViewHistoryService(TopicViewStore topicViewStore, Clock clock) {
        this.topicViewStore = topicViewStore;
        this.clock = clock;
    }

    public void recordAuthenticatedTopicView(Long userId, Long topicId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }
        if (topicId == null || topicId <= 0) {
            throw new IllegalArgumentException("Topic is required.");
        }

        LocalDateTime now = LocalDateTime.now(clock);

        for (int attempt = 0; attempt < 2; attempt++) {
            Optional<EsTopicUserView> existing = topicViewStore.findByUserIdAndTopicId(userId, topicId);
            EsTopicUserView topicUserView;
            if (existing.isPresent()) {
                topicUserView = existing.get();
                topicUserView.setLastViewedAt(now);
                if (shouldIncrementVisitCount(topicUserView.getLastCountedAt(), now)) {
                    long currentCount = topicUserView.getVisitCount() == null ? 0L : topicUserView.getVisitCount();
                    topicUserView.setVisitCount(currentCount + 1L);
                    topicUserView.setLastCountedAt(now);
                }
            } else {
                topicUserView = new EsTopicUserView();
                topicUserView.setUserId(userId);
                topicUserView.setEsTopicId(topicId);
                topicUserView.setFirstViewedAt(now);
                topicUserView.setLastViewedAt(now);
                topicUserView.setVisitCount(1L);
                topicUserView.setLastCountedAt(now);
            }

            try {
                topicViewStore.saveOrUpdate(topicUserView);
                return;
            } catch (RuntimeException ex) {
                if (attempt == 0 && isUniqueKeyRace(ex)) {
                    continue;
                }
                throw ex;
            }
        }
    }

    public List<RecentlyViewedTopic> findRecentAuthenticatedTopicViews(Long userId) {
        return findRecentAuthenticatedTopicViews(userId, DEFAULT_RECENT_LIMIT);
    }

    public List<RecentlyViewedTopic> findRecentAuthenticatedTopicViews(Long userId, int limit) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        int normalizedLimit = limit > 0 ? limit : DEFAULT_RECENT_LIMIT;
        return topicViewStore.findRecentActiveTopicsByUserId(userId, normalizedLimit).stream()
                .map(row -> new RecentlyViewedTopic(
                        row.esTopicId(),
                        row.topicName(),
                        row.esTopicSpaceId(),
                        row.lastViewedAt(),
                        row.topicEmoji()))
                .toList();
    }

    static boolean shouldIncrementVisitCount(LocalDateTime lastCountedAt, LocalDateTime now) {
        if (now == null) {
            return false;
        }
        if (lastCountedAt == null) {
            return true;
        }
        return !now.isBefore(lastCountedAt.plus(VISIT_COUNT_WINDOW));
    }

    private boolean isUniqueKeyRace(RuntimeException ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();
                if (constraintName != null &&
                        "uq_es_topic_user_view_user_topic".equalsIgnoreCase(constraintName)) {
                    return true;
                }
            }
            String message = cursor.getMessage();
            if (message != null && message.toLowerCase().contains("uq_es_topic_user_view_user_topic")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    public record RecentlyViewedTopic(Long topicId, String topicName, Long topicSpaceId, LocalDateTime lastViewedAt,
            String topicEmoji) {
    }

    interface TopicViewStore {
        Optional<EsTopicUserView> findByUserIdAndTopicId(Long userId, Long topicId);

        EsTopicUserView saveOrUpdate(EsTopicUserView topicUserView);

        List<EsTopicUserViewDao.RecentlyViewedTopicRow> findRecentActiveTopicsByUserId(Long userId, int limit);
    }

    static final class DaoTopicViewStore implements TopicViewStore {

        private final EsTopicUserViewDao dao;

        DaoTopicViewStore(EsTopicUserViewDao dao) {
            this.dao = dao;
        }

        @Override
        public Optional<EsTopicUserView> findByUserIdAndTopicId(Long userId, Long topicId) {
            return dao.findByUserIdAndTopicId(userId, topicId);
        }

        @Override
        public EsTopicUserView saveOrUpdate(EsTopicUserView topicUserView) {
            return dao.saveOrUpdate(topicUserView);
        }

        @Override
        public List<EsTopicUserViewDao.RecentlyViewedTopicRow> findRecentActiveTopicsByUserId(Long userId, int limit) {
            return dao.findRecentActiveTopicsByUserId(userId, limit);
        }
    }
}
