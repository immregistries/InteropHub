package org.airahub.interophub.dao;

import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsSurveyResponse;
import org.hibernate.Transaction;

public class EsSurveyResponseDao extends GenericDao<EsSurveyResponse, Long> {

    public EsSurveyResponseDao() {
        super(EsSurveyResponse.class);
    }

    public Optional<EsSurveyResponse> findByTopicMeetingSurveyIdAndUserId(Long esTopicMeetingSurveyId, Long userId) {
        if (esTopicMeetingSurveyId == null || userId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSurveyResponse r"
                            + " where r.esTopicMeetingSurveyId = :assignmentId"
                            + " and r.userId = :userId",
                    EsSurveyResponse.class)
                    .setParameter("assignmentId", esTopicMeetingSurveyId)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Optional<EsSurveyResponse> findByTopicMeetingSurveyIdAndEmailNormalized(
            Long esTopicMeetingSurveyId, String emailNormalized) {
        if (esTopicMeetingSurveyId == null || emailNormalized == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSurveyResponse r"
                            + " where r.esTopicMeetingSurveyId = :assignmentId"
                            + " and r.emailNormalized = :email",
                    EsSurveyResponse.class)
                    .setParameter("assignmentId", esTopicMeetingSurveyId)
                    .setParameter("email", emailNormalized)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public long countByTopicMeetingSurveyId(Long esTopicMeetingSurveyId) {
        if (esTopicMeetingSurveyId == null) {
            return 0L;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(r) from EsSurveyResponse r where r.esTopicMeetingSurveyId = :assignmentId",
                    Long.class)
                    .setParameter("assignmentId", esTopicMeetingSurveyId)
                    .uniqueResult();
            return count != null ? count : 0L;
        }
    }

    public long countByTopicMeetingSurveyIdExcludingAdmin(Long esTopicMeetingSurveyId) {
        if (esTopicMeetingSurveyId == null) {
            return 0L;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(r) from EsSurveyResponse r"
                            + " where r.esTopicMeetingSurveyId = :assignmentId"
                            + " and (r.userId is null"
                            + "  or r.userId not in (select u.userId from User u where u.isAdmin = true))",
                    Long.class)
                    .setParameter("assignmentId", esTopicMeetingSurveyId)
                    .uniqueResult();
            return count != null ? count : 0L;
        }
    }

    public EsSurveyResponse saveOrUpdate(EsSurveyResponse response) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsSurveyResponse merged = session.merge(response);
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
