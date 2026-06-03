package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsSurvey;
import org.airahub.interophub.model.EsSurvey.SurveyStatus;

public class EsSurveyDao extends GenericDao<EsSurvey, Long> {

    public EsSurveyDao() {
        super(EsSurvey.class);
    }

    public List<EsSurvey> findByStatus(SurveyStatus status) {
        if (status == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSurvey s where s.status = :status order by s.surveyName asc",
                    EsSurvey.class)
                    .setParameter("status", status)
                    .getResultList();
        }
    }

    public List<EsSurvey> findAllOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSurvey s order by s.status asc, s.surveyName asc",
                    EsSurvey.class)
                    .getResultList();
        }
    }

    public Optional<EsSurvey> findByKey(String surveyKey) {
        if (surveyKey == null || surveyKey.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSurvey s where s.surveyKey = :key",
                    EsSurvey.class)
                    .setParameter("key", surveyKey.trim())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }
}
