package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsInterest;

public class EsInterestDao extends GenericDao<EsInterest, Long> {

    public EsInterestDao() {
        super(EsInterest.class);
    }

    public List<EsInterest> findByCampaignAndTableAndRoundAndSession(Long campaignId, Integer tableNo, Integer roundNo,
            String sessionKey) {
        if (campaignId == null || tableNo == null || roundNo == null || sessionKey == null || sessionKey.isBlank()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInterest i where i.esCampaignId = :cid"
                            + " and i.tableNo = :tableNo and i.roundNo = :roundNo"
                            + " and i.sessionKey = :sessionKey"
                            + " order by i.createdAt asc, i.esInterestId asc",
                    EsInterest.class)
                    .setParameter("cid", campaignId)
                    .setParameter("tableNo", tableNo)
                    .setParameter("roundNo", roundNo)
                    .setParameter("sessionKey", sessionKey)
                    .getResultList();
        }
    }

    public Set<Long> findTopicIdsByCampaignAndTableAndRoundAndSession(Long campaignId, Integer tableNo, Integer roundNo,
            String sessionKey) {
        return findByCampaignAndTableAndRoundAndSession(campaignId, tableNo, roundNo, sessionKey).stream()
                .map(EsInterest::getEsTopicId)
                .collect(Collectors.toSet());
    }

    public int deleteByCampaignAndTableAndRoundAndSession(Long campaignId, Integer tableNo, Integer roundNo,
            String sessionKey) {
        if (campaignId == null || tableNo == null || roundNo == null || sessionKey == null || sessionKey.isBlank()) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int deleted = session.createMutationQuery(
                    "delete from EsInterest i where i.esCampaignId = :cid"
                            + " and i.tableNo = :tableNo and i.roundNo = :roundNo"
                            + " and i.sessionKey = :sessionKey")
                    .setParameter("cid", campaignId)
                    .setParameter("tableNo", tableNo)
                    .setParameter("roundNo", roundNo)
                    .setParameter("sessionKey", sessionKey)
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
                    "delete from EsInterest i where i.esCampaignId = :cid")
                    .setParameter("cid", campaignId)
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

    public List<VoteTotalRow> findVoteTotalsByCampaignAndTableAndRound(Long campaignId, Integer tableNo,
            Integer roundNo) {
        if (campaignId == null || tableNo == null || roundNo == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsInterestDao$VoteTotalRow(i.esTopicId, count(i.esInterestId))"
                            + " from EsInterest i where i.esCampaignId = :cid"
                            + " and i.tableNo = :tableNo and i.roundNo = :roundNo"
                            + " group by i.esTopicId",
                    VoteTotalRow.class)
                    .setParameter("cid", campaignId)
                    .setParameter("tableNo", tableNo)
                    .setParameter("roundNo", roundNo)
                    .getResultList();
        }
    }

    public List<CampaignTopicRoundVoteRow> findVoteTotalsByCampaignTopicAndRound(Long campaignId) {
        if (campaignId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsInterestDao$CampaignTopicRoundVoteRow("
                            + " i.esTopicId, i.roundNo, count(i.esInterestId))"
                            + " from EsInterest i"
                            + " where i.esCampaignId = :cid"
                            + " group by i.esTopicId, i.roundNo",
                    CampaignTopicRoundVoteRow.class)
                    .setParameter("cid", campaignId)
                    .getResultList();
        }
    }

    public List<TableVoterCountRow> findDistinctVoterCountsByCampaignAndRound(Long campaignId, Integer roundNo) {
        if (campaignId == null || roundNo == null || roundNo < 1) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select new org.airahub.interophub.dao.EsInterestDao$TableVoterCountRow("
                            + " i.tableNo, count(distinct i.sessionKey))"
                            + " from EsInterest i"
                            + " where i.esCampaignId = :cid"
                            + " and i.roundNo = :roundNo"
                            + " and i.tableNo is not null"
                            + " and i.sessionKey is not null"
                            + " and i.sessionKey <> ''"
                            + " group by i.tableNo",
                    TableVoterCountRow.class)
                    .setParameter("cid", campaignId)
                    .setParameter("roundNo", roundNo)
                    .getResultList();
        }
    }

    public Optional<EsInterest> findAnyByCampaignAndTableAndRoundAndSession(Long campaignId, Integer tableNo,
            Integer roundNo, String sessionKey) {
        if (campaignId == null || tableNo == null || roundNo == null || sessionKey == null || sessionKey.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInterest i where i.esCampaignId = :cid"
                            + " and i.tableNo = :tableNo and i.roundNo = :roundNo"
                            + " and i.sessionKey = :sessionKey",
                    EsInterest.class)
                    .setParameter("cid", campaignId)
                    .setParameter("tableNo", tableNo)
                    .setParameter("roundNo", roundNo)
                    .setParameter("sessionKey", sessionKey)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public EsInterest saveOrUpdate(EsInterest interest) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsInterest merged = (EsInterest) session.merge(interest);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public static final class VoteTotalRow {
        private final Long esTopicId;
        private final Long voteCount;

        public VoteTotalRow(Long esTopicId, Long voteCount) {
            this.esTopicId = esTopicId;
            this.voteCount = voteCount == null ? 0L : voteCount;
        }

        public Long getEsTopicId() {
            return esTopicId;
        }

        public Long getVoteCount() {
            return voteCount;
        }
    }

    public static final class CampaignTopicRoundVoteRow {
        private final Long esTopicId;
        private final Integer roundNo;
        private final Long voteCount;

        public CampaignTopicRoundVoteRow(Long esTopicId, Integer roundNo, Long voteCount) {
            this.esTopicId = esTopicId;
            this.roundNo = roundNo;
            this.voteCount = voteCount == null ? 0L : voteCount;
        }

        public Long getEsTopicId() {
            return esTopicId;
        }

        public Integer getRoundNo() {
            return roundNo;
        }

        public Long getVoteCount() {
            return voteCount;
        }
    }

    public static final class TableVoterCountRow {
        private final Integer tableNo;
        private final Long voterCount;

        public TableVoterCountRow(Integer tableNo, Long voterCount) {
            this.tableNo = tableNo;
            this.voterCount = voterCount == null ? 0L : voterCount;
        }

        public Integer getTableNo() {
            return tableNo;
        }

        public Long getVoterCount() {
            return voterCount;
        }
    }
}
