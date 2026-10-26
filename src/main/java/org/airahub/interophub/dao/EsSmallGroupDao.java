package org.airahub.interophub.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsSmallGroup;

public class EsSmallGroupDao extends GenericDao<EsSmallGroup, Long> {

    public EsSmallGroupDao() {
        super(EsSmallGroup.class);
    }

    public List<EsSmallGroup> findByInitiativeId(Long initiativeId) {
        if (initiativeId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSmallGroup g where g.esInitiativeId = :id"
                            + " order by g.cycleStartDate, g.esSmallGroupId",
                    EsSmallGroup.class)
                    .setParameter("id", initiativeId)
                    .getResultList();
        }
    }

    public Optional<EsSmallGroup> findInInitiative(Long initiativeId, Long smallGroupId) {
        if (initiativeId == null || smallGroupId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSmallGroup g where g.esInitiativeId = :initiativeId and g.esSmallGroupId = :id",
                    EsSmallGroup.class)
                    .setParameter("initiativeId", initiativeId)
                    .setParameter("id", smallGroupId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsSmallGroup> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsSmallGroup g where g.esSmallGroupId in :ids",
                    EsSmallGroup.class)
                    .setParameterList("ids", ids)
                    .getResultList();
        }
    }
}
