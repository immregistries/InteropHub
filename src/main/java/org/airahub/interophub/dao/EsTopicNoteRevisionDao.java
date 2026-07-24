package org.airahub.interophub.dao;

import java.util.List;
import java.util.Optional;
import org.airahub.interophub.config.HibernateUtil;
import org.airahub.interophub.model.EsTopicNoteRevision;

public class EsTopicNoteRevisionDao extends GenericDao<EsTopicNoteRevision, Long> {

    public EsTopicNoteRevisionDao() {
        super(EsTopicNoteRevision.class);
    }

    public List<EsTopicNoteRevision> findByNoteIdOrdered(Long esTopicNoteId) {
        if (esTopicNoteId == null) {
            return List.of();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicNoteRevision r where r.esTopicNoteId = :noteId"
                            + " order by r.revisionNo asc, r.esTopicNoteRevisionId asc",
                    EsTopicNoteRevision.class)
                    .setParameter("noteId", esTopicNoteId)
                    .getResultList();
        }
    }

    public Optional<EsTopicNoteRevision> findLatestByNoteId(Long esTopicNoteId) {
        if (esTopicNoteId == null) {
            return Optional.empty();
        }
        try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "from EsTopicNoteRevision r where r.esTopicNoteId = :noteId"
                            + " order by r.revisionNo desc, r.esTopicNoteRevisionId desc",
                    EsTopicNoteRevision.class)
                    .setParameter("noteId", esTopicNoteId)
                    .setMaxResults(1)
                    .uniqueResultOptional();
        }
    }
}