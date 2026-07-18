package org.airahub.interophub.dao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicStageDefinition;

public class EsTopicStageDefinitionDao extends GenericDao<EsTopicStageDefinition, Long> {

    public EsTopicStageDefinitionDao() {
        super(EsTopicStageDefinition.class);
    }

    public Optional<EsTopicStageDefinition> findByNameInSpace(String name, Long esTopicSpaceId) {
        if (name == null || name.isBlank() || esTopicSpaceId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicStageDefinition d where d.esTopicSpaceId = :spaceId and lower(d.stageName) = :name",
                    EsTopicStageDefinition.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("name", name.trim().toLowerCase())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Optional<EsTopicStageDefinition> findByNameInSpaceExcludingId(String name, Long esTopicSpaceId,
            Long excludeId) {
        if (name == null || name.isBlank() || esTopicSpaceId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicStageDefinition d where d.esTopicSpaceId = :spaceId and lower(d.stageName) = :name"
                            + " and (:excludeId is null or d.esTopicStageDefinitionId <> :excludeId)",
                    EsTopicStageDefinition.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("name", name.trim().toLowerCase())
                    .setParameter("excludeId", excludeId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsTopicStageDefinition> findAllOrderedBySpaceId(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicStageDefinition d"
                            + " where d.esTopicSpaceId = :spaceId"
                            + " order by d.displayOrder asc, lower(d.stageName) asc, d.esTopicStageDefinitionId asc",
                    EsTopicStageDefinition.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .getResultList();
        }
    }

    public Map<Long, Long> findTopicUsageCountsByDefinitionId() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select t.esTopicStageDefinitionId, count(t.esTopicId)"
                            + " from EsTopic t"
                            + " where t.esTopicStageDefinitionId is not null"
                            + " group by t.esTopicStageDefinitionId",
                    Object[].class)
                    .getResultList();
            Map<Long, Long> counts = new LinkedHashMap<>();
            for (Object[] row : rows) {
                counts.put((Long) row[0], (Long) row[1]);
            }
            return counts;
        }
    }

    public EsTopicStageDefinition saveOrUpdate(EsTopicStageDefinition definition) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicStageDefinition merged = (EsTopicStageDefinition) session.merge(definition);
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
