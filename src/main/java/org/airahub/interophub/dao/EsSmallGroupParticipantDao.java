package org.airahub.interophub.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsSmallGroupParticipant;

public class EsSmallGroupParticipantDao extends GenericDao<EsSmallGroupParticipant, Long> {

    public EsSmallGroupParticipantDao() {
        super(EsSmallGroupParticipant.class);
    }

    public List<EsSmallGroupParticipant> findBySmallGroupId(Long smallGroupId) {
        if (smallGroupId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSmallGroupParticipant p where p.esSmallGroupId = :id order by p.esSmallGroupParticipantId",
                    EsSmallGroupParticipant.class)
                    .setParameter("id", smallGroupId)
                    .getResultList();
        }
    }

    public List<EsSmallGroupParticipant> findBySmallGroupIds(Collection<Long> smallGroupIds) {
        if (smallGroupIds == null || smallGroupIds.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSmallGroupParticipant p where p.esSmallGroupId in :ids"
                            + " order by p.esSmallGroupId, p.esSmallGroupParticipantId",
                    EsSmallGroupParticipant.class)
                    .setParameterList("ids", smallGroupIds)
                    .getResultList();
        }
    }

    public Optional<EsSmallGroupParticipant> findByGroupAndMember(Long smallGroupId, Long memberId) {
        if (smallGroupId == null || memberId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSmallGroupParticipant p where p.esSmallGroupId = :groupId"
                            + " and p.esInitiativeMemberId = :memberId",
                    EsSmallGroupParticipant.class)
                    .setParameter("groupId", smallGroupId)
                    .setParameter("memberId", memberId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }
}
