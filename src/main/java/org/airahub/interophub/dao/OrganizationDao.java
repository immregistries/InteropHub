package org.airahub.interophub.dao;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.Organization;

public class OrganizationDao extends GenericDao<Organization, Long> {

    public OrganizationDao() {
        super(Organization.class);
    }

    public List<Organization> findAllOrderByName() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from Organization o order by lower(o.organizationName), o.organizationId",
                    Organization.class)
                    .getResultList();
        }
    }

    public List<Organization> findActiveMatchingName(String fragment, int limit) {
        if (fragment == null || fragment.isBlank() || limit <= 0) {
            return List.of();
        }
        String safe = fragment.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
        if (safe.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from Organization o where o.active = true and lower(o.organizationName) like :q"
                            + " order by lower(o.organizationName), o.organizationId",
                    Organization.class)
                    .setParameter("q", "%" + safe + "%")
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    public Optional<Organization> findActiveById(Long organizationId) {
        return findById(organizationId).filter(org -> Boolean.TRUE.equals(org.getActive()));
    }
}
