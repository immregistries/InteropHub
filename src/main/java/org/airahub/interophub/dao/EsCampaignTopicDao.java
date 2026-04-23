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
                            + " ct.esTopicId, t.topicName, t.description, t.topicType, t.policyStatus, t.neighborhood, t.stage, ct.displayOrder, t.confluenceUrl)"
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

    public List<EsCampaignMeetingBrowseRow> findActiveMeetingRowsByCampaignIdOrdered(Long campaignId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsCampaignMeetingBrowseRow("
                            + " t.esTopicId, t.topicCode, t.topicName, t.stage, 0,"
                            + " m.esTopicMeetingId, m.meetingName, m.meetingDescription, m.joinRequiresApproval)"
                            + " from EsTopicMeeting m, EsTopic t"
                            + " where m.esTopicId = t.esTopicId"
                            + " and t.status = :activeTopicStatus"
                            + " and m.disabledAt is null"
                            + " order by"
                            + " lower(coalesce(m.meetingName, t.topicName)) asc,"
                            + " t.esTopicId asc",
                    EsCampaignMeetingBrowseRow.class)
                    .setParameter("activeTopicStatus", EsTopic.EsTopicStatus.ACTIVE)
                    .getResultList();
        }
    }

    public List<EsCampaignMeetingBrowseRow> findAllActiveMeetingRowsOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsCampaignMeetingBrowseRow("
                            + " t.esTopicId, t.topicCode, t.topicName, t.stage, 0,"
                            + " m.esTopicMeetingId, m.meetingName, m.meetingDescription, m.joinRequiresApproval)"
                            + " from EsTopicMeeting m, EsTopic t"
                            + " where m.esTopicId = t.esTopicId"
                            + " and t.status = :activeTopicStatus"
                            + " and m.disabledAt is null"
                            + " order by"
                            + " lower(coalesce(m.meetingName, t.topicName)) asc,"
                            + " t.esTopicId asc",
                    EsCampaignMeetingBrowseRow.class)
                    .setParameter("activeTopicStatus", EsTopic.EsTopicStatus.ACTIVE)
                    .getResultList();
        }
    }

    public List<EsCampaignTopicBrowseRow> findDistinctTopicBrowseRowsByCampaignIdOrdered(Long campaignId) {
        if (campaignId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsCampaignTopicBrowseRow("
                            + " t.esTopicId, t.topicName, t.description, t.topicType, t.policyStatus, t.neighborhood, t.stage, max(ct.displayOrder), t.confluenceUrl)"
                            + " from EsCampaignTopic ct, EsTopic t"
                            + " where ct.esCampaignId = :campaignId"
                            + " and ct.esTopicId = t.esTopicId"
                            + " and t.status = :activeStatus"
                            + " group by t.esTopicId, t.topicName, t.description, t.topicType, t.policyStatus, t.neighborhood, t.stage"
                            + " order by max(ct.displayOrder) desc, lower(t.topicName) asc",
                    EsCampaignTopicBrowseRow.class)
                    .setParameter("campaignId", campaignId)
                    .setParameter("activeStatus", EsTopic.EsTopicStatus.ACTIVE)
                    .getResultList();
        }
    }

    public List<EsCampaignTopicBrowseRow> findBrowseRowsByCampaignIdAndTableNoOrdered(Long campaignId,
            Integer tableNo) {
        if (campaignId == null || tableNo == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsCampaignTopicBrowseRow("
                            + " ct.esTopicId, t.topicName, t.description, t.topicType, t.policyStatus, t.neighborhood, t.stage, ct.displayOrder, t.confluenceUrl)"
                            + " from EsCampaignTopic ct, EsTopic t"
                            + " where ct.esCampaignId = :campaignId"
                            + " and ct.tableNo = :tableNo"
                            + " and ct.esTopicId = t.esTopicId"
                            + " and t.status = :activeStatus"
                            + " order by ct.displayOrder asc, lower(t.topicName) asc, ct.esCampaignTopicId asc",
                    EsCampaignTopicBrowseRow.class)
                    .setParameter("campaignId", campaignId)
                    .setParameter("tableNo", tableNo)
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

    public Optional<EsCampaignTopic> findByCampaignIdAndTopicIdAndTableNo(
            Long campaignId, Long topicId, Integer tableNo) {
        if (campaignId == null || topicId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "from EsCampaignTopic ct where ct.esCampaignId = :campaignId"
                    + " and ct.esTopicId = :topicId"
                    + (tableNo != null ? " and ct.tableNo = :tableNo" : " and ct.tableNo is null");
            var query = session.createQuery(hql, EsCampaignTopic.class)
                    .setParameter("campaignId", campaignId)
                    .setParameter("topicId", topicId)
                    .setMaxResults(1);
            if (tableNo != null) {
                query.setParameter("tableNo", tableNo);
            }
            return query.uniqueResultOptional();
        }
    }

    public long countByCampaignId(Long campaignId) {
        if (campaignId == null) {
            return 0L;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(distinct ct.esTopicId) from EsCampaignTopic ct"
                            + " where ct.esCampaignId = :campaignId",
                    Long.class)
                    .setParameter("campaignId", campaignId)
                    .uniqueResult();
            return count == null ? 0L : count;
        }
    }

    public List<Integer> findDistinctTableNosByCampaignId(Long campaignId) {
        if (campaignId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select distinct ct.tableNo from EsCampaignTopic ct"
                            + " where ct.esCampaignId = :campaignId"
                            + " and ct.tableNo is not null"
                            + " order by ct.tableNo asc",
                    Integer.class)
                    .setParameter("campaignId", campaignId)
                    .getResultList();
        }
    }

    public long countByCampaignIdAndTableNo(Long campaignId, Integer tableNo) {
        if (campaignId == null) {
            return 0L;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "select count(ct.esCampaignTopicId) from EsCampaignTopic ct"
                    + " where ct.esCampaignId = :campaignId"
                    + (tableNo != null ? " and ct.tableNo = :tableNo" : " and ct.tableNo is null");
            var query = session.createQuery(hql, Long.class)
                    .setParameter("campaignId", campaignId);
            if (tableNo != null) {
                query.setParameter("tableNo", tableNo);
            }
            Long count = query.uniqueResult();
            return count == null ? 0L : count;
        }
    }

    public int deleteByCampaignIdAndTopicIdAndTableNoNotIn(
            Long campaignId, Long topicId, List<Integer> allowedTableNos) {
        if (campaignId == null || topicId == null || allowedTableNos == null || allowedTableNos.isEmpty()) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int deleted = session.createMutationQuery(
                    "delete from EsCampaignTopic ct"
                            + " where ct.esCampaignId = :campaignId"
                            + " and ct.esTopicId = :topicId"
                            + " and (ct.tableNo is null or ct.tableNo not in (:allowedTableNos))")
                    .setParameter("campaignId", campaignId)
                    .setParameter("topicId", topicId)
                    .setParameterList("allowedTableNos", allowedTableNos)
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

    public int deleteByCampaignId(Long campaignId) {
        if (campaignId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int deleted = session.createMutationQuery(
                    "delete from EsCampaignTopic ct where ct.esCampaignId = :campaignId")
                    .setParameter("campaignId", campaignId)
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
