package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.airahub.interophub.model.User;

/**
 * Converts a meeting's stored wall-clock start time (naive {@link LocalDateTime}
 * plus the meeting's own {@code timezoneId}) into the viewer's configured time
 * zone for display, per {@code docs/InteropHub_Global_Search_Design.md}
 * ("Date and time in the user's configured time zone"). Falls back to a
 * fixed default zone when neither the meeting nor the viewer has one set.
 */
public final class MeetingTimeFormatter {

    public static final String DEFAULT_TIMEZONE = "America/New_York";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_WITH_ZONE_FMT = DateTimeFormatter.ofPattern("h:mm a zzz",
            Locale.ENGLISH);

    private MeetingTimeFormatter() {
    }

    public static ZoneId resolveViewerZone(User viewer) {
        if (viewer != null) {
            String tzId = viewer.getTimezoneId();
            if (tzId != null && !tzId.isBlank()) {
                try {
                    return ZoneId.of(tzId.trim());
                } catch (Exception ignored) {
                    // Fall through to default below.
                }
            }
        }
        return ZoneId.of(DEFAULT_TIMEZONE);
    }

    /** Converts a meeting's own wall-clock start time into the viewer's zone. */
    public static ZonedDateTime toViewerZone(LocalDateTime scheduledStart, String meetingTimezoneId,
            ZoneId viewerZone) {
        if (scheduledStart == null) {
            return null;
        }
        ZoneId meetingZone = meetingTimezoneId == null || meetingTimezoneId.isBlank()
                ? viewerZone
                : safeZone(meetingTimezoneId, viewerZone);
        return ZonedDateTime.of(scheduledStart, meetingZone).withZoneSameInstant(viewerZone);
    }

    public static String formatDate(ZonedDateTime displayTime) {
        return displayTime == null ? "" : displayTime.format(DATE_FMT);
    }

    public static String formatDateAndTime(ZonedDateTime displayTime) {
        if (displayTime == null) {
            return "";
        }
        return displayTime.format(DATE_FMT) + " · " + displayTime.format(TIME_WITH_ZONE_FMT);
    }

    private static ZoneId safeZone(String zoneId, ZoneId fallback) {
        try {
            return ZoneId.of(zoneId.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }
}
