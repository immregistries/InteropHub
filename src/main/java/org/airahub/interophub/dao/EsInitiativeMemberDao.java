package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsInitiativeMember;

public class EsInitiativeMemberDao extends GenericDao<EsInitiativeMember, Long> {

    public EsInitiativeMemberDao() {
        super(EsInitiativeMember.class);
    }

    public List<EsInitiativeMember> findByInitiativeId(Long esInitiativeId) {
        if (esInitiativeId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiativeMember m where m.esInitiativeId = :id"
                            + " order by lower(coalesce(m.contactLastName, '')),"
                            + " lower(coalesce(m.contactFirstName, '')), m.esInitiativeMemberId",
                    EsInitiativeMember.class)
                    .setParameter("id", esInitiativeId)
                    .getResultList();
        }
    }

    public Optional<EsInitiativeMember> findByInitiativeAndEmail(Long esInitiativeId, String emailNormalized) {
        if (esInitiativeId == null || emailNormalized == null || emailNormalized.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiativeMember m where m.esInitiativeId = :id and m.emailNormalized = :email",
                    EsInitiativeMember.class)
                    .setParameter("id", esInitiativeId)
                    .setParameter("email", emailNormalized)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public int updateUserIdWhereNullByEmailNormalized(String emailNormalized, Long userId) {
        if (emailNormalized == null || userId == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                int updated = session.createMutationQuery(
                        "update EsInitiativeMember m set m.userId = :uid"
                                + " where m.emailNormalized = :email and m.userId is null")
                        .setParameter("uid", userId)
                        .setParameter("email", emailNormalized)
                        .executeUpdate();
                tx.commit();
                return updated;
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }
}
