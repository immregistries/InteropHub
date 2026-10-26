package org.airahub.interophub.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Counts the Small Group facts a checklist hint reports. A hint never changes
 * an item's status.
 */
public final class SmallGroupSignals {

    public static final int NOTES_GRACE_DAYS = 7;

    public record MeetingView(LocalDate day, boolean cancelled, boolean notesPublished, int agendaItems,
            int agendaItemsLinked) {
    }

    private SmallGroupSignals() {
    }

    public static boolean inCycle(LocalDate day, LocalDate cycleStart, LocalDate closeout) {
        if (day == null || cycleStart == null || closeout == null) {
            return false;
        }
        return !day.isBefore(cycleStart) && !day.isAfter(closeout);
    }

    public static boolean notesOverdue(LocalDate day, LocalDate today) {
        return day != null && today != null && day.isBefore(today.minusDays(NOTES_GRACE_DAYS));
    }

    public static ChecklistHints.GroupSignals summarize(boolean topicHasActiveSeries, boolean seriesSelected,
            int unregisteredParticipants, int participantsMissingFollow, LocalDate cycleStart, LocalDate closeout,
            List<MeetingView> meetings, boolean orientationBundle, boolean starterPacketDuringCycle, LocalDate today) {
        boolean datesSet = cycleStart != null && closeout != null;
        int meetingsInCycle = 0;
        int agendaItems = 0;
        int agendaLinked = 0;
        int missingNotes = 0;
        if (datesSet && meetings != null) {
            for (MeetingView meeting : meetings) {
                if (meeting == null || meeting.cancelled() || !inCycle(meeting.day(), cycleStart, closeout)) {
                    continue;
                }
                meetingsInCycle++;
                agendaItems += Math.max(0, meeting.agendaItems());
                agendaLinked += Math.max(0, meeting.agendaItemsLinked());
                if (!meeting.notesPublished() && notesOverdue(meeting.day(), today)) {
                    missingNotes++;
                }
            }
        }
        return new ChecklistHints.GroupSignals(topicHasActiveSeries, seriesSelected, unregisteredParticipants,
                participantsMissingFollow, datesSet, meetingsInCycle, agendaItems, agendaLinked, missingNotes,
                orientationBundle, starterPacketDuringCycle);
    }
}
