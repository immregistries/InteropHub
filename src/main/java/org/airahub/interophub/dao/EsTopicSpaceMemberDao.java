package org.airahub.interophub.dao;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicSpaceMember;
import org.airahub.interophub.model.EsTopicSpaceMember.MemberStatus;

/**
 * Membership rows are never deleted by the app: removal sets {@link MemberStatus#REMOVED} so that
 * auto-membership by email domain doesn't re-add the person. Every finder here that feeds access,
 * counts, or notifications returns ACTIVE rows only; {@link #findBySpaceIdAndUserId} deliberately
 * returns a row of any status.
 */
public class EsTopicSpaceMemberDao extends GenericDao<EsTopicSpaceMember, Long> {

    public EsTopicSpaceMemberDao() {
        super(EsTopicSpaceMember.class);
    }

    /** The user's row for this space in any status, including REMOVED. */
    public Optional<EsTopicSpaceMember> findBySpaceIdAndUserId(Long esTopicSpaceId, Long userId) {
        if (esTopicSpaceId == null || userId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicSpaceMember m"
                            + " where m.esTopicSpaceId = :spaceId and m.userId = :userId",
                    EsTopicSpaceMember.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsTopicSpaceMember> findAllBySpaceId(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicSpaceMember m"
                            + " where m.esTopicSpaceId = :spaceId and m.memberStatus = :status"
                            + " order by case when m.role = :adminRole then 0 else 1 end asc, m.userId asc, m.esTopicSpaceMemberId asc",
                    EsTopicSpaceMember.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("status", MemberStatus.ACTIVE)
                    .setParameter("adminRole", EsTopicSpaceMember.MemberRole.ADMIN)
                    .getResultList();
        }
    }

    /** REMOVED rows for a space, most recently removed first - shown to admins so a removal can be undone. */
    public List<EsTopicSpaceMember> findRemovedBySpaceId(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicSpaceMember m"
                            + " where m.esTopicSpaceId = :spaceId and m.memberStatus = :status"
                            + " order by m.statusChangedAt desc, m.esTopicSpaceMemberId desc",
                    EsTopicSpaceMember.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("status", MemberStatus.REMOVED)
                    .getResultList();
        }
    }

    /** User ids with a row of any status in this space - the set auto-membership must never touch. */
    public Set<Long> findUserIdsWithAnyRowBySpaceId(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return Set.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return new LinkedHashSet<>(session.createQuery(
                    "select m.userId from EsTopicSpaceMember m where m.esTopicSpaceId = :spaceId",
                    Long.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .getResultList());
        }
    }

    public List<EsTopicSpaceMember> findAllByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicSpaceMember m"
                            + " where m.userId = :userId and m.memberStatus = :status"
                            + " order by m.esTopicSpaceId asc, m.esTopicSpaceMemberId asc",
                    EsTopicSpaceMember.class)
                    .setParameter("userId", userId)
                    .setParameter("status", MemberStatus.ACTIVE)
                    .getResultList();
        }
    }

    /**
     * ADMIN members of a Topic Space — used as the notification fallback for
     * topics in that space with no dedicated champion/support contact.
     */
    public List<EsTopicSpaceMember> findAdminsBySpaceId(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicSpaceMember m"
                            + " where m.esTopicSpaceId = :spaceId and m.role = :role and m.memberStatus = :status"
                            + " order by m.userId asc",
                    EsTopicSpaceMember.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("status", MemberStatus.ACTIVE)
                    .setParameter("role", EsTopicSpaceMember.MemberRole.ADMIN)
                    .getResultList();
        }
    }

    public EsTopicSpaceMember saveOrUpdate(EsTopicSpaceMember member) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicSpaceMember merged = (EsTopicSpaceMember) session.merge(member);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }
}