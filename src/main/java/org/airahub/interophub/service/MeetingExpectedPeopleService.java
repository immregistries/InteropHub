package org.airahub.interophub.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.dao.EsAgendaItemPresenterDao;
import org.airahub.interophub.dao.EsMeetingAgendaItemDao;
import org.airahub.interophub.dao.EsMeetingRsvpDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingMemberDao;
import org.airahub.interophub.model.EsAgendaItemPresenter;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsMeetingRsvp;
import org.airahub.interophub.model.EsSubscription;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicMeetingMember;
import org.airahub.interophub.model.MeetingRsvpResponse;

/**
 * Meeting Attendance Console, Phase 5 (docs/meeting-attendance-console-design.md):
 * who's "expected" for a meeting or one of its agenda topics - series
 * members, RSVPs, accepted presenters, and topic followers - deduplicated by
 * user id (falling back to email). Shared by the console page (for its
 * plain-list rendering when the candidate pool is small) and the people
 * -search endpoint (for the "Expected" badge), so the two can never disagree
 * about who counts as expected.
 */
public class MeetingExpectedPeopleService {

    /** One candidate "expected" person, identified by userId (preferred) or email, with why they're expected. */
    public record ExpectedPerson(Long userId, String emailNormalized, String source) {
    }

    private final EsMeetingRsvpDao rsvpDao;
    private final EsMeetingAgendaItemDao agendaItemDao;
    private final EsAgendaItemPresenterDao presenterDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicMeetingMemberDao topicMeetingMemberDao;
    private final EsTopicDao topicDao;

    public MeetingExpectedPeopleService() {
        this.rsvpDao = new EsMeetingRsvpDao();
        this.agendaItemDao = new EsMeetingAgendaItemDao();
        this.presenterDao = new EsAgendaItemPresenterDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicMeetingMemberDao = new EsTopicMeetingMemberDao();
        this.topicDao = new EsTopicDao();
    }

    /** Every non-cancelled/non-postponed agenda topic on this meeting occurrence, in agenda order. */
    public List<EsTopic> listAgendaTopics(Long esMeetingId) {
        List<EsTopic> topics = new ArrayList<>();
        for (Long topicId : agendaTopicIds(esMeetingId)) {
            topicDao.findById(topicId).ifPresent(topics::add);
        }
        return topics;
    }

    /** Series members, RSVP Coming/Maybe, any accepted presenter, and any follower of a topic on this agenda. */
    public List<ExpectedPerson> findExpectedMeetingWide(EsMeeting meeting) {
        return findExpectedMeetingWide(meeting, rsvpDao.findByMeetingId(meeting.getEsMeetingId()));
    }

    /** Same as {@link #findExpectedMeetingWide(EsMeeting)}, reusing an already-loaded RSVP list. */
    public List<ExpectedPerson> findExpectedMeetingWide(EsMeeting meeting, List<EsMeetingRsvp> rsvps) {
        Map<String, ExpectedPerson> candidates = new LinkedHashMap<>();
        for (EsTopicMeetingMember m : topicMeetingMemberDao.findByMeetingIdAndStatus(
                meeting.getEsTopicMeetingId(), EsTopicMeetingMember.MembershipStatus.APPROVED)) {
            addCandidate(candidates, m.getUserId(), m.getEmailNormalized(), "Series member");
        }
        for (EsMeetingRsvp r : rsvps) {
            if (r.getResponse() != MeetingRsvpResponse.NOT_COMING) {
                addCandidate(candidates, r.getUserId(), null, "RSVP");
            }
        }
        List<Long> topicIds = agendaTopicIds(meeting.getEsMeetingId());
        for (EsAgendaItemPresenter p : presenterDao.findByAgendaItemIds(agendaItemIdsForTopics(meeting.getEsMeetingId()))) {
            if (p.getStatus() == EsAgendaItemPresenter.PresenterStatus.ACCEPTED) {
                addCandidate(candidates, p.getUserId(), p.getEmailNormalized(), "Presenter");
            }
        }
        for (EsSubscription s : subscriptionDao.findActiveSubscribersByTopicIds(topicIds)) {
            addCandidate(candidates, s.getUserId(), s.getEmailNormalized(), "Topic follower");
        }
        return List.copyOf(candidates.values());
    }

    /** Accepted presenters and followers of just this one topic, scoped to this meeting occurrence's agenda. */
    public List<ExpectedPerson> findExpectedForTopic(Long esMeetingId, Long topicId) {
        Map<String, ExpectedPerson> candidates = new LinkedHashMap<>();
        List<Long> itemIds = agendaItemDao.findByMeetingIdOrdered(esMeetingId).stream()
                .filter(i -> topicId.equals(i.getEsTopicId())
                        && i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.CANCELLED
                        && i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.POSTPONED)
                .map(EsMeetingAgendaItem::getEsMeetingAgendaItemId)
                .toList();
        for (EsAgendaItemPresenter p : presenterDao.findByAgendaItemIds(itemIds)) {
            if (p.getStatus() == EsAgendaItemPresenter.PresenterStatus.ACCEPTED) {
                addCandidate(candidates, p.getUserId(), p.getEmailNormalized(), "Presenter");
            }
        }
        for (EsSubscription s : subscriptionDao.findActiveSubscribersByTopicIds(List.of(topicId))) {
            addCandidate(candidates, s.getUserId(), s.getEmailNormalized(), "Follower");
        }
        return List.copyOf(candidates.values());
    }

    private List<Long> agendaTopicIds(Long esMeetingId) {
        return agendaItemDao.findByMeetingIdOrdered(esMeetingId).stream()
                .filter(i -> i.getEsTopicId() != null
                        && i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.CANCELLED
                        && i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.POSTPONED)
                .map(EsMeetingAgendaItem::getEsTopicId)
                .distinct()
                .toList();
    }

    private List<Long> agendaItemIdsForTopics(Long esMeetingId) {
        return agendaItemDao.findByMeetingIdOrdered(esMeetingId).stream()
                .filter(i -> i.getEsTopicId() != null
                        && i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.CANCELLED
                        && i.getStatus() != EsMeetingAgendaItem.AgendaItemStatus.POSTPONED)
                .map(EsMeetingAgendaItem::getEsMeetingAgendaItemId)
                .toList();
    }

    /** Adds a candidate keyed by userId if present, else by email - first source given for a person wins. */
    private void addCandidate(Map<String, ExpectedPerson> candidates, Long userId, String emailNormalized,
            String source) {
        if (userId == null && (emailNormalized == null || emailNormalized.isBlank())) {
            return;
        }
        String key = userId != null ? "u:" + userId : "e:" + emailNormalized;
        candidates.putIfAbsent(key, new ExpectedPerson(userId, emailNormalized, source));
    }
}
