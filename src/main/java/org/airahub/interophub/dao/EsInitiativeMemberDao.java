package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsInitiativeMember;
import org.airahub.interophub.service.PersonNames;

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

    public Optional<EsInitiativeMember> findByInitiativeAndUser(Long esInitiativeId, Long userId) {
        if (esInitiativeId == null || userId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiativeMember m where m.esInitiativeId = :id and m.userId = :userId",
                    EsInitiativeMember.class)
                    .setParameter("id", esInitiativeId)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    /**
     * Links the account and fills blank roster names. An email stored in a name
     * field is treated as blank.
     */
    public int updateUserIdWhereNullByEmailNormalized(String emailNormalized, Long userId, String firstName,
            String lastName) {
        if (emailNormalized == null || userId == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            org.hibernate.Transaction tx = session.beginTransaction();
            try {
                List<EsInitiativeMember> rows = session.createQuery(
                        "from EsInitiativeMember m where m.emailNormalized = :email and m.userId is null",
                        EsInitiativeMember.class)
                        .setParameter("email", emailNormalized)
                        .getResultList();
                for (EsInitiativeMember member : rows) {
                    member.setUserId(userId);
                    PersonNames.Names names = PersonNames.fillBlanks(member.getContactFirstName(),
                            member.getContactLastName(), firstName, lastName);
                    member.setContactFirstName(names.firstName());
                    member.setContactLastName(names.lastName());
                }
                tx.commit();
                return rows.size();
            } catch (Exception ex) {
                tx.rollback();
                throw ex;
            }
        }
    }
}
