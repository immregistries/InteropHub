package org.airahub.interophub.dao;

import java.util.Collection;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsChecklistItem;

public class EsChecklistItemDao extends GenericDao<EsChecklistItem, Long> {

    public EsChecklistItemDao() {
        super(EsChecklistItem.class);
    }

    public List<EsChecklistItem> findByChecklistId(Long checklistId) {
        if (checklistId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsChecklistItem i where i.esChecklistId = :id order by i.displayOrder, i.esChecklistItemId",
                    EsChecklistItem.class)
                    .setParameter("id", checklistId)
                    .getResultList();
        }
    }

    public List<EsChecklistItem> findByChecklistIds(Collection<Long> checklistIds) {
        if (checklistIds == null || checklistIds.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsChecklistItem i where i.esChecklistId in :ids order by i.displayOrder, i.esChecklistItemId",
                    EsChecklistItem.class)
                    .setParameter("ids", checklistIds)
                    .getResultList();
        }
    }
}
