package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.User;

public class UserDao extends GenericDao<User, Long> {
    public UserDao() {
        super(User.class);
    }

    public Optional<User> findByEmail(String email) {
        String normalized = email == null ? null : email.trim().toLowerCase();
        return findByEmailNormalized(normalized);
    }

    public Optional<User> findByEmailNormalized(String emailNormalized) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from User u where u.emailNormalized = :emailNormalized", User.class)
                    .setParameter("emailNormalized", emailNormalized)
                    .uniqueResultOptional();
        }
    }

    public User saveOrUpdate(User user) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            User merged = (User) session.merge(user);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public List<User> findByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from User u where u.userId in (:userIds)", User.class)
                    .setParameter("userIds", userIds)
                    .getResultList();
        }
    }
}
