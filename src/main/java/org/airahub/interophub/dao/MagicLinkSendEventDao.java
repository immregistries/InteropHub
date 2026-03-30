package org.airahub.interophub.dao;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.MagicLinkSendEvent;

public class MagicLinkSendEventDao {
    private static final Logger LOGGER = Logger.getLogger(MagicLinkSendEventDao.class.getName());

    public MagicLinkSendEvent log(MagicLinkSendEvent event) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(event);
            tx.commit();
            return event;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            LOGGER.log(Level.SEVERE,
                    "Failed to save magic link send event for userId=" + event.getUserId()
                            + " magicId=" + event.getMagicId() + " type=" + event.getEventType(),
                    ex);
            return event;
        }
    }

    public List<MagicLinkSendEvent> findRecentByUserId(Long userId, int limit) {
        int resolvedLimit = Math.max(1, limit);
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from MagicLinkSendEvent e where e.userId = :userId order by e.eventAt desc, e.sendEventId desc",
                    MagicLinkSendEvent.class)
                    .setParameter("userId", userId)
                    .setMaxResults(resolvedLimit)
                    .getResultList();
        }
    }
}
