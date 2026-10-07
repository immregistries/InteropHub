package org.airahub.interophub.dao;

import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsCommunicationBundle;

public class EsCommunicationBundleDao extends GenericDao<EsCommunicationBundle, Long> {
    public EsCommunicationBundleDao() { super(EsCommunicationBundle.class); }

    public Optional<EsCommunicationBundle> findByTopicAndPurpose(Long topicId, Long purposeId) {
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsCommunicationBundle where esTopicId = :topicId and purposeId = :purposeId",
                    EsCommunicationBundle.class)
                    .setParameter("topicId", topicId)
                    .setParameter("purposeId", purposeId)
                    .uniqueResultOptional();
        }
    }
}
