package org.airahub.interophub.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeeting;
import org.airahub.interophub.model.EsMeetingRoleAssignment;
import org.airahub.interophub.model.MeetingRoleType;

public class MeetingRoleService {

    private final MeetingAuthorizationService authorizationService;
    private final MeetingImmutabilityGuard immutabilityGuard;

    public MeetingRoleService() {
        this.authorizationService = new MeetingAuthorizationService();
        this.immutabilityGuard = new MeetingImmutabilityGuard();
    }

    public EsMeetingRoleAssignment assignChair(Long meetingId, Long newChairUserId, Long actingUserId) {
        return assignRole(meetingId, MeetingRoleType.CHAIR, newChairUserId, actingUserId);
    }

    public EsMeetingRoleAssignment assignScribe(Long meetingId, Long newScribeUserId, Long actingUserId) {
        return assignRole(meetingId, MeetingRoleType.SCRIBE, newScribeUserId, actingUserId);
    }

    private EsMeetingRoleAssignment assignRole(Long meetingId, MeetingRoleType roleType, Long newUserId,
            Long actingUserId) {
        if (meetingId == null || roleType == null || newUserId == null || actingUserId == null) {
            throw new IllegalArgumentException("meetingId, newUserId, and actingUserId are required.");
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
                    throw new IllegalStateException("User is not authorized to assign meeting roles.");
                }

                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                EsMeetingRoleAssignment current = session.createQuery(
                        "from EsMeetingRoleAssignment r where r.esMeetingId = :meetingId"
                                + " and r.roleType = :roleType and r.endedAt is null",
                        EsMeetingRoleAssignment.class)
                        .setParameter("meetingId", meetingId)
                        .setParameter("roleType", roleType)
                        .setMaxResults(1)
                        .uniqueResultOptional()
                        .orElse(null);
                if (current != null) {
                    current.setEndedAt(now);
                    session.merge(current);
                }

                EsMeetingRoleAssignment assignment = new EsMeetingRoleAssignment();
                assignment.setEsMeetingId(meetingId);
                assignment.setRoleType(roleType);
                assignment.setUserId(newUserId);
                assignment.setStartedAt(now);
                assignment.setAssignedByUserId(actingUserId);
                session.persist(assignment);

                if (roleType == MeetingRoleType.CHAIR) {
                    meeting.setCurrentChairUserId(newUserId);
                } else {
                    meeting.setCurrentScribeUserId(newUserId);
                }
                session.merge(meeting);

                tx.commit();
                return assignment;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }

    public Optional<EsMeetingRoleAssignment> findCurrentChairAssignment(Long meetingId) {
        return findCurrentRoleAssignment(meetingId, MeetingRoleType.CHAIR);
    }

    public Optional<EsMeetingRoleAssignment> findCurrentScribeAssignment(Long meetingId) {
        return findCurrentRoleAssignment(meetingId, MeetingRoleType.SCRIBE);
    }

    private Optional<EsMeetingRoleAssignment> findCurrentRoleAssignment(Long meetingId, MeetingRoleType roleType) {
        if (meetingId == null || roleType == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingRoleAssignment r where r.esMeetingId = :meetingId"
                            + " and r.roleType = :roleType and r.endedAt is null"
                            + " order by r.startedAt desc, r.esMeetingRoleAssignmentId desc",
                    EsMeetingRoleAssignment.class)
                    .setParameter("meetingId", meetingId)
                    .setParameter("roleType", roleType)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }
}