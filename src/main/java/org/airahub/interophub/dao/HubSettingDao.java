package org.airahub.interophub.dao;

import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.HubSetting;

public class HubSettingDao extends GenericDao<HubSetting, Long> {
    public HubSettingDao() {
        super(HubSetting.class);
    }

    public Optional<HubSetting> findActive() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session
                    .createQuery(
                            "from HubSetting hs where hs.active = true order by hs.settingId asc",
                            HubSetting.class)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Optional<HubSetting> findFirst() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session
                    .createQuery("from HubSetting hs order by hs.settingId asc", HubSetting.class)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public HubSetting saveOrUpdate(HubSetting settings) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            HubSetting merged = (HubSetting) session.merge(settings);
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
