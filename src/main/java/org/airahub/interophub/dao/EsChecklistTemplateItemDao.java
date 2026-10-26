package org.airahub.interophub.dao;

import java.util.Collection;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsChecklistTemplateItem;

public class EsChecklistTemplateItemDao extends GenericDao<EsChecklistTemplateItem, Long> {

    public EsChecklistTemplateItemDao() {
        super(EsChecklistTemplateItem.class);
    }

    public List<EsChecklistTemplateItem> findByTemplateId(Long templateId) {
        if (templateId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsChecklistTemplateItem i where i.esChecklistTemplateId = :id order by i.displayOrder, i.esChecklistTemplateItemId",
                    EsChecklistTemplateItem.class)
                    .setParameter("id", templateId)
                    .getResultList();
        }
    }

    public List<EsChecklistTemplateItem> findByTemplateIds(Collection<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsChecklistTemplateItem i where i.esChecklistTemplateId in :ids order by i.displayOrder",
                    EsChecklistTemplateItem.class)
                    .setParameter("ids", templateIds)
                    .getResultList();
        }
    }
}
