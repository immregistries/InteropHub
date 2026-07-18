package org.airahub.interophub.dao;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopic;
import org.airahub.interophub.service.DandelionSyncService;

public class EsTopicDao extends GenericDao<EsTopic, Long> {

    public EsTopicDao() {
        super(EsTopic.class);
    }

    public Optional<EsTopic> findByTopicCode(String topicCode) {
        if (topicCode == null || topicCode.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from EsTopic t where t.topicCode = :code", EsTopic.class)
                    .setParameter("code", topicCode.trim())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Optional<EsTopic> findByTopicCodeAndSpaceId(String topicCode, Long esTopicSpaceId) {
        if (topicCode == null || topicCode.isBlank() || esTopicSpaceId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopic t where t.topicCode = :code and t.esTopicSpaceId = :spaceId",
                    EsTopic.class)
                    .setParameter("code", topicCode.trim())
                    .setParameter("spaceId", esTopicSpaceId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsTopic> findAllActive() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopic t where t.status = :status order by t.topicCode asc",
                    EsTopic.class)
                    .setParameter("status", EsTopic.EsTopicStatus.ACTIVE)
                    .getResultList();
        }
    }

    public List<EsTopic> findAllActiveForPublicPage() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopic t"
                            + " where t.status = :status"
                            + " order by"
                            + " case when t.neighborhood is null or trim(t.neighborhood) = '' then 1 else 0 end asc,"
                            + " lower(coalesce(t.neighborhood, '')) asc,"
                            + " case"
                            + " when lower(coalesce(t.stage, '')) = 'draft' then 1"
                            + " when lower(coalesce(t.stage, '')) = 'gather' then 2"
                            + " when lower(coalesce(t.stage, '')) = 'monitor' then 3"
                            + " when lower(coalesce(t.stage, '')) = 'pilot' then 4"
                            + " when lower(coalesce(t.stage, '')) = 'rollout' then 5"
                            + " when lower(coalesce(t.stage, '')) = 'parked' then 6"
                            + " else 99 end asc,"
                            + " lower(t.topicName) asc",
                    EsTopic.class)
                    .setParameter("status", EsTopic.EsTopicStatus.ACTIVE)
                    .getResultList();
        }
    }

    public java.util.Optional<EsCampaignTopicBrowseRow> findActiveById(Long topicId) {
        if (topicId == null) {
            return java.util.Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsCampaignTopicBrowseRow("
                            + " t.esTopicId, t.topicName, t.description, t.topicType, t.policyStatus, t.neighborhood, t.stage, 0, t.confluenceUrl)"
                            + " from EsTopic t"
                            + " where t.esTopicId = :topicId and t.status = :status",
                    EsCampaignTopicBrowseRow.class)
                    .setParameter("topicId", topicId)
                    .setParameter("status", EsTopic.EsTopicStatus.ACTIVE)
                    .uniqueResultOptional();
        }
    }

    public List<EsCampaignTopicBrowseRow> findAllActiveBrowseRowsOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsCampaignTopicBrowseRow("
                            + " t.esTopicId, t.topicName, t.description, t.topicType, t.policyStatus, t.neighborhood, t.stage, 0, t.confluenceUrl)"
                            + " from EsTopic t"
                            + " where t.status = :status"
                            + " order by"
                            + " case"
                            + " when lower(coalesce(t.stage, '')) = 'draft' then 1"
                            + " when lower(coalesce(t.stage, '')) = 'gather' then 2"
                            + " when lower(coalesce(t.stage, '')) = 'monitor' then 3"
                            + " when lower(coalesce(t.stage, '')) = 'parked' then 4"
                            + " when lower(coalesce(t.stage, '')) = 'pilot' then 5"
                            + " when lower(coalesce(t.stage, '')) = 'rollout' then 6"
                            + " else 99 end asc,"
                            + " lower(t.topicName) asc",
                    EsCampaignTopicBrowseRow.class)
                    .setParameter("status", EsTopic.EsTopicStatus.ACTIVE)
                    .getResultList();
        }
    }

    public Map<Long, Long> findSpaceIdsByTopicIds(List<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Map.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select t.esTopicId, t.esTopicSpaceId from EsTopic t where t.esTopicId in (:topicIds)",
                    Object[].class)
                    .setParameterList("topicIds", topicIds)
                    .getResultList();
            Map<Long, Long> result = new LinkedHashMap<>();
            for (Object[] row : rows) {
                result.put((Long) row[0], (Long) row[1]);
            }
            return result;
        }
    }

    public List<EsTopic> findAllOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from EsTopic t order by t.topicCode asc", EsTopic.class)
                    .getResultList();
        }
    }

    public List<EsTopic> findAllOrderByTopicName() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from EsTopic t order by lower(t.topicName) asc, t.esTopicId asc", EsTopic.class)
                    .getResultList();
        }
    }

    public long countBySpaceId(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return 0L;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(t) from EsTopic t where t.esTopicSpaceId = :spaceId",
                    Long.class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .getSingleResult();
            return count == null ? 0L : count;
        }
    }

    public EsTopic saveOrUpdate(EsTopic topic) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsTopic merged = (EsTopic) session.merge(topic);
            tx.commit();
            new DandelionSyncService().enqueueTopicUpsert(merged.getEsTopicId());
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public List<String> findDistinctPolicyStatuses() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "SELECT DISTINCT t.policyStatus FROM EsTopic t WHERE t.policyStatus IS NOT NULL ORDER BY t.policyStatus",
                    String.class)
                    .getResultList();
        }
    }

    public List<String> findDistinctTopicTypes() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "SELECT DISTINCT t.topicType FROM EsTopic t WHERE t.topicType IS NOT NULL ORDER BY t.topicType",
                    String.class)
                    .getResultList();
        }
    }
}
