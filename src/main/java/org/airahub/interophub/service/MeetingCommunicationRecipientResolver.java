package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.airahub.interophub.dao.EsAgendaItemPresenterDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.model.CommunicationRecipientPreview;
import org.airahub.interophub.model.EsAgendaItemPresenter;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsMeetingCommunication;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.RecipientGroup;

/**
 * Resolves the full list of unique recipients for a meeting communication,
 * based on the four data sources controlled by the boolean group flags.
 *
 * Priority (highest to lowest) when a person qualifies for multiple groups:
 * AGENDA_PRESENTER > TOPIC_CHAMPION > TOPIC_SUBSCRIBER > GENERAL_MEETING_MEMBER
 *
 * Deduplication is by emailNormalized. The highest-priority group wins as
 * primaryGroup; all qualifying groups are retained in allGroups.
 */
public class MeetingCommunicationRecipientResolver {

    private final EsTopicMeetingMemberDao memberDao;
    private final EsMeetingAgendaItemDao agendaItemDao;
    private final EsAgendaItemPresenterDao presenterDao;
    private final EsSubscriptionDao subscriptionDao;

    public MeetingCommunicationRecipientResolver() {
        this.memberDao = new EsTopicMeetingMemberDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.presenterDao = new EsAgendaItemPresenterDao();
        this.subscriptionDao = new EsSubscriptionDao();
    }

    /**
     * Returns all resolved, deduplicated recipients for the given communication
     * and meeting. The list is ordered by primaryGroup priority (highest first).
     */
    public List<CommunicationRecipientPreview> resolve(
            EsMeetingCommunication communication,
            EsMeeting meeting) {

        // Keyed by emailNormalized — accumulates data as we process each source
        Map<String, RecipientBuilder> builders = new LinkedHashMap<>();

        // Load agenda items for this meeting (needed for topic IDs and presenter
        // lookup). Exclude POSTPONED and CANCELLED items so their champions,
        // subscribers, and presenters are not notified.
        List<EsMeetingAgendaItem> agendaItems = agendaItemDao.findByMeetingIdOrdered(meeting.getEsMeetingId());
        List<EsMeetingAgendaItem> activeAgendaItems = agendaItems.stream()
                .filter(item -> item.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.POSTPONED
                        && item.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.CANCELLED)
                .collect(Collectors.toList());
        List<Long> topicIds = activeAgendaItems.stream()
                .map(EsMeetingAgendaItem::getEsTopicId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        List<Long> agendaItemIds = activeAgendaItems.stream()
                .map(EsMeetingAgendaItem::getEsMeetingAgendaItemId)
                .collect(Collectors.toList());
        // Map agenda item ID -> item title for enriching presenter recipients
        Map<Long, String> agendaItemTitles = activeAgendaItems.stream()
                .collect(Collectors.toMap(
                        EsMeetingAgendaItem::getEsMeetingAgendaItemId,
                        EsMeetingAgendaItem::getTitle,
                        (a, b) -> a));
        // Map topic ID -> topic name (via agenda items) for enriching subscription
        // recipients
        // (EsMeetingAgendaItem.title is the agenda item title; topic name requires a
        // separate
        // lookup — we use the agenda item title as a proxy when topic name is
        // unavailable)

        // Source 1: General Meeting Members (APPROVED)
        if (communication.isIncludeGeneralMembers()) {
            List<EsTopicMeetingMember> members = memberDao.findByMeetingIdAndStatus(
                    meeting.getEsTopicMeetingId(),
                    EsTopicMeetingMember.MembershipStatus.APPROVED);
            for (EsTopicMeetingMember m : members) {
                builders.computeIfAbsent(m.getEmailNormalized(),
                        k -> new RecipientBuilder(m.getEmail(), m.getEmailNormalized(), m.getUserId(), null))
                        .addGroup(RecipientGroup.GENERAL_MEETING_MEMBER);
            }
        }

        // Source 2: Topic Subscribers (SUBSCRIBED status, topics on agenda)
        if (communication.isIncludeTopicSubscribers() && !topicIds.isEmpty()) {
            List<EsSubscription> subs = subscriptionDao.findActiveSubscribersByTopicIds(topicIds);
            for (EsSubscription s : subs) {
                RecipientBuilder rb = builders.computeIfAbsent(
                        s.getEmailNormalized(),
                        k -> new RecipientBuilder(s.getEmail(), s.getEmailNormalized(), s.getUserId(), null));
                rb.addGroup(RecipientGroup.TOPIC_SUBSCRIBER);
                if (s.getEsTopicId() != null) {
                    rb.addTopicId(s.getEsTopicId());
                }
            }
        }

        // Source 3: Topic Champions (CHAMPION status, topics on agenda)
        if (communication.isIncludeTopicChampions() && !topicIds.isEmpty()) {
            List<EsSubscription> champions = subscriptionDao.findActiveChampionsByTopicIds(topicIds);
            for (EsSubscription s : champions) {
                RecipientBuilder rb = builders.computeIfAbsent(
                        s.getEmailNormalized(),
                        k -> new RecipientBuilder(s.getEmail(), s.getEmailNormalized(), s.getUserId(), null));
                rb.addGroup(RecipientGroup.TOPIC_CHAMPION);
                if (s.getEsTopicId() != null) {
                    rb.addTopicId(s.getEsTopicId());
                }
            }
        }

        // Source 4: Agenda Presenters (non-DECLINED, non-REMOVED)
        if (communication.isIncludePresenters() && !agendaItemIds.isEmpty()) {
            List<EsAgendaItemPresenter> presenters = presenterDao.findByAgendaItemIds(agendaItemIds);
            for (EsAgendaItemPresenter p : presenters) {
                if (p.getStatus() == EsAgendaItemPresenter.PresenterStatus.DECLINED
                        || p.getStatus() == EsAgendaItemPresenter.PresenterStatus.REMOVED) {
                    continue;
                }
                RecipientBuilder rb = builders.computeIfAbsent(
                        p.getEmailNormalized(),
                        k -> new RecipientBuilder(p.getEmail(), p.getEmailNormalized(), p.getUserId(),
                                p.getDisplayName()));
                rb.addGroup(RecipientGroup.AGENDA_PRESENTER);
                String title = agendaItemTitles.get(p.getEsMeetingAgendaItemId());
                if (title != null) {
                    rb.addAgendaItemTitle(title);
                }
                // Prefer a non-null display name from presenter record
                if (rb.displayName == null && p.getDisplayName() != null) {
                    rb.displayName = p.getDisplayName();
                }
            }
        }

        // Build final list ordered by priority (highest-priority group first)
        List<CommunicationRecipientPreview> result = new ArrayList<>();
        for (RecipientBuilder rb : builders.values()) {
            result.add(rb.build());
        }
        result.sort((a, b) -> b.getPrimaryGroup().ordinal() - a.getPrimaryGroup().ordinal());
        return result;
    }

    // ---------------------------------------------------------------------------
    // Internal mutable builder
    // ---------------------------------------------------------------------------

    private static class RecipientBuilder {
        private final String email;
        private final String emailNormalized;
        private final Long userId;
        private String displayName;
        private final Set<RecipientGroup> groups = EnumSet.noneOf(RecipientGroup.class);
        private final List<String> topicIds = new ArrayList<>();
        private final List<String> agendaItemTitles = new ArrayList<>();

        RecipientBuilder(String email, String emailNormalized, Long userId, String displayName) {
            this.email = email;
            this.emailNormalized = emailNormalized;
            this.userId = userId;
            this.displayName = displayName;
        }

        void addGroup(RecipientGroup group) {
            groups.add(group);
        }

        void addTopicId(Long topicId) {
            String s = String.valueOf(topicId);
            if (!topicIds.contains(s)) {
                topicIds.add(s);
            }
        }

        void addAgendaItemTitle(String title) {
            if (!agendaItemTitles.contains(title)) {
                agendaItemTitles.add(title);
            }
        }

        CommunicationRecipientPreview build() {
            // Highest ordinal = highest priority
            RecipientGroup primary = groups.stream()
                    .max((a, b) -> a.ordinal() - b.ordinal())
                    .orElse(RecipientGroup.GENERAL_MEETING_MEMBER);
            return new CommunicationRecipientPreview(
                    email,
                    emailNormalized,
                    userId,
                    displayName,
                    primary,
                    EnumSet.copyOf(groups),
                    List.copyOf(topicIds),
                    List.copyOf(agendaItemTitles));
        }
    }
}
