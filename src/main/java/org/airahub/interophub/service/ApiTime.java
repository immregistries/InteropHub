package org.airahub.interophub.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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
        Instant instant = databaseTime.atZone(ZoneId.systemDefault()).toInstant();
        return UTC_MILLIS.format(instant.atZone(ZoneOffset.UTC));
    }

    public static LocalDateTime parse(String apiTimestamp) {
        LocalDateTime utc = LocalDateTime.parse(apiTimestamp, UTC_MILLIS);
        return utc.atZone(ZoneOffset.UTC).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
