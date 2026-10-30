package org.airahub.interophub.servlet;

import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.airahub.interophub.service.InitiativeImport;
import org.airahub.interophub.service.InitiativeRosterRules;
import org.airahub.interophub.service.InitiativeTimelineText;
import org.airahub.interophub.service.OrganizationDirectory;
import org.airahub.interophub.service.PersonNames;

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
        chip(out, contextPath, code, "/small-groups", "Small Groups", active);
        chip(out, contextPath, code, "/checklist", "Checklist", active);
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
            out.println("        <div class=\"aira-cluster\">");
            out.println("          <a class=\"aira-button aira-button--secondary\" href=\""
                    + InitiativeHtml.esc(contextPath + "/es/initiatives/templates") + "\">Templates</a>");
            out.println("          <a class=\"aira-button aira-button--primary\" href=\""
                    + InitiativeHtml.esc(contextPath + "/es/initiatives/new") + "\">New initiative</a>");
            out.println("        </div>");
        }
        out.println("      </div>");
        if (initiatives.isEmpty()) {
            out.println("      <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">No initiatives yet.</p>");
            out.println("        <p>An administrator can create one and add managers.</p></div>");
            return;
        }
        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-wrap\"><table class=\"aira-table\">");
        out.println("        <caption class=\"aira-visually-hidden\">Initiatives</caption>");
        out.println("        <thead><tr><th scope=\"col\" class=\"aira-table__cell--primary\">Initiative</th><th scope=\"col\">Status</th><th scope=\"col\">Primary topic</th><th scope=\"col\">Visibility</th></tr></thead><tbody>");
        for (EsInitiative initiative : initiatives) {
            out.println("          <tr><th scope=\"row\" class=\"aira-table__cell--primary\"><a class=\"aira-inline-link\" href=\""
                    + InitiativeHtml.esc(contextPath + "/es/initiatives/" + initiative.getInitiativeCode()) + "\">"
                    + InitiativeHtml.esc(initiative.getInitiativeName()) + "</a></th>");
            out.println("            <td>" + badge(initiative.getStatus().name(), initiative.getStatus().getLabel()) + "</td>");
            out.println("            <td>" + InitiativeHtml.esc(topicNames.getOrDefault(initiative.getPrimaryEsTopicId(), ""))
                    + "</td>");
            out.println("            <td>" + InitiativeHtml.esc(initiative.getVisibility().getLabel()) + "</td></tr>");
        }
        out.println("        </tbody></table></div></section>");
    }

    static void createForm(PrintWriter out, String contextPath, List<EsTopic> topics, FormInput input) {
        out.println("      <div class=\"aira-page-header\"><div>");
        out.println("        <h1 class=\"aira-page-title\">New initiative</h1>");
        out.println("        <p class=\"aira-meta\"><a class=\"aira-inline-link\" href=\""
                + InitiativeHtml.esc(contextPath + "/es/initiatives") + "\">All initiatives</a></p>");
        out.println("      </div></div>");
        out.println("      <form class=\"aira-form aira-form--narrow\" method=\"post\" action=\""
                + InitiativeHtml.esc(contextPath + "/es/initiatives/new") + "\">");
        formError(out, input, "create");
        fields(out, input, "create", null, topics, true);
        out.println("        <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--primary\" type=\"submit\">Create initiative</button></div>");
        out.println("      </form>");
    }

    static void overview(PrintWriter out, String contextPath, EsInitiative initiative, EsTopic topic,
            List<EsInitiativeLink> links, InitiativeRosterRules.Representation representation,
            List<EsInitiativeTimelineEntry> timeline, Map<Long, String> userNames, User viewer, boolean canComment,
            FormInput input) {
        header(out, initiative);
        out.println("      <section class=\"aira-panel aira-stack aira-form--narrow\">");
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
        out.println("      <section class=\"aira-panel aira-form--narrow\"><h2 class=\"aira-section-title\">Links</h2>");
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
        String postUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode();
        out.println("      <section class=\"aira-stack\">");
        out.println("        <h2 class=\"aira-section-title\">History</h2>");
        timeline(out, postUrl, timeline, userNames, viewer, canComment, EsInitiativeTimelineEntry.SubjectType.INITIATIVE,
                initiative.getEsInitiativeId(), input);
        out.println("      </section>");
    }

    static void organizations(PrintWriter out, String contextPath, EsInitiative initiative,
            List<EsInitiativeOrganization> participations, Map<Long, Organization> organizations,
            List<EsInitiativeMember> members, InitiativeRosterRules.Representation representation, boolean canEdit,
            String statusFilter, String typeFilter, FormInput input) {
        header(out, initiative);
        representationBlock(out, representation);
        String pageUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/organizations";
        String postUrl = pageUrl + filterQuery("status", statusFilter, "type", typeFilter);
        out.println("      <form class=\"aira-form aira-form--inline\" method=\"get\" action=\""
                + InitiativeHtml.esc(pageUrl) + "\">");
        out.println("        <div class=\"aira-field\"><label for=\"orgFilterStatus\">Status</label>");
        out.println("          <select class=\"aira-select\" id=\"orgFilterStatus\" name=\"status\">");
        out.println("            <option value=\"\">Any</option>");
        statusOptions(out, EsInitiativeOrganization.ParticipationStatus.values(), statusFilter,
                EsInitiativeOrganization.ParticipationStatus::getLabel);
        out.println("          </select></div>");
        out.println("        <div class=\"aira-field\"><label for=\"orgFilterType\">Type</label>");
        out.println("          <select class=\"aira-select\" id=\"orgFilterType\" name=\"type\">");
        out.println("            <option value=\"\">Any</option>");
        typeOptions(out, typeFilter);
        out.println("          </select></div>");
        out.println("        <button class=\"aira-button aira-button--secondary\" type=\"submit\">Filter</button>");
        out.println("      </form>");

        Map<Long, List<EsInitiativeMember>> byOrg = groupMembers(members);
        List<EsInitiativeOrganization> visible = new ArrayList<>();
        for (EsInitiativeOrganization participation : participations) {
            if (orgVisible(participation, organizations.get(participation.getOrganizationId()), statusFilter, typeFilter)) {
                visible.add(participation);
            }
        }
        out.println("      <section class=\"aira-table-panel\">");
        out.println("        <div class=\"aira-table-panel__header\"><div>");
        out.println("          <h2 class=\"aira-table-panel__title\">Organizations</h2>");
        out.println("          <p class=\"aira-table-panel__description\">" + visible.size()
                + (visible.size() == participations.size() ? "" : " of " + participations.size()) + "</p>");
        out.println("        </div>");
        if (canEdit) {
            out.println("          <div class=\"aira-table-panel__actions\">");
            out.println("            <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"button\" data-dialog-open=\"add-organization\">Add organization</button>");
            out.println("          </div>");
        }
        out.println("        </div>");
        out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table aira-table--compact\">");
        out.println("          <caption class=\"aira-visually-hidden\">Organizations</caption><thead><tr>");
        out.println("            <th scope=\"col\" class=\"aira-table__cell--primary\">Organization</th>");
        out.println("            <th scope=\"col\">Type</th><th scope=\"col\">Status</th>");
        out.println("            <th scope=\"col\" class=\"aira-table__cell--numeric\">Contacts</th>");
        out.println("            <th scope=\"col\">Needs a contact</th>");
        out.println("            <th scope=\"col\" class=\"aira-table__cell--date\">Last update</th>");
        if (canEdit) {
            out.println("            <th scope=\"col\" class=\"aira-table__cell--actions\"><span class=\"aira-visually-hidden\">Actions</span></th>");
        }
        out.println("          </tr></thead><tbody>");
        if (visible.isEmpty()) {
            out.println("            <tr><td colspan=\"" + (canEdit ? 7 : 6) + "\">No organizations match this filter.</td></tr>");
        }
        for (EsInitiativeOrganization participation : visible) {
            Organization organization = organizations.get(participation.getOrganizationId());
            String name = organization == null ? "Organization" : organization.getOrganizationName();
            String type = organization == null ? "" : organization.getOrganizationType();
            List<EsInitiativeMember> contacts = byOrg.getOrDefault(participation.getEsInitiativeOrganizationId(), List.of());
            List<String> statuses = new ArrayList<>();
            for (EsInitiativeMember contact : contacts) {
                statuses.add(contact.getMemberStatus().name());
            }
            String detailUrl = pageUrl + "/" + participation.getEsInitiativeOrganizationId();
            out.println("            <tr><th scope=\"row\" class=\"aira-table__cell--primary\"><a class=\"aira-inline-link\" href=\""
                    + InitiativeHtml.esc(detailUrl) + "\">" + InitiativeHtml.esc(name) + "</a></th>");
            out.println("              <td>" + InitiativeHtml.esc(OrganizationDirectory.label(type)) + "</td>");
            out.println("              <td>" + badge(participation.getParticipationStatus().name(),
                    participation.getParticipationStatus().getLabel()) + "</td>");
            out.println("              <td class=\"aira-table__cell--numeric\">" + contacts.size() + "</td>");
            out.println("              <td>" + (InitiativeRosterRules.needsContact(statuses)
                    ? "<span class=\"aira-badge aira-badge--warning\">Needs a contact</span>" : "") + "</td>");
            out.println("              <td class=\"aira-table__cell--date\">" + when(participation.getUpdatedAt()) + "</td>");
            if (canEdit) {
                out.println("              <td class=\"aira-table__cell--actions\"><button class=\"aira-button aira-button--small aira-button--secondary\" type=\"button\" data-dialog-open=\"change-org-status\" data-record-id=\""
                        + participation.getEsInitiativeOrganizationId() + "\" data-status=\""
                        + participation.getParticipationStatus().name() + "\" data-dialog-title=\""
                        + InitiativeHtml.esc("Change status for " + name) + "\">Change status</button></td>");
            }
            out.println("            </tr>");
        }
        out.println("          </tbody></table></div></section>");
        if (!canEdit) {
            return;
        }
        dialogStart(out, "add-organization", "Add organization", input, true);
        out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                + InitiativeHtml.esc(postUrl) + "\">");
        out.println("          <h3 class=\"aira-dialog__title\" id=\"add-organization-title\">Add organization</h3>");
        formError(out, input, "add-organization");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"add-organization\" />");
        organizationField(out, contextPath, initiative.getInitiativeCode(), "add-org", input, "add-organization", "", null);
        out.println("          <div class=\"aira-field\"><label for=\"participationStatus\">Status</label>");
        out.println("            <select class=\"aira-select\" id=\"participationStatus\" name=\"participationStatus\" required>");
        statusOptions(out, EsInitiativeOrganization.ParticipationStatus.values(),
                input.value("add-organization", "participationStatus",
                        EsInitiativeOrganization.ParticipationStatus.PROSPECT.name()),
                EsInitiativeOrganization.ParticipationStatus::getLabel);
        out.println("            </select></div>");
        out.println("          <div class=\"aira-field\"><label for=\"orgNote\">Note</label>");
        out.println("            <input class=\"aira-input\" id=\"orgNote\" name=\"note\" type=\"text\" value=\""
                + InitiativeHtml.esc(input.value("add-organization", "note", "")) + "\" /></div>");
        dialogActions(out, "add-organization", "Add organization");
        out.println("        </form></dialog>");
        statusDialog(out, "change-org-status", postUrl, "org-status", "participationId", "participationStatus",
                input, EsInitiativeOrganization.ParticipationStatus.values(),
                EsInitiativeOrganization.ParticipationStatus::getLabel, participations, organizations);
    }

    static void organizationDetail(PrintWriter out, String contextPath, EsInitiative initiative,
            EsInitiativeOrganization participation, Organization organization, List<EsInitiativeMember> contacts,
            List<EsInitiativeTimelineEntry> timeline, Map<Long, String> userNames, Map<Long, User> users, User viewer,
            boolean canEdit, FormInput input) {
        String listUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/organizations";
        String postUrl = listUrl + "/" + participation.getEsInitiativeOrganizationId();
        String name = organization == null ? "Organization" : organization.getOrganizationName();
        String type = organization == null ? "" : organization.getOrganizationType();
        out.println("      <div class=\"aira-page-header\"><div>");
        out.println("        <p class=\"aira-meta\"><a class=\"aira-inline-link\" href=\"" + InitiativeHtml.esc(listUrl)
                + "\">Organizations</a></p>");
        out.println("        <h1 class=\"aira-page-title\">" + InitiativeHtml.esc(name) + "</h1>");
        out.println("      </div></div>");
        if (canEdit) {
            out.println("      <form class=\"aira-form aira-form--inline\" method=\"post\" action=\""
                    + InitiativeHtml.esc(postUrl) + "\">");
            formError(out, input, "org-status");
            out.println("        <input type=\"hidden\" name=\"action\" value=\"org-status\" />");
            out.println("        <input type=\"hidden\" name=\"participationId\" value=\""
                    + participation.getEsInitiativeOrganizationId() + "\" />");
            out.println("        <div class=\"aira-field\"><label for=\"detailOrgStatus\">Status</label>");
            out.println("          <select class=\"aira-select\" id=\"detailOrgStatus\" name=\"participationStatus\" required>");
            statusOptions(out, EsInitiativeOrganization.ParticipationStatus.values(),
                    input.value("org-status", "participationStatus", participation.getParticipationStatus().name()),
                    EsInitiativeOrganization.ParticipationStatus::getLabel);
            out.println("          </select></div>");
            out.println("        <div class=\"aira-field aira-field--grow\"><label for=\"detailOrgNote\">Note</label>");
            out.println("          <input class=\"aira-input\" id=\"detailOrgNote\" name=\"note\" type=\"text\" value=\""
                    + InitiativeHtml.esc(input.value("org-status", "note", "")) + "\" /></div>");
            out.println("        <button class=\"aira-button aira-button--secondary\" type=\"submit\">Update status</button>");
            out.println("      </form>");
            out.println("      <form class=\"aira-form aira-form--inline\" method=\"post\" action=\""
                    + InitiativeHtml.esc(postUrl) + "\">");
            formError(out, input, "org-type");
            out.println("        <input type=\"hidden\" name=\"action\" value=\"org-type\" />");
            out.println("        <input type=\"hidden\" name=\"participationId\" value=\""
                    + participation.getEsInitiativeOrganizationId() + "\" />");
            out.println("        <div class=\"aira-field\"><label for=\"detailOrgType\">Type</label>");
            out.println("          <select class=\"aira-select\" id=\"detailOrgType\" name=\"organizationType\" required>");
            typeOptions(out, input.value("org-type", "organizationType", type));
            out.println("          </select></div>");
            out.println("        <button class=\"aira-button aira-button--secondary\" type=\"submit\">Update type</button>");
            out.println("      </form>");
            out.println("      <p class=\"aira-meta\">Type is shared by every initiative that uses this organization.</p>");
        } else {
            out.println("      <div class=\"aira-cluster\">");
            out.println("        <span class=\"aira-badge aira-badge--subtle\">"
                    + InitiativeHtml.esc(OrganizationDirectory.label(type)) + "</span>");
            out.println("        " + badge(participation.getParticipationStatus().name(),
                    participation.getParticipationStatus().getLabel()));
            out.println("      </div>");
        }
        out.println("      <section class=\"aira-table-panel\">");
        out.println("        <div class=\"aira-table-panel__header\"><div>");
        out.println("          <h2 class=\"aira-table-panel__title\">Contacts</h2>");
        out.println("        </div>");
        if (canEdit) {
            out.println("          <div class=\"aira-table-panel__actions\">");
            out.println("            <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"button\" data-dialog-open=\"add-person\">Add person</button>");
            out.println("          </div>");
        }
        out.println("        </div><div class=\"aira-table-wrap\"><table class=\"aira-table aira-table--compact\">");
        out.println("          <caption class=\"aira-visually-hidden\">Contacts</caption>");
        out.println("          <thead><tr><th scope=\"col\" class=\"aira-table__cell--primary\">Name</th><th scope=\"col\">Status</th><th scope=\"col\">Role</th></tr></thead><tbody>");
        if (contacts.isEmpty()) {
            out.println("            <tr><td colspan=\"3\">No contacts yet.</td></tr>");
        }
        String peopleUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/people/";
        for (EsInitiativeMember contact : contacts) {
            out.println("            <tr><th scope=\"row\" class=\"aira-table__cell--primary\"><a class=\"aira-inline-link\" href=\""
                    + InitiativeHtml.esc(peopleUrl + contact.getEsInitiativeMemberId()) + "\">"
                    + InitiativeHtml.esc(personLabel(contact, users)) + "</a></th>");
            out.println("              <td>" + badge(contact.getMemberStatus().name(), contact.getMemberStatus().getLabel())
                    + "</td>");
            out.println("              <td>" + InitiativeHtml.esc(contact.getInitiativeRole()) + "</td></tr>");
        }
        out.println("          </tbody></table></div></section>");
        if (canEdit && organization != null) {
            dialogStart(out, "add-person", "Add person", input, true);
            out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                    + InitiativeHtml.esc(postUrl) + "\">");
            out.println("          <h3 class=\"aira-dialog__title\" id=\"add-person-title\">Add person</h3>");
            formError(out, input, "add-member");
            out.println("          <input type=\"hidden\" name=\"action\" value=\"add-member\" />");
            out.println("          <input type=\"hidden\" name=\"personIntent\" value=\"contact\" />");
            personSearch(out, contextPath, initiative.getInitiativeCode(), "contact", input, "add-member", true);
            out.println("          <div class=\"aira-field\"><span class=\"aira-label\">Organization</span>");
            out.println("            <p>" + InitiativeHtml.esc(name) + "</p>");
            out.println("            <input type=\"hidden\" name=\"organizationId\" value=\""
                    + organization.getOrganizationId() + "\" /></div>");
            personRoleStatus(out, input, "add-member", "contact", EsInitiativeMember.MemberStatus.PROSPECT.name());
            dialogActions(out, "add-person", "Add person");
            out.println("        </form></dialog>");
        }
        out.println("      <section class=\"aira-stack\">");
        out.println("        <h2 class=\"aira-section-title\">History</h2>");
        timeline(out, postUrl, timeline, userNames, viewer, canEdit, EsInitiativeTimelineEntry.SubjectType.ORGANIZATION,
                participation.getEsInitiativeOrganizationId(), input);
        out.println("      </section>");
    }

    static void people(PrintWriter out, String contextPath, EsInitiative initiative, List<EsInitiativeMember> members,
            List<EsInitiativeOrganization> participations, Map<Long, Organization> organizations,
            java.util.Set<Long> missingFollow, String statusFilter, String organizationFilter, String typeFilter,
            boolean canEdit, Map<Long, User> users, FormInput input) {
        header(out, initiative);
        String pageUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/people";
        String postUrl = pageUrl + filterQuery("status", statusFilter, "organization", organizationFilter, "type", typeFilter);
        out.println("      <form class=\"aira-form aira-form--inline\" method=\"get\" action=\""
                + InitiativeHtml.esc(pageUrl) + "\">");
        out.println("        <div class=\"aira-field\"><label for=\"filterStatus\">Status</label>");
        out.println("          <select class=\"aira-select\" id=\"filterStatus\" name=\"status\">");
        out.println("            <option value=\"\">Any</option>");
        statusOptions(out, EsInitiativeMember.MemberStatus.values(), statusFilter, EsInitiativeMember.MemberStatus::getLabel);
        out.println("          </select></div>");
        out.println("        <div class=\"aira-field aira-field--grow\"><label for=\"filterOrg\">Organization</label>");
        out.println("          <select class=\"aira-select\" id=\"filterOrg\" name=\"organization\">");
        out.println("            <option value=\"\">Any</option>");
        for (EsInitiativeOrganization participation : participations) {
            Organization organization = organizations.get(participation.getOrganizationId());
            String selected = String.valueOf(participation.getEsInitiativeOrganizationId()).equals(organizationFilter)
                    ? " selected" : "";
            out.println("            <option value=\"" + participation.getEsInitiativeOrganizationId() + "\"" + selected + ">"
                    + InitiativeHtml.esc(organization == null ? "Organization" : organization.getOrganizationName())
                    + "</option>");
        }
        out.println("          </select></div>");
        out.println("        <div class=\"aira-field\"><label for=\"filterType\">Organization type</label>");
        out.println("          <select class=\"aira-select\" id=\"filterType\" name=\"type\">");
        out.println("            <option value=\"\">Any</option>");
        typeOptions(out, typeFilter);
        out.println("          </select></div>");
        out.println("        <button class=\"aira-button aira-button--secondary\" type=\"submit\">Filter</button>");
        out.println("      </form>");
        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-panel__header\"><div>");
        out.println("        <h2 class=\"aira-table-panel__title\">People</h2>");
        out.println("        <p class=\"aira-table-panel__description\">" + members.size() + "</p></div>");
        if (canEdit) {
            out.println("        <div class=\"aira-table-panel__actions\">");
            out.println("          <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"button\" data-dialog-open=\"add-person\">Add person</button>");
            out.println("        </div>");
        }
        out.println("      </div><div class=\"aira-table-wrap\"><table class=\"aira-table aira-table--compact\">");
        out.println("        <caption class=\"aira-visually-hidden\">People</caption><thead><tr>");
        out.println("          <th scope=\"col\" class=\"aira-table__cell--primary\">Name</th>");
        out.println("          <th scope=\"col\">Organization</th><th scope=\"col\">Type</th><th scope=\"col\">Status</th>");
        out.println("          <th scope=\"col\">Role</th><th scope=\"col\">Primary topic</th>");
        if (canEdit) {
            out.println("          <th scope=\"col\" class=\"aira-table__cell--actions\"><span class=\"aira-visually-hidden\">Actions</span></th>");
        }
        out.println("        </tr></thead><tbody>");
        if (members.isEmpty()) {
            out.println("          <tr><td colspan=\"" + (canEdit ? 7 : 6) + "\">No people match this filter.</td></tr>");
        }
        for (EsInitiativeMember member : members) {
            Organization organization = organizationFor(member, participations, organizations);
            EsInitiativeOrganization participation = participationFor(member, participations);
            String type = organization == null ? "" : OrganizationDirectory.label(organization.getOrganizationType());
            out.println("          <tr><th scope=\"row\" class=\"aira-table__cell--primary\"><a class=\"aira-inline-link\" href=\""
                    + InitiativeHtml.esc(pageUrl + "/" + member.getEsInitiativeMemberId()) + "\">"
                    + InitiativeHtml.esc(personLabel(member, users)) + "</a></th>");
            if (organization != null && participation != null) {
                out.println("            <td><a class=\"aira-inline-link\" href=\""
                        + InitiativeHtml.esc(contextPath + "/es/initiatives/" + initiative.getInitiativeCode()
                                + "/organizations/" + participation.getEsInitiativeOrganizationId())
                        + "\">" + InitiativeHtml.esc(organization.getOrganizationName()) + "</a></td>");
            } else {
                out.println("            <td></td>");
            }
            out.println("            <td>" + InitiativeHtml.esc(type) + "</td>");
            out.println("            <td>" + badge(member.getMemberStatus().name(), member.getMemberStatus().getLabel())
                    + "</td>");
            out.println("            <td class=\"aira-table__cell--wrap\">" + InitiativeHtml.esc(member.getInitiativeRole())
                    + "</td>");
            out.println("            <td>" + (missingFollow.contains(member.getEsInitiativeMemberId())
                    ? "<span class=\"aira-badge aira-badge--warning\">Not following</span>" : "") + "</td>");
            if (canEdit) {
                out.println("            <td class=\"aira-table__cell--actions\"><button class=\"aira-button aira-button--small aira-button--secondary\" type=\"button\" data-dialog-open=\"change-member-status\" data-record-id=\""
                        + member.getEsInitiativeMemberId() + "\" data-status=\"" + member.getMemberStatus().name()
                        + "\" data-dialog-title=\"" + InitiativeHtml.esc("Change status for " + personLabel(member, users))
                        + "\">Change status</button></td>");
            }
            out.println("          </tr>");
        }
        out.println("        </tbody></table></div></section>");
        if (!canEdit) {
            return;
        }
        dialogStart(out, "add-person", "Add person", input, true);
        out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                + InitiativeHtml.esc(postUrl) + "\">");
        out.println("          <h3 class=\"aira-dialog__title\" id=\"add-person-title\">Add person</h3>");
        formError(out, input, "add-member");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"add-member\" />");
        out.println("          <input type=\"hidden\" name=\"personIntent\" value=\"create\" />");
        personSearch(out, contextPath, initiative.getInitiativeCode(), "person", input, "add-member", false);
        organizationField(out, contextPath, initiative.getInitiativeCode(), "add-person", input, "add-member", "", null);
        personRoleStatus(out, input, "add-member", "person", EsInitiativeMember.MemberStatus.PROSPECT.name());
        dialogActions(out, "add-person", "Add person");
        out.println("        </form></dialog>");
        memberStatusDialog(out, postUrl, input);
    }

    static void personDetail(PrintWriter out, String contextPath, EsInitiative initiative, EsInitiativeMember member,
            List<EsInitiativeOrganization> participations, Map<Long, Organization> organizations,
            List<EsInitiativeTimelineEntry> timeline, Map<Long, String> userNames, Map<Long, User> users, User viewer,
            boolean missingFollow, boolean canEdit, FormInput input) {
        String listUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/people";
        String postUrl = listUrl + "/" + member.getEsInitiativeMemberId();
        Organization organization = organizationFor(member, participations, organizations);
        out.println("      <div class=\"aira-page-header\"><div>");
        out.println("        <p class=\"aira-meta\"><a class=\"aira-inline-link\" href=\"" + InitiativeHtml.esc(listUrl)
                + "\">People</a></p>");
        out.println("        <h1 class=\"aira-page-title\">" + InitiativeHtml.esc(personLabel(member, users)) + "</h1>");
        out.println("      </div></div>");
        out.println("      <div class=\"aira-cluster\">");
        out.println("        " + badge(member.getMemberStatus().name(), member.getMemberStatus().getLabel()));
        if (member.getEmail() != null) {
            out.println("        <span class=\"aira-meta\">" + InitiativeHtml.esc(member.getEmail()) + "</span>");
        }
        if (missingFollow) {
            out.println("        <span class=\"aira-badge aira-badge--warning\">Joined, but no longer follows the primary topic</span>");
        }
        out.println("      </div>");
        if (canEdit) {
            out.println("      <form class=\"aira-form aira-form--inline\" method=\"post\" action=\""
                    + InitiativeHtml.esc(postUrl) + "\">");
            formError(out, input, "member-status");
            out.println("        <input type=\"hidden\" name=\"action\" value=\"member-status\" />");
            out.println("        <input type=\"hidden\" name=\"memberId\" value=\"" + member.getEsInitiativeMemberId()
                    + "\" />");
            out.println("        <div class=\"aira-field\"><label for=\"detailPersonStatus\">Status</label>");
            out.println("          <select class=\"aira-select\" id=\"detailPersonStatus\" name=\"memberStatus\" required>");
            statusOptions(out, EsInitiativeMember.MemberStatus.values(),
                    input.value("member-status", "memberStatus", member.getMemberStatus().name()),
                    EsInitiativeMember.MemberStatus::getLabel);
            out.println("          </select></div>");
            out.println("        <div class=\"aira-field aira-field--grow\"><label for=\"detailPersonNote\">Note</label>");
            out.println("          <input class=\"aira-input\" id=\"detailPersonNote\" name=\"note\" type=\"text\" value=\""
                    + InitiativeHtml.esc(input.value("member-status", "note", "")) + "\" /></div>");
            out.println("        <button class=\"aira-button aira-button--secondary\" type=\"submit\">Update status</button>");
            out.println("      </form>");
            out.println("      <form class=\"aira-form aira-form--narrow\" method=\"post\" action=\""
                    + InitiativeHtml.esc(postUrl) + "\">");
            formError(out, input, "edit-member");
            out.println("        <input type=\"hidden\" name=\"action\" value=\"edit-member\" />");
            out.println("        <input type=\"hidden\" name=\"memberId\" value=\"" + member.getEsInitiativeMemberId()
                    + "\" />");
            out.println("        <div class=\"aira-field-row\">");
            out.println("          <div class=\"aira-field\"><label for=\"editPersonFirst\">First name</label>");
            out.println("            <input class=\"aira-input\" id=\"editPersonFirst\" name=\"firstName\" type=\"text\" maxlength=\"100\" value=\""
                    + InitiativeHtml.esc(input.value("edit-member", "firstName", member.getContactFirstName()))
                    + "\" /></div>");
            out.println("          <div class=\"aira-field\"><label for=\"editPersonLast\">Last name</label>");
            out.println("            <input class=\"aira-input\" id=\"editPersonLast\" name=\"lastName\" type=\"text\" maxlength=\"100\" value=\""
                    + InitiativeHtml.esc(input.value("edit-member", "lastName", member.getContactLastName()))
                    + "\" /></div>");
            out.println("          <div class=\"aira-field\"><label for=\"editPersonEmail\">Email</label>");
            out.println("            <input class=\"aira-input\" id=\"editPersonEmail\" name=\"email\" type=\"email\" value=\""
                    + InitiativeHtml.esc(input.value("edit-member", "email", member.getEmail())) + "\" /></div>");
            out.println("        </div>");
            out.println("        <div class=\"aira-field\"><label for=\"editPersonRole\">Role in the initiative</label>");
            out.println("          <input class=\"aira-input\" id=\"editPersonRole\" name=\"initiativeRole\" type=\"text\" maxlength=\"200\" value=\""
                    + InitiativeHtml.esc(input.value("edit-member", "initiativeRole", member.getInitiativeRole()))
                    + "\" /></div>");
            organizationField(out, contextPath, initiative.getInitiativeCode(), "edit-person", input, "edit-member",
                    organization == null ? "" : organization.getOrganizationName(),
                    organization == null ? null : organization.getOrganizationId());
            out.println("        <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--primary\" type=\"submit\">Save person</button></div>");
            out.println("      </form>");
        }
        out.println("      <section class=\"aira-stack\">");
        out.println("        <h2 class=\"aira-section-title\">History</h2>");
        timeline(out, postUrl, timeline, userNames, viewer, canEdit, EsInitiativeTimelineEntry.SubjectType.MEMBER,
                member.getEsInitiativeMemberId(), input);
        out.println("      </section>");
    }

    static void settings(PrintWriter out, String contextPath, EsInitiative initiative, List<EsTopic> topics,
            List<EsInitiativeManager> managers, Map<Long, User> users, List<EsInitiativeLink> links, FormInput input) {
        header(out, initiative);
        String postUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/settings";
        out.println("      <form class=\"aira-form aira-form--narrow\" method=\"post\" action=\""
                + InitiativeHtml.esc(postUrl) + "\">");
        formError(out, input, "save-settings");
        out.println("        <input type=\"hidden\" name=\"action\" value=\"save-settings\" />");
        fields(out, input, "save-settings", initiative, topics, false);
        out.println("        <p class=\"aira-meta\">Changing the primary topic does not change follows that are already in place. Joined members who do not follow the new topic are flagged on the people roster.</p>");
        out.println("        <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--primary\" type=\"submit\">Save settings</button></div>");
        out.println("      </form>");

        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-panel__header\"><div>");
        out.println("        <h2 class=\"aira-table-panel__title\">Managers</h2></div>");
        out.println("        <div class=\"aira-table-panel__actions\">");
        out.println("          <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"button\" data-dialog-open=\"add-manager\">Add manager</button>");
        out.println("        </div></div>");
        out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table\">");
        out.println("          <caption class=\"aira-visually-hidden\">Managers</caption>");
        out.println("          <thead><tr><th scope=\"col\" class=\"aira-table__cell--primary\">Person</th><th scope=\"col\">Role</th><th scope=\"col\" class=\"aira-table__cell--actions\"><span class=\"aira-visually-hidden\">Actions</span></th></tr></thead><tbody>");
        if (managers.isEmpty()) {
            out.println("            <tr><td colspan=\"3\">No managers yet.</td></tr>");
        }
        for (EsInitiativeManager manager : managers) {
            User user = users.get(manager.getUserId());
            String label = user == null ? "User " + manager.getUserId()
                    : (user.getEmail() == null ? user.getFullName() : user.getFullName() + " · " + user.getEmail());
            out.println("            <tr><th scope=\"row\" class=\"aira-table__cell--primary\">" + InitiativeHtml.esc(label)
                    + "</th><td>");
            out.println("              <form class=\"aira-form aira-form--inline\" method=\"post\" action=\""
                    + InitiativeHtml.esc(postUrl) + "\">");
            out.println("                <input type=\"hidden\" name=\"action\" value=\"manager-role\" />");
            out.println("                <input type=\"hidden\" name=\"managerId\" value=\""
                    + manager.getEsInitiativeManagerId() + "\" />");
            out.println("                <div class=\"aira-field\"><label class=\"aira-visually-hidden\" for=\"role-"
                    + manager.getEsInitiativeManagerId() + "\">Role for " + InitiativeHtml.esc(label) + "</label>");
            out.println("                  <select class=\"aira-select\" id=\"role-" + manager.getEsInitiativeManagerId()
                    + "\" name=\"managerRole\">");
            for (EsInitiativeManager.ManagerRole role : EsInitiativeManager.ManagerRole.values()) {
                String selected = role == manager.getManagerRole() ? " selected" : "";
                out.println("                    <option value=\"" + role.name() + "\"" + selected + ">"
                        + InitiativeHtml.esc(role.getLabel()) + "</option>");
            }
            out.println("                  </select></div>");
            out.println("                <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"submit\">Update</button>");
            out.println("              </form></td><td class=\"aira-table__cell--actions\">");
            out.println("              <form method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
            out.println("                <input type=\"hidden\" name=\"action\" value=\"remove-manager\" />");
            out.println("                <input type=\"hidden\" name=\"managerId\" value=\""
                    + manager.getEsInitiativeManagerId() + "\" />");
            out.println("                <button class=\"aira-button aira-button--small aira-button--ghost\" type=\"submit\">Remove</button>");
            out.println("              </form></td></tr>");
        }
        out.println("          </tbody></table></div></section>");
        dialogStart(out, "add-manager", "Add manager", input);
        out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                + InitiativeHtml.esc(postUrl) + "\">");
        out.println("          <h3 class=\"aira-dialog__title\" id=\"add-manager-title\">Add manager</h3>");
        formError(out, input, "add-manager");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"add-manager\" />");
        out.println("          <div class=\"aira-field\"><label for=\"managerEmail\">Email of an InteropHub account</label>");
        out.println("            <input class=\"aira-input\" id=\"managerEmail\" name=\"managerEmail\" type=\"email\" required value=\""
                + InitiativeHtml.esc(input.value("add-manager", "managerEmail", "")) + "\" /></div>");
        out.println("          <div class=\"aira-field\"><label for=\"managerRole\">Role</label>");
        out.println("            <select class=\"aira-select\" id=\"managerRole\" name=\"managerRole\" required>");
        for (EsInitiativeManager.ManagerRole role : EsInitiativeManager.ManagerRole.values()) {
            String selected = role.name().equals(input.value("add-manager", "managerRole",
                    EsInitiativeManager.ManagerRole.EDITOR.name())) ? " selected" : "";
            out.println("              <option value=\"" + role.name() + "\"" + selected + ">"
                    + InitiativeHtml.esc(role.getLabel()) + "</option>");
        }
        out.println("            </select></div>");
        dialogActions(out, "add-manager", "Add manager");
        out.println("        </form></dialog>");

        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-panel__header\"><div>");
        out.println("        <h2 class=\"aira-table-panel__title\">Links</h2></div>");
        out.println("        <div class=\"aira-table-panel__actions\">");
        out.println("          <button class=\"aira-button aira-button--small aira-button--secondary\" type=\"button\" data-dialog-open=\"add-link\">Add link</button>");
        out.println("        </div></div>");
        out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table\">");
        out.println("          <caption class=\"aira-visually-hidden\">Links</caption>");
        out.println("          <thead><tr><th scope=\"col\" class=\"aira-table__cell--primary\">Label</th><th scope=\"col\">URL</th><th scope=\"col\" class=\"aira-table__cell--actions\"><span class=\"aira-visually-hidden\">Actions</span></th></tr></thead><tbody>");
        if (links.isEmpty()) {
            out.println("            <tr><td colspan=\"3\">No links yet.</td></tr>");
        }
        for (EsInitiativeLink link : links) {
            out.println("            <tr><th scope=\"row\" class=\"aira-table__cell--primary\"><a class=\"aira-inline-link\" href=\""
                    + InitiativeHtml.esc(link.getLinkUrl()) + "\" target=\"_blank\" rel=\"noopener\">"
                    + InitiativeHtml.esc(link.getLinkLabel()) + "</a></th>");
            out.println("              <td class=\"aira-table__cell--wrap\">" + InitiativeHtml.esc(link.getLinkUrl())
                    + "</td><td class=\"aira-table__cell--actions\">");
            out.println("              <form method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
            out.println("                <input type=\"hidden\" name=\"action\" value=\"delete-link\" />");
            out.println("                <input type=\"hidden\" name=\"linkId\" value=\"" + link.getEsInitiativeLinkId()
                    + "\" />");
            out.println("                <button class=\"aira-button aira-button--small aira-button--ghost\" type=\"submit\">Remove</button>");
            out.println("              </form></td></tr>");
        }
        out.println("          </tbody></table></div></section>");
        dialogStart(out, "add-link", "Add link", input);
        out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                + InitiativeHtml.esc(postUrl) + "\">");
        out.println("          <h3 class=\"aira-dialog__title\" id=\"add-link-title\">Add link</h3>");
        formError(out, input, "add-link");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"add-link\" />");
        out.println("          <div class=\"aira-field\"><label for=\"linkLabel\">Label</label>");
        out.println("            <input class=\"aira-input\" id=\"linkLabel\" name=\"linkLabel\" type=\"text\" required maxlength=\"200\" value=\""
                + InitiativeHtml.esc(input.value("add-link", "linkLabel", "")) + "\" /></div>");
        out.println("          <div class=\"aira-field\"><label for=\"linkUrl\">URL</label>");
        out.println("            <input class=\"aira-input\" id=\"linkUrl\" name=\"linkUrl\" type=\"url\" required maxlength=\"1000\" pattern=\"https?://.+\" title=\"Start with http:// or https://\" value=\""
                + InitiativeHtml.esc(input.value("add-link", "linkUrl", "")) + "\" /></div>");
        dialogActions(out, "add-link", "Add link");
        out.println("        </form></dialog>");
    }

    static void importPage(PrintWriter out, String contextPath, EsInitiative initiative, List<String> headers,
            InitiativeImport.ColumnMapping mapping, InitiativeImport.Preview preview, FormInput input) {
        header(out, initiative);
        String postUrl = contextPath + "/es/initiatives/" + initiative.getInitiativeCode() + "/import";
        out.println("      <form class=\"aira-form aira-form--narrow\" method=\"post\" enctype=\"multipart/form-data\" action=\""
                + InitiativeHtml.esc(postUrl) + "\">");
        formError(out, input, "preview");
        out.println("        <input type=\"hidden\" name=\"action\" value=\"preview\" />");
        out.println("        <p class=\"aira-meta\">Map the columns in the file, check the preview, then import. A row with an organization and no contact adds only the organization. Notes become the first timeline entry.</p>");
        out.println("        <div class=\"aira-field\"><label for=\"importFile\">Spreadsheet (.xlsx or .csv)</label>");
        out.println("          <input id=\"importFile\" name=\"file\" type=\"file\" accept=\".xlsx,.xlsm,.csv,text/csv\" required /></div>");
        out.println("        <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--secondary\" type=\"submit\">Upload and preview</button></div>");
        out.println("      </form>");
        if (headers == null || mapping == null) {
            if (input.failed("remap") || input.failed("commit")) {
                formError(out, input, input.failed("remap") ? "remap" : "commit");
            }
            return;
        }
        out.println("      <form class=\"aira-form aira-form--narrow\" method=\"post\" action=\""
                + InitiativeHtml.esc(postUrl) + "\">");
        formError(out, input, "remap");
        out.println("        <input type=\"hidden\" name=\"action\" value=\"remap\" />");
        out.println("        <h2 class=\"aira-section-title\">Column mapping</h2>");
        out.println("        <div class=\"aira-field-row\">");
        mappingSelect(out, "mapContact", "Point of contact", headers, mapped(input, "mapContact", mapping.contact()));
        mappingSelect(out, "mapEmail", "Email", headers, mapped(input, "mapEmail", mapping.email()));
        mappingSelect(out, "mapOrganization", "Organization", headers,
                mapped(input, "mapOrganization", mapping.organization()));
        mappingSelect(out, "mapOrganizationType", "Organization type", headers,
                mapped(input, "mapOrganizationType", mapping.organizationType()));
        mappingSelect(out, "mapNotes", "Notes", headers, mapped(input, "mapNotes", mapping.notes()));
        out.println("        </div>");
        out.println("        <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--secondary\" type=\"submit\">Update preview</button></div>");
        out.println("      </form>");
        if (preview == null) {
            return;
        }
        out.println("      <section class=\"aira-table-panel\"><div class=\"aira-table-panel__header\"><div>");
        out.println("        <h2 class=\"aira-table-panel__title\">Preview</h2>");
        out.println("        <p class=\"aira-table-panel__description\">" + preview.memberCount() + " people, "
                + preview.organizationCount() + " organizations, " + preview.errorCount()
                + " rows that will not be imported.</p></div></div>");
        out.println("        <div class=\"aira-table-wrap\"><table class=\"aira-table\">");
        out.println("          <caption class=\"aira-visually-hidden\">Import preview</caption>");
        out.println("          <thead><tr><th scope=\"col\">Row</th><th scope=\"col\">What will happen</th><th scope=\"col\">Detail</th></tr></thead><tbody>");
        if (preview.rows().isEmpty()) {
            out.println("            <tr><td colspan=\"3\">The file has no rows to import.</td></tr>");
        }
        for (InitiativeImport.PreviewRow row : preview.rows()) {
            out.println("            <tr><td>" + row.rowNumber() + "</td><td>" + InitiativeHtml.esc(previewLabel(row))
                    + "</td><td class=\"aira-table__cell--wrap\">" + InitiativeHtml.esc(row.detail()) + "</td></tr>");
        }
        out.println("          </tbody></table></div>");
        out.println("        <form class=\"aira-form\" method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
        formError(out, input, "commit");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"commit\" />");
        out.println("          <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--primary\" type=\"submit\">Import these rows</button></div>");
        out.println("        </form></section>");
    }

    private static void memberStatusDialog(PrintWriter out, String postUrl, FormInput input) {
        dialogStart(out, "change-member-status", "Change status", input);
        out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                + InitiativeHtml.esc(postUrl) + "\">");
        String heading = "Change status";
        out.println("          <h3 class=\"aira-dialog__title\" id=\"change-member-status-title\" data-dialog-heading>"
                + InitiativeHtml.esc(heading) + "</h3>");
        formError(out, input, "member-status");
        out.println("          <input type=\"hidden\" name=\"action\" value=\"member-status\" />");
        out.println("          <input data-record-id type=\"hidden\" name=\"memberId\" value=\""
                + InitiativeHtml.esc(input.value("member-status", "memberId", "")) + "\" />");
        out.println("          <div class=\"aira-field\"><label for=\"memberStatusDialog\">Status</label>");
        out.println("            <select class=\"aira-select\" id=\"memberStatusDialog\" name=\"memberStatus\" data-status-select required>");
        statusOptions(out, EsInitiativeMember.MemberStatus.values(),
                input.value("member-status", "memberStatus", ""), EsInitiativeMember.MemberStatus::getLabel);
        out.println("            </select></div>");
        out.println("          <div class=\"aira-field\"><label for=\"memberStatusNote\">Note</label>");
        out.println("            <input class=\"aira-input\" id=\"memberStatusNote\" name=\"note\" type=\"text\" data-status-note value=\""
                + InitiativeHtml.esc(input.value("member-status", "note", "")) + "\" /></div>");
        dialogActions(out, "change-member-status", "Update status");
        out.println("        </form></dialog>");
    }

    private static void statusDialog(PrintWriter out, String id, String postUrl, String action, String idName,
            String statusName, FormInput input, EsInitiativeOrganization.ParticipationStatus[] values,
            java.util.function.Function<EsInitiativeOrganization.ParticipationStatus, String> labels,
            List<EsInitiativeOrganization> participations, Map<Long, Organization> organizations) {
        String heading = "Change status";
        if (input.failed(action)) {
            String postedId = input.posted(idName);
            for (EsInitiativeOrganization participation : participations) {
                if (postedId.equals(String.valueOf(participation.getEsInitiativeOrganizationId()))) {
                    Organization organization = organizations.get(participation.getOrganizationId());
                    heading = "Change status for "
                            + (organization == null ? "organization" : organization.getOrganizationName());
                }
            }
        }
        dialogStart(out, id, heading, input);
        out.println("        <form class=\"aira-stack aira-stack--compact\" method=\"post\" action=\""
                + InitiativeHtml.esc(postUrl) + "\">");
        out.println("          <h3 class=\"aira-dialog__title\" id=\"" + id + "-title\" data-dialog-heading>"
                + InitiativeHtml.esc(heading) + "</h3>");
        formError(out, input, action);
        out.println("          <input type=\"hidden\" name=\"action\" value=\"" + action + "\" />");
        out.println("          <input data-record-id type=\"hidden\" name=\"" + idName + "\" value=\""
                + InitiativeHtml.esc(input.value(action, idName, "")) + "\" />");
        out.println("          <div class=\"aira-field\"><label for=\"" + id + "-status\">Status</label>");
        out.println("            <select class=\"aira-select\" id=\"" + id + "-status\" name=\"" + statusName
                + "\" data-status-select required>");
        statusOptions(out, values, input.value(action, statusName, ""), labels);
        out.println("            </select></div>");
        out.println("          <div class=\"aira-field\"><label for=\"" + id + "-note\">Note</label>");
        out.println("            <input class=\"aira-input\" id=\"" + id + "-note\" name=\"note\" type=\"text\" data-status-note value=\""
                + InitiativeHtml.esc(input.value(action, "note", "")) + "\" /></div>");
        dialogActions(out, id, "Update status");
        out.println("        </form></dialog>");
    }

    static void dialogStart(PrintWriter out, String id, String title, FormInput input) {
        dialogStart(out, id, title, input, false);
    }

    static void dialogStart(PrintWriter out, String id, String title, FormInput input, boolean popover) {
        String open = id.equals(input.openDialog()) ? " data-open-on-load" : "";
        String type = popover ? "aira-dialog aira-dialog--popover-visible" : "aira-dialog";
        out.println("      <dialog class=\"" + type + "\" id=\"" + id + "\" aria-labelledby=\"" + id + "-title\"" + open
                + ">");
    }

    static void dialogActions(PrintWriter out, String id, String submitLabel) {
        out.println("          <div class=\"aira-dialog__actions aira-form-actions aira-form-actions--end\">");
        out.println("            <button class=\"aira-button aira-button--ghost\" type=\"button\" data-dialog-close=\""
                + id + "\">Cancel</button>");
        out.println("            <button class=\"aira-button aira-button--primary\" type=\"submit\">"
                + InitiativeHtml.esc(submitLabel) + "</button>");
        out.println("          </div>");
    }

    static void personSearch(PrintWriter out, String contextPath, String code, String prefix, FormInput input,
            String action, boolean allowRoster) {
        String suggest = contextPath + "/es/initiatives/" + code + "/people-search";
        boolean showNew = input.failed(action) && "NEW".equals(input.value(action, "personSource", ""));
        out.println("          <div data-people-search data-suggest-url=\"" + InitiativeHtml.esc(suggest) + "\""
                + (allowRoster ? " data-people-allow-roster" : "") + ">");
        out.println("            <div class=\"aira-field aira-dialog-search\">");
        out.println("              <label for=\"" + prefix + "Person\">Person</label>");
        out.println("              <input class=\"aira-input\" id=\"" + prefix + "Person\" data-people-query type=\"search\" autocomplete=\"off\" placeholder=\"Name, email, or organization\" />");
        out.println("              <div class=\"aira-search-popover\" hidden data-people-popover><div class=\"aira-search-listbox\" role=\"listbox\" data-people-list></div></div>");
        out.println("            </div>");
        out.println("            <p class=\"aira-meta\" data-people-chosen hidden></p>");
        out.println("            <input type=\"hidden\" name=\"personSource\" data-people-source value=\""
                + InitiativeHtml.esc(input.value(action, "personSource", "")) + "\" />");
        out.println("            <input type=\"hidden\" name=\"memberId\" data-people-member value=\""
                + InitiativeHtml.esc(input.value(action, "memberId", "")) + "\" />");
        out.println("            <input type=\"hidden\" name=\"userId\" data-people-user value=\""
                + InitiativeHtml.esc(input.value(action, "userId", "")) + "\" />");
        out.println("            <div data-people-new" + (showNew ? "" : " hidden") + ">");
        out.println("              <div class=\"aira-field-row\">");
        out.println("                <div class=\"aira-field\"><label for=\"" + prefix + "First\">First name</label>");
        out.println("                  <input class=\"aira-input\" id=\"" + prefix + "First\" name=\"firstName\" type=\"text\" maxlength=\"100\" value=\""
                + InitiativeHtml.esc(input.value(action, "firstName", "")) + "\" /></div>");
        out.println("                <div class=\"aira-field\"><label for=\"" + prefix + "Last\">Last name</label>");
        out.println("                  <input class=\"aira-input\" id=\"" + prefix + "Last\" name=\"lastName\" type=\"text\" maxlength=\"100\" value=\""
                + InitiativeHtml.esc(input.value(action, "lastName", "")) + "\" /></div>");
        out.println("              </div>");
        out.println("              <div class=\"aira-field\"><label for=\"" + prefix + "Email\">Email</label>");
        out.println("                <input class=\"aira-input\" id=\"" + prefix + "Email\" name=\"email\" type=\"email\" value=\""
                + InitiativeHtml.esc(input.value(action, "email", "")) + "\" /></div>");
        out.println("            </div></div>");
    }

    static String personLabel(EsInitiativeMember member, Map<Long, User> users) {
        if (member == null) {
            return "Unnamed contact";
        }
        User account = users == null || member.getUserId() == null ? null : users.get(member.getUserId());
        return PersonNames.display(account == null ? null : account.getDisplayName(),
                account == null ? null : account.getFirstName(), account == null ? null : account.getLastName(),
                member.getContactFirstName(), member.getContactLastName(), member.getEmail());
    }

    private static void personRoleStatus(PrintWriter out, FormInput input, String action, String prefix,
            String defaultStatus) {
        out.println("          <div class=\"aira-field\"><label for=\"" + prefix + "Role\">Role in the initiative</label>");
        out.println("            <input class=\"aira-input\" id=\"" + prefix + "Role\" name=\"initiativeRole\" type=\"text\" maxlength=\"200\" value=\""
                + InitiativeHtml.esc(input.value(action, "initiativeRole", "")) + "\" /></div>");
        out.println("          <div class=\"aira-field-row\">");
        out.println("            <div class=\"aira-field\"><label for=\"" + prefix + "Status\">Status</label>");
        out.println("              <select class=\"aira-select\" id=\"" + prefix + "Status\" name=\"memberStatus\" required>");
        statusOptions(out, EsInitiativeMember.MemberStatus.values(),
                input.value(action, "memberStatus", defaultStatus), EsInitiativeMember.MemberStatus::getLabel);
        out.println("              </select></div>");
        out.println("            <div class=\"aira-field\"><label for=\"" + prefix + "Note\">Note</label>");
        out.println("              <input class=\"aira-input\" id=\"" + prefix + "Note\" name=\"note\" type=\"text\" value=\""
                + InitiativeHtml.esc(input.value(action, "note", "")) + "\" /></div>");
        out.println("          </div>");
    }

    private static String previewLabel(InitiativeImport.PreviewRow row) {
        String who = row.contactName() != null ? row.contactName()
                : (row.email() != null ? row.email() : row.organizationName());
        return switch (row.action()) {
            case ADD_MEMBER -> "Add " + (who == null ? "person" : who);
            case ADD_ORGANIZATION -> "Add organization " + (row.organizationName() == null ? "" : row.organizationName());
            case SKIP -> "Skip";
            case ERROR -> "Needs attention";
        };
    }

    private static int mapped(FormInput input, String name, int fallback) {
        if (!input.failed("remap")) {
            return fallback;
        }
        try {
            return Integer.parseInt(input.posted(name));
        } catch (NumberFormatException ex) {
            return -1;
        }
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

    private static void fields(PrintWriter out, FormInput input, String action, EsInitiative initiative,
            List<EsTopic> topics, boolean creating) {
        String name = input.value(action, "name", initiative == null ? "" : initiative.getInitiativeName());
        String code = input.value(action, "code", initiative == null ? "" : initiative.getInitiativeCode());
        String summary = input.value(action, "summary", initiative == null ? "" : InitiativeHtml.text(initiative.getSummary()));
        String description = input.value(action, "description",
                initiative == null ? "" : InitiativeHtml.text(initiative.getDescription()));
        out.println("        <div class=\"aira-field-row\">");
        out.println("          <div class=\"aira-field\"><label for=\"initiativeName\">Name</label>");
        out.println("            <input class=\"aira-input\" id=\"initiativeName\" name=\"name\" type=\"text\" required maxlength=\"200\" value=\""
                + InitiativeHtml.esc(name) + "\" /></div>");
        out.println("          <div class=\"aira-field\"><label for=\"initiativeCode\">Code</label>");
        out.println("            <input class=\"aira-input\" id=\"initiativeCode\" name=\"code\" type=\"text\" maxlength=\"80\" pattern=\"[a-z0-9]+(?:-[a-z0-9]+)*\" title=\"Use lowercase letters, numbers, and hyphens.\" value=\""
                + InitiativeHtml.esc(code) + "\" />");
        out.println("            <p class=\"aira-meta\">Used in the page address. Leave blank to build it from the name.</p></div>");
        out.println("        </div>");
        out.println("        <div class=\"aira-field\"><label for=\"summary\">Summary</label>");
        out.println("          <textarea class=\"aira-textarea\" id=\"summary\" name=\"summary\">" + InitiativeHtml.esc(summary)
                + "</textarea></div>");
        out.println("        <div class=\"aira-field\"><label for=\"description\">Description</label>");
        out.println("          <textarea class=\"aira-textarea\" id=\"description\" name=\"description\">"
                + InitiativeHtml.esc(description) + "</textarea></div>");
        out.println("        <div class=\"aira-field\"><label for=\"primaryEsTopicId\">Primary topic</label>");
        out.println("          <select class=\"aira-select\" id=\"primaryEsTopicId\" name=\"primaryEsTopicId\" required>");
        out.println("            <option value=\"\">Choose a topic</option>");
        String selectedTopic = input.value(action, "primaryEsTopicId",
                initiative == null || initiative.getPrimaryEsTopicId() == null ? ""
                        : String.valueOf(initiative.getPrimaryEsTopicId()));
        for (EsTopic topic : topics) {
            String selected = String.valueOf(topic.getEsTopicId()).equals(selectedTopic) ? " selected" : "";
            out.println("            <option value=\"" + topic.getEsTopicId() + "\"" + selected + ">"
                    + InitiativeHtml.esc(topic.getTopicName()) + "</option>");
        }
        out.println("          </select></div>");
        out.println("        <div class=\"aira-field-row\">");
        enumSelect(out, "status", "Status", EsInitiative.Status.values(),
                input.value(action, "status", initiative == null ? EsInitiative.Status.PLANNING.name()
                        : initiative.getStatus().name()),
                value -> ((EsInitiative.Status) value).getLabel());
        enumSelect(out, "visibility", "Visibility", EsInitiative.Visibility.values(),
                input.value(action, "visibility", initiative == null ? EsInitiative.Visibility.PRIVATE.name()
                        : initiative.getVisibility().name()),
                value -> ((EsInitiative.Visibility) value).getLabel());
        enumSelect(out, "joinPolicy", "Join policy", EsInitiative.JoinPolicy.values(),
                input.value(action, "joinPolicy", initiative == null ? EsInitiative.JoinPolicy.INVITE_ONLY.name()
                        : initiative.getJoinPolicy().name()),
                value -> ((EsInitiative.JoinPolicy) value).getLabel());
        out.println("        </div>");
        out.println("        <div class=\"aira-field-row\">");
        dateField(out, "startDate", "Start date", input.value(action, "startDate",
                initiative == null || initiative.getStartDate() == null ? "" : initiative.getStartDate().toString()));
        dateField(out, "endDate", "End date", input.value(action, "endDate",
                initiative == null || initiative.getEndDate() == null ? "" : initiative.getEndDate().toString()));
        out.println("        </div>");
        if (creating) {
            out.println("        <p class=\"aira-meta\">You will be added as an admin manager. Global administrators can always open an initiative.</p>");
        }
    }

    private static void enumSelect(PrintWriter out, String name, String label, Enum<?>[] values, String selected,
            java.util.function.Function<Enum<?>, String> labels) {
        out.println("          <div class=\"aira-field\"><label for=\"" + name + "\">" + label + "</label>");
        out.println("            <select class=\"aira-select\" id=\"" + name + "\" name=\"" + name + "\" required>");
        for (Enum<?> value : values) {
            String mark = value.name().equals(selected) ? " selected" : "";
            out.println("              <option value=\"" + value.name() + "\"" + mark + ">"
                    + InitiativeHtml.esc(labels.apply(value)) + "</option>");
        }
        out.println("            </select></div>");
    }

    private static <E extends Enum<E>> void statusOptions(PrintWriter out, E[] values, String selected,
            java.util.function.Function<E, String> labels) {
        for (E value : values) {
            String mark = value.name().equals(selected) ? " selected" : "";
            out.println("              <option value=\"" + value.name() + "\"" + mark + ">"
                    + InitiativeHtml.esc(labels.apply(value)) + "</option>");
        }
    }

    private static void typeOptions(PrintWriter out, String selected) {
        for (OrganizationDirectory.OrganizationType organizationType : OrganizationDirectory.TYPES) {
            String mark = organizationType.code().equals(selected) ? " selected" : "";
            out.println("              <option value=\"" + organizationType.code() + "\"" + mark + ">"
                    + InitiativeHtml.esc(organizationType.label()) + "</option>");
        }
    }

    private static void dateField(PrintWriter out, String name, String label, String value) {
        out.println("          <div class=\"aira-field\"><label for=\"" + name + "\">" + label + "</label>");
        out.println("            <input class=\"aira-input\" id=\"" + name + "\" name=\"" + name + "\" type=\"date\" value=\""
                + InitiativeHtml.esc(value) + "\" /></div>");
    }

    static void organizationField(PrintWriter out, String contextPath, String code, String prefix,
            FormInput input, String action, String currentName, Long organizationId) {
        String suggest = contextPath + "/es/initiatives/" + code + "/organization-suggest";
        String query = input.value(action, "organizationQuery", currentName);
        String postedId = input.value(action, "organizationId", organizationId == null ? "" : String.valueOf(organizationId));
        String newName = input.value(action, "newOrganizationName", "");
        String newType = input.value(action, "newOrganizationType", "");
        String website = input.value(action, "newOrganizationWebsite", "");
        boolean showNew = input.failed(action) && postedId.isBlank()
                && (!newName.isBlank() || !newType.isBlank() || !website.isBlank());
        String shownNewName = showNew && newName.isBlank() ? query : newName;
        out.println("          <div class=\"aira-dialog-search\" data-org-typeahead data-suggest-url=\""
                + InitiativeHtml.esc(suggest) + "\">");
        out.println("            <div class=\"aira-field\"><label for=\"" + prefix + "-organization-query\">Organization</label>");
        out.println("              <input class=\"aira-input\" id=\"" + prefix + "-organization-query\" name=\"organizationQuery\" data-org-query type=\"text\" autocomplete=\"off\" value=\""
                + InitiativeHtml.esc(query) + "\" placeholder=\"Search organizations\" />");
        out.println("              <input data-org-id type=\"hidden\" name=\"organizationId\" value=\""
                + InitiativeHtml.esc(postedId) + "\" /></div>");
        out.println("            <div class=\"aira-search-popover\" hidden data-org-popover><div class=\"aira-search-listbox\" data-org-list role=\"listbox\"></div></div>");
        out.println("            <div data-org-new" + (showNew ? "" : " hidden") + ">");
        String required = showNew ? " required" : "";
        out.println("              <div class=\"aira-field\"><label for=\"" + prefix + "-new-name\">New organization name</label>");
        out.println("                <input class=\"aira-input\" id=\"" + prefix + "-new-name\" data-org-new-name name=\"newOrganizationName\" type=\"text\""
                + required + " maxlength=\"200\" value=\"" + InitiativeHtml.esc(shownNewName) + "\" /></div>");
        out.println("              <div class=\"aira-field\"><label for=\"" + prefix + "-new-type\">Type</label>");
        out.println("                <select class=\"aira-select\" id=\"" + prefix + "-new-type\" data-org-new-type name=\"newOrganizationType\""
                + required + ">");
        out.println("                  <option value=\"\">Choose a type</option>");
        typeOptions(out, newType);
        out.println("                </select></div>");
        out.println("              <div class=\"aira-field\"><label for=\"" + prefix + "-new-website\">Website</label>");
        out.println("                <input class=\"aira-input\" id=\"" + prefix + "-new-website\" name=\"newOrganizationWebsite\" type=\"text\" maxlength=\"500\" value=\""
                + InitiativeHtml.esc(website) + "\" /></div>");
        out.println("            </div></div>");
    }

    private static void representationBlock(PrintWriter out, InitiativeRosterRules.Representation representation) {
        out.println("      <div class=\"aira-cluster\" aria-label=\"Representation\">");
        out.println("        <span class=\"aira-badge aira-badge--subtle\">" + representation.committedOrganizations()
                + " committed " + (representation.committedOrganizations() == 1 ? "organization" : "organizations")
                + "</span>");
        out.println("        <span class=\"aira-badge aira-badge--"
                + (representation.jurisdictionPresent() ? "success" : "warning") + "\">Jurisdiction: "
                + (representation.jurisdictionPresent() ? "present" : "not yet") + "</span>");
        out.println("        <span class=\"aira-badge aira-badge--"
                + (representation.atLeastThreeOrganizations() ? "success" : "warning") + "\">At least 3 organizations: "
                + (representation.atLeastThreeOrganizations() ? "yes" : "not yet") + "</span>");
        for (InitiativeRosterRules.TypeCount count : representation.committedByType()) {
            out.println("        <span class=\"aira-chip\">" + InitiativeHtml.esc(count.label()) + " " + count.count()
                    + "</span>");
        }
        out.println("      </div>");
    }

    static void timeline(PrintWriter out, String postUrl, List<EsInitiativeTimelineEntry> entries,
            Map<Long, String> userNames, User viewer, boolean canComment, EsInitiativeTimelineEntry.SubjectType type,
            Long subjectId, FormInput input) {
        if (entries.isEmpty()) {
            out.println("        <p class=\"aira-meta\">No history yet.</p>");
        } else if (entries.size() > InitiativeTimelineText.PREVIEW_COUNT) {
            out.println("        <p><button class=\"aira-button aira-button--small aira-button--ghost\" type=\"button\" data-timeline-more>Show all</button></p>");
        }
        String editDialogId = "edit-comment-" + type.name() + "-" + subjectId;
        int firstVisible = InitiativeTimelineText.firstVisible(entries.size(), false);
        for (int index = 0; index < entries.size(); index++) {
            EsInitiativeTimelineEntry entry = entries.get(index);
            String who = userNames.getOrDefault(entry.getCreatedByUserId(), "Someone");
            boolean ownComment = canComment && viewer != null && InitiativeRosterRules.canEditEntry(entry.getEntryType(),
                    entry.getCreatedByUserId(), viewer.getUserId());
            String hidden = index < firstVisible ? " hidden data-timeline-older" : "";
            out.println("        <p class=\"aira-meta\"" + hidden + ">" + InitiativeHtml.esc(InitiativeTimelineText.line(
                    entry.getCreatedAt(), who, entry.getEntryType(), entry.getFromValue(), entry.getToValue(),
                    entry.getNoteText(), entry.getEditedAt() != null)));
            if (ownComment) {
                out.println("          <button class=\"aira-button aira-button--link\" type=\"button\" data-dialog-open=\""
                        + editDialogId + "\" data-record-id=\"" + entry.getEsInitiativeTimelineEntryId()
                        + "\" data-comment=\"" + InitiativeHtml.esc(entry.getNoteText()).replace("\r", "&#13;").replace("\n", "&#10;")
                        + "\">Edit</button>");
            }
            out.println("        </p>");
        }
        if (canComment && entries.stream().anyMatch(entry -> viewer != null && InitiativeRosterRules.canEditEntry(
                entry.getEntryType(), entry.getCreatedByUserId(), viewer.getUserId()))) {
            dialogStart(out, editDialogId, "Edit comment", input);
            out.println("        <form method=\"post\" action=\"" + InitiativeHtml.esc(postUrl) + "\">");
            out.println("          <h3 class=\"aira-dialog__title\" id=\"" + editDialogId + "-title\">Edit comment</h3>");
            formError(out, input, "edit-comment");
            out.println("          <input type=\"hidden\" name=\"action\" value=\"edit-comment\" />");
            out.println("          <input data-record-id type=\"hidden\" name=\"entryId\" value=\""
                    + InitiativeHtml.esc(input.value("edit-comment", "entryId", "")) + "\" />");
            out.println("          <div class=\"aira-field\"><label for=\"" + editDialogId + "-note\">Comment</label>");
            out.println("            <textarea class=\"aira-textarea\" id=\"" + editDialogId
                    + "-note\" name=\"note\" data-comment-note required>"
                    + InitiativeHtml.esc(input.value("edit-comment", "note", "")) + "</textarea></div>");
            dialogActions(out, editDialogId, "Save comment");
            out.println("        </form></dialog>");
        }
        if (canComment) {
            out.println("        <form class=\"aira-form aira-form--narrow\" method=\"post\" action=\""
                    + InitiativeHtml.esc(postUrl) + "\">");
            formError(out, input, "comment");
            out.println("          <input type=\"hidden\" name=\"action\" value=\"comment\" />");
            out.println("          <input type=\"hidden\" name=\"subjectType\" value=\"" + type.name() + "\" />");
            out.println("          <input type=\"hidden\" name=\"subjectId\" value=\"" + subjectId + "\" />");
            out.println("          <div class=\"aira-field\"><label for=\"comment-" + type.name() + "-" + subjectId
                    + "\">Comment</label>");
            out.println("            <textarea class=\"aira-textarea\" id=\"comment-" + type.name() + "-" + subjectId
                    + "\" name=\"note\" required>" + InitiativeHtml.esc(input.value("comment", "note", ""))
                    + "</textarea></div>");
            out.println("          <div class=\"aira-form-actions\"><button class=\"aira-button aira-button--secondary\" type=\"submit\">Add comment</button></div>");
            out.println("        </form>");
        }
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

    private static Map<Long, List<EsInitiativeMember>> groupMembers(List<EsInitiativeMember> members) {
        Map<Long, List<EsInitiativeMember>> grouped = new LinkedHashMap<>();
        for (EsInitiativeMember member : members) {
            if (member.getEsInitiativeOrganizationId() != null) {
                grouped.computeIfAbsent(member.getEsInitiativeOrganizationId(), id -> new ArrayList<>()).add(member);
            }
        }
        return grouped;
    }

    private static boolean orgVisible(EsInitiativeOrganization participation, Organization organization,
            String statusFilter, String typeFilter) {
        if (statusFilter != null && !statusFilter.isBlank()
                && !statusFilter.equals(participation.getParticipationStatus().name())) {
            return false;
        }
        String type = organization == null ? "" : organization.getOrganizationType();
        return typeFilter == null || typeFilter.isBlank() || typeFilter.equals(type);
    }

    private static EsInitiativeOrganization participationFor(EsInitiativeMember member,
            List<EsInitiativeOrganization> participations) {
        if (member.getEsInitiativeOrganizationId() == null) {
            return null;
        }
        for (EsInitiativeOrganization participation : participations) {
            if (member.getEsInitiativeOrganizationId().equals(participation.getEsInitiativeOrganizationId())) {
                return participation;
            }
        }
        return null;
    }

    private static Organization organizationFor(EsInitiativeMember member, List<EsInitiativeOrganization> participations,
            Map<Long, Organization> organizations) {
        EsInitiativeOrganization participation = participationFor(member, participations);
        return participation == null ? null : organizations.get(participation.getOrganizationId());
    }

    static void formError(PrintWriter out, FormInput input, String action) {
        if (!input.failed(action)) {
            return;
        }
        out.println("          <div class=\"aira-alert aira-alert--error\"><p>"
                + InitiativeHtml.esc(input.error()) + "</p></div>");
    }

    static String badge(String code, String label) {
        String tone = switch (code) {
            case "COMMITTED", "JOINED", "ACTIVE" -> "success";
            case "DECLINED", "WITHDRAWN", "NOT_ACCEPTED", "LEFT", "ARCHIVED", "CANCELLED", "REMOVED" -> "danger";
            case "PROSPECT", "PLANNING" -> "subtle";
            case "CLOSED", "HANDOFF" -> "warning";
            default -> "info";
        };
        return "<span class=\"aira-badge aira-badge--" + tone + "\">" + InitiativeHtml.esc(label) + "</span>";
    }

    private static String when(LocalDateTime value) {
        return value == null ? "—" : DATE_FORMAT.format(value);
    }

    private static String filterQuery(String... pairs) {
        StringBuilder query = new StringBuilder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String value = pairs[i + 1];
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return query.isEmpty() ? "" : "?" + query;
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
}
