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
import org.airahub.interophub.dao.SmallGroupQueries;
import org.airahub.interophub.model.EsChecklist;
import org.airahub.interophub.model.EsChecklistItem;
import org.airahub.interophub.model.EsChecklistTemplate;
import org.airahub.interophub.model.EsInitiative;
import org.airahub.interophub.model.EsInitiativeLink;
import org.airahub.interophub.model.EsInitiativeManager;
import org.airahub.interophub.model.EsInitiativeMember;
import org.airahub.interophub.model.EsInitiativeOrganization;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;
import org.airahub.interophub.model.EsSmallGroup;
import org.airahub.interophub.model.EsSmallGroupParticipant;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.Organization;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.AuthFlowService;
import org.airahub.interophub.service.ChecklistDueDates;
import org.airahub.interophub.service.ChecklistFeedback;
import org.airahub.interophub.service.ChecklistHints;
import org.airahub.interophub.service.ChecklistMerge;
import org.airahub.interophub.service.ChecklistService;
import org.airahub.interophub.service.InitiativeAccess;
import org.airahub.interophub.service.InitiativeImport;
import org.airahub.interophub.service.InitiativeRosterRules;
import org.airahub.interophub.service.InitiativeService;
import org.airahub.interophub.service.OrganizationDirectory;
import org.airahub.interophub.service.PeopleDirectory;
import org.airahub.interophub.service.PeopleSearch;
import org.airahub.interophub.service.PeopleToBringIn;
import org.airahub.interophub.service.PublicUrlService;
import org.airahub.interophub.service.SmallGroupSeriesComparison;
import org.airahub.interophub.service.SmallGroupService;
import org.immregistries.aira.web.AiraPage;

public class EsInitiativeServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(EsInitiativeServlet.class.getName());
    private static final String FLASH = "initiativeFlash";
    private static final String FLASH_KIND = "initiativeFlashKind";

    private final AuthFlowService authFlowService = new AuthFlowService();
    private final InitiativeService initiatives = new InitiativeService();
    private final ChecklistService checklists = new ChecklistService();
    private final SmallGroupService smallGroups = new SmallGroupService();
    private final PeopleDirectory peopleDirectory = new PeopleDirectory();
    private final PublicUrlService publicUrls = new PublicUrlService();

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
        if (parts.length > 1 && "people-search".equals(parts[1])) {
            searchPeople(request, response, initiative, level);
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
        if (parts.length == 4 && "small-groups".equals(parts[1]) && "download".equals(parts[3])) {
            serveSmallGroupDownload(response, initiative, parts[2]);
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
            if ("small-groups".equals(section)) {
                if (!InitiativeAccess.canEditSmallGroup(level)) {
                    throw new IllegalArgumentException("You don't have permission to change this Small Group.");
                }
                String warning = dispatchSmallGroup(request, user, initiative, parts, action);
                if (wantsJson(request)) {
                    writeSmallGroupChecklistJson(request, response, user, initiative, parts);
                    return;
                }
                if (warning != null) {
                    flash(request, "warning", warning);
                } else {
                    flash(request, "success", "Saved.");
                }
                redirect(response, request, initiative, smallGroupRedirect(request, parts, action));
                return;
            }
            if (requiresSettings(action) && !InitiativeAccess.canManageSettings(level)) {
                throw new IllegalArgumentException("You don't have permission to change these settings.");
            }
            if (!requiresSettings(action) && !InitiativeAccess.canEditRoster(level)) {
                throw new IllegalArgumentException("You don't have permission to change this initiative.");
            }
            String warning = dispatch(request, user, initiative, section, action);
            if (wantsJson(request)) {
                writeChecklistJson(request, response, user, initiative);
                return;
            }
            boolean keepsItsOwnMessage = "commit".equals(action) || "preview".equals(action) || "remap".equals(action);
            if (warning != null) {
                flash(request, "warning", warning);
            } else if (!keepsItsOwnMessage) {
                flash(request, "success", "Saved.");
            } else if (!"commit".equals(action)) {
                flash(request, "success", "Preview updated. Import only after you have checked it.");
            }
            redirect(response, request, initiative, redirectAfter(request, parts, action));
        } catch (IllegalArgumentException ex) {
            if (wantsJson(request)) {
                writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
                return;
            }
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
            if (wantsJson(request)) {
                writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not save that change.");
                return;
            }
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
            case "add-member" -> {
                String intent = "contact".equals(param(request, "personIntent")) ? "contact" : "create";
                InitiativeService.PersonPlacement placed = initiatives.placePerson(user, initiative, intent,
                        longParam(request, "memberId"), longParam(request, "userId"), param(request, "email"),
                        param(request, "firstName"), param(request, "lastName"), longParam(request, "organizationId"),
                        param(request, "newOrganizationName"), param(request, "newOrganizationType"),
                        param(request, "newOrganizationWebsite"), param(request, "initiativeRole"),
                        enumParam(EsInitiativeMember.MemberStatus.class, param(request, "memberStatus")),
                        EsInitiativeMember.MemberSource.STAFF_ADDED, param(request, "note"));
                yield placed.warning();
            }
            case "edit-member" -> {
                initiatives.updateMember(user, initiative, longParam(request, "memberId"), param(request, "firstName"),
                        param(request, "lastName"), param(request, "email"), longParam(request, "organizationId"),
                        param(request, "newOrganizationName"), param(request, "newOrganizationType"),
                        param(request, "newOrganizationWebsite"), param(request, "initiativeRole"));
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
                request.setAttribute("checklist.created", checklists.addItem(user, initiative, param(request, "phase"),
                        param(request, "title"), param(request, "guidance"), longParam(request, "afterItemId")));
                yield null;
            }
            case "save-item" -> {
                Long itemId = longParam(request, "itemId");
                LocalDate due = dateParam(request, "dueDate");
                LocalDate currentDue = currentDue(initiative, itemId);
                checklists.editItem(user, initiative, itemId, param(request, "title"), param(request, "guidance"));
                if ((currentDue == null && due != null) || (currentDue != null && !currentDue.equals(due))) {
                    checklists.setDueOverride(user, initiative, itemId, due);
                }
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
                checklists.reorder(initiative, longParam(request, "itemId"), longParam(request, "beforeItemId"),
                        param(request, "phase"));
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
            case "small-groups" -> renderSmallGroups(request, response, user, initiative, level, parts, input);
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
        Map<Long, User> contactUsers = memberUsers(contacts);
        page(request, response, name + " - " + initiative.getInitiativeName() + " - InteropHub", initiative,
                "organizations", input, out -> InitiativePageRenderer.organizationDetail(out, request.getContextPath(),
                        initiative, participation, organization, contacts, timeline, names(timeline), contactUsers, user,
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
        Map<Long, User> peopleUsers = memberUsers(visible);
        page(request, response, initiative.getInitiativeName() + " people - InteropHub", initiative, "people", input,
                out -> InitiativePageRenderer.people(out, request.getContextPath(), initiative, visible, participations,
                        organizations, missing, statusFilter, organizationFilter, typeFilter,
                        InitiativeAccess.canEditRoster(level), peopleUsers, input));
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
        Map<Long, User> personUsers = memberUsers(List.of(member));
        page(request, response, InitiativePageRenderer.personLabel(member, personUsers) + " - "
                + initiative.getInitiativeName() + " - InteropHub",
                initiative, "people", input, out -> InitiativePageRenderer.personDetail(out, request.getContextPath(),
                        initiative, shown, participations, indexOrganizations(participations), timeline, names(timeline),
                        personUsers, user, missingFollow, InitiativeAccess.canEditRoster(level), input));
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

    private void searchPeople(HttpServletRequest request, HttpServletResponse response, EsInitiative initiative,
            InitiativeAccess.Level level) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (!InitiativeAccess.canEditRoster(level)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().print("{\"results\":[]}");
            return;
        }
        List<PeopleSearch.Hit> hits = peopleDirectory.search(initiative.getEsInitiativeId(), param(request, "q"));
        StringBuilder json = new StringBuilder("{\"results\":[");
        for (int i = 0; i < hits.size(); i++) {
            PeopleSearch.Hit hit = hits.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"source\":\"").append(hit.source().name()).append("\",\"sourceLabel\":\"")
                    .append(InitiativeHtml.json(hit.sourceLabel())).append("\",\"name\":\"")
                    .append(InitiativeHtml.json(hit.name())).append("\",\"email\":\"")
                    .append(InitiativeHtml.json(hit.email())).append("\",\"organization\":\"")
                    .append(InitiativeHtml.json(hit.organization())).append("\",\"firstName\":\"")
                    .append(InitiativeHtml.json(hit.firstName())).append("\",\"lastName\":\"")
                    .append(InitiativeHtml.json(hit.lastName())).append("\"");
            json.append(",\"memberId\":").append(hit.memberId() == null ? "null" : hit.memberId());
            json.append(",\"userId\":").append(hit.userId() == null ? "null" : hit.userId());
            json.append('}');
        }
        json.append("]}");
        response.getWriter().print(json);
    }

    private Map<Long, User> memberUsers(List<EsInitiativeMember> members) {
        Set<Long> ids = new HashSet<>();
        for (EsInitiativeMember member : members) {
            if (member.getUserId() != null) {
                ids.add(member.getUserId());
            }
        }
        Map<Long, User> users = new HashMap<>();
        for (User account : initiatives.usersByIds(ids)) {
            users.put(account.getUserId(), account);
        }
        return users;
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
                .addLocalStylesheet("/css/initiative-checklist.css?v=6")
                .context(InteropAiraPageFactory.initiativesContext())
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
                    + "/js/initiative-dialogs.js?v=3\"></script>");
            out.println("    <script src=\"" + InitiativeHtml.esc(request.getContextPath())
                    + "/js/initiative-org-typeahead.js\"></script>");
            out.println("    <script src=\"" + InitiativeHtml.esc(request.getContextPath())
                    + "/js/initiative-people-search.js\"></script>");
            out.println("    <script src=\"" + InitiativeHtml.esc(request.getContextPath())
                    + "/js/initiative-checklist.js?v=4\"></script>");
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
        String fragment = "";
        int hash = suffix.indexOf('#');
        if (hash >= 0) {
            fragment = suffix.substring(hash);
            suffix = suffix.substring(0, hash);
        }
        EsInitiative current = initiatives.findByCode(initiative.getInitiativeCode()).orElse(initiative);
        String target = request.getContextPath() + "/es/initiatives/" + current.getInitiativeCode() + suffix;
        String query = request.getQueryString();
        if (query != null && !query.isBlank() && query.indexOf('\r') < 0 && query.indexOf('\n') < 0
                && !"commit".equals(param(request, "action"))) {
            target += "?" + query;
        }
        response.sendRedirect(target + fragment);
    }

    private boolean requiresSettings(String action) {
        return "save-settings".equals(action) || "add-manager".equals(action) || "manager-role".equals(action)
                || "remove-manager".equals(action) || "add-link".equals(action) || "delete-link".equals(action);
    }

    private String redirectAfter(HttpServletRequest request, String[] parts, String action) {
        if ("commit".equals(action)) {
            return "/people";
        }
        if (parts.length <= 1 || "overview".equals(parts[1])) {
            return "";
        }
        if ("apply-upgrade".equals(action)) {
            return "/checklist";
        }
        if ("checklist".equals(parts[1]) && parts.length == 2) {
            String itemId = param(request, "itemId");
            if (itemId != null && !itemId.isBlank() && itemId.chars().allMatch(Character::isDigit)) {
                return "/checklist#item-" + itemId;
            }
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
            case "edit-item", "save-item", "item-status", "due-override", "assign-item" -> itemDialog(request);
            case "comment", "edit-comment" -> "CHECKLIST_ITEM".equals(param(request, "subjectType"))
                    ? "item-" + param(request, "subjectId") + "-dialog" : null;
            case "create-small-group" -> "new-small-group";
            case "add-participant", "add-registrant", "add-new-participant" -> "add-participant";
            case "remove-participant" -> "remove-participant";
            case "group-status" -> "group-status";
            case "save-small-group" -> "edit-details";
            default -> null;
        };
    }

    private String itemDialog(HttpServletRequest request) {
        String itemId = param(request, "itemId");
        return itemId == null || itemId.isBlank() ? null : "item-" + itemId + "-dialog";
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
                        "1".equals(param(request, "removed")), "1".equals(param(request, "hideDone")),
                        authFlowService.isAdminUser(user), input));
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

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }

    private void writeChecklistJson(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative) throws IOException {
        EsChecklist checklist = checklists.findForInitiative(initiative.getEsInitiativeId()).orElse(null);
        List<EsChecklistItem> items = checklist == null ? List.of() : checklists.items(checklist.getEsChecklistId());
        LocalDate today = LocalDate.now();
        ChecklistPageRenderer.Progress progress = ChecklistPageRenderer.progress(items, initiative, today);
        EsChecklistItem created = (EsChecklistItem) request.getAttribute("checklist.created");
        Long itemId = created == null ? longParam(request, "itemId") : created.getEsChecklistItemId();
        EsChecklistItem item = null;
        if (itemId != null) {
            for (EsChecklistItem candidate : items) {
                if (itemId.equals(candidate.getEsChecklistItemId())) {
                    item = candidate;
                }
            }
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"ok\":true");
        json.append(",\"done\":").append(progress.done());
        json.append(",\"total\":").append(progress.total());
        json.append(",\"overdue\":").append(progress.overdue());
        json.append(",\"dueSoon\":").append(progress.dueSoon());
        json.append(",\"progress\":").append(jsonString(progress.text()));
        if (item != null) {
            json.append(",\"itemId\":").append(item.getEsChecklistItemId());
            json.append(",\"title\":").append(jsonString(item.getTitle()));
            json.append(",\"status\":").append(jsonString(item.getItemStatus().name()));
            json.append(",\"removed\":").append(Boolean.TRUE.equals(item.getRemoved()));
            json.append(",\"urgency\":").append(jsonString(ChecklistPageRenderer.itemUrgency(item, initiative, today)));
            json.append(",\"due\":").append(jsonString(ChecklistPageRenderer.dueText(item, initiative)));
            json.append(",\"override\":").append(item.getDueDateOverride() != null);
            json.append(",\"guidance\":").append(jsonString(item.getGuidance()));
            json.append(",\"phase\":").append(jsonString(item.getPhaseLabel()));
            json.append(",\"assignee\":");
            json.append(item.getAssignedUserId() == null ? "null" : item.getAssignedUserId().toString());
        }
        if (created != null && item != null) {
            String page = request.getContextPath() + "/es/initiatives/" + initiative.getInitiativeCode() + "/checklist";
            String query = request.getQueryString();
            if (query != null && !query.isBlank() && query.indexOf('\r') < 0 && query.indexOf('\n') < 0) {
                page += "?" + query;
            }
            List<EsInitiativeManager> managers = initiatives.managers(initiative.getEsInitiativeId());
            Map<Long, String> names = managerNames(managers);
            json.append(",\"html\":").append(jsonString(ChecklistPageRenderer.itemRowMarkup(page, item, managers, names,
                    hintFacts(initiative), initiative, today, true)));
            json.append(",\"dialog\":").append(jsonString(ChecklistPageRenderer.itemDialogMarkup(page, item, names, user,
                    true)));
        }
        json.append('}');
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.toString());
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"ok\":false,\"error\":" + jsonString(message) + "}");
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")
                .replace("\t", "\\t") + "\"";
    }

    private LocalDate currentDue(EsInitiative initiative, Long itemId) {
        if (itemId == null) {
            return null;
        }
        EsChecklist checklist = checklists.findForInitiative(initiative.getEsInitiativeId()).orElse(null);
        if (checklist == null) {
            return null;
        }
        for (EsChecklistItem item : checklists.items(checklist.getEsChecklistId())) {
            if (itemId.equals(item.getEsChecklistItemId())) {
                return item.getDueDateOverride();
            }
        }
        return null;
    }

    private void serveSmallGroupDownload(HttpServletResponse response, EsInitiative initiative, String rawId)
            throws IOException {
        EsSmallGroup group = smallGroup(initiative, rawId);
        if (group == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        EsChecklist checklist = checklists.findForSmallGroup(group.getEsSmallGroupId()).orElse(null);
        if (checklist == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String markdown = checklists.download(checklist, group.getSmallGroupName());
        response.setContentType("text/markdown;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"small-group-" + group.getEsSmallGroupId()
                + "-checklist.md\"");
        response.getWriter().write(markdown);
    }

    private void renderSmallGroups(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative, InitiativeAccess.Level level, String[] parts, FormInput input) throws IOException {
        boolean canEdit = InitiativeAccess.canEditSmallGroup(level);
        if (parts.length == 2) {
            List<EsSmallGroup> groups = smallGroups.list(initiative.getEsInitiativeId());
            List<EsTopic> topics = topicChoices(initiative, null);
            page(request, response, initiative.getInitiativeName() + " small groups - InteropHub", initiative,
                    "small-groups", input, out -> SmallGroupPageRenderer.list(out, request.getContextPath(), initiative,
                            groups, topicNames(groups, topics), smallGroups.activeCounts(groups), topics,
                            seriesChoices(topics), groups, canEdit, input));
            return;
        }
        EsSmallGroup group = smallGroup(initiative, parts[2]);
        if (group == null) {
            renderMissing(request, response);
            return;
        }
        if (parts.length == 4 && "upgrade".equals(parts[3])) {
            renderSmallGroupUpgrade(request, response, initiative, group, level, input);
            return;
        }
        if (parts.length != 3) {
            renderMissing(request, response);
            return;
        }
        String base = request.getContextPath() + "/es/initiatives/" + initiative.getInitiativeCode() + "/small-groups/"
                + group.getEsSmallGroupId();
        EsTopic topic = initiatives.findTopic(group.getEsTopicId()).orElse(null);
        List<EsTopic> topics = topicChoices(initiative, group.getEsTopicId());
        List<EsTopicMeeting> series = seriesChoices(topics);
        List<EsSmallGroupParticipant> rows = smallGroups.participants(group.getEsSmallGroupId());
        Map<Long, EsInitiativeMember> members = new HashMap<>();
        for (EsInitiativeMember member : initiatives.members(initiative.getEsInitiativeId())) {
            members.put(member.getEsInitiativeMemberId(), member);
        }
        List<SmallGroupPageRenderer.ParticipantRow> participants = new ArrayList<>();
        for (EsSmallGroupParticipant row : rows) {
            if (row.getParticipantStatus() != EsSmallGroupParticipant.Status.ACTIVE) {
                continue;
            }
            participants.add(new SmallGroupPageRenderer.ParticipantRow(row, members.get(row.getEsInitiativeMemberId())));
        }
        List<EsSmallGroup> earlier = new ArrayList<>();
        for (EsSmallGroup candidate : smallGroups.list(initiative.getEsInitiativeId())) {
            if (!group.getEsSmallGroupId().equals(candidate.getEsSmallGroupId())) {
                earlier.add(candidate);
            }
        }
        String path = smallGroups.registrationPath(group);
        String registrationUrl = path == null ? null : publicUrls.resolveExternalUrl(path);
        EsChecklist checklist = checklists.findForSmallGroup(group.getEsSmallGroupId()).orElse(null);
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
        List<EsInitiativeTimelineEntry> groupEntries = initiatives.timeline(
                EsInitiativeTimelineEntry.SubjectType.SMALL_GROUP, group.getEsSmallGroupId());
        names.putAll(names(groupEntries));
        boolean upgrade = checklists.upgradeAvailable(checklist, group.acceptsTemplateUpgrade());
        int availableVersion = checklist == null ? 0
                : checklists.currentTemplate(checklist.getTemplateKind()).map(EsChecklistTemplate::getVersionNo).orElse(0);
        ChecklistHints.Facts facts = smallGroups.facts(initiative, group);
        Map<Long, String> organizationNames = new HashMap<>();
        Map<Long, Organization> directory = indexOrganizations(initiatives.organizations(initiative.getEsInitiativeId()));
        for (EsInitiativeOrganization participation : initiatives.organizations(initiative.getEsInitiativeId())) {
            Organization organization = directory.get(participation.getOrganizationId());
            if (organization != null) {
                organizationNames.put(participation.getEsInitiativeOrganizationId(), organization.getOrganizationName());
            }
        }
        List<EsInitiativeMember> participantMembers = new ArrayList<>();
        List<EsSmallGroupParticipant> activeRows = new ArrayList<>();
        for (SmallGroupPageRenderer.ParticipantRow row : participants) {
            activeRows.add(row.participant());
            if (row.member() != null) {
                participantMembers.add(row.member());
            }
        }
        int bringPage = 1;
        try {
            if (param(request, "bringPage") != null) {
                bringPage = Integer.parseInt(param(request, "bringPage"));
            }
        } catch (NumberFormatException ignored) {
            bringPage = 1;
        }
        PeopleToBringIn.Page bringIn = smallGroups.bringIn(initiative, group, param(request, "bringIn"), bringPage);
        page(request, response, group.getSmallGroupName() + " - InteropHub", initiative, "small-groups", input,
                out -> SmallGroupPageRenderer.page(out, request.getContextPath(), base, initiative, group, topic, topics,
                        series, participants, memberUsers(new ArrayList<>(members.values())), organizationNames,
                        smallGroups.registeredParticipantIds(group, activeRows),
                        smallGroups.followingParticipantIds(group, participantMembers), bringIn,
                        param(request, "bringIn"), registrationUrl, earlier,
                        canEdit, smallGroups.anchors(initiative, group), checklist, items, managers, names, itemTimeline,
                        checklistEntries, groupEntries, user, facts, upgrade, availableVersion,
                        "1".equals(param(request, "removed")), "1".equals(param(request, "hideDone")),
                        authFlowService.isAdminUser(user), input));
    }

    private void renderSmallGroupUpgrade(HttpServletRequest request, HttpServletResponse response,
            EsInitiative initiative, EsSmallGroup group, InitiativeAccess.Level level, FormInput input)
            throws IOException {
        String back = "/small-groups/" + group.getEsSmallGroupId();
        if (!InitiativeAccess.canEditSmallGroup(level)) {
            redirect(response, request, initiative, back);
            return;
        }
        EsChecklist checklist = checklists.findForSmallGroup(group.getEsSmallGroupId()).orElse(null);
        if (checklist == null || !checklists.upgradeAvailable(checklist, group.acceptsTemplateUpgrade())) {
            redirect(response, request, initiative, back);
            return;
        }
        EsChecklistTemplate next = checklists.currentTemplate(checklist.getTemplateKind()).orElseThrow();
        EsChecklistTemplate applied = checklists.findTemplate(checklist.getAppliedEsChecklistTemplateId()).orElse(null);
        int fromVersion = applied == null ? 0 : applied.getVersionNo();
        ChecklistMerge.Result result = checklists.previewUpgrade(checklist);
        String base = request.getContextPath() + "/es/initiatives/" + initiative.getInitiativeCode() + back;
        page(request, response, "Template upgrade - InteropHub", initiative, "small-groups", input,
                out -> SmallGroupPageRenderer.upgrade(out, base, fromVersion, next, result,
                        group.acceptsTemplateUpgrade()));
    }

    private String dispatchSmallGroup(HttpServletRequest request, User user, EsInitiative initiative, String[] parts,
            String action) {
        Long groupId = parts.length >= 3 ? pathId(parts[2]) : null;
        EsSmallGroup group = groupId == null ? null : smallGroups.find(initiative, groupId).orElse(null);
        if (!"create-small-group".equals(action) && group == null) {
            throw new IllegalArgumentException("That Small Group was not found.");
        }
        Long id = group == null ? null : group.getEsSmallGroupId();
        return switch (action == null ? "" : action) {
            case "create-small-group" -> {
                EsSmallGroup created = smallGroups.create(user, initiative, param(request, "name"),
                        longParam(request, "topicId"), longParam(request, "seriesId"), param(request, "description"),
                        enumParam(EsSmallGroup.Status.class, param(request, "status")),
                        dateParam(request, "cycleStartDate"), dateParam(request, "workingDayDate"),
                        dateParam(request, "closeoutDate"), param(request, "breakNote"), productAreas(request),
                        param(request, "charterUrl"), longParam(request, "copyFromId"));
                request.setAttribute("smallGroup.created", created.getEsSmallGroupId());
                yield null;
            }
            case "save-small-group" -> {
                smallGroups.updateDetails(user, initiative, group, param(request, "name"), longParam(request, "topicId"),
                        longParam(request, "seriesId"), param(request, "description"),
                        dateParam(request, "cycleStartDate"), dateParam(request, "workingDayDate"),
                        dateParam(request, "closeoutDate"), param(request, "breakNote"), productAreas(request),
                        param(request, "charterUrl"));
                yield null;
            }
            case "group-status" -> {
                smallGroups.changeStatus(user, initiative, group, enumParam(EsSmallGroup.Status.class, param(request, "status")),
                        param(request, "note"));
                yield null;
            }
            case "add-participant", "add-registrant", "add-new-participant" -> smallGroups.addFromSearch(user, initiative,
                    group, longParam(request, "memberId"), longParam(request, "userId"), param(request, "email"),
                    param(request, "firstName"), param(request, "lastName"), longParam(request, "organizationId"),
                    param(request, "newOrganizationName"), param(request, "newOrganizationType"),
                    param(request, "newOrganizationWebsite"), param(request, "role"));
            case "remove-participant" -> {
                smallGroups.removeParticipant(user, initiative, group, longParam(request, "participantId"),
                        param(request, "note"));
                yield null;
            }
            case "update-role" -> {
                smallGroups.updateRole(user, initiative, group, longParam(request, "participantId"), param(request, "role"));
                yield null;
            }
            case "copy-participants" -> smallGroups.copyParticipants(user, initiative, group,
                    longParam(request, "copyFromId"));
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
            case "start-checklist" -> {
                checklists.startForSmallGroup(initiative, id);
                yield null;
            }
            case "item-status" -> {
                checklists.setStatus(user, initiative, id, longParam(request, "itemId"),
                        enumParam(EsChecklistItem.Status.class, param(request, "itemStatus")), param(request, "note"));
                yield null;
            }
            case "add-item", "add-phase" -> {
                request.setAttribute("checklist.created", checklists.addItem(user, initiative, id, param(request, "phase"),
                        param(request, "title"), param(request, "guidance"), longParam(request, "afterItemId")));
                yield null;
            }
            case "save-item" -> {
                Long itemId = longParam(request, "itemId");
                LocalDate due = dateParam(request, "dueDate");
                LocalDate currentDue = currentDue(initiative, id, itemId);
                checklists.editItem(user, initiative, id, itemId, param(request, "title"), param(request, "guidance"));
                if ((currentDue == null && due != null) || (currentDue != null && !currentDue.equals(due))) {
                    checklists.setDueOverride(user, initiative, id, itemId, due);
                }
                yield null;
            }
            case "edit-item" -> {
                checklists.editItem(user, initiative, id, longParam(request, "itemId"), param(request, "title"),
                        param(request, "guidance"));
                yield null;
            }
            case "remove-item" -> {
                checklists.removeItem(user, initiative, id, longParam(request, "itemId"));
                yield null;
            }
            case "restore-item" -> {
                checklists.restoreItem(user, initiative, id, longParam(request, "itemId"));
                yield null;
            }
            case "move-item" -> {
                checklists.move(initiative, id, longParam(request, "itemId"), param(request, "direction"));
                yield null;
            }
            case "reorder-item" -> {
                checklists.reorder(initiative, id, longParam(request, "itemId"), longParam(request, "beforeItemId"),
                        param(request, "phase"));
                yield null;
            }
            case "rename-phase" -> {
                checklists.renamePhase(initiative, id, param(request, "fromPhase"), param(request, "toPhase"));
                yield null;
            }
            case "assign-item" -> {
                checklists.assign(user, initiative, id, longParam(request, "itemId"), longParam(request, "assignedUserId"));
                yield null;
            }
            case "due-override" -> {
                checklists.setDueOverride(user, initiative, id, longParam(request, "itemId"), dateParam(request, "dueDate"));
                yield null;
            }
            case "apply-upgrade" -> {
                EsChecklist checklist = checklists.findForSmallGroup(id).orElse(null);
                checklists.applyUpgrade(user, initiative, checklist, group.acceptsTemplateUpgrade());
                yield null;
            }
            default -> throw new IllegalArgumentException("That action is not recognized.");
        };
    }

    private String smallGroupRedirect(HttpServletRequest request, String[] parts, String action) {
        Object created = request.getAttribute("smallGroup.created");
        if (created != null) {
            return "/small-groups/" + created;
        }
        if (parts.length >= 3 && pathId(parts[2]) != null) {
            String suffix = "/small-groups/" + parts[2];
            String itemId = param(request, "itemId");
            if (itemId != null && !itemId.isBlank() && itemId.chars().allMatch(Character::isDigit)
                    && !"apply-upgrade".equals(action)) {
                return suffix + "#item-" + itemId;
            }
            return suffix;
        }
        return "/small-groups";
    }

    private void writeSmallGroupChecklistJson(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative, String[] parts) throws IOException {
        EsSmallGroup group = smallGroup(initiative, parts.length >= 3 ? parts[2] : null);
        if (group == null) {
            writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "That Small Group was not found.");
            return;
        }
        EsChecklist checklist = checklists.findForSmallGroup(group.getEsSmallGroupId()).orElse(null);
        List<EsChecklistItem> items = checklist == null ? List.of() : checklists.items(checklist.getEsChecklistId());
        LocalDate today = LocalDate.now();
        ChecklistDueDates.Anchors anchors = smallGroups.anchors(initiative, group);
        ChecklistPageRenderer.Progress progress = ChecklistPageRenderer.progress(items, anchors, today);
        EsChecklistItem created = (EsChecklistItem) request.getAttribute("checklist.created");
        Long itemId = created == null ? longParam(request, "itemId") : created.getEsChecklistItemId();
        EsChecklistItem item = null;
        if (itemId != null) {
            for (EsChecklistItem candidate : items) {
                if (itemId.equals(candidate.getEsChecklistItemId())) {
                    item = candidate;
                }
            }
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"ok\":true");
        json.append(",\"done\":").append(progress.done());
        json.append(",\"total\":").append(progress.total());
        json.append(",\"overdue\":").append(progress.overdue());
        json.append(",\"dueSoon\":").append(progress.dueSoon());
        json.append(",\"progress\":").append(jsonString(progress.text()));
        if (item != null) {
            json.append(",\"itemId\":").append(item.getEsChecklistItemId());
            json.append(",\"title\":").append(jsonString(item.getTitle()));
            json.append(",\"status\":").append(jsonString(item.getItemStatus().name()));
            json.append(",\"removed\":").append(Boolean.TRUE.equals(item.getRemoved()));
            json.append(",\"urgency\":").append(jsonString(ChecklistPageRenderer.itemUrgency(item, anchors, today)));
            json.append(",\"due\":").append(jsonString(ChecklistPageRenderer.dueText(item, anchors)));
            json.append(",\"override\":").append(item.getDueDateOverride() != null);
            json.append(",\"guidance\":").append(jsonString(item.getGuidance()));
            json.append(",\"phase\":").append(jsonString(item.getPhaseLabel()));
            json.append(",\"assignee\":");
            json.append(item.getAssignedUserId() == null ? "null" : item.getAssignedUserId().toString());
        }
        if (created != null && item != null) {
            String pageUrl = request.getContextPath() + "/es/initiatives/" + initiative.getInitiativeCode()
                    + "/small-groups/" + group.getEsSmallGroupId();
            String query = request.getQueryString();
            if (query != null && !query.isBlank() && query.indexOf('\r') < 0 && query.indexOf('\n') < 0) {
                pageUrl += "?" + query;
            }
            List<EsInitiativeManager> managers = initiatives.managers(initiative.getEsInitiativeId());
            Map<Long, String> names = managerNames(managers);
            ChecklistHints.Facts facts = smallGroups.facts(initiative, group);
            json.append(",\"html\":").append(jsonString(ChecklistPageRenderer.itemRowMarkup(pageUrl, item, managers, names,
                    facts, anchors, today, true)));
            json.append(",\"dialog\":").append(jsonString(ChecklistPageRenderer.itemDialogMarkup(pageUrl, item, names, user,
                    true)));
        }
        json.append('}');
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.toString());
    }

    private LocalDate currentDue(EsInitiative initiative, Long smallGroupId, Long itemId) {
        if (itemId == null || smallGroupId == null) {
            return null;
        }
        EsChecklist checklist = checklists.findForSmallGroup(smallGroupId).orElse(null);
        if (checklist == null) {
            return null;
        }
        for (EsChecklistItem item : checklists.items(checklist.getEsChecklistId())) {
            if (itemId.equals(item.getEsChecklistItemId())) {
                return item.getDueDateOverride();
            }
        }
        return null;
    }

    private EsSmallGroup smallGroup(EsInitiative initiative, String rawId) {
        Long id = pathId(rawId);
        return id == null ? null : smallGroups.find(initiative, id).orElse(null);
    }

    private List<EsTopic> topicChoices(EsInitiative initiative, Long extraTopicId) {
        List<EsTopic> topics = new ArrayList<>(initiatives.activeTopics());
        addTopic(topics, initiative.getPrimaryEsTopicId());
        addTopic(topics, extraTopicId);
        return topics;
    }

    private void addTopic(List<EsTopic> topics, Long topicId) {
        if (topicId == null) {
            return;
        }
        for (EsTopic topic : topics) {
            if (topicId.equals(topic.getEsTopicId())) {
                return;
            }
        }
        initiatives.findTopic(topicId).ifPresent(topics::add);
    }

    private List<EsTopicMeeting> seriesChoices(List<EsTopic> topics) {
        List<Long> topicIds = new ArrayList<>();
        for (EsTopic topic : topics) {
            topicIds.add(topic.getEsTopicId());
        }
        return SmallGroupQueries.seriesForTopics(topicIds);
    }

    private Map<Long, String> topicNames(List<EsSmallGroup> groups, List<EsTopic> topics) {
        Map<Long, String> names = new HashMap<>();
        for (EsTopic topic : topics) {
            names.put(topic.getEsTopicId(), topic.getTopicName());
        }
        for (EsSmallGroup group : groups) {
            if (!names.containsKey(group.getEsTopicId())) {
                initiatives.findTopic(group.getEsTopicId())
                        .ifPresent(topic -> names.put(topic.getEsTopicId(), topic.getTopicName()));
            }
        }
        return names;
    }

    private List<String> productAreas(HttpServletRequest request) {
        String[] values = request.getParameterValues("productArea");
        if (values == null) {
            return List.of();
        }
        List<String> areas = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                areas.add(value.trim());
            }
        }
        return areas;
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
