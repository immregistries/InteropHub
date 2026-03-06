package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.AppRedirectAllowlist;

public class AppRedirectAllowlistDao extends GenericDao<AppRedirectAllowlist, Long> {
    public AppRedirectAllowlistDao() {
        super(AppRedirectAllowlist.class);
    }

    public List<AppRedirectAllowlist> findByAppId(Long appId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from AppRedirectAllowlist ar where ar.appId = :appId order by ar.allowId asc",
                    AppRedirectAllowlist.class)
                    .setParameter("appId", appId)
                    .getResultList();
        }
    }

    public List<AppRedirectAllowlist> findEnabledByAppId(Long appId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from AppRedirectAllowlist ar where ar.appId = :appId and ar.enabled = true order by ar.allowId asc",
                    AppRedirectAllowlist.class)
                    .setParameter("appId", appId)
                    .getResultList();
        }
    }

    public AppRedirectAllowlist saveOrUpdate(AppRedirectAllowlist allowlistEntry) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            AppRedirectAllowlist merged = (AppRedirectAllowlist) session.merge(allowlistEntry);
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
