package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicSupporter;
import org.airahub.interophub.model.Supporter;
import org.hibernate.Transaction;

public class EsTopicSupporterDao extends GenericDao<EsTopicSupporter, Long> {

    public EsTopicSupporterDao() {
        super(EsTopicSupporter.class);
    }

    /** All relationships for a Topic, including ones pointing at an inactive Supporter - for admin management. */
    public List<EsTopicSupporter> findByTopicId(Long topicId) {
        if (topicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicSupporter ts where ts.esTopicId = :topicId order by ts.esTopicSupporterId asc",
                    EsTopicSupporter.class)
                    .setParameter("topicId", topicId)
                    .getResultList();
        }
    }

    /** Active Supporters of a Topic, sorted by short name - for public chip/section display. */
    public List<Supporter> findActiveSupportersByTopicId(Long topicId) {
        if (topicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select s from EsTopicSupporter ts, Supporter s"
                            + " where ts.esTopicId = :topicId and ts.supporterId = s.supporterId"
                            + " and s.active = true"
                            + " order by lower(s.shortName) asc, s.supporterId asc",
                    Supporter.class)
                    .setParameter("topicId", topicId)
                    .getResultList();
        }
    }

    /** Topic ids a Supporter supports - used to build the public per-supporter topic listing. */
    public List<Long> findTopicIdsBySupporterId(Long supporterId) {
        if (supporterId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select ts.esTopicId from EsTopicSupporter ts where ts.supporterId = :supporterId",
                    Long.class)
                    .setParameter("supporterId", supporterId)
                    .getResultList();
        }
    }

    public Optional<EsTopicSupporter> findByTopicAndSupporter(Long topicId, Long supporterId) {
        if (topicId == null || supporterId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicSupporter ts where ts.esTopicId = :topicId and ts.supporterId = :supporterId",
                    EsTopicSupporter.class)
                    .setParameter("topicId", topicId)
                    .setParameter("supporterId", supporterId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    /** Creates the relationship unless it already exists; a pre-existing relationship is left untouched. */
    public void add(Long topicId, Long supporterId) {
        if (topicId == null || supporterId == null || findByTopicAndSupporter(topicId, supporterId).isPresent()) {
            return;
        }
        EsTopicSupporter row = new EsTopicSupporter();
        row.setEsTopicId(topicId);
        row.setSupporterId(supporterId);
        Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(row);
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            // A unique-constraint violation here means the relationship already exists -
            // not an error for the caller (mirrors EsTopicRelationshipServlet's handling).
        }
    }

    public void delete(Long esTopicSupporterId) {
        deleteById(esTopicSupporterId);
    }
}
