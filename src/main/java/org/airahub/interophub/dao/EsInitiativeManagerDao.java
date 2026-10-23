package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsInitiativeManager;

public class EsInitiativeManagerDao extends GenericDao<EsInitiativeManager, Long> {

    public EsInitiativeManagerDao() {
        super(EsInitiativeManager.class);
    }

    public List<EsInitiativeManager> findByInitiativeId(Long esInitiativeId) {
        if (esInitiativeId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiativeManager m where m.esInitiativeId = :id order by m.createdAt, m.esInitiativeManagerId",
                    EsInitiativeManager.class)
                    .setParameter("id", esInitiativeId)
                    .getResultList();
        }
    }

    public List<EsInitiativeManager> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiativeManager m where m.userId = :userId",
                    EsInitiativeManager.class)
                    .setParameter("userId", userId)
                    .getResultList();
        }
    }

    public Optional<EsInitiativeManager> findByInitiativeAndUser(Long esInitiativeId, Long userId) {
        if (esInitiativeId == null || userId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsInitiativeManager m where m.esInitiativeId = :id and m.userId = :userId",
                    EsInitiativeManager.class)
                    .setParameter("id", esInitiativeId)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }

    public boolean existsForUser(Long userId) {
        if (userId == null) {
            return false;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(m.esInitiativeManagerId) from EsInitiativeManager m where m.userId = :userId",
                    Long.class)
                    .setParameter("userId", userId)
                    .uniqueResult();
            return count != null && count > 0;
        }
    }
}
