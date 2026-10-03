package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.User;

/**
 * Formats a topic's follower list as CSV for the "Download followers" admin
 * tool on the topic-manage Followers page. Includes everything the page knows
 * about each follower — opted-out addresses are kept and flagged rather than
 * dropped, leaving the operator responsible for how the list is used.
 */
public final class TopicFollowerCsv {

    /** Byte-order mark so Excel opens the UTF-8 file with accented names intact. */
    public static final String UTF8_BOM = "﻿";

    static final List<String> HEADERS = List.of(
            "First Name", "Last Name", "Full Name", "Organization", "Job Title", "Email",
            "Status", "Role", "Opted Out of Email", "Following Since", "Added By", "Added On",
            "Add Reason", "Last Login");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private TopicFollowerCsv() {
    }

    /** The full CSV document (header + one line per follower), without the BOM. */
    public static String toCsv(List<TopicFollowerManagementService.FollowerRow> rows) {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, HEADERS);
        for (TopicFollowerManagementService.FollowerRow row : rows) {
            appendLine(sb, toFields(row));
        }
        return sb.toString();
    }

    static List<String> toFields(TopicFollowerManagementService.FollowerRow row) {
        EsSubscription s = row.subscription();
        User u = row.user();
        String firstName = u != null ? trimToNull(u.getFirstName()) : null;
        String lastName = u != null ? trimToNull(u.getLastName()) : null;
        if (firstName == null && lastName == null) {
            firstName = trimToNull(s.getContactFirstName());
            lastName = trimToNull(s.getContactLastName());
        }
        return List.of(
                orEmpty(firstName),
                orEmpty(lastName),
                row.hasDisplayName() ? row.displayName() : "",
                row.organization(),
                u != null ? orEmpty(u.getRoleTitle()) : "",
                orEmpty(s.getEmail()),
                statusLabel(row.status()),
                roleLabel(s.getStatus()),
                row.optedOutOfEmail() ? "Yes" : "No",
                formatDate(s.getCreatedAt()),
                row.addedByUser() != null ? orEmpty(row.addedByUser().getFullName()) : "",
                formatDate(s.getManagedAddedAt()),
                orEmpty(s.getManagedAddReason()),
                u != null ? formatDateTime(u.getLastLoginAt()) : "");
    }

    static String statusLabel(TopicFollowerManagementService.FollowerStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case REGISTERED -> "Registered";
            case UNVERIFIED -> "Unverified";
            case NOT_REGISTERED -> "Not registered";
        };
    }

    static String roleLabel(EsSubscription.SubscriptionStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case CHAMPION -> "Champion";
            case SUPPORT -> "Support";
            case SUBSCRIBED -> "Follower";
            default -> status.name();
        };
    }

    /**
     * Escapes one CSV field (RFC 4180): quoted when it contains a comma,
     * quote, or line break, with embedded quotes doubled. Values starting with
     * a formula trigger character are prefixed with an apostrophe so a
     * user-entered name like {@code =HYPERLINK(...)} is shown as text instead
     * of being run by Excel.
     */
    static String escapeField(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String v = value;
        char first = v.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            v = "'" + v;
        }
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** Download file name: {@code <topic-name>-followers-<yyyy-MM-dd>.csv}, filesystem-safe. */
    public static String fileName(String topicName, java.time.LocalDate date) {
        String slug = topicName == null ? "" : topicName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-+$", "");
        }
        return (slug.isEmpty() ? "topic" : slug) + "-followers-" + DATE_FMT.format(date) + ".csv";
    }

    private static void appendLine(StringBuilder sb, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escapeField(fields.get(i)));
        }
        sb.append("\r\n");
    }

    private static String formatDate(LocalDateTime value) {
        return value == null ? "" : DATE_FMT.format(value);
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FMT.format(value);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
