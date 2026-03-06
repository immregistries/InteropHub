package org.airahub.interophub.dao;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.config.HibernateUtil;
import org.hibernate.Transaction;

public class GenericDao<T, ID extends Serializable> {
    private static final Logger LOGGER = Logger.getLogger(GenericDao.class.getName());

    private final Class<T> entityClass;

    public GenericDao(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public T save(T entity) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(entity);
            tx.commit();
            return entity;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            LOGGER.log(Level.SEVERE, "Failed to save entity: " + entityClass.getSimpleName(), ex);
            throw ex;
        }
    }

    public Optional<T> findById(ID id) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            T entity = session.get(entityClass, id);
            return Optional.ofNullable(entity);
        }
    }

    public List<T> findAll() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            var cb = session.getCriteriaBuilder();
            var cq = cb.createQuery(entityClass);
            var root = cq.from(entityClass);
            cq.select(root);
            return session.createQuery(cq).getResultList();
        }
    }

    public void deleteById(ID id) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            T entity = session.get(entityClass, id);
            if (entity == null) {
                return;
            }
            tx = session.beginTransaction();
            session.remove(entity);
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            LOGGER.log(Level.SEVERE, "Failed to delete entity: " + entityClass.getSimpleName(), ex);
            throw ex;
        }
    }
}
