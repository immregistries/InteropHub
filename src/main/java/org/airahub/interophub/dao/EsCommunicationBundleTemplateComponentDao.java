package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCommunicationBundleTemplateComponent;

public class EsCommunicationBundleTemplateComponentDao
        extends GenericDao<EsCommunicationBundleTemplateComponent, Long> {
    public EsCommunicationBundleTemplateComponentDao() {
        super(EsCommunicationBundleTemplateComponent.class);
    }

    public List<EsCommunicationBundleTemplateComponent> findByTemplateId(Long templateId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCommunicationBundleTemplateComponent where templateId = :templateId"
                            + " order by displayOrder, componentId",
                    EsCommunicationBundleTemplateComponent.class)
                    .setParameter("templateId", templateId)
                    .getResultList();
        }
    }
}
