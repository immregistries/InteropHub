package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.LegalTerm;
import org.hibernate.Transaction;

public class LegalTermDao extends GenericDao<LegalTerm, Long> {
    public LegalTermDao() {
        super(LegalTerm.class);
    }

    public LegalTerm saveOrUpdate(LegalTerm legalTerm) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            LegalTerm merged = (LegalTerm) session.merge(legalTerm);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public List<LegalTerm> findActiveForScope(LegalTerm.ScopeType scopeType) {
        if (scopeType == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from LegalTerm lt "
                            + "where lt.active = true "
                            + "and lt.effectiveAt <= :now "
                            + "and (lt.retiredAt is null or lt.retiredAt > :now) "
                            + "and (lt.scopeType = :scopeType or lt.scopeType = :bothScope) "
                            + "order by lt.displayOrder asc, lt.termCode asc, lt.versionNum desc",
                    LegalTerm.class)
                    .setParameter("now", now)
                    .setParameter("scopeType", scopeType)
                    .setParameter("bothScope", LegalTerm.ScopeType.BOTH)
                    .getResultList();
        }
    }
}
