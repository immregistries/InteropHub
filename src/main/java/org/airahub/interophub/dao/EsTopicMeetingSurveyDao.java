package org.airahub.interophub.dao;

import java.time.LocalDate;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicMeetingSurvey;
import org.airahub.interophub.model.EsTopicMeetingSurvey.AssignmentStatus;
import org.hibernate.Transaction;

public class EsTopicMeetingSurveyDao extends GenericDao<EsTopicMeetingSurvey, Long> {

    public EsTopicMeetingSurveyDao() {
        super(EsTopicMeetingSurvey.class);
    }

    public List<EsTopicMeetingSurvey> findByTopicMeetingId(Long esTopicMeetingId) {
        if (esTopicMeetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeetingSurvey a where a.esTopicMeetingId = :id order by a.startDate desc",
                    EsTopicMeetingSurvey.class)
                    .setParameter("id", esTopicMeetingId)
                    .getResultList();
        }
    }

    public List<EsTopicMeetingSurvey> findActive(Long esTopicMeetingId, LocalDate today) {
        if (esTopicMeetingId == null || today == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeetingSurvey a"
                            + " where a.esTopicMeetingId = :id"
                            + " and a.status = :status"
                            + " and a.startDate <= :today"
                            + " and a.endDate >= :today",
                    EsTopicMeetingSurvey.class)
                    .setParameter("id", esTopicMeetingId)
                    .setParameter("status", AssignmentStatus.ACTIVE)
                    .setParameter("today", today)
                    .getResultList();
        }
    }

    public List<EsTopicMeetingSurvey> findAllOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeetingSurvey a order by a.startDate desc",
                    EsTopicMeetingSurvey.class)
                    .getResultList();
        }
    }

    public EsTopicMeetingSurvey saveOrUpdate(EsTopicMeetingSurvey assignment) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicMeetingSurvey merged = session.merge(assignment);
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
