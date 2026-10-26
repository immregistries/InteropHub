package org.airahub.interophub.service;

import java.time.LocalDate;

/**
 * Due dates from an initiative's start and end, or a Small Group's cycle
 * anchors. A date stays blank until its anchor is set. An override wins.
 */
public final class ChecklistDueDates {

    public static final int DUE_SOON_DAYS = 7;

    public enum Urgency {
        OVERDUE,
        DUE_SOON
    }

    public record Anchors(LocalDate start, LocalDate end, LocalDate cycleStart, LocalDate workingDay,
            LocalDate closeout) {

        public static Anchors initiative(LocalDate start, LocalDate end) {
            return new Anchors(start, end, null, null, null);
        }

        public static Anchors of(LocalDate start, LocalDate end, LocalDate cycleStart, LocalDate workingDay,
                LocalDate closeout) {
            return new Anchors(start, end, cycleStart, workingDay, closeout);
        }
    }

    private ChecklistDueDates() {
    }

    public static LocalDate effective(String anchor, Integer offsetDays, LocalDate override, LocalDate start,
            LocalDate end) {
        return effective(anchor, offsetDays, override, Anchors.initiative(start, end));
    }

    public static LocalDate effective(String anchor, Integer offsetDays, LocalDate override, Anchors anchors) {
        if (override != null) {
            return override;
        }
        if (anchors == null || anchor == null) {
            return null;
        }
        LocalDate base = switch (anchor) {
            case "start" -> anchors.start();
            case "end" -> anchors.end();
            case "cycle-start" -> anchors.cycleStart();
            case "working-day" -> anchors.workingDay();
            case "closeout" -> anchors.closeout();
            default -> null;
        };
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
