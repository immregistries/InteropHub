package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.Optional;
import org.airahub.interophub.model.AuthLoginCode;

public class AuthLoginCodeDao extends GenericDao<AuthLoginCode, Long> {
    public AuthLoginCodeDao() {
        super(AuthLoginCode.class);
    }

    public Optional<AuthLoginCode> findValidByCodeHash(byte[] codeHash) {
        try (org.hibernate.Session session = org.airahub.interophub.config.HibernateUtil.getSessionFactory()
                .openSession()) {
            return session.createQuery(
                    "from AuthLoginCode alc where alc.codeHash = :codeHash and alc.consumedAt is null and alc.expiresAt > :now order by alc.issuedAt desc",
                    AuthLoginCode.class)
                    .setParameter("codeHash", codeHash)
                    .setParameter("now", LocalDateTime.now())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public boolean markConsumedIfValid(Long loginCodeId) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = org.airahub.interophub.config.HibernateUtil.getSessionFactory()
                .openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update AuthLoginCode alc set alc.consumedAt = :consumedAt where alc.loginCodeId = :loginCodeId and alc.consumedAt is null and alc.expiresAt > :now")
                    .setParameter("consumedAt", LocalDateTime.now())
                    .setParameter("loginCodeId", loginCodeId)
                    .setParameter("now", LocalDateTime.now())
                    .executeUpdate();
            tx.commit();
            return updated == 1;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }
}
