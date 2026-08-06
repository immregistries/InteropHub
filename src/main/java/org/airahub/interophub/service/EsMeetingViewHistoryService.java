package org.airahub.interophub.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.dao.EsMeetingUserViewDao;
import org.airahub.interophub.model.EsMeetingUserView;
import org.hibernate.exception.ConstraintViolationException;

public class EsMeetingViewHistoryService {

    private static final Duration VISIT_COUNT_WINDOW = Duration.ofMinutes(30);
    private static final int DEFAULT_RECENT_LIMIT = 10;

    private final MeetingViewStore meetingViewStore;
    private final Clock clock;

    public EsMeetingViewHistoryService() {
        this(new DaoMeetingViewStore(new EsMeetingUserViewDao()), Clock.systemDefaultZone());
    }

    EsMeetingViewHistoryService(MeetingViewStore meetingViewStore, Clock clock) {
        this.meetingViewStore = meetingViewStore;
        this.clock = clock;
    }

    public void recordAuthenticatedMeetingView(Long userId, Long esMeetingId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }
        if (esMeetingId == null || esMeetingId <= 0) {
            throw new IllegalArgumentException("Meeting is required.");
        }

        LocalDateTime now = LocalDateTime.now(clock);

        for (int attempt = 0; attempt < 2; attempt++) {
            Optional<EsMeetingUserView> existing = meetingViewStore.findByUserIdAndMeetingId(userId, esMeetingId);
            EsMeetingUserView meetingUserView;
            if (existing.isPresent()) {
                meetingUserView = existing.get();
                meetingUserView.setLastViewedAt(now);
                if (shouldIncrementVisitCount(meetingUserView.getLastCountedAt(), now)) {
                    long currentCount = meetingUserView.getVisitCount() == null ? 0L : meetingUserView.getVisitCount();
                    meetingUserView.setVisitCount(currentCount + 1L);
                    meetingUserView.setLastCountedAt(now);
                }
            } else {
                meetingUserView = new EsMeetingUserView();
                meetingUserView.setUserId(userId);
                meetingUserView.setEsMeetingId(esMeetingId);
                meetingUserView.setFirstViewedAt(now);
                meetingUserView.setLastViewedAt(now);
                meetingUserView.setVisitCount(1L);
                meetingUserView.setLastCountedAt(now);
            }

            try {
                meetingViewStore.saveOrUpdate(meetingUserView);
                return;
            } catch (RuntimeException ex) {
                if (attempt == 0 && isUniqueKeyRace(ex)) {
                    continue;
                }
                throw ex;
            }
        }
    }

    public List<RecentlyViewedMeeting> findRecentAuthenticatedMeetingViews(Long userId) {
        return findRecentAuthenticatedMeetingViews(userId, DEFAULT_RECENT_LIMIT);
    }

    public List<RecentlyViewedMeeting> findRecentAuthenticatedMeetingViews(Long userId, int limit) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        int normalizedLimit = limit > 0 ? limit : DEFAULT_RECENT_LIMIT;
        return meetingViewStore.findRecentMeetingsByUserId(userId, normalizedLimit).stream()
                .map(row -> new RecentlyViewedMeeting(
                        row.esMeetingId(),
                        row.meetingName(),
                        row.esTopicSpaceId(),
                        row.scheduledStart(),
                        row.lastViewedAt()))
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
                        "uq_es_meeting_user_view_user_meeting".equalsIgnoreCase(constraintName)) {
                    return true;
                }
            }
            String message = cursor.getMessage();
            if (message != null && message.toLowerCase().contains("uq_es_meeting_user_view_user_meeting")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    public record RecentlyViewedMeeting(Long esMeetingId, String meetingName, Long esTopicSpaceId,
            LocalDateTime scheduledStart, LocalDateTime lastViewedAt) {
    }

    interface MeetingViewStore {
        Optional<EsMeetingUserView> findByUserIdAndMeetingId(Long userId, Long esMeetingId);

        EsMeetingUserView saveOrUpdate(EsMeetingUserView meetingUserView);

        List<EsMeetingUserViewDao.RecentlyViewedMeetingRow> findRecentMeetingsByUserId(Long userId, int limit);
    }

    static final class DaoMeetingViewStore implements MeetingViewStore {

        private final EsMeetingUserViewDao dao;

        DaoMeetingViewStore(EsMeetingUserViewDao dao) {
            this.dao = dao;
        }

        @Override
        public Optional<EsMeetingUserView> findByUserIdAndMeetingId(Long userId, Long esMeetingId) {
            return dao.findByUserIdAndMeetingId(userId, esMeetingId);
        }

        @Override
        public EsMeetingUserView saveOrUpdate(EsMeetingUserView meetingUserView) {
            return dao.saveOrUpdate(meetingUserView);
        }

        @Override
        public List<EsMeetingUserViewDao.RecentlyViewedMeetingRow> findRecentMeetingsByUserId(Long userId, int limit) {
            return dao.findRecentMeetingsByUserId(userId, limit);
        }
    }
}
