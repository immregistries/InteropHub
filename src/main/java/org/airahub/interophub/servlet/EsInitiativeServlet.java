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

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        User user = requireUser(request, response);
        if (user == null) {
            return;
        }
        String[] parts = parts(request);
        if (parts.length == 0) {
            renderList(request, response, user);
            return;
        }
        if ("new".equals(parts[0])) {
            if (!authFlowService.isAdminUser(user)) {
                renderMissing(request, response);
                return;
            }
            renderCreate(request, response, null);
            return;
        }
        EsInitiative initiative = initiatives.findByCode(parts[0]).orElse(null);
        InitiativeAccess.Level level = initiatives.access(user, initiative);
        if (initiative == null || !InitiativeAccess.canView(level)) {
            renderMissing(request, response);
            return;
        }
        String section = parts.length > 1 ? parts[1] : "overview";
        if ("organization-suggest".equals(section)) {
            suggest(request, response, initiative, level);
            return;
        }
        switch (section) {
            case "overview" -> renderOverview(request, response, user, initiative, level);
            case "organizations" -> renderOrganizations(request, response, user, initiative, level);
            case "people" -> renderPeople(request, response, user, initiative, level);
            case "settings" -> {
                if (!InitiativeAccess.canManageSettings(level)) {
                    redirect(response, request, initiative, "");
                    return;
                }
                renderSettings(request, response, initiative);
            }
            case "import" -> {
                if (!InitiativeAccess.canEditRoster(level)) {
                    redirect(response, request, initiative, "/people");
                    return;
                }
                renderImport(request, response, user, initiative);
            }
            default -> renderMissing(request, response);
        }
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
            redirect(response, request, initiative, redirectAfter(section, action));
        } catch (IllegalArgumentException ex) {
            flash(request, "danger", ex.getMessage());
            String target = parts.length == 0 ? request.getContextPath() + "/es/initiatives"
                    : request.getContextPath() + "/es/initiatives/" + (parts.length > 0 ? parts[0] : "")
                            + (parts.length > 1 ? "/" + parts[1] : "");
            if (parts.length == 1 && "new".equals(parts[0])) {
                target = request.getContextPath() + "/es/initiatives/new";
            }
            response.sendRedirect(target);
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
        page(request, response, "Initiatives - InteropHub", null, null, false, out -> {
            InitiativePageRenderer.list(out, request.getContextPath(), visible, topicNames,
                    authFlowService.isAdminUser(user));
        });
    }

    private void renderCreate(HttpServletRequest request, HttpServletResponse response, String error) throws IOException {
        page(request, response, "New initiative - InteropHub", null, null, false, out -> {
            InitiativePageRenderer.createForm(out, request.getContextPath(), initiatives.activeTopics(), error);
        });
    }

    private void renderOverview(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative, InitiativeAccess.Level level) throws IOException {
        EsTopic topic = initiatives.findTopic(initiative.getPrimaryEsTopicId()).orElse(null);
        page(request, response, initiative.getInitiativeName() + " - InteropHub", initiative, "overview",
                InitiativeAccess.canEditRoster(level), out -> InitiativePageRenderer.overview(out, request.getContextPath(),
                        initiative, topic, initiatives.links(initiative.getEsInitiativeId()),
                        representation(initiative),
                        initiatives.timeline(EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiative.getEsInitiativeId()),
                        names(initiatives.timeline(EsInitiativeTimelineEntry.SubjectType.INITIATIVE,
                                initiative.getEsInitiativeId())),
                        user, InitiativeAccess.canComment(level)));
    }

    private void renderOrganizations(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative, InitiativeAccess.Level level) throws IOException {
        List<EsInitiativeOrganization> participations = initiatives.organizations(initiative.getEsInitiativeId());
        List<EsInitiativeMember> members = initiatives.members(initiative.getEsInitiativeId());
        Map<Long, Organization> organizations = indexOrganizations(participations);
        List<EsInitiativeTimelineEntry> timeline = initiatives.timelineForInitiative(initiative.getEsInitiativeId(),
                EsInitiativeTimelineEntry.SubjectType.ORGANIZATION);
        page(request, response, initiative.getInitiativeName() + " organizations - InteropHub", initiative,
                "organizations", true, out -> InitiativePageRenderer.organizations(out, request.getContextPath(),
                        initiative, participations, organizations, members, timeline, names(timeline), user,
                        representation(initiative), InitiativeAccess.canEditRoster(level)));
    }

    private void renderPeople(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative, InitiativeAccess.Level level) throws IOException {
        List<EsInitiativeOrganization> participations = initiatives.organizations(initiative.getEsInitiativeId());
        Map<Long, Organization> organizations = indexOrganizations(participations);
        String statusFilter = param(request, "status");
        String organizationFilter = param(request, "organization");
        String typeFilter = param(request, "type");
        Long organizationId = longParam(request, "organization");
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
        List<EsInitiativeTimelineEntry> timeline = initiatives.timelineForInitiative(initiative.getEsInitiativeId(),
                EsInitiativeTimelineEntry.SubjectType.MEMBER);
        page(request, response, initiative.getInitiativeName() + " people - InteropHub", initiative, "people", true,
                out -> InitiativePageRenderer.people(out, request.getContextPath(), initiative, visible, participations,
                        organizations, timeline, names(timeline), user, missing, statusFilter, organizationFilter,
                        typeFilter, InitiativeAccess.canEditRoster(level)));
    }

    private void renderSettings(HttpServletRequest request, HttpServletResponse response, EsInitiative initiative)
            throws IOException {
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
        page(request, response, current.getInitiativeName() + " settings - InteropHub", current, "settings", false,
                out -> InitiativePageRenderer.settings(out, request.getContextPath(), current, initiatives.activeTopics(),
                        managers, users, initiatives.links(current.getEsInitiativeId())));
    }

    private void renderImport(HttpServletRequest request, HttpServletResponse response, User user,
            EsInitiative initiative) throws IOException {
        HeldImport held = held(request, user, initiative);
        page(request, response, initiative.getInitiativeName() + " import - InteropHub", initiative, "import", true,
                out -> InitiativePageRenderer.importPage(out, request.getContextPath(), initiative,
                        held == null ? null : held.headers, held == null ? null : held.mapping,
                        held == null ? null : held.preview));
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
            String section, boolean typeahead, PageBody body) throws IOException {
        InteropAiraPageFactory.markInitiativesNavActive(request);
        response.setContentType("text/html;charset=UTF-8");
        AiraPage page = InteropAiraPageFactory.base(request, title)
                .applicationSubtitle("Initiatives")
                .mainClass("aira-main")
                .addLocalStylesheet("/css/initiative-org.css")
                .context(InteropAiraPageFactory.topicsMeetingsContext("InteropHub", null, false, false))
                .build();
        String kind = (String) request.getSession().getAttribute(FLASH_KIND);
        String message = (String) request.getSession().getAttribute(FLASH);
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
            if (typeahead) {
                out.println("    <script src=\"" + InitiativeHtml.esc(request.getContextPath())
                        + "/js/initiative-org-typeahead.js\"></script>");
            }
            page.writeEnd(out);
        }
    }

    private void renderMissing(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        page(request, response, "Initiative - InteropHub", null, null, false, out -> {
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
        response.sendRedirect(request.getContextPath() + "/es/initiatives/" + current.getInitiativeCode() + suffix);
    }

    private boolean requiresSettings(String action) {
        return "save-settings".equals(action) || "add-manager".equals(action) || "manager-role".equals(action)
                || "remove-manager".equals(action) || "add-link".equals(action) || "delete-link".equals(action);
    }

    private String redirectAfter(String section, String action) {
        if ("commit".equals(action)) {
            return "/people";
        }
        if ("overview".equals(section)) {
            return "";
        }
        return "/" + section;
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
