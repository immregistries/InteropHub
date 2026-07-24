package org.airahub.interophub.dao;

import java.util.List;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicNoteEditorHistory;

public class EsTopicNoteEditorHistoryDao extends GenericDao<EsTopicNoteEditorHistory, Long> {

    public EsTopicNoteEditorHistoryDao() {
        super(EsTopicNoteEditorHistory.class);
    }

    public List<EsTopicNoteEditorHistory> findByNoteIdOrdered(Long esTopicNoteId) {
        if (esTopicNoteId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicNoteEditorHistory h where h.esTopicNoteId = :noteId"
                            + " order by h.changedAt asc, h.esTopicNoteEditorHistoryId asc",
                    EsTopicNoteEditorHistory.class)
                    .setParameter("noteId", esTopicNoteId)
                    .getResultList();
        }
    }
}