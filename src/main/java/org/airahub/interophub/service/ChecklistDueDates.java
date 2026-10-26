package org.airahub.interophub.service;

import java.time.LocalDate;

/**
 * Due dates for an initiative checklist. Small Group anchors are recognized by
 * the parser, and they stay blank until a Small Group has dates.
 */
public final class ChecklistDueDates {

    public static final int DUE_SOON_DAYS = 7;

    public enum Urgency {
        OVERDUE,
        DUE_SOON
    }

    private ChecklistDueDates() {
    }

    public static LocalDate effective(String anchor, Integer offsetDays, LocalDate override, LocalDate start,
            LocalDate end) {
        if (override != null) {
            return override;
        }
        LocalDate base = null;
        if ("start".equals(anchor)) {
            base = start;
        } else if ("end".equals(anchor)) {
            base = end;
        }
        if (base == null) {
            return null;
        }
        return base.plusDays(offsetDays == null ? 0 : offsetDays);
    }

    /** Only an open item is overdue or due soon. A completed item keeps its date without a warning. */
    public static Urgency urgency(LocalDate due, LocalDate today, String status) {
        if (due == null || today == null || !"OPEN".equals(status)) {
            return null;
        }
        if (due.isBefore(today)) {
            return Urgency.OVERDUE;
        }
        if (!due.isAfter(today.plusDays(DUE_SOON_DAYS))) {
            return Urgency.DUE_SOON;
        }
        return null;
    }

    public static String ruleText(String anchor, Integer offsetDays) {
        if (anchor == null || anchor.isBlank()) {
            return null;
        }
        int offset = offsetDays == null ? 0 : offsetDays;
        if (offset == 0) {
            return anchor;
        }
        return anchor + (offset > 0 ? "+" : "") + offset + "d";
    }
}
