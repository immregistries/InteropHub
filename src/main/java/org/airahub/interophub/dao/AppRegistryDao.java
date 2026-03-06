package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.AppRegistry;

public class AppRegistryDao extends GenericDao<AppRegistry, Long> {
    public AppRegistryDao() {
        super(AppRegistry.class);
    }

    public List<AppRegistry> findAllOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from AppRegistry ar order by ar.appCode asc", AppRegistry.class)
                    .getResultList();
        }
    }

    public Optional<AppRegistry> findByAppCode(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from AppRegistry ar where ar.appCode = :appCode", AppRegistry.class)
                    .setParameter("appCode", appCode.trim())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public AppRegistry saveOrUpdate(AppRegistry appRegistry) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            AppRegistry merged = (AppRegistry) session.merge(appRegistry);
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
