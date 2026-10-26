package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsChecklist;
import org.airahub.interophub.model.EsChecklistTemplate;

public class EsChecklistDao extends GenericDao<EsChecklist, Long> {

    public EsChecklistDao() {
        super(EsChecklist.class);
    }

    public Optional<EsChecklist> findByInitiativeId(Long initiativeId) {
        if (initiativeId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsChecklist c where c.esInitiativeId = :id",
                    EsChecklist.class)
                    .setParameter("id", initiativeId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public List<EsChecklist> findByKind(EsChecklistTemplate.Kind kind) {
        if (kind == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsChecklist c where c.templateKind = :kind order by c.esChecklistId",
                    EsChecklist.class)
                    .setParameter("kind", kind)
                    .getResultList();
        }
    }
}
