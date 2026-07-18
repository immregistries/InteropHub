package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicSpace;

public class EsTopicSpaceDao extends GenericDao<EsTopicSpace, Long> {

    public EsTopicSpaceDao() {
        super(EsTopicSpace.class);
    }

    public Optional<EsTopicSpace> findBySpaceCode(String spaceCode) {
        if (spaceCode == null || spaceCode.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicSpace s where lower(s.spaceCode) = :spaceCode",
                    EsTopicSpace.class)
                    .setParameter("spaceCode", spaceCode.trim().toLowerCase())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public boolean isActiveSpaceId(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return false;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(s) from EsTopicSpace s where s.esTopicSpaceId = :spaceId and s.isActive = true",
                    Long.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .getSingleResult();
            return count != null && count > 0;
        }
    }

    public List<EsTopicSpace> findAllActiveOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicSpace s"
                            + " where s.isActive = true"
                            + " order by s.displayOrder asc, lower(s.spaceName) asc, s.esTopicSpaceId asc",
                    EsTopicSpace.class)
                    .getResultList();
        }
    }

    public List<EsTopicSpace> findAllOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicSpace s"
                            + " order by s.displayOrder asc, lower(s.spaceName) asc, s.esTopicSpaceId asc",
                    EsTopicSpace.class)
                    .getResultList();
        }
    }

    public EsTopicSpace saveOrUpdate(EsTopicSpace topicSpace) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicSpace merged = (EsTopicSpace) session.merge(topicSpace);
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