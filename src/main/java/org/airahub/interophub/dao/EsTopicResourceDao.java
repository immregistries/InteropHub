package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicResource;

public class EsTopicResourceDao extends GenericDao<EsTopicResource, Long> {
    public EsTopicResourceDao() { super(EsTopicResource.class); }

    public List<EsTopicResource> findActiveByTopicId(Long topicId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicResource where esTopicId = :topicId and status = :status"
                            + " order by createdAt, topicResourceId",
                    EsTopicResource.class)
                    .setParameter("topicId", topicId)
                    .setParameter("status", EsTopicResource.Status.ACTIVE)
                    .getResultList();
        }
    }

    public Optional<EsTopicResource> findByStoredFileId(Long storedFileId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicResource where storedFileId = :storedFileId",
                    EsTopicResource.class)
                    .setParameter("storedFileId", storedFileId)
                    .uniqueResultOptional();
        }
    }
}
