package org.airahub.interophub.dao;

import java.util.List;
import java.util.Map;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsMeetingAgendaItem;

public class EsMeetingAgendaItemDao extends GenericDao<EsMeetingAgendaItem, Long> {

    public EsMeetingAgendaItemDao() {
        super(EsMeetingAgendaItem.class);
    }

    /**
     * Returns all agenda items for a meeting ordered by display_order ascending.
     */
    public List<EsMeetingAgendaItem> findByMeetingIdOrdered(Long esMeetingId) {
        if (esMeetingId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingAgendaItem a where a.esMeetingId = :mid"
                            + " order by a.displayOrder asc, a.esMeetingAgendaItemId asc",
                    EsMeetingAgendaItem.class)
                    .setParameter("mid", esMeetingId)
                    .getResultList();
        }
    }

    /**
     * Returns all agenda items linked to a specific topic (across all meetings),
     * ordered by meeting id then display_order.
     */
    public List<EsMeetingAgendaItem> findByTopicId(Long esTopicId) {
        if (esTopicId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingAgendaItem a where a.esTopicId = :tid"
                            + " order by a.esMeetingId asc, a.displayOrder asc",
                    EsMeetingAgendaItem.class)
                    .setParameter("tid", esTopicId)
                    .getResultList();
        }
    }

    public List<EsMeetingAgendaItem> findByStatus(EsMeetingAgendaItem.AgendaItemStatus status) {
        if (status == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsMeetingAgendaItem a where a.status = :status"
                            + " order by a.esMeetingId asc, a.displayOrder asc",
                    EsMeetingAgendaItem.class)
                    .setParameter("status", status)
                    .getResultList();
        }
    }

    /**
     * Updates display_order for each agenda item in the map within a single
     * transaction. Uses individual HQL updates per item.
     *
     * @param itemIdToNewOrder map of esMeetingAgendaItemId to new display_order
     *                         value
     * @return total number of rows updated
     */
    public int reorderItems(Map<Long, Integer> itemIdToNewOrder) {
        if (itemIdToNewOrder == null || itemIdToNewOrder.isEmpty()) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int total = 0;
            for (Map.Entry<Long, Integer> entry : itemIdToNewOrder.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                total += session.createMutationQuery(
                        "update EsMeetingAgendaItem a set a.displayOrder = :order"
                                + " where a.esMeetingAgendaItemId = :id")
                        .setParameter("order", entry.getValue())
                        .setParameter("id", entry.getKey())
                        .executeUpdate();
            }
            tx.commit();
            return total;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }

    /**
     * Marks an agenda item as POSTPONED and records the target meeting id.
     *
     * @return number of rows updated (0 or 1)
     */
    public int postponeItem(Long esMeetingAgendaItemId, Long postponedToMeetingId) {
        if (esMeetingAgendaItemId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update EsMeetingAgendaItem a set a.status = :status"
                            + ", a.postponedToMeetingId = :meetingId"
                            + " where a.esMeetingAgendaItemId = :id")
                    .setParameter("status", EsMeetingAgendaItem.AgendaItemStatus.POSTPONED)
                    .setParameter("meetingId", postponedToMeetingId)
                    .setParameter("id", esMeetingAgendaItemId)
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

    /**
     * @return number of rows updated (0 or 1)
     */
    public int markCovered(Long esMeetingAgendaItemId) {
        return updateStatus(esMeetingAgendaItemId, EsMeetingAgendaItem.AgendaItemStatus.COVERED);
    }

    /**
     * @return number of rows updated (0 or 1)
     */
    public int markNotCovered(Long esMeetingAgendaItemId) {
        return updateStatus(esMeetingAgendaItemId, EsMeetingAgendaItem.AgendaItemStatus.NOT_COVERED);
    }

    /**
     * @return number of rows updated (0 or 1)
     */
    public int cancelItem(Long esMeetingAgendaItemId) {
        return updateStatus(esMeetingAgendaItemId, EsMeetingAgendaItem.AgendaItemStatus.CANCELLED);
    }

    private int updateStatus(Long esMeetingAgendaItemId, EsMeetingAgendaItem.AgendaItemStatus newStatus) {
        if (esMeetingAgendaItemId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update EsMeetingAgendaItem a set a.status = :status"
                            + " where a.esMeetingAgendaItemId = :id")
                    .setParameter("status", newStatus)
                    .setParameter("id", esMeetingAgendaItemId)
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

    public EsMeetingAgendaItem saveOrUpdate(EsMeetingAgendaItem item) {
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            EsMeetingAgendaItem merged = session.merge(item);
            tx.commit();
            return merged;
        } catch (Exception ex) {
            if (tx != null) {
                tx.rollback();
            }
            throw ex;
        }
    }
}
