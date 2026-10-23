package org.airahub.interophub.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsInitiative;

public class EsInitiativeDao extends GenericDao<EsInitiative, Long> {

    public EsInitiativeDao() {
        super(EsInitiative.class);
    }

    public Optional<EsInitiative> findByCode(String initiativeCode) {
        if (initiativeCode == null || initiativeCode.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiative i where i.initiativeCode = :code",
                    EsInitiative.class)
                    .setParameter("code", initiativeCode.trim())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsInitiative> findAllOrderByName() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiative i order by lower(i.initiativeName), i.esInitiativeId",
                    EsInitiative.class)
                    .getResultList();
        }
    }

    public List<EsInitiative> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiative i where i.esInitiativeId in :ids order by lower(i.initiativeName)",
                    EsInitiative.class)
                    .setParameter("ids", ids)
                    .getResultList();
        }
    }
}
