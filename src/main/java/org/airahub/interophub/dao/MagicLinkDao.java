package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.MagicLink;

public class MagicLinkDao extends GenericDao<MagicLink, Long> {
    public MagicLinkDao() {
        super(MagicLink.class);
    }

    public Optional<MagicLink> findValidUnconsumedByTokenHash(byte[] tokenHash) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from MagicLink ml where ml.tokenHash = :tokenHash "
                            + "and ml.consumedAt is null and ml.expiresAt > :now "
                            + "order by ml.issuedAt desc",
                    MagicLink.class)
                    .setParameter("tokenHash", tokenHash)
                    .setParameter("now", LocalDateTime.now())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public void markConsumed(Long magicId) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.createMutationQuery(
                    "update MagicLink ml set ml.consumedAt = :consumedAt "
                            + "where ml.magicId = :magicId and ml.consumedAt is null")
                    .setParameter("consumedAt", LocalDateTime.now())
                    .setParameter("magicId", magicId)
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
