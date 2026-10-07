package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicResource;
import org.airahub.interophub.model.StoredFile;

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

    public StoredFile registerUpload(StoredFile file, EsTopicResource resource) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                session.persist(file);
                resource.setStoredFileId(file.getStoredFileId());
                session.persist(resource);
                transaction.commit();
                return file;
            } catch (RuntimeException ex) {
                try {
                    transaction.rollback();
                } catch (RuntimeException rollback) {
                    ex.addSuppressed(rollback);
                }
                throw ex;
            }
        }
    }
}
