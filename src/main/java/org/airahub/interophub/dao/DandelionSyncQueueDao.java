package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.DandelionSyncQueueItem;

public class DandelionSyncQueueDao extends GenericDao<DandelionSyncQueueItem, Long> {
    public DandelionSyncQueueDao() {
        super(DandelionSyncQueueItem.class);
    }

    public DandelionSyncQueueItem replacePending(
            Long esTopicSpaceId,
            DandelionSyncQueueItem.EntityType entityType,
            Long entityId,
            Long secondaryEntityId,
            DandelionSyncQueueItem.OperationType operation) {
        if (esTopicSpaceId == null || entityType == null || entityId == null || operation == null) {
            throw new IllegalArgumentException(
                    "Queue item requires esTopicSpaceId, entityType, entityId, and operation.");
        }

        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            StringBuilder hql = new StringBuilder();
            hql.append("delete from DandelionSyncQueueItem q");
            hql.append(" where q.status = :status");
            hql.append(" and q.esTopicSpaceId = :spaceId");
            hql.append(" and q.entityType = :entityType");
            hql.append(" and q.entityId = :entityId");
            if (secondaryEntityId == null) {
                hql.append(" and q.secondaryEntityId is null");
            } else {
                hql.append(" and q.secondaryEntityId = :secondaryEntityId");
            }

            var delete = session.createMutationQuery(hql.toString())
                    .setParameter("status", DandelionSyncQueueItem.QueueStatus.PENDING)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("entityType", entityType)
                    .setParameter("entityId", entityId);
            if (secondaryEntityId != null) {
                delete.setParameter("secondaryEntityId", secondaryEntityId);
            }
            delete.executeUpdate();

            DandelionSyncQueueItem item = new DandelionSyncQueueItem();
            item.setEsTopicSpaceId(esTopicSpaceId);
            item.setEntityType(entityType);
            item.setEntityId(entityId);
            item.setSecondaryEntityId(secondaryEntityId);
            item.setOperation(operation);
            item.setStatus(DandelionSyncQueueItem.QueueStatus.PENDING);
            item.setAttemptCount(0);
            item.setLastError(null);
            item.setSentAt(null);
            session.persist(item);

            tx.commit();
            return item;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public List<DandelionSyncQueueItem> findPendingByEntityType(
            Long esTopicSpaceId,
            DandelionSyncQueueItem.EntityType entityType,
            int limit) {
        if (esTopicSpaceId == null || entityType == null) {
            return List.of();
        }
        int maxRows = limit > 0 ? limit : 100;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from DandelionSyncQueueItem q"
                            + " where q.status = :status and q.esTopicSpaceId = :spaceId"
                            + " and q.entityType = :entityType"
                            + " order by q.createdAt asc, q.syncQueueId asc",
                    DandelionSyncQueueItem.class)
                    .setParameter("status", DandelionSyncQueueItem.QueueStatus.PENDING)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("entityType", entityType)
                    .setMaxResults(maxRows)
                    .getResultList();
        }
    }

    public boolean hasPendingByEntityType(Long esTopicSpaceId, DandelionSyncQueueItem.EntityType entityType) {
        if (esTopicSpaceId == null || entityType == null) {
            return false;
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "select count(q) from DandelionSyncQueueItem q"
                            + " where q.status = :status and q.esTopicSpaceId = :spaceId"
                            + " and q.entityType = :entityType",
                    Long.class)
                    .setParameter("status", DandelionSyncQueueItem.QueueStatus.PENDING)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("entityType", entityType)
                    .getSingleResult();
            return count != null && count > 0;
        }
    }

    public int requeueFailedByEntityType(Long esTopicSpaceId, DandelionSyncQueueItem.EntityType entityType) {
        if (esTopicSpaceId == null || entityType == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update DandelionSyncQueueItem q"
                            + " set q.status = :pending, q.attemptCount = 0, q.lastError = null, q.sentAt = null"
                            + " where q.status = :failed and q.esTopicSpaceId = :spaceId"
                            + " and q.entityType = :entityType")
                    .setParameter("pending", DandelionSyncQueueItem.QueueStatus.PENDING)
                    .setParameter("failed", DandelionSyncQueueItem.QueueStatus.FAILED)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("entityType", entityType)
                    .executeUpdate();
            tx.commit();
            return updated;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public int requeueAllByEntityType(Long esTopicSpaceId, DandelionSyncQueueItem.EntityType entityType) {
        if (esTopicSpaceId == null || entityType == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update DandelionSyncQueueItem q"
                            + " set q.status = :pending, q.attemptCount = 0, q.lastError = null, q.sentAt = null"
                            + " where q.esTopicSpaceId = :spaceId and q.entityType = :entityType")
                    .setParameter("pending", DandelionSyncQueueItem.QueueStatus.PENDING)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setParameter("entityType", entityType)
                    .executeUpdate();
            tx.commit();
            return updated;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public void markSent(Long syncQueueId, LocalDateTime sentAt) {
        if (syncQueueId == null) {
            return;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.createMutationQuery(
                    "update DandelionSyncQueueItem q"
                            + " set q.status = :status, q.sentAt = :sentAt, q.lastError = null"
                            + " where q.syncQueueId = :id")
                    .setParameter("status", DandelionSyncQueueItem.QueueStatus.SENT)
                    .setParameter("sentAt", sentAt)
                    .setParameter("id", syncQueueId)
                    .executeUpdate();
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public void markFailed(Long syncQueueId, String message, int maxAttempts) {
        if (syncQueueId == null) {
            return;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            DandelionSyncQueueItem item = session.get(DandelionSyncQueueItem.class, syncQueueId);
            if (item == null) {
                tx.commit();
                return;
            }
            int nextAttempt = (item.getAttemptCount() == null ? 0 : item.getAttemptCount()) + 1;
            item.setAttemptCount(nextAttempt);
            item.setLastError(truncate(message, 4000));
            item.setStatus(nextAttempt >= maxAttempts
                    ? DandelionSyncQueueItem.QueueStatus.FAILED
                    : DandelionSyncQueueItem.QueueStatus.PENDING);
            session.merge(item);
            tx.commit();
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    public Map<DandelionSyncQueueItem.QueueStatus, Long> countByStatus(Long esTopicSpaceId) {
        if (esTopicSpaceId == null) {
            return Map.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Object[]> rows = session.createQuery(
                    "select q.status, count(q) from DandelionSyncQueueItem q"
                            + " where q.esTopicSpaceId = :spaceId group by q.status",
                    Object[].class)
                    .setParameter("spaceId", esTopicSpaceId)
                    .getResultList();
            Map<DandelionSyncQueueItem.QueueStatus, Long> counts = new LinkedHashMap<>();
            for (DandelionSyncQueueItem.QueueStatus status : DandelionSyncQueueItem.QueueStatus.values()) {
                counts.put(status, 0L);
            }
            for (Object[] row : rows) {
                counts.put((DandelionSyncQueueItem.QueueStatus) row[0], (Long) row[1]);
            }
            return counts;
        }
    }

    public List<DandelionSyncQueueItem> findRecentFailures(Long esTopicSpaceId, int limit) {
        if (esTopicSpaceId == null) {
            return List.of();
        }
        int maxRows = limit > 0 ? limit : 20;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from DandelionSyncQueueItem q where q.status = :status and q.esTopicSpaceId = :spaceId"
                            + " order by q.updatedAt desc, q.syncQueueId desc",
                    DandelionSyncQueueItem.class)
                    .setParameter("status", DandelionSyncQueueItem.QueueStatus.FAILED)
                    .setParameter("spaceId", esTopicSpaceId)
                    .setMaxResults(maxRows)
                    .getResultList();
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
