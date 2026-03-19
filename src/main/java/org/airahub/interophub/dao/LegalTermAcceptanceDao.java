package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.LegalTermAcceptance;
import org.hibernate.Transaction;

public class LegalTermAcceptanceDao extends GenericDao<LegalTermAcceptance, Long> {
    public LegalTermAcceptanceDao() {
        super(LegalTermAcceptance.class);
    }

    public LegalTermAcceptance saveOrUpdate(LegalTermAcceptance acceptance) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            LegalTermAcceptance merged = (LegalTermAcceptance) session.merge(acceptance);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public Optional<LegalTermAcceptance> findByTermUserWorkspace(Long termId, Long userId, Long workspaceId) {
        if (termId == null || userId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = workspaceId == null
                    ? "from LegalTermAcceptance lta where lta.termId = :termId and lta.userId = :userId and lta.workspaceId is null"
                    : "from LegalTermAcceptance lta where lta.termId = :termId and lta.userId = :userId and lta.workspaceId = :workspaceId";
            var query = session.createQuery(hql, LegalTermAcceptance.class)
                    .setParameter("termId", termId)
                    .setParameter("userId", userId)
                    .setMaxResults(1);
            if (workspaceId != null) {
                query.setParameter("workspaceId", workspaceId);
            }
            return query.uniqueResultOptional();
        }
    }

    public List<LegalTermAcceptance> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from LegalTermAcceptance lta where lta.userId = :userId order by lta.acceptedAt desc",
                    LegalTermAcceptance.class)
                    .setParameter("userId", userId)
                    .getResultList();
        }
    }
}
