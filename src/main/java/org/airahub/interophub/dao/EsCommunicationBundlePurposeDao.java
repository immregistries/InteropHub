package org.airahub.interophub.dao;

import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCommunicationBundlePurpose;

public class EsCommunicationBundlePurposeDao extends GenericDao<EsCommunicationBundlePurpose, Long> {
    public EsCommunicationBundlePurposeDao() { super(EsCommunicationBundlePurpose.class); }

    public Optional<EsCommunicationBundlePurpose> findActiveByKey(String purposeKey) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCommunicationBundlePurpose where purposeKey = :key and active = true",
                    EsCommunicationBundlePurpose.class)
                    .setParameter("key", purposeKey)
                    .uniqueResultOptional();
        }
    }

    public Optional<EsCommunicationBundlePurpose> findByKey(String purposeKey) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCommunicationBundlePurpose where purposeKey = :key",
                    EsCommunicationBundlePurpose.class)
                    .setParameter("key", purposeKey)
                    .uniqueResultOptional();
        }
    }
}
