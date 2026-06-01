package org.airahub.interophub.dao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicNeighborhood;

public class EsTopicNeighborhoodDao extends GenericDao<EsTopicNeighborhood, Long> {

    public EsTopicNeighborhoodDao() {
        super(EsTopicNeighborhood.class);
    }

    public Set<Long> findNeighborhoodIdsByTopicId(Long topicId) {
        if (topicId == null) {
            return Set.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Long> ids = session.createQuery(
                    "select tn.esNeighborhoodId"
                            + " from EsTopicNeighborhood tn"
                            + " where tn.esTopicId = :topicId"
                            + " order by tn.esNeighborhoodId asc",
                    Long.class)
                    .setParameter("topicId", topicId)
                    .getResultList();
            return new LinkedHashSet<>(ids);
        }
    }

    public List<String> findNeighborhoodNamesByTopicId(Long topicId) {
        if (topicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select n.neighborhoodName"
                            + " from EsTopicNeighborhood tn, EsNeighborhood n"
                            + " where tn.esTopicId = :topicId"
                            + " and tn.esNeighborhoodId = n.esNeighborhoodId"
                            + " and n.isActive = true"
                            + " order by n.displayOrder asc, lower(n.neighborhoodName) asc, n.esNeighborhoodId asc",
                    String.class)
                    .setParameter("topicId", topicId)
                    .getResultList();
        }
    }

    public Map<Long, List<String>> findNeighborhoodNamesByTopicIds(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> namesByTopicId = new LinkedHashMap<>();
        for (Long topicId : topicIds) {
            if (topicId != null) {
                namesByTopicId.put(topicId, new ArrayList<>());
            }
        }

        if (namesByTopicId.isEmpty()) {
            return Map.of();
        }

        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select tn.esTopicId, n.neighborhoodName"
                            + " from EsTopicNeighborhood tn, EsNeighborhood n"
                            + " where tn.esTopicId in (:topicIds)"
                            + " and tn.esNeighborhoodId = n.esNeighborhoodId"
                            + " and n.isActive = true"
                            + " order by tn.esTopicId asc, n.displayOrder asc, lower(n.neighborhoodName) asc, n.esNeighborhoodId asc",
                    Object[].class)
                    .setParameterList("topicIds", new ArrayList<>(namesByTopicId.keySet()))
                    .getResultList();

            for (Object[] row : rows) {
                Long topicId = (Long) row[0];
                String name = (String) row[1];
                List<String> names = namesByTopicId.get(topicId);
                if (names != null && name != null) {
                    names.add(name);
                }
            }
        }

        return namesByTopicId;
    }

    public Map<Long, Long> findActiveTopicCountsByNeighborhoodId() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select tn.esNeighborhoodId, count(distinct tn.esTopicId)"
                            + " from EsTopicNeighborhood tn, EsTopic t"
                            + " where tn.esTopicId = t.esTopicId"
                            + " and t.status = :activeStatus"
                            + " group by tn.esNeighborhoodId",
                    Object[].class)
                    .setParameter("activeStatus", org.airahub.interophub.model.EsTopic.EsTopicStatus.ACTIVE)
                    .getResultList();

            Map<Long, Long> counts = new LinkedHashMap<>();
            for (Object[] row : rows) {
                counts.put((Long) row[0], (Long) row[1]);
            }
            return counts;
        }
    }

    public void replaceTopicNeighborhoods(Long topicId, Set<Long> neighborhoodIds) {
        if (topicId == null) {
            return;
        }

        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            session.createMutationQuery("delete from EsTopicNeighborhood tn where tn.esTopicId = :topicId")
                    .setParameter("topicId", topicId)
                    .executeUpdate();

            if (neighborhoodIds != null) {
                for (Long neighborhoodId : neighborhoodIds) {
                    if (neighborhoodId == null) {
                        continue;
                    }
                    EsTopicNeighborhood row = new EsTopicNeighborhood();
                    row.setEsTopicId(topicId);
                    row.setEsNeighborhoodId(neighborhoodId);
                    session.persist(row);
                }
            }

            tx.commit();
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }
}
