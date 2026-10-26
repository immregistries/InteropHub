package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            List<EsInitiativeMember> rosterChoices, SmallGroupSeriesComparison.Result comparison, String registrationUrl,
            List<EsSmallGroup> earlierGroups, boolean canEdit, ChecklistDueDates.Anchors anchors, EsChecklist checklist,
            List<EsChecklistItem> items, List<EsInitiativeManager> managers, Map<Long, String> userNames,
            Map<Long, List<EsInitiativeTimelineEntry>> itemTimeline, List<EsInitiativeTimelineEntry> checklistTimeline,
            List<EsInitiativeTimelineEntry> groupTimeline, User viewer, ChecklistHints.Facts facts,
            boolean upgradeAvailable, int availableVersion, boolean showRemoved, boolean hideDone, boolean templateAdmin,
            FormInput input) {
        header(out, contextPath, group, topic, seriesChoices, canEdit, input);
        details(out, base, group, topics, seriesChoices, canEdit, input);
        participants(out, contextPath, base, initiative, participants, rosterChoices, canEdit, input);
        comparison(out, base, comparison, participants, registrationUrl, canEdit);
        if (canEdit && !earlierGroups.isEmpty()) {
            copyForm(out, base, earlierGroups);
        }
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

    private static void header(PrintWriter out, String contextPath, EsSmallGroup group, EsTopic topic,
            List<EsTopicMeeting> seriesChoices, boolean canEdit, FormInput input) {
        out.println("      <div class=\"aira-page-header\"><div>");
        out.println("        <h1 class=\"aira-page-title\">" + InitiativeHtml.esc(group.getSmallGroupName()) + "</h1>");
        out.println("        <p class=\"aira-meta\">");
        out.println("          " + InitiativePageRenderer.badge(group.getStatus().name(), group.getStatus().getLabel()));
        if (topic != null) {
            out.println("          <a href=\"" + InitiativeHtml.esc(contextPath + "/es/topic/" + topic.getEsTopicId())
                    + "\">" + InitiativeHtml.esc(topic.getTopicName()) + "</a>");
        }
        EsTopicMeeting series = selected(seriesChoices, group.getEsTopicMeetingId());
        if (series != null) {
            out.println("          · <a href=\"" + InitiativeHtml.esc(contextPath + "/es/meeting-series?seriesId="
                    + series.getEsTopicMeetingId()) + "\">" + InitiativeHtml.esc(series.getMeetingName()) + "</a>");
        }
        if (group.getCharterUrl() != null && !group.getCharterUrl().isBlank()) {
            out.println("          · <a href=\"" + InitiativeHtml.esc(group.getCharterUrl()) + "\">Charter</a>");
        }
        out.println("        </p>");
        out.println("      </div>");
        if (canEdit) {
            out.println("        <button class=\"aira-button aira-button--secondary\" type=\"button\" data-dialog-open=\"group-status\">Change status</button>");
        }
        out.println("      </div>");
        if (canEdit) {
            statusDialog(out, group, input);
        }
    }

    private static void details(PrintWriter out, String base, EsSmallGroup group, List<EsTopic> topics,
            List<EsTopicMeeting> seriesChoices, boolean canEdit, FormInput input) {
        out.println("      <section class=\"aira-stack aira-stack--compact\">");
        out.println("        <h2>Cycle</h2>");
        if (!canEdit) {
            out.println("        <p>" + InitiativeHtml.esc(cycle(group)) + "</p>");
            if (group.getBreakNote() != null && !group.getBreakNote().isBlank()) {
                out.println("        <p>" + InitiativeHtml.esc(group.getBreakNote()) + "</p>");
            }
            if (group.getDescription() != null && !group.getDescription().isBlank()) {
                out.println("        <p>" + InitiativeHtml.esc(group.getDescription()) + "</p>");
            }
            out.println("        <p class=\"aira-meta\">" + InitiativeHtml.esc(areaLabels(group.getPrimaryProductAreas()))
                    + "</p>");
            out.println("      </section>");
            return;
        }
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
        out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\">Save details</button>");
        out.println("        </form></section>");
    }

    private static void participants(PrintWriter out, String contextPath, String base, EsInitiative initiative,
            List<ParticipantRow> participants, List<EsInitiativeMember> rosterChoices, boolean canEdit, FormInput input) {
        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-panel__header\">");
        out.println("        <h2 class=\"aira-table-panel__title\">Participants</h2>");
        if (canEdit) {
            out.println("        <div class=\"aira-table-panel__actions\">");
            out.println("          <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"button\" data-dialog-open=\"add-participant\">Add participant</button>");
            out.println("          <button class=\"aira-button aira-button--small aira-button--ghost\" type=\"button\" data-dialog-open=\"add-new-participant\">Add new person</button>");
            out.println("        </div>");
        }
        out.println("      </div>");
        if (participants.isEmpty()) {
            out.println("        <p class=\"aira-meta\">No participants yet. People are added here directly. They do not need to sign in.</p>");
        } else {
            out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table\"><thead><tr>");
            out.println("          <th scope=\"col\">Person</th><th scope=\"col\">Email</th><th scope=\"col\">Role or team</th>");
            if (canEdit) {
                out.println("          <th scope=\"col\"><span class=\"aira-visually-hidden\">Remove</span></th>");
            }
            out.println("        </tr></thead><tbody>");
            for (ParticipantRow row : participants) {
                EsInitiativeMember member = row.member();
                String name = member == null ? "Participant" : member.displayName();
                out.println("          <tr><th scope=\"row\">" + InitiativeHtml.esc(name) + "</th>");
                out.println("            <td>" + InitiativeHtml.esc(member == null ? "" : member.getEmail()) + "</td><td>");
                if (canEdit) {
                    out.println("              <form method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
                    out.println("                <input type=\"hidden\" name=\"action\" value=\"update-role\" />");
                    out.println("                <input type=\"hidden\" name=\"participantId\" value=\""
                            + row.participant().getEsSmallGroupParticipantId() + "\" />");
                    out.println("                <input class=\"aira-input\" name=\"role\" type=\"text\" maxlength=\"200\" value=\""
                            + InitiativeHtml.esc(row.participant().getParticipantRole()) + "\" />");
                    out.println("                <button class=\"aira-button aira-button--small aira-button--ghost\" type=\"submit\">Save</button>");
                    out.println("              </form>");
                } else {
                    out.println("              " + InitiativeHtml.esc(row.participant().getParticipantRole()));
                }
                out.println("            </td>");
                if (canEdit) {
                    out.println("            <td><button class=\"aira-button aira-button--link\" type=\"button\" data-dialog-open=\"remove-participant\" data-record-id=\""
                            + row.participant().getEsSmallGroupParticipantId() + "\" data-dialog-title=\"Remove "
                            + InitiativeHtml.esc(name) + "\">Remove</button></td>");
                }
                out.println("          </tr>");
            }
            out.println("        </tbody></table></div>");
        }
        out.println("      </section>");
        if (canEdit) {
            addDialog(out, base, rosterChoices, input);
            newPersonDialog(out, contextPath, base, initiative, input);
            removeDialog(out, base, input);
        }
    }

    private static void comparison(PrintWriter out, String base, SmallGroupSeriesComparison.Result comparison,
            List<ParticipantRow> participants, String registrationUrl, boolean canEdit) {
        Map<Long, EsInitiativeMember> members = new HashMap<>();
        for (ParticipantRow row : participants) {
            if (row.member() != null) {
                members.put(row.member().getEsInitiativeMemberId(), row.member());
            }
        }
        out.println("      <section class=\"aira-stack aira-stack--compact\">");
        out.println("        <h2>Meeting series</h2>");
        out.println("        <p class=\"aira-meta\">The series is compared with this participant list. Copy a registration link to share it. This page does not send email.</p>");
        if (registrationUrl != null) {
            out.println("        <p><button class=\"aira-button aira-button--small aira-button--secondary\" type=\"button\" data-copy-value=\""
                    + attr(registrationUrl) + "\">Copy registration link</button></p>");
        }
        out.println("        <h3>Not registered</h3>");
        if (comparison.participantsNotRegistered().isEmpty()) {
            out.println("        <p class=\"aira-meta\">Every participant is registered for the series, or no series is selected.</p>");
        } else {
            out.println("        <ul class=\"aira-stack aira-stack--compact\">");
            for (SmallGroupSeriesComparison.Person person : comparison.participantsNotRegistered()) {
                EsInitiativeMember member = members.get(person.memberId());
                String label = member == null ? "Participant" : member.displayName();
                out.println("          <li>" + InitiativeHtml.esc(label));
                if (registrationUrl != null) {
                    out.println("            <button class=\"aira-button aira-button--small aira-button--ghost\" type=\"button\" data-copy-value=\""
                            + attr(SmallGroupSeriesComparison.copyText(label, registrationUrl))
                            + "\">Copy registration link</button>");
                }
                out.println("          </li>");
            }
            out.println("        </ul>");
        }
        out.println("        <h3>Registered, not a participant</h3>");
        if (comparison.registrantsNotParticipants().isEmpty()) {
            out.println("        <p class=\"aira-meta\">No series registrants are missing from the participant list.</p>");
        } else {
            out.println("        <ul class=\"aira-stack aira-stack--compact\">");
            for (SmallGroupSeriesComparison.Registrant registrant : comparison.registrantsNotParticipants()) {
                String label = registrant.label() == null || registrant.label().isBlank()
                        ? registrant.email() : registrant.label();
                out.println("          <li>" + InitiativeHtml.esc(label == null ? registrant.email() : label));
                if (canEdit) {
                    out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
                    out.println("              <input type=\"hidden\" name=\"action\" value=\"add-registrant\" />");
                    out.println("              <input type=\"hidden\" name=\"email\" value=\""
                            + InitiativeHtml.esc(registrant.email()) + "\" />");
                    out.println("              <input type=\"hidden\" name=\"userId\" value=\""
                            + (registrant.userId() == null ? "" : registrant.userId()) + "\" />");
                    out.println("              <input type=\"hidden\" name=\"label\" value=\""
                            + InitiativeHtml.esc(label) + "\" />");
                    out.println("              <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"submit\">Add as participant</button>");
                    out.println("            </form>");
                }
                out.println("          </li>");
            }
            out.println("        </ul>");
        }
        out.println("      </section>");
    }

    private static void copyForm(PrintWriter out, String base, List<EsSmallGroup> earlierGroups) {
        out.println("      <form class=\"aira-form aira-form--narrow\" method=\"post\" action=\""
                + InitiativeHtml.esc(base) + "\">");
        out.println("        <input type=\"hidden\" name=\"action\" value=\"copy-participants\" />");
        out.println("        <div class=\"aira-field\"><label for=\"copyFromId\">Copy participants from</label>");
        out.println("          <select class=\"aira-select\" id=\"copyFromId\" name=\"copyFromId\" required>");
        out.println("            <option value=\"\">Choose an earlier Small Group</option>");
        for (EsSmallGroup earlier : earlierGroups) {
            out.println("            <option value=\"" + earlier.getEsSmallGroupId() + "\">"
                    + InitiativeHtml.esc(earlier.getSmallGroupName()) + "</option>");
        }
        out.println("          </select></div>");
        out.println("        <button class=\"aira-button aira-button--secondary\" type=\"submit\">Copy participants</button>");
        out.println("      </form>");
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

    private static void addDialog(PrintWriter out, String base, List<EsInitiativeMember> rosterChoices, FormInput input) {
        String id = "add-participant";
        InitiativePageRenderer.dialogStart(out, id, "Add participant", input);
        out.println("        <h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">Add participant</h3>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        InitiativePageRenderer.formError(out, input, "add-participant");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"add-participant\" />");
        out.println("          <div class=\"aira-field\"><label for=\"rosterFilter\">Find on the roster</label>");
        out.println("            <input class=\"aira-input\" id=\"rosterFilter\" type=\"search\" data-roster-filter=\"memberId\" placeholder=\"Type a name or email\" /></div>");
        out.println("          <div class=\"aira-field\"><label for=\"memberId\">Person</label>");
        out.println("            <select class=\"aira-select\" id=\"memberId\" name=\"memberId\" required>");
        out.println("              <option value=\"\">Choose a person</option>");
        for (EsInitiativeMember member : rosterChoices) {
            String label = member.displayName() + (member.getEmail() == null || member.getEmail().isBlank()
                    ? "" : " · " + member.getEmail());
            out.println("              <option value=\"" + member.getEsInitiativeMemberId() + "\">"
                    + InitiativeHtml.esc(label) + "</option>");
        }
        out.println("            </select></div>");
        textField(out, "participantRole", "role", "Role or team", input.value("add-participant", "role", ""), 200, false);
        out.println("          <p class=\"aira-meta\">Adding someone who has not joined marks them joined and follows this Small Group's topic when they have an email or an account.</p>");
        InitiativePageRenderer.dialogActions(out, id, "Add");
        out.println("        </form></dialog>");
    }

    private static void newPersonDialog(PrintWriter out, String contextPath, String base, EsInitiative initiative,
            FormInput input) {
        String id = "add-new-participant";
        InitiativePageRenderer.dialogStart(out, id, "Add new person", input);
        out.println("        <h3 class=\"aira-dialog__title\" id=\"" + id + "-title\">Add new person</h3>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(base) + "\">");
        InitiativePageRenderer.formError(out, input, "add-new-participant");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"add-new-participant\" />");
        InitiativePageRenderer.personIdentity(out, input, "add-new-participant", "newPerson");
        InitiativePageRenderer.organizationField(out, contextPath, initiative.getInitiativeCode(), "newPerson", input,
                "add-new-participant", "", null);
        textField(out, "newPersonRole", "role", "Role or team", input.value("add-new-participant", "role", ""), 200, false);
        out.println("          <p class=\"aira-meta\">This also adds them to the initiative roster as joined.</p>");
        InitiativePageRenderer.dialogActions(out, id, "Add person");
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
