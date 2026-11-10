package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Calendar dates in the form the topic page already uses, for example Nov 9, 2026. */
public final class GuideDisplayDates {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a");

    private GuideDisplayDates() {
    }

    public static String day(LocalDateTime value) {
        return value == null ? "" : DAY.format(value);
    }

    /** Accepts an API timestamp. A value that is not one of those is returned unchanged. */
    public static String day(String apiTimestamp) {
        if (apiTimestamp == null || apiTimestamp.isBlank()) {
            return "";
        }
        try {
            return day(ApiTime.parse(apiTimestamp));
        } catch (RuntimeException ex) {
            return apiTimestamp;
        }
    }

    /** Date and time on the server clock, for example Nov 9, 2026, 3:50 PM. Used where several events share a day. */
    public static String dayTime(String apiTimestamp) {
        if (apiTimestamp == null || apiTimestamp.isBlank()) {
            return "";
        }
        try {
            return DAY_TIME.format(ApiTime.parse(apiTimestamp));
        } catch (RuntimeException ex) {
            return apiTimestamp;
        }
    }
}
