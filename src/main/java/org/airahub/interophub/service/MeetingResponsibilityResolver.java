package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.airahub.interophub.dao.EsAgendaItemPresenterDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicMeetingCochairDao;
import org.airahub.interophub.dao.EsTopicSpaceMemberDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsAgendaItemPresenter;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsTopicSpaceMember;
import org.airahub.interophub.model.User;

/**
 * Resolves who is specifically responsible for one meeting, for use by
 * proactive per-meeting notifications (the daily staff action digest) -
 * mirroring {@link TopicContactResolver}'s tiered fallback for topic
 * notifications, but for meetings:
 *
 * <ol>
 * <li>This meeting's own people: designated/current chair, designated/current
 * scribe, active cochairs of the series, accepted presenters on its agenda,
 * and champions of any topic on that agenda.</li>
 * <li>If none of those exist, the Topic-Space's ADMIN members.</li>
 * <li>If there are none of those either, all site administrators.</li>
 * </ol>
 *
 * <p>
 * {@link #resolveForNotesPublishing} is a narrower tier-1 for the
 * PUBLISH_NOTES action specifically: publishing notes is the scribe's job,
 * or the chair's if there's no scribe - not every meeting-role contact. It
 * falls through to the same Topic-Space-admin / site-admin tiers as above
 * when there's neither.
 * </p>
 *
 * <p>
 * {@link #resolveForAgendaOwnership} is a separate narrower cascade for
 * PUBLISH_PROPOSED_AGENDA and FINALIZE_AGENDA specifically - agenda upkeep
 * is the chair's job, not a presenter's or cochair's: chair, then the
 * meeting's topic champions if there's no chair, then Topic-Space admins,
 * then site admins.
 * </p>
 *
 * <p>
 * This is deliberately narrower than
 * {@link MeetingAuthorizationService#canControlMeeting}
 * (which also always includes site admins, unconditionally, and is what the
 * dashboard/action queue keeps using - an admin should still be able to see
 * and act on anything). This resolver is for "who should be proactively
 * emailed," where site admins should only hear about a meeting nobody more
 * specific is watching.
 */
public class MeetingResponsibilityResolver {

    public enum Tier {
        MEETING_ROLE,
        TOPIC_SPACE_ADMIN,
        SITE_ADMIN
    }

    public record Responsible(Long userId, String email, String emailNormalized, Tier tier) {
    }

    private final EsMeetingAgendaItemDao agendaItemDao;
    private final EsAgendaItemPresenterDao presenterDao;
    private final EsTopicMeetingCochairDao cochairDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicSpaceMemberDao topicSpaceMemberDao;
    private final UserDao userDao;

    public MeetingResponsibilityResolver() {
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.presenterDao = new EsAgendaItemPresenterDao();
        this.cochairDao = new EsTopicMeetingCochairDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicSpaceMemberDao = new EsTopicSpaceMemberDao();
        this.userDao = new UserDao();
    }

    public List<Responsible> resolveForMeeting(EsMeeting meeting) {
        List<Responsible> meetingRoleContacts = collectMeetingRoleContacts(meeting);
        if (!meetingRoleContacts.isEmpty()) {
            return meetingRoleContacts;
        }
        return resolveFallbackTiers(meeting);
    }

    /**
     * Tier 1 for PUBLISH_NOTES only: the scribe (designated or current), or
     * the chair (designated or current) if there's no scribe - not the full
     * meeting-role contact list. Falls through to the same Topic-Space-admin
     * / site-admin tiers as {@link #resolveForMeeting} when neither exists.
     */
    public List<Responsible> resolveForNotesPublishing(EsMeeting meeting) {
        List<Responsible> notesContacts = collectNotesResponsibleContacts(meeting);
        if (!notesContacts.isEmpty()) {
            return notesContacts;
        }
        return resolveFallbackTiers(meeting);
    }

    /**
     * Tier 1 for PUBLISH_PROPOSED_AGENDA and FINALIZE_AGENDA only: the chair
     * (designated or current), or the meeting's topic champions if there's
     * no chair - not cochairs, presenters, or the scribe. Falls through to
     * the same Topic-Space-admin / site-admin tiers as {@link #resolveForMeeting}
     * when neither exists.
     */
    public List<Responsible> resolveForAgendaOwnership(EsMeeting meeting) {
        List<Responsible> chairContacts = collectChairContacts(meeting);
        if (!chairContacts.isEmpty()) {
            return chairContacts;
        }
        List<Responsible> championContacts = collectTopicChampionContacts(meeting);
        if (!championContacts.isEmpty()) {
            return championContacts;
        }
        return resolveFallbackTiers(meeting);
    }

    private List<Responsible> resolveFallbackTiers(EsMeeting meeting) {
        if (meeting.getEsTopicSpaceId() != null) {
            List<Responsible> spaceAdmins = collectTopicSpaceAdmins(meeting.getEsTopicSpaceId());
            if (!spaceAdmins.isEmpty()) {
                return spaceAdmins;
            }
        }
        return collectSiteAdmins();
    }

    private List<Responsible> collectNotesResponsibleContacts(EsMeeting meeting) {
        Set<Long> scribeIds = new LinkedHashSet<>();
        addIfPresent(scribeIds, meeting.getDesignatedScribeUserId());
        addIfPresent(scribeIds, meeting.getCurrentScribeUserId());
        if (!scribeIds.isEmpty()) {
            return resolveUsers(scribeIds, Tier.MEETING_ROLE);
        }

        return collectChairContacts(meeting);
    }

    private List<Responsible> collectChairContacts(EsMeeting meeting) {
        Set<Long> chairIds = new LinkedHashSet<>();
        addIfPresent(chairIds, meeting.getDesignatedChairUserId());
        addIfPresent(chairIds, meeting.getCurrentChairUserId());
        return resolveUsers(chairIds, Tier.MEETING_ROLE);
    }

    private List<Responsible> collectTopicChampionContacts(EsMeeting meeting) {
        List<Long> topicIds = collectAgendaTopicIds(meeting);
        if (topicIds.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        subscriptionDao.findActiveChampionsByTopicIds(topicIds)
                .forEach(champion -> addIfPresent(userIds, champion.getUserId()));
        return resolveUsers(userIds, Tier.MEETING_ROLE);
    }

    private List<Long> collectAgendaTopicIds(EsMeeting meeting) {
        return agendaItemDao.findByMeetingIdOrdered(meeting.getEsMeetingId()).stream()
                .map(EsMeetingAgendaItem::getEsTopicId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<Responsible> collectMeetingRoleContacts(EsMeeting meeting) {
        Set<Long> userIds = new LinkedHashSet<>();
        addIfPresent(userIds, meeting.getDesignatedChairUserId());
        addIfPresent(userIds, meeting.getCurrentChairUserId());
        addIfPresent(userIds, meeting.getDesignatedScribeUserId());
        addIfPresent(userIds, meeting.getCurrentScribeUserId());

        if (meeting.getEsTopicMeetingId() != null) {
            cochairDao.findActiveByTopicMeetingId(meeting.getEsTopicMeetingId())
                    .forEach(cochair -> addIfPresent(userIds, cochair.getUserId()));
        }

        List<EsMeetingAgendaItem> agendaItems = agendaItemDao.findByMeetingIdOrdered(meeting.getEsMeetingId());
        List<Long> agendaItemIds = agendaItems.stream()
                .map(EsMeetingAgendaItem::getEsMeetingAgendaItemId)
                .filter(Objects::nonNull)
                .toList();
        if (!agendaItemIds.isEmpty()) {
            for (EsAgendaItemPresenter presenter : presenterDao.findByAgendaItemIds(agendaItemIds)) {
                if (presenter.getStatus() == EsAgendaItemPresenter.PresenterStatus.ACCEPTED) {
                    addIfPresent(userIds, presenter.getUserId());
                }
            }
        }

        List<Long> topicIds = collectAgendaTopicIds(meeting);
        if (!topicIds.isEmpty()) {
            subscriptionDao.findActiveChampionsByTopicIds(topicIds)
                    .forEach(champion -> addIfPresent(userIds, champion.getUserId()));
        }

        return resolveUsers(userIds, Tier.MEETING_ROLE);
    }

    private List<Responsible> collectTopicSpaceAdmins(Long esTopicSpaceId) {
        List<EsTopicSpaceMember> admins = topicSpaceMemberDao.findAdminsBySpaceId(esTopicSpaceId);
        Set<Long> userIds = new LinkedHashSet<>();
        admins.forEach(admin -> addIfPresent(userIds, admin.getUserId()));
        return resolveUsers(userIds, Tier.TOPIC_SPACE_ADMIN);
    }

    private List<Responsible> collectSiteAdmins() {
        List<Responsible> admins = new ArrayList<>();
        for (User admin : userDao.findAllAdmins()) {
            if (admin.getEmail() != null && admin.getEmailNormalized() != null) {
                admins.add(new Responsible(admin.getUserId(), admin.getEmail(), admin.getEmailNormalized(),
                        Tier.SITE_ADMIN));
            }
        }
        return admins;
    }

    private List<Responsible> resolveUsers(Set<Long> userIds, Tier tier) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<Responsible> resolved = new ArrayList<>();
        for (User user : userDao.findByIds(List.copyOf(userIds))) {
            if (user.getEmail() != null && user.getEmailNormalized() != null) {
                resolved.add(new Responsible(user.getUserId(), user.getEmail(), user.getEmailNormalized(), tier));
            }
        }
        return resolved;
    }

    private static void addIfPresent(Set<Long> userIds, Long userId) {
        if (userId != null) {
            userIds.add(userId);
        }
    }
}
