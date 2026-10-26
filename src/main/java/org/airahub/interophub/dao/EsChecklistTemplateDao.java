package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsChecklistTemplate;

public class EsChecklistTemplateDao extends GenericDao<EsChecklistTemplate, Long> {

    public EsChecklistTemplateDao() {
        super(EsChecklistTemplate.class);
    }

    public Optional<EsChecklistTemplate> findCurrent(EsChecklistTemplate.Kind kind) {
        if (kind == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsChecklistTemplate t where t.templateKind = :kind and t.current = true"
                            + " order by t.versionNo desc",
                    EsChecklistTemplate.class)
                    .setParameter("kind", kind)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public Optional<EsChecklistTemplate> findByKindAndVersion(EsChecklistTemplate.Kind kind, int versionNo) {
        if (kind == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsChecklistTemplate t where t.templateKind = :kind and t.versionNo = :version",
                    EsChecklistTemplate.class)
                    .setParameter("kind", kind)
                    .setParameter("version", versionNo)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsChecklistTemplate> findAllOrderByKindAndVersion() {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsChecklistTemplate t order by t.templateKind, t.versionNo desc",
                    EsChecklistTemplate.class)
                    .getResultList();
        }
    }
}
