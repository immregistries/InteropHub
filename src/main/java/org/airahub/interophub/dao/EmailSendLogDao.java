package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
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

    /**
     * Inserts a partial log entry before the email is sent. The bodyText,
     * smtpMessageId, and smtpProvider fields are left null and must be filled in
     * via {@link #updateAfterSend} once the send completes.
     *
     * @return the generated emailLogId of the inserted row
     */
    public Long preInsert(String emailReason, String recipientEmail, String recipientEmailNormalized,
            Long userId, String subject, Long esMeetingCommunicationId) {
        EmailSendLog entry = new EmailSendLog();
        entry.setEmailReason(emailReason);
        entry.setRecipientEmail(recipientEmail);
        entry.setRecipientEmailNormalized(recipientEmailNormalized);
        entry.setUserId(userId);
        entry.setSubject(subject);
        entry.setEsMeetingCommunicationId(esMeetingCommunicationId);
        log(entry);
        return entry.getEmailLogId();
    }

    /**
     * Updates a previously pre-inserted log entry with the final body text and
     * SMTP delivery details.
     */
    public void updateAfterSend(Long emailLogId, String bodyText, String smtpMessageId, String smtpProvider) {
        if (emailLogId == null) {
            return;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.createMutationQuery(
                    "update EmailSendLog e set e.bodyText = :body,"
                            + " e.smtpMessageId = :msgId, e.smtpProvider = :provider"
                            + " where e.emailLogId = :id")
                    .setParameter("body", bodyText)
                    .setParameter("msgId", smtpMessageId)
                    .setParameter("provider", smtpProvider)
                    .setParameter("id", emailLogId)
                    .executeUpdate();
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    LOGGER.log(Level.WARNING, "Rollback failed updating email log id=" + emailLogId, rollbackEx);
                }
            }
            LOGGER.log(Level.WARNING, "Failed to update email log after send for id=" + emailLogId, ex);
        }
    }

    /**
     * Returns a log entry by its primary key. Used by the unsubscribe servlet to
     * verify that the log_id in the unsubscribe URL matches the claimed email.
     */
    public Optional<EmailSendLog> findById(Long emailLogId) {
        if (emailLogId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return Optional.ofNullable(session.find(EmailSendLog.class, emailLogId));
        }
    }
}
