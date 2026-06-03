package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsSurveyAnswer;
import org.hibernate.Transaction;

public class EsSurveyAnswerDao extends GenericDao<EsSurveyAnswer, Long> {

    public EsSurveyAnswerDao() {
        super(EsSurveyAnswer.class);
    }

    public List<EsSurveyAnswer> findByResponseId(Long esSurveyResponseId) {
        if (esSurveyResponseId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSurveyAnswer a where a.esSurveyResponseId = :responseId order by a.esSurveyAnswerId asc",
                    EsSurveyAnswer.class)
                    .setParameter("responseId", esSurveyResponseId)
                    .getResultList();
        }
    }

    public EsSurveyAnswer save(EsSurveyAnswer answer) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsSurveyAnswer merged = session.merge(answer);
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
