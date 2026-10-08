package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicResource;
import org.airahub.interophub.model.EsCommunicationBundleAudit;
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

    public EsTopicResource updateMetadataWithAudit(EsTopicResource resource, Long userId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                EsTopicResource current =
                        session.find(EsTopicResource.class, resource.getTopicResourceId(), LockModeType.PESSIMISTIC_WRITE);
                if (current == null || current.getStatus() != EsTopicResource.Status.ACTIVE
                        || !current.getEsTopicId().equals(resource.getEsTopicId())) {
                    throw new IllegalArgumentException("Choose an active resource from this Topic.");
                }
                current.setTitle(resource.getTitle());
                current.setDescription(resource.getDescription());
                current.setAttribution(resource.getAttribution());
                current.setExternalUrl(resource.getExternalUrl());
                current.setUpdatedByUserId(userId);
                session.persist(EsCommunicationBundleAudit.event(null, current.getTopicResourceId(),
                        "RESOURCE_METADATA_UPDATED", "Topic Resource metadata updated.", userId));
                transaction.commit();
                return current;
            } catch (RuntimeException ex) {
                rollback(transaction, ex);
                throw ex;
            }
        }
    }

    public StoredFile replaceUpload(StoredFile replacement, EsTopicResource resource, Long userId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            var transaction = session.beginTransaction();
            try {
                EsTopicResource current =
                        session.find(EsTopicResource.class, resource.getTopicResourceId(), LockModeType.PESSIMISTIC_WRITE);
                StoredFile file = replacement.getStoredFileId() == null ? null
                        : session.find(StoredFile.class, replacement.getStoredFileId(), LockModeType.PESSIMISTIC_WRITE);
                if (current == null || current.getStatus() != EsTopicResource.Status.ACTIVE
                        || !current.getEsTopicId().equals(resource.getEsTopicId())
                        || file == null || !file.getStoredFileId().equals(current.getStoredFileId())) {
                    throw new IllegalArgumentException("Choose an active file-backed resource from this Topic.");
                }
                String previousFilename = file.getOriginalFilename();
                file.setStorageKey(replacement.getStorageKey());
                file.setBlobEndpoint(replacement.getBlobEndpoint());
                file.setBlobContainer(replacement.getBlobContainer());
                file.setOriginalFilename(replacement.getOriginalFilename());
                file.setContentType(replacement.getContentType());
                file.setSizeBytes(replacement.getSizeBytes());
                file.setUploadedByUserId(replacement.getUploadedByUserId());
                file.setUploadedAt(replacement.getUploadedAt());
                file.setDownloadOnly(replacement.isDownloadOnly());
                current.setResourceType(resource.getResourceType());
                current.setUpdatedByUserId(userId);
                session.persist(EsCommunicationBundleAudit.event(null, current.getTopicResourceId(),
                        "RESOURCE_FILE_REPLACED",
                        "Current file replaced (" + previousFilename + " -> "
                                + replacement.getOriginalFilename() + ").",
                        userId));
                transaction.commit();
                return file;
            } catch (RuntimeException ex) {
                rollback(transaction, ex);
                throw ex;
            }
        }
    }

    private static void rollback(org.hibernate.Transaction transaction, RuntimeException ex) {
        try {
            transaction.rollback();
        } catch (RuntimeException rollback) {
            ex.addSuppressed(rollback);
        }
    }
}
