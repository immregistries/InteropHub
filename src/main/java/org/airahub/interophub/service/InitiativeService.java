package org.airahub.interophub.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.dao.EsChecklistDao;
import org.airahub.interophub.dao.EsChecklistItemDao;
import org.airahub.interophub.dao.EsInitiativeDao;
import org.airahub.interophub.dao.EsInitiativeLinkDao;
import org.airahub.interophub.dao.EsInitiativeManagerDao;
import org.airahub.interophub.dao.EsInitiativeMemberDao;
import org.airahub.interophub.dao.EsInitiativeOrganizationDao;
import org.airahub.interophub.dao.EsInitiativeTimelineEntryDao;
import org.airahub.interophub.dao.EsSmallGroupDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.OrganizationDao;
import org.airahub.interophub.dao.UserDao;
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
import org.airahub.interophub.service.TopicFollowerManagementService.Outcome;

/**
 * Creates initiatives and keeps their roster, timeline, and primary-topic follows
 * in step. Callers enforce {@link InitiativeAccess} before mutating.
 */
public class InitiativeService {

    private static final Logger LOGGER = Logger.getLogger(InitiativeService.class.getName());

    public record ImportResult(int organizationsAdded, int membersAdded, int notesAdded) {
    }

    private final EsInitiativeDao initiativeDao;
    private final EsInitiativeManagerDao managerDao;
    private final EsInitiativeLinkDao linkDao;
    private final EsInitiativeOrganizationDao initiativeOrganizationDao;
    private final EsInitiativeMemberDao memberDao;
    private final EsInitiativeTimelineEntryDao timelineDao;
    private final EsChecklistDao checklistDao;
    private final EsSmallGroupDao smallGroupDao;
    private final EsChecklistItemDao checklistItemDao;
    private final OrganizationDao organizationDao;
    private final EsTopicDao topicDao;
    private final UserDao userDao;
    private final EsSubscriptionDao subscriptionDao;
    private final TopicFollowerManagementService followerService;
    private final AuthFlowService authFlowService;

    public InitiativeService() {
        this.initiativeDao = new EsInitiativeDao();
        this.managerDao = new EsInitiativeManagerDao();
        this.linkDao = new EsInitiativeLinkDao();
        this.initiativeOrganizationDao = new EsInitiativeOrganizationDao();
        this.memberDao = new EsInitiativeMemberDao();
        this.timelineDao = new EsInitiativeTimelineEntryDao();
        this.checklistDao = new EsChecklistDao();
        this.smallGroupDao = new EsSmallGroupDao();
        this.checklistItemDao = new EsChecklistItemDao();
        this.organizationDao = new OrganizationDao();
        this.topicDao = new EsTopicDao();
        this.userDao = new UserDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.followerService = new TopicFollowerManagementService();
        this.authFlowService = new AuthFlowService();
    }

    public InitiativeAccess.Level access(User user, EsInitiative initiative) {
        if (user == null || initiative == null) {
            return InitiativeAccess.Level.NONE;
        }
        EsInitiativeManager.ManagerRole role = managerDao
                .findByInitiativeAndUser(initiative.getEsInitiativeId(), user.getUserId())
                .map(EsInitiativeManager::getManagerRole)
                .orElse(null);
        return InitiativeAccess.level(authFlowService.isAdminUser(user), role);
    }

    public boolean canSeeNavigation(User user) {
        if (user == null) {
            return false;
        }
        return authFlowService.isAdminUser(user) || managerDao.existsForUser(user.getUserId());
    }

    public Optional<EsTopic> findTopic(Long topicId) {
        if (topicId == null) {
            return Optional.empty();
        }
        return topicDao.findById(topicId);
    }

    public List<EsInitiative> listVisible(User user) {
        if (user == null) {
            return List.of();
        }
        if (authFlowService.isAdminUser(user)) {
            return initiativeDao.findAllOrderByName();
        }
        List<Long> ids = new ArrayList<>();
        for (EsInitiativeManager manager : managerDao.findByUserId(user.getUserId())) {
            ids.add(manager.getEsInitiativeId());
        }
        return initiativeDao.findByIds(ids);
    }

    public Optional<EsInitiative> findByCode(String code) {
        return initiativeDao.findByCode(code);
    }

    public List<EsTopic> activeTopics() {
        return topicDao.findAllActiveOrderByTopicName();
    }

    public List<EsInitiativeManager> managers(Long initiativeId) {
        return managerDao.findByInitiativeId(initiativeId);
    }

    public List<EsInitiativeLink> links(Long initiativeId) {
        return linkDao.findByInitiativeId(initiativeId);
    }

    public List<EsInitiativeOrganization> organizations(Long initiativeId) {
        return initiativeOrganizationDao.findByInitiativeId(initiativeId);
    }

    public List<EsInitiativeMember> members(Long initiativeId) {
        return memberDao.findByInitiativeId(initiativeId);
    }

    public List<Organization> organizationsByIds(Set<Long> ids) {
        List<Organization> found = new ArrayList<>();
        if (ids == null) {
            return found;
        }
        for (Long id : ids) {
            organizationDao.findById(id).ifPresent(found::add);
        }
        return found;
    }

    public List<User> usersByIds(Set<Long> ids) {
        return userDao.findByIds(ids);
    }

    public List<EsInitiativeTimelineEntry> timeline(EsInitiativeTimelineEntry.SubjectType type, Long subjectId) {
        return timelineDao.findBySubject(type, subjectId);
    }

    public List<EsInitiativeTimelineEntry> timelineForInitiative(Long initiativeId,
            EsInitiativeTimelineEntry.SubjectType type) {
        return timelineDao.findByInitiativeAndSubjectType(initiativeId, type);
    }

    public List<Organization> suggestOrganizations(String query) {
        return OrganizationDirectory.rank(organizationDao.findActiveMatchingName(query, 40), query, 8);
    }

    public EsInitiative create(User actor, String name, String code, String summary, String description, Long topicId,
            EsInitiative.Status status, EsInitiative.Visibility visibility, EsInitiative.JoinPolicy joinPolicy,
            LocalDate startDate, LocalDate endDate) {
        String initiativeName = requireText(name, "Name", 200);
        String initiativeCode = trimToNull(code);
        if (initiativeCode == null) {
            initiativeCode = InitiativeCodes.slug(initiativeName);
        }
        String codeError = InitiativeCodes.validationError(initiativeCode);
        if (codeError != null) {
            throw new IllegalArgumentException(codeError);
        }
        if (initiativeDao.findByCode(initiativeCode).isPresent()) {
            throw new IllegalArgumentException("That code is already used.");
        }
        requireTopic(topicId);
        requireDateOrder(startDate, endDate);
        if (status == null || visibility == null || joinPolicy == null) {
            throw new IllegalArgumentException("Status, visibility, and join policy are required.");
        }

        EsInitiative initiative = new EsInitiative();
        initiative.setInitiativeName(initiativeName);
        initiative.setInitiativeCode(initiativeCode);
        initiative.setSummary(trimToNull(summary));
        initiative.setDescription(trimToNull(description));
        initiative.setPrimaryEsTopicId(topicId);
        initiative.setStatus(status);
        initiative.setVisibility(visibility);
        initiative.setJoinPolicy(joinPolicy);
        initiative.setStartDate(startDate);
        initiative.setEndDate(endDate);
        initiative.setCreatedByUserId(actor.getUserId());
        initiative = initiativeDao.save(initiative);

        EsInitiativeManager manager = new EsInitiativeManager();
        manager.setEsInitiativeId(initiative.getEsInitiativeId());
        manager.setUserId(actor.getUserId());
        manager.setManagerRole(EsInitiativeManager.ManagerRole.ADMIN);
        manager.setCreatedByUserId(actor.getUserId());
        managerDao.save(manager);

        addPlanned(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.INITIATIVE,
                initiative.getEsInitiativeId(), actor,
                InitiativeRosterRules.planStatusUpdate(null, status.name(), null), EsInitiative.Status::labelOf);
        new ChecklistService().copyCurrentIfPresent(initiative, actor);
        return initiative;
    }

    public void updateSettings(User actor, EsInitiative initiative, String name, String code, String summary,
            String description, Long topicId, EsInitiative.Status status, EsInitiative.Visibility visibility,
            EsInitiative.JoinPolicy joinPolicy, LocalDate startDate, LocalDate endDate) {
        String initiativeName = requireText(name, "Name", 200);
        String initiativeCode = trimToNull(code);
        if (initiativeCode == null) {
            initiativeCode = InitiativeCodes.slug(initiativeName);
        }
        String codeError = InitiativeCodes.validationError(initiativeCode);
        if (codeError != null) {
            throw new IllegalArgumentException(codeError);
        }
        initiativeDao.findByCode(initiativeCode).ifPresent(existing -> {
            if (!existing.getEsInitiativeId().equals(initiative.getEsInitiativeId())) {
                throw new IllegalArgumentException("That code is already used.");
            }
        });
        EsTopic topic = requireTopic(topicId);
        requireDateOrder(startDate, endDate);
        if (status == null || visibility == null || joinPolicy == null) {
            throw new IllegalArgumentException("Status, visibility, and join policy are required.");
        }

        Long initiativeId = initiative.getEsInitiativeId();
        recordField(initiativeId, EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiativeId, actor, "Name",
                initiative.getInitiativeName(), initiativeName);
        recordField(initiativeId, EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiativeId, actor, "Code",
                initiative.getInitiativeCode(), initiativeCode);
        recordField(initiativeId, EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiativeId, actor, "Summary",
                initiative.getSummary(), trimToNull(summary));
        recordField(initiativeId, EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiativeId, actor, "Description",
                initiative.getDescription(), trimToNull(description));
        if (!topicId.equals(initiative.getPrimaryEsTopicId())) {
            String previousTopic = topicDao.findById(initiative.getPrimaryEsTopicId()).map(EsTopic::getTopicName)
                    .orElse(String.valueOf(initiative.getPrimaryEsTopicId()));
            recordField(initiativeId, EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiativeId, actor,
                    "Primary topic", previousTopic, topic.getTopicName());
        }
        recordField(initiativeId, EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiativeId, actor, "Visibility",
                initiative.getVisibility().getLabel(), visibility.getLabel());
        recordField(initiativeId, EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiativeId, actor, "Join policy",
                initiative.getJoinPolicy().getLabel(), joinPolicy.getLabel());
        recordField(initiativeId, EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiativeId, actor, "Start date",
                dateText(initiative.getStartDate()), dateText(startDate));
        recordField(initiativeId, EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiativeId, actor, "End date",
                dateText(initiative.getEndDate()), dateText(endDate));
        InitiativeRosterRules.PlannedEntry statusChange = InitiativeRosterRules.planStatusUpdate(
                initiative.getStatus().name(), status.name(), null);
        if (statusChange != null) {
            addPlanned(initiativeId, EsInitiativeTimelineEntry.SubjectType.INITIATIVE, initiativeId, actor,
                    statusChange, EsInitiative.Status::labelOf);
        }

        initiative.setInitiativeName(initiativeName);
        initiative.setInitiativeCode(initiativeCode);
        initiative.setSummary(trimToNull(summary));
        initiative.setDescription(trimToNull(description));
        initiative.setPrimaryEsTopicId(topicId);
        initiative.setStatus(status);
        initiative.setVisibility(visibility);
        initiative.setJoinPolicy(joinPolicy);
        initiative.setStartDate(startDate);
        initiative.setEndDate(endDate);
        initiativeDao.save(initiative);
    }

    public void addManager(User actor, EsInitiative initiative, String email, EsInitiativeManager.ManagerRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Choose a manager role.");
        }
        String normalized = EsNormalizer.normalizeEmail(email);
        if (normalized == null || !normalized.contains("@")) {
            throw new IllegalArgumentException("Enter the email address of an InteropHub account.");
        }
        User managerUser = userDao.findByEmailNormalized(normalized)
                .filter(user -> user.getStatus() != User.UserStatus.DELETED)
                .orElseThrow(() -> new IllegalArgumentException(
                        "That person needs an InteropHub account before they can manage this initiative."));
        if (managerDao.findByInitiativeAndUser(initiative.getEsInitiativeId(), managerUser.getUserId()).isPresent()) {
            throw new IllegalArgumentException("That person is already a manager.");
        }
        EsInitiativeManager manager = new EsInitiativeManager();
        manager.setEsInitiativeId(initiative.getEsInitiativeId());
        manager.setUserId(managerUser.getUserId());
        manager.setManagerRole(role);
        manager.setCreatedByUserId(actor.getUserId());
        managerDao.save(manager);
        addComment(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.INITIATIVE,
                initiative.getEsInitiativeId(), actor,
                "Added " + displayUser(managerUser) + " as " + role.getLabel() + ".");
    }

    public void changeManagerRole(User actor, EsInitiative initiative, Long managerId,
            EsInitiativeManager.ManagerRole role) {
        EsInitiativeManager manager = requireManager(initiative, managerId);
        if (role == null) {
            throw new IllegalArgumentException("Choose a manager role.");
        }
        if (manager.getManagerRole() == role) {
            return;
        }
        String from = manager.getManagerRole().getLabel();
        manager.setManagerRole(role);
        managerDao.save(manager);
        User managerUser = userDao.findById(manager.getUserId()).orElse(null);
        recordField(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.INITIATIVE,
                initiative.getEsInitiativeId(), actor, "Manager role for " + displayUser(managerUser), from,
                role.getLabel());
    }

    public void removeManager(User actor, EsInitiative initiative, Long managerId) {
        EsInitiativeManager manager = requireManager(initiative, managerId);
        User managerUser = userDao.findById(manager.getUserId()).orElse(null);
        managerDao.deleteById(manager.getEsInitiativeManagerId());
        addComment(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.INITIATIVE,
                initiative.getEsInitiativeId(), actor, "Removed " + displayUser(managerUser) + " as a manager.");
    }

    public void addLink(User actor, EsInitiative initiative, String label, String url) {
        String linkLabel = requireText(label, "Link label", 200);
        String linkUrl = requireUrl(url);
        EsInitiativeLink link = new EsInitiativeLink();
        link.setEsInitiativeId(initiative.getEsInitiativeId());
        link.setLinkLabel(linkLabel);
        link.setLinkUrl(linkUrl);
        link.setDisplayOrder(linkDao.nextDisplayOrder(initiative.getEsInitiativeId()));
        link.setCreatedByUserId(actor.getUserId());
        linkDao.save(link);
    }

    public void deleteLink(EsInitiative initiative, Long linkId) {
        EsInitiativeLink link = linkDao.findById(linkId)
                .filter(item -> initiative.getEsInitiativeId().equals(item.getEsInitiativeId()))
                .orElseThrow(() -> new IllegalArgumentException("That link was not found."));
        linkDao.deleteById(link.getEsInitiativeLinkId());
    }

    public Organization createOrganization(User actor, String name, String typeCode, String website) {
        String error = OrganizationDirectory.validationError(name, typeCode);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        Organization organization = new Organization();
        organization.setOrganizationName(name.trim());
        organization.setOrganizationType(typeCode.trim());
        organization.setWebsiteUrl(OrganizationDirectory.normalizeWebsite(website));
        organization.setActive(Boolean.TRUE);
        organization.setCreatedByUserId(actor.getUserId());
        return organizationDao.save(organization);
    }

    public EsInitiativeOrganization addOrganization(User actor, EsInitiative initiative, Long organizationId,
            String newName, String newType, String newWebsite, EsInitiativeOrganization.ParticipationStatus status,
            String note) {
        Organization organization = resolveOrganization(actor, organizationId, newName, newType, newWebsite);
        if (initiativeOrganizationDao
                .findByInitiativeAndOrganization(initiative.getEsInitiativeId(), organization.getOrganizationId())
                .isPresent()) {
            throw new IllegalArgumentException(organization.getOrganizationName() + " is already on this initiative.");
        }
        EsInitiativeOrganization.ParticipationStatus chosen = status == null
                ? EsInitiativeOrganization.ParticipationStatus.PROSPECT
                : status;
        EsInitiativeOrganization participation = newParticipation(actor, initiative, organization, chosen);
        participation = initiativeOrganizationDao.save(participation);
        if (chosen != EsInitiativeOrganization.ParticipationStatus.PROSPECT || trimToNull(note) != null) {
            addPlanned(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.ORGANIZATION,
                    participation.getEsInitiativeOrganizationId(), actor,
                    InitiativeRosterRules.planStatusUpdate(null, chosen.name(), note),
                    EsInitiativeOrganization.ParticipationStatus::labelOf);
        }
        return participation;
    }

    public void changeOrganizationStatus(User actor, EsInitiative initiative, Long participationId,
            EsInitiativeOrganization.ParticipationStatus status, String note) {
        EsInitiativeOrganization participation = requireParticipation(initiative, participationId);
        if (status == null) {
            throw new IllegalArgumentException("Choose a status.");
        }
        InitiativeRosterRules.PlannedEntry planned = InitiativeRosterRules.planStatusUpdate(
                participation.getParticipationStatus().name(), status.name(), note);
        if (planned == null) {
            return;
        }
        if (planned.kind() == InitiativeRosterRules.PlannedKind.STATUS_CHANGE) {
            participation.setParticipationStatus(status);
            participation.setStatusChangedAt(LocalDateTime.now());
            participation.setStatusChangedByUserId(actor.getUserId());
            initiativeOrganizationDao.save(participation);
        }
        addPlanned(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.ORGANIZATION,
                participation.getEsInitiativeOrganizationId(), actor, planned,
                EsInitiativeOrganization.ParticipationStatus::labelOf);
    }

    public void changeOrganizationType(User actor, EsInitiative initiative, Long participationId, String typeCode) {
        EsInitiativeOrganization participation = requireParticipation(initiative, participationId);
        if (!OrganizationDirectory.isKnown(typeCode)) {
            throw new IllegalArgumentException("Choose an organization type.");
        }
        Organization organization = organizationDao.findById(participation.getOrganizationId())
                .orElseThrow(() -> new IllegalArgumentException("That organization was not found."));
        if (typeCode.equals(organization.getOrganizationType())) {
            return;
        }
        String from = OrganizationDirectory.label(organization.getOrganizationType());
        organization.setOrganizationType(typeCode);
        organizationDao.save(organization);
        recordField(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.ORGANIZATION,
                participation.getEsInitiativeOrganizationId(), actor, "Type", from,
                OrganizationDirectory.label(typeCode));
    }

    /**
     * @return a warning when the member was saved but the primary-topic follow did not
     */
    public String addMember(User actor, EsInitiative initiative, String firstName, String lastName, String email,
            Long linkedUserId, Long organizationId, String newName, String newType, String newWebsite, String role,
            EsInitiativeMember.MemberStatus status, EsInitiativeMember.MemberSource source, String note) {
        return placePerson(actor, initiative, "create", null, linkedUserId, email, firstName, lastName, organizationId,
                newName, newType, newWebsite, role, status, source, note).warning();
    }

    /**
     * Finds or creates a roster row from a people-search choice.
     * {@code intent} is {@code create} (a duplicate is an error), {@code contact}
     * (an existing person is attached to the organization), or {@code participant}
     * (an existing person is returned for the Small Group).
     */
    public PersonPlacement placePerson(User actor, EsInitiative initiative, String intent, Long memberId, Long userId,
            String email, String firstName, String lastName, Long organizationId, String newName, String newType,
            String newWebsite, String role, EsInitiativeMember.MemberStatus status,
            EsInitiativeMember.MemberSource source, String note) {
        PreparedPerson prepared = preparePerson(email, firstName, lastName, userId);
        EsInitiativeMember existing = findExistingMember(initiative, memberId, prepared.userId(), prepared.normalized());
        EsInitiativeOrganization participation = resolveParticipation(actor, initiative, organizationId, newName,
                newType, newWebsite);
        if (existing != null) {
            PersonNames.Names filled = PersonNames.fillBlanks(existing.getContactFirstName(),
                    existing.getContactLastName(), prepared.names().firstName(), prepared.names().lastName());
            existing.setContactFirstName(filled.firstName());
            existing.setContactLastName(filled.lastName());
            if (prepared.normalized() != null && existing.getEmailNormalized() == null) {
                applyEmail(existing, prepared.normalized(), prepared.rawEmail());
            } else {
                applyEmail(existing, existing.getEmailNormalized(), existing.getEmail());
            }
            if ("contact".equals(intent) && participation != null
                    && existing.getEsInitiativeOrganizationId() == null) {
                existing.setEsInitiativeOrganizationId(participation.getEsInitiativeOrganizationId());
            }
            memberDao.save(existing);
            if ("create".equals(intent)) {
                throw new IllegalArgumentException("That person is already on this roster.");
            }
            if ("contact".equals(intent) && participation != null
                    && !participation.getEsInitiativeOrganizationId().equals(existing.getEsInitiativeOrganizationId())) {
                throw new IllegalArgumentException("That person is already on this roster.");
            }
            return new PersonPlacement(existing, null, false);
        }
        if (prepared.names().firstName() == null && prepared.names().lastName() == null
                && prepared.normalized() == null) {
            throw new IllegalArgumentException("Enter a first name, a last name, or an email address.");
        }
        EsInitiativeMember.MemberStatus chosen = status == null ? EsInitiativeMember.MemberStatus.PROSPECT : status;
        EsInitiativeMember.MemberSource chosenSource = source == null ? EsInitiativeMember.MemberSource.STAFF_ADDED
                : source;
        EsInitiativeMember member = new EsInitiativeMember();
        member.setEsInitiativeId(initiative.getEsInitiativeId());
        member.setContactFirstName(prepared.names().firstName());
        member.setContactLastName(prepared.names().lastName());
        applyEmail(member, prepared.normalized(), prepared.rawEmail());
        member.setEsInitiativeOrganizationId(
                participation == null ? null : participation.getEsInitiativeOrganizationId());
        member.setInitiativeRole(limit(trimToNull(role), 200));
        member.setMemberStatus(chosen);
        member.setMemberSource(chosenSource);
        member.setStatusChangedAt(LocalDateTime.now());
        member.setStatusChangedByUserId(actor.getUserId());
        member.setCreatedByUserId(actor.getUserId());
        if (chosen == EsInitiativeMember.MemberStatus.JOINED) {
            member.setJoinedAt(LocalDateTime.now());
        }
        member = memberDao.save(member);
        if (chosen != EsInitiativeMember.MemberStatus.PROSPECT || trimToNull(note) != null) {
            addPlanned(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.MEMBER,
                    member.getEsInitiativeMemberId(), actor,
                    InitiativeRosterRules.planStatusUpdate(null, chosen.name(), note),
                    EsInitiativeMember.MemberStatus::labelOf);
        }
        return new PersonPlacement(member,
                followIfJoined(actor, initiative, member, null, organizationName(participation)), true);
    }

    public void updateMember(User actor, EsInitiative initiative, Long memberId, String firstName, String lastName,
            String email, Long organizationId, String newName, String newType, String newWebsite, String role) {
        EsInitiativeMember member = requireMember(initiative, memberId);
        PreparedPerson prepared = preparePerson(email, firstName, lastName, member.getUserId());
        String normalized = prepared.normalized();
        if (prepared.names().firstName() == null && prepared.names().lastName() == null && normalized == null) {
            throw new IllegalArgumentException("Enter a first name, a last name, or an email address.");
        }
        if (normalized != null) {
            memberDao.findByInitiativeAndEmail(initiative.getEsInitiativeId(), normalized).ifPresent(existing -> {
                if (!existing.getEsInitiativeMemberId().equals(member.getEsInitiativeMemberId())) {
                    throw new IllegalArgumentException("Someone with that email is already on this roster.");
                }
            });
        }
        EsInitiativeOrganization participation = resolveParticipation(actor, initiative, organizationId, newName,
                newType, newWebsite);
        Long subjectId = member.getEsInitiativeMemberId();
        recordField(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.MEMBER, subjectId, actor,
                "Name", member.displayName(),
                PersonNames.display(null, null, null, prepared.names().firstName(), prepared.names().lastName(),
                        prepared.rawEmail()));
        recordField(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.MEMBER, subjectId, actor,
                "Email", member.getEmail(), trimToNull(email));
        recordField(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.MEMBER, subjectId, actor,
                "Role", member.getInitiativeRole(), limit(trimToNull(role), 200));
        String previousOrg = participationName(member.getEsInitiativeOrganizationId());
        String nextOrg = organizationName(participation);
        recordField(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.MEMBER, subjectId, actor,
                "Organization", previousOrg, nextOrg);

        member.setContactFirstName(prepared.names().firstName());
        member.setContactLastName(prepared.names().lastName());
        applyEmail(member, normalized, prepared.rawEmail());
        member.setEsInitiativeOrganizationId(
                participation == null ? null : participation.getEsInitiativeOrganizationId());
        member.setInitiativeRole(limit(trimToNull(role), 200));
        memberDao.save(member);
    }

    /**
     * @return a warning when the status was saved but the primary-topic follow did not
     */
    public String changeMemberStatus(User actor, EsInitiative initiative, Long memberId,
            EsInitiativeMember.MemberStatus status, String note) {
        EsInitiativeMember member = requireMember(initiative, memberId);
        if (status == null) {
            throw new IllegalArgumentException("Choose a status.");
        }
        String previous = member.getMemberStatus().name();
        InitiativeRosterRules.PlannedEntry planned = InitiativeRosterRules.planStatusUpdate(previous, status.name(),
                note);
        if (planned == null) {
            return null;
        }
        if (planned.kind() == InitiativeRosterRules.PlannedKind.STATUS_CHANGE) {
            member.setMemberStatus(status);
            member.setStatusChangedAt(LocalDateTime.now());
            member.setStatusChangedByUserId(actor.getUserId());
            if (status == EsInitiativeMember.MemberStatus.JOINED && member.getJoinedAt() == null) {
                member.setJoinedAt(LocalDateTime.now());
            }
            memberDao.save(member);
        }
        addPlanned(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.MEMBER,
                member.getEsInitiativeMemberId(), actor, planned, EsInitiativeMember.MemberStatus::labelOf);
        return followIfJoined(actor, initiative, member, previous,
                participationName(member.getEsInitiativeOrganizationId()));
    }

    public void addComment(User actor, EsInitiative initiative, EsInitiativeTimelineEntry.SubjectType subjectType,
            Long subjectId, String note) {
        requireSubject(initiative, subjectType, subjectId);
        String text = trimToNull(note);
        if (text == null) {
            throw new IllegalArgumentException("Enter a comment.");
        }
        addComment(initiative.getEsInitiativeId(), subjectType, subjectId, actor, text);
    }

    public void editComment(User actor, EsInitiative initiative, Long entryId, String note) {
        EsInitiativeTimelineEntry entry = timelineDao.findById(entryId)
                .filter(item -> initiative.getEsInitiativeId().equals(item.getEsInitiativeId()))
                .orElseThrow(() -> new IllegalArgumentException("That timeline entry was not found."));
        if (!InitiativeRosterRules.canEditEntry(entry.getEntryType(), entry.getCreatedByUserId(), actor.getUserId())) {
            throw new IllegalArgumentException("You can edit only your own comments.");
        }
        String text = trimToNull(note);
        if (text == null) {
            throw new IllegalArgumentException("Enter a comment.");
        }
        entry.setNoteText(text);
        entry.setEditedAt(LocalDateTime.now());
        timelineDao.save(entry);
    }

    public InitiativeImport.Preview previewImport(EsInitiative initiative, InitiativeImport.ParsedSheet sheet,
            InitiativeImport.ColumnMapping mapping) {
        List<InitiativeImport.KnownOrganization> known = new ArrayList<>();
        Set<Long> onInitiative = new HashSet<>();
        for (EsInitiativeOrganization participation : organizations(initiative.getEsInitiativeId())) {
            onInitiative.add(participation.getOrganizationId());
        }
        for (Organization organization : organizationDao.findAllOrderByName()) {
            known.add(new InitiativeImport.KnownOrganization(
                    OrganizationDirectory.normalizeName(organization.getOrganizationName()),
                    organization.getOrganizationType(),
                    onInitiative.contains(organization.getOrganizationId())));
        }
        List<InitiativeImport.KnownMember> knownMembers = new ArrayList<>();
        for (EsInitiativeMember member : members(initiative.getEsInitiativeId())) {
            if (member.getEmailNormalized() != null) {
                knownMembers.add(new InitiativeImport.KnownMember(member.getEmailNormalized()));
            }
        }
        return InitiativeImport.preview(sheet, mapping, known, knownMembers);
    }

    public ImportResult commitImport(User actor, EsInitiative initiative, InitiativeImport.Preview preview) {
        if (preview == null) {
            throw new IllegalArgumentException("Import preview expired. Upload the file again.");
        }
        Map<String, Organization> byName = new HashMap<>();
        for (Organization organization : organizationDao.findAllOrderByName()) {
            byName.putIfAbsent(OrganizationDirectory.normalizeName(organization.getOrganizationName()), organization);
        }
        int organizationsAdded = 0;
        int membersAdded = 0;
        int notesAdded = 0;
        for (InitiativeImport.PreviewRow row : preview.rows()) {
            if (row.action() != InitiativeImport.Action.ADD_MEMBER
                    && row.action() != InitiativeImport.Action.ADD_ORGANIZATION) {
                continue;
            }
            EsInitiativeOrganization participation = null;
            if (row.organizationName() != null) {
                boolean added = ensureImportedOrganization(actor, initiative, byName, row);
                if (added) {
                    organizationsAdded++;
                }
                Organization organization = byName.get(OrganizationDirectory.normalizeName(row.organizationName()));
                if (organization != null) {
                    participation = initiativeOrganizationDao
                            .findByInitiativeAndOrganization(initiative.getEsInitiativeId(),
                                    organization.getOrganizationId())
                            .orElse(null);
                }
            }
            if (row.action() == InitiativeImport.Action.ADD_ORGANIZATION) {
                if (participation != null && row.notes() != null) {
                    addComment(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.ORGANIZATION,
                            participation.getEsInitiativeOrganizationId(), actor, row.notes());
                    notesAdded++;
                }
                continue;
            }
            PersonNames.Names contact = PersonNames.splitFullName(row.contactName());
            String normalized = EsNormalizer.normalizeEmail(row.email());
            if (normalized != null && memberDao.findByInitiativeAndEmail(initiative.getEsInitiativeId(), normalized)
                    .isPresent()) {
                continue;
            }
            EsInitiativeMember member = new EsInitiativeMember();
            member.setEsInitiativeId(initiative.getEsInitiativeId());
            member.setContactFirstName(contact.firstName());
            member.setContactLastName(contact.lastName());
            applyEmail(member, normalized, row.email());
            member.setEsInitiativeOrganizationId(
                    participation == null ? null : participation.getEsInitiativeOrganizationId());
            member.setMemberStatus(EsInitiativeMember.MemberStatus.PROSPECT);
            member.setMemberSource(EsInitiativeMember.MemberSource.SPREADSHEET_IMPORT);
            member.setStatusChangedAt(LocalDateTime.now());
            member.setStatusChangedByUserId(actor.getUserId());
            member.setCreatedByUserId(actor.getUserId());
            member = memberDao.save(member);
            membersAdded++;
            if (row.notes() != null) {
                addComment(initiative.getEsInitiativeId(), EsInitiativeTimelineEntry.SubjectType.MEMBER,
                        member.getEsInitiativeMemberId(), actor, row.notes());
                notesAdded++;
            }
        }
        return new ImportResult(organizationsAdded, membersAdded, notesAdded);
    }

    public boolean followsPrimaryTopic(EsInitiative initiative, EsInitiativeMember member,
            List<EsSubscription> activeFollows) {
        if (member.getMemberStatus() != EsInitiativeMember.MemberStatus.JOINED) {
            return true;
        }
        if (activeFollows == null) {
            return false;
        }
        for (EsSubscription subscription : activeFollows) {
            if (!initiative.getPrimaryEsTopicId().equals(subscription.getEsTopicId())) {
                continue;
            }
            if (member.getUserId() != null && member.getUserId().equals(subscription.getUserId())) {
                return true;
            }
            if (member.getEmailNormalized() != null
                    && member.getEmailNormalized().equals(subscription.getEmailNormalized())) {
                return true;
            }
        }
        return false;
    }

    public boolean primaryTopicHasChampion(EsInitiative initiative) {
        return !subscriptionDao.findChampionOnlyByTopicId(initiative.getPrimaryEsTopicId()).isEmpty();
    }

    public List<EsSubscription> activePrimaryTopicFollows(EsInitiative initiative) {
        return subscriptionDao.findActiveByTopicId(initiative.getPrimaryEsTopicId());
    }

    private boolean ensureImportedOrganization(User actor, EsInitiative initiative, Map<String, Organization> byName,
            InitiativeImport.PreviewRow row) {
        String key = OrganizationDirectory.normalizeName(row.organizationName());
        Organization organization = byName.get(key);
        boolean created = false;
        if (organization == null) {
            if (row.organizationTypeCode() == null) {
                throw new IllegalArgumentException("Organization type is required to add " + row.organizationName() + ".");
            }
            organization = createOrganization(actor, row.organizationName(), row.organizationTypeCode(), null);
            byName.put(key, organization);
            created = true;
        }
        if (initiativeOrganizationDao
                .findByInitiativeAndOrganization(initiative.getEsInitiativeId(), organization.getOrganizationId())
                .isPresent()) {
            return false;
        }
        initiativeOrganizationDao.save(newParticipation(actor, initiative, organization,
                EsInitiativeOrganization.ParticipationStatus.PROSPECT));
        return created || row.addToInitiative();
    }

    private EsInitiativeOrganization newParticipation(User actor, EsInitiative initiative, Organization organization,
            EsInitiativeOrganization.ParticipationStatus status) {
        EsInitiativeOrganization participation = new EsInitiativeOrganization();
        participation.setEsInitiativeId(initiative.getEsInitiativeId());
        participation.setOrganizationId(organization.getOrganizationId());
        participation.setParticipationStatus(status);
        participation.setStatusChangedAt(LocalDateTime.now());
        participation.setStatusChangedByUserId(actor.getUserId());
        participation.setCreatedByUserId(actor.getUserId());
        return participation;
    }

    private String followIfJoined(User actor, EsInitiative initiative, EsInitiativeMember member, String previousStatus,
            String organizationName) {
        EsSubscription existing = subscriptionDao.findByUserOrEmailAndTopic(member.getUserId(),
                member.getEmailNormalized(), initiative.getPrimaryEsTopicId()).orElse(null);
        Optional<InitiativeFollowRules.FollowRequest> request = InitiativeFollowRules.plan(
                initiative.getInitiativeName(), initiative.getPrimaryEsTopicId(), previousStatus,
                member.getMemberStatus().name(), member.getEmail(), member.getContactFirstName(),
                member.getContactLastName(), organizationName,
                existing == null ? null : existing.getStatus());
        if (request.isEmpty()) {
            return null;
        }
        InitiativeFollowRules.FollowRequest follow = request.get();
        try {
            Outcome<EsSubscription> outcome = followerService.addManagedFollower(follow.topicId(), follow.email(),
                    follow.firstName(), follow.lastName(), follow.organization(), follow.reason(), actor.getUserId(),
                    follow.sendNotification());
            if (!outcome.isSuccess()) {
                return "Saved the member, but could not follow the primary topic: " + outcome.getErrorMessage();
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Could not follow the primary topic for initiative member "
                    + member.getEsInitiativeMemberId(), ex);
            return "Saved the member, but could not follow the primary topic.";
        }
        return null;
    }

    private Organization resolveOrganization(User actor, Long organizationId, String newName, String newType,
            String newWebsite) {
        if (organizationId != null) {
            return organizationDao.findActiveById(organizationId)
                    .orElseThrow(() -> new IllegalArgumentException("Choose an organization."));
        }
        if (trimToNull(newName) == null) {
            throw new IllegalArgumentException("Choose an organization or add a new one.");
        }
        return createOrganization(actor, newName, newType, newWebsite);
    }

    private EsInitiativeOrganization resolveParticipation(User actor, EsInitiative initiative, Long organizationId,
            String newName, String newType, String newWebsite) {
        if (organizationId == null && trimToNull(newName) == null) {
            return null;
        }
        Organization organization = resolveOrganization(actor, organizationId, newName, newType, newWebsite);
        return initiativeOrganizationDao
                .findByInitiativeAndOrganization(initiative.getEsInitiativeId(), organization.getOrganizationId())
                .orElseGet(() -> initiativeOrganizationDao.save(newParticipation(actor, initiative, organization,
                        EsInitiativeOrganization.ParticipationStatus.PROSPECT)));
    }

    private void applyEmail(EsInitiativeMember member, String normalized, String rawEmail) {
        if (normalized == null) {
            member.setEmail(null);
            member.setEmailNormalized(null);
            member.setUserId(null);
            PersonNames.Names cleared = PersonNames.fillBlanks(member.getContactFirstName(), member.getContactLastName(),
                    null, null);
            member.setContactFirstName(cleared.firstName());
            member.setContactLastName(cleared.lastName());
            return;
        }
        member.setEmail(rawEmail == null ? normalized : rawEmail.trim());
        member.setEmailNormalized(normalized);
        userDao.findByEmailNormalized(normalized)
                .filter(user -> user.getStatus() != User.UserStatus.DELETED)
                .ifPresentOrElse(user -> {
                    member.setUserId(user.getUserId());
                    PersonNames.Names account = PersonNames.accountNames(user.getDisplayName(), user.getFirstName(),
                            user.getLastName());
                    PersonNames.Names filled = PersonNames.fillBlanks(member.getContactFirstName(),
                            member.getContactLastName(), account.firstName(), account.lastName());
                    member.setContactFirstName(filled.firstName());
                    member.setContactLastName(filled.lastName());
                }, () -> {
                    member.setUserId(null);
                    PersonNames.Names filled = PersonNames.fillBlanks(member.getContactFirstName(),
                            member.getContactLastName(), null, null);
                    member.setContactFirstName(filled.firstName());
                    member.setContactLastName(filled.lastName());
                });
    }

    private String normalizeOptionalEmail(String email) {
        String normalized = EsNormalizer.normalizeEmail(email);
        if (trimToNull(email) == null) {
            return null;
        }
        if (normalized == null || !normalized.contains("@")) {
            throw new IllegalArgumentException("Email address is not valid.");
        }
        return normalized;
    }

    private EsTopic requireTopic(Long topicId) {
        if (topicId == null) {
            throw new IllegalArgumentException("Choose a primary topic.");
        }
        return topicDao.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Choose a primary topic."));
    }

    private void requireDateOrder(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after the start date.");
        }
    }

    private EsInitiativeManager requireManager(EsInitiative initiative, Long managerId) {
        return managerDao.findById(managerId)
                .filter(manager -> initiative.getEsInitiativeId().equals(manager.getEsInitiativeId()))
                .orElseThrow(() -> new IllegalArgumentException("That manager was not found."));
    }

    private EsInitiativeOrganization requireParticipation(EsInitiative initiative, Long participationId) {
        return initiativeOrganizationDao.findById(participationId)
                .filter(item -> initiative.getEsInitiativeId().equals(item.getEsInitiativeId()))
                .orElseThrow(() -> new IllegalArgumentException("That organization was not found on this initiative."));
    }

    private EsInitiativeMember requireMember(EsInitiative initiative, Long memberId) {
        return memberDao.findById(memberId)
                .filter(item -> initiative.getEsInitiativeId().equals(item.getEsInitiativeId()))
                .orElseThrow(() -> new IllegalArgumentException("That person was not found on this initiative."));
    }

    private void requireSubject(EsInitiative initiative, EsInitiativeTimelineEntry.SubjectType subjectType,
            Long subjectId) {
        if (subjectType == null || subjectId == null) {
            throw new IllegalArgumentException("Choose where to comment.");
        }
        boolean found = switch (subjectType) {
            case INITIATIVE -> initiative.getEsInitiativeId().equals(subjectId);
            case ORGANIZATION -> initiativeOrganizationDao.findById(subjectId)
                    .filter(item -> initiative.getEsInitiativeId().equals(item.getEsInitiativeId()))
                    .isPresent();
            case MEMBER -> memberDao.findById(subjectId)
                    .filter(item -> initiative.getEsInitiativeId().equals(item.getEsInitiativeId()))
                    .isPresent();
            case SMALL_GROUP -> smallGroupDao.findById(subjectId)
                    .filter(item -> initiative.getEsInitiativeId().equals(item.getEsInitiativeId()))
                    .isPresent();
            case CHECKLIST -> checklistDao.findById(subjectId)
                    .filter(item -> initiative.getEsInitiativeId().equals(item.getEsInitiativeId()))
                    .isPresent();
            case CHECKLIST_ITEM -> checklistItemDao.findById(subjectId)
                    .flatMap(item -> checklistDao.findById(item.getEsChecklistId()))
                    .filter(item -> initiative.getEsInitiativeId().equals(item.getEsInitiativeId()))
                    .isPresent();
            default -> false;
        };
        if (!found) {
            throw new IllegalArgumentException("That item was not found on this initiative.");
        }
    }

    private void addPlanned(Long initiativeId, EsInitiativeTimelineEntry.SubjectType type, Long subjectId, User actor,
            InitiativeRosterRules.PlannedEntry planned, java.util.function.Function<String, String> label) {
        EsInitiativeTimelineEntry entry = new EsInitiativeTimelineEntry();
        entry.setEsInitiativeId(initiativeId);
        entry.setSubjectType(type);
        entry.setSubjectId(subjectId);
        entry.setCreatedByUserId(actor.getUserId());
        if (planned.kind() == InitiativeRosterRules.PlannedKind.STATUS_CHANGE) {
            entry.setEntryType(EsInitiativeTimelineEntry.EntryType.STATUS_CHANGE);
            entry.setFromValue(planned.fromValue() == null ? null : label.apply(planned.fromValue()));
            entry.setToValue(planned.toValue() == null ? null : label.apply(planned.toValue()));
            entry.setNoteText(planned.note());
        } else {
            entry.setEntryType(EsInitiativeTimelineEntry.EntryType.COMMENT);
            entry.setNoteText(planned.note());
        }
        timelineDao.save(entry);
    }

    private void addComment(Long initiativeId, EsInitiativeTimelineEntry.SubjectType type, Long subjectId, User actor,
            String note) {
        EsInitiativeTimelineEntry entry = new EsInitiativeTimelineEntry();
        entry.setEsInitiativeId(initiativeId);
        entry.setSubjectType(type);
        entry.setSubjectId(subjectId);
        entry.setEntryType(EsInitiativeTimelineEntry.EntryType.COMMENT);
        entry.setNoteText(note);
        entry.setCreatedByUserId(actor.getUserId());
        timelineDao.save(entry);
    }

    private void recordField(Long initiativeId, EsInitiativeTimelineEntry.SubjectType type, Long subjectId, User actor,
            String field, String from, String to) {
        String left = trimToNull(from);
        String right = trimToNull(to);
        if (java.util.Objects.equals(left, right)) {
            return;
        }
        EsInitiativeTimelineEntry entry = new EsInitiativeTimelineEntry();
        entry.setEsInitiativeId(initiativeId);
        entry.setSubjectType(type);
        entry.setSubjectId(subjectId);
        entry.setEntryType(EsInitiativeTimelineEntry.EntryType.FIELD_CHANGE);
        entry.setNoteText(field);
        entry.setFromValue(left);
        entry.setToValue(right);
        entry.setCreatedByUserId(actor.getUserId());
        timelineDao.save(entry);
    }

    private String participationName(Long participationId) {
        if (participationId == null) {
            return null;
        }
        return initiativeOrganizationDao.findById(participationId)
                .flatMap(item -> organizationDao.findById(item.getOrganizationId()))
                .map(Organization::getOrganizationName)
                .orElse(null);
    }

    private String organizationName(EsInitiativeOrganization participation) {
        if (participation == null) {
            return null;
        }
        return organizationDao.findById(participation.getOrganizationId()).map(Organization::getOrganizationName)
                .orElse(null);
    }

    private PreparedPerson preparePerson(String email, String firstName, String lastName, Long userId) {
        String rawEmail = trimToNull(email);
        String postedFirst = firstName;
        String postedLast = lastName;
        if (rawEmail == null && PersonNames.looksLikeEmail(postedFirst)) {
            rawEmail = postedFirst.trim();
            postedFirst = null;
        }
        if (rawEmail == null && PersonNames.looksLikeEmail(postedLast)) {
            rawEmail = postedLast.trim();
            postedLast = null;
        }
        User account = userId == null ? null
                : userDao.findById(userId).filter(user -> user.getStatus() != User.UserStatus.DELETED).orElse(null);
        String normalized = normalizeOptionalEmail(rawEmail);
        if (account == null && normalized != null) {
            account = userDao.findByEmailNormalized(normalized)
                    .filter(user -> user.getStatus() != User.UserStatus.DELETED).orElse(null);
        }
        if (account != null && normalized == null) {
            rawEmail = account.getEmail();
            normalized = account.getEmailNormalized();
        }
        PersonNames.Names names = new PersonNames.Names(PersonNames.nameOrNull(postedFirst),
                PersonNames.nameOrNull(postedLast));
        if (account != null) {
            PersonNames.Names accountNames = PersonNames.accountNames(account.getDisplayName(), account.getFirstName(),
                    account.getLastName());
            if (accountNames.firstName() != null || accountNames.lastName() != null) {
                names = accountNames;
            }
        }
        if (names.firstName() == null && names.lastName() == null) {
            names = namesFromFollows(normalized);
        }
        return new PreparedPerson(names, normalized, rawEmail, account == null ? null : account.getUserId());
    }

    private PersonNames.Names namesFromFollows(String emailNormalized) {
        if (emailNormalized == null) {
            return new PersonNames.Names(null, null);
        }
        for (EsSubscription subscription : subscriptionDao.findByEmailNormalized(emailNormalized)) {
            String first = PersonNames.nameOrNull(subscription.getContactFirstName());
            String last = PersonNames.nameOrNull(subscription.getContactLastName());
            if (first != null || last != null) {
                return new PersonNames.Names(first, last);
            }
        }
        return new PersonNames.Names(null, null);
    }

    private EsInitiativeMember findExistingMember(EsInitiative initiative, Long memberId, Long userId,
            String emailNormalized) {
        if (memberId != null) {
            return requireMember(initiative, memberId);
        }
        if (emailNormalized != null) {
            Optional<EsInitiativeMember> byEmail = memberDao.findByInitiativeAndEmail(initiative.getEsInitiativeId(),
                    emailNormalized);
            if (byEmail.isPresent()) {
                return byEmail.get();
            }
        }
        if (userId != null) {
            return memberDao.findByInitiativeAndUser(initiative.getEsInitiativeId(), userId).orElse(null);
        }
        return null;
    }

    public record PersonPlacement(EsInitiativeMember member, String warning, boolean created) {
    }

    private record PreparedPerson(PersonNames.Names names, String normalized, String rawEmail, Long userId) {
    }

    private String displayUser(User user) {
        if (user == null) {
            return "a manager";
        }
        String fullName = user.getFullName();
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        return user.getEmail();
    }

    private String requireUrl(String url) {
        String trimmed = requireText(url, "Link URL", 1000);
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("Link URL must start with http:// or https://.");
        }
        return trimmed;
    }

    private String requireText(String value, String label, int max) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + " must be " + max + " characters or less.");
        }
        return trimmed;
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private String dateText(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
