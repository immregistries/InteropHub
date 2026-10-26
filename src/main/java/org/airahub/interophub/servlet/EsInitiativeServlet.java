package org.airahub.interophub.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.model.EsChecklist;
import org.airahub.interophub.model.EsChecklistItem;
import org.airahub.interophub.model.EsChecklistTemplate;
import org.airahub.interophub.model.EsInitiative;
import org.airahub.interophub.model.EsInitiativeLink;
import org.airahub.interophub.model.EsInitiativeManager;
import org.airahub.interophub.model.EsInitiativeMember;
import org.airahub.interophub.model.EsInitiativeOrganization;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.Organization;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.ChecklistFeedback;
import org.airahub.interophub.service.ChecklistHints;
import org.airahub.interophub.service.ChecklistMerge;
import org.airahub.interophub.service.ChecklistService;
import org.airahub.interophub.service.InitiativeAccess;
import org.airahub.interophub.service.InitiativeImport;
import org.airahub.interophub.service.InitiativeRosterRules;
import org.airahub.interophub.service.InitiativeService;
import org.airahub.interophub.service.OrganizationDirectory;
import org.immregistries.aira.web.AiraPage;

public class EsInitiativeServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(EsInitiativeServlet.class.getName());
    private static final String FLASH = "initiativeFlash";
    private static final String FLASH_KIND = "initiativeFlashKind";

    private final AuthFlowService authFlowService = new AuthFlowService();
    private final InitiativeService initiatives = new InitiativeService();
    private final ChecklistService checklists = new ChecklistService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        User user = requireUser(request, response);
        if (user == null) {
            return;
        }
        String[] parts = parts(request);
        if (parts.length > 0 && "templates".equals(parts[0])) {
            if (!InitiativeAccess.canManageTemplates(authFlowService.isAdminUser(user))) {
                renderMissing(request, response);
                return;
            }
            if (serveTemplateDownload(request, response, parts)) {
                return;
            }
            renderTemplates(request, response, parts, FormInput.none(), null);
            return;
        }
        if (parts.length == 0) {
            renderList(request, response, user);
            return;
        }
        if ("new".equals(parts[0])) {
            if (!authFlowService.isAdminUser(user)) {
                renderMissing(request, response);
                return;
            }
            renderCreate(request, response, FormInput.none());
            return;
        }
        EsInitiative initiative = initiatives.findByCode(parts[0]).orElse(null);
        InitiativeAccess.Level level = initiatives.access(user, initiative);
        if (initiative == null || !InitiativeAccess.canView(level)) {
            renderMissing(request, response);
            return;
        }
        if (parts.length > 1 && "organization-suggest".equals(parts[1])) {
            suggest(request, response, initiative, level);
            return;
        }
        if (parts.length == 3 && "checklist".equals(parts[1]) && "download".equals(parts[2])) {
            String markdown = checklists.download(initiative);
            response.setContentType("text/markdown;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + initiative.getInitiativeCode()
                    + "-checklist.md\"");
            response.getWriter().write(markdown);
            return;
        }
        renderCurrent(request, response, user, initiative, level, parts, FormInput.none());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        User user = requireUser(request, response);
        if (user == null) {
            return;
        }
        String[] parts = parts(request);
        try {
            if (parts.length > 0 && "templates".equals(parts[0])) {
                if (!InitiativeAccess.canManageTemplates(authFlowService.isAdminUser(user))) {
                    renderMissing(request, response);
                    return;
                }
                String action = param(request, "action");
                if ("save-template".equals(action)) {
                    checklists.saveUpload(user, param(request, "markdown"));
                    flash(request, "success", "Template saved.");
                    response.sendRedirect(request.getContextPath() + "/es/initiatives/templates");
                } else {
                    ChecklistService.TemplatePreview preview = checklists.previewUpload(param(request, "markdown"));
                    if (!preview.ok()) {
                        throw new IllegalArgumentException(preview.errorText());
                    }
                    renderTemplates(request, response, parts, FormInput.replay(request, "preview-template", null, null),
                            preview);
                }
                return;
            }
            if (parts.length == 1 && "new".equals(parts[0])) {
                if (!authFlowService.isAdminUser(user)) {
                    renderMissing(request, response);
                    return;
                }
                EsInitiative created = initiatives.create(user, param(request, "name"), param(request, "code"),
                        param(request, "summary"), param(request, "description"), longParam(request, "primaryEsTopicId"),
                        enumParam(EsInitiative.Status.class, param(request, "status")),
                        enumParam(EsInitiative.Visibility.class, param(request, "visibility")),
                        enumParam(EsInitiative.JoinPolicy.class, param(request, "joinPolicy")),
                        dateParam(request, "startDate"), dateParam(request, "endDate"));
                flash(request, "success", "Initiative created.");
                redirect(response, request, created, "");
                return;
            }
            if (parts.length == 0) {
                response.sendRedirect(request.getContextPath() + "/es/initiatives");
                return;
            }
            EsInitiative initiative = initiatives.findByCode(parts[0]).orElse(null);
            InitiativeAccess.Level level = initiatives.access(user, initiative);
            if (initiative == null || !InitiativeAccess.canView(level)) {
                renderMissing(request, response);
                return;
            }
            String section = parts.length > 1 ? parts[1] : "overview";
            String action = param(request, "action");
            if (requiresSettings(action) && !InitiativeAccess.canManageSettings(level)) {
                throw new IllegalArgumentException("You don't have permission to change these settings.");
            }
            if (!requiresSettings(action) && !InitiativeAccess.canEditRoster(level)) {
                throw new IllegalArgumentException("You don't have permission to change this initiative.");
            }
            String warning = dispatch(request, user, initiative, section, action);
            boolean keepsItsOwnMessage = "commit".equals(action) || "preview".equals(action) || "remap".equals(action);
            if (warning != null) {
                flash(request, "warning", warning);
            } else if (!keepsItsOwnMessage) {
                flash(request, "success", "Saved.");
            } else if (!"commit".equals(action)) {
                flash(request, "success", "Preview updated. Import only after you have checked it.");
            }
            redirect(response, request, initiative, redirectAfter(parts, action));
        } catch (IllegalArgumentException ex) {
            if (parts.length > 0 && "templates".equals(parts[0])) {
                renderTemplates(request, response, parts,
                        FormInput.replay(request, param(request, "action"), ex.getMessage(), null), null);
                return;
            }
            if (parts.length == 1 && "new".equals(parts[0])) {
                renderCreate(request, response, FormInput.replay(request, "create", ex.getMessage(), null));
                return;
            }
            EsInitiative initiative = parts.length == 0 ? null : initiatives.findByCode(parts[0]).orElse(null);
            InitiativeAccess.Level level = initiatives.access(user, initiative);
            if (initiative == null || !InitiativeAccess.canView(level)) {
                renderMissing(request, response);
                return;
            }
            String action = param(request, "action");
            renderCurrent(request, response, user, initiative, level, parts,
                    FormInput.replay(request, action == null ? "" : action, ex.getMessage(), dialogFor(request, parts, action)));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.SEVERE, "Initiative update failed", ex);
            flash(request, "danger", "Could not save that change.");
            response.sendRedirect(request.getContextPath() + "/es/initiatives");
        }
    }

    private String dispatch(HttpServletRequest request, User user, EsInitiative initiative, String section, String action)
            throws IOException {
        return switch (action == null ? "" : action) {
            case "save-settings" -> {
                initiatives.updateSettings(user, initiative, param(request, "name"), param(request, "code"),
                        param(request, "summary"), param(request, "description"), longParam(request, "primaryEsTopicId"),
                        enumParam(EsInitiative.Status.class, param(request, "status")),
                        enumParam(EsInitiative.Visibility.class, param(request, "visibility")),
                        enumParam(EsInitiative.JoinPolicy.class, param(request, "joinPolicy")),
                        dateParam(request, "startDate"), dateParam(request, "endDate"));
                yield null;
            }
            case "add-manager" -> {
                initiatives.addManager(user, initiative, param(request, "managerEmail"),
                        enumParam(EsInitiativeManager.ManagerRole.class, param(request, "managerRole")));
                yield null;
            }
            case "manager-role" -> {
                initiatives.changeManagerRole(user, initiative, longParam(request, "managerId"),
                        enumParam(EsInitiativeManager.ManagerRole.class, param(request, "managerRole")));
                yield null;
            }
            case "remove-manager" -> {
                initiatives.removeManager(user, initiative, longParam(request, "managerId"));
                yield null;
            }
            case "add-link" -> {
                initiatives.addLink(user, initiative, param(request, "linkLabel"), param(request, "linkUrl"));
                yield null;
            }
            case "delete-link" -> {
                initiatives.deleteLink(initiative, longParam(request, "linkId"));
                yield null;
            }
            case "add-organization" -> {
                initiatives.addOrganization(user, initiative, longParam(request, "organizationId"),
                        param(request, "newOrganizationName"), param(request, "newOrganizationType"),
                        param(request, "newOrganizationWebsite"),
                        enumParam(EsInitiativeOrganization.ParticipationStatus.class, param(request, "participationStatus")),
                        param(request, "note"));
                yield null;
            }
            case "org-status" -> {
                initiatives.changeOrganizationStatus(user, initiative, longParam(request, "participationId"),
                        enumParam(EsInitiativeOrganization.ParticipationStatus.class, param(request, "participationStatus")),
                        param(request, "note"));
                yield null;
            }
            case "org-type" -> {
                initiatives.changeOrganizationType(user, initiative, longParam(request, "participationId"),
                        param(request, "organizationType"));
                yield null;
            }
            case "add-member" -> initiatives.addMember(user, initiative, param(request, "name"), param(request, "email"),
                    longParam(request, "organizationId"), param(request, "newOrganizationName"),
                    param(request, "newOrganizationType"), param(request, "newOrganizationWebsite"),
                    param(request, "initiativeRole"),
                    enumParam(EsInitiativeMember.MemberStatus.class, param(request, "memberStatus")),
                    param(request, "note"));
            case "edit-member" -> {
                initiatives.updateMember(user, initiative, longParam(request, "memberId"), param(request, "name"),
                        param(request, "email"), longParam(request, "organizationId"), param(request, "newOrganizationName"),
                        param(request, "newOrganizationType"), param(request, "newOrganizationWebsite"),
                        param(request, "initiativeRole"));
                yield null;
            }
            case "member-status" -> initiatives.changeMemberStatus(user, initiative, longParam(request, "memberId"),
                    enumParam(EsInitiativeMember.MemberStatus.class, param(request, "memberStatus")),
                    param(request, "note"));
            case "comment" -> {
                initiatives.addComment(user, initiative,
                        enumParam(EsInitiativeTimelineEntry.SubjectType.class, param(request, "subjectType")),
                        longParam(request, "subjectId"), param(request, "note"));
                yield null;
            }
            case "edit-comment" -> {
                initiatives.editComment(user, initiative, longParam(request, "entryId"), param(request, "note"));
                yield null;
            }
            case "preview" -> {
                storePreview(request, user, initiative, readUpload(request));
                yield null;
            }
            case "remap" -> {
                HeldImport held = held(request, user, initiative);
                if (held == null) {
                    throw new IllegalArgumentException("Upload the file again before changing the column mapping.");
                }
                InitiativeImport.ParsedSheet sheet = InitiativeImport.parse(held.filename, held.bytes);
                InitiativeImport.ColumnMapping mapping = readMapping(request);
                InitiativeImport.Preview preview = initiatives.previewImport(initiative, sheet, mapping);
                request.getSession().setAttribute(importKey(initiative),
                        new HeldImport(user.getUserId(), held.filename, held.bytes, sheet.headers(), mapping, preview));
                yield null;
            }
            case "commit" -> {
                HeldImport held = held(request, user, initiative);
                if (held == null || held.preview == null) {
                    throw new IllegalArgumentException("Import preview expired. Upload the file again.");
                }
                InitiativeService.ImportResult result = initiatives.commitImport(user, initiative, held.preview);
                request.getSession().removeAttribute(importKey(initiative));
                flash(request, "success", "Imported " + result.membersAdded() + " people and "
                        + result.organizationsAdded() + " organizations.");
                yield null;
            }
            case "start-checklist" -> {
                checklists.startChecklist(initiative);
                yield null;
            }
            case "item-status" -> {
                checklists.setStatus(user, initiative, longParam(request, "itemId"),
                        enumParam(EsChecklistItem.Status.class, param(request, "itemStatus")), param(request, "note"));
                yield null;
            }
            case "add-item", "add-phase" -> {
                checklists.addItem(user, initiative, param(request, "phase"), param(request, "title"),
                        param(request, "guidance"), longParam(request, "afterItemId"));
                yield null;
            }
            case "edit-item" -> {
                checklists.editItem(user, initiative, longParam(request, "itemId"), param(request, "title"),
                        param(request, "guidance"));
                yield null;
            }
            case "remove-item" -> {
                checklists.removeItem(user, initiative, longParam(request, "itemId"));
                yield null;
            }
            case "restore-item" -> {
                checklists.restoreItem(user, initiative, longParam(request, "itemId"));
                yield null;
            }
            case "move-item" -> {
                checklists.move(initiative, longParam(request, "itemId"), param(request, "direction"));
                yield null;
            }
            case "reorder-item" -> {
                checklists.reorder(initiative, longParam(request, "itemId"), longParam(request, "beforeItemId"));
                yield null;
            }
            case "rename-phase" -> {
                checklists.renamePhase(initiative, param(request, "fromPhase"), param(request, "toPhase"));
                yield null;
            }
            case "assign-item" -> {
                checklists.assign(user, initiative, longParam(request, "itemId"), longParam(request, "assignedUserId"));
                yield null;
            }
            case "due-override" -> {
                checklists.setDueOverride(user, initiative, longParam(request, "itemId"), dateParam(request, "dueDate"));
                yield null;
            }
            case "apply-upgrade" -> {
                checklists.applyUpgrade(user, initiative);
                yield null;
            }
            default -> throw new IllegalArgumentException("That action is not recognized.");
        };
    }

    private void renderList(HttpServletRequest request, HttpServletResponse response, User user) throws IOException {
        List<EsInitiative> visible = initiatives.listVisible(user);
        Map<Long, String> topicNames = new HashMap<>();
        for (EsInitiative initiative : visible) {
            initiatives.findTopic(initiative.getPrimaryEsTopicId())
                    .ifPresent(topic -> topicNames.put(topic.getEsTopicId(), topic.getTopicName()));
        }
        page(request, response, "Initiatives - InteropHub", null, null, FormInput.none(), out -> {
            InitiativePageRenderer.list(out, request.getContextPath(), visible, topicNames,
                    authFlowService.isAdminUser(user));
        });
    }

    private void renderCurrent(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative, InitiativeAccess.Level level, String[] parts, FormInput input) throws IOException {
        String section = parts.length > 1 ? parts[1] : "overview";
        switch (section) {
            case "overview" -> renderOverview(request, response, user, initiative, level, input);
            case "organizations" -> {
                if (parts.length == 2) {
                    renderOrganizations(request, response, initiative, level, input);
                } else if (parts.length == 3) {
                    renderOrganization(request, response, user, initiative, level, parts[2], input);
                } else {
                    renderMissing(request, response);
                }
            }
            case "people" -> {
                if (parts.length == 2) {
                    renderPeople(request, response, initiative, level, input);
                } else if (parts.length == 3) {
                    renderPerson(request, response, user, initiative, level, parts[2], input);
                } else {
                    renderMissing(request, response);
                }
            }
            case "settings" -> {
                if (!InitiativeAccess.canManageSettings(level)) {
                    redirect(response, request, initiative, "");
                    return;
                }
                renderSettings(request, response, initiative, input);
            }
            case "import" -> {
                if (!InitiativeAccess.canEditRoster(level)) {
                    redirect(response, request, initiative, "/people");
                    return;
                }
                renderImport(request, response, user, initiative, input);
            }
            case "checklist" -> {
                if (parts.length == 3 && "upgrade".equals(parts[2])) {
                    renderUpgrade(request, response, initiative, level, input);
                } else if (parts.length == 2) {
                    renderChecklist(request, response, user, initiative, level, input);
                } else {
                    renderMissing(request, response);
                }
            }
            default -> renderMissing(request, response);
        }
    }

    private void renderCreate(HttpServletRequest request, HttpServletResponse response, FormInput input) throws IOException {
        page(request, response, "New initiative - InteropHub", null, null, input, out -> {
            InitiativePageRenderer.createForm(out, request.getContextPath(), initiatives.activeTopics(), input);
        });
    }

    private void renderOverview(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative, InitiativeAccess.Level level, FormInput input) throws IOException {
        EsTopic topic = initiatives.findTopic(initiative.getPrimaryEsTopicId()).orElse(null);
        List<EsInitiativeTimelineEntry> timeline = initiatives.timeline(EsInitiativeTimelineEntry.SubjectType.INITIATIVE,
                initiative.getEsInitiativeId());
        page(request, response, initiative.getInitiativeName() + " - InteropHub", initiative, "overview", input,
                out -> InitiativePageRenderer.overview(out, request.getContextPath(), initiative, topic,
                        initiatives.links(initiative.getEsInitiativeId()), representation(initiative), timeline,
                        names(timeline), user, InitiativeAccess.canComment(level), input));
    }

    private void renderOrganizations(HttpServletRequest request, HttpServletResponse response, EsInitiative initiative,
            InitiativeAccess.Level level, FormInput input) throws IOException {
        List<EsInitiativeOrganization> participations = initiatives.organizations(initiative.getEsInitiativeId());
        page(request, response, initiative.getInitiativeName() + " organizations - InteropHub", initiative,
                "organizations", input, out -> InitiativePageRenderer.organizations(out, request.getContextPath(),
                        initiative, participations, indexOrganizations(participations),
                        initiatives.members(initiative.getEsInitiativeId()), representation(initiative),
                        InitiativeAccess.canEditRoster(level), param(request, "status"), param(request, "type"), input));
    }

    private void renderOrganization(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative, InitiativeAccess.Level level, String rawId, FormInput input) throws IOException {
        Long id = pathId(rawId);
        EsInitiativeOrganization participation = id == null ? null : findParticipation(initiative, id);
        if (participation == null) {
            renderMissing(request, response);
            return;
        }
        Organization organization = initiatives.organizationsByIds(Set.of(participation.getOrganizationId())).stream()
                .findFirst().orElse(null);
        List<EsInitiativeMember> contacts = new ArrayList<>();
        for (EsInitiativeMember member : initiatives.members(initiative.getEsInitiativeId())) {
            if (participation.getEsInitiativeOrganizationId().equals(member.getEsInitiativeOrganizationId())) {
                contacts.add(member);
            }
        }
        List<EsInitiativeTimelineEntry> timeline = initiatives.timeline(EsInitiativeTimelineEntry.SubjectType.ORGANIZATION,
                participation.getEsInitiativeOrganizationId());
        String name = organization == null ? "Organization" : organization.getOrganizationName();
        page(request, response, name + " - " + initiative.getInitiativeName() + " - InteropHub", initiative,
                "organizations", input, out -> InitiativePageRenderer.organizationDetail(out, request.getContextPath(),
                        initiative, participation, organization, contacts, timeline, names(timeline), user,
                        InitiativeAccess.canEditRoster(level), input));
    }

    private void renderPeople(HttpServletRequest request, HttpServletResponse response, EsInitiative initiative,
            InitiativeAccess.Level level, FormInput input) throws IOException {
        List<EsInitiativeOrganization> participations = initiatives.organizations(initiative.getEsInitiativeId());
        Map<Long, Organization> organizations = indexOrganizations(participations);
        String statusFilter = param(request, "status");
        String organizationFilter = param(request, "organization");
        String typeFilter = param(request, "type");
        Long organizationId = organizationFilter == null || organizationFilter.isBlank() ? null : pathId(organizationFilter);
        List<EsInitiativeMember> visible = new ArrayList<>();
        for (EsInitiativeMember member : initiatives.members(initiative.getEsInitiativeId())) {
            Organization organization = organizationFor(member, participations, organizations);
            String type = organization == null ? null : organization.getOrganizationType();
            if (InitiativeRosterRules.memberMatches(member.getMemberStatus().name(),
                    member.getEsInitiativeOrganizationId(), type, statusFilter, organizationId, typeFilter)) {
                visible.add(member);
            }
        }
        List<EsSubscription> follows = initiatives.activePrimaryTopicFollows(initiative);
        Set<Long> missing = new HashSet<>();
        for (EsInitiativeMember member : visible) {
            if (!initiatives.followsPrimaryTopic(initiative, member, follows)) {
                missing.add(member.getEsInitiativeMemberId());
            }
        }
        page(request, response, initiative.getInitiativeName() + " people - InteropHub", initiative, "people", input,
                out -> InitiativePageRenderer.people(out, request.getContextPath(), initiative, visible, participations,
                        organizations, missing, statusFilter, organizationFilter, typeFilter,
                        InitiativeAccess.canEditRoster(level), input));
    }

    private void renderPerson(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative, InitiativeAccess.Level level, String rawId, FormInput input) throws IOException {
        Long id = pathId(rawId);
        EsInitiativeMember member = null;
        if (id != null) {
            for (EsInitiativeMember candidate : initiatives.members(initiative.getEsInitiativeId())) {
                if (id.equals(candidate.getEsInitiativeMemberId())) {
                    member = candidate;
                }
            }
        }
        if (member == null) {
            renderMissing(request, response);
            return;
        }
        List<EsInitiativeOrganization> participations = initiatives.organizations(initiative.getEsInitiativeId());
        List<EsSubscription> follows = initiatives.activePrimaryTopicFollows(initiative);
        boolean missingFollow = !initiatives.followsPrimaryTopic(initiative, member, follows);
        List<EsInitiativeTimelineEntry> timeline = initiatives.timeline(EsInitiativeTimelineEntry.SubjectType.MEMBER,
                member.getEsInitiativeMemberId());
        EsInitiativeMember shown = member;
        page(request, response, member.displayName() + " - " + initiative.getInitiativeName() + " - InteropHub",
                initiative, "people", input, out -> InitiativePageRenderer.personDetail(out, request.getContextPath(),
                        initiative, shown, participations, indexOrganizations(participations), timeline, names(timeline),
                        user, missingFollow, InitiativeAccess.canEditRoster(level), input));
    }

    private void renderSettings(HttpServletRequest request, HttpServletResponse response, EsInitiative initiative,
            FormInput input) throws IOException {
        List<EsInitiativeManager> managers = initiatives.managers(initiative.getEsInitiativeId());
        Set<Long> userIds = new HashSet<>();
        for (EsInitiativeManager manager : managers) {
            userIds.add(manager.getUserId());
        }
        Map<Long, User> users = new HashMap<>();
        for (User manager : initiatives.usersByIds(userIds)) {
            users.put(manager.getUserId(), manager);
        }
        EsInitiative current = initiatives.findByCode(initiative.getInitiativeCode()).orElse(initiative);
        page(request, response, current.getInitiativeName() + " settings - InteropHub", current, "settings", input,
                out -> InitiativePageRenderer.settings(out, request.getContextPath(), current, initiatives.activeTopics(),
                        managers, users, initiatives.links(current.getEsInitiativeId()), input));
    }

    private void renderImport(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative, FormInput input) throws IOException {
        HeldImport held = held(request, user, initiative);
        page(request, response, initiative.getInitiativeName() + " import - InteropHub", initiative, "import", input,
                out -> InitiativePageRenderer.importPage(out, request.getContextPath(), initiative,
                        held == null ? null : held.headers, held == null ? null : held.mapping,
                        held == null ? null : held.preview, input));
    }

    private void suggest(HttpServletRequest request, HttpServletResponse response, EsInitiative initiative,
            InitiativeAccess.Level level) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (!InitiativeAccess.canEditRoster(level)) {
            response.getWriter().print("{\"matches\":[]}");
            return;
        }
        List<Organization> matches = initiatives.suggestOrganizations(param(request, "q"));
        StringBuilder json = new StringBuilder("{\"matches\":[");
        for (int i = 0; i < matches.size(); i++) {
            Organization organization = matches.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":").append(organization.getOrganizationId())
                    .append(",\"name\":\"").append(InitiativeHtml.json(organization.getOrganizationName()))
                    .append("\",\"typeLabel\":\"")
                    .append(InitiativeHtml.json(OrganizationDirectory.label(organization.getOrganizationType())))
                    .append("\"}");
        }
        json.append("]}");
        response.getWriter().print(json);
    }

    private InitiativeRosterRules.Representation representation(EsInitiative initiative) {
        List<String> committed = new ArrayList<>();
        Map<Long, Organization> organizations = indexOrganizations(initiatives.organizations(initiative.getEsInitiativeId()));
        for (EsInitiativeOrganization participation : initiatives.organizations(initiative.getEsInitiativeId())) {
            if (participation.getParticipationStatus() == EsInitiativeOrganization.ParticipationStatus.COMMITTED) {
                Organization organization = organizations.get(participation.getOrganizationId());
                if (organization != null) {
                    committed.add(organization.getOrganizationType());
                }
            }
        }
        return InitiativeRosterRules.summarize(committed);
    }

    private Map<Long, Organization> indexOrganizations(List<EsInitiativeOrganization> participations) {
        Set<Long> ids = new HashSet<>();
        for (EsInitiativeOrganization participation : participations) {
            ids.add(participation.getOrganizationId());
        }
        Map<Long, Organization> byId = new HashMap<>();
        for (Organization organization : initiatives.organizationsByIds(ids)) {
            byId.put(organization.getOrganizationId(), organization);
        }
        return byId;
    }

    private Organization organizationFor(EsInitiativeMember member, List<EsInitiativeOrganization> participations,
            Map<Long, Organization> organizations) {
        for (EsInitiativeOrganization participation : participations) {
            if (participation.getEsInitiativeOrganizationId().equals(member.getEsInitiativeOrganizationId())) {
                return organizations.get(participation.getOrganizationId());
            }
        }
        return null;
    }

    private Map<Long, String> names(List<EsInitiativeTimelineEntry> entries) {
        Set<Long> ids = new HashSet<>();
        for (EsInitiativeTimelineEntry entry : entries) {
            if (entry.getCreatedByUserId() != null) {
                ids.add(entry.getCreatedByUserId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        for (User user : initiatives.usersByIds(ids)) {
            names.put(user.getUserId(), user.getFullName());
        }
        return names;
    }

    private void page(HttpServletRequest request, HttpServletResponse response, String title, EsInitiative initiative,
            String section, FormInput input, PageBody body) throws IOException {
        InteropAiraPageFactory.markInitiativesNavActive(request);
        response.setContentType("text/html;charset=UTF-8");
        AiraPage page = InteropAiraPageFactory.base(request, title)
                .applicationSubtitle("Initiatives")
                .mainClass("aira-main")
                .addLocalStylesheet("/css/initiative-org.css")
                .addLocalStylesheet("/css/initiative-checklist.css")
                .context(InteropAiraPageFactory.topicsMeetingsContext("InteropHub", null, false, false))
                .build();
        String kind = null;
        String message = null;
        if (input.error() == null) {
            kind = (String) request.getSession().getAttribute(FLASH_KIND);
            message = (String) request.getSession().getAttribute(FLASH);
        }
        request.getSession().removeAttribute(FLASH_KIND);
        request.getSession().removeAttribute(FLASH);
        try (PrintWriter out = response.getWriter()) {
            page.writeStart(out);
            out.println("    <div class=\"aira-container aira-stack\">");
            if (initiative != null) {
                InitiativeAccess.Level level = initiatives.access(
                        authFlowService.findAuthenticatedUser(request).orElse(null), initiative);
                InitiativePageRenderer.subnav(out, request.getContextPath(), initiative.getInitiativeCode(), section,
                        InitiativeAccess.canEditRoster(level), InitiativeAccess.canManageSettings(level));
            }
            InitiativePageRenderer.flash(out, kind, message);
            body.write(out);
            out.println("    </div>");
            out.println(InteropAiraPageFactory.headerSearchScriptTag(request.getContextPath()));
            out.println("    <script src=\"" + InitiativeHtml.esc(request.getContextPath())
                    + "/js/initiative-dialogs.js\"></script>");
            out.println("    <script src=\"" + InitiativeHtml.esc(request.getContextPath())
                    + "/js/initiative-org-typeahead.js\"></script>");
            out.println("    <script src=\"" + InitiativeHtml.esc(request.getContextPath())
                    + "/js/initiative-checklist.js\"></script>");
            page.writeEnd(out);
        }
    }

    private void renderMissing(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        page(request, response, "Initiative - InteropHub", null, null, FormInput.none(), out -> {
            out.println("      <div class=\"aira-empty-state\"><p class=\"aira-empty-state__title\">That initiative is not available.</p></div>");
        });
    }

    private User requireUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<User> user = authFlowService.findAuthenticatedUser(request);
        if (user.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/home");
            return null;
        }
        return user.get();
    }

    private void storePreview(HttpServletRequest request, User user, EsInitiative initiative, Upload upload) {
        InitiativeImport.ParsedSheet sheet = InitiativeImport.parse(upload.filename, upload.bytes);
        InitiativeImport.ColumnMapping mapping = InitiativeImport.suggest(sheet.headers());
        InitiativeImport.Preview preview = initiatives.previewImport(initiative, sheet, mapping);
        request.getSession().setAttribute(importKey(initiative),
                new HeldImport(user.getUserId(), upload.filename, upload.bytes, sheet.headers(), mapping, preview));
    }

    private Upload readUpload(HttpServletRequest request) throws IOException {
        try {
            Part part = request.getPart("file");
            if (part == null || part.getSize() == 0) {
                throw new IllegalArgumentException("Choose a file to import.");
            }
            if (part.getSize() > 10_000_000) {
                throw new IllegalArgumentException("That file is too large to import.");
            }
            byte[] bytes;
            try (InputStream in = part.getInputStream()) {
                bytes = in.readAllBytes();
            }
            String filename = part.getSubmittedFileName() == null ? "import.xlsx" : part.getSubmittedFileName();
            return new Upload(filename, bytes);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Choose a file to import.");
        }
    }

    private InitiativeImport.ColumnMapping readMapping(HttpServletRequest request) {
        return new InitiativeImport.ColumnMapping(indexParam(request, "mapContact"), indexParam(request, "mapEmail"),
                indexParam(request, "mapOrganization"), indexParam(request, "mapOrganizationType"),
                indexParam(request, "mapNotes"));
    }

    private HeldImport held(HttpServletRequest request, User user, EsInitiative initiative) {
        Object value = request.getSession().getAttribute(importKey(initiative));
        if (!(value instanceof HeldImport held) || held.userId != user.getUserId()) {
            return null;
        }
        return held;
    }

    private String importKey(EsInitiative initiative) {
        return "initiativeImport:" + initiative.getEsInitiativeId();
    }

    private void flash(HttpServletRequest request, String kind, String message) {
        request.getSession().setAttribute(FLASH_KIND, kind);
        request.getSession().setAttribute(FLASH, message);
    }

    private void redirect(HttpServletResponse response, HttpServletRequest request, EsInitiative initiative, String suffix)
            throws IOException {
        EsInitiative current = initiatives.findByCode(initiative.getInitiativeCode()).orElse(initiative);
        String target = request.getContextPath() + "/es/initiatives/" + current.getInitiativeCode() + suffix;
        String query = request.getQueryString();
        if (query != null && !query.isBlank() && query.indexOf('\r') < 0 && query.indexOf('\n') < 0
                && !"commit".equals(param(request, "action"))) {
            target += "?" + query;
        }
        response.sendRedirect(target);
    }

    private boolean requiresSettings(String action) {
        return "save-settings".equals(action) || "add-manager".equals(action) || "manager-role".equals(action)
                || "remove-manager".equals(action) || "add-link".equals(action) || "delete-link".equals(action);
    }

    private String redirectAfter(String[] parts, String action) {
        if ("commit".equals(action)) {
            return "/people";
        }
        if (parts.length <= 1 || "overview".equals(parts[1])) {
            return "";
        }
        if ("apply-upgrade".equals(action)) {
            return "/checklist";
        }
        String suffix = "/" + parts[1];
        if (parts.length > 2 && ("organizations".equals(parts[1]) || "people".equals(parts[1]))) {
            suffix += "/" + parts[2];
        }
        return suffix;
    }

    private String dialogFor(HttpServletRequest request, String[] parts, String action) {
        boolean organizationList = parts.length == 2 && "organizations".equals(parts[1]);
        boolean peopleList = parts.length == 2 && "people".equals(parts[1]);
        return switch (action == null ? "" : action) {
            case "add-organization" -> "add-organization";
            case "org-status" -> organizationList ? "change-org-status" : null;
            case "add-member" -> "add-person";
            case "member-status" -> peopleList ? "change-member-status" : null;
            case "add-manager" -> "add-manager";
            case "add-link" -> "add-link";
            case "add-item" -> "add-item";
            case "add-phase" -> "add-phase";
            case "edit-item", "item-status", "due-override", "assign-item" -> itemDialog(request);
            case "comment", "edit-comment" -> "CHECKLIST_ITEM".equals(param(request, "subjectType"))
                    ? "item-" + param(request, "subjectId") : null;
            default -> null;
        };
    }

    private String itemDialog(HttpServletRequest request) {
        String itemId = param(request, "itemId");
        return itemId == null || itemId.isBlank() ? null : "item-" + itemId;
    }

    private void renderChecklist(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative, InitiativeAccess.Level level, FormInput input) throws IOException {
        EsChecklist checklist = checklists.findForInitiative(initiative.getEsInitiativeId()).orElse(null);
        List<EsChecklistItem> items = checklist == null ? List.of() : checklists.items(checklist.getEsChecklistId());
        List<EsInitiativeManager> managers = initiatives.managers(initiative.getEsInitiativeId());
        Map<Long, String> names = managerNames(managers);
        Map<Long, List<EsInitiativeTimelineEntry>> itemTimeline = new HashMap<>();
        List<EsInitiativeTimelineEntry> itemEntries = initiatives.timelineForInitiative(initiative.getEsInitiativeId(),
                EsInitiativeTimelineEntry.SubjectType.CHECKLIST_ITEM);
        for (EsInitiativeTimelineEntry entry : itemEntries) {
            itemTimeline.computeIfAbsent(entry.getSubjectId(), key -> new ArrayList<>()).add(entry);
        }
        names.putAll(names(itemEntries));
        List<EsInitiativeTimelineEntry> checklistEntries = checklist == null ? List.of()
                : initiatives.timeline(EsInitiativeTimelineEntry.SubjectType.CHECKLIST, checklist.getEsChecklistId());
        names.putAll(names(checklistEntries));
        boolean upgrade = checklists.upgradeAvailable(initiative, checklist);
        int availableVersion = checklist == null ? 0
                : checklists.currentTemplate(checklist.getTemplateKind()).map(EsChecklistTemplate::getVersionNo).orElse(0);
        page(request, response, initiative.getInitiativeName() + " checklist - InteropHub", initiative, "checklist",
                input, out -> ChecklistPageRenderer.checklist(out, request.getContextPath(), initiative, checklist, items,
                        managers, names, itemTimeline, checklistEntries, user, hintFacts(initiative),
                        InitiativeAccess.canEditChecklist(level), upgrade, availableVersion,
                        "1".equals(param(request, "removed")), input));
    }

    private void renderUpgrade(HttpServletRequest request, HttpServletResponse response, EsInitiative initiative,
            InitiativeAccess.Level level, FormInput input) throws IOException {
        if (!InitiativeAccess.canEditChecklist(level)) {
            redirect(response, request, initiative, "/checklist");
            return;
        }
        EsChecklist checklist = checklists.findForInitiative(initiative.getEsInitiativeId()).orElse(null);
        if (checklist == null || !checklists.upgradeAvailable(initiative, checklist)) {
            redirect(response, request, initiative, "/checklist");
            return;
        }
        EsChecklistTemplate next = checklists.currentTemplate(checklist.getTemplateKind()).orElseThrow();
        EsChecklistTemplate applied = checklists.findTemplate(checklist.getAppliedEsChecklistTemplateId()).orElse(null);
        int fromVersion = applied == null ? 0 : applied.getVersionNo();
        ChecklistMerge.Result result = checklists.previewUpgrade(initiative);
        boolean canApply = initiative.getStatus() != EsInitiative.Status.CLOSED
                && initiative.getStatus() != EsInitiative.Status.ARCHIVED;
        page(request, response, "Template upgrade - InteropHub", initiative, "checklist", input,
                out -> ChecklistPageRenderer.upgrade(out, request.getContextPath(), initiative, fromVersion, next,
                        result, canApply));
    }

    private void renderTemplates(HttpServletRequest request, HttpServletResponse response, String[] parts,
            FormInput input, ChecklistService.TemplatePreview preview) throws IOException {
        if (parts.length >= 3 && "feedback".equals(parts[1])) {
            EsChecklistTemplate.Kind kind;
            try {
                kind = EsChecklistTemplate.Kind.valueOf(parts[2]);
            } catch (IllegalArgumentException ex) {
                renderMissing(request, response);
                return;
            }
            ChecklistFeedback.Report report = checklists.feedback(kind);
            page(request, response, kind.name() + " template feedback - InteropHub", null, null, input,
                    out -> ChecklistPageRenderer.feedback(out, request.getContextPath(), kind, report));
            return;
        }
        if (parts.length >= 2 && !"feedback".equals(parts[1])) {
            Long id = pathId(parts[1]);
            EsChecklistTemplate template = id == null ? null : checklists.findTemplate(id).orElse(null);
            if (template == null) {
                renderMissing(request, response);
                return;
            }
            page(request, response, template.getTitle() + " - InteropHub", null, null, input,
                    out -> ChecklistPageRenderer.templateSource(out, request.getContextPath(), template));
            return;
        }
        page(request, response, "Checklist templates - InteropHub", null, null, input,
                out -> ChecklistPageRenderer.templates(out, request.getContextPath(), checklists.templates(), preview,
                        input));
    }

    private boolean serveTemplateDownload(HttpServletRequest request, HttpServletResponse response, String[] parts)
            throws IOException {
        if (parts.length == 3 && "download".equals(parts[2])) {
            Long id = pathId(parts[1]);
            EsChecklistTemplate template = id == null ? null : checklists.findTemplate(id).orElse(null);
            if (template == null) {
                renderMissing(request, response);
                return true;
            }
            response.setContentType("text/markdown;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + template.getTemplateKind().name()
                    .toLowerCase(java.util.Locale.US) + "-v" + template.getVersionNo() + ".md\"");
            response.getWriter().write(template.getSourceMarkdown());
            return true;
        }
        return false;
    }

    private ChecklistHints.Facts hintFacts(EsInitiative initiative) {
        List<EsInitiativeOrganization> participations = initiatives.organizations(initiative.getEsInitiativeId());
        Map<Long, Organization> organizations = indexOrganizations(participations);
        List<EsInitiativeMember> members = initiatives.members(initiative.getEsInitiativeId());
        List<String> committedTypes = new ArrayList<>();
        int needingContact = 0;
        for (EsInitiativeOrganization participation : participations) {
            Organization organization = organizations.get(participation.getOrganizationId());
            boolean committed = participation.getParticipationStatus()
                    == EsInitiativeOrganization.ParticipationStatus.COMMITTED;
            boolean interested = participation.getParticipationStatus()
                    == EsInitiativeOrganization.ParticipationStatus.INTERESTED;
            if (committed && organization != null) {
                committedTypes.add(organization.getOrganizationType());
            }
            if (committed || interested) {
                boolean joined = false;
                for (EsInitiativeMember member : members) {
                    if (participation.getEsInitiativeOrganizationId().equals(member.getEsInitiativeOrganizationId())
                            && member.getMemberStatus() == EsInitiativeMember.MemberStatus.JOINED) {
                        joined = true;
                    }
                }
                if (!joined) {
                    needingContact++;
                }
            }
        }
        List<EsSubscription> follows = initiatives.activePrimaryTopicFollows(initiative);
        int missing = 0;
        for (EsInitiativeMember member : members) {
            if (!initiatives.followsPrimaryTopic(initiative, member, follows)) {
                missing++;
            }
        }
        return new ChecklistHints.Facts(InitiativeRosterRules.summarize(committedTypes), needingContact,
                initiatives.primaryTopicHasChampion(initiative), missing);
    }

    private Map<Long, String> managerNames(List<EsInitiativeManager> managers) {
        Set<Long> ids = new HashSet<>();
        for (EsInitiativeManager manager : managers) {
            ids.add(manager.getUserId());
        }
        Map<Long, String> names = new HashMap<>();
        for (User manager : initiatives.usersByIds(ids)) {
            String label = manager.getFullName();
            names.put(manager.getUserId(), label == null || label.isBlank() ? manager.getEmail() : label);
        }
        return names;
    }

    private Long pathId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private EsInitiativeOrganization findParticipation(EsInitiative initiative, Long id) {
        for (EsInitiativeOrganization participation : initiatives.organizations(initiative.getEsInitiativeId())) {
            if (id.equals(participation.getEsInitiativeOrganizationId())) {
                return participation;
            }
        }
        return null;
    }

    private String[] parts(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return new String[0];
        }
        String[] raw = path.split("/");
        List<String> parts = new ArrayList<>();
        for (String part : raw) {
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        return parts.toArray(String[]::new);
    }

    private String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? null : value.trim();
    }

    private Long longParam(HttpServletRequest request, String name) {
        String value = param(request, name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("That selection was not recognized.");
        }
    }

    private int indexParam(HttpServletRequest request, String name) {
        String value = param(request, name);
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private LocalDate dateParam(HttpServletRequest request, String name) {
        String value = param(request, name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Enter dates as YYYY-MM-DD.");
        }
    }

    private <E extends Enum<E>> E enumParam(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("That choice is not recognized.");
        }
    }

    private interface PageBody {
        void write(PrintWriter out);
    }

    private record Upload(String filename, byte[] bytes) {
    }

    static final class HeldImport implements java.io.Serializable {
        final long userId;
        final String filename;
        final byte[] bytes;
        final List<String> headers;
        final InitiativeImport.ColumnMapping mapping;
        final InitiativeImport.Preview preview;

        HeldImport(long userId, String filename, byte[] bytes, List<String> headers,
                InitiativeImport.ColumnMapping mapping, InitiativeImport.Preview preview) {
            this.userId = userId;
            this.filename = filename;
            this.bytes = bytes;
            this.headers = headers;
            this.mapping = mapping;
            this.preview = preview;
        }
    }
}
