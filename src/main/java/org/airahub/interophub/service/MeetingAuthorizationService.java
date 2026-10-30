package org.airahub.interophub.service;

import org.airahub.interophub.dao.EsAgendaItemPresenterDao;
import org.airahub.interophub.dao.EsSubscriptionDao;
import org.airahub.interophub.dao.EsTopicDao;
import org.airahub.interophub.dao.EsTopicMeetingCochairDao;
import org.airahub.interophub.dao.UserDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.model.EsTopicNote;
import org.airahub.interophub.model.User;

public class MeetingAuthorizationService {

    private final AuthFlowService authFlowService;
    private final TopicSpaceAccessService topicSpaceAccessService;
    private final UserDao userDao;
    private final EsTopicMeetingCochairDao cochairDao;
    private final EsAgendaItemPresenterDao presenterDao;
    private final EsSubscriptionDao subscriptionDao;
    private final EsTopicDao topicDao;

    public MeetingAuthorizationService() {
        this.authFlowService = new AuthFlowService();
        this.topicSpaceAccessService = new TopicSpaceAccessService();
        this.userDao = new UserDao();
        this.cochairDao = new EsTopicMeetingCochairDao();
        this.presenterDao = new EsAgendaItemPresenterDao();
        this.subscriptionDao = new EsSubscriptionDao();
        this.topicDao = new EsTopicDao();
    }

    public boolean canControlMeeting(Long userId, EsMeeting meeting) {
        if (userId == null || meeting == null) {
            return false;
        }
        User user = userDao.findById(userId).orElse(null);
        if (user != null && authFlowService.isAdminUser(user)) {
            return true;
        }
        if (user != null && topicSpaceAccessService.canAdministerSpace(user, meeting.getEsTopicSpaceId())) {
            return true;
        }
        if (userId.equals(meeting.getCreatedByUserId())) {
            return true;
        }
        if (userId.equals(meeting.getDesignatedChairUserId())
                || userId.equals(meeting.getDesignatedScribeUserId())
                || userId.equals(meeting.getCurrentChairUserId())
                || userId.equals(meeting.getCurrentScribeUserId())) {
            return true;
        }
        if (cochairDao.findByTopicMeetingIdAndUserId(meeting.getEsTopicMeetingId(), userId)
                .map(c -> c.getStatus() == org.airahub.interophub.model.TopicMeetingCochairStatus.ACTIVE)
                .orElse(false)) {
            return true;
        }
        return presenterDao.hasAcceptedPresenterForMeetingAndUserId(meeting.getEsMeetingId(), userId);
    }

    public boolean canEditTopicNote(Long userId, EsTopicNote note) {
        if (userId == null || note == null) {
            return false;
        }
        User user = userDao.findById(userId).orElse(null);
        if (user != null && authFlowService.isAdminUser(user)) {
            return true;
        }
        if (note.getEsMeetingId() != null) {
            EsMeeting meeting = new org.airahub.interophub.dao.EsMeetingDao().findById(note.getEsMeetingId())
                    .orElse(null);
            if (meeting != null && canControlMeeting(userId, meeting)) {
                return true;
            }
            if (note.getEsMeetingAgendaItemId() != null
                    && presenterDao.findAcceptedByAgendaItemIdAndUserId(note.getEsMeetingAgendaItemId(), userId)
                            .isPresent()) {
                return true;
            }
        }
        if (note.getEsTopicId() != null
                && subscriptionDao.hasActiveChampionForTopicAndUserId(note.getEsTopicId(), userId)) {
            return true;
        }
        if (user != null) {
            EsTopic topic = topicDao.findById(note.getEsTopicId()).orElse(null);
            return topicSpaceAccessService.canEditTopic(user, topic);
        }
        return false;
    }

    public boolean canCreateAdHocTopicNote(Long userId, Long topicId) {
        if (userId == null || topicId == null) {
            return false;
        }
        User user = userDao.findById(userId).orElse(null);
        if (user != null && authFlowService.isAdminUser(user)) {
            return true;
        }
        EsTopic topic = topicDao.findById(topicId).orElse(null);
        return user != null && topicSpaceAccessService.canEditTopic(user, topic);
    }
}