package org.airahub.interophub.service;

import java.time.Clock;
import java.time.Instant;
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

    private static ZoneId resolveMeetingZone(String timezoneId) {
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
