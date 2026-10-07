package org.airahub.interophub.dao;

import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsArtifactDemo;
import org.airahub.interophub.model.StoredFile;
import jakarta.persistence.LockModeType;
import java.util.logging.*;

/** TEMPORARY - see {@link EsArtifactDemo}. */
public class EsArtifactDemoDao extends GenericDao<EsArtifactDemo, Long> {
    private static final Logger LOGGER = Logger.getLogger(EsArtifactDemoDao.class.getName());

    public EsArtifactDemoDao() {
        super(EsArtifactDemo.class);
    }

    public Optional<EsArtifactDemo> findBySlotKey(String slotKey) {
        if (slotKey == null || slotKey.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsArtifactDemo a where a.slotKey = :slotKey", EsArtifactDemo.class)
                    .setParameter("slotKey", slotKey)
                    .uniqueResultOptional();
        }
    }

    public StoredFile register(String slotKey, StoredFile candidate) {
            try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
                org.hibernate.Transaction transaction = session.beginTransaction();
                try {
                    EsArtifactDemo demo = session.createQuery(
                            "from EsArtifactDemo where slotKey = :slot", EsArtifactDemo.class)
                            .setParameter("slot", slotKey).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                            .uniqueResult();
                    if (demo == null) {
                        if (candidate.getStoredFileId() != null) {
                            throw new IllegalStateException("Demo slot changed; reload before replacing.");
                        }
                        demo = new EsArtifactDemo();
                        demo.setSlotKey(slotKey);
                    } else if (!java.util.Objects.equals(demo.getStoredFile().getStoredFileId(),
                            candidate.getStoredFileId())) {
                        throw new IllegalStateException("Another upload changed this slot; reload before replacing.");
                    }
                    StoredFile saved = session.merge(candidate);
                    demo.setStoredFile(saved);
                    session.persist(demo);
                    transaction.commit();
                    return saved;
                } catch (RuntimeException ex) {
                    try {
                        transaction.rollback();
                    } catch (RuntimeException rollback) {
                        ex.addSuppressed(rollback);
                    }
                    LOGGER.log(Level.SEVERE, "Failed to register artifact demo upload", ex);
                    throw ex;
                }
        }
    }
}
