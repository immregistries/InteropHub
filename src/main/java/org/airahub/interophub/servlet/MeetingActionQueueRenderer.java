package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.airahub.interophub.model.MeetingAction;

/**
 * Renders the shared meeting-cadence action queue on the authenticated
 * welcome page (docs/interophub-meeting-cadence-design.md's "authenticated
 * home page"): Needs attention / Upcoming / Snoozed sections, each backed by
 * MeetingActionQueueService.getVisibleMeetingActions - never rendered from
 * separately stored completion state, so the queue can't drift from reality.
 */
final class MeetingActionQueueRenderer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    private MeetingActionQueueRenderer() {
    }

    static void render(PrintWriter out, String contextPath, List<MeetingAction> actions, LocalDateTime nowUtc) {
        if (actions.isEmpty()) {
            return;
        }
        List<MeetingAction> needsAttention = new ArrayList<>();
        List<MeetingAction> upcoming = new ArrayList<>();
        List<MeetingAction> snoozed = new ArrayList<>();
        for (MeetingAction action : actions) {
            if (action.isSnoozed(nowUtc)) {
                snoozed.add(action);
            } else if (action.isOverdue()) {
                needsAttention.add(action);
            } else {
                upcoming.add(action);
            }
        }

        out.println("          <section id=\"meeting-action-queue\" class=\"aira-stack aira-stack--compact\">");
        renderGroup(out, contextPath, "Needs attention", "aira-table-panel--danger", needsAttention, true, nowUtc);
        renderGroup(out, contextPath, "Upcoming", null, upcoming, false, nowUtc);
        renderGroup(out, contextPath, "Snoozed", null, snoozed, false, nowUtc);
        out.println("          </section>");
    }

    private static void renderGroup(PrintWriter out, String contextPath, String title, String accentClass,
            List<MeetingAction> group, boolean showSnoozeControls, LocalDateTime nowUtc) {
        if (group.isEmpty()) {
            return;
        }
        String panelClass = accentClass == null ? "aira-table-panel" : "aira-table-panel " + accentClass;
        out.println("            <div class=\"" + panelClass + "\">");
        out.println("              <div class=\"aira-table-panel__header\">");
        out.println("                <div><h2 class=\"aira-table-panel__title\">" + title
                + "</h2></div>");
        out.println("              </div>");
        out.println("              <div class=\"aira-table-panel__body\">");
        out.println("                <div class=\"aira-choice-list\">");
        for (MeetingAction action : group) {
            renderRow(out, contextPath, action, nowUtc);
        }
        out.println("                </div>");
        out.println("              </div>");
        out.println("            </div>");
    }

    private static void renderRow(PrintWriter out, String contextPath, MeetingAction action, LocalDateTime nowUtc) {
        out.println("                  <div class=\"aira-choice-row\">");
        out.println("                    <div class=\"aira-choice-row__control\">");
        if (action.isUnread()) {
            out.println(
                    "                      <span class=\"aira-status-dot\" style=\"color: var(--aira-info);\" title=\"Unread\" aria-hidden=\"true\"></span>");
        }
        out.println("                    </div>");
        out.println("                    <div>");
        out.println("                      <p class=\"aira-choice-row__title\">" + escapeHtml(action.getInstruction())
                + "</p>");
        out.println("                      <p class=\"aira-choice-row__meta\">" + escapeHtml(action.getMeetingName())
                + "</p>");
        out.println("                      <p class=\"aira-choice-row__meta\">" + dueBadge(action, nowUtc) + "</p>");
        out.println("                      <div class=\"aira-choice-row__actions aira-inline-form\">");
        out.println("                        <a class=\"aira-button aira-button--primary aira-button--small\" href=\""
                + contextPath + action.getHref() + "\">" + linkLabel(action) + "</a>");
        renderStateForm(out, contextPath, action, "read", "Mark read", action.isUnread());
        if (action.isSnoozed(nowUtc)) {
            renderStateForm(out, contextPath, action, "unsnooze", "Unsnooze", true);
        } else {
            renderStateForm(out, contextPath, action, "snoozeTomorrow", "Snooze until tomorrow", true);
            renderStateForm(out, contextPath, action, "snoozeNextWeek", "Snooze until next week", true);
        }
        out.println("                      </div>");
        out.println("                    </div>");
        out.println("                  </div>");
    }

    private static void renderStateForm(PrintWriter out, String contextPath, MeetingAction action, String op,
            String label, boolean show) {
        if (!show) {
            return;
        }
        out.println("                        <form class=\"aira-inline-form\" method=\"post\" action=\""
                + contextPath + "/es/meeting-action\">");
        out.println("                          <input type=\"hidden\" name=\"meetingId\" value=\""
                + action.getEsMeetingId() + "\">");
        out.println("                          <input type=\"hidden\" name=\"actionType\" value=\""
                + action.getActionType() + "\">");
        out.println("                          <input type=\"hidden\" name=\"op\" value=\"" + op + "\">");
        out.println(
                "                          <button type=\"submit\" class=\"aira-button aira-button--tertiary aira-button--small\">"
                        + escapeHtml(label) + "</button>");
        out.println("                        </form>");
    }

    private static String linkLabel(MeetingAction action) {
        return switch (action.getActionType()) {
            case PUBLISH_PROPOSED_AGENDA, FINALIZE_AGENDA -> "Open agenda";
            case CLOSE_MEETING, PUBLISH_NOTES -> "Open meeting workspace";
        };
    }

    private static String dueBadge(MeetingAction action, LocalDateTime nowUtc) {
        if (action.isSnoozed(nowUtc)) {
            return "<span class=\"aira-badge aira-badge--subtle\">Snoozed until " + formatDate(action.getSnoozedUntil())
                    + "</span>";
        }
        if (action.isOverdue()) {
            return "<span class=\"aira-badge aira-badge--danger\">Overdue since " + formatDate(action.getDueAt())
                    + "</span>";
        }
        return "<span class=\"aira-badge aira-badge--info\">Due " + formatDate(action.getDueAt()) + "</span>";
    }

    /**
     * MeetingAction.dueAt is a wall-clock value in the meeting's own timezone
     * (see MeetingActionQueueService); MeetingAction doesn't carry that timezone
     * id, so rather than falsely relabeling it into another zone this renders
     * the date portion as-is - a reasonable MVP display since the meeting's own
     * time is what a reader familiar with that meeting would expect. Converting
     * per-viewer (like MeetingTimeFormatter does for scheduledStart) is a
     * possible later refinement, not required for correctness here.
     */
    private static String formatDate(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_FMT);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
