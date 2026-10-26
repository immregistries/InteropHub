package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.List;

/**
 * What organizers changed on their own checklists, grouped the way the template
 * admin reviews it.
 */
public final class ChecklistFeedback {

    public record Row(String initiativeName, String key, String phase, String title, String detail) {
    }

    public record Report(List<Row> added, List<Row> removedOrNotApplicable, List<Row> reworded) {
    }

    public record Observed(String initiativeName, String key, String origin, boolean removed, boolean locallyEdited,
            boolean notApplicable, String phase, String title, String guidance, String templateTitle,
            String templateGuidance) {
    }

    private ChecklistFeedback() {
    }

    public static Report collect(List<Observed> items) {
        List<Row> added = new ArrayList<>();
        List<Row> removed = new ArrayList<>();
        List<Row> reworded = new ArrayList<>();
        if (items != null) {
            for (Observed item : items) {
                if ("CUSTOM".equals(item.origin()) && !item.removed()) {
                    added.add(new Row(item.initiativeName(), item.key(), item.phase(), item.title(), "Added"));
                    continue;
                }
                if (!"TEMPLATE".equals(item.origin())) {
                    continue;
                }
                if (item.removed()) {
                    removed.add(new Row(item.initiativeName(), item.key(), item.phase(), item.title(), "Removed"));
                } else if (item.notApplicable()) {
                    removed.add(new Row(item.initiativeName(), item.key(), item.phase(), item.title(), "Not applicable"));
                }
                if (item.locallyEdited() && !item.removed()) {
                    String detail = "Template: " + nullToEmpty(item.templateTitle());
                    if (item.templateGuidance() != null && !item.templateGuidance().isBlank()) {
                        detail = detail + "\n" + item.templateGuidance();
                    }
                    reworded.add(new Row(item.initiativeName(), item.key(), item.phase(), item.title(), detail));
                }
            }
        }
        return new Report(List.copyOf(added), List.copyOf(removed), List.copyOf(reworded));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
