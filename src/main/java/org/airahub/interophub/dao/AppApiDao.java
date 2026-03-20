package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.AppApi;

public class AppApiDao extends GenericDao<AppApi, Long> {
    public AppApiDao() {
        super(AppApi.class);
    }

    public List<AppApi> findByAppId(Long appId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from AppApi aa where aa.appId = :appId order by aa.purposeLabel asc, aa.apiId asc",
                    AppApi.class)
                    .setParameter("appId", appId)
                    .getResultList();
        }
    }

    public List<AppApi> findEnabledByAppId(Long appId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from AppApi aa where aa.appId = :appId and aa.enabled = true order by aa.purposeLabel asc, aa.apiId asc",
                    AppApi.class)
                    .setParameter("appId", appId)
                    .getResultList();
        }
    }

    public Optional<AppApi> findByAppIdAndApiCode(Long appId, String apiCode) {
        if (appId == null || apiCode == null || apiCode.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from AppApi aa where aa.appId = :appId and aa.apiCode = :apiCode",
                    AppApi.class)
                    .setParameter("appId", appId)
                    .setParameter("apiCode", apiCode.trim())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public AppApi saveOrUpdate(AppApi appApi) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            AppApi merged = (AppApi) session.merge(appApi);
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