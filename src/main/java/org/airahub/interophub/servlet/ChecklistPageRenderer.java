package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.io.StringWriter;
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
            boolean showRemoved, boolean hideDone, boolean templateAdmin, FormInput input) {
        String base = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/checklist";
        checklistAt(out, contextPath, base, ChecklistDueDates.Anchors.initiative(initiative.getStartDate(), initiative.getEndDate()),
                initiative.getInitiativeName(), true, checklist, items, managers, userNames, itemTimeline,
                checklistTimeline, viewer, facts, canEdit, upgradeAvailable, availableVersion, showRemoved, hideDone,
                templateAdmin, input);
    }

    static void embedded(PrintWriter out, String contextPath, String base, ChecklistDueDates.Anchors anchors,
            EsChecklist checklist, List<EsChecklistItem> items, List<EsInitiativeManager> managers,
            Map<Long, String> userNames, Map<Long, List<EsInitiativeTimelineEntry>> itemTimeline,
            List<EsInitiativeTimelineEntry> checklistTimeline, User viewer, ChecklistHints.Facts facts, boolean canEdit,
            boolean upgradeAvailable, int availableVersion, boolean showRemoved, boolean hideDone, boolean templateAdmin,
            FormInput input) {
        checklistAt(out, contextPath, base, anchors, null, false, checklist, items, managers, userNames, itemTimeline,
                checklistTimeline, viewer, facts, canEdit, upgradeAvailable, availableVersion, showRemoved, hideDone,
                templateAdmin, input);
    }

    private static void checklistAt(PrintWriter out, String contextPath, String base, ChecklistDueDates.Anchors anchors, String title,
            boolean showTitle, EsChecklist checklist, List<EsChecklistItem> items, List<EsInitiativeManager> managers,
            Map<Long, String> userNames, Map<Long, List<EsInitiativeTimelineEntry>> itemTimeline,
            List<EsInitiativeTimelineEntry> checklistTimeline, User viewer, ChecklistHints.Facts facts, boolean canEdit,
            boolean upgradeAvailable, int availableVersion, boolean showRemoved, boolean hideDone, boolean templateAdmin,
            FormInput input) {
        String action = base + query(showRemoved, hideDone);
        if (showTitle) {
            out.println("      <div class=\"aira-page-header\"><div>");
            out.println("        <h1 class=\"aira-page-title\">" + InitiativeHtml.esc(title) + "</h1>");
            out.println("      </div></div>");
        }
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
        LocalDate today = LocalDate.now();
        Progress progress = progress(items, anchors, today);
        out.println("      <p class=\"aira-meta\" id=\"checklist-progress\">" + InitiativeHtml.esc(progress.text()) + "</p>");
        if (upgradeAvailable) {
            out.println("      <div class=\"aira-alert aira-alert--info\"><p>Template v" + availableVersion
                    + " is available. <a href=\"" + InitiativeHtml.esc(base + "/upgrade") + "\">Preview the upgrade</a>.</p></div>");
        }
        out.println("      <div class=\"initiative-checklist\" data-checklist data-show-removed=\"" + showRemoved + "\">");
        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-panel__header\">");
        out.println("        <h2 class=\"aira-table-panel__title\">Checklist</h2>");
        out.println("        <div class=\"aira-table-panel__actions\">");
        out.println("          <a class=\"aira-button aira-button--small aira-button--secondary\" href=\""
                + InitiativeHtml.esc(base + "/download") + "\">Download as template</a>");
        out.println("          <a class=\"aira-button aira-button--small aira-button--ghost\" data-removed-toggle href=\""
                + InitiativeHtml.esc(base + query(!showRemoved, hideDone)) + "\">"
                + (showRemoved ? "Hide removed" : "Show removed") + "</a>");
        out.println("          <a class=\"aira-button aira-button--small aira-button--ghost\" data-hide-done href=\""
                + InitiativeHtml.esc(base + query(showRemoved, !hideDone)) + "\" aria-pressed=\"" + hideDone
                + "\">" + (hideDone ? "Show done" : "Hide done") + "</a>");
        if (templateAdmin) {
            out.println("          <a class=\"aira-button aira-button--small aira-button--ghost\" href=\""
                    + InitiativeHtml.esc(contextPath + "/es/initiatives/templates") + "\">Templates</a>");
        }
        if (canEdit) {
            out.println("          <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"button\" data-dialog-open=\"add-phase\">Add phase</button>");
        }
        out.println("        </div></div>");
        Map<String, List<EsChecklistItem>> phases = phases(items, showRemoved);
        out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table\"><thead><tr>");
        out.println("          <th scope=\"col\"><span class=\"aira-visually-hidden\">Reorder</span></th>");
        out.println("          <th scope=\"col\"><span class=\"aira-visually-hidden\">Done</span></th>");
        out.println("          <th scope=\"col\">Item</th><th scope=\"col\">Assignee</th><th scope=\"col\">Due</th>");
        out.println("          <th scope=\"col\"><span class=\"aira-visually-hidden\">Details</span></th>");
        out.println("          <th scope=\"col\"><span class=\"aira-visually-hidden\">Remove</span></th>");
        out.println("        </tr></thead><tbody>");
        for (Map.Entry<String, List<EsChecklistItem>> phase : phases.entrySet()) {
            phaseRows(out, action, phase.getKey(), phase.getValue(), managers, userNames, facts, anchors, today,
                    canEdit, hideDone, input);
        }
        out.println("        </tbody></table></div></section>");
        out.println("      <div id=\"checklist-dialogs\">");
        for (EsChecklistItem item : items) {
            if (!showRemoved && Boolean.TRUE.equals(item.getRemoved())) {
                continue;
            }
            itemDialog(out, action, item, userNames, itemTimeline.getOrDefault(item.getEsChecklistItemId(), List.of()),
                    viewer, canEdit, input);
        }
        if (canEdit) {
            addPhaseDialog(out, action, input);
        }
        out.println("      </div></div>");
        out.println("      <section class=\"aira-stack aira-stack--compact\">");
        out.println("        <h2>Checklist history</h2>");
        InitiativePageRenderer.timeline(out, action, checklistTimeline, userNames, viewer, false,
                EsInitiativeTimelineEntry.SubjectType.CHECKLIST, checklist.getEsChecklistId(), input);
        out.println("      </section>");
    }

    static String itemRowMarkup(String action, EsChecklistItem item, List<EsInitiativeManager> managers,
            Map<Long, String> userNames, ChecklistHints.Facts facts, EsInitiative initiative, LocalDate today,
            boolean canEdit) {
        return itemRowMarkup(action, item, managers, userNames, facts,
                ChecklistDueDates.Anchors.initiative(initiative.getStartDate(), initiative.getEndDate()), today, canEdit);
    }

    static String itemRowMarkup(String action, EsChecklistItem item, List<EsInitiativeManager> managers,
            Map<Long, String> userNames, ChecklistHints.Facts facts, ChecklistDueDates.Anchors anchors, LocalDate today,
            boolean canEdit) {
        StringWriter buffer = new StringWriter();
        itemRow(new PrintWriter(buffer), action, item, managers, userNames, facts, anchors, today, canEdit, false);
        return buffer.toString();
    }

    static String itemDialogMarkup(String action, EsChecklistItem item, Map<Long, String> userNames, User viewer,
            boolean canEdit) {
        StringWriter buffer = new StringWriter();
        itemDialog(new PrintWriter(buffer), action, item, userNames, List.of(), viewer, canEdit, FormInput.none());
        return buffer.toString();
    }

    static Progress progress(List<EsChecklistItem> items, EsInitiative initiative, LocalDate today) {
        return progress(items, ChecklistDueDates.Anchors.initiative(initiative.getStartDate(), initiative.getEndDate()),
                today);
    }

    static Progress progress(List<EsChecklistItem> items, ChecklistDueDates.Anchors anchors, LocalDate today) {
        int done = 0;
        int total = 0;
        int overdue = 0;
        int dueSoon = 0;
        for (EsChecklistItem item : items) {
            if (Boolean.TRUE.equals(item.getRemoved())) {
                continue;
            }
            total++;
            if (item.getItemStatus() == EsChecklistItem.Status.DONE) {
                done++;
            }
            ChecklistDueDates.Urgency urgency = urgency(item, anchors, today);
            if (urgency == ChecklistDueDates.Urgency.OVERDUE) {
                overdue++;
            } else if (urgency == ChecklistDueDates.Urgency.DUE_SOON) {
                dueSoon++;
            }
        }
        return new Progress(done, total, overdue, dueSoon);
    }

    record Progress(int done, int total, int overdue, int dueSoon) {
        String text() {
            return done + " of " + total + " done · " + overdue + " overdue · " + dueSoon + " due soon";
        }
    }

    static void upgrade(PrintWriter out, String contextPath, EsInitiative initiative, int fromVersion,
            EsChecklistTemplate next, ChecklistMerge.Result result, boolean canApply) {
        String base = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/checklist";
        upgrade(out, base, fromVersion, next, result, canApply,
                "Closed and archived initiatives keep the template they ran with.", "Back to the checklist");
    }

    static void upgrade(PrintWriter out, String base, int fromVersion, EsChecklistTemplate next,
            ChecklistMerge.Result result, boolean canApply, String closedMessage, String backLabel) {
        out.println("      <p><a href=\"" + InitiativeHtml.esc(base) + "\">" + InitiativeHtml.esc(backLabel) + "</a></p>");
        out.println("      <h1 class=\"aira-page-title\">Template v" + next.getVersionNo() + "</h1>");
        if (!canApply) {
            out.println("      <div class=\"aira-alert aira-alert--info\"><p>" + InitiativeHtml.esc(closedMessage)
                    + "</p></div>");
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
            out.println("          <tr><td>" + kindLabel(template.getTemplateKind().name()) + "</td><td><a href=\""
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
            out.println("        <h2>" + InitiativeHtml.esc(preview.parsed().title()) + " · " + kindLabel(preview.parsed().kind())
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
        out.println("      <p class=\"aira-meta\">" + kindLabel(template.getTemplateKind().name()) + " v" + template.getVersionNo()
                + (Boolean.TRUE.equals(template.getCurrent()) ? " · current" : "") + "</p>");
        out.println("      <p><a class=\"aira-button aira-button--secondary aira-button--small\" href=\""
                + InitiativeHtml.esc(base + "/download") + "\">Download</a></p>");
        out.println("      <textarea class=\"aira-textarea\" readonly>" + InitiativeHtml.esc(template.getSourceMarkdown())
                + "</textarea>");
    }

    static void feedback(PrintWriter out, String contextPath, EsChecklistTemplate.Kind kind, ChecklistFeedback.Report report) {
        out.println("      <p><a href=\"" + InitiativeHtml.esc(contextPath + "/es/initiatives/templates") + "\">Templates</a></p>");
        out.println("      <h1 class=\"aira-page-title\">" + kindLabel(kind.name()) + " template feedback</h1>");
        feedbackTable(out, "Items organizers added", report.added());
        feedbackTable(out, "Removed or not applicable", report.removedOrNotApplicable());
        feedbackTable(out, "Reworded", report.reworded());
    }

    private static void phaseRows(PrintWriter out, String action, String phase, List<EsChecklistItem> items,
            List<EsInitiativeManager> managers, Map<Long, String> userNames, ChecklistHints.Facts facts,
            ChecklistDueDates.Anchors anchors, LocalDate today, boolean canEdit, boolean hideDone, FormInput input) {
        String phaseId = "phase-" + Math.abs(phase.hashCode());
        out.println("        <tr class=\"initiative-checklist-phase\" data-phase=\"" + InitiativeHtml.esc(phase) + "\">");
        out.println("          <th colspan=\"7\" scope=\"colgroup\">");
        if (canEdit) {
            String posted = input.failed("rename-phase") && phase.equals(input.posted("fromPhase"))
                    ? input.posted("toPhase") : phase;
            out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(action)
                    + "\" data-checklist-save>");
            out.println("              <input type=\"hidden\" name=\"action\" value=\"rename-phase\" />");
            out.println("              <input type=\"hidden\" name=\"fromPhase\" value=\"" + InitiativeHtml.esc(phase) + "\" />");
            out.println("              <button class=\"initiative-checklist-phase-name\" type=\"button\">"
                    + InitiativeHtml.esc(phase) + "</button>");
            out.println("              <input class=\"aira-input initiative-checklist-editor\" name=\"toPhase\" type=\"text\" maxlength=\"200\" hidden value=\""
                    + InitiativeHtml.esc(posted) + "\" />");
            out.println("              <span class=\"initiative-checklist-note\" aria-live=\"polite\"></span>");
            out.println("            </form>");
        } else {
            out.println("            " + InitiativeHtml.esc(phase));
        }
        out.println("          </th></tr>");
        for (EsChecklistItem item : items) {
            itemRow(out, action, item, managers, userNames, facts, anchors, today, canEdit,
                    hideDone && item.getItemStatus() == EsChecklistItem.Status.DONE);
        }
        if (canEdit) {
            String kept = phase.equals(input.posted("phase")) && input.posted("afterItemId").isEmpty()
                    && input.failed("add-item") ? input.posted("title") : "";
            out.println("        <tr class=\"initiative-checklist-add\" data-phase=\"" + InitiativeHtml.esc(phase) + "\">");
            out.println("          <td colspan=\"7\">");
            out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(action)
                    + "\" data-checklist-save>");
            out.println("              <input type=\"hidden\" name=\"action\" value=\"add-item\" />");
            out.println("              <input type=\"hidden\" name=\"phase\" value=\"" + InitiativeHtml.esc(phase) + "\" />");
            out.println("              <label class=\"aira-visually-hidden\" for=\"add-" + phaseId + "\">Add item to "
                    + InitiativeHtml.esc(phase) + "</label>");
            out.println("              <input class=\"aira-input\" id=\"add-" + phaseId
                    + "\" name=\"title\" type=\"text\" maxlength=\"500\" placeholder=\"+ Add item\" value=\""
                    + InitiativeHtml.esc(kept) + "\" />");
            out.println("              <span class=\"initiative-checklist-note\" aria-live=\"polite\"></span>");
            out.println("            </form></td></tr>");
        }
    }

    private static void itemRow(PrintWriter out, String action, EsChecklistItem item, List<EsInitiativeManager> managers,
            Map<Long, String> userNames, ChecklistHints.Facts facts, ChecklistDueDates.Anchors anchors, LocalDate today,
            boolean canEdit, boolean hidden) {
        boolean removed = Boolean.TRUE.equals(item.getRemoved());
        boolean editable = canEdit && !removed;
        EsChecklistItem.Status status = item.getItemStatus();
        boolean muted = status == EsChecklistItem.Status.DONE || status == EsChecklistItem.Status.NOT_APPLICABLE;
        ChecklistDueDates.Urgency urgency = urgency(item, anchors, today);
        LocalDate due = dueDate(item, anchors);
        String id = String.valueOf(item.getEsChecklistItemId());
        out.println("        <tr id=\"item-" + id + "\" class=\"initiative-checklist-row" + (muted ? " aira-muted" : "")
                + "\" data-item-id=\"" + id + "\" data-phase=\"" + InitiativeHtml.esc(item.getPhaseLabel())
                + "\" data-status=\"" + status.name() + "\" data-removed=\"" + removed + "\""
                + (hidden ? " hidden" : "") + ">");
        out.println("          <td>");
        if (editable) {
            out.println("            <button class=\"initiative-checklist-handle\" type=\"button\" draggable=\"true\" aria-label=\"Reorder "
                    + InitiativeHtml.esc(item.getTitle()) + "\">⋮⋮</button>");
        }
        out.println("          </td>");
        out.println("          <td>");
        if (editable) {
            String next = status == EsChecklistItem.Status.DONE ? "OPEN" : "DONE";
            out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(action)
                    + "\" data-checklist-save>");
            out.println("              <input type=\"hidden\" name=\"action\" value=\"item-status\" />");
            out.println("              <input type=\"hidden\" name=\"itemId\" value=\"" + id + "\" />");
            out.println("              <input type=\"hidden\" name=\"itemStatus\" value=\"" + next + "\" />");
            out.println("              <input type=\"checkbox\"" + (status == EsChecklistItem.Status.DONE ? " checked" : "")
                    + " aria-label=\"Done\" />");
            out.println("              <noscript><button class=\"aira-button aira-button--link\" type=\"submit\">"
                    + (status == EsChecklistItem.Status.DONE ? "Reopen" : "Mark done") + "</button></noscript>");
            out.println("              <span class=\"initiative-checklist-note\" aria-live=\"polite\"></span>");
            out.println("            </form>");
        } else {
            out.println("            <input type=\"checkbox\" disabled" + (status == EsChecklistItem.Status.DONE ? " checked" : "")
                    + " aria-label=\"Done\" />");
        }
        out.println("          </td>");
        out.println("          <td class=\"aira-table__cell--wrap\">");
        if (editable) {
            out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(action)
                    + "\" data-checklist-save>");
            out.println("              <input type=\"hidden\" name=\"action\" value=\"edit-item\" />");
            out.println("              <input type=\"hidden\" name=\"itemId\" value=\"" + id + "\" />");
            out.println("              <input type=\"hidden\" name=\"guidance\" value=\"" + InitiativeHtml.esc(item.getGuidance())
                    + "\" />");
            out.println("              <button class=\"initiative-checklist-text\" type=\"button\">"
                    + InitiativeHtml.esc(item.getTitle()) + "</button>");
            out.println("              <input class=\"aira-input initiative-checklist-editor\" name=\"title\" type=\"text\" maxlength=\"500\" hidden value=\""
                    + InitiativeHtml.esc(item.getTitle()) + "\" />");
            out.println("              <span class=\"initiative-checklist-note\" aria-live=\"polite\"></span>");
            out.println("            </form>");
        } else {
            out.println("            " + InitiativeHtml.esc(item.getTitle()));
        }
        if (item.getItemOrigin() == EsChecklistItem.Origin.TEMPLATE && !Boolean.TRUE.equals(item.getInCurrentTemplate())) {
            out.println("            <br /><span class=\"aira-badge aira-badge--warning\">No longer in template</span>");
        }
        String hint = ChecklistHints.hint(item.getCheckCode(), facts);
        if (hint != null) {
            out.println("            <br /><span class=\"aira-meta\">" + InitiativeHtml.esc(hint) + "</span>");
        }
        out.println("          </td>");
        out.println("          <td>");
        if (editable) {
            out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(action)
                    + "\" data-checklist-save>");
            out.println("              <input type=\"hidden\" name=\"action\" value=\"assign-item\" />");
            out.println("              <input type=\"hidden\" name=\"itemId\" value=\"" + id + "\" />");
            out.println("              <label class=\"aira-visually-hidden\" for=\"assignee-" + id + "\">Assignee</label>");
            out.println("              <select class=\"aira-select\" id=\"assignee-" + id + "\" name=\"assignedUserId\">");
            out.println("                <option value=\"\">Unassigned</option>");
            for (EsInitiativeManager manager : managers) {
                boolean selected = manager.getUserId().equals(item.getAssignedUserId());
                out.println("                <option value=\"" + manager.getUserId() + "\"" + (selected ? " selected" : "")
                        + ">" + InitiativeHtml.esc(userNames.getOrDefault(manager.getUserId(), "Manager")) + "</option>");
            }
            out.println("              </select>");
            out.println("              <noscript><button class=\"aira-button aira-button--small\" type=\"submit\">Save</button></noscript>");
            out.println("              <span class=\"initiative-checklist-note\" aria-live=\"polite\"></span>");
            out.println("            </form>");
        } else if (item.getAssignedUserId() != null) {
            out.println("            " + InitiativeHtml.esc(userNames.getOrDefault(item.getAssignedUserId(), "")));
        }
        out.println("          </td>");
        dueCell(out, due, urgency, item.getDueDateOverride() != null);
        out.println("          <td><button class=\"aira-button aira-button--link\" type=\"button\" data-dialog-open=\"item-"
                + id + "-dialog\">Details</button></td>");
        out.println("          <td>");
        if (canEdit) {
            out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(action)
                    + "\" data-checklist-save>");
            out.println("              <input type=\"hidden\" name=\"action\" value=\""
                    + (removed ? "restore-item" : "remove-item") + "\" />");
            out.println("              <input type=\"hidden\" name=\"itemId\" value=\"" + id + "\" />");
            out.println("              <button class=\"aira-button aira-button--link\" type=\"submit\" aria-label=\""
                    + (removed ? "Restore" : "Remove") + "\">" + (removed ? "Restore" : "×") + "</button>");
            out.println("              <span class=\"initiative-checklist-note\" aria-live=\"polite\"></span>");
            out.println("            </form>");
        }
        out.println("          </td></tr>");
    }

    private static void itemDialog(PrintWriter out, String action, EsChecklistItem item, Map<Long, String> userNames,
            List<EsInitiativeTimelineEntry> timeline, User viewer, boolean canEdit, FormInput input) {
        String id = "item-" + item.getEsChecklistItemId() + "-dialog";
        boolean removed = Boolean.TRUE.equals(item.getRemoved());
        boolean failed = id.equals(input.openDialog());
        InitiativePageRenderer.dialogStart(out, id, item.getTitle(), input);
        out.println("        <h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">" + InitiativeHtml.esc(item.getTitle())
                + "</h3>");
        if (canEdit && !removed) {
            boolean textFailed = failed && (input.failed("save-item") || input.failed("edit-item"));
            out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                    + InitiativeHtml.esc(action) + "\" data-checklist-save>");
            if (textFailed) {
                InitiativePageRenderer.formError(out, input, input.failed("save-item") ? "save-item" : "edit-item");
            }
            out.println("          <input type=\"hidden\" name=\"action\" value=\"save-item\" />");
            out.println("          <input type=\"hidden\" name=\"itemId\" value=\"" + item.getEsChecklistItemId() + "\" />");
            out.println("          <div class=\"aira-field\"><label for=\"" + id + "-title-input\">Title</label>");
            out.println("            <input class=\"aira-input\" id=\"" + id + "-title-input\" name=\"title\" type=\"text\" maxlength=\"500\" required value=\""
                    + InitiativeHtml.esc(textFailed ? input.posted("title") : item.getTitle()) + "\" /></div>");
            out.println("          <div class=\"aira-field\"><label for=\"" + id + "-guidance\">Guidance</label>");
            out.println("            <textarea class=\"aira-textarea\" id=\"" + id + "-guidance\" name=\"guidance\">"
                    + InitiativeHtml.esc(textFailed ? input.posted("guidance") : item.getGuidance()) + "</textarea></div>");
            String dueValue = item.getDueDateOverride() == null ? "" : item.getDueDateOverride().toString();
            out.println("          <div class=\"aira-field\"><label for=\"" + id + "-due\">Due date override</label>");
            out.println("            <input class=\"aira-input\" id=\"" + id + "-due\" name=\"dueDate\" type=\"date\" value=\""
                    + InitiativeHtml.esc(failed && input.failed("save-item") ? input.posted("dueDate") : dueValue)
                    + "\" /></div>");
            out.println("          <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--primary\" type=\"submit\">Save</button></div>");
            out.println("          <span class=\"initiative-checklist-note\" aria-live=\"polite\"></span>");
            out.println("        </form>");
            out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                    + InitiativeHtml.esc(action) + "\" data-checklist-save>");
            if (failed && input.failed("item-status")) {
                InitiativePageRenderer.formError(out, input, "item-status");
            }
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
            out.println("              <input class=\"aira-input\" id=\"" + id + "-note\" name=\"note\" type=\"text\" value=\""
                    + InitiativeHtml.esc(failed && input.failed("item-status") ? input.posted("note") : "")
                    + "\" /></div>");
            out.println("          </div>");
            out.println("          <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--secondary\" type=\"submit\">Update status</button></div>");
            out.println("          <span class=\"initiative-checklist-note\" aria-live=\"polite\"></span>");
            out.println("        </form>");
        } else if (item.getGuidance() != null && !item.getGuidance().isBlank()) {
            out.println("        <p>" + InitiativeHtml.esc(item.getGuidance()) + "</p>");
        }
        InitiativePageRenderer.timeline(out, action, timeline, userNames, viewer, canEdit && !removed,
                EsInitiativeTimelineEntry.SubjectType.CHECKLIST_ITEM, item.getEsChecklistItemId(), input);
        out.println("        <div class=\"aira-dialog__actions aira-form-actions aira-form-actions--end\">");
        out.println("          <button class=\"aira-button aira-button--ghost\" type=\"button\" data-dialog-close=\""
                + id + "\">Close</button></div>");
        out.println("      </dialog>");
    }

    private static void addPhaseDialog(PrintWriter out, String action, FormInput input) {
        InitiativePageRenderer.dialogStart(out, "add-phase", "Add phase", input);
        out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                + InitiativeHtml.esc(action) + "\">");
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

    private static void dueCell(PrintWriter out, LocalDate due, ChecklistDueDates.Urgency urgency, boolean override) {
        out.println("          <td class=\"initiative-checklist-due aira-table__cell--nowrap\">");
        out.println("            <span class=\"initiative-checklist-due-text\">"
                + (due == null ? "" : InitiativePageRenderer.DATE_FORMAT.format(due)) + "</span>");
        if (override) {
            out.println("            <span class=\"aira-meta initiative-checklist-override\">override</span>");
        }
        if (urgency == ChecklistDueDates.Urgency.OVERDUE) {
            out.println("            <span class=\"aira-badge aira-badge--danger initiative-checklist-urgency\">Overdue</span>");
        } else if (urgency == ChecklistDueDates.Urgency.DUE_SOON) {
            out.println("            <span class=\"aira-badge aira-badge--warning initiative-checklist-urgency\">Due soon</span>");
        }
        out.println("          </td>");
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

    private static LocalDate dueDate(EsChecklistItem item, ChecklistDueDates.Anchors anchors) {
        return ChecklistDueDates.effective(item.getDueAnchor(), item.getDueOffsetDays(), item.getDueDateOverride(),
                anchors);
    }

    private static ChecklistDueDates.Urgency urgency(EsChecklistItem item, ChecklistDueDates.Anchors anchors,
            LocalDate today) {
        return ChecklistDueDates.urgency(dueDate(item, anchors), today,
                item.getItemStatus() == null ? null : item.getItemStatus().name());
    }

    private static String query(boolean showRemoved, boolean hideDone) {
        StringBuilder query = new StringBuilder();
        if (showRemoved) {
            query.append("removed=1");
        }
        if (hideDone) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append("hideDone=1");
        }
        return query.length() == 0 ? "" : "?" + query;
    }

    static String kindLabel(String kind) {
        if ("SMALL_GROUP".equals(kind)) {
            return "Small Group";
        }
        if ("INITIATIVE".equals(kind)) {
            return "Initiative";
        }
        return kind == null ? "" : kind;
    }

    static String urgencyName(ChecklistDueDates.Urgency urgency) {
        return urgency == null ? "" : urgency.name();
    }

    static String itemUrgency(EsChecklistItem item, EsInitiative initiative, LocalDate today) {
        return itemUrgency(item, ChecklistDueDates.Anchors.initiative(initiative.getStartDate(), initiative.getEndDate()),
                today);
    }

    static String itemUrgency(EsChecklistItem item, ChecklistDueDates.Anchors anchors, LocalDate today) {
        return urgencyName(urgency(item, anchors, today));
    }

    static String dueText(EsChecklistItem item, EsInitiative initiative) {
        return dueText(item, ChecklistDueDates.Anchors.initiative(initiative.getStartDate(), initiative.getEndDate()));
    }

    static String dueText(EsChecklistItem item, ChecklistDueDates.Anchors anchors) {
        LocalDate due = dueDate(item, anchors);
        return due == null ? "" : InitiativePageRenderer.DATE_FORMAT.format(due);
    }
}
