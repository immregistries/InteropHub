package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicRelationship;

public class EsTopicRelationshipDao extends GenericDao<EsTopicRelationship, Long> {

    public EsTopicRelationshipDao() {
        super(EsTopicRelationship.class);
    }

    /** Outbound links: relationships where this topic is the source. */
    public List<EsTopicRelationship> findByFromTopicId(Long fromTopicId) {
        if (fromTopicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicRelationship r where r.fromTopicId = :id"
                            + " order by r.displayOrder, r.esTopicRelationshipId",
                    EsTopicRelationship.class)
                    .setParameter("id", fromTopicId)
                    .getResultList();
        }
    }

    /** Inbound links: relationships where this topic is the target. */
    public List<EsTopicRelationship> findByToTopicId(Long toTopicId) {
        if (toTopicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicRelationship r where r.toTopicId = :id"
                            + " order by r.displayOrder, r.esTopicRelationshipId",
                    EsTopicRelationship.class)
                    .setParameter("id", toTopicId)
                    .getResultList();
        }
    }

    public void delete(Long relationshipId) {
        deleteById(relationshipId);
    }
}
