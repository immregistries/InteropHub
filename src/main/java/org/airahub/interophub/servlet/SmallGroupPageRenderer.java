package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.airahub.interophub.model.EsChecklist;
import org.airahub.interophub.model.EsChecklistItem;
import org.airahub.interophub.model.EsInitiative;
import org.airahub.interophub.model.EsInitiativeManager;
import org.airahub.interophub.model.EsInitiativeMember;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;
import org.airahub.interophub.model.EsSmallGroup;
import org.airahub.interophub.model.EsSmallGroupParticipant;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.ChecklistDueDates;
import org.airahub.interophub.service.ChecklistHints;
import org.airahub.interophub.service.PeopleToBringIn;
import org.airahub.interophub.service.SmallGroupParticipantRules;
import org.airahub.interophub.service.SmallGroupSeriesComparison;

final class SmallGroupPageRenderer {

    private static final String[][] PRODUCT_AREAS = {
            {"GUIDANCE", "Guidance"},
            {"TESTS", "Tests"},
            {"SOFTWARE", "Software"}
    };

    record ParticipantRow(EsSmallGroupParticipant participant, EsInitiativeMember member) {
    }

    private SmallGroupPageRenderer() {
    }

    static void list(PrintWriter out, String contextPath, EsInitiative initiative, List<EsSmallGroup> groups,
            Map<Long, String> topicNames, Map<Long, Integer> participantCounts, List<EsTopic> topics,
            List<EsTopicMeeting> seriesChoices, List<EsSmallGroup> copySources, boolean canEdit, FormInput input) {
        String base = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/small-groups";
        out.println("      <div class=\"aira-page-header\"><div>");
        out.println("        <h1 class=\"aira-page-title\">Small Groups</h1>");
        out.println("      </div>");
        if (canEdit) {
            out.println("        <button class=\"aira-button aira-button--primary\" type=\"button\" data-dialog-open=\"new-small-group\">New Small Group</button>");
        }
        out.println("      </div>");
        if (groups.isEmpty()) {
            out.println("      <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No Small Groups yet.</p></div>");
        } else {
            out.println("      <div class=\"aira-table-wrap\"><table class=\"aira-table\"><thead><tr>");
            out.println("        <th scope=\"col\">Name</th><th scope=\"col\">Topic</th><th scope=\"col\">Status</th>");
            out.println("        <th scope=\"col\">Cycle</th><th scope=\"col\">Participants</th>");
            out.println("      </tr></thead><tbody>");
            for (EsSmallGroup group : groups) {
                String href = base + "/" + group.getEsSmallGroupId();
                out.println("        <tr>");
                out.println("          <th scope=\"row\"><a href=\"" + InitiativeHtml.esc(href) + "\">"
                        + InitiativeHtml.esc(group.getSmallGroupName()) + "</a></th>");
                out.println("          <td>" + InitiativeHtml.esc(topicNames.getOrDefault(group.getEsTopicId(), "")) + "</td>");
                out.println("          <td>" + InitiativePageRenderer.badge(group.getStatus().name(), group.getStatus().getLabel())
                        + "</td>");
                out.println("          <td>" + InitiativeHtml.esc(cycle(group)) + "</td>");
                out.println("          <td>" + participantCounts.getOrDefault(group.getEsSmallGroupId(), 0) + "</td>");
                out.println("        </tr>");
            }
            out.println("      </tbody></table></div>");
        }
        if (canEdit) {
            newDialog(out, contextPath, base, initiative, topics, seriesChoices, copySources, input);
        }
    }

    static void page(PrintWriter out, String contextPath, String base, EsInitiative initiative, EsSmallGroup group,
            EsTopic topic, List<EsTopic> topics, List<EsTopicMeeting> seriesChoices, List<ParticipantRow> participants,
            Map<Long, User> users, Map<Long, String> organizationNames, Set<Long> registeredIds, Set<Long> followingIds,
            PeopleToBringIn.Page bringIn, String bringFilter, String registrationUrl, List<EsSmallGroup> earlierGroups,
            boolean canEdit, ChecklistDueDates.Anchors anchors, EsChecklist checklist,
            List<EsChecklistItem> items, List<EsInitiativeManager> managers, Map<Long, String> userNames,
            Map<Long, List<EsInitiativeTimelineEntry>> itemTimeline, List<EsInitiativeTimelineEntry> checklistTimeline,
            List<EsInitiativeTimelineEntry> groupTimeline, User viewer, ChecklistHints.Facts facts,
            boolean upgradeAvailable, int availableVersion, boolean showRemoved, boolean hideDone, boolean templateAdmin,
            FormInput input) {
        header(out, contextPath, base, group, topic, topics, seriesChoices, canEdit, input);
        participants(out, contextPath, base, initiative, participants, users, organizationNames, registeredIds,
                followingIds, registrationUrl, earlierGroups, canEdit, input);
        bringIn(out, base, initiative, bringIn, bringFilter, canEdit);
        ChecklistPageRenderer.embedded(out, contextPath, base, anchors, checklist, items, managers, userNames,
                itemTimeline, checklistTimeline, viewer, facts, canEdit, upgradeAvailable, availableVersion, showRemoved,
                hideDone, templateAdmin, input);
        out.println("      <section class=\"aira-stack aira-stack--compact\">");
        out.println("        <h2>Small Group history</h2>");
        InitiativePageRenderer.timeline(out, base, groupTimeline, userNames, viewer, canEdit,
                EsInitiativeTimelineEntry.SubjectType.SMALL_GROUP, group.getEsSmallGroupId(), input);
        out.println("      </section>");
    }

    static void upgrade(PrintWriter out, String base, int fromVersion,
            org.airahub.interophub.model.EsChecklistTemplate next,
            org.airahub.interophub.service.ChecklistMerge.Result result, boolean canApply) {
        ChecklistPageRenderer.upgrade(out, base, fromVersion, next, result, canApply,
                "Closed and cancelled Small Groups keep the template they ran with.", "Back to the Small Group");
    }

    private static void header(PrintWriter out, String contextPath, String base, EsSmallGroup group, EsTopic topic,
            List<EsTopic> topics, List<EsTopicMeeting> seriesChoices, boolean canEdit, FormInput input) {
        EsTopicMeeting series = selected(seriesChoices, group.getEsTopicMeetingId());
        out.println("      <div class=\"aira-page-header\"><div>");
        out.println("        <h1 class=\"aira-page-title\">" + InitiativeHtml.esc(group.getSmallGroupName()) + "</h1>");
        out.println("        <p><strong>" + InitiativeHtml.esc(group.getSmallGroupName()) + "</strong> · "
                + InitiativeHtml.esc(group.getStatus().getLabel()));
        if (topic != null) {
            out.println("          · Topic: <a href=\"" + InitiativeHtml.esc(contextPath + "/es/topic/" + topic.getEsTopicId())
                    + "\">" + InitiativeHtml.esc(topic.getTopicName()) + "</a>");
        }
        if (series != null) {
            out.println("          · Series: <a href=\"" + InitiativeHtml.esc(contextPath + "/es/meeting-series?seriesId="
                    + series.getEsTopicMeetingId()) + "\">" + InitiativeHtml.esc(series.getMeetingName()) + "</a>");
        }
        out.println("        </p>");
        out.println("        <p>" + InitiativeHtml.esc(summaryLine(group)));
        if (group.getCharterUrl() != null && !group.getCharterUrl().isBlank()) {
            out.println("          · <a href=\"" + InitiativeHtml.esc(group.getCharterUrl()) + "\">Charter</a>");
        }
        out.println("        </p>");
        if (group.getBreakNote() != null && !group.getBreakNote().isBlank()) {
            out.println("        <p>" + InitiativeHtml.esc(group.getBreakNote()) + "</p>");
        }
        if (group.getDescription() != null && !group.getDescription().isBlank()) {
            out.println("        <p>" + InitiativeHtml.esc(group.getDescription()) + "</p>");
        }
        out.println("      </div>");
        if (canEdit) {
            out.println("        <div class=\"aira-cluster\">");
            out.println("          <button class=\"aira-button aira-button--secondary\" type=\"button\" data-dialog-open=\"edit-details\">Edit details</button>");
            out.println("          <button class=\"aira-button aira-button--secondary\" type=\"button\" data-dialog-open=\"group-status\">Change status</button>");
            out.println("        </div>");
        }
        out.println("      </div>");
        if (canEdit) {
            detailsDialog(out, base, group, topics, seriesChoices, input);
            statusDialog(out, group, input);
        }
    }

    private static void detailsDialog(PrintWriter out, String base, EsSmallGroup group, List<EsTopic> topics,
            List<EsTopicMeeting> seriesChoices, FormInput input) {
        String id = "edit-details";
        InitiativePageRenderer.dialogStart(out, id, "Edit details", input);
        out.println("        <h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">Edit details</h3>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(base)
                + "\" data-series-form data-context=\"" + InitiativeHtml.esc(contextOf(base)) + "\">");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"save-small-group\" />");
        InitiativePageRenderer.formError(out, input, "save-small-group");
        textField(out, "groupName", "name", "Name", input.value("save-small-group", "name", group.getSmallGroupName()), 200, true);
        topicSelect(out, "groupTopicId", "topicId", topics, input.value("save-small-group", "topicId", String.valueOf(group.getEsTopicId())));
        seriesSelect(out, "groupSeriesId", seriesChoices, input.value("save-small-group", "seriesId",
                group.getEsTopicMeetingId() == null ? "" : String.valueOf(group.getEsTopicMeetingId())));
        out.println("          <p class=\"aira-meta\" data-series-empty hidden>This topic has no meeting series. <a data-series-create href=\"#\">Create a series</a> for the topic, then choose it here.</p>");
        dateRow(out, "groupCycleStart", "cycleStartDate", "Cycle start", input.value("save-small-group", "cycleStartDate", iso(group.getCycleStartDate())),
                "groupWorkingDay", "workingDayDate", "Working day", input.value("save-small-group", "workingDayDate", iso(group.getWorkingDayDate())),
                "groupCloseout", "closeoutDate", "Closeout", input.value("save-small-group", "closeoutDate", iso(group.getCloseoutDate())));
        areas(out, "save-small-group", group.getPrimaryProductAreas(), input);
        textField(out, "groupCharter", "charterUrl", "Charter URL", input.value("save-small-group", "charterUrl", group.getCharterUrl()), 2048, false);
        areaField(out, "groupBreak", "breakNote", "Break", input.value("save-small-group", "breakNote", group.getBreakNote()));
        areaField(out, "groupDescription", "description", "Description", input.value("save-small-group", "description", group.getDescription()));
        InitiativePageRenderer.dialogActions(out, id, "Save details");
        out.println("        </form></dialog>");
    }

    private static void participants(PrintWriter out, String contextPath, String base, EsInitiative initiative,
            List<ParticipantRow> participants, Map<Long, User> users, Map<Long, String> organizationNames,
            Set<Long> registeredIds, Set<Long> followingIds, String registrationUrl, List<EsSmallGroup> earlierGroups,
            boolean canEdit, FormInput input) {
        String peopleUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/people/";
        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-panel__header\">");
        out.println("        <h2 class=\"aira-table-panel__title\">Participants</h2>");
        if (canEdit) {
            out.println("        <div class=\"aira-table-panel__actions\">");
            out.println("          <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"button\" data-dialog-open=\"add-participant\">Add participant</button>");
            if (!earlierGroups.isEmpty()) {
                out.println("          <button class=\"aira-button aira-button--small aira-button--ghost\" type=\"button\" data-dialog-open=\"copy-participants\">Copy participants</button>");
            }
            if (registrationUrl != null) {
                out.println("          <button class=\"aira-button aira-button--small aira-button--ghost\" type=\"button\" data-copy-value=\""
                        + attr(registrationUrl) + "\">Copy registration link for everyone not registered</button>");
            }
            out.println("        </div>");
        }
        out.println("      </div>");
        if (participants.isEmpty()) {
            out.println("        <p class=\"aira-meta\">No participants yet. People are added here directly. They do not need to sign in.</p>");
        } else {
            out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table aira-table--compact\"><thead><tr>");
            out.println("          <th scope=\"col\">Name</th><th scope=\"col\">Organization</th><th scope=\"col\">Email</th>");
            out.println("          <th scope=\"col\">Role or team</th><th scope=\"col\">Registered</th><th scope=\"col\">Follows topic</th>");
            if (canEdit) {
                out.println("          <th scope=\"col\"><span class=\"aira-visually-hidden\">Remove</span></th>");
            }
            out.println("        </tr></thead><tbody>");
            for (ParticipantRow row : participants) {
                EsInitiativeMember member = row.member();
                String name = InitiativePageRenderer.personLabel(member, users);
                Long memberId = member == null ? null : member.getEsInitiativeMemberId();
                boolean registered = memberId != null && registeredIds != null && registeredIds.contains(memberId);
                String email = member == null ? "" : member.getEmail();
                String organization = member == null || organizationNames == null ? ""
                        : organizationNames.getOrDefault(member.getEsInitiativeOrganizationId(), "");
                out.println("          <tr><th scope=\"row\" class=\"aira-table__cell--primary\">");
                if (memberId != null) {
                    out.println("            <a href=\"" + InitiativeHtml.esc(peopleUrl + memberId) + "\">"
                            + InitiativeHtml.esc(name) + "</a>");
                } else {
                    out.println("            " + InitiativeHtml.esc(name));
                }
                out.println("          </th>");
                out.println("            <td>" + InitiativeHtml.esc(organization) + "</td>");
                out.println("            <td>" + InitiativeHtml.esc(email) + "</td><td>");
                String role = row.participant().getParticipantRole();
                if (canEdit) {
                    out.println("              <form method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\" data-inline-role>");
                    out.println("                <input type=\"hidden\" name=\"action\" value=\"update-role\" />");
                    out.println("                <input type=\"hidden\" name=\"participantId\" value=\""
                            + row.participant().getEsSmallGroupParticipantId() + "\" />");
                    out.println("                <button class=\"initiative-checklist-text\" type=\"button\" data-inline-display>"
                            + InitiativeHtml.esc(role == null || role.isBlank() ? "Add role" : role) + "</button>");
                    out.println("                <input class=\"aira-input\" name=\"role\" type=\"text\" maxlength=\"200\" hidden value=\""
                            + InitiativeHtml.esc(role) + "\" />");
                    out.println("              </form>");
                } else {
                    out.println("              " + InitiativeHtml.esc(role));
                }
                out.println("            </td>");
                out.println("            <td>" + (registered ? "✓" : "—"));
                if (!registered && canEdit && registrationUrl != null) {
                    out.println("              <button class=\"aira-button aira-button--link\" type=\"button\" data-copy-value=\""
                            + attr(SmallGroupSeriesComparison.copyText(name, registrationUrl))
                            + "\">Copy link</button>");
                }
                out.println("            </td>");
                boolean follows = memberId != null && followingIds != null && followingIds.contains(memberId);
                out.println("            <td>" + (follows ? "✓" : "—") + "</td>");
                if (canEdit) {
                    out.println("            <td><button class=\"aira-button aira-button--link\" type=\"button\" data-dialog-open=\"remove-participant\" data-record-id=\""
                            + row.participant().getEsSmallGroupParticipantId() + "\" data-dialog-title=\"Remove "
                            + InitiativeHtml.esc(name) + "\" aria-label=\"Remove " + InitiativeHtml.esc(name)
                            + "\">×</button></td>");
                }
                out.println("          </tr>");
            }
            out.println("        </tbody></table></div>");
        }
        out.println("      </section>");
        if (canEdit) {
            addDialog(out, contextPath, base, initiative, input);
            if (!earlierGroups.isEmpty()) {
                copyDialog(out, base, earlierGroups);
            }
            removeDialog(out, base, input);
        }
    }

    private static void bringIn(PrintWriter out, String base, EsInitiative initiative, PeopleToBringIn.Page page,
            String filter, boolean canEdit) {
        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-panel__header\"><div>");
        out.println("        <h2 class=\"aira-table-panel__title\">People to bring in</h2>");
        if (page != null) {
            out.println("        <p class=\"aira-table-panel__description\">" + page.total() + " people · "
                    + page.registeredCount() + " registered · " + page.followsCount() + " follow the topic · "
                    + page.onInitiativeCount() + " on the initiative</p>");
        }
        out.println("      </div></div>");
        out.println("      <div class=\"aira-cluster\" role=\"group\" aria-label=\"Filter people to bring in\">");
        filterLink(out, base, filter, "", "All");
        filterLink(out, base, filter, "registered", "Registered");
        filterLink(out, base, filter, "follows", "Follows topic");
        filterLink(out, base, filter, "initiative", "On initiative");
        out.println("      </div>");
        if (page == null || page.filteredTotal() == 0) {
            out.println("      <p class=\"aira-meta\">No one else to bring in.</p></section>");
            return;
        }
        out.println("      <div class=\"aira-table-wrap\"><table class=\"aira-table aira-table--compact\"><thead><tr>");
        out.println("        <th scope=\"col\">Name</th><th scope=\"col\">Organization</th><th scope=\"col\">On initiative</th>");
        out.println("        <th scope=\"col\">Registered for series</th><th scope=\"col\">Follows topic</th>");
        if (canEdit) {
            out.println("        <th scope=\"col\"><span class=\"aira-visually-hidden\">Add</span></th>");
        }
        out.println("      </tr></thead><tbody>");
        for (PeopleToBringIn.Row row : page.rows()) {
            out.println("        <tr><th scope=\"row\" class=\"aira-table__cell--primary\">" + InitiativeHtml.esc(row.name())
                    + "</th>");
            out.println("          <td>" + InitiativeHtml.esc(row.organization()) + "</td>");
            out.println("          <td>" + InitiativeHtml.esc(row.initiativeStatus()) + "</td>");
            out.println("          <td>" + InitiativeHtml.esc(row.registrationLabel()) + "</td>");
            out.println("          <td>" + (row.follows() ? "✓" : "—") + "</td>");
            if (canEdit) {
                out.println("          <td><form method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
                out.println("            <input type=\"hidden\" name=\"action\" value=\"add-participant\" />");
                out.println("            <input type=\"hidden\" name=\"personSource\" value=\""
                        + (row.memberId() != null ? "ROSTER" : row.userId() != null ? "ACCOUNT" : "KNOWN") + "\" />");
                out.println("            <input type=\"hidden\" name=\"memberId\" value=\""
                        + (row.memberId() == null ? "" : row.memberId()) + "\" />");
                out.println("            <input type=\"hidden\" name=\"userId\" value=\""
                        + (row.userId() == null ? "" : row.userId()) + "\" />");
                out.println("            <input type=\"hidden\" name=\"email\" value=\"" + InitiativeHtml.esc(row.email())
                        + "\" />");
                out.println("            <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"submit\">Add</button>");
                out.println("          </form></td>");
            }
            out.println("        </tr>");
        }
        out.println("      </tbody></table></div>");
        if (page.pages() > 1) {
            out.println("      <p class=\"aira-cluster\">");
            for (int number = 1; number <= page.pages(); number++) {
                String href = base + bringQuery(filter, number);
                if (number == page.page()) {
                    out.println("        <span>" + number + "</span>");
                } else {
                    out.println("        <a href=\"" + InitiativeHtml.esc(href) + "\">" + number + "</a>");
                }
            }
            out.println("      </p>");
        }
        out.println("      </section>");
    }

    private static void filterLink(PrintWriter out, String base, String current, String value, String label) {
        boolean on = (value.isEmpty() && (current == null || current.isBlank())) || value.equals(current);
        out.println("        <a class=\"aira-button aira-button--small "
                + (on ? "aira-button--secondary" : "aira-button--ghost") + "\" href=\""
                + InitiativeHtml.esc(base + bringQuery(value, 1)) + "\"" + (on ? " aria-current=\"true\"" : "") + ">"
                + InitiativeHtml.esc(label) + "</a>");
    }

    private static String bringQuery(String filter, int page) {
        StringBuilder query = new StringBuilder("?");
        if (filter != null && !filter.isBlank()) {
            query.append("bringIn=").append(filter).append('&');
        }
        query.append("bringPage=").append(page);
        return query.toString();
    }

    private static void newDialog(PrintWriter out, String contextPath, String base, EsInitiative initiative,
            List<EsTopic> topics, List<EsTopicMeeting> seriesChoices, List<EsSmallGroup> copySources, FormInput input) {
        String id = "new-small-group";
        InitiativePageRenderer.dialogStart(out, id, "New Small Group", input);
        out.println("        <h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">New Small Group</h3>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(base)
                + "\" data-series-form data-context=\"" + InitiativeHtml.esc(contextPath) + "\">");
        InitiativePageRenderer.formError(out, input, "create-small-group");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"create-small-group\" />");
        textField(out, "newGroupName", "name", "Name", input.value("create-small-group", "name", ""), 200, true);
        String defaultTopic = initiative.getPrimaryEsTopicId() == null ? "" : String.valueOf(initiative.getPrimaryEsTopicId());
        topicSelect(out, "newTopicId", "topicId", topics, input.value("create-small-group", "topicId", defaultTopic));
        seriesSelect(out, "newSeriesId", seriesChoices, input.value("create-small-group", "seriesId", ""));
        out.println("          <p class=\"aira-meta\" data-series-empty hidden>This topic has no meeting series. <a data-series-create href=\"#\">Create a series</a>, then come back and choose it. A Small Group can be saved without one.</p>");
        statusSelect(out, "newStatus", input.value("create-small-group", "status", EsSmallGroup.Status.PLANNING.name()));
        dateRow(out, "newCycleStart", "cycleStartDate", "Cycle start", input.value("create-small-group", "cycleStartDate", ""),
                "newWorkingDay", "workingDayDate", "Working day", input.value("create-small-group", "workingDayDate", ""),
                "newCloseout", "closeoutDate", "Closeout", input.value("create-small-group", "closeoutDate", ""));
        areas(out, "create-small-group", null, input);
        textField(out, "newCharter", "charterUrl", "Charter URL", input.value("create-small-group", "charterUrl", ""), 2048, false);
        areaField(out, "newDescription", "description", "Description", input.value("create-small-group", "description", ""));
        if (!copySources.isEmpty()) {
            out.println("          <div class=\"aira-field\"><label for=\"copyFromId\">Copy participants from</label>");
            out.println("            <select class=\"aira-select\" id=\"copyFromId\" name=\"copyFromId\">");
            out.println("              <option value=\"\">Do not copy</option>");
            for (EsSmallGroup source : copySources) {
                String selected = String.valueOf(source.getEsSmallGroupId())
                        .equals(input.value("create-small-group", "copyFromId", "")) ? " selected" : "";
                out.println("              <option value=\"" + source.getEsSmallGroupId() + "\"" + selected + ">"
                        + InitiativeHtml.esc(source.getSmallGroupName()) + "</option>");
            }
            out.println("            </select></div>");
        }
        InitiativePageRenderer.dialogActions(out, id, "Create Small Group");
        out.println("        </form></dialog>");
    }

    private static void copyDialog(PrintWriter out, String base, List<EsSmallGroup> earlierGroups) {
        String id = "copy-participants";
        InitiativePageRenderer.dialogStart(out, id, "Copy participants", FormInput.none());
        out.println("        <h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">Copy participants</h3>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"copy-participants\" />");
        out.println("          <div class=\"aira-field\"><label for=\"copyFromId\">Copy participants from an earlier Small Group</label>");
        out.println("            <select class=\"aira-select\" id=\"copyFromId\" name=\"copyFromId\" required>");
        out.println("              <option value=\"\">Choose a Small Group</option>");
        for (EsSmallGroup earlier : earlierGroups) {
            out.println("              <option value=\"" + earlier.getEsSmallGroupId() + "\">"
                    + InitiativeHtml.esc(earlier.getSmallGroupName()) + "</option>");
        }
        out.println("            </select></div>");
        InitiativePageRenderer.dialogActions(out, id, "Copy participants");
        out.println("        </form></dialog>");
    }

    private static void addDialog(PrintWriter out, String contextPath, String base, EsInitiative initiative,
            FormInput input) {
        String id = "add-participant";
        InitiativePageRenderer.dialogStart(out, id, "Add participant", input, true);
        out.println("        <h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">Add participant</h3>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        InitiativePageRenderer.formError(out, input, "add-participant");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"add-participant\" />");
        InitiativePageRenderer.personSearch(out, contextPath, initiative.getInitiativeCode(), "participant", input,
                "add-participant", true);
        InitiativePageRenderer.organizationField(out, contextPath, initiative.getInitiativeCode(), "participant", input,
                "add-participant", "", null);
        textField(out, "participantRole", "role", "Role or team", input.value("add-participant", "role", ""), 200, false);
        out.println("          <p class=\"aira-meta\">Someone who is not on the roster is added as joined. This does not send email.</p>");
        InitiativePageRenderer.dialogActions(out, id, "Add");
        out.println("        </form></dialog>");
    }


    private static void removeDialog(PrintWriter out, String base, FormInput input) {
        String id = "remove-participant";
        InitiativePageRenderer.dialogStart(out, id, "Remove participant", input);
        out.println("        <h3 class=\"aira-dialog__title\" id=\"" + id + "-title\" data-dialog-heading>Remove participant</h3>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        InitiativePageRenderer.formError(out, input, "remove-participant");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"remove-participant\" />");
        out.println("          <input data-record-id type=\"hidden\" name=\"participantId\" value=\""
                + InitiativeHtml.esc(input.value("remove-participant", "participantId", "")) + "\" />");
        textField(out, "removeNote", "note", "Note", input.value("remove-participant", "note", ""), 500, false);
        out.println("          <p class=\"aira-meta\">Removing someone leaves their topic follows as they are.</p>");
        InitiativePageRenderer.dialogActions(out, id, "Remove");
        out.println("        </form></dialog>");
    }

    private static void statusDialog(PrintWriter out, EsSmallGroup group, FormInput input) {
        String id = "group-status";
        InitiativePageRenderer.dialogStart(out, id, "Change status", input);
        out.println("        <h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">Change status</h3>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"\">");
        InitiativePageRenderer.formError(out, input, "group-status");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"group-status\" />");
        statusSelect(out, "groupStatus", input.value("group-status", "status", group.getStatus().name()));
        textField(out, "statusNote", "note", "Note", input.value("group-status", "note", ""), 500, false);
        InitiativePageRenderer.dialogActions(out, id, "Update status");
        out.println("        </form></dialog>");
    }

    private static void topicSelect(PrintWriter out, String id, String name, List<EsTopic> topics, String selected) {
        out.println("          <div class=\"aira-field\"><label for=\"" + id + "\">Topic</label>");
        out.println("            <select class=\"aira-select\" id=\"" + id + "\" name=\"" + name
                + "\" data-topic-select required>");
        for (EsTopic topic : topics) {
            String mark = String.valueOf(topic.getEsTopicId()).equals(selected) ? " selected" : "";
            out.println("              <option value=\"" + topic.getEsTopicId() + "\"" + mark + ">"
                    + InitiativeHtml.esc(topic.getTopicName()) + "</option>");
        }
        out.println("            </select></div>");
    }

    private static void seriesSelect(PrintWriter out, String id, List<EsTopicMeeting> seriesChoices, String selected) {
        out.println("          <div class=\"aira-field\"><label for=\"" + id + "\">Meeting series</label>");
        out.println("            <select class=\"aira-select\" id=\"" + id + "\" name=\"seriesId\" data-series-select>");
        out.println("              <option value=\"\">No series yet</option>");
        for (EsTopicMeeting series : seriesChoices) {
            String mark = String.valueOf(series.getEsTopicMeetingId()).equals(selected) ? " selected" : "";
            out.println("              <option value=\"" + series.getEsTopicMeetingId() + "\" data-topic-id=\""
                    + series.getEsTopicId() + "\"" + mark + ">" + InitiativeHtml.esc(series.getMeetingName())
                    + "</option>");
        }
        out.println("            </select></div>");
    }

    private static void statusSelect(PrintWriter out, String id, String selected) {
        out.println("          <div class=\"aira-field\"><label for=\"" + id + "\">Status</label>");
        out.println("            <select class=\"aira-select\" id=\"" + id + "\" name=\"status\" required>");
        for (EsSmallGroup.Status status : EsSmallGroup.Status.values()) {
            String mark = status.name().equals(selected) ? " selected" : "";
            out.println("              <option value=\"" + status.name() + "\"" + mark + ">"
                    + InitiativeHtml.esc(status.getLabel()) + "</option>");
        }
        out.println("            </select></div>");
    }

    private static void areas(PrintWriter out, String action, String stored, FormInput input) {
        List<String> chosen = SmallGroupParticipantRules.splitProductAreas(
                input.failed(action) ? input.posted("productArea") : stored);
        out.println("          <fieldset class=\"aira-fieldset\"><legend>Product areas</legend>");
        for (String[] area : PRODUCT_AREAS) {
            boolean on = chosen.contains(area[0]);
            out.println("            <label class=\"aira-check\"><input type=\"checkbox\" name=\"productArea\" value=\""
                    + area[0] + "\"" + (on ? " checked" : "") + " /><span>" + area[1] + "</span></label>");
        }
        out.println("          </fieldset>");
    }

    private static void textField(PrintWriter out, String id, String name, String label, String value, int maxLength,
            boolean required) {
        String type = "charterUrl".equals(name) ? "url" : "text";
        out.println("          <div class=\"aira-field\"><label for=\"" + id + "\">" + label + "</label>");
        out.println("            <input class=\"aira-input\" id=\"" + id + "\" name=\"" + name + "\" type=\"" + type
                + "\"" + (required ? " required" : "") + " maxlength=\"" + maxLength + "\" value=\""
                + InitiativeHtml.esc(value) + "\" /></div>");
    }

    private static void areaField(PrintWriter out, String id, String name, String label, String value) {
        out.println("          <div class=\"aira-field\"><label for=\"" + id + "\">" + label + "</label>");
        out.println("            <textarea class=\"aira-textarea\" id=\"" + id + "\" name=\"" + name + "\">"
                + InitiativeHtml.esc(value) + "</textarea></div>");
    }

    private static void dateRow(PrintWriter out, String id1, String name1, String label1, String value1, String id2,
            String name2, String label2, String value2, String id3, String name3, String label3, String value3) {
        out.println("          <div class=\"aira-field-row\">");
        dateField(out, id1, name1, label1, value1);
        dateField(out, id2, name2, label2, value2);
        dateField(out, id3, name3, label3, value3);
        out.println("          </div>");
    }

    private static void dateField(PrintWriter out, String id, String name, String label, String value) {
        out.println("            <div class=\"aira-field\"><label for=\"" + id + "\">" + label + "</label>");
        out.println("              <input class=\"aira-input\" id=\"" + id + "\" name=\"" + name
                + "\" type=\"date\" value=\"" + InitiativeHtml.esc(value) + "\" /></div>");
    }

    private static EsTopicMeeting selected(List<EsTopicMeeting> seriesChoices, Long seriesId) {
        if (seriesId == null) {
            return null;
        }
        for (EsTopicMeeting series : seriesChoices) {
            if (seriesId.equals(series.getEsTopicMeetingId())) {
                return series;
            }
        }
        return null;
    }

    private static String summaryLine(EsSmallGroup group) {
        StringBuilder line = new StringBuilder();
        String dates = cycle(group);
        if (!"Dates not set".equals(dates)) {
            line.append("Cycle ").append(dates);
        }
        if (group.getWorkingDayDate() != null) {
            if (line.length() > 0) {
                line.append(" · ");
            }
            line.append("Working day ").append(InitiativePageRenderer.DATE_FORMAT.format(group.getWorkingDayDate()));
        }
        String areas = areaLabels(group.getPrimaryProductAreas());
        if (!areas.isEmpty()) {
            if (line.length() > 0) {
                line.append(" · ");
            }
            line.append(areas);
        }
        return line.toString();
    }

    private static String cycle(EsSmallGroup group) {
        String start = group.getCycleStartDate() == null ? ""
                : InitiativePageRenderer.DATE_FORMAT.format(group.getCycleStartDate());
        String end = group.getCloseoutDate() == null ? ""
                : InitiativePageRenderer.DATE_FORMAT.format(group.getCloseoutDate());
        if (start.isEmpty() && end.isEmpty()) {
            return "Dates not set";
        }
        if (start.isEmpty()) {
            return "Through " + end;
        }
        if (end.isEmpty()) {
            return "From " + start;
        }
        return start + " – " + end;
    }

    private static String areaLabels(String stored) {
        List<String> codes = SmallGroupParticipantRules.splitProductAreas(stored);
        if (codes.isEmpty()) {
            return "";
        }
        StringBuilder labels = new StringBuilder();
        for (String code : codes) {
            if (labels.length() > 0) {
                labels.append(", ");
            }
            labels.append(labelOf(code));
        }
        return labels.toString();
    }

    private static String labelOf(String code) {
        for (String[] area : PRODUCT_AREAS) {
            if (area[0].equals(code)) {
                return area[1];
            }
        }
        return code;
    }

    private static String iso(java.time.LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static String contextOf(String base) {
        int hub = base.indexOf("/es/initiatives/");
        return hub < 0 ? "" : base.substring(0, hub);
    }

    private static String attr(String value) {
        return InitiativeHtml.esc(value).replace("\n", "&#10;").replace("\r", "");
    }
}
