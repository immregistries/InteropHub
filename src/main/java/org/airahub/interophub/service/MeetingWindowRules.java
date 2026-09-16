package org.airahub.interophub.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.airahub.interophub.model.EsMeeting;

public final class MeetingWindowRules {

    private static final int START_WINDOW_MINUTES = 15;

    private MeetingWindowRules() {
    }

    public static boolean isMeetingStartWindowOpen(EsMeeting meeting) {
        return isMeetingStartWindowOpen(meeting, Clock.systemUTC().instant());
    }

    public static boolean isMeetingStartWindowOpen(EsMeeting meeting, Instant nowInstant) {
        if (meeting == null || meeting.getScheduledStart() == null || nowInstant == null) {
            return false;
        }
        ZoneId zone = resolveMeetingZone(meeting.getTimezoneId());
        ZonedDateTime meetingStart = meeting.getScheduledStart().atZone(zone);
        ZonedDateTime now = nowInstant.atZone(zone);
        return !now.isBefore(meetingStart.minusMinutes(START_WINDOW_MINUTES));
    }

    public static boolean isPastScheduledEnd(EsMeeting meeting) {
        return isPastScheduledEnd(meeting, Clock.systemUTC().instant());
    }

    /**
     * True once the meeting's close-meeting anchor (see {@link #closeMeetingAnchor})
     * has passed, comparing actual instants (scheduledEnd/scheduledStart are naive
     * wall-clock values in the meeting's own timezone - see MeetingActionQueueService's
     * class doc for why this can't be compared to "now" without the zone conversion
     * below).
     */
    public static boolean isPastScheduledEnd(EsMeeting meeting, Instant nowInstant) {
        LocalDateTime anchor = closeMeetingAnchor(meeting);
        if (anchor == null || nowInstant == null) {
            return false;
        }
        ZoneId zone = resolveMeetingZone(meeting.getTimezoneId());
        return !nowInstant.isBefore(anchor.atZone(zone).toInstant());
    }

    /**
     * The wall-clock moment a meeting is "supposed to be over" for the forgotten-
     * meeting recovery flow (the workspace page's Close-meeting button and the
     * CLOSE_MEETING queue action): scheduledEnd if one was ever recorded, otherwise
     * scheduledStart. In practice almost no meeting has an explicit scheduledEnd
     * today, so this fallback is what makes the recovery flow fire at all instead
     * of leaving a forgotten meeting stuck open forever.
     */
    public static LocalDateTime closeMeetingAnchor(EsMeeting meeting) {
        if (meeting == null) {
            return null;
        }
        return meeting.getScheduledEnd() != null ? meeting.getScheduledEnd() : meeting.getScheduledStart();
    }

    /** Package-visible so other meeting-timing logic (e.g. MeetingActionQueueService) shares this fallback. */
    static ZoneId resolveMeetingZone(String timezoneId) {
        if (timezoneId == null || timezoneId.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezoneId);
        } catch (Exception ex) {
            return ZoneOffset.UTC;
        }
    }
}
