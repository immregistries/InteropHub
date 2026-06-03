package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicMeetingPoll;
import org.hibernate.Transaction;

public class EsTopicMeetingPollDao extends GenericDao<EsTopicMeetingPoll, Long> {

    public EsTopicMeetingPollDao() {
        super(EsTopicMeetingPoll.class);
    }

    public List<EsTopicMeetingPoll> findByTopicMeetingId(Long esTopicMeetingId) {
        if (esTopicMeetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeetingPoll p"
                            + " where p.esTopicMeetingId = :meetingId"
                            + " order by p.updatedAt desc, p.createdAt desc",
                    EsTopicMeetingPoll.class)
                    .setParameter("meetingId", esTopicMeetingId)
                    .getResultList();
        }
    }

    public EsTopicMeetingPoll saveOrUpdate(EsTopicMeetingPoll poll) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicMeetingPoll merged = session.merge(poll);
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
