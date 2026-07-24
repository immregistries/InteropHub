package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingAgendaActivity;
import org.airahub.interophub.model.EsMeetingParticipantCount;

public class MeetingParticipantCountService {

    private final MeetingAuthorizationService authorizationService;
    private final MeetingImmutabilityGuard immutabilityGuard;

    public MeetingParticipantCountService() {
        this.authorizationService = new MeetingAuthorizationService();
        this.immutabilityGuard = new MeetingImmutabilityGuard();
    }

    public EsMeetingParticipantCount recordParticipantCount(Long agendaActivityId, Integer count, Long actingUserId) {
        if (agendaActivityId == null || count == null || actingUserId == null) {
            throw new IllegalArgumentException("agendaActivityId, count, and actingUserId are required.");
        }
        if (count < 0) {
            throw new IllegalArgumentException("participantCount must be nonnegative.");
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                EsMeetingAgendaActivity activity = session.get(EsMeetingAgendaActivity.class, agendaActivityId);
                if (activity == null) {
                    throw new IllegalArgumentException("Agenda activity not found: " + agendaActivityId);
                }
                EsMeeting meeting = session.get(EsMeeting.class, activity.getEsMeetingId());
                immutabilityGuard.ensureMeetingMutable(meeting);
                if (!authorizationService.canControlMeeting(actingUserId, meeting)) {
                    throw new IllegalStateException("User is not authorized to record participant counts.");
                }

                EsMeetingParticipantCount observation = new EsMeetingParticipantCount();
                observation.setEsMeetingAgendaActivityId(agendaActivityId);
                observation.setParticipantCount(count);
                observation.setRecordedAt(LocalDateTime.now(ZoneOffset.UTC));
                observation.setRecordedByUserId(actingUserId);
                session.persist(observation);

                tx.commit();
                return observation;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }
}