package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.AppApiSecret;

public class AppApiSecretDao extends GenericDao<AppApiSecret, Long> {
    public AppApiSecretDao() {
        super(AppApiSecret.class);
    }

    public List<AppApiSecret> findByApiId(Long apiId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from AppApiSecret aas where aas.apiId = :apiId order by aas.secretId asc",
                    AppApiSecret.class)
                    .setParameter("apiId", apiId)
                    .getResultList();
        }
    }

    public List<AppApiSecret> findByUserId(Long userId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from AppApiSecret aas where aas.userId = :userId order by aas.updatedAt desc, aas.secretId desc",
                    AppApiSecret.class)
                    .setParameter("userId", userId)
                    .getResultList();
        }
    }

    public List<AppApiSecret> findByUserIdAndAppId(Long userId, Long appId) {
        if (userId == null || appId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from AppApiSecret aas "
                            + "where aas.userId = :userId "
                            + "and aas.apiId in (select aa.apiId from AppApi aa where aa.appId = :appId) "
                            + "order by aas.updatedAt desc, aas.secretId desc",
                    AppApiSecret.class)
                    .setParameter("userId", userId)
                    .setParameter("appId", appId)
                    .getResultList();
        }
    }

    public Optional<AppApiSecret> findByApiIdAndUserId(Long apiId, Long userId) {
        if (apiId == null || userId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from AppApiSecret aas where aas.apiId = :apiId and aas.userId = :userId",
                    AppApiSecret.class)
                    .setParameter("apiId", apiId)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public AppApiSecret saveOrUpdate(AppApiSecret appApiSecret) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            AppApiSecret merged = (AppApiSecret) session.merge(appApiSecret);
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