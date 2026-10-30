package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.UserToken;
import org.hibernate.Transaction;

public class UserTokenDao extends GenericDao<UserToken, Long> {
    public UserTokenDao() {
        super(UserToken.class);
    }

    /**
     * A token the bearer filter can accept: hash matches, not revoked, not expired.
     * The caller still checks the app and the user.
     */
    public Optional<UserToken> findActiveByTokenHash(byte[] tokenHash) {
        if (tokenHash == null || tokenHash.length == 0) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from UserToken t where t.tokenHash = :tokenHash "
                            + "and t.revokedAt is null and t.expiresAt > :now",
                    UserToken.class)
                    .setParameter("tokenHash", tokenHash)
                    .setParameter("now", LocalDateTime.now())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<UserToken> findUnrevokedForUserAndApps(Long userId, Collection<Long> appIds) {
        if (userId == null || appIds == null || appIds.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from UserToken t where t.userId = :userId and t.appId in :appIds and t.revokedAt is null "
                            + "order by t.issuedAt desc",
                    UserToken.class)
                    .setParameter("userId", userId)
                    .setParameterList("appIds", appIds)
                    .getResultList();
        }
    }

    /** Updates {@code last_used_at} at most once a minute. */
    public void touchLastUsedAt(Long tokenId) {
        if (tokenId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.createMutationQuery(
                    "update UserToken t set t.lastUsedAt = :now where t.tokenId = :tokenId "
                            + "and (t.lastUsedAt is null or t.lastUsedAt < :threshold)")
                    .setParameter("now", now)
                    .setParameter("tokenId", tokenId)
                    .setParameter("threshold", now.minusMinutes(1))
                    .executeUpdate();
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public boolean revoke(Long tokenId) {
        if (tokenId == null) {
            return false;
        }
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update UserToken t set t.revokedAt = :now where t.tokenId = :tokenId and t.revokedAt is null")
                    .setParameter("now", LocalDateTime.now())
                    .setParameter("tokenId", tokenId)
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
