package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.airahub.interophub.model.EsInitiative;
import org.airahub.interophub.model.EsInitiativeLink;
import org.airahub.interophub.model.EsInitiativeManager;
import org.airahub.interophub.model.EsInitiativeMember;
import org.airahub.interophub.model.EsInitiativeOrganization;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.Organization;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.InitiativeAccess;
import org.airahub.interophub.service.InitiativeImport;
import org.airahub.interophub.service.InitiativeRosterRules;
import org.airahub.interophub.service.OrganizationDirectory;

final class InitiativePageRenderer {

    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private InitiativePageRenderer() {
    }

    static void subnav(PrintWriter out, String contextPath, String code, String active, boolean canEdit,
            boolean canManage) {
        out.println("      <nav class=\"aira-chip-list\" aria-label=\"Initiative sections\">");
        chip(out, contextPath, code, "", "Overview", active);
        chip(out, contextPath, code, "/organizations", "Organizations", active);
        chip(out, contextPath, code, "/people", "People", active);
        if (canEdit) {
            chip(out, contextPath, code, "/import", "Import", active);
        }
        if (canManage) {
            chip(out, contextPath, code, "/settings", "Settings", active);
        }
        out.println("      </nav>");
    }

    private static void chip(PrintWriter out, String contextPath, String code, String suffix, String label,
            String active) {
        boolean current = suffix.isEmpty() ? "overview".equals(active) : suffix.equals("/" + active);
        out.println("        <a class=\"aira-chip" + (current ? " is-selected" : "") + "\" href=\""
                + InitiativeHtml.esc(contextPath + "/es/initiatives/" + code + suffix) + "\""
                + (current ? " aria-current=\"page\"" : "") + ">" + label + "</a>");
    }

    static void flash(PrintWriter out, String kind, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String tone = "warning".equals(kind) ? "warning" : ("danger".equals(kind) ? "error" : "success");
        out.println("      <div class=\"aira-alert aira-alert--" + tone + "\"><p>"
                + InitiativeHtml.esc(message) + "</p></div>");
    }

    static void list(PrintWriter out, String contextPath, List<EsInitiative> initiatives, Map<Long, String> topicNames,
            boolean admin) {
        out.println("      <div class=\"aira-page-header\"><div>");
        out.println("        <h1 class=\"aira-page-title\">Initiatives</h1>");
        out.println("        <p class=\"aira-meta\">Efforts you can manage. Listed initiatives are not public yet.</p>");
        out.println("      </div>");
        if (admin) {
            out.println("        <a class=\"aira-button aira-button--primary\" href=\""
                    + InitiativeHtml.esc(contextPath + "/es/initiatives/new") + "\">New initiative</a>");
        }
        out.println("      </div>");
        if (initiatives.isEmpty()) {
            out.println("      <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No initiatives yet.</p>");
            out.println("        <p>An administrator can create one and add managers.</p></div>");
            return;
        }
        out.println("      <div class=\"aira-table-wrap\"><table class=\"aira-table\">");
        out.println("        <thead><tr><th>Initiative</th><th>Status</th><th>Primary topic</th><th>Visibility</th></tr></thead><tbody>");
        for (EsInitiative initiative : initiatives) {
            out.println("          <tr><td class=\"aira-table__cell--primary\"><a class=\"aira-inline-link\" href=\""
                    + InitiativeHtml.esc(contextPath + "/es/initiatives/" + initiative.getInitiativeCode()) + "\">"
                    + InitiativeHtml.esc(initiative.getInitiativeName()) + "</a></td>");
            out.println("            <td>" + InitiativeHtml.esc(initiative.getStatus().getLabel()) + "</td>");
            out.println("            <td>" + InitiativeHtml.esc(topicNames.getOrDefault(initiative.getPrimaryEsTopicId(), ""))
                    + "</td>");
            out.println("            <td>" + InitiativeHtml.esc(initiative.getVisibility().getLabel()) + "</td></tr>");
        }
        out.println("        </tbody></table></div>");
    }

    static void createForm(PrintWriter out, String contextPath, List<EsTopic> topics, String error) {
        out.println("      <div class=\"aira-page-header\"><div>");
        out.println("        <h1 class=\"aira-page-title\">New initiative</h1>");
        out.println("        <p class=\"aira-meta\"><a class=\"aira-inline-link\" href=\""
                + InitiativeHtml.esc(contextPath + "/es/initiatives") + "\">All initiatives</a></p>");
        out.println("      </div></div>");
        flash(out, "danger", error);
        out.println("      <form class=\"aira-form\" method=\"post\" action=\""
                + InitiativeHtml.esc(contextPath + "/es/initiatives/new") + "\">");
        fields(out, null, topics, true);
        out.println("        <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--primary\" type=\"submit\">Create initiative</button></div>");
        out.println("      </form>");
    }

    static void overview(PrintWriter out, String contextPath, EsInitiative initiative, EsTopic topic,
            List<EsInitiativeLink> links, InitiativeRosterRules.Representation representation,
            List<EsInitiativeTimelineEntry> timeline, Map<Long, String> userNames, User viewer, boolean canComment) {
        header(out, initiative);
        out.println("      <section class=\"aira-panel aira-stack\">");
        if (initiative.getSummary() != null) {
            out.println("        <p>" + InitiativeHtml.esc(initiative.getSummary()) + "</p>");
        }
        if (initiative.getDescription() != null) {
            out.println("        <p>" + InitiativeHtml.esc(initiative.getDescription()) + "</p>");
        }
        out.println("        <div class=\"aira-cluster\">");
        meta(out, "Status", initiative.getStatus().getLabel());
        meta(out, "Visibility", initiative.getVisibility().getLabel());
        meta(out, "Join policy", initiative.getJoinPolicy().getLabel());
        meta(out, "Dates", dateRange(initiative.getStartDate(), initiative.getEndDate()));
        out.println("        </div>");
        if (topic != null) {
            out.println("        <p>Primary topic: <a class=\"aira-inline-link\" href=\""
                    + InitiativeHtml.esc(contextPath + "/es/topic/" + topic.getEsTopicId()) + "\">"
                    + InitiativeHtml.esc(topic.getTopicName()) + "</a></p>");
        }
        out.println("      </section>");
        representationBlock(out, representation);
        out.println("      <section class=\"aira-panel\"><h2 class=\"aira-section-title\">Links</h2>");
        if (links.isEmpty()) {
            out.println("        <p class=\"aira-meta\">No links yet. Add the Smartsheet, Slack channel, or document folder from Settings.</p>");
        } else {
            out.println("        <ul>");
            for (EsInitiativeLink link : links) {
                out.println("          <li><a class=\"aira-inline-link\" href=\"" + InitiativeHtml.esc(link.getLinkUrl())
                        + "\" target=\"_blank\" rel=\"noopener\">" + InitiativeHtml.esc(link.getLinkLabel()) + "</a></li>");
            }
            out.println("        </ul>");
        }
        out.println("      </section>");
        timeline(out, contextPath + "/es/initiatives/" + initiative.getInitiativeCode(), timeline, userNames, viewer,
                canComment, EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiative.getEsInitiativeId());
    }

    static void organizations(PrintWriter out, String contextPath, EsInitiative initiative,
            List<EsInitiativeOrganization> participations, Map<Long, Organization> organizations,
            List<EsInitiativeMember> members, List<EsInitiativeTimelineEntry> timeline, Map<Long, String> userNames,
            User viewer, InitiativeRosterRules.Representation representation, boolean canEdit) {
        header(out, initiative);
        representationBlock(out, representation);
        String postUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/organizations";
        if (canEdit) {
            out.println("      <section class=\"aira-panel\"><h2 class=\"aira-section-title\">Add organization</h2>");
            out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
            out.println("          <input type=\"hidden\" name=\"action\" value=\"add-organization\" />");
            organizationField(out, contextPath, initiative.getInitiativeCode(), "", null);
            out.println("          <div class=\"aira-field\"><label for=\"participationStatus\">Status</label>");
            out.println("            <select class=\"aira-select\" id=\"participationStatus\" name=\"participationStatus\">");
            statusOptions(out, EsInitiativeOrganization.ParticipationStatus.values(),
                    EsInitiativeOrganization.ParticipationStatus.PROSPECT.name(), EsInitiativeOrganization.ParticipationStatus::getLabel);
            out.println("            </select></div>");
            out.println("          <div class=\"aira-field\"><label for=\"orgNote\">Note</label>");
            out.println("            <input class=\"aira-input\" id=\"orgNote\" name=\"note\" type=\"text\" /></div>");
            out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\">Add organization</button>");
            out.println("        </form></section>");
        }
        if (participations.isEmpty()) {
            out.println("      <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No organizations yet.</p></div>");
            return;
        }
        Map<Long, List<EsInitiativeMember>> byOrg = new LinkedHashMap<>();
        for (EsInitiativeMember member : members) {
            if (member.getEsInitiativeOrganizationId() != null) {
                byOrg.computeIfAbsent(member.getEsInitiativeOrganizationId(), id -> new ArrayList<>()).add(member);
            }
        }
        Map<Long, List<EsInitiativeTimelineEntry>> history = groupTimeline(timeline);
        out.println("      <div class=\"aira-stack\">");
        for (EsInitiativeOrganization participation : participations) {
            Organization organization = organizations.get(participation.getOrganizationId());
            String name = organization == null ? "Organization" : organization.getOrganizationName();
            String type = organization == null ? "" : organization.getOrganizationType();
            List<EsInitiativeMember> contacts = byOrg.getOrDefault(participation.getEsInitiativeOrganizationId(), List.of());
            List<String> statuses = new ArrayList<>();
            for (EsInitiativeMember contact : contacts) {
                statuses.add(contact.getMemberStatus().name());
            }
            boolean needsContact = InitiativeRosterRules.needsContact(statuses);
            out.println("        <section class=\"aira-section-card\"><div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">"
                    + InitiativeHtml.esc(name) + "</h2></div>");
            out.println("          <div class=\"aira-section-card__body aira-stack\">");
            out.println("            <div class=\"aira-cluster\">");
            out.println("              <span class=\"aira-badge aira-badge--subtle\">"
                    + InitiativeHtml.esc(OrganizationDirectory.label(type)) + "</span>");
            out.println("              <span class=\"aira-badge aira-badge--info\">"
                    + InitiativeHtml.esc(participation.getParticipationStatus().getLabel()) + "</span>");
            if (needsContact) {
                out.println("              <span class=\"aira-badge aira-badge--warning\">Needs a contact</span>");
            }
            out.println("            </div>");
            if (contacts.isEmpty()) {
                out.println("            <p class=\"aira-meta\">No contacts yet.</p>");
            } else {
                out.println("            <ul>");
                for (EsInitiativeMember contact : contacts) {
                    out.println("              <li>" + InitiativeHtml.esc(contact.displayName()) + " <span class=\"aira-meta\">"
                            + InitiativeHtml.esc(contact.getMemberStatus().getLabel()) + "</span></li>");
                }
                out.println("            </ul>");
            }
            if (canEdit) {
                out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
                out.println("              <input type=\"hidden\" name=\"action\" value=\"org-status\" />");
                out.println("              <input type=\"hidden\" name=\"participationId\" value=\""
                        + participation.getEsInitiativeOrganizationId() + "\" />");
                out.println("              <div class=\"aira-field\"><label>Status</label><select class=\"aira-select\" name=\"participationStatus\">");
                statusOptions(out, EsInitiativeOrganization.ParticipationStatus.values(),
                        participation.getParticipationStatus().name(), EsInitiativeOrganization.ParticipationStatus::getLabel);
                out.println("              </select></div>");
                out.println("              <div class=\"aira-field\"><label>Note</label><input class=\"aira-input\" name=\"note\" type=\"text\" /></div>");
                out.println("              <button class=\"aira-button aira-button--secondary\" type=\"submit\">Update status</button>");
                out.println("            </form>");
                out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
                out.println("              <input type=\"hidden\" name=\"action\" value=\"org-type\" />");
                out.println("              <input type=\"hidden\" name=\"participationId\" value=\""
                        + participation.getEsInitiativeOrganizationId() + "\" />");
                out.println("              <div class=\"aira-field\"><label>Type</label><select class=\"aira-select\" name=\"organizationType\">");
                for (OrganizationDirectory.OrganizationType organizationType : OrganizationDirectory.TYPES) {
                    String selected = organizationType.code().equals(type) ? " selected" : "";
                    out.println("                <option value=\"" + organizationType.code() + "\"" + selected + ">"
                            + InitiativeHtml.esc(organizationType.label()) + "</option>");
                }
                out.println("              </select></div>");
                out.println("              <p class=\"aira-meta\">Type is shared by every initiative that uses this organization.</p>");
                out.println("              <button class=\"aira-button aira-button--secondary\" type=\"submit\">Update type</button>");
                out.println("            </form>");
            }
            timeline(out, postUrl, history.getOrDefault(participation.getEsInitiativeOrganizationId(), List.of()),
                    userNames, viewer, canEdit, EsInitiativeTimelineEntry.SubjectType.ORGANIZATION,
                    participation.getEsInitiativeOrganizationId());
            out.println("          </div></section>");
        }
        out.println("      </div>");
    }

    static void people(PrintWriter out, String contextPath, EsInitiative initiative,
            List<EsInitiativeMember> members, List<EsInitiativeOrganization> participations,
            Map<Long, Organization> organizations, List<EsInitiativeTimelineEntry> timeline, Map<Long, String> userNames,
            User viewer, java.util.Set<Long> missingFollow, String statusFilter, String organizationFilter,
            String typeFilter, boolean canEdit) {
        header(out, initiative);
        String pageUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/people";
        out.println("      <form class=\"aira-form\" method=\"get\" action=\"" + InitiativeHtml.esc(pageUrl) + "\">");
        out.println("        <div class=\"aira-cluster\">");
        out.println("          <div class=\"aira-field\"><label for=\"filterStatus\">Status</label><select class=\"aira-select\" id=\"filterStatus\" name=\"status\">");
        out.println("            <option value=\"\">Any</option>");
        statusOptions(out, EsInitiativeMember.MemberStatus.values(), statusFilter, EsInitiativeMember.MemberStatus::getLabel);
        out.println("          </select></div>");
        out.println("          <div class=\"aira-field\"><label for=\"filterOrg\">Organization</label><select class=\"aira-select\" id=\"filterOrg\" name=\"organization\">");
        out.println("            <option value=\"\">Any</option>");
        for (EsInitiativeOrganization participation : participations) {
            Organization organization = organizations.get(participation.getOrganizationId());
            String selected = String.valueOf(participation.getEsInitiativeOrganizationId()).equals(organizationFilter) ? " selected" : "";
            out.println("            <option value=\"" + participation.getEsInitiativeOrganizationId() + "\"" + selected + ">"
                    + InitiativeHtml.esc(organization == null ? "Organization" : organization.getOrganizationName()) + "</option>");
        }
        out.println("          </select></div>");
        out.println("          <div class=\"aira-field\"><label for=\"filterType\">Organization type</label><select class=\"aira-select\" id=\"filterType\" name=\"type\">");
        out.println("            <option value=\"\">Any</option>");
        for (OrganizationDirectory.OrganizationType organizationType : OrganizationDirectory.TYPES) {
            String selected = organizationType.code().equals(typeFilter) ? " selected" : "";
            out.println("            <option value=\"" + organizationType.code() + "\"" + selected + ">"
                    + InitiativeHtml.esc(organizationType.label()) + "</option>");
        }
        out.println("          </select></div>");
        out.println("          <button class=\"aira-button aira-button--secondary\" type=\"submit\">Filter</button>");
        out.println("        </div></form>");
        if (canEdit) {
            out.println("      <section class=\"aira-panel\"><h2 class=\"aira-section-title\">Add person</h2>");
            out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(pageUrl) + "\">");
            out.println("          <input type=\"hidden\" name=\"action\" value=\"add-member\" />");
            out.println("          <div class=\"aira-field\"><label for=\"personName\">Name</label>");
            out.println("            <input class=\"aira-input\" id=\"personName\" name=\"name\" type=\"text\" /></div>");
            out.println("          <div class=\"aira-field\"><label for=\"personEmail\">Email</label>");
            out.println("            <input class=\"aira-input\" id=\"personEmail\" name=\"email\" type=\"email\" /></div>");
            organizationField(out, contextPath, initiative.getInitiativeCode(), "", null);
            out.println("          <div class=\"aira-field\"><label for=\"personRole\">Role in the initiative</label>");
            out.println("            <input class=\"aira-input\" id=\"personRole\" name=\"initiativeRole\" type=\"text\" /></div>");
            out.println("          <div class=\"aira-field\"><label for=\"personStatus\">Status</label><select class=\"aira-select\" id=\"personStatus\" name=\"memberStatus\">");
            statusOptions(out, EsInitiativeMember.MemberStatus.values(), EsInitiativeMember.MemberStatus.PROSPECT.name(),
                    EsInitiativeMember.MemberStatus::getLabel);
            out.println("          </select></div>");
            out.println("          <div class=\"aira-field\"><label for=\"personNote\">Note</label>");
            out.println("            <input class=\"aira-input\" id=\"personNote\" name=\"note\" type=\"text\" /></div>");
            out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\">Add person</button>");
            out.println("        </form></section>");
        }
        Map<Long, List<EsInitiativeTimelineEntry>> history = groupTimeline(timeline);
        if (members.isEmpty()) {
            out.println("      <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No people match this filter.</p></div>");
            return;
        }
        out.println("      <div class=\"aira-stack\">");
        for (EsInitiativeMember member : members) {
            Organization organization = organizationFor(member, participations, organizations);
            String type = organization == null ? "" : OrganizationDirectory.label(organization.getOrganizationType());
            out.println("        <section class=\"aira-section-card\"><div class=\"aira-section-card__header\"><h2 class=\"aira-section-card__title\">"
                    + InitiativeHtml.esc(member.displayName()) + "</h2></div>");
            out.println("          <div class=\"aira-section-card__body aira-stack\">");
            out.println("            <div class=\"aira-cluster\">");
            out.println("              <span class=\"aira-badge aira-badge--info\">"
                    + InitiativeHtml.esc(member.getMemberStatus().getLabel()) + "</span>");
            if (member.getEmail() != null) {
                out.println("              <span class=\"aira-meta\">" + InitiativeHtml.esc(member.getEmail()) + "</span>");
            }
            if (organization != null) {
                out.println("              <span class=\"aira-meta\">" + InitiativeHtml.esc(organization.getOrganizationName())
                        + (type.isBlank() ? "" : " · " + InitiativeHtml.esc(type)) + "</span>");
            }
            if (member.getInitiativeRole() != null) {
                out.println("              <span class=\"aira-meta\">" + InitiativeHtml.esc(member.getInitiativeRole()) + "</span>");
            }
            if (missingFollow.contains(member.getEsInitiativeMemberId())) {
                out.println("              <span class=\"aira-badge aira-badge--warning\">Joined, but no longer follows the primary topic</span>");
            }
            out.println("            </div>");
            if (canEdit) {
                out.println("            <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(pageUrl) + "\">");
                out.println("              <input type=\"hidden\" name=\"action\" value=\"member-status\" />");
                out.println("              <input type=\"hidden\" name=\"memberId\" value=\"" + member.getEsInitiativeMemberId() + "\" />");
                out.println("              <div class=\"aira-field\"><label>Status</label><select class=\"aira-select\" name=\"memberStatus\">");
                statusOptions(out, EsInitiativeMember.MemberStatus.values(), member.getMemberStatus().name(),
                        EsInitiativeMember.MemberStatus::getLabel);
                out.println("              </select></div>");
                out.println("              <div class=\"aira-field\"><label>Note</label><input class=\"aira-input\" name=\"note\" type=\"text\" /></div>");
                out.println("              <button class=\"aira-button aira-button--secondary\" type=\"submit\">Update status</button>");
                out.println("            </form>");
                String currentName = ((member.getContactFirstName() == null ? "" : member.getContactFirstName()) + " "
                        + (member.getContactLastName() == null ? "" : member.getContactLastName())).trim();
                out.println("            <details><summary>Edit person</summary>");
                out.println("              <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(pageUrl) + "\">");
                out.println("                <input type=\"hidden\" name=\"action\" value=\"edit-member\" />");
                out.println("                <input type=\"hidden\" name=\"memberId\" value=\"" + member.getEsInitiativeMemberId() + "\" />");
                out.println("                <div class=\"aira-field\"><label>Name</label><input class=\"aira-input\" name=\"name\" type=\"text\" value=\""
                        + InitiativeHtml.esc(currentName) + "\" /></div>");
                out.println("                <div class=\"aira-field\"><label>Email</label><input class=\"aira-input\" name=\"email\" type=\"email\" value=\""
                        + InitiativeHtml.esc(member.getEmail()) + "\" /></div>");
                organizationField(out, contextPath, initiative.getInitiativeCode(),
                        organization == null ? "" : organization.getOrganizationName(),
                        organization == null ? null : organization.getOrganizationId());
                out.println("                <div class=\"aira-field\"><label>Role</label><input class=\"aira-input\" name=\"initiativeRole\" type=\"text\" value=\""
                        + InitiativeHtml.esc(member.getInitiativeRole()) + "\" /></div>");
                out.println("                <button class=\"aira-button aira-button--secondary\" type=\"submit\">Save person</button>");
                out.println("              </form></details>");
            }
            timeline(out, pageUrl, history.getOrDefault(member.getEsInitiativeMemberId(), List.of()), userNames, viewer,
                    canEdit, EsInitiativeTimelineEntry.SubjectType.MEMBER, member.getEsInitiativeMemberId());
            out.println("          </div></section>");
        }
        out.println("      </div>");
    }

    static void settings(PrintWriter out, String contextPath, EsInitiative initiative, List<EsTopic> topics,
            List<EsInitiativeManager> managers, Map<Long, User> users, List<EsInitiativeLink> links) {
        header(out, initiative);
        String postUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/settings";
        out.println("      <section class=\"aira-panel\"><h2 class=\"aira-section-title\">Initiative</h2>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"save-settings\" />");
        fields(out, initiative, topics, false);
        out.println("          <p class=\"aira-meta\">Changing the primary topic does not change follows that are already in place. Joined members who do not follow the new topic are flagged on the people roster.</p>");
        out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\">Save settings</button>");
        out.println("        </form></section>");

        out.println("      <section class=\"aira-panel\"><h2 class=\"aira-section-title\">Managers</h2>");
        out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table\"><thead><tr><th>Person</th><th>Role</th><th></th></tr></thead><tbody>");
        for (EsInitiativeManager manager : managers) {
            User user = users.get(manager.getUserId());
            String label = user == null ? "User " + manager.getUserId() : user.getFullName();
            out.println("          <tr><td>" + InitiativeHtml.esc(label) + "</td><td>");
            out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
            out.println("              <input type=\"hidden\" name=\"action\" value=\"manager-role\" />");
            out.println("              <input type=\"hidden\" name=\"managerId\" value=\"" + manager.getEsInitiativeManagerId() + "\" />");
            out.println("              <select class=\"aira-select\" name=\"managerRole\">");
            for (EsInitiativeManager.ManagerRole role : EsInitiativeManager.ManagerRole.values()) {
                String selected = role == manager.getManagerRole() ? " selected" : "";
                out.println("                <option value=\"" + role.name() + "\"" + selected + ">"
                        + InitiativeHtml.esc(role.getLabel()) + "</option>");
            }
            out.println("              </select>");
            out.println("              <button class=\"aira-button aira-button--secondary\" type=\"submit\">Update</button>");
            out.println("            </form></td><td>");
            out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
            out.println("              <input type=\"hidden\" name=\"action\" value=\"remove-manager\" />");
            out.println("              <input type=\"hidden\" name=\"managerId\" value=\"" + manager.getEsInitiativeManagerId() + "\" />");
            out.println("              <button class=\"aira-button aira-button--secondary\" type=\"submit\">Remove</button>");
            out.println("            </form></td></tr>");
        }
        out.println("        </tbody></table></div>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"add-manager\" />");
        out.println("          <div class=\"aira-field\"><label for=\"managerEmail\">Email of an InteropHub account</label>");
        out.println("            <input class=\"aira-input\" id=\"managerEmail\" name=\"managerEmail\" type=\"email\" required /></div>");
        out.println("          <div class=\"aira-field\"><label for=\"managerRole\">Role</label><select class=\"aira-select\" id=\"managerRole\" name=\"managerRole\">");
        for (EsInitiativeManager.ManagerRole role : EsInitiativeManager.ManagerRole.values()) {
            out.println("            <option value=\"" + role.name() + "\">" + InitiativeHtml.esc(role.getLabel()) + "</option>");
        }
        out.println("          </select></div>");
        out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\">Add manager</button>");
        out.println("        </form></section>");

        out.println("      <section class=\"aira-panel\"><h2 class=\"aira-section-title\">Links</h2>");
        if (!links.isEmpty()) {
            out.println("        <ul>");
            for (EsInitiativeLink link : links) {
                out.println("          <li>" + InitiativeHtml.esc(link.getLinkLabel()) + " — <a class=\"aira-inline-link\" href=\""
                        + InitiativeHtml.esc(link.getLinkUrl()) + "\">" + InitiativeHtml.esc(link.getLinkUrl()) + "</a>");
                out.println("            <form method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
                out.println("              <input type=\"hidden\" name=\"action\" value=\"delete-link\" />");
                out.println("              <input type=\"hidden\" name=\"linkId\" value=\"" + link.getEsInitiativeLinkId() + "\" />");
                out.println("              <button class=\"aira-button aira-button--secondary\" type=\"submit\">Remove</button>");
                out.println("            </form></li>");
            }
            out.println("        </ul>");
        }
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"add-link\" />");
        out.println("          <div class=\"aira-field\"><label for=\"linkLabel\">Label</label>");
        out.println("            <input class=\"aira-input\" id=\"linkLabel\" name=\"linkLabel\" type=\"text\" required /></div>");
        out.println("          <div class=\"aira-field\"><label for=\"linkUrl\">URL</label>");
        out.println("            <input class=\"aira-input\" id=\"linkUrl\" name=\"linkUrl\" type=\"url\" required /></div>");
        out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\">Add link</button>");
        out.println("        </form></section>");
    }

    static void importPage(PrintWriter out, String contextPath, EsInitiative initiative, List<String> headers,
            InitiativeImport.ColumnMapping mapping, InitiativeImport.Preview preview) {
        header(out, initiative);
        String postUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/import";
        out.println("      <section class=\"aira-panel\"><h2 class=\"aira-section-title\">Upload</h2>");
        out.println("        <p class=\"aira-meta\">Map the columns in the file, check the preview, then import. A row with an organization and no contact adds only the organization. Notes become the first timeline entry.</p>");
        out.println("        <form class=\"aira-form\" method=\"post\" enctype=\"multipart/form-data\" action=\""
                + InitiativeHtml.esc(postUrl) + "\">");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"preview\" />");
        out.println("          <div class=\"aira-field\"><label for=\"importFile\">Spreadsheet (.xlsx or .csv)</label>");
        out.println("            <input id=\"importFile\" name=\"file\" type=\"file\" accept=\".xlsx,.xlsm,.csv\" required /></div>");
        out.println("          <button class=\"aira-button aira-button--secondary\" type=\"submit\">Upload and preview</button>");
        out.println("        </form></section>");
        if (headers == null || mapping == null) {
            return;
        }
        out.println("      <section class=\"aira-panel\"><h2 class=\"aira-section-title\">Column mapping</h2>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"remap\" />");
        mappingSelect(out, "mapContact", "Point of contact", headers, mapping.contact());
        mappingSelect(out, "mapEmail", "Email", headers, mapping.email());
        mappingSelect(out, "mapOrganization", "Organization", headers, mapping.organization());
        mappingSelect(out, "mapOrganizationType", "Organization type", headers, mapping.organizationType());
        mappingSelect(out, "mapNotes", "Notes", headers, mapping.notes());
        out.println("          <button class=\"aira-button aira-button--secondary\" type=\"submit\">Update preview</button>");
        out.println("        </form></section>");
        if (preview == null) {
            return;
        }
        out.println("      <section class=\"aira-panel\"><h2 class=\"aira-section-title\">Preview</h2>");
        out.println("        <p class=\"aira-meta\">" + preview.memberCount() + " people, " + preview.organizationCount()
                + " organizations, " + preview.errorCount() + " rows that will not be imported.</p>");
        out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table\"><thead><tr><th>Row</th><th>What will happen</th><th>Detail</th></tr></thead><tbody>");
        for (InitiativeImport.PreviewRow row : preview.rows()) {
            out.println("          <tr><td>" + row.rowNumber() + "</td><td>" + InitiativeHtml.esc(previewLabel(row))
                    + "</td><td>" + InitiativeHtml.esc(row.detail()) + "</td></tr>");
        }
        out.println("        </tbody></table></div>");
        out.println("        <form method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"commit\" />");
        out.println("          <button class=\"aira-button aira-button--primary\" type=\"submit\">Import these rows</button>");
        out.println("        </form></section>");
    }

    private static String previewLabel(InitiativeImport.PreviewRow row) {
        String who = row.contactName() != null ? row.contactName() : (row.email() != null ? row.email() : row.organizationName());
        return switch (row.action()) {
            case ADD_MEMBER -> "Add " + (who == null ? "person" : who);
            case ADD_ORGANIZATION -> "Add organization " + (row.organizationName() == null ? "" : row.organizationName());
            case SKIP -> "Skip";
            case ERROR -> "Needs attention";
        };
    }

    private static void mappingSelect(PrintWriter out, String name, String label, List<String> headers, int selected) {
        out.println("          <div class=\"aira-field\"><label for=\"" + name + "\">" + InitiativeHtml.esc(label) + "</label>");
        out.println("            <select class=\"aira-select\" id=\"" + name + "\" name=\"" + name + "\">");
        out.println("              <option value=\"-1\"" + (selected < 0 ? " selected" : "") + ">(Not in this file)</option>");
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i) == null || headers.get(i).isBlank() ? "Column " + (i + 1) : headers.get(i);
            out.println("              <option value=\"" + i + "\"" + (i == selected ? " selected" : "") + ">"
                    + InitiativeHtml.esc(header) + "</option>");
        }
        out.println("            </select></div>");
    }

    private static void header(PrintWriter out, EsInitiative initiative) {
        out.println("      <div class=\"aira-page-header\"><div>");
        out.println("        <h1 class=\"aira-page-title\">" + InitiativeHtml.esc(initiative.getInitiativeName()) + "</h1>");
        out.println("        <p class=\"aira-meta\">" + InitiativeHtml.esc(initiative.getInitiativeCode()) + "</p>");
        out.println("      </div></div>");
    }

    private static void fields(PrintWriter out, EsInitiative initiative, List<EsTopic> topics, boolean creating) {
        String name = initiative == null ? "" : initiative.getInitiativeName();
        String code = initiative == null ? "" : initiative.getInitiativeCode();
        String summary = initiative == null ? "" : InitiativeHtml.text(initiative.getSummary());
        String description = initiative == null ? "" : InitiativeHtml.text(initiative.getDescription());
        out.println("        <div class=\"aira-field\"><label for=\"initiativeName\">Name</label>");
        out.println("          <input class=\"aira-input\" id=\"initiativeName\" name=\"name\" type=\"text\" required value=\""
                + InitiativeHtml.esc(name) + "\" /></div>");
        out.println("        <div class=\"aira-field\"><label for=\"initiativeCode\">Code</label>");
        out.println("          <input class=\"aira-input\" id=\"initiativeCode\" name=\"code\" type=\"text\" value=\""
                + InitiativeHtml.esc(code) + "\" />");
        out.println("          <p class=\"aira-meta\">Used in the page address. Leave blank to build it from the name.</p></div>");
        out.println("        <div class=\"aira-field\"><label for=\"summary\">Summary</label>");
        out.println("          <textarea class=\"aira-textarea\" id=\"summary\" name=\"summary\">" + InitiativeHtml.esc(summary)
                + "</textarea></div>");
        out.println("        <div class=\"aira-field\"><label for=\"description\">Description</label>");
        out.println("          <textarea class=\"aira-textarea\" id=\"description\" name=\"description\">"
                + InitiativeHtml.esc(description) + "</textarea></div>");
        out.println("        <div class=\"aira-field\"><label for=\"primaryEsTopicId\">Primary topic</label>");
        out.println("          <select class=\"aira-select\" id=\"primaryEsTopicId\" name=\"primaryEsTopicId\" required>");
        out.println("            <option value=\"\">Choose a topic</option>");
        Long selectedTopic = initiative == null ? null : initiative.getPrimaryEsTopicId();
        for (EsTopic topic : topics) {
            String selected = topic.getEsTopicId().equals(selectedTopic) ? " selected" : "";
            out.println("            <option value=\"" + topic.getEsTopicId() + "\"" + selected + ">"
                    + InitiativeHtml.esc(topic.getTopicName()) + "</option>");
        }
        out.println("          </select></div>");
        enumSelect(out, "status", "Status", EsInitiative.Status.values(),
                initiative == null ? EsInitiative.Status.PLANNING.name() : initiative.getStatus().name(),
                value -> ((EsInitiative.Status) value).getLabel());
        enumSelect(out, "visibility", "Visibility", EsInitiative.Visibility.values(),
                initiative == null ? EsInitiative.Visibility.PRIVATE.name() : initiative.getVisibility().name(),
                value -> ((EsInitiative.Visibility) value).getLabel());
        enumSelect(out, "joinPolicy", "Join policy", EsInitiative.JoinPolicy.values(),
                initiative == null ? EsInitiative.JoinPolicy.INVITE_ONLY.name() : initiative.getJoinPolicy().name(),
                value -> ((EsInitiative.JoinPolicy) value).getLabel());
        dateField(out, "startDate", "Start date", initiative == null ? null : initiative.getStartDate());
        dateField(out, "endDate", "End date", initiative == null ? null : initiative.getEndDate());
        if (creating) {
            out.println("        <p class=\"aira-meta\">You will be added as an admin manager. Global administrators can always open an initiative.</p>");
        }
    }

    private static void enumSelect(PrintWriter out, String name, String label, Enum<?>[] values, String selected,
            java.util.function.Function<Enum<?>, String> labels) {
        out.println("        <div class=\"aira-field\"><label for=\"" + name + "\">" + label + "</label>");
        out.println("          <select class=\"aira-select\" id=\"" + name + "\" name=\"" + name + "\">");
        for (Enum<?> value : values) {
            String mark = value.name().equals(selected) ? " selected" : "";
            out.println("            <option value=\"" + value.name() + "\"" + mark + ">"
                    + InitiativeHtml.esc(labels.apply(value)) + "</option>");
        }
        out.println("          </select></div>");
    }

    private static <E extends Enum<E>> void statusOptions(PrintWriter out, E[] values, String selected,
            java.util.function.Function<E, String> labels) {
        for (E value : values) {
            String mark = value.name().equals(selected) ? " selected" : "";
            out.println("              <option value=\"" + value.name() + "\"" + mark + ">"
                    + InitiativeHtml.esc(labels.apply(value)) + "</option>");
        }
    }

    private static void dateField(PrintWriter out, String name, String label, LocalDate value) {
        out.println("        <div class=\"aira-field\"><label for=\"" + name + "\">" + label + "</label>");
        out.println("          <input class=\"aira-input\" id=\"" + name + "\" name=\"" + name + "\" type=\"date\" value=\""
                + (value == null ? "" : value) + "\" /></div>");
    }

    static void organizationField(PrintWriter out, String contextPath, String code, String currentName, Long organizationId) {
        String suggest = contextPath + "/es/initiatives/" + code + "/organization-suggest";
        out.println("          <div class=\"initiative-org-field\" data-org-typeahead data-suggest-url=\""
                + InitiativeHtml.esc(suggest) + "\">");
        out.println("            <div class=\"aira-field\"><label>Organization</label>");
        out.println("              <input class=\"aira-input\" data-org-query type=\"text\" autocomplete=\"off\" value=\""
                + InitiativeHtml.esc(currentName) + "\" placeholder=\"Search organizations\" />");
        out.println("              <input data-org-id type=\"hidden\" name=\"organizationId\" value=\""
                + (organizationId == null ? "" : organizationId) + "\" /></div>");
        out.println("            <div class=\"aira-search-popover\" hidden data-org-popover><div class=\"aira-search-listbox\" data-org-list role=\"listbox\"></div></div>");
        out.println("            <div data-org-new hidden>");
        out.println("              <div class=\"aira-field\"><label>New organization name</label>");
        out.println("                <input class=\"aira-input\" data-org-new-name name=\"newOrganizationName\" type=\"text\" /></div>");
        out.println("              <div class=\"aira-field\"><label>Type</label><select class=\"aira-select\" data-org-new-type name=\"newOrganizationType\">");
        out.println("                <option value=\"\">Choose a type</option>");
        for (OrganizationDirectory.OrganizationType organizationType : OrganizationDirectory.TYPES) {
            out.println("                <option value=\"" + organizationType.code() + "\">"
                    + InitiativeHtml.esc(organizationType.label()) + "</option>");
        }
        out.println("              </select></div>");
        out.println("              <div class=\"aira-field\"><label>Website</label>");
        out.println("                <input class=\"aira-input\" name=\"newOrganizationWebsite\" type=\"text\" /></div>");
        out.println("            </div></div>");
    }

    private static void representationBlock(PrintWriter out, InitiativeRosterRules.Representation representation) {
        out.println("      <section class=\"aira-panel\"><h2 class=\"aira-section-title\">Representation</h2>");
        out.println("        <p>" + representation.committedOrganizations() + " committed "
                + (representation.committedOrganizations() == 1 ? "organization" : "organizations") + ".</p>");
        if (representation.committedByType().isEmpty()) {
            out.println("        <p class=\"aira-meta\">No organizations are committed yet.</p>");
        } else {
            out.println("        <div class=\"aira-chip-list\">");
            for (InitiativeRosterRules.TypeCount count : representation.committedByType()) {
                out.println("          <span class=\"aira-chip\">" + InitiativeHtml.esc(count.label()) + " "
                        + count.count() + "</span>");
            }
            out.println("        </div>");
        }
        out.println("        <p>Jurisdiction: " + (representation.jurisdictionPresent() ? "present" : "not yet") + ".</p>");
        out.println("        <p>At least 3 organizations: " + (representation.atLeastThreeOrganizations() ? "yes" : "not yet")
                + ".</p></section>");
    }

    private static void timeline(PrintWriter out, String postUrl, List<EsInitiativeTimelineEntry> entries,
            Map<Long, String> userNames, User viewer, boolean canComment, EsInitiativeTimelineEntry.SubjectType type,
            Long subjectId) {
        out.println("            <details><summary>History</summary><div class=\"aira-stack\">");
        if (entries.isEmpty()) {
            out.println("              <p class=\"aira-meta\">No history yet.</p>");
        }
        for (EsInitiativeTimelineEntry entry : entries) {
            String who = userNames.getOrDefault(entry.getCreatedByUserId(), "Someone");
            String when = entry.getCreatedAt() == null ? "" : DATE_FORMAT.format(entry.getCreatedAt());
            out.println("              <article>");
            out.println("                <p class=\"aira-meta\">" + InitiativeHtml.esc(when) + " · " + InitiativeHtml.esc(who) + "</p>");
            out.println("                <p>" + InitiativeHtml.esc(entryText(entry)) + "</p>");
            if (canComment && viewer != null && InitiativeRosterRules.canEditEntry(entry.getEntryType(),
                    entry.getCreatedByUserId(), viewer.getUserId())) {
                out.println("                <form method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
                out.println("                  <input type=\"hidden\" name=\"action\" value=\"edit-comment\" />");
                out.println("                  <input type=\"hidden\" name=\"entryId\" value=\""
                        + entry.getEsInitiativeTimelineEntryId() + "\" />");
                out.println("                  <div class=\"aira-field\"><label>Edit comment</label>");
                out.println("                    <textarea class=\"aira-textarea\" name=\"note\">"
                        + InitiativeHtml.esc(entry.getNoteText()) + "</textarea></div>");
                out.println("                  <button class=\"aira-button aira-button--secondary\" type=\"submit\">Save comment</button>");
                out.println("                </form>");
            }
            out.println("              </article>");
        }
        if (canComment) {
            out.println("              <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
            out.println("                <input type=\"hidden\" name=\"action\" value=\"comment\" />");
            out.println("                <input type=\"hidden\" name=\"subjectType\" value=\"" + type.name() + "\" />");
            out.println("                <input type=\"hidden\" name=\"subjectId\" value=\"" + subjectId + "\" />");
            out.println("                <div class=\"aira-field\"><label>Comment</label>");
            out.println("                  <textarea class=\"aira-textarea\" name=\"note\"></textarea></div>");
            out.println("                <button class=\"aira-button aira-button--secondary\" type=\"submit\">Add comment</button>");
            out.println("              </form>");
        }
        out.println("            </div></details>");
    }

    private static String entryText(EsInitiativeTimelineEntry entry) {
        if (entry.getEntryType() == EsInitiativeTimelineEntry.EntryType.STATUS_CHANGE) {
            String from = entry.getFromValue() == null || entry.getFromValue().isBlank() ? "New" : entry.getFromValue();
            String to = entry.getToValue() == null ? "" : entry.getToValue();
            String text = from + " → " + to;
            if (entry.getNoteText() != null && !entry.getNoteText().isBlank()) {
                text = text + ". \"" + entry.getNoteText() + "\"";
            }
            return text;
        }
        if (entry.getEntryType() == EsInitiativeTimelineEntry.EntryType.FIELD_CHANGE) {
            String field = entry.getNoteText() == null ? "Updated" : entry.getNoteText();
            return field + ": " + shorten(entry.getFromValue()) + " → " + shorten(entry.getToValue());
        }
        String note = entry.getNoteText() == null ? "" : entry.getNoteText();
        if (entry.getEditedAt() != null) {
            note = note + " (edited)";
        }
        return note;
    }

    private static String shorten(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        String trimmed = value.trim();
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "…" : trimmed;
    }

    private static Map<Long, List<EsInitiativeTimelineEntry>> groupTimeline(List<EsInitiativeTimelineEntry> entries) {
        Map<Long, List<EsInitiativeTimelineEntry>> grouped = new LinkedHashMap<>();
        for (EsInitiativeTimelineEntry entry : entries) {
            grouped.computeIfAbsent(entry.getSubjectId(), id -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    private static Organization organizationFor(EsInitiativeMember member, List<EsInitiativeOrganization> participations,
            Map<Long, Organization> organizations) {
        if (member.getEsInitiativeOrganizationId() == null) {
            return null;
        }
        for (EsInitiativeOrganization participation : participations) {
            if (member.getEsInitiativeOrganizationId().equals(participation.getEsInitiativeOrganizationId())) {
                return organizations.get(participation.getOrganizationId());
            }
        }
        return null;
    }

    private static void meta(PrintWriter out, String label, String value) {
        out.println("          <span class=\"aira-meta-chip\"><span class=\"aira-meta-chip__label\">"
                + InitiativeHtml.esc(label) + "</span> <span class=\"aira-meta-chip__value\">"
                + InitiativeHtml.esc(value) + "</span></span>");
    }

    private static String dateRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return "Not set";
        }
        if (start != null && end != null) {
            return start + " to " + end;
        }
        return start != null ? "Starts " + start : "Ends " + end;
    }

    static String sectionActive(String section) {
        return section == null ? "overview" : section;
    }

    static boolean canEdit(InitiativeAccess.Level level) {
        return InitiativeAccess.canEditRoster(level);
    }
}
