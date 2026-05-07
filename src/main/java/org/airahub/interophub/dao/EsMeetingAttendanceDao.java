package org.airahub.interophub.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeetingAttendance;
import org.hibernate.Transaction;

public class EsMeetingAttendanceDao extends GenericDao<EsMeetingAttendance, Long> {

    public EsMeetingAttendanceDao() {
        super(EsMeetingAttendance.class);
    }

    public List<EsMeetingAttendance> findByMeetingIdAndDate(Long esTopicMeetingId, LocalDate date) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingAttendance a"
                            + " where a.esTopicMeetingId = :meetingId"
                            + " and a.attendanceDate = :date"
                            + " order by a.firstName, a.lastName",
                    EsMeetingAttendance.class)
                    .setParameter("meetingId", esTopicMeetingId)
                    .setParameter("date", date)
                    .getResultList();
        }
    }

    public Optional<EsMeetingAttendance> findByMeetingIdDateAndEmailNormalized(
            Long esTopicMeetingId, LocalDate date, String emailNormalized) {
        if (esTopicMeetingId == null || date == null || emailNormalized == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingAttendance a"
                            + " where a.esTopicMeetingId = :meetingId"
                            + " and a.attendanceDate = :date"
                            + " and a.emailNormalized = :emailNormalized",
                    EsMeetingAttendance.class)
                    .setParameter("meetingId", esTopicMeetingId)
                    .setParameter("date", date)
                    .setParameter("emailNormalized", emailNormalized)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public EsMeetingAttendance saveOrUpdate(EsMeetingAttendance record) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsMeetingAttendance merged = (EsMeetingAttendance) session.merge(record);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }
}
