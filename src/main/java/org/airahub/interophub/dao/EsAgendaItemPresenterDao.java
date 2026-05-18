package org.airahub.interophub.dao;

import java.time.LocalDateTime;
import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsAgendaItemPresenter;

public class EsAgendaItemPresenterDao extends GenericDao<EsAgendaItemPresenter, Long> {

    public EsAgendaItemPresenterDao() {
        super(EsAgendaItemPresenter.class);
    }

    /**
     * Returns all presenters for an agenda item ordered by presenter_role then id.
     */
    public List<EsAgendaItemPresenter> findByAgendaItemId(Long esMeetingAgendaItemId) {
        if (esMeetingAgendaItemId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsAgendaItemPresenter p where p.esMeetingAgendaItemId = :aid"
                            + " order by p.presenterRole asc, p.esAgendaItemPresenterId asc",
                    EsAgendaItemPresenter.class)
                    .setParameter("aid", esMeetingAgendaItemId)
                    .getResultList();
        }
    }

    /**
     * Returns presenters that are not REMOVED or DECLINED (i.e., still
     * active/pending).
     */
    public List<EsAgendaItemPresenter> findActiveByAgendaItemId(Long esMeetingAgendaItemId) {
        if (esMeetingAgendaItemId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsAgendaItemPresenter p where p.esMeetingAgendaItemId = :aid"
                            + " and p.status not in (:excluded)"
                            + " order by p.presenterRole asc, p.esAgendaItemPresenterId asc",
                    EsAgendaItemPresenter.class)
                    .setParameter("aid", esMeetingAgendaItemId)
                    .setParameterList("excluded", List.of(
                            EsAgendaItemPresenter.PresenterStatus.REMOVED,
                            EsAgendaItemPresenter.PresenterStatus.DECLINED))
                    .getResultList();
        }
    }

    public List<EsAgendaItemPresenter> findByEmailNormalized(String emailNormalized) {
        if (emailNormalized == null || emailNormalized.isBlank()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsAgendaItemPresenter p where p.emailNormalized = :email"
                            + " order by p.createdAt desc",
                    EsAgendaItemPresenter.class)
                    .setParameter("email", emailNormalized.trim())
                    .getResultList();
        }
    }

    /**
     * Returns all presenters for a batch of agenda item IDs in a single query,
     * ordered by agenda item id then presenter id. Used for bulk loading.
     */
    public List<EsAgendaItemPresenter> findByAgendaItemIds(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsAgendaItemPresenter p where p.esMeetingAgendaItemId in (:ids)"
                            + " order by p.esMeetingAgendaItemId asc, p.esAgendaItemPresenterId asc",
                    EsAgendaItemPresenter.class)
                    .setParameterList("ids", itemIds)
                    .getResultList();
        }
    }

    public List<EsAgendaItemPresenter> findByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsAgendaItemPresenter p where p.userId = :uid order by p.createdAt desc",
                    EsAgendaItemPresenter.class)
                    .setParameter("uid", userId)
                    .getResultList();
        }
    }

    /**
     * Records an acceptance response.
     *
     * @return number of rows updated (0 or 1)
     */
    public int acceptInvitation(Long esAgendaItemPresenterId) {
        return updateStatusWithNote(esAgendaItemPresenterId, EsAgendaItemPresenter.PresenterStatus.ACCEPTED, null);
    }

    /**
     * Records a decline response with an optional note.
     *
     * @return number of rows updated (0 or 1)
     */
    public int declineInvitation(Long esAgendaItemPresenterId, String responseNote) {
        return updateStatusWithNote(esAgendaItemPresenterId, EsAgendaItemPresenter.PresenterStatus.DECLINED,
                responseNote);
    }

    /**
     * Records a needs-changes response with an optional note.
     *
     * @return number of rows updated (0 or 1)
     */
    public int markNeedsChanges(Long esAgendaItemPresenterId, String responseNote) {
        return updateStatusWithNote(esAgendaItemPresenterId, EsAgendaItemPresenter.PresenterStatus.NEEDS_CHANGES,
                responseNote);
    }

    /**
     * Removes a presenter from the agenda item.
     *
     * @return number of rows updated (0 or 1)
     */
    public int removePresenter(Long esAgendaItemPresenterId) {
        return updateStatusWithNote(esAgendaItemPresenterId, EsAgendaItemPresenter.PresenterStatus.REMOVED, null);
    }

    public int updateRole(Long esAgendaItemPresenterId, EsAgendaItemPresenter.PresenterRole newRole) {
        if (esAgendaItemPresenterId == null || newRole == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update EsAgendaItemPresenter p set p.presenterRole = :role"
                            + " where p.esAgendaItemPresenterId = :id")
                    .setParameter("role", newRole)
                    .setParameter("id", esAgendaItemPresenterId)
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

    private int updateStatusWithNote(Long esAgendaItemPresenterId, EsAgendaItemPresenter.PresenterStatus newStatus,
            String responseNote) {
        if (esAgendaItemPresenterId == null) {
            return 0;
        }
        org.hibernate.Transaction tx = null;
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createMutationQuery(
                    "update EsAgendaItemPresenter p set p.status = :status"
                            + ", p.responseNote = :note"
                            + ", p.respondedAt = :now"
                            + " where p.esAgendaItemPresenterId = :id")
                    .setParameter("status", newStatus)
                    .setParameter("note", responseNote)
                    .setParameter("now", LocalDateTime.now())
                    .setParameter("id", esAgendaItemPresenterId)
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
}
