package org.airahub.interophub.dao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicPathDefinition;

public class EsTopicPathDefinitionDao extends GenericDao<EsTopicPathDefinition, Long> {

    public EsTopicPathDefinitionDao() {
        super(EsTopicPathDefinition.class);
    }

    public Optional<EsTopicPathDefinition> findByNameInSpace(String name, Long esTopicSpaceId) {
        if (name == null || name.isBlank() || esTopicSpaceId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicPathDefinition d where d.esTopicSpaceId = :spaceId and lower(d.pathName) = :name",
                    EsTopicPathDefinition.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("name", name.trim().toLowerCase())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Optional<EsTopicPathDefinition> findByNameInSpaceExcludingId(String name, Long esTopicSpaceId,
            Long excludeId) {
        if (name == null || name.isBlank() || esTopicSpaceId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicPathDefinition d where d.esTopicSpaceId = :spaceId and lower(d.pathName) = :name"
                            + " and (:excludeId is null or d.esTopicPathDefinitionId <> :excludeId)",
                    EsTopicPathDefinition.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("name", name.trim().toLowerCase())
                    .setParameter("excludeId", excludeId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsTopicPathDefinition> findAllOrderedBySpaceId(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicPathDefinition d"
                            + " where d.esTopicSpaceId = :spaceId"
                            + " order by d.displayOrder asc, lower(d.pathName) asc, d.esTopicPathDefinitionId asc",
                    EsTopicPathDefinition.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .getResultList();
        }
    }

    public Map<Long, Long> findTopicUsageCountsByDefinitionId() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select t.esTopicPathDefinitionId, count(t.esTopicId)"
                            + " from EsTopic t"
                            + " where t.esTopicPathDefinitionId is not null"
                            + " group by t.esTopicPathDefinitionId",
                    Object[].class)
                    .getResultList();
            Map<Long, Long> counts = new LinkedHashMap<>();
            for (Object[] row : rows) {
                counts.put((Long) row[0], (Long) row[1]);
            }
            return counts;
        }
    }

    public EsTopicPathDefinition saveOrUpdate(EsTopicPathDefinition definition) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopicPathDefinition merged = (EsTopicPathDefinition) session.merge(definition);
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
