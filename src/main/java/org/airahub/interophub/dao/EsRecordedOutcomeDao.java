package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsRecordedOutcome;

public class EsRecordedOutcomeDao extends GenericDao<EsRecordedOutcome, Long> {

    public EsRecordedOutcomeDao() {
        super(EsRecordedOutcome.class);
    }

    public List<EsRecordedOutcome> findByNoteIdOrdered(Long esTopicNoteId) {
        if (esTopicNoteId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsRecordedOutcome o where o.esTopicNoteId = :noteId"
                            + " order by o.displayOrder asc, o.esRecordedOutcomeId asc",
                    EsRecordedOutcome.class)
                    .setParameter("noteId", esTopicNoteId)
                    .getResultList();
        }
    }

    public Optional<EsRecordedOutcome> findByNoteIdAndSourceNodeId(Long esTopicNoteId, String sourceNodeId) {
        if (esTopicNoteId == null || sourceNodeId == null || sourceNodeId.isBlank()) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsRecordedOutcome o where o.esTopicNoteId = :noteId and o.sourceNodeId = :sourceNodeId",
                    EsRecordedOutcome.class)
                    .setParameter("noteId", esTopicNoteId)
                    .setParameter("sourceNodeId", sourceNodeId.trim())
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }
}