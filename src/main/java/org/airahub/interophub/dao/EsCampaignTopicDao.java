package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCampaignTopic;
import org.airahub.interophub.model.EsTopic;

public class EsCampaignTopicDao extends GenericDao<EsCampaignTopic, Long> {

    public EsCampaignTopicDao() {
        super(EsCampaignTopic.class);
    }

    public List<EsCampaignTopic> findByCampaignId(Long campaignId) {
        if (campaignId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCampaignTopic ct where ct.esCampaignId = :campaignId"
                            + " order by ct.displayOrder asc, ct.esCampaignTopicId asc",
                    EsCampaignTopic.class)
                    .setParameter("campaignId", campaignId)
                    .getResultList();
        }
    }

    public List<EsCampaignTopicBrowseRow> findBrowseRowsByCampaignIdOrdered(Long campaignId) {
        if (campaignId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsCampaignTopicBrowseRow("
                            + " ct.esTopicId, t.topicName, t.description, t.stage, ct.displayOrder)"
                            + " from EsCampaignTopic ct, EsTopic t"
                            + " where ct.esCampaignId = :campaignId"
                            + " and ct.esTopicId = t.esTopicId"
                            + " and t.status = :activeStatus"
                            + " order by"
                            + " case"
                            + " when lower(coalesce(t.stage, '')) = 'draft' then 1"
                            + " when lower(coalesce(t.stage, '')) = 'gather' then 2"
                            + " when lower(coalesce(t.stage, '')) = 'monitor' then 3"
                            + " when lower(coalesce(t.stage, '')) = 'parked' then 4"
                            + " when lower(coalesce(t.stage, '')) = 'pilot' then 5"
                            + " when lower(coalesce(t.stage, '')) = 'rollout' then 6"
                            + " else 99 end asc,"
                            + " ct.displayOrder desc,"
                            + " lower(t.topicName) asc,"
                            + " ct.esCampaignTopicId asc",
                    EsCampaignTopicBrowseRow.class)
                    .setParameter("campaignId", campaignId)
                    .setParameter("activeStatus", EsTopic.EsTopicStatus.ACTIVE)
                    .getResultList();
        }
    }

    public List<EsCampaignTopic> findByCampaignIdAndSetNo(Long campaignId, Integer setNo) {
        if (campaignId == null || setNo == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCampaignTopic ct where ct.esCampaignId = :campaignId"
                            + " and ct.topicSetNo = :setNo order by ct.displayOrder asc",
                    EsCampaignTopic.class)
                    .setParameter("campaignId", campaignId)
                    .setParameter("setNo", setNo)
                    .getResultList();
        }
    }

    public List<EsCampaignTopic> findByCampaignIdAndTableNo(Long campaignId, Integer tableNo) {
        if (campaignId == null || tableNo == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCampaignTopic ct where ct.esCampaignId = :campaignId"
                            + " and ct.tableNo = :tableNo order by ct.displayOrder asc",
                    EsCampaignTopic.class)
                    .setParameter("campaignId", campaignId)
                    .setParameter("tableNo", tableNo)
                    .getResultList();
        }
    }

    public Optional<EsCampaignTopic> findByCampaignIdAndTopicId(Long campaignId, Long topicId) {
        if (campaignId == null || topicId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCampaignTopic ct where ct.esCampaignId = :campaignId"
                            + " and ct.esTopicId = :topicId",
                    EsCampaignTopic.class)
                    .setParameter("campaignId", campaignId)
                    .setParameter("topicId", topicId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public EsCampaignTopic saveOrUpdate(EsCampaignTopic campaignTopic) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsCampaignTopic merged = (EsCampaignTopic) session.merge(campaignTopic);
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
