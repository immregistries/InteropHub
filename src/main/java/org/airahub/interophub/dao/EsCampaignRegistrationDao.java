package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCampaignRegistration;

public class EsCampaignRegistrationDao extends GenericDao<EsCampaignRegistration, Long> {

    public EsCampaignRegistrationDao() {
        super(EsCampaignRegistration.class);
    }

    public EsCampaignRegistration saveOrUpdate(EsCampaignRegistration registration) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsCampaignRegistration merged = (EsCampaignRegistration) session.merge(registration);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public long countByCampaignId(Long esCampaignId) {
        if (esCampaignId == null) {
            return 0L;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(r.esCampaignRegistrationId) from EsCampaignRegistration r"
                            + " where r.esCampaignId = :campaignId",
                    Long.class)
                    .setParameter("campaignId", esCampaignId)
                    .uniqueResult();
            return count == null ? 0L : count;
        }
    }

    public List<String> findRecentFirstNamesByCampaignId(Long esCampaignId, int limit) {
        if (esCampaignId == null || limit <= 0) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select r.firstName from EsCampaignRegistration r"
                            + " where r.esCampaignId = :campaignId"
                            + " and r.firstName is not null and r.firstName <> ''"
                            + " order by r.createdAt desc, r.esCampaignRegistrationId desc",
                    String.class)
                    .setParameter("campaignId", esCampaignId)
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    public Optional<Long> findLatestIdByCampaignAndSessionKey(Long esCampaignId, String sessionKey) {
        if (esCampaignId == null || sessionKey == null || sessionKey.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select r.esCampaignRegistrationId from EsCampaignRegistration r"
                            + " where r.esCampaignId = :campaignId and r.sessionKey = :sessionKey"
                            + " order by r.createdAt desc, r.esCampaignRegistrationId desc",
                    Long.class)
                    .setParameter("campaignId", esCampaignId)
                    .setParameter("sessionKey", sessionKey)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public int deleteByCampaignId(Long esCampaignId) {
        if (esCampaignId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int deleted = session.createMutationQuery(
                    "delete from EsCampaignRegistration r where r.esCampaignId = :campaignId")
                    .setParameter("campaignId", esCampaignId)
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
