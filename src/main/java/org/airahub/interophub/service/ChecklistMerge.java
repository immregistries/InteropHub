package org.airahub.interophub.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges a newer template into one checklist by item key. The organizer's
 * status, assignee, notes, and deliberate removals stay put.
 */
public final class ChecklistMerge {

    public enum Kind {
        UPDATED,
        WORDING_KEPT,
        STAYS_REMOVED,
        ADDED,
        NO_LONGER_IN_TEMPLATE,
        CUSTOM_UNTOUCHED
    }

    public record State(String key, String origin, boolean removed, boolean locallyEdited, boolean inCurrentTemplate,
            String phase, String title, String guidance, String dueAnchor, Integer dueOffsetDays, String checkCode,
            int displayOrder, String status, Long assignedUserId, LocalDate dueDateOverride) {

        State withTemplateFields(ChecklistTemplateParser.Item item, boolean keepWording) {
            return new State(key, "TEMPLATE", removed, locallyEdited, true,
                    item.phase(), keepWording ? title : item.title(), keepWording ? guidance : item.guidance(),
                    item.dueAnchor(), item.dueOffsetDays(), item.checkCode(), item.displayOrder(), status,
                    assignedUserId, dueDateOverride);
        }
    }

    public record Change(Kind kind, String key, String detail, State before, State after) {
    }

    public record Result(List<Change> changes, List<State> items, int added, int noLongerInTemplate) {
    }

    private ChecklistMerge() {
    }

    public static Result merge(List<State> current, List<ChecklistTemplateParser.Item> template) {
        Map<String, State> byKey = new LinkedHashMap<>();
        if (current != null) {
            for (State state : current) {
                byKey.put(state.key(), state);
            }
        }
        List<Change> changes = new ArrayList<>();
        Map<String, State> next = new LinkedHashMap<>();
        if (template != null) {
            for (ChecklistTemplateParser.Item item : template) {
                State existing = byKey.remove(item.key());
                if (existing == null) {
                    State added = new State(item.key(), "TEMPLATE", false, false, true, item.phase(), item.title(),
                            item.guidance(), item.dueAnchor(), item.dueOffsetDays(), item.checkCode(),
                            item.displayOrder(), "OPEN", null, null);
                    next.put(item.key(), added);
                    changes.add(new Change(Kind.ADDED, item.key(), "Add \"" + item.title() + "\" as open.", null, added));
                    continue;
                }
                if ("CUSTOM".equals(existing.origin())) {
                    next.put(existing.key(), existing);
                    changes.add(new Change(Kind.CUSTOM_UNTOUCHED, existing.key(),
                            "Custom item \"" + existing.title() + "\" is left as it is.", existing, existing));
                    continue;
                }
                if (existing.removed()) {
                    next.put(existing.key(), existing);
                    changes.add(new Change(Kind.STAYS_REMOVED, existing.key(),
                            "\"" + existing.title() + "\" stays removed.", existing, existing));
                    continue;
                }
                boolean keepWording = existing.locallyEdited();
                State updated = existing.withTemplateFields(item, keepWording);
                next.put(updated.key(), updated);
                if (keepWording) {
                    changes.add(new Change(Kind.WORDING_KEPT, existing.key(),
                            "Keep the organizer's wording. The template now says \"" + item.title() + "\".",
                            existing, updated));
                } else {
                    changes.add(new Change(Kind.UPDATED, existing.key(),
                            "Update \"" + item.title() + "\". Status and assignee stay.", existing, updated));
                }
            }
        }
        int dropped = 0;
        for (State leftover : byKey.values()) {
            if ("CUSTOM".equals(leftover.origin())) {
                next.put(leftover.key(), leftover);
                changes.add(new Change(Kind.CUSTOM_UNTOUCHED, leftover.key(),
                        "Custom item \"" + leftover.title() + "\" is left as it is.", leftover, leftover));
                continue;
            }
            State marked = new State(leftover.key(), leftover.origin(), leftover.removed(), leftover.locallyEdited(),
                    false, leftover.phase(), leftover.title(), leftover.guidance(), leftover.dueAnchor(),
                    leftover.dueOffsetDays(), leftover.checkCode(), leftover.displayOrder(), leftover.status(),
                    leftover.assignedUserId(), leftover.dueDateOverride());
            next.put(marked.key(), marked);
            dropped++;
            changes.add(new Change(Kind.NO_LONGER_IN_TEMPLATE, leftover.key(),
                    "\"" + leftover.title() + "\" is no longer in the template.", leftover, marked));
        }
        int added = 0;
        for (Change change : changes) {
            if (change.kind() == Kind.ADDED) {
                added++;
            }
        }
        return new Result(List.copyOf(changes), List.copyOf(next.values()), added, dropped);
    }

    public static String upgradeNote(int fromVersion, int toVersion, Result result) {
        return "Updated from template v" + fromVersion + " to v" + toVersion + ": " + result.added()
                + " added, " + result.noLongerInTemplate() + " no longer in template";
    }
}
