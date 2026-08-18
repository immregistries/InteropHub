package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.Supporter;
import org.hibernate.Transaction;

public class SupporterDao extends GenericDao<Supporter, Long> {

    public SupporterDao() {
        super(Supporter.class);
    }

    public List<Supporter> findAllOrderByShortName() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from Supporter s order by lower(s.shortName) asc, s.supporterId asc", Supporter.class)
                    .getResultList();
        }
    }

    public List<Supporter> findAllActiveOrderByShortName() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from Supporter s where s.active = true"
                            + " order by lower(s.shortName) asc, s.supporterId asc",
                    Supporter.class)
                    .getResultList();
        }
    }

    /** Active Supporters not already associated with the given Topic - candidates for a new relationship. */
    public List<Supporter> findActiveExcludingTopic(Long topicId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "from Supporter s where s.active = true";
            if (topicId != null) {
                hql += " and s.supporterId not in"
                        + " (select ts.supporterId from EsTopicSupporter ts where ts.esTopicId = :topicId)";
            }
            hql += " order by lower(s.shortName) asc, s.supporterId asc";
            var query = session.createQuery(hql, Supporter.class);
            if (topicId != null) {
                query.setParameter("topicId", topicId);
            }
            return query.getResultList();
        }
    }

    public List<Supporter> findByIds(List<Long> supporterIds) {
        if (supporterIds == null || supporterIds.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from Supporter s where s.supporterId in (:ids)", Supporter.class)
                    .setParameter("ids", supporterIds)
                    .getResultList();
        }
    }

    public Optional<Supporter> findByFullNameIgnoreCase(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from Supporter s where lower(s.fullName) = :name", Supporter.class)
                    .setParameter("name", fullName.trim().toLowerCase())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Supporter saveOrUpdate(Supporter supporter) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            Supporter merged = session.merge(supporter);
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
