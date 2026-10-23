package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsInitiativeLink;

public class EsInitiativeLinkDao extends GenericDao<EsInitiativeLink, Long> {

    public EsInitiativeLinkDao() {
        super(EsInitiativeLink.class);
    }

    public List<EsInitiativeLink> findByInitiativeId(Long esInitiativeId) {
        if (esInitiativeId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiativeLink l where l.esInitiativeId = :id"
                            + " order by l.displayOrder, l.esInitiativeLinkId",
                    EsInitiativeLink.class)
                    .setParameter("id", esInitiativeId)
                    .getResultList();
        }
    }

    public int nextDisplayOrder(Long esInitiativeId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Integer max = session.createQuery(
                    "select max(l.displayOrder) from EsInitiativeLink l where l.esInitiativeId = :id",
                    Integer.class)
                    .setParameter("id", esInitiativeId)
                    .uniqueResult();
            return max == null ? 0 : max + 1;
        }
    }
}
