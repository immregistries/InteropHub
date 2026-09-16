package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeetingActionState;
import org.airahub.interophub.model.MeetingActionType;

public class EsMeetingActionStateDao extends GenericDao<EsMeetingActionState, Long> {

    public EsMeetingActionStateDao() {
        super(EsMeetingActionState.class);
    }

    /**
     * Batch-fetches every action-state row a user has for any of the given
     * meetings, so a dashboard/queue render does one query instead of one per
     * candidate action.
     */
    public List<EsMeetingActionState> findByUserIdAndMeetingIds(Long userId, List<Long> esMeetingIds) {
        if (userId == null || esMeetingIds == null || esMeetingIds.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingActionState s where s.userId = :userId and s.esMeetingId in (:meetingIds)",
                    EsMeetingActionState.class)
                    .setParameter("userId", userId)
                    .setParameterList("meetingIds", esMeetingIds)
                    .getResultList();
        }
    }

    public Optional<EsMeetingActionState> findOne(Long userId, Long esMeetingId, MeetingActionType actionType) {
        if (userId == null || esMeetingId == null || actionType == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingActionState s where s.userId = :userId"
                            + " and s.esMeetingId = :meetingId and s.actionType = :actionType",
                    EsMeetingActionState.class)
                    .setParameter("userId", userId)
                    .setParameter("meetingId", esMeetingId)
                    .setParameter("actionType", actionType)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    /** Marks the action read now, creating the row if it doesn't exist yet. */
    public void markRead(Long userId, Long esMeetingId, MeetingActionType actionType, LocalDateTime readAt) {
        EsMeetingActionState state = findOne(userId, esMeetingId, actionType).orElseGet(() -> newState(userId, esMeetingId, actionType));
        state.setReadAt(readAt);
        save(state);
    }

    /** Snoozes the action until the given instant, creating the row if it doesn't exist yet. */
    public void snoozeUntil(Long userId, Long esMeetingId, MeetingActionType actionType, LocalDateTime snoozedUntil) {
        EsMeetingActionState state = findOne(userId, esMeetingId, actionType).orElseGet(() -> newState(userId, esMeetingId, actionType));
        state.setSnoozedUntil(snoozedUntil);
        save(state);
    }

    private EsMeetingActionState newState(Long userId, Long esMeetingId, MeetingActionType actionType) {
        EsMeetingActionState state = new EsMeetingActionState();
        state.setUserId(userId);
        state.setEsMeetingId(esMeetingId);
        state.setActionType(actionType);
        return state;
    }
}
