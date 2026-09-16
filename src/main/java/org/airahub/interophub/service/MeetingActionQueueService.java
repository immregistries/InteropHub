package org.airahub.interophub.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.airahub.interophub.dao.EsMeetingActionStateDao;
import org.airahub.interophub.dao.EsMeetingCommunicationDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeeting.MeetingStatus;
import org.airahub.interophub.model.EsMeetingActionState;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.EsMeetingCommunication.CommunicationStatus;
import org.airahub.interophub.model.EsMeetingCommunication.CommunicationType;
import org.airahub.interophub.model.MeetingAction;
import org.airahub.interophub.model.MeetingActionType;

/**
 * Derives the shared meeting-cadence action queue for a user: which of the
 * four fixed-cadence actions (publish proposed agenda, finalize agenda, close
 * meeting, publish notes) are currently unmet for meetings that user is
 * authorized to act on. See docs/interophub-meeting-cadence-design.md.
 *
 * <p>Completion is always derived from authoritative state (meeting status,
 * {@link EsMeetingCommunication} rows) rather than stored separately, so the
 * queue can never disagree with what actually happened. The dashboard and
 * daily digest should both call this class rather than deriving their own
 * copy of this logic. Only personal read/snooze state is persisted
 * ({@link EsMeetingActionState}).
 *
 * <p>{@code scheduledStart}/{@code scheduledEnd} are naive wall-clock values
 * in the meeting's own {@code timezoneId} (same convention as
 * {@link MeetingWindowRules} and {@link MeetingTimeFormatter}), while
 * {@code createdAt}/{@code completedAt} are UTC-anchored (set via
 * {@code LocalDateTime.now(ZoneOffset.UTC)} in {@link MeetingLifecycleService}).
 * All due-date arithmetic below is done in the meeting's own zone so a
 * non-UTC meeting's deadlines land on the calendar day a human reading them
 * would expect; overdue-ness is always decided by comparing actual instants.
 */
public class MeetingActionQueueService {

    private static final Set<CommunicationStatus> ACTIVE_COMMUNICATION_STATUSES =
            EnumSet.of(CommunicationStatus.SCHEDULED, CommunicationStatus.SENDING, CommunicationStatus.SENT);

    /** Statuses in which the agenda has reached (or passed) Final - publishing the proposed agenda is moot. */
    private static final Set<MeetingStatus> AGENDA_FINALIZED_OR_LATER = EnumSet.of(
            MeetingStatus.FINALIZED, MeetingStatus.IN_SESSION, MeetingStatus.COMPLETED, MeetingStatus.CLOSED);

    /** Statuses in which the meeting has actually started (or later) - agenda phases are moot by then. */
    private static final Set<MeetingStatus> MEETING_STARTED_OR_LATER =
            EnumSet.of(MeetingStatus.IN_SESSION, MeetingStatus.COMPLETED, MeetingStatus.CLOSED);

    /** Statuses in which "close the meeting" is already satisfied. */
    private static final Set<MeetingStatus> MEETING_ENDED =
            EnumSet.of(MeetingStatus.COMPLETED, MeetingStatus.CLOSED, MeetingStatus.CANCELLED);

    /**
     * How far into the future a meeting's scheduledStart may be for its actions
     * to appear at all - comfortably beyond the longest lead time (14 days) plus
     * margin, so a long-running recurring series with far-future draft instances
     * doesn't flood "Upcoming" with meetings nobody needs to think about yet.
     */
    private static final int CANDIDATE_HORIZON_DAYS = 30;

    private final EsMeetingDao meetingDao;
    private final EsMeetingCommunicationDao communicationDao;
    private final EsMeetingActionStateDao actionStateDao;
    private final MeetingAuthorizationService authorizationService;
    private final Clock clock;

    public MeetingActionQueueService() {
        this(Clock.systemUTC(), new MeetingAuthorizationService());
    }

    MeetingActionQueueService(Clock clock, MeetingAuthorizationService authorizationService) {
        this.meetingDao = new EsMeetingDao();
        this.communicationDao = new EsMeetingCommunicationDao();
        this.actionStateDao = new EsMeetingActionStateDao();
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    /** Pairs a derived action with its source meeting, for callers (the digest) that need to resolve recipients per-meeting. */
    public record MeetingWithAction(EsMeeting meeting, MeetingAction action) {
    }

    public List<MeetingAction> getVisibleMeetingActions(Long userId) {
        return getVisibleMeetingActions(userId, clock.instant());
    }

    /**
     * Every currently due-or-overdue action across all candidate meetings,
     * without per-user eligibility filtering - for the staff digest, which
     * resolves recipients per meeting via {@link MeetingResponsibilityResolver}'s
     * tiered fallback rather than the dashboard's flat
     * {@link MeetingAuthorizationService#canControlMeeting}.
     */
    public List<MeetingWithAction> getAllDueOrOverdueActions(Instant nowInstant) {
        List<MeetingWithAction> result = new ArrayList<>();
        LocalDateTime horizon = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC).plusDays(CANDIDATE_HORIZON_DAYS);
        for (EsMeeting meeting : meetingDao.findActionQueueCandidates(horizon)) {
            List<EsMeetingCommunication> communications = communicationDao.findByMeetingId(meeting.getEsMeetingId());
            for (MeetingActionType type : MeetingActionType.values()) {
                deriveAction(meeting, type, communications, nowInstant)
                        .filter(MeetingAction::isOverdue)
                        .ifPresent(action -> result.add(new MeetingWithAction(meeting, action)));
            }
        }
        return result;
    }

    public List<MeetingAction> getVisibleMeetingActions(Long userId, Instant nowInstant) {
        if (userId == null) {
            return List.of();
        }
        LocalDateTime horizon = LocalDateTime.ofInstant(nowInstant, ZoneOffset.UTC).plusDays(CANDIDATE_HORIZON_DAYS);
        List<MeetingAction> visible = new ArrayList<>();
        for (EsMeeting meeting : meetingDao.findActionQueueCandidates(horizon)) {
            if (!authorizationService.canControlMeeting(userId, meeting)) {
                continue;
            }
            List<EsMeetingCommunication> communications = communicationDao.findByMeetingId(meeting.getEsMeetingId());
            for (MeetingActionType type : MeetingActionType.values()) {
                deriveAction(meeting, type, communications, nowInstant).ifPresent(visible::add);
            }
        }
        return attachPersonalState(userId, visible);
    }

    private List<MeetingAction> attachPersonalState(Long userId, List<MeetingAction> actions) {
        if (actions.isEmpty()) {
            return actions;
        }
        List<Long> meetingIds = actions.stream()
                .map(MeetingAction::getEsMeetingId)
                .distinct()
                .collect(Collectors.toList());
        Map<String, EsMeetingActionState> stateByKey = actionStateDao.findByUserIdAndMeetingIds(userId, meetingIds)
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getEsMeetingId() + ":" + s.getActionType(),
                        s -> s,
                        (first, second) -> first));
        List<MeetingAction> merged = new ArrayList<>(actions.size());
        for (MeetingAction action : actions) {
            EsMeetingActionState state = stateByKey.get(action.getEsMeetingId() + ":" + action.getActionType());
            if (state == null) {
                merged.add(action);
                continue;
            }
            merged.add(new MeetingAction(
                    action.getEsMeetingId(),
                    action.getMeetingName(),
                    action.getActionType(),
                    action.getInstruction(),
                    action.getHref(),
                    action.getDueAt(),
                    action.isOverdue(),
                    state.getReadAt(),
                    state.getSnoozedUntil()));
        }
        return merged;
    }

    /**
     * Pure derivation of a single action type for a single meeting - no I/O, so
     * this is unit-testable directly with constructed objects and a fixed
     * {@code now}. Returns empty when the action doesn't apply right now,
     * whether genuinely complete or moot because the meeting has moved past the
     * point where this phase still matters (this is a reminder system, not an
     * enforcement system - see docs/interophub-meeting-cadence-design.md).
     */
    static Optional<MeetingAction> deriveAction(EsMeeting meeting, MeetingActionType type,
            List<EsMeetingCommunication> communications, Instant nowInstant) {
        ZoneId meetingZone = MeetingWindowRules.resolveMeetingZone(meeting.getTimezoneId());
        switch (type) {
            case PUBLISH_PROPOSED_AGENDA:
                return derivePublishProposedAgenda(meeting, communications, meetingZone, nowInstant);
            case FINALIZE_AGENDA:
                return deriveFinalizeAgenda(meeting, communications, meetingZone, nowInstant);
            case CLOSE_MEETING:
                return deriveCloseMeeting(meeting, meetingZone, nowInstant);
            case PUBLISH_NOTES:
                return derivePublishNotes(meeting, communications, meetingZone, nowInstant);
            default:
                return Optional.empty();
        }
    }

    private static Optional<MeetingAction> derivePublishProposedAgenda(EsMeeting meeting,
            List<EsMeetingCommunication> communications, ZoneId meetingZone, Instant nowInstant) {
        MeetingStatus status = meeting.getStatus();
        if (AGENDA_FINALIZED_OR_LATER.contains(status)) {
            // Agenda reached (or skipped straight to) Final - this phase is superseded, not incomplete.
            return Optional.empty();
        }
        if (status == MeetingStatus.PROPOSED && hasActiveCommunication(communications, CommunicationType.PROPOSED_AGENDA)) {
            return Optional.empty();
        }
        if (isAtOrPast(meeting.getScheduledStart(), meetingZone, nowInstant)) {
            // The meeting has already happened - publishing a "proposed" agenda beforehand is no
            // longer possible, regardless of status or a since-cancelled communication.
            return Optional.empty();
        }
        LocalDateTime dueAt = dueNoEarlierThanCreated(meeting, meetingZone, 14);
        return Optional.of(new MeetingAction(
                meeting.getEsMeetingId(), meeting.getMeetingName(), MeetingActionType.PUBLISH_PROPOSED_AGENDA,
                "Publish the proposed agenda and send the proposed-agenda notice.",
                agendaHref(meeting), dueAt, isAtOrPast(dueAt, meetingZone, nowInstant), null, null));
    }

    private static Optional<MeetingAction> deriveFinalizeAgenda(EsMeeting meeting,
            List<EsMeetingCommunication> communications, ZoneId meetingZone, Instant nowInstant) {
        MeetingStatus status = meeting.getStatus();
        if (MEETING_STARTED_OR_LATER.contains(status)) {
            // Meeting already started (or later) - finalizing the agenda beforehand is moot now.
            return Optional.empty();
        }
        if (status == MeetingStatus.FINALIZED && hasActiveCommunication(communications, CommunicationType.FINAL_AGENDA)) {
            return Optional.empty();
        }
        if (isAtOrPast(meeting.getScheduledStart(), meetingZone, nowInstant)) {
            // The meeting has already happened - finalizing the agenda beforehand is no longer
            // possible, regardless of status or a since-cancelled communication.
            return Optional.empty();
        }
        LocalDateTime dueAt = dueNoEarlierThanCreated(meeting, meetingZone, 3);
        return Optional.of(new MeetingAction(
                meeting.getEsMeetingId(), meeting.getMeetingName(), MeetingActionType.FINALIZE_AGENDA,
                "Finalize the agenda and send the final-agenda notice.",
                agendaHref(meeting), dueAt, isAtOrPast(dueAt, meetingZone, nowInstant), null, null));
    }

    private static Optional<MeetingAction> deriveCloseMeeting(EsMeeting meeting, ZoneId meetingZone, Instant nowInstant) {
        LocalDateTime scheduledEnd = meeting.getScheduledEnd();
        if (scheduledEnd == null || MEETING_ENDED.contains(meeting.getStatus())) {
            return Optional.empty();
        }
        if (!isAtOrPast(scheduledEnd, meetingZone, nowInstant)) {
            return Optional.empty();
        }
        return Optional.of(new MeetingAction(
                meeting.getEsMeetingId(), meeting.getMeetingName(), MeetingActionType.CLOSE_MEETING,
                "The scheduled end time has passed. Close the meeting or mark it cancelled.",
                workspaceHref(meeting), scheduledEnd, true, null, null));
    }

    private static Optional<MeetingAction> derivePublishNotes(EsMeeting meeting,
            List<EsMeetingCommunication> communications, ZoneId meetingZone, Instant nowInstant) {
        if (meeting.getStatus() != MeetingStatus.COMPLETED) {
            // Not yet completed, or already Closed - publishing notes is either premature or moot.
            return Optional.empty();
        }
        boolean notesPublished = meeting.getNotesPublishedAt() != null;
        boolean communicationSent = hasActiveCommunication(communications, CommunicationType.NOTES_AVAILABLE);
        if (notesPublished && communicationSent) {
            return Optional.empty();
        }
        LocalDateTime closeDueAtMeetingZone = toMeetingZone(meeting.getCloseDueAt(), meetingZone);
        if (closeDueAtMeetingZone != null && isAtOrPast(closeDueAtMeetingZone, meetingZone, nowInstant)) {
            // The 7-day note-editing window has closed - notes can no longer be published
            // for review, so there's nothing more to prompt for on this meeting.
            return Optional.empty();
        }
        LocalDateTime completedAtMeetingZone = toMeetingZone(meeting.getCompletedAt(), meetingZone);
        LocalDateTime dueAt = completedAtMeetingZone != null
                ? completedAtMeetingZone.plusHours(24)
                : LocalDateTime.ofInstant(nowInstant, meetingZone);
        return Optional.of(new MeetingAction(
                meeting.getEsMeetingId(), meeting.getMeetingName(), MeetingActionType.PUBLISH_NOTES,
                "Review the meeting notes, publish them, and send the notes-available notice.",
                workspaceHref(meeting), dueAt, isAtOrPast(dueAt, meetingZone, nowInstant), null, null));
    }

    /**
     * The normal lead time is {@code leadDays} before scheduledStart, but never
     * before the meeting was created - a meeting created inside its own lead
     * time is due immediately, never "overdue since before it existed".
     */
    private static LocalDateTime dueNoEarlierThanCreated(EsMeeting meeting, ZoneId meetingZone, int leadDays) {
        LocalDateTime normalDueAt = meeting.getScheduledStart().minusDays(leadDays);
        LocalDateTime createdAt = toMeetingZone(meeting.getCreatedAt(), meetingZone);
        return createdAt != null && createdAt.isAfter(normalDueAt) ? createdAt : normalDueAt;
    }

    /** Converts a UTC-anchored naive timestamp (createdAt/completedAt) into the meeting's own zone for display/arithmetic. */
    private static LocalDateTime toMeetingZone(LocalDateTime utcValue, ZoneId meetingZone) {
        if (utcValue == null) {
            return null;
        }
        return utcValue.atZone(ZoneOffset.UTC).withZoneSameInstant(meetingZone).toLocalDateTime();
    }

    /** True once the given meeting-zone wall-clock moment has arrived, compared as actual instants. */
    private static boolean isAtOrPast(LocalDateTime meetingZoneLocalDateTime, ZoneId meetingZone, Instant nowInstant) {
        Instant dueInstant = meetingZoneLocalDateTime.atZone(meetingZone).toInstant();
        return !nowInstant.isBefore(dueInstant);
    }

    private static boolean hasActiveCommunication(List<EsMeetingCommunication> communications, CommunicationType type) {
        for (EsMeetingCommunication communication : communications) {
            if (communication.getCommunicationType() == type
                    && ACTIVE_COMMUNICATION_STATUSES.contains(communication.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private static String agendaHref(EsMeeting meeting) {
        return "/es/agenda?meetingId=" + meeting.getEsMeetingId();
    }

    private static String workspaceHref(EsMeeting meeting) {
        return "/es/meeting-workspace?meetingId=" + meeting.getEsMeetingId();
    }
}
