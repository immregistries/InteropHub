package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ChecklistDueDatesTest {

    @Test
    void usesInitiativeDatesAndLetsAnOverrideWin() {
        LocalDate start = LocalDate.of(2026, 10, 24);
        LocalDate end = LocalDate.of(2026, 12, 1);
        assertEquals(LocalDate.of(2026, 10, 31), ChecklistDueDates.effective("start", 7, null, start, end));
        assertEquals(LocalDate.of(2026, 11, 17), ChecklistDueDates.effective("end", -14, null, start, end));
        assertEquals(start, ChecklistDueDates.effective("start", 0, null, start, end));
        assertEquals(LocalDate.of(2026, 11, 1),
                ChecklistDueDates.effective("start", 7, LocalDate.of(2026, 11, 1), start, end));
        assertNull(ChecklistDueDates.effective("start", 7, null, null, end));
        assertNull(ChecklistDueDates.effective("cycle-start", -21, null, start, end));
    }

    @Test
    void overdueAndDueSoonApplyToOpenItems() {
        LocalDate today = LocalDate.of(2026, 10, 26);
        assertEquals(ChecklistDueDates.Urgency.OVERDUE,
                ChecklistDueDates.urgency(today.minusDays(1), today, "OPEN"));
        assertEquals(ChecklistDueDates.Urgency.DUE_SOON,
                ChecklistDueDates.urgency(today.plusDays(7), today, "OPEN"));
        assertNull(ChecklistDueDates.urgency(today.plusDays(8), today, "OPEN"));
        assertNull(ChecklistDueDates.urgency(today.minusDays(1), today, "DONE"));
        assertEquals("start+7d", ChecklistDueDates.ruleText("start", 7));
        assertEquals("end-14d", ChecklistDueDates.ruleText("end", -14));
        assertEquals("end", ChecklistDueDates.ruleText("end", 0));
    }
}
