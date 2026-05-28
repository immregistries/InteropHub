package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.User;
import org.airahub.interophub.service.DandelionSyncService;

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
            new DandelionSyncService().enqueueContactUpsert(merged.getUserId());
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

    public List<User> findRecentRegistrations(int limit) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from User u order by u.createdAt desc", User.class)
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    public List<User> searchUsers(String query) {
        String pattern = "%" + query.trim().toLowerCase() + "%";
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from User u where lower(u.email) like :p or lower(u.firstName) like :p or lower(u.lastName) like :p or lower(u.organization) like :p order by u.createdAt desc",
                    User.class)
                    .setParameter("p", pattern)
                    .getResultList();
        }
    }

    public long countActiveUsers() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select count(u) from User u where u.status = 'ACTIVE'", Long.class)
                    .getSingleResult();
        }
    }

    public long countRecentLogins() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select count(u) from User u where u.status = 'ACTIVE' and u.lastLoginAt >= :since", Long.class)
                    .setParameter("since", java.time.LocalDateTime.now().minusDays(30))
                    .getSingleResult();
        }
    }

    public List<User> findRecentLogins(int limit) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from User u where u.status = 'ACTIVE' and u.lastLoginAt >= :since order by u.lastLoginAt desc",
                    User.class)
                    .setParameter("since", java.time.LocalDateTime.now().minusDays(30))
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    public List<User> searchRecentLogins(String query) {
        String pattern = "%" + query.trim().toLowerCase() + "%";
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from User u where u.status = 'ACTIVE' and u.lastLoginAt >= :since" +
                            " and (lower(u.email) like :p or lower(u.firstName) like :p or lower(u.lastName) like :p or lower(u.organization) like :p)"
                            +
                            " order by u.lastLoginAt desc",
                    User.class)
                    .setParameter("since", java.time.LocalDateTime.now().minusDays(30))
                    .setParameter("p", pattern)
                    .getResultList();
        }
    }

    public List<User> findAllOrderByName() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from User u order by lower(coalesce(u.firstName, '')), lower(coalesce(u.lastName, '')), lower(u.email)",
                    User.class)
                    .getResultList();
        }
    }

    public List<User> findAllNonDeletedWithEmail() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from User u where u.status <> 'DELETED' and u.emailNormalized is not null",
                    User.class)
                    .getResultList();
        }
    }
}
