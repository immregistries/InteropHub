package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.EsMeetingCommunication.CommunicationStatus;
import org.airahub.interophub.model.EsMeetingCommunication.CommunicationType;
import org.airahub.interophub.model.MeetingAction;
import org.airahub.interophub.model.MeetingActionType;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for MeetingActionQueueService.deriveAction - no database
 * involved, matching MeetingWindowRulesTest's style of constructing entities
 * directly with a fixed "now".
 */
class MeetingActionQueueServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 12, 0);
    private static final Instant NOW_INSTANT = NOW.toInstant(ZoneOffset.UTC);

    @Test
    void publishProposedAgendaIsUpcomingWithNormalLeadTime() {
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.plusDays(20));

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.PUBLISH_PROPOSED_AGENDA, List.of());

        assertTrue(action.isPresent());
        assertEquals(NOW.plusDays(6), action.get().getDueAt()); // scheduledStart - 14d
        assertFalse(action.get().isOverdue());
    }

    @Test
    void publishProposedAgendaIsDueImmediatelyWhenCreatedInsideLeadTime() {
        // Created 2 days ago, meeting is 10 days out - a 12-day lead time, less than the normal 14 days.
        EsMeeting meeting = draftMeeting(NOW.minusDays(2), NOW.plusDays(10));

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.PUBLISH_PROPOSED_AGENDA, List.of());

        assertTrue(action.isPresent());
        // Due at creation time (already passed), never "overdue since before the meeting existed".
        assertEquals(meeting.getCreatedAt(), action.get().getDueAt());
        assertTrue(action.get().isOverdue());
    }

    @Test
    void publishProposedAgendaIsCompleteOncePublishedAndCommunicationActive() {
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.plusDays(20));
        meeting.setStatus(EsMeeting.MeetingStatus.PROPOSED);
        EsMeetingCommunication sent = communication(CommunicationType.PROPOSED_AGENDA, CommunicationStatus.SENT);

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.PUBLISH_PROPOSED_AGENDA, List.of(sent));

        assertTrue(action.isEmpty());
    }

    @Test
    void publishProposedAgendaIsMootOnceAgendaSkipsStraightToFinalized() {
        // DRAFT -> FINALIZED directly, skipping PROPOSED entirely: no PROPOSED_AGENDA
        // communication will ever exist, so this must not linger forever.
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.plusDays(20));
        meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.PUBLISH_PROPOSED_AGENDA, List.of());

        assertTrue(action.isEmpty());
    }

    @Test
    void publishProposedAgendaDoesNotReviveForAPastMeetingStuckInDraft() {
        // Same class of bug as finalizeAgendaDoesNotReviveForAPastMeetingWhoseCommunicationWasCancelled,
        // but for a meeting that never even reached Proposed before its scheduledStart passed.
        EsMeeting meeting = draftMeeting(NOW.minusDays(120), NOW.minusDays(90));

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.PUBLISH_PROPOSED_AGENDA, List.of());

        assertTrue(action.isEmpty());
    }

    @Test
    void dueDateIsComputedInTheMeetingsOwnTimezoneNotUtc() {
        // scheduledStart is 2026-09-18T09:00 in America/Los_Angeles (PDT, UTC-7),
        // i.e. 2026-09-18T16:00 UTC. The 3-day-lead deadline is therefore
        // 2026-09-15T09:00 LA = 2026-09-15T16:00 UTC, which is AFTER "now"
        // (2026-09-15T12:00 UTC) - not yet due.
        //
        // A naive implementation that subtracted 3 days from the raw LocalDateTime
        // numbers and compared them directly against "now"'s raw numbers (as if both
        // were the same clock) would compute the deadline as the naive
        // 2026-09-15T09:00 and wrongly call it already-overdue relative to "now"'s
        // naive 2026-09-15T12:00 - a false positive purely from skipping the
        // LA<->UTC conversion.
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), LocalDateTime.of(2026, 9, 18, 9, 0));
        meeting.setTimezoneId("America/Los_Angeles");
        meeting.setStatus(EsMeeting.MeetingStatus.PROPOSED);

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.FINALIZE_AGENDA, List.of());

        assertTrue(action.isPresent());
        assertFalse(action.get().isOverdue());
    }

    @Test
    void finalizeAgendaIsOverdueOncePastDeadline() {
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.plusDays(1));
        meeting.setStatus(EsMeeting.MeetingStatus.PROPOSED);

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.FINALIZE_AGENDA, List.of());

        assertTrue(action.isPresent());
        assertTrue(action.get().isOverdue()); // scheduledStart - 3d already passed
    }

    @Test
    void finalizeAgendaIsMootOnceMeetingHasStarted() {
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.minusHours(1));
        meeting.setStatus(EsMeeting.MeetingStatus.IN_SESSION);

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.FINALIZE_AGENDA, List.of());

        assertTrue(action.isEmpty());
    }

    @Test
    void finalizeAgendaDoesNotReviveForAPastMeetingWhoseCommunicationWasCancelled() {
        // Reproduces the "meeting long since over, but still Finalized (never
        // marked In Session/Completed/Closed) with its final-agenda notice
        // cancelled after the fact" case - the status/communication checks alone
        // would otherwise flag this as due again forever.
        EsMeeting meeting = draftMeeting(NOW.minusDays(120), NOW.minusDays(90));
        meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
        EsMeetingCommunication cancelled = communication(CommunicationType.FINAL_AGENDA, CommunicationStatus.CANCELLED);

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.FINALIZE_AGENDA, List.of(cancelled));

        assertTrue(action.isEmpty());
    }

    @Test
    void closeMeetingAppearsOnceScheduledEndHasPassed() {
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.minusDays(20));
        meeting.setStatus(EsMeeting.MeetingStatus.FINALIZED);
        meeting.setScheduledEnd(NOW.minusHours(2));

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.CLOSE_MEETING, List.of());

        assertTrue(action.isPresent());
        assertTrue(action.get().isOverdue());
    }

    @Test
    void closeMeetingDoesNotAppearOnceMeetingHasEnded() {
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.minusDays(20));
        meeting.setStatus(EsMeeting.MeetingStatus.COMPLETED);
        meeting.setScheduledEnd(NOW.minusHours(2));

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.CLOSE_MEETING, List.of());

        assertTrue(action.isEmpty());
    }

    @Test
    void publishNotesIsOverdueTwentyFourHoursAfterCompletion() {
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.minusDays(2));
        meeting.setStatus(EsMeeting.MeetingStatus.COMPLETED);
        meeting.setCompletedAt(NOW.minusHours(30));

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.PUBLISH_NOTES, List.of());

        assertTrue(action.isPresent());
        assertTrue(action.get().isOverdue());
    }

    @Test
    void publishNotesIsUpcomingWithinTheFirstTwentyFourHours() {
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.minusDays(2));
        meeting.setStatus(EsMeeting.MeetingStatus.COMPLETED);
        meeting.setCompletedAt(NOW.minusHours(10));

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.PUBLISH_NOTES, List.of());

        assertTrue(action.isPresent());
        assertFalse(action.get().isOverdue());
    }

    @Test
    void publishNotesIsCompleteOncePublishedAndCommunicationActive() {
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.minusDays(2));
        meeting.setStatus(EsMeeting.MeetingStatus.COMPLETED);
        meeting.setCompletedAt(NOW.minusHours(30));
        meeting.setNotesPublishedAt(NOW.minusHours(5));
        EsMeetingCommunication scheduled = communication(CommunicationType.NOTES_AVAILABLE, CommunicationStatus.SCHEDULED);

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.PUBLISH_NOTES, List.of(scheduled));

        assertTrue(action.isEmpty());
    }

    @Test
    void publishNotesDoesNotLingerPastTheSevenDayEditWindow() {
        // Notes were never published, and the 7-day note-editing lock (closeDueAt) has
        // already closed - there's nothing more to prompt for on this meeting.
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.minusDays(10));
        meeting.setStatus(EsMeeting.MeetingStatus.COMPLETED);
        meeting.setCompletedAt(NOW.minusDays(9));
        meeting.setCloseDueAt(NOW.minusDays(2));

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.PUBLISH_NOTES, List.of());

        assertTrue(action.isEmpty());
    }

    @Test
    void publishNotesIsStillActiveWithinTheSevenDayEditWindow() {
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.minusDays(2));
        meeting.setStatus(EsMeeting.MeetingStatus.COMPLETED);
        meeting.setCompletedAt(NOW.minusDays(1));
        meeting.setCloseDueAt(NOW.plusDays(6));

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.PUBLISH_NOTES, List.of());

        assertTrue(action.isPresent());
    }

    @Test
    void publishNotesDoesNotLingerOnceMeetingIsClosed() {
        EsMeeting meeting = draftMeeting(NOW.minusDays(30), NOW.minusDays(10));
        meeting.setStatus(EsMeeting.MeetingStatus.CLOSED);
        meeting.setCompletedAt(NOW.minusDays(9));

        Optional<MeetingAction> action = derive(meeting, MeetingActionType.PUBLISH_NOTES, List.of());

        assertTrue(action.isEmpty());
    }

    private static Optional<MeetingAction> derive(
            EsMeeting meeting, MeetingActionType type, List<EsMeetingCommunication> communications) {
        return MeetingActionQueueService.deriveAction(meeting, type, communications, NOW_INSTANT);
    }

    private static EsMeeting draftMeeting(LocalDateTime createdAt, LocalDateTime scheduledStart) {
        EsMeeting meeting = new EsMeeting();
        meeting.setEsMeetingId(1L);
        meeting.setMeetingName("Test Meeting");
        meeting.setStatus(EsMeeting.MeetingStatus.DRAFT);
        meeting.setCreatedAt(createdAt);
        meeting.setScheduledStart(scheduledStart);
        return meeting;
    }

    private static EsMeetingCommunication communication(CommunicationType type, CommunicationStatus status) {
        EsMeetingCommunication communication = new EsMeetingCommunication();
        communication.setCommunicationType(type);
        communication.setStatus(status);
        return communication;
    }
}
