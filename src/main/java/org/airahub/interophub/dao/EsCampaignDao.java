package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCampaign;

public class EsCampaignDao extends GenericDao<EsCampaign, Long> {

    public EsCampaignDao() {
        super(EsCampaign.class);
    }

    public Optional<EsCampaign> findByCampaignCode(String campaignCode) {
        if (campaignCode == null || campaignCode.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCampaign c where c.campaignCode = :code", EsCampaign.class)
                    .setParameter("code", campaignCode.trim())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsCampaign> findAllActive() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCampaign c where c.status = :status order by c.startAt asc, c.esCampaignId asc",
                    EsCampaign.class)
                    .setParameter("status", EsCampaign.CampaignStatus.ACTIVE)
                    .getResultList();
        }
    }

    public List<EsCampaign> findAllOrdered() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCampaign c order by c.startAt asc, c.esCampaignId asc",
                    EsCampaign.class)
                    .getResultList();
        }
    }

    public EsCampaign saveOrUpdate(EsCampaign campaign) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsCampaign merged = (EsCampaign) session.merge(campaign);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public int changeRoundByDelta(Long campaignId, int delta) {
        if (campaignId == null || delta == 0) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated;
            if (delta > 0) {
                updated = session.createMutationQuery(
                        "update EsCampaign c set c.currentRoundNo = c.currentRoundNo + 1"
                                + " where c.esCampaignId = :campaignId")
                        .setParameter("campaignId", campaignId)
                        .executeUpdate();
            } else {
                updated = session.createMutationQuery(
                        "update EsCampaign c set c.currentRoundNo = c.currentRoundNo - 1"
                                + " where c.esCampaignId = :campaignId and c.currentRoundNo > 1")
                        .setParameter("campaignId", campaignId)
                        .executeUpdate();
            }
            tx.commit();
            return updated;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }
}
