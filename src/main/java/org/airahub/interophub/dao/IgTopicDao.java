package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.IgTopic;

public class IgTopicDao extends GenericDao<IgTopic, Long> {
    public IgTopicDao() {
        super(IgTopic.class);
    }

    public List<IgTopic> findAllOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from IgTopic it order by it.topicCode asc", IgTopic.class)
                    .getResultList();
        }
    }

    public Optional<IgTopic> findByTopicCode(String topicCode) {
        if (topicCode == null || topicCode.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from IgTopic it where it.topicCode = :topicCode", IgTopic.class)
                    .setParameter("topicCode", topicCode.trim())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public IgTopic saveOrUpdate(IgTopic topic) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            IgTopic merged = (IgTopic) session.merge(topic);
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
