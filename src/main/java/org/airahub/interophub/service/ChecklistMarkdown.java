package org.airahub.interophub.service;

import java.util.List;

/**
 * Writes a checklist back out in the template Markdown format. The version line
 * is left off so an admin can set it when the file is uploaded.
 */
public final class ChecklistMarkdown {

    private ChecklistMarkdown() {
    }

    public static String write(String title, String kind, List<ChecklistTemplateParser.Item> items) {
        StringBuilder text = new StringBuilder();
        text.append("# ").append(title == null || title.isBlank() ? "Checklist" : title.trim()).append("\n");
        text.append("kind: ").append(kind == null ? "" : kind).append("\n\n");
        String phase = null;
        if (items != null) {
            for (ChecklistTemplateParser.Item item : items) {
                if (item.phase() != null && !item.phase().equals(phase)) {
                    phase = item.phase();
                    text.append("## ").append(phase).append("\n");
                }
                text.append("- [").append(item.key()).append("] ").append(item.title());
                String due = ChecklistDueDates.ruleText(item.dueAnchor(), item.dueOffsetDays());
                if (due != null) {
                    text.append(" (due: ").append(due).append(")");
                }
                if (item.checkCode() != null && !item.checkCode().isBlank()) {
                    text.append(" (check: ").append(item.checkCode()).append(")");
                }
                text.append("\n");
                if (item.guidance() != null && !item.guidance().isBlank()) {
                    for (String line : item.guidance().split("\n", -1)) {
                        if (!line.isBlank()) {
                            text.append("  ").append(line).append("\n");
                        }
                    }
                }
            }
        }
        return text.toString();
    }
}
