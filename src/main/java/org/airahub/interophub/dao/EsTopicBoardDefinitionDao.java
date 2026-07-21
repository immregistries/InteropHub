package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicBoardDefinition;

public class EsTopicBoardDefinitionDao extends GenericDao<EsTopicBoardDefinition, Long> {

    public EsTopicBoardDefinitionDao() {
        super(EsTopicBoardDefinition.class);
    }

    public Optional<EsTopicBoardDefinition> findByBoardCode(String boardCode) {
        if (boardCode == null || boardCode.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicBoardDefinition b where lower(b.boardCode) = :boardCode",
                    EsTopicBoardDefinition.class)
                    .setParameter("boardCode", boardCode.trim().toLowerCase())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsTopicBoardDefinition> findAllOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicBoardDefinition b"
                            + " order by lower(b.boardName) asc, lower(b.boardCode) asc, b.esTopicBoardDefinitionId asc",
                    EsTopicBoardDefinition.class)
                    .getResultList();
        }
    }

    public List<EsTopicBoardDefinition> findAllActiveOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicBoardDefinition b"
                            + " where b.isActive = true"
                            + " order by lower(b.boardName) asc, lower(b.boardCode) asc, b.esTopicBoardDefinitionId asc",
                    EsTopicBoardDefinition.class)
                    .getResultList();
        }
    }

    public EsTopicBoardDefinition saveOrUpdate(EsTopicBoardDefinition definition) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicBoardDefinition merged = (EsTopicBoardDefinition) session.merge(definition);
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
