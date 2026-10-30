package org.airahub.interophub.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Timestamps in the guide API. Rows store {@link LocalDateTime} in the JVM's zone.
 * Responses and {@code since} values use the matching UTC instant, labeled {@code Z}.
 * {@code /api/auth/exchange} keeps its own formatting.
 */
public final class ApiTime {
    private static final DateTimeFormatter UTC_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private ApiTime() {
    }

    public static String format(LocalDateTime databaseTime) {
        if (databaseTime == null) {
            return null;
        }
        Instant instant = toInstant(databaseTime);
        return UTC_MILLIS.format(instant.atZone(ZoneOffset.UTC));
    }

    /** Database clock value at the millisecond precision responses use. */
    public static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * Accepts any ISO-8601 UTC instant {@link Instant#parse} accepts, including
     * values that omit the millisecond fraction this API writes.
     */
    public static LocalDateTime parse(String apiTimestamp) {
        return LocalDateTime.ofInstant(parseInstant(apiTimestamp), ZoneId.systemDefault());
    }

    public static Instant parseInstant(String apiTimestamp) {
        if (apiTimestamp == null) {
            throw new java.time.format.DateTimeParseException("Timestamp is required.", "", 0);
        }
        return Instant.parse(apiTimestamp.trim());
    }

    /** Millisecond precision, matching {@link #format}. */
    public static boolean sameInstant(LocalDateTime databaseTime, String apiTimestamp) {
        if (databaseTime == null || apiTimestamp == null) {
            return false;
        }
        return toInstant(databaseTime).equals(parseInstant(apiTimestamp).truncatedTo(ChronoUnit.MILLIS));
    }

    /** True when the stored time is at or after {@code since}. */
    public static boolean atOrAfter(LocalDateTime databaseTime, String since) {
        if (databaseTime == null) {
            return false;
        }
        Instant stored = toInstant(databaseTime);
        Instant cutoff = parseInstant(since).truncatedTo(ChronoUnit.MILLIS);
        return !stored.isBefore(cutoff);
    }

    private static Instant toInstant(LocalDateTime databaseTime) {
        return databaseTime.atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.MILLIS);
    }
}
