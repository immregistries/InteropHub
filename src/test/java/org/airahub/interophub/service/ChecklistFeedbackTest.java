package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChecklistFeedbackTest {

    @Test
    void groupsAddedRemovedAndRewordedItems() {
        ChecklistFeedback.Report report = ChecklistFeedback.collect(List.of(
                new ChecklistFeedback.Observed("Pilot", "mine", "CUSTOM", false, false, false, "Extra", "My step",
                        null, null, null),
                new ChecklistFeedback.Observed("Pilot", "old", "TEMPLATE", true, false, false, "Set up", "Old step",
                        null, "Old step", null),
                new ChecklistFeedback.Observed("Pilot", "skip", "TEMPLATE", false, false, true, "Set up", "Skip",
                        null, "Skip", null),
                new ChecklistFeedback.Observed("Pilot", "words", "TEMPLATE", false, true, false, "Set up",
                        "Organizer words", "Local", "Template words", "Template note"),
                new ChecklistFeedback.Observed("Pilot", "plain", "TEMPLATE", false, false, false, "Set up", "Plain",
                        null, "Plain", null)));
        assertEquals(1, report.added().size());
        assertEquals("mine", report.added().get(0).key());
        assertEquals(2, report.removedOrNotApplicable().size());
        assertEquals("Removed", report.removedOrNotApplicable().get(0).detail());
        assertEquals("Not applicable", report.removedOrNotApplicable().get(1).detail());
        assertEquals(1, report.reworded().size());
        assertEquals("Organizer words", report.reworded().get(0).title());
        assertTrue(report.reworded().get(0).detail().contains("Template words"));
        assertFalse(report.reworded().stream().anyMatch(row -> "plain".equals(row.key())));
    }
}
