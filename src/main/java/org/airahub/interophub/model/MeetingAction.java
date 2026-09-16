package org.airahub.interophub.model;

import java.time.LocalDateTime;

/**
 * A single derived, shared meeting-cadence action visible to one user for one
 * meeting. This is an in-memory (non-persistent) value object - the
 * applicability, due date, and completion of an action are always recomputed
 * from authoritative meeting/agenda/communication state; only readAt and
 * snoozedUntil come from stored personal state (see EsMeetingActionState).
 */
public class MeetingAction {

    private final Long esMeetingId;
    private final String meetingName;
    private final MeetingActionType actionType;
    private final String instruction;
    private final String href;
    private final LocalDateTime dueAt;
    private final boolean overdue;
    private final LocalDateTime readAt;
    private final LocalDateTime snoozedUntil;

    public MeetingAction(
            Long esMeetingId,
            String meetingName,
            MeetingActionType actionType,
            String instruction,
            String href,
            LocalDateTime dueAt,
            boolean overdue,
            LocalDateTime readAt,
            LocalDateTime snoozedUntil) {
        this.esMeetingId = esMeetingId;
        this.meetingName = meetingName;
        this.actionType = actionType;
        this.instruction = instruction;
        this.href = href;
        this.dueAt = dueAt;
        this.overdue = overdue;
        this.readAt = readAt;
        this.snoozedUntil = snoozedUntil;
    }

    public Long getEsMeetingId() {
        return esMeetingId;
    }

    public String getMeetingName() {
        return meetingName;
    }

    public MeetingActionType getActionType() {
        return actionType;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getHref() {
        return href;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    /** True once {@code dueAt} has arrived - "needs attention" rather than "upcoming". */
    public boolean isOverdue() {
        return overdue;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public LocalDateTime getSnoozedUntil() {
        return snoozedUntil;
    }

    public boolean isSnoozed(LocalDateTime now) {
        return snoozedUntil != null && now != null && snoozedUntil.isAfter(now);
    }

    public boolean isUnread() {
        return readAt == null;
    }
}
