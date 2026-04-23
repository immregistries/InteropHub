package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicMeetingMember;

public class EsTopicMeetingMemberDao extends GenericDao<EsTopicMeetingMember, Long> {

    public EsTopicMeetingMemberDao() {
        super(EsTopicMeetingMember.class);
    }

    public List<EsTopicMeetingMember> findByMeetingId(Long esTopicMeetingId) {
        if (esTopicMeetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeetingMember m where m.esTopicMeetingId = :meetingId order by m.createdAt asc",
                    EsTopicMeetingMember.class)
                    .setParameter("meetingId", esTopicMeetingId)
                    .getResultList();
        }
    }

    public Optional<EsTopicMeetingMember> findByMeetingIdAndEmailNormalized(Long esTopicMeetingId,
            String emailNormalized) {
        if (esTopicMeetingId == null || emailNormalized == null || emailNormalized.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeetingMember m where m.esTopicMeetingId = :meetingId and m.emailNormalized = :email",
                    EsTopicMeetingMember.class)
                    .setParameter("meetingId", esTopicMeetingId)
                    .setParameter("email", emailNormalized.trim())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsTopicMeetingMember> findByMeetingIdsAndEmailNormalized(List<Long> esTopicMeetingIds,
            String emailNormalized) {
        if (esTopicMeetingIds == null || esTopicMeetingIds.isEmpty() || emailNormalized == null
                || emailNormalized.isBlank()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeetingMember m where m.esTopicMeetingId in :meetingIds"
                            + " and m.emailNormalized = :email",
                    EsTopicMeetingMember.class)
                    .setParameterList("meetingIds", esTopicMeetingIds)
                    .setParameter("email", emailNormalized.trim())
                    .getResultList();
        }
    }

    public Optional<EsTopicMeetingMember> findByMeetingIdAndUserOrEmail(Long esTopicMeetingId, Long userId,
            String emailNormalized) {
        if (esTopicMeetingId == null) {
            return Optional.empty();
        }
        boolean hasUser = userId != null;
        boolean hasEmail = emailNormalized != null && !emailNormalized.isBlank();
        if (!hasUser && !hasEmail) {
            return Optional.empty();
        }

        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder();
            hql.append("from EsTopicMeetingMember m where m.esTopicMeetingId = :meetingId and (");
            if (hasUser) {
                hql.append("m.userId = :userId");
            }
            if (hasEmail) {
                if (hasUser) {
                    hql.append(" or ");
                }
                hql.append("m.emailNormalized = :email");
            }
            hql.append(") order by m.updatedAt desc, m.createdAt desc");

            var query = session.createQuery(hql.toString(), EsTopicMeetingMember.class)
                    .setParameter("meetingId", esTopicMeetingId)
                    .setMaxResults(1);
            if (hasUser) {
                query.setParameter("userId", userId);
            }
            if (hasEmail) {
                query.setParameter("email", emailNormalized.trim());
            }
            return query.uniqueResultOptional();
        }
    }

    public List<EsTopicMeetingMember> findByMeetingIdsAndUserOrEmail(List<Long> esTopicMeetingIds, Long userId,
            String emailNormalized) {
        if (esTopicMeetingIds == null || esTopicMeetingIds.isEmpty()) {
            return List.of();
        }
        boolean hasUser = userId != null;
        boolean hasEmail = emailNormalized != null && !emailNormalized.isBlank();
        if (!hasUser && !hasEmail) {
            return List.of();
        }

        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder();
            hql.append("from EsTopicMeetingMember m where m.esTopicMeetingId in :meetingIds and (");
            if (hasUser) {
                hql.append("m.userId = :userId");
            }
            if (hasEmail) {
                if (hasUser) {
                    hql.append(" or ");
                }
                hql.append("m.emailNormalized = :email");
            }
            hql.append(")");

            var query = session.createQuery(hql.toString(), EsTopicMeetingMember.class)
                    .setParameterList("meetingIds", esTopicMeetingIds);
            if (hasUser) {
                query.setParameter("userId", userId);
            }
            if (hasEmail) {
                query.setParameter("email", emailNormalized.trim());
            }
            return query.getResultList();
        }
    }

    public List<EsTopicMeetingMember> findByTopicIdAndUserOrEmail(Long esTopicId, Long userId,
            String emailNormalized) {
        if (esTopicId == null) {
            return List.of();
        }
        boolean hasUser = userId != null;
        boolean hasEmail = emailNormalized != null && !emailNormalized.isBlank();
        if (!hasUser && !hasEmail) {
            return List.of();
        }

        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder();
            hql.append("from EsTopicMeetingMember m where m.esTopicMeetingId in (");
            hql.append("select tm.esTopicMeetingId from EsTopicMeeting tm where tm.esTopicId = :topicId");
            hql.append(") and (");
            if (hasUser) {
                hql.append("m.userId = :userId");
            }
            if (hasEmail) {
                if (hasUser) {
                    hql.append(" or ");
                }
                hql.append("m.emailNormalized = :email");
            }
            hql.append(")");

            var query = session.createQuery(hql.toString(), EsTopicMeetingMember.class)
                    .setParameter("topicId", esTopicId);
            if (hasUser) {
                query.setParameter("userId", userId);
            }
            if (hasEmail) {
                query.setParameter("email", emailNormalized.trim());
            }
            return query.getResultList();
        }
    }

    /**
     * Sets user_id on all es_topic_meeting_member rows that match the given email
     * and currently have user_id IS NULL. Safe to call repeatedly (idempotent).
     *
     * @return the number of rows updated
     */
    public int updateUserIdWhereNullByEmailNormalized(String emailNormalized, Long userId) {
        if (emailNormalized == null || userId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update EsTopicMeetingMember m set m.userId = :uid"
                            + " where m.emailNormalized = :email and m.userId is null")
                    .setParameter("uid", userId)
                    .setParameter("email", emailNormalized)
                    .executeUpdate();
            tx.commit();
            return updated;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public EsTopicMeetingMember saveOrUpdate(EsTopicMeetingMember member) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicMeetingMember merged = (EsTopicMeetingMember) session.merge(member);
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
