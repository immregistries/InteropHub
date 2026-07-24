package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.dao.EsMeetingDao;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaActivity;
import org.airahub.interophub.model.EsMeetingAgendaItem;
import org.airahub.interophub.model.EsTopicMeeting;

public class AgendaActivityService {

    private final EsMeetingDao meetingDao;
    private final MeetingAuthorizationService authorizationService;
    private final MeetingImmutabilityGuard immutabilityGuard;

    public AgendaActivityService() {
        this.meetingDao = new EsMeetingDao();
        this.authorizationService = new MeetingAuthorizationService();
        this.immutabilityGuard = new MeetingImmutabilityGuard();
    }

    public EsMeetingAgendaActivity activateAgendaItem(Long meetingId, Long agendaItemId, Long actingUserId) {
        if (meetingId == null || agendaItemId == null || actingUserId == null) {
            throw new IllegalArgumentException("meetingId, agendaItemId, and actingUserId are required.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsMeeting meeting = session.get(EsMeeting.class, meetingId);
                if (meeting == null) {
                    throw new IllegalArgumentException("Meeting not found: " + meetingId);
                }
                immutabilityGuard.ensureMeetingMutable(meeting);
                if (!authorizationService.canControlMeeting(actingUserId, meeting)) {
                    throw new IllegalStateException("User is not authorized to activate agenda items.");
                }

                EsMeetingAgendaItem agendaItem = session.get(EsMeetingAgendaItem.class, agendaItemId);
                if (agendaItem == null) {
                    throw new IllegalArgumentException("Agenda item not found: " + agendaItemId);
                }
                if (!meetingId.equals(agendaItem.getEsMeetingId())) {
                    throw new IllegalArgumentException("Agenda item does not belong to the meeting.");
                }

                EsTopicMeeting topicMeeting = session.get(EsTopicMeeting.class, meeting.getEsTopicMeetingId());
                if (topicMeeting == null) {
                    throw new IllegalStateException("Meeting series could not be resolved.");
                }
                Long topicId = agendaItem.getEsTopicId() != null ? agendaItem.getEsTopicId()
                        : topicMeeting.getEsTopicId();
                if (topicId == null) {
                    throw new IllegalStateException("Effective topic could not be resolved for the agenda item.");
                }

                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                session.createMutationQuery(
                        "update EsMeetingAgendaActivity a set a.endedAt = :endedAt, a.endedByUserId = :endedByUserId"
                                + " where a.esMeetingId = :meetingId and a.endedAt is null")
                        .setParameter("endedAt", now)
                        .setParameter("endedByUserId", actingUserId)
                        .setParameter("meetingId", meetingId)
                        .executeUpdate();

                EsMeetingAgendaActivity activity = new EsMeetingAgendaActivity();
                activity.setEsMeetingId(meetingId);
                activity.setEsMeetingAgendaItemId(agendaItemId);
                activity.setEsTopicId(topicId);
                activity.setStartedAt(now);
                activity.setStartedByUserId(actingUserId);
                session.persist(activity);

                meeting.setCurrentAgendaItemId(agendaItemId);
                session.merge(meeting);

                tx.commit();
                return activity;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public Optional<EsMeetingAgendaActivity> findCurrentAgendaActivity(Long meetingId) {
        return meetingDao.findById(meetingId).flatMap(meeting -> {
            try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
                return session.createQuery(
                        "from EsMeetingAgendaActivity a where a.esMeetingId = :meetingId and a.endedAt is null"
                                + " order by a.startedAt desc, a.esMeetingAgendaActivityId desc",
                        EsMeetingAgendaActivity.class)
                        .setParameter("meetingId", meeting.getEsMeetingId())
                        .setMaxResults(1)
                        .uniqueResultOptional();
            }
        });
    }
}