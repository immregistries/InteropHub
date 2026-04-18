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
