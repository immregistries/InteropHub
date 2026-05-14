package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsAgendaItemComment;

public class EsAgendaItemCommentDao extends GenericDao<EsAgendaItemComment, Long> {

    public EsAgendaItemCommentDao() {
        super(EsAgendaItemComment.class);
    }

    /**
     * Returns all comments for an agenda item ordered by createdAt ascending.
     */
    public List<EsAgendaItemComment> findByAgendaItemId(Long esMeetingAgendaItemId) {
        if (esMeetingAgendaItemId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsAgendaItemComment c where c.esMeetingAgendaItemId = :aid"
                            + " order by c.createdAt asc",
                    EsAgendaItemComment.class)
                    .setParameter("aid", esMeetingAgendaItemId)
                    .getResultList();
        }
    }

    /**
     * Returns comments for an agenda item filtered by type, ordered by createdAt
     * ascending.
     */
    public List<EsAgendaItemComment> findByAgendaItemIdAndType(Long esMeetingAgendaItemId,
            EsAgendaItemComment.CommentType commentType) {
        if (esMeetingAgendaItemId == null || commentType == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsAgendaItemComment c where c.esMeetingAgendaItemId = :aid"
                            + " and c.commentType = :type"
                            + " order by c.createdAt asc",
                    EsAgendaItemComment.class)
                    .setParameter("aid", esMeetingAgendaItemId)
                    .setParameter("type", commentType)
                    .getResultList();
        }
    }
}
