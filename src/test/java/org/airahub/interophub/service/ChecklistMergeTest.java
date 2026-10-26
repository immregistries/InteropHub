package org.airahub.interophub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChecklistMergeTest {

    @Test
    void everyMergeCase() {
        ChecklistMerge.State kept = state("shared", "TEMPLATE", false, false, "Old phase", "Old title", "Old guidance",
                "OPEN", 4L);
        ChecklistMerge.State edited = state("edited", "TEMPLATE", false, true, "Phase", "Organizer title",
                "Organizer guidance", "DONE", 9L);
        ChecklistMerge.State removed = state("gone", "TEMPLATE", true, false, "Phase", "Removed title", null, "OPEN",
                null);
        ChecklistMerge.State custom = state("mine", "CUSTOM", false, false, "Extra", "My item", "Mine", "OPEN", null);
        ChecklistMerge.State dropped = state("retired", "TEMPLATE", false, false, "Old", "Retired", null, "DONE", 2L);

        List<ChecklistTemplateParser.Item> template = List.of(
                item("shared", "New phase", "New title", "New guidance", "start", 7, "topic-champion", 1),
                item("edited", "Moved", "Template title", "Template guidance", "end", -14, null, 2),
                item("gone", "Phase", "Template would restore this", null, null, null, null, 3),
                item("fresh", "New phase", "Brand new", "Do this", "start", 0, null, 4));

        ChecklistMerge.Result result = ChecklistMerge.merge(List.of(kept, edited, removed, custom, dropped), template);

        ChecklistMerge.Change shared = change(result, "shared");
        assertEquals(ChecklistMerge.Kind.UPDATED, shared.kind());
        assertEquals("New phase", shared.after().phase());
        assertEquals("New title", shared.after().title());
        assertEquals("New guidance", shared.after().guidance());
        assertEquals("start", shared.after().dueAnchor());
        assertEquals(7, shared.after().dueOffsetDays());
        assertEquals("topic-champion", shared.after().checkCode());
        assertEquals(1, shared.after().displayOrder());
        assertEquals("OPEN", shared.after().status());
        assertEquals(4L, shared.after().assignedUserId());

        ChecklistMerge.Change wording = change(result, "edited");
        assertEquals(ChecklistMerge.Kind.WORDING_KEPT, wording.kind());
        assertEquals("Organizer title", wording.after().title());
        assertEquals("Organizer guidance", wording.after().guidance());
        assertEquals("Moved", wording.after().phase());
        assertEquals("end", wording.after().dueAnchor());
        assertEquals(-14, wording.after().dueOffsetDays());
        assertEquals("DONE", wording.after().status());
        assertEquals(9L, wording.after().assignedUserId());
        assertTrue(wording.detail().contains("Template title"));

        ChecklistMerge.Change stays = change(result, "gone");
        assertEquals(ChecklistMerge.Kind.STAYS_REMOVED, stays.kind());
        assertTrue(stays.after().removed());
        assertEquals("Removed title", stays.after().title());

        ChecklistMerge.Change added = change(result, "fresh");
        assertEquals(ChecklistMerge.Kind.ADDED, added.kind());
        assertEquals("OPEN", added.after().status());
        assertEquals("TEMPLATE", added.after().origin());
        assertNull(added.after().assignedUserId());

        ChecklistMerge.Change retired = change(result, "retired");
        assertEquals(ChecklistMerge.Kind.NO_LONGER_IN_TEMPLATE, retired.kind());
        assertFalse(retired.after().inCurrentTemplate());
        assertEquals("DONE", retired.after().status());
        assertEquals(2L, retired.after().assignedUserId());

        ChecklistMerge.Change mine = change(result, "mine");
        assertEquals(ChecklistMerge.Kind.CUSTOM_UNTOUCHED, mine.kind());
        assertEquals("My item", mine.after().title());
        assertEquals("Extra", mine.after().phase());

        assertEquals(1, result.added());
        assertEquals(1, result.noLongerInTemplate());
        assertEquals("Updated from template v1 to v2: 1 added, 1 no longer in template",
                ChecklistMerge.upgradeNote(1, 2, result));
    }

    private static ChecklistMerge.State state(String key, String origin, boolean removed, boolean edited, String phase,
            String title, String guidance, String status, Long assignee) {
        return new ChecklistMerge.State(key, origin, removed, edited, true, phase, title, guidance, null, null, null,
                1, status, assignee, null);
    }

    private static ChecklistTemplateParser.Item item(String key, String phase, String title, String guidance,
            String anchor, Integer offset, String check, int order) {
        return new ChecklistTemplateParser.Item(key, phase, title, guidance, anchor, offset, check, order, order);
    }

    private static ChecklistMerge.Change change(ChecklistMerge.Result result, String key) {
        for (ChecklistMerge.Change change : result.changes()) {
            if (key.equals(change.key())) {
                return change;
            }
        }
        throw new AssertionError(key);
    }
}
