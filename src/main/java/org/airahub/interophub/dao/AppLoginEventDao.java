package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.AppLoginEvent;

public class AppLoginEventDao {
    private static final Logger LOGGER = Logger.getLogger(AppLoginEventDao.class.getName());

    public void log(AppLoginEvent event) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(event);
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            LOGGER.log(Level.SEVERE, "Failed to save app login event for userId=" + event.getUserId()
                    + " appId=" + event.getAppId(), ex);
        }
    }

    public long countByAppAndMonth(Long appId, int year, int month) {
        LocalDateTime start = YearMonth.of(year, month).atDay(1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long result = session.createQuery(
                    "select count(e) from AppLoginEvent e where e.appId = :appId"
                            + " and e.loggedInAt >= :start and e.loggedInAt < :end",
                    Long.class)
                    .setParameter("appId", appId)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .uniqueResult();
            return result == null ? 0L : result;
        }
    }

    public List<AppLoginEvent> findByAppAndMonth(Long appId, int year, int month) {
        LocalDateTime start = YearMonth.of(year, month).atDay(1).atStartOfDay();
        LocalDateTime end = start.plusMonths(1);
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from AppLoginEvent e where e.appId = :appId"
                            + " and e.loggedInAt >= :start and e.loggedInAt < :end"
                            + " order by e.loggedInAt desc",
                    AppLoginEvent.class)
                    .setParameter("appId", appId)
                    .setParameter("start", start)
                    .setParameter("end", end)
                    .getResultList();
        }
    }
}
