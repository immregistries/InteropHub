package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicMeeting;
import org.airahub.interophub.model.EsTopicMeetingMember;

public class EsTopicMeetingDao extends GenericDao<EsTopicMeeting, Long> {

    public EsTopicMeetingDao() {
        super(EsTopicMeeting.class);
    }

    public Optional<EsTopicMeeting> findByTopicId(Long esTopicId) {
        if (esTopicId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeeting m where m.esTopicId = :topicId",
                    EsTopicMeeting.class)
                    .setParameter("topicId", esTopicId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<AdminMeetingBrowseRow> findAllActiveBrowseRows() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<EsTopicMeeting> meetings = session.createQuery(
                    "from EsTopicMeeting m where m.status = :status order by lower(m.meetingName)",
                    EsTopicMeeting.class)
                    .setParameter("status", EsTopicMeeting.MeetingStatus.ACTIVE)
                    .getResultList();

            if (meetings.isEmpty()) {
                return List.of();
            }

            List<Long> meetingIds = meetings.stream()
                    .map(EsTopicMeeting::getEsTopicMeetingId)
                    .collect(Collectors.toCollection(ArrayList::new));

            Map<Long, Long> requestedCounts = countByStatus(session, meetingIds,
                    EsTopicMeetingMember.MembershipStatus.REQUESTED);
            Map<Long, Long> approvedCounts = countByStatus(session, meetingIds,
                    EsTopicMeetingMember.MembershipStatus.APPROVED);

            return meetings.stream()
                    .map(m -> new AdminMeetingBrowseRow(
                            m.getEsTopicMeetingId(),
                            m.getMeetingName(),
                            requestedCounts.getOrDefault(m.getEsTopicMeetingId(), 0L),
                            approvedCounts.getOrDefault(m.getEsTopicMeetingId(), 0L)))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private Map<Long, Long> countByStatus(org.hibernate.Session session, List<Long> meetingIds,
            EsTopicMeetingMember.MembershipStatus status) {
        List<Object[]> rows = session.createQuery(
                "select mm.esTopicMeetingId, count(mm.esTopicMeetingMemberId)"
                        + " from EsTopicMeetingMember mm"
                        + " where mm.esTopicMeetingId in :ids and mm.membershipStatus = :status"
                        + " group by mm.esTopicMeetingId",
                Object[].class)
                .setParameterList("ids", meetingIds)
                .setParameter("status", status)
                .getResultList();
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }

    public EsTopicMeeting saveOrUpdate(EsTopicMeeting meeting) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicMeeting merged = (EsTopicMeeting) session.merge(meeting);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public void disableMeeting(EsTopicMeeting meeting, Long disabledByUserId) {
        if (meeting == null) {
            return;
        }
        meeting.setStatus(EsTopicMeeting.MeetingStatus.DISABLED);
        meeting.setDisabledAt(LocalDateTime.now());
        meeting.setDisabledByUserId(disabledByUserId);
        saveOrUpdate(meeting);
    }
}
