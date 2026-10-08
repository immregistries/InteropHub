package org.airahub.interophub.dao;

import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.StoredFile;

public class StoredFileDao extends GenericDao<StoredFile, Long> {
    public StoredFileDao() { super(StoredFile.class); }

    public boolean isPreserved(Long storedFileId) {
        if (storedFileId == null) {
            return false;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "select count(v) from EsTopicResourceVersion v where v.storedFileId = :id", Long.class)
                    .setParameter("id", storedFileId).getSingleResult() > 0;
        }
    }

    public Optional<StoredFile> findByPublicId(String publicId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from StoredFile where publicId = :id", StoredFile.class)
                    .setParameter("id", publicId).uniqueResultOptional();
        }
    }
}
