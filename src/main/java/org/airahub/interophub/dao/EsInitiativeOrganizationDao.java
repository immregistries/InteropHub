package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsInitiativeOrganization;

public class EsInitiativeOrganizationDao extends GenericDao<EsInitiativeOrganization, Long> {

    public EsInitiativeOrganizationDao() {
        super(EsInitiativeOrganization.class);
    }

    public List<EsInitiativeOrganization> findByInitiativeId(Long esInitiativeId) {
        if (esInitiativeId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiativeOrganization o where o.esInitiativeId = :id"
                            + " order by o.createdAt, o.esInitiativeOrganizationId",
                    EsInitiativeOrganization.class)
                    .setParameter("id", esInitiativeId)
                    .getResultList();
        }
    }

    public Optional<EsInitiativeOrganization> findByInitiativeAndOrganization(Long esInitiativeId, Long organizationId) {
        if (esInitiativeId == null || organizationId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiativeOrganization o where o.esInitiativeId = :initiativeId"
                            + " and o.organizationId = :organizationId",
                    EsInitiativeOrganization.class)
                    .setParameter("initiativeId", esInitiativeId)
                    .setParameter("organizationId", organizationId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }
}
