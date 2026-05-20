package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicCuration;

public class EsTopicCurationDao extends GenericDao<EsTopicCuration, Long> {

    public EsTopicCurationDao() {
        super(EsTopicCuration.class);
    }

    /** All entries in this topic's curated list, ordered by display_order. */
    public List<EsTopicCuration> findByCuratorTopicId(Long curatorTopicId) {
        if (curatorTopicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicCuration c where c.curatorTopicId = :id"
                            + " order by c.displayOrder, c.esTopicCurationId",
                    EsTopicCuration.class)
                    .setParameter("id", curatorTopicId)
                    .getResultList();
        }
    }

    /** Reverse lookup: curated lists that include this topic (backlinks). */
    public List<EsTopicCuration> findByCuratedTopicId(Long curatedTopicId) {
        if (curatedTopicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicCuration c where c.curatedTopicId = :id"
                            + " order by c.curatorTopicId",
                    EsTopicCuration.class)
                    .setParameter("id", curatedTopicId)
                    .getResultList();
        }
    }

    /**
     * Distinct non-blank curation status values already used within a curator's
     * list.
     * Used to populate the status datalist so champions can re-use existing values.
     */
    public List<String> findDistinctCurationStatuses(Long curatorTopicId) {
        if (curatorTopicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select distinct c.curationStatus from EsTopicCuration c"
                            + " where c.curatorTopicId = :id"
                            + " and c.curationStatus is not null and c.curationStatus <> ''",
                    String.class)
                    .setParameter("id", curatorTopicId)
                    .getResultList();
        }
    }

    public EsTopicCuration saveOrUpdate(EsTopicCuration curation) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicCuration merged = (EsTopicCuration) session.merge(curation);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public void delete(Long curationId) {
        deleteById(curationId);
    }
}
