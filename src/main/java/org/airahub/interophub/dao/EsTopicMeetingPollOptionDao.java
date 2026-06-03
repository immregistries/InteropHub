package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicMeetingPollOption;
import org.hibernate.Transaction;

public class EsTopicMeetingPollOptionDao extends GenericDao<EsTopicMeetingPollOption, Long> {

    public EsTopicMeetingPollOptionDao() {
        super(EsTopicMeetingPollOption.class);
    }

    public List<EsTopicMeetingPollOption> findByPollIdOrdered(Long esTopicMeetingPollId) {
        if (esTopicMeetingPollId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicMeetingPollOption o"
                            + " where o.esTopicMeetingPollId = :pollId"
                            + " order by o.displayOrder asc, o.startsAtUtc asc, o.esTopicMeetingPollOptionId asc",
                    EsTopicMeetingPollOption.class)
                    .setParameter("pollId", esTopicMeetingPollId)
                    .getResultList();
        }
    }

    public int maxDisplayOrder(Long esTopicMeetingPollId) {
        if (esTopicMeetingPollId == null) {
            return 0;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Integer max = session.createQuery(
                    "select max(o.displayOrder) from EsTopicMeetingPollOption o"
                            + " where o.esTopicMeetingPollId = :pollId",
                    Integer.class)
                    .setParameter("pollId", esTopicMeetingPollId)
                    .uniqueResult();
            return max == null ? 0 : max;
        }
    }

    public EsTopicMeetingPollOption saveOrUpdate(EsTopicMeetingPollOption option) {
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicMeetingPollOption merged = session.merge(option);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public int deleteByIdAndPollId(Long optionId, Long pollId) {
        if (optionId == null || pollId == null) {
            return 0;
        }
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int deleted = session.createMutationQuery(
                    "delete from EsTopicMeetingPollOption o"
                            + " where o.esTopicMeetingPollOptionId = :optionId"
                            + " and o.esTopicMeetingPollId = :pollId")
                    .setParameter("optionId", optionId)
                    .setParameter("pollId", pollId)
                    .executeUpdate();
            tx.commit();
            return deleted;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }
}
