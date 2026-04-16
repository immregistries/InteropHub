package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopic;

public class EsTopicDao extends GenericDao<EsTopic, Long> {

    public EsTopicDao() {
        super(EsTopic.class);
    }

    public Optional<EsTopic> findByTopicCode(String topicCode) {
        if (topicCode == null || topicCode.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from EsTopic t where t.topicCode = :code", EsTopic.class)
                    .setParameter("code", topicCode.trim())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsTopic> findAllActive() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopic t where t.status = :status order by t.topicCode asc",
                    EsTopic.class)
                    .setParameter("status", EsTopic.EsTopicStatus.ACTIVE)
                    .getResultList();
        }
    }

    public List<EsTopic> findAllOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from EsTopic t order by t.topicCode asc", EsTopic.class)
                    .getResultList();
        }
    }

    public EsTopic saveOrUpdate(EsTopic topic) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopic merged = (EsTopic) session.merge(topic);
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
