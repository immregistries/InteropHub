package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.Session;

public class SessionDao extends GenericDao<Session, Long> {
    public SessionDao() {
        super(Session.class);
    }

    public Optional<Session> findValidByTokenHash(byte[] tokenHash) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from Session s where s.sessionTokenHash = :tokenHash "
                            + "and s.revokedAt is null and s.expiresAt > :now "
                            + "order by s.issuedAt desc",
                    Session.class)
                    .setParameter("tokenHash", tokenHash)
                    .setParameter("now", LocalDateTime.now())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public void revokeByTokenHash(byte[] tokenHash) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.createMutationQuery(
                    "update Session s set s.revokedAt = :revokedAt "
                            + "where s.sessionTokenHash = :tokenHash and s.revokedAt is null")
                    .setParameter("revokedAt", LocalDateTime.now())
                    .setParameter("tokenHash", tokenHash)
                    .executeUpdate();
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }
}
