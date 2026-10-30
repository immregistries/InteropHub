package org.airahub.interophub.service;

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
import org.airahub.interophub.dao.EsCampaignDao;
import org.airahub.interophub.dao.EsInitiativeOrganizationDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.dao.EsSmallGroupDao;
import org.airahub.interophub.dao.EsSmallGroupParticipantDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.dao.OrganizationDao;
import org.airahub.interophub.dao.SmallGroupQueries;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsInitiative;
import org.airahub.interophub.model.EsInitiativeMember;
import org.airahub.interophub.model.EsInitiativeOrganization;
import org.airahub.interophub.model.EsInitiativeTimelineEntry;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsSmallGroup;
import org.airahub.interophub.model.EsSmallGroupParticipant;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.Organization;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.TopicFollowerManagementService.Outcome;

/**
 * Small Groups inside an initiative. Callers enforce {@link InitiativeAccess}
 * before mutating. Topic, series, meeting, and notes records are only read.
 */
public class SmallGroupService {

    private static final Logger LOGGER = Logger.getLogger(SmallGroupService.class.getName());

    private final EsSmallGroupDao groupDao;
    private final EsSmallGroupParticipantDao participantDao;
    private final EsTopicDao topicDao;
    private final EsTopicMeetingDao seriesDao;
    private final EsTopicMeetingMemberDao seriesMemberDao;
    private final EsMeetingDao meetingDao;
    private final EsMeetingAgendaItemDao agendaDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsCampaignDao campaignDao;
    private final EsInitiativeOrganizationDao participationDao;
    private final OrganizationDao organizationDao;
    private final UserDao userDao;
    private final InitiativeService initiatives;
    private final ChecklistService checklists;
    private final TopicFollowerManagementService followerService;

    public SmallGroupService() {
        this.groupDao = new EsSmallGroupDao();
        this.participantDao = new EsSmallGroupParticipantDao();
        this.topicDao = new EsTopicDao();
        this.seriesDao = new EsTopicMeetingDao();
        this.seriesMemberDao = new EsTopicMeetingMemberDao();
        this.meetingDao = new EsMeetingDao();
        this.agendaDao = new EsMeetingAgendaItemDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.campaignDao = new EsCampaignDao();
        this.participationDao = new EsInitiativeOrganizationDao();
        this.organizationDao = new OrganizationDao();
        this.userDao = new UserDao();
        this.initiatives = new InitiativeService();
        this.checklists = new ChecklistService();
        this.followerService = new TopicFollowerManagementService();
    }

    public List<EsSmallGroup> list(Long initiativeId) {
        return groupDao.findByInitiativeId(initiativeId);
    }

    public Map<Long, Integer> activeCounts(List<EsSmallGroup> groups) {
        Map<Long, Integer> counts = new HashMap<>();
        if (groups == null || groups.isEmpty()) {
            return counts;
        }
        List<Long> ids = new ArrayList<>();
        for (EsSmallGroup group : groups) {
            ids.add(group.getEsSmallGroupId());
            counts.put(group.getEsSmallGroupId(), 0);
        }
        for (EsSmallGroupParticipant participant : participantDao.findBySmallGroupIds(ids)) {
            if (participant.getParticipantStatus() == EsSmallGroupParticipant.Status.ACTIVE) {
                counts.merge(participant.getEsSmallGroupId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    public Optional<EsSmallGroup> find(EsInitiative initiative, Long smallGroupId) {
        if (initiative == null) {
            return Optional.empty();
        }
        return groupDao.findInInitiative(initiative.getEsInitiativeId(), smallGroupId);
    }

    public List<EsTopicMeeting> seriesForTopic(Long topicId) {
        return SmallGroupQueries.seriesForTopic(topicId);
    }

    public EsSmallGroup create(User actor, EsInitiative initiative, String name, Long topicId, Long seriesId,
            String description, EsSmallGroup.Status status, LocalDate cycleStart, LocalDate workingDay,
            LocalDate closeout, String breakNote, List<String> productAreas, String charterUrl, Long copyFromId) {
        EsTopic topic = requireTopic(topicId);
        EsTopicMeeting series = resolveSeries(topic.getEsTopicId(), seriesId);
        LocalDate[] dates = dates(cycleStart, workingDay, closeout);
        if (checklists.currentTemplate(org.airahub.interophub.model.EsChecklistTemplate.Kind.SMALL_GROUP).isEmpty()) {
            throw new IllegalArgumentException("There is no current Small Group template.");
        }
        EsSmallGroup group = new EsSmallGroup();
        group.setEsInitiativeId(initiative.getEsInitiativeId());
        group.setEsTopicId(topic.getEsTopicId());
        group.setEsTopicMeetingId(series == null ? null : series.getEsTopicMeetingId());
        group.setSmallGroupName(requireText(name, "Name", 200));
        group.setDescription(clean(description, 8000));
        group.setStatus(status == null ? EsSmallGroup.Status.PLANNING : status);
        group.setCycleStartDate(dates[0]);
        group.setWorkingDayDate(dates[1]);
        group.setCloseoutDate(dates[2]);
        group.setBreakNote(clean(breakNote, 4000));
        group.setPrimaryProductAreas(SmallGroupParticipantRules.productAreas(productAreas));
        group.setCharterUrl(url(charterUrl));
        group.setCreatedByUserId(actor.getUserId());
        group = groupDao.save(group);
        checklists.startForSmallGroup(initiative, group.getEsSmallGroupId());
        record(initiative.getEsInitiativeId(), group.getEsSmallGroupId(), actor,
                EsInitiativeTimelineEntry.EntryType.STATUS_CHANGE, null, group.getStatus().getLabel(), null);
        if (copyFromId != null) {
            copyParticipants(actor, initiative, group, copyFromId);
        }
        return group;
    }

    public void updateDetails(User actor, EsInitiative initiative, EsSmallGroup group, String name, Long topicId,
            Long seriesId, String description, LocalDate cycleStart, LocalDate workingDay, LocalDate closeout,
            String breakNote, List<String> productAreas, String charterUrl) {
        EsSmallGroup current = requireGroup(initiative, group);
        EsTopic topic = requireTopic(topicId);
        EsTopicMeeting series = resolveSeries(topic.getEsTopicId(), seriesId);
        LocalDate[] dates = dates(cycleStart, workingDay, closeout);
        String nextName = requireText(name, "Name", 200);
        String nextDescription = clean(description, 8000);
        String nextBreak = clean(breakNote, 4000);
        String nextAreas = SmallGroupParticipantRules.productAreas(productAreas);
        String nextCharter = url(charterUrl);
        recordField(initiative, current, actor, "Name", current.getSmallGroupName(), nextName);
        recordField(initiative, current, actor, "Topic", topicLabel(current.getEsTopicId()), topic.getTopicName());
        recordField(initiative, current, actor, "Series", seriesLabel(current.getEsTopicMeetingId()),
                series == null ? null : series.getMeetingName());
        recordField(initiative, current, actor, "Cycle start", text(current.getCycleStartDate()), text(dates[0]));
        recordField(initiative, current, actor, "Working day", text(current.getWorkingDayDate()), text(dates[1]));
        recordField(initiative, current, actor, "Closeout", text(current.getCloseoutDate()), text(dates[2]));
        recordField(initiative, current, actor, "Break", current.getBreakNote(), nextBreak);
        recordField(initiative, current, actor, "Product areas", current.getPrimaryProductAreas(), nextAreas);
        recordField(initiative, current, actor, "Charter", current.getCharterUrl(), nextCharter);
        recordField(initiative, current, actor, "Description", current.getDescription(), nextDescription);
        current.setSmallGroupName(nextName);
        current.setEsTopicId(topic.getEsTopicId());
        current.setEsTopicMeetingId(series == null ? null : series.getEsTopicMeetingId());
        current.setDescription(nextDescription);
        current.setCycleStartDate(dates[0]);
        current.setWorkingDayDate(dates[1]);
        current.setCloseoutDate(dates[2]);
        current.setBreakNote(nextBreak);
        current.setPrimaryProductAreas(nextAreas);
        current.setCharterUrl(nextCharter);
        groupDao.save(current);
    }

    public void changeStatus(User actor, EsInitiative initiative, EsSmallGroup group, EsSmallGroup.Status status,
            String note) {
        EsSmallGroup current = requireGroup(initiative, group);
        if (status == null) {
            throw new IllegalArgumentException("Choose a status.");
        }
        InitiativeRosterRules.PlannedEntry planned = InitiativeRosterRules.planStatusUpdate(
                current.getStatus().name(), status.name(), note);
        if (planned == null) {
            return;
        }
        if (planned.kind() == InitiativeRosterRules.PlannedKind.STATUS_CHANGE) {
            current.setStatus(status);
            groupDao.save(current);
            record(initiative.getEsInitiativeId(), current.getEsSmallGroupId(), actor,
                    EsInitiativeTimelineEntry.EntryType.STATUS_CHANGE,
                    EsSmallGroup.Status.labelOf(planned.fromValue()),
                    EsSmallGroup.Status.labelOf(planned.toValue()), planned.note());
        } else {
            record(initiative.getEsInitiativeId(), current.getEsSmallGroupId(), actor,
                    EsInitiativeTimelineEntry.EntryType.COMMENT, null, null, planned.note());
        }
    }

    /** @return a warning when the person was saved but a topic follow did not */
    public String addParticipant(User actor, EsInitiative initiative, EsSmallGroup group, Long memberId, String role) {
        EsSmallGroup current = requireGroup(initiative, group);
        EsInitiativeMember member = requireMember(initiative, memberId);
        EsSmallGroupParticipant existing = participantDao
                .findByGroupAndMember(current.getEsSmallGroupId(), member.getEsInitiativeMemberId()).orElse(null);
        if (existing != null && existing.getParticipantStatus() == EsSmallGroupParticipant.Status.ACTIVE) {
            throw new IllegalArgumentException(member.displayName() + " is already a participant.");
        }
        String warning = markJoinedIfNeeded(actor, initiative, current, member);
        if (existing == null) {
            EsSmallGroupParticipant participant = new EsSmallGroupParticipant();
            participant.setEsSmallGroupId(current.getEsSmallGroupId());
            participant.setEsInitiativeMemberId(member.getEsInitiativeMemberId());
            participant.setParticipantRole(clean(role, 200));
            participant.setParticipantStatus(EsSmallGroupParticipant.Status.ACTIVE);
            participant.setCreatedByUserId(actor.getUserId());
            participantDao.save(participant);
        } else {
            existing.setParticipantStatus(EsSmallGroupParticipant.Status.ACTIVE);
            existing.setParticipantRole(clean(role, 200));
            participantDao.save(existing);
        }
        comment(actor, initiative, current, "Added " + member.displayName()
                + (clean(role, 200) == null ? "." : " as " + clean(role, 200) + "."));
        return joinWarnings(warning, followTopic(actor, current, member));
    }

    public String addFromSearch(User actor, EsInitiative initiative, EsSmallGroup group, Long memberId, Long userId,
            String email, String firstName, String lastName, Long organizationId, String newOrganizationName,
            String newOrganizationType, String newOrganizationWebsite, String role) {
        EsSmallGroup current = requireGroup(initiative, group);
        InitiativeService.PersonPlacement placed = initiatives.placePerson(actor, initiative, "participant", memberId,
                userId, email, firstName, lastName, organizationId, newOrganizationName, newOrganizationType,
                newOrganizationWebsite, null, EsInitiativeMember.MemberStatus.JOINED,
                EsInitiativeMember.MemberSource.SMALL_GROUP,
                "Added to the " + current.getSmallGroupName() + " small group.");
        return joinWarnings(placed.warning(),
                addParticipant(actor, initiative, current, placed.member().getEsInitiativeMemberId(), role));
    }

    public String addNewPerson(User actor, EsInitiative initiative, EsSmallGroup group, String firstName, String lastName,
            String email, Long organizationId, String newOrganizationName, String newOrganizationType,
            String newOrganizationWebsite, String role) {
        return addFromSearch(actor, initiative, group, null, null, email, firstName, lastName, organizationId,
                newOrganizationName, newOrganizationType, newOrganizationWebsite, role);
    }

    public String addRegistrant(User actor, EsInitiative initiative, EsSmallGroup group, String email, Long userId,
            String label) {
        return addFromSearch(actor, initiative, group, null, userId, email, null, null, null, null, null, null, null);
    }

    public void removeParticipant(User actor, EsInitiative initiative, EsSmallGroup group, Long participantId,
            String note) {
        EsSmallGroup current = requireGroup(initiative, group);
        EsSmallGroupParticipant participant = participantDao.findById(participantId)
                .filter(row -> current.getEsSmallGroupId().equals(row.getEsSmallGroupId()))
                .orElseThrow(() -> new IllegalArgumentException("That participant was not found."));
        if (participant.getParticipantStatus() == EsSmallGroupParticipant.Status.REMOVED) {
            return;
        }
        participant.setParticipantStatus(EsSmallGroupParticipant.Status.REMOVED);
        participantDao.save(participant);
        EsInitiativeMember member = initiatives.members(initiative.getEsInitiativeId()).stream()
                .filter(row -> participant.getEsInitiativeMemberId().equals(row.getEsInitiativeMemberId()))
                .findFirst().orElse(null);
        String name = member == null ? "A participant" : member.displayName();
        String text = "Removed " + name + ".";
        String cleanNote = clean(note, 4000);
        if (cleanNote != null) {
            text = text + " " + cleanNote;
        }
        comment(actor, initiative, current, text);
    }

    public void updateRole(User actor, EsInitiative initiative, EsSmallGroup group, Long participantId, String role) {
        EsSmallGroup current = requireGroup(initiative, group);
        EsSmallGroupParticipant participant = participantDao.findById(participantId)
                .filter(row -> current.getEsSmallGroupId().equals(row.getEsSmallGroupId()))
                .orElseThrow(() -> new IllegalArgumentException("That participant was not found."));
        String next = clean(role, 200);
        if (same(participant.getParticipantRole(), next)) {
            return;
        }
        participant.setParticipantRole(next);
        participantDao.save(participant);
    }

    public String copyParticipants(User actor, EsInitiative initiative, EsSmallGroup group, Long sourceGroupId) {
        EsSmallGroup current = requireGroup(initiative, group);
        EsSmallGroup source = groupDao.findInInitiative(initiative.getEsInitiativeId(), sourceGroupId)
                .orElseThrow(() -> new IllegalArgumentException("Choose a Small Group in this initiative."));
        if (source.getEsSmallGroupId().equals(current.getEsSmallGroupId())) {
            throw new IllegalArgumentException("Choose an earlier Small Group.");
        }
        Set<Long> active = new HashSet<>();
        for (EsSmallGroupParticipant participant : participantDao.findBySmallGroupId(current.getEsSmallGroupId())) {
            if (participant.getParticipantStatus() == EsSmallGroupParticipant.Status.ACTIVE) {
                active.add(participant.getEsInitiativeMemberId());
            }
        }
        List<Long> sourceActive = new ArrayList<>();
        Map<Long, String> roles = new HashMap<>();
        for (EsSmallGroupParticipant participant : participantDao.findBySmallGroupId(source.getEsSmallGroupId())) {
            if (participant.getParticipantStatus() == EsSmallGroupParticipant.Status.ACTIVE) {
                sourceActive.add(participant.getEsInitiativeMemberId());
                roles.put(participant.getEsInitiativeMemberId(), participant.getParticipantRole());
            }
        }
        List<Long> copy = SmallGroupParticipantRules.memberIdsToCopy(active, sourceActive);
        String warning = null;
        for (Long memberId : copy) {
            warning = joinWarnings(warning,
                    addParticipant(actor, initiative, current, memberId, roles.get(memberId)));
        }
        comment(actor, initiative, current, copy.isEmpty()
                ? "No new participants to copy from " + source.getSmallGroupName() + "."
                : "Copied " + copy.size() + " participants from " + source.getSmallGroupName() + ".");
        return warning;
    }

    public List<EsSmallGroupParticipant> participants(Long smallGroupId) {
        return participantDao.findBySmallGroupId(smallGroupId);
    }

    public ChecklistDueDates.Anchors anchors(EsInitiative initiative, EsSmallGroup group) {
        return ChecklistDueDates.Anchors.of(
                initiative == null ? null : initiative.getStartDate(),
                initiative == null ? null : initiative.getEndDate(),
                group == null ? null : group.getCycleStartDate(),
                group == null ? null : group.getWorkingDayDate(),
                group == null ? null : group.getCloseoutDate());
    }

    public ChecklistHints.Facts facts(EsInitiative initiative, EsSmallGroup group) {
        ChecklistHints.Facts organizations = organizationFacts(initiative, group);
        return new ChecklistHints.Facts(organizations.representation(), organizations.organizationsNeedingContact(),
                organizations.topicHasChampion(), organizations.joinedMembersMissingFollow(),
                signals(group, LocalDate.now()));
    }

    public ChecklistHints.GroupSignals signals(EsSmallGroup group, LocalDate today) {
        if (group == null) {
            return SmallGroupSignals.summarize(false, false, 0, 0, null, null, List.of(), false, false, today);
        }
        List<EsTopicMeeting> series = SmallGroupQueries.seriesForTopic(group.getEsTopicId());
        boolean topicHasActive = false;
        EsTopicMeeting selected = null;
        for (EsTopicMeeting candidate : series) {
            if (activeSeries(candidate)) {
                topicHasActive = true;
            }
            if (candidate.getEsTopicMeetingId().equals(group.getEsTopicMeetingId())) {
                selected = candidate;
            }
        }
        if (selected == null && group.getEsTopicMeetingId() != null) {
            selected = seriesDao.findById(group.getEsTopicMeetingId()).orElse(null);
        }
        boolean selectedActive = activeSeries(selected);
        List<EsSmallGroupParticipant> rows = participantDao.findBySmallGroupId(group.getEsSmallGroupId());
        List<EsInitiativeMember> members = initiatives.members(group.getEsInitiativeId());
        Map<Long, EsInitiativeMember> byId = new HashMap<>();
        for (EsInitiativeMember member : members) {
            byId.put(member.getEsInitiativeMemberId(), member);
        }
        List<SmallGroupSeriesComparison.Person> people = new ArrayList<>();
        for (EsSmallGroupParticipant row : rows) {
            if (row.getParticipantStatus() != EsSmallGroupParticipant.Status.ACTIVE) {
                continue;
            }
            EsInitiativeMember member = byId.get(row.getEsInitiativeMemberId());
            if (member != null) {
                people.add(new SmallGroupSeriesComparison.Person(member.getEsInitiativeMemberId(),
                        member.getEmailNormalized(), member.getUserId()));
            }
        }
        List<SmallGroupSeriesComparison.Registrant> registrants = List.of();
        List<SmallGroupSignals.MeetingView> meetings = List.of();
        if (selected != null) {
            registrants = registrants(selected.getEsTopicMeetingId(), group.getEsTopicId());
            meetings = meetingViews(selected.getEsTopicMeetingId());
        }
        int unregistered = SmallGroupSeriesComparison.compare(people, registrants).participantsNotRegistered().size();
        int missingFollow = missingTopicFollows(group.getEsTopicId(), people);
        boolean packet = SmallGroupQueries.starterPacketDuring(group.getEsTopicId(), group.getCycleStartDate(),
                group.getCloseoutDate());
        return SmallGroupSignals.summarize(topicHasActive, selectedActive, unregistered, missingFollow,
                group.getCycleStartDate(), group.getCloseoutDate(), meetings,
                SmallGroupQueries.topicHasOrientationBundle(group.getEsTopicId()), packet, today);
    }

    public SmallGroupSeriesComparison.Result comparison(EsSmallGroup group) {
        if (group == null || group.getEsTopicMeetingId() == null) {
            return new SmallGroupSeriesComparison.Result(List.of(), List.of());
        }
        List<SmallGroupSeriesComparison.Person> people = new ArrayList<>();
        Map<Long, EsInitiativeMember> byId = new HashMap<>();
        for (EsInitiativeMember member : initiatives.members(group.getEsInitiativeId())) {
            byId.put(member.getEsInitiativeMemberId(), member);
        }
        for (EsSmallGroupParticipant row : participantDao.findBySmallGroupId(group.getEsSmallGroupId())) {
            if (row.getParticipantStatus() != EsSmallGroupParticipant.Status.ACTIVE) {
                continue;
            }
            EsInitiativeMember member = byId.get(row.getEsInitiativeMemberId());
            if (member != null) {
                people.add(new SmallGroupSeriesComparison.Person(member.getEsInitiativeMemberId(),
                        member.getEmailNormalized(), member.getUserId()));
            }
        }
        return SmallGroupSeriesComparison.compare(people, registrants(group.getEsTopicMeetingId(), group.getEsTopicId()));
    }

    public PeopleToBringIn.Page bringIn(EsInitiative initiative, EsSmallGroup group, String filter, int page) {
        List<EsInitiativeMember> members = initiatives.members(initiative.getEsInitiativeId());
        Map<Long, EsInitiativeMember> byId = new HashMap<>();
        for (EsInitiativeMember member : members) {
            byId.put(member.getEsInitiativeMemberId(), member);
        }
        Map<Long, String> organizations = new HashMap<>();
        for (EsInitiativeOrganization participation : initiatives.organizations(initiative.getEsInitiativeId())) {
            organizationDao.findById(participation.getOrganizationId()).ifPresent(organization -> organizations
                    .put(participation.getEsInitiativeOrganizationId(), organization.getOrganizationName()));
        }
        List<PeopleToBringIn.Person> participants = new ArrayList<>();
        for (EsSmallGroupParticipant row : participantDao.findBySmallGroupId(group.getEsSmallGroupId())) {
            if (row.getParticipantStatus() != EsSmallGroupParticipant.Status.ACTIVE) {
                continue;
            }
            EsInitiativeMember member = byId.get(row.getEsInitiativeMemberId());
            if (member != null) {
                participants.add(bringInPerson(member, organizations, null, false));
            }
        }
        List<PeopleToBringIn.Person> joined = new ArrayList<>();
        for (EsInitiativeMember member : members) {
            if (member.getMemberStatus() == EsInitiativeMember.MemberStatus.JOINED) {
                joined.add(bringInPerson(member, organizations, member.getMemberStatus().getLabel(), false));
            }
        }
        List<PeopleToBringIn.Person> followers = new ArrayList<>();
        for (EsSubscription follow : subscriptionDao.findActiveByTopicId(group.getEsTopicId())) {
            User user = follow.getUserId() == null ? null : userDao.findById(follow.getUserId()).orElse(null);
            followers.add(new PeopleToBringIn.Person(follow.getEmailNormalized(), follow.getUserId(), null,
                    user == null ? null : user.getDisplayName(), user == null ? null : user.getFirstName(),
                    user == null ? null : user.getLastName(), follow.getContactFirstName(), follow.getContactLastName(),
                    follow.getEmail(), follow.getContactOrganization(), null, null, true));
        }
        List<PeopleToBringIn.Person> registrants = new ArrayList<>();
        if (group.getEsTopicMeetingId() != null) {
            for (SmallGroupSeriesComparison.Registrant registrant : registrants(group.getEsTopicMeetingId(),
                    group.getEsTopicId())) {
                if (!"APPROVED".equals(registrant.status()) && !"REQUESTED".equals(registrant.status())) {
                    continue;
                }
                registrants.add(new PeopleToBringIn.Person(registrant.emailNormalized(), registrant.userId(), null,
                        registrant.label(), null, null, null, null, registrant.email(), null, null, registrant.status(),
                        false));
            }
        }
        for (EsInitiativeMember member : members) {
            if (member.getMemberStatus() == EsInitiativeMember.MemberStatus.JOINED) {
                continue;
            }
            if (matchesBringIn(member, followers) || matchesBringIn(member, registrants)) {
                joined.add(bringInPerson(member, organizations, member.getMemberStatus().getLabel(), false));
            }
        }
        return PeopleToBringIn.page(PeopleToBringIn.assemble(joined, followers, registrants, participants), filter, page);
    }

    public Set<Long> registeredParticipantIds(EsSmallGroup group, List<EsSmallGroupParticipant> active) {
        Set<Long> registered = new HashSet<>();
        if (group == null || group.getEsTopicMeetingId() == null) {
            return registered;
        }
        Set<Long> missing = new HashSet<>();
        for (SmallGroupSeriesComparison.Person person : comparison(group).participantsNotRegistered()) {
            if (person.memberId() != null) {
                missing.add(person.memberId());
            }
        }
        for (EsSmallGroupParticipant row : active) {
            if (!missing.contains(row.getEsInitiativeMemberId())) {
                registered.add(row.getEsInitiativeMemberId());
            }
        }
        return registered;
    }

    public Set<Long> followingParticipantIds(EsSmallGroup group, List<EsInitiativeMember> participants) {
        Set<Long> following = new HashSet<>();
        List<EsSubscription> follows = subscriptionDao.findActiveByTopicId(group.getEsTopicId());
        for (EsInitiativeMember member : participants) {
            if (member == null) {
                continue;
            }
            for (EsSubscription follow : follows) {
                if (SmallGroupSeriesComparison.samePerson(member.getEmailNormalized(), member.getUserId(),
                        follow.getEmailNormalized(), follow.getUserId())) {
                    following.add(member.getEsInitiativeMemberId());
                    break;
                }
            }
        }
        return following;
    }

    private boolean matchesBringIn(EsInitiativeMember member, List<PeopleToBringIn.Person> people) {
        for (PeopleToBringIn.Person person : people) {
            if (PeopleToBringIn.same(member.getEmailNormalized(), member.getUserId(), member.getEsInitiativeMemberId(),
                    person.emailNormalized(), person.userId(), person.memberId())) {
                return true;
            }
        }
        return false;
    }

    private PeopleToBringIn.Person bringInPerson(EsInitiativeMember member, Map<Long, String> organizations,
            String initiativeStatus, boolean follows) {
        User user = member.getUserId() == null ? null : userDao.findById(member.getUserId()).orElse(null);
        return new PeopleToBringIn.Person(member.getEmailNormalized(), member.getUserId(),
                member.getEsInitiativeMemberId(), user == null ? null : user.getDisplayName(),
                user == null ? null : user.getFirstName(), user == null ? null : user.getLastName(),
                member.getContactFirstName(), member.getContactLastName(), member.getEmail(),
                organizations.get(member.getEsInitiativeOrganizationId()), initiativeStatus, null, follows);
    }

    public String registrationPath(EsSmallGroup group) {
        if (group == null) {
            return null;
        }
        EsTopic topic = topicDao.findById(group.getEsTopicId()).orElse(null);
        if (topic == null) {
            return null;
        }
        return campaignDao.findMostRecentActive()
                .map(campaign -> SmallGroupSeriesComparison.registrationPath(campaign.getCampaignCode(),
                        topic.getTopicCode()))
                .orElse(null);
    }

    public ChecklistHints.Facts organizationFacts(EsInitiative initiative, EsSmallGroup group) {
        List<SmallGroupParticipantRules.OrgRow> rows = new ArrayList<>();
        for (EsInitiativeOrganization participation : initiatives.organizations(initiative.getEsInitiativeId())) {
            Organization organization = organizationDao.findById(participation.getOrganizationId()).orElse(null);
            rows.add(new SmallGroupParticipantRules.OrgRow(participation.getEsInitiativeOrganizationId(),
                    participation.getParticipationStatus().name(),
                    organization == null ? null : organization.getOrganizationType()));
        }
        Set<Long> represented = new HashSet<>();
        if (group != null) {
            Map<Long, EsInitiativeMember> byId = new HashMap<>();
            for (EsInitiativeMember member : initiatives.members(initiative.getEsInitiativeId())) {
                byId.put(member.getEsInitiativeMemberId(), member);
            }
            for (EsSmallGroupParticipant participant : participantDao.findBySmallGroupId(group.getEsSmallGroupId())) {
                if (participant.getParticipantStatus() != EsSmallGroupParticipant.Status.ACTIVE) {
                    continue;
                }
                EsInitiativeMember member = byId.get(participant.getEsInitiativeMemberId());
                if (member != null && member.getEsInitiativeOrganizationId() != null) {
                    represented.add(member.getEsInitiativeOrganizationId());
                }
            }
        }
        return SmallGroupParticipantRules.organizationFacts(rows, represented,
                initiatives.primaryTopicHasChampion(initiative), missingPrimaryFollows(initiative));
    }

    private int missingPrimaryFollows(EsInitiative initiative) {
        List<EsSubscription> follows = initiatives.activePrimaryTopicFollows(initiative);
        int missing = 0;
        for (EsInitiativeMember member : initiatives.members(initiative.getEsInitiativeId())) {
            if (!initiatives.followsPrimaryTopic(initiative, member, follows)) {
                missing++;
            }
        }
        return missing;
    }

    private int missingTopicFollows(Long topicId, List<SmallGroupSeriesComparison.Person> people) {
        List<EsSubscription> follows = subscriptionDao.findActiveByTopicId(topicId);
        int missing = 0;
        for (SmallGroupSeriesComparison.Person person : people) {
            boolean canFollow = (person.emailNormalized() != null && !person.emailNormalized().isBlank())
                    || person.userId() != null;
            if (!canFollow) {
                continue;
            }
            boolean following = false;
            for (EsSubscription follow : follows) {
                if (SmallGroupSeriesComparison.samePerson(person.emailNormalized(), person.userId(),
                        follow.getEmailNormalized(), follow.getUserId())) {
                    following = true;
                    break;
                }
            }
            if (!following) {
                missing++;
            }
        }
        return missing;
    }

    private List<SmallGroupSeriesComparison.Registrant> registrants(Long seriesId, Long topicId) {
        Map<String, EsSubscription> followsByEmail = new HashMap<>();
        Map<Long, EsSubscription> followsByUser = new HashMap<>();
        if (topicId != null) {
            for (EsSubscription follow : subscriptionDao.findActiveByTopicId(topicId)) {
                if (follow.getEmailNormalized() != null) {
                    followsByEmail.putIfAbsent(follow.getEmailNormalized(), follow);
                }
                if (follow.getUserId() != null) {
                    followsByUser.putIfAbsent(follow.getUserId(), follow);
                }
            }
        }
        List<SmallGroupSeriesComparison.Registrant> registrants = new ArrayList<>();
        for (EsTopicMeetingMember member : seriesMemberDao.findByMeetingId(seriesId)) {
            User user = member.getUserId() == null ? null : userDao.findById(member.getUserId()).orElse(null);
            EsSubscription follow = member.getUserId() == null ? null : followsByUser.get(member.getUserId());
            if (follow == null && member.getEmailNormalized() != null) {
                follow = followsByEmail.get(member.getEmailNormalized());
            }
            String label = PersonNames.display(user == null ? null : user.getDisplayName(),
                    user == null ? null : user.getFirstName(), user == null ? null : user.getLastName(),
                    follow == null ? null : follow.getContactFirstName(),
                    follow == null ? null : follow.getContactLastName(), member.getEmail());
            registrants.add(new SmallGroupSeriesComparison.Registrant(member.getEmailNormalized(), member.getUserId(),
                    member.getMembershipStatus().name(), member.getEmail(), label));
        }
        return registrants;
    }

    private List<SmallGroupSignals.MeetingView> meetingViews(Long seriesId) {
        List<EsMeeting> meetings = meetingDao.findByEsTopicMeetingId(seriesId);
        List<Long> ids = new ArrayList<>();
        for (EsMeeting meeting : meetings) {
            ids.add(meeting.getEsMeetingId());
        }
        List<EsMeetingAgendaItem> items = ids.isEmpty() ? List.of() : agendaDao.findByMeetingIds(ids);
        List<SmallGroupSignals.MeetingView> views = new ArrayList<>();
        for (EsMeeting meeting : meetings) {
            int agenda = 0;
            int linked = 0;
            for (EsMeetingAgendaItem item : items) {
                if (!meeting.getEsMeetingId().equals(item.getEsMeetingId())) {
                    continue;
                }
                if (item.getStatus() == EsMeetingAgendaItem.AgendaItemStatus.CANCELLED) {
                    continue;
                }
                agenda++;
                if (item.getEsTopicId() != null) {
                    linked++;
                }
            }
            LocalDate day = meeting.getScheduledStart() == null ? null : meeting.getScheduledStart().toLocalDate();
            views.add(new SmallGroupSignals.MeetingView(day,
                    meeting.getStatus() == EsMeeting.MeetingStatus.CANCELLED,
                    meeting.getNotesPublishedAt() != null, agenda, linked));
        }
        return views;
    }

    private String markJoinedIfNeeded(User actor, EsInitiative initiative, EsSmallGroup group,
            EsInitiativeMember member) {
        if (!SmallGroupParticipantRules.needsJoined(member.getMemberStatus().name())) {
            return null;
        }
        return initiatives.changeMemberStatus(actor, initiative, member.getEsInitiativeMemberId(),
                EsInitiativeMember.MemberStatus.JOINED,
                "Added to the " + group.getSmallGroupName() + " small group.");
    }

    private String followTopic(User actor, EsSmallGroup group, EsInitiativeMember member) {
        String email = member.getEmail();
        if ((email == null || email.isBlank()) && member.getUserId() != null) {
            User linked = userDao.findById(member.getUserId()).orElse(null);
            if (linked != null) {
                email = linked.getEmail();
            }
        }
        String normalized = EsNormalizer.normalizeEmail(email);
        EsSubscription existing = subscriptionDao
                .findByUserOrEmailAndTopic(member.getUserId(), normalized, group.getEsTopicId()).orElse(null);
        Optional<InitiativeFollowRules.FollowRequest> request = SmallGroupParticipantRules.topicFollow(
                group.getSmallGroupName(), group.getEsTopicId(), email, member.getContactFirstName(),
                member.getContactLastName(), organizationName(member),
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
                return "Saved the participant, but could not follow the Small Group topic: "
                        + outcome.getErrorMessage();
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Could not follow the Small Group topic for member "
                    + member.getEsInitiativeMemberId(), ex);
            return "Saved the participant, but could not follow the Small Group topic.";
        }
        return null;
    }

    private String organizationName(EsInitiativeMember member) {
        if (member.getEsInitiativeOrganizationId() == null) {
            return null;
        }
        EsInitiativeOrganization participation = participationDao.findById(member.getEsInitiativeOrganizationId())
                .orElse(null);
        if (participation == null) {
            return null;
        }
        return organizationDao.findById(participation.getOrganizationId()).map(Organization::getOrganizationName)
                .orElse(null);
    }

    private EsInitiativeMember locateMember(EsInitiative initiative, String email, String name) {
        String normalized = EsNormalizer.normalizeEmail(email);
        if (normalized != null) {
            for (EsInitiativeMember member : initiatives.members(initiative.getEsInitiativeId())) {
                if (normalized.equals(member.getEmailNormalized())) {
                    return member;
                }
            }
        }
        EsInitiativeMember match = null;
        for (EsInitiativeMember member : initiatives.members(initiative.getEsInitiativeId())) {
            if (name != null && name.trim().equals(member.displayName())
                    && (match == null || member.getEsInitiativeMemberId() > match.getEsInitiativeMemberId())) {
                match = member;
            }
        }
        if (match == null) {
            throw new IllegalArgumentException("The new person was not saved on the roster.");
        }
        return match;
    }

    private EsInitiativeMember findRosterMatch(EsInitiative initiative, String emailNormalized, Long userId) {
        for (EsInitiativeMember member : initiatives.members(initiative.getEsInitiativeId())) {
            if (SmallGroupSeriesComparison.samePerson(member.getEmailNormalized(), member.getUserId(),
                    emailNormalized, userId)) {
                return member;
            }
        }
        return null;
    }

    private EsInitiativeMember requireMember(EsInitiative initiative, Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("Choose a person.");
        }
        for (EsInitiativeMember member : initiatives.members(initiative.getEsInitiativeId())) {
            if (memberId.equals(member.getEsInitiativeMemberId())) {
                return member;
            }
        }
        throw new IllegalArgumentException("That person is not on this initiative's roster.");
    }

    private EsSmallGroup requireGroup(EsInitiative initiative, EsSmallGroup group) {
        if (group == null || initiative == null
                || !initiative.getEsInitiativeId().equals(group.getEsInitiativeId())) {
            throw new IllegalArgumentException("That Small Group was not found.");
        }
        return groupDao.findInInitiative(initiative.getEsInitiativeId(), group.getEsSmallGroupId())
                .orElseThrow(() -> new IllegalArgumentException("That Small Group was not found."));
    }

    private EsTopic requireTopic(Long topicId) {
        if (topicId == null) {
            throw new IllegalArgumentException("Choose a topic.");
        }
        return topicDao.findById(topicId).orElseThrow(() -> new IllegalArgumentException("That topic was not found."));
    }

    private EsTopicMeeting resolveSeries(Long topicId, Long seriesId) {
        if (seriesId == null) {
            return null;
        }
        EsTopicMeeting series = seriesDao.findById(seriesId)
                .orElseThrow(() -> new IllegalArgumentException("That meeting series was not found."));
        if (!topicId.equals(series.getEsTopicId())) {
            throw new IllegalArgumentException("That meeting series belongs to a different topic.");
        }
        return series;
    }

    private static boolean activeSeries(EsTopicMeeting series) {
        return series != null && series.getStatus() == EsTopicMeeting.MeetingStatus.ACTIVE
                && series.getDisabledAt() == null;
    }

    private static LocalDate[] dates(LocalDate cycleStart, LocalDate workingDay, LocalDate closeout) {
        if (cycleStart != null && closeout != null && closeout.isBefore(cycleStart)) {
            throw new IllegalArgumentException("Closeout cannot be before the cycle start.");
        }
        return new LocalDate[] { cycleStart, workingDay, closeout };
    }

    private String topicLabel(Long topicId) {
        return topicDao.findById(topicId).map(EsTopic::getTopicName).orElse(null);
    }

    private String seriesLabel(Long seriesId) {
        return seriesId == null ? null
                : seriesDao.findById(seriesId).map(EsTopicMeeting::getMeetingName).orElse(null);
    }

    private void comment(User actor, EsInitiative initiative, EsSmallGroup group, String text) {
        record(initiative.getEsInitiativeId(), group.getEsSmallGroupId(), actor,
                EsInitiativeTimelineEntry.EntryType.COMMENT, null, null, text);
    }

    private void recordField(EsInitiative initiative, EsSmallGroup group, User actor, String field, String from,
            String to) {
        if (same(from, to)) {
            return;
        }
        record(initiative.getEsInitiativeId(), group.getEsSmallGroupId(), actor,
                EsInitiativeTimelineEntry.EntryType.FIELD_CHANGE, from, to, field);
    }

    private void record(Long initiativeId, Long smallGroupId, User actor, EsInitiativeTimelineEntry.EntryType type,
            String from, String to, String note) {
        org.airahub.interophub.dao.EsInitiativeTimelineEntryDao timelineDao =
                new org.airahub.interophub.dao.EsInitiativeTimelineEntryDao();
        EsInitiativeTimelineEntry entry = new EsInitiativeTimelineEntry();
        entry.setEsInitiativeId(initiativeId);
        entry.setSubjectType(EsInitiativeTimelineEntry.SubjectType.SMALL_GROUP);
        entry.setSubjectId(smallGroupId);
        entry.setEntryType(type);
        entry.setFromValue(from);
        entry.setToValue(to);
        entry.setNoteText(note);
        entry.setCreatedByUserId(actor == null ? null : actor.getUserId());
        timelineDao.save(entry);
    }

    private static String requireText(String value, String label, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + " must be " + max + " characters or less.");
        }
        return trimmed;
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("That text must be " + max + " characters or less.");
        }
        return trimmed;
    }

    private static String url(String value) {
        String trimmed = clean(value, 2048);
        if (trimmed == null) {
            return null;
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("Charter link must start with http:// or https://.");
        }
        return trimmed;
    }

    private static String text(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static boolean same(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equals(b);
    }

    private static String joinWarnings(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null || second.isBlank() ? null : second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + " " + second;
    }
}
