package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsSurveyQuestion;
import org.hibernate.Transaction;

public class EsSurveyQuestionDao extends GenericDao<EsSurveyQuestion, Long> {

    public EsSurveyQuestionDao() {
        super(EsSurveyQuestion.class);
    }

    public List<EsSurveyQuestion> findBySurveyIdOrdered(Long esSurveyId) {
        if (esSurveyId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSurveyQuestion q where q.esSurveyId = :surveyId order by q.displayOrder asc",
                    EsSurveyQuestion.class)
                    .setParameter("surveyId", esSurveyId)
                    .getResultList();
        }
    }

    public int maxDisplayOrder(Long esSurveyId) {
        if (esSurveyId == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Integer max = session.createQuery(
                    "select max(q.displayOrder) from EsSurveyQuestion q where q.esSurveyId = :surveyId",
                    Integer.class)
                    .setParameter("surveyId", esSurveyId)
                    .uniqueResult();
            return max != null ? max : 0;
        }
    }

    public EsSurveyQuestion saveOrUpdate(EsSurveyQuestion question) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsSurveyQuestion merged = session.merge(question);
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
