package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicSpaceMember;

public class EsTopicSpaceMemberDao extends GenericDao<EsTopicSpaceMember, Long> {

    public EsTopicSpaceMemberDao() {
        super(EsTopicSpaceMember.class);
    }

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
                            + " where m.esTopicSpaceId = :spaceId"
                            + " order by case when m.role = :adminRole then 0 else 1 end asc, m.userId asc, m.esTopicSpaceMemberId asc",
                    EsTopicSpaceMember.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("adminRole", EsTopicSpaceMember.MemberRole.ADMIN)
                    .getResultList();
        }
    }

    public List<EsTopicSpaceMember> findAllByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicSpaceMember m"
                            + " where m.userId = :userId"
                            + " order by m.esTopicSpaceId asc, m.esTopicSpaceMemberId asc",
                    EsTopicSpaceMember.class)
                    .setParameter("userId", userId)
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