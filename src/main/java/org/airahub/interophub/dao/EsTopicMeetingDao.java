package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicMeeting;

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
