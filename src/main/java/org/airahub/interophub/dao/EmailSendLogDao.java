package org.airahub.interophub.dao;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EmailSendLog;

public class EmailSendLogDao {
    private static final Logger LOGGER = Logger.getLogger(EmailSendLogDao.class.getName());

    public EmailSendLog log(EmailSendLog entry) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(entry);
            tx.commit();
            return entry;
        } catch (Exception ex) {
            if (tx != null) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    LOGGER.log(Level.WARNING,
                            "Rollback failed while saving email send log for userId=" + entry.getUserId()
                                    + " reason=" + entry.getEmailReason(),
                            rollbackEx);
                }
            }
            LOGGER.log(Level.SEVERE,
                    "Failed to save email send log for userId=" + entry.getUserId()
                            + " reason=" + entry.getEmailReason(),
                    ex);
            return entry;
        }
    }

    public List<EmailSendLog> findRecentSent(int limit) {
        int resolvedLimit = Math.max(1, limit);
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EmailSendLog e order by e.sentAt desc, e.emailLogId desc",
                    EmailSendLog.class)
                    .setMaxResults(resolvedLimit)
                    .getResultList();
        }
    }

    public List<EmailSendLog> findByEmailNormalized(String emailNormalized, int limit) {
        int resolvedLimit = Math.max(1, limit);
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EmailSendLog e where e.recipientEmailNormalized = :email"
                            + " order by e.sentAt desc, e.emailLogId desc",
                    EmailSendLog.class)
                    .setParameter("email", emailNormalized)
                    .setMaxResults(resolvedLimit)
                    .getResultList();
        }
    }

    public List<EmailSendLog> findByUserId(Long userId, int limit) {
        int resolvedLimit = Math.max(1, limit);
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EmailSendLog e where e.userId = :userId"
                            + " order by e.sentAt desc, e.emailLogId desc",
                    EmailSendLog.class)
                    .setParameter("userId", userId)
                    .setMaxResults(resolvedLimit)
                    .getResultList();
        }
    }
}
