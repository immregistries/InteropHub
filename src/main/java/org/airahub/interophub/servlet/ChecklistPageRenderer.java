package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.model.EsChecklist;
import org.airahub.interophub.model.EsChecklistItem;
import org.airahub.interophub.model.EsChecklistTemplate;
import org.airahub.interophub.model.EsInitiative;
import org.airahub.interophub.model.EsInitiativeManager;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.ChecklistDueDates;
import org.airahub.interophub.service.ChecklistFeedback;
import org.airahub.interophub.service.ChecklistHints;
import org.airahub.interophub.service.ChecklistMerge;
import org.airahub.interophub.service.ChecklistService;
import org.airahub.interophub.service.ChecklistTemplateParser;

final class ChecklistPageRenderer {

    private ChecklistPageRenderer() {
    }

    static void checklist(PrintWriter out, String contextPath, EsInitiative initiative, EsChecklist checklist,
            List<EsChecklistItem> items, List<EsInitiativeManager> managers, Map<Long, String> userNames,
            Map<Long, List<EsInitiativeTimelineEntry>> itemTimeline, List<EsInitiativeTimelineEntry> checklistTimeline,
            User viewer, ChecklistHints.Facts facts, boolean canEdit, boolean upgradeAvailable, int availableVersion,
            boolean showRemoved, FormInput input) {
        String base = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/checklist";
        if (checklist == null) {
            out.println("      <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No checklist yet.</p>");
            if (canEdit) {
                out.println("        <form method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
                out.println("          <input type=\"hidden\" name=\"action\" value=\"start-checklist\" />");
                out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\">Start checklist</button>");
                out.println("        </form>");
            }
            out.println("      </div>");
            return;
        }
        if (input.error() != null) {
            out.println("      <div class=\"aira-alert aira-alert--error\"><p>" + InitiativeHtml.esc(input.error())
                    + "</p></div>");
        }
        if (upgradeAvailable) {
            out.println("      <div class=\"aira-alert aira-alert--info\"><p>Template v" + availableVersion
                    + " is available. <a href=\"" + InitiativeHtml.esc(base + "/upgrade") + "\">Preview the upgrade</a>.</p></div>");
        }
        out.println("      <div class=\"aira-cluster\">");
        out.println("        <a class=\"aira-button aira-button--secondary aira-button--small\" href=\""
                + InitiativeHtml.esc(base + "/download") + "\">Download as template</a>");
        out.println("        <a class=\"aira-button aira-button--ghost aira-button--small\" href=\""
                + InitiativeHtml.esc(base + (showRemoved ? "" : "?removed=1")) + "\">"
                + (showRemoved ? "Hide removed" : "Show removed") + "</a>");
        out.println("      </div>");
        if (!checklistTimeline.isEmpty()) {
            out.println("      <section class=\"aira-stack aira-stack--compact\">");
            out.println("        <h2>Checklist history</h2>");
            InitiativePageRenderer.timeline(out, base, checklistTimeline, userNames, viewer, false,
                    EsInitiativeTimelineEntry.SubjectType.CHECKLIST, checklist.getEsChecklistId(), input);
            out.println("      </section>");
        }
        Map<String, List<EsChecklistItem>> phases = phases(items, showRemoved);
        LocalDate today = LocalDate.now();
        for (Map.Entry<String, List<EsChecklistItem>> phase : phases.entrySet()) {
            phasePanel(out, base, phase.getKey(), phase.getValue(), managers, userNames, itemTimeline, viewer, facts,
                    initiative, today, canEdit, showRemoved, input);
        }
            if (canEdit) {
                addPhaseDialog(out, base, input);
                addItemDialog(out, base, input);
            out.println("      <form id=\"checklist-reorder\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
            out.println("        <input type=\"hidden\" name=\"action\" value=\"reorder-item\" />");
            out.println("        <input type=\"hidden\" name=\"itemId\" value=\"\" />");
            out.println("        <input type=\"hidden\" name=\"beforeItemId\" value=\"\" />");
            out.println("      </form>");
            out.println("      <p><button class=\"aira-button aira-button--secondary aira-button--small\" type=\"button\" data-dialog-open=\"add-phase\">Add phase</button></p>");
        }
    }

    static void upgrade(PrintWriter out, String contextPath, EsInitiative initiative, int fromVersion,
            EsChecklistTemplate next, ChecklistMerge.Result result, boolean canApply) {
        String base = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/checklist";
        out.println("      <p><a href=\"" + InitiativeHtml.esc(base) + "\">Back to the checklist</a></p>");
        out.println("      <h1 class=\"aira-page-title\">Template v" + next.getVersionNo() + "</h1>");
        if (!canApply) {
            out.println("      <div class=\"aira-alert aira-alert--info\"><p>Closed and archived initiatives keep the template they ran with.</p></div>");
            return;
        }
        out.println("      <p>" + InitiativeHtml.esc(ChecklistMerge.upgradeNote(fromVersion, next.getVersionNo(), result))
                + "</p>");
        out.println("      <div class=\"aira-table-wrap\"><table class=\"aira-table\"><thead><tr>");
        out.println("        <th scope=\"col\">Item</th><th scope=\"col\">What happens</th></tr></thead><tbody>");
        for (ChecklistMerge.Change change : result.changes()) {
            String title = change.after() == null ? change.key() : change.after().title();
            out.println("        <tr><th scope=\"row\">" + InitiativeHtml.esc(title) + "</th><td>"
                    + InitiativeHtml.esc(change.detail()) + "</td></tr>");
        }
        out.println("      </tbody></table></div>");
        out.println("      <form method=\"post\" action=\"" + InitiativeHtml.esc(base + "/upgrade") + "\">");
        out.println("        <input type=\"hidden\" name=\"action\" value=\"apply-upgrade\" />");
        out.println("        <button class=\"aira-button aira-button--primary\" type=\"submit\">Apply upgrade</button>");
        out.println("      </form>");
    }

    static void templates(PrintWriter out, String contextPath, List<EsChecklistTemplate> templates,
            ChecklistService.TemplatePreview preview, FormInput input) {
        String base = contextPath + "/es/initiatives/templates";
        out.println("      <div class=\"aira-page-header\"><div>");
        out.println("        <h1 class=\"aira-page-title\">Checklist templates</h1>");
        out.println("        <p class=\"aira-meta\"><a href=\"" + InitiativeHtml.esc(contextPath + "/es/initiatives")
                + "\">Initiatives</a></p>");
        out.println("      </div></div>");
        out.println("      <div class=\"aira-cluster\">");
        out.println("        <a class=\"aira-button aira-button--secondary aira-button--small\" href=\""
                + InitiativeHtml.esc(base + "/feedback/INITIATIVE") + "\">Initiative feedback</a>");
        out.println("        <a class=\"aira-button aira-button--secondary aira-button--small\" href=\""
                + InitiativeHtml.esc(base + "/feedback/SMALL_GROUP") + "\">Small Group feedback</a>");
        out.println("      </div>");
        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-panel__header\">");
        out.println("        <h2 class=\"aira-table-panel__title\">Versions</h2></div>");
        out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table\"><thead><tr>");
        out.println("          <th scope=\"col\">Kind</th><th scope=\"col\">Version</th><th scope=\"col\">Title</th><th scope=\"col\">Current</th></tr></thead><tbody>");
        if (templates.isEmpty()) {
            out.println("          <tr><td colspan=\"4\">No templates yet.</td></tr>");
        }
        for (EsChecklistTemplate template : templates) {
            out.println("          <tr><td>" + template.getTemplateKind().name() + "</td><td><a href=\""
                    + InitiativeHtml.esc(base + "/" + template.getEsChecklistTemplateId()) + "\">v"
                    + template.getVersionNo() + "</a></td><td>" + InitiativeHtml.esc(template.getTitle())
                    + "</td><td>" + (Boolean.TRUE.equals(template.getCurrent()) ? "Current" : "") + "</td></tr>");
        }
        out.println("        </tbody></table></div></section>");
        out.println("      <form class=\"aira-form aira-form--narrow\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        out.println("        <h2>Upload a version</h2>");
        uploadErrors(out, input);
        String markdown = input.posted("markdown");
        out.println("        <input type=\"hidden\" name=\"action\" value=\"preview-template\" />");
        out.println("        <div class=\"aira-field\"><label for=\"template-markdown\">Markdown</label>");
        out.println("          <textarea class=\"aira-textarea\" id=\"template-markdown\" name=\"markdown\" required>"
                + InitiativeHtml.esc(markdown) + "</textarea></div>");
        out.println("        <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--secondary\" type=\"submit\">Preview</button></div>");
        out.println("      </form>");
        if (preview != null && preview.ok()) {
            out.println("      <section class=\"aira-stack aira-stack--compact\">");
            out.println("        <h2>" + InitiativeHtml.esc(preview.parsed().title()) + " · " + preview.parsed().kind()
                    + " v" + preview.parsed().version() + "</h2>");
            out.println("        <p class=\"aira-meta\">" + preview.parsed().items().size() + " items. Saving makes this the current version.</p>");
            out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table\"><thead><tr>");
            out.println("          <th scope=\"col\">Phase</th><th scope=\"col\">Item</th><th scope=\"col\">Due</th><th scope=\"col\">Check</th></tr></thead><tbody>");
            for (ChecklistTemplateParser.Item item : preview.parsed().items()) {
                out.println("          <tr><td>" + InitiativeHtml.esc(item.phase()) + "</td><td>"
                        + InitiativeHtml.esc(item.title()) + "</td><td>"
                        + InitiativeHtml.esc(ChecklistDueDates.ruleText(item.dueAnchor(), item.dueOffsetDays()))
                        + "</td><td>" + InitiativeHtml.esc(item.checkCode()) + "</td></tr>");
            }
            out.println("        </tbody></table></div>");
            out.println("        <form method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
            out.println("          <input type=\"hidden\" name=\"action\" value=\"save-template\" />");
            out.println("          <textarea class=\"aira-visually-hidden\" name=\"markdown\">" + InitiativeHtml.esc(markdown)
                    + "</textarea>");
            out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\">Save version</button>");
            out.println("        </form></section>");
        }
    }

    static void templateSource(PrintWriter out, String contextPath, EsChecklistTemplate template) {
        String base = contextPath + "/es/initiatives/templates/" + template.getEsChecklistTemplateId();
        out.println("      <p><a href=\"" + InitiativeHtml.esc(contextPath + "/es/initiatives/templates") + "\">Templates</a></p>");
        out.println("      <h1 class=\"aira-page-title\">" + InitiativeHtml.esc(template.getTitle()) + "</h1>");
        out.println("      <p class=\"aira-meta\">" + template.getTemplateKind().name() + " v" + template.getVersionNo()
                + (Boolean.TRUE.equals(template.getCurrent()) ? " · current" : "") + "</p>");
        out.println("      <p><a class=\"aira-button aira-button--secondary aira-button--small\" href=\""
                + InitiativeHtml.esc(base + "/download") + "\">Download</a></p>");
        out.println("      <textarea class=\"aira-textarea\" readonly>" + InitiativeHtml.esc(template.getSourceMarkdown())
                + "</textarea>");
    }

    static void feedback(PrintWriter out, String contextPath, EsChecklistTemplate.Kind kind, ChecklistFeedback.Report report) {
        out.println("      <p><a href=\"" + InitiativeHtml.esc(contextPath + "/es/initiatives/templates") + "\">Templates</a></p>");
        out.println("      <h1 class=\"aira-page-title\">" + kind.name() + " template feedback</h1>");
        feedbackTable(out, "Items organizers added", report.added());
        feedbackTable(out, "Removed or not applicable", report.removedOrNotApplicable());
        feedbackTable(out, "Reworded", report.reworded());
    }

    private static void phasePanel(PrintWriter out, String base, String phase, List<EsChecklistItem> items,
            List<EsInitiativeManager> managers, Map<Long, String> userNames,
            Map<Long, List<EsInitiativeTimelineEntry>> itemTimeline, User viewer, ChecklistHints.Facts facts,
            EsInitiative initiative, LocalDate today, boolean canEdit, boolean showRemoved, FormInput input) {
        String phaseId = "phase-" + Math.abs(phase.hashCode());
        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-panel__header\">");
        out.println("        <h2 class=\"aira-table-panel__title\">" + InitiativeHtml.esc(phase) + "</h2>");
        if (canEdit) {
            out.println("        <form class=\"aira-form aira-form--inline\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
            out.println("          <input type=\"hidden\" name=\"action\" value=\"rename-phase\" />");
            out.println("          <input type=\"hidden\" name=\"fromPhase\" value=\"" + InitiativeHtml.esc(phase) + "\" />");
            out.println("          <div class=\"aira-field\"><label for=\"" + phaseId + "\">Rename</label>");
            out.println("            <input class=\"aira-input\" id=\"" + phaseId + "\" name=\"toPhase\" type=\"text\" maxlength=\"200\" required value=\""
                    + InitiativeHtml.esc(input.failed("rename-phase") && phase.equals(input.posted("fromPhase"))
                            ? input.posted("toPhase") : phase) + "\" /></div>");
            out.println("          <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"submit\">Rename</button>");
            out.println("        </form>");
        }
        out.println("      </div><div class=\"aira-table-wrap\"><table class=\"aira-table\"><thead><tr>");
        out.println("        <th scope=\"col\">Status</th><th scope=\"col\">Item</th><th scope=\"col\">Assignee</th>");
        out.println("        <th scope=\"col\">Due</th><th scope=\"col\">Updated</th><th scope=\"col\">Actions</th>");
        out.println("      </tr></thead><tbody>");
        for (EsChecklistItem item : items) {
            itemRow(out, base, item, managers, userNames, facts, initiative, today, canEdit);
        }
        out.println("      </tbody></table></div>");
        if (canEdit) {
            out.println("        <form class=\"aira-form aira-form--inline\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
            out.println("          <input type=\"hidden\" name=\"action\" value=\"add-item\" />");
            out.println("          <input type=\"hidden\" name=\"phase\" value=\"" + InitiativeHtml.esc(phase) + "\" />");
            out.println("          <div class=\"aira-field aira-field--grow\"><label class=\"aira-visually-hidden\" for=\"add-"
                    + phaseId + "\">Add item to " + InitiativeHtml.esc(phase) + "</label>");
            out.println("            <input class=\"aira-input\" id=\"add-" + phaseId + "\" name=\"title\" type=\"text\" maxlength=\"500\" required placeholder=\"Add an item\" value=\""
                    + InitiativeHtml.esc(phase.equals(input.posted("phase")) && input.posted("afterItemId").isEmpty()
                            ? input.value("add-item", "title", "") : "") + "\" /></div>");
            out.println("          <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"submit\">Add</button>");
            out.println("        </form>");
            for (EsChecklistItem item : items) {
                itemDialog(out, base, item, userNames, itemTimeline.getOrDefault(item.getEsChecklistItemId(), List.of()),
                        viewer, initiative, input);
            }
        }
        out.println("      </section>");
    }

    private static void itemRow(PrintWriter out, String base, EsChecklistItem item, List<EsInitiativeManager> managers,
            Map<Long, String> userNames, ChecklistHints.Facts facts, EsInitiative initiative, LocalDate today,
            boolean canEdit) {
        boolean removed = Boolean.TRUE.equals(item.getRemoved());
        LocalDate due = ChecklistDueDates.effective(item.getDueAnchor(), item.getDueOffsetDays(),
                item.getDueDateOverride(), initiative.getStartDate(), initiative.getEndDate());
        ChecklistDueDates.Urgency urgency = ChecklistDueDates.urgency(due, today,
                item.getItemStatus() == null ? null : item.getItemStatus().name());
        out.println("        <tr class=\"initiative-checklist-row\" data-item-id=\"" + item.getEsChecklistItemId() + "\">");
        out.println("          <td><span class=\"aira-badge aira-badge--" + statusTone(item.getItemStatus()) + "\">"
                + item.getItemStatus().getLabel() + "</span>");
        if (canEdit && !removed) {
            if (item.getItemStatus() != EsChecklistItem.Status.DONE) {
                statusButton(out, base, item, EsChecklistItem.Status.DONE, "Done");
            }
            if (item.getItemStatus() != EsChecklistItem.Status.OPEN) {
                statusButton(out, base, item, EsChecklistItem.Status.OPEN, "Reopen");
            }
            if (item.getItemStatus() != EsChecklistItem.Status.NOT_APPLICABLE) {
                statusButton(out, base, item, EsChecklistItem.Status.NOT_APPLICABLE, "Not applicable");
            }
        }
        out.println("          </td>");
        out.println("          <td>");
        if (canEdit && !removed) {
            out.println("            <form class=\"aira-form aira-form--inline\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
            out.println("              <input type=\"hidden\" name=\"action\" value=\"edit-item\" />");
            out.println("              <input type=\"hidden\" name=\"itemId\" value=\"" + item.getEsChecklistItemId() + "\" />");
            out.println("              <input type=\"hidden\" name=\"guidance\" value=\"" + InitiativeHtml.esc(item.getGuidance()) + "\" />");
            out.println("              <div class=\"aira-field aira-field--grow\"><label class=\"aira-visually-hidden\" for=\"title-"
                    + item.getEsChecklistItemId() + "\">Title</label>");
            out.println("                <input class=\"aira-input\" id=\"title-" + item.getEsChecklistItemId()
                    + "\" name=\"title\" type=\"text\" maxlength=\"500\" required value=\""
                    + InitiativeHtml.esc(item.getTitle()) + "\" /></div>");
            out.println("              <button class=\"aira-button aira-button--small\" type=\"submit\">Save</button></form>");
        } else {
            out.println("            " + InitiativeHtml.esc(item.getTitle()));
        }
        if (item.getItemOrigin() == EsChecklistItem.Origin.TEMPLATE && !Boolean.TRUE.equals(item.getInCurrentTemplate())) {
            out.println("            <span class=\"aira-badge aira-badge--warning\">No longer in template</span>");
        }
        String hint = ChecklistHints.hint(item.getCheckCode(), facts);
        if (hint != null) {
            out.println("            <p class=\"aira-meta\">" + InitiativeHtml.esc(hint) + "</p>");
        }
        if (item.getGuidance() != null && !item.getGuidance().isBlank()) {
            String excerpt = item.getGuidance().replace('\n', ' ');
            if (excerpt.length() > 120) {
                excerpt = excerpt.substring(0, 117) + "...";
            }
            out.println("            <p class=\"aira-meta\">" + InitiativeHtml.esc(excerpt) + "</p>");
        }
        out.println("          </td>");
        out.println("          <td>");
        if (canEdit && !removed) {
            out.println("            <form class=\"aira-form aira-form--inline\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
            out.println("              <input type=\"hidden\" name=\"action\" value=\"assign-item\" />");
            out.println("              <input type=\"hidden\" name=\"itemId\" value=\"" + item.getEsChecklistItemId() + "\" />");
            out.println("              <label class=\"aira-visually-hidden\" for=\"assignee-" + item.getEsChecklistItemId() + "\">Assignee</label>");
            out.println("              <select class=\"aira-select\" id=\"assignee-" + item.getEsChecklistItemId() + "\" name=\"assignedUserId\">");
            out.println("                <option value=\"\">Unassigned</option>");
            for (EsInitiativeManager manager : managers) {
                boolean selected = manager.getUserId().equals(item.getAssignedUserId());
                out.println("                <option value=\"" + manager.getUserId() + "\"" + (selected ? " selected" : "")
                        + ">" + InitiativeHtml.esc(userNames.getOrDefault(manager.getUserId(), "Manager")) + "</option>");
            }
            out.println("              </select>");
            out.println("              <button class=\"aira-button aira-button--small\" type=\"submit\">Save</button></form>");
        } else {
            out.println("            " + InitiativeHtml.esc(userNames.getOrDefault(item.getAssignedUserId(), "")));
        }
        out.println("          </td>");
        out.println("          <td>" + (due == null ? "" : InitiativePageRenderer.DATE_FORMAT.format(due)));
        if (item.getDueDateOverride() != null) {
            out.println("            <span class=\"aira-meta\">override</span>");
        }
        if (urgency == ChecklistDueDates.Urgency.OVERDUE) {
            out.println("            <span class=\"aira-badge aira-badge--danger\">Overdue</span>");
        } else if (urgency == ChecklistDueDates.Urgency.DUE_SOON) {
            out.println("            <span class=\"aira-badge aira-badge--warning\">Due soon</span>");
        }
        out.println("          </td>");
        LocalDateTime updated = item.getStatusChangedAt() != null ? item.getStatusChangedAt() : item.getUpdatedAt();
        out.println("          <td>" + (updated == null ? "" : InitiativePageRenderer.DATE_FORMAT.format(updated)) + "</td>");
        out.println("          <td>");
        if (canEdit) {
            if (removed) {
                mini(out, base, item, "restore-item", "Restore");
            } else {
                out.println("            <span class=\"aira-button aira-button--small aira-button--ghost initiative-checklist-handle\" draggable=\"true\">Move</span>");
                mini(out, base, item, "move-item", "up", "Up");
                mini(out, base, item, "move-item", "down", "Down");
                out.println("            <button class=\"aira-button aira-button--small aira-button--ghost\" type=\"button\" data-add-after=\""
                        + item.getEsChecklistItemId() + "\" data-phase=\"" + InitiativeHtml.esc(item.getPhaseLabel())
                        + "\">Add below</button>");
                mini(out, base, item, "remove-item", "Remove");
            }
            out.println("            <button class=\"aira-button aira-button--small\" type=\"button\" data-dialog-open=\"item-"
                    + item.getEsChecklistItemId() + "\">Details</button>");
        }
        out.println("          </td></tr>");
    }

    private static void itemDialog(PrintWriter out, String base, EsChecklistItem item, Map<Long, String> userNames,
            List<EsInitiativeTimelineEntry> timeline, User viewer, EsInitiative initiative, FormInput input) {
        String id = "item-" + item.getEsChecklistItemId();
        boolean failed = id.equals(input.openDialog());
        InitiativePageRenderer.dialogStart(out, id, item.getTitle(), input);
        out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        out.println("          <h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">" + InitiativeHtml.esc(item.getTitle()) + "</h3>");
        if (failed && input.failed("edit-item")) {
            InitiativePageRenderer.formError(out, input, "edit-item");
        }
        out.println("          <input type=\"hidden\" name=\"action\" value=\"edit-item\" />");
        out.println("          <input type=\"hidden\" name=\"itemId\" value=\"" + item.getEsChecklistItemId() + "\" />");
        out.println("          <div class=\"aira-field\"><label for=\"" + id + "-title-input\">Title</label>");
        out.println("            <input class=\"aira-input\" id=\"" + id + "-title-input\" name=\"title\" type=\"text\" maxlength=\"500\" required value=\""
                + InitiativeHtml.esc(failed ? input.value("edit-item", "title", item.getTitle()) : item.getTitle()) + "\" /></div>");
        out.println("          <div class=\"aira-field\"><label for=\"" + id + "-guidance\">Guidance</label>");
        out.println("            <textarea class=\"aira-textarea\" id=\"" + id + "-guidance\" name=\"guidance\">"
                + InitiativeHtml.esc(failed ? input.value("edit-item", "guidance", item.getGuidance()) : item.getGuidance())
                + "</textarea></div>");
        out.println("          <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--secondary\" type=\"submit\">Save text</button></div>");
        out.println("        </form>");
        out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"item-status\" />");
        out.println("          <input type=\"hidden\" name=\"itemId\" value=\"" + item.getEsChecklistItemId() + "\" />");
        out.println("          <div class=\"aira-field-row\">");
        out.println("            <div class=\"aira-field\"><label for=\"" + id + "-status\">Status</label>");
        out.println("              <select class=\"aira-select\" id=\"" + id + "-status\" name=\"itemStatus\">");
        for (EsChecklistItem.Status status : EsChecklistItem.Status.values()) {
            out.println("                <option value=\"" + status.name() + "\""
                    + (status == item.getItemStatus() ? " selected" : "") + ">" + status.getLabel() + "</option>");
        }
        out.println("              </select></div>");
        out.println("            <div class=\"aira-field\"><label for=\"" + id + "-note\">Note</label>");
        out.println("              <input class=\"aira-input\" id=\"" + id + "-note\" name=\"note\" type=\"text\" value=\"\" /></div>");
        out.println("          </div>");
        out.println("          <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--primary\" type=\"submit\">Update status</button></div>");
        out.println("        </form>");
        out.println("        <form id=\"" + id + "-due-form\" class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                + InitiativeHtml.esc(base) + "\">");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"due-override\" />");
        out.println("          <input type=\"hidden\" name=\"itemId\" value=\"" + item.getEsChecklistItemId() + "\" />");
        out.println("          <div class=\"aira-field\"><label for=\"" + id + "-due\">Due date override</label>");
        String dueValue = item.getDueDateOverride() == null ? "" : item.getDueDateOverride().toString();
        out.println("            <input class=\"aira-input\" id=\"" + id + "-due\" name=\"dueDate\" type=\"date\" value=\""
                + InitiativeHtml.esc(failed ? input.value("due-override", "dueDate", dueValue) : dueValue) + "\" /></div>");
        out.println("          <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--small aira-button--secondary\" type=\"submit\">Save due date</button></div>");
        out.println("        </form>");
        InitiativePageRenderer.timeline(out, base, timeline, userNames, viewer, true,
                EsInitiativeTimelineEntry.SubjectType.CHECKLIST_ITEM, item.getEsChecklistItemId(), input);
        out.println("        <div class=\"aira-dialog__actions aira-form-actions aira-form-actions--end\">");
        out.println("          <button class=\"aira-button aira-button--ghost\" type=\"button\" data-dialog-close=\""
                + id + "\">Close</button></div>");
        out.println("      </dialog>");
    }

    private static void addItemDialog(PrintWriter out, String base, FormInput input) {
        InitiativePageRenderer.dialogStart(out, "add-item", "Add item", input);
        out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        out.println("          <h3 class=\"aira-dialog__title\" id=\"add-item-title\">Add item</h3>");
        if (input.failed("add-item") && !input.posted("afterItemId").isEmpty()) {
            InitiativePageRenderer.formError(out, input, "add-item");
        }
        out.println("          <input type=\"hidden\" name=\"action\" value=\"add-item\" />");
        out.println("          <input type=\"hidden\" name=\"afterItemId\" value=\""
                + InitiativeHtml.esc(input.value("add-item", "afterItemId", "")) + "\" />");
        out.println("          <div class=\"aira-field\"><label for=\"add-item-phase\">Phase</label>");
        out.println("            <input class=\"aira-input\" id=\"add-item-phase\" name=\"phase\" type=\"text\" maxlength=\"200\" required value=\""
                + InitiativeHtml.esc(input.value("add-item", "phase", "")) + "\" /></div>");
        out.println("          <div class=\"aira-field\"><label for=\"add-item-title-input\">Title</label>");
        out.println("            <input class=\"aira-input\" id=\"add-item-title-input\" name=\"title\" type=\"text\" maxlength=\"500\" required value=\""
                + InitiativeHtml.esc(input.posted("afterItemId").isEmpty() ? "" : input.value("add-item", "title", ""))
                + "\" /></div>");
        out.println("          <div class=\"aira-field\"><label for=\"add-item-guidance\">Guidance</label>");
        out.println("            <textarea class=\"aira-textarea\" id=\"add-item-guidance\" name=\"guidance\">"
                + InitiativeHtml.esc(input.value("add-item", "guidance", "")) + "</textarea></div>");
        InitiativePageRenderer.dialogActions(out, "add-item", "Add");
        out.println("        </form></dialog>");
    }

    private static void addPhaseDialog(PrintWriter out, String base, FormInput input) {
        InitiativePageRenderer.dialogStart(out, "add-phase", "Add phase", input);
        out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        out.println("          <h3 class=\"aira-dialog__title\" id=\"add-phase-title\">Add phase</h3>");
        InitiativePageRenderer.formError(out, input, "add-phase");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"add-phase\" />");
        out.println("          <div class=\"aira-field\"><label for=\"add-phase-name\">Phase</label>");
        out.println("            <input class=\"aira-input\" id=\"add-phase-name\" name=\"phase\" type=\"text\" maxlength=\"200\" required value=\""
                + InitiativeHtml.esc(input.failed("add-phase") ? input.posted("phase") : "") + "\" /></div>");
        out.println("          <div class=\"aira-field\"><label for=\"add-phase-title-input\">First item</label>");
        out.println("            <input class=\"aira-input\" id=\"add-phase-title-input\" name=\"title\" type=\"text\" maxlength=\"500\" required value=\""
                + InitiativeHtml.esc(input.failed("add-phase") ? input.posted("title") : "") + "\" /></div>");
        InitiativePageRenderer.dialogActions(out, "add-phase", "Add phase");
        out.println("        </form></dialog>");
    }

    private static void statusButton(PrintWriter out, String base, EsChecklistItem item, EsChecklistItem.Status status,
            String label) {
        out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        out.println("              <input type=\"hidden\" name=\"action\" value=\"item-status\" />");
        out.println("              <input type=\"hidden\" name=\"itemId\" value=\"" + item.getEsChecklistItemId() + "\" />");
        out.println("              <input type=\"hidden\" name=\"itemStatus\" value=\"" + status.name() + "\" />");
        out.println("              <button class=\"aira-button aira-button--small\" type=\"submit\">" + label + "</button></form>");
    }

    private static void mini(PrintWriter out, String base, EsChecklistItem item, String action, String label) {
        mini(out, base, item, action, null, label);
    }

    private static void mini(PrintWriter out, String base, EsChecklistItem item, String action, String direction,
            String label) {
        out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        out.println("              <input type=\"hidden\" name=\"action\" value=\"" + action + "\" />");
        out.println("              <input type=\"hidden\" name=\"itemId\" value=\"" + item.getEsChecklistItemId() + "\" />");
        if (direction != null) {
            out.println("              <input type=\"hidden\" name=\"direction\" value=\"" + direction + "\" />");
        }
        out.println("              <button class=\"aira-button aira-button--small aira-button--ghost\" type=\"submit\">"
                + label + "</button></form>");
    }

    private static void feedbackTable(PrintWriter out, String title, List<ChecklistFeedback.Row> rows) {
        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-panel__header\">");
        out.println("        <h2 class=\"aira-table-panel__title\">" + InitiativeHtml.esc(title) + "</h2>");
        out.println("        <p class=\"aira-table-panel__description\">" + rows.size() + "</p></div>");
        out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table\"><thead><tr>");
        out.println("          <th scope=\"col\">Initiative</th><th scope=\"col\">Phase</th><th scope=\"col\">Item</th><th scope=\"col\">Detail</th>");
        out.println("        </tr></thead><tbody>");
        if (rows.isEmpty()) {
            out.println("          <tr><td colspan=\"4\">None.</td></tr>");
        }
        for (ChecklistFeedback.Row row : rows) {
            out.println("          <tr><td>" + InitiativeHtml.esc(row.initiativeName()) + "</td><td>"
                    + InitiativeHtml.esc(row.phase()) + "</td><td>" + InitiativeHtml.esc(row.title()) + "</td><td>"
                    + InitiativeHtml.esc(row.detail()) + "</td></tr>");
        }
        out.println("        </tbody></table></div></section>");
    }

    private static void uploadErrors(PrintWriter out, FormInput input) {
        String error = input.failed("preview-template") ? input.error()
                : (input.failed("save-template") ? input.error() : null);
        if (error == null || error.isBlank()) {
            return;
        }
        out.println("        <div class=\"aira-alert aira-alert--error\">");
        for (String line : error.split("\n")) {
            out.println("          <p>" + InitiativeHtml.esc(line) + "</p>");
        }
        out.println("        </div>");
    }

    private static Map<String, List<EsChecklistItem>> phases(List<EsChecklistItem> items, boolean showRemoved) {
        Map<String, List<EsChecklistItem>> phases = new LinkedHashMap<>();
        List<EsChecklistItem> ordered = new ArrayList<>(items);
        ordered.sort((left, right) -> Integer.compare(
                left.getDisplayOrder() == null ? 0 : left.getDisplayOrder(),
                right.getDisplayOrder() == null ? 0 : right.getDisplayOrder()));
        for (EsChecklistItem item : ordered) {
            if (!showRemoved && Boolean.TRUE.equals(item.getRemoved())) {
                continue;
            }
            phases.computeIfAbsent(item.getPhaseLabel(), key -> new ArrayList<>()).add(item);
        }
        return phases;
    }

    private static String statusTone(EsChecklistItem.Status status) {
        if (status == EsChecklistItem.Status.DONE) {
            return "success";
        }
        if (status == EsChecklistItem.Status.NOT_APPLICABLE) {
            return "subtle";
        }
        return "info";
    }
}
