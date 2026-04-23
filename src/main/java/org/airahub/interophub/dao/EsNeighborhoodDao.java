package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsNeighborhood;

public class EsNeighborhoodDao extends GenericDao<EsNeighborhood, Long> {

    public EsNeighborhoodDao() {
        super(EsNeighborhood.class);
    }

    public Optional<EsNeighborhood> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsNeighborhood n where lower(n.neighborhoodCode) = :code",
                    EsNeighborhood.class)
                    .setParameter("code", code.trim().toLowerCase())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Optional<EsNeighborhood> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsNeighborhood n where lower(n.neighborhoodName) = :name",
                    EsNeighborhood.class)
                    .setParameter("name", name.trim().toLowerCase())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsNeighborhood> findAllActive() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsNeighborhood n"
                            + " where n.isActive = true"
                            + " order by n.displayOrder asc, lower(n.neighborhoodName) asc, n.esNeighborhoodId asc",
                    EsNeighborhood.class)
                    .getResultList();
        }
    }

    public List<EsNeighborhood> findAllOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsNeighborhood n"
                            + " order by n.displayOrder asc, lower(n.neighborhoodName) asc, n.esNeighborhoodId asc",
                    EsNeighborhood.class)
                    .getResultList();
        }
    }

    public EsNeighborhood saveOrUpdate(EsNeighborhood neighborhood) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsNeighborhood merged = (EsNeighborhood) session.merge(neighborhood);
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
